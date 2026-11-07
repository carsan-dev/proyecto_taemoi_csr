package com.taemoi.project.web;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TarifaSede;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.WebBloque;
import com.taemoi.project.entities.WebEscuela;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.TarifaSedeRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.WebEscuelaRepository;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;

/**
 * Datos de la web por plantilla de la escuela ACTIVA ({@link TenantContext}; anónimo, la del Host cuando llegue la
 * pieza 1 del plan de dominio y, hasta entonces, la por defecto). Pieza W1 de fase4-web-plan.md.
 *
 * <p>Solo hay web si la escuela tiene fila en {@code web_escuela} publicada; la vista previa de la gestión (P o G de
 * la escuela, lo decide el controlador) la ve también sin publicar o sin fila. Nunca para Moiskimdo (comportamiento
 * heredado de Taemoi, la 1: tiene su web propia) ni para una escuela inactiva: en esos casos, vacío (404).
 *
 * <p>Número de sentencias fijo, sea cual sea el número de sedes, grupos, turnos o eventos.
 */
@Service
public class WebPublicaService {

	static final int PROXIMOS_EVENTOS = 3;
	private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("Europe/Madrid");

	private final WebEscuelaRepository webs;
	private final OrganizacionRepository organizaciones;
	private final WebPublicaConsultas consultas;
	private final SedeRepository sedes;
	private final TurnoRepository turnos;
	private final AforoPreinscripcionService aforo;
	private final PreinscripcionRepository preinscripciones;
	private final TarifaSedeRepository tarifasSede;
	private final TemporadaService temporadas;
	private final ObjectMapper json;

	public WebPublicaService(WebEscuelaRepository webs, OrganizacionRepository organizaciones,
			WebPublicaConsultas consultas, SedeRepository sedes, TurnoRepository turnos,
			AforoPreinscripcionService aforo, PreinscripcionRepository preinscripciones,
			TarifaSedeRepository tarifasSede, TemporadaService temporadas, ObjectMapper json) {
		this.webs = webs;
		this.organizaciones = organizaciones;
		this.consultas = consultas;
		this.sedes = sedes;
		this.turnos = turnos;
		this.aforo = aforo;
		this.preinscripciones = preinscripciones;
		this.tarifasSede = tarifasSede;
		this.temporadas = temporadas;
		this.json = json;
	}

	/** {@code GET /api/publico/web}; vacío si la escuela activa no tiene web visible. */
	@Transactional(readOnly = true)
	public Optional<WebPublicaDTO> web(boolean vistaPrevia) {
		return escuelaConWeb(vistaPrevia).map(e -> construir(e, vistaPrevia));
	}

	/** {@code GET /api/publico/web/legal}; vacío si la escuela activa no tiene web visible. */
	@Transactional(readOnly = true)
	public Optional<LegalWebDTO> legal(boolean vistaPrevia) {
		return escuelaConWeb(vistaPrevia).map(this::legal);
	}

	// ------------------------------------------------------------------ escuela

	private record EscuelaConWeb(Organizacion organizacion, WebEscuela web) {
	}

	private Optional<EscuelaConWeb> escuelaConWeb(boolean vistaPrevia) {
		Long id = TenantContext.get();
		if (id == null) {
			return Optional.empty();
		}
		WebEscuela web = webs.findById(id).orElse(null);
		if (!vistaPrevia && (web == null || !web.isPublicada())) {
			return Optional.empty();
		}
		Organizacion organizacion = organizaciones.findById(id).orElse(null);
		if (organizacion == null || !Boolean.TRUE.equals(organizacion.getActiva())
				|| Boolean.TRUE.equals(organizacion.getComportamientoLegadoTaemoi())) {
			return Optional.empty();
		}
		return Optional.of(new EscuelaConWeb(organizacion, web));
	}

	// ------------------------------------------------------------------ web

	private WebPublicaDTO construir(EscuelaConWeb e, boolean vistaPrevia) {
		Organizacion o = e.organizacion();
		LocalDate hoy = LocalDate.now(zona(o));
		String temporada = temporadas.actual();

		List<Sede> activas = sedes.findByActivaTrueOrderByIdAsc();
		Set<Long> idsSedes = activas.stream().map(Sede::getId).collect(Collectors.toSet());
		List<Tarifa> tarifas = consultas.tarifasActivas();
		Set<Long> idsTarifas = tarifas.stream().map(Tarifa::getId).collect(Collectors.toSet());

		return new WebPublicaDTO(
				new WebPublicaDTO.Escuela(o.getNombre(), o.getColorPrimario(), o.getLogoImagenId()),
				e.web() == null ? null : e.web().getTema(),
				e.web() != null && e.web().isPublicada(),
				vistaPrevia,
				temporada,
				bloques(),
				sedes(activas, temporada),
				tarifas.stream().map(WebPublicaService::tarifa).toList(),
				tarifasSede(idsTarifas, idsSedes, hoy),
				consultas.licencias().stream()
						.map(f -> new WebPublicaDTO.Licencia((String) f[0], (String) f[1], (Double) f[2])).toList(),
				consultas.examenDesde(),
				consultas.proximosEventos(hoy, PROXIMOS_EVENTOS).stream()
						.map(f -> new WebPublicaDTO.Evento((Long) f[0], (String) f[1], (LocalDate) f[2], f[3] != null))
						.toList(),
				new WebPublicaDTO.Contacto(o.getEmailContacto(), o.getTelefono()));
	}

	private List<WebPublicaDTO.Bloque> bloques() {
		List<WebBloque> bloques = consultas.bloques();
		List<Long> imagenes = bloques.stream().map(WebBloque::getImagenId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> urls = new HashMap<>();
		for (Object[] fila : consultas.urlsDeImagenes(imagenes)) {
			urls.put((Long) fila[0], (String) fila[1]);
		}
		return bloques.stream()
				.map(b -> new WebPublicaDTO.Bloque(b.getClave(), b.getTexto(),
						b.getImagenId() == null ? null : urls.get(b.getImagenId()),
						b.getOrden() == null ? 0 : b.getOrden()))
				.toList();
	}

	/**
	 * Sedes activas con sus grupos visibles en la preinscripción (de disciplinas activas y con turnos) y las plazas de
	 * cada turno y grupo: grupos, turnos, ocupación (4) y lista de espera, cada uno en una consulta.
	 */
	private List<WebPublicaDTO.Sede> sedes(List<Sede> activas, String temporada) {
		List<Grupo> grupos = consultas.gruposDeSedes(activas.stream().map(Sede::getId).toList()).stream()
				.filter(PreinscripcionService::esVisibleEnPreinscripcion).toList();
		Map<Long, List<Turno>> turnosPorGrupo = grupos.isEmpty() ? Map.of()
				: turnos.findByGrupoIn(grupos).stream().collect(Collectors.groupingBy(t -> t.getGrupo().getId()));
		List<Turno> todos = turnosPorGrupo.values().stream().flatMap(List::stream).toList();
		AforoPreinscripcionService.Ocupacion ocupacion = aforo.ocupacion(todos, temporada);
		Map<Long, Integer> enEspera = new HashMap<>();
		if (!todos.isEmpty()) {
			for (Object[] fila : preinscripciones.contarPorTurnoTemporadaYEstado(
					todos.stream().map(Turno::getId).toList(), temporada, EstadoPreinscripcion.EN_LISTA_ESPERA)) {
				enEspera.put((Long) fila[0], ((Number) fila[1]).intValue());
			}
		}
		Function<Turno, PlazasWeb> plazas = t -> PlazasWeb.deTurno(ocupacion.plazas(t),
				enEspera.getOrDefault(t.getId(), 0));

		Map<Long, List<WebPublicaDTO.Grupo>> gruposPorSede = new HashMap<>();
		grupos.stream()
				.filter(g -> turnosPorGrupo.containsKey(g.getId()))
				.sorted(ordenGrupos(turnosPorGrupo))
				.forEach(g -> {
					List<WebPublicaDTO.Turno> suyos = turnosPorGrupo.get(g.getId()).stream().sorted(ORDEN_TURNOS)
							.map(t -> new WebPublicaDTO.Turno(t.getId(), t.getDiaSemana(), t.getHoraInicio(),
									t.getHoraFin(), plazas.apply(t)))
							.toList();
					gruposPorSede.computeIfAbsent(g.getSedeId(), s -> new ArrayList<>())
							.add(new WebPublicaDTO.Grupo(g.getId(), g.getNombre(), g.getDisciplina().getCodigo(),
									g.getDisciplina().getNombre(), g.getRangoEdadMin(), g.getRangoEdadMax(),
									PlazasWeb.peor(suyos.stream().map(WebPublicaDTO.Turno::plazas).toList()), suyos));
				});
		return activas.stream()
				.map(s -> new WebPublicaDTO.Sede(s.getId(), s.getNombre(), s.getDireccion(), s.getLocalidad(),
						s.getCodigoPostal(), s.getTelefono(), gruposPorSede.getOrDefault(s.getId(), List.of())))
				.toList();
	}

	private static WebPublicaDTO.Tarifa tarifa(Tarifa t) {
		return new WebPublicaDTO.Tarifa(t.getId(), t.getCodigo(), t.getNombre(),
				t.getDisciplina() == null ? null : t.getDisciplina().getCodigo(), t.getImporte(), t.getImporteAdulto(),
				t.getImporteMenor());
	}

	/**
	 * Por tarifa y sede que salen: la vigente hoy (la de {@code vigenteDesde} más reciente que no pasa de hoy) y las
	 * futuras, en el orden del listado de la API (tarifa, sede y fecha).
	 */
	private List<WebPublicaDTO.TarifaSede> tarifasSede(Set<Long> idsTarifas, Set<Long> idsSedes, LocalDate hoy) {
		List<TarifaSede> todas = tarifasSede.findAllByOrderByTarifaIdAscSedeIdAscVigenteDesdeAsc().stream()
				.filter(t -> idsTarifas.contains(t.getTarifaId()) && idsSedes.contains(t.getSedeId())).toList();
		Map<List<Long>, TarifaSede> vigentes = new HashMap<>();
		for (TarifaSede t : todas) {
			if (!t.getVigenteDesde().isAfter(hoy)) {
				vigentes.put(List.of(t.getTarifaId(), t.getSedeId()), t); // la última gana: van por fecha ascendente
			}
		}
		return todas.stream()
				.filter(t -> t.getVigenteDesde().isAfter(hoy) || vigentes.get(List.of(t.getTarifaId(), t.getSedeId())) == t)
				.map(t -> new WebPublicaDTO.TarifaSede(t.getTarifaId(), t.getSedeId(), t.getImporte(),
						t.getImporteAdulto(), t.getImporteMenor(), t.getVigenteDesde()))
				.toList();
	}

	// ------------------------------------------------------------------ legal

	private LegalWebDTO legal(EscuelaConWeb e) {
		Organizacion o = e.organizacion();
		Map<Long, PlantillaPreinscripcion> porDisciplina = new LinkedHashMap<>();
		for (PlantillaPreinscripcion p : consultas.plantillasActivas()) {
			porDisciplina.putIfAbsent(p.getDisciplina().getId(), p); // la primera es la versión más alta
		}
		List<LegalWebDTO.Normas> normas = porDisciplina.values().stream()
				.map(p -> new LegalWebDTO.Normas(p.getDisciplina().getCodigo(), p.getDisciplina().getNombre(),
						p.getVersion(), contenido(p.getContenido()), p.getInstrucciones()))
				.toList();
		LocalDate actualizada = e.web() == null || e.web().getPublicadaEn() == null ? null
				: e.web().getPublicadaEn().toLocalDate();
		return new LegalWebDTO(new LegalWebDTO.Titular(o.getNombre(), o.getNombreLegal(), o.getCif(), o.getDireccion(),
				o.getLocalidad(), o.getProvincia(), o.getCodigoPostal(), o.getEmailContacto(), o.getTelefono()), normas,
				actualizada);
	}

	private JsonNode contenido(String contenido) {
		try {
			return json.readTree(contenido);
		} catch (Exception e) {
			return TextNode.valueOf(contenido);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private static ZoneId zona(Organizacion o) {
		try {
			return o.getZonaHoraria() == null ? ZONA_POR_DEFECTO : ZoneId.of(o.getZonaHoraria());
		} catch (DateTimeException e) {
			return ZONA_POR_DEFECTO;
		}
	}

	private static final Comparator<Turno> ORDEN_TURNOS = Comparator
			.comparingInt((Turno t) -> ordenDia(t.getDiaSemana()))
			.thenComparing(Turno::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Turno::getId);

	/** Como la preinscripción: edad mínima, máxima, primer día, primera hora e id. */
	private static Comparator<Grupo> ordenGrupos(Map<Long, List<Turno>> turnosPorGrupo) {
		Function<Grupo, Turno> primero = g -> turnosPorGrupo.get(g.getId()).stream().min(ORDEN_TURNOS).orElseThrow();
		return Comparator.comparingInt((Grupo g) -> Optional.ofNullable(g.getRangoEdadMin()).orElse(0))
				.thenComparingInt(g -> Optional.ofNullable(g.getRangoEdadMax()).orElse(99))
				.thenComparing(primero, ORDEN_TURNOS)
				.thenComparing(Grupo::getId);
	}

	private static int ordenDia(String dia) {
		String d = dia == null ? ""
				: Normalizer.normalize(dia, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
		return switch (d) {
			case "lunes" -> 1;
			case "martes" -> 2;
			case "miercoles" -> 3;
			case "jueves" -> 4;
			case "viernes" -> 5;
			case "sabado" -> 6;
			case "domingo" -> 7;
			default -> 99;
		};
	}
}
