package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.TemporadaService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza W1 de la web por plantilla (fase4-web-plan.md §5): {@code GET /api/publico/web} y {@code /legal}.
 *
 * <p>Lo anónimo sale de la escuela del Host (pieza 1 del plan de dominio): cada escuela sintética tiene su fila en
 * {@code organizacion_dominio} y sus peticiones llevan ese Host; sin Host conocido, la por defecto (la 1). Datos
 * inventados en escuelas sintéticas; la 1 (Moiskimdo) no se toca (las filas de dominio se borran al acabar).
 *
 * <pre>
 * X  publicada (tema chandal), 2 sedes activas + 1 inactiva, grupos visibles, de competición, de disciplina inactiva y
 *    sin turnos; plazas Quedan 2 / Quedan 1 / Lleno / Lista de espera; tarifas activas e inactiva; tarifas por sede
 *    pasada, vigente y futura; licencia y exámenes; eventos pasados, ocultos y 4 próximos; bloques (uno con una imagen
 *    de Y); datos legales y de firmante.
 * Y  publicada, una de cada (para el aislamiento y el recuento de sentencias).
 *    También una tarifa y un examen (más barato) de la disciplina inactiva, que no salen.
 * Z  con fila sin publicar; cuentas de propietario, familia, profesor y responsable de sede.  W  sin fila.
 * </pre>
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
		+ "com.taemoi.project.integracion.ContadorSentencias")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebPublicaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String WEB = "/api/publico/web";
	private static final String LEGAL = "/api/publico/web/legal";
	/** Claves que nunca salen en {@code /api/publico/web}, en ningún nivel del JSON. */
	private static final Set<String> CLAVES_PRIVADAS = Set.of("cif", "nombreLegal", "firmanteNombre", "firmanteCargo",
			"firmanteNif", "emailRemitenteNombre", "aforo", "aforoTurno", "aforoEfectivo", "ocupacion",
			"ocupacionEfectiva", "alumnos", "activa", "codigoClubFederacion", "slug", "organizacionId",
			"logoImagenId", "zonaHoraria", "comportamientoLegadoTaemoi", "publicadaPor", "dni", "nif", "webUrl",
			"vendibleProfesor", "provincia");
	/** En {@code /legal}: el titular sí (con CIF), nunca el firmante ni el remitente. */
	private static final Set<String> CLAVES_PRIVADAS_LEGAL = Set.of("firmanteNombre", "firmanteCargo", "firmanteNif",
			"emailRemitenteNombre", "slug", "organizacionId", "publicadaPor", "comportamientoLegadoTaemoi");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@Autowired TemporadaService temporadas;
	@Autowired JwtService jwt;
	@Autowired UsuarioRepository usuarios;

	@MockitoBean JavaMailSender mailSender;
	@Autowired ResolutorEscuelaPorHost porHost;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	LocalDate hoy;
	String temporada;
	long x, y, z, w;
	long disciplinaX, disciplinaInactivaX, disciplinaY;
	long sedeA, sedeB, sedeInactiva, sedeY;
	long grupoPeques, grupoInfantil, grupoAdultos, grupoCompeticion, grupoSedeInactiva, grupoSinTurnos, grupoKick,
			grupoY;
	long turnoLunes, turnoMiercoles, turnoInfantil, turnoAdultos, turnoY;
	long tarifaGeneral, tarifaFamiliar, tarifaInterna, tarifaY;
	long eventoMas1, eventoMas2, eventoMas3, eventoMas4, eventoY;
	long imagenX, imagenY;
	long propietarioZ, familiaZ, profesorZ, responsableZ, gestorW;
	/** Host de cada escuela sintética ({@code organizacion_dominio}). */
	final Map<Long, String> hosts = new HashMap<>();
	int preinscripciones;

	@BeforeAll
	void sembrar() {
		hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
		temporada = temporadas.actual();

		// ---------------------------------------------------------------- X
		x = organizacion("x", "Escuela Web X " + marca);
		jdbc.update("""
				UPDATE organizacion SET nombre_legal = 'Club Deportivo X', cif = 'G00000000', direccion = 'Calle X 1',
				  localidad = 'Herrera', provincia = 'Sevilla', codigo_postal = '41567', email_contacto = 'contacto-x@example.test',
				  telefono = '600000001', color_primario = '#1A5C3B', firmante_nombre = 'Firmante Secreto X',
				  firmante_cargo = 'Presidencia', firmante_nif = '00000000T', email_remitente_nombre = 'Remitente secreto X'
				WHERE id = ?""", x);
		disciplinaX = disciplina(x, "TAEKWONDO", "Taekwondo X", true);
		disciplinaInactivaX = disciplina(x, "KICKBOXING", "Kickboxing X", false);
		limite(x, 1);
		plantilla(x, disciplinaX);

		sedeA = sede(x, "Herrera X " + marca, 2, true);
		jdbc.update("UPDATE sede SET direccion = 'Avda. Constitución s/n', localidad = 'Herrera', codigo_postal = '41567',"
				+ " telefono = '600000002', email = 'sede-secreta@example.test', codigo_club_federacion = 'CLUB SECRETO'"
				+ " WHERE id = ?", sedeA);
		sedeB = sede(x, "Estepa X " + marca, null, true);
		sedeInactiva = sede(x, "Cerrada X " + marca, null, false);

		grupoInfantil = grupo(x, disciplinaX, sedeA, "Infantil X", 7, 11);
		grupoPeques = grupo(x, disciplinaX, sedeA, "Peques X", 4, 6);
		grupoCompeticion = grupo(x, disciplinaX, sedeA, "Competición X", 12, 99);
		grupoKick = grupo(x, disciplinaInactivaX, sedeA, "Kick X", 12, 99);
		grupoAdultos = grupo(x, disciplinaX, sedeB, "Adultos X", 14, null);
		grupoSinTurnos = grupo(x, disciplinaX, sedeB, "Sin turnos X", 4, 6);
		grupoSedeInactiva = grupo(x, disciplinaX, sedeInactiva, "Cerrado X", 4, 6);

		turnoMiercoles = turno(x, grupoPeques, "Miércoles", "17:00", "17:45");
		turnoLunes = turno(x, grupoPeques, "Lunes", "17:00", "17:45");
		turnoInfantil = turno(x, grupoInfantil, "Martes", "18:00", "19:00");
		turnoAdultos = turno(x, grupoAdultos, "Jueves", "20:00", "21:30");
		turno(x, grupoCompeticion, "Viernes", "19:00", "20:30");
		turno(x, grupoKick, "Viernes", "18:00", "19:00");
		turno(x, grupoSedeInactiva, "Lunes", "18:00", "19:00");

		// Sede A, aforo 2: lunes sin nadie (Quedan 2), miércoles 1 pendiente (Quedan 1), infantil 2 pendientes (Lleno).
		preinscripcion(x, disciplinaX, grupoPeques, "PENDIENTE", turnoMiercoles);
		preinscripcion(x, disciplinaX, grupoInfantil, "PENDIENTE", turnoInfantil);
		preinscripcion(x, disciplinaX, grupoInfantil, "PENDIENTE", turnoInfantil);
		// Sede B, límite de la escuela 1: un pendiente y uno en espera (Lista de espera).
		preinscripcion(x, disciplinaX, grupoAdultos, "PENDIENTE", turnoAdultos);
		preinscripcion(x, disciplinaX, grupoAdultos, "EN_LISTA_ESPERA", turnoAdultos);

		tarifaGeneral = tarifa(x, disciplinaX, "GENERAL", "Taekwondo X", "25.00", null, null, true, 1);
		tarifaFamiliar = tarifa(x, null, "FAMILIAR", "Familiar X", "0.00", "22.00", "18.00", true, 2);
		tarifaInterna = tarifa(x, null, "INTERNA", "Tarifa interna X", "1.00", null, null, false, 3);
		long tarifaKick = tarifa(x, disciplinaInactivaX, "KICK", "Kick X", "30.00", null, null, true, 4);
		tarifaSede(x, tarifaGeneral, sedeA, "26.00", hoy.minusYears(2).withDayOfMonth(1));
		tarifaSede(x, tarifaGeneral, sedeA, "27.00", hoy.minusYears(1).withDayOfMonth(1));
		tarifaSede(x, tarifaGeneral, sedeA, "28.00", hoy.plusYears(1).withDayOfMonth(1));
		tarifaSede(x, tarifaGeneral, sedeInactiva, "30.00", hoy.minusYears(1).withDayOfMonth(1));
		tarifaSede(x, tarifaInterna, sedeA, "5.00", hoy.minusYears(1).withDayOfMonth(1));
		tarifaSede(x, tarifaKick, sedeA, "31.00", hoy.minusYears(1).withDayOfMonth(1));

		long licencia = producto(x, "Licencia federativa X", 45.0, "LICENCIA", disciplinaX);
		jdbc.update("UPDATE disciplina SET producto_licencia_id = ? WHERE id = ?", licencia, disciplinaX);
		producto(x, "Examen amarillo X", 20.0, "EXAMEN", null);
		producto(x, "Examen negro X", 35.0, "EXAMEN", null);
		producto(x, "Examen gratis X", 0.0, "EXAMEN", null);
		producto(x, "Examen kick X", 10.0, "EXAMEN", disciplinaInactivaX);

		imagenX = imagen(x, "galeria-x");
		evento(x, "Pasado X", hoy.minusDays(10), true, null);
		evento(x, "Oculto X", hoy.plusDays(1), false, null);
		eventoMas4 = evento(x, "Más 4 X", hoy.plusDays(4), true, null);
		eventoMas2 = evento(x, "Más 2 X", hoy.plusDays(2), true, imagenX);
		eventoMas1 = evento(x, "Más 1 X", hoy.plusDays(1), true, null);
		eventoMas3 = evento(x, "Más 3 X", hoy.plusDays(3), true, null);
		evento(x, "Sin fecha X", null, true, null);

		// ---------------------------------------------------------------- Y (una de cada)
		y = organizacion("y", "Escuela Web Y " + marca);
		jdbc.update("UPDATE organizacion SET cif = 'G99999999', email_contacto = 'contacto-y@example.test' WHERE id = ?", y);
		disciplinaY = disciplina(y, "TAEKWONDO", "Taekwondo Y", true);
		plantilla(y, disciplinaY);
		sedeY = sede(y, "Sede de Y " + marca, null, true);
		grupoY = grupo(y, disciplinaY, sedeY, "Grupo de Y", 4, 99);
		turnoY = turno(y, grupoY, "Lunes", "18:00", "19:00");
		tarifaY = tarifa(y, disciplinaY, "GENERAL", "Tarifa de Y", "99.00", null, null, true, 1);
		tarifaSede(y, tarifaY, sedeY, "98.00", hoy.minusYears(1).withDayOfMonth(1));
		long licenciaY = producto(y, "Licencia de Y", 15.0, "LICENCIA", disciplinaY);
		jdbc.update("UPDATE disciplina SET producto_licencia_id = ? WHERE id = ?", licenciaY, disciplinaY);
		producto(y, "Examen de Y", 5.0, "EXAMEN", null);
		imagenY = imagen(y, "galeria-y");
		eventoY = evento(y, "Evento de Y", hoy.plusDays(1), true, imagenY);
		bloque(y, "portada.titular", "Titular de Y", imagenY, 1);
		web(y, "estandar", true, "2026-09-15 09:00:00");

		// Bloques de X: uno apunta a una imagen de Y (no debe salir su URL).
		bloque(x, "profesor.1.nombre", "Profesora X", null, 3);
		bloque(x, "portada.titular", "Titular de X", null, 1);
		bloque(x, "galeria.1", null, imagenX, 2);
		bloque(x, "galeria.2", null, imagenY, 2);
		web(x, "chandal", true, "2026-10-01 10:00:00");

		// ---------------------------------------------------------------- Z y W
		z = organizacion("z", "Escuela Web Z " + marca);
		disciplina(z, "TAEKWONDO", "Taekwondo Z", true);
		web(z, "linea-comarcal", false, null);
		propietarioZ = cuenta("propietario-z", z, "PROPIETARIO");
		familiaZ = cuenta("familia-z", z, "FAMILIA");
		alumnoDe(z, familiaZ);
		profesorZ = cuenta("profesor-z", z, "PROFESOR");
		responsableZ = cuenta("responsable-z", z, "RESPONSABLE_SEDE");
		w = organizacion("w", "Escuela Web W " + marca);
		gestorW = cuenta("gestor-w", w, "ADMINISTRADOR");

		for (long org : List.of(x, y, z, w)) {
			String host = "w1-" + org + "-" + marca + ".test";
			jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", org,
					host);
			hosts.put(org, host);
		}
		porHost.recargar();
	}

	@AfterAll
	void retirarDominios() {
		hosts.values().forEach(host -> jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", host));
		porHost.recargar();
	}

	// ------------------------------------------------------------------ Moiskimdo

	/** La 1 no tiene fila en web_escuela: 404 en las dos rutas, anónimo (su web de siempre no cambia). */
	@Test
	void moiskimdo_anonimo_404() throws Exception {
		assertEquals(404, mvc.perform(get(WEB)).andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get(LEGAL)).andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get(WEB).param("vistaPrevia", "true")).andReturn().getResponse().getStatus());
	}

	/** Ni la vista previa de su propietario: «Web · Tema» no existe para Moiskimdo. */
	@Test
	void moiskimdo_vistaPreviaDelPropietario_404() throws Exception {
		Usuario admin = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase("admin-it@example.test").orElseThrow());
		Cookie sesion = new Cookie("jwt", jwt.generateToken(admin, 600_000, ORG1));
		assertEquals(404, mvc.perform(get(WEB).param("vistaPrevia", "true").cookie(sesion)).andReturn().getResponse()
				.getStatus());
		assertEquals(404, mvc.perform(get(LEGAL).param("vistaPrevia", "true").cookie(sesion)).andReturn().getResponse()
				.getStatus());
	}

	// ------------------------------------------------------------------ publicada, anónimo

	@Test
	void publicada_anonimo_escuelaTemaYContacto() throws Exception {
		JsonNode web = cuerpo(anonimo(x, WEB));
		assertEquals("Escuela Web X " + marca, web.at("/escuela/nombre").asText());
		assertEquals("#1A5C3B", web.at("/escuela/colorPrimario").asText());
		assertTrue(web.at("/escuela/logoVersion").isNull());
		assertEquals("chandal", web.get("tema").asText());
		assertTrue(web.get("publicada").asBoolean());
		assertFalse(web.get("vistaPrevia").asBoolean());
		assertEquals(temporada, web.get("temporada").asText());
		assertEquals("contacto-x@example.test", web.at("/contacto/email").asText());
		assertEquals("600000001", web.at("/contacto/telefono").asText());
	}

	@Test
	void publicada_anonimo_cacheYEtag() throws Exception {
		MvcResult r = anonimo(x, WEB);
		String etag = r.getResponse().getHeader("ETag");
		assertNotNull(etag);
		String cache = r.getResponse().getHeader("Cache-Control");
		assertVaria(r);
		assertTrue(cache.contains("max-age=60") && cache.contains("public"), cache);
		assertEquals(304, anonimo(x, get(WEB).header("If-None-Match", etag)).getResponse().getStatus());
		assertEquals(304, anonimo(x, get(LEGAL).header("If-None-Match",
				anonimo(x, LEGAL).getResponse().getHeader("ETag"))).getResponse().getStatus());
	}

	/** Solo sedes activas; grupos visibles con turnos y de disciplinas activas, en el orden de la preinscripción. */
	@Test
	void publicada_anonimo_sedesYGrupos() throws Exception {
		JsonNode sedes = cuerpo(anonimo(x, WEB)).get("sedes");
		assertEquals(List.of(sedeA, sedeB), ids(sedes, "id"));
		JsonNode a = sedes.get(0);
		assertEquals("Herrera X " + marca, a.get("nombre").asText());
		assertEquals("Avda. Constitución s/n", a.get("direccion").asText());
		assertEquals("Herrera", a.get("localidad").asText());
		assertEquals("41567", a.get("codigoPostal").asText());
		assertEquals("600000002", a.get("telefono").asText());
		assertFalse(a.has("email"), "el correo de la sede no sale");
		assertEquals(List.of(grupoPeques, grupoInfantil), ids(a.get("grupos"), "id"), "edad 4-6 antes que 7-11");
		JsonNode peques = a.get("grupos").get(0);
		assertEquals("Peques X", peques.get("nombre").asText());
		assertEquals("TAEKWONDO", peques.get("disciplina").asText());
		assertEquals("Taekwondo X", peques.get("disciplinaNombre").asText());
		assertEquals(4, peques.get("rangoEdadMin").asInt());
		assertEquals(6, peques.get("rangoEdadMax").asInt());
		assertEquals(List.of(turnoLunes, turnoMiercoles), ids(peques.get("turnos"), "id"), "lunes antes que miércoles");
		JsonNode lunes = peques.get("turnos").get(0);
		assertEquals("Lunes", lunes.get("diaSemana").asText());
		assertEquals("17:00", lunes.get("horaInicio").asText());
		assertEquals("17:45", lunes.get("horaFin").asText());
		JsonNode b = sedes.get(1);
		assertEquals(List.of(grupoAdultos), ids(b.get("grupos"), "id"), "sin el grupo sin turnos");
		assertTrue(b.get("grupos").get(0).get("rangoEdadMax").isNull());
	}

	/** «Quedan N» / «Lleno» / «Lista de espera» por turno; el grupo, el peor de sus turnos. */
	@Test
	void publicada_anonimo_plazas() throws Exception {
		JsonNode sedes = cuerpo(anonimo(x, WEB)).get("sedes");
		JsonNode peques = sedes.get(0).get("grupos").get(0);
		assertPlazas(peques.get("turnos").get(0).get("plazas"), "QUEDAN", 2);
		assertPlazas(peques.get("turnos").get(1).get("plazas"), "QUEDAN", 1);
		assertPlazas(peques.get("plazas"), "QUEDAN", 1);
		JsonNode infantil = sedes.get(0).get("grupos").get(1);
		assertPlazas(infantil.get("turnos").get(0).get("plazas"), "LLENO", null);
		assertPlazas(infantil.get("plazas"), "LLENO", null);
		JsonNode adultos = sedes.get(1).get("grupos").get(0);
		assertPlazas(adultos.get("turnos").get(0).get("plazas"), "LISTA_ESPERA", null);
		assertPlazas(adultos.get("plazas"), "LISTA_ESPERA", null);
	}

	/** Todas las activas en su orden; por sede, la vigente y las futuras; licencia y examen desde el menor (> 0). */
	@Test
	void publicada_anonimo_tarifas() throws Exception {
		JsonNode web = cuerpo(anonimo(x, WEB));
		JsonNode tarifas = web.get("tarifas");
		assertEquals(List.of(tarifaGeneral, tarifaFamiliar), ids(tarifas, "id"));
		assertEquals("GENERAL", tarifas.get(0).get("codigo").asText());
		assertEquals("Taekwondo X", tarifas.get(0).get("nombre").asText());
		assertEquals("TAEKWONDO", tarifas.get(0).get("disciplina").asText());
		assertEquals(0, tarifas.get(0).get("importe").decimalValue().compareTo(new java.math.BigDecimal("25")));
		assertTrue(tarifas.get(1).get("disciplina").isNull());
		assertEquals(0, tarifas.get(1).get("importeAdulto").decimalValue().compareTo(new java.math.BigDecimal("22")));
		assertEquals(0, tarifas.get(1).get("importeMenor").decimalValue().compareTo(new java.math.BigDecimal("18")));

		JsonNode porSede = web.get("tarifasSede");
		assertEquals(2, porSede.size(), porSede.toString());
		assertEquals(hoy.minusYears(1).withDayOfMonth(1).toString(), porSede.get(0).get("vigenteDesde").asText());
		assertEquals(0, porSede.get(0).get("importe").decimalValue().compareTo(new java.math.BigDecimal("27")));
		assertEquals(hoy.plusYears(1).withDayOfMonth(1).toString(), porSede.get(1).get("vigenteDesde").asText());
		for (JsonNode t : porSede) {
			assertEquals(tarifaGeneral, t.get("tarifaId").asLong());
			assertEquals(sedeA, t.get("sedeId").asLong());
		}

		assertEquals(1, web.get("licencias").size());
		assertEquals("TAEKWONDO", web.at("/licencias/0/disciplina").asText());
		assertEquals("Licencia federativa X", web.at("/licencias/0/concepto").asText());
		assertEquals(45.0, web.at("/licencias/0/precio").asDouble());
		assertEquals(20.0, web.get("examenDesde").asDouble(), "el menor > 0 de X (no el de Y)");
	}

	/** Los 3 próximos visibles, del más cercano al más lejano. */
	@Test
	void publicada_anonimo_proximosEventos() throws Exception {
		JsonNode eventos = cuerpo(anonimo(x, WEB)).get("proximosEventos");
		assertEquals(List.of(eventoMas1, eventoMas2, eventoMas3), ids(eventos, "id"));
		assertEquals("Más 1 X", eventos.get(0).get("titulo").asText());
		assertEquals(hoy.plusDays(1).toString(), eventos.get(0).get("fecha").asText());
		assertFalse(eventos.get(0).get("conImagen").asBoolean());
		assertTrue(eventos.get(1).get("conImagen").asBoolean());
	}

	/** Por orden y clave; la imagen de otra escuela no sale. */
	@Test
	void publicada_anonimo_bloques() throws Exception {
		JsonNode bloques = cuerpo(anonimo(x, WEB)).get("bloques");
		List<String> claves = new ArrayList<>();
		bloques.forEach(b -> claves.add(b.get("clave").asText()));
		assertEquals(List.of("portada.titular", "galeria.1", "galeria.2", "profesor.1.nombre"), claves);
		assertEquals("Titular de X", bloques.get(0).get("texto").asText());
		assertTrue(bloques.get(0).get("imagenUrl").isNull());
		assertTrue(bloques.get(1).get("imagenUrl").asText().endsWith("/imagenes/org-" + x + "/web/galeria-x.jpg"),
				bloques.get(1).toString());
		assertTrue(bloques.get(2).get("imagenUrl").isNull(), "la imagen de Y no sale en X");
		assertEquals("Profesora X", bloques.get(3).get("texto").asText());
	}

	/** Ninguna clave privada en ningún nivel, ni los valores secretos sembrados. */
	@Test
	void publicada_anonimo_sinCamposPrivados() throws Exception {
		MvcResult r = anonimo(x, WEB);
		JsonNode web = cuerpo(r);
		Set<String> claves = claves(web);
		Set<String> privadas = new TreeSet<>(claves);
		privadas.retainAll(CLAVES_PRIVADAS);
		assertEquals(Set.of(), privadas, "claves privadas en " + claves);
		String texto = r.getResponse().getContentAsString();
		for (String secreto : List.of("G00000000", "Club Deportivo X", "Firmante Secreto", "00000000T",
				"Remitente secreto", "sede-secreta@", "CLUB SECRETO", "Tarifa interna", "Pasado X", "Oculto X",
				"Competición X", "Kick X", "Cerrad", "Sin turnos X", "Más 4 X")) {
			assertFalse(texto.contains(secreto), secreto + " en " + texto);
		}
	}

	/** Lo de Y nunca en X y lo de X nunca en Y. */
	@Test
	void aislamiento_entreEscuelas() throws Exception {
		String deX = anonimo(x, WEB).getResponse().getContentAsString()
				+ anonimo(x, LEGAL).getResponse().getContentAsString();
		String deY = anonimo(y, WEB).getResponse().getContentAsString()
				+ anonimo(y, LEGAL).getResponse().getContentAsString();
		for (String deOtra : List.of("Escuela Web Y", "Sede de Y", "Grupo de Y", "Tarifa de Y", "Licencia de Y",
				"Evento de Y", "Titular de Y", "galeria-y", "G99999999", "contacto-y@", "Taekwondo Y")) {
			assertFalse(deX.contains(deOtra), deOtra + " en X");
		}
		for (String deOtra : List.of("Escuela Web X", "Herrera X", "Peques X", "Taekwondo X", "Licencia federativa X",
				"Más 1 X", "Titular de X", "galeria-x", "G00000000", "contacto-x@")) {
			assertFalse(deY.contains(deOtra), deOtra + " en Y");
		}
		JsonNode y1 = json.readTree(anonimo(y, WEB).getResponse().getContentAsString());
		assertEquals(List.of(sedeY), ids(y1.get("sedes"), "id"));
		assertEquals(List.of(eventoY), ids(y1.get("proximosEventos"), "id"));
		assertEquals(5.0, y1.get("examenDesde").asDouble());
	}

	// ------------------------------------------------------------------ legal

	@Test
	void legal_anonimo_titularNormasYFecha() throws Exception {
		MvcResult r = anonimo(x, LEGAL);
		JsonNode legal = cuerpo(r);
		assertEquals("Escuela Web X " + marca, legal.at("/titular/nombre").asText());
		assertEquals("Club Deportivo X", legal.at("/titular/nombreLegal").asText());
		assertEquals("G00000000", legal.at("/titular/cif").asText());
		assertEquals("Calle X 1", legal.at("/titular/direccion").asText());
		assertEquals("Herrera", legal.at("/titular/localidad").asText());
		assertEquals("Sevilla", legal.at("/titular/provincia").asText());
		assertEquals("41567", legal.at("/titular/codigoPostal").asText());
		assertEquals("contacto-x@example.test", legal.at("/titular/email").asText());
		assertEquals("600000001", legal.at("/titular/telefono").asText());
		assertEquals("2026-10-01", legal.get("actualizadaEn").asText());
		assertEquals(1, legal.get("normas").size(), "solo la disciplina activa con plantilla");
		JsonNode normas = legal.get("normas").get(0);
		assertEquals("TAEKWONDO", normas.get("disciplina").asText());
		assertEquals("Taekwondo X", normas.get("disciplinaNombre").asText());
		assertTrue(normas.get("version").asInt() >= 1);
		assertFalse(normas.get("contenido").isNull());
		assertFalse(normas.get("instrucciones").asText().isBlank());

		Set<String> privadas = new TreeSet<>(claves(legal));
		privadas.retainAll(CLAVES_PRIVADAS_LEGAL);
		assertEquals(Set.of(), privadas);
		String texto = r.getResponse().getContentAsString();
		for (String secreto : List.of("Firmante Secreto", "00000000T", "Remitente secreto", "sede-secreta@")) {
			assertFalse(texto.contains(secreto), secreto);
		}
		assertTrue(r.getResponse().getHeader("Cache-Control").contains("public"));
	}

	// ------------------------------------------------------------------ sin publicar y vista previa

	@Test
	void sinPublicar_anonimo_404_tambienConVistaPrevia() throws Exception {
		assertEquals(404, anonimo(z, WEB).getResponse().getStatus());
		assertEquals(404, anonimo(z, LEGAL).getResponse().getStatus());
		assertEquals(404, anonimo(z, get(WEB).param("vistaPrevia", "true")).getResponse().getStatus());
		assertEquals(404, anonimo(z, get(LEGAL).param("vistaPrevia", "true")).getResponse().getStatus());
		assertEquals(404, anonimo(w, WEB).getResponse().getStatus(), "sin fila");
	}

	/** El propietario ve la vista previa de su web sin publicar; sin el parámetro, lo publicado (nada: 404). */
	@Test
	void sinPublicar_vistaPreviaDelPropietario_200_sinCache() throws Exception {
		Cookie sesion = sesion(propietarioZ, z);
		MvcResult r = mvc.perform(get(WEB).param("vistaPrevia", "true").cookie(sesion)).andReturn();
		assertEquals(200, r.getResponse().getStatus());
		JsonNode web = cuerpo(r);
		assertEquals("Escuela Web Z " + marca, web.at("/escuela/nombre").asText());
		assertEquals("linea-comarcal", web.get("tema").asText());
		assertFalse(web.get("publicada").asBoolean());
		assertTrue(web.get("vistaPrevia").asBoolean());
		assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"));
		assertVaria(r);
		MvcResult legal = mvc.perform(get(LEGAL).param("vistaPrevia", "true").cookie(sesion)).andReturn();
		assertEquals(200, legal.getResponse().getStatus());
		assertTrue(legal.getResponse().getHeader("Cache-Control").contains("no-store"));
		assertVaria(legal);
		assertTrue(cuerpo(legal).get("actualizadaEn").isNull());
		assertEquals(404, mvc.perform(get(WEB).cookie(sesion)).andReturn().getResponse().getStatus());
	}

	/** Con vistaPrevia en la URL nunca se guarda en caché, aunque sea anónima y salga lo publicado. */
	@Test
	void vistaPreviaAnonima_publicada_200_sinCache() throws Exception {
		for (String url : List.of(WEB, LEGAL)) {
			MvcResult r = anonimo(x, get(url).param("vistaPrevia", "true"));
			assertEquals(200, r.getResponse().getStatus(), url);
			assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"), url);
			assertVaria(r);
		}
		assertFalse(cuerpo(anonimo(x, get(WEB).param("vistaPrevia", "true"))).get("vistaPrevia").asBoolean());
	}

	/** Familia, profesor y responsable de sede no son gestión: su vista previa se ignora y sin publicar es 404. */
	@Test
	void sinPublicar_vistaPreviaSinRolDeGestion_404() throws Exception {
		for (long cuenta : List.of(familiaZ, profesorZ, responsableZ)) {
			Cookie sesion = sesion(cuenta, z);
			assertEquals(200, mvc.perform(get("/api/auth/permisos").cookie(sesion)).andReturn().getResponse().getStatus(),
					"la sesión vale (cuenta " + cuenta + ")");
			assertEquals(404, mvc.perform(get(WEB).param("vistaPrevia", "true").cookie(sesion)).andReturn().getResponse()
					.getStatus(), "cuenta " + cuenta);
			assertEquals(404, mvc.perform(get(LEGAL).param("vistaPrevia", "true").cookie(sesion)).andReturn()
					.getResponse().getStatus(), "cuenta " + cuenta);
		}
	}

	/** El gestor de una escuela sin fila también la previsualiza (primera publicación): sin tema. */
	@Test
	void sinFila_vistaPreviaDelGestor_200_sinTema() throws Exception {
		MvcResult r = mvc.perform(get(WEB).param("vistaPrevia", "true").cookie(sesion(gestorW, w))).andReturn();
		assertEquals(200, r.getResponse().getStatus());
		JsonNode web = cuerpo(r);
		assertTrue(web.get("tema").isNull());
		assertFalse(web.get("publicada").asBoolean());
		assertEquals(List.of(), ids(web.get("sedes"), "id"), "W no tiene sedes sembradas");
	}

	// ------------------------------------------------------------------ sentencias

	/**
	 * Sin N+1: X (3 sedes, 7 grupos, 7 turnos, 4 bloques, 7 eventos) y Y (una de cada) cuestan las mismas sentencias.
	 * No cuenta la fila de auditoría que el filtro de auditoría apunta en cada petición (no es del endpoint).
	 */
	@Test
	void sentencias_lasMismasConMasDatos() throws Exception {
		anonimo(x, WEB);
		anonimo(y, WEB);
		List<String> deX = sentencias(() -> anonimo(x, WEB));
		List<String> deY = sentencias(() -> anonimo(y, WEB));
		assertEquals(deY.size(), deX.size(), "mismas sentencias con más sedes, grupos, turnos y eventos: " + deX);
		assertTrue(deX.size() <= 17, "sentencias: " + deX);
		List<String> legalX = sentencias(() -> anonimo(x, LEGAL));
		List<String> legalY = sentencias(() -> anonimo(y, LEGAL));
		assertEquals(legalY.size(), legalX.size());
		assertTrue(legalX.size() <= 3, "sentencias de legal: " + legalX);
		List<String> moiskimdo = sentencias(() -> mvc.perform(get(WEB)).andReturn());
		assertEquals(1, moiskimdo.size(), "Moiskimdo sale tras leer web_escuela: " + moiskimdo);
		assertTrue(moiskimdo.get(0).contains("web_escuela"), moiskimdo.toString());
	}

	private static List<String> sentencias(java.util.concurrent.Callable<?> accion) throws Exception {
		return ContadorSentencias.contar(accion).sentencias().stream()
				.filter(s -> !s.startsWith("insert into auditoria_evento")).toList();
	}

	// ------------------------------------------------------------------ utilidades

	/** Petición anónima con el Host de {@code org} (su fila de {@code organizacion_dominio}). */
	private MvcResult anonimo(long org, String url) throws Exception {
		return anonimo(org, get(url));
	}

	private MvcResult anonimo(long org, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Host", hosts.get(org))).andReturn();
	}

	/** La respuesta cambia con la sesión: una caché no debe reutilizar la anónima para una vista previa. */
	private static void assertVaria(MvcResult r) {
		String vary = String.join(",", r.getResponse().getHeaders("Vary"));
		assertTrue(vary.contains("Cookie") && vary.contains("Authorization"), "Vary: " + vary);
	}

	private JsonNode cuerpo(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static Set<String> claves(JsonNode nodo) {
		Set<String> claves = new TreeSet<>();
		if (nodo.isObject()) {
			for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
				String nombre = it.next();
				claves.add(nombre);
				claves.addAll(claves(nodo.get(nombre)));
			}
		} else if (nodo.isArray()) {
			nodo.forEach(hijo -> claves.addAll(claves(hijo)));
		}
		return claves;
	}

	private static void assertPlazas(JsonNode plazas, String estado, Integer quedan) {
		assertEquals(estado, plazas.get("estado").asText(), plazas.toString());
		if (quedan == null) {
			assertTrue(plazas.get("quedan").isNull(), plazas.toString());
		} else {
			assertEquals(quedan.intValue(), plazas.get("quedan").asInt(), plazas.toString());
		}
	}

	private Cookie sesion(long usuarioId, long org) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return new Cookie("jwt", jwt.generateToken(usuario, 600_000, org));
	}

	private long organizacion(String sufijo, String nombre) {
		String slug = "it-w1-" + sufijo + "-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, nombre);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	private long disciplina(long org, String codigo, String nombre, boolean activa) {
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden, activa)
				VALUES (?, ?, ?, NULL, 14, 1, ?)""", org, codigo, nombre, activa);
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = ?", Long.class, org,
				codigo);
	}

	private void limite(long org, int limite) {
		jdbc.update("INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (?, 'limite_turno', ?)",
				org, String.valueOf(limite));
	}

	/** La plantilla activa de Moiskimdo, copiada a la escuela sintética. */
	private void plantilla(long org, long disciplina) {
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", org, disciplina);
	}

	private long sede(long org, String nombre, Integer aforo, boolean activa) {
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, aforo_turno, activa) VALUES (?, ?, ?, ?)", org, nombre,
				aforo, activa);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	private long grupo(long org, long disciplina, long sede, String nombre, Integer edadMin, Integer edadMax) {
		jdbc.update("""
				INSERT INTO grupo (nombre, organizacion_id, disciplina_id, sede_id, rango_edad_min, rango_edad_max)
				VALUES (?, ?, ?, ?, ?, ?)""", nombre, org, disciplina, sede, edadMin, edadMax);
		return jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = ?", Long.class, org);
	}

	private long turno(long org, long grupo, String dia, String inicio, String fin) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, ?, ?, NULL, ?, ?)""", dia, inicio, fin, grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	private void preinscripcion(long org, long disciplina, long grupo, String estado, long turno) {
		String referencia = "W1" + marca.toUpperCase() + (++preinscripciones);
		long plantilla = jdbc.queryForObject(
				"SELECT MAX(id) FROM plantilla_preinscripcion WHERE organizacion_id = ? AND disciplina_id = ?", Long.class,
				org, disciplina);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, ?, ?, 'Solicitud', 'Sintética W1', NULL, ?, '2015-05-05', 'Calle 1', '600000000', ?, 0, 0, 1,
				  'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2026-09-10 12:00:00', '2026-09-10 12:00:00', ?, ?, ?)""",
				referencia, temporada, estado, UUID.randomUUID().toString().replace("-", "").repeat(2),
				"w1-" + referencia.toLowerCase() + "@example.test", plantilla, "e".repeat(64), grupo, org, disciplina);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
	}

	private long tarifa(long org, Long disciplina, String codigo, String nombre, String importe, String adulto,
			String menor, boolean activa, int orden) {
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, importe_adulto, importe_menor,
				  activa, orden)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", org, disciplina, codigo, nombre, new java.math.BigDecimal(importe),
				adulto == null ? null : new java.math.BigDecimal(adulto),
				menor == null ? null : new java.math.BigDecimal(menor), activa, orden);
		return jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = ?", Long.class, org,
				codigo);
	}

	private void tarifaSede(long org, long tarifa, long sede, String importe, LocalDate desde) {
		jdbc.update("""
				INSERT INTO tarifa_sede (organizacion_id, tarifa_id, sede_id, importe, vigente_desde)
				VALUES (?, ?, ?, ?, ?)""", org, tarifa, sede, new java.math.BigDecimal(importe), java.sql.Date.valueOf(desde));
	}

	private long producto(long org, String concepto, double precio, String tipo, Long disciplina) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, organizacion_id, tipo, disciplina_id, vendible_profesor)
				VALUES (?, ?, ?, ?, ?, FALSE)""", concepto, precio, org, tipo, disciplina);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, org);
	}

	private long imagen(long org, String nombre) {
		String ruta = "org-" + org + "/web/" + nombre + ".jpg";
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/jpeg', ?, ?)",
				nombre + ".jpg", ruta, "/imagenes/" + ruta, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, org);
	}

	private long evento(long org, String titulo, LocalDate fecha, boolean visible, Long foto) {
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, fecha_evento, foto_evento_id)
				VALUES (?, 'Descripción de prueba', ?, 1000, ?, ?, ?)""", titulo, visible, org,
				fecha == null ? null : java.sql.Date.valueOf(fecha), foto);
		return jdbc.queryForObject("SELECT MAX(id) FROM evento WHERE organizacion_id = ?", Long.class, org);
	}

	private void bloque(long org, String clave, String texto, Long imagen, int orden) {
		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave, texto, imagen_id, orden) VALUES (?, ?, ?, ?, ?)", org,
				clave, texto, imagen, orden);
	}

	private void web(long org, String tema, boolean publicada, String publicadaEn) {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada, publicada_en) VALUES (?, ?, ?, ?)", org,
				tema, publicada, publicadaEn);
	}

	/** Alumno activo vinculado a mano a una cuenta de familia (le abre la sesión en {@code org}). */
	private void alumnoDe(long org, long usuario) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Familia W1', ?, '600000000', 0, ?)""",
				expediente, org);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, alumno, org);
	}

	private long cuenta(String nombre, long org, String rol) {
		String email = "it-w1-" + nombre + "-" + marca + "@example.test";
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", id, org, rol);
		return id;
	}
}
