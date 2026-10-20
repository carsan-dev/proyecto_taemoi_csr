package com.taemoi.project.services.impl;


import com.taemoi.project.services.CatalogoLegadoTaemoi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
import com.taemoi.project.services.PDFService;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.utils.ColoresGrado;
import com.taemoi.project.utils.DiaSemanaUtils;
import com.taemoi.project.utils.FechaUtils;

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

	// Cache for the PNG logo to avoid repeated conversion
	private String logoPngBase64Cache = null;
	private String logoPreinscripcionBase64Cache = null;

	@Override public byte[] generarPreviewPreinscripcion(String deporte,String contenido,String instrucciones){
		com.taemoi.project.entities.Disciplina disciplina=catalogoDisciplinas.obtenerPorCodigo(deporte);PlantillaPreinscripcion plantilla=new PlantillaPreinscripcion();plantilla.setDisciplina(disciplina);plantilla.setVersion(0);plantilla.setContenido(contenido);plantilla.setInstrucciones(instrucciones);
		Preinscripcion p=new Preinscripcion();p.setDisciplina(disciplina);p.setPlantilla(plantilla);p.setTemporada("2026/2027");p.setReferencia("PREVIEW");p.setNombre("María");p.setApellidos("García López");p.setDni("12345678Z");p.setFechaNacimiento(LocalDate.of(2000,1,15));p.setTelefono("600 000 000");p.setTelefono2("611 111 111");p.setEmail("maria@example.org");p.setTieneDiscapacidad(false);p.setDireccion("Calle Ejemplo, 1, Umbrete");p.setGrupoSnapshot("Grupo de ejemplo · lunes y miércoles 18:00–19:00");p.setConsentimientoFotografico(true);p.setFirmanteNombre("María García López");p.setFirma(firmaPreview());return generarPreinscripcionFirmada(p);
	}
	private byte[] firmaPreview(){try{java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(360,90,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,360,90);g.setColor(java.awt.Color.DARK_GRAY);g.drawString("Firma de previsualización",80,48);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}

	@Override
	public byte[] generarPreinscripcionFirmada(Preinscripcion p) {
		String firma = "data:image/png;base64," + Base64.getEncoder().encodeToString(p.getFirma());
		String nombreDeporte = p.getDeporte().replace('_', ' ');
		boolean conNormas = true;
		JsonNode contenidoPlantilla = parsearContenidoPlantilla(p.getPlantilla().getContenido());
		String cabecera=campoPlantilla(contenidoPlantilla,"cabecera","Escuela Moi Kim Do");
		String contacto=campoPlantilla(contenidoPlantilla,"contacto","");
		String titulo=campoPlantilla(contenidoPlantilla,"titulo","Solicitud de preinscripción");
		String consentimiento=campoPlantilla(contenidoPlantilla,"consentimiento","");
		String importes=campoPlantilla(contenidoPlantilla,"importes","");
		String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>"
				+ "@page{size:A4;margin:10mm 12mm 11mm}body{font-family:Arial,sans-serif;color:#172126;font-size:9.2pt;line-height:1.24}p{margin:5px 0}ol{margin:5px 0;padding-left:20px}li{margin:0 0 1.5px}"
				+ ".head{border-bottom:2px solid #b7272d;padding-bottom:6px;margin-bottom:9px}.head-table{width:100%;border-collapse:collapse}.head-table td{padding:0;vertical-align:middle}.logo-cell{width:24mm}.head img{display:block;width:19mm}.title-cell{padding:0 4mm!important}h1{font-size:15pt;margin:0 0 2px;text-transform:uppercase;line-height:1.08}h2{font-size:10pt;color:#b7272d;margin:0}.ref-cell{width:34mm;text-align:right;font-weight:bold;white-space:nowrap}.section{margin:9px 0}.keep{page-break-inside:avoid}.section h3{font-size:10pt;margin:7px 0 4px;border-bottom:1px solid #aeb6b9;padding-bottom:3px}.grid{width:100%;border-collapse:collapse}.grid td{border:1px solid #ccd2d4;padding:4px;text-align:left}.label{font-size:7pt;color:#59666b;text-transform:uppercase}.notice{background:#f4f1e9;border-left:3px solid #b7272d;padding:6px}.signature{height:20mm;max-width:66mm;object-fit:contain;border-bottom:1px solid #555}.foot{font-size:7pt;color:#59666b;margin-top:6px}"
				+ "</style></head><body><div class='head'><table class='head-table'><tr><td class='logo-cell'>" + (!getLogoPreinscripcionBase64().isEmpty()?"<img src='"+getLogoPreinscripcionBase64()+"'/>":"")
				+ "</td><td class='title-cell'><h1>"+esc(titulo)+"</h1><h2>"+esc(cabecera)+" · "+esc(contacto)+" · Temporada "+esc(p.getTemporada())+"</h2></td><td class='ref-cell'>"+esc(p.getReferencia())+"</td></tr></table></div>"
				+ "<div class='notice'><strong>Preinscripción recibida.</strong> "+(p.getEstado()==com.taemoi.project.entities.EstadoPreinscripcion.EN_LISTA_ESPERA?"Solicitud incorporada a la lista de espera. Se notificará la asignación automática cuando exista aforo.":"Plaza asignada; esta solicitud ya cuenta en el aforo del grupo.")+"</div>"
				+ "<div class='section'><h3>Datos de la persona solicitante</h3><table class='grid'><tr><td><span class='label'>Nombre y apellidos</span><br/>"+esc(p.getNombre()+" "+p.getApellidos())+"</td><td><span class='label'>DNI/NIE</span><br/>"+esc(p.getDni()==null||p.getDni().isBlank()?"No facilitado":p.getDni())+"</td></tr>"
				+ "<tr><td><span class='label'>Fecha de nacimiento</span><br/>"+p.getFechaNacimiento()+"</td><td><span class='label'>Correo electrónico</span><br/>"+esc(p.getEmail())+"</td></tr><tr><td><span class='label'>Teléfono principal</span><br/>"+esc(p.getTelefono())+"</td><td><span class='label'>Teléfono secundario</span><br/>"+esc(p.getTelefono2()==null||p.getTelefono2().isBlank()?"No facilitado":p.getTelefono2())+"</td></tr><tr><td><span class='label'>Discapacidad</span><br/>"+(p.getTieneDiscapacidad()==null?"No indicado":Boolean.TRUE.equals(p.getTieneDiscapacidad())?"Sí":"No")+"</td><td><span class='label'>Dirección</span><br/>"+esc(p.getDireccion())+"</td></tr></table></div>"
				+ (p.getTutorNombre()!=null&&!p.getTutorNombre().isBlank()?"<div class='section'><h3>Responsable legal</h3>"+esc(p.getTutorNombre())+" · "+esc(p.getTutorDni())+"</div>":"")
				+ "<div class='section'><h3>Actividad solicitada</h3>"+esc(p.getGrupoSnapshot()!=null?p.getGrupoSnapshot():nombreDeportePublico(p.getDeporte()))+"</div>"
				+ (conNormas ? "<div class='section keep'><h3>Consentimiento fotográfico (opcional)</h3><p>"+(Boolean.TRUE.equals(p.getConsentimientoFotografico())?"[X] Autorizado. ":"[ ] No autorizado. ")+esc(consentimiento)+"</p></div><div class='section'><h3>Normas de la escuela</h3>"+normasHtml(contenidoPlantilla)+"<p><strong>[X] Me comprometo a cumplir las normas establecidas por el club.</strong></p></div>" : "")
				+ "<div class='section keep'><h3>Importes y pago</h3><p>"+esc(importes)+"</p></div>"
				+ "<div class='section keep'><h3>Firma</h3><img class='signature' src='"+firma+"'/><p>Firmado por "+esc(p.getFirmanteNombre())+" el "+LocalDate.now(ZoneId.of("Europe/Madrid"))+".</p></div>"
				+ "<div class='foot'>Documento generado electrónicamente. Referencia "+esc(p.getReferencia())+". Versión documental preservada con la solicitud.</div></body></html>";
		try { ByteArrayOutputStream out=new ByteArrayOutputStream(); PdfRendererBuilder builder=new PdfRendererBuilder(); builder.withHtmlContent(html,null); builder.toStream(out); builder.run(); return out.toByteArray(); }
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

	private String getLogoPreinscripcionBase64() {
		if (logoPreinscripcionBase64Cache != null) return logoPreinscripcionBase64Cache;
		try (InputStream input = new ClassPathResource("static/logo_tkd_kick.jpeg").getInputStream()) {
			logoPreinscripcionBase64Cache = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(input.readAllBytes());
			return logoPreinscripcionBase64Cache;
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Converts the school logo SVG to PNG and returns it as a base64 data URI. This
	 * provides full compatibility with OpenHTML to PDF. The result is cached to
	 * avoid repeated conversions.
	 *
	 * @return Base64-encoded PNG logo as a data URI
	 */
	private String getLogoPngBase64() {
		if (logoPngBase64Cache != null) {
			return logoPngBase64Cache;
		}

		try {
			ClassPathResource resource = new ClassPathResource("static/logo_escuela.svg");
			InputStream svgInputStream = resource.getInputStream();

			// Convert SVG to PNG using Apache Batik
			PNGTranscoder transcoder = new PNGTranscoder();
			transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 300f);
			transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 300f);

			TranscoderInput input = new TranscoderInput(svgInputStream);
			ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
			TranscoderOutput output = new TranscoderOutput(pngOutputStream);

			transcoder.transcode(input, output);
			svgInputStream.close();
			pngOutputStream.flush();

			// Convert PNG bytes to base64
			byte[] pngBytes = pngOutputStream.toByteArray();
			String base64 = Base64.getEncoder().encodeToString(pngBytes);
			logoPngBase64Cache = "data:image/png;base64," + base64;

			pngOutputStream.close();
			return logoPngBase64Cache;
		} catch (Exception e) {
			System.err.println("Error converting logo SVG to PNG: " + e.getMessage());
			e.printStackTrace();
			// Return empty string if conversion fails
			return "";
		}
	}

	/**
	 * Generates a standard header section with logo for PDF reports. The logo is
	 * converted to PNG and embedded as base64 for full compatibility.
	 *
	 * @param titulo Main title for the report
	 * @return HTML string with header section
	 */
	private String generarCabeceraConLogo(String titulo) {
		return generarCabeceraConLogo(titulo, "#007bff"); // Default blue
	}

	/**
	 * Generates a standard header section with logo and custom border color.
	 *
	 * @param titulo      Main title for the report
	 * @param borderColor Border color for the header
	 * @return HTML string with header section
	 */
	private String generarCabeceraConLogo(String titulo, String borderColor) {
		String logoPng = getLogoPngBase64();
		StringBuilder header = new StringBuilder();

		header.append("<div class='pdf-header' style='border-bottom-color: ").append(borderColor).append(";'>");

		if (!logoPng.isEmpty()) {
			header.append("<div class='logo-container'>");
			header.append("<img src='").append(logoPng).append("' alt='Logo' />");
			header.append("</div>");
		}

		header.append("<div class='header-content'>");
		header.append("<h1 class='club-name'>Moi's Kim Do Taekwondo</h1>");
		if (titulo != null && !titulo.isEmpty()) {
			header.append("<h2 class='report-title'>").append(titulo).append("</h2>");
		}
		header.append("</div>");
		header.append("</div>");

		return header.toString();
	}

	/**
	 * Generates modern CSS styles matching the website design. Uses Montserrat
	 * font, website colors, and modern design principles.
	 *
	 * @param pageTitle       Title to display in the header
	 * @param fechaGeneracion Date string to display in footer
	 * @return CSS style string
	 */
	private String generarEstilosModernos(String pageTitle, String fechaGeneracion) {
		return "@page {" + "  margin: 18mm 15mm 20mm 15mm;" + "  @top-center {" + "    content: '" + pageTitle + "';"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 13pt;"
				+ "    font-weight: 700;" + "    color: #1b2b2e;" + "    text-align: center;" + "  }"
				+ "  @bottom-left {" + "    content: '" + fechaGeneracion + "';"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 9pt;"
				+ "    color: #6c757d;" + "  }" + "  @bottom-right {"
				+ "    content: 'Página ' counter(page) ' de ' counter(pages);"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 9pt;"
				+ "    color: #6c757d;" + "  }" + "}" + "* { box-sizing: border-box; }" + "body {"
				+ "  font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "  color: #212529;"
				+ "  line-height: 1.6;" + "  background: #ffffff;" + "}" + ".pdf-header {" + "  text-align: center;"
				+ "  margin-bottom: 5mm;" + "  padding-bottom: 3mm;" + "  border-bottom: 2px solid #007bff;"
				+ "  page-break-inside: avoid;" + "}" + ".logo-container {" + "  text-align: center;"
				+ "  margin-bottom: 2mm;" + "}" + ".logo-container img {" + "  width: 26mm;" + "  height: auto;"
				+ "  max-height: 26mm;" + "}" + ".header-content {" + "  text-align: center;" + "}" + ".club-name {"
				+ "  font-size: 15pt;" + "  font-weight: 700;" + "  color: #1b2b2e;" + "  margin: 1mm 0;"
				+ "  text-transform: uppercase;" + "  letter-spacing: 1px;" + "}" + ".report-title {"
				+ "  font-size: 12pt;" + "  font-weight: 600;" + "  color: #007bff;" + "  margin: 1mm 0 0 0;" + "}"
				+ "h1, h2, h3 {" + "  font-weight: 700;" + "  color: #1b2b2e;" + "  margin: 8mm 0 5mm 0;" + "}"
				+ "h1 { font-size: 22pt; }" + "h2 {" + "  font-size: 16pt;" + "  padding-bottom: 2mm;"
				+ "  border-bottom: 2px solid #007bff;" + "  display: inline-block;" + "  min-width: 40%;" + "}"
				+ ".grupo {" + "  margin-top: 6mm;" + "  margin-bottom: 6mm;" + "  page-break-inside: avoid;" + "}"
				+ ".encabezado-grupo {" + "  background-color: #f8f9fa;" + "  padding: 4mm 3mm;"
				+ "  border-radius: 3mm;" + "  border: 1px solid #dee2e6;" + "  margin-bottom: 3mm;" + "}"
				+ ".encabezado-grupo .izquierda {" + "  display: inline-block;" + "  width: 70%;"
				+ "  vertical-align: middle;" + "}" + ".encabezado-grupo .derecha {" + "  display: inline-block;"
				+ "  width: 28%;" + "  text-align: right;" + "  font-weight: 600;" + "  color: #007bff;"
				+ "  font-size: 11pt;" + "  vertical-align: middle;" + "}" + ".cinturon {" + "  display: inline-block;"
				+ "  vertical-align: middle;" + "  width: 28mm;" + "  height: 6mm;" + "  margin-right: 3mm;"
				+ "  border: 1.5px solid #495057;" + "  border-radius: 1mm;" + "  position: relative;" + "}"
				+ ".cinturon.doble .superior, .cinturon.doble .inferior {" + "  width: 100%;" + "  height: 50%;"
				+ "  position: absolute;" + "}" + ".cinturon.doble .superior {" + "  top: 0;"
				+ "  border-top-left-radius: 0.5mm;" + "  border-top-right-radius: 0.5mm;" + "}"
				+ ".cinturon.doble .inferior {" + "  bottom: 0;" + "  border-bottom-left-radius: 0.5mm;"
				+ "  border-bottom-right-radius: 0.5mm;" + "}" + ".grado-nombre {" + "  display: inline-block;"
				+ "  vertical-align: middle;" + "  font-size: 12pt;" + "  font-weight: 600;" + "  color: #1b2b2e;"
				+ "  white-space: nowrap;" + "}" + ".raya {" + "  position: absolute;" + "  top: 50%;"
				+ "  transform: translateY(-50%);" + "  height: 80%;" + "  background-color: #FFD700;"
				+ "  z-index: 10;" + "}" + "table {" + "  width: 100%;" + "  border-collapse: collapse;"
				+ "  margin-top: 2mm;" + "  border-radius: 2mm;" + "  overflow: hidden;" + "}" + "th, td {"
				+ "  border: 1px solid #dee2e6;" + "  padding: 3mm 2mm;" + "  text-align: center;"
				+ "  font-size: 10pt;" + "}" + "th {" + "  background-color: #007bff;" + "  color: #ffffff;"
				+ "  font-weight: 600;" + "  font-size: 10pt;" + "  text-transform: uppercase;"
				+ "  letter-spacing: 0.5px;" + "}" + "tbody tr:nth-child(even) {" + "  background-color: #f8f9fa;" + "}"
				+ "tbody tr:nth-child(odd) {" + "  background-color: #ffffff;" + "}" + "tbody tr:hover {"
				+ "  background-color: #e7f3ff;" + "}" + "tbody tr { page-break-inside: avoid; }"
				+ "thead { display: table-header-group; }" + "table { page-break-inside: auto; }" + ".kickboxing {"
				+ "  padding-top: 10mm;" + "  border-top: 3px solid #ff4500;" + "  margin-top: 10mm;" + "}"
				+ ".section-header {" + "  background-color: #1b2b2e;" + "  color: #ffffff;" + "  padding: 5mm;"
				+ "  border-radius: 2mm;" + "  margin-bottom: 5mm;" + "  font-size: 14pt;" + "  font-weight: 700;"
				+ "  text-align: center;" + "}";
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

		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = now.format(formatter);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Listado de Alumnos por Grado", fechaGeneracion));
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		if (deportes.size() == 2 && deportes.contains(CatalogoLegadoTaemoi.TAEKWONDO) && deportes.contains(CatalogoLegadoTaemoi.KICKBOXING)) {
			html.append(generarCabeceraConLogo("Listado de Alumnos por Grado"));

			List<AlumnoDeporte> alumnosTaekwondo = alumnos.stream().filter(a -> CatalogoLegadoTaemoi.TAEKWONDO.equals(a.getDeporte()))
					.collect(Collectors.toList());
			html.append("<div class='section-header' style='background-color: #0D47A1;'>");
			html.append("Taekwondo");
			html.append("</div>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<AlumnoDeporte> alumnosKickboxing = alumnos.stream().filter(a -> CatalogoLegadoTaemoi.KICKBOXING.equals(a.getDeporte()))
					.collect(Collectors.toList());
			html.append("<div class='section-header kickboxing' style='background-color: #ff4500; margin-top: 10mm;'>");
			html.append("Kickboxing");
			html.append("</div>");
			html.append(generarSeccion(alumnosKickboxing));
		} else {
			// Use sport-specific colors for the header border
			if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deportes.get(0))) {
				html.append(generarCabeceraConLogo("Taekwondo por Grado", "#0D47A1"));
			} else {
				html.append(generarCabeceraConLogo("Kickboxing por Grado", "#ff4500"));
			}
			html.append(generarSeccion(alumnos));
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>N&#186; Expediente</th>");
			html.append("<th>Licencia Fed.</th>");
			html.append("<th>Fecha del Grado</th>");
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
				html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
				String licencia = alumnoDeporte.getNumeroLicencia() != null
						? alumnoDeporte.getNumeroLicencia().toString()
						: "N/A";
				html.append("<td>").append(licencia).append("</td>");
				// Format date as dd/MM/yyyy
				String fechaGrado = "N/A";
				if (alumnoDeporte.getFechaGrado() != null) {
					LocalDate fecha = Instant.ofEpochMilli(alumnoDeporte.getFechaGrado().getTime())
							.atZone(ZoneId.systemDefault()).toLocalDate();
					fechaGrado = fecha.format(dateFormatter);
				}
				html.append("<td>").append(fechaGrado).append("</td>");
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
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Informe de Licencias de Alumnos", fechaGeneracion));
		html.append(".status-badge {");
		html.append("  display: inline-block;");
		html.append("  padding: 1mm 3mm;");
		html.append("  border-radius: 2mm;");
		html.append("  font-weight: 600;");
		html.append("  font-size: 9pt;");
		html.append("}");
		html.append(".status-vigente { background: #d4edda; color: #155724; }");
		html.append(".status-caducada { background: #fff3cd; color: #856404; }");
		html.append(".status-sin { background: #f8d7da; color: #721c24; }");
		// Estilos para el cinturón en la columna de grado
		html.append(".grado-cell { text-align: center; vertical-align: middle; }");
		html.append(".grado-belt { margin: 0 auto 1.5mm auto; }");
		html.append(".grado-text { font-size: 8pt; font-weight: 600; line-height: 1.2; }");
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
		html.append(generarCabeceraConLogo("Informe de Licencias"));

		html.append("<div class='section-header' style='background-color: #28a745;'>");
		html.append("Licencias en Vigor (" + licenciasVigor.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasVigor));

		html.append("<div class='section-header' style='background-color: #ffc107; margin-top: 5mm;'>");
		html.append("Licencias Caducadas (" + licenciasCaducadas.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasCaducadas));

		html.append("<div class='section-header' style='background-color: #dc3545; margin-top: 5mm;'>");
		html.append("Sin Licencia (" + sinLicencia.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(sinLicencia));

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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
		html.append("<th>EXP.</th>");
		html.append("<th>N&#186; Lic.</th>");
		html.append("<th>Fecha Lic.</th>");
		html.append("<th>Grado</th>");
		html.append("</tr></thead>");
		html.append("<tbody>");
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		for (AlumnoDeporte alumnoDeporte : alumnos) {
			Alumno alumno = alumnoDeporte.getAlumno();
			html.append("<tr>");
			html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");
			html.append("<td>").append(getDeporteNombre(alumnoDeporte.getDeporte())).append("</td>");
			html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
			String numLicencia = (alumnoDeporte.getNumeroLicencia() != null)
					? alumnoDeporte.getNumeroLicencia().toString()
					: "N/A";
			html.append("<td>").append(numLicencia).append("</td>");
			// Format date as dd/MM/yyyy
			String fechaLic = "N/A";
			if (alumnoDeporte.getFechaLicencia() != null) {
				LocalDate fechaLicencia = Instant.ofEpochMilli(alumnoDeporte.getFechaLicencia().getTime())
						.atZone(ZoneId.systemDefault()).toLocalDate();
				fechaLic = fechaLicencia.format(dateFormatter);
			}
			html.append("<td>").append(fechaLic).append("</td>");
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Alumnos Infantiles a Promocionar", fechaGeneracion));
		html.append(".promotion-group { margin-bottom: 8mm; }");
		html.append(".sport-section { margin-bottom: 12mm; page-break-inside: avoid; }");
		html.append(
				".sport-title { color: #2c3e50; font-size: 18pt; margin-top: 8mm; margin-bottom: 4mm; border-bottom: 2px solid #3498db; padding-bottom: 2mm; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append(".grado-cell { text-align: center; }");
		html.append(".grado-belt { margin: 0 auto 1.5mm auto; }");
		html.append(".grado-text { font-size: 9pt; font-weight: 600; line-height: 1.2; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo("Infantiles a Promocionar"));

		// If no students found
		if (deporteGradoMap.isEmpty()) {
			html.append("<p style='text-align: center; color: #7f8c8d; margin-top: 20mm;'>");
			html.append("No se encontraron alumnos infantiles aptos para promocionar.");
			html.append("</p>");
		} else {

			// Iterate through sports
			for (Map.Entry<String, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
				String deporte = deporteEntry.getKey();

				html.append("<div class='sport-section'>");
				html.append("<h1 class='sport-title'>").append(getDeporteNombre(deporte)).append("</h1>");

				// Iterate through promotion grades for this sport
				for (Map.Entry<String, List<AlumnoPromotionInfo>> gradeEntry : deporteEntry.getValue().entrySet()) {
					String promotionGrade = gradeEntry.getKey();
					List<AlumnoPromotionInfo> alumnos = gradeEntry.getValue();

					html.append("<div class='promotion-group'>");
					html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");

					html.append("<table class='promotion-table'>");
					html.append("<thead><tr>");
					html.append("<th>Nombre y Apellidos</th>");
					html.append("<th>N&#186; Expediente</th>");
					html.append("<th>Grado Actual</th>");
					html.append("<th>Grado a Promocionar</th>");
					html.append("<th>Edad</th>");
					html.append("</tr></thead>");
					html.append("<tbody>");

					for (AlumnoPromotionInfo info : alumnos) {
						Alumno alumno = info.alumno;
						AlumnoDeporte alumnoDeporte = info.alumnoDeporte;

						html.append("<tr>");

						html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
								.append("</td>");

						html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");

						html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

						Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
						html.append(generarCeldaGrado(gradoPromocion,
								progresionGrados.obtenerNombreVisible(gradoPromocion)));

						int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
						html.append("<td>").append(edad).append("</td>");

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
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Alumnos Adultos a Promocionar", fechaGeneracion));
		html.append(".promotion-group { margin-bottom: 8mm; }");
		html.append(".sport-section { margin-bottom: 12mm; page-break-inside: avoid; }");
		html.append(
				".sport-title { color: #2c3e50; font-size: 18pt; margin-top: 8mm; margin-bottom: 4mm; border-bottom: 2px solid #3498db; padding-bottom: 2mm; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append(".grado-cell { text-align: center; }");
		html.append(".grado-belt { margin: 0 auto 1.5mm auto; }");
		html.append(".grado-text { font-size: 9pt; font-weight: 600; line-height: 1.2; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo("Adultos a Promocionar"));

		// If no students found
		if (deporteGradoMap.isEmpty()) {
			html.append("<p style='text-align: center; color: #7f8c8d; margin-top: 20mm;'>");
			html.append("No se encontraron alumnos adultos aptos para promocionar.");
			html.append("</p>");
		} else {
			// Iterate through sports
			for (Map.Entry<String, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
				String deporte = deporteEntry.getKey();
				html.append("<div class='sport-section'>");
				html.append("<h1 class='sport-title'>").append(getDeporteNombre(deporte)).append("</h1>");

				// Iterate through promotion grades for this sport
				for (Map.Entry<String, List<AlumnoPromotionInfo>> gradeEntry : deporteEntry.getValue().entrySet()) {
					String promotionGrade = gradeEntry.getKey();
					List<AlumnoPromotionInfo> alumnos = gradeEntry.getValue();

					html.append("<div class='promotion-group'>");
					html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
					html.append("<table class='promotion-table'>");
					html.append("<thead><tr>");
					html.append("<th>Nombre y Apellidos</th>");
					html.append("<th>N&#186; Expediente</th>");
					html.append("<th>Grado Actual</th>");
					html.append("<th>Grado a Promocionar</th>");
					html.append("<th>Edad</th>");
					html.append("</tr></thead>");
					html.append("<tbody>");

					for (AlumnoPromotionInfo info : alumnos) {
						Alumno alumno = info.alumno;
						AlumnoDeporte alumnoDeporte = info.alumnoDeporte;
						html.append("<tr>");
						html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
								.append("</td>");
						html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");

						html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

						// Grado a Promocionar with belt
						Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
						html.append(generarCeldaGrado(gradoPromocion,
								progresionGrados.obtenerNombreVisible(gradoPromocion)));

						int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
						html.append("<td>").append(edad).append("</td>");
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
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Alumnos " + categoriaTexto + " a Promocionar - " + deporteTexto,
				fechaGeneracion));
		html.append(".promotion-group { margin-bottom: 8mm; }");
		html.append(".promotion-table { table-layout: fixed; }");
		html.append(".promotion-table th, .promotion-table td { word-wrap: break-word; }");
		html.append(".grado-cell { text-align: center; }");
		html.append(".grado-belt { margin: 0 auto 1.5mm auto; }");
		html.append(".grado-text { font-size: 9pt; font-weight: 600; line-height: 1.2; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo(titulo));

		// If no students found
		if (gradoMap.isEmpty()) {
			html.append("<p style='text-align: center; color: #7f8c8d; margin-top: 20mm;'>");
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
				html.append("<th>N&#186; Expediente</th>");
				html.append("<th>Grado Actual</th>");
				html.append("<th>Grado a Promocionar</th>");
				html.append("<th>Edad</th>");
				html.append("</tr></thead>");
				html.append("<tbody>");

				for (AlumnoPromotionInfo info : alumnos) {
					Alumno alumno = info.alumno;
					AlumnoDeporte alumnoDeporte = info.alumnoDeporte;
					html.append("<tr>");
					html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
							.append("</td>");
					html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");

					html.append(generarCeldaGrado(alumnoDeporte.getGrado(), nombreGrado(alumnoDeporte)));

					// Grado a Promocionar with belt
					Grado gradoPromocion = siguienteGrado(alumno, alumnoDeporte);
					html.append(generarCeldaGrado(gradoPromocion,
							progresionGrados.obtenerNombreVisible(gradoPromocion)));

					int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
					html.append("<td>").append(edad).append("</td>");
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
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		String logoPng = getLogoPngBase64();
		StringBuilder html = new StringBuilder();

		// HTML Header with styles
		abrirHtmlAsistencia(html);

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

					paginaAsistencia(html, logoPng, year, month, deporteActual, grupo, twa.turno, twa.alumnos, fechas);
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
		String logoPng = getLogoPngBase64();
		StringBuilder html = new StringBuilder();
		abrirHtmlAsistencia(html);
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
			paginaAsistencia(html, logoPng, year, month, deporte, turno.getDiaSemana(), turno, alumnos,
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

	/** Cabecera HTML y estilos del listado de asistencia (el de gestión y el del profesor, E7-2). */
	private static void abrirHtmlAsistencia(StringBuilder html) {
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>");
		html.append("<style>");
		html.append("@page { margin: 8.5mm; }");
		html.append("* { box-sizing: border-box; font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif; }");
		html.append("body { margin: 0; padding: 0; }");
		html.append(".page-break { page-break-after: always; }");
		html.append(
				".header-section { text-align: center; margin-bottom: 3.2mm; border-bottom: 1.5px solid #007bff; padding-bottom: 2.2mm; }");
		html.append(".logo-container { text-align: center; margin-bottom: 1.6mm; }");
		html.append(".logo-container img { width: 22mm; height: auto; max-height: 22mm; }");
		html.append(".header-titles .main { font-size: 14.5pt; color: #1b2b2e; font-weight: 700; margin: 1.1mm 0; }");
		html.append(".header-titles .sub { font-size: 10.5pt; color: #007bff; margin: 0 0 1.1mm; font-weight: 600; }");
		html.append("h2 { font-size: 9.5pt; margin: 1.1mm 0; font-weight: 700; color: #1b2b2e; text-align: center; }");
		html.append(
				"p.info { text-align: center; font-size: 7.2pt; margin: 0.7mm 0; font-weight: 600; color: #495057; }");
		html.append(
				".deporte-label { text-align: center; font-size: 9.2pt; margin: 1.1mm 0; font-weight: 700; color: #007bff; text-transform: uppercase; }");
		html.append(".table-container { width: 100%; overflow: visible; }");
		html.append(".table-wrapper { display: table; width: auto; table-layout: fixed; margin-left: auto; margin-right: auto; padding-left: 4mm; padding-right: 4mm; }");
		html.append(".table-cell { display: table-cell; vertical-align: top; overflow: visible; }");
		html.append(".main-table { border-collapse: collapse; width: 100%; table-layout: fixed; }");
		html.append(
				".main-table th, .main-table td, .side-table th, .side-table td { border: 1px solid #dee2e6; text-align: center; font-size: 8pt; vertical-align: middle; box-sizing: border-box; }");
		html.append(".main-table th, .side-table th { padding: 1.2mm 0.6mm; font-size: 7.5pt; }");
		html.append(
				".main-table tbody td, .side-table tbody td { padding: 0; height: 6.5mm; max-height: 6.5mm; min-height: 6.5mm; line-height: 6.5mm; overflow: hidden; }");
		html.append(
				".main-table thead th, .side-table thead th { background-color: #007bff; color: #ffffff; font-weight: 600; height: 8mm; min-height: 8mm; max-height: 8mm; overflow: hidden; }");
		html.append(".main-table thead th { text-transform: uppercase; letter-spacing: 0.2px; }");
		html.append(".main-table tbody tr:nth-child(even) { background: #f8f9fa; }");
		html.append(".main-table tbody tr:nth-child(odd) { background: #ffffff; }");
		html.append(
				".compact .main-table tbody td, .compact .side-table tbody td { height: 5.4mm; max-height: 5.4mm; min-height: 5.4mm; line-height: 5.4mm; font-size: 7.2pt; }");
		html.append(
				".compact .main-table th, .compact .side-table th { height: 7mm; min-height: 7mm; max-height: 7mm; font-size: 7.2pt; padding: 1mm 0.5mm; }");
		html.append(".main-table th:first-child, .main-table td:first-child { width: 11mm; font-size: 7pt; }");
		html.append(".main-table th:nth-child(2), .main-table td:nth-child(2) { width: 13mm; }");
		html.append(".main-table th:nth-child(3), .main-table td:nth-child(3) { width: 11mm; font-size: 7.5pt; }");
		html.append(".main-table th:nth-child(4), .main-table td:nth-child(4) { width: 9mm; font-size: 7.5pt; }");
		html.append(
				".main-table th:nth-child(5) { width: 75mm; text-align: center; padding: 1.2mm 0.6mm; font-size: 7.5pt; }");
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
				".side-table { margin-left: 3.3mm; border: 1px solid #dee2e6; table-layout: fixed; font-size: 6.5pt; max-width: 95%; width: auto; display: inline-table; border-collapse: collapse; }");
		html.append(
				".side-table th, .side-table td { width: 6mm; padding: 0.8mm 0.3mm; text-align: center; box-sizing: border-box; }");
		html.append(
				".side-table th:last-child, .side-table td:last-child { border-right: 1px solid #dee2e6 !important; }");
		html.append(
				".side-table tfoot td { background: #e9ecef; font-weight: 600; text-align: center; font-size: 6.5pt; }");
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
	private void paginaAsistencia(StringBuilder html, String logoPng, int year, int month, String deporte, String dia,
			com.taemoi.project.entities.Turno turno, List<AlumnoDeporte> alumnos, List<LocalDate> fechas) {
		// Sort alumnos by nombre
		alumnos.sort(Comparator.comparing(ad -> ad.getAlumno().getNombre()));

		int totalAlumnos = alumnos.size();
		String turnoStr = turno.getHoraInicio() + " a " + turno.getHoraFin();

		// Header
		html.append("<div class='header-section'>");
		if (!logoPng.isEmpty()) {
			html.append("<div class='logo-container'><img src='").append(logoPng)
					.append("' alt='Logo' /></div>");
		}
		html.append("<div class='header-titles'>");
		html.append("<p class='main'>CLUB MOI'S KIM DO</p>");
		html.append("<p class='sub'>Tae Kwon Do</p>");
		html.append("</div>");
		html.append("<h2>LISTADO DE ASISTENCIA - ")
				.append(Month.of(month).getDisplayName(TextStyle.FULL, Locale.of("es")).toUpperCase())
				.append(" ").append(year).append("</h2>");
		html.append("<p class='deporte-label'>").append(deporte.replace("_", " "))
				.append("</p>");
		html.append("<p class='info'>Total: ").append(totalAlumnos).append(" alumnos</p>");
		html.append("<p class='info'>").append(dia.toUpperCase()).append(" - Turno de ").append(turnoStr)
				.append("</p>");
		html.append("</div>");

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

	private static byte[] renderizarAsistencia(String html) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		// Group debts by student
		Map<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> deudasPorAlumno = productosImpagados.stream()
				.collect(Collectors.groupingBy(com.taemoi.project.entities.ProductoAlumno::getAlumno));

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Informe de Deudas de Alumnos", fechaGeneracion));
		html.append(".deuda-total {");
		html.append("  font-weight: 700;");
		html.append("  color: #dc3545;");
		html.append("  font-size: 11pt;");
		html.append("}");
		html.append(".total-general {");
		html.append("  margin-top: 8mm;");
		html.append("  padding: 4mm;");
		html.append("  background: #f8f9fa;");
		html.append("  border: 2px solid #dc3545;");
		html.append("  border-radius: 2mm;");
		html.append("  text-align: center;");
		html.append("}");
		html.append(".total-general h3 {");
		html.append("  margin: 0;");
		html.append("  font-size: 14pt;");
		html.append("  color: #dc3545;");
		html.append("}");
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
		html.append(generarCabeceraConLogo("Informe de Deudas", "#dc3545"));

		double totalGeneral = 0.0;

		if (deudasPorAlumno.isEmpty()) {
			html.append("<div class='section-header' style='background-color: #28a745;'>");
			html.append("No hay deudas pendientes");
			html.append("</div>");
		} else {
			html.append("<div class='section-header' style='background-color: #dc3545;'>");
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

				double totalAlumno = deudas.stream().mapToDouble(pa -> pa.getPrecio() != null ? pa.getPrecio() : 0.0)
						.sum();
				totalGeneral += totalAlumno;

				html.append("<div class='alumno-section'>");
				html.append("<h3 style='margin-top: 6mm; margin-bottom: 2mm; color: #212529;'>")
						.append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append(" (Exp. ")
						.append(alumno.getNumeroExpediente()).append(")");
				html.append(" - <span class='deuda-total'>Total: ").append(String.format("%.2f", totalAlumno))
						.append(" €</span>");
				html.append("</h3>");

				html.append("<table>");
				html.append("<thead><tr>");
				html.append("<th>Concepto</th>");
				html.append("<th>Fecha Asignación</th>");
				html.append("<th>Cantidad</th>");
				html.append("<th>Precio</th>");
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
					html.append("<td>").append(fechaAsignacion).append("</td>");

					html.append("<td>").append(pa.getCantidad() != null ? pa.getCantidad() : 1).append("</td>");
					html.append("<td>").append(String.format("%.2f", pa.getPrecio() != null ? pa.getPrecio() : 0.0))
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
			html.append("<h3>TOTAL GENERAL: ").append(String.format("%.2f", totalGeneral)).append(" €</h3>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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
				totalGeneral += precio;
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Listado de Productos", fechaGeneracion));
		html.append("@page { margin: 14mm 15mm 20mm 15mm;");
		html.append("  @top-center { content: ''; }");
		html.append("  @bottom-left { content: '" + fechaGeneracion + "';");
		html.append("    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;");
		html.append("    font-size: 9pt; color: #6c757d; }");
		html.append("  @bottom-right { content: 'PÃ¡gina ' counter(page) ' de ' counter(pages);");
		html.append("    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;");
		html.append("    font-size: 9pt; color: #6c757d; }");
		html.append("}");
		html.append("body { font-size: 8.5pt; }");
		html.append(".pdf-header { margin-bottom: 2mm; padding-bottom: 1.5mm; border-bottom-width: 1.5px; }");
		html.append(".logo-container { margin-bottom: 1mm; }");
		html.append(".logo-container img { width: 18mm; max-height: 18mm; }");
		html.append(".club-name { font-size: 11.5pt; margin: 0.5mm 0; }");
		html.append(".report-title { font-size: 9.5pt; margin-top: 0.4mm; }");
		html.append(".section-header { padding: 2mm; margin-bottom: 2mm; font-size: 9.5pt; }");
		html.append(".productos-table { margin-top: 0.5mm; }");
		html.append(".productos-table th, .productos-table td { font-size: 7.5pt; padding: 1.1mm 1.2mm; line-height: 1.2; }");
		html.append(".productos-table th { letter-spacing: 0.2px; }");
		html.append(".productos-table { table-layout: fixed; }");
		html.append(".productos-table th:nth-child(1) { width: 70%; }");
		html.append(".productos-table td:nth-child(1) { width: 70%; text-align: left; }");
		html.append(".productos-table th:nth-child(2), .productos-table td:nth-child(2) { width: 30%; }");
		html.append(".precio-cell { font-weight: 600; color: #1b2b2e; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		html.append(generarCabeceraConLogo("Listado de Productos"));

		if (productos.isEmpty()) {
			html.append("<div class='section-header' style='background-color: #6c757d;'>");
			html.append("No hay productos registrados");
			html.append("</div>");
		} else {
			html.append("<div class='section-header' style='background-color: #17a2b8;'>");
			html.append("Productos (" + productos.size() + ")");
			html.append("</div>");

			html.append("<table class='productos-table'>");
			html.append("<thead><tr>");
			html.append("<th>Concepto</th>");
			html.append("<th>Precio</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");

			for (Producto producto : productos) {
				String concepto = producto.getConcepto() != null ? producto.getConcepto() : "N/A";
				String precio = producto.getPrecio() != null
						? String.format("%.2f", producto.getPrecio()) + " &#8364;"
						: "N/A";

				html.append("<tr>");
				html.append("<td>").append(concepto).append("</td>");
				html.append("<td class='precio-cell'>").append(precio).append("</td>");
				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Informe de Reservas de Plaza", fechaGeneracion));
		html.append(".status-badge { display: inline-block; padding: 1mm 3mm; border-radius: 2mm; font-weight: 600; font-size: 9pt; }");
		html.append(".status-pagado { background: #d4edda; color: #155724; }");
		html.append(".status-pendiente { background: #f8d7da; color: #721c24; }");
		html.append(".resumen-general { margin-top: 8mm; padding: 4mm; background: #f8f9fa; border: 2px solid #007bff; border-radius: 2mm; text-align: center; }");
		html.append(".sport-section { page-break-inside: avoid; margin-bottom: 8mm; }");
		html.append(".sport-section tr { page-break-inside: avoid; page-break-after: auto; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");
		html.append(generarCabeceraConLogo("Informe de Reservas de Plaza - " + temporada, "#007bff"));

		if (reservasPorDeporte.isEmpty()) {
			html.append("<div class='section-header' style='background-color: #6c757d;'>");
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
				String color = getColorDeporte(deporte);

				html.append("<div class='sport-section'>");
				html.append("<div class='section-header' style='background-color: ").append(color).append(";'>");
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
					html.append("<td><span class='status-badge ").append(pagado ? "status-pagado" : "status-pendiente")
							.append("'>").append(pagado ? "Pagado" : "Sin Pagar").append("</span></td>");
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
			html.append("<p style='color: #28a745;'><strong>Pagadas:</strong> ").append(totalPagadas).append("</p>");
			html.append("<p style='color: #dc3545;'><strong>Sin pagar:</strong> ").append(totalPendientes).append("</p>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

	private String getColorDeporte(String deporte) {
		if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporte)) {
			return "#0D47A1";
		}
		if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporte)) {
			return "#ff4500";
		}
		if (CatalogoLegadoTaemoi.PILATES.equals(deporte)) {
			return "#57A2A8";
		}
		if (CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA.equals(deporte)) {
			return "#c2185b";
		}
		return "#007bff";
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

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

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
		html.append(generarEstilosModernos("Informe de Mensualidades", fechaGeneracion));
		html.append(".status-badge {");
		html.append("  display: inline-block;");
		html.append("  padding: 1mm 3mm;");
		html.append("  border-radius: 2mm;");
		html.append("  font-weight: 600;");
		html.append("  font-size: 9pt;");
		html.append("}");
		html.append(".status-pagado { background: #d4edda; color: #155724; }");
		html.append(".status-pendiente { background: #f8d7da; color: #721c24; }");
		html.append(".status-cancelado { background: #e2e3e5; color: #383d41; }");
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
		html.append(".resumen-alumno {");
		html.append("  font-size: 10pt;");
		html.append("  margin-top: 2mm;");
		html.append("  padding: 2mm;");
		html.append("  background: #f8f9fa;");
		html.append("  border-radius: 2mm;");
		html.append("}");
		html.append(".resumen-general {");
		html.append("  margin-top: 8mm;");
		html.append("  padding: 4mm;");
		html.append("  background: #f8f9fa;");
		html.append("  border: 2px solid #007bff;");
		html.append("  border-radius: 2mm;");
		html.append("  text-align: center;");
		html.append("}");
		html.append(".resumen-general h3 {");
		html.append("  margin: 2mm 0;");
		html.append("  font-size: 12pt;");
		html.append("  color: #1b2b2e;");
		html.append("}");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Determine title and color based on sport filter
		String titulo;
		String colorPrincipal;
		if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporteFiltro)) {
			titulo = "Informe de Mensualidades - Taekwondo";
			colorPrincipal = "#0D47A1"; // Blue for Taekwondo
		} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporteFiltro)) {
			titulo = "Informe de Mensualidades - Kickboxing";
			colorPrincipal = "#ff4500"; // Orange for Kickboxing
		} else {
			titulo = "Informe de Mensualidades";
			colorPrincipal = "#007bff"; // Default blue
		}

		// Add header with logo
		html.append(generarCabeceraConLogo(titulo, colorPrincipal));

		int totalAlumnos = mensualidadesPorAlumno.size();
		// Pieza 6b-2c: un cargo cancelado sigue saliendo (fila «CANCELADO») pero no suma en ningún total.
		int totalMensualidades = (int) todasMensualidades.stream().filter(m -> !m.estaCancelado()).count();
		int totalPagadas = 0;
		int totalPendientes = 0;
		double totalImportePagado = 0.0;
		double totalImportePendiente = 0.0;

		if (mensualidadesPorAlumno.isEmpty()) {
			html.append("<div class='section-header' style='background-color: #6c757d;'>");
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

			html.append("<div class='section-header' style='background-color: ").append(colorPrincipal).append(";'>");
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
						.mapToDouble(m -> m.getPrecio() != null ? m.getPrecio() : 0.0).sum();
				double importePendiente = vigentes.stream().filter(m -> !Boolean.TRUE.equals(m.getPagado()))
						.mapToDouble(m -> m.getPrecio() != null ? m.getPrecio() : 0.0).sum();

				totalPagadas += pagas;
				totalPendientes += pendientes;
				totalImportePagado += importePagado;
				totalImportePendiente += importePendiente;

				// Determine color based on student's sport (for general PDF with all sports)
				String colorAlumno = "#007bff"; // Default
				String deporteNombre = "";
				if (deporteFiltro == null) {
					// In general PDF, color-code by sport
					if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporte)) {
						colorAlumno = "#0D47A1"; // Blue
						deporteNombre = " <span style='color: #0D47A1; font-weight: bold;'>[Taekwondo]</span>";
					} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporte)) {
						colorAlumno = "#ff4500"; // Orange
						deporteNombre = " <span style='color: #ff4500; font-weight: bold;'>[Kickboxing]</span>";
					} else if (deporte != null) {
						deporteNombre = " <span style='font-weight: bold;'>[" + getDeporteNombre(deporte) + "]</span>";
					} else {
						deporteNombre = " <span style='font-weight: bold;'>[Sin deporte]</span>";
					}
				}

				html.append("<div class='alumno-section'>");
				html.append("<h3 style='margin-top: 6mm; margin-bottom: 2mm; color: #212529;'>")
						.append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append(" (Exp. ")
						.append(alumno.getNumeroExpediente()).append(")").append(deporteNombre);
				html.append("</h3>");

				html.append("<div class='resumen-alumno' style='border-left: 4px solid ").append(colorAlumno)
						.append(";'>");
				html.append("<strong>Total mensualidades:</strong> ").append(vigentes.size());
				html.append(" | <strong style='color: #28a745;'>Pagadas:</strong> ").append(pagas);
				html.append(" (").append(String.format("%.2f", importePagado)).append(" €)");
				html.append(" | <strong style='color: #dc3545;'>Pendientes:</strong> ").append(pendientes);
				html.append(" (").append(String.format("%.2f", importePendiente)).append(" €)");
				html.append("</div>");

				html.append("<table>");
				html.append("<thead><tr>");
				html.append("<th>Concepto</th>");
				html.append("<th>Fecha Asignación</th>");
				html.append("<th>Precio</th>");
				html.append("<th>Estado</th>");
				html.append("<th>Fecha Pago</th>");
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
					html.append("<td>").append(fechaAsignacion).append("</td>");

					html.append("<td>").append(String.format("%.2f", pa.getPrecio() != null ? pa.getPrecio() : 0.0))
							.append(" €</td>");

					boolean cancelado = pa.estaCancelado();
					String estadoClass = cancelado ? "status-cancelado" : (pagado ? "status-pagado" : "status-pendiente");
					String estadoTexto = cancelado ? "CANCELADO" : (pagado ? "PAGADO" : "PENDIENTE");
					html.append("<td><span class='status-badge ").append(estadoClass).append("'>").append(estadoTexto)
							.append("</span></td>");

					String fechaPago = "N/A";
					if (pagado && pa.getFechaPago() != null) {
						LocalDate fecha = Instant.ofEpochMilli(pa.getFechaPago().getTime())
								.atZone(ZoneId.systemDefault()).toLocalDate();
						DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
						fechaPago = fecha.format(dateFormatter);
					}
					html.append("<td>").append(fechaPago).append("</td>");

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
			html.append("<p style='margin: 2mm 0;'><strong>Total de alumnos:</strong> ").append(totalAlumnos)
					.append("</p>");
			html.append("<p style='margin: 2mm 0;'><strong>Total de mensualidades:</strong> ")
					.append(totalMensualidades).append("</p>");
			html.append("<p style='margin: 2mm 0; color: #28a745;'><strong>Mensualidades pagadas:</strong> ")
					.append(totalPagadas).append(" (").append(String.format("%.2f", totalImportePagado))
					.append(" €)</p>");
			html.append("<p style='margin: 2mm 0; color: #dc3545;'><strong>Mensualidades pendientes:</strong> ")
					.append(totalPendientes).append(" (").append(String.format("%.2f", totalImportePendiente))
					.append(" €)</p>");
			html.append("<p style='margin: 2mm 0; font-size: 12pt;'><strong>TOTAL GENERAL:</strong> ")
					.append(String.format("%.2f", totalImportePagado + totalImportePendiente)).append(" €</p>");
			html.append("</div>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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
		String fechaGeneracion = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");

		// Use modern styles with portrait page for more rows per page
		html.append("@page { size: A4; margin: 12mm 10mm 12mm 10mm; }");
		html.append(generarEstilosModernos("Listado Mensualidad " + nombreMes + " " + anio, fechaGeneracion));
		// Additional styles for this report
		html.append(".deporte-cell { font-weight: 600; }");
		html.append(".deporte-taekwondo { color: #0D47A1; }");
		html.append(".deporte-kickboxing { color: #ff4500; }");
		html.append(".grado-cell { text-align: center; }");
		html.append(".comp-cell { text-align: center; }");
		html.append(".nombre-cell { vertical-align: middle; }");
		html.append(".line-item { margin: 0; padding: 0; }");
		html.append(".line-item + .line-item { margin-top: 1mm; }");
		html.append(".grado-cell .line-item { display: flex; justify-content: center; align-items: center; }");
		html.append(".grado-cell .line-item + .line-item { margin-top: 2mm; }");
		html.append(".grado-cell .line-item:first-child { margin-top: 1mm; }");
		html.append(".grado-cell .line-item:last-child { margin-bottom: 1mm; }");
		html.append("td { font-size: 9pt; padding: 2mm 1.5mm; vertical-align: middle; }");
		html.append("th { font-size: 9pt; padding: 2mm 1.5mm; }");
		html.append("tr { page-break-inside: avoid; }");
		html.append("table { -fs-table-paginate: paginate; }");
		html.append("thead { display: table-header-group; }");

		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header with logo and title below it
		html.append(generarCabeceraConLogo("Listado Mensualidad " + nombreMes + " " + anio, "#721c24"));

		// Table
		html.append("<table>");
		html.append("<thead><tr>");
		html.append("<th style='width: 28%;'>APELLIDOS Y NOMBRE</th>");
		html.append("<th style='width: 12%;'>DEPORTE</th>");
		html.append("<th style='width: 14%;'>DESCUENTO</th>");
		html.append("<th style='width: 12%;'>CUANTÍA</th>");
		html.append("<th style='width: 8%;'>COMP</th>");
		html.append("<th style='width: 12%;'>GRADO</th>");
		html.append("<th style='width: 14%;'>FECHA DE PAGO</th>");
		html.append("</tr></thead>");
		html.append("<tbody>");

		if (alumnoDeportes.isEmpty()) {
			html.append("<tr><td colspan='7' style='text-align: center; padding: 10mm; color: #999;'>");
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
					String deporteClass = "";
					String deporteNombre = "";
					if (CatalogoLegadoTaemoi.TAEKWONDO.equals(ad.getDeporte())) {
						deporteClass = "deporte-taekwondo";
						deporteNombre = "Taekwondo";
					} else if (CatalogoLegadoTaemoi.KICKBOXING.equals(ad.getDeporte())) {
						deporteClass = "deporte-kickboxing";
						deporteNombre = "Kickboxing";
					} else if (ad.getDeporte() != null) {
						deporteNombre = getDeporteNombre(ad.getDeporte());
					}
					html.append("<div class='line-item " + deporteClass + "'>")
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
				html.append("<td>");
				for (AlumnoDeporte ad : deportesAlumno) {
					String mapKey = alumno.getId() + "_" + ad.getDeporte();
					List<com.taemoi.project.entities.ProductoAlumno> delMes = productosPorAlumnoDeporte.get(mapKey);
					List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = vigentes(delMes);
					String cuantia = "-";
					if (productosAlumno.isEmpty() && delMes != null && !delMes.isEmpty()) {
						cuantia = "Cancelado";
					} else if (!productosAlumno.isEmpty()) {
						double totalCuantia = productosAlumno.stream().filter(p -> p.getPrecio() != null)
								.mapToDouble(com.taemoi.project.entities.ProductoAlumno::getPrecio).sum();
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
				html.append("<td class='comp-cell'>");
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
								.mapToDouble(com.taemoi.project.entities.ProductoAlumno::getPrecio).sum();
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
				html.append("<td>");
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
		PdfRendererBuilder builder = new PdfRendererBuilder();
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
		String fecha = LocalDate.now().format(
				DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES")));
		StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8' /><style>");
		html.append(generarEstilosModernos(titulo, fecha)).append("</style></head><body>")
				.append(generarCabeceraConLogo(titulo));
		if (alumnas.isEmpty()) {
			html.append("<p>No hay alumnas de D.P.F. para los filtros seleccionados.</p>");
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
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = now.format(formatter);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Listado de Competidores", fechaGeneracion));
		html.append(".badge-categoria {").append(" display: inline-block;").append(" padding: 1.2mm 3mm;")
				.append(" border-radius: 999px;").append(" border: 1.5px solid #007bff;")
				.append(" background: #ffffff;").append(" font-weight: 700;").append(" font-size: 11pt;")
				.append(" color: #1b2b2e;").append(" }");

		html.append(".badge-categoria.badge-muted {").append(" border-color: #6c757d;").append(" color: #6c757d;")
				.append(" }");

		html.append(".grado-celda { white-space: nowrap; }");
		html.append(
				".grado-label { margin-left: 2mm; font-size: 9pt; font-weight: 600; vertical-align: middle; white-space: nowrap; }");

		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header with logo
		html.append(generarCabeceraConLogo("Listado de Competidores"));

		// Separate by sport
		List<AlumnoDeporte> competidoresTaekwondo = competidores.stream()
				.filter(ad -> CatalogoLegadoTaemoi.TAEKWONDO.equals(ad.getDeporte())).collect(Collectors.toList());

		List<AlumnoDeporte> competidoresKickboxing = competidores.stream()
				.filter(ad -> CatalogoLegadoTaemoi.KICKBOXING.equals(ad.getDeporte())).collect(Collectors.toList());

		// Taekwondo section
		if (!competidoresTaekwondo.isEmpty()) {
			html.append("<div class='section-header' style='background-color: #0D47A1;'>");
			html.append("Taekwondo (").append(competidoresTaekwondo.size()).append(" competidores)");
			html.append("</div>");
			html.append(generarSeccionCompetidores(competidoresTaekwondo));
		}

		// Kickboxing section
		if (!competidoresKickboxing.isEmpty()) {
			html.append("<div class='section-header kickboxing' style='background-color: #ff4500; margin-top: 10mm;'>");
			html.append("Kickboxing (").append(competidoresKickboxing.size()).append(" competidores)");
			html.append("</div>");
			html.append(generarSeccionCompetidores(competidoresKickboxing));
		}

		if (competidores.isEmpty()) {
			html.append("<p style='text-align: center; color: #999; padding: 20mm;'>No hay competidores activos</p>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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

		String deporte = competidores.isEmpty() ? CatalogoLegadoTaemoi.TAEKWONDO : competidores.get(0).getDeporte();

		// Color según deporte para la badge
		String colorAcento;
		if (CatalogoLegadoTaemoi.KICKBOXING.equals(deporte)) {
			colorAcento = "#ff4500";
		} else if (CatalogoLegadoTaemoi.TAEKWONDO.equals(deporte)) {
			colorAcento = "#0D47A1";
		} else {
			colorAcento = "#007bff";
		}

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

			boolean esSinCategoria = SIN_CATEGORIA.equals(nombreCategoria);

			// Encabezado de grupo con badge (sin rectángulo azul)
			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");

			html.append("<div class='izquierda'>");
			html.append("<span class='badge-categoria");
			if (esSinCategoria) {
				html.append(" badge-muted");
			}
			html.append("' style='border-color: ").append(esSinCategoria ? "#6c757d" : colorAcento).append(";");

			if (!esSinCategoria) {
				html.append(" color: ").append(colorAcento).append(";");
			}
			html.append("'>").append(nombreCategoria).append("</span>");
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
			html.append("<th>Peso</th>");
			html.append("<th>Fecha Peso</th>");
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

				html.append("<td>").append(peso).append("</td>");
				html.append("<td>").append(fechaPeso).append("</td>");
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

		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = now.format(formatter);

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
		html.append(generarEstilosModernos("Convocatoria a Exámen " + fechaConvocatoria, fechaGeneracion));
		html.append(
				".convocatoria-title { text-align: center; font-size: 18pt; font-weight: 700; margin: 5mm 0 2mm 0; text-transform: uppercase; }");
		html.append(".convocatoria-subtitle { text-align: center; font-size: 10pt; margin: 0 0 3mm 0; color: #666; }");
		html.append(".total-alumnos { text-align: center; font-size: 12pt; font-weight: 600; margin: 3mm 0 5mm 0; }");
		html.append(
				".grade-transition-box { margin: 5mm 0 2mm 0; padding: 2mm 3mm; background: #f8f9fa; border-left: 4px solid #007bff; }");
		html.append(".grade-transition-table { width: 100%; border-collapse: collapse; }");
		html.append(".grade-from { text-align: left; vertical-align: middle; font-weight: 600; font-size: 11pt; }");
		html.append(".grade-to { text-align: right; vertical-align: middle; font-weight: 600; font-size: 11pt; }");
		html.append(
				".student-count { text-align: center; font-size: 10pt; color: #666; font-weight: 600; vertical-align: middle; }");
		html.append(".belt-with-text { vertical-align: middle; }");
		html.append(".belt-text { margin-left: 3mm; vertical-align: middle; white-space: nowrap; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header
		html.append(generarCabeceraConLogo(""));

		// Convocatoria Title
		html.append("<div class='convocatoria-title'>CONVOCATORIA A EXÁMEN ").append(fechaConvocatoria)
				.append("</div>");
		html.append("<div class='convocatoria-subtitle'>(SOLO ALUMNOS CON DERECHO A EXAMEN)</div>");
		html.append("<div class='total-alumnos'>TOTAL ALUMNOS: ").append(alumnosReporte.size()).append("</div>");

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

			// Left: DE [GRADE]
			html.append("<td class='grade-from'>");
			html.append("<span class='belt-with-text'>");
			html.append(CinturonesPdf.inline(cinturonDe(convocatoria.getDeporte(), gradoActual), 50, 15));
			html.append("<span class='belt-text'>DE ")
					.append(progresionGrados.obtenerNombreVisible(convocatoria.getDeporte(),
							gradoActual == null ? null : gradoActual).toUpperCase())
					.append("</span>");
			html.append("</span>");
			html.append("</td>");

			// Center: Student count
			html.append("<td class='student-count' style='width: 30%;'>");
			html.append(alumnos.size()).append(" Alumnos");
			html.append("</td>");

			// Right: A [GRADE]
			html.append("<td class='grade-to'>");
			html.append("<span class='belt-with-text'>");
			html.append("<span class='belt-text' style='margin-right: 3mm;'>A ").append(
					progresionGrados.obtenerNombreVisible(convocatoria.getDeporte(),
							gradoSiguiente == null ? null : gradoSiguiente).toUpperCase())
					.append("</span>");
			html.append(CinturonesPdf.inline(cinturonDe(convocatoria.getDeporte(), gradoSiguiente), 50, 15));
			html.append("</span>");
			html.append("</td>");

			html.append("</tr>");
			html.append("</table>");
			html.append("</div>");

			// Table for this grade group
			html.append("<table>");
			html.append("<thead>");
			html.append("<tr>");
			html.append("<th>NOMBRE</th>");
			html.append("<th>EXP</th>");
			html.append("<th>LIC. FEDERATIVA</th>");
			html.append("<th>EDAD</th>");
			html.append("<th>CATEGORÍA</th>");
			html.append("<th>PESO</th>");
			html.append("<th>PAGADO</th>");
			html.append("</tr>");
			html.append("</thead>");
			html.append("<tbody>");

			for (com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO alumno : alumnos) {
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombreCompleto().toUpperCase()).append("</td>");
				html.append("<td>").append(alumno.getNumeroExpediente() != null ? alumno.getNumeroExpediente() : "")
						.append("</td>");
				html.append("<td>").append(alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia() : "")
						.append("</td>");
				html.append("<td>").append(alumno.getEdad()).append("</td>");
				html.append("<td>").append(alumno.getCategoria() != null ? alumno.getCategoria() : "").append("</td>");
				html.append("<td>").append(alumno.getPeso() != null ? alumno.getPeso() : "").append("</td>");
				html.append("<td style='font-weight: 600;");
				if (alumno.getPagado() != null && alumno.getPagado()) {
					html.append(" color: #28a745;'>SI");
				} else {
					html.append(" color: #dc3545;'>NO");
				}
				html.append("</td>");
				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
		}

		html.append("</body>");
		html.append("</html>");

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
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
