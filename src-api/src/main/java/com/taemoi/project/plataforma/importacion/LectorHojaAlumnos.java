package com.taemoi.project.plataforma.importacion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lee la hoja maestra subida ({@code .xlsx}) sin tocar la base de datos. Defensas contra ficheros hostiles, además de los
 * límites de POI por defecto (que siguen activos): hasta 5 MB, extensión {@code .xlsx}, tipo declarado de Excel o
 * genérico, contenido OOXML (zip), descomprimido total acotado (bomba zip) y fórmulas sin evaluar.
 *
 * <p>Los errores son {@link IllegalArgumentException} (400) con un mensaje para quien sube el fichero; nunca citan su
 * contenido. Nada se guarda en disco.
 */
@Component
class LectorHojaAlumnos {

	static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
	/** Lo que puede ocupar el zip descomprimido entero: una hoja de miles de filas no llega a unos pocos MB. */
	static final long DESCOMPRIMIDO_MAXIMO = 100L * 1024 * 1024;
	static final int ENTRADAS_MAXIMAS = 1000;
	static final int FILAS_MAXIMAS = 5000;

	static final String FALTA_FICHERO = "Falta el fichero de la hoja de alumnos.";
	static final String DEMASIADO_GRANDE = "El fichero no puede pasar de 5 MB.";
	static final String NO_ES_XLSX = "Este fichero no es un Excel .xlsx.";
	static final String SIN_HOJA = "Este fichero no tiene la hoja " + ColumnasHoja.HOJA + ".";
	static final String DEMASIADAS_FILAS = "La hoja " + ColumnasHoja.HOJA + " tiene más de 5.000 filas.";
	private static final String NO_ES_LA_HOJA = "Este fichero no es la hoja de alumnos: ";

	/** Tipos declarados que se aceptan: el de Excel y los genéricos que mandan algunos navegadores. */
	private static final Set<String> TIPOS = Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"application/octet-stream", "application/zip", "application/x-zip-compressed");

	HojaAlumnos leer(MultipartFile fichero) {
		if (fichero == null || fichero.isEmpty()) {
			throw new IllegalArgumentException(FALTA_FICHERO);
		}
		if (fichero.getSize() > TAMANO_MAXIMO) {
			throw new IllegalArgumentException(DEMASIADO_GRANDE);
		}
		byte[] contenido;
		try {
			contenido = fichero.getBytes();
		} catch (IOException e) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
		return leer(contenido, fichero.getOriginalFilename(), fichero.getContentType());
	}

	static HojaAlumnos leer(byte[] contenido, String nombre, String tipo) {
		if (contenido == null || contenido.length == 0) {
			throw new IllegalArgumentException(FALTA_FICHERO);
		}
		if (contenido.length > TAMANO_MAXIMO) {
			throw new IllegalArgumentException(DEMASIADO_GRANDE);
		}
		if (nombre == null || !nombre.strip().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
		String tipoDeclarado = tipo == null ? "" : tipo.split(";")[0].strip().toLowerCase(Locale.ROOT);
		if (!tipoDeclarado.isEmpty() && !TIPOS.contains(tipoDeclarado)) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
		if (!esOoxml(contenido)) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
		comprobarDescomprimido(contenido);
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
			return leer(libro, contenido, nombre);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			// Un zip que no es un libro de Excel (o uno dañado): sin el detalle, que puede citar el contenido.
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
	}

	private static HojaAlumnos leer(XSSFWorkbook libro, byte[] contenido, String nombre) {
		Sheet hoja = libro.getSheet(ColumnasHoja.HOJA);
		if (hoja == null) {
			throw new IllegalArgumentException(SIN_HOJA);
		}
		Map<String, Integer> columnas = cabeceras(hoja.getRow(0));
		List<String> faltan = ColumnasHoja.OBLIGATORIAS.stream().filter(c -> !columnas.containsKey(c)).toList();
		if (!faltan.isEmpty()) {
			throw new IllegalArgumentException(NO_ES_LA_HOJA + (faltan.size() == 1 ? "falta la columna " + faltan.get(0)
					: "faltan las columnas " + String.join(", ", faltan.subList(0, faltan.size() - 1)) + " y "
							+ faltan.get(faltan.size() - 1))
					+ ".");
		}
		List<FilaHoja> filas = new ArrayList<>();
		for (int r = 1; r <= hoja.getLastRowNum(); r++) {
			Row fila = hoja.getRow(r);
			if (fila == null) {
				continue;
			}
			Map<String, Valor> valores = new HashMap<>();
			boolean conDatos = false;
			for (Map.Entry<String, Integer> columna : columnas.entrySet()) {
				Valor valor = CeldaExcel.leer(fila.getCell(columna.getValue()));
				valores.put(columna.getKey(), valor);
				// La columna revisar la escribe el importador: una fila solo con ella está vacía.
				conDatos |= !valor.vacio() && !ColumnasHoja.REVISAR.equals(columna.getKey());
			}
			if (!conDatos) {
				continue;
			}
			if (filas.size() == FILAS_MAXIMAS) {
				throw new IllegalArgumentException(DEMASIADAS_FILAS);
			}
			filas.add(new FilaHoja(r + 1, valores));
		}
		return new HojaAlumnos(contenido, nombre, columnas, filas);
	}

	/** Columnas conocidas de la fila de cabecera (sin distinguir mayúsculas ni espacios); la primera, si se repite. */
	private static Map<String, Integer> cabeceras(Row cabecera) {
		Map<String, Integer> columnas = new LinkedHashMap<>();
		if (cabecera == null) {
			return columnas;
		}
		for (Cell celda : cabecera) {
			String nombre = CeldaExcel.leer(celda).texto().toLowerCase(Locale.ROOT);
			if (ColumnasHoja.CONOCIDAS.contains(nombre)) {
				columnas.putIfAbsent(nombre, celda.getColumnIndex());
			}
		}
		return columnas;
	}

	private static boolean esOoxml(byte[] contenido) {
		try (InputStream entrada = FileMagic.prepareToCheckMagic(new ByteArrayInputStream(contenido))) {
			return FileMagic.valueOf(entrada) == FileMagic.OOXML;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Descomprime el zip contando lo que sale (no lo que dicen sus cabeceras, que pueden mentir) y lo rechaza si pasa de
	 * {@link #DESCOMPRIMIDO_MAXIMO} o de {@link #ENTRADAS_MAXIMAS}. POI vuelve a leerlo después con sus propios límites.
	 */
	private static void comprobarDescomprimido(byte[] contenido) {
		long total = 0;
		int entradas = 0;
		byte[] bloque = new byte[64 * 1024];
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
			for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
				if (++entradas > ENTRADAS_MAXIMAS) {
					throw new IllegalArgumentException(NO_ES_XLSX);
				}
				for (int leidos = zip.read(bloque); leidos != -1; leidos = zip.read(bloque)) {
					total += leidos;
					if (total > DESCOMPRIMIDO_MAXIMO) {
						throw new IllegalArgumentException(NO_ES_XLSX);
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
		if (entradas == 0) {
			throw new IllegalArgumentException(NO_ES_XLSX);
		}
	}
}
