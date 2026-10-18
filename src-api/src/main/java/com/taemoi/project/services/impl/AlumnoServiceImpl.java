package com.taemoi.project.services.impl;


import com.taemoi.project.services.CatalogoLegadoTaemoi;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.request.AlumnoDeporteCreacionDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralItemResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralMiPosicionResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingItemResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingMiPosicionResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.dtos.response.RetoDiarioEstadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AlumnoRetoDiarioLog;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.alumno.AlumnoDuplicadoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.alumno.DatosAlumnoInvalidosException;
import com.taemoi.project.repositories.AlumnoSaludRepository;
import com.taemoi.project.repositories.ConsentimientoRepository;
import com.taemoi.project.repositories.TutorRepository;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRetoDiarioLogRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.exceptions.usuario.UsuarioCompartidoException;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.TutorService;
import com.taemoi.project.services.ConsentimientoService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.utils.EmailUtils;
import com.taemoi.project.utils.MensualidadUtils;
import com.taemoi.project.utils.NifUtils;
import com.taemoi.project.utils.TelefonoUtils;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación del servicio para operaciones relacionadas con los alumnos.
 */
@Service
public class AlumnoServiceImpl implements AlumnoService {

	private static final Logger logger = LoggerFactory.getLogger(AlumnoServiceImpl.class);
	private static final ZoneId RETO_DIARIO_ZONE_ID = ZoneId.of("Europe/Madrid");
	/** Nombres de producto de rojo que buscaba el código hasta V27 (respaldo si falta el catálogo). */
	private static final String DERECHO_EXAMEN_ROJO = "DERECHO A EXAMEN ROJO";
	private static final String DERECHO_EXAMEN_ROJO_BORDADO = "DERECHO A EXAMEN ROJO BORDADO";
	private static final String DERECHO_RECOMPENSA_ROJO = "DERECHO DE CAMBIO A ROJO POR RECOMPENSA";
	private static final String DERECHO_RECOMPENSA_ROJO_BORDADO = "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA";

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	private AlumnoRepository alumnoRepository;

	/**
	 * Inyección del repositorio de categoría.
	 */
	@Autowired
	private CategoriaRepository categoriaRepository;

	/**
	 * Inyección del repositorio de grado.
	 */
	@Autowired
	private GradoRepository gradoRepository;

	/**
	 * Inyección del repositorio de imagen.
	 */
	@Autowired
	private ImagenRepository imagenRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MembresiaService membresiaService;

	@Autowired
	private TutorService tutorService;

	@Autowired
	private ConsentimientoService consentimientoService;

	@Autowired
	private ConsentimientoRepository consentimientoRepository;

	@Autowired
	private TutorRepository tutorRepository;

	@Autowired
	private AlumnoSaludRepository alumnoSaludRepository;

	@Autowired
	private TurnoRepository turnoRepository;

	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	/** Pieza 6b-1: los cobros activos de los cargos que se borran con el alumno o con su convocatoria se anulan. */
	@Autowired
	private com.taemoi.project.services.CobroService cobros;

	@Autowired
	private ImagenService imagenService;
	
	@Autowired
	private DocumentoRepository documentoRepository;
	
	@Autowired
	private DocumentoService documentoService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private com.taemoi.project.services.CatalogoDisciplinasService catalogoDisciplinas;

	@Autowired
	private com.taemoi.project.services.ProgresionGradosService progresionGrados;

	@Autowired
	private com.taemoi.project.services.TarifasService tarifasService;

	@Autowired
	private com.taemoi.project.services.ProductosCatalogoService productosCatalogo;

	@Autowired
	private com.taemoi.project.services.AlumnoDeporteService alumnoDeporteService;

	@Autowired
	private com.taemoi.project.repositories.AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private AlumnoRetoDiarioLogRepository alumnoRetoDiarioLogRepository;

	@Autowired
	private com.taemoi.project.repositories.PreinscripcionRepository preinscripcionRepository;

	@Autowired
	private com.taemoi.project.repositories.RegistroPendienteRepository registroPendienteRepository;

	@Autowired
	private com.taemoi.project.services.ContadorService contadorService;

	@Autowired
	private com.taemoi.project.services.CatalogoCategoriasService catalogoCategorias;

	@Autowired
	private com.taemoi.project.services.ComportamientoLegadoService comportamientoLegado;

	/** Entrega 6: filtros y comprobaciones por sede del responsable (sin efecto para la gestión). */
	@Autowired
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	/** Entrega 6-4: tarifa de la sede en la mensualidad que crea el alta (D6-27 a D6-29). */
	@Autowired
	private com.taemoi.project.services.TarifaSedeService tarifaSede;

	/**
	 * Obtiene una página de todos los alumnos paginados.
	 *
	 * @param pageable Objeto Pageable para la paginación de resultados.
	 * @return Una página de objetos Alumno.
	 */
	@Override
	public Page<Alumno> obtenerTodosLosAlumnos(@NonNull Pageable pageable) {
		return alumnoRepository.findAll(pageable);
	}

	/**
	 * Obtiene una lista de todos los alumnos.
	 *
	 * @return Una lista de objetos Alumno.
	 */
	@Override
	public List<Alumno> obtenerTodosLosAlumnos() {
		return alumnoRepository.findAll();
	}

	/**
	 * Obtiene un alumno por su ID.
	 *
	 * @param id El ID del alumno a buscar.
	 * @return Un objeto Optional que contiene el alumno si se encuentra, de lo
	 *         contrario, un Optional vacío.
	 */
	@Override
	public Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id) {
		return alumnoRepository.findById(id);
	}

	/**
	 * Obtiene un alumno DTO por su ID.
	 *
	 * @param id El ID del alumno a buscar.
	 * @return Un objeto Optional que contiene el alumno DTO si se encuentra, de lo
	 *         contrario, un Optional vacío.
	 */
	@Override
	public Optional<AlumnoDTO> obtenerAlumnoPorIdDTO(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = obtenerAlumnoPorId(id);
		return optionalAlumno.map(AlumnoDTO::deAlumno);
	}
	
	@Override
	public List<Documento> obtenerDocumentosAlumno(Long alumnoId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId)
	            .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));
	    return documentoRepository.findByAlumnoIdOrderByNombreAsc(alumno.getId());
	}

	/**
	 * Obtiene una página de alumnos filtrados según los parámetros especificados.
	 *
	 * @param nombre      El nombre a filtrar.
	 * @param gradoId     El ID del grado a filtrar.
	 * @param categoriaId El ID de la categoría a filtrar.
	 * @param pageable    Objeto Pageable para la paginación de resultados.
	 * @return Una página de objetos Alumno que cumplen con los criterios de
	 *         búsqueda.
	 * @throws IllegalArgumentException Si no se proporciona al menos un criterio de
	 *                                  filtrado.
	 */
	@Override
	public Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			@NonNull Pageable pageable) {
		return obtenerAlumnosFiltrados(nombre, gradoId, categoriaId, incluirInactivos, false, pageable);
	}

	@Override
	public Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			boolean aptoParaExamen, @NonNull Pageable pageable) {
		org.springframework.data.jpa.domain.Specification<Alumno> filtro =
				buildAlumnoSpecification(nombre, gradoId, categoriaId, incluirInactivos, aptoParaExamen);
		java.util.Optional<com.taemoi.project.organizacion.AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isPresent()) {
			// Responsable de sede (entrega 6-1c): D9 por sus sedes.
			filtro = filtro.and(com.taemoi.project.acceso.PredicadosAlcance.alumnosDeSusSedes(restriccion.get()));
		}
		// Sin paginar, por id, como GET /api/alumnos/ids (también con gestión): sin ORDER BY, MySQL devolvía el orden
		// del índice que eligiera, y con estadísticas distintas elige uk (organizacion_id, numero_expediente).
		if (pageable.isUnpaged()) {
			return new org.springframework.data.domain.PageImpl<>(
					alumnoRepository.findAll(filtro, org.springframework.data.domain.Sort.by("id")));
		}
		return alumnoRepository.findAll(filtro, pageable);
	}

	/**
	 * Obtiene una lista de alumnos filtrados según los parámetros especificados.
	 *
	 * @param nombre      El nombre a filtrar.
	 * @param gradoId     El ID del grado a filtrar.
	 * @param categoriaId El ID de la categoría a filtrar.
	 * @return Una lista de objetos Alumno que cumplen con los criterios de
	 *         búsqueda.
	 * @throws IllegalArgumentException Si no se proporciona al menos un criterio de
	 *                                  filtrado.
	 */
	@Override
	public List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId,
			boolean incluirInactivos) {
		org.springframework.data.jpa.domain.Specification<Alumno> filtro =
				buildAlumnoSpecification(nombre, gradoId, categoriaId, incluirInactivos, false);
		java.util.Optional<com.taemoi.project.organizacion.AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isEmpty()) {
			return alumnoRepository.findAll(filtro);
		}
		return alumnoRepository.findAll(
				filtro.and(com.taemoi.project.acceso.PredicadosAlcance.alumnosDeSusSedes(restriccion.get())),
				org.springframework.data.domain.Sort.by("id"));
	}

	@Override
	public Page<Alumno> obtenerAlumnosPorDeporte(String deporte, String nombre, Long gradoId, Long categoriaId,
			boolean incluirInactivos, boolean aptoParaExamen, @NonNull Pageable pageable) {
		// El nombre no va al filtro común (que busca también en «nombre apellidos» y sin distinguir tildes).
		org.springframework.data.jpa.domain.Specification<Alumno> filtro =
				buildAlumnoSpecification(null, gradoId, categoriaId, incluirInactivos, aptoParaExamen)
						.and(filtroDeporteComoElNavegador(deporte, nombre, incluirInactivos));
		java.util.Optional<com.taemoi.project.organizacion.AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isPresent()) {
			filtro = filtro.and(com.taemoi.project.acceso.PredicadosAlcance.alumnosDeSusSedes(restriccion.get()));
		}
		return alumnoRepository.findAll(filtro, pageable);
	}

	/**
	 * Criterio del navegador en el filtro por deporte (R2-P6, ver {@link AlumnoService#obtenerAlumnosPorDeporte}).
	 * La ficha activa sin inactivos ya la pide el filtro común; aquí, el resto.
	 */
	private static org.springframework.data.jpa.domain.Specification<Alumno> filtroDeporteComoElNavegador(
			String deporte, String nombre, boolean incluirInactivos) {
		// JavaScript trim(): también el espacio duro y el BOM, que String.strip() no quita.
		String texto = nombre == null ? "" : nombre.replaceAll("^[\\s\\u00A0\\uFEFF\\p{Z}]+|[\\s\\u00A0\\uFEFF\\p{Z}]+$", "")
				.toLowerCase(java.util.Locale.ROOT);
		// Comparaciones binarias con utf8mb4_bin/minusculas_utf8mb4_bin (FuncionesSqlContributor): convierten a utf8mb4
		// antes de la colación, así no dependen del juego de caracteres de la columna.
		return (root, query, cb) -> {
			org.hibernate.query.criteria.HibernateCriteriaBuilder hcb =
					(org.hibernate.query.criteria.HibernateCriteriaBuilder) cb;
			List<Predicate> predicados = new ArrayList<>();
			// Tiene el deporte, activo o no (practicaDeporte del navegador; código exacto, como su ===). La igualdad
			// normal va delante para que la consulta pueda usar el índice del código; la binaria afina mayúsculas.
			jakarta.persistence.criteria.Subquery<Long> conDeporte = query.subquery(Long.class);
			jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> ad =
					conDeporte.from(com.taemoi.project.entities.AlumnoDeporte.class);
			jakarta.persistence.criteria.Path<String> codigo = ad.get("disciplina").get("codigo");
			conDeporte.select(ad.get("id")).where(cb.equal(ad.get("alumno"), root), cb.equal(codigo, deporte),
					cb.equal(cb.function(com.taemoi.project.config.FuncionesSqlContributor.UTF8MB4_BIN, String.class,
							codigo), deporte));
			predicados.add(cb.exists(conDeporte));
			if (!incluirInactivos) {
				// isAlumnoActivo del navegador: al menos un deporte activo (cualquiera).
				jakarta.persistence.criteria.Subquery<Long> conActivo = query.subquery(Long.class);
				jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> activo =
						conActivo.from(com.taemoi.project.entities.AlumnoDeporte.class);
				conActivo.select(activo.get("id")).where(cb.equal(activo.get("alumno"), root),
						cb.equal(activo.get("activo"), true));
				predicados.add(cb.exists(conActivo));
			}
			if (!texto.isEmpty()) {
				// includes() del navegador: binario tras pasar a minúsculas (distingue tildes) y sin comodines (LOCATE).
				String minusculas = com.taemoi.project.config.FuncionesSqlContributor.MINUSCULAS_UTF8MB4_BIN;
				predicados.add(cb.or(
						cb.greaterThan(hcb.locate(cb.function(minusculas, String.class, root.get("nombre")), texto), 0),
						cb.greaterThan(hcb.locate(cb.function(minusculas, String.class, root.get("apellidos")), texto),
								0)));
			}
			return cb.and(predicados.toArray(new Predicate[0]));
		};
	}

	/**
	 * Ids para la navegación de editar-alumno: mismo filtro que el listado sin paginar, sin cargar las fichas. Para el
	 * responsable de sede, solo los de su alcance (D9), por id.
	 */
	@Override
	public List<Long> obtenerIdsAlumnos(boolean incluirInactivos) {
		java.util.Optional<com.taemoi.project.organizacion.AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isEmpty()) {
			return alumnoRepository.buscarIdsEnOrganizacionActiva(incluirInactivos);
		}
		return alumnoRepository.buscarIds(
				com.taemoi.project.acceso.PredicadosAlcance.alumnosDeSusSedes(restriccion.get()), incluirInactivos);
	}

	@Override
	@Transactional
	public Alumno crearAlumnoDesdeDTO(@NonNull AlumnoDTO nuevoAlumnoDTO) {
		// D6-2: sede principal del alta. Responsable: obligatoria y suya (403 si no; automática si tiene una).
		// Gestión: la pedida o, si no, la única sede activa (con varias, NULL como hasta ahora).
		Long sedePrincipal;
		try {
			// D6-18: tampoco en una sede inactiva (400), ni la gestión ni el responsable.
			sedePrincipal = gestionPorSede.sedeDeAlta(nuevoAlumnoDTO.getSedePrincipalId(), true);
		} catch (IllegalArgumentException e) {
			throw new DatosAlumnoInvalidosException(e.getMessage());
		}
		String nifNormalizado = NifUtils.normalizeForStorage(nuevoAlumnoDTO.getNif());

		// Verificar si el Alumno ya existe
		if (nifNormalizado != null && alumnoRepository.existsByNif(nifNormalizado)) {
			throw new AlumnoDuplicadoException("El alumno con NIF " + nifNormalizado + " ya existe.");
		}

		// Todo alumno nace con al menos un deporte (AlumnoDeporte). Si el cliente envía los
		// campos de deporte único en el nivel superior, se traducen al deporte principal.
		boolean desdeCamposDeporteUnico = nuevoAlumnoDTO.getDeportesInicial() == null
				|| nuevoAlumnoDTO.getDeportesInicial().isEmpty();
		List<AlumnoDeporteCreacionDTO> deportesIniciales = desdeCamposDeporteUnico
				? List.of(mapearDeporteUnicoADeporteInicial(nuevoAlumnoDTO))
				: nuevoAlumnoDTO.getDeportesInicial();
		// Se valida todo antes de guardar nada: los errores de datos son un 400, no un 500.
		validarDeportesIniciales(deportesIniciales);

		// Crear nuevo Alumno
		Alumno nuevoAlumno = new Alumno();
		nuevoAlumno.setNombre(nuevoAlumnoDTO.getNombre());
		nuevoAlumno.setApellidos(nuevoAlumnoDTO.getApellidos());
		nuevoAlumno.setFechaNacimiento(nuevoAlumnoDTO.getFechaNacimiento());
		nuevoAlumno.setNif(nifNormalizado);
		nuevoAlumno.setDireccion(nuevoAlumnoDTO.getDireccion());
		String normalizedEmail = EmailUtils.normalizeEmail(nuevoAlumnoDTO.getEmail());
		nuevoAlumno.setEmail(normalizedEmail);
		// El teléfono es obligatorio en el alta (la columna admite NULL por las fichas en papel).
		if (TelefonoUtils.normalizar(nuevoAlumnoDTO.getTelefono()) == null) {
			throw new DatosAlumnoInvalidosException("El teléfono es obligatorio.");
		}
		nuevoAlumno.setTelefono(TelefonoUtils.normalizar(nuevoAlumnoDTO.getTelefono()));
		nuevoAlumno.setTelefono2(TelefonoUtils.normalizar(nuevoAlumnoDTO.getTelefono2()));
		nuevoAlumno.setGenero(nuevoAlumnoDTO.getGenero());
		nuevoAlumno.setLocalidad(textoOpcional(nuevoAlumnoDTO.getLocalidad()));
		nuevoAlumno.setCodigoPostal(textoOpcional(nuevoAlumnoDTO.getCodigoPostal()));
		nuevoAlumno.setObservaciones(nuevoAlumnoDTO.getObservaciones());
		nuevoAlumno.setTieneDiscapacidad(Optional.ofNullable(nuevoAlumnoDTO.getTieneDiscapacidad()).orElse(false));

		// Asignar AutorizacionWeb, si no está definida por defecto a true
		nuevoAlumno.setAutorizacionWeb(
				nuevoAlumnoDTO.getAutorizacionWeb() != null ? nuevoAlumnoDTO.getAutorizacionWeb() : true);

		// Asignar imagen si se proporcionó
		if (nuevoAlumnoDTO.getFotoAlumno() != null) {
			// Guardar la imagen antes de asignarla al alumno
			Imagen imagenGuardada = imagenRepository.save(nuevoAlumnoDTO.getFotoAlumno());
			nuevoAlumno.setFotoAlumno(imagenGuardada);
		}

		// Asignar fecha de alta
		Date fechaAlta = nuevoAlumnoDTO.getFechaAlta() != null ? nuevoAlumnoDTO.getFechaAlta() : new Date();
		nuevoAlumno.setFechaAlta(fechaAlta);

		// Asignar fecha de alta inicial (solo se establece una vez en la creación)
		nuevoAlumno.setFechaAltaInicial(nuevoAlumnoDTO.getFechaAltaInicial() != null ? nuevoAlumnoDTO.getFechaAltaInicial() : fechaAlta);

		// Número de expediente de la organización activa (contador con bloqueo, misma transacción)
		nuevoAlumno.setNumeroExpediente(contadorService.siguienteNumeroExpediente());
		nuevoAlumno.setSedePrincipalId(sedePrincipal);

		// Guardar primero el Alumno
		Alumno alumnoGuardado = alumnoRepository.save(nuevoAlumno);
		// El responsable legal de la ficha vive en tutor (vacío = sin responsable).
		tutorService.aplicarResponsableLegal(alumnoGuardado, TutorService.CambioResponsableLegal.fijar(
				nuevoAlumnoDTO.getResponsableLegalNombre(), nuevoAlumnoDTO.getResponsableLegalNif()));
		// La autorización de imagen de la ficha queda como consentimiento (origen APP, firmado por quien da el alta).
		consentimientoService.registrarCambioAutorizacionWeb(alumnoGuardado,
				Boolean.TRUE.equals(alumnoGuardado.getAutorizacionWeb()));

		logger.info("Creando alumno con {} deporte(s){}", deportesIniciales.size(),
				desdeCamposDeporteUnico ? " (campos de deporte único)" : "");

		AlumnoDeporte primerDeporte = null;
		for (AlumnoDeporteCreacionDTO deporteDTO : deportesIniciales) {
			// Código de grado ya validado (validarDeportesIniciales); se resuelve en el sistema de la disciplina.
			String gradoTipo = null;
			if (deporteDTO.getGrado() != null && !deporteDTO.getGrado().isEmpty()) {
				gradoTipo = deporteDTO.getGrado();
			}

			Date fechaAltaDeporte = deporteDTO.getFechaAlta() != null ? deporteDTO.getFechaAlta() : new Date();
			Date fechaAltaInicialDeporte = deporteDTO.getFechaAltaInicial() != null ? deporteDTO.getFechaAltaInicial() : fechaAltaDeporte;
			Date fechaGradoDeporte = deporteDTO.getFechaGrado() != null ? deporteDTO.getFechaGrado() : new Date();

			AlumnoDeporte creado;
			try {
				creado = alumnoDeporteService.agregarDeporteAAlumnoCompleto(
					alumnoGuardado.getId(),
					deporteDTO.getDeporte(),
					gradoTipo,
					fechaAltaDeporte,
					fechaAltaInicialDeporte,
					fechaGradoDeporte,
					deporteDTO.codigoTarifaEfectivo(),
					deporteDTO.getCuantiaTarifa(),
					deporteDTO.getRolFamiliar(),
					deporteDTO.getGrupoFamiliar(),
					deporteDTO.getCategoria(),
					deporteDTO.getCompetidor(),
					deporteDTO.getFechaAltaCompeticion(),
					deporteDTO.getFechaAltaCompetidorInicial(),
					deporteDTO.getPeso(),
					deporteDTO.getFechaPeso(),
					deporteDTO.getTieneLicencia(),
					deporteDTO.getNumeroLicencia(),
					deporteDTO.getFechaLicencia()
				);
			} catch (IllegalArgumentException e) {
				// Datos del deporte no válidos (p. ej. categoría inexistente): 400 y se deshace el alta.
				throw new DatosAlumnoInvalidosException(e.getMessage());
			}
			// Mantener la colección en memoria al día: la respuesta y los cargos se calculan con ella
			if (creado != null) {
				alumnoGuardado.addDeporte(creado);
				if (primerDeporte == null) {
					primerDeporte = creado;
				}
			}
		}

		if (desdeCamposDeporteUnico && primerDeporte != null) {
			// El deporte traducido desde los campos de nivel superior es el principal
			primerDeporte.setPrincipal(true);
			primerDeporte.setAptoParaExamen(nuevoAlumnoDTO.getAptoParaExamen() != null
					? nuevoAlumnoDTO.getAptoParaExamen()
					: alumnoDeporteService.esAptoParaExamen(primerDeporte));
			alumnoDeporteRepository.save(primerDeporte);
		}

		// La licencia federativa se gestiona aparte: el alta del alumno nunca la cobra.

		asignarMensualidadGeneralSiCorresponde(alumnoGuardado);

		// Finalmente, retornar el Alumno guardado
		return alumnoGuardado;
	}

	/**
	 * Comprueba los deportes iniciales de un alta antes de guardar nada.
	 *
	 * @throws DatosAlumnoInvalidosException si falta el deporte, se repite, el grado no existe en la
	 *         escuela o no es válido, o se marca como competidor sin licencia.
	 */
	private void validarDeportesIniciales(List<AlumnoDeporteCreacionDTO> deportesIniciales) {
		java.util.Set<String> vistos = new java.util.HashSet<>();
		for (AlumnoDeporteCreacionDTO deporteDTO : deportesIniciales) {
			if (deporteDTO == null || deporteDTO.getDeporte() == null) {
				throw new DatosAlumnoInvalidosException("Cada deporte del alta debe indicar el deporte.");
			}
			String deporte = deporteDTO.getDeporte();
			if (!vistos.add(deporte)) {
				throw new DatosAlumnoInvalidosException("El deporte " + deporte + " está repetido en el alta.");
			}
			// Antes lo garantizaba el enum Deporte al leer la petición; ahora, el catálogo de la escuela.
			try {
				catalogoDisciplinas.validarCodigo(deporte);
			} catch (IllegalArgumentException e) {
				throw new DatosAlumnoInvalidosException("El deporte " + deporte + " no está configurado en la escuela.");
			}
			if (deporteDTO.getGrado() != null && !deporteDTO.getGrado().isEmpty()) {
				// Código de grado de la escuela (cualquier sistema) o de Taemoi, como antes TipoGrado.valueOf
				String tipoGrado = deporteDTO.getGrado();
				if (!progresionGrados.existeCodigoGrado(tipoGrado)
						&& CatalogoLegadoTaemoi.nombreGradoLegado(tipoGrado) == null) {
					throw new DatosAlumnoInvalidosException("Grado no válido para " + deporte + ": " + deporteDTO.getGrado());
				}
				if (catalogoDisciplinas.buscarPorCodigo(deporte).isEmpty()) {
					throw new DatosAlumnoInvalidosException(
							"El deporte " + deporte + " no está configurado en la escuela.");
				}
				if (!progresionGrados.tieneProgresion(deporte)) {
					throw new DatosAlumnoInvalidosException("El deporte " + deporte + " no tiene sistema de grados.");
				}
				if (progresionGrados.buscarGrado(deporte, tipoGrado).isEmpty()) {
					throw new DatosAlumnoInvalidosException(
							"El grado " + tipoGrado + " no está configurado en la escuela.");
				}
			}
			if (Boolean.TRUE.equals(deporteDTO.getCompetidor()) && !Boolean.TRUE.equals(deporteDTO.getTieneLicencia())) {
				throw new DatosAlumnoInvalidosException("El alumno debe tener una licencia federativa activa para "
						+ "ser marcado como competidor en " + deporte + ".");
			}
		}
	}

	/**
	 * Traduce los campos de deporte único del nivel superior de {@link AlumnoDTO} (contrato
	 * anterior de la API) al deporte inicial del alumno, con los mismos valores por defecto
	 * que aplicaba el antiguo modo de deporte único.
	 *
	 * @throws DatosAlumnoInvalidosException si no se indica ningún deporte o los datos no son coherentes.
	 */
	private AlumnoDeporteCreacionDTO mapearDeporteUnicoADeporteInicial(AlumnoDTO dto) {
		if (dto.getDeporte() == null) {
			throw new DatosAlumnoInvalidosException(
					"Debe indicarse al menos un deporte: 'deportesInicial' o el campo 'deporte'.");
		}
		Date hoy = new Date();
		AlumnoDeporteCreacionDTO deporte = new AlumnoDeporteCreacionDTO();
		deporte.setDeporte(dto.getDeporte());

		if (dto.getGrado() != null && !dto.getGrado().isEmpty()) {
			deporte.setGrado(dto.getGrado());
		} else if (catalogoDisciplinas.buscarPorCodigo(dto.getDeporte()).isPresent()) {
			// Disciplina con grados: grado de entrada de su sistema (BLANCO en Taemoi)
			progresionGrados.obtenerGradoInicial(dto.getDeporte())
					.ifPresent(inicial -> deporte.setGrado(inicial.getCodigo()));
		}
		deporte.setFechaGrado(dto.getFechaGrado() != null ? dto.getFechaGrado() : hoy);
		deporte.setFechaAlta(dto.getFechaAlta());
		deporte.setFechaAltaInicial(dto.getFechaAltaInicial());

		RolFamiliar rolFamiliar = dto.getRolFamiliar() != null ? dto.getRolFamiliar() : RolFamiliar.NINGUNO;
		deporte.setTipoTarifa(dto.getTipoTarifa());
		deporte.setRolFamiliar(rolFamiliar.name());
		deporte.setGrupoFamiliar(dto.getGrupoFamiliar());
		if (dto.getCuantiaTarifa() != null && dto.getCuantiaTarifa() > 0) {
			deporte.setCuantiaTarifa(dto.getCuantiaTarifa());
		} else if (dto.getTipoTarifa() != null) {
			try {
				deporte.setCuantiaTarifa(asignarCuantiaTarifa(dto.getTipoTarifa(), rolFamiliar));
			} catch (IllegalArgumentException e) {
				// La organización no tiene esa tarifa (en Taemoi están todas las de TipoTarifa)
				throw new DatosAlumnoInvalidosException(e.getMessage());
			}
		} else {
			throw new DatosAlumnoInvalidosException("Debe indicarse el tipo de tarifa o la cuantía de la tarifa.");
		}

		boolean tieneLicencia = Boolean.TRUE.equals(dto.getTieneLicencia());
		deporte.setTieneLicencia(tieneLicencia);
		if (tieneLicencia) {
			deporte.setNumeroLicencia(dto.getNumeroLicencia());
			deporte.setFechaLicencia(dto.getFechaLicencia() != null ? dto.getFechaLicencia() : hoy);
		}

		boolean competidor = Boolean.TRUE.equals(dto.getCompetidor());
		deporte.setCompetidor(competidor);
		if (competidor) {
			deporte.setPeso(dto.getPeso());
			deporte.setFechaPeso(dto.getFechaPeso() != null ? dto.getFechaPeso() : hoy);
		}
		return deporte;
	}

    @Override
    @Transactional
    public Documento agregarDocumentoAAlumno(Long alumnoId, MultipartFile archivo) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

        try {
            Documento documento = documentoService.guardarDocumento(alumno, archivo);
            alumno.getDocumentos().add(documento);
            alumnoRepository.save(alumno);
            return documento;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el documento: " + e.getMessage(), e);
        }
    }

	/**
	 * Actualiza un alumno existente.
	 *
	 * @param id                   El ID del alumno a actualizar.
	 * @param alumnoActualizado    El objeto AlumnoDTO con los datos actualizados.
	 * @param nuevaFechaNacimiento La nueva fecha de nacimiento del alumno.
	 * @param imagen               La nueva imagen del alumno, si se proporciona.
	 * @return El alumno actualizado.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado.
	 */
	@Override
	@Transactional
	public Alumno actualizarAlumno(@NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento,
			MultipartFile nuevaImagen) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumnoExistente = optionalAlumno.get();

			// Actualizar datos generales del alumno
			alumnoExistente.setNombre(alumnoActualizado.getNombre());
			alumnoExistente.setApellidos(alumnoActualizado.getApellidos());
			alumnoExistente.setFechaNacimiento(nuevaFechaNacimiento);

			// Actualizar otros campos del alumno
			String nifNormalizado = NifUtils.normalizeForStorage(alumnoActualizado.getNif());
			if (nifNormalizado != null && alumnoRepository.existsByNifAndIdNot(nifNormalizado, id)) {
				throw new AlumnoDuplicadoException("El alumno con NIF " + nifNormalizado + " ya existe.");
			}
			alumnoExistente.setNif(nifNormalizado);
			alumnoExistente.setDireccion(alumnoActualizado.getDireccion());
			String normalizedEmail = EmailUtils.normalizeEmail(alumnoActualizado.getEmail());
			Usuario usuario = alumnoExistente.getUsuario();
			if (usuario != null && normalizedEmail != null) {
				String usuarioEmail = usuario.getEmail();
				if ((usuarioEmail == null || !usuarioEmail.equals(normalizedEmail))
						&& gestionPorSede.restriccion().isPresent()) {
					// Entrega 6-1: el responsable de sede nunca cambia el email de una cuenta (es el de acceso; con él
					// podría apropiarse de una cuenta de gestión). Cambiar el de un alumno con cuenta: 403; sin cambio
					// en la ficha, la cuenta no se toca.
					if (!java.util.Objects.equals(normalizedEmail, EmailUtils.normalizeEmail(alumnoExistente.getEmail()))) {
						throw new org.springframework.security.access.AccessDeniedException("Acceso denegado.");
					}
				} else if (usuarioEmail == null || !usuarioEmail.equals(normalizedEmail)) {
					// El email es de la cuenta global: solo se cambia si no pertenece a otra escuela.
					if (!membresiaService.puedeGestionarUsuarioGlobal(usuario.getId(),
							alumnoExistente.getOrganizacionId())) {
						throw new UsuarioCompartidoException("El alumno está vinculado a una cuenta que también "
								+ "pertenece a otra escuela: su email no se puede cambiar desde esta escuela.");
					}
					Optional<Usuario> usuarioConEmail = usuarioRepository.findByEmailIgnoreCase(normalizedEmail);
					if (usuarioConEmail.isPresent() && !usuarioConEmail.get().getId().equals(usuario.getId())) {
						throw new IllegalArgumentException("El correo electronico ya esta asociado a otro usuario.");
					}
					usuario.setEmail(normalizedEmail);
					usuarioRepository.save(usuario);
				}
			}
			alumnoExistente.setEmail(normalizedEmail);
			alumnoExistente.setTelefono(TelefonoUtils.normalizar(alumnoActualizado.getTelefono()));
			alumnoExistente.setTelefono2(TelefonoUtils.normalizar(alumnoActualizado.getTelefono2()));
			// Campos nuevos (V25): el frontend actual aún no los envía, así que nulo = sin cambios (si no,
			// cada edición borraría lo importado). Una cadena vacía sí vacía localidad y código postal.
			if (alumnoActualizado.getGenero() != null) {
				alumnoExistente.setGenero(alumnoActualizado.getGenero());
			}
			if (alumnoActualizado.getLocalidad() != null) {
				alumnoExistente.setLocalidad(textoOpcional(alumnoActualizado.getLocalidad()));
			}
			if (alumnoActualizado.getCodigoPostal() != null) {
				alumnoExistente.setCodigoPostal(textoOpcional(alumnoActualizado.getCodigoPostal()));
			}
			// Responsable legal: nulo (o ausente) = sin cambios; cadena vacía = quitarlo.
			tutorService.aplicarResponsableLegal(alumnoExistente, new TutorService.CambioResponsableLegal(
					alumnoActualizado.getResponsableLegalNombre(), alumnoActualizado.getResponsableLegalNif()));
			if (alumnoActualizado.getObservaciones() != null) {
				alumnoExistente.setObservaciones(alumnoActualizado.getObservaciones());
			}

			if (alumnoActualizado.getFechaAlta() != null) {
				alumnoExistente.setFechaAlta(alumnoActualizado.getFechaAlta());
			}

			// Actualizar fechaAltaInicial solo si se proporciona un valor
			if (alumnoActualizado.getFechaAltaInicial() != null) {
				alumnoExistente.setFechaAltaInicial(alumnoActualizado.getFechaAltaInicial());
			}

			alumnoExistente.setFechaBaja(alumnoActualizado.getFechaBaja());
			Boolean autorizacionAnterior = alumnoExistente.getAutorizacionWeb();
			alumnoExistente.setAutorizacionWeb(alumnoActualizado.getAutorizacionWeb());
			if (alumnoActualizado.getAutorizacionWeb() != null
					&& !alumnoActualizado.getAutorizacionWeb().equals(autorizacionAnterior)) {
				consentimientoService.registrarCambioAutorizacionWeb(alumnoExistente,
						alumnoActualizado.getAutorizacionWeb());
			}

			alumnoExistente.setTieneDiscapacidad(Optional.ofNullable(alumnoActualizado.getTieneDiscapacidad()).orElse(false));

			// Manejo de la imagen del alumno
			try {
				if (nuevaImagen != null && "null".equals(nuevaImagen.getOriginalFilename())) {
					// Si la imagen enviada es 'null', eliminar la imagen existente
					Imagen imagenAnterior = alumnoExistente.getFotoAlumno();
					if (imagenAnterior != null) {
						// Eliminar la imagen del sistema de archivos y de la base de datos
						imagenService.eliminarImagenDeSistema(imagenAnterior);
						imagenRepository.delete(imagenAnterior);
						alumnoExistente.setFotoAlumno(null); // Remover referencia de la imagen
					}
				} else if (nuevaImagen != null && !nuevaImagen.isEmpty()) {
					// Si hay una nueva imagen, reemplazar la imagen existente
					Imagen imagenAnterior = alumnoExistente.getFotoAlumno();
					if (imagenAnterior != null) {
						// Eliminar la imagen anterior
						imagenService.eliminarImagenDeSistema(imagenAnterior);
						imagenRepository.delete(imagenAnterior);
					}

					// Guardar la nueva imagen y asignarla al alumno
					Imagen nuevaImagenGuardada = imagenService.guardarImagen(nuevaImagen);
					alumnoExistente.setFotoAlumno(nuevaImagenGuardada);
				}
			} catch (IOException e) {
				throw new RuntimeException("Error al procesar la imagen", e);
			}

			// Guardar los cambios en el alumno en la base de datos
			return alumnoRepository.save(alumnoExistente);
		} else {
			throw new RuntimeException("No se encontro el alumno con ID: " + id);
		}
	}

	@Override
	@Transactional
	public Alumno actualizarObservaciones(@NonNull Long id, String observaciones) {
		Alumno alumno = alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
		alumno.setObservaciones(observaciones);
		return alumnoRepository.save(alumno);
	}

	/**
	 * Elimina la imagen asociada a un alumno especificado por su ID.
	 * 
	 * @param id El ID del alumno cuya imagen se eliminará.
	 * @throws RuntimeException Si no se encuentra el alumno con el ID especificado
	 *                          o si el alumno no tiene una imagen asociada.
	 */
	@Override
	public void eliminarImagenAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Imagen imagen = alumno.getFotoAlumno();
			if (imagen != null) {

				imagenService.eliminarImagenDeSistema(imagen);

				alumno.setFotoAlumno(null);
				alumnoRepository.save(alumno);
				imagenRepository.delete(imagen);
			} else {
				throw new RuntimeException("El alumno no tiene una imagen asociada.");
			}
		} else {
			throw new RuntimeException("No se encontró el alumno con ID: " + id);
		}
	}
	
    @Override
    public void eliminarDocumentoDeAlumno(Long alumnoId, Long documentoId) {
        Documento documento = obtenerDocumentoDeAlumno(alumnoId, documentoId);
        Alumno alumno = documento.getAlumno();

        documentoService.eliminarDocumento(documento);

        if (alumno != null) {
            alumno.getDocumentos().remove(documento);
            alumnoRepository.save(alumno);
        }
    }

	@Override
	public Documento obtenerDocumentoDeAlumno(Long alumnoId, Long documentoId) {
		logger.info("Getting document {} for alumno {}", documentoId, alumnoId);

		// Verificar que el alumno existe
		if (!alumnoRepository.existsById(alumnoId)) {
			logger.error("Alumno not found with ID: {}", alumnoId);
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId);
		}

		Documento documento = documentoRepository.findById(documentoId)
				.orElseThrow(() -> {
					logger.error("Document not found with ID: {}", documentoId);
					return new RuntimeException("Documento no encontrado con ID: " + documentoId);
				});

		if (documento.getAlumno() == null) {
			logger.error("Document {} has no associated alumno (null)", documentoId);
			throw new RuntimeException("El documento no tiene un alumno asociado.");
		}

		if (!documento.getAlumno().getId().equals(alumnoId)) {
			logger.error("Document {} belongs to alumno {} but was requested for alumno {}",
					documentoId, documento.getAlumno().getId(), alumnoId);
			throw new RuntimeException("El documento no pertenece a este alumno.");
		}

		logger.info("Document {} successfully retrieved for alumno {}", documentoId, alumnoId);
		return documento;
	}

	@Override
	@Transactional(readOnly = true)
	public RetoDiarioEstadoDTO obtenerEstadoRetoDiario(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		LocalDate hoy = LocalDate.now(RETO_DIARIO_ZONE_ID);
		LocalDate fechaCompletado = toLocalDate(alumno.getFechaRetoDiarioCompletado());
		int rachaPersistida = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		boolean completadoHoy = fechaCompletado != null && fechaCompletado.equals(hoy);
		int rachaActual = calcularRachaActual(rachaPersistida, fechaCompletado, hoy);
		long nextResetAtEpochMs = calcularProximoResetEpochMs();

		return new RetoDiarioEstadoDTO(
				rachaActual,
				completadoHoy,
				fechaCompletado != null ? fechaCompletado.toString() : null,
				nextResetAtEpochMs);
	}

	@Override
	@Transactional
	public RetoDiarioEstadoDTO completarRetoDiario(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		LocalDate hoy = LocalDate.now(RETO_DIARIO_ZONE_ID);
		LocalDate fechaAnterior = toLocalDate(alumno.getFechaRetoDiarioCompletado());
		int rachaAnterior = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		int nuevaRacha;

		if (fechaAnterior != null && fechaAnterior.equals(hoy)) {
			nuevaRacha = calcularRachaActual(rachaAnterior, fechaAnterior, hoy);
		} else if (fechaAnterior != null && fechaAnterior.equals(hoy.minusDays(1))) {
			nuevaRacha = rachaAnterior + 1;
		} else {
			nuevaRacha = 1;
		}

		// DATE column: persist date without timezone ambiguity.
		alumno.setFechaRetoDiarioCompletado(java.sql.Date.valueOf(hoy));
		alumno.setRachaRetoDiario(nuevaRacha);
		alumnoRepository.save(alumno);
		registrarLogRetoDiarioSiNoExiste(alumno, hoy);

		return new RetoDiarioEstadoDTO(nuevaRacha, true, hoy.toString(), calcularProximoResetEpochMs());
	}

	@Override
	@Transactional
	public RetoDiarioRankingSemanalResponse obtenerRankingSemanalRetoDiario(Long alumnoId, String deporte,
			Integer limit) {
		if (alumnoId == null) {
			throw new IllegalArgumentException("El alumno es obligatorio.");
		}
		if (deporte == null) {
			throw new IllegalArgumentException("El deporte es obligatorio.");
		}

		Alumno alumnoActual = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));
		if (!Boolean.TRUE.equals(alumnoActual.getActivo())) {
			throw new IllegalArgumentException("El alumno seleccionado no está activo.");
		}
		if (!alumnoDeporteRepository.existsByAlumnoIdAndDeporteAndActivoTrue(alumnoId, deporte)) {
			throw new IllegalArgumentException("El deporte seleccionado no está activo para el alumno.");
		}

		int limiteTop = normalizarLimiteTop(limit);
		LocalDate hoy = LocalDate.now(RETO_DIARIO_ZONE_ID);
		WeekFields weekFields = WeekFields.ISO;
		int anioIso = hoy.get(weekFields.weekBasedYear());
		int semanaIso = hoy.get(weekFields.weekOfWeekBasedYear());

		ejecutarBackfillSemanalSiNecesario(anioIso, semanaIso, hoy);

		List<AlumnoDeporte> participantesDeporte = alumnoDeporteRepository.findActivosConAlumnoActivoByDeporte(deporte);
		List<Long> alumnoIds = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.map(Alumno::getId)
				.distinct()
				.toList();

		Map<Long, AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection> puntuacionesPorAlumno = new HashMap<>();
		if (!alumnoIds.isEmpty()) {
			List<AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection> puntuaciones = alumnoRetoDiarioLogRepository
					.obtenerPuntuacionesSemana(anioIso, semanaIso, alumnoIds);
			for (AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection puntuacion : puntuaciones) {
				puntuacionesPorAlumno.put(puntuacion.getAlumnoId(), puntuacion);
			}
		}

		List<RankingSemanaParticipante> participantesRanking = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.collect(Collectors.toMap(
						Alumno::getId,
						alumno -> construirParticipanteRanking(alumno, alumnoId, puntuacionesPorAlumno.get(alumno.getId())),
						(p1, p2) -> p1))
				.values().stream()
				.collect(Collectors.toCollection(ArrayList::new));

		aplicarDesambiguacionAliasDuplicados(participantesRanking);

		List<RankingSemanaParticipante> ranking = participantesRanking.stream()
				.sorted(Comparator
						.comparingInt(RankingSemanaParticipante::getDiasCompletados).reversed()
						.thenComparing(RankingSemanaParticipante::getUltimaFechaCompletado,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(RankingSemanaParticipante::getUltimaMarcaCompletado,
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(RankingSemanaParticipante::getAlias, String.CASE_INSENSITIVE_ORDER))
				.toList();

		asignarPosicionesDensas(ranking);

		List<RetoDiarioRankingItemResponse> top = ranking.stream()
				.limit(limiteTop)
				.map(item -> new RetoDiarioRankingItemResponse(
						item.getPosicion(),
						item.getAlias(),
						item.getDiasCompletados(),
						item.getEsUsuarioActual()))
				.toList();

		RankingSemanaParticipante participanteActual = ranking.stream()
				.filter(RankingSemanaParticipante::getEsUsuarioActual)
				.findFirst()
				.orElseGet(() -> new RankingSemanaParticipante(
						alumnoId,
						construirAliasRanking(alumnoActual),
						obtenerInicialSegundoApellido(alumnoActual),
						0,
						null,
						null,
						true));

		Integer diasParaSuperarSiguiente = calcularDiasParaSuperarSiguiente(participanteActual, ranking);
		RetoDiarioRankingMiPosicionResponse miPosicion = new RetoDiarioRankingMiPosicionResponse(
				participanteActual.getPosicion(),
				participanteActual.getAlias(),
				participanteActual.getDiasCompletados(),
				diasParaSuperarSiguiente);

		return new RetoDiarioRankingSemanalResponse(
				deporte,
				anioIso,
				semanaIso,
				ranking.size(),
				top,
				miPosicion);
	}

	@Override
	@Transactional(readOnly = true)
	public RetoDiarioRankingGeneralResponse obtenerRankingGeneralRetoDiario(Long alumnoId, String deporte,
			Integer limit) {
		if (alumnoId == null) {
			throw new IllegalArgumentException("El alumno es obligatorio.");
		}
		if (deporte == null) {
			throw new IllegalArgumentException("El deporte es obligatorio.");
		}

		Alumno alumnoActual = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));
		if (!Boolean.TRUE.equals(alumnoActual.getActivo())) {
			throw new IllegalArgumentException("El alumno seleccionado no est\u00E1 activo.");
		}
		if (!alumnoDeporteRepository.existsByAlumnoIdAndDeporteAndActivoTrue(alumnoId, deporte)) {
			throw new IllegalArgumentException("El deporte seleccionado no est\u00E1 activo para el alumno.");
		}

		int limiteTop = normalizarLimiteTop(limit);

		List<AlumnoDeporte> participantesDeporte = alumnoDeporteRepository.findActivosConAlumnoActivoByDeporte(deporte);
		List<Long> alumnoIds = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.map(Alumno::getId)
				.distinct()
				.toList();

		Map<Long, HistoricoRetoDiarioStats> historicoPorAlumno = calcularHistoricoRetoDiarioPorAlumno(alumnoIds);

		List<RankingGeneralParticipante> participantesRanking = participantesDeporte.stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && alumno.getId() != null)
				.collect(Collectors.toMap(
						Alumno::getId,
						alumno -> construirParticipanteRankingGeneral(alumno, alumnoId, historicoPorAlumno.get(alumno.getId())),
						(p1, p2) -> p1))
				.values().stream()
				.collect(Collectors.toCollection(ArrayList::new));

		aplicarDesambiguacionAliasDuplicadosGeneral(participantesRanking);

		List<RankingGeneralParticipante> ranking = participantesRanking.stream()
				.sorted(Comparator
						.comparingInt(RankingGeneralParticipante::getMejorRacha).reversed()
						.thenComparing(Comparator.comparingInt(RankingGeneralParticipante::getDiasCompletadosTotales)
								.reversed())
						.thenComparing(RankingGeneralParticipante::getUltimaFechaCompletado,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(RankingGeneralParticipante::getUltimaMarcaCompletado,
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(RankingGeneralParticipante::getAlias, String.CASE_INSENSITIVE_ORDER))
				.toList();

		asignarPosicionesDensasPorMejorRacha(ranking);

		List<RetoDiarioRankingGeneralItemResponse> top = ranking.stream()
				.limit(limiteTop)
				.map(item -> new RetoDiarioRankingGeneralItemResponse(
						item.getPosicion(),
						item.getAlias(),
						item.getMejorRacha(),
						item.getDiasCompletadosTotales(),
						item.getEsUsuarioActual()))
				.toList();

		RankingGeneralParticipante participanteActual = ranking.stream()
				.filter(RankingGeneralParticipante::getEsUsuarioActual)
				.findFirst()
				.orElseGet(() -> new RankingGeneralParticipante(
						alumnoId,
						construirAliasRanking(alumnoActual),
						obtenerInicialSegundoApellido(alumnoActual),
						0,
						0,
						null,
						null,
						true));

		Integer diasParaSuperarSiguiente = calcularDiasParaSuperarSiguienteGeneral(participanteActual, ranking);
		RetoDiarioRankingGeneralMiPosicionResponse miPosicion = new RetoDiarioRankingGeneralMiPosicionResponse(
				participanteActual.getPosicion(),
				participanteActual.getAlias(),
				participanteActual.getMejorRacha(),
				participanteActual.getDiasCompletadosTotales(),
				diasParaSuperarSiguiente);

		return new RetoDiarioRankingGeneralResponse(
				deporte,
				ranking.size(),
				top,
				miPosicion);
	}

	private void registrarLogRetoDiarioSiNoExiste(Alumno alumno, LocalDate fechaLocal) {
		if (alumno == null || alumno.getId() == null || fechaLocal == null) {
			return;
		}
		if (alumnoRetoDiarioLogRepository.existsByAlumnoIdAndFechaCompletado(alumno.getId(), fechaLocal)) {
			return;
		}
		WeekFields weekFields = WeekFields.ISO;
		AlumnoRetoDiarioLog log = new AlumnoRetoDiarioLog();
		log.setAlumno(alumno);
		log.setFechaCompletado(fechaLocal);
		log.setAnioIso(fechaLocal.get(weekFields.weekBasedYear()));
		log.setSemanaIso(fechaLocal.get(weekFields.weekOfWeekBasedYear()));
		alumnoRetoDiarioLogRepository.save(log);
	}

	private void ejecutarBackfillSemanalSiNecesario(int anioIso, int semanaIso, LocalDate hoy) {
		if (alumnoRetoDiarioLogRepository.existsByAnioIsoAndSemanaIso(anioIso, semanaIso)) {
			return;
		}

		LocalDate inicioSemana = obtenerInicioSemanaIso(hoy);
		LocalDate finSemana = inicioSemana.plusDays(6);
		List<Alumno> alumnosActivos = alumnoRepository.findByActivoTrue();

		for (Alumno alumno : alumnosActivos) {
			if (alumno == null || alumno.getId() == null) {
				continue;
			}
			LocalDate fechaFinRacha = toLocalDate(alumno.getFechaRetoDiarioCompletado());
			int racha = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
			if (fechaFinRacha == null || racha <= 0) {
				continue;
			}

			LocalDate fechaInicioRacha = fechaFinRacha.minusDays(racha - 1L);
			LocalDate inicioInterseccion = fechaInicioRacha.isAfter(inicioSemana) ? fechaInicioRacha : inicioSemana;
			LocalDate finInterseccion = fechaFinRacha.isBefore(finSemana) ? fechaFinRacha : finSemana;

			if (inicioInterseccion.isAfter(finInterseccion)) {
				continue;
			}

			for (LocalDate fecha = inicioInterseccion; !fecha.isAfter(finInterseccion); fecha = fecha.plusDays(1)) {
				if (fecha.isAfter(hoy)) {
					continue;
				}
				registrarLogRetoDiarioSiNoExiste(alumno, fecha);
			}
		}
	}

	private LocalDate obtenerInicioSemanaIso(LocalDate fecha) {
		WeekFields weekFields = WeekFields.ISO;
		return fecha.with(weekFields.dayOfWeek(), 1);
	}

	private int normalizarLimiteTop(Integer limit) {
		if (limit == null) {
			return 10;
		}
		return Math.max(1, Math.min(10, limit));
	}

	private RankingSemanaParticipante construirParticipanteRanking(
			Alumno alumno,
			Long alumnoActualId,
			AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection puntuacion) {
		int dias = 0;
		LocalDate ultimaFecha = null;
		Long ultimaMarca = null;
		if (puntuacion != null) {
			dias = puntuacion.getDiasCompletados() != null ? Math.max(0, puntuacion.getDiasCompletados().intValue()) : 0;
			ultimaFecha = puntuacion.getUltimaFechaCompletado();
			ultimaMarca = puntuacion.getUltimaMarcaCompletadoId();
		}
		boolean esUsuarioActual = alumnoActualId != null && alumnoActualId.equals(alumno.getId());
		return new RankingSemanaParticipante(
				alumno.getId(),
				construirAliasRanking(alumno),
				obtenerInicialSegundoApellido(alumno),
				dias,
				ultimaFecha,
				ultimaMarca,
				esUsuarioActual);
	}

	private void aplicarDesambiguacionAliasDuplicados(List<RankingSemanaParticipante> participantes) {
		if (participantes == null || participantes.isEmpty()) {
			return;
		}

		Map<String, Integer> conteoAliasBase = new HashMap<>();
		for (RankingSemanaParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			conteoAliasBase.put(clave, conteoAliasBase.getOrDefault(clave, 0) + 1);
		}

		for (RankingSemanaParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			if (conteoAliasBase.getOrDefault(clave, 0) > 1) {
				participante.aplicarInicialSegundoApellido();
			}
		}
	}

	private String normalizarClaveAlias(String aliasBase) {
		if (aliasBase == null) {
			return "";
		}
		return aliasBase.trim().toLowerCase(Locale.ROOT);
	}

	private void asignarPosicionesDensas(List<RankingSemanaParticipante> ranking) {
		int posicionActual = 0;
		Integer diasPrevios = null;
		for (RankingSemanaParticipante participante : ranking) {
			if (diasPrevios == null || participante.getDiasCompletados() != diasPrevios) {
				posicionActual++;
				diasPrevios = participante.getDiasCompletados();
			}
			participante.setPosicion(posicionActual);
		}
	}

	private Integer calcularDiasParaSuperarSiguiente(RankingSemanaParticipante participanteActual,
			List<RankingSemanaParticipante> rankingCompleto) {
		if (participanteActual == null || participanteActual.getPosicion() == null || participanteActual.getPosicion() <= 1) {
			return null;
		}

		int diasActuales = participanteActual.getDiasCompletados();
		Integer diasSiguienteSuperior = rankingCompleto.stream()
				.map(RankingSemanaParticipante::getDiasCompletados)
				.filter(dias -> dias > diasActuales)
				.min(Integer::compareTo)
				.orElse(null);
		if (diasSiguienteSuperior == null) {
			return null;
		}
		return (diasSiguienteSuperior - diasActuales) + 1;
	}

	private Map<Long, HistoricoRetoDiarioStats> calcularHistoricoRetoDiarioPorAlumno(List<Long> alumnoIds) {
		Map<Long, HistoricoRetoDiarioStats> historicoPorAlumno = new HashMap<>();
		if (alumnoIds == null || alumnoIds.isEmpty()) {
			return historicoPorAlumno;
		}

		List<AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioFechaProjection> registros = alumnoRetoDiarioLogRepository
				.obtenerFechasCompletadoHistorico(alumnoIds);

		for (AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioFechaProjection registro : registros) {
			if (registro == null || registro.getAlumnoId() == null || registro.getFechaCompletado() == null) {
				continue;
			}

			HistoricoRetoDiarioStats stats = historicoPorAlumno.computeIfAbsent(
					registro.getAlumnoId(),
					ignored -> new HistoricoRetoDiarioStats());
			stats.registrarFechaCompletado(registro.getFechaCompletado(), registro.getLogId());
		}

		return historicoPorAlumno;
	}

	private RankingGeneralParticipante construirParticipanteRankingGeneral(
			Alumno alumno,
			Long alumnoActualId,
			HistoricoRetoDiarioStats historico) {
		int mejorRacha = 0;
		int diasCompletadosTotales = 0;
		LocalDate ultimaFecha = null;
		Long ultimaMarca = null;

		if (historico != null) {
			mejorRacha = historico.getMejorRacha();
			diasCompletadosTotales = historico.getDiasCompletadosTotales();
			ultimaFecha = historico.getUltimaFechaCompletado();
			ultimaMarca = historico.getUltimaMarcaCompletado();
		}

		int rachaPersistida = alumno.getRachaRetoDiario() != null ? Math.max(0, alumno.getRachaRetoDiario()) : 0;
		if (rachaPersistida > 0) {
			mejorRacha = Math.max(mejorRacha, rachaPersistida);
			diasCompletadosTotales = Math.max(diasCompletadosTotales, rachaPersistida);
			LocalDate fechaPersistida = toLocalDate(alumno.getFechaRetoDiarioCompletado());
			if (fechaPersistida != null && (ultimaFecha == null || fechaPersistida.isAfter(ultimaFecha))) {
				ultimaFecha = fechaPersistida;
				ultimaMarca = null;
			}
		}

		boolean esUsuarioActual = alumnoActualId != null && alumnoActualId.equals(alumno.getId());
		return new RankingGeneralParticipante(
				alumno.getId(),
				construirAliasRanking(alumno),
				obtenerInicialSegundoApellido(alumno),
				mejorRacha,
				diasCompletadosTotales,
				ultimaFecha,
				ultimaMarca,
				esUsuarioActual);
	}

	private void aplicarDesambiguacionAliasDuplicadosGeneral(List<RankingGeneralParticipante> participantes) {
		if (participantes == null || participantes.isEmpty()) {
			return;
		}

		Map<String, Integer> conteoAliasBase = new HashMap<>();
		for (RankingGeneralParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			conteoAliasBase.put(clave, conteoAliasBase.getOrDefault(clave, 0) + 1);
		}

		for (RankingGeneralParticipante participante : participantes) {
			String clave = normalizarClaveAlias(participante.getAliasBase());
			if (conteoAliasBase.getOrDefault(clave, 0) > 1) {
				participante.aplicarInicialSegundoApellido();
			}
		}
	}

	private void asignarPosicionesDensasPorMejorRacha(List<RankingGeneralParticipante> ranking) {
		int posicionActual = 0;
		Integer mejorRachaPrevia = null;
		for (RankingGeneralParticipante participante : ranking) {
			if (mejorRachaPrevia == null || participante.getMejorRacha() != mejorRachaPrevia) {
				posicionActual++;
				mejorRachaPrevia = participante.getMejorRacha();
			}
			participante.setPosicion(posicionActual);
		}
	}

	private Integer calcularDiasParaSuperarSiguienteGeneral(RankingGeneralParticipante participanteActual,
			List<RankingGeneralParticipante> rankingCompleto) {
		if (participanteActual == null || participanteActual.getPosicion() == null || participanteActual.getPosicion() <= 1) {
			return null;
		}

		int mejorRachaActual = participanteActual.getMejorRacha();
		Integer mejorRachaSuperior = rankingCompleto.stream()
				.map(RankingGeneralParticipante::getMejorRacha)
				.filter(racha -> racha > mejorRachaActual)
				.min(Integer::compareTo)
				.orElse(null);
		if (mejorRachaSuperior == null) {
			return null;
		}
		return (mejorRachaSuperior - mejorRachaActual) + 1;
	}

	private String construirAliasRanking(Alumno alumno) {
		if (alumno == null) {
			return "Alumno";
		}
		String primerNombre = capitalizarNombreSimple(extraerPrimerSegmento(alumno.getNombre()));
		String primerApellido = capitalizarNombreSimple(extraerPrimerSegmento(alumno.getApellidos()));

		if (primerNombre.isBlank()) {
			primerNombre = "Alumno";
		}

		return primerApellido.isBlank() ? primerNombre : primerNombre + " " + primerApellido;
	}

	private String obtenerInicialSegundoApellido(Alumno alumno) {
		if (alumno == null || alumno.getApellidos() == null) {
			return "";
		}

		String apellidos = alumno.getApellidos().trim();
		if (apellidos.isBlank()) {
			return "";
		}

		String[] partes = apellidos.split("\\s+");
		if (partes.length < 2) {
			return "";
		}

		String segundoApellido = capitalizarNombreSimple(partes[1]);
		if (segundoApellido.isBlank()) {
			return "";
		}

		return segundoApellido.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	private String extraerPrimerSegmento(String texto) {
		if (texto == null) {
			return "";
		}
		String limpio = texto.trim();
		if (limpio.isBlank()) {
			return "";
		}
		return limpio.split("\\s+")[0];
	}

	private String capitalizarNombreSimple(String texto) {
		if (texto == null || texto.isBlank()) {
			return "";
		}

		String valor = texto.trim().toLowerCase(Locale.ROOT);
		StringBuilder resultado = new StringBuilder(valor.length());
		boolean capitalizar = true;

		for (int i = 0; i < valor.length(); i++) {
			char caracter = valor.charAt(i);
			if (Character.isLetter(caracter)) {
				resultado.append(capitalizar ? Character.toUpperCase(caracter) : caracter);
				capitalizar = false;
			} else {
				resultado.append(caracter);
				capitalizar = caracter == '-' || caracter == '\'';
			}
		}

		return resultado.toString();
	}

	private int calcularRachaActual(int rachaPersistida, LocalDate fechaCompletado, LocalDate hoy) {
		if (fechaCompletado == null) {
			return 0;
		}
		if (fechaCompletado.equals(hoy) || fechaCompletado.equals(hoy.minusDays(1))) {
			return Math.max(0, rachaPersistida);
		}
		return 0;
	}

	private long calcularProximoResetEpochMs() {
		return LocalDate.now(RETO_DIARIO_ZONE_ID)
				.plusDays(1)
				.atStartOfDay(RETO_DIARIO_ZONE_ID)
				.toInstant()
				.toEpochMilli();
	}

	private LocalDate toLocalDate(Date fecha) {
		if (fecha == null) {
			return null;
		}
		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		return java.time.Instant.ofEpochMilli(fecha.getTime())
				.atZone(RETO_DIARIO_ZONE_ID)
				.toLocalDate();
	}

	private static class HistoricoRetoDiarioStats {
		private int mejorRacha;
		private int rachaActual;
		private int diasCompletadosTotales;
		private LocalDate ultimaFechaCompletado;
		private Long ultimaMarcaCompletado;
		private LocalDate fechaAnterior;

		void registrarFechaCompletado(LocalDate fecha, Long marcaCompletado) {
			if (fecha == null) {
				return;
			}

			if (fechaAnterior != null) {
				if (fecha.equals(fechaAnterior)) {
					return;
				}
				rachaActual = fecha.equals(fechaAnterior.plusDays(1)) ? rachaActual + 1 : 1;
			} else {
				rachaActual = 1;
			}

			diasCompletadosTotales++;
			mejorRacha = Math.max(mejorRacha, rachaActual);
			ultimaFechaCompletado = fecha;
			ultimaMarcaCompletado = marcaCompletado;
			fechaAnterior = fecha;
		}

		int getMejorRacha() {
			return mejorRacha;
		}

		int getDiasCompletadosTotales() {
			return diasCompletadosTotales;
		}

		LocalDate getUltimaFechaCompletado() {
			return ultimaFechaCompletado;
		}

		Long getUltimaMarcaCompletado() {
			return ultimaMarcaCompletado;
		}
	}

	private static class RankingGeneralParticipante {
		private final Long alumnoId;
		private final String aliasBase;
		private final String inicialSegundoApellido;
		private String alias;
		private final int mejorRacha;
		private final int diasCompletadosTotales;
		private final LocalDate ultimaFechaCompletado;
		private final Long ultimaMarcaCompletado;
		private final boolean esUsuarioActual;
		private Integer posicion;

		RankingGeneralParticipante(Long alumnoId, String aliasBase, String inicialSegundoApellido, int mejorRacha,
				int diasCompletadosTotales, LocalDate ultimaFechaCompletado, Long ultimaMarcaCompletado,
				boolean esUsuarioActual) {
			this.alumnoId = alumnoId;
			this.aliasBase = aliasBase;
			this.inicialSegundoApellido = inicialSegundoApellido == null ? "" : inicialSegundoApellido;
			this.alias = aliasBase;
			this.mejorRacha = mejorRacha;
			this.diasCompletadosTotales = diasCompletadosTotales;
			this.ultimaFechaCompletado = ultimaFechaCompletado;
			this.ultimaMarcaCompletado = ultimaMarcaCompletado;
			this.esUsuarioActual = esUsuarioActual;
		}

		public Long getAlumnoId() {
			return alumnoId;
		}

		public String getAlias() {
			return alias;
		}

		public String getAliasBase() {
			return aliasBase;
		}

		public int getMejorRacha() {
			return mejorRacha;
		}

		public int getDiasCompletadosTotales() {
			return diasCompletadosTotales;
		}

		public LocalDate getUltimaFechaCompletado() {
			return ultimaFechaCompletado;
		}

		public Long getUltimaMarcaCompletado() {
			return ultimaMarcaCompletado;
		}

		public boolean getEsUsuarioActual() {
			return esUsuarioActual;
		}

		public Integer getPosicion() {
			return posicion;
		}

		public void setPosicion(Integer posicion) {
			this.posicion = posicion;
		}

		public void aplicarInicialSegundoApellido() {
			if (inicialSegundoApellido == null || inicialSegundoApellido.isBlank()) {
				return;
			}
			this.alias = aliasBase + " " + inicialSegundoApellido + ".";
		}
	}

	private static class RankingSemanaParticipante {
		private final Long alumnoId;
		private final String aliasBase;
		private final String inicialSegundoApellido;
		private String alias;
		private final int diasCompletados;
		private final LocalDate ultimaFechaCompletado;
		private final Long ultimaMarcaCompletado;
		private final boolean esUsuarioActual;
		private Integer posicion;

		RankingSemanaParticipante(Long alumnoId, String aliasBase, String inicialSegundoApellido, int diasCompletados,
				LocalDate ultimaFechaCompletado, Long ultimaMarcaCompletado,
				boolean esUsuarioActual) {
			this.alumnoId = alumnoId;
			this.aliasBase = aliasBase;
			this.inicialSegundoApellido = inicialSegundoApellido == null ? "" : inicialSegundoApellido;
			this.alias = aliasBase;
			this.diasCompletados = diasCompletados;
			this.ultimaFechaCompletado = ultimaFechaCompletado;
			this.ultimaMarcaCompletado = ultimaMarcaCompletado;
			this.esUsuarioActual = esUsuarioActual;
		}

		public Long getAlumnoId() {
			return alumnoId;
		}

		public String getAlias() {
			return alias;
		}

		public String getAliasBase() {
			return aliasBase;
		}

		public int getDiasCompletados() {
			return diasCompletados;
		}

		public LocalDate getUltimaFechaCompletado() {
			return ultimaFechaCompletado;
		}

		public Long getUltimaMarcaCompletado() {
			return ultimaMarcaCompletado;
		}

		public boolean getEsUsuarioActual() {
			return esUsuarioActual;
		}

		public Integer getPosicion() {
			return posicion;
		}

		public void setPosicion(Integer posicion) {
			this.posicion = posicion;
		}

		public void aplicarInicialSegundoApellido() {
			if (inicialSegundoApellido == null || inicialSegundoApellido.isBlank()) {
				return;
			}
			this.alias = aliasBase + " " + inicialSegundoApellido + ".";
		}
	}

	/**
	 * Elimina un alumno por su ID y elimina su imagen.
	 *
	 * @param id El ID del alumno a eliminar.
	 * @return true si se elimina con éxito, false si el alumno no existe.
	 */
	@Override
	@Transactional
	public boolean eliminarAlumno(@NonNull Long id) {
		return alumnoRepository.findById(id).map(alumno -> {
			// Primero sus datos de personas (V25): consentimientos (pueden apuntar a sus documentos, que se
			// borran en cascada con el alumno), tutores y salud. Es la supresión completa del alumno.
			consentimientoRepository.borrarDelAlumno(alumno.getId());
			tutorRepository.borrarDelAlumno(alumno.getId());
			alumnoSaludRepository.borrarDelAlumno(alumno.getId());
			// Las preinscripciones de las que salió se conservan como histórico (mismo estado), sin alumno.
			preinscripcionRepository.desvincularAlumno(alumno.getId());
			// Datos que solo existen para este alumno: su registro del reto diario y un registro de cuenta pendiente.
			alumnoRetoDiarioLogRepository.borrarDelAlumno(alumno.getId());
			registroPendienteRepository.borrarDelAlumno(alumno.getId());
			// Los vínculos de familia (usuario_alumno) caen con el alumno: ON DELETE CASCADE (V31).
			Imagen imagen = alumno.getFotoAlumno();
			if (imagen != null) {
				imagenService.eliminarImagenDeSistema(imagen);
				imagenRepository.delete(imagen);
			}

			if (alumno.getUsuario() != null) {
				// La cuenta es global: se quita solo su acceso como familia a esta escuela y se borra
				// únicamente si ya no le queda ninguna membresía (en ninguna escuela).
				Usuario usuario = alumno.getUsuario();
				alumno.setUsuario(null);
				usuario.setAlumno(null);
				if (membresiaService.retirarFamilia(usuario.getId(), alumno.getOrganizacionId())) {
					usuarioRepository.save(usuario);
				} else {
					usuarioRepository.delete(usuario);
				}
			}

			for (Grupo grupo : alumno.getGrupos()) {
				grupo.getAlumnos().remove(alumno);
				grupoRepository.save(grupo);
			}

			for (Turno turno : alumno.getTurnos()) {
				turno.getAlumnos().remove(alumno);
				turnoRepository.save(turno);
			}

			// Pieza 6b-1: antes de borrar sus cargos (los suyos y los de sus convocatorias), en esta misma transacción,
			// sus cobros ACTIVOS se anulan («cargo borrado»); los ya entregados en caja siguen activos, sin cargo.
			List<Long> cargosQueSeBorran = new ArrayList<>();
			alumno.getProductosAlumno().forEach(pa -> cargosQueSeBorran.add(pa.getId()));
			alumno.getConvocatorias().stream().map(AlumnoConvocatoria::getProductoAlumno).filter(Objects::nonNull)
					.forEach(pa -> cargosQueSeBorran.add(pa.getId()));
			cobros.alBorrarCargos(cargosQueSeBorran);

			for (ProductoAlumno productoAlumno : alumno.getProductosAlumno()) {
				productoAlumnoRepository.delete(productoAlumno);
			}

			for (AlumnoConvocatoria alumnoConvocatoria : alumno.getConvocatorias()) {
				if (alumnoConvocatoria.getProductoAlumno() != null) {
					productoAlumnoRepository.delete(alumnoConvocatoria.getProductoAlumno());
				}
				alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
			}

			alumnoRepository.delete(alumno);
			return true;
		}).orElse(false);
	}

	@Override
	public Alumno darDeBajaAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Date fechaBajaHoy = new Date();

			// Desactivar todos los deportes del alumno
			List<AlumnoDeporte> deportesDelAlumno = alumnoDeporteRepository.findByAlumnoId(id);
			for (AlumnoDeporte alumnoDeporte : deportesDelAlumno) {
				if (Boolean.TRUE.equals(alumnoDeporte.getActivo())) {
					alumnoDeporte.setActivo(false);
					alumnoDeporte.setFechaBaja(fechaBajaHoy);
					alumnoDeporteRepository.save(alumnoDeporte);
				}
			}

			// Marcar al alumno como inactivo
			alumno.setActivo(false);
			alumno.setFechaBaja(fechaBajaHoy);
			return alumnoRepository.save(alumno);
		} else {
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id);
		}
	}

	@Override
	public Alumno darDeAltaAlumno(@NonNull Long id) {
		Optional<Alumno> optionalAlumno = alumnoRepository.findById(id);
		if (optionalAlumno.isPresent()) {
			Alumno alumno = optionalAlumno.get();
			Date fechaAltaHoy = new Date();

			// Activar automáticamente el primer deporte inactivo (si existe)
			List<AlumnoDeporte> deportesDelAlumno = alumnoDeporteRepository.findByAlumnoId(id);
			List<AlumnoDeporte> deportesInactivos = deportesDelAlumno.stream()
					.filter(d -> Boolean.FALSE.equals(d.getActivo()))
					.sorted((a, b) -> {
						// Ordenar por fechaAltaInicial para activar el deporte más antiguo
						Date fechaA = a.getFechaAltaInicial() != null ? a.getFechaAltaInicial() : a.getFechaAlta();
						Date fechaB = b.getFechaAltaInicial() != null ? b.getFechaAltaInicial() : b.getFechaAlta();
						if (fechaA == null) return 1;
						if (fechaB == null) return -1;
						return fechaA.compareTo(fechaB);
					})
					.toList();

			// Si hay deportes inactivos, activar el primero (el más antiguo)
			if (!deportesInactivos.isEmpty()) {
				AlumnoDeporte deporteAActivar = deportesInactivos.get(0);
				deporteAActivar.setActivo(true);
				deporteAActivar.setFechaBaja(null);
				deporteAActivar.setFechaAlta(fechaAltaHoy);
				// fechaAltaInicial se mantiene sin cambios
				alumnoDeporteRepository.save(deporteAActivar);
			}

			// Marcar al alumno como activo
			alumno.setActivo(true);
			alumno.setFechaBaja(null);
			alumno.setFechaAlta(fechaAltaHoy);
			// fechaAltaInicial se mantiene sin cambios (conserva la fecha original de alta)
			return alumnoRepository.save(alumno);
		} else {
			throw new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id);
		}
	}

	@Override
	public List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

		// Mapeando los turnos asignados al alumno a DTOs (incluyendo grupoId y grupoNombre)
		return alumno.getTurnos().stream().map(turno -> new TurnoDTO(
				turno.getId(),
				turno.getDiaSemana(),
				turno.getHoraInicio(),
				turno.getHoraFin(),
				turno.getGrupo() != null ? turno.getGrupo().getId() : null,
				turno.getGrupo() != null ? turno.getGrupo().getNombre() : null,
				turno.getTipo()
		)).collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void asignarAlumnoATurno(Long alumnoId, Long turnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		Turno turno = turnoRepository.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

		Grupo grupoDelTurno = turno.getGrupo();

		// Verificar si el alumno ya está asignado al grupo del turno
		if (!alumno.getGrupos().contains(grupoDelTurno)) {
			// Si no está asignado, asignar al alumno al grupo del turno
			alumno.getGrupos().add(grupoDelTurno);
			grupoDelTurno.getAlumnos().add(alumno);
			grupoRepository.save(grupoDelTurno); // Guarda la relación en la tabla intermedia
		}

		// Asignar el turno al alumno si no está ya asignado
		if (!alumno.getTurnos().contains(turno)) {
			alumno.addTurno(turno);
		}

		alumnoRepository.save(alumno); // Guarda la relación en la tabla intermedia

		// Verificar y eliminar el grupo si no quedan turnos del grupo
		verificarYEliminarGrupoSiNoQuedanTurnos(alumno, grupoDelTurno);
	}

	@Override
	public void removerAlumnoDeTurno(Long alumnoId, Long turnoId) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));

		Turno turno = turnoRepository.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));

		// Verificar que el turno esté asignado al alumno antes de removerlo
		if (alumno.getTurnos().contains(turno)) {
			alumno.removeTurno(turno);
			alumnoRepository.save(alumno); // Elimina la relación en la tabla intermedia
		}

		// Verificar y eliminar el grupo si no quedan turnos del grupo
		verificarYEliminarGrupoSiNoQuedanTurnos(alumno, turno.getGrupo());
	}

	private void verificarYEliminarGrupoSiNoQuedanTurnos(Alumno alumno, Grupo grupo) {
		boolean tieneTurnosDelGrupo = alumno.getTurnos().stream().anyMatch(turno -> turno.getGrupo().equals(grupo));

		if (!tieneTurnosDelGrupo) {
			// Si no tiene turnos del grupo, eliminar el alumno de ese grupo
			alumno.getGrupos().remove(grupo);
			grupo.getAlumnos().remove(alumno);
			grupoRepository.save(grupo);
			alumnoRepository.save(alumno);
		}
	}

	// Método para obtener alumnos aptos y mapear a AlumnoConGruposDTO
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO() {
		// Busca alumnos activos que tengan al menos un deporte activo con aptoParaExamen = true
		List<Alumno> alumnos = alumnoRepository.findAlumnosAptosParaExamen();
		java.util.Optional<Set<Long>> enAlcance = gestionPorSede.idsAlumnosEnAlcance();

		return alumnos.stream()
				.filter(alumno -> enAlcance.isEmpty() || enAlcance.get().contains(alumno.getId()))
				.map(alumno -> {
					AlumnoDeporte alumnoDeporte = null;
					if (alumno.getDeportes() != null) {
						alumnoDeporte = alumno.getDeportes().stream()
								.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getAptoParaExamen()))
								.findFirst()
								.orElse(null);
					}
					return AlumnoConGruposDTO.deAlumnoConGrupos(alumno, alumnoDeporte);
				})
				.collect(Collectors.toList());
	}
	// Servicio para obtener alumnos aptos para examen por deporte, excluyendo
	// "competición"
	@Override
	public List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion) {
		String deporteEnum;
		try {
			deporteEnum = catalogoDisciplinas.validarCodigo(deporte.trim().toUpperCase().replace(" ", "_"));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}

		List<AlumnoDeporte> alumnosDeporte = alumnoDeporteService.obtenerAptosParaExamenPorDeporte(deporteEnum);
		java.util.Optional<Set<Long>> enAlcance = gestionPorSede.idsAlumnosEnAlcance();

		return alumnosDeporte.stream()
				.filter(ad -> ad.getAlumno() != null)
				.filter(ad -> enAlcance.isEmpty() || enAlcance.get().contains(ad.getAlumno().getId()))
				.map(ad -> AlumnoConGruposDTO.deAlumnoConGrupos(ad.getAlumno(), ad))
				.collect(Collectors.toList());
	}
	// Servicio para obtener un alumno apto para examen por su ID
	@Override
	public Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id) {
		Optional<Alumno> alumno = alumnoRepository.findById(id);

		if (alumno.isEmpty()) {
			return Optional.empty();
		}

		Alumno alumnoEntity = alumno.get();
		AlumnoDeporte alumnoDeporte = null;
		if (alumnoEntity.getDeportes() != null) {
			alumnoDeporte = alumnoEntity.getDeportes().stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && Boolean.TRUE.equals(ad.getAptoParaExamen()))
					.findFirst()
					.orElse(null);
		}

		if (alumnoDeporte == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(AlumnoConGruposDTO.deAlumnoConGrupos(alumnoEntity, alumnoDeporte));
	}
	/**
	 * Obtiene alumnos elegibles para una convocatoria específica
	 * basándose en el deporte y el estado de aptoParaExamen en AlumnoDeporte
	 */
	@Override
	public List<AlumnoConGruposDTO> obtenerAlumnosElegiblesParaConvocatoria(String deporte) {
		List<AlumnoDeporte> alumnosDeporte = alumnoDeporteService.obtenerAptosParaExamenPorDeporte(deporte);
		java.util.Optional<Set<Long>> enAlcance = gestionPorSede.idsAlumnosEnAlcance();

		return alumnosDeporte.stream()
				.filter(ad -> ad.getAlumno() != null)
				.filter(ad -> enAlcance.isEmpty() || enAlcance.get().contains(ad.getAlumno().getId()))
				.map(ad -> AlumnoConGruposDTO.deAlumnoConGrupos(ad.getAlumno(), ad))
				.collect(Collectors.toList());
	}

	/**
	 * Cuantía de la tarifa de la organización activa con ese código (tabla {@code tarifa}).
	 *
	 * @param tipoTarifa El tipo de tarifa del alumno.
	 * @return La cuantía asignada.
	 * @throws IllegalArgumentException si la tarifa es nula o la organización no la tiene.
	 */
	@Override
	public double asignarCuantiaTarifa(String tipoTarifa) {
		if (tipoTarifa == null) {
			throw new IllegalArgumentException("El tipo de tarifa no puede ser null");
		}
		return tarifasService.obtenerImporte(tipoTarifa).doubleValue();
	}

	/**
	 * Asigna la cuantía de la tarifa según el tipo de tarifa y el rol familiar.
	 * Regla de {@link com.taemoi.project.services.TarifasService#calcularImporte}: importe por rol
	 * si la tarifa lo tiene (PADRES_HIJOS en Taemoi: progenitor 28 €, hijo 26 €).
	 *
	 * @param tipoTarifa El tipo de tarifa del alumno.
	 * @param rolFamiliar El rol familiar del alumno (PADRE, HIJO, NINGUNO).
	 * @return La cuantía asignada.
	 */
	@Override
	public double asignarCuantiaTarifa(String tipoTarifa, RolFamiliar rolFamiliar) {
		if (tipoTarifa == null) {
			throw new IllegalArgumentException("El tipo de tarifa no puede ser null");
		}
		return tarifasService.obtenerImporte(tipoTarifa, rolFamiliar).doubleValue();
	}

	/**
	 * Asigna una categoría según la edad del alumno.
	 *
	 * @param edad La edad del alumno.
	 * @return La categoría asignada.
	 */
	@Override
	public Categoria asignarCategoriaSegunEdad(int edad) {
		// Sin deporte: taekwondo, como siempre (catálogo de categorías de su disciplina, V28).
		return catalogoCategorias.categoriaPorEdad(com.taemoi.project.services.GradoService.DISCIPLINA_POR_DEFECTO, edad)
				.orElse(null);
	}

	/**
	 * Asigna un grado según la edad del alumno y otros criterios.
	 *
	 * @param nuevoAlumnoDTO El objeto AlumnoDTO con los datos del alumno.
	 * @return El grado asignado.
	 */
	@Override
	public Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO) {
		// Sin deporte: taekwondo, como siempre. Disciplina sin grados: los de taekwondo (contrato heredado).
		String disciplina = nuevoAlumnoDTO.getDeporte() != null ? nuevoAlumnoDTO.getDeporte()
				: com.taemoi.project.services.GradoService.DISCIPLINA_POR_DEFECTO;
		boolean esMenor = catalogoDisciplinas.esMenorPorAnioNatural(nuevoAlumnoDTO.getFechaNacimiento(), disciplina);
		String disciplinaConGrados = progresionGrados.tieneProgresion(disciplina) ? disciplina
				: com.taemoi.project.services.GradoService.DISCIPLINA_POR_DEFECTO;

		// Grados del grupo de edad del alumno en el sistema de la disciplina (aplica_a)
		List<Grado> gradosDisponibles = progresionGrados.listarGradosAplicables(disciplinaConGrados, esMenor);
		if (gradosDisponibles.isEmpty()) {
			throw new IllegalStateException("La disciplina " + disciplinaConGrados + " no tiene grados configurados");
		}

		// Asignar un grado aleatoriamente de la lista de grados disponibles
		return gradosDisponibles.get(new Random().nextInt(gradosDisponibles.size()));
	}

	/**
	 * Calcula el siguiente grado para un alumno según su deporte, edad y grado actual.
	 * Progresión del catálogo (ProgresionGradosService) en el sistema de grados de su disciplina.
	 */
	@Override
	public String calcularSiguienteGrado(Alumno alumno) {
		AlumnoDeporte deporteConGrado = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());
		if (deporteConGrado == null || deporteConGrado.getGrado() == null) {
			// El principal no tiene grado (p. ej. Pilates): usar el primer deporte activo con grado
			List<AlumnoDeporte> activosConGrado = alumno.getDeportes() == null ? List.of()
					: alumno.getDeportes().stream()
							.filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && ad.getGrado() != null)
							.toList();
			deporteConGrado = AlumnoDeporteUtils.seleccionarDeportePrincipal(activosConGrado);
		}
		if (deporteConGrado == null || deporteConGrado.getGrado() == null || deporteConGrado.getDeporte() == null) {
			return null;
		}
		return calcularSiguienteGradoPorDeporte(deporteConGrado.getGrado().getCodigo(), deporteConGrado.getDeporte(),
				alumno);
	}

	/**
	 * Calcula el siguiente grado para un alumno según un deporte específico y grado actual.
	 * Útil para sistema multi-deporte.
	 */
	private String calcularSiguienteGradoPorDeporte(String codigoGradoActual, String deporte, Alumno alumno) {
		String disciplina = deporte == null ? null : deporte;
		// Grupo de edad por disciplina.edad_adulto (año natural); siguiente grado en su sistema
		boolean esMenor = catalogoDisciplinas.esMenorPorAnioNatural(alumno.getFechaNacimiento(), disciplina);
		return progresionGrados.obtenerSiguienteGrado(disciplina, esMenor, codigoGradoActual)
				.map(Grado::getCodigo)
				.orElse(null);
	}

	private String resolverConceptoProducto(String deporte, String gradoSiguiente, boolean porRecompensa,
			boolean rojoBordado) {
		if (CatalogoLegadoTaemoi.GRADO_ROJO.equals(gradoSiguiente)) {
			if (porRecompensa) {
				return rojoBordado ? DERECHO_RECOMPENSA_ROJO_BORDADO : DERECHO_RECOMPENSA_ROJO;
			}
			return rojoBordado ? DERECHO_EXAMEN_ROJO_BORDADO : DERECHO_EXAMEN_ROJO;
		}
		String legado = CatalogoLegadoTaemoi.productoGradoLegado(gradoSiguiente, porRecompensa);
		if (legado == null && comportamientoLegado.activo()
				&& CatalogoLegadoTaemoi.nombreGradoLegado(gradoSiguiente) != null) {
			// Lo que lanzaba TipoGrado.obtenerNombreProducto para un grado de Taemoi sin ese producto, con el
			// nombre del grado en el catálogo (D3).
			String nombre = progresionGrados.buscarGrado(deporte, gradoSiguiente).map(Grado::getNombre)
					.orElse(gradoSiguiente);
			throw new IllegalArgumentException("El grado " + nombre
					+ " no tiene producto de " + (porRecompensa ? "recompensa" : "examen") + " asociado");
		}
		return legado;
	}

	/**
	 * Producto que se cobra para llegar al siguiente grado, del catálogo
	 * ({@link com.taemoi.project.services.ProductosCatalogoService#resolverProductoGrado}): enlace del
	 * grado o codigo; si no hay, el nombre que buscaba el código hasta V27 ({@link #resolverConceptoProducto}).
	 */
	private Producto buscarProductoDeGrado(String deporte, String gradoSiguiente, boolean porRecompensa,
			boolean rojoBordado) {
		String conceptoProducto = resolverConceptoProducto(deporte, gradoSiguiente, porRecompensa, rojoBordado);
		boolean bordado = rojoBordado && CatalogoLegadoTaemoi.GRADO_ROJO.equals(gradoSiguiente);
		return productosCatalogo
				.resolverProductoGrado(deporte == null ? null : deporte, gradoSiguiente, porRecompensa,
						bordado, conceptoProducto)
				.orElseThrow(() -> new IllegalArgumentException(
						"Producto no encontrado para el grado: " + gradoSiguiente + " (" + conceptoProducto + ")"));
	}

	@Override
	@Transactional
	public AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId, boolean porRecompensa,
			boolean rojoBordado) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada"));
		// D6-17 (entrega 6-3): apuntarle crea el cargo del examen en su sede principal; si está inactiva, 400.
		gestionPorSede.exigirSedeActiva(alumno.getSedePrincipalId());

		// Find the AlumnoDeporte for the sport of the convocatoria
		AlumnoDeporte alumnoDeporte = alumno.getDeportes().stream()
				.filter(ad -> java.util.Objects.equals(ad.getDeporte(), convocatoria.getDeporte()) && Boolean.TRUE.equals(ad.getActivo()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no está activo en el deporte " + convocatoria.getDeporte()));

		// Verify the alumno is eligible for exam in this sport
		if (!Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
			throw new IllegalArgumentException("El alumno no está apto para examen en el deporte " + convocatoria.getDeporte());
		}

		// Calculate next grade based on the AlumnoDeporte's current grade
		String gradoActual = alumnoDeporte.getGrado().getTipoGrado();
		String gradoSiguiente = calcularSiguienteGradoPorDeporte(alumnoDeporte.getGrado().getCodigo(),
				convocatoria.getDeporte(), alumno);
		if (gradoSiguiente == null) {
			throw new IllegalArgumentException("No se pudo determinar el siguiente grado del alumno para el deporte " + convocatoria.getDeporte());
		}

		Producto producto = buscarProductoDeGrado(convocatoria.getDeporte(), gradoSiguiente, porRecompensa, rojoBordado);

		// Construir concepto con el nombre del deporte al final
		String conceptoConDeporte = com.taemoi.project.services.CatalogoLegadoTaemoi.conceptoBase(producto, null, comportamientoLegado.activo()) + " - "
				+ formatearNombreDeporte(convocatoria.getDeporte());

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(conceptoConDeporte);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setPagado(false);
		productoAlumnoRepository.save(productoAlumno);

		AlumnoConvocatoria alumnoConvocatoria = new AlumnoConvocatoria();
		alumnoConvocatoria.setAlumno(alumno);
		alumnoConvocatoria.setConvocatoria(convocatoria);
		alumnoConvocatoria.setProductoAlumno(productoAlumno);
		alumnoConvocatoria.setCuantiaExamen(producto.getPrecio());
		alumnoConvocatoria.setGradoActual(gradoActual);
		alumnoConvocatoria.setGradoSiguiente(gradoSiguiente);
		alumnoConvocatoria.setAlumnoDeporte(alumnoDeporte);
		alumnoConvocatoriaRepository.save(alumnoConvocatoria);

		return convertirAAlumnoConvocatoriaDTO(alumnoConvocatoria);
	}

	@Override
	@Transactional
	public void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId) {
		AlumnoConvocatoria alumnoConvocatoria = alumnoConvocatoriaRepository
				.findByConvocatoriaIdAndAlumnoId(convocatoriaId, alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("El alumno no está inscrito en esta convocatoria"));

		ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
		if (productoAlumno != null) {
			// Pieza 6b-1: su cobro ACTIVO se anula («cargo borrado») en esta transacción, antes de borrar el cargo.
			cobros.alBorrarCargos(List.of(productoAlumno.getId()));
			alumnoConvocatoria.setProductoAlumno(null);
			alumnoConvocatoriaRepository.save(alumnoConvocatoria);
			productoAlumnoRepository.delete(productoAlumno);
		}

		alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
	}

	@Override
	@Transactional
	public AlumnoDeporte pasarGradoPorRecompensa(Long alumnoId, String deporte, boolean rojoBordado) {
		return pasarGradoInterno(alumnoId, deporte, rojoBordado, true);
	}

	@Override
	@Transactional
	public AlumnoDeporte pasarGradoConDerechoExamen(Long alumnoId, String deporte, boolean rojoBordado) {
		return pasarGradoInterno(alumnoId, deporte, rojoBordado, false);
	}

	/**
	 * Método interno para pasar de grado (por recompensa o derecho de examen)
	 */
	private AlumnoDeporte pasarGradoInterno(Long alumnoId, String deporte, boolean rojoBordado, boolean porRecompensa) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado con ID: " + alumnoId));

		AlumnoDeporte alumnoDeporte = alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, deporte)
				.orElseThrow(() -> new IllegalArgumentException(
						"El alumno no tiene asignado el deporte: " + deporte));
		// D6-17 (entrega 6-3): el pase crea un cargo en su sede principal; si está inactiva, 400.
		gestionPorSede.exigirSedeActiva(alumno.getSedePrincipalId());

		// Validar que el deporte admite grados (disciplina sin sistema de grados)
		if (!progresionGrados.tieneProgresion(deporte == null ? null : deporte)) {
			throw new IllegalArgumentException("El deporte " + deporte + " no tiene sistema de grados");
		}

		// Verificar que el alumno está apto para examen en este deporte
		if (!Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
			throw new IllegalArgumentException(
					"El alumno no está apto para examen en el deporte " + deporte);
		}

		String gradoActual = alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado() : null;
		if (gradoActual == null) {
			throw new IllegalArgumentException("El alumno no tiene grado asignado en el deporte " + deporte);
		}

		// Calcular siguiente grado con la misma lógica por edad y deporte
		String gradoSiguiente = calcularSiguienteGradoPorDeporte(alumnoDeporte.getGrado().getCodigo(), deporte,
				alumno);
		if (gradoSiguiente == null) {
			throw new IllegalArgumentException(
					"No se pudo determinar el siguiente grado del alumno para el deporte " + deporte);
		}

		// Obtener producto asociado al siguiente grado (recompensa o derecho de examen)
		Producto producto = buscarProductoDeGrado(deporte, gradoSiguiente, porRecompensa, rojoBordado);

		// Construir concepto con el nombre del deporte al final
		String conceptoConDeporte = com.taemoi.project.services.CatalogoLegadoTaemoi.conceptoBase(producto, null, comportamientoLegado.activo()) + " - "
				+ formatearNombreDeporte(deporte);

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setAlumnoDeporte(alumnoDeporte);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(conceptoConDeporte);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setPagado(false);
		productoAlumnoRepository.save(productoAlumno);

		// No actualizar el grado aqui; se actualizara cuando el producto se marque como pagado
		return alumnoDeporte;
	}

	/**
	 * Formatea el nombre del deporte para mostrarlo en el concepto
	 */
	private String formatearNombreDeporte(String deporte) {
		// El código de la disciplina tal cual (antes el nombre del enum Deporte, que coincidía).
		return deporte;
	}


	/**
	 * Verifica si la fecha de nacimiento es válida.
	 *
	 * @param fechaNacimiento La fecha de nacimiento a verificar.
	 * @return true si la fecha de nacimiento es válida, false si no lo es.
	 */
	@Override
	public boolean fechaNacimientoValida(Date fechaNacimiento) {
		Calendar fechaActualMenos3Anios = Calendar.getInstance();
		fechaActualMenos3Anios.add(Calendar.YEAR, -3);

		Calendar fechaNacimientoCalendar = Calendar.getInstance();
		fechaNacimientoCalendar.setTime(fechaNacimiento);

		return fechaNacimientoCalendar.before(fechaActualMenos3Anios);
	}

	/**
	 * Verifica si los datos del alumno son válidos.
	 *
	 * @param alumnoDTO El objeto AlumnoDTO con los datos del alumno.
	 * @return true si los datos son válidos, false si no lo son.
	 */
	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

	@Override
	public boolean datosAlumnoValidos(AlumnoDTO alumnoDTO) {
		return datosAlumnoValidos(alumnoDTO, true);
	}

	@Override
	public boolean datosAlumnoValidos(AlumnoDTO alumnoDTO, boolean requiereTarifaYFechaAlta) {
		if (alumnoDTO.getNombre() == null || alumnoDTO.getNombre().isEmpty() || alumnoDTO.getApellidos() == null
				|| alumnoDTO.getApellidos().isEmpty()) {
			return false;
		}
		if (alumnoDTO.getFechaNacimiento() == null || alumnoDTO.getFechaNacimiento().after(new Date())) {
			return false;
		}

		if (alumnoDTO.getDireccion() == null || alumnoDTO.getDireccion().isEmpty() || alumnoDTO.getEmail() == null
				|| alumnoDTO.getEmail().isEmpty()) {
			return false;
		}
		if (TelefonoUtils.normalizar(alumnoDTO.getTelefono()) != null && !TelefonoUtils.esValido(alumnoDTO.getTelefono())) {
			return false;
		}
		if (TelefonoUtils.normalizar(alumnoDTO.getTelefono2()) != null && !TelefonoUtils.esValido(alumnoDTO.getTelefono2())) {
			return false;
		}
		if (alumnoDTO.getCodigoPostal() != null && alumnoDTO.getCodigoPostal().strip().length() > 10) {
			return false;
		}
		if (alumnoDTO.getLocalidad() != null && alumnoDTO.getLocalidad().strip().length() > 100) {
			return false;
		}
		if (requiereTarifaYFechaAlta) {
			if (alumnoDTO.getTipoTarifa() == null) {
				return false;
			}
			if (alumnoDTO.getFechaAlta() == null || alumnoDTO.getFechaAlta().after(new Date())) {
				return false;
			}
		} else if (alumnoDTO.getFechaAlta() != null && alumnoDTO.getFechaAlta().after(new Date())) {
			return false;
		}
		return true;
	}

	/**
	 * Genera una contraseña codificada a partir del nombre y apellidos de un
	 * usuario.
	 *
	 * @param nombre    El nombre del usuario.
	 * @param apellidos Los apellidos del usuario.
	 * @return La contraseña codificada generada a partir del nombre y apellidos.
	 */
	@Override
	public String generarContrasena(String nombre, String apellidos) {
		String cadena = (nombre + apellidos).toLowerCase();
		return passwordEncoder.encode(cadena);
	}

	/**
	 * Indica si el alumno es apto para examen en alguno de sus deportes activos. La aptitud
	 * se calcula y guarda por deporte en {@link AlumnoDeporte#getAptoParaExamen()}.
	 *
	 * @param alumno El alumno a comprobar.
	 * @return true si algún deporte activo del alumno está marcado como apto para examen.
	 */
	@Override
	public boolean esAptoParaExamen(Alumno alumno) {
		return alumno.getDeportes() != null && alumno.getDeportes().stream()
				.filter(ad -> Boolean.TRUE.equals(ad.getActivo()))
				.anyMatch(ad -> Boolean.TRUE.equals(ad.getAptoParaExamen()));
	}

	private void asignarMensualidadGeneralSiCorresponde(Alumno alumno) {
	    LocalDate fechaActual = LocalDate.now();
	    String mesAno = fechaActual.getYear() + "-" + "%02d".formatted(fechaActual.getMonthValue());

	    AlumnoDeporte deportePrincipal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());
	    String disciplina = deportePrincipal != null && deportePrincipal.getDeporte() != null
	            ? deportePrincipal.getDeporte() : null;
	    // Producto de mensualidad del catálogo (o, sin catálogo, el de concepto «MENSUALIDAD»)
	    var optionalProductoMensualidad = productosCatalogo.resolverProductoMensualidad(disciplina);
	    String nombreProducto = com.taemoi.project.services.CatalogoLegadoTaemoi.conceptoBase(
	            optionalProductoMensualidad.orElse(null),
	            com.taemoi.project.services.ProductosCatalogoService.CONCEPTO_LEGADO_MENSUALIDAD,
	            comportamientoLegado.activo());
	    String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(nombreProducto, mesAno);

	    boolean mensualidadCargada = productoAlumnoRepository.existsByConcepto(nombreMensualidad);

	    if (mensualidadCargada) {
	        if (optionalProductoMensualidad.isEmpty()) {
	            System.out.println("ADVERTENCIA: Producto 'MENSUALIDAD' no encontrado. " +
	                    "Se omitirá la asignación automática de mensualidad para el alumno " + alumno.getId());
	            return;
	        }

	        Producto productoMensualidad = optionalProductoMensualidad.get();

	        ProductoAlumno productoAlumno = new ProductoAlumno();
	        productoAlumno.setAlumno(alumno);
	        productoAlumno.setProducto(productoMensualidad);
	        productoAlumno.setConcepto(nombreMensualidad);
	        productoAlumno.setAlumnoDeporte(deportePrincipal);
	        // Entrega 6-4: la tarifa de la sede del alumno para el mes en curso, si la hay (D6-27 a D6-29); si no, la
	        // cuantía de la inscripción tal cual.
	        productoAlumno.setPrecio(deportePrincipal != null
	                ? tarifaSede.preparar().cuantiaMensualidad(deportePrincipal, alumno, YearMonth.from(fechaActual))
	                : null);
	        productoAlumno.setFechaAsignacion(Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant()));
	        productoAlumno.setCantidad(1);
	        productoAlumno.setPagado(false);

	        productoAlumnoRepository.save(productoAlumno);
	    }
	}

	private AlumnoConvocatoriaDTO convertirAAlumnoConvocatoriaDTO(AlumnoConvocatoria alumnoConvocatoria) {
		AlumnoConvocatoriaDTO dto = new AlumnoConvocatoriaDTO();
		dto.setAlumnoId(alumnoConvocatoria.getAlumno().getId());
		dto.setNombre(alumnoConvocatoria.getAlumno().getNombre());
		dto.setApellidos(alumnoConvocatoria.getAlumno().getApellidos());
		dto.setCuantiaExamen(alumnoConvocatoria.getCuantiaExamen());
		dto.setGradoActual(alumnoConvocatoria.getGradoActual());
		dto.setGradoSiguiente(alumnoConvocatoria.getGradoSiguiente());
		dto.setPagado(alumnoConvocatoria.getPagado());
		return dto;
	}

	@Override
	public long countAlumnos() {
		java.util.Optional<com.taemoi.project.organizacion.AlcanceSesion> restriccion = gestionPorSede.restriccion();
		if (restriccion.isEmpty()) {
			return alumnoRepository.count();
		}
		return alumnoRepository.count(com.taemoi.project.acceso.PredicadosAlcance.alumnosDeSusSedes(restriccion.get()));
	}

	/**
	 * Builds a JPA Specification for filtering Alumno entities.
	 * Extracted to avoid code duplication between paginated and non-paginated filter methods.
	 *
	 * @param nombre          Name filter (searches in nombre, apellidos, and full name)
	 * @param gradoId         Grado ID filter
	 * @param categoriaId     Categoria ID filter
	 * @param incluirInactivos Whether to include inactive students
	 * @param aptoParaExamen  Whether to filter only students eligible for exam
	 * @return Specification for filtering Alumno
	 */
	private org.springframework.data.jpa.domain.Specification<Alumno> buildAlumnoSpecification(
			String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos, boolean aptoParaExamen) {
		// El filtro por grado compara por codigo: el id que envía la aplicación es el de
		// /api/grados (taekwondo) y debe encontrar también a los alumnos de kickboxing, cuyos
		// grados están en su propio sistema (V26). Un id que no existe en la escuela no encuentra a nadie.
		final boolean filtrarPorGrado = gradoId != null;
		final String codigoGradoFiltro = gradoId == null ? null
				: gradoRepository.findById(gradoId).map(Grado::getCodigo).orElse(null);
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (nombre != null && !nombre.isEmpty()) {
				String nombreLower = nombre.toLowerCase();

				// Build full name expression (nombre + ' ' + apellidos)
				Expression<String> fullNameExpression = criteriaBuilder
						.concat(criteriaBuilder.lower(root.get("nombre")), criteriaBuilder.literal(" "));
				fullNameExpression = criteriaBuilder.concat(fullNameExpression,
						criteriaBuilder.lower(root.get("apellidos")));

				// Create predicate comparing full name with search value
				Predicate fullNamePredicate = criteriaBuilder.like(fullNameExpression, "%" + nombreLower + "%");

				// Also compare nombre and apellidos individually
				Predicate nombrePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("nombre")),
						"%" + nombreLower + "%");
				Predicate apellidosPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("apellidos")),
						"%" + nombreLower + "%");

				// Combine predicates with OR
				predicates.add(criteriaBuilder.or(fullNamePredicate, nombrePredicate, apellidosPredicate));
			}

			if (filtrarPorGrado || categoriaId != null) {
				jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
				jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> deporteRoot = subquery.from(com.taemoi.project.entities.AlumnoDeporte.class);
				List<Predicate> deportePredicates = new ArrayList<>();

				deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("alumno").get("id"), root.get("id")));
				deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("activo"), true));

				if (filtrarPorGrado) {
					deportePredicates.add(codigoGradoFiltro == null ? criteriaBuilder.disjunction()
							: criteriaBuilder.equal(deporteRoot.get("grado").get("codigo"), codigoGradoFiltro));
				}
				if (categoriaId != null) {
					deportePredicates.add(criteriaBuilder.equal(deporteRoot.get("categoria").get("id"), categoriaId));
				}

				subquery.select(deporteRoot.get("alumno").get("id"))
						.where(criteriaBuilder.and(deportePredicates.toArray(new Predicate[0])));
				predicates.add(criteriaBuilder.exists(subquery));
			}

			if (!incluirInactivos) {
				predicates.add(criteriaBuilder.equal(root.get("activo"), true));
			}

			// Filter by aptoParaExamen - join with deportes to check if any has aptoParaExamen = true
			if (aptoParaExamen) {
				// Use subquery to check if alumno has at least one deporte with aptoParaExamen = true
				jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
				jakarta.persistence.criteria.Root<com.taemoi.project.entities.AlumnoDeporte> deporteRoot = subquery.from(com.taemoi.project.entities.AlumnoDeporte.class);
				subquery.select(deporteRoot.get("alumno").get("id"))
						.where(criteriaBuilder.and(
								criteriaBuilder.equal(deporteRoot.get("alumno").get("id"), root.get("id")),
								criteriaBuilder.equal(deporteRoot.get("aptoParaExamen"), true),
								criteriaBuilder.equal(deporteRoot.get("activo"), true)
						));
				predicates.add(criteriaBuilder.exists(subquery));
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}

	// ==================== IMPLEMENTACIÓN MÉTODOS MULTI-DEPORTE ====================

	@Override
	public List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId) {
		return alumnoDeporteService.obtenerDeportesDelAlumno(alumnoId);
	}

	@Override
	public List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId) {
		return alumnoDeporteService.obtenerDeportesActivosDelAlumno(alumnoId);
	}

	@Override
	public com.taemoi.project.entities.AlumnoDeporte agregarDeporteAAlumno(Long alumnoId,
			String deporte,
			String gradoInicial,
			java.util.Date fechaAlta,
			java.util.Date fechaAltaInicial,
			java.util.Date fechaGrado) {
		return alumnoDeporteService.agregarDeporteAAlumno(
				alumnoId,
				deporte,
				gradoInicial,
				fechaAlta,
				fechaAltaInicial,
				fechaGrado
		);
	}

	@Override
	public void removerDeporteDeAlumno(Long alumnoId, String deporte) {
		alumnoDeporteService.removerDeporteDeAlumno(alumnoId, deporte);
	}

	@Override
	public com.taemoi.project.entities.AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId,
			String deporte,
			String nuevoGrado) {
		return alumnoDeporteService.actualizarGradoPorDeporte(alumnoId, deporte, nuevoGrado);
	}

	@Override
	@Transactional
	public Alumno actualizarFechaAltaInicial(@NonNull Long id, Date nuevaFechaAltaInicial) {
		Alumno alumno = alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));

		// Validación opcional: advertir si fecha es posterior a la fecha de alta de algún deporte
		if (alumno.getDeportes() != null) {
			for (com.taemoi.project.entities.AlumnoDeporte deporte : alumno.getDeportes()) {
				if (deporte.getFechaAlta() != null && nuevaFechaAltaInicial.after(deporte.getFechaAlta())) {
					logger.warn("fechaAltaInicial ({}) is after fechaAlta ({}) for sport {}",
							nuevaFechaAltaInicial, deporte.getFechaAlta(), deporte.getDeporte());
					// Permitir pero registrar advertencia
				}
			}
		}

		alumno.setFechaAltaInicial(nuevaFechaAltaInicial);
		return alumnoRepository.save(alumno);
	}

	@Override
	public Alumno buscarAlumno(@NonNull Long id) {
		return alumnoRepository.findById(id)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
	}
}
