package com.taemoi.project.servicios.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.servicios.PDFService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PDFServiceImpl implements PDFService {

	@Autowired
	private AlumnoRepository alumnoRepository;

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

		StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<head>");
		html.append("<meta charset='UTF-8' />");
		html.append("<title>Listado de Alumnos por Grado</title>");
		html.append("<style>");
		html.append("body { font-family: Arial, sans-serif; margin: 30px; }");
		html.append("h1, h2 { text-align: center; color: #333; }");
		html.append(".grupo { margin-top: 20px; margin-bottom: 20px; }");
		html.append(
				".encabezado-grupo { display: flex; align-items: center; background: #f0f0f0; padding: 10px; border: 1px solid #ccc; }");
		html.append(
				".cinturon { width: 80px; height: 20px; margin-right: 10px; border: 1px solid #000; position: relative; }");
		html.append(".raya { position: absolute; top: 0; height: 100%; background-color: #FFD700; }");
		html.append("table { width: 100%; border-collapse: collapse; margin-top: 5px; }");
		html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }");
		html.append("th { background-color: #666; color: #fff; }");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");

		if (deportes.size() == 2 && deportes.contains(Deporte.TAEKWONDO) && deportes.contains(Deporte.KICKBOXING)) {

			html.append("<h1>Informe General de Alumnos por Grado</h1>");

			List<Alumno> alumnosTaekwondo = alumnos.stream().filter(a -> a.getDeporte() == Deporte.TAEKWONDO)
					.collect(Collectors.toList());
			html.append("<h2>Taekwondo</h2>");
			html.append(generarSeccion(alumnosTaekwondo));

			List<Alumno> alumnosKickboxing = alumnos.stream().filter(a -> a.getDeporte() == Deporte.KICKBOXING)
					.collect(Collectors.toList());
			html.append("<h2>Kickboxing</h2>");
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

			String baseColor = obtenerColorCinturon(tipo);
			html.append("<div class='grupo'>");
			html.append("<div class='encabezado-grupo'>");

			if (tipo.name().contains("DAN")) {
				html.append("<div class='cinturon' style='background-color: " + baseColor + ";'>");
				try {
					String[] parts = tipo.name().split("_");
					int danCount = Integer.parseInt(parts[1]);
					int stripeWidth = 8;
					int gap = 2;
					for (int i = 0; i < danCount; i++) {
						int rightOffset = i * (stripeWidth + gap);
						html.append("<div class='raya' style='right: " + rightOffset + "px; width: " + stripeWidth
								+ "px;'></div>");
					}
				} catch (Exception e) {
				}
				html.append("</div>");
			} else {
				String cinturonStyle = obtenerEstiloCinturon(tipo);
				html.append("<div class='cinturon' style='" + cinturonStyle + "'></div>");
			}

			html.append("<div>Grado: ").append(tipo.getNombre()).append(" (Total: ").append(alumnosGrado.size())
					.append(")</div>");
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
}