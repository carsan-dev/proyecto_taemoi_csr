package com.taemoi.project.services.impl;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.utils.DiaSemanaUtils;
import com.taemoi.project.utils.FechaUtils;

@Service
public class PDFServiceImpl implements PDFService {

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoService alumnoService;

	@Autowired
	private GradoRepository gradoRepository;

	@Autowired
	private com.taemoi.project.repositories.ProductoAlumnoRepository productoAlumnoRepository;

	// Cache for the PNG logo to avoid repeated conversion
	private String logoPngBase64Cache = null;

	/**
	 * Converts the school logo SVG to PNG and returns it as a base64 data URI.
	 * This provides full compatibility with OpenHTML to PDF.
	 * The result is cached to avoid repeated conversions.
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
	 * Generates a standard header section with logo for PDF reports.
	 * The logo is converted to PNG and embedded as base64 for full compatibility.
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
	 * @param titulo Main title for the report
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
	 * Generates modern CSS styles matching the website design.
	 * Uses Montserrat font, website colors, and modern design principles.
	 *
	 * @param pageTitle Title to display in the header
	 * @param fechaGeneracion Date string to display in footer
	 * @return CSS style string
	 */
	private String generarEstilosModernos(String pageTitle, String fechaGeneracion) {
		return "@page {" +
			"  margin: 25mm 15mm 20mm 15mm;" +
			"  @top-center {" +
			"    content: '" + pageTitle + "';" +
			"    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" +
			"    font-size: 16pt;" +
			"    font-weight: 700;" +
			"    color: #1b2b2e;" +
			"    text-align: center;" +
			"  }" +
			"  @bottom-left {" +
			"    content: '" + fechaGeneracion + "';" +
			"    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" +
			"    font-size: 9pt;" +
			"    color: #6c757d;" +
			"  }" +
			"  @bottom-right {" +
			"    content: 'Página ' counter(page) ' de ' counter(pages);" +
			"    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" +
			"    font-size: 9pt;" +
			"    color: #6c757d;" +
			"  }" +
			"}" +
			"* { box-sizing: border-box; }" +
			"body {" +
			"  font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" +
			"  color: #212529;" +
			"  line-height: 1.6;" +
			"  background: #ffffff;" +
			"}" +
			".pdf-header {" +
			"  text-align: center;" +
			"  margin-bottom: 8mm;" +
			"  padding-bottom: 5mm;" +
			"  border-bottom: 3px solid #007bff;" +
			"  page-break-inside: avoid;" +
			"}" +
			".logo-container {" +
			"  text-align: center;" +
			"  margin-bottom: 3mm;" +
			"}" +
			".logo-container img {" +
			"  width: 35mm;" +
			"  height: auto;" +
			"  max-height: 35mm;" +
			"}" +
			".header-content {" +
			"  text-align: center;" +
			"}" +
			".club-name {" +
			"  font-size: 20pt;" +
			"  font-weight: 700;" +
			"  color: #1b2b2e;" +
			"  margin: 2mm 0;" +
			"  text-transform: uppercase;" +
			"  letter-spacing: 1px;" +
			"}" +
			".report-title {" +
			"  font-size: 16pt;" +
			"  font-weight: 600;" +
			"  color: #007bff;" +
			"  margin: 2mm 0 0 0;" +
			"}" +
			"h1, h2, h3 {" +
			"  font-weight: 700;" +
			"  color: #1b2b2e;" +
			"  margin: 8mm 0 5mm 0;" +
			"}" +
			"h1 { font-size: 22pt; }" +
			"h2 {" +
			"  font-size: 16pt;" +
			"  padding-bottom: 2mm;" +
			"  border-bottom: 2px solid #007bff;" +
			"  display: inline-block;" +
			"  min-width: 40%;" +
			"}" +
			".grupo {" +
			"  margin-top: 6mm;" +
			"  margin-bottom: 6mm;" +
			"  page-break-inside: avoid;" +
			"}" +
			".encabezado-grupo {" +
			"  background-color: #f8f9fa;" +
			"  padding: 4mm 3mm;" +
			"  border-radius: 3mm;" +
			"  border: 1px solid #dee2e6;" +
			"  margin-bottom: 3mm;" +
			"}" +
			".encabezado-grupo .izquierda {" +
			"  display: inline-block;" +
			"  width: 70%;" +
			"  vertical-align: middle;" +
			"}" +
			".encabezado-grupo .derecha {" +
			"  display: inline-block;" +
			"  width: 28%;" +
			"  text-align: right;" +
			"  font-weight: 600;" +
			"  color: #007bff;" +
			"  font-size: 11pt;" +
			"  vertical-align: middle;" +
			"}" +
			".cinturon {" +
			"  display: inline-block;" +
			"  vertical-align: middle;" +
			"  width: 28mm;" +
			"  height: 6mm;" +
			"  margin-right: 3mm;" +
			"  border: 1.5px solid #495057;" +
			"  border-radius: 1mm;" +
			"  position: relative;" +
			"}" +
			".cinturon.doble .superior, .cinturon.doble .inferior {" +
			"  width: 100%;" +
			"  height: 50%;" +
			"  position: absolute;" +
			"}" +
			".cinturon.doble .superior {" +
			"  top: 0;" +
			"  border-top-left-radius: 0.5mm;" +
			"  border-top-right-radius: 0.5mm;" +
			"}" +
			".cinturon.doble .inferior {" +
			"  bottom: 0;" +
			"  border-bottom-left-radius: 0.5mm;" +
			"  border-bottom-right-radius: 0.5mm;" +
			"}" +
			".grado-nombre {" +
			"  display: inline-block;" +
			"  vertical-align: middle;" +
			"  font-size: 12pt;" +
			"  font-weight: 600;" +
			"  color: #1b2b2e;" +
			"}" +
			".raya {" +
			"  position: absolute;" +
			"  top: 50%;" +
			"  transform: translateY(-50%);" +
			"  height: 80%;" +
			"  background-color: #FFD700;" +
			"  z-index: 10;" +
			"}" +
			"table {" +
			"  width: 100%;" +
			"  border-collapse: collapse;" +
			"  margin-top: 2mm;" +
			"  border-radius: 2mm;" +
			"  overflow: hidden;" +
			"}" +
			"th, td {" +
			"  border: 1px solid #dee2e6;" +
			"  padding: 3mm 2mm;" +
			"  text-align: center;" +
			"  font-size: 10pt;" +
			"}" +
			"th {" +
			"  background-color: #007bff;" +
			"  color: #ffffff;" +
			"  font-weight: 600;" +
			"  font-size: 10pt;" +
			"  text-transform: uppercase;" +
			"  letter-spacing: 0.5px;" +
			"}" +
			"tbody tr:nth-child(even) {" +
			"  background-color: #f8f9fa;" +
			"}" +
			"tbody tr:nth-child(odd) {" +
			"  background-color: #ffffff;" +
			"}" +
			"tbody tr:hover {" +
			"  background-color: #e7f3ff;" +
			"}" +
			".kickboxing {" +
			"  padding-top: 10mm;" +
			"  border-top: 3px solid #ff4500;" +
			"  margin-top: 10mm;" +
			"}" +
			".section-header {" +
			"  background-color: #1b2b2e;" +
			"  color: #ffffff;" +
			"  padding: 5mm;" +
			"  border-radius: 2mm;" +
			"  margin-bottom: 5mm;" +
			"  font-size: 14pt;" +
			"  font-weight: 700;" +
			"  text-align: center;" +
			"}";
	}

	@Override
	public byte[] generarInformeAlumnosPorGrado() {
		return generarInformePorGrado(Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING));
	}

	@Override
	public byte[] generarInformeTaekwondoPorGrado() {
		return generarInformePorGrado(Collections.singletonList(Deporte.TAEKWONDO));
	}

	@Override
	public byte[] generarInformeKickboxingPorGrado() {
		return generarInformePorGrado(Collections.singletonList(Deporte.KICKBOXING));
	}

	/**
	 * Genera el informe PDF filtrando los alumnos según la lista de deportes. Si se
	 * solicitan ambos deportes, se generan secciones separadas para cada uno.
	 */
	private byte[] generarInformePorGrado(List<Deporte> deportes) {
		List<Alumno> alumnos = alumnoRepository.findByGradoNotNullAndDeporteIn(deportes);

		LocalDate now = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
				Locale.of("es", "ES"));
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
		if (deportes.size() == 2 && deportes.contains(Deporte.TAEKWONDO) && deportes.contains(Deporte.KICKBOXING)) {
			html.append(generarCabeceraConLogo("Listado de Alumnos por Grado"));

			List<Alumno> alumnosTaekwondo = alumnos.stream()
					.filter(a -> a.getDeporte() == Deporte.TAEKWONDO)
					.collect(Collectors.toList());
			html.append("<div class='section-header' style='background-color: #0D47A1;'>");
			html.append("Taekwondo");
			html.append("</div>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<Alumno> alumnosKickboxing = alumnos.stream()
					.filter(a -> a.getDeporte() == Deporte.KICKBOXING)
					.collect(Collectors.toList());
			html.append("<div class='section-header kickboxing' style='background-color: #ff4500; margin-top: 10mm;'>");
			html.append("Kickboxing");
			html.append("</div>");
			html.append(generarSeccion(alumnosKickboxing));
		} else {
			// Use sport-specific colors for the header border
			if (deportes.get(0) == Deporte.TAEKWONDO) {
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
	private String generarSeccion(List<Alumno> alumnos) {
		StringBuilder html = new StringBuilder();
		Map<Grado, List<Alumno>> alumnosPorGrado = alumnos.stream().collect(Collectors.groupingBy(Alumno::getGrado));

		List<Map.Entry<Grado, List<Alumno>>> entradasOrdenadas = new ArrayList<>(alumnosPorGrado.entrySet());
		entradasOrdenadas.sort((e1, e2) -> {
			int ordinal1 = e1.getKey().getTipoGrado().ordinal();
			int ordinal2 = e2.getKey().getTipoGrado().ordinal();
			return Integer.compare(ordinal2, ordinal1);
		});

		for (Map.Entry<Grado, List<Alumno>> entry : entradasOrdenadas) {
			Grado grado = entry.getKey();
			List<Alumno> alumnosGrado = entry.getValue();
			alumnosGrado.sort(Comparator.comparing(a -> (a.getNombre() + " " + a.getApellidos()).toLowerCase()));
			TipoGrado tipo = grado.getTipoGrado();

			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");
			html.append("<div class='izquierda'>");
			if (tipo.name().contains("ROJO_NEGRO")) {
				String[] parts = tipo.name().split("_");
				String colorInferior = obtenerColorPorNombre(parts[0]);
				String colorSuperior = obtenerColorPorNombre(parts[1]);
				int stripeCount = 0;
				try {
					stripeCount = Integer.parseInt(parts[2]);
				} catch (Exception e) {
				}
				html.append("<div class='cinturon doble' style='position: relative;'>");
				html.append(
						"<div class='superior' style='background-color: " + colorSuperior + "; z-index: 1;'></div>");
				html.append(
						"<div class='inferior' style='background-color: " + colorInferior + "; z-index: 1;'></div>");
				int stripeWidth = 6;
				int gap = 2;
				int initialMargin = 6;
				for (int i = 0; i < stripeCount; i++) {
					int rightOffset = initialMargin + i * (stripeWidth + gap);
					html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
							+ "px; z-index: 2;'></div>");
				}
				html.append("</div>");
			} else if (tipo.name().contains("DAN")
					|| (tipo.name().contains("PUM") && !tipo.name().contains("ROJO_NEGRO"))) {
				html.append("<div class='cinturon' style='background-color: " + obtenerColorCinturon(tipo) + ";'>");
				try {
					int stripeCount = 0;
					if (tipo.name().contains("DAN")) {
						String[] parts = tipo.name().split("_");
						stripeCount = Integer.parseInt(parts[1]);
					}
					int stripeWidth = 6;
					int gap = 2;
					int initialMargin = 6;
					for (int i = 0; i < stripeCount; i++) {
						int rightOffset = initialMargin + i * (stripeWidth + gap);
						html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
								+ "px;'></div>");
					}
				} catch (Exception e) {
				}
				html.append("</div>");
			} else if (tipo.name().contains("_")) {
				String[] parts = tipo.name().split("_");
				String colorSuperior = obtenerColorPorNombre(parts[1]);
				String colorInferior = obtenerColorPorNombre(parts[0]);
				html.append("<div class='cinturon doble'>");
				html.append("<div class='superior' style='background-color: " + colorSuperior + ";'></div>");
				html.append("<div class='inferior' style='background-color: " + colorInferior + ";'></div>");
				html.append("</div>");
			} else {
				String cinturonStyle = obtenerEstiloCinturon(tipo);
				html.append("<div class='cinturon' style='" + cinturonStyle + "'></div>");
			}

			html.append("<span class='grado-nombre'>").append(tipo.getNombre()).append("</span>");
			html.append("</div>");
			html.append("<div class='derecha'>");
			html.append("Alumnos: ").append(alumnosGrado.size());
			html.append("</div>");
			html.append("</div>");

			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>N&#186; Expediente</th>");
			html.append("<th>Licencia Federativa</th>");
			html.append("<th>Fecha del Grado</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");
			for (Alumno alumno : alumnosGrado) {
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append("</td>");
				html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
				String licencia = alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia().toString() : "N/A";
				html.append("<td>").append(licencia).append("</td>");
				String fechaGrado = alumno.getFechaGrado() != null ? alumno.getFechaGrado().toString() : "N/A";
				html.append("<td>").append(fechaGrado).append("</td>");
				html.append("</tr>");
			}
			html.append("</tbody>");
			html.append("</table>");
			html.append("</div>");
		}
		return html.toString();
	}

	/**
	 * Retorna el estilo en línea para el div del cinturón. Para grados DAN se
	 * retorna únicamente el color base (ya que las rayas se generan en el HTML).
	 */
	private String obtenerEstiloCinturon(TipoGrado tipo) {
		if (tipo == null) {
			return "background-color: #CCCCCC;";
		}
		return "background-color: " + obtenerColorCinturon(tipo) + ";";
	}

	/**
	 * Devuelve un color (en formato hexadecimal) según el TipoGrado.
	 */
	private String obtenerColorCinturon(TipoGrado tipo) {
		if (tipo == null)
			return "#CCCCCC";
		switch (tipo) {
		case BLANCO:
			return "#FFFFFF";
		case BLANCO_AMARILLO:
			return "#FFFACD";
		case AMARILLO:
			return "#FFFF00";
		case AMARILLO_NARANJA:
			return "#FFD700";
		case NARANJA:
			return "#FFA500";
		case NARANJA_VERDE:
			return "#ADFF2F";
		case VERDE:
			return "#008000";
		case VERDE_AZUL:
			return "#20B2AA";
		case AZUL:
			return "#0000FF";
		case AZUL_ROJO:
			return "#8A2BE2";
		case ROJO:
			return "#FF0000";
		case ROJO_NEGRO_1_PUM:
		case ROJO_NEGRO_2_PUM:
		case ROJO_NEGRO_3_PUM:
			return "#800000";
		case NEGRO_1_DAN:
		case NEGRO_2_DAN:
		case NEGRO_3_DAN:
		case NEGRO_4_DAN:
		case NEGRO_5_DAN:
			return "#000000";
		default:
			return "#CCCCCC";
		}
	}

	private String obtenerColorPorNombre(String nombre) {
		switch (nombre) {
		case "BLANCO":
			return "#FFFFFF";
		case "BLANCO_AMARILLO":
			return "#FFFACD";
		case "AMARILLO":
			return "#FFFF00";
		case "AMARILLO_NARANJA":
			return "#FFD700";
		case "NARANJA":
			return "#FFA500";
		case "NARANJA_VERDE":
			return "#ADFF2F";
		case "VERDE":
			return "#008000";
		case "VERDE_AZUL":
			return "#20B2AA";
		case "AZUL":
			return "#0000FF";
		case "AZUL_ROJO":
			return "#8A2BE2";
		case "ROJO":
			return "#FF0000";
		case "NEGRO":
			return "#000000";
		default:
			return "#CCCCCC";
		}
	}

	@Override
	public byte[] generarInformeLicencias() {
		List<Alumno> alumnos = alumnoRepository.findAll();

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
				Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		List<Alumno> licenciasVigor = new ArrayList<>();
		List<Alumno> licenciasCaducadas = new ArrayList<>();
		List<Alumno> sinLicencia = new ArrayList<>();

		for (Alumno alumno : alumnos) {
			if (alumno.getTieneLicencia() != null && alumno.getTieneLicencia() && alumno.getFechaLicencia() != null) {
				LocalDate fechaLicencia = Instant.ofEpochMilli(alumno.getFechaLicencia().getTime())
						.atZone(ZoneId.systemDefault()).toLocalDate();
				if (fechaLicencia.plusYears(1).isAfter(today)) {
					licenciasVigor.add(alumno);
				} else {
					licenciasCaducadas.add(alumno);
				}
			} else {
				sinLicencia.add(alumno);
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
		html.append(".status-caducada { background: #f8d7da; color: #721c24; }");
		html.append(".status-sin { background: #fff3cd; color: #856404; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo("Informe de Licencias"));

		html.append("<div class='section-header' style='background-color: #28a745;'>");
		html.append("Licencias en Vigor (" + licenciasVigor.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasVigor));

		html.append("<div class='section-header' style='background-color: #dc3545;'>");
		html.append("Licencias Caducadas (" + licenciasCaducadas.size() + ")");
		html.append("</div>");
		html.append(generarTablaAlumnos(licenciasCaducadas));

		html.append("<div class='section-header' style='background-color: #ffc107;'>");
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
	private String generarTablaAlumnos(List<Alumno> alumnos) {
		StringBuilder html = new StringBuilder();
		html.append("<table>");
		html.append("<thead><tr>");
		html.append("<th>Nombre y Apellidos</th>");
		html.append("<th>N&#186; Expediente</th>");
		html.append("<th>N&#186; Licencia</th>");
		html.append("<th>Fecha de Licencia</th>");
		html.append("<th>Grado</th>");
		html.append("</tr></thead>");
		html.append("<tbody>");
		for (Alumno alumno : alumnos) {
			html.append("<tr>");
			html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");
			html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
			String numLicencia = (alumno.getNumeroLicencia() != null) ? alumno.getNumeroLicencia().toString() : "N/A";
			html.append("<td>").append(numLicencia).append("</td>");
			String fechaLic = (alumno.getFechaLicencia() != null) ? alumno.getFechaLicencia().toString() : "N/A";
			html.append("<td>").append(fechaLic).append("</td>");
			String nombreGrado = (alumno.getGrado() != null && alumno.getGrado().getTipoGrado().getNombre() != null)
					? alumno.getGrado().getTipoGrado().getNombre()
					: "N/A";
			html.append("<td>").append(nombreGrado).append("</td>");
			html.append("</tr>");
		}
		html.append("</tbody>");
		html.append("</table>");
		return html.toString();
	}

	@Override
	public byte[] generarInformeInfantilesAPromocionar() {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();
		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
				Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		List<Alumno> alumnosInfantiles = new ArrayList<>();
		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() != null && Boolean.TRUE.equals(alumno.getAptoParaExamen())) {
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				if (edad < 15) {
					alumnosInfantiles.add(alumno);
				}
			}
		}

		Map<String, List<Alumno>> alumnosAgrupados = alumnosInfantiles.stream()
				.collect(Collectors.groupingBy(alumno -> getPromotionGrade(alumno)));

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Alumnos Infantiles a Promocionar", fechaGeneracion));
		html.append(".promotion-group { margin-bottom: 8mm; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo("Infantiles a Promocionar"));

		for (Map.Entry<String, List<Alumno>> entry : alumnosAgrupados.entrySet()) {
			String promotionGrade = entry.getKey();
			html.append("<div class='promotion-group'>");
			html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>N&#186; Expediente</th>");
			html.append("<th>Licencia Federativa</th>");
			html.append("<th>Fecha Licencia</th>");
			html.append("<th>Edad</th>");
			html.append("<th>Derecho a Examen</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");
			for (Alumno alumno : entry.getValue()) {
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append("</td>");
				html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
				String licencia = alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia().toString() : "N/A";
				html.append("<td>").append(licencia).append("</td>");
				String fechaLic = alumno.getFechaLicencia() != null ? alumno.getFechaLicencia().toString() : "N/A";
				html.append("<td>").append(fechaLic).append("</td>");
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				html.append("<td>").append(edad).append("</td>");
				String derechoExamen = alumno.getTieneDerechoExamen() ? "<span style='color: #28a745; font-weight: 600;'>Sí</span>" : "<span style='color: #dc3545; font-weight: 600;'>No</span>";
				html.append("<td>").append(derechoExamen).append("</td>");
				html.append("</tr>");
			}
			html.append("</tbody>");
			html.append("</table>");
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
	public byte[] generarInformeAdultosAPromocionar() {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();
		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
				Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		// Filtramos los adultos aptos
		List<Alumno> alumnosAdultos = todosAlumnos.stream()
				.filter(a -> a.getFechaNacimiento() != null && Boolean.TRUE.equals(a.getAptoParaExamen()))
				.filter(a -> FechaUtils.calcularEdad(a.getFechaNacimiento()) >= 15).collect(Collectors.toList());

		// Agrupamos por grado de promoción
		Map<String, List<Alumno>> alumnosAgrupados = alumnosAdultos.stream()
				.collect(Collectors.groupingBy(this::getPromotionGrade));

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>");
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append(generarEstilosModernos("Alumnos Adultos a Promocionar", fechaGeneracion));
		html.append(".promotion-group { margin-bottom: 8mm; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Add header with logo
		html.append(generarCabeceraConLogo("Adultos a Promocionar"));

		for (Map.Entry<String, List<Alumno>> entry : alumnosAgrupados.entrySet()) {
			String promotionGrade = entry.getKey();
			html.append("<div class='promotion-group'>");
			html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>N&#186; Expediente</th>");
			html.append("<th>Licencia Federativa</th>");
			html.append("<th>Fecha Licencia</th>");
			html.append("<th>Edad</th>");
			html.append("<th>Derecho a Examen</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");
			for (Alumno alumno : entry.getValue()) {
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				String derechoExamen = alumno.getTieneDerechoExamen() ? "<span style='color: #28a745; font-weight: 600;'>Sí</span>" : "<span style='color: #dc3545; font-weight: 600;'>No</span>";
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");
				html.append("<td>").append(alumno.getNumeroExpediente()).append("</td>");
				html.append("<td>").append(alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia() : "N/A").append("</td>");
				html.append("<td>").append(alumno.getFechaLicencia() != null ? alumno.getFechaLicencia().toString() : "N/A").append("</td>");
				html.append("<td>").append(edad).append("</td>");
				html.append("<td>").append(derechoExamen).append("</td>");
				html.append("</tr>");
			}
			html.append("</tbody>");
			html.append("</table>");
			html.append("</div>");
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

	private String getPromotionGrade(Alumno alumno) {
		if (alumno == null || alumno.getGrado() == null) {
			return "Sin Grado Asignado";
		}

		var nuevoTipo = alumnoService.calcularSiguienteGrado(alumno);
		if (nuevoTipo == null) {
			return "Grado Máximo Alcanzado";
		}
		var nuevoGrado = gradoRepository.findByTipoGrado(nuevoTipo);
		if (nuevoGrado == null) {
			return "Grado No Encontrado";
		}
		return (nuevoGrado.getTipoGrado() != null && nuevoGrado.getTipoGrado().getNombre() != null)
				? nuevoGrado.getTipoGrado().getNombre()
				: "N/A";
	}

	@Override
	public byte[] generarListadoAsistencia(int year, int month, String grupo, String turno) throws IOException {
		// Normalize the turno string to handle different dash characters
		String normalizedTurno = turno.replace('–', '-').replace('—', '-');

		List<Alumno> alumnos = alumnoRepository.findAll().stream()
				.filter(a -> a.getFechaNacimiento() != null && a.getTurnos() != null && !a.getTurnos().isEmpty())
				.filter(a -> a.getTurnos().stream()
						.anyMatch(t -> t.getDiaSemana() != null && t.getHoraInicio() != null && t.getHoraFin() != null
								&& t.getDiaSemana().equalsIgnoreCase(grupo)
								&& normalizedTurno.contains(t.getHoraInicio().toString())
								&& normalizedTurno.contains(t.getHoraFin().toString())))
				.collect(Collectors.toList());
		int totalAlumnos = alumnos.size();

		DayOfWeek dow = DiaSemanaUtils.mapGrupoToDayOfWeek(grupo);
		LocalDate inicio = LocalDate.of(year, month, 1);
		LocalDate finMes = inicio.with(TemporalAdjusters.lastDayOfMonth());
		List<LocalDate> fechas = new ArrayList<>();
		LocalDate actual = inicio.with(TemporalAdjusters.nextOrSame(dow));
		while (!actual.isAfter(finMes)) {
			fechas.add(actual);
			actual = actual.plusWeeks(1);
		}

		String logoPng = getLogoPngBase64();
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>");
		html.append("<style>");
		html.append("@page { margin: 12mm; }");
		html.append("* { box-sizing: border-box; font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif; }");
		html.append("body { margin: 0; padding: 0; }");
		html.append(".header-section { text-align: center; margin-bottom: 6mm; border-bottom: 2px solid #007bff; padding-bottom: 4mm; }");
		html.append(".logo-container { text-align: center; margin-bottom: 3mm; }");
		html.append(".logo-container img { width: 30mm; height: auto; max-height: 30mm; }");
		html.append(".header-titles .main { font-size: 20pt; color: #1b2b2e; font-weight: 700; margin: 2mm 0; }");
		html.append(".header-titles .sub { font-size: 16pt; color: #007bff; margin: 0 0 3mm; font-weight: 600; }");
		html.append("h2 { font-size: 14pt; margin: 3mm 0; font-weight: 700; color: #1b2b2e; text-align: center; }");
		html.append("p.info { text-align: center; font-size: 10pt; margin: 2mm 0; font-weight: 600; color: #495057; }");
		html.append(".table-container { width: 100%; }");
		html.append(".table-wrapper { display: table; width: 100%; }");
		html.append(".table-cell { display: table-cell; vertical-align: top; }");
		html.append(".main-table, .side-table { border-collapse: collapse; width: 100%; }");
		html.append(".main-table th, .main-table td { border: 1px solid #dee2e6; padding: 2mm 1mm; text-align: center; font-size: 9pt; }");
		html.append(".main-table th { background-color: #007bff; color: #ffffff; font-weight: 600; text-transform: uppercase; letter-spacing: 0.3px; }");
		html.append(".main-table tbody tr:nth-child(even) { background: #f8f9fa; }");
		html.append(".main-table tbody tr:nth-child(odd) { background: #ffffff; }");
		html.append(".main-table th:first-child, .main-table td:first-child { width: 15mm; }");
		html.append(".cinturon-blanco { background: #ffffff; border: 1px solid #495057; }");
		html.append(".cinturon-amarillo { background: #ffeb3b; }");
		html.append(".cinturon-verde { background: #4caf50; }");
		html.append(".cinturon-azul { background: #2196f3; }");
		html.append(".cinturon-rojo { background: #f44336; }");
		html.append(".cinturon-negro { background: #212529; }");
		html.append(".licencia-no { color: #dc3545; font-weight: 700; }");
		html.append(".licencia-ok { color: #28a745; font-weight: 600; }");
		html.append(".side-table { table-layout: fixed; margin-left: 2mm; }");
		html.append(".side-table th, .side-table td { border: 1px solid #dee2e6; width: 13mm; height: 10mm; padding: 1mm; text-align: center; font-size: 9pt; }");
		html.append(".side-table th { background-color: #007bff; color: #ffffff; font-weight: 600; }");
		html.append(".side-table tfoot td { background: #e9ecef; font-weight: 600; }");
		html.append("</style>");
		html.append("</head><body>");

		// Header
		html.append("<div class='header-section'>");
		if (!logoPng.isEmpty()) {
			html.append("<div class='logo-container'><img src='").append(logoPng).append("' alt='Logo' /></div>");
		}
		html.append("<div class='header-titles'>");
		html.append("<p class='main'>CLUB MOI'S KIM DO</p>");
		html.append("<p class='sub'>Tae Kwon Do</p>");
		html.append("</div>");
		html.append("<h2>LISTADO DE ASISTENCIA - ")
				.append(Month.of(month).getDisplayName(TextStyle.FULL, Locale.of("es")).toUpperCase())
				.append(" ").append(year).append("</h2>");
		html.append("<p class='info'>Total: ").append(totalAlumnos).append(" alumnos</p>");
		html.append("<p class='info'>").append(grupo.toUpperCase())
				.append(" - Turno de ").append(turno.replace("–", " a ")).append("</p>");
		html.append("</div>");

		// Tables
		html.append("<div class='table-wrapper'>");
		html.append("<div class='table-cell' style='width: 60%;'>");
		html.append("<table class='main-table'>");
		html.append("<thead><tr>");
		html.append("<th></th><th>Lic. Fed</th><th>Edad</th><th>Nombre y Apellidos</th><th>Nº Exp.</th>");
		html.append("</tr></thead><tbody>");

		for (Alumno a : alumnos) {
			LocalDate nac = Instant.ofEpochMilli(a.getFechaNacimiento().getTime())
					.atZone(ZoneId.systemDefault()).toLocalDate();
			int edad = Period.between(nac, LocalDate.now()).getYears();
			boolean licOk = Boolean.TRUE.equals(a.getTieneLicencia()) && a.getNumeroLicencia() != null;
			String lic = licOk ? a.getNumeroLicencia().toString() : "NO";
			String licClass = licOk ? "licencia-ok" : "licencia-no";
			String cintClass = "cinturon-" + extractPrimaryBeltColor(a.getGrado().getTipoGrado());

			html.append("<tr>");
			html.append("<td class='").append(cintClass).append("'></td>");
			html.append("<td class='").append(licClass).append("'>").append(lic).append("</td>");
			html.append("<td>").append(edad).append("</td>");
			html.append("<td style='text-align:left; padding-left: 2mm;'>").append(a.getNombre()).append(" ")
					.append(a.getApellidos()).append("</td>");
			html.append("<td>").append(a.getNumeroExpediente()).append("</td>");
			html.append("</tr>");
		}

		html.append("</tbody></table>");
		html.append("</div>");

		// Attendance grid
		html.append("<div class='table-cell' style='width: 40%;'>");
		html.append("<table class='side-table'><thead><tr>");
		for (LocalDate f : fechas) {
			html.append("<th>").append(f.getDayOfMonth()).append("</th>");
		}
		html.append("</tr></thead><tbody>");
		for (int i = 0; i < totalAlumnos; i++) {
			html.append("<tr>");
			for (@SuppressWarnings("unused") LocalDate f : fechas) {
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

		html.append("</body></html>");

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
		builder.useFastMode();
		builder.withHtmlContent(html.toString(), null);
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

	@Override
	public byte[] generarInformeDeudas() {
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = productoAlumnoRepository
				.findAllUnpaidWithAlumno();

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
				Locale.of("es", "ES"));
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
					.entrySet().stream()
					.sorted((e1, e2) -> {
						String nombre1 = e1.getKey().getNombre() + " " + e1.getKey().getApellidos();
						String nombre2 = e2.getKey().getNombre() + " " + e2.getKey().getApellidos();
						return nombre1.compareTo(nombre2);
					})
					.collect(Collectors.toList());

			for (Map.Entry<Alumno, List<com.taemoi.project.entities.ProductoAlumno>> entry : sortedEntries) {
				Alumno alumno = entry.getKey();
				List<com.taemoi.project.entities.ProductoAlumno> deudas = entry.getValue();

				double totalAlumno = deudas.stream()
						.mapToDouble(pa -> pa.getPrecio() != null ? pa.getPrecio() : 0.0)
						.sum();
				totalGeneral += totalAlumno;

				html.append("<div class='alumno-section'>");
				html.append("<h3 style='margin-top: 6mm; margin-bottom: 2mm; color: #212529;'>")
						.append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append(" (Exp. ").append(alumno.getNumeroExpediente()).append(")");
				html.append(" - <span class='deuda-total'>Total: ")
						.append(String.format("%.2f", totalAlumno)).append(" €</span>");
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
	public byte[] generarInformeDeudasCSV() {
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = productoAlumnoRepository
				.findAllUnpaidWithAlumno();

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
				.entrySet().stream()
				.sorted((e1, e2) -> {
					String nombre1 = e1.getKey().getNombre() + " " + e1.getKey().getApellidos();
					String nombre2 = e2.getKey().getNombre() + " " + e2.getKey().getApellidos();
					return nombre1.compareTo(nombre2);
				})
				.collect(Collectors.toList());

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
	 * Extracts the primary color from a TipoGrado for CSS class naming.
	 * For combined grades like BLANCO_AMARILLO, returns the first color.
	 * For black belt grades (NEGRO_X_DAN or ROJO_NEGRO_X_PUM), returns "negro".
	 */
	private String extractPrimaryBeltColor(TipoGrado tipoGrado) {
		if (tipoGrado == null) {
			return "blanco";
		}

		String enumName = tipoGrado.name();

		// Handle black belt grades (NEGRO_X_DAN)
		if (enumName.startsWith("NEGRO_")) {
			return "negro";
		}

		// Handle ROJO_NEGRO grades (pre-black belt)
		if (enumName.startsWith("ROJO_NEGRO_")) {
			return "negro";
		}

		// Extract first color from combined grades (e.g., BLANCO_AMARILLO -> blanco)
		String firstPart = enumName.split("_")[0].toLowerCase();

		return firstPart;
	}
}