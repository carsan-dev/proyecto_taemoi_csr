package com.taemoi.project.plataforma.importacion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Hoja maestra de prueba generada en memoria con POI (sin ficheros binarios en el repositorio). Datos inventados.
 *
 * <p>Cada fila es un mapa columna → valor: {@code String} (celda de texto), {@code Number} (celda numérica),
 * {@code LocalDate} (celda de fecha con formato) o {@code Boolean}. Las columnas que no aparecen quedan vacías.
 */
public final class HojaDePrueba {

	/** Las 26 columnas de la hoja maestra, en su orden. */
	public static final List<String> CABECERAS = List.of("numero_federacion", "nombre_federacion", "fecha_nacimiento",
			"menor", "grado_federacion", "dni_federacion", "fecha_licencia", "nombre", "apellidos", "dni_nie", "grado",
			"sede", "grupo", "email_familia", "telefono", "telefono2", "direccion", "localidad", "codigo_postal",
			"tutor_nombre", "tutor_parentesco", "tutor_dni", "tarifa", "autorizacion_imagen", "observaciones", "revisar");

	private final List<String> cabeceras;
	private final List<Map<String, Object>> filas = new ArrayList<>();
	private String nombreHoja = "Alumnos";

	public HojaDePrueba() {
		this(CABECERAS);
	}

	public HojaDePrueba(List<String> cabeceras) {
		this.cabeceras = cabeceras;
	}

	public HojaDePrueba conNombreDeHoja(String nombre) {
		this.nombreHoja = nombre;
		return this;
	}

	/** Añade una fila; {@code null} = fila vacía. */
	public HojaDePrueba fila(Map<String, Object> valores) {
		filas.add(valores);
		return this;
	}

	/** Mapa ordenado a partir de pares columna, valor. */
	public static Map<String, Object> valores(Object... pares) {
		Map<String, Object> mapa = new LinkedHashMap<>();
		for (int i = 0; i < pares.length; i += 2) {
			mapa.put((String) pares[i], pares[i + 1]);
		}
		return mapa;
	}

	/**
	 * Una fila completa y válida de un menor (nacido en {@code nacimiento}) en {@code sede}, sin grupo ni tarifa salvo
	 * que se cambien; con los valores extra que se pasen (pisan los de base).
	 */
	public static Map<String, Object> alumno(int numero, String nombre, LocalDate nacimiento, String sede,
			Object... extra) {
		Map<String, Object> base = valores("numero_federacion", numero, "nombre_federacion", "FICTICIO " + nombre,
				"fecha_nacimiento", nacimiento, "nombre", nombre, "apellidos", "Prueba Ficticia", "grado", "AMARILLO",
				"sede", sede, "telefono", "600000" + String.format("%03d", numero % 1000), "direccion",
				"Calle Inventada " + numero, "localidad", "Pueblo Ficticio", "codigo_postal", "41000",
				"email_familia", "familia-" + numero + "@example.test", "autorizacion_imagen", "SIN REGISTRAR");
		base.putAll(valores(extra));
		return base;
	}

	public byte[] xlsx() {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			CellStyle fecha = libro.createCellStyle();
			fecha.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			Sheet hoja = libro.createSheet(nombreHoja);
			Row cabecera = hoja.createRow(0);
			for (int c = 0; c < cabeceras.size(); c++) {
				cabecera.createCell(c).setCellValue(cabeceras.get(c));
			}
			for (int f = 0; f < filas.size(); f++) {
				Map<String, Object> valores = filas.get(f);
				if (valores == null) {
					continue;
				}
				Row fila = hoja.createRow(f + 1);
				for (int c = 0; c < cabeceras.size(); c++) {
					Object valor = valores.get(cabeceras.get(c));
					if (valor == null) {
						continue;
					}
					Cell celda = fila.createCell(c);
					if (valor instanceof LocalDate d) {
						celda.setCellValue(d);
						celda.setCellStyle(fecha);
					} else if (valor instanceof Number n) {
						celda.setCellValue(n.doubleValue());
					} else if (valor instanceof Boolean b) {
						celda.setCellValue(b);
					} else {
						celda.setCellValue(valor.toString());
					}
				}
			}
			libro.createSheet("Listas").createRow(0).createCell(0).setCellValue("SI");
			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
