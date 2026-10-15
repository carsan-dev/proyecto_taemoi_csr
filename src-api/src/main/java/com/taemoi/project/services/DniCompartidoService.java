package com.taemoi.project.services;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.cifrado.DatoCifradoIlegibleException;
import com.taemoi.project.dtos.request.AplicarDniCompartidoRequest;
import com.taemoi.project.dtos.request.AplicarDniCompartidoRequest.Accion;
import com.taemoi.project.dtos.request.TutorRequest;
import com.taemoi.project.dtos.response.DniCompartidoDTO;
import com.taemoi.project.dtos.response.DniCompartidoDTO.Cambio;
import com.taemoi.project.dtos.response.DniCompartidoDTO.Miembro;
import com.taemoi.project.dtos.response.DniCompartidoDTO.Propuesta;
import com.taemoi.project.dtos.response.DniCompartidoDTO.TutorResumen;
import com.taemoi.project.dtos.response.DniCompartidoResultadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.ParentescoTutor;
import com.taemoi.project.entities.Tutor;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.TutorRepository;
import com.taemoi.project.utils.NifUtils;

/**
 * DNIs compartidos por varios alumnos de la escuela activa (normalmente el DNI de un padre puesto en
 * las fichas de sus hijos). {@code alumno.nif} debe guardar solo el DNI del propio alumno; el de un
 * padre va en {@code tutor}. Nada se corrige solo: el informe propone y un administrador aplica.
 *
 * <p>Reglas de la propuesta (por grupo; el NIF se compara normalizado y {@code 00000000Z} no cuenta):
 * <ul>
 * <li>Un único adulto (18 o más, cumplidos hoy incluidos): el DNI es suyo; el resto queda sin NIF y
 * con un tutor TUTOR_LEGAL con ese NIF y su nombre.</li>
 * <li>Todos menores: es de un tutor que no es alumno; todos quedan sin NIF y con ese tutor, con el
 * nombre que ya conste ({@link #nombreConocido}) o "Pendiente".</li>
 * <li>Si no (varios adultos, o falta alguna fecha de nacimiento): revisión manual.</li>
 * </ul>
 * El tutor es responsable legal si el alumno es menor y no tiene ninguno. Si el alumno ya tiene un
 * tutor con ese NIF no se toca; si tiene uno SIN NIF con el mismo nombre (sin distinguir mayúsculas,
 * acentos ni espacios) se le pone el NIF en lugar de crear otro.
 *
 * <p>El responsable legal del que solo se conoce el NIF (tutor sin nombre, ver {@code TutorService})
 * no cuenta como tutor en ninguna de estas reglas: el informe y la corrección se comportan como cuando
 * ese NIF solo estaba en la ficha del alumno. Si la corrección crea un responsable legal, el marcador
 * desaparece ({@code TutorService.crear/asignarNif}).
 *
 * <p>El DNI completo solo sale en el cuerpo de la respuesta del informe (administradores, sin caché):
 * logs, auditoría, mensajes de error y la respuesta de la corrección llevan la versión enmascarada, y
 * la referencia de grupo que viaja del cliente es el NIF cifrado (AES-GCM) ligado a la escuela activa.
 */
@Service
public class DniCompartidoService {

	static final String MODULO_AUDITORIA = "dni-compartidos";
	static final String ENDPOINT_APLICAR = "/api/admin/dni-compartidos/aplicar";
	static final String TITULAR_ADULTO = "TITULAR_ADULTO";
	static final String TUTOR_NO_ALUMNO = "TUTOR_NO_ALUMNO";
	static final String REVISION_MANUAL = "REVISION_MANUAL";
	static final String NOMBRE_PENDIENTE = "Pendiente";
	private static final int MAYORIA_EDAD = 18;
	private static final int VISIBLES = 4;
	private static final int LONGITUD_NIF = 9;
	private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
	private static final Logger logger = LoggerFactory.getLogger(DniCompartidoService.class);

	private final AlumnoRepository alumnos;
	private final TutorRepository tutores;
	private final TutorService tutorService;
	private final AuditoriaService auditoria;
	private final CifradorDatos cifrador;

	public DniCompartidoService(AlumnoRepository alumnos, TutorRepository tutores, TutorService tutorService,
			AuditoriaService auditoria, CifradorDatos cifrador) {
		this.alumnos = alumnos;
		this.tutores = tutores;
		this.tutorService = tutorService;
		this.auditoria = auditoria;
		this.cifrador = cifrador;
	}

	@Transactional(readOnly = true)
	public List<DniCompartidoDTO> listar() {
		LocalDate hoy = LocalDate.now(ZONA);
		List<DniCompartidoDTO> grupos = new ArrayList<>();
		agruparPorNif().forEach((nif, miembros) -> {
			if (miembros.size() < 2) {
				return;
			}
			List<Ficha> fichas = miembros.stream().map(a -> ficha(a, hoy)).toList();
			grupos.add(new DniCompartidoDTO(referencia(nif), nif,
					fichas.stream().map(DniCompartidoService::miembro).toList(), proponer(nif, fichas)));
		});
		return grupos;
	}

	@Transactional
	public DniCompartidoResultadoDTO aplicar(AplicarDniCompartidoRequest datos) {
		String nif = leerReferencia(datos.grupo());
		String enmascarado = enmascarar(nif);
		LocalDate hoy = LocalDate.now(ZONA);

		// 1. Bloqueo de los miembros (orden de id ascendente) antes de leer nada más: dos peticiones
		// simultáneas sobre el mismo grupo se serializan y la segunda ve lo que hizo la primera.
		Set<Long> pedidos = new TreeSet<>(datos.alumnoIds());
		List<Alumno> bloqueados = alumnos.bloquearPorIdsEnOrganizacionActiva(pedidos);
		if (bloqueados.size() != pedidos.size()) {
			throw grupoCambiado();
		}
		List<Ficha> fichas = bloqueados.stream().map(a -> ficha(a, hoy)).toList();

		// 2. Pertenencia: o es el grupo actual (pendiente) o es exactamente el grupo ya corregido con este
		// NIF (los que lo conservan más los que, sin NIF propio, lo tienen en un tutor).
		Set<Long> actuales = new HashSet<>();
		agruparPorNif().getOrDefault(nif, List.of()).forEach(a -> actuales.add(a.getId()));
		boolean pendiente = pedidos.size() >= 2 && actuales.equals(pedidos);
		boolean corregido = false;
		if (!pendiente) {
			Set<Long> huella = new HashSet<>(actuales);
			huella.addAll(alumnosConTutorDeNif(nif));
			corregido = huella.equals(pedidos) && fichas.stream()
					.filter(f -> !actuales.contains(f.alumno().getId()))
					.allMatch(f -> NifUtils.normalizeForComparison(f.alumno().getNif()) == null);
		}
		if (!pendiente && !corregido) {
			throw grupoCambiado();
		}

		// 3. Decisión: quién es el titular del DNI (o nadie).
		Ficha titular = null;
		Accion accion = datos.accion();
		if (accion == Accion.PROPUESTA) {
			if (datos.tipo() == null || datos.tipo().isBlank()) {
				throw new IllegalArgumentException("Falta el tipo de la propuesta que se acepta.");
			}
			Propuesta propuesta = proponer(nif, fichas);
			if (!propuesta.tipo().equals(datos.tipo()) || !Objects.equals(propuesta.titularId(), datos.titularId())) {
				throw new IllegalStateException(
						"La propuesta ha cambiado desde que se mostró el informe. Vuelve a cargarlo antes de aplicar.");
			}
			if (REVISION_MANUAL.equals(propuesta.tipo())) {
				throw new IllegalArgumentException(
						"Este grupo no tiene propuesta automática: elige quién es el titular del DNI.");
			}
			titular = propuesta.titularId() == null ? null : buscar(fichas, propuesta.titularId());
		} else if (accion == Accion.TITULAR) {
			if (datos.titularId() == null || !pedidos.contains(datos.titularId())) {
				throw new IllegalArgumentException("El titular del DNI debe ser uno de los alumnos del grupo.");
			}
			titular = buscar(fichas, datos.titularId());
		}
		final Long titularId = titular == null ? null : titular.alumno().getId();
		List<Ficha> resto = fichas.stream().filter(f -> !Objects.equals(f.alumno().getId(), titularId)).toList();
		List<Long> sinNif = resto.stream().map(f -> f.alumno().getId()).toList();

		// 4. Ya corregido: solo si coincide con esta misma decisión; si no, el grupo cambió.
		if (corregido) {
			if (!actuales.equals(titularId == null ? Set.of() : Set.of(titularId))
					|| !resto.stream().allMatch(f -> tutorConNif(f, nif) != null)) {
				throw grupoCambiado();
			}
			return new DniCompartidoResultadoDTO(enmascarado, false, titularId, sinNif, 0, 0);
		}

		// 5. Aplicar.
		Nombre nombreTutor;
		if (titular != null) {
			nombreTutor = new Nombre(titular.alumno().getNombre(), titular.alumno().getApellidos());
		} else if (datos.nombreTutor() != null && !datos.nombreTutor().isBlank()) {
			nombreTutor = new Nombre(datos.nombreTutor().strip(), null);
		} else {
			Nombre conocido = nombreConocido(nif, fichas);
			nombreTutor = conocido != null ? conocido : new Nombre(NOMBRE_PENDIENTE, null);
		}
		if (titular != null && nif.length() == LONGITUD_NIF && !nif.equals(titular.alumno().getNif())) {
			titular.alumno().setNif(nif);
			alumnos.save(titular.alumno());
		}
		int creados = 0;
		int completados = 0;
		for (Ficha f : resto) {
			Alumno alumno = f.alumno();
			alumno.setNif(null);
			alumnos.save(alumno);
			Cambio cambio = cambio(f, nif, nombreTutor);
			if (cambio.tutorExistenteId() != null) {
				tutorService.asignarNif(alumno.getId(), cambio.tutorExistenteId(), nif, cambio.responsableLegal());
				completados++;
			} else if (cambio.crearTutor()) {
				tutorService.crear(alumno.getId(), new TutorRequest(nombreTutor.nombre(), nombreTutor.apellidos(), nif,
						ParentescoTutor.TUTOR_LEGAL, null, null, cambio.responsableLegal(), null));
				creados++;
			}
		}

		String resumen = "Corrección de DNI compartido " + enmascarado + " (" + accion + "): "
				+ (titularId == null ? "sin titular alumno" : "titular alumno #" + titularId)
				+ "; sin NIF: " + sinNif.stream().map(id -> "#" + id).collect(Collectors.joining(", "))
				+ "; tutores creados: " + creados + "; tutores completados: " + completados;
		auditoria.registrarAccesoSensible(AuditoriaAccion.UPDATE, MODULO_AUDITORIA, ENDPOINT_APLICAR,
				titularId != null ? titularId : sinNif.get(0), resumen);
		logger.info("{}", resumen);
		return new DniCompartidoResultadoDTO(enmascarado, true, titularId, sinNif, creados, completados);
	}

	// ------------------------------------------------------------------ propuesta

	private Propuesta proponer(String nif, List<Ficha> fichas) {
		if (fichas.stream().anyMatch(f -> f.edad() == null)) {
			return revisionManual("Falta la fecha de nacimiento de algún alumno.");
		}
		List<Ficha> adultos = fichas.stream().filter(f -> !esMenor(f)).toList();
		if (adultos.size() == 1) {
			Alumno adulto = adultos.get(0).alumno();
			Nombre nombre = new Nombre(adulto.getNombre(), adulto.getApellidos());
			return new Propuesta(TITULAR_ADULTO, "El DNI es de " + nombre.completo() + ".", adulto.getId(),
					nombre.completo(), cambios(nif, fichas, adulto.getId(), nombre));
		}
		if (adultos.isEmpty()) {
			Nombre conocido = nombreConocido(nif, fichas);
			Nombre nombre = conocido == null ? new Nombre(NOMBRE_PENDIENTE, null) : conocido;
			return new Propuesta(TUTOR_NO_ALUMNO, "DNI de un tutor no alumno.", null, nombre.completo(),
					cambios(nif, fichas, null, nombre));
		}
		return revisionManual("Revisión manual: varios adultos comparten el DNI.");
	}

	private static Propuesta revisionManual(String descripcion) {
		return new Propuesta(REVISION_MANUAL, descripcion, null, null, List.of());
	}

	private static List<Cambio> cambios(String nif, List<Ficha> fichas, Long titularId, Nombre nombreTutor) {
		return fichas.stream().map(f -> Objects.equals(f.alumno().getId(), titularId)
				? new Cambio(f.alumno().getId(), false, false, null, false)
				: cambio(f, nif, nombreTutor)).toList();
	}

	/** Qué pasa con el tutor de un miembro que pierde el NIF (nunca duplica un par nombre + NIF). */
	private static Cambio cambio(Ficha f, String nif, Nombre nombreTutor) {
		Long id = f.alumno().getId();
		if (tutorConNif(f, nif) != null) {
			return new Cambio(id, true, false, null, false);
		}
		String buscado = clave(nombreTutor.completo());
		Tutor homonimo = f.tutores().stream()
				.filter(t -> NifUtils.normalizeForComparison(t.getNif()) == null)
				.filter(t -> !buscado.isEmpty() && buscado.equals(clave(t.getNombreCompleto())))
				.findFirst().orElse(null);
		boolean responsable = esMenor(f) && !tieneResponsable(f);
		return homonimo != null
				? new Cambio(id, true, false, homonimo.getId(), responsable)
				: new Cambio(id, true, true, null, responsable);
	}

	/**
	 * Nombre del tutor dueño del DNI si ya consta, mirando solo tutores sin NIF o con ese NIF (uno con
	 * otro NIF es otra persona): un tutor con nombre y ese NIF (el primer responsable legal con ese NIF
	 * también lo es); o, si todos los miembros tienen como primer responsable legal a la misma persona
	 * sin NIF o con ese NIF, esa. Si no, {@code null}.
	 */
	private static Nombre nombreConocido(String nif, List<Ficha> fichas) {
		for (Ficha f : fichas) {
			Tutor t = tutorConNif(f, nif);
			if (t != null && t.getNombreCompleto() != null) {
				return new Nombre(t.getNombre(), t.getApellidos());
			}
		}
		Set<String> claves = new LinkedHashSet<>();
		Tutor comun = null;
		for (Ficha f : fichas) {
			Tutor primero = f.tutores().stream().filter(Tutor::isResponsableLegal).findFirst().orElse(null);
			if (primero == null || primero.getNombreCompleto() == null) {
				return null;
			}
			String suNif = NifUtils.normalizeForComparison(primero.getNif());
			if (suNif != null && !suNif.equals(nif)) {
				return null;
			}
			claves.add(clave(primero.getNombreCompleto()));
			if (comun == null) {
				comun = primero;
			}
		}
		return claves.size() == 1 && comun != null ? new Nombre(comun.getNombre(), comun.getApellidos()) : null;
	}

	// ------------------------------------------------------------------ utilidades

	private Map<String, List<Alumno>> agruparPorNif() {
		Map<String, List<Alumno>> grupos = new LinkedHashMap<>();
		for (Alumno a : alumnos.buscarConNifEnOrganizacionActiva()) {
			String nif = NifUtils.normalizeForComparison(a.getNif());
			if (nif != null) {
				grupos.computeIfAbsent(nif, k -> new ArrayList<>()).add(a);
			}
		}
		grupos.values().forEach(l -> l.sort(Comparator.comparing(Alumno::getId)));
		return grupos;
	}

	private Set<Long> alumnosConTutorDeNif(String nif) {
		Set<Long> ids = new HashSet<>();
		for (Object[] fila : tutores.buscarAlumnoYNifConNif()) {
			if (nif.equals(NifUtils.normalizeForComparison((String) fila[1]))) {
				ids.add((Long) fila[0]);
			}
		}
		return ids;
	}

	private Ficha ficha(Alumno alumno, LocalDate hoy) {
		LocalDate nacimiento = fecha(alumno.getFechaNacimiento());
		Integer edad = nacimiento == null ? null : Period.between(nacimiento, hoy).getYears();
		// El responsable legal solo-NIF (tutor sin nombre) no cuenta: ver la cabecera de la clase.
		List<Tutor> conNombre = tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumno.getId()).stream()
				.filter(Tutor::tieneNombre).toList();
		return new Ficha(alumno, nacimiento, edad, conNombre);
	}

	private static Miembro miembro(Ficha f) {
		Alumno a = f.alumno();
		return new Miembro(a.getId(), a.getNombre(), a.getApellidos(), f.nacimiento(), f.edad(),
				Boolean.TRUE.equals(a.getActivo()),
				f.tutores().stream().map(t -> new TutorResumen(t.getId(), t.getNombre(), t.getApellidos(),
						NifUtils.normalizeForComparison(t.getNif()), t.getParentesco(),
						t.isResponsableLegal())).toList());
	}

	private static Ficha buscar(List<Ficha> fichas, Long id) {
		return fichas.stream().filter(f -> Objects.equals(f.alumno().getId(), id)).findFirst().orElseThrow();
	}

	private static boolean esMenor(Ficha f) {
		return f.edad() != null && f.edad() < MAYORIA_EDAD;
	}

	private static boolean tieneResponsable(Ficha f) {
		return f.tutores().stream().anyMatch(Tutor::isResponsableLegal);
	}

	private static Tutor tutorConNif(Ficha f, String nif) {
		return f.tutores().stream().filter(t -> nif.equals(NifUtils.normalizeForComparison(t.getNif())))
				.findFirst().orElse(null);
	}

	/** Nombre para comparar: sin acentos, minúsculas y espacios simples. */
	private static String clave(String nombre) {
		if (nombre == null) {
			return "";
		}
		String sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinAcentos.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static LocalDate fecha(java.util.Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date sql) {
			return sql.toLocalDate();
		}
		return fecha.toInstant().atZone(ZONA).toLocalDate();
	}

	/** Solo los 4 últimos caracteres a la vista (p. ej. {@code *****678Z}). */
	static String enmascarar(String nif) {
		if (nif == null) {
			return null;
		}
		if (nif.length() <= VISIBLES) {
			return "*".repeat(nif.length());
		}
		int ocultos = nif.length() - VISIBLES;
		return "*".repeat(ocultos) + nif.substring(ocultos);
	}

	private String referencia(String nif) {
		return cifrador.cifrar(nif, contextoReferencia());
	}

	private String leerReferencia(String referencia) {
		try {
			String nif = NifUtils.normalizeForComparison(cifrador.descifrar(referencia, contextoReferencia()));
			if (nif == null) {
				throw new IllegalArgumentException("La referencia del grupo no es válida.");
			}
			return nif;
		} catch (DatoCifradoIlegibleException e) {
			throw new IllegalArgumentException("La referencia del grupo no es válida.");
		}
	}

	/** Ligada a la escuela activa: la referencia de otra escuela no se puede descifrar aquí. */
	private static String contextoReferencia() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new IllegalStateException("No hay escuela activa.");
		}
		return CifradorDatos.contexto("alumno", "nif-compartido", organizacion);
	}

	private static IllegalStateException grupoCambiado() {
		return new IllegalStateException(
				"El grupo ha cambiado desde que se mostró el informe. Vuelve a cargarlo antes de aplicar.");
	}

	private record Ficha(Alumno alumno, LocalDate nacimiento, Integer edad, List<Tutor> tutores) {
	}

	private record Nombre(String nombre, String apellidos) {
		String completo() {
			String n = nombre == null ? "" : nombre.strip();
			String a = apellidos == null ? "" : apellidos.strip();
			return (n + " " + a).strip();
		}
	}
}
