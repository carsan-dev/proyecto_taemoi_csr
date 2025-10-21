package com.taemoi.project.servicios.impl;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.PDFService;
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
				new Locale("es", "ES"));
		String fechaGeneracion = now.format(formatter);

		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append("@page {");
		html.append("  margin: 30mm 10mm 30mm 10mm;");
		html.append("  @top-center {");
		html.append("    content: 'Listado de Alumnos por Grado';");
		html.append("    font-size: 24px;");
		html.append("    font-weight: bold;");
		html.append("  	 font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-left {");
		html.append("    content: '" + fechaGeneracion + "';");
		html.append("    font-size: 12px;");
		html.append("  	 font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-right {");
		html.append("    content: 'Página ' counter(page) ' de ' counter(pages);");
		html.append("    font-size: 12px;");
		html.append("  	 font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("}");
		html.append("body { font-family: Arial, sans-serif; }");
		html.append(".grupo { margin-top: 20px; margin-bottom: 20px; page-break-inside: avoid; }");
		html.append(
				".encabezado-grupo { display: table; table-layout: fixed; background: #f0f0f0; padding: 10px 5px; border: 1px solid #ccc; width: 100%; }");
		html.append(".celda { display: table-cell; vertical-align: middle; }");
		html.append(".izquierda { padding-left: 10px; width: 100%; }");
		html.append(".derecha { padding-right: 10px; text-align: right; width: 50%; }");
		html.append(
				".cinturon { display: inline-block; vertical-align: middle; width: 100px; height: 20px; margin-right: 10px; border: 1px solid #000; position: relative; }");
		html.append(".cinturon.doble .superior, .cinturon.doble .inferior { width: 100%; height: 50%; }");
		html.append(".cinturon.doble .superior { position: absolute; top: 0; }");
		html.append(".cinturon.doble .inferior { position: absolute; bottom: 0; }");
		html.append(".grado-nombre { display: inline-block; vertical-align: middle; margin-left: 10px;}");
		html.append(
				".raya { position: absolute; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; }");
		html.append("table { width: 100%; border-collapse: collapse; }");
		html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
		html.append("th { background-color: #666; color: #fff; }");
		html.append(".kickboxing { padding-top: 30px; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		if (deportes.size() == 2 && deportes.contains(Deporte.TAEKWONDO) && deportes.contains(Deporte.KICKBOXING)) {

			List<Alumno> alumnosTaekwondo = alumnos.stream().filter(a -> a.getDeporte() == Deporte.TAEKWONDO)
					.collect(Collectors.toList());
			html.append("<h2>Taekwondo</h2>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<Alumno> alumnosKickboxing = alumnos.stream().filter(a -> a.getDeporte() == Deporte.KICKBOXING)
					.collect(Collectors.toList());
			html.append("<h2 class='kickboxing'>Kickboxing</h2>");
			html.append(generarSeccion(alumnosKickboxing));

		} else {
			String titulo = (deportes.get(0) == Deporte.TAEKWONDO) ? "Informe de Taekwondo por Grado"
					: "Informe de Kickboxing por Grado";
			html.append("<h1>").append(titulo).append("</h1>");
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

			html.append("<div class='celda izquierda'>");
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

			html.append("<span class='grado-nombre'><strong>").append(tipo.getNombre()).append("</strong></span>");
			html.append("</div>");
			html.append("<div class='celda derecha'>");
			html.append("<span class='total'>Alumnos: ").append(alumnosGrado.size()).append("</span>");
			html.append("</div>");

			html.append("<div style='clear: both;'></div>");

			html.append("</div>");

			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>Nº Expediente</th>");
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
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append("@page {");
		html.append("  margin: 30mm 10mm 30mm 10mm;");
		html.append("  @top-center {");
		html.append("    content: 'Informe de Licencias de Alumnos';");
		html.append("    font-size: 24px;");
		html.append("    font-weight: bold;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-left {");
		html.append("    content: 'Fecha: " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "';");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-right {");
		html.append("    content: 'Página ' counter(page) ' de ' counter(pages);");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("}");
		html.append("body { font-family: Arial, sans-serif; }");
		html.append("h2 { margin-top: 20px; }");
		html.append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }");
		html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
		html.append("th { background-color: #666; color: #fff; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		html.append("<h2>Licencias en Vigor</h2>");
		html.append(generarTablaAlumnos(licenciasVigor));

		html.append("<h2>Licencias Caducadas</h2>");
		html.append(generarTablaAlumnos(licenciasCaducadas));

		html.append("<h2>Sin Licencia</h2>");
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
		html.append("<th>Nº Expediente</th>");
		html.append("<th>Nº Licencia</th>");
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
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append("@page {");
		html.append("  margin: 30mm 10mm 30mm 10mm;");
		html.append("  @top-center {");
		html.append("    content: 'Listado de Alumnos Infantiles a Promocionar';");
		html.append("    font-size: 24px;");
		html.append("    font-weight: bold;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-left {");
		html.append("    content: 'Fecha: " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "';");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-right {");
		html.append("    content: 'Página ' counter(page) ' de ' counter(pages);");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("}");
		html.append("body { font-family: Arial, sans-serif; }");
		html.append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }");
		html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
		html.append("th { background-color: #666; color: #fff; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		for (Map.Entry<String, List<Alumno>> entry : alumnosAgrupados.entrySet()) {
			String promotionGrade = entry.getKey();
			html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>");
			html.append("<table>");
			html.append("<thead><tr>");
			html.append("<th>Nombre y Apellidos</th>");
			html.append("<th>Nº Expediente</th>");
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
				html.append("<td>").append(alumno.getTieneDerechoExamen() ? "Sí" : "No").append("</td>");
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
	public byte[] generarInformeAdultosAPromocionar() {
		List<Alumno> todosAlumnos = alumnoRepository.findAll();
		LocalDate today = LocalDate.now();

		// Filtramos los adultos aptos
		List<Alumno> alumnosAdultos = todosAlumnos.stream()
				.filter(a -> a.getFechaNacimiento() != null && Boolean.TRUE.equals(a.getAptoParaExamen()))
				.filter(a -> FechaUtils.calcularEdad(a.getFechaNacimiento()) >= 15).collect(Collectors.toList());

		// Agrupamos por grado de promoción
		Map<String, List<Alumno>> alumnosAgrupados = alumnosAdultos.stream()
				.collect(Collectors.groupingBy(this::getPromotionGrade));

		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<style>");
		html.append("@page {");
		html.append("  margin: 30mm 10mm 30mm 10mm;");
		html.append("  @top-center {");
		html.append("    content: 'Listado de Alumnos Adultos a Promocionar';");
		html.append("    font-size: 24px;");
		html.append("    font-weight: bold;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-left {");
		html.append("    content: 'Fecha: " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "';");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("  @bottom-right {");
		html.append("    content: 'Página ' counter(page) ' de ' counter(pages);");
		html.append("    font-size: 12px;");
		html.append("    font-family: Arial, sans-serif;");
		html.append("  }");
		html.append("}");
		html.append("body { font-family: Arial, sans-serif; }");
		html.append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }");
		html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
		html.append("th { background-color: #666; color: #fff; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		for (Map.Entry<String, List<Alumno>> entry : alumnosAgrupados.entrySet()) {
			String promotionGrade = entry.getKey();
			html.append("<h2>Promocionan a ").append(promotionGrade).append("</h2>").append("<table>")
					.append("<thead><tr>").append("<th>Nombre y Apellidos</th>").append("<th>Nº Expediente</th>")
					.append("<th>Licencia Federativa</th>").append("<th>Fecha Licencia</th>").append("<th>Edad</th>")
					.append("<th>Derecho a Examen</th>").append("</tr></thead>").append("<tbody>");
			for (Alumno alumno : entry.getValue()) {
				int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
				html.append("<tr>").append("<td>").append(alumno.getNombre()).append(" ").append(alumno.getApellidos())
						.append("</td>").append("<td>").append(alumno.getNumeroExpediente()).append("</td>")
						.append("<td>").append(alumno.getNumeroLicencia() != null ? alumno.getNumeroLicencia() : "N/A")
						.append("</td>").append("<td>")
						.append(alumno.getFechaLicencia() != null ? alumno.getFechaLicencia().toString() : "N/A")
						.append("</td>").append("<td>").append(edad).append("</td>").append("<td>")
						.append(alumno.getTieneDerechoExamen() ? "Sí" : "No").append("</td>").append("</tr>");
			}
			html.append("</tbody></table>");
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

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>").append("@page{margin:15mm;}")
				.append(".header-titles{margin:8mm 0;text-align:center;font-family:Arial,sans-serif;}")
				.append(".header-titles .main{font-size:24pt;color:red;font-weight:bold;margin:0;}")
				.append(".header-titles .sub{font-size:18pt;color:teal;margin:0 0 4mm;}")
				.append("h2{font-family:Arial,sans-serif;text-align:center;font-size:16pt;margin:4mm 0;font-weight:bold;}")
				.append("p.total{font-family:Arial,sans-serif;text-align:center;font-size:12pt;margin:2mm 0;font-weight:bold;}")
				.append(".main-table, .side-table{border-collapse:collapse;font-family:Arial,sans-serif;}")
				.append(".main-table th, .main-table td{border:1px solid #000;padding:4px;text-align:center;}")
				.append(".main-table th{background:#ffd080;font-weight:bold;}")
				.append(".main-table th:first-child, .main-table td:first-child{width:20mm;}")
				.append(".cinturon-blanco{background:#fff;}").append(".cinturon-amarillo{background:yellow;}")
				.append(".cinturon-verde{background:green;}").append(".cinturon-azul{background:blue;}")
				.append(".cinturon-rojo{background:red;}").append(".cinturon-negro{background:black;}")
				.append(".licencia-no{color:red;font-weight:bold;}").append(".side-table{table-layout:fixed;}")
				.append(".side-table th, .side-table td{border:1px solid #000;width:14mm;height:12mm;padding:2px;text-align:center;}")
				.append("</style></head><body>").append("<div class='header-titles'>")
				.append("<p class='main'>CLUB MOI'S KIM DO</p>").append("<p class='sub'>Tae Kwon Do</p>")
				.append("</div>").append("<h2>LISTADO ASISTENCIA ")
				.append(Month.of(month).getDisplayName(TextStyle.FULL, new Locale("es")).toUpperCase()).append(" - ")
				.append(year).append("</h2>").append("<p class='total'>TOTAL : ").append(totalAlumnos)
				.append(" ALUMNOS</p>").append("<p class='total' style='font-size:12pt;'>").append(grupo.toUpperCase())
				.append(" - TURNO DE ").append(turno.replace("–", " a ")).append("</p>")
				.append("<table style='width:100%;'><tr>").append("<td style='vertical-align:top;'>")
				.append("<table class='main-table' style='width:100%;'>").append("<thead><tr>")
				.append("<th></th><th>LIC. FED</th><th>EDAD</th><th>NOMBRE</th><th>Nº EXP.</th>")
				.append("</tr></thead><tbody>");
		for (Alumno a : alumnos) {
			LocalDate nac = Instant.ofEpochMilli(a.getFechaNacimiento().getTime()).atZone(ZoneId.systemDefault())
					.toLocalDate();
			int edad = Period.between(nac, LocalDate.now()).getYears();
			boolean licOk = Boolean.TRUE.equals(a.getTieneLicencia()) && a.getNumeroLicencia() != null;
			String lic = licOk ? a.getNumeroLicencia().toString() : "NO";
			String licClass = licOk ? "" : "licencia-no";
			String cintClass = "cinturon-" + extractPrimaryBeltColor(a.getGrado().getTipoGrado());

			html.append("<tr>").append("<td class='").append(cintClass).append("'></td>").append("<td class='")
					.append(licClass).append("'>").append(lic).append("</td>").append("<td>").append(edad)
					.append("</td>").append("<td style='text-align:left;'>").append(a.getNombre()).append(" ")
					.append(a.getApellidos()).append("</td>").append("<td>").append(a.getNumeroExpediente())
					.append("</td>").append("</tr>");
		}
		html.append("</tbody></table></td>").append("<td style='vertical-align:top;'>")
				.append("<table class='side-table'><thead><tr>");
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
		html.append("</tr></tfoot></table></td>").append("</tr></table>").append("</body></html>");

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