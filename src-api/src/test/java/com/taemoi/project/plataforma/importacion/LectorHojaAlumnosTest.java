package com.taemoi.project.plataforma.importacion;

import static com.taemoi.project.plataforma.importacion.HojaDePrueba.valores;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Importador de alumnos (fase 4): lectura de la hoja maestra y rechazo de ficheros que no lo son. */
class LectorHojaAlumnosTest {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private static HojaAlumnos leer(byte[] contenido) {
		return LectorHojaAlumnos.leer(contenido, "hoja.xlsx", XLSX);
	}

	private static String mensaje(byte[] contenido, String nombre, String tipo) {
		return assertThrows(IllegalArgumentException.class, () -> LectorHojaAlumnos.leer(contenido, nombre, tipo))
				.getMessage();
	}

	@Test
	void numerosSinNotacionCientificaNiCerosPerdidos() {
		HojaAlumnos hoja = leer(new HojaDePrueba()
				.fila(valores("numero_federacion", 1234, "telefono", 606000000, "telefono2", 346060000000000L,
						"codigo_postal", 41000, "nombre", "Ana"))
				.xlsx());
		FilaHoja fila = hoja.filas().get(0);
		assertEquals("1234", fila.valor("numero_federacion").texto());
		assertEquals("606000000", fila.valor("telefono").texto());
		assertEquals("346060000000000", fila.valor("telefono2").texto());
		assertEquals("41000", fila.valor("codigo_postal").texto());
	}

	@Test
	void numeroConFormatoDeCeros_conservaLosCerosDeLaIzquierda() throws IOException {
		try (XSSFWorkbook libro = new XSSFWorkbook()) {
			Sheet hoja = cabeceras(libro);
			CellStyle ceros = libro.createCellStyle();
			ceros.setDataFormat(libro.createDataFormat().getFormat("00000"));
			Row fila = hoja.createRow(1);
			Cell cp = fila.createCell(HojaDePrueba.CABECERAS.indexOf("codigo_postal"));
			cp.setCellValue(4001);
			cp.setCellStyle(ceros);
			fila.createCell(HojaDePrueba.CABECERAS.indexOf("nombre")).setCellValue("Ana");
			assertEquals("04001", leer(bytes(libro)).filas().get(0).valor("codigo_postal").texto());
		}
	}

	@Test
	void fechas_celdaDeFechaYTextoDdMmAaaa() {
		HojaAlumnos hoja = leer(new HojaDePrueba()
				.fila(valores("nombre", "Ana", "fecha_nacimiento", LocalDate.of(2012, 3, 4), "fecha_licencia",
						"05/01/2026"))
				.xlsx());
		FilaHoja fila = hoja.filas().get(0);
		assertEquals(LocalDate.of(2012, 3, 4), fila.valor("fecha_nacimiento").fecha());
		assertEquals(LocalDate.of(2026, 1, 5), CeldaExcel.fecha(fila.valor("fecha_licencia")));
		assertEquals(LocalDate.of(2026, 1, 5), CeldaExcel.fecha(new Valor("5/1/2026", null, false)));
		assertNull(CeldaExcel.fecha(new Valor("31/02/2026", null, false)));
		assertNull(CeldaExcel.fecha(new Valor("2026-01-05", null, false)));
		assertNull(CeldaExcel.fecha(new Valor("5/1/26", null, false)));
	}

	@Test
	void filasVaciasYSoloConRevisar_seIgnoran_yLaFilaEsLaDeExcel() {
		HojaAlumnos hoja = leer(new HojaDePrueba()
				.fila(valores("nombre", "Ana"))
				.fila(null)
				.fila(valores("revisar", "Falta el teléfono."))
				.fila(valores("nombre", "Eva"))
				.xlsx());
		assertEquals(2, hoja.filas().size());
		assertEquals(2, hoja.filas().get(0).fila());
		assertEquals(5, hoja.filas().get(1).fila());
	}

	@Test
	void cabecerasPorNombre_enCualquierOrden_ySeIgnoranLasDesconocidas() {
		List<String> desordenadas = new ArrayList<>(HojaDePrueba.CABECERAS);
		java.util.Collections.reverse(desordenadas);
		desordenadas.add(3, "columna_inventada");
		HojaAlumnos hoja = leer(new HojaDePrueba(desordenadas)
				.fila(valores("nombre", "Ana", "telefono", "600 000 000", "columna_inventada", "x")).xlsx());
		assertEquals("Ana", hoja.filas().get(0).valor("nombre").texto());
		assertEquals("600 000 000", hoja.filas().get(0).valor("telefono").texto());
		assertFalse(hoja.columnas().containsKey("columna_inventada"));
	}

	@Test
	void formula_seLeeSuValorGuardado_sinEvaluarla() throws IOException {
		try (XSSFWorkbook libro = new XSSFWorkbook()) {
			Sheet hoja = cabeceras(libro);
			Row fila = hoja.createRow(1);
			Cell nombre = fila.createCell(HojaDePrueba.CABECERAS.indexOf("nombre"));
			nombre.setCellFormula("UPPER(\"evaluada\")");
			nombre.setCellValue("Guardada");
			Cell telefono = fila.createCell(HojaDePrueba.CABECERAS.indexOf("telefono"));
			telefono.setCellFormula("1/0");
			telefono.setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.DIV0.getCode());
			FilaHoja leida = leer(bytes(libro)).filas().get(0);
			assertEquals("Guardada", leida.valor("nombre").texto());
			assertTrue(leida.valor("telefono").error());
		}
	}

	@Test
	void columnasObligatoriasQueFaltan_400ConLaLista() {
		List<String> sin = new ArrayList<>(HojaDePrueba.CABECERAS);
		sin.removeAll(List.of("telefono", "grupo", "revisar"));
		assertEquals("Este fichero no es la hoja de alumnos: faltan las columnas grupo, telefono y revisar.",
				mensaje(new HojaDePrueba(sin).xlsx(), "h.xlsx", XLSX));
		List<String> sinUna = new ArrayList<>(HojaDePrueba.CABECERAS);
		sinUna.remove("sede");
		assertEquals("Este fichero no es la hoja de alumnos: falta la columna sede.",
				mensaje(new HojaDePrueba(sinUna).xlsx(), "h.xlsx", XLSX));
	}

	@Test
	void sinHojaAlumnos_400() {
		assertEquals("Este fichero no tiene la hoja Alumnos.",
				mensaje(new HojaDePrueba().conNombreDeHoja("Deportistas").xlsx(), "h.xlsx", XLSX));
	}

	@Test
	void noEsXlsx_400() throws IOException {
		String noEs = "Este fichero no es un Excel .xlsx.";
		byte[] valido = new HojaDePrueba().xlsx();
		assertEquals(noEs, mensaje("numero;nombre\n1;Ana".getBytes(), "h.csv", "text/csv"));
		assertEquals(noEs, mensaje("numero;nombre\n1;Ana".getBytes(), "h.xlsx", XLSX));
		assertEquals(noEs, mensaje(valido, "h.csv", XLSX));
		assertEquals(noEs, mensaje(valido, "h.xlsx", "text/csv"));
		assertEquals(noEs, mensaje(valido, null, XLSX));
		try (HSSFWorkbook xls = new HSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			xls.createSheet("Alumnos");
			xls.write(salida);
			assertEquals(noEs, mensaje(salida.toByteArray(), "h.xlsx", XLSX));
		}
		// Un zip cualquiera con la extensión de Excel.
		assertEquals(noEs, mensaje(zip("hola.txt", 10), "h.xlsx", XLSX));
		// Tipo genérico o sin tipo: vale si el contenido es un .xlsx.
		assertEquals(0, LectorHojaAlumnos.leer(valido, "h.XLSX", "application/octet-stream").filas().size());
		assertEquals(0, LectorHojaAlumnos.leer(valido, "h.xlsx", null).filas().size());
	}

	@Test
	void vacioOMayorDe5Mb_400() {
		assertEquals("Falta el fichero de la hoja de alumnos.", mensaje(new byte[0], "h.xlsx", XLSX));
		byte[] grande = new byte[5 * 1024 * 1024 + 1];
		assertEquals("El fichero no puede pasar de 5 MB.", mensaje(grande, "h.xlsx", XLSX));
	}

	@Test
	void zipQueSeDescomprimeDemasiado_400() throws IOException {
		byte[] bomba = zip("xl/worksheets/sheet1.xml", 150 * 1024 * 1024);
		assertTrue(bomba.length < 5 * 1024 * 1024, "La prueba debe caber en el límite de tamaño");
		assertEquals("Este fichero no es un Excel .xlsx.", mensaje(bomba, "h.xlsx", XLSX));
	}

	// ------------------------------------------------------------------ apoyo

	private static Sheet cabeceras(XSSFWorkbook libro) {
		Sheet hoja = libro.createSheet("Alumnos");
		Row cabecera = hoja.createRow(0);
		for (int c = 0; c < HojaDePrueba.CABECERAS.size(); c++) {
			cabecera.createCell(c).setCellValue(HojaDePrueba.CABECERAS.get(c));
		}
		return hoja;
	}

	private static byte[] bytes(XSSFWorkbook libro) throws IOException {
		try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	/** Un zip con una entrada de {@code tamano} ceros. */
	private static byte[] zip(String entrada, int tamano) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(salida)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write("<Types/>".getBytes());
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry(entrada));
			byte[] bloque = new byte[1024 * 1024];
			for (int escrito = 0; escrito < tamano; escrito += bloque.length) {
				zip.write(bloque, 0, Math.min(bloque.length, tamano - escrito));
			}
			zip.closeEntry();
		}
		return salida.toByteArray();
	}
}
