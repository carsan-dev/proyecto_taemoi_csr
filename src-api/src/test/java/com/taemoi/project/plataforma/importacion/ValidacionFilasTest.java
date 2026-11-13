package com.taemoi.project.plataforma.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.ParentescoTutor;

/** Importador de alumnos (fase 4): reglas por fila contra una escuela inventada, sin base de datos. */
class ValidacionFilasTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 13);
	private static final LocalDate MENOR = LocalDate.of(2014, 5, 6);
	private static final LocalDate ADULTO = LocalDate.of(1990, 5, 6);

	private static final ContextoEscuela ESCUELA = new ContextoEscuela("TAEKWONDO",
			List.of(new ContextoEscuela.SedeRef(10L, "Mollina"), new ContextoEscuela.SedeRef(11L, "Estepa")),
			List.of(new ContextoEscuela.GrupoRef(100L, "Infantil L-X 17:00", 10L),
					new ContextoEscuela.GrupoRef(101L, "Adultos", 11L),
					new ContextoEscuela.GrupoRef(102L, "Doble", 11L), new ContextoEscuela.GrupoRef(103L, "doble", 11L)),
			List.of(new ContextoEscuela.TarifaRef("INFANTIL", "Infantil", "TAEKWONDO"),
					new ContextoEscuela.TarifaRef("ADULTO", "Adulto general", null),
					new ContextoEscuela.TarifaRef("PILATES", "Pilates", "PILATES")),
			codigo -> !codigo.equals("NEGRO_5_DAN"), Set.of("12345678Z"), Set.of(900),
			email -> email.contains("@") && !email.startsWith("@"));

	private static FilaValidada una(Map<String, Object> valores) {
		return validar(List.of(valores)).get(0);
	}

	private static List<FilaValidada> validar(List<Map<String, Object>> filas) {
		List<FilaHoja> hoja = new java.util.ArrayList<>();
		for (int i = 0; i < filas.size(); i++) {
			Map<String, Valor> valores = new HashMap<>();
			filas.get(i).forEach((columna, valor) -> valores.put(columna, valor instanceof LocalDate d ? Valor.deFecha(d)
					: valor instanceof Valor v ? v : Valor.deTexto(String.valueOf(valor))));
			hoja.add(new FilaHoja(i + 2, valores));
		}
		return new ValidacionFilas(ESCUELA, HOY).validar(hoja);
	}

	private static Map<String, Object> fila(Object... extra) {
		Map<String, Object> base = HojaDePrueba.valores("numero_federacion", "1", "nombre", "Ana", "apellidos",
				"Ficticia Uno", "fecha_nacimiento", MENOR, "grado", "AMARILLO", "sede", "Mollina", "grupo",
				"Infantil L-X 17:00", "telefono", "600000001", "direccion", "Calle Inventada 1", "email_familia",
				"familia@example.test", "tarifa", "INFANTIL", "autorizacion_imagen", "SI", "tutor_nombre",
				"Tutora Ficticia", "tutor_parentesco", "MADRE");
		base.putAll(HojaDePrueba.valores(extra));
		return base;
	}

	private static List<String> motivos(List<Incidencia> incidencias) {
		return incidencias.stream().map(i -> i.columna() + ": " + i.motivo()).toList();
	}

	@Test
	void filaCompleta_listaSinAvisos_conLosDatosResueltos() {
		FilaValidada f = una(fila("dni_nie", "12.345.679-s".replace(".", ""), "telefono", "+34 600 00 00 01",
				"fecha_licencia", "05/01/2026", "localidad", "Pueblo", "codigo_postal", "29532", "tutor_dni",
				"x-1234567-l", "observaciones", "Alergia ficticia"));
		assertEquals(List.of(), motivos(f.errores()));
		assertEquals(List.of(), motivos(f.avisos()));
		assertTrue(f.lista());
		DatosAlumno d = f.datos();
		assertEquals(1, d.numeroLicencia());
		assertEquals("12345679S", d.nif());
		assertEquals("600000001", d.telefono());
		assertEquals(10L, d.sedeId());
		assertEquals(100L, d.grupoId());
		assertEquals("AMARILLO", d.grado());
		assertEquals("INFANTIL", d.tarifa());
		assertEquals(LocalDate.of(2026, 1, 5), d.fechaLicencia());
		assertEquals("X1234567L", d.tutorNif());
		assertEquals(ParentescoTutor.MADRE, d.tutorParentesco());
		assertEquals(DatosAlumno.UsoImagen.SI, d.usoImagen());
		assertTrue(d.menor());
	}

	@Test
	void obligatorios_errores() {
		FilaValidada f = una(HojaDePrueba.valores("observaciones", "algo"));
		assertEquals(List.of("numero_federacion: Falta el número de federación.", "nombre: Falta el nombre.",
				"apellidos: Faltan los apellidos.", "fecha_nacimiento: Falta la fecha de nacimiento.",
				"sede: Falta la sede.", "telefono: Falta el teléfono."), motivos(f.errores()));
		assertNull(f.datos());
	}

	@Test
	void formatos_errores() {
		FilaValidada f = una(fila("numero_federacion", "12a", "fecha_nacimiento", "31/02/2014", "telefono", "12345",
				"telefono2", "abc", "dni_nie", "1234567Z", "tutor_dni", "ZZ", "email_familia", "no-es-correo",
				"fecha_licencia", "2026-01-05"));
		assertEquals(List.of(
				"numero_federacion: El número de federación no es válido: escribe un número entero positivo.",
				"fecha_nacimiento: La fecha de nacimiento no es válida: escríbela como DD/MM/AAAA.",
				"dni_nie: El DNI/NIE no es válido.",
				"email_familia: El correo de la familia no es válido.",
				"telefono: El teléfono no es válido: escribe de 9 a 15 cifras.",
				"telefono2: El segundo teléfono no es válido: escribe de 9 a 15 cifras.",
				"tutor_dni: El DNI del tutor no es válido.",
				"fecha_licencia: La fecha de licencia no es válida: escríbela como DD/MM/AAAA."), motivos(f.errores()));
		assertEquals("fecha_nacimiento: La fecha de nacimiento no puede ser futura.",
				motivos(una(fila("fecha_nacimiento", HOY.plusDays(1))).errores()).get(0));
		assertEquals(List.of("numero_federacion: El número de federación no es válido: escribe un número entero positivo."),
				motivos(una(fila("numero_federacion", "0")).errores()));
		assertEquals(List.of("numero_federacion: El número de federación no es válido: escribe un número entero positivo."),
				motivos(una(fila("numero_federacion", "99999999999")).errores()));
	}

	@Test
	void catalogo_sedeGrupoTarifa_sinAproximaciones() {
		assertEquals(List.of("sede: «Estepa centro» no es una sede de esta escuela."),
				motivos(una(fila("sede", "Estepa centro")).errores()));
		// Mayúsculas y espacios sobrantes no cuentan; las tildes sí.
		assertTrue(una(fila("sede", "  MOLLINA ", "grupo", "infantil  l-x 17:00")).lista());
		assertEquals(List.of("sede: «Mollína» no es una sede de esta escuela."),
				motivos(una(fila("sede", "Mollína")).errores()));
		assertEquals(List.of("grupo: No hay ningún grupo «Adultos» en Mollina."),
				motivos(una(fila("grupo", "Adultos")).errores()));
		assertEquals(List.of("grupo: Hay varios grupos «Doble» en Estepa: deja solo uno con ese nombre."),
				motivos(una(fila("sede", "Estepa", "grupo", "Doble")).errores()));
		assertEquals(List.of("tarifa: «Familiar» no es una tarifa de esta escuela."),
				motivos(una(fila("tarifa", "Familiar")).errores()));
		assertEquals("ADULTO", una(fila("tarifa", "adulto general")).datos().tarifa());
		assertEquals(List.of("tarifa: La tarifa «PILATES» es de otra disciplina."),
				motivos(una(fila("tarifa", "PILATES")).errores()));
	}

	@Test
	void grados_conversionYSistema() {
		FilaValidada sinGrado = una(fila("grado", "SIN GRADO"));
		assertEquals("BLANCO", sinGrado.datos().grado());
		assertEquals(List.of("grado: Sin grado: entrará como BLANCO."), motivos(sinGrado.avisos()));
		FilaValidada menorDan = una(fila("grado", "1 DAN"));
		assertEquals("ROJO_NEGRO_1_PUM", menorDan.datos().grado());
		assertEquals(List.of("grado: Menor con 1 DAN: entrará como 1 PUM."), motivos(menorDan.avisos()));
		FilaValidada adultoDan = una(fila("grado", "1 DAN", "fecha_nacimiento", ADULTO, "dni_nie", "12345679S"));
		assertEquals("NEGRO_1_DAN", adultoDan.datos().grado());
		assertEquals(List.of(), motivos(adultoDan.avisos()));
		assertEquals(List.of("grado: Menor con 4 DAN: no hay PUM equivalente; revisa el grado o la fecha de nacimiento."),
				motivos(una(fila("grado", "4 DAN")).errores()));
		assertEquals(List.of("grado: El grado «5 DAN» no existe en esta disciplina."),
				motivos(una(fila("grado", "5 DAN", "fecha_nacimiento", ADULTO)).errores()));
		// 18 años cumplidos hoy: adulto.
		assertEquals("NEGRO_2_DAN", una(fila("grado", "2 DAN", "fecha_nacimiento", HOY.minusYears(18), "dni_nie",
				"12345679S")).datos().grado());
		assertEquals("ROJO_NEGRO_2_PUM",
				una(fila("grado", "2 DAN", "fecha_nacimiento", HOY.minusYears(18).plusDays(1))).datos().grado());
	}

	@Test
	void avisos() {
		FilaValidada f = una(fila("direccion", "", "tarifa", "", "grupo", "", "email_familia", "",
				"fecha_nacimiento", ADULTO, "autorizacion_imagen", ""));
		assertTrue(f.lista());
		assertEquals(List.of("dni_nie: Adulto sin DNI/NIE: añádelo cuando lo tengas.",
				"grupo: Sin grupo: no estará en ningún turno.",
				"email_familia: Sin correo: la familia no podrá crear su cuenta.",
				"direccion: Sin dirección: entrará como «Pendiente».",
				"tarifa: Sin tarifa: no tendrá importe de mensualidad."), motivos(f.avisos()));
		assertEquals("Pendiente", f.datos().direccion());
		assertNull(f.datos().grupoId());
		assertNull(f.datos().tarifa());
		assertNull(f.datos().email());
		assertEquals(DatosAlumno.UsoImagen.SIN_REGISTRAR, f.datos().usoImagen());
	}

	@Test
	void listasCerradas_parentescoYUsoDeImagen() {
		assertEquals(ParentescoTutor.TUTOR_LEGAL, una(fila("tutor_parentesco", "tutor legal")).datos().tutorParentesco());
		assertEquals(ParentescoTutor.OTRO, una(fila("tutor_parentesco", "")).datos().tutorParentesco());
		assertEquals(List.of("tutor_parentesco: «ABUELA» no es un parentesco válido: usa PADRE, MADRE, TUTOR LEGAL u OTRO."),
				motivos(una(fila("tutor_parentesco", "ABUELA")).errores()));
		assertEquals(DatosAlumno.UsoImagen.SI, una(fila("autorizacion_imagen", "sí")).datos().usoImagen());
		assertEquals(DatosAlumno.UsoImagen.NO, una(fila("autorizacion_imagen", "No")).datos().usoImagen());
		assertEquals(DatosAlumno.UsoImagen.SIN_REGISTRAR,
				una(fila("autorizacion_imagen", "sin registrar")).datos().usoImagen());
		assertEquals(List.of("autorizacion_imagen: «QUIZÁ» no vale para el uso de imagen: usa SI, NO o SIN REGISTRAR."),
				motivos(una(fila("autorizacion_imagen", "QUIZÁ")).errores()));
		assertEquals(List.of("tutor_nombre: Falta el nombre del tutor."),
				motivos(una(fila("tutor_nombre", "", "tutor_dni", "12345679S")).errores()));
	}

	@Test
	void repetidasEnElFicheroYDniDeOtroAlumno() {
		List<FilaValidada> filas = validar(List.of(fila("numero_federacion", "5", "dni_nie", "11111111H"),
				fila("numero_federacion", "6", "dni_nie", "11111111-h"), fila("numero_federacion", "5"),
				fila("numero_federacion", "7", "dni_nie", "12345678Z")));
		assertEquals(List.of("numero_federacion: El número de federación está repetido en la fila 4.",
				"dni_nie: El DNI/NIE está repetido en la fila 3."), motivos(filas.get(0).errores()));
		assertEquals(List.of("dni_nie: El DNI/NIE está repetido en la fila 2."), motivos(filas.get(1).errores()));
		assertEquals(List.of("numero_federacion: El número de federación está repetido en la fila 2."),
				motivos(filas.get(2).errores()));
		assertEquals(List.of("dni_nie: Ya hay otro alumno de la escuela con este DNI/NIE."),
				motivos(filas.get(3).errores()));
		assertEquals(5, filas.get(0).errores().get(0).numeroFederacion());
		assertEquals(4, filas.get(2).errores().get(0).fila());
	}

	@Test
	void yaCargada_seSaltaSinErroresNiAvisos() {
		FilaValidada f = una(HojaDePrueba.valores("numero_federacion", "900", "telefono", ""));
		assertTrue(f.yaCargada());
		assertFalse(f.lista());
		assertEquals(List.of(), f.errores());
		assertEquals(List.of(), f.avisos());
		// Ya cargada no cuenta como repetida.
		List<FilaValidada> dos = validar(List.of(fila("numero_federacion", "900"), fila("numero_federacion", "900")));
		assertTrue(dos.get(0).yaCargada() && dos.get(1).yaCargada());
	}

	@Test
	void errorDeFormula_unErrorPorCelda() {
		FilaValidada f = una(fila("telefono", Valor.ERROR, "grado", Valor.ERROR));
		assertEquals(List.of("grado: La celda tiene un error de fórmula: escribe el valor.",
				"telefono: La celda tiene un error de fórmula: escribe el valor."), motivos(f.errores()));
	}

	@Test
	void largos_errores() {
		String largo = "x".repeat(256);
		assertEquals(List.of("nombre: El nombre es demasiado largo: máximo 255 caracteres.",
				"localidad: La localidad es demasiado larga: máximo 100 caracteres.",
				"codigo_postal: El código postal es demasiado largo: máximo 10 caracteres."),
				motivos(una(fila("nombre", largo, "localidad", "y".repeat(101), "codigo_postal", "12345678901")).errores()));
	}

	@Test
	void losMotivosNoCitanDatosPersonales() {
		List<FilaValidada> filas = validar(List.of(
				fila("nombre", "NombreSecreto", "apellidos", "ApellidoSecreto", "dni_nie", "99999999R", "telefono", "1",
						"email_familia", "secreto@", "tutor_nombre", "TutorSecreto", "tutor_dni", "88888888Y",
						"sede", "Nada"),
				fila("nombre", "NombreSecreto", "dni_nie", "99999999R", "numero_federacion", "2")));
		for (FilaValidada f : filas) {
			for (Incidencia i : f.errores()) {
				for (String secreto : List.of("Secreto", "99999999R", "88888888Y", "secreto@")) {
					assertFalse(i.motivo().contains(secreto), i.motivo());
				}
			}
		}
		assertNotNull(filas.get(0).errores());
	}
}
