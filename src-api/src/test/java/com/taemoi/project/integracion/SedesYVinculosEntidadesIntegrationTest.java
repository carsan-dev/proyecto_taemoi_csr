package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AsignacionProfesor;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.MembresiaSede;
import com.taemoi.project.entities.OrigenUsuarioAlumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AsignacionProfesorRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.MembresiaSedeRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;

/**
 * Fase 3, entrega 1, con la aplicación arrancada sobre V31 ({@code ddl-auto=validate}): los grupos creados por
 * la API reciben la única sede activa de su escuela (Taemoi: «Umbrete») sin cambiar el JSON; la marca de
 * administrador de plataforma no la escribe la aplicación; borrar un usuario con filas heredadas de
 * {@code usuario_rol} funciona sin el mapeo {@code Usuario.roles}; y las entidades nuevas se guardan y leen.
 * Datos sintéticos.
 */
@SpringBootTest
class SedesYVinculosEntidadesIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;

	@Autowired GrupoService grupoService;
	@Autowired GrupoRepository grupos;
	@Autowired SedeRepository sedes;
	@Autowired UsuarioRepository usuarios;
	@Autowired UsuarioAlumnoRepository usuarioAlumnos;
	@Autowired AsignacionProfesorRepository asignaciones;
	@Autowired MembresiaSedeRepository membresiaSedes;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void taemoi_grupoNuevoEnUmbrete_yElJsonNoCambia() throws Exception {
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 AND nombre = 'Umbrete'", Long.class);
		GrupoConAlumnosDTO creado = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(dto("IT sede Taemoi")));

		assertEquals(umbrete, sedeDe(creado.getId()));
		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(creado));
		assertFalse(json.has("sede") || json.has("sedeId") || json.has("sede_id"), json.toString());
		Grupo entidad = TenantContext.calcularCon(ORG1, () -> grupos.findById(creado.getId()).orElseThrow());
		JsonNode jsonEntidad = objectMapper.readTree(objectMapper.writeValueAsString(entidad));
		assertFalse(jsonEntidad.has("sedeId") || jsonEntidad.has("sede"), jsonEntidad.toString());
		borrarGrupo(creado.getId());
	}

	@Test
	void actualizarGrupo_conservaLaSede_yCompletaLaQueFalte() {
		GrupoConAlumnosDTO creado = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(dto("IT sede conserva")));
		long umbrete = sedeDe(creado.getId());
		long otraSede = TenantContext.calcularCon(ORG1, () -> {
			Sede sede = new Sede();
			sede.setNombre("IT inactiva " + UUID.randomUUID());
			sede.setActiva(false);
			return sedes.save(sede).getId();
		});
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", otraSede, creado.getId());
		TenantContext.ejecutarCon(ORG1, () -> grupoService.actualizarGrupo(creado.getId(), dto("IT sede conserva 2")));
		assertEquals(otraSede, sedeDe(creado.getId()), "un grupo con sede la conserva");

		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", creado.getId());
		TenantContext.ejecutarCon(ORG1, () -> grupoService.actualizarGrupo(creado.getId(), dto("IT sede conserva 3")));
		assertEquals(umbrete, sedeDe(creado.getId()), "una sede inactiva no cuenta: queda la única activa");
		jdbc.update("DELETE FROM sede WHERE id = ?", otraSede);
		borrarGrupo(creado.getId());
	}

	@Test
	void escuelaSinSedeOConVarias_grupoSinSede() {
		long sinSede = organizacion("it-sin-sede");
		GrupoConAlumnosDTO a = TenantContext.calcularCon(sinSede, () -> grupoService.crearGrupo(dto("IT sin sede")));
		assertNull(sedeDe(a.getId()));

		long dosSedes = organizacion("it-dos-sedes");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'Una'), (?, 'Otra')", dosSedes, dosSedes);
		GrupoConAlumnosDTO b = TenantContext.calcularCon(dosSedes, () -> grupoService.crearGrupo(dto("IT dos sedes")));
		assertNull(sedeDe(b.getId()));

		long unaSede = organizacion("it-una-sede");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'Unica')", unaSede);
		GrupoConAlumnosDTO c = TenantContext.calcularCon(unaSede, () -> grupoService.crearGrupo(dto("IT una sede")));
		assertEquals(jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ?", Long.class, unaSede), sedeDe(c.getId()));
	}

	@Test
	void adminPlataforma_laAplicacionNuncaLaEscribe() throws Exception {
		Usuario usuario = crearUsuario("it-plataforma-" + UUID.randomUUID() + "@example.test");
		assertFalse(usuario.isEsAdminPlataforma());
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT es_admin_plataforma FROM usuario WHERE id = ?",
				Boolean.class, usuario.getId()));

		jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", usuario.getId());
		usuario.setNombre("Renombrado");
		TenantContext.calcularCon(ORG1, () -> usuarios.save(usuario));
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT es_admin_plataforma FROM usuario WHERE id = ?",
				Boolean.class, usuario.getId()), "guardar un usuario cargado antes no borra la marca");
		Usuario recargado = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuario.getId()).orElseThrow());
		assertTrue(recargado.isEsAdminPlataforma());

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(recargado));
		assertFalse(json.has("esAdminPlataforma") || json.has("roles"), json.toString());
		jdbc.update("UPDATE usuario SET es_admin_plataforma = FALSE WHERE id = ?", usuario.getId());
	}

	@Test
	void borrarUsuarioConFilasHeredadasDeUsuarioRol() {
		Usuario usuario = crearUsuario("it-borrar-rol-" + UUID.randomUUID() + "@example.test");
		jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", usuario.getId());
		TenantContext.ejecutarCon(ORG1, () -> usuarios.deleteById(usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ?", Integer.class, usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ?", Integer.class,
				usuario.getId()));
	}

	@Test
	void entidadesNuevas_seGuardanYSeLeenFiltrandoPorOrganizacion() {
		Usuario usuario = crearUsuario("it-entidades-" + UUID.randomUUID() + "@example.test");
		long grupoId = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(dto("IT asignacion")).getId());
		long alumnoId = jdbc.queryForObject("""
				SELECT COALESCE(MAX(numero_expediente), 70000) + 1 FROM alumno""", Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno IT', ?, '600000000', 0, 1, 'it@example.test')""",
				alumnoId);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1",
				Long.class, alumnoId);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROFESOR')", usuario.getId());
		long membresia = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ?", Long.class, usuario.getId());
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 AND nombre = 'Umbrete'", Long.class);

		TenantContext.ejecutarCon(ORG1, () -> {
			usuarioAlumnos.save(new UsuarioAlumno(usuario.getId(), alumno, ORG1, RelacionUsuarioAlumno.TUTOR,
					OrigenUsuarioAlumno.MANUAL));
			asignaciones.save(new AsignacionProfesor(ORG1, usuario.getId(), grupoId, RolAsignacionProfesor.TITULAR));
			membresiaSedes.save(new MembresiaSede(membresia, umbrete));
		});

		TenantContext.ejecutarCon(ORG1, () -> {
			List<UsuarioAlumno> vinculos = usuarioAlumnos.findByUsuarioIdAndOrganizacionId(usuario.getId(), ORG1);
			assertEquals(1, vinculos.size());
			assertEquals(RelacionUsuarioAlumno.TUTOR, vinculos.get(0).getRelacion());
			assertEquals(OrigenUsuarioAlumno.MANUAL, vinculos.get(0).getOrigen());
			assertTrue(usuarioAlumnos.findByUsuarioIdAndOrganizacionId(usuario.getId(), 999L).isEmpty());
			assertEquals(RolAsignacionProfesor.TITULAR,
					asignaciones.findByUsuarioIdAndOrganizacionId(usuario.getId(), ORG1).get(0).getRol());
			assertEquals(List.of(umbrete), membresiaSedes.findByMembresiaId(membresia).stream()
					.map(MembresiaSede::getSedeId).toList());
			assertEquals(List.of("Umbrete"), sedes.findAllByOrderByIdAsc().stream().map(Sede::getNombre)
					.filter("Umbrete"::equals).toList());
		});
		assertEquals("MANUAL", jdbc.queryForObject("SELECT origen FROM usuario_alumno WHERE usuario_id = ?", String.class,
				usuario.getId()));

		TenantContext.ejecutarCon(ORG1, () -> usuarios.deleteById(usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ?", Integer.class,
				usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = ?", Integer.class,
				usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM membresia_sede WHERE membresia_id = ?", Integer.class,
				membresia));
		jdbc.update("DELETE FROM alumno WHERE id = ?", alumno);
		borrarGrupo(grupoId);
	}

	// ---------------------------------------------------------------------------------------------

	private void borrarGrupo(long grupoId) {
		jdbc.update("DELETE FROM grupo WHERE id = ?", grupoId);
	}

	private Long sedeDe(long grupoId) {
		return jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupoId);
	}

	private static GrupoConAlumnosDTO dto(String nombre) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		return dto;
	}

	/** Escuela nueva (creada tras V31, sin sede) con la disciplina TAEKWONDO. */
	private long organizacion(String prefijo) {
		String slug = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		return org;
	}

	private Usuario crearUsuario(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}
}
