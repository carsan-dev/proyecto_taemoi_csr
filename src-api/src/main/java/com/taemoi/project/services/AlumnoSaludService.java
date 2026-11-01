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
	/**
	 * Longitud de la lista de tipos en claro: con espacios hasta aquí, todas las combinaciones se cifran igual de largas y
	 * la longitud de {@code discapacidad_tipos} no delata qué tipos hay. La lista completa son 51 caracteres.
	 */
	static final int LONGITUD_TIPOS_EN_CLARO = 64;

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
	 * <p>Lo primero es bloquear la fila del alumno, igual que {@code AlumnoService#anonimizarAlumno}: las dos operaciones se
	 * serializan. Sin ello, una anonimización confirmada entre la comprobación y la escritura se desharía entera.
	 *
	 * <p>Nunca descifra lo almacenado: la respuesta solo lleva lo que vino en la petición ya validado y el booleano del
	 * alumno. Devolver lo guardado sería una lectura sin auditar: con {@code tieneDiscapacidad} nulo, los tipos salen vacíos y
	 * el texto del detalle nulo, aunque haya guardados.
	 *
	 * <p>Discapacidad:
	 * <ul>
	 * <li>{@code tieneDiscapacidad} nulo: no cambia ni el booleano ni los tipos; llegar con tipos o detalle, 400.</li>
	 * <li>{@code true}: los tipos son opcionales (sin ellos, columnas a NULL y el detalle se descarta); con tipos se guarda
	 * el detalle ({@code discapacidadOtra}), obligatorio con {@code OTRA} y opcional sin ella.</li>
	 * <li>{@code false}: booleano a falso y columnas a NULL, se envíe lo que se envíe.</li>
	 * </ul>
	 */
	@Transactional
	public AlumnoSaludDTO guardar(Long alumnoId, AlumnoSaludRequest datos) {
		Alumno alumno = bloquearFichaNoAnonimizada(alumnoId);
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
			registro.setDiscapacidadTiposCifrados(cifrador.cifrar(discapacidad.tiposEnClaro(), contexto(TIPOS, alumnoId)));
			registro.setDiscapacidadOtraCifrada(cifrador.cifrar(discapacidad.otra(), contexto(OTRA, alumnoId)));
		}
		registro.setOrigen(datos.origen() != null ? datos.origen() : OrigenDato.APP);
		registro.setActualizadoEn(Instant.now());
		return AlumnoSaludDTO.de(salud.saveAndFlush(registro), descripcion, impideDetalle, alergias,
				alumno.getTieneDiscapacidad(), discapacidad.tipos(), discapacidad.otra());
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

	/** Lo que se guardará de discapacidad, en claro y ya validado. Sin contenido en {@link #toString()}. */
	record Discapacidad(List<TipoDiscapacidad> tipos, String otra) {
		private static final Discapacidad NADA = new Discapacidad(List.of(), null);

		/** Nombres del enum separados por comas, rellenos con espacios hasta {@link #LONGITUD_TIPOS_EN_CLARO}; sin tipos, {@code null}. */
		private String tiposEnClaro() {
			if (tipos.isEmpty()) {
				return null;
			}
			String nombres = tipos.stream().map(Enum::name).collect(Collectors.joining(SEPARADOR_TIPOS));
			return nombres + " ".repeat(LONGITUD_TIPOS_EN_CLARO - nombres.length());
		}

		@Override
		public String toString() {
			return "Discapacidad[sin contenido]";
		}
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
		String otra = texto(datos.discapacidadOtra(), "La discapacidad indicada");
		if (otra == null && elegidos.contains(TipoDiscapacidad.OTRA)) {
			throw new IllegalArgumentException("Indica cuál es la discapacidad.");
		}
		return new Discapacidad(List.copyOf(elegidos), otra);
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
			return Arrays.stream(nombres.stripTrailing().split(SEPARADOR_TIPOS)).map(TipoDiscapacidad::valueOf).toList();
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

	/**
	 * Bloquea la fila del alumno de la escuela activa y decide con lo confirmado, no con la ficha que la sesión pudiera tener
	 * ya cargada (open-in-view). El bloqueo es la primera sentencia de la transacción, así que sus lecturas ya ven lo que
	 * confirmó quien tenía la fila.
	 */
	private Alumno bloquearFichaNoAnonimizada(Long alumnoId) {
		if (alumnoId == null) {
			throw new AlumnoNoEncontradoException("Alumno no encontrado.");
		}
		AlumnoRepository.EstadoSedeAlumno estado = alumnos.bloquearEstadoDeSede(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado."));
		if (estado.getAnonimizadoEn() != null) {
			throw new IllegalStateException(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA);
		}
		return alumnos.releerBloqueado(alumnoId)
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
