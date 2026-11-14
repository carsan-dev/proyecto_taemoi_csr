package com.taemoi.project.plataforma.importacion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Decisión d558.6: el MISMO libro subido, con la columna {@code revisar} de la hoja «Alumnos» reescrita (motivos de
 * error y aviso de cada fila separados por «; », «Ya estaba cargada: no se toca.» en las ya cargadas, vacía si no hay
 * nada) y las celdas con error en rojo claro ({@code FFC7CE}).
 * Las celdas que una pasada anterior dejó en ese rojo y ya no tienen error vuelven a su estilo sin relleno. Se genera en
 * memoria: no se guarda nada.
 *
 * @param nombre    {@code <original>-revisar.xlsx}
 * @param contenido el libro
 */
public record HojaRevisada(String nombre, byte[] contenido) {

	public static final String TIPO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	static final byte[] ROJO_CLARO = { (byte) 0xFF, (byte) 0xC7, (byte) 0xCE };
	static final String YA_CARGADA = "Ya estaba cargada: no se toca.";
	private static final String SEPARADOR = "; ";

	static HojaRevisada de(HojaAlumnos hoja, InformeImportacion informe) {
		Map<Integer, List<String>> motivos = new LinkedHashMap<>();
		Map<Integer, Set<String>> celdasConError = new HashMap<>();
		for (Incidencia error : informe.errores()) {
			motivos.computeIfAbsent(error.fila(), f -> new java.util.ArrayList<>()).add(error.motivo());
			if (error.columna() != null) {
				celdasConError.computeIfAbsent(error.fila(), f -> new HashSet<>()).add(error.columna());
			}
		}
		for (Incidencia aviso : informe.avisos()) {
			motivos.computeIfAbsent(aviso.fila(), f -> new java.util.ArrayList<>()).add(aviso.motivo());
		}
		// Las ya cargadas no tienen errores ni avisos: solo la nota, sin celdas en rojo.
		for (InformeImportacion.FilaYaCargada yaCargada : informe.yaCargadas()) {
			motivos.computeIfAbsent(yaCargada.fila(), f -> new java.util.ArrayList<>()).add(YA_CARGADA);
		}
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(hoja.contenido()));
				ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			new Marcado(libro, hoja.columnas()).marcar(motivos, celdasConError);
			libro.write(salida);
			return new HojaRevisada(nombre(hoja.nombreOriginal()), salida.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar la hoja con errores", e);
		}
	}

	/** {@code <original>-revisar.xlsx}, sin ruta ni caracteres que no valen en un nombre de fichero. */
	static String nombre(String original) {
		String base = original == null ? "" : original.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1).strip();
		if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
			base = base.substring(0, base.length() - ".xlsx".length());
		}
		base = base.replaceAll("[\\p{Cntrl}:*?\"<>|]", "").strip();
		return (base.isEmpty() ? "alumnos" : base) + "-revisar.xlsx";
	}

	private static final class Marcado {
		private final XSSFWorkbook libro;
		private final Sheet hoja;
		private final Map<String, Integer> columnas;
		private final Map<Integer, String> nombresPorIndice;
		private final Map<Short, XSSFCellStyle> enRojo = new HashMap<>();
		private final Map<Short, XSSFCellStyle> sinRojo = new HashMap<>();

		Marcado(XSSFWorkbook libro, Map<String, Integer> columnas) {
			this.libro = libro;
			this.hoja = libro.getSheet(ColumnasHoja.HOJA);
			this.columnas = columnas;
			this.nombresPorIndice = columnas.entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
		}

		void marcar(Map<Integer, List<String>> motivos, Map<Integer, Set<String>> celdasConError) {
			int revisar = columnas.get(ColumnasHoja.REVISAR);
			int ultima = Math.max(hoja.getLastRowNum() + 1,
					motivos.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
			for (int numero = 2; numero <= ultima; numero++) {
				Row fila = hoja.getRow(numero - 1);
				List<String> deLaFila = motivos.getOrDefault(numero, List.of());
				Set<String> conError = celdasConError.getOrDefault(numero, Set.of());
				if (fila == null) {
					if (deLaFila.isEmpty()) {
						continue;
					}
					fila = hoja.createRow(numero - 1);
				}
				Cell celdaRevisar = fila.getCell(revisar);
				if (!deLaFila.isEmpty()) {
					if (celdaRevisar == null) {
						celdaRevisar = fila.createCell(revisar);
					}
					// Vaciar antes: una cadena en línea (t="inlineStr") conservaría su <is> antiguo junto al valor nuevo.
					celdaRevisar.setBlank();
					celdaRevisar.setCellValue(String.join(SEPARADOR, deLaFila));
				} else if (celdaRevisar != null) {
					celdaRevisar.setBlank();
				}
				// Celdas con error, en rojo; las que una pasada anterior dejó en rojo y ya no tienen error, sin él.
				for (String columna : conError) {
					Integer indice = columnas.get(columna);
					if (indice != null) {
						Cell celda = fila.getCell(indice);
						if (celda == null) {
							celda = fila.createCell(indice);
						}
						celda.setCellStyle(enRojo((XSSFCellStyle) celda.getCellStyle()));
					}
				}
				for (Cell celda : fila) {
					String columna = nombresPorIndice.get(celda.getColumnIndex());
					XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
					if (!conError.contains(columna) && esRojoClaro(estilo)) {
						celda.setCellStyle(sinRojo(estilo));
					}
				}
			}
		}

		private XSSFCellStyle enRojo(XSSFCellStyle original) {
			if (esRojoClaro(original)) {
				return original;
			}
			return enRojo.computeIfAbsent(original.getIndex(), i -> {
				XSSFCellStyle estilo = libro.createCellStyle();
				estilo.cloneStyleFrom(original);
				estilo.setFillForegroundColor(new XSSFColor(ROJO_CLARO, null));
				estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				return estilo;
			});
		}

		private XSSFCellStyle sinRojo(XSSFCellStyle original) {
			return sinRojo.computeIfAbsent(original.getIndex(), i -> {
				XSSFCellStyle estilo = libro.createCellStyle();
				estilo.cloneStyleFrom(original);
				estilo.setFillPattern(FillPatternType.NO_FILL);
				return estilo;
			});
		}

		private static boolean esRojoClaro(XSSFCellStyle estilo) {
			if (estilo == null || estilo.getFillPattern() != FillPatternType.SOLID_FOREGROUND) {
				return false;
			}
			XSSFColor color = estilo.getFillForegroundXSSFColor();
			return color != null && color.getRGB() != null && Arrays.equals(ROJO_CLARO, color.getRGB());
		}
	}
}
