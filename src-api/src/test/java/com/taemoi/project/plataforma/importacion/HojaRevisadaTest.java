package com.taemoi.project.plataforma.importacion;

import static com.taemoi.project.plataforma.importacion.HojaDePrueba.valores;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Importador de alumnos (fase 4, d558.6): la hoja devuelta con la columna revisar y las celdas con error marcadas. */
class HojaRevisadaTest {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private static int columna(String nombre) {
		return HojaDePrueba.CABECERAS.indexOf(nombre);
	}

	@Test
	void revisarConMotivos_celdasEnRojo_yElRestoIntacto() throws IOException {
		byte[] original = new HojaDePrueba()
				.fila(valores("numero_federacion", 1, "nombre", "Ana", "revisar", "texto viejo"))
				.fila(valores("numero_federacion", 2, "nombre", "Eva", "telefono", "1"))
				.fila(valores("numero_federacion", 3, "nombre", "Luz", "revisar", "otro texto viejo"))
				.xlsx();
		HojaAlumnos hoja = LectorHojaAlumnos.leer(original, "hoja maestra.xlsx", XLSX);
		InformeImportacion informe = new InformeImportacion(3, 1, 0, 0,
				List.of(new Incidencia(3, 2, "telefono", "El teléfono no es válido: escribe de 9 a 15 cifras."),
						new Incidencia(3, 2, "sede", "Falta la sede.")),
				List.of(new Incidencia(2, 1, "direccion", "Sin dirección: entrará como «Pendiente»."),
						new Incidencia(3, 2, "grupo", "Sin grupo: no estará en ningún turno.")));

		HojaRevisada revisada = HojaRevisada.de(hoja, informe);
		assertEquals("hoja maestra-revisar.xlsx", revisada.nombre());
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(revisada.contenido()))) {
			Sheet alumnos = libro.getSheet("Alumnos");
			assertNotNull(libro.getSheet("Listas"), "Se conservan las demás hojas");
			assertEquals(3, alumnos.getLastRowNum());
			Row cabecera = alumnos.getRow(0);
			for (int c = 0; c < HojaDePrueba.CABECERAS.size(); c++) {
				assertEquals(HojaDePrueba.CABECERAS.get(c), cabecera.getCell(c).getStringCellValue());
			}
			int revisar = columna("revisar");
			assertEquals("Sin dirección: entrará como «Pendiente».",
					alumnos.getRow(1).getCell(revisar).getStringCellValue());
			assertEquals("El teléfono no es válido: escribe de 9 a 15 cifras.; Falta la sede.; Sin grupo: no estará en "
					+ "ningún turno.", alumnos.getRow(2).getCell(revisar).getStringCellValue());
			assertEquals("", alumnos.getRow(3).getCell(revisar).getStringCellValue());
			// Datos intactos.
			assertEquals("Eva", alumnos.getRow(2).getCell(columna("nombre")).getStringCellValue());
			assertEquals("1", alumnos.getRow(2).getCell(columna("telefono")).getStringCellValue());
			// Rojo en las celdas con error (también en la sede, que estaba vacía); en ninguna más.
			assertTrue(enRojo(alumnos.getRow(2).getCell(columna("telefono"))));
			assertTrue(enRojo(alumnos.getRow(2).getCell(columna("sede"))));
			assertFalse(enRojo(alumnos.getRow(2).getCell(columna("nombre"))));
			assertFalse(enRojo(alumnos.getRow(1).getCell(columna("nombre"))));
			assertFalse(enRojo(alumnos.getRow(2).getCell(revisar)));

			// Segunda pasada sobre la hoja ya revisada: el teléfono corregido deja de estar en rojo.
			alumnos.getRow(2).getCell(columna("telefono")).setCellValue("600000002");
			java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
			libro.write(salida);
			HojaAlumnos corregida = LectorHojaAlumnos.leer(salida.toByteArray(), revisada.nombre(), XLSX);
			InformeImportacion segundo = new InformeImportacion(3, 2, 0, 0,
					List.of(new Incidencia(3, 2, "sede", "Falta la sede.")), List.of());
			try (XSSFWorkbook otra = new XSSFWorkbook(
					new ByteArrayInputStream(HojaRevisada.de(corregida, segundo).contenido()))) {
				Sheet hoja2 = otra.getSheet("Alumnos");
				assertFalse(enRojo(hoja2.getRow(2).getCell(columna("telefono"))));
				assertTrue(enRojo(hoja2.getRow(2).getCell(columna("sede"))));
				assertEquals("Falta la sede.", hoja2.getRow(2).getCell(revisar).getStringCellValue());
				assertEquals("", hoja2.getRow(1).getCell(revisar).getStringCellValue());
			}
		}
	}

	@Test
	void nombreDelFichero() {
		assertEquals("alumnos-revisar.xlsx", HojaRevisada.nombre(null));
		assertEquals("alumnos-revisar.xlsx", HojaRevisada.nombre(".xlsx"));
		assertEquals("Sierra Sur-revisar.xlsx", HojaRevisada.nombre("C:\\carpeta\\Sierra Sur.XLSX"));
		assertEquals("hoja-revisar.xlsx", HojaRevisada.nombre("../hoja\".xlsx"));
	}

	private static boolean enRojo(Cell celda) {
		XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
		return estilo.getFillPattern() == FillPatternType.SOLID_FOREGROUND
				&& estilo.getFillForegroundXSSFColor() != null
				&& java.util.Arrays.equals(HojaRevisada.ROJO_CLARO, estilo.getFillForegroundXSSFColor().getRGB());
	}

	@Test
	void rojoClaroEsFfc7ce() {
		assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xC7, (byte) 0xCE }, HojaRevisada.ROJO_CLARO);
	}
}
