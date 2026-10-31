package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.SedeActivaRequest;
import com.taemoi.project.dtos.request.SedeRequest;
import com.taemoi.project.dtos.response.SedeDTO;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.repositories.SedeRepository;

/**
 * API de sedes para propietario y gestor (pieza E8-2; D8-2, fase3-e8-miembros.md §6.2): listar, ver, crear, editar y
 * activar o desactivar las sedes de la escuela activa. {@code Sede} es {@code @TenantId}: toda consulta va filtrada por
 * la escuela activa y una sede de otra escuela no existe (404).
 *
 * <ul>
 * <li>Nombre único en la escuela (409), comparado en la base con la colación de la columna, como la clave única
 * {@code uk_sede_organizacion_nombre}; si dos altas a la vez pasan la comprobación, la clave decide y también es 409.</li>
 * <li>Longitudes de las columnas en caracteres (400); las cadenas se recortan y las opcionales vacías quedan en
 * {@code null}.</li>
 * <li>Nunca sin sedes activas (409): activar y desactivar bloquean antes todas las sedes de la escuela, así que dos
 * desactivaciones a la vez no la dejan a cero. Desactivar no toca {@code membresia_sede} (D5-5).</li>
 * <li>Sin borrado: grupos, tarifas, entregas y movimientos la referencian (FK RESTRICT) y el historial se conserva.</li>
 * <li>Cada escritura correcta deja UNA fila en el registro ({@link RegistroAccionesSedes}, misma transacción), también
 * la que no cambia nada ({@code sinCambios=SI}): por eso las tres rutas están en
 * {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}.</li>
 * </ul>
 */
@Service
public class GestionSedesService {

	static final String NO_EXISTE = "La sede no existe.";
	static final String NOMBRE_REPETIDO = "Ya hay una sede con ese nombre.";
	static final String SIN_SEDES_ACTIVAS = "La escuela se quedaría sin sedes activas.";
	static final String NOMBRE_OBLIGATORIO = "Indica el nombre de la sede.";
	static final String ACTIVA_OBLIGATORIA = "Indica si la sede queda activa.";
	static final String SIN_DATOS = "Indica los datos de la sede.";
	static final String EMAIL_NO_VALIDO = "El email de la sede no es válido.";
	static final String AFORO_NO_VALIDO = "El aforo por turno debe estar entre 1 y 999999 (o vacío: el límite de la escuela).";

	/** Tope del aforo por turno: seis cifras, como el panel. */
	static final int AFORO_MAXIMO = 999_999;

	/** Clave única de V31 (organizacion_id, nombre). */
	private static final String CLAVE_UNICA_NOMBRE = "uk_sede_organizacion_nombre";
	/** Algo antes de la arroba, un dominio con punto y ningún espacio ni segunda arroba. */
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final String RUTA = "/api/sedes";

	private final SedeRepository sedes;
	private final RegistroAccionesSedes registro;
	private final AforoPreinscripcionService aforo;
	private final TemporadaService temporadas;

	public GestionSedesService(SedeRepository sedes, RegistroAccionesSedes registro,
			AforoPreinscripcionService aforo, TemporadaService temporadas) {
		this.sedes = sedes;
		this.registro = registro;
		this.aforo = aforo;
		this.temporadas = temporadas;
	}

	/**
	 * Resultado de una edición.
	 *
	 * @param aforoCambiado    el aforo de la sede es otro: pueden haberse abierto plazas
	 * @param turnosSobreAforo el aforo recién cambiado queda por debajo de la ocupación de algún turno de la sede
	 */
	public record Edicion(SedeDTO sede, boolean aforoCambiado, boolean turnosSobreAforo) {
	}

	/** Todas las sedes de la escuela activa, también las inactivas, por id. */
	@Transactional(readOnly = true)
	public List<SedeDTO> listar() {
		return sedes.findAllByOrderByIdAsc().stream().map(SedeDTO::de).toList();
	}

	/** Una sede de la escuela activa; de otra escuela o inexistente, 404. */
	@Transactional(readOnly = true)
	public SedeDTO ver(Long id) {
		return SedeDTO.de(exigir(id));
	}

	/**
	 * Crea una sede activa.
	 *
	 * @throws IllegalArgumentException (400) datos no válidos
	 * @throws IllegalStateException    (409) el nombre ya está en la escuela
	 */
	@Transactional
	public SedeDTO crear(SedeRequest solicitud) {
		Datos datos = Datos.de(solicitud);
		if (sedes.existsByNombre(datos.nombre())) {
			throw new IllegalStateException(NOMBRE_REPETIDO);
		}
		Sede sede = new Sede();
		datos.aplicar(sede);
		sede.setActiva(true);
		Sede guardada = guardar(sede);
		registro.registrar(RegistroAccionesSedes.Accion.SEDE_CREADA, RUTA, Map.of("sede", guardada.getId()));
		return SedeDTO.de(guardada);
	}

	/**
	 * Reemplaza los datos editables de la sede (no toca {@code activa}).
	 *
	 * @throws NoSuchElementException   (404) de otra escuela o inexistente
	 * @throws IllegalArgumentException (400) datos no válidos
	 * @throws IllegalStateException    (409) el nombre ya lo tiene otra sede de la escuela
	 */
	@Transactional
	public Edicion editar(Long id, SedeRequest solicitud) {
		Sede sede = exigir(id);
		Datos datos = Datos.de(solicitud);
		if (sedes.existsByNombreAndIdNot(datos.nombre(), sede.getId())) {
			throw new IllegalStateException(NOMBRE_REPETIDO);
		}
		boolean sinCambios = datos.igualQue(sede);
		boolean aforoCambiado = !Objects.equals(datos.aforoTurno(), sede.getAforoTurno());
		if (!sinCambios) {
			datos.aplicar(sede);
			sede = guardar(sede);
		}
		registro.registrar(RegistroAccionesSedes.Accion.SEDE_EDITADA, RUTA + "/" + sede.getId(),
				valores(sede.getId(), sinCambios));
		int aforoEfectivo = datos.aforoTurno() != null ? datos.aforoTurno() : aforo.limiteComun();
		boolean turnosSobreAforo = aforoCambiado
				&& aforo.hayTurnosSobreAforo(sede.getId(), aforoEfectivo, temporadas.actual());
		return new Edicion(SedeDTO.de(sede), aforoCambiado, turnosSobreAforo);
	}

	/**
	 * Activa o desactiva la sede. Bloquea antes todas las sedes de la escuela (READ_COMMITTED): una desactivación
	 * simultánea espera y ve la otra confirmada. Igual que ya está: 200 sin cambios (y su fila, {@code sinCambios=SI}).
	 *
	 * @throws IllegalArgumentException (400) sin {@code activa}
	 * @throws NoSuchElementException   (404) de otra escuela o inexistente
	 * @throws IllegalStateException    (409) es la última sede activa de la escuela
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public SedeDTO cambiarActiva(Long id, SedeActivaRequest solicitud) {
		if (solicitud == null || solicitud.activa() == null) {
			throw new IllegalArgumentException(ACTIVA_OBLIGATORIA);
		}
		boolean activa = solicitud.activa();
		List<Sede> deLaEscuela = sedes.bloquearDeLaEscuela();
		Sede sede = deLaEscuela.stream().filter(s -> Objects.equals(s.getId(), id)).findFirst()
				.orElseThrow(() -> new NoSuchElementException(NO_EXISTE));
		boolean sinCambios = sede.isActiva() == activa;
		if (!sinCambios) {
			long activas = deLaEscuela.stream().filter(Sede::isActiva).count();
			if (!activa && activas <= 1) {
				throw new IllegalStateException(SIN_SEDES_ACTIVAS);
			}
			sede.setActiva(activa);
			sede = sedes.saveAndFlush(sede);
		}
		registro.registrar(activa ? RegistroAccionesSedes.Accion.SEDE_ACTIVADA
				: RegistroAccionesSedes.Accion.SEDE_DESACTIVADA, RUTA + "/" + sede.getId() + "/activa",
				valores(sede.getId(), sinCambios));
		return SedeDTO.de(sede);
	}

	// ------------------------------------------------------------------ utilidades

	private Sede exigir(Long id) {
		if (id == null) {
			throw new NoSuchElementException(NO_EXISTE);
		}
		return sedes.buscarEnEscuelaActiva(id).orElseThrow(() -> new NoSuchElementException(NO_EXISTE));
	}

	/** Guarda con flush: dos altas o renombrados a la vez con el mismo nombre chocan aquí con la clave única (409). */
	private Sede guardar(Sede sede) {
		try {
			return sedes.saveAndFlush(sede);
		} catch (DataIntegrityViolationException error) {
			if (violaNombreUnico(error)) {
				throw new IllegalStateException(NOMBRE_REPETIDO);
			}
			throw error;
		}
	}

	/** La violación es la de la clave única del nombre: su nombre aparece en la causa. */
	static boolean violaNombreUnico(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
					&& violacion.getConstraintName() != null
					&& violacion.getConstraintName().contains(CLAVE_UNICA_NOMBRE)) {
				return true;
			}
			if (causa.getMessage() != null && causa.getMessage().contains(CLAVE_UNICA_NOMBRE)) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	private static Map<String, Object> valores(Long sede, boolean sinCambios) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("sede", sede);
		if (sinCambios) {
			valores.put("sinCambios", Boolean.TRUE);
		}
		return valores;
	}

	/** Los datos de la petición ya validados y normalizados. */
	record Datos(String nombre, String direccion, String localidad, String codigoPostal, String telefono, String email,
			String codigoClubFederacion, Integer aforoTurno) {

		static Datos de(SedeRequest solicitud) {
			if (solicitud == null) {
				throw new IllegalArgumentException(SIN_DATOS);
			}
			String nombre = recortar(solicitud.nombre());
			if (nombre == null) {
				throw new IllegalArgumentException(NOMBRE_OBLIGATORIO);
			}
			maximo(nombre, 100, "El nombre de la sede");
			String direccion = maximo(recortar(solicitud.direccion()), 255, "La dirección");
			String localidad = maximo(recortar(solicitud.localidad()), 100, "La localidad");
			String codigoPostal = maximo(recortar(solicitud.codigoPostal()), 10, "El código postal");
			String telefono = maximo(recortar(solicitud.telefono()), 20, "El teléfono");
			String email = maximo(recortar(solicitud.email()), 180, "El email");
			if (email != null && !EMAIL.matcher(email).matches()) {
				throw new IllegalArgumentException(EMAIL_NO_VALIDO);
			}
			String club = maximo(recortar(solicitud.codigoClubFederacion()), 40, "El código del club en la federación");
			Integer aforo = solicitud.aforoTurno();
			if (aforo != null && (aforo < 1 || aforo > AFORO_MAXIMO)) {
				throw new IllegalArgumentException(AFORO_NO_VALIDO);
			}
			return new Datos(nombre, direccion, localidad, codigoPostal, telefono, email, club, aforo);
		}

		void aplicar(Sede sede) {
			sede.setNombre(nombre);
			sede.setDireccion(direccion);
			sede.setLocalidad(localidad);
			sede.setCodigoPostal(codigoPostal);
			sede.setTelefono(telefono);
			sede.setEmail(email);
			sede.setCodigoClubFederacion(codigoClubFederacion);
			sede.setAforoTurno(aforoTurno);
		}

		boolean igualQue(Sede sede) {
			return Objects.equals(nombre, sede.getNombre()) && Objects.equals(direccion, sede.getDireccion())
					&& Objects.equals(localidad, sede.getLocalidad())
					&& Objects.equals(codigoPostal, sede.getCodigoPostal())
					&& Objects.equals(telefono, sede.getTelefono()) && Objects.equals(email, sede.getEmail())
					&& Objects.equals(codigoClubFederacion, sede.getCodigoClubFederacion())
					&& Objects.equals(aforoTurno, sede.getAforoTurno());
		}

		/** Recortada; vacía o en blanco, {@code null}. */
		private static String recortar(String valor) {
			if (valor == null) {
				return null;
			}
			String recortado = valor.strip();
			return recortado.isEmpty() ? null : recortado;
		}

		/** Longitud en caracteres (puntos de código), como cuenta {@code VARCHAR(n)} en utf8mb4. */
		private static String maximo(String valor, int maximo, String campo) {
			if (valor != null && valor.codePointCount(0, valor.length()) > maximo) {
				throw new IllegalArgumentException(campo + " no puede pasar de " + maximo + " caracteres.");
			}
			return valor;
		}
	}
}
