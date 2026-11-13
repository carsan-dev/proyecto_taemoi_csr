package com.taemoi.project.plataforma.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;

/**
 * Lectura normalizada de una celda de la hoja maestra. Una celda puede venir como texto, número o fecha de Excel:
 * <ul>
 * <li>Un número sale en notación normal y sin decimales sobrantes ({@code 606000000}, no {@code 6.06E8}); con un formato
 * de ceros ({@code 00000}) conserva los ceros de la izquierda.</li>
 * <li>Una fórmula NO se evalúa: se usa el valor que Excel dejó guardado; un error guardado es un {@link Valor#ERROR} y
 * una fórmula sin valor guardado, un {@link Valor#FORMULA_SIN_VALOR}.</li>
 * <li>Las fechas admiten celda de fecha o texto {@code DD/MM/AAAA}.</li>
 * </ul>
 */
final class CeldaExcel {

	private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("d/M/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);
	/** La primera fecha que se acepta (la primera del calendario de Excel). */
	static final LocalDate PRIMERA_FECHA = LocalDate.of(1900, 1, 1);

	private CeldaExcel() {
	}

	static Valor leer(Cell celda) {
		if (celda == null) {
			return Valor.VACIO;
		}
		CellType tipo = celda.getCellType();
		if (tipo == CellType.FORMULA) {
			// Sin valor guardado (nadie calculó la fórmula: la generó un programa) POI daría 0 o una fecha de 1899.
			CellType cacheado = celda.getCachedFormulaResultType();
			if (celda instanceof XSSFCell xssf && sinValorGuardado(xssf, cacheado)) {
				return Valor.FORMULA_SIN_VALOR;
			}
			tipo = cacheado;
		}
		return switch (tipo) {
		case STRING -> Valor.deTexto(limpiar(celda.getStringCellValue()));
		case NUMERIC -> numerica(celda);
		case BOOLEAN -> Valor.deTexto(celda.getBooleanCellValue() ? "SI" : "NO");
		case ERROR -> Valor.ERROR;
		default -> Valor.VACIO;
		};
	}

	/**
	 * Sin {@code <v>}, o con un {@code <v></v>} vacío (openpyxl y pandas lo escriben así) y un tipo que no es texto. Una
	 * fórmula de texto con {@code <v></v>} es una cadena vacía legítima.
	 */
	private static boolean sinValorGuardado(XSSFCell celda, CellType cacheado) {
		if (!celda.getCTCell().isSetV()) {
			return true;
		}
		String v = celda.getCTCell().getV();
		return (v == null || v.isEmpty()) && cacheado != CellType.STRING;
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
	 * año con cuatro). {@code null} si está vacío, no es una fecha válida o es anterior al {@link #PRIMERA_FECHA} (una
	 * celda de fecha con un 0 sale como 31/12/1899).
	 */
	static LocalDate fecha(Valor valor) {
		LocalDate fecha = valor.fecha();
		if (fecha == null) {
			if (valor.error() || valor.texto().isEmpty()) {
				return null;
			}
			try {
				fecha = LocalDate.parse(valor.texto(), DD_MM_AAAA);
			} catch (DateTimeParseException e) {
				return null;
			}
		}
		return fecha.isBefore(PRIMERA_FECHA) ? null : fecha;
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
