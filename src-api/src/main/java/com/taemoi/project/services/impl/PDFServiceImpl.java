package com.taemoi.project.services.impl;


import com.taemoi.project.services.CatalogoLegadoTaemoi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.MarcaEscuela;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.utils.ColoresGrado;
import com.taemoi.project.utils.DiaSemanaUtils;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.ImporteCargo;

@Service
public class PDFServiceImpl implements PDFService {
	private static final Pattern TEMPORADA_PATTERN = Pattern.compile("\\b(20\\d{2})/(20\\d{2})\\b");
	private static final ObjectMapper TEMPLATE_JSON_MAPPER = new ObjectMapper();

	@Autowired
	private AlumnoRepository alumnoRepository;

	/** Entrega 6-1d: el listado de asistencia del responsable de sede solo lleva los turnos de sus sedes. */
	@Autowired
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	/** Pieza E7-2: los turnos del grupo del profesor, por id (escuela activa por {@code @TenantId}). */
	@Autowired
	private com.taemoi.project.repositories.TurnoRepository turnoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private com.taemoi.project.repositories.ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private com.taemoi.project.repositories.ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private com.taemoi.project.services.ConvocatoriaService convocatoriaService;

	@Autowired
	private com.taemoi.project.services.CatalogoDisciplinasService catalogoDisciplinas;

	@Autowired
	private com.taemoi.project.services.ProgresionGradosService progresionGrados;

	/** Entrega 6-4 (D6-33): cuantía esperada del listado mensual con la tarifa de la sede del alumno para ese mes. */
	@Autowired
	private com.taemoi.project.services.TarifaSedeService tarifaSede;

	/** Grupo de edad del alumno en la disciplina de la inscripción (disciplina.edad_adulto, año natural). */
	private boolean esMenor(Alumno alumno, AlumnoDeporte alumnoDeporte) {
		return catalogoDisciplinas.esMenorPorAnioNatural(alumno.getFechaNacimiento(),
				alumnoDeporte.getDeporte() == null ? null : alumnoDeporte.getDeporte());
	}

	/** Nombre visible del grado de la inscripción (el de su fila: «MARRÓN» en kickboxing); «N/A» sin grado. */
	private String nombreGrado(AlumnoDeporte alumnoDeporte) {
		return progresionGrados.obtenerNombreVisible(alumnoDeporte.getGrado());
	}

	/** Siguiente grado de la inscripción en el sistema de su disciplina; nulo si no tiene grado o no hay siguiente. */
	private Grado siguienteGrado(Alumno alumno, AlumnoDeporte alumnoDeporte) {
		if (alumnoDeporte.getGrado() == null) {
			return null;
		}
		return progresionGrados.obtenerSiguienteGrado(
				alumnoDeporte.getDeporte() == null ? null : alumnoDeporte.getDeporte(),
				esMenor(alumno, alumnoDeporte), alumnoDeporte.getGrado().getCodigo()).orElse(null);
	}

	/** Código del grado; nulo si no hay grado. */
	private static String tipoDe(Grado grado) {
		return grado == null ? null : grado.getCodigo();
	}

	/**
	 * Pieza E7-7a (decisión 211): nombre y logo de la escuela en las cabeceras (la activa; la solicitud firmada, la de su
	 * preinscripción). Moiskimdo, como siempre; antes había una sola caché de logo para todas las escuelas.
	 */
	@Autowired
	private MarcaEscuelaService marcaEscuela;

	@Override public byte[] generarPreviewPreinscripcion(String deporte,String contenido,String instrucciones){
		com.taemoi.project.entities.Disciplina disciplina=catalogoDisciplinas.obtenerPorCodigo(deporte);PlantillaPreinscripcion plantilla=new PlantillaPreinscripcion();plantilla.setDisciplina(disciplina);plantilla.setVersion(0);plantilla.setContenido(contenido);plantilla.setInstrucciones(instrucciones);
		Preinscripcion p=new Preinscripcion();p.setDisciplina(disciplina);p.setPlantilla(plantilla);p.setTemporada("2026/2027");p.setReferencia("PREVIEW");p.setNombre("María");p.setApellidos("García López");p.setDni("12345678Z");p.setFechaNacimiento(LocalDate.of(2000,1,15));p.setTelefono("600 000 000");p.setTelefono2("611 111 111");p.setEmail("maria@example.org");p.setTieneDiscapacidad(false);p.setDireccion(marcaEscuela.deActiva().legado()?"Calle Ejemplo, 1, Umbrete":"Calle Ejemplo, 1");/* E7-7a: la localidad de Moiskimdo, solo en Moiskimdo */p.setGrupoSnapshot("Grupo de ejemplo · lunes y miércoles 18:00–19:00");p.setConsentimientoFotografico(true);p.setFirmanteNombre("María García López");p.setFirma(firmaPreview());return generarPreinscripcionFirmada(p);
	}
	private byte[] firmaPreview(){try{java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(360,90,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,360,90);g.setColor(java.awt.Color.DARK_GRAY);g.drawString("Firma de previsualización",80,48);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}

	@Override
	public byte[] generarPreinscripcionFirmada(Preinscripcion p) {
		String firma = "data:image/png;base64," + Base64.getEncoder().encodeToString(p.getFirma());
		String nombreDeporte = p.getDeporte().replace('_', ' ');
		boolean conNormas = true;
		JsonNode contenidoPlantilla = parsearContenidoPlantilla(p.getPlantilla().getContenido());
		MarcaEscuela marca = marcaEscuela.deEntidadOActiva(p.getOrganizacionId());
		String logoSolicitud = marca.logoSolicitud();
		String cabecera=campoPlantilla(contenidoPlantilla,"cabecera",marca.cabeceraSolicitudPorDefecto());
		String contacto=campoPlantilla(contenidoPlantilla,"contacto","");
		String titulo=campoPlantilla(contenidoPlantilla,"titulo","Solicitud de preinscripción");
		String consentimiento=campoPlantilla(contenidoPlantilla,"consentimiento","");
		String importes=campoPlantilla(contenidoPlantilla,"importes","");
		// Decisión 315: fuentes, cabecera, pie y tablas comunes; la estructura, el orden y los textos legales, los de siempre.
		String contextoCabecera = contexto(esc(contacto), "Temporada " + esc(p.getTemporada()));
		String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>"
				+ EstiloPdf.pie(cabecera, titulo) + EstiloPdf.cssComun()
				+ "@page{margin:10mm 12mm 11mm}body{font-size:9.2pt;line-height:1.24}p{margin:5px 0}ol{margin:5px 0;padding-left:20px}li{margin:0 0 1.5px}"
				+ ".cab{margin-bottom:3mm;padding-bottom:2mm}.section{margin:9px 0}.keep{page-break-inside:avoid}"
				+ ".section h3{font-family:" + EstiloPdf.TITULOS + ";font-weight:700;font-size:10pt;color:" + EstiloPdf.TINTA + ";margin:7px 0 3px;padding:0}"
				+ ".grid{width:100%;border-collapse:collapse;margin:0}.grid td{border:none;border-bottom:0.5pt solid " + EstiloPdf.FILETE + ";padding:3px 4px 3px 0;text-align:left;font-size:9.2pt}"
				+ ".label{font-size:7pt;font-weight:600;color:" + EstiloPdf.GRIS + "}"
				+ ".notice{background:" + EstiloPdf.FONDO_CABECERA_TABLA + ";border-left:2pt solid " + EstiloPdf.FILETE_CABECERA_TABLA + ";padding:6px}"
				+ ".signature{height:20mm;max-width:66mm;object-fit:contain;border-bottom:1px solid #555}.foot{font-size:7pt;color:" + EstiloPdf.GRIS + ";margin-top:6px}"
				+ "</style></head><body>"
				+ EstiloPdf.cabecera(marca.colorPrimario(), logoSolicitud, esc(cabecera), null, esc(titulo), contextoCabecera,
						"<strong>" + esc(p.getReferencia()) + "</strong>")
				+ "<div class='notice'><strong>Preinscripción recibida.</strong> "+(p.getEstado()==com.taemoi.project.entities.EstadoPreinscripcion.EN_LISTA_ESPERA?"Solicitud incorporada a la lista de espera. Se notificará la asignación automática cuando exista aforo.":"Plaza asignada; esta solicitud ya cuenta en el aforo del grupo.")+"</div>"
				+ "<div class='section'><h3>Datos de la persona solicitante</h3><table class='grid'><tr><td><span class='label'>Nombre y apellidos</span><br/>"+esc(p.getNombre()+" "+p.getApellidos())+"</td><td><span class='label'>DNI/NIE</span><br/>"+esc(p.getDni()==null||p.getDni().isBlank()?"No facilitado":p.getDni())+"</td></tr>"
				+ "<tr><td><span class='label'>Fecha de nacimiento</span><br/>"+p.getFechaNacimiento()+"</td><td><span class='label'>Correo electrónico</span><br/>"+esc(p.getEmail())+"</td></tr><tr><td><span class='label'>Teléfono principal</span><br/>"+esc(p.getTelefono())+"</td><td><span class='label'>Teléfono secundario</span><br/>"+esc(p.getTelefono2()==null||p.getTelefono2().isBlank()?"No facilitado":p.getTelefono2())+"</td></tr><tr><td><span class='label'>Discapacidad</span><br/>"+(p.getTieneDiscapacidad()==null?"No indicado":Boolean.TRUE.equals(p.getTieneDiscapacidad())?"Sí":"No")+"</td><td><span class='label'>Dirección</span><br/>"+esc(p.getDireccion())+"</td></tr></table></div>"
				+ (p.getTutorNombre()!=null&&!p.getTutorNombre().isBlank()?"<div class='section'><h3>Responsable legal</h3>"+esc(p.getTutorNombre())+" · "+esc(p.getTutorDni())+"</div>":"")
				+ "<div class='section'><h3>Actividad solicitada</h3>"+esc(p.getGrupoSnapshot()!=null?p.getGrupoSnapshot():nombreDeportePublico(p.getDeporte()))+"</div>"
				+ (conNormas ? "<div class='section keep'><h3>Consentimiento fotográfico (opcional)</h3><p>"+(Boolean.TRUE.equals(p.getConsentimientoFotografico())?"[X] Autorizado. ":"[ ] No autorizado. ")+esc(consentimiento)+"</p></div><div class='section'><h3>Normas de la escuela</h3>"+normasHtml(contenidoPlantilla)+"<p><strong>[X] Me comprometo a cumplir las normas establecidas por el club.</strong></p></div>" : "")
				+ "<div class='section keep'><h3>Importes y pago</h3><p>"+esc(importes)+"</p></div>"
				+ "<div class='section keep'><h3>Firma</h3><img class='signature' src='"+firma+"'/><p>Firmado por "+esc(p.getFirmanteNombre())+" el "+LocalDate.now(ZoneId.of("Europe/Madrid"))+".</p></div>"
				+ "<div class='foot'>Documento generado electrónicamente. Referencia "+esc(p.getReferencia())+". Versión documental preservada con la solicitud.</div></body></html>";
		try { ByteArrayOutputStream out=new ByteArrayOutputStream(); PdfRendererBuilder builder=EstiloPdf.builder(); builder.withHtmlContent(html,null); builder.toStream(out); builder.run(); return out.toByteArray(); }
		catch(Exception e){throw new IllegalStateException("No se pudo generar la solicitud PDF",e);}
	}

	private String esc(String value){ if(value==null)return ""; return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
	/** Nombre público de la disciplina (en Taemoi: «Taekwondo», «Defensa Personal Femenina» desde V30…). */
	private String nombreDeportePublico(String deporte){return catalogoDisciplinas.obtenerPorCodigo(deporte).getNombre();}
	private JsonNode parsearContenidoPlantilla(String contenido) {
		if (contenido == null || contenido.isBlank()) {
			return TEMPLATE_JSON_MAPPER.createObjectNode();
		}
		try {
			JsonNode root = TEMPLATE_JSON_MAPPER.readTree(contenido);
			return root != null && root.isObject() ? root : TEMPLATE_JSON_MAPPER.createObjectNode();
		} catch (IOException e) {
			return TEMPLATE_JSON_MAPPER.createObjectNode();
		}
	}

	private String campoPlantilla(JsonNode contenido, String campo, String fallback) {
		JsonNode valor = contenido.path(campo);
		return valor.isTextual() ? normalizarTextoPlantilla(valor.textValue()) : fallback;
	}

	private String normasHtml(JsonNode contenido) {
		JsonNode normas = contenido.path("normas");
		if (!normas.isArray()) {
			return "";
		}
		StringBuilder out = new StringBuilder("<ol>");
		for (JsonNode item : normas) {
			if (item.isTextual()) {
				out.append("<li>").append(esc(normalizarTextoPlantilla(item.textValue()))).append("</li>");
			}
		}
		return out.append("</ol>").toString();
	}

	private String normalizarTextoPlantilla(String value) {
		return value.replace('\r', ' ').replace('\n', ' ').replace("\\n", " ");
	}

	/**
	 * Decisiones 309, 314 y 315: cabecera común de los informes (logo, nombre de la escuela, título, contexto y fecha
	 * de generación; filete en el color de la escuela). Los textos llegan ya listos para HTML.
	 *
	 * @param titulo   título del informe
	 * @param contexto filtros o contexto del informe (deporte, temporada, solo activos…); nulo o vacío si no hay
	 */
	private String cabeceraInforme(String titulo, String contexto) {
		return EstiloPdf.cabeceraInforme(marcaEscuela.deActiva(), titulo, contexto);
	}

	/** Contexto «Solo alumnos activos» de los informes con ese filtro; nulo si se piden todos. */
	private static String contextoActivos(boolean soloActivos) {
		return soloActivos ? "Solo alumnos activos" : null;
	}

	/** Une las partes no vacías del contexto de la cabecera con « · ». */
	private static String contexto(String... partes) {
		String unido = Arrays.stream(partes).filter(p -> p != null && !p.isBlank())
				.collect(Collectors.joining(" · "));
		return unido.isEmpty() ? null : unido;
	}

	/**
	 * Estilos de los informes: el pie de página («Escuela · Informe», «Página X de Y»), los estilos comunes de
	 * {@link EstiloPdf} y los de las piezas propias de estos informes (rótulos de grupo con su cinturón, secciones
	 * por disciplina y resúmenes), sin colores fijos por informe.
	 *
	 * @param informe nombre del informe en el pie de página
	 */
	private String estilosInforme(String informe) {
		return EstiloPdf.pie(marcaEscuela.deActiva().cabeceraInformes(), informe) + EstiloPdf.cssComun()
				+ " h1, h2, h3 { font-family: " + EstiloPdf.TITULOS + "; font-weight: 700; color: " + EstiloPdf.TINTA
				+ "; line-height: 1.25; margin: 5mm 0 1.5mm 0; page-break-after: avoid; }"
				+ " h1 { font-size: 12pt; } h2 { font-size: 10.5pt; } h3 { font-size: 10.5pt; }"
				// Sección por disciplina o estado (Taekwondo, Licencias en vigor…): rótulo mayor.
				+ " .section-header { font-family: " + EstiloPdf.TITULOS + "; font-weight: 700; font-size: 12pt;"
				+ " color: " + EstiloPdf.TINTA + "; margin: 6mm 0 1mm 0; padding: 0; page-break-after: avoid; }"
				// Grupo (grado, categoría): cinturón y nombre a la izquierda, recuento en gris a la derecha.
				+ " .grupo { margin: 0 0 3mm 0; page-break-inside: avoid; }"
				+ " .encabezado-grupo { margin: 4.5mm 0 0.5mm 0; page-break-after: avoid; }"
				+ " .encabezado-grupo .izquierda { display: inline-block; width: 72%; vertical-align: middle; }"
				+ " .encabezado-grupo .derecha { display: inline-block; width: 27%; text-align: right;"
				+ " vertical-align: middle; font-size: 8pt; color: " + EstiloPdf.GRIS + "; }"
				+ " .cinturon { display: inline-block; vertical-align: middle; width: 28mm; height: 6mm; margin-right: 3mm;"
				+ " border: 1.5px solid #495057; border-radius: 1mm; position: relative; }"
				+ " .cinturon.doble .superior, .cinturon.doble .inferior { width: 100%; height: 50%; position: absolute; }"
				+ " .cinturon.doble .superior { top: 0; border-top-left-radius: 0.5mm; border-top-right-radius: 0.5mm; }"
				+ " .cinturon.doble .inferior { bottom: 0; border-bottom-left-radius: 0.5mm;"
				+ " border-bottom-right-radius: 0.5mm; }"
				+ " .raya { position: absolute; top: 50%; transform: translateY(-50%); height: 80%;"
				+ " background-color: #FFD700; z-index: 10; }"
				+ " .grado-nombre, .categoria-nombre { display: inline-block; vertical-align: middle; font-family: "
				+ EstiloPdf.TITULOS + "; font-weight: 700; font-size: 10.5pt; color: " + EstiloPdf.TINTA
				+ "; white-space: nowrap; }"
				// Celdas con cinturón dibujado (licencias, promociones).
				+ " .grado-cell { vertical-align: middle; }"
				+ " .grado-belt { margin: 0 0 1mm 0; }"
				+ " .grado-text { font-size: 8pt; font-weight: 600; line-height: 1.2; }"
				// Resúmenes al final de un informe.
				+ " .resumen-general { margin-top: 6mm; padding-top: 2mm; border-top: 1pt solid "
				+ EstiloPdf.FILETE_CABECERA_TABLA + "; page-break-inside: avoid; }"
				+ " .resumen-general h3 { margin: 0 0 1.5mm 0; }"
				+ " .resumen-general p { margin: 0.8mm 0; }";
	}

	@Override
	public byte[] generarInformeAlumnosPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Arrays.asList(CatalogoLegadoTaemoi.TAEKWONDO, CatalogoLegadoTaemoi.KICKBOXING), soloActivos);
	}

	@Override
	public byte[] generarInformeTaekwondoPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Collections.singletonList(CatalogoLegadoTaemoi.TAEKWONDO), soloActivos);
	}

	@Override
	public byte[] generarInformeKickboxingPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Collections.singletonList(CatalogoLegadoTaemoi.KICKBOXING), soloActivos);
	}

	/**
	 * Genera el informe PDF filtrando los alumnos según la lista de deportes. Si se
	 * solicitan ambos deportes, se generan secciones separadas para cada uno.
	 */
	private byte[] generarInformePorGrado(List<String> deportes, boolean soloActivos) {
		List<AlumnoDeporte> alumnos = alumnoDeporteRepository.findByGradoNotNullAndDeporteIn(deportes);

		// Filter by active status if requested
		if (soloActivos) {
			alumnos = alumnos.stream()
					.filter(ad -> ad.getAlumno() != null && Boolean.TRUE.equals(ad.getAlumno().getActivo()))
					.collect(Collectors.toList());
		}

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Listado de Alumnos por Grado"));
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		if (deportes.size() == 2 && deportes.contains(CatalogoLegadoTaemoi.TAEKWONDO) && deportes.contains(CatalogoLegadoTaemoi.KICKBOXING)) {
			html.append(cabeceraInforme("Listado de Alumnos por Grado", contextoActivos(soloActivos)));

			List<AlumnoDeporte> alumnosTaekwondo = alumnos.stream().filter(a -> CatalogoLegadoTaemoi.TAEKWONDO.equals(a.getDeporte()))
					.collect(Collectors.toList());
			html.append("<div class='section-header'>");
			html.append("Taekwondo");
			html.append("</div>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<AlumnoDeporte> alumnosKickboxing = alumnos.stream().filter(a -> CatalogoLegadoTaemoi.KICKBOXING.equals(a.getDeporte()))
					.collect(Collectors.toList());
			html.append("<div class='section-header'>");
			html.append("Kickboxing");
			html.append("</div>");
			html.append(generarSeccion(alumnosKickboxing));
		} else {
			if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deportes.get(0))) {
				html.append(cabeceraInforme("Taekwondo por Grado", contextoActivos(soloActivos)));
			} else {
				html.append(cabeceraInforme("Kickboxing por Grado", contextoActivos(soloActivos)));
			}
			html.append(generarSeccion(alumnos));
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outputStream.toByteArray();
	}

	/**
	 * Genera el HTML para una sección del informe, agrupando alumnos por grado.
	 */
	private String generarSeccion(List<AlumnoDeporte> alumnos) {
		StringBuilder html = new StringBuilder();
		// Un apartado por codigo de grado (todos los alumnos son de la misma disciplina), del grado más
		// alto al más bajo según el orden del sistema de grados de la disciplina.
		Map<String, List<AlumnoDeporte>> alumnosPorGrado = alumnos.stream()
				.collect(Collectors.groupingBy(ad -> ad.getGrado().getCodigo(), java.util.LinkedHashMap::new,
						Collectors.toList()));

		List<Map.Entry<String, List<AlumnoDeporte>>> entradasOrdenadas = new ArrayList<>(alumnosPorGrado.entrySet());
		entradasOrdenadas.sort((e1, e2) -> {
			int orden1 = ordenPresentacion(e1.getValue().get(0).getGrado());
			int orden2 = ordenPresentacion(e2.getValue().get(0).getGrado());
			return Integer.compare(orden2, orden1);
		});

		for (Map.Entry<String, List<AlumnoDeporte>> entry : entradasOrdenadas) {
			Grado grado = entry.getValue().get(0).getGrado();
			List<AlumnoDeporte> alumnosGrado = entry.getValue();
			alumnosGrado.sort(Comparator.comparing(ad -> {
				Alumno alumno = ad.getAlumno();
				if (alumno == null) {
					return "";
				}
				return (alumno.getNombre() + " " + alumno.getApellidos()).toLowerCase();
			}));

			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");
			html.append("<div class='izquierda'>");
			html.append(CinturonesPdf.encabezado(ColoresGrado.de(grado)));

			html.append("<span class='grado-nombre'>").append(progresionGrados.obtenerNombreVisible(grado))
					.append("</span>");
			html.append("</div>");
			html.append("<div class='derecha'>");
			html.append("Alumnos: ").append(alumnosGrado.size());
			html.append("</div>");
			html.append("</div>");

			html.append("<table class='tabla-grado'>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th class='num'>N&#186; Expediente</th>");
			html.append("<th class='num'>Licencia Fed.</th>");
			html.append("<th class='num'>Fecha del Grado</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");
			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			for (AlumnoDeporte alumnoDeporte : alumnosGrado) {
				Alumno alumno = alumnoDeporte.getAlumno();
				if (alumno == null) {
					continue;
				}
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append("</td>");
				html.append("<td class='num'>").append(alumno.getNumeroExpediente()).append("</td>");
				String licencia = alumnoDeporte.getNumeroLicencia() != null
						? alumnoDeporte.getNumeroLicencia().toString()
						: "N/A";
				html.append("<td class='num'>").append(licencia).append("</td>");
				// Format date as dd/MM/yyyy
				String fechaGrado = "N/A";
				if (alumnoDeporte.getFechaGrado() != null) {
					LocalDate fecha = Instant.ofEpochMilli(alumnoDeporte.getFechaGrado().getTime())
							.atZone(ZoneId.systemDefault()).toLocalDate();
					fechaGrado = fecha.format(dateFormatter);
				}
				html.append("<td class='num'>").append(fechaGrado).append("</td>");

				html.append("</tr>");
			}
			html.append("</tbody>");
			html.append("</table>");
			html.append("</div>");
		}
		return html.toString();
	}


	@Override
	public byte[] generarInformeLicencias(boolean soloActivos) {
		List<AlumnoDeporte> alumnos = alumnoDeporteRepository.findAll();

		// Filter by active status if requested
		if (soloActivos) {
			alumnos = alumnos.stream().filter(ad -> Boolean.TRUE.equals(ad.getActivo()) && ad.getAlumno() != null
					&& Boolean.TRUE.equals(ad.getAlumno().getActivo())).collect(Collectors.toList());
		}
		// Exclude sports without license requirements (disciplina.requiere_licencia)
		alumnos = alumnos.stream().filter(
				ad -> ad.getDeporte() != null && catalogoDisciplinas.requiereLicencia(ad.getDeporte()))
				.collect(Collectors.toList());

		LocalDate today = LocalDate.now();

		List<AlumnoDeporte> licenciasVigor = new ArrayList<>();
		List<AlumnoDeporte> licenciasCaducadas = new ArrayList<>();
		List<AlumnoDeporte> sinLicencia = new ArrayList<>();

		for (AlumnoDeporte alumnoDeporte : alumnos) {
			if (Boolean.TRUE.equals(alumnoDeporte.getTieneLicencia()) && alumnoDeporte.getFechaLicencia() != null) {
				LocalDate fechaLicencia = Instant.ofEpochMilli(alumnoDeporte.getFechaLicencia().getTime())
						.atZone(ZoneId.systemDefault()).toLocalDate();
				// La lógica debe coincidir con listado-alumnos.component.ts:
				// Una licencia es válida si su año >= año actual
				int añoLicencia = fechaLicencia.getYear();
				int añoActual = today.getYear();
				if (añoLicencia >= añoActual) {
					licenciasVigor.add(alumnoDeporte);
				} else {
					licenciasCaducadas.add(alumnoDeporte);
				}
			} else {
				sinLicencia.add(alumnoDeporte);
			}
		}

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Informe de Licencias de Alumnos"));
		// Ajustar anchos de columnas para la tabla de licencias
		html.append(".licencias-table { table-layout: fixed; }");
		html.append(".licencias-table th:nth-child(1), .licencias-table td:nth-child(1) { width: 28%; }");
		html.append(".licencias-table th:nth-child(2), .licencias-table td:nth-child(2) { width: 14%; }");
		html.append(".licencias-table th:nth-child(3), .licencias-table td:nth-child(3) { width: 10%; }");
		html.append(".licencias-table th:nth-child(4), .licencias-table td:nth-child(4) { width: 10%; }");
		html.append(
				".licencias-table th:nth-child(5), .licencias-table td:nth-child(5) { width: 14%; white-space: nowrap; }");
		html.append(".licencias-table th:nth-child(6), .licencias-table td:nth-child(6) { width: 24%; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(cabeceraInforme("Informe de Licencias", contextoActivos(soloActivos)));

		html.append("<div class='section-header'>");
		html.append("Licencias en Vigor (" + licenciasVigor.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasVigor));

		html.append("<div class='section-header'>");
		html.append("Licencias Caducadas (" + licenciasCaducadas.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasCaducadas));

		html.append("<div class='section-header'>");
		html.append("Sin Licencia (" + sinLicencia.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(sinLicencia));

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outputStream.toByteArray();
	}

	/**
	 * Método auxiliar que genera una tabla HTML con la información del alumno.
	 */
	private String generarTablaAlumnos(List<AlumnoDeporte> alumnos) {
		StringBuilder html = new StringBuilder();
		html.append("<table class='licencias-table'>");
		html.append("<thead><tr>");
		html.append("<th>Nombre y Apellidos</th>");
		html.append("<th>Deporte</th>");
		html.append("<th class='num'>EXP.</th>");
		html.append("<th class='num'>N&#186; Lic.</th>");
		html.append("<th class='num'>Fecha Lic.</th>");
		html.append("<th>Grado</th>");
		html.append("</tr></thead>");
		html.append("<tbody>");
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		for (AlumnoDeporte alumnoDeporte : alumnos) {
			Alumno alumno = alumnoDeporte.getAlumno();
			html.append("<tr>");
			html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");
			html.append("<td>").append(getDeporteNombre(alumnoDeporte.getDeporte())).append("</td>");
			html.append("<td class='num'>").append(alumno.getNumeroExpediente()).append("</td>");
			String numLicencia = (alumnoDeporte.getNumeroLicencia() != null)
					? alumnoDeporte.getNumeroLicencia().toString()
					: "N/A";
			html.append("<td class='num'>").append(numLicencia).append("</td>");
			// Format date as dd/MM/yyyy
			String fechaLic = "N/A";
			if (alumnoDeporte.getFechaLicencia() != null) {
				LocalDate fechaLicencia = Instant.ofEpochMilli(alumnoDeporte.getFechaLicencia().getTime())
						.atZone(ZoneId.systemDefault()).toLocalDate();
				fechaLic = fechaLicencia.format(dateFormatter);
			}
			html.append("<td class='num'>").append(fechaLic).append("</td>");

			// Use belt drawing for grade
			html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));
			html.append("</tr>");
		}
		html.append("</tbody>");
		html.append("</table>");
		return html.toString();
	}

	/**
	 * Orden de presentación de los grados en los informes (del más bajo al más alto). Para los códigos de
	 * Taemoi es el de siempre (el del enum: los PUM antes que los DAN, también en kickboxing, aunque su
	 * {@code grado.orden} de progresión ponga los PUM al final para que no tengan siguiente). Los códigos
	 * propios de otras escuelas usan {@code grado.orden}. Hasta que el catálogo tenga un orden de
	 * presentación propio (contracción).
	 */
	private static final Map<String, Integer> ORDEN_PRESENTACION_TAEMOI = new java.util.HashMap<>();

	static {
		List<String> codigos = CatalogoLegadoTaemoi.codigosGrado();
		for (int i = 0; i < codigos.size(); i++) {
			ORDEN_PRESENTACION_TAEMOI.put(codigos.get(i), i + 1);
		}
	}

	private static int ordenPresentacion(String codigo, Integer ordenCatalogo) {
		Integer taemoi = codigo == null ? null : ORDEN_PRESENTACION_TAEMOI.get(codigo);
		if (taemoi != null) {
			return taemoi;
		}
		return ordenCatalogo == null ? Integer.MIN_VALUE : ordenCatalogo;
	}

	private static int ordenPresentacion(Grado grado) {
		return grado == null ? Integer.MIN_VALUE : ordenPresentacion(grado.getCodigo(), grado.getOrden());
	}

	private String generarCeldaGrado(Grado grado, String texto) {
		String textoSeguro = (texto == null || texto.trim().isEmpty()) ? "N/A" : texto;
		if (grado == null) {
			return "<td class='grado-cell'><div class='grado-text'>" + textoSeguro + "</div></td>";
		}
		String cinturon = CinturonesPdf.inline(ColoresGrado.de(grado), 70, 16);
		return "<td class='grado-cell'><div class='grado-belt'>" + cinturon + "</div><div class='grado-text'>"
				+ textoSeguro + "</div></td>";
	}

	@Override
	public byte[] generarInformeInfantilesAPromocionar(boolean soloActivos) {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();

		// Filter by active status if requested
		if (soloActivos) {
			todosAlumnos = todosAlumnos.stream().filter(a -> Boolean.TRUE.equals(a.getActivo()))
					.collect(Collectors.toList());
		}


		// Map<String, Map<PromotionGrade, List<AlumnoPromotionInfo>>>
		Map<String, Map<String, List<AlumnoPromotionInfo>>> deporteGradoMap = new java.util.LinkedHashMap<>();

		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() == null) {
				continue;
			}

			// Check each sport the student practices
			for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {

				// Skip inactive sports or sports where student is not ready for exam
				if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
						|| !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
					continue;
				}
				if (!esMenor(alumno, alumnoDeporte)) {
					continue;
				}

				// Calculate promotion grade for this sport
				String promotionGrade = getPromotionGradeForSport(alumno, alumnoDeporte);

				// Add to the map
				String deporte = alumnoDeporte.getDeporte();
				deporteGradoMap.putIfAbsent(deporte, new java.util.LinkedHashMap<>());
				deporteGradoMap.get(deporte).putIfAbsent(promotionGrade, new ArrayList<>());
				deporteGradoMap.get(deporte).get(promotionGrade)
						.add(new AlumnoPromotionInfo(alumno, alumnoDeporte, promotionGrade));
			}
		}

		// Build HTML
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Alumnos Infantiles a Promocionar"));
		html.append(".promotion-group { margin-bottom: 4mm; }");
		html.append(".sport-section { margin-bottom: 6mm; page-break-inside: avoid; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(cabeceraInforme("Infantiles a Promocionar", contextoActivos(soloActivos)));

		// If no students found
		if (deporteGradoMap.isEmpty()) {
			html.append("<p class='vacio'>");
			html.append("No se encontraron alumnos infantiles aptos para promocionar.");
			html.append("</p>");
		} else {

			// Iterate through sports
			for (Map.Entry<String, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
				String deporte = deporteEntry.getKey();

				html.append("<div class='sport-section'>");
				html.append("<div class='section-header'>").append(getDeporteNombre(deporte)).append("</div>");

				// Iterate through promotion grades for this sport
				for (Map.Entry<String, List<AlumnoPromotionInfo>> gradeEntry : deporteEntry.getValue().entrySet()) {
					String promotionGrade = gradeEntry.getKey();
					List<AlumnoPromotionInfo> alumnos = gradeEntry.getValue();

					html.append("<div class='promotion-group'>");
					html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");

					html.append("<table class='promotion-table'>");
					html.append("<thead><tr>");
					html.append("<th>Nombre y Apellidos</th>");
					html.append("<th class='num'>N&#186; Expediente</th>");
					html.append("<th>Grado Actual</th>");
					html.append("<th>Grado a Promocionar</th>");
					html.append("<th class='num'>Edad</th>");
					html.append("</tr></thead>");
					html.append("<tbody>");

					for (AlumnoPromotionInfo info : alumnos) {
						Alumno alumno = info.alumno;
						AlumnoDeporte alumnoDeporte = info.alumnoDeporte;

						html.append("<tr>");

						html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
								.append("</td>");

						html.append("<td class='num'>").append(alumno.getNumeroExpediente()).append("</td>");

						html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

						Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
						html.append(generarCeldaGrado(gradoPromocion,
								progresionGrados.obtenerNombreVisible(gradoPromocion)));

						int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
						html.append("<td class='num'>").append(edad).append("</td>");

						html.append("</tr>");
					}

					html.append("</tbody>");
					html.append("</table>");
					html.append("</div>");
				}

				html.append("</div>"); // Close sport-section
			}
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);

		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return outputStream.toByteArray();
	}

	@Override
	public byte[] generarInformeAdultosAPromocionar(boolean soloActivos) {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();

		// Filter by active status if requested
		if (soloActivos) {
			todosAlumnos = todosAlumnos.stream().filter(a -> Boolean.TRUE.equals(a.getActivo()))
					.collect(Collectors.toList());
		}


		// Create a structure to hold alumno-deporte-promotion info
		// Map<String, Map<PromotionGrade, List<AlumnoPromotionInfo>>>
		Map<String, Map<String, List<AlumnoPromotionInfo>>> deporteGradoMap = new java.util.LinkedHashMap<>();

		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() == null) {
				continue;
			}

			// Check each sport the student practices
			for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {
				// Skip inactive sports or sports where student is not ready for exam
				if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
						|| !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
					continue;
				}
				if (esMenor(alumno, alumnoDeporte)) {
					continue;
				}

				// Calculate promotion grade for this sport
				String promotionGrade = getPromotionGradeForSport(alumno, alumnoDeporte);

				// Add to the map
				String deporte = alumnoDeporte.getDeporte();
				deporteGradoMap.putIfAbsent(deporte, new java.util.LinkedHashMap<>());
				deporteGradoMap.get(deporte).putIfAbsent(promotionGrade, new ArrayList<>());
				deporteGradoMap.get(deporte).get(promotionGrade)
						.add(new AlumnoPromotionInfo(alumno, alumnoDeporte, promotionGrade));
			}
		}

		// Build HTML
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Alumnos Adultos a Promocionar"));
		html.append(".promotion-group { margin-bottom: 4mm; }");
		html.append(".sport-section { margin-bottom: 6mm; page-break-inside: avoid; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(cabeceraInforme("Adultos a Promocionar", contextoActivos(soloActivos)));

		// If no students found
		if (deporteGradoMap.isEmpty()) {
			html.append("<p class='vacio'>");
			html.append("No se encontraron alumnos adultos aptos para promocionar.");
			html.append("</p>");
		} else {
			// Iterate through sports
			for (Map.Entry<String, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
				String deporte = deporteEntry.getKey();
				html.append("<div class='sport-section'>");
				html.append("<div class='section-header'>").append(getDeporteNombre(deporte)).append("</div>");

				// Iterate through promotion grades for this sport
				for (Map.Entry<String, List<AlumnoPromotionInfo>> gradeEntry : deporteEntry.getValue().entrySet()) {
					String promotionGrade = gradeEntry.getKey();
					List<AlumnoPromotionInfo> alumnos = gradeEntry.getValue();

					html.append("<div class='promotion-group'>");
					html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
					html.append("<table class='promotion-table'>");
					html.append("<thead><tr>");
					html.append("<th>Nombre y Apellidos</th>");
					html.append("<th class='num'>N&#186; Expediente</th>");
					html.append("<th>Grado Actual</th>");
					html.append("<th>Grado a Promocionar</th>");
					html.append("<th class='num'>Edad</th>");
					html.append("</tr></thead>");
					html.append("<tbody>");

					for (AlumnoPromotionInfo info : alumnos) {
						Alumno alumno = info.alumno;
						AlumnoDeporte alumnoDeporte = info.alumnoDeporte;
						html.append("<tr>");
						html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
								.append("</td>");
						html.append("<td class='num'>").append(alumno.getNumeroExpediente()).append("</td>");

						html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

						// Grado a Promocionar with belt
						Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
						html.append(generarCeldaGrado(gradoPromocion,
								progresionGrados.obtenerNombreVisible(gradoPromocion)));

						int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
						html.append("<td class='num'>").append(edad).append("</td>");
						html.append("</tr>");
					}

					html.append("</tbody>");
					html.append("</table>");
					html.append("</div>");
				}

				html.append("</div>"); // Close sport-section
			}
		}

		html.append("</body></html>");

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(os);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de adultos a promocionar: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el informe PDF de adultos a promocionar", e);
		}
		return os.toByteArray();
	}

	@Override
	public byte[] generarInformeInfantilesAPromocionarTaekwondo(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, CatalogoLegadoTaemoi.TAEKWONDO, true);
	}

	@Override
	public byte[] generarInformeInfantilesAPromocionarKickboxing(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, CatalogoLegadoTaemoi.KICKBOXING, true);
	}

	@Override
	public byte[] generarInformeAdultosAPromocionarTaekwondo(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, CatalogoLegadoTaemoi.TAEKWONDO, false);
	}

	@Override
	public byte[] generarInformeAdultosAPromocionarKickboxing(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, CatalogoLegadoTaemoi.KICKBOXING, false);
	}

	/**
	 * Generic method to generate promotion reports filtered by sport
	 *
	 * @param soloActivos Filter only active students
	 * @param deporte     Sport to filter (TAEKWONDO or KICKBOXING)
	 * @param esInfantil  true for menores (disciplina.edad_adulto), false for
	 *                    adultos
	 * @return PDF bytes
	 */
	private byte[] generarInformePromocionPorDeporte(boolean soloActivos, String deporte, boolean esInfantil) {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();

		// Filter by active status if requested
		if (soloActivos) {
			todosAlumnos = todosAlumnos.stream().filter(a -> Boolean.TRUE.equals(a.getActivo()))
					.collect(Collectors.toList());
		}


		// Map<PromotionGrade, List<AlumnoPromotionInfo>>
		Map<String, List<AlumnoPromotionInfo>> gradoMap = new java.util.LinkedHashMap<>();

		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() == null) {
				continue;
			}

			// Check each sport the student practices
			for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {
				// Skip if not the sport we're looking for
				if (!java.util.Objects.equals(alumnoDeporte.getDeporte(), deporte)) {
					continue;
				}

				// Skip inactive sports or sports where student is not ready for exam
				if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
						|| !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
					continue;
				}
				boolean esMenor = esMenor(alumno, alumnoDeporte);
				if (esInfantil != esMenor) {
					continue;
				}

				// Calculate promotion grade for this sport
				String promotionGrade = getPromotionGradeForSport(alumno, alumnoDeporte);

				// Add to the map
				gradoMap.putIfAbsent(promotionGrade, new ArrayList<>());
				gradoMap.get(promotionGrade).add(new AlumnoPromotionInfo(alumno, alumnoDeporte, promotionGrade));
			}
		}

		// Build HTML
		String categoriaTexto = esInfantil ? "Infantiles" : "Adultos";
		String deporteTexto = getDeporteNombre(deporte);
		String titulo = categoriaTexto + " a Promocionar - " + deporteTexto;

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Alumnos " + categoriaTexto + " a Promocionar - " + deporteTexto));
		html.append(".promotion-group { margin-bottom: 4mm; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(cabeceraInforme(titulo, contextoActivos(soloActivos)));

		// If no students found
		if (gradoMap.isEmpty()) {
			html.append("<p class='vacio'>");
			html.append("No se encontraron alumnos ").append(categoriaTexto.toLowerCase()).append(" de ")
					.append(deporteTexto).append(" aptos para promocionar.");
			html.append("</p>");
		} else {
			// Iterate through promotion grades
			for (Map.Entry<String, List<AlumnoPromotionInfo>> entry : gradoMap.entrySet()) {
				String promotionGrade = entry.getKey();
				List<AlumnoPromotionInfo> alumnos = entry.getValue();

				html.append("<div class='promotion-group'>");
				html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
				html.append("<table class='promotion-table'>");
				html.append("<thead><tr>");
				html.append("<th>Nombre y Apellidos</th>");
				html.append("<th class='num'>N&#186; Expediente</th>");
				html.append("<th>Grado Actual</th>");
				html.append("<th>Grado a Promocionar</th>");
				html.append("<th class='num'>Edad</th>");
				html.append("</tr></thead>");
				html.append("<tbody>");

				for (AlumnoPromotionInfo info : alumnos) {
					Alumno alumno = info.alumno;
					AlumnoDeporte alumnoDeporte = info.alumnoDeporte;
					html.append("<tr>");
					html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
							.append("</td>");
					html.append("<td class='num'>").append(alumno.getNumeroExpediente()).append("</td>");

					html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

					// Grado a Promocionar with belt
					Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
					html.append(generarCeldaGrado(gradoPromocion,
							progresionGrados.obtenerNombreVisible(gradoPromocion)));

					int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
					html.append("<td class='num'>").append(edad).append("</td>");
					html.append("</tr>");
				}

				html.append("</tbody>");
				html.append("</table>");
				html.append("</div>");
			}
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outputStream.toByteArray();
	}

	/**
	 * Calculates the next grade for a student in a specific sport. Works with the
	 * multi-sport system.
	 */
	private String getPromotionGradeForSport(Alumno alumno, AlumnoDeporte alumnoDeporte) {
		if (alumno == null || alumnoDeporte == null || alumnoDeporte.getGrado() == null) {
			return "Sin Grado Asignado";
		}

		// Siguiente grado en el sistema de la disciplina (orden + aplica_a), por grupo de edad
		Grado nuevoGrado = siguienteGrado(alumno, alumnoDeporte);

		if (nuevoGrado == null) {
			return "Grado Máximo Alcanzado";
		}

		return progresionGrados.obtenerNombreVisible(nuevoGrado);
	}

	@Override
	public byte[] generarListadoAsistencia(int year, int month, String grupo, String deporte) throws IOException {
		return generarListadoAsistencia(year, month, Collections.singletonList(grupo), deporte);
	}

	@Override
	public byte[] generarListadoAsistencia(int year, int month, List<String> grupos, String deporte)
			throws IOException {
		List<String> gruposFiltrados = grupos == null ? Collections.emptyList()
				: grupos.stream().filter(g -> g != null && !g.isBlank()).map(String::trim).distinct()
						.collect(Collectors.toList());

		if (gruposFiltrados.isEmpty()) {
			throw new IllegalArgumentException("Debe seleccionar al menos un grupo");
		}

		gruposFiltrados.sort(Comparator.comparing(g -> DiaSemanaUtils.mapGrupoToDayOfWeek(g).getValue()));
		// Responsable de sede: solo los turnos de grupos de sus sedes. Gestión: todos, como hasta ahora.
		final java.util.Set<Long> sedesDelResponsable = gestionPorSede.restriccion()
				.map(com.taemoi.project.organizacion.AlcanceSesion::sedes).orElse(null);

		List<String> deportesFiltrar = deporte != null ? Collections.singletonList(deporte)
				: Arrays.asList(CatalogoLegadoTaemoi.TAEKWONDO, CatalogoLegadoTaemoi.KICKBOXING, CatalogoLegadoTaemoi.PILATES,
						CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA);

		List<String> deportesOrdenados = Arrays.asList(CatalogoLegadoTaemoi.TAEKWONDO, CatalogoLegadoTaemoi.KICKBOXING, CatalogoLegadoTaemoi.PILATES,
				CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA);
		List<String> deportesReporte = deportesOrdenados.stream().filter(deportesFiltrar::contains)
				.collect(Collectors.toList());

		class TurnoWithAlumnos {
			com.taemoi.project.entities.Turno turno;
			List<AlumnoDeporte> alumnos;

			TurnoWithAlumnos(com.taemoi.project.entities.Turno turno, List<AlumnoDeporte> alumnos) {
				this.turno = turno;
				this.alumnos = alumnos;
			}
		}

		MarcaEscuela marca = marcaEscuela.deActiva();
		StringBuilder html = new StringBuilder();

		// HTML Header with styles
		abrirHtmlAsistencia(html, marca);

		boolean firstPage = true;

		for (String grupo : gruposFiltrados) {
			List<AlumnoDeporte> allAlumnosDeporte = alumnoDeporteRepository.findActivosByDeporteIn(deportesFiltrar)
					.stream().filter(ad -> ad.getAlumno() != null)
					.filter(ad -> ad.getAlumno().getFechaNacimiento() != null && ad.getAlumno().getTurnos() != null
							&& !ad.getAlumno().getTurnos().isEmpty())
					.filter(ad -> ad.getAlumno().getTurnos().stream()
							.anyMatch(t -> t.getDiaSemana() != null && t.getDiaSemana().equalsIgnoreCase(grupo)))
					.collect(Collectors.toList());

			// Collect all turnos with their alumnos, grouped by deporte
			Map<String, List<TurnoWithAlumnos>> turnosByDeporte = new java.util.HashMap<>();

			// Process each alumno to build the turno-alumno-deporte relationships
			for (AlumnoDeporte alumnoDeporte : allAlumnosDeporte) {
				Alumno alumno = alumnoDeporte.getAlumno();
				String alumnoDeporteEnum = alumnoDeporte.getDeporte();
				if (alumno == null || alumnoDeporteEnum == null) {
					continue;
				}

				for (com.taemoi.project.entities.Turno turno : alumno.getTurnos()) {
					if (turno.getDiaSemana() == null || !turno.getDiaSemana().equalsIgnoreCase(grupo)) {
						continue;
					}
					if (turno.getGrupo() == null || turno.getGrupo().getDeporte() == null) {
						continue;
					}
					if (sedesDelResponsable != null && (turno.getGrupo().getSedeId() == null
							|| !sedesDelResponsable.contains(turno.getGrupo().getSedeId()))) {
						continue;
					}
					if (!java.util.Objects.equals(turno.getGrupo().getDeporte(), alumnoDeporteEnum)) {
						continue;
					}

					turnosByDeporte.computeIfAbsent(alumnoDeporteEnum, k -> new ArrayList<>());

					TurnoWithAlumnos existing = turnosByDeporte.get(alumnoDeporteEnum).stream()
							.filter(twa -> twa.turno.getId().equals(turno.getId())).findFirst().orElse(null);

					if (existing == null) {
						List<AlumnoDeporte> alumnosForTurno = new ArrayList<>();
						alumnosForTurno.add(alumnoDeporte);
						turnosByDeporte.get(alumnoDeporteEnum).add(new TurnoWithAlumnos(turno, alumnosForTurno));
					} else if (!existing.alumnos.contains(alumnoDeporte)) {
						existing.alumnos.add(alumnoDeporte);
					}
				}
			}

			// Sort turnos within each deporte by time
			for (List<TurnoWithAlumnos> turnos : turnosByDeporte.values()) {
				turnos.sort(Comparator.comparing(twa -> twa.turno.getHoraInicio()));
			}

			// Calculate dates for the month
			List<LocalDate> fechas = fechasDelMes(year, month, DiaSemanaUtils.mapGrupoToDayOfWeek(grupo));

			for (String deporteActual : deportesReporte) {
				List<TurnoWithAlumnos> turnosDeporte = turnosByDeporte.get(deporteActual);
				if (turnosDeporte == null || turnosDeporte.isEmpty()) {
					continue;
				}

				for (TurnoWithAlumnos twa : turnosDeporte) {
					// Add page break before each page except the first
					if (!firstPage) {
						html.append("<div class='page-break'></div>");
					}
					firstPage = false;

					paginaAsistencia(html, marca, year, month, deporteActual, grupo, twa.turno, twa.alumnos, fechas);
				}
			}
		}

		html.append("</body></html>");

		return renderizarAsistencia(html.toString());
	}

	/**
	 * Pieza E7-2 (D7-15): la hoja de asistencia del grupo de un profesor, por ids de turno (nunca por nombre ni por día,
	 * que mezclarían grupos homónimos de otras sedes). Por cada turno (por día de la semana y hora), una página como la de
	 * gestión con los alumnos ACTIVOS del turno que además están en el grupo del turno ({@code alumno_grupo}) y tienen la
	 * inscripción activa en la disciplina del grupo. Turnos de la escuela activa ({@code @TenantId}); sin grupo o con un
	 * día desconocido, no salen. No lee datos de salud.
	 */
	@Override
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public byte[] generarListadoAsistenciaPorTurnos(int year, int month, List<Long> turnoIds) throws IOException {
		if (month < 1 || month > 12) {
			throw new IllegalArgumentException("Mes no válido.");
		}
		List<com.taemoi.project.entities.Turno> turnos = new ArrayList<>();
		if (turnoIds != null && !turnoIds.isEmpty()) {
			for (com.taemoi.project.entities.Turno turno : turnoRepository
					.findAllById(turnoIds.stream().filter(java.util.Objects::nonNull).distinct().toList())) {
				if (turno.getGrupo() != null && turno.getGrupo().getDeporte() != null && diaDeLaSemana(turno) != null) {
					turnos.add(turno);
				}
			}
		}
		turnos.sort(Comparator.comparing((com.taemoi.project.entities.Turno t) -> diaDeLaSemana(t).getValue())
				.thenComparing(com.taemoi.project.entities.Turno::getHoraInicio,
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(com.taemoi.project.entities.Turno::getId));

		Map<String, List<AlumnoDeporte>> activosPorDeporte = new java.util.HashMap<>();
		MarcaEscuela marca = marcaEscuela.deActiva();
		StringBuilder html = new StringBuilder();
		abrirHtmlAsistencia(html, marca);
		boolean firstPage = true;
		for (com.taemoi.project.entities.Turno turno : turnos) {
			String deporte = turno.getGrupo().getDeporte();
			java.util.Set<Long> delGrupo = turno.getGrupo().getAlumnos().stream().map(Alumno::getId)
					.collect(Collectors.toSet());
			java.util.Set<Long> delTurno = turno.getAlumnos().stream().map(Alumno::getId).collect(Collectors.toSet());
			// Misma consulta que gestión (alumno e inscripción activos), acotada al turno y a su grupo.
			List<AlumnoDeporte> alumnos = activosPorDeporte
					.computeIfAbsent(deporte, d -> alumnoDeporteRepository.findActivosByDeporteIn(List.of(d))).stream()
					.filter(ad -> ad.getAlumno() != null && ad.getAlumno().getFechaNacimiento() != null)
					.filter(ad -> delTurno.contains(ad.getAlumno().getId()) && delGrupo.contains(ad.getAlumno().getId()))
					.collect(Collectors.toCollection(ArrayList::new));
			if (alumnos.isEmpty()) {
				continue;
			}
			if (!firstPage) {
				html.append("<div class='page-break'></div>");
			}
			firstPage = false;
			paginaAsistencia(html, marca, year, month, deporte, turno.getDiaSemana(), turno, alumnos,
					fechasDelMes(year, month, diaDeLaSemana(turno)));
		}
		html.append("</body></html>");
		return renderizarAsistencia(html.toString());
	}

	private static DayOfWeek diaDeLaSemana(com.taemoi.project.entities.Turno turno) {
		if (turno.getDiaSemana() == null) {
			return null;
		}
		try {
			return DiaSemanaUtils.mapGrupoToDayOfWeek(turno.getDiaSemana());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Cabecera HTML y estilos del listado de asistencia (el de gestión y el del profesor, E7-2). Decisión 315: la
	 * cabecera, el pie y la tipografía comunes; conserva su rejilla densa, sus márgenes estrechos y los colores que
	 * codifican información (cinturón, licencia, apto para examen).
	 */
	private static void abrirHtmlAsistencia(StringBuilder html, MarcaEscuela marca) {
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>");
		html.append("<style>");
		html.append(EstiloPdf.pie(marca.asistenciaPrincipal(), "Listado de asistencia"));
		html.append(EstiloPdf.cssComun());
		html.append("@page { margin: 8.5mm; }");
		html.append("body { margin: 0; padding: 0; }");
		html.append(".page-break { page-break-after: always; }");
		html.append(".cab { margin-bottom: 3mm; padding-bottom: 2mm; }");
		html.append(".table-container { width: 100%; overflow: visible; }");
		html.append(".table-wrapper { display: table; width: auto; table-layout: fixed; margin-left: auto; margin-right: auto; padding-left: 4mm; padding-right: 4mm; }");
		html.append(".table-cell { display: table-cell; vertical-align: top; overflow: visible; }");
		html.append(".main-table { border-collapse: collapse; width: 100%; table-layout: fixed; margin-top: 0; }");
		html.append(
				".main-table th, .main-table td, .side-table th, .side-table td { border: 0.5pt solid #cfcfc8; text-align: center; font-size: 8pt; vertical-align: middle; box-sizing: border-box; }");
		html.append(".main-table th, .side-table th { padding: 1.2mm 0.6mm; font-size: 7.5pt; }");
		html.append(
				".main-table tbody td, .side-table tbody td { padding: 0; height: 6.5mm; max-height: 6.5mm; min-height: 6.5mm; line-height: 6.5mm; overflow: hidden; }");
		html.append(
				".main-table thead th, .side-table thead th { background-color: #fafaf8; color: #5d6573; font-weight: 600; height: 8mm; min-height: 8mm; max-height: 8mm; overflow: hidden; vertical-align: middle; border-bottom: 1pt solid #cfcfc8; }");
		html.append(
				".compact .main-table tbody td, .compact .side-table tbody td { height: 5.4mm; max-height: 5.4mm; min-height: 5.4mm; line-height: 5.4mm; font-size: 7.2pt; }");
		html.append(
				".compact .main-table th, .compact .side-table th { height: 7mm; min-height: 7mm; max-height: 7mm; font-size: 7.2pt; padding: 1mm 0.5mm; }");
		html.append(".main-table th:first-child, .main-table td:first-child { width: 11mm; font-size: 7pt; }");
		html.append(".main-table th:nth-child(2), .main-table td:nth-child(2) { width: 13mm; }");
		html.append(".main-table th:nth-child(3), .main-table td:nth-child(3) { width: 11mm; font-size: 7.5pt; }");
		html.append(".main-table th:nth-child(4), .main-table td:nth-child(4) { width: 9mm; font-size: 7.5pt; }");
		html.append(
				".main-table th:nth-child(5) { width: 75mm; text-align: left; padding: 1.2mm 0.6mm 1.2mm 1mm; font-size: 7.5pt; }");
		html.append(
				".main-table td:nth-child(5) { width: 75mm; text-align: left; padding-left: 1mm; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 7.5pt; }");
		html.append(".main-table th:nth-child(6), .main-table td:nth-child(6) { width: 13mm; font-size: 7.5pt; }");
		html.append(".cinturon-blanco { background: #ffffff; }");
		html.append(".cinturon-amarillo { background: #ffeb3b; }");
		html.append(".cinturon-naranja { background: #ff9800; }");
		html.append(".cinturon-verde { background: #4caf50; }");
		html.append(".cinturon-azul { background: #2196f3; }");
		html.append(".cinturon-rojo { background: #f44336; }");
		html.append(".cinturon-negro { background: #212529; color: #ffffff; font-weight: 700; font-size: 7pt; }");
		html.append(".cinturon-split { position: relative; width: 100%; height: 100%; }");
		html.append(".cinturon-half-superior { position: absolute; width: 100%; height: 50%; top: 0; left: 0; }");
		html.append(".cinturon-half-inferior { position: absolute; width: 100%; height: 50%; bottom: 0; left: 0; }");
		html.append(".licencia-no { color: #dc3545; font-weight: 700; }");
		html.append(".licencia-ok { color: #28a745; font-weight: 600; }");
		html.append(".licencia-caducada { color: #ffc107; font-weight: 600; }");
		html.append(
				".apto-examen { background-color: #d4edda; color: #155724; font-weight: 700; font-size: 7pt; vertical-align: middle; }");
		html.append(
				".no-apto-examen { background-color: #ffffff; color: #6c757d; font-size: 7pt; vertical-align: middle; }");
		html.append(
				".side-table { margin-left: 3.3mm; margin-top: 0; border: 0.5pt solid #cfcfc8; table-layout: fixed; font-size: 6.5pt; max-width: 95%; width: auto; display: inline-table; border-collapse: collapse; }");
		html.append(
				".side-table th, .side-table td { width: 6mm; padding: 0.8mm 0.3mm; text-align: center; box-sizing: border-box; }");
		html.append(
				".side-table tfoot td { background: #fafaf8; color: #5d6573; font-weight: 600; text-align: center; font-size: 6.5pt; }");
		html.append(".main-table tbody tr, .side-table tbody tr { page-break-inside: avoid; }");
		html.append(".main-table thead, .side-table thead { display: table-header-group; }");
		html.append("</style>");
		html.append("</head><body>");
	}

	/** Fechas del mes que caen en ese día de la semana. */
	private static List<LocalDate> fechasDelMes(int year, int month, DayOfWeek dow) {
		LocalDate inicio = LocalDate.of(year, month, 1);
		LocalDate finMes = inicio.with(TemporalAdjusters.lastDayOfMonth());
		List<LocalDate> fechas = new ArrayList<>();
		LocalDate actual = inicio.with(TemporalAdjusters.nextOrSame(dow));
		while (!actual.isAfter(finMes)) {
			fechas.add(actual);
			actual = actual.plusWeeks(1);
		}
		return fechas;
	}

	/** Una página del listado de asistencia: un turno y una disciplina (el de gestión y el del profesor, E7-2). */
	private void paginaAsistencia(StringBuilder html, MarcaEscuela marca, int year, int month, String deporte, String dia,
			com.taemoi.project.entities.Turno turno, List<AlumnoDeporte> alumnos, List<LocalDate> fechas) {
		// Sort alumnos by nombre
		alumnos.sort(Comparator.comparing(ad -> ad.getAlumno().getNombre()));

		int totalAlumnos = alumnos.size();
		String turnoStr = turno.getHoraInicio() + " a " + turno.getHoraFin();

		// Cabecera común (decisión 315): el mes en el título; disciplina, alumnos, día y turno como contexto.
		String nombreMes = Month.of(month).getDisplayName(TextStyle.FULL, Locale.of("es"));
		String titulo = "Listado de asistencia - " + conMayuscula(nombreMes) + " " + year;
		// El día, con mayúscula inicial en los dos listados (gestión lo recibe como venga escrito: «lunes»).
		String contexto = contexto(EstiloPdf.esc(deporte.replace("_", " ")), "Total: " + totalAlumnos + " alumnos",
				EstiloPdf.esc(conMayuscula(dia)) + " - Turno de " + EstiloPdf.esc(turnoStr));
		html.append(EstiloPdf.cabecera(marca.colorPrimario(), marca.logoInformes(), marca.asistenciaPrincipalHtml(),
				marca.asistenciaSubtituloHtml(), titulo, contexto, EstiloPdf.esc(EstiloPdf.fechaGeneracion())));

		// Tables
		boolean compact = totalAlumnos >= 32;
		html.append("<div class='table-container")
				.append(compact ? " compact" : "")
				.append("'>");
		html.append("<div class='table-wrapper'>");
		html.append("<div class='table-cell'>");
		html.append("<table class='main-table'>");
		html.append("<thead><tr>");
		html.append(
				"<th>Apto</th><th>Grado</th><th>Lic. Fed</th><th>Edad</th><th>Nombre y Apellidos</th><th>Nº Exp.</th>");
		html.append("</tr></thead><tbody>");

		for (AlumnoDeporte ad : alumnos) {
			Alumno a = ad.getAlumno();
			LocalDate nac = Instant.ofEpochMilli(a.getFechaNacimiento().getTime())
					.atZone(ZoneId.systemDefault()).toLocalDate();
			int edad = Period.between(nac, LocalDate.now()).getYears();

			// Determinar estado de licencia
			boolean tieneLicencia = Boolean.TRUE.equals(ad.getTieneLicencia())
					&& ad.getNumeroLicencia() != null;
			String lic;
			String licClass;

			if (!tieneLicencia) {
				// No tiene licencia
				lic = "NO";
				licClass = "licencia-no";
			} else {
				// Tiene licencia, verificar si está caducada
				boolean licenciaCaducada = false;
				if (ad.getFechaLicencia() != null) {
					LocalDate fechaLicencia = Instant.ofEpochMilli(ad.getFechaLicencia().getTime())
							.atZone(ZoneId.systemDefault()).toLocalDate();
					// La lógica debe coincidir con listado-alumnos.component.ts:
					// Una licencia es válida si su año >= año actual
					int añoLicencia = fechaLicencia.getYear();
					int añoActual = LocalDate.now().getYear();
					licenciaCaducada = añoLicencia < añoActual;
				}

				lic = ad.getNumeroLicencia().toString();
				licClass = licenciaCaducada ? "licencia-caducada" : "licencia-ok";
			}

			// Calculate months with current grade
			int mesesConGrado = 0;
			if (ad.getFechaGrado() != null) {
				LocalDate fechaGrado = Instant.ofEpochMilli(ad.getFechaGrado().getTime())
						.atZone(ZoneId.systemDefault()).toLocalDate();
				mesesConGrado = (int) Period.between(fechaGrado, LocalDate.now()).toTotalMonths();
			}
			boolean aptoExamen = Boolean.TRUE.equals(ad.getAptoParaExamen());
			String aptoClass = aptoExamen ? "apto-examen" : "no-apto-examen";
			String aptoText = mesesConGrado + "m";

			html.append("<tr>");
			html.append("<td class='").append(aptoClass).append("'>").append(aptoText).append("</td>");
			html.append(CinturonesPdf.celdaAsistencia(ad.getGrado()));
			html.append("<td class='").append(licClass).append("'>").append(lic).append("</td>");
			html.append("<td>").append(edad).append("</td>");
			html.append("<td>").append(a.getNombre()).append(" ").append(a.getApellidos()).append("</td>");
			html.append("<td>").append(a.getNumeroExpediente()).append("</td>");
			html.append("</tr>");
		}

		html.append("</tbody></table>");
		html.append("</div>");

		// Attendance grid
		html.append("<div class='table-cell'>");
		html.append("<table class='side-table'><thead><tr>");
		for (LocalDate f : fechas) {
			html.append("<th>").append(f.getDayOfMonth()).append("</th>");
		}
		html.append("</tr></thead><tbody>");
		for (int i = 0; i < totalAlumnos; i++) {
			html.append("<tr>");
			for (@SuppressWarnings("unused")
			LocalDate f : fechas) {
				html.append("<td></td>");
			}
			html.append("</tr>");
		}
		html.append("</tbody><tfoot><tr>");
		for (LocalDate f : fechas) {
			html.append("<td>").append(f.getDayOfMonth()).append("</td>");
		}
		html.append("</tr></tfoot></table>");
		html.append("</div>");
		html.append("</div>");
		html.append("</div>");
	}

	/** Primera letra en mayúscula («lunes» → «Lunes», «septiembre» → «Septiembre»); el resto, igual. */
	private static String conMayuscula(String texto) {
		return texto == null || texto.isEmpty() ? ""
				: texto.substring(0, 1).toUpperCase(Locale.of("es")) + texto.substring(1);
	}

	private static byte[] renderizarAsistencia(String html) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.useFastMode();
		builder.withHtmlContent(html, null);
		builder.toStream(os);
		try {
			builder.run();
		} catch (Throwable e) {
			System.err.println("Error generando PDF de asistencia: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el listado de asistencia PDF", e);
		}
		return os.toByteArray();
	}


	/**
	 * Entrega 6-3 (D6-13, D6-9): el responsable de sede, solo las deudas imputadas a sus sedes (sede del cargo). La
	 * gestión, la lista de siempre (sin tocarla).
	 */
	private List<com.taemoi.project.entities.ProductoAlumno> deudasDeSusSedes(
			List<com.taemoi.project.entities.ProductoAlumno> deudas) {
		java.util.Optional<java.util.Set<Long>> suyas = gestionPorSede.restriccion()
				.map(com.taemoi.project.organizacion.AlcanceSesion::sedes);
		if (suyas.isEmpty()) {
			return deudas;
		}
		return deudas.stream().filter(pa -> pa.getSedeId() != null && suyas.get().contains(pa.getSedeId()))
				.collect(Collectors.toList());
	}

	@Override
	public byte[] generarInformeDeudas(boolean soloActivos) {
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = deudasDeSusSedes(productoAlumnoRepository
				.findAllUnpaidWithAlumno());

		// Filter by active status if requested
		if (soloActivos) {
			productosImpagados = productosImpagados.stream()
					.filter(pa -> pa.getAlumno() != null && Boolean.TRUE.equals(pa.getAlumno().getActivo()))
					.collect(Collectors.toList());
		}


		// Group debts by student
		Map<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> deudasPorAlumno = productosImpagados.stream()
				.collect(Collectors.groupingBy(com.taemoi.project.entities.ProductoAlumno::getAlumno));

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Informe de Deudas de Alumnos"));
		html.append(".total-general { margin-top: 6mm; padding-top: 2mm; border-top: 1pt solid #cfcfc8; }");
		html.append(".total-general h3 { margin: 0; }");
		html.append(".alumno-section {");
		html.append("  page-break-inside: avoid;");
		html.append("  margin-bottom: 8mm;");
		html.append("}");
		html.append(".alumno-section h3 {");
		html.append("  page-break-after: avoid;");
		html.append("}");
		html.append(".alumno-section table {");
		html.append("  page-break-inside: auto;");
		html.append("}");
		html.append(".alumno-section tr {");
		html.append("  page-break-inside: avoid;");
		html.append("  page-break-after: auto;");
		html.append("}");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(cabeceraInforme("Informe de Deudas", contextoActivos(soloActivos)));

		double totalGeneral = 0.0;

		if (deudasPorAlumno.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("No hay deudas pendientes");
			html.append("</div>");
		} else {
			html.append("<div class='section-header'>");
			html.append("Deudas Pendientes (" + deudasPorAlumno.size() + " alumnos)");
			html.append("</div>");

			// Sort by student name
			List<Map.Entry<Alumno, List<com.taemoi.project.entities.ProductoAlumno>>> sortedEntries = deudasPorAlumno
					.entrySet().stream().sorted((e1, e2) -> {
						String nombre1 = e1.getKey().getNombre() + " " + e1.getKey().getApellidos();
						String nombre2 = e2.getKey().getNombre() + " " + e2.getKey().getApellidos();
						return nombre1.compareTo(nombre2);
					}).collect(Collectors.toList());

			for (Map.Entry<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> entry : sortedEntries) {
				Alumno alumno = entry.getKey();
				List<com.taemoi.project.entities.ProductoAlumno> deudas = entry.getValue();

				// Decisión 297: el total suma el importe (precio × cantidad); la columna «Precio» sigue siendo el unitario.
				double totalAlumno = deudas.stream().mapToDouble(ImporteCargo::sinRedondear).sum();
				totalGeneral += totalAlumno;

				html.append("<div class='alumno-section'>");
				html.append("<h3>")
						.append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append(" (Exp. ")
						.append(alumno.getNumeroExpediente()).append(")");
				html.append(" - <span class='pendiente-dinero'>Total: ").append(String.format("%.2f", totalAlumno))
						.append(" €</span>");
				html.append("</h3>");

				html.append("<table>");
				html.append("<thead><tr>");
				html.append("<th>Concepto</th>");
				html.append("<th class='num'>Fecha Asignación</th>");
				html.append("<th class='num'>Cantidad</th>");
				html.append("<th class='num'>Precio</th>");
				html.append("<th>Notas</th>");
				html.append("</tr></thead>");
				html.append("<tbody>");

				for (com.taemoi.project.entities.ProductoAlumno pa : deudas) {
					html.append("<tr>");
					html.append("<td>").append(pa.getConcepto() != null ? pa.getConcepto() : "N/A").append("</td>");

					String fechaAsignacion = "N/A";
					if (pa.getFechaAsignacion() != null) {
						LocalDate fecha = Instant.ofEpochMilli(pa.getFechaAsignacion().getTime())
								.atZone(ZoneId.systemDefault()).toLocalDate();
						DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
						fechaAsignacion = fecha.format(dateFormatter);
					}
					html.append("<td class='num'>").append(fechaAsignacion).append("</td>");

					html.append("<td class='num'>").append(pa.getCantidad() != null ? pa.getCantidad() : 1).append("</td>");
					html.append("<td class='num'>")
							.append(String.format("%.2f", pa.getPrecio() != null ? pa.getPrecio() : 0.0))
							.append(" €</td>");
					html.append("<td>").append(pa.getNotas() != null ? pa.getNotas() : "").append("</td>");
					html.append("</tr>");
				}

				html.append("</tbody>");
				html.append("</table>");
				html.append("</div>");
			}

			// Add total general
			html.append("<div class='total-general'>");
			html.append("<h3>TOTAL GENERAL: <span class='pendiente-dinero'>").append(String.format("%.2f", totalGeneral))
					.append(" €</span></h3>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outputStream.toByteArray();
	}

	@Override
	public byte[] generarInformeDeudasCSV(boolean soloActivos) {
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = deudasDeSusSedes(productoAlumnoRepository
				.findAllUnpaidWithAlumno());

		// Filter by active status if requested
		if (soloActivos) {
			productosImpagados = productosImpagados.stream()
					.filter(pa -> pa.getAlumno() != null && Boolean.TRUE.equals(pa.getAlumno().getActivo()))
					.collect(Collectors.toList());
		}

		// Group debts by student
		Map<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> deudasPorAlumno = productosImpagados.stream()
				.collect(Collectors.groupingBy(com.taemoi.project.entities.ProductoAlumno::getAlumno));

		StringBuilder csv = new StringBuilder();
		// UTF-8 BOM for Excel compatibility
		csv.append('\ufeff');

		// CSV Header
		csv.append("Alumno,Nº Expediente,Concepto,Fecha Asignación,Cantidad,Precio (€),Notas\n");

		// Sort by student name
		List<Map.Entry<Alumno, List<com.taemoi.project.entities.ProductoAlumno>>> sortedEntries = deudasPorAlumno
				.entrySet().stream().sorted((e1, e2) -> {
					String nombre1 = e1.getKey().getNombre() + " " + e1.getKey().getApellidos();
					String nombre2 = e2.getKey().getNombre() + " " + e2.getKey().getApellidos();
					return nombre1.compareTo(nombre2);
				}).collect(Collectors.toList());

		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		double totalGeneral = 0.0;

		for (Map.Entry<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> entry : sortedEntries) {
			Alumno alumno = entry.getKey();
			List<com.taemoi.project.entities.ProductoAlumno> deudas = entry.getValue();

			String nombreCompleto = alumno.getNombre() + " " + alumno.getApellidos();
			String numExpediente = String.valueOf(alumno.getNumeroExpediente());

			for (com.taemoi.project.entities.ProductoAlumno pa : deudas) {
				csv.append('"').append(escapeCSV(nombreCompleto)).append('"').append(',');
				csv.append('"').append(escapeCSV(numExpediente)).append('"').append(',');
				csv.append('"').append(escapeCSV(pa.getConcepto() != null ? pa.getConcepto() : "N/A")).append('"')
						.append(',');

				String fechaAsignacion = "N/A";
				if (pa.getFechaAsignacion() != null) {
					LocalDate fecha = Instant.ofEpochMilli(pa.getFechaAsignacion().getTime())
							.atZone(ZoneId.systemDefault()).toLocalDate();
					fechaAsignacion = fecha.format(dateFormatter);
				}
				csv.append('"').append(fechaAsignacion).append('"').append(',');

				csv.append(pa.getCantidad() != null ? pa.getCantidad() : 1).append(',');

				double precio = pa.getPrecio() != null ? pa.getPrecio() : 0.0;
				totalGeneral += ImporteCargo.sinRedondear(pa);
				csv.append(String.format("%.2f", precio)).append(',');

				csv.append('"').append(escapeCSV(pa.getNotas() != null ? pa.getNotas() : "")).append('"');
				csv.append('\n');
			}
		}

		// Add total row
		csv.append("\n");
		csv.append("TOTAL GENERAL,,,,,").append(String.format("%.2f", totalGeneral)).append(",\n");

		return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Override
	public byte[] generarInformeProductos() {
		List<Producto> productos = productoRepository.findAll();
		productos.sort(Comparator.comparing(
				p -> p.getConcepto() == null ? "" : p.getConcepto().toLowerCase(Locale.ROOT)));


		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		// Decisión 315: el pie común sustituye al propio, que salía mal codificado («PÃ¡gina X de Y»).
		html.append(estilosInforme("Listado de Productos"));
		html.append(".productos-table { margin-top: 0.5mm; }");
		html.append(".productos-table { table-layout: fixed; }");
		html.append(".productos-table th:nth-child(1), .productos-table td:nth-child(1) { width: 70%; }");
		html.append(".productos-table th:nth-child(2), .productos-table td:nth-child(2) { width: 30%; }");
		html.append(".precio-cell { font-weight: 600; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		html.append(cabeceraInforme("Listado de Productos", null));

		if (productos.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("No hay productos registrados");
			html.append("</div>");
		} else {
			html.append("<div class='section-header'>");
			html.append("Productos (" + productos.size() + ")");
			html.append("</div>");

			html.append("<table class='productos-table'>");
			html.append("<thead><tr>");
			html.append("<th>Concepto</th>");
			html.append("<th class='num'>Precio</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");

			for (Producto producto : productos) {
				String concepto = producto.getConcepto() != null ? producto.getConcepto() : "N/A";
				String precio = producto.getPrecio() != null
						? String.format("%.2f", producto.getPrecio()) + " &#8364;"
						: "N/A";

				html.append("<tr>");
				html.append("<td>").append(concepto).append("</td>");
				html.append("<td class='precio-cell num'>").append(precio).append("</td>");
				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outputStream.toByteArray();
	}

	@Override
	public byte[] generarInformeMensualidades(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(null, soloActivos);
	}

	@Override
	public byte[] generarInformeMensualidadesTaekwondo(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(CatalogoLegadoTaemoi.TAEKWONDO, soloActivos);
	}

	@Override
	public byte[] generarInformeMensualidadesKickboxing(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(CatalogoLegadoTaemoi.KICKBOXING, soloActivos);
	}

	@Override
	public List<String> obtenerTemporadasReservasPlaza() {
		TreeSet<String> temporadas = new TreeSet<>(Comparator.reverseOrder());
		for (String concepto : productoAlumnoRepository.findConceptosReservaPlaza()) {
			if (concepto == null) {
				continue;
			}
			Matcher matcher = TEMPORADA_PATTERN.matcher(concepto);
			while (matcher.find()) {
				temporadas.add(matcher.group());
			}
		}
		return new ArrayList<>(temporadas);
	}

	@Override
	public byte[] generarInformeReservasPlaza(String temporada, boolean soloActivos) {
		if (temporada == null || !TEMPORADA_PATTERN.matcher(temporada).matches()) {
			throw new IllegalArgumentException("Temporada no valida");
		}

		List<com.taemoi.project.entities.ProductoAlumno> reservas = productoAlumnoRepository
				.findReservasPlazaByTemporada(temporada, soloActivos);


		Map<String, List<com.taemoi.project.entities.ProductoAlumno>> reservasPorDeporte = new java.util.LinkedHashMap<>();
		for (com.taemoi.project.entities.ProductoAlumno reserva : reservas) {
			String deporte = obtenerDeporteReserva(reserva);
			String key = deporte != null ? deporte : "SIN_DEPORTE";
			reservasPorDeporte.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reserva);
		}

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Informe de Reservas de Plaza"));
		html.append(".sport-section { page-break-inside: avoid; margin-bottom: 4mm; }");
		html.append(".sport-section tr { page-break-inside: avoid; page-break-after: auto; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");
		html.append(cabeceraInforme("Informe de Reservas de Plaza",
				contexto("Temporada " + temporada, contextoActivos(soloActivos))));

		if (reservasPorDeporte.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("No hay reservas de plaza registradas para la temporada ").append(temporada);
			html.append("</div>");
		} else {
			int totalReservas = 0;
			int totalPagadas = 0;
			int totalPendientes = 0;

			for (Map.Entry<String, List<com.taemoi.project.entities.ProductoAlumno>> entry : ordenarReservasPorDeporte(reservasPorDeporte).entrySet()) {
				List<com.taemoi.project.entities.ProductoAlumno> reservasDeporte = entry.getValue();
				String deporte = obtenerDeporteReserva(reservasDeporte.get(0));
				String deporteNombre = deporte != null ? getDeporteNombre(deporte) : "Sin deporte";
				html.append("<div class='sport-section'>");
				html.append("<div class='section-header'>");
				html.append(deporteNombre).append(" (").append(reservasDeporte.size()).append(" reservas)");
				html.append("</div>");
				html.append("<table>");
				html.append("<thead><tr>");
				html.append("<th>Alumno</th>");
				html.append("<th>Concepto</th>");
				html.append("<th>Estado</th>");
				html.append("</tr></thead>");
				html.append("<tbody>");

				for (com.taemoi.project.entities.ProductoAlumno reserva : reservasDeporte) {
					Alumno alumno = obtenerAlumnoReserva(reserva);
					boolean pagado = Boolean.TRUE.equals(reserva.getPagado());
					totalReservas++;
					if (pagado) {
						totalPagadas++;
					} else {
						totalPendientes++;
					}

					html.append("<tr>");
					html.append("<td>").append(formatearNombreAlumno(alumno)).append("</td>");
					html.append("<td>").append(reserva.getConcepto() != null ? reserva.getConcepto() : "N/A").append("</td>");
					html.append("<td><span class='").append(pagado ? "estado" : "pendiente-dinero").append("'>")
							.append(pagado ? "Pagado" : "Sin Pagar").append("</span></td>");
					html.append("</tr>");
				}

				html.append("</tbody>");
				html.append("</table>");
				html.append("</div>");
			}

			html.append("<div class='resumen-general'>");
			html.append("<h3>RESUMEN GENERAL</h3>");
			html.append("<p><strong>Temporada:</strong> ").append(temporada).append("</p>");
			html.append("<p><strong>Total reservas:</strong> ").append(totalReservas).append("</p>");
			html.append("<p><strong>Pagadas:</strong> ").append(totalPagadas).append("</p>");
			html.append("<p><strong>Sin pagar:</strong> ").append(totalPendientes).append("</p>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de reservas de plaza: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el informe PDF de reservas de plaza", e);
		}
		return outputStream.toByteArray();
	}

	private Map<String, List<com.taemoi.project.entities.ProductoAlumno>> ordenarReservasPorDeporte(
			Map<String, List<com.taemoi.project.entities.ProductoAlumno>> reservasPorDeporte) {
		return reservasPorDeporte.entrySet().stream()
				.sorted((e1, e2) -> {
					int prioridad1 = prioridadDeporte(obtenerDeporteReserva(e1.getValue().get(0)));
					int prioridad2 = prioridadDeporte(obtenerDeporteReserva(e2.getValue().get(0)));
					if (prioridad1 != prioridad2) {
						return Integer.compare(prioridad1, prioridad2);
					}
					return e1.getKey().compareTo(e2.getKey());
				})
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> entry.getValue().stream()
								.sorted(Comparator.comparing(pa -> formatearNombreAlumno(obtenerAlumnoReserva(pa))))
								.collect(Collectors.toList()),
						(a, b) -> a,
						java.util.LinkedHashMap::new));
	}

	private int prioridadDeporte(String deporte) {
		if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporte)) {
			return 0;
		}
		if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporte)) {
			return 1;
		}
		return 2;
	}

	private Alumno obtenerAlumnoReserva(com.taemoi.project.entities.ProductoAlumno reserva) {
		if (reserva.getAlumnoDeporte() != null && reserva.getAlumnoDeporte().getAlumno() != null) {
			return reserva.getAlumnoDeporte().getAlumno();
		}
		return reserva.getAlumno();
	}

	private String obtenerDeporteReserva(com.taemoi.project.entities.ProductoAlumno reserva) {
		if (reserva.getAlumnoDeporte() != null && reserva.getAlumnoDeporte().getDeporte() != null) {
			return reserva.getAlumnoDeporte().getDeporte();
		}
		String deporteConcepto = inferDeporteFromConcepto(reserva.getConcepto());
		if (deporteConcepto != null) {
			return deporteConcepto;
		}
		return deportePrincipal(reserva.getAlumno());
	}

	/** Deporte del AlumnoDeporte principal del alumno, o null si no tiene deportes. */
	private String deportePrincipal(Alumno alumno) {
		if (alumno == null) {
			return null;
		}
		AlumnoDeporte principal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());
		return principal != null ? principal.getDeporte() : null;
	}

	private String formatearNombreAlumno(Alumno alumno) {
		if (alumno == null) {
			return "N/A";
		}
		return alumno.getNombre() + " " + alumno.getApellidos();
	}

	private String formatearFecha(Date fecha) {
		if (fecha == null) {
			return "N/A";
		}
		LocalDate localDate = Instant.ofEpochMilli(fecha.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
		return localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}

	/**
	 * Generates a PDF report of student monthly fees (mensualidades).
	 *
	 * @param deporteFiltro If null, generates report for all students with color
	 *                      coding by sport. If specified, filters students by that
	 *                      sport.
	 * @param soloActivos   If true, only includes active students.
	 */
	private byte[] generarInformeMensualidadesPorDeporte(String deporteFiltro, boolean soloActivos) {
		List<com.taemoi.project.entities.ProductoAlumno> todasMensualidades;
		if (deporteFiltro != null) {
			todasMensualidades = productoAlumnoRepository
					.findMensualidadesYTarifasCompetidorByDeporteOrConcepto(deporteFiltro, deporteFiltro);
		} else {
			todasMensualidades = productoAlumnoRepository.findAllMensualidadesYTarifasCompetidorWithAlumno();
		}

		// Filter by active status if requested
		if (soloActivos) {
			todasMensualidades = todasMensualidades.stream().filter(pa -> {
				Alumno alumno = pa.getAlumnoDeporte() != null ? pa.getAlumnoDeporte().getAlumno() : pa.getAlumno();
				if (alumno == null || !Boolean.TRUE.equals(alumno.getActivo())) {
					return false;
				}
				if (pa.getAlumnoDeporte() != null && !Boolean.TRUE.equals(pa.getAlumnoDeporte().getActivo())) {
					return false;
				}
				return true;
			}).collect(Collectors.toList());
		}


		class MensualidadGroup {
			private final Alumno alumno;
			private final String deporte;
			private final List<com.taemoi.project.entities.ProductoAlumno> mensualidades = new ArrayList<>();

			MensualidadGroup(Alumno alumno, String deporte) {
				this.alumno = alumno;
				this.deporte = deporte;
			}
		}

		Map<String, MensualidadGroup> mensualidadesPorAlumno = new java.util.LinkedHashMap<>();
		for (com.taemoi.project.entities.ProductoAlumno pa : todasMensualidades) {
			AlumnoDeporte alumnoDeporte = pa.getAlumnoDeporte();
			Alumno alumno = alumnoDeporte != null ? alumnoDeporte.getAlumno() : pa.getAlumno();
			if (alumno == null) {
				continue;
			}
			String deporte = alumnoDeporte != null ? alumnoDeporte.getDeporte() : null;
			if (deporte == null) {
				deporte = inferDeporteFromConcepto(pa.getConcepto());
			}
			if (deporte == null) {
				deporte = deportePrincipal(alumno);
			}
			String deporteFinal = deporte;
			String deporteKey = deporteFinal != null ? deporteFinal : "SIN_DEPORTE";
			String key = alumno.getId() + "_" + deporteKey;
			MensualidadGroup group = mensualidadesPorAlumno.get(key);
			if (group == null) {
				group = new MensualidadGroup(alumno, deporteFinal);
				mensualidadesPorAlumno.put(key, group);
			}
			group.mensualidades.add(pa);
		}

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Informe de Mensualidades"));
		html.append(".alumno-section {");
		html.append("  page-break-inside: avoid;");
		html.append("  margin-bottom: 8mm;");
		html.append("}");
		html.append(".alumno-section h3 {");
		html.append("  page-break-after: avoid;");
		html.append("}");
		html.append(".alumno-section table {");
		html.append("  page-break-inside: auto;");
		html.append("}");
		html.append(".alumno-section tr {");
		html.append("  page-break-inside: avoid;");
		html.append("  page-break-after: auto;");
		html.append("}");
		html.append(".alumno-section { page-break-inside: avoid; margin-bottom: 5mm; }");
		html.append(".alumno-section h3 { page-break-after: avoid; }");
		html.append(".alumno-section table { page-break-inside: auto; }");
		html.append(".alumno-section tr { page-break-inside: avoid; page-break-after: auto; }");
		html.append(".resumen-alumno { font-size: 8pt; color: #5d6573; margin: 0 0 1mm 0; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Título según la disciplina filtrada
		String titulo;
		if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporteFiltro)) {
			titulo = "Informe de Mensualidades - Taekwondo";
		} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporteFiltro)) {
			titulo = "Informe de Mensualidades - Kickboxing";
		} else {
			titulo = "Informe de Mensualidades";
		}

		html.append(cabeceraInforme(titulo, contextoActivos(soloActivos)));

		int totalAlumnos = mensualidadesPorAlumno.size();
		// Pieza 6b-2c: un cargo cancelado sigue saliendo (fila «CANCELADO») pero no suma en ningún total.
		int totalMensualidades = (int) todasMensualidades.stream().filter(m -> !m.estaCancelado()).count();
		int totalPagadas = 0;
		int totalPendientes = 0;
		double totalImportePagado = 0.0;
		double totalImportePendiente = 0.0;

		if (mensualidadesPorAlumno.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("No hay mensualidades registradas");
			html.append("</div>");
		} else {
			String tituloSeccion;
			if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporteFiltro)) {
				tituloSeccion = "Mensualidades de Alumnos - Taekwondo (" + totalAlumnos + " alumnos)";
			} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporteFiltro)) {
				tituloSeccion = "Mensualidades de Alumnos - Kickboxing (" + totalAlumnos + " alumnos)";
			} else {
				tituloSeccion = "Mensualidades de Alumnos (" + totalAlumnos + " alumnos)";
			}

			html.append("<div class='section-header'>");
			html.append(tituloSeccion);
			html.append("</div>");

			// Sort by student name (and sport)
			List<MensualidadGroup> sortedGroups = mensualidadesPorAlumno.values().stream().sorted((g1, g2) -> {
				String nombre1 = (g1.alumno != null ? g1.alumno.getNombre() + " " + g1.alumno.getApellidos() : "") + " "
						+ (g1.deporte != null ? g1.deporte : "");
				String nombre2 = (g2.alumno != null ? g2.alumno.getNombre() + " " + g2.alumno.getApellidos() : "") + " "
						+ (g2.deporte != null ? g2.deporte : "");
				return nombre1.compareTo(nombre2);
			}).collect(Collectors.toList());

			for (MensualidadGroup group : sortedGroups) {
				Alumno alumno = group.alumno;
				String deporte = group.deporte;
				List<com.taemoi.project.entities.ProductoAlumno> mensualidades = group.mensualidades;

				List<com.taemoi.project.entities.ProductoAlumno> vigentes = mensualidades.stream()
						.filter(m -> !m.estaCancelado()).collect(Collectors.toList());
				int pagas = (int) vigentes.stream().filter(m -> Boolean.TRUE.equals(m.getPagado())).count();
				int pendientes = vigentes.size() - pagas;
				double importePagado = vigentes.stream().filter(m -> Boolean.TRUE.equals(m.getPagado()))
						.mapToDouble(ImporteCargo::sinRedondear).sum();
				double importePendiente = vigentes.stream().filter(m -> !Boolean.TRUE.equals(m.getPagado()))
						.mapToDouble(ImporteCargo::sinRedondear).sum();

				totalPagadas += pagas;
				totalPendientes += pendientes;
				totalImportePagado += importePagado;
				totalImportePendiente += importePendiente;

				// En el informe general, la disciplina del alumno junto a su nombre.
				String deporteNombre = "";
				if (deporteFiltro == null) {
					String nombre = deporte == null ? "Sin deporte" : getDeporteNombre(deporte);
					deporteNombre = " <span class='recuento'>[" + nombre + "]</span>";
				}

				html.append("<div class='alumno-section'>");
				html.append("<h3>")
						.append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append(" (Exp. ")
						.append(alumno.getNumeroExpediente()).append(")").append(deporteNombre);
				html.append("</h3>");

				html.append("<div class='resumen-alumno'>");
				html.append("<strong>Total mensualidades:</strong> ").append(vigentes.size());
				html.append(" | <strong>Pagadas:</strong> ").append(pagas);
				html.append(" (").append(String.format("%.2f", importePagado)).append(" €)");
				html.append(" | <strong>Pendientes:</strong> ").append(pendientes);
				html.append(" (").append(importePendiente(importePendiente)).append(")");
				html.append("</div>");

				html.append("<table>");
				html.append("<thead><tr>");
				html.append("<th>Concepto</th>");
				html.append("<th class='num'>Fecha Asignación</th>");
				html.append("<th class='num'>Importe</th>");
				html.append("<th>Estado</th>");
				html.append("<th class='num'>Fecha Pago</th>");
				html.append("<th>Notas</th>");
				html.append("</tr></thead>");
				html.append("<tbody>");

				for (com.taemoi.project.entities.ProductoAlumno pa : mensualidades) {
					boolean pagado = Boolean.TRUE.equals(pa.getPagado());

					html.append("<tr>");
					html.append("<td>").append(pa.getConcepto() != null ? pa.getConcepto() : "N/A").append("</td>");

					String fechaAsignacion = "N/A";
					if (pa.getFechaAsignacion() != null) {
						LocalDate fecha = Instant.ofEpochMilli(pa.getFechaAsignacion().getTime())
								.atZone(ZoneId.systemDefault()).toLocalDate();
						DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
						fechaAsignacion = fecha.format(dateFormatter);
					}
					html.append("<td class='num'>").append(fechaAsignacion).append("</td>");

					// Decisión 297: la fila muestra el importe del cargo (precio × cantidad), para que las filas sumen el total.
					html.append("<td class='num'>").append(String.format("%.2f", ImporteCargo.de(pa))).append(" €</td>");

					boolean cancelado = pa.estaCancelado();
					// Decisión 315: ocre solo para lo pendiente de cobro; pagado y cancelado, neutros.
					String estadoClass = cancelado || pagado ? "estado" : "pendiente-dinero";
					String estadoTexto = cancelado ? "CANCELADO" : (pagado ? "PAGADO" : "PENDIENTE");
					html.append("<td><span class='").append(estadoClass).append("'>").append(estadoTexto)
							.append("</span></td>");

					String fechaPago = "N/A";
					if (pagado && pa.getFechaPago() != null) {
						LocalDate fecha = Instant.ofEpochMilli(pa.getFechaPago().getTime())
								.atZone(ZoneId.systemDefault()).toLocalDate();
						DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
						fechaPago = fecha.format(dateFormatter);
					}
					html.append("<td class='num'>").append(fechaPago).append("</td>");

					html.append("<td>").append(pa.getNotas() != null ? pa.getNotas() : "").append("</td>");
					html.append("</tr>");
				}

				html.append("</tbody>");
				html.append("</table>");
				html.append("</div>");
			}

			// Add general summary
			html.append("<div class='resumen-general'>");
			html.append("<h3>RESUMEN GENERAL</h3>");
			html.append("<p><strong>Total de alumnos:</strong> ").append(totalAlumnos).append("</p>");
			html.append("<p><strong>Total de mensualidades:</strong> ").append(totalMensualidades).append("</p>");
			html.append("<p><strong>Mensualidades pagadas:</strong> ").append(totalPagadas).append(" (")
					.append(String.format("%.2f", totalImportePagado)).append(" €)</p>");
			html.append("<p><strong>Mensualidades pendientes:</strong> ").append(totalPendientes).append(" (")
					.append(importePendiente(totalImportePendiente)).append(")</p>");
			html.append("<p><strong>TOTAL GENERAL:</strong> ")
					.append(String.format("%.2f", totalImportePagado + totalImportePendiente)).append(" €</p>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de mensualidades: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el informe PDF de mensualidades", e);
		}
		return outputStream.toByteArray();
	}

	/** Importe pendiente («28.00 €»): en ocre si hay algo pendiente (decisión 315); sin pendiente, neutro. */
	private static String importePendiente(double importe) {
		String texto = String.format("%.2f", importe) + " €";
		return importe > 0 ? "<span class='pendiente-dinero'>" + texto + "</span>" : texto;
	}

	/** Pieza 6b-2c: los cargos no cancelados de la lista (vacía si es nula). */
	private static List<com.taemoi.project.entities.ProductoAlumno> vigentes(
			List<com.taemoi.project.entities.ProductoAlumno> cargos) {
		if (cargos == null) {
			return List.of();
		}
		return cargos.stream().filter(pa -> !pa.estaCancelado()).collect(Collectors.toList());
	}

	@Override
	public byte[] generarListadoMensualidadMensual(String mesAno, boolean soloActivos) {
		// Parse mesAno format "YYYY-MM" (e.g., "2025-12")
		String[] partes = mesAno.split("-");
		int anio = Integer.parseInt(partes[0]);
		int mes = Integer.parseInt(partes[1]);

		// Create month name in Spanish
		String[] mesesEspanol = { "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
				"SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE" };
		String nombreMes = mesesEspanol[mes - 1];

		// Build concept to search (e.g., "MENSUALIDAD DICIEMBRE 2025")
		String conceptoBase = "MENSUALIDAD " + nombreMes + " " + anio;
		String mesAnoConcepto = nombreMes + " " + anio; // Para buscar tarifas competidor

		// Get AlumnoDeporte for Taekwondo and Kickboxing (active only if requested)
		List<String> deportesFiltrados = Arrays.asList(CatalogoLegadoTaemoi.TAEKWONDO, CatalogoLegadoTaemoi.KICKBOXING);
		List<AlumnoDeporte> alumnoDeportes;
		if (soloActivos) {
			alumnoDeportes = alumnoDeporteRepository.findActivosByDeporteIn(deportesFiltrados);
		} else {
			alumnoDeportes = alumnoDeporteRepository.findAll().stream()
					.filter(ad -> ad.getDeporte() != null && deportesFiltrados.contains(ad.getDeporte()))
					.collect(Collectors.toList());
		}

		// Entrega 6-3 (D6-13, D6-3): el responsable de sede, solo las filas de los alumnos de su sede principal.
		java.util.Optional<java.util.Set<Long>> sedesDelResponsableListado = gestionPorSede.restriccion()
				.map(com.taemoi.project.organizacion.AlcanceSesion::sedes);
		if (sedesDelResponsableListado.isPresent()) {
			alumnoDeportes = alumnoDeportes.stream()
					.filter(ad -> ad.getAlumno() != null && ad.getAlumno().getSedePrincipalId() != null
							&& sedesDelResponsableListado.get().contains(ad.getAlumno().getSedePrincipalId()))
					.collect(Collectors.toList());
		}

		// Get mensualidades AND tarifas competidor for this month
		List<com.taemoi.project.entities.ProductoAlumno> productosDelMes = productoAlumnoRepository
				.findMensualidadesYTarifasCompetidorByMes(conceptoBase, mesAnoConcepto);

		// Entrega 6-4 (D6-33): sin cargo del mes, la cuantía resuelta para ese mes (con la tarifa de la sede, si la hay).
		com.taemoi.project.services.ResolutorCuantiaMensualidad cuantias = tarifaSede.preparar();
		java.time.YearMonth mesListado = java.time.YearMonth.of(anio, mes);

		// Group products by alumno+deporte, collecting all products (mensualidad +
		// tarifa competidor). Pieza 6b-2c: los cancelados se agrupan también (para pintar «Cancelado» si el alumno no
		// tiene ningún cargo vigente del mes) pero no suman a la cuantía ni cuentan para la fecha de pago.
		Map<String, List<com.taemoi.project.entities.ProductoAlumno>> productosPorAlumnoDeporte = productosDelMes
				.stream().filter(pa -> pa.getAlumno() != null && pa.getAlumnoDeporte() != null).collect(Collectors
						.groupingBy(pa -> pa.getAlumno().getId() + "_" + pa.getAlumnoDeporte().getDeporte()));

		// Build HTML for PDF
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");

		html.append(estilosInforme("Listado Mensualidad " + nombreMes + " " + anio));
		// Additional styles for this report
		html.append(".deporte-cell { font-weight: 600; }");
		html.append(".nombre-cell { vertical-align: middle; }");
		html.append(".line-item { margin: 0; padding: 0; }");
		html.append(".line-item + .line-item { margin-top: 1mm; }");
		html.append(".grado-cell .line-item + .line-item { margin-top: 2mm; }");
		html.append(".grado-cell .line-item:first-child { margin-top: 1mm; }");
		html.append(".grado-cell .line-item:last-child { margin-bottom: 1mm; }");
		html.append("tr { page-break-inside: avoid; }");
		html.append("table { -fs-table-paginate: paginate; }");
		html.append("thead { display: table-header-group; }");

		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header with logo and title below it
		html.append(cabeceraInforme("Listado Mensualidad " + nombreMes + " " + anio, contextoActivos(soloActivos)));

		// Table
		html.append("<table>");
		html.append("<thead><tr>");
		html.append("<th style='width: 28%;'>APELLIDOS Y NOMBRE</th>");
		html.append("<th style='width: 12%;'>DEPORTE</th>");
		html.append("<th style='width: 14%;'>DESCUENTO</th>");
		html.append("<th class='num' style='width: 12%;'>CUANTÍA</th>");
		html.append("<th class='num' style='width: 8%;'>COMP</th>");
		html.append("<th style='width: 12%;'>GRADO</th>");
		html.append("<th class='num' style='width: 14%;'>FECHA DE PAGO</th>");
		html.append("</tr></thead>");
		html.append("<tbody>");

		if (alumnoDeportes.isEmpty()) {
			html.append("<tr><td colspan='7' class='vacio'>");
			html.append("No hay alumnos activos de Taekwondo o Kickboxing");
			html.append("</td></tr>");
		} else {

			Comparator<AlumnoDeporte> ordenPorAlumno = Comparator.comparing((AlumnoDeporte ad) -> {
				Alumno alumno = ad.getAlumno();
				if (alumno == null) {
					return "";
				}
				String apellidos = alumno.getApellidos() == null ? "" : alumno.getApellidos();
				String nombre = alumno.getNombre() == null ? "" : alumno.getNombre();
				return (apellidos + " " + nombre).trim().toLowerCase(Locale.ROOT);
			}).thenComparing(ad -> {
				if (CatalogoLegadoTaemoi.TAEKWONDO.equals(ad.getDeporte())) {
					return 0;
				}
				if (CatalogoLegadoTaemoi.KICKBOXING.equals(ad.getDeporte())) {
					return 1;
				}
				return 2;
			});

			List<AlumnoDeporte> alumnoDeportesOrdenados = alumnoDeportes.stream().filter(ad -> ad.getAlumno() != null)
					.sorted(ordenPorAlumno).collect(Collectors.toList());

			Map<Long, List<AlumnoDeporte>> deportesPorAlumno = new java.util.LinkedHashMap<>();
			for (AlumnoDeporte ad : alumnoDeportesOrdenados) {
				Long alumnoId = ad.getAlumno().getId();
				List<AlumnoDeporte> lista = deportesPorAlumno.get(alumnoId);
				if (lista == null) {
					lista = new ArrayList<>();
					deportesPorAlumno.put(alumnoId, lista);
				}
				lista.add(ad);
			}

			for (List<AlumnoDeporte> deportesAlumno : deportesPorAlumno.values()) {
				if (deportesAlumno.isEmpty()) {
					continue;
				}
				Alumno alumno = deportesAlumno.get(0).getAlumno();
				if (alumno == null) {
					continue;
				}

				html.append("<tr>");

				// NOMBRE
				String nombreCompleto = (alumno.getApellidos() == null ? "" : alumno.getApellidos()) + " "
						+ (alumno.getNombre() == null ? "" : alumno.getNombre());
				html.append("<td class='nombre-cell'>").append(nombreCompleto.trim()).append("</td>");

				// DEPORTE
				html.append("<td class='deporte-cell'>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String deporteNombre = "";
					if (CatalogoLegadoTaemoi.TAEKWONDO.equals(ad.getDeporte())) {
						deporteNombre = "Taekwondo";
					} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(ad.getDeporte())) {
						deporteNombre = "Kickboxing";
					} else if (ad.getDeporte() != null) {
						deporteNombre = getDeporteNombre(ad.getDeporte());
					}
					html.append("<div class='line-item'>")
							.append(deporteNombre.isEmpty() ? "-" : deporteNombre).append("</div>");
				}
				html.append("</td>");

				// DESCUENTO
				html.append("<td>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String codigoTarifa = ad.getCodigoTarifa();
					String tipoTarifa = codigoTarifa != null ? codigoTarifa.replace("_", " ") : "-";
					html.append("<div class='line-item'>").append(tipoTarifa).append("</div>");
				}
				html.append("</td>");

				// CUANTIA
				html.append("<td class='num'>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String mapKey = alumno.getId() + "_" + ad.getDeporte();
					List<com.taemoi.project.entities.ProductoAlumno> delMes = productosPorAlumnoDeporte.get(mapKey);
					List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = vigentes(delMes);
					String cuantia = "-";
					if (productosAlumno.isEmpty() && delMes != null && !delMes.isEmpty()) {
						cuantia = "Cancelado";
					} else if (!productosAlumno.isEmpty()) {
						double totalCuantia = productosAlumno.stream().filter(p -> p.getPrecio() != null)
								.mapToDouble(ImporteCargo::sinRedondear).sum();
						cuantia = String.format("%.2f EUR", totalCuantia);
					} else {
						Double esperada = cuantias.cuantiaMensualidad(ad, mesListado);
						if (esperada != null) {
							cuantia = String.format("%.2f EUR", esperada);
						}
					}
					html.append("<div class='line-item'>").append(cuantia).append("</div>");
				}
				html.append("</td>");

				// COMP
				html.append("<td class='num'>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String mapKey = alumno.getId() + "_" + ad.getDeporte();
					List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = vigentes(
							productosPorAlumnoDeporte.get(mapKey));
					String comp = "-";
					if (productosAlumno != null && !productosAlumno.isEmpty()) {
						double competidorCuantia = productosAlumno.stream().filter(p -> {
							String concepto = p.getConcepto();
							return concepto != null
									&& concepto.toUpperCase(Locale.ROOT).startsWith("TARIFA COMPETIDOR");
						}).filter(p -> p.getPrecio() != null)
								.mapToDouble(ImporteCargo::sinRedondear).sum();
						if (competidorCuantia > 0) {
							comp = String.format("%.2f", competidorCuantia);
						}
					}
					html.append("<div class='line-item'>").append(comp).append("</div>");
				}
				html.append("</td>");

				// GRADO
				html.append("<td class='grado-cell'>");
				for (AlumnoDeporte ad : deportesAlumno) {
					html.append("<div class='line-item'>");
					if (ad.getGrado() != null) {
						html.append(CinturonesPdf.inline(ColoresGrado.de(ad.getGrado()), 60, 12));
					} else {
						html.append("-");
					}
					html.append("</div>");
				}
				html.append("</td>");

				// FECHA DE PAGO
				html.append("<td class='num'>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String mapKey = alumno.getId() + "_" + ad.getDeporte();
					List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = vigentes(
							productosPorAlumnoDeporte.get(mapKey));
					String fechaPago = "";
					if (productosAlumno != null && !productosAlumno.isEmpty()) {
						boolean todosPagados = productosAlumno.stream()
								.allMatch(p -> Boolean.TRUE.equals(p.getPagado()));
						if (todosPagados) {
							Date ultimaFechaPago = productosAlumno.stream().filter(p -> p.getFechaPago() != null)
									.map(com.taemoi.project.entities.ProductoAlumno::getFechaPago).max(Date::compareTo)
									.orElse(null);
							if (ultimaFechaPago != null) {
								LocalDate fecha = Instant.ofEpochMilli(ultimaFechaPago.getTime())
										.atZone(ZoneId.systemDefault()).toLocalDate();
								DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
								fechaPago = fecha.format(dateFormatter);
							}
						}
					}
					html.append("<div class='line-item'>").append(fechaPago).append("</div>");
				}
				html.append("</td>");

				html.append("</tr>");
			}
		}

		html.append("</tbody>");
		html.append("</table>");

		html.append("</body>");
		html.append("</html>");

		// Generate PDF
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de listado mensualidad mensual: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el listado mensualidad mensual PDF", e);
		}
		return outputStream.toByteArray();
	}

	@Override
	public byte[] generarInformeAlumnasDpf(boolean soloActivos) {
		List<Alumno> alumnas = alumnoDeporteRepository
				.findActivosByDeporteWithAlumno(CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA).stream()
				.map(AlumnoDeporte::getAlumno)
				.filter(alumno -> alumno != null && (!soloActivos || Boolean.TRUE.equals(alumno.getActivo())))
				.sorted(Comparator.comparing(alumno ->
						((alumno.getNombre() == null ? "" : alumno.getNombre()) + " "
						+ (alumno.getApellidos() == null ? "" : alumno.getApellidos())), String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
		String titulo = "Informe de Alumnas de D.P.F";
		StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8' /><style>");
		html.append(estilosInforme(titulo)).append("</style></head><body>")
				.append(cabeceraInforme(titulo, contextoActivos(soloActivos)));
		if (alumnas.isEmpty()) {
			html.append("<p class='vacio'>No hay alumnas de D.P.F. para los filtros seleccionados.</p>");
		} else {
			html.append("<table><thead><tr><th>Nombre</th><th>Apellidos</th></tr></thead><tbody>");
			for (Alumno alumna : alumnas) {
				html.append("<tr><td>").append(esc(alumna.getNombre())).append("</td><td>")
						.append(esc(alumna.getApellidos())).append("</td></tr>");
			}
			html.append("</tbody></table>");
		}
		html.append("</body></html>");
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			throw new RuntimeException("Error al generar el informe de alumnas de D.P.F. PDF", e);
		}
		return outputStream.toByteArray();
	}

	@Override
	public byte[] generarInformeCompetidores() {
		// Get all active competitors for Taekwondo and Kickboxing
		List<AlumnoDeporte> competidores = alumnoDeporteRepository
				.findCompetidoresActivosByDeporteIn(catalogoDisciplinas.codigosQuePermitenCompeticion());


		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Listado de Competidores"));
		html.append(".grado-celda { white-space: nowrap; }");
		html.append(
				".grado-label { margin-left: 2mm; font-size: 9pt; font-weight: 600; vertical-align: middle; white-space: nowrap; }");

		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header with logo
		html.append(cabeceraInforme("Listado de Competidores", null));

		// Separate by sport
		List<AlumnoDeporte> competidoresTaekwondo = competidores.stream()
				.filter(ad -> CatalogoLegadoTaemoi.TAEKWONDO.equals(ad.getDeporte())).collect(Collectors.toList());

		List<AlumnoDeporte> competidoresKickboxing = competidores.stream()
				.filter(ad -> CatalogoLegadoTaemoi.KICKBOXING.equals(ad.getDeporte())).collect(Collectors.toList());

		// Taekwondo section
		if (!competidoresTaekwondo.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("Taekwondo (").append(competidoresTaekwondo.size()).append(" competidores)");
			html.append("</div>");
			html.append(generarSeccionCompetidores(competidoresTaekwondo));
		}

		// Kickboxing section
		if (!competidoresKickboxing.isEmpty()) {
			html.append("<div class='section-header'>");
			html.append("Kickboxing (").append(competidoresKickboxing.size()).append(" competidores)");
			html.append("</div>");
			html.append(generarSeccionCompetidores(competidoresKickboxing));
		}

		if (competidores.isEmpty()) {
			html.append("<p class='vacio'>No hay competidores activos</p>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de competidores: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el informe de competidores PDF", e);
		}
		return outputStream.toByteArray();
	}

	/**
	 * Genera el HTML para una sección de competidores, agrupando por categoría. En
	 * la tabla se reemplaza la columna Categoría por Grado (con dibujo de
	 * cinturón).
	 */
	private String generarSeccionCompetidores(List<AlumnoDeporte> competidores) {
		StringBuilder html = new StringBuilder();

		final String SIN_CATEGORIA = "Sin categoría";

		// Agrupar por nombre de categoría
		Map<String, List<AlumnoDeporte>> porCategoria = competidores.stream().collect(Collectors.groupingBy(ad -> {
			if (ad.getCategoria() == null || ad.getCategoria().getNombre() == null) {
				return SIN_CATEGORIA;
			}
			String n = ad.getCategoria().getNombre().trim();
			return n.isEmpty() ? SIN_CATEGORIA : n;
		}));

		// Ordenar categorías, dejando "Sin categoría" al final
		List<String> categoriasOrdenadas = new ArrayList<>(porCategoria.keySet());
		categoriasOrdenadas.sort((a, b) -> {
			if (a.equals(SIN_CATEGORIA) && !b.equals(SIN_CATEGORIA))
				return 1;
			if (!a.equals(SIN_CATEGORIA) && b.equals(SIN_CATEGORIA))
				return -1;
			return a.compareToIgnoreCase(b);
		});

		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

		for (String nombreCategoria : categoriasOrdenadas) {
			List<AlumnoDeporte> grupo = porCategoria.get(nombreCategoria);
			if (grupo == null || grupo.isEmpty())
				continue;

			// Ordenar alumnos por nombre
			grupo.sort(Comparator
					.comparing(ad -> (ad.getAlumno().getNombre() + " " + ad.getAlumno().getApellidos()).toLowerCase()));

			// Rótulo de grupo: la categoría y, a la derecha, el recuento.
			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");

			html.append("<div class='izquierda'>");
			html.append("<span class='categoria-nombre'>").append(nombreCategoria).append("</span>");
			html.append("</div>");

			html.append("<div class='derecha'>");
			html.append("Competidores: ").append(grupo.size());
			html.append("</div>");

			html.append("</div>");

			// Tabla: reemplazamos "Categoría" por "Grado"
			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>Grado</th>");
			html.append("<th class='num'>Peso</th>");
			html.append("<th class='num'>Fecha Peso</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");

			for (AlumnoDeporte ad : grupo) {
				Alumno alumno = ad.getAlumno();

				String peso = ad.getPeso() != null ? String.format("%.1f kg", ad.getPeso()) : "";

				String fechaPeso = "";
				if (ad.getFechaPeso() != null) {
					LocalDate fecha = Instant.ofEpochMilli(ad.getFechaPeso().getTime()).atZone(ZoneId.systemDefault())
							.toLocalDate();
					fechaPeso = fecha.format(dateFormatter);
				}

				// Grado del alumno
				String tipoGrado = tipoDe(ad.getGrado());
				String nombreGrado = (tipoGrado != null) ? progresionGrados.obtenerNombreVisible(ad.getGrado())
						: "Sin grado";

				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append("</td>");

				// Celda GRADO con dibujo del cinturón
				html.append("<td class='grado-celda'>");
				if (tipoGrado != null) {
					html.append(CinturonesPdf.inline(ColoresGrado.de(ad.getGrado()), 55, 12));
				} else {
					// Cinturón gris simple
					html.append(
							"<div style='background-color: #CCCCCC; width: 55px; height: 12px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'></div>");
				}
				html.append("<span class='grado-label'>").append(nombreGrado).append("</span>");
				html.append("</td>");

				html.append("<td class='num'>").append(peso).append("</td>");
				html.append("<td class='num'>").append(fechaPeso).append("</td>");
				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
			html.append("</div>");
		}

		return html.toString();
	}

	@Override
	public byte[] generarInformeConvocatoria(Long convocatoriaId) {
		// Get convocatoria data
		com.taemoi.project.entities.Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
				.orElseThrow(
						() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		// Get report data from service
		List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO> alumnosReporte = convocatoriaService
				.obtenerReporteDeConvocatoria(convocatoriaId);

		// Format convocatoria date
		String fechaConvocatoria = "";
		if (convocatoria.getFechaConvocatoria() != null) {
			LocalDate fechaConv = Instant.ofEpochMilli(convocatoria.getFechaConvocatoria().getTime())
					.atZone(ZoneId.systemDefault()).toLocalDate();
			fechaConvocatoria = fechaConv.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		}

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(estilosInforme("Convocatoria a examen " + fechaConvocatoria));
		// Rótulo de cada paso de grado: cinturón y grado de origen, cinturón y grado de destino, recuento a la derecha.
		html.append(".grade-transition-box { margin: 5mm 0 0.5mm 0; page-break-after: avoid; }");
		html.append(".grade-transition-table { width: 100%; border-collapse: collapse; margin: 0; }");
		html.append(".grade-transition-table td { border: none; padding: 0; vertical-align: middle; font-family: "
				+ EstiloPdf.TITULOS + "; font-weight: 700; font-size: 10.5pt; }");
		html.append(".grade-transition-table td.grade-from { width: 36%; }");
		html.append(".grade-transition-table td.grade-to { width: 40%; }");
		html.append(".grade-transition-table td.student-count { text-align: right; font-family: " + EstiloPdf.TEXTO
				+ "; font-weight: 400; font-size: 8pt; color: " + EstiloPdf.GRIS + "; }");
		html.append(".belt-with-text { vertical-align: middle; }");
		html.append(".belt-text { margin: 0 3mm; vertical-align: middle; white-space: nowrap; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Cabecera común: la convocatoria en el título; disciplina y alumnos como contexto.
		html.append(cabeceraInforme("Convocatoria a examen " + fechaConvocatoria,
				contexto(convocatoria.getDeporte() == null ? null : getDeporteNombre(convocatoria.getDeporte()),
						"(Solo alumnos con derecho a examen)", "Total alumnos: " + alumnosReporte.size())));

		// Group students by grade transition
		Map<String, List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO>> groupedByGrade = alumnosReporte
				.stream().collect(Collectors.groupingBy(a -> a.getGradoActual() + "_TO_" + a.getGradoSiguiente()));

		// Sort groups by grade ordinal (descending - highest first)
		List<Map.Entry<String, List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO>>> sortedGroups = new ArrayList<>(
				groupedByGrade.entrySet());
		sortedGroups.sort((e1, e2) -> {
			String t1 = e1.getValue().get(0).getGradoActual();
			String t2 = e2.getValue().get(0).getGradoActual();
			int g1 = ordenPresentacion(t1 == null ? null : t1, null);
			int g2 = ordenPresentacion(t2 == null ? null : t2, null);
			return Integer.compare(g2, g1);
		});

		for (Map.Entry<String, List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO>> entry : sortedGroups) {
			List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO> alumnos = entry.getValue();
			if (alumnos.isEmpty())
				continue;

			String gradoActual = alumnos.get(0).getGradoActual();
			String gradoSiguiente = alumnos.get(0).getGradoSiguiente();

			// Sort students by name
			alumnos.sort(Comparator.comparing(a -> a.getNombreCompleto().toLowerCase()));

			// Grade transition header using table for layout
			html.append("<div class='grade-transition-box'>");
			html.append("<table class='grade-transition-table'>");
			html.append("<tr>");

			// DE [GRADO]
			html.append("<td class='grade-from'>");
			html.append("<span class='belt-with-text'>");
			html.append(CinturonesPdf.inline(cinturonDe(convocatoria.getDeporte(), gradoActual), 50, 15));
			html.append("<span class='belt-text'>DE ")
					.append(progresionGrados.obtenerNombreVisible(convocatoria.getDeporte(),
							gradoActual == null ? null : gradoActual).toUpperCase())
					.append("</span>");
			html.append("</span>");
			html.append("</td>");

			// A [GRADO]
			html.append("<td class='grade-to'>");
			html.append("<span class='belt-with-text'>");
			html.append(CinturonesPdf.inline(cinturonDe(convocatoria.getDeporte(), gradoSiguiente), 50, 15));
			html.append("<span class='belt-text'>A ").append(
					progresionGrados.obtenerNombreVisible(convocatoria.getDeporte(),
							gradoSiguiente == null ? null : gradoSiguiente).toUpperCase())
					.append("</span>");
			html.append("</span>");
			html.append("</td>");

			// Recuento, en gris a la derecha
			html.append("<td class='student-count'>");
			html.append(alumnos.size()).append(" Alumnos");
			html.append("</td>");

			html.append("</tr>");
			html.append("</table>");
			html.append("</div>");

			// Table for this grade group
			html.append("<table>");
			html.append("<thead>");
			html.append("<tr>");
			html.append("<th>NOMBRE</th>");
			html.append("<th class='num'>EXP</th>");
			html.append("<th class='num'>LIC. FEDERATIVA</th>");
			html.append("<th class='num'>EDAD</th>");
			html.append("<th>CATEGORÍA</th>");
			html.append("<th class='num'>PESO</th>");
			html.append("<th>PAGADO</th>");
			html.append("</tr>");
			html.append("</thead>");
			html.append("<tbody>");

			for (com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO alumno : alumnos) {
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombreCompleto().toUpperCase()).append("</td>");
				html.append("<td class='num'>")
						.append(alumno.getNumeroExpediente() != null ? alumno.getNumeroExpediente() : "").append("</td>");
				html.append("<td class='num'>")
						.append(alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia() : "").append("</td>");
				html.append("<td class='num'>").append(alumno.getEdad()).append("</td>");
				html.append("<td>").append(alumno.getCategoria() != null ? alumno.getCategoria() : "").append("</td>");
				html.append("<td class='num'>").append(alumno.getPeso() != null ? alumno.getPeso() : "").append("</td>");
				// Decisión 315: el pago pendiente, en ocre; el pagado, neutro.
				if (alumno.getPagado() != null && alumno.getPagado()) {
					html.append("<td><span class='estado'>SI</span></td>");
				} else {
					html.append("<td><span class='pendiente-dinero'>NO</span></td>");
				}
				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html.toString(), null);
		builder.toStream(outputStream);
		try {
			builder.run();
		} catch (Exception e) {
			System.err.println("Error generando PDF de convocatoria: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Error al generar el informe PDF de convocatoria", e);
		}
		return outputStream.toByteArray();
	}

	/**
	 * Cinturón de un código de grado de la convocatoria: colores del grado en el sistema de la disciplina (o,
	 * si no se encuentra, derivados del código, como antes); nulo sin código.
	 */
	private ColoresGrado.Cinturon cinturonDe(String disciplina, String codigoGrado) {
		if (codigoGrado == null) {
			return null;
		}
		return progresionGrados.buscarGrado(disciplina, codigoGrado).map(ColoresGrado::de)
				.orElseGet(() -> ColoresGrado.desdeCodigo(codigoGrado));
	}

	/**
	 * Escapes CSV special characters (quotes, commas, newlines)
	 */
	private String escapeCSV(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\"", "\"\"");
	}

	/**
	 * Gets the display name for a sport
	 */
	private String getDeporteNombre(String deporte) {
		if (deporte == null) {
			return "N/A";
		}
		switch (deporte) {
		case CatalogoLegadoTaemoi.TAEKWONDO:
			return "Taekwondo";
		case CatalogoLegadoTaemoi.KICKBOXING:
			return "Kickboxing";
		case CatalogoLegadoTaemoi.PILATES:
			return "Pilates";
		case CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA:
			return "D.P. Fem.";
		default:
			return deporte;
		}
	}

	private String inferDeporteFromConcepto(String concepto) {
		if (concepto == null || concepto.isBlank()) {
			return null;
		}
		String upper = concepto.toUpperCase(Locale.ROOT);
		if (upper.contains("KICKBOXING")) {
			return CatalogoLegadoTaemoi.KICKBOXING;
		}
		if (upper.contains("TAEKWONDO")) {
			return CatalogoLegadoTaemoi.TAEKWONDO;
		}
		if (upper.contains("PILATES")) {
			return CatalogoLegadoTaemoi.PILATES;
		}
		if (upper.contains("DEFENSA")) {
			return CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA;
		}
		return null;
	}

	/**
	 * Helper class to hold promotion information for an alumno in a specific sport
	 */
	private static class AlumnoPromotionInfo {
		final Alumno alumno;
		final AlumnoDeporte alumnoDeporte;

		AlumnoPromotionInfo(Alumno alumno, AlumnoDeporte alumnoDeporte, String promotionGrade) {
			this.alumno = alumno;
			this.alumnoDeporte = alumnoDeporte;
		}
	}
}
