package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;

import jakarta.servlet.http.Cookie;

/**
 * Fase 4, pieza F4-2: el alta es TODO O NADA. Un fallo forzado a mitad de la copia del catálogo (al guardar las tarifas,
 * después de la escuela, la sede, la cuenta pendiente con su invitación, la membresía y el resto del catálogo) no deja
 * ninguna fila de la escuela nueva ni de su propietario, y no sale ningún correo. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AltaEscuelaDeshechaIntegrationTest extends MySqlIntegrationTest {

	private static final String CLAVE = "Clave-Segura-123";
	/** Tablas con filas de una escuela (y las globales que el alta toca). */
	private static final List<String> TABLAS = List.of("organizacion", "sede", "usuario", "membresia", "disciplina",
			"sistema_grados", "grado", "categoria", "tarifa", "producto", "auditoria_evento");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder encoder;

	@MockitoBean EmailService email;
	@MockitoSpyBean TarifaRepository tarifas;

	final String marca = java.util.UUID.randomUUID().toString().substring(0, 8);
	Cookie cookieSuperadmin;

	@BeforeAll
	void preparar() throws Exception {
		String correo = "it-f42d-superadmin-" + marca + "@example.test";
		Usuario superadmin = TenantContext.calcularCon(1L, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Superadmin");
			usuario.setApellidos("Ficticio");
			usuario.setEmail(correo);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", superadmin.getId()));
		MvcResult login = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andReturn();
		cookieSuperadmin = new Cookie("jwt", json.readTree(login.getResponse().getContentAsString()).get("token").asText());
	}

	@AfterEach
	void limpiar() {
		reset(tarifas);
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void falloAMitadDeLaCopia_noQuedaNada_niSaleCorreo() throws Exception {
		// IllegalStateException: el manejador global la responde (409); otra excepción saldría de MockMvc.
		doThrow(new IllegalStateException("Fallo forzado en la copia")).when(tarifas).saveAll(anyIterable());
		Map<String, Long> antes = recuento();
		long maximoAuditoria = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
		String nombre = "Escuela IT Deshecha " + marca;
		String emailPropietario = "it-f42d-propietario-" + marca + "@example.test";
		String cuerpo = """
				{"nombre": "%s", "emailContacto": "buzon-%s@example.test", "sede": {"nombre": "Sede"},
				 "propietario": {"nombre": "Eva", "apellidos": "Ficticia", "email": "%s"},
				 "disciplinas": ["TAEKWONDO", "KICKBOXING", "PILATES"]}""".formatted(nombre, marca, emailPropietario);

		int estado = mvc.perform(post("/api/plataforma/escuelas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.cookie(cookieSuperadmin)).andReturn().getResponse().getStatus();

		assertEquals(409, estado);
		verify(tarifas).saveAll(anyIterable());
		Map<String, Long> despues = recuento();
		// La única fila nueva: la del filtro HTTP con el 409, en la escuela activa del superadmin.
		despues.put("auditoria_evento", despues.get("auditoria_evento") - jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND estado_http = 409 AND endpoint = ?", Long.class,
				maximoAuditoria, "/api/plataforma/escuelas"));
		assertEquals(antes, despues);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM organizacion WHERE nombre = ?", Long.class, nombre));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, emailPropietario));
		verify(email, never()).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());

		// Sin el fallo, la misma alta sale entera (el id reservado y perdido solo deja un hueco).
		reset(tarifas);
		int otra = mvc.perform(post("/api/plataforma/escuelas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.cookie(cookieSuperadmin)).andReturn().getResponse().getStatus();
		assertEquals(201, otra);
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM organizacion WHERE nombre = ?", Long.class, nombre));
		verify(email).sendEmailConImagen(any(), any(), any(), any(), any(), any(), any());
	}

	private Map<String, Long> recuento() {
		Map<String, Long> filas = new LinkedHashMap<>();
		for (String tabla : TABLAS) {
			filas.put(tabla, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class));
		}
		return filas;
	}
}
