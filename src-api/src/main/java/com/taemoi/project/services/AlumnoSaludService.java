package com.taemoi.project.services;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.cifrado.DatoCifradoIlegibleException;
import com.taemoi.project.dtos.request.AlumnoSaludRequest;
import com.taemoi.project.dtos.response.AlumnoSaludDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.TipoDiscapacidad;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AlumnoSaludRepository;

/**
 * Datos de salud de un alumno (decisión 7). Cada lectura queda en {@code auditoria_evento} (quién,
 * cuándo y qué alumno, nunca el contenido) dentro de la misma transacción: si no se puede auditar, no
 * se lee. Sin caché. Nada de este servicio escribe el contenido en logs.
 *
 * <p>Cifra y descifra aquí (no con un conversor JPA) para ligar cada texto a su fila y columna: el
 * dato autenticado de AES-GCM es {@code alumno_salud.<columna>#<alumno_id>}.
 */
@Service
public class AlumnoSaludService {

	/** Módulo de sus eventos de auditoría; identifica las lecturas de salud en el registro del propietario (D2). */
	public static final String MODULO_AUDITORIA = "salud";
	private static final String TABLA = "alumno_salud";
	private static final String TIPOS = "discapacidad_tipos";
	private static final String OTRA = "discapacidad_otra";
	private static final String SEPARADOR_TIPOS = ",";

	private final AlumnoSaludRepository salud;
	private final AlumnoRepository alumnos;
	private final AuditoriaService auditoria;
	private final CifradorDatos cifrador;

	public AlumnoSaludService(AlumnoSaludRepository salud, AlumnoRepository alumnos, AuditoriaService auditoria,
			CifradorDatos cifrador) {
		this.salud = salud;
		this.alumnos = alumnos;
		this.auditoria = auditoria;
		this.cifrador = cifrador;
	}

	@Transactional
	public AlumnoSaludDTO obtener(Long alumnoId) {
		return leerRegistrando(alumnoId, ruta(alumnoId), "Lectura de datos de salud del alumno #" + alumnoId);
	}

	/**
	 * Pieza E7-2 (D7-14): la lectura del profesor por {@code /api/profesor/alumnos/{id}/salud}. La misma que
	 * {@link #obtener} (mismo módulo, misma transacción; si no se puede registrar, no se lee) con la ruta del profesor en
	 * el evento y un resumen solo con el id. Quien llama ya comprobó que el alumno está activo en sus grupos.
	 */
	@Transactional
	public AlumnoSaludDTO obtenerComoProfesor(Long alumnoId) {
		return leerRegistrando(alumnoId, "/api/profesor/alumnos/" + alumnoId + "/salud",
				"Lectura de datos de salud del alumno #" + alumnoId + " (profesor)");
	}

	private AlumnoSaludDTO leerRegistrando(Long alumnoId, String endpoint, String resumen) {
		Alumno alumno = comprobarAlumno(alumnoId);
		auditoria.registrarAccesoSensible(AuditoriaAccion.READ, MODULO_AUDITORIA, endpoint, alumnoId, resumen);
		return salud.findById(alumnoId).map(s -> aDto(s, alumno))
				.orElseGet(() -> AlumnoSaludDTO.vacio(alumnoId, alumno.getTieneDiscapacidad()));
	}

	/**
	 * Sustituye la salud del alumno y, en la misma transacción, su discapacidad. Todo se valida antes de escribir nada: un
	 * 400 no deja ninguna escritura parcial. Una ficha anonimizada no se modifica (decisión 287, 409 como el resto de
	 * escrituras sobre ella).
	 *
	 * <p>Discapacidad:
	 * <ul>
	 * <li>{@code tieneDiscapacidad} nulo: no cambia ni el booleano ni los tipos; llegar con tipos o texto de «Otra», 400.</li>
	 * <li>{@code true}: los tipos son opcionales (sin ellos, columnas a NULL); con {@code OTRA} el texto es obligatorio, y
	 * sin {@code OTRA} se descarta.</li>
	 * <li>{@code false}: booleano a falso y columnas a NULL, se envíe lo que se envíe.</li>
	 * </ul>
	 */
	@Transactional
	public AlumnoSaludDTO guardar(Long alumnoId, AlumnoSaludRequest datos) {
		Alumno alumno = comprobarAlumno(alumnoId);
		alumno.exigirNoAnonimizado();
		String descripcion = texto(datos.descripcion(), "La descripción");
		String impideDetalle = texto(datos.impideDetalle(), "El detalle de lo que impide la actividad");
		String alergias = texto(datos.alergias(), "Las alergias");
		Discapacidad discapacidad = discapacidad(datos);

		AlumnoSalud registro = salud.findById(alumnoId).orElseGet(() -> new AlumnoSalud(alumnoId));
		registro.setLesionEnfermedad(datos.lesionEnfermedad());
		registro.setDescripcionCifrada(cifrador.cifrar(descripcion, contexto("descripcion", alumnoId)));
		registro.setImpideActividad(datos.impideActividad());
		registro.setImpideDetalleCifrado(cifrador.cifrar(impideDetalle, contexto("impide_detalle", alumnoId)));
		registro.setAlergiasCifradas(cifrador.cifrar(alergias, contexto("alergias", alumnoId)));
		if (datos.tieneDiscapacidad() != null) {
			alumno.setTieneDiscapacidad(datos.tieneDiscapacidad());
			registro.setDiscapacidadTiposCifrados(cifrador.cifrar(discapacidad.tipos(), contexto(TIPOS, alumnoId)));
			registro.setDiscapacidadOtraCifrada(cifrador.cifrar(discapacidad.otra(), contexto(OTRA, alumnoId)));
		}
		registro.setOrigen(datos.origen() != null ? datos.origen() : OrigenDato.APP);
		registro.setActualizadoEn(Instant.now());
		return aDto(salud.saveAndFlush(registro), alumno);
	}

	/**
	 * Invariante {@code tiene_discapacidad = false} ⇒ sin tipos: quien deje el booleano en falso partiendo de verdadero
	 * fuera de {@link #guardar} llama aquí, en su misma transacción (sin ella, falla). Pone las dos columnas a NULL y
	 * actualiza {@code actualizado_en} sin leer, descifrar ni auditar; sin fila no hace nada.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void limpiarDiscapacidad(Long alumnoId) {
		salud.limpiarDiscapacidad(alumnoId, Instant.now());
	}

	/** Lo que se guardará de discapacidad, en claro y ya validado; {@code tipos} son los nombres del enum separados por comas. */
	private record Discapacidad(String tipos, String otra) {
		private static final Discapacidad NADA = new Discapacidad(null, null);
	}

	/** Valida los campos de discapacidad del PUT. Ningún mensaje repite lo recibido. */
	private static Discapacidad discapacidad(AlumnoSaludRequest datos) {
		List<TipoDiscapacidad> tipos = datos.discapacidadTipos();
		boolean sinTipos = tipos == null || tipos.isEmpty();
		if (datos.tieneDiscapacidad() == null) {
			if (!sinTipos || (datos.discapacidadOtra() != null && !datos.discapacidadOtra().isBlank())) {
				throw new IllegalArgumentException("Indica si tiene discapacidad.");
			}
			return Discapacidad.NADA;
		}
		if (!datos.tieneDiscapacidad() || sinTipos) {
			return Discapacidad.NADA;
		}
		if (tipos.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Los tipos de discapacidad no son válidos.");
		}
		EnumSet<TipoDiscapacidad> elegidos = EnumSet.copyOf(tipos);
		String otra = null;
		if (elegidos.contains(TipoDiscapacidad.OTRA)) {
			otra = texto(datos.discapacidadOtra(), "La discapacidad indicada");
			if (otra == null) {
				throw new IllegalArgumentException("Indica cuál es la discapacidad.");
			}
		}
		return new Discapacidad(elegidos.stream().map(Enum::name).collect(Collectors.joining(SEPARADOR_TIPOS)), otra);
	}

	/** Lo guardado de discapacidad solo se descifra y se enseña si el booleano del alumno es {@code true}. */
	private AlumnoSaludDTO aDto(AlumnoSalud s, Alumno alumno) {
		Long id = s.getAlumnoId();
		boolean conDiscapacidad = Boolean.TRUE.equals(alumno.getTieneDiscapacidad());
		return AlumnoSaludDTO.de(s,
				cifrador.descifrar(s.getDescripcionCifrada(), contexto("descripcion", id)),
				cifrador.descifrar(s.getImpideDetalleCifrado(), contexto("impide_detalle", id)),
				cifrador.descifrar(s.getAlergiasCifradas(), contexto("alergias", id)),
				alumno.getTieneDiscapacidad(),
				conDiscapacidad ? tipos(s) : List.of(),
				conDiscapacidad ? cifrador.descifrar(s.getDiscapacidadOtraCifrada(), contexto(OTRA, id)) : null);
	}

	private List<TipoDiscapacidad> tipos(AlumnoSalud s) {
		String nombres = cifrador.descifrar(s.getDiscapacidadTiposCifrados(), contexto(TIPOS, s.getAlumnoId()));
		if (nombres == null) {
			return List.of();
		}
		try {
			return Arrays.stream(nombres.split(SEPARADOR_TIPOS)).map(TipoDiscapacidad::valueOf).toList();
		} catch (IllegalArgumentException e) {
			// Sin causa: el mensaje de valueOf repetiría lo guardado.
			throw new DatoCifradoIlegibleException("Tipo de discapacidad guardado desconocido.");
		}
	}

	private static String contexto(String columna, Long alumnoId) {
		return CifradorDatos.contexto(TABLA, columna, alumnoId);
	}

	private Alumno comprobarAlumno(Long alumnoId) {
		if (alumnoId == null) {
			throw new AlumnoNoEncontradoException("Alumno no encontrado.");
		}
		return alumnos.buscarPorIdEnOrganizacionActiva(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado."));
	}

	private static String ruta(Long alumnoId) {
		return "/api/alumnos/" + alumnoId + "/salud";
	}

	/** Texto opcional; el mensaje de error nunca repite el valor. */
	private static String texto(String valor, String campo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > AlumnoSaludRequest.MAX_TEXTO) {
			throw new IllegalArgumentException(
					campo + " no puede superar " + AlumnoSaludRequest.MAX_TEXTO + " caracteres.");
		}
		return limpio;
	}
}
