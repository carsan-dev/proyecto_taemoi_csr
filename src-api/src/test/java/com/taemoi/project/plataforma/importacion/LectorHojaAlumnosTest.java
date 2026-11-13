package com.taemoi.project.plataforma.importacion;

import static com.taemoi.project.plataforma.importacion.HojaDePrueba.valores;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.springframework.web.multipart.MultipartFile;

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
	void formulaSinValorGuardado_esUnErrorDeLaCelda_noUnCero() throws IOException {
		try (XSSFWorkbook libro = new XSSFWorkbook()) {
			Sheet hoja = cabeceras(libro);
			CellStyle fecha = libro.createCellStyle();
			fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
			Row fila = hoja.createRow(1);
			XSSFCell nombre = (XSSFCell) fila.createCell(HojaDePrueba.CABECERAS.indexOf("nombre"));
			nombre.setCellFormula("UPPER(\"evaluada\")");
			XSSFCell nacimiento = (XSSFCell) fila.createCell(HojaDePrueba.CABECERAS.indexOf("fecha_nacimiento"));
			nacimiento.setCellFormula("DATE(2014,5,6)");
			nacimiento.setCellStyle(fecha);
			for (XSSFCell celda : List.of(nombre, nacimiento)) {
				if (celda.getCTCell().isSetV()) {
					celda.getCTCell().unsetV();
				}
			}
			FilaHoja leida = leer(bytes(libro)).filas().get(0);
			for (String columna : List.of("nombre", "fecha_nacimiento")) {
				Valor valor = leida.valor(columna);
				assertTrue(valor.error(), columna + " sin valor guardado no puede leerse como dato");
				assertEquals("", valor.texto(), columna);
				assertNull(valor.fecha(), columna);
				assertNull(CeldaExcel.fecha(valor), columna);
			}
		}
	}

	@Test
	void formulaConValorVacio_numericaTextoOBooleana_esUnErrorDeLaCelda_noUnCero() throws IOException {
		// openpyxl y pandas escriben <f>..</f><v></v>: el <v> existe pero esta vacio.
		for (String formula : List.of("1+1", "UPPER(\"x\")", "TRUE()")) {
			Valor valor = formulaConV(formula, null, "");
			assertTrue(valor.error(), formula + " con <v></v> no puede leerse como dato");
			assertEquals("", valor.texto(), formula);
		}
		Valor booleana = formulaConV("TRUE()", STCellType.B, "");
		assertTrue(booleana.error());
	}

	@Test
	void formulaDeTextoConValorVacio_esUnaCeldaVacia_noUnError() throws IOException {
		Valor valor = formulaConV("UPPER(\"\")", STCellType.STR, "");
		assertFalse(valor.error());
		assertEquals("", valor.texto());
		assertNull(valor.fecha());
	}

	@Test
	void formulaConValorGuardadoReal_seLeeSuValor() throws IOException {
		assertEquals("2", formulaConV("1+1", null, "2").texto());
		Valor texto = formulaConV("UPPER(\"ana\")", STCellType.STR, "ANA");
		assertFalse(texto.error());
		assertEquals("ANA", texto.texto());
	}

	/** Lee la celda "nombre" de una fila cuyo XML tiene la formula, el tipo en cache y el valor indicados. */
	private static Valor formulaConV(String formula, STCellType.Enum tipo, String v) throws IOException {
		return leer(xlsxConFormula(formula, tipo, v)).filas().get(0).valor("nombre");
	}

	/** Un xlsx cuya celda "nombre" tiene {@code <f>formula</f><v>v</v>}, comprobado tras guardar y reabrir. */
	static byte[] xlsxConFormula(String formula, STCellType.Enum tipo, String v) throws IOException {
		byte[] bytes;
		try (XSSFWorkbook libro = new XSSFWorkbook()) {
			Sheet hoja = cabeceras(libro);
			Row fila = hoja.createRow(1);
			// Otra celda con dato para que la fila no se descarte como vacia.
			fila.createCell(HojaDePrueba.CABECERAS.indexOf("telefono")).setCellValue("600000001");
			XSSFCell celda = (XSSFCell) fila.createCell(HojaDePrueba.CABECERAS.indexOf("nombre"));
			celda.setCellFormula(formula);
			if (tipo != null) {
				celda.getCTCell().setT(tipo);
			}
			celda.getCTCell().setV(v);
			bytes = bytes(libro);
		}
		try (XSSFWorkbook releido = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
			XSSFCell celda = releido.getSheetAt(0).getRow(1).getCell(HojaDePrueba.CABECERAS.indexOf("nombre"));
			assertTrue(celda.getCTCell().isSetF());
			assertTrue(celda.getCTCell().isSetV(), "el <v> debe persistir tras reabrir");
			assertEquals(v, celda.getCTCell().getV());
		}
		return bytes;
	}

	@Test
	void fechasAnterioresA1900_noSonFechas() {
		assertNull(CeldaExcel.fecha(new Valor("31/12/1899", null, false)));
		assertNull(CeldaExcel.fecha(Valor.deFecha(LocalDate.of(1899, 12, 31))));
		assertNull(CeldaExcel.fecha(new Valor("01/01/0001", null, false)));
		assertEquals(LocalDate.of(1900, 1, 1), CeldaExcel.fecha(new Valor("01/01/1900", null, false)));
		assertEquals(LocalDate.of(1900, 1, 1), CeldaExcel.fecha(Valor.deFecha(LocalDate.of(1900, 1, 1))));
	}

	@Test
	void cabeceraRepetida_400() {
		List<String> repetida = new ArrayList<>(HojaDePrueba.CABECERAS);
		repetida.add(" Nombre ");
		assertEquals("Este fichero no es la hoja de alumnos: la columna nombre está repetida.",
				mensaje(new HojaDePrueba(repetida).xlsx(), "h.xlsx", XLSX));
		List<String> revisarDosVeces = new ArrayList<>(HojaDePrueba.CABECERAS);
		revisarDosVeces.add(2, "revisar");
		assertEquals("Este fichero no es la hoja de alumnos: la columna revisar está repetida.",
				mensaje(new HojaDePrueba(revisarDosVeces).xlsx(), "h.xlsx", XLSX));
	}

	@Test
	void libroConMacros_400_aunqueSeLlameXlsx() throws IOException {
		String conMacros = "El fichero tiene macros: guarda la hoja como libro de Excel (.xlsx).";
		// Un .xlsx con el proyecto de VBA dentro.
		byte[] conVba = conEntrada(new HojaDePrueba().xlsx(), "xl/vbaProject.bin", new byte[] { 1, 2, 3 });
		assertEquals(conMacros, mensaje(conVba, "h.xlsx", XLSX));
		// Un .xlsm (tipo de contenido de libro con macros) renombrado a .xlsx.
		try (XSSFWorkbook xlsm = new XSSFWorkbook(XSSFWorkbookType.XLSM)) {
			Sheet hoja = cabeceras(xlsm);
			hoja.createRow(1).createCell(HojaDePrueba.CABECERAS.indexOf("nombre")).setCellValue("Ana");
			assertEquals(conMacros, mensaje(bytes(xlsm), "h.xlsx", XLSX));
		}
	}

	@Test
	void ficheroDeMasDe5Mb_seRechazaAntesDeLeerlo() throws IOException {
		MultipartFile grande = mock(MultipartFile.class);
		when(grande.isEmpty()).thenReturn(false);
		when(grande.getSize()).thenReturn(LectorHojaAlumnos.TAMANO_MAXIMO + 1);
		when(grande.getOriginalFilename()).thenReturn("h.xlsx");
		when(grande.getContentType()).thenReturn(XLSX);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new LectorHojaAlumnos().leer(grande));
		assertEquals("El fichero no puede pasar de 5 MB.", e.getMessage());
		verify(grande, never()).getBytes();
		verify(grande, never()).getInputStream();
	}

	@Test
	void descomprimidoDeMasDe25Mb_400() throws IOException {
		int mb = 1024 * 1024;
		LectorHojaAlumnos.comprobarDescomprimido(zip("xl/media/relleno.bin", 24 * mb));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> LectorHojaAlumnos.comprobarDescomprimido(zip("xl/media/relleno.bin", 26 * mb)));
		assertEquals("Este fichero no es un Excel .xlsx.", e.getMessage());
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

	/** El mismo zip con una entrada más. */
	private static byte[] conEntrada(byte[] zip, String nombre, byte[] contenido) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ZipInputStream entrada = new ZipInputStream(new java.io.ByteArrayInputStream(zip));
				ZipOutputStream nuevo = new ZipOutputStream(salida)) {
			for (ZipEntry e = entrada.getNextEntry(); e != null; e = entrada.getNextEntry()) {
				nuevo.putNextEntry(new ZipEntry(e.getName()));
				entrada.transferTo(nuevo);
				nuevo.closeEntry();
			}
			nuevo.putNextEntry(new ZipEntry(nombre));
			nuevo.write(contenido);
			nuevo.closeEntry();
		}
		return salida.toByteArray();
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
