package com.taemoi.project.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.response.AlumnoFichaDTO;
import com.taemoi.project.dtos.response.CambioSedeAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository.CargoDeCambioDeSede;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.utils.ImporteCargo;

/**
 * Pieza 9-1 (decisión 369): cambiar de sede a un alumno, solo propietario y gestión.
 *
 * <p><b>Qué cambia.</b> {@code alumno.sede_principal_id} y la sede de los cargos que siguen sin cobrar: no pagados, no
 * cancelados y sin cobro ACTIVO, tengan la sede que tengan o ninguna ({@code producto_alumno.sede_id}). Los cargos
 * pagados, los cancelados, todos los cobros, las entregas de caja y los grupos del alumno no se tocan, ni el importe de
 * ningún cargo: el dinero cobrado se queda en la caja donde entró.
 *
 * <p><b>Concurrencia con el cobro</b> (READ_COMMITTED, como {@link CobroService}). Todo cobro de un cargo existente
 * bloquea la fila del cargo, la relee ({@link CobroService#bloquearCargo}) y copia su sede al cobro
 * ({@link CobroService#registrar}). Aquí se bloquean esos mismos cargos antes de escribir, en orden de id. Si un cobro
 * llega antes, el cambio espera, ve el cargo pagado y no lo mueve: cargo y cobro siguen en la sede vieja. Si llega
 * después, espera, relee y el cobro nace en la sede nueva. {@code producto_alumno.sede_id} es {@code updatable = false},
 * así que un {@code save} de un cargo cargado antes no devuelve la sede vieja.
 *
 * <p><b>Orden de bloqueo:</b> cargos y después alumno. El cobro bloquea su cargo y al insertar la fila de
 * {@code cobro} toma un bloqueo compartido sobre el alumno (clave ajena): al revés habría un ciclo. El bloqueo del alumno
 * ordena dos cambios simultáneos, pero no impide que nazca un cargo suyo: el que nace a la vez
 * ({@code asignarProductoAAlumno} o la generación masiva) leyó al alumno antes y puede quedar en la sede vieja, porque su
 * INSERT solo espera por la clave ajena. El cobro simultáneo de un cargo recién nacido que no entró en la lista de
 * bloqueados puede interbloquearse con el cambio: InnoDB aborta una de las transacciones y nada queda a medias. Es un
 * caso conocido, pendiente de confirmar por el propietario; se arregla volviendo a cambiar de sede.
 */
@Service
public class CambioSedeAlumnoService {

	static final String MISMA_SEDE = "El alumno ya está en esa sede.";
	private static final String SEDE_INEXISTENTE = "La sede no existe en la escuela.";
	private static final String SEDE_INACTIVA = "La sede está inactiva.";

	private final AlumnoRepository alumnos;
	private final SedeRepository sedes;
	private final ProductoAlumnoRepository cargos;
	private final GrupoRepository grupos;
	private final RegistroAccionesSedes registro;

	public CambioSedeAlumnoService(AlumnoRepository alumnos, SedeRepository sedes, ProductoAlumnoRepository cargos,
			GrupoRepository grupos, RegistroAccionesSedes registro) {
		this.alumnos = alumnos;
		this.sedes = sedes;
		this.cargos = cargos;
		this.grupos = grupos;
		this.registro = registro;
	}

	/**
	 * La ficha del panel: el DTO del alumno con su sede principal y el nombre de esta.
	 *
	 * @param alumno DTO con {@code sedePrincipalId} de {@code AlumnoService#obtenerAlumnoPorIdDTO}
	 */
	@Transactional(readOnly = true)
	public AlumnoFichaDTO ficha(AlumnoDTO alumno) {
		return new AlumnoFichaDTO(alumno, alumno.getSedePrincipalId(), nombreDeSede(alumno.getSedePrincipalId()));
	}

	/**
	 * Lectura previa: lo que haría {@link #cambiar} ahora mismo, sin modificar nada. Valida lo mismo, así que lo que
	 * aquí sale bien solo falla después si algo cambia entre medias.
	 *
	 * @throws AlumnoNoEncontradoException (404) no existe en la escuela activa
	 * @throws IllegalStateException       (409) alumno anonimizado
	 * @throws IllegalArgumentException    (400) sede sin indicar, de otra escuela o inactiva, o la que ya tiene
	 */
	@Transactional(readOnly = true)
	public CambioSedeAlumnoDTO previa(Long alumnoId, Long sedeId) {
		Alumno alumno = exigirAlumno(alumnoId);
		Sede nueva = exigirSedeDestino(sedeId);
		if (nueva.getId().equals(alumno.getSedePrincipalId())) {
			throw new IllegalArgumentException(MISMA_SEDE);
		}
		return resumen(alumnoId, alumno.getSedePrincipalId(), nueva);
	}

	/**
	 * Cambia al alumno de sede y mueve sus cargos sin cobrar, todo en una transacción (ver la clase). Devuelve la sede
	 * de la que viene y lo que se movió, con la misma forma que {@link #previa}.
	 *
	 * @throws AlumnoNoEncontradoException (404) no existe en la escuela activa
	 * @throws IllegalStateException       (409) alumno anonimizado
	 * @throws IllegalArgumentException    (400) sede sin indicar, de otra escuela o inactiva, o la que ya tiene
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CambioSedeAlumnoDTO cambiar(Long alumnoId, Long sedeId) {
		exigirAlumno(alumnoId);
		Sede nueva = exigirSedeDestino(sedeId);

		List<Long> afectados = cargos.cargosQuePasanASede(alumnoId, nueva.getId()).stream()
				.map(CargoDeCambioDeSede::getId).toList();
		if (!afectados.isEmpty()) {
			cargos.bloquearPorIdsEnOrden(afectados);
		}
		// Con los cargos bloqueados: el estado del alumno se decide con lo ya confirmado, no con lo leído al empezar.
		AlumnoRepository.EstadoSedeAlumno estado = alumnos.bloquearEstadoDeSede(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));
		if (estado.getAnonimizadoEn() != null) {
			throw new IllegalStateException(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA);
		}
		Long anterior = estado.getSedePrincipalId();
		if (nueva.getId().equals(anterior)) {
			throw new IllegalArgumentException(MISMA_SEDE);
		}
		CambioSedeAlumnoDTO resultado = resumen(alumnoId, anterior, nueva);

		alumnos.asignarSedePrincipal(alumnoId, nueva.getId());
		int movidos = cargos.moverASede(alumnoId, nueva.getId());

		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("alumno", alumnoId);
		valores.put("sedeAnterior", anterior);
		valores.put("sedeNueva", nueva.getId());
		valores.put("cargosMovidos", (long) movidos);
		registro.registrar(RegistroAccionesSedes.Accion.ALUMNO_SEDE_CAMBIADA, "/api/alumnos/" + alumnoId + "/sede",
				valores);
		return resultado;
	}

	private CambioSedeAlumnoDTO resumen(Long alumnoId, Long sedeActual, Sede nueva) {
		List<CargoDeCambioDeSede> pendientes = cargos.cargosQuePasanASede(alumnoId, nueva.getId());
		List<CargoDeCambioDeSede> cobrados = cargos.cargosCobradosEnSede(alumnoId, sedeActual);
		return new CambioSedeAlumnoDTO(sedeActual, nombreDeSede(sedeActual), nueva.getId(), nueva.getNombre(),
				pendientes.size(), suma(pendientes), cobrados.size(), suma(cobrados),
				grupos.contarDelAlumnoEnSede(alumnoId, sedeActual));
	}

	private static BigDecimal suma(List<CargoDeCambioDeSede> filas) {
		return filas.stream().map(fila -> ImporteCargo.de(fila.getPrecio(), fila.getCantidad()))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
	}

	private String nombreDeSede(Long sedeId) {
		return sedeId == null ? null : sedes.buscarEnEscuelaActiva(sedeId).map(Sede::getNombre).orElse(null);
	}

	private Alumno exigirAlumno(Long alumnoId) {
		Alumno alumno = alumnos.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));
		alumno.exigirNoAnonimizado();
		return alumno;
	}

	private Sede exigirSedeDestino(Long sedeId) {
		if (sedeId == null) {
			throw new IllegalArgumentException(GestionPorSede.INDICA_LA_SEDE);
		}
		Sede sede = sedes.buscarEnEscuelaActiva(sedeId)
				.orElseThrow(() -> new IllegalArgumentException(SEDE_INEXISTENTE));
		if (!sede.isActiva()) {
			throw new IllegalArgumentException(SEDE_INACTIVA);
		}
		return sede;
	}
}
