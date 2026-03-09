package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.dtos.response.AuditoriaEventoDetalleDTO;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.AuditoriaEvento;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AuditoriaEventoRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.UsuarioService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class AuditoriaServiceImpl implements AuditoriaService {

	private static final Logger logger = LoggerFactory.getLogger(AuditoriaServiceImpl.class);
	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 200;
	private static final Set<String> MODULOS_API_CONOCIDOS = Set.of(
			"admin",
			"auth",
			"alumnos",
			"eventos",
			"grados",
			"grupos",
			"informes",
			"mail",
			"pagos",
			"productos",
			"productos-alumno",
			"tesoreria",
			"turnos",
			"convocatorias");
	private static final Set<Integer> ESTADOS_HTTP_RUIDO_ESCANER = Set.of(401, 403, 404, 405, 415);
	private static final List<String> FRAGMENTOS_ENDPOINT_RUIDO_ESCANER = List.of(
			"/upload",
			"/files",
			"/documents",
			"/attachments",
			"/multipart",
			"/bulk",
			"/batch",
			"/blob",
			"/storage",
			"/assets",
			"/resources",
			"/catalog",
			"/content",
			"/import",
			"/drive",
			"/s3",
			"/v1/",
			"/v2/");

	private final AuditoriaEventoRepository auditoriaEventoRepository;
	private final UsuarioService usuarioService;

	public AuditoriaServiceImpl(
			AuditoriaEventoRepository auditoriaEventoRepository,
			UsuarioService usuarioService) {
		this.auditoriaEventoRepository = auditoriaEventoRepository;
		this.usuarioService = usuarioService;
	}

	@Override
	@Transactional
	public void registrarEvento(
			AuditoriaAccion accion,
			String metodoHttp,
			String endpoint,
			String queryParamsJson,
			String payloadJson,
			boolean payloadTruncado,
			Integer estadoHttp,
			String ipCliente,
			String userAgent) {
		try {
			AuditoriaEvento evento = new AuditoriaEvento();
			evento.setFechaEvento(new Date());
			evento.setAccion(accion);
			evento.setMetodoHttp(metodoHttp);
			evento.setEndpoint(endpoint);
			evento.setEstadoHttp(estadoHttp);
			evento.setIpCliente(ipCliente);
			evento.setUserAgent(userAgent);
			evento.setQueryParamsJson(queryParamsJson);
			evento.setPayloadJson(payloadJson);
			evento.setPayloadTruncado(payloadTruncado);

			enriquecerContextoUsuario(evento);
			enriquecerModuloYRecurso(evento, endpoint);
			evento.setResumen(construirResumen(evento));

			auditoriaEventoRepository.save(evento);
		} catch (Exception ex) {
			logger.error("No se pudo registrar evento de auditoria", ex);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AuditoriaEventoDTO> obtenerEventos(
			LocalDate desde,
			LocalDate hasta,
			String resultado,
			String accion,
			String modulo,
			String usuario,
			String endpoint,
			String texto,
			Integer page,
			Integer size,
			Boolean incluirRuido) {
		Specification<AuditoriaEvento> spec = Specification.where(null);
		spec = spec.and(filtroFechaDesde(desde))
				.and(filtroFechaHasta(hasta))
				.and(filtroResultado(resultado))
				.and(filtroAccion(accion))
				.and(filtroModulo(modulo))
				.and(filtroUsuario(usuario))
				.and(filtroEndpoint(endpoint))
				.and(filtroTextoLibre(texto));
		if (!Boolean.TRUE.equals(incluirRuido)) {
			spec = spec.and(filtroExcluirRuidoEscaner());
		}

		int pageNumber = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		Pageable pageable = PageRequest.of(pageNumber - 1, pageSize,
				Sort.by(Sort.Order.desc("fechaEvento"), Sort.Order.desc("id")));

		return auditoriaEventoRepository.findAll(spec, pageable).map(this::convertirAResumenDTO);
	}

	@Override
	@Transactional(readOnly = true)
	public AuditoriaEventoDetalleDTO obtenerEventoPorId(Long id) {
		AuditoriaEvento evento = auditoriaEventoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado"));
		return convertirADetalleDTO(evento);
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> obtenerModulosDisponibles() {
		return auditoriaEventoRepository.findDistinctModulos();
	}

	@Override
	@Transactional
	public long eliminarEventosAnterioresA(LocalDate fechaLimite) {
		LocalDateTime fechaHora = fechaLimite.atStartOfDay();
		Date limiteDate = Date.from(fechaHora.atZone(ZoneId.systemDefault()).toInstant());
		return auditoriaEventoRepository.deleteByFechaEventoBefore(limiteDate);
	}

	private AuditoriaEventoDTO convertirAResumenDTO(AuditoriaEvento evento) {
		AuditoriaEventoDTO dto = new AuditoriaEventoDTO();
		dto.setId(evento.getId());
		dto.setFechaEvento(evento.getFechaEvento());
		dto.setAccion(evento.getAccion() != null ? evento.getAccion().name() : null);
		dto.setMetodoHttp(evento.getMetodoHttp());
		dto.setEndpoint(evento.getEndpoint());
		dto.setModulo(evento.getModulo());
		dto.setRecursoId(evento.getRecursoId());
		dto.setEstadoHttp(evento.getEstadoHttp());
		dto.setUsuarioId(evento.getUsuarioId());
		dto.setUsuarioEmail(evento.getUsuarioEmail());
		dto.setUsuarioNombre(evento.getUsuarioNombre());
		dto.setResumen(evento.getResumen());
		dto.setPayloadTruncado(evento.getPayloadTruncado());
		dto.setRuidoEscaner(esRuidoEscaner(evento));
		return dto;
	}

	private AuditoriaEventoDetalleDTO convertirADetalleDTO(AuditoriaEvento evento) {
		AuditoriaEventoDetalleDTO dto = new AuditoriaEventoDetalleDTO();
		dto.setId(evento.getId());
		dto.setFechaEvento(evento.getFechaEvento());
		dto.setAccion(evento.getAccion() != null ? evento.getAccion().name() : null);
		dto.setMetodoHttp(evento.getMetodoHttp());
		dto.setEndpoint(evento.getEndpoint());
		dto.setModulo(evento.getModulo());
		dto.setRecursoId(evento.getRecursoId());
		dto.setEstadoHttp(evento.getEstadoHttp());
		dto.setUsuarioId(evento.getUsuarioId());
		dto.setUsuarioEmail(evento.getUsuarioEmail());
		dto.setUsuarioNombre(evento.getUsuarioNombre());
		dto.setIpCliente(evento.getIpCliente());
		dto.setUserAgent(evento.getUserAgent());
		dto.setQueryParamsJson(evento.getQueryParamsJson());
		dto.setPayloadJson(evento.getPayloadJson());
		dto.setPayloadTruncado(evento.getPayloadTruncado());
		dto.setResumen(evento.getResumen());
		dto.setRuidoEscaner(esRuidoEscaner(evento));
		return dto;
	}

	private void enriquecerContextoUsuario(AuditoriaEvento evento) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return;
		}

		Object principal = authentication.getPrincipal();
		if (principal instanceof Usuario usuarioPrincipal) {
			evento.setUsuarioId(usuarioPrincipal.getId());
			evento.setUsuarioEmail(usuarioPrincipal.getEmail());
			evento.setUsuarioNombre(construirNombreCompleto(usuarioPrincipal.getNombre(), usuarioPrincipal.getApellidos()));
			return;
		}

		String email = null;
		if (principal instanceof UserDetails userDetails) {
			email = userDetails.getUsername();
		} else if (principal instanceof String principalName && !"anonymousUser".equalsIgnoreCase(principalName)) {
			email = principalName;
		}

		if (email == null || email.isBlank()) {
			return;
		}

		Optional<Usuario> usuarioOpt = usuarioService.encontrarPorEmail(email);
		if (usuarioOpt.isPresent()) {
			Usuario usuario = usuarioOpt.get();
			evento.setUsuarioId(usuario.getId());
			evento.setUsuarioEmail(usuario.getEmail());
			evento.setUsuarioNombre(construirNombreCompleto(usuario.getNombre(), usuario.getApellidos()));
		} else {
			evento.setUsuarioEmail(email);
		}
	}

	private void enriquecerModuloYRecurso(AuditoriaEvento evento, String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return;
		}

		String normalizado = endpoint.trim();
		String[] segmentos = normalizado.split("/");
		int indiceApi = -1;
		for (int i = 0; i < segmentos.length; i++) {
			if ("api".equalsIgnoreCase(segmentos[i])) {
				indiceApi = i;
				break;
			}
		}

		if (indiceApi >= 0 && indiceApi + 1 < segmentos.length) {
			evento.setModulo(segmentos[indiceApi + 1].toLowerCase(Locale.ROOT));
		}

		for (int i = Math.max(indiceApi + 2, 0); i < segmentos.length; i++) {
			String segmento = segmentos[i];
			if (segmento != null && segmento.matches("\\d+")) {
				try {
					evento.setRecursoId(Long.parseLong(segmento));
				} catch (NumberFormatException ex) {
					// Ignorar parseo no valido
				}
				break;
			}
		}
	}

	private String construirResumen(AuditoriaEvento evento) {
		String modulo = evento.getModulo() == null ? "sistema" : evento.getModulo();
		String recurso = evento.getRecursoId() == null ? "" : (" #" + evento.getRecursoId());
		return String.format(
				Locale.ROOT,
				"%s %s%s -> %s",
				evento.getAccion() != null ? evento.getAccion().name() : "OPERACION",
				modulo,
				recurso,
				evento.getEstadoHttp());
	}

	private String construirNombreCompleto(String nombre, String apellidos) {
		String nombreClean = nombre == null ? "" : nombre.trim();
		String apellidosClean = apellidos == null ? "" : apellidos.trim();
		String nombreCompleto = (nombreClean + " " + apellidosClean).trim();
		return nombreCompleto.isBlank() ? null : nombreCompleto;
	}

	private Specification<AuditoriaEvento> filtroFechaDesde(LocalDate desde) {
		if (desde == null) {
			return null;
		}
		Date inicio = Date.from(desde.atStartOfDay(ZoneId.systemDefault()).toInstant());
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEvento"), inicio);
	}

	private Specification<AuditoriaEvento> filtroFechaHasta(LocalDate hasta) {
		if (hasta == null) {
			return null;
		}
		Date fin = Date.from(hasta.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaEvento"), fin);
	}

	private Specification<AuditoriaEvento> filtroAccion(String accion) {
		if (accion == null || accion.isBlank()) {
			return null;
		}
		AuditoriaAccion accionEnum;
		try {
			accionEnum = AuditoriaAccion.valueOf(accion.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accion de auditoria no valida: " + accion);
		}

		return (root, query, cb) -> cb.equal(root.get("accion"), accionEnum);
	}

	private Specification<AuditoriaEvento> filtroResultado(String resultado) {
		if (resultado == null || resultado.isBlank() || "TODOS".equalsIgnoreCase(resultado)) {
			return null;
		}

		String resultadoNormalizado = resultado.trim().toUpperCase(Locale.ROOT);
		return switch (resultadoNormalizado) {
		case "EXITO" -> (root, query, cb) -> cb.between(root.get("estadoHttp"), 200, 299);
		case "ERROR" -> (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("estadoHttp"), 400);
		default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Resultado de auditoria no valido: " + resultado);
		};
	}

	private Specification<AuditoriaEvento> filtroModulo(String modulo) {
		if (modulo == null || modulo.isBlank()) {
			return null;
		}
		String valor = modulo.trim().toUpperCase(Locale.ROOT);
		return (root, query, cb) -> cb.equal(cb.upper(root.get("modulo")), valor);
	}

	private Specification<AuditoriaEvento> filtroUsuario(String usuario) {
		if (usuario == null || usuario.isBlank()) {
			return null;
		}
		String valor = "%" + usuario.trim().toUpperCase(Locale.ROOT) + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.upper(cb.coalesce(root.get("usuarioEmail"), "")), valor),
				cb.like(cb.upper(cb.coalesce(root.get("usuarioNombre"), "")), valor));
	}

	private Specification<AuditoriaEvento> filtroEndpoint(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		String valor = "%" + endpoint.trim().toUpperCase(Locale.ROOT) + "%";
		return (root, query, cb) -> cb.like(cb.upper(cb.coalesce(root.get("endpoint"), "")), valor);
	}

	private Specification<AuditoriaEvento> filtroTextoLibre(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String valor = "%" + texto.trim().toUpperCase(Locale.ROOT) + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.upper(cb.coalesce(root.get("resumen"), "")), valor),
				cb.like(cb.upper(cb.coalesce(root.get("payloadJson"), "")), valor),
				cb.like(cb.upper(cb.coalesce(root.get("queryParamsJson"), "")), valor));
	}

	private Specification<AuditoriaEvento> filtroExcluirRuidoEscaner() {
		return (root, query, cb) -> cb.not(construirPredicadoRuidoEscaner(root, cb));
	}

	private Predicate construirPredicadoRuidoEscaner(Root<AuditoriaEvento> root, CriteriaBuilder cb) {
		Expression<String> endpointLower = cb.lower(cb.coalesce(root.get("endpoint").as(String.class), ""));
		Expression<String> moduloLower = cb.lower(cb.coalesce(root.get("modulo").as(String.class), ""));
		Expression<String> usuarioEmailLimpio = cb.trim(cb.coalesce(root.get("usuarioEmail").as(String.class), ""));

		Predicate endpointApi = cb.like(endpointLower, "/api/%");
		Predicate endpointSospechoso = cb.disjunction();
		for (String fragmento : FRAGMENTOS_ENDPOINT_RUIDO_ESCANER) {
			endpointSospechoso = cb.or(endpointSospechoso, cb.like(endpointLower, "%" + fragmento + "%"));
		}

		Predicate estadoSospechoso = root.get("estadoHttp").in(ESTADOS_HTTP_RUIDO_ESCANER);
		Predicate accionEscritura = root.get("accion").in(
				AuditoriaAccion.CREATE,
				AuditoriaAccion.UPDATE,
				AuditoriaAccion.DELETE);
		Predicate anonimo = cb.and(
				cb.isNull(root.get("usuarioId")),
				cb.equal(cb.length(usuarioEmailLimpio), 0));
		Predicate moduloDesconocido = cb.not(moduloLower.in(MODULOS_API_CONOCIDOS));

		return cb.and(
				endpointApi,
				endpointSospechoso,
				estadoSospechoso,
				accionEscritura,
				anonimo,
				moduloDesconocido);
	}

	private boolean esRuidoEscaner(AuditoriaEvento evento) {
		if (evento == null) {
			return false;
		}
		String endpoint = normalizarTexto(evento.getEndpoint());
		if (!endpoint.startsWith("/api/")) {
			return false;
		}
		if (!esAccionEscritura(evento.getAccion())) {
			return false;
		}
		if (evento.getEstadoHttp() == null || !ESTADOS_HTTP_RUIDO_ESCANER.contains(evento.getEstadoHttp())) {
			return false;
		}
		boolean anonimo = evento.getUsuarioId() == null
				&& (evento.getUsuarioEmail() == null || evento.getUsuarioEmail().trim().isEmpty());
		if (!anonimo) {
			return false;
		}
		String modulo = normalizarTexto(evento.getModulo());
		if (MODULOS_API_CONOCIDOS.contains(modulo)) {
			return false;
		}
		return FRAGMENTOS_ENDPOINT_RUIDO_ESCANER.stream().anyMatch(endpoint::contains);
	}

	private boolean esAccionEscritura(AuditoriaAccion accion) {
		return accion == AuditoriaAccion.CREATE
				|| accion == AuditoriaAccion.UPDATE
				|| accion == AuditoriaAccion.DELETE;
	}

	private String normalizarTexto(String texto) {
		return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
	}
}
