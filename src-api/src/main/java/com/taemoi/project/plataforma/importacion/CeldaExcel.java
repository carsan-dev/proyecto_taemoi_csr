package com.taemoi.project.plataforma.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * Lectura normalizada de una celda de la hoja maestra. Una celda puede venir como texto, número o fecha de Excel:
 * <ul>
 * <li>Un número sale en notación normal y sin decimales sobrantes ({@code 606000000}, no {@code 6.06E8}); con un formato
 * de ceros ({@code 00000}) conserva los ceros de la izquierda.</li>
 * <li>Una fórmula NO se evalúa: se usa el valor que Excel dejó guardado; un error guardado es un {@link Valor#ERROR}.</li>
 * <li>Las fechas admiten celda de fecha o texto {@code DD/MM/AAAA}.</li>
 * </ul>
 */
final class CeldaExcel {

	private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("d/M/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);

	private CeldaExcel() {
	}

	static Valor leer(Cell celda) {
		if (celda == null) {
			return Valor.VACIO;
		}
		CellType tipo = celda.getCellType();
		if (tipo == CellType.FORMULA) {
			tipo = celda.getCachedFormulaResultType();
		}
		return switch (tipo) {
		case STRING -> Valor.deTexto(limpiar(celda.getStringCellValue()));
		case NUMERIC -> numerica(celda);
		case BOOLEAN -> Valor.deTexto(celda.getBooleanCellValue() ? "SI" : "NO");
		case ERROR -> Valor.ERROR;
		default -> Valor.VACIO;
		};
	}

	private static Valor numerica(Cell celda) {
		if (DateUtil.isCellDateFormatted(celda)) {
			java.time.LocalDateTime fecha = celda.getLocalDateTimeCellValue();
			if (fecha != null) {
				return Valor.deFecha(fecha.toLocalDate());
			}
		}
		return Valor.deTexto(numero(celda.getNumericCellValue(), celda.getCellStyle().getDataFormatString()));
	}

	/**
	 * La fecha de un valor: la de la celda de fecha o la del texto {@code DD/MM/AAAA} (día y mes con una o dos cifras,
	 * año con cuatro). {@code null} si está vacío o no es una fecha válida.
	 */
	static LocalDate fecha(Valor valor) {
		if (valor.fecha() != null) {
			return valor.fecha();
		}
		if (valor.error() || valor.texto().isEmpty()) {
			return null;
		}
		try {
			return LocalDate.parse(valor.texto(), DD_MM_AAAA);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** Número en notación normal; con un formato solo de ceros, completado con ceros a la izquierda hasta su longitud. */
	static String numero(double valor, String formato) {
		if (Double.isNaN(valor) || Double.isInfinite(valor)) {
			return "";
		}
		String texto = BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
		if (formato != null && formato.matches("0+") && texto.matches("\\d+") && texto.length() < formato.length()) {
			texto = "0".repeat(formato.length() - texto.length()) + texto;
		}
		return texto;
	}

	/** Sin espacios en los extremos; los espacios de no separación cuentan como espacios. */
	static String limpiar(String texto) {
		if (texto == null) {
			return "";
		}
		return texto.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').strip();
	}
}
