package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.taemoi.project.acceso.GestionPorSede;
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
 * administrador de plataforma no la escribe la aplicación; y las entidades nuevas se guardan y leen. (La tabla
 * heredada {@code usuario_rol} ya no existe: V32.)
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
	void taemoi_grupoNuevoEnUmbrete_yElJsonLlevaSuSede() throws Exception {
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 AND nombre = 'Umbrete'", Long.class);
		GrupoConAlumnosDTO creado = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(dto("IT sede Taemoi")));

		assertEquals(umbrete, sedeDe(creado.getId()));
		// Entrega 6-1b (D6-5): el DTO de grupos lleva sedeId (campo aditivo); la entidad sigue sin exponerla.
		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(creado));
		assertEquals(umbrete, json.get("sedeId").asLong(), json.toString());
		assertFalse(json.has("sede") || json.has("sede_id"), json.toString());
		Grupo entidad = TenantContext.calcularCon(ORG1, () -> grupos.findById(creado.getId()).orElseThrow());
		JsonNode jsonEntidad = objectMapper.readTree(objectMapper.writeValueAsString(entidad));
		assertFalse(jsonEntidad.has("sedeId") || jsonEntidad.has("sede"), jsonEntidad.toString());
		borrarGrupo(creado.getId());
	}

	@Test
	void actualizarGrupo_conservaLaSede() {
		// Desde la V32_1 no hay grupos sin sede: ya no se prueba completar la que falte.
		GrupoConAlumnosDTO creado = TenantContext.calcularCon(ORG1, () -> grupoService.crearGrupo(dto("IT sede conserva")));
		long otraSede = TenantContext.calcularCon(ORG1, () -> {
			Sede sede = new Sede();
			sede.setNombre("IT inactiva " + UUID.randomUUID());
			sede.setActiva(false);
			return sedes.save(sede).getId();
		});
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", otraSede, creado.getId());
		TenantContext.ejecutarCon(ORG1, () -> grupoService.actualizarGrupo(creado.getId(), dto("IT sede conserva 2")));
		assertEquals(otraSede, sedeDe(creado.getId()), "un grupo con sede la conserva");
		borrarGrupo(creado.getId());
		jdbc.update("DELETE FROM sede WHERE id = ?", otraSede);
	}

	@Test
	void grupoSinSede_laUnicaActiva_ningunaActiva400_variasObligatoria() {
		// E10-4: sin ninguna sede activa el alta ya no deja el grupo sin sede: 400 y no se crea.
		long sinSede = organizacion("it-sin-sede");
		IllegalArgumentException ninguna = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(sinSede, () -> grupoService.crearGrupo(dto("IT sin sede"))));
		assertEquals(GestionPorSede.SIN_SEDE_ACTIVA, ninguna.getMessage());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE organizacion_id = ?", Integer.class,
				sinSede));

		long dosSedes = organizacion("it-dos-sedes");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'Una'), (?, 'Otra')", dosSedes, dosSedes);
		IllegalArgumentException sinElegir = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(dosSedes, () -> grupoService.crearGrupo(dto("IT dos sedes"))));
		assertEquals("Indica la sede.", sinElegir.getMessage());

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
