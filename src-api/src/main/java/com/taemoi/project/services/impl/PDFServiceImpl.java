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
import java.util.Date;
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
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.utils.DiaSemanaUtils;
import com.taemoi.project.utils.FechaUtils;

@Service
public class PDFServiceImpl implements PDFService {

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Autowired
	private GradoRepository gradoRepository;

	@Autowired
	private com.taemoi.project.repositories.ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private com.taemoi.project.repositories.ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private com.taemoi.project.services.ConvocatoriaService convocatoriaService;

	@Autowired
	private com.taemoi.project.config.GradeProgressionConfig gradeProgressionConfig;

	// Cache for the PNG logo to avoid repeated conversion
	private String logoPngBase64Cache = null;

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
		return "@page {" + "  margin: 25mm 15mm 20mm 15mm;" + "  @top-center {" + "    content: '" + pageTitle + "';"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 16pt;"
				+ "    font-weight: 700;" + "    color: #1b2b2e;" + "    text-align: center;" + "  }"
				+ "  @bottom-left {" + "    content: '" + fechaGeneracion + "';"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 9pt;"
				+ "    color: #6c757d;" + "  }" + "  @bottom-right {"
				+ "    content: 'Página ' counter(page) ' de ' counter(pages);"
				+ "    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "    font-size: 9pt;"
				+ "    color: #6c757d;" + "  }" + "}" + "* { box-sizing: border-box; }" + "body {"
				+ "  font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;" + "  color: #212529;"
				+ "  line-height: 1.6;" + "  background: #ffffff;" + "}" + ".pdf-header {" + "  text-align: center;"
				+ "  margin-bottom: 8mm;" + "  padding-bottom: 5mm;" + "  border-bottom: 3px solid #007bff;"
				+ "  page-break-inside: avoid;" + "}" + ".logo-container {" + "  text-align: center;"
				+ "  margin-bottom: 3mm;" + "}" + ".logo-container img {" + "  width: 35mm;" + "  height: auto;"
				+ "  max-height: 35mm;" + "}" + ".header-content {" + "  text-align: center;" + "}" + ".club-name {"
				+ "  font-size: 20pt;" + "  font-weight: 700;" + "  color: #1b2b2e;" + "  margin: 2mm 0;"
				+ "  text-transform: uppercase;" + "  letter-spacing: 1px;" + "}" + ".report-title {"
				+ "  font-size: 16pt;" + "  font-weight: 600;" + "  color: #007bff;" + "  margin: 2mm 0 0 0;" + "}"
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
				+ "  vertical-align: middle;" + "  font-size: 12pt;" + "  font-weight: 600;" + "  color: #1b2b2e;" + "  white-space: nowrap;" + "}"
				+ ".raya {" + "  position: absolute;" + "  top: 50%;" + "  transform: translateY(-50%);"
				+ "  height: 80%;" + "  background-color: #FFD700;" + "  z-index: 10;" + "}" + "table {"
				+ "  width: 100%;" + "  border-collapse: collapse;" + "  margin-top: 2mm;" + "  border-radius: 2mm;"
				+ "  overflow: hidden;" + "}" + "th, td {" + "  border: 1px solid #dee2e6;" + "  padding: 3mm 2mm;"
				+ "  text-align: center;" + "  font-size: 10pt;" + "}" + "th {" + "  background-color: #007bff;"
				+ "  color: #ffffff;" + "  font-weight: 600;" + "  font-size: 10pt;" + "  text-transform: uppercase;"
				+ "  letter-spacing: 0.5px;" + "}" + "tbody tr:nth-child(even) {" + "  background-color: #f8f9fa;" + "}"
				+ "tbody tr:nth-child(odd) {" + "  background-color: #ffffff;" + "}" + "tbody tr:hover {"
				+ "  background-color: #e7f3ff;" + "}"
				+ "tbody tr { page-break-inside: avoid; }"
				+ "thead { display: table-header-group; }"
				+ "table { page-break-inside: auto; }" + ".kickboxing {" + "  padding-top: 10mm;"
				+ "  border-top: 3px solid #ff4500;" + "  margin-top: 10mm;" + "}" + ".section-header {"
				+ "  background-color: #1b2b2e;" + "  color: #ffffff;" + "  padding: 5mm;" + "  border-radius: 2mm;"
				+ "  margin-bottom: 5mm;" + "  font-size: 14pt;" + "  font-weight: 700;" + "  text-align: center;"
				+ "}";
	}

	@Override
	public byte[] generarInformeAlumnosPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING), soloActivos);
	}

	@Override
	public byte[] generarInformeTaekwondoPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Collections.singletonList(Deporte.TAEKWONDO), soloActivos);
	}

	@Override
	public byte[] generarInformeKickboxingPorGrado(boolean soloActivos) {
		return generarInformePorGrado(Collections.singletonList(Deporte.KICKBOXING), soloActivos);
	}

	/**
	 * Genera el informe PDF filtrando los alumnos según la lista de deportes. Si se
	 * solicitan ambos deportes, se generan secciones separadas para cada uno.
	 */
	private byte[] generarInformePorGrado(List<Deporte> deportes, boolean soloActivos) {
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
		if (deportes.size() == 2 && deportes.contains(Deporte.TAEKWONDO) && deportes.contains(Deporte.KICKBOXING)) {
			html.append(generarCabeceraConLogo("Listado de Alumnos por Grado"));

			List<AlumnoDeporte> alumnosTaekwondo = alumnos.stream().filter(a -> a.getDeporte() == Deporte.TAEKWONDO)
					.collect(Collectors.toList());
			html.append("<div class='section-header' style='background-color: #0D47A1;'>");
			html.append("Taekwondo");
			html.append("</div>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<AlumnoDeporte> alumnosKickboxing = alumnos.stream().filter(a -> a.getDeporte() == Deporte.KICKBOXING)
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
	private String generarSeccion(List<AlumnoDeporte> alumnos) {
		StringBuilder html = new StringBuilder();
		Map<Grado, List<AlumnoDeporte>> alumnosPorGrado = alumnos.stream()
				.collect(Collectors.groupingBy(AlumnoDeporte::getGrado));

		List<Map.Entry<Grado, List<AlumnoDeporte>>> entradasOrdenadas = new ArrayList<>(alumnosPorGrado.entrySet());
		entradasOrdenadas.sort((e1, e2) -> {
			int ordinal1 = e1.getKey().getTipoGrado().ordinal();
			int ordinal2 = e2.getKey().getTipoGrado().ordinal();
			return Integer.compare(ordinal2, ordinal1);
		});

		for (Map.Entry<Grado, List<AlumnoDeporte>> entry : entradasOrdenadas) {
			Grado grado = entry.getKey();
			List<AlumnoDeporte> alumnosGrado = entry.getValue();
			alumnosGrado.sort(Comparator.comparing(ad -> {
				Alumno alumno = ad.getAlumno();
				if (alumno == null) {
					return "";
				}
				return (alumno.getNombre() + " " + alumno.getApellidos()).toLowerCase();
			}));
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
				String licencia = alumnoDeporte.getNumeroLicencia() != null ? alumnoDeporte.getNumeroLicencia().toString() : "N/A";
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
	public byte[] generarInformeLicencias(boolean soloActivos) {
		List<AlumnoDeporte> alumnos = alumnoDeporteRepository.findAll();

		// Filter by active status if requested
		if (soloActivos) {
			alumnos = alumnos.stream()
					.filter(ad -> Boolean.TRUE.equals(ad.getActivo())
							&& ad.getAlumno() != null
							&& Boolean.TRUE.equals(ad.getAlumno().getActivo()))
					.collect(Collectors.toList());
		}
		// Exclude sports without license requirements
		alumnos = alumnos.stream()
				.filter(ad -> ad.getDeporte() != Deporte.PILATES
						&& ad.getDeporte() != Deporte.DEFENSA_PERSONAL_FEMENINA)
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
		html.append(".licencias-table th:nth-child(5), .licencias-table td:nth-child(5) { width: 14%; white-space: nowrap; }");
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
			TipoGrado tipoGrado = (alumnoDeporte.getGrado() != null) ? alumnoDeporte.getGrado().getTipoGrado() : null;
			String nombreGrado = (tipoGrado != null && tipoGrado.getNombre() != null) ? tipoGrado.getNombre() : "N/A";
			html.append(generarCeldaGrado(tipoGrado, nombreGrado));
			html.append("</tr>");
		}
		html.append("</tbody>");
		html.append("</table>");
		return html.toString();
	}

	private String generarCeldaGrado(TipoGrado tipoGrado, String texto) {
		String textoSeguro = (texto == null || texto.trim().isEmpty()) ? "N/A" : texto;
		if (tipoGrado == null) {
			return "<td class='grado-cell'><div class='grado-text'>" + textoSeguro + "</div></td>";
		}
		String cinturon = generarCinturonInlineHTML(tipoGrado, 70, 16);
		return "<td class='grado-cell'><div class='grado-belt'>" + cinturon + "</div><div class='grado-text'>"
				+ textoSeguro + "</div></td>";
	}

@Override
public byte[] generarInformeInfantilesAPromocionar(boolean soloActivos) {
    List<Alumno> todosAlumnos = alumnoRepository.findAll();

    // Filter by active status if requested
    if (soloActivos) {
        todosAlumnos = todosAlumnos.stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivo()))
                .collect(Collectors.toList());
    }

    LocalDate today = LocalDate.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
            "EEEE, d 'de' MMMM 'de' yyyy",
            Locale.of("es", "ES")
    );
    String fechaGeneracion = today.format(formatter);

    // Map<Deporte, Map<PromotionGrade, List<AlumnoPromotionInfo>>>
    Map<Deporte, Map<String, List<AlumnoPromotionInfo>>> deporteGradoMap = new java.util.LinkedHashMap<>();

    for (Alumno alumno : todosAlumnos) {
        if (alumno.getFechaNacimiento() == null) {
            continue;
        }

        int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
        if (edad >= 15) {
            continue; // Not infantiles
        }

        // Check each sport the student practices
        for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {

            // Skip inactive sports or sports where student is not ready for exam
            if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
                    || !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
                continue;
            }

            // Calculate promotion grade for this sport
            String promotionGrade = getPromotionGradeForSport(alumno, alumnoDeporte);

            // Add to the map
            Deporte deporte = alumnoDeporte.getDeporte();
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
    html.append(".sport-title { color: #2c3e50; font-size: 18pt; margin-top: 8mm; margin-bottom: 4mm; border-bottom: 2px solid #3498db; padding-bottom: 2mm; }");
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
        for (Map.Entry<Deporte, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
            Deporte deporte = deporteEntry.getKey();

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

                    html.append("<td>")
                            .append(alumno.getNombre()).append(" ")
                            .append(alumno.getApellidos())
                            .append("</td>");

                    html.append("<td>")
                            .append(alumno.getNumeroExpediente())
                            .append("</td>");

                    TipoGrado gradoActualTipo = alumnoDeporte.getGrado() != null
							? alumnoDeporte.getGrado().getTipoGrado()
							: null;
					String gradoActualNombre = gradoActualTipo != null ? gradoActualTipo.getNombre() : "N/A";
					html.append(generarCeldaGrado(gradoActualTipo, gradoActualNombre));

					TipoGrado gradoPromocionTipo = gradeProgressionConfig.obtenerSiguienteGrado(
							alumnoDeporte.getDeporte(),
							FechaUtils.esMenor(alumno.getFechaNacimiento(), alumnoDeporte.getDeporte()),
							gradoActualTipo);
					html.append(generarCeldaGrado(gradoPromocionTipo, promotionGrade));

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
		// Map<Deporte, Map<PromotionGrade, List<AlumnoPromotionInfo>>>
		Map<Deporte, Map<String, List<AlumnoPromotionInfo>>> deporteGradoMap = new java.util.LinkedHashMap<>();

		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() == null) {
				continue;
			}

			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
			if (edad < 15) {
				continue; // Not adultos
			}

			// Check each sport the student practices
			for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {
				// Skip inactive sports or sports where student is not ready for exam
				if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
						|| !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
					continue;
				}

				// Calculate promotion grade for this sport
				String promotionGrade = getPromotionGradeForSport(alumno, alumnoDeporte);

				// Add to the map
				Deporte deporte = alumnoDeporte.getDeporte();
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
			for (Map.Entry<Deporte, Map<String, List<AlumnoPromotionInfo>>> deporteEntry : deporteGradoMap.entrySet()) {
				Deporte deporte = deporteEntry.getKey();
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

						TipoGrado gradoActualTipo = alumnoDeporte.getGrado() != null
								? alumnoDeporte.getGrado().getTipoGrado()
								: null;
						String gradoActualNombre = gradoActualTipo != null ? gradoActualTipo.getNombre() : "N/A";
						html.append(generarCeldaGrado(gradoActualTipo, gradoActualNombre));

						// Grado a Promocionar with belt
						TipoGrado gradoPromocionTipo = gradeProgressionConfig.obtenerSiguienteGrado(
								alumnoDeporte.getDeporte(),
								FechaUtils.esMenor(alumno.getFechaNacimiento(), alumnoDeporte.getDeporte()),
								alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado() : null);
						html.append(generarCeldaGrado(gradoPromocionTipo, promotionGrade));

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
		return generarInformePromocionPorDeporte(soloActivos, Deporte.TAEKWONDO, true);
	}

	@Override
	public byte[] generarInformeInfantilesAPromocionarKickboxing(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, Deporte.KICKBOXING, true);
	}

	@Override
	public byte[] generarInformeAdultosAPromocionarTaekwondo(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, Deporte.TAEKWONDO, false);
	}

	@Override
	public byte[] generarInformeAdultosAPromocionarKickboxing(boolean soloActivos) {
		return generarInformePromocionPorDeporte(soloActivos, Deporte.KICKBOXING, false);
	}

	/**
	 * Generic method to generate promotion reports filtered by sport
	 *
	 * @param soloActivos Filter only active students
	 * @param deporte     Sport to filter (TAEKWONDO or KICKBOXING)
	 * @param esInfantil  true for infantiles (<15), false for adultos (>=15)
	 * @return PDF bytes
	 */
	private byte[] generarInformePromocionPorDeporte(boolean soloActivos, Deporte deporte, boolean esInfantil) {
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

		int edadLimite = 15;

		for (Alumno alumno : todosAlumnos) {
			if (alumno.getFechaNacimiento() == null) {
				continue;
			}

			int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());

			// Filter by age category
			if (esInfantil && edad >= edadLimite) {
				continue; // Not infantiles
			}
			if (!esInfantil && edad < edadLimite) {
				continue; // Not adultos
			}

			// Check each sport the student practices
			for (AlumnoDeporte alumnoDeporte : alumno.getDeportes()) {
				// Skip if not the sport we're looking for
				if (alumnoDeporte.getDeporte() != deporte) {
					continue;
				}

				// Skip inactive sports or sports where student is not ready for exam
				if (!Boolean.TRUE.equals(alumnoDeporte.getActivo())
						|| !Boolean.TRUE.equals(alumnoDeporte.getAptoParaExamen())) {
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

					TipoGrado gradoActualTipo = alumnoDeporte.getGrado() != null
							? alumnoDeporte.getGrado().getTipoGrado()
							: null;
					String gradoActualNombre = gradoActualTipo != null ? gradoActualTipo.getNombre() : "N/A";
					html.append(generarCeldaGrado(gradoActualTipo, gradoActualNombre));

					// Grado a Promocionar with belt
					TipoGrado gradoPromocionTipo = gradeProgressionConfig.obtenerSiguienteGrado(
							alumnoDeporte.getDeporte(),
							FechaUtils.esMenor(alumno.getFechaNacimiento(), alumnoDeporte.getDeporte()),
							alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado() : null);
					html.append(generarCeldaGrado(gradoPromocionTipo, promotionGrade));

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

		TipoGrado gradoActual = alumnoDeporte.getGrado().getTipoGrado();
		Deporte deporte = alumnoDeporte.getDeporte();
		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		boolean esMenor = FechaUtils.esMenor(alumno.getFechaNacimiento(), deporte);

		// Get next grade using grade progression config
		TipoGrado nuevoTipo = gradeProgressionConfig.obtenerSiguienteGrado(deporte, esMenor, gradoActual);

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
	public byte[] generarListadoAsistencia(int year, int month, String grupo, Deporte deporte) throws IOException {
		return generarListadoAsistencia(year, month, Collections.singletonList(grupo), deporte);
	}

	@Override
	public byte[] generarListadoAsistencia(int year, int month, List<String> grupos, Deporte deporte) throws IOException {
		List<String> gruposFiltrados = grupos == null
				? Collections.emptyList()
				: grupos.stream()
						.filter(g -> g != null && !g.isBlank())
						.map(String::trim)
						.distinct()
						.collect(Collectors.toList());

		if (gruposFiltrados.isEmpty()) {
			throw new IllegalArgumentException("Debe seleccionar al menos un grupo");
		}

		gruposFiltrados.sort(Comparator.comparing(g -> DiaSemanaUtils.mapGrupoToDayOfWeek(g).getValue()));

		List<Deporte> deportesFiltrar = deporte != null
				? Collections.singletonList(deporte)
				: Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING, Deporte.PILATES,
						Deporte.DEFENSA_PERSONAL_FEMENINA);

		List<Deporte> deportesOrdenados = Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING, Deporte.PILATES,
				Deporte.DEFENSA_PERSONAL_FEMENINA);
		List<Deporte> deportesReporte = deportesOrdenados.stream()
				.filter(deportesFiltrar::contains)
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
		html.append(".table-wrapper { display: table; width: 100%; table-layout: fixed; }");
		html.append(".table-cell { display: table-cell; vertical-align: top; overflow: visible; }");
		html.append(".main-table, .side-table { border-collapse: collapse; width: 100%; table-layout: fixed; }");
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
		html.append(".main-table th:first-child, .main-table td:first-child { width: 8mm; font-size: 7pt; }");
		html.append(".main-table th:nth-child(2), .main-table td:nth-child(2) { width: 10.5mm; }");
		html.append(".main-table th:nth-child(3), .main-table td:nth-child(3) { width: 8mm; font-size: 7.5pt; }");
		html.append(".main-table th:nth-child(4), .main-table td:nth-child(4) { width: 7mm; font-size: 7.5pt; }");
		html.append(
				".main-table th:nth-child(5) { width: 55mm; text-align: center; padding: 1.2mm 0.6mm; font-size: 7.5pt; }");
		html.append(
				".main-table td:nth-child(5) { width: 55mm; text-align: left; padding-left: 1mm; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 7.5pt; }");
		html.append(".main-table th:nth-child(6), .main-table td:nth-child(6) { width: 10mm; font-size: 7.5pt; }");
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
				".side-table { margin-left: 3.3mm; border: 1px solid #dee2e6; table-layout: fixed; font-size: 6.5pt; max-width: 95%; }");
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

		boolean firstPage = true;

		for (String grupo : gruposFiltrados) {
			List<AlumnoDeporte> allAlumnosDeporte = alumnoDeporteRepository.findActivosByDeporteIn(deportesFiltrar).stream()
					.filter(ad -> ad.getAlumno() != null)
					.filter(ad -> ad.getAlumno().getFechaNacimiento() != null
							&& ad.getAlumno().getTurnos() != null
							&& !ad.getAlumno().getTurnos().isEmpty())
					.filter(ad -> ad.getAlumno().getTurnos().stream()
							.anyMatch(t -> t.getDiaSemana() != null && t.getDiaSemana().equalsIgnoreCase(grupo)))
					.collect(Collectors.toList());

			// Collect all turnos with their alumnos, grouped by deporte
			Map<Deporte, List<TurnoWithAlumnos>> turnosByDeporte = new java.util.HashMap<>();

			// Process each alumno to build the turno-alumno-deporte relationships
			for (AlumnoDeporte alumnoDeporte : allAlumnosDeporte) {
				Alumno alumno = alumnoDeporte.getAlumno();
				Deporte alumnoDeporteEnum = alumnoDeporte.getDeporte();
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
					if (turno.getGrupo().getDeporte() != alumnoDeporteEnum) {
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
			DayOfWeek dow = DiaSemanaUtils.mapGrupoToDayOfWeek(grupo);
			LocalDate inicio = LocalDate.of(year, month, 1);
			LocalDate finMes = inicio.with(TemporalAdjusters.lastDayOfMonth());
			List<LocalDate> fechas = new ArrayList<>();
			LocalDate actual = inicio.with(TemporalAdjusters.nextOrSame(dow));
			while (!actual.isAfter(finMes)) {
				fechas.add(actual);
				actual = actual.plusWeeks(1);
			}

			for (Deporte deporteActual : deportesReporte) {
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

					List<AlumnoDeporte> alumnos = twa.alumnos;
					// Sort alumnos by nombre
					alumnos.sort(Comparator.comparing(ad -> ad.getAlumno().getNombre()));

					int totalAlumnos = alumnos.size();
					String turnoStr = twa.turno.getHoraInicio() + " a " + twa.turno.getHoraFin();

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
					html.append("<p class='deporte-label'>").append(deporteActual.name().replace("_", " ")).append("</p>");
					html.append("<p class='info'>Total: ").append(totalAlumnos).append(" alumnos</p>");
					html.append("<p class='info'>").append(grupo.toUpperCase()).append(" - Turno de ").append(turnoStr)
							.append("</p>");
					html.append("</div>");

					// Tables
					html.append("<div class='table-wrapper'>");
					html.append("<div class='table-cell' style='width: 65%;'>");
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
						boolean tieneLicencia = Boolean.TRUE.equals(ad.getTieneLicencia()) && ad.getNumeroLicencia() != null;
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
						TipoGrado tipoGrado = ad.getGrado() != null ? ad.getGrado().getTipoGrado() : null;
						html.append(generateBeltCellHtml(tipoGrado));
						html.append("<td class='").append(licClass).append("'>").append(lic).append("</td>");
						html.append("<td>").append(edad).append("</td>");
						html.append("<td>").append(a.getNombre()).append(" ").append(a.getApellidos()).append("</td>");
						html.append("<td>").append(a.getNumeroExpediente()).append("</td>");
						html.append("</tr>");
					}

					html.append("</tbody></table>");
					html.append("</div>");

					// Attendance grid
					html.append("<div class='table-cell' style='width: 35%;'>");
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
				}
			}
		}

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


	/**
	 * Generates HTML content for the belt color cell in the attendance list. Shows
	 * dan numbers for black belts and split colors for half-color belts.
	 */
	private String generateBeltCellHtml(TipoGrado tipoGrado) {
		if (tipoGrado == null) {
			return "<td class='cinturon-blanco'></td>";
		}

		String enumName = tipoGrado.name();

		// Handle black belt grades (NEGRO_X_DAN) - show dan number
		if (enumName.startsWith("NEGRO_")) {
			String[] parts = enumName.split("_");
			if (parts.length >= 2) {
				String danNumber = parts[1]; // e.g., "1" from NEGRO_1_DAN
				return "<td class='cinturon-negro'>" + danNumber + "º</td>";
			}
			return "<td class='cinturon-negro'></td>";
		}

		// Handle ROJO_NEGRO grades (pre-black belt) - show split colors with PUM number
		if (enumName.startsWith("ROJO_NEGRO_")) {
			String[] parts = enumName.split("_");
			String pumNumber = parts.length >= 3 ? parts[2] : "";
			return "<td><div class='cinturon-split' style='position: relative;'>"
					+ "<div class='cinturon-half-superior' style='background-color: #212529;'></div>"
					+ "<div class='cinturon-half-inferior' style='background-color: #f44336;'></div>"
					+ "<div style='position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; z-index: 10;'>"
					+ "<span style='color: #ffffff; font-weight: 700; font-size: 7pt;'>" + pumNumber + "º</span>"
					+ "</div></div></td>";
		}

		// Check if it's a half-color belt (e.g., BLANCO_AMARILLO, AMARILLO_NARANJA)
		String[] parts = enumName.split("_");
		if (parts.length == 2 && !enumName.contains("DAN") && !enumName.contains("PUM")) {
			String color1 = getBeltColorHex(parts[1]);
			String color2 = getBeltColorHex(parts[0]);
			// El borde viene de la celda <td>, no necesitamos añadir uno extra
			return "<td><div class='cinturon-split'>"
					+ "<div class='cinturon-half-superior' style='background-color: " + color1 + ";'></div>"
					+ "<div class='cinturon-half-inferior' style='background-color: " + color2 + ";'></div></div></td>";
		}

		// Single color belt
		String color = parts[0].toLowerCase();
		return "<td class='cinturon-" + color + "'></td>";
	}

	/**
	 * Returns the hex color code for a belt color name.
	 */
	private String getBeltColorHex(String colorName) {
		switch (colorName) {
		case "BLANCO":
			return "#ffffff";
		case "AMARILLO":
			return "#ffeb3b";
		case "NARANJA":
			return "#ff9800";
		case "VERDE":
			return "#4caf50";
		case "AZUL":
			return "#2196f3";
		case "ROJO":
			return "#f44336";
		case "NEGRO":
			return "#212529";
		default:
			return "#cccccc";
		}
	}

	@Override
	public byte[] generarInformeDeudas(boolean soloActivos) {
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = productoAlumnoRepository
				.findAllUnpaidWithAlumno();

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
		List<com.taemoi.project.entities.ProductoAlumno> productosImpagados = productoAlumnoRepository
				.findAllUnpaidWithAlumno();

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
	public byte[] generarInformeMensualidades(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(null, soloActivos);
	}

	@Override
	public byte[] generarInformeMensualidadesTaekwondo(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(Deporte.TAEKWONDO, soloActivos);
	}

	@Override
	public byte[] generarInformeMensualidadesKickboxing(boolean soloActivos) {
		return generarInformeMensualidadesPorDeporte(Deporte.KICKBOXING, soloActivos);
	}

	/**
	 * Generates a PDF report of student monthly fees (mensualidades).
	 *
	 * @param deporteFiltro If null, generates report for all students with color
	 *                      coding by sport. If specified, filters students by that
	 *                      sport.
	 * @param soloActivos   If true, only includes active students.
	 */
	private byte[] generarInformeMensualidadesPorDeporte(Deporte deporteFiltro, boolean soloActivos) {
		List<com.taemoi.project.entities.ProductoAlumno> todasMensualidades;
		if (deporteFiltro != null) {
			todasMensualidades = productoAlumnoRepository
					.findMensualidadesYTarifasCompetidorByDeporteOrConcepto(deporteFiltro, deporteFiltro.name());
		} else {
			todasMensualidades = productoAlumnoRepository.findAllMensualidadesYTarifasCompetidorWithAlumno();
		}

		// Filter by active status if requested
		if (soloActivos) {
			todasMensualidades = todasMensualidades.stream()
					.filter(pa -> {
						Alumno alumno = pa.getAlumnoDeporte() != null
								? pa.getAlumnoDeporte().getAlumno()
								: pa.getAlumno();
						if (alumno == null || !Boolean.TRUE.equals(alumno.getActivo())) {
							return false;
						}
						if (pa.getAlumnoDeporte() != null && !Boolean.TRUE.equals(pa.getAlumnoDeporte().getActivo())) {
							return false;
						}
						return true;
					})
					.collect(Collectors.toList());
		}

		LocalDate today = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
		String fechaGeneracion = today.format(formatter);

		class MensualidadGroup {
			private final Alumno alumno;
			private final Deporte deporte;
			private final List<com.taemoi.project.entities.ProductoAlumno> mensualidades = new ArrayList<>();

			MensualidadGroup(Alumno alumno, Deporte deporte) {
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
			Deporte deporte = alumnoDeporte != null ? alumnoDeporte.getDeporte() : alumno.getDeporte();
			if (deporte == null) {
				deporte = inferDeporteFromConcepto(pa.getConcepto());
			}
			Deporte deporteFinal = deporte;
			String deporteKey = deporteFinal != null ? deporteFinal.name() : "SIN_DEPORTE";
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
		if (deporteFiltro == Deporte.TAEKWONDO) {
			titulo = "Informe de Mensualidades - Taekwondo";
			colorPrincipal = "#0D47A1"; // Blue for Taekwondo
		} else if (deporteFiltro == Deporte.KICKBOXING) {
			titulo = "Informe de Mensualidades - Kickboxing";
			colorPrincipal = "#ff4500"; // Orange for Kickboxing
		} else {
			titulo = "Informe de Mensualidades";
			colorPrincipal = "#007bff"; // Default blue
		}

		// Add header with logo
		html.append(generarCabeceraConLogo(titulo, colorPrincipal));

		int totalAlumnos = mensualidadesPorAlumno.size();
		int totalMensualidades = todasMensualidades.size();
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
			if (deporteFiltro == Deporte.TAEKWONDO) {
				tituloSeccion = "Mensualidades de Alumnos - Taekwondo (" + totalAlumnos + " alumnos)";
			} else if (deporteFiltro == Deporte.KICKBOXING) {
				tituloSeccion = "Mensualidades de Alumnos - Kickboxing (" + totalAlumnos + " alumnos)";
			} else {
				tituloSeccion = "Mensualidades de Alumnos (" + totalAlumnos + " alumnos)";
			}

			html.append("<div class='section-header' style='background-color: ").append(colorPrincipal).append(";'>");
			html.append(tituloSeccion);
			html.append("</div>");

			// Sort by student name (and sport)
			List<MensualidadGroup> sortedGroups = mensualidadesPorAlumno.values().stream()
					.sorted((g1, g2) -> {
						String nombre1 = (g1.alumno != null ? g1.alumno.getNombre() + " " + g1.alumno.getApellidos() : "")
								+ " " + (g1.deporte != null ? g1.deporte.name() : "");
						String nombre2 = (g2.alumno != null ? g2.alumno.getNombre() + " " + g2.alumno.getApellidos() : "")
								+ " " + (g2.deporte != null ? g2.deporte.name() : "");
						return nombre1.compareTo(nombre2);
					}).collect(Collectors.toList());

			for (MensualidadGroup group : sortedGroups) {
				Alumno alumno = group.alumno;
				Deporte deporte = group.deporte;
				List<com.taemoi.project.entities.ProductoAlumno> mensualidades = group.mensualidades;

				int pagas = (int) mensualidades.stream().filter(m -> Boolean.TRUE.equals(m.getPagado())).count();
				int pendientes = mensualidades.size() - pagas;
				double importePagado = mensualidades.stream().filter(m -> Boolean.TRUE.equals(m.getPagado()))
						.mapToDouble(m -> m.getPrecio() != null ? m.getPrecio() : 0.0).sum();
				double importePendiente = mensualidades.stream().filter(m -> !Boolean.TRUE.equals(m.getPagado()))
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
					if (deporte == Deporte.TAEKWONDO) {
						colorAlumno = "#0D47A1"; // Blue
						deporteNombre = " <span style='color: #0D47A1; font-weight: bold;'>[Taekwondo]</span>";
					} else if (deporte == Deporte.KICKBOXING) {
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
				html.append("<strong>Total mensualidades:</strong> ").append(mensualidades.size());
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

					String estadoClass = pagado ? "status-pagado" : "status-pendiente";
					String estadoTexto = pagado ? "PAGADO" : "PENDIENTE";
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
		List<Deporte> deportesFiltrados = Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING);
		List<AlumnoDeporte> alumnoDeportes;
		if (soloActivos) {
			alumnoDeportes = alumnoDeporteRepository.findActivosByDeporteIn(deportesFiltrados);
		} else {
			alumnoDeportes = alumnoDeporteRepository.findAll().stream()
					.filter(ad -> ad.getDeporte() != null && deportesFiltrados.contains(ad.getDeporte()))
					.collect(Collectors.toList());
		}

		// Get mensualidades AND tarifas competidor for this month
		List<com.taemoi.project.entities.ProductoAlumno> productosDelMes = productoAlumnoRepository
				.findMensualidadesYTarifasCompetidorByMes(conceptoBase, mesAnoConcepto);

		// Group products by alumno+deporte, collecting all products (mensualidad + tarifa competidor)
		Map<String, List<com.taemoi.project.entities.ProductoAlumno>> productosPorAlumnoDeporte = productosDelMes.stream()
				.filter(pa -> pa.getAlumno() != null && pa.getAlumnoDeporte() != null)
				.collect(Collectors.groupingBy(
						pa -> pa.getAlumno().getId() + "_" + pa.getAlumnoDeporte().getDeporte().name()));

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

			Comparator<AlumnoDeporte> ordenPorAlumno = Comparator
			        .comparing((AlumnoDeporte ad) -> {
			            Alumno alumno = ad.getAlumno();
			            if (alumno == null) {
			                return "";
			            }
			            String apellidos = alumno.getApellidos() == null ? "" : alumno.getApellidos();
			            String nombre = alumno.getNombre() == null ? "" : alumno.getNombre();
			            return (apellidos + " " + nombre).trim().toLowerCase(Locale.ROOT);
			        })
			        .thenComparing(ad -> {
			            if (ad.getDeporte() == Deporte.TAEKWONDO) {
			                return 0;
			            }
			            if (ad.getDeporte() == Deporte.KICKBOXING) {
			                return 1;
			            }
			            return 2;
			        });

			List<AlumnoDeporte> alumnoDeportesOrdenados = alumnoDeportes.stream()
			        .filter(ad -> ad.getAlumno() != null)
			        .sorted(ordenPorAlumno)
			        .collect(Collectors.toList());

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
			    String nombreCompleto = (alumno.getApellidos() == null ? "" : alumno.getApellidos())
			            + " " + (alumno.getNombre() == null ? "" : alumno.getNombre());
			    html.append("<td class='nombre-cell'>").append(nombreCompleto.trim()).append("</td>");

			    // DEPORTE
			    html.append("<td class='deporte-cell'>");
			    for (AlumnoDeporte ad : deportesAlumno) {
			        String deporteClass = "";
			        String deporteNombre = "";
			        if (ad.getDeporte() == Deporte.TAEKWONDO) {
			            deporteClass = "deporte-taekwondo";
			            deporteNombre = "Taekwondo";
			        } else if (ad.getDeporte() == Deporte.KICKBOXING) {
			            deporteClass = "deporte-kickboxing";
			            deporteNombre = "Kickboxing";
			        } else if (ad.getDeporte() != null) {
			            deporteNombre = getDeporteNombre(ad.getDeporte());
			        }
			        html.append("<div class='line-item " + deporteClass + "'>")
			                .append(deporteNombre.isEmpty() ? "-" : deporteNombre)
			                .append("</div>");
			    }
			    html.append("</td>");

			    // DESCUENTO
			    html.append("<td>");
			    for (AlumnoDeporte ad : deportesAlumno) {
			        String tipoTarifa = ad.getTipoTarifa() != null
			                ? ad.getTipoTarifa().toString().replace("_", " ")
			                : "-";
			        html.append("<div class='line-item'>").append(tipoTarifa).append("</div>");
			    }
			    html.append("</td>");

			    // CUANTIA
			    html.append("<td>");
			    for (AlumnoDeporte ad : deportesAlumno) {
			        String mapKey = alumno.getId() + "_" + ad.getDeporte().name();
			        List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = productosPorAlumnoDeporte.get(mapKey);
			        String cuantia = "-";
			        if (productosAlumno != null && !productosAlumno.isEmpty()) {
			            double totalCuantia = productosAlumno.stream()
			                    .filter(p -> p.getPrecio() != null)
			                    .mapToDouble(com.taemoi.project.entities.ProductoAlumno::getPrecio)
			                    .sum();
			            cuantia = String.format("%.2f EUR", totalCuantia);
			        } else if (ad.getCuantiaTarifa() != null) {
			            cuantia = String.format("%.2f EUR", ad.getCuantiaTarifa());
			        }
			        html.append("<div class='line-item'>").append(cuantia).append("</div>");
			    }
			    html.append("</td>");

			    // COMP
			    html.append("<td class='comp-cell'>");
			    for (AlumnoDeporte ad : deportesAlumno) {
			        String mapKey = alumno.getId() + "_" + ad.getDeporte().name();
			        List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = productosPorAlumnoDeporte.get(mapKey);
			        String comp = "-";
			        if (productosAlumno != null && !productosAlumno.isEmpty()) {
			            double competidorCuantia = productosAlumno.stream()
			                    .filter(p -> {
			                        String concepto = p.getConcepto();
			                        return concepto != null
			                                && concepto.toUpperCase(Locale.ROOT).startsWith("TARIFA COMPETIDOR");
			                    })
			                    .filter(p -> p.getPrecio() != null)
			                    .mapToDouble(com.taemoi.project.entities.ProductoAlumno::getPrecio)
			                    .sum();
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
			        TipoGrado tipoGrado = ad.getGrado() != null ? ad.getGrado().getTipoGrado() : null;
			        html.append("<div class='line-item'>");
			        if (tipoGrado != null) {
			            html.append(generarCinturonInlineHTML(tipoGrado, 60, 12));
			        } else {
			            html.append("-");
			        }
			        html.append("</div>");
			    }
			    html.append("</td>");

			    // FECHA DE PAGO
			    html.append("<td>");
			    for (AlumnoDeporte ad : deportesAlumno) {
			        String mapKey = alumno.getId() + "_" + ad.getDeporte().name();
			        List<com.taemoi.project.entities.ProductoAlumno> productosAlumno = productosPorAlumnoDeporte.get(mapKey);
			        String fechaPago = "";
			        if (productosAlumno != null && !productosAlumno.isEmpty()) {
			            boolean todosPagados = productosAlumno.stream()
			                    .allMatch(p -> Boolean.TRUE.equals(p.getPagado()));
			            if (todosPagados) {
			                Date ultimaFechaPago = productosAlumno.stream()
			                        .filter(p -> p.getFechaPago() != null)
			                        .map(com.taemoi.project.entities.ProductoAlumno::getFechaPago)
			                        .max(Date::compareTo)
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
	public byte[] generarInformeCompetidores() {
		// Get all active competitors for Taekwondo and Kickboxing
		List<AlumnoDeporte> competidores = alumnoDeporteRepository.findCompetidoresActivosByDeporteIn(
				Arrays.asList(Deporte.TAEKWONDO, Deporte.KICKBOXING));

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
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		// Header with logo
		html.append(generarCabeceraConLogo("Listado de Competidores"));

		// Separate by sport
		List<AlumnoDeporte> competidoresTaekwondo = competidores.stream()
				.filter(ad -> ad.getDeporte() == Deporte.TAEKWONDO)
				.collect(Collectors.toList());

		List<AlumnoDeporte> competidoresKickboxing = competidores.stream()
				.filter(ad -> ad.getDeporte() == Deporte.KICKBOXING)
				.collect(Collectors.toList());

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
	 * Genera el HTML para una sección de competidores, agrupando por grado.
	 */
	private String generarSeccionCompetidores(List<AlumnoDeporte> competidores) {
		StringBuilder html = new StringBuilder();

		// Group by grade
		Map<Grado, List<AlumnoDeporte>> competidoresPorGrado = competidores.stream()
				.filter(ad -> ad.getGrado() != null)
				.collect(Collectors.groupingBy(AlumnoDeporte::getGrado));

		// Sort grades by ordinal (highest first)
		List<Map.Entry<Grado, List<AlumnoDeporte>>> entradasOrdenadas = new ArrayList<>(competidoresPorGrado.entrySet());
		entradasOrdenadas.sort((e1, e2) -> {
			int ordinal1 = e1.getKey().getTipoGrado().ordinal();
			int ordinal2 = e2.getKey().getTipoGrado().ordinal();
			return Integer.compare(ordinal2, ordinal1);
		});

		for (Map.Entry<Grado, List<AlumnoDeporte>> entry : entradasOrdenadas) {
			Grado grado = entry.getKey();
			List<AlumnoDeporte> competidoresGrado = entry.getValue();
			// Sort by name
			competidoresGrado.sort(Comparator.comparing(ad ->
					(ad.getAlumno().getNombre() + " " + ad.getAlumno().getApellidos()).toLowerCase()));
			TipoGrado tipo = grado.getTipoGrado();

			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");
			html.append("<div class='izquierda'>");

			// Draw belt based on grade type
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
				html.append("<div class='superior' style='background-color: ").append(colorSuperior).append("; z-index: 1;'></div>");
				html.append("<div class='inferior' style='background-color: ").append(colorInferior).append("; z-index: 1;'></div>");
				int stripeWidth = 6;
				int gap = 2;
				int initialMargin = 6;
				for (int i = 0; i < stripeCount; i++) {
					int rightOffset = initialMargin + i * (stripeWidth + gap);
					html.append("<div class='raya' style='right:").append(rightOffset).append("px; width:").append(stripeWidth).append("px; z-index: 2;'></div>");
				}
				html.append("</div>");
			} else if (tipo.name().contains("DAN") || (tipo.name().contains("PUM") && !tipo.name().contains("ROJO_NEGRO"))) {
				html.append("<div class='cinturon' style='background-color: ").append(obtenerColorCinturon(tipo)).append(";'>");
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
						html.append("<div class='raya' style='right:").append(rightOffset).append("px; width:").append(stripeWidth).append("px;'></div>");
					}
				} catch (Exception e) {
				}
				html.append("</div>");
			} else if (tipo.name().contains("_")) {
				String[] parts = tipo.name().split("_");
				String colorSuperior = obtenerColorPorNombre(parts[1]);
				String colorInferior = obtenerColorPorNombre(parts[0]);
				html.append("<div class='cinturon doble'>");
				html.append("<div class='superior' style='background-color: ").append(colorSuperior).append(";'></div>");
				html.append("<div class='inferior' style='background-color: ").append(colorInferior).append(";'></div>");
				html.append("</div>");
			} else {
				String cinturonStyle = obtenerEstiloCinturon(tipo);
				html.append("<div class='cinturon' style='").append(cinturonStyle).append("'></div>");
			}

			html.append("<span class='grado-nombre'>").append(tipo.getNombre()).append("</span>");
			html.append("</div>");
			html.append("<div class='derecha'>");
			html.append("Competidores: ").append(competidoresGrado.size());
			html.append("</div>");
			html.append("</div>");

			// Table with competitor-specific columns
			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>Categoría</th>");
			html.append("<th>Peso</th>");
			html.append("<th>Fecha Peso</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");

			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

			for (AlumnoDeporte ad : competidoresGrado) {
				Alumno alumno = ad.getAlumno();
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");

				// Categoría
				String categoria = ad.getCategoria() != null ? ad.getCategoria().getNombre() : "";
				html.append("<td>").append(categoria).append("</td>");

				// Peso
				String peso = ad.getPeso() != null ? String.format("%.1f kg", ad.getPeso()) : "";
				html.append("<td>").append(peso).append("</td>");

				// Fecha Peso
				String fechaPeso = "";
				if (ad.getFechaPeso() != null) {
					LocalDate fecha = Instant.ofEpochMilli(ad.getFechaPeso().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
					fechaPeso = fecha.format(dateFormatter);
				}
				html.append("<td>").append(fechaPeso).append("</td>");

				html.append("</tr>");
			}

			html.append("</tbody>");
			html.append("</table>");
			html.append("</div>");
		}

		// Handle competitors without grade
		List<AlumnoDeporte> sinGrado = competidores.stream()
				.filter(ad -> ad.getGrado() == null)
				.sorted(Comparator.comparing(ad -> (ad.getAlumno().getNombre() + " " + ad.getAlumno().getApellidos()).toLowerCase()))
				.collect(Collectors.toList());

		if (!sinGrado.isEmpty()) {
			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");
			html.append("<div class='izquierda'>");
			html.append("<div class='cinturon' style='background-color: #CCCCCC;'></div>");
			html.append("<span class='grado-nombre'>Sin Grado</span>");
			html.append("</div>");
			html.append("<div class='derecha'>");
			html.append("Competidores: ").append(sinGrado.size());
			html.append("</div>");
			html.append("</div>");

			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>Categoría</th>");
			html.append("<th>Peso</th>");
			html.append("<th>Fecha Peso</th>");
			html.append("</tr></thead>");
			html.append("<tbody>");

			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

			for (AlumnoDeporte ad : sinGrado) {
				Alumno alumno = ad.getAlumno();
				html.append("<tr>");
				html.append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos()).append("</td>");

				String categoria = ad.getCategoria() != null ? ad.getCategoria().getNombre() : "";
				html.append("<td>").append(categoria).append("</td>");

				String peso = ad.getPeso() != null ? String.format("%.1f kg", ad.getPeso()) : "";
				html.append("<td>").append(peso).append("</td>");

				String fechaPeso = "";
				if (ad.getFechaPeso() != null) {
					LocalDate fecha = Instant.ofEpochMilli(ad.getFechaPeso().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
					fechaPeso = fecha.format(dateFormatter);
				}
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
			TipoGrado g1 = e1.getValue().get(0).getGradoActual();
			TipoGrado g2 = e2.getValue().get(0).getGradoActual();
			return Integer.compare(g2.ordinal(), g1.ordinal());
		});

		for (Map.Entry<String, List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO>> entry : sortedGroups) {
			List<com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO> alumnos = entry.getValue();
			if (alumnos.isEmpty())
				continue;

			TipoGrado gradoActual = alumnos.get(0).getGradoActual();
			TipoGrado gradoSiguiente = alumnos.get(0).getGradoSiguiente();

			// Sort students by name
			alumnos.sort(Comparator.comparing(a -> a.getNombreCompleto().toLowerCase()));

			// Grade transition header using table for layout
			html.append("<div class='grade-transition-box'>");
			html.append("<table class='grade-transition-table'>");
			html.append("<tr>");

			// Left: DE [GRADE]
			html.append("<td class='grade-from'>");
			html.append("<span class='belt-with-text'>");
			html.append(generarCinturonInlineHTML(gradoActual, 50, 15));
			html.append("<span class='belt-text'>DE ").append(gradoActual.getNombre().toUpperCase()).append("</span>");
			html.append("</span>");
			html.append("</td>");

			// Center: Student count
			html.append("<td class='student-count' style='width: 30%;'>");
			html.append(alumnos.size()).append(" Alumnos");
			html.append("</td>");

			// Right: A [GRADE]
			html.append("<td class='grade-to'>");
			html.append("<span class='belt-with-text'>");
			html.append("<span class='belt-text' style='margin-right: 3mm;'>A ")
					.append(gradoSiguiente.getNombre().toUpperCase()).append("</span>");
			html.append(generarCinturonInlineHTML(gradoSiguiente, 50, 15));
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

	private String generarCinturonInlineHTML(TipoGrado tipo, int width, int height) {
		if (tipo == null) {
			return "";
		}

		StringBuilder html = new StringBuilder();

		if (tipo.name().contains("ROJO_NEGRO")) {
			String[] parts = tipo.name().split("_");
			String colorInferior = obtenerColorPorNombre(parts[0]);
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			int stripeCount = 0;
			try {
				stripeCount = Integer.parseInt(parts[2]);
			} catch (Exception e) {
			}

			html.append("<div style='position: relative; width: ").append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			html.append("<div style='background-color: ").append(colorSuperior)
					.append("; position: absolute; top: 0; left: 0; right: 0; height: 50%; z-index: 1;'></div>");
			html.append("<div style='background-color: ").append(colorInferior)
					.append("; position: absolute; bottom: 0; left: 0; right: 0; height: 50%; z-index: 1;'></div>");

			int stripeWidth = Math.max(2, width / 15);
			int gap = 1;
			int initialMargin = 3;
			for (int i = 0; i < stripeCount; i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div style='position: absolute; right:").append(rightOffset).append("px; width:")
						.append(stripeWidth)
						.append("px; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; z-index: 10;'></div>");
			}
			html.append("</div>");
		} else if (tipo.name().contains("DAN")
				|| (tipo.name().contains("PUM") && !tipo.name().contains("ROJO_NEGRO"))) {
			html.append("<div style='background-color: ").append(obtenerColorCinturon(tipo)).append("; width: ")
					.append(width).append("px; height: ").append(height)
					.append("px; position: relative; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");

			int stripeCount = 0;
			if (tipo.name().contains("DAN")) {
				String[] parts = tipo.name().split("_");
				try {
					stripeCount = Integer.parseInt(parts[1]);
				} catch (Exception e) {
				}
			}

			int stripeWidth = Math.max(2, width / 15);
			int gap = 1;
			int initialMargin = 3;
			for (int i = 0; i < stripeCount; i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div style='position: absolute; right:").append(rightOffset).append("px; width:")
						.append(stripeWidth)
						.append("px; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; z-index: 10;'></div>");
			}
			html.append("</div>");
		} else if (tipo.name().contains("_")) {
			String[] parts = tipo.name().split("_");
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			String colorInferior = obtenerColorPorNombre(parts[0]);
			html.append("<div style='position: relative; width: ").append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			html.append("<div style='background-color: ").append(colorSuperior)
					.append("; position: absolute; top: 0; left: 0; right: 0; height: 50%;'></div>");
			html.append("<div style='background-color: ").append(colorInferior)
					.append("; position: absolute; bottom: 0; left: 0; right: 0; height: 50%;'></div>");
			html.append("</div>");
		} else {
			html.append("<div style='background-color: ").append(obtenerColorCinturon(tipo)).append("; width: ")
					.append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'></div>");
		}

		return html.toString();
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
	private String getDeporteNombre(Deporte deporte) {
		if (deporte == null) {
			return "N/A";
		}
		switch (deporte) {
		case TAEKWONDO:
			return "Taekwondo";
		case KICKBOXING:
			return "Kickboxing";
		case PILATES:
			return "Pilates";
		case DEFENSA_PERSONAL_FEMENINA:
			return "D.P. Fem.";
		default:
			return deporte.name();
		}
	}

	private Deporte inferDeporteFromConcepto(String concepto) {
		if (concepto == null || concepto.isBlank()) {
			return null;
		}
		String upper = concepto.toUpperCase(Locale.ROOT);
		if (upper.contains("KICKBOXING")) {
			return Deporte.KICKBOXING;
		}
		if (upper.contains("TAEKWONDO")) {
			return Deporte.TAEKWONDO;
		}
		if (upper.contains("PILATES")) {
			return Deporte.PILATES;
		}
		if (upper.contains("DEFENSA")) {
			return Deporte.DEFENSA_PERSONAL_FEMENINA;
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
