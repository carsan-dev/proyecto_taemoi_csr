package com.taemoi.project.services.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.TesoreriaService;

@Service
public class TesoreriaServiceImpl implements TesoreriaService {

	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 200;
	private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Pattern ANO_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
	private static final String[] MESES_ES = {
			"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
			"JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
	};

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Override
	public TesoreriaResumenDTO obtenerResumen(Integer mes, Integer ano, String deporte) {
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String anoTexto = filtro.ano != null ? String.valueOf(filtro.ano) : null;
		String mesNombre = filtro.mes != null ? MESES_ES[filtro.mes - 1] : null;

		long totalMovimientos = toLong(productoAlumnoRepository.contarMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				null,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));
		long totalPagados = toLong(productoAlumnoRepository.contarMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				Boolean.TRUE,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));
		long totalPendientes = toLong(productoAlumnoRepository.contarMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				Boolean.FALSE,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));
		double importeTotal = toDouble(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				null,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));
		double importePagado = toDouble(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				Boolean.TRUE,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));
		double importePendiente = toDouble(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				Boolean.FALSE,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));

		long alumnosConPendientes = toLong(productoAlumnoRepository.contarAlumnosConPendientesTesoreria(
				filtro.deporte,
				filtro.deporteNombre,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre));

		TesoreriaResumenDTO resumen = new TesoreriaResumenDTO();
		resumen.setMes(filtro.mes);
		resumen.setAno(filtro.ano);
		resumen.setDeporte(filtro.deporte != null ? filtro.deporte.name() : "TODOS");
		resumen.setTotalMovimientos(totalMovimientos);
		resumen.setTotalPagados(totalPagados);
		resumen.setTotalPendientes(totalPendientes);
		resumen.setImporteTotal(importeTotal);
		resumen.setImportePagado(importePagado);
		resumen.setImportePendiente(importePendiente);
		resumen.setAlumnosConPendientes(alumnosConPendientes);
		return resumen;
	}

	@Override
	public Page<TesoreriaMovimientoDTO> obtenerMovimientos(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Integer page,
			Integer size) {
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		int pageNumber = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);
		String textoNormalizado = normalizarTextoBusqueda(texto);

		return obtenerPaginaMovimientosFiltrados(filtro, pagado, textoNormalizado, pageable)
				.map(this::convertirAMovimientoDTO);
	}

	@Override
	public List<Integer> obtenerAniosDisponibles() {
		TreeSet<Integer> anios = new TreeSet<>();
		anios.addAll(productoAlumnoRepository.findAniosDistintosTesoreriaConFecha());

		List<String> conceptosSinFecha = productoAlumnoRepository.findConceptosTesoreriaSinFecha();
		for (String concepto : conceptosSinFecha) {
			PeriodoMovimiento periodo = extraerPeriodoDesdeConcepto(concepto);
			Integer ano = periodo != null ? periodo.ano : null;
			if (ano != null) {
				anios.add(ano);
			}
		}

		if (anios.isEmpty()) {
			anios.add(LocalDate.now().getYear());
		}

		return new ArrayList<>(anios);
	}

	private long toLong(Object value) {
		if (value == null) {
			return 0L;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return 0L;
	}

	private double toDouble(Object value) {
		if (value == null) {
			return 0.0;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return 0.0;
	}

	@Override
	public byte[] exportarMovimientosCSV(Integer mes, Integer ano, String deporte, Boolean pagado, String texto) {
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String textoNormalizado = normalizarTextoBusqueda(texto);
		List<TesoreriaMovimientoDTO> movimientos = obtenerMovimientosFiltrados(filtro, pagado, textoNormalizado).stream()
				.map(this::convertirAMovimientoDTO)
				.collect(Collectors.toList());

		StringBuilder csv = new StringBuilder();
		csv.append("Alumno,Deporte,Concepto,Categoria,Fecha cargo,Estado,Fecha pago,Importe,Notas\n");

		for (TesoreriaMovimientoDTO movimiento : movimientos) {
			csv.append(escapeCSV(movimiento.getAlumnoNombreCompleto())).append(",");
			csv.append(escapeCSV(movimiento.getDeporte())).append(",");
			csv.append(escapeCSV(movimiento.getConcepto())).append(",");
			csv.append(escapeCSV(movimiento.getCategoria())).append(",");
			csv.append(escapeCSV(formatearFecha(movimiento.getFechaAsignacion()))).append(",");
			csv.append(escapeCSV(Boolean.TRUE.equals(movimiento.getPagado()) ? "PAGADO" : "PENDIENTE")).append(",");
			csv.append(escapeCSV(formatearFecha(movimiento.getFechaPago()))).append(",");
			csv.append(movimiento.getPrecio() != null ? String.format(Locale.ROOT, "%.2f", movimiento.getPrecio()) : "0.00")
					.append(",");
			csv.append(escapeCSV(movimiento.getNotas())).append("\n");
		}

		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public byte[] exportarMovimientosPDF(Integer mes, Integer ano, String deporte, Boolean pagado, String texto) {
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String textoNormalizado = normalizarTextoBusqueda(texto);
		List<TesoreriaMovimientoDTO> movimientos = obtenerMovimientosFiltrados(filtro, pagado, textoNormalizado).stream()
				.map(this::convertirAMovimientoDTO)
				.collect(Collectors.toList());
		String html = generarHtmlInformeTesoreria(movimientos, filtro, pagado);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(html, null);
			builder.toStream(outputStream);
			builder.run();
			return outputStream.toByteArray();
		} catch (Exception ex) {
			throw new RuntimeException("Error al generar el informe PDF de tesoreria", ex);
		}
	}

	private List<ProductoAlumno> obtenerMovimientosFiltrados(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto) {
		String anoTexto = filtro.ano != null ? String.valueOf(filtro.ano) : null;
		String mesNombre = filtro.mes != null ? MESES_ES[filtro.mes - 1] : null;

		return productoAlumnoRepository.findMovimientosTesoreriaFiltrados(
				filtro.deporte,
				filtro.deporteNombre,
				pagado,
				texto,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre);
	}

	private Page<ProductoAlumno> obtenerPaginaMovimientosFiltrados(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto,
			Pageable pageable) {
		String anoTexto = filtro.ano != null ? String.valueOf(filtro.ano) : null;
		String mesNombre = filtro.mes != null ? MESES_ES[filtro.mes - 1] : null;

		return productoAlumnoRepository.findMovimientosTesoreriaPaginados(
				filtro.deporte,
				filtro.deporteNombre,
				pagado,
				texto,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre,
				pageable);
	}

	private String normalizarTextoBusqueda(String texto) {
		if (texto == null) {
			return null;
		}

		String limpio = texto.trim();
		if (limpio.isEmpty()) {
			return null;
		}

		return "%" + limpio.toUpperCase(Locale.ROOT) + "%";
	}

	private Integer obtenerAnoMovimiento(ProductoAlumno productoAlumno) {
		PeriodoMovimiento periodo = obtenerPeriodoMovimiento(productoAlumno);
		return periodo != null ? periodo.ano : null;
	}

	private PeriodoMovimiento obtenerPeriodoMovimiento(ProductoAlumno productoAlumno) {
		Date fechaReferencia = productoAlumno.getFechaAsignacion() != null
				? productoAlumno.getFechaAsignacion()
				: productoAlumno.getFechaPago();

		if (fechaReferencia != null) {
			LocalDate localDate = fechaReferencia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			return new PeriodoMovimiento(localDate.getYear(), localDate.getMonthValue());
		}

		return extraerPeriodoDesdeConcepto(productoAlumno.getConcepto());
	}

	private PeriodoMovimiento extraerPeriodoDesdeConcepto(String concepto) {
		if (concepto == null || concepto.isBlank()) {
			return null;
		}

		String conceptoUpper = concepto.toUpperCase(Locale.ROOT);
		Matcher matcherAno = ANO_PATTERN.matcher(conceptoUpper);
		Integer ano = matcherAno.find() ? Integer.parseInt(matcherAno.group(1)) : null;
		if (ano == null) {
			return null;
		}

		Integer mes = null;
		for (int i = 0; i < MESES_ES.length; i++) {
			if (conceptoUpper.contains(MESES_ES[i])) {
				mes = i + 1;
				break;
			}
		}

		return new PeriodoMovimiento(ano, mes);
	}

	private TesoreriaMovimientoDTO convertirAMovimientoDTO(ProductoAlumno productoAlumno) {
		Alumno alumno = obtenerAlumno(productoAlumno);
		TesoreriaMovimientoDTO dto = new TesoreriaMovimientoDTO();
		dto.setProductoAlumnoId(productoAlumno.getId());
		dto.setAlumnoId(alumno != null ? alumno.getId() : null);
		dto.setAlumnoNombreCompleto(obtenerNombreCompletoAlumno(alumno));
		dto.setDeporte(obtenerDeporte(productoAlumno));
		dto.setConcepto(productoAlumno.getConcepto());
		dto.setCategoria(obtenerCategoria(productoAlumno.getConcepto()));
		dto.setFechaAsignacion(productoAlumno.getFechaAsignacion());
		dto.setPagado(Boolean.TRUE.equals(productoAlumno.getPagado()));
		dto.setFechaPago(productoAlumno.getFechaPago());
		dto.setPrecio(productoAlumno.getPrecio() != null ? productoAlumno.getPrecio() : 0.0);
		dto.setNotas(productoAlumno.getNotas());
		return dto;
	}

	private Alumno obtenerAlumno(ProductoAlumno productoAlumno) {
		if (productoAlumno.getAlumnoDeporte() != null && productoAlumno.getAlumnoDeporte().getAlumno() != null) {
			return productoAlumno.getAlumnoDeporte().getAlumno();
		}
		return productoAlumno.getAlumno();
	}

	private Long obtenerAlumnoId(ProductoAlumno productoAlumno) {
		Alumno alumno = obtenerAlumno(productoAlumno);
		return alumno != null ? alumno.getId() : null;
	}

	private String obtenerNombreCompletoAlumno(Alumno alumno) {
		if (alumno == null) {
			return "Sin alumno asociado";
		}

		String nombre = alumno.getNombre() != null ? alumno.getNombre().trim() : "";
		String apellidos = alumno.getApellidos() != null ? alumno.getApellidos().trim() : "";
		String nombreCompleto = (nombre + " " + apellidos).trim();
		return nombreCompleto.isEmpty() ? "Sin nombre" : nombreCompleto;
	}

	private String obtenerDeporte(ProductoAlumno productoAlumno) {
		if (productoAlumno.getAlumnoDeporte() != null && productoAlumno.getAlumnoDeporte().getDeporte() != null) {
			return productoAlumno.getAlumnoDeporte().getDeporte().name();
		}

		String concepto = productoAlumno.getConcepto() != null
				? productoAlumno.getConcepto().toUpperCase(Locale.ROOT)
				: "";
		if (concepto.contains(Deporte.TAEKWONDO.name())) {
			return Deporte.TAEKWONDO.name();
		}
		if (concepto.contains("KICKBOXING")) {
			return Deporte.KICKBOXING.name();
		}
		if (concepto.contains("PILATES")) {
			return Deporte.PILATES.name();
		}
		if (concepto.contains("DEFENSA") || concepto.contains("PERSONAL")) {
			return Deporte.DEFENSA_PERSONAL_FEMENINA.name();
		}

		return "GENERAL";
	}

	private String obtenerCategoria(String concepto) {
		String conceptoUpper = concepto != null ? concepto.toUpperCase(Locale.ROOT) : "";

		if (conceptoUpper.startsWith("MENSUALIDAD")) {
			return "MENSUALIDAD";
		}
		if (conceptoUpper.startsWith("TARIFA COMPETIDOR")) {
			return "TARIFA_COMPETIDOR";
		}
		if (conceptoUpper.contains("LICENCIA")) {
			return "LICENCIA";
		}
		if (conceptoUpper.startsWith("RESERVA DE PLAZA")) {
			return "RESERVA_PLAZA";
		}
		if (conceptoUpper.contains("EXAMEN") || conceptoUpper.contains("RECOMPENSA")) {
			return "EXAMEN";
		}
		return "OTRO";
	}

	private FiltroTesoreria construirFiltro(Integer mes, Integer ano, String deporte) {
		if (mes != null && (mes < 1 || mes > 12)) {
			throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
		}
		if (ano != null && (ano < 1900 || ano > 2200)) {
			throw new IllegalArgumentException("El año no es válido.");
		}
		if (mes != null && ano == null) {
			throw new IllegalArgumentException("Para filtrar por mes, también debes indicar el año.");
		}

		Deporte deporteEnum = parsearDeporte(deporte);
		String deporteNombre = deporteEnum != null ? deporteEnum.name() : null;
		return new FiltroTesoreria(deporteEnum, deporteNombre, mes, ano);
	}

	private Deporte parsearDeporte(String deporte) {
		if (deporte == null || deporte.isBlank() || "TODOS".equalsIgnoreCase(deporte)) {
			return null;
		}

		try {
			return Deporte.valueOf(deporte.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}
	}

	private String generarHtmlInformeTesoreria(
			List<TesoreriaMovimientoDTO> movimientos,
			FiltroTesoreria filtro,
			Boolean pagado) {
		double totalImporte = movimientos.stream()
				.mapToDouble(m -> m.getPrecio() != null ? m.getPrecio() : 0.0)
				.sum();
		long totalPagados = movimientos.stream().filter(m -> Boolean.TRUE.equals(m.getPagado())).count();
		long totalPendientes = movimientos.size() - totalPagados;

		String titulo = "Informe de Tesoreria";
		String estadoLabel = pagado == null ? "Todos" : (Boolean.TRUE.equals(pagado) ? "Pagados" : "Pendientes");
		String deporteLabel = filtro.deporte != null ? filtro.deporte.name() : "TODOS";
		String periodoLabel = obtenerPeriodoLabel(filtro.mes, filtro.ano);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8' />");
		html.append("<style>");
		html.append("body { font-family: Arial, sans-serif; color: #1f2937; font-size: 12px; }");
		html.append("h1 { margin-bottom: 4px; }");
		html.append(".meta { margin: 0 0 10px; color: #4b5563; }");
		html.append(".resumen { margin: 10px 0 14px; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #f9fafb; }");
		html.append(".resumen span { display: inline-block; margin-right: 16px; font-weight: 600; }");
		html.append("table { width: 100%; border-collapse: collapse; }");
		html.append("th, td { border: 1px solid #e5e7eb; padding: 6px; text-align: left; }");
		html.append("th { background: #f3f4f6; text-transform: uppercase; font-size: 11px; }");
		html.append("</style></head><body>");
		html.append("<h1>").append(escapeHtml(titulo)).append("</h1>");
		html.append("<p class='meta'><strong>Periodo:</strong> ").append(escapeHtml(periodoLabel))
				.append(" | <strong>Deporte:</strong> ").append(escapeHtml(deporteLabel))
				.append(" | <strong>Estado:</strong> ").append(escapeHtml(estadoLabel)).append("</p>");
		html.append("<div class='resumen'>");
		html.append("<span>Total movimientos: ").append(movimientos.size()).append("</span>");
		html.append("<span>Pagados: ").append(totalPagados).append("</span>");
		html.append("<span>Pendientes: ").append(totalPendientes).append("</span>");
		html.append("<span>Importe total: ").append(String.format(Locale.ROOT, "%.2f", totalImporte)).append(" EUR</span>");
		html.append("</div>");

		html.append("<table><thead><tr>");
		html.append("<th>Alumno</th><th>Deporte</th><th>Concepto</th><th>Categoria</th>");
		html.append("<th>Fecha cargo</th><th>Estado</th><th>Fecha pago</th><th>Importe</th>");
		html.append("</tr></thead><tbody>");

		if (movimientos.isEmpty()) {
			html.append("<tr><td colspan='8' style='text-align:center;'>No hay movimientos para los filtros seleccionados.</td></tr>");
		} else {
			for (TesoreriaMovimientoDTO movimiento : movimientos) {
				html.append("<tr>");
				html.append("<td>").append(escapeHtml(movimiento.getAlumnoNombreCompleto())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getDeporte())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getConcepto())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getCategoria())).append("</td>");
				html.append("<td>").append(escapeHtml(formatearFecha(movimiento.getFechaAsignacion()))).append("</td>");
				html.append("<td>").append(Boolean.TRUE.equals(movimiento.getPagado()) ? "PAGADO" : "PENDIENTE").append("</td>");
				html.append("<td>").append(escapeHtml(formatearFecha(movimiento.getFechaPago()))).append("</td>");
				html.append("<td>").append(String.format(Locale.ROOT, "%.2f", movimiento.getPrecio() != null ? movimiento.getPrecio() : 0.0))
						.append(" EUR</td>");
				html.append("</tr>");
			}
		}

		html.append("</tbody></table>");
		html.append("</body></html>");
		return html.toString();
	}

	private String formatearFecha(Date fecha) {
		if (fecha == null) {
			return "-";
		}
		LocalDate localDate = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return FECHA_FORMATTER.format(localDate);
	}

	private String obtenerPeriodoLabel(Integer mes, Integer ano) {
		if (ano == null) {
			return "Todos los años";
		}
		if (mes == null) {
			return "Año " + ano;
		}
		return MESES_ES[mes - 1] + " " + ano;
	}

	private String escapeCSV(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}

	private String escapeHtml(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private static class FiltroTesoreria {
		private final Deporte deporte;
		private final String deporteNombre;
		private final Integer mes;
		private final Integer ano;

		private FiltroTesoreria(Deporte deporte, String deporteNombre, Integer mes, Integer ano) {
			this.deporte = deporte;
			this.deporteNombre = deporteNombre;
			this.mes = mes;
			this.ano = ano;
		}
	}

	private static class PeriodoMovimiento {
		private final Integer ano;
		private final Integer mes;

		private PeriodoMovimiento(Integer ano, Integer mes) {
			this.ano = ano;
			this.mes = mes;
		}
	}
}
