package com.taemoi.project.services;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * <p>Reglas de la propuesta (por grupo):
 * <ul>
 * <li>Un único adulto (18 o más): el DNI es suyo; el resto queda sin NIF y con un tutor
 * TUTOR_LEGAL con ese NIF y su nombre, responsable legal si es menor y no tiene responsable.</li>
 * <li>Todos menores: es de un tutor que no es alumno; todos quedan sin NIF y con ese tutor, con el
 * nombre conocido (tutores o responsable legal con ese NIF) o "Pendiente".</li>
 * <li>Si no (varios adultos, o falta alguna fecha de nacimiento): revisión manual.</li>
 * </ul>
 *
 * <p>El DNI completo nunca sale de aquí: respuestas, auditoría y logs llevan solo la versión
 * enmascarada, y la referencia de grupo que viaja al cliente es el NIF cifrado (AES-GCM) ligado a la
 * escuela activa.
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
			grupos.add(new DniCompartidoDTO(referencia(nif), enmascarar(nif),
					fichas.stream().map(DniCompartidoService::miembro).toList(), proponer(nif, fichas)));
		});
		return grupos;
	}

	@Transactional
	public DniCompartidoResultadoDTO aplicar(AplicarDniCompartidoRequest datos) {
		String nif = leerReferencia(datos.grupo());
		String enmascarado = enmascarar(nif);
		LocalDate hoy = LocalDate.now(ZONA);

		Set<Long> pedidos = new LinkedHashSet<>(datos.alumnoIds());
		List<Ficha> fichas = new ArrayList<>();
		for (Long id : pedidos) {
			Alumno alumno = alumnos.buscarPorIdEnOrganizacionActiva(id).orElseThrow(DniCompartidoService::grupoCambiado);
			fichas.add(ficha(alumno, hoy));
		}

		// Decisión: quién es el titular del DNI (o nadie) y con qué nombre se crea el tutor.
		Ficha titular = null;
		String[] nombreTutor;
		Accion accion = datos.accion();
		if (accion == Accion.PROPUESTA) {
			Propuesta propuesta = proponer(nif, fichas);
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
		if (titular != null) {
			nombreTutor = new String[] { titular.alumno().getNombre(), titular.alumno().getApellidos() };
		} else if (datos.nombreTutor() != null && !datos.nombreTutor().isBlank()) {
			nombreTutor = new String[] { datos.nombreTutor().strip(), null };
		} else {
			String[] conocido = nombreConocido(nif, fichas);
			nombreTutor = conocido != null ? conocido : new String[] { NOMBRE_PENDIENTE, null };
		}
		final Long titularId = titular == null ? null : titular.alumno().getId();
		List<Ficha> resto = fichas.stream().filter(f -> !Objects.equals(f.alumno().getId(), titularId)).toList();
		List<Long> sinNif = resto.stream().map(f -> f.alumno().getId()).toList();

		// Grupo actual con ese NIF en la escuela activa.
		Set<Long> actuales = new LinkedHashSet<>();
		for (Alumno a : agruparPorNif().getOrDefault(nif, List.of())) {
			actuales.add(a.getId());
		}

		boolean yaAplicado = actuales.equals(titularId == null ? Set.of() : Set.of(titularId))
				&& resto.stream().allMatch(f -> normalizar(f.alumno().getNif()) == null && tieneTutorConNif(f, nif));
		if (yaAplicado) {
			return new DniCompartidoResultadoDTO(enmascarado, false, titularId, sinNif, 0);
		}
		if (pedidos.size() < 2 || !actuales.equals(pedidos)) {
			throw grupoCambiado();
		}

		int creados = 0;
		for (Ficha f : resto) {
			Alumno alumno = f.alumno();
			alumno.setNif(null);
			alumnos.save(alumno);
			if (!tieneTutorConNif(f, nif)) {
				boolean responsable = esMenor(f) && !tieneResponsable(f);
				tutorService.crear(alumno.getId(), new TutorRequest(nombreTutor[0], nombreTutor[1], nif,
						ParentescoTutor.TUTOR_LEGAL, null, null, responsable, null));
				creados++;
			}
		}

		String resumen = "Corrección de DNI compartido " + enmascarado + " (" + accion + "): "
				+ (titularId == null ? "sin titular alumno" : "titular alumno #" + titularId)
				+ "; sin NIF: " + sinNif.stream().map(id -> "#" + id).collect(Collectors.joining(", "))
				+ "; tutores creados: " + creados;
		auditoria.registrarAccesoSensible(AuditoriaAccion.UPDATE, MODULO_AUDITORIA, ENDPOINT_APLICAR,
				titularId != null ? titularId : sinNif.get(0), resumen);
		logger.info("{}", resumen);
		return new DniCompartidoResultadoDTO(enmascarado, true, titularId, sinNif, creados);
	}

	// ------------------------------------------------------------------ propuesta

	private Propuesta proponer(String nif, List<Ficha> fichas) {
		if (fichas.stream().anyMatch(f -> f.edad() == null)) {
			return revisionManual("Falta la fecha de nacimiento de algún alumno.");
		}
		List<Ficha> adultos = fichas.stream().filter(f -> !esMenor(f)).toList();
		if (adultos.size() == 1) {
			Alumno adulto = adultos.get(0).alumno();
			String nombre = nombreCompleto(adulto.getNombre(), adulto.getApellidos());
			return new Propuesta(TITULAR_ADULTO, "El DNI es de " + nombre + ".", adulto.getId(), nombre,
					cambios(nif, fichas, adulto.getId()));
		}
		if (adultos.isEmpty()) {
			String[] conocido = nombreConocido(nif, fichas);
			String nombre = conocido == null ? NOMBRE_PENDIENTE : nombreCompleto(conocido[0], conocido[1]);
			return new Propuesta(TUTOR_NO_ALUMNO, "DNI de un tutor no alumno.", null, nombre,
					cambios(nif, fichas, null));
		}
		return revisionManual("Revisión manual: varios adultos comparten el DNI.");
	}

	private static Propuesta revisionManual(String descripcion) {
		return new Propuesta(REVISION_MANUAL, descripcion, null, null, List.of());
	}

	private static List<Cambio> cambios(String nif, List<Ficha> fichas, Long titularId) {
		return fichas.stream().map(f -> {
			if (Objects.equals(f.alumno().getId(), titularId)) {
				return new Cambio(f.alumno().getId(), false, false, false);
			}
			boolean crear = !tieneTutorConNif(f, nif);
			return new Cambio(f.alumno().getId(), true, crear, crear && esMenor(f) && !tieneResponsable(f));
		}).toList();
	}

	/**
	 * Nombre del tutor dueño del DNI si ya consta: un tutor con ese NIF, el responsable legal heredado
	 * con ese NIF o, si todos los miembros tienen el mismo responsable legal, ese. {nombre, apellidos}.
	 */
	private static String[] nombreConocido(String nif, List<Ficha> fichas) {
		for (Ficha f : fichas) {
			for (Tutor t : f.tutores()) {
				if (nif.equals(normalizar(t.getNif())) && t.getNombre() != null && !t.getNombre().isBlank()) {
					return new String[] { t.getNombre(), t.getApellidos() };
				}
			}
		}
		for (Ficha f : fichas) {
			Alumno a = f.alumno();
			if (nif.equals(normalizar(a.getResponsableLegalNif())) && a.getResponsableLegalNombre() != null
					&& !a.getResponsableLegalNombre().isBlank()) {
				return new String[] { a.getResponsableLegalNombre().strip(), null };
			}
		}
		Set<String> responsables = new LinkedHashSet<>();
		for (Ficha f : fichas) {
			Tutor primero = f.tutores().stream().filter(Tutor::isResponsableLegal).findFirst().orElse(null);
			if (primero == null || primero.getNombreCompleto() == null) {
				return null;
			}
			responsables.add(primero.getNombreCompleto());
		}
		if (responsables.size() == 1) {
			Tutor t = fichas.get(0).tutores().stream().filter(Tutor::isResponsableLegal).findFirst().orElseThrow();
			return new String[] { t.getNombre(), t.getApellidos() };
		}
		return null;
	}

	// ------------------------------------------------------------------ utilidades

	private Map<String, List<Alumno>> agruparPorNif() {
		Map<String, List<Alumno>> grupos = new LinkedHashMap<>();
		for (Alumno a : alumnos.buscarConNifEnOrganizacionActiva()) {
			String nif = normalizar(a.getNif());
			if (nif != null) {
				grupos.computeIfAbsent(nif, k -> new ArrayList<>()).add(a);
			}
		}
		grupos.values().forEach(l -> l.sort(Comparator.comparing(Alumno::getId)));
		return grupos;
	}

	private Ficha ficha(Alumno alumno, LocalDate hoy) {
		LocalDate nacimiento = fecha(alumno.getFechaNacimiento());
		Integer edad = nacimiento == null ? null : Period.between(nacimiento, hoy).getYears();
		return new Ficha(alumno, nacimiento, edad, tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumno.getId()));
	}

	private static Miembro miembro(Ficha f) {
		Alumno a = f.alumno();
		return new Miembro(a.getId(), a.getNombre(), a.getApellidos(), f.nacimiento(), f.edad(),
				Boolean.TRUE.equals(a.getActivo()),
				f.tutores().stream().map(t -> new TutorResumen(t.getId(), t.getNombre(), t.getApellidos(),
						enmascarar(normalizar(t.getNif())), t.getParentesco(), t.isResponsableLegal())).toList());
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

	private static boolean tieneTutorConNif(Ficha f, String nif) {
		return f.tutores().stream().anyMatch(t -> nif.equals(normalizar(t.getNif())));
	}

	private static String nombreCompleto(String nombre, String apellidos) {
		String n = nombre == null ? "" : nombre.strip();
		String a = apellidos == null ? "" : apellidos.strip();
		return (n + " " + a).strip();
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

	/** Mayúsculas, sin espacios ni guiones; {@code null} si queda vacío o es el marcador "sin NIF". */
	static String normalizar(String nif) {
		String base = NifUtils.normalizeForStorage(nif);
		if (base == null) {
			return null;
		}
		String limpio = base.replaceAll("[\\s-]", "");
		return limpio.isEmpty() || NifUtils.PLACEHOLDER_SIN_NIF.equals(limpio) ? null : limpio;
	}

	/** Solo los 4 últimos caracteres a la vista (p. ej. {@code *****678Z}). */
	static String enmascarar(String nif) {
		if (nif == null) {
			return null;
		}
		int ocultos = Math.max(0, nif.length() - VISIBLES);
		return "*".repeat(nif.length() <= VISIBLES ? nif.length() : ocultos)
				+ (nif.length() <= VISIBLES ? "" : nif.substring(ocultos));
	}

	private String referencia(String nif) {
		return cifrador.cifrar(nif, contextoReferencia());
	}

	private String leerReferencia(String referencia) {
		try {
			String nif = normalizar(cifrador.descifrar(referencia, contextoReferencia()));
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
}
