package com.taemoi.project.integracion;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

/**
 * Escuela sintética de la entrega 6-1 con tres sedes: A y B activas, C inactiva (D6-17). Datos inventados.
 *
 * <pre>
 * grupos   gA, gA2 (sede A), gB (sede B), gC (sede C, inactiva), gSin (sin sede)
 * alumnos  aA        sede principal A
 *          aGrupoA   sede principal B, en gA y gB  (D9: lo ve A por grupo; lo edita solo B, D6-3)
 *          aSinSede  sin sede principal, en gA     (D6-1: lo ve A; nadie lo edita salvo gestión)
 *          aB        sede principal B, en gB       (fuera del alcance de A)
 *          aNada     sin sede ni grupo             (solo gestión)
 *          aInactivoA sede principal A, de baja
 * turnos   tA (lunes, gA, con aA), tB (lunes, gB, con aB), tSin (sin grupo)
 * cuentas  FAMILIA + RESPONSABLE_SEDE (la puerta de entrada exige FAMILIA, D6-15), cada una con su alumno propio
 *          sin sede: respA (sede A) y respAC (sedes A y C); y un gestor (ADMINISTRADOR).
 * </pre>
 * TAEKWONDO activo: aA (apto), aB (apto), aGrupoA (no apto).
 */
final class EscuelaConSedesDePrueba {

	static final long ORG1 = 1L;

	final JdbcTemplate jdbc;
	final String marca = UUID.randomUUID().toString().substring(0, 8);

	long org, disciplina, sA, sB, sC;
	long gA, gA2, gB, gC, gSin;
	long aA, aGrupoA, aSinSede, aB, aNada, aInactivoA;
	long tA, tB, tSin;
	long respA, respAC, gestor, propioA, propioAC;

	private EscuelaConSedesDePrueba(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	static EscuelaConSedesDePrueba crear(JdbcTemplate jdbc, GrupoService grupos, UsuarioRepository usuarios,
			String prefijo) {
		EscuelaConSedesDePrueba e = new EscuelaConSedesDePrueba(jdbc);
		String slug = prefijo + "-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT sedes')", slug);
		e.org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", e.org);
		e.disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, e.org);
		e.sA = e.sede("A");
		e.sB = e.sede("B");
		e.sC = e.sede("C");

		e.gA = e.grupo(grupos, "IT E6 gA " + e.marca, e.sA);
		e.gA2 = e.grupo(grupos, "IT E6 gA2 " + e.marca, e.sA);
		e.gB = e.grupo(grupos, "IT E6 gB " + e.marca, e.sB);
		e.gC = e.grupo(grupos, "IT E6 gC " + e.marca, e.sC);
		e.gSin = e.grupo(grupos, "IT E6 gSin " + e.marca, null);
		jdbc.update("UPDATE grupo SET sede_id = NULL WHERE id = ?", e.gSin);
		jdbc.update("UPDATE sede SET activa = FALSE WHERE id = ?", e.sC);

		e.aA = e.alumno("E6A" + e.marca, e.sA, true);
		e.aGrupoA = e.alumno("E6GRUPOA" + e.marca, e.sB, true);
		e.aSinSede = e.alumno("E6SINSEDE" + e.marca, null, true);
		e.aB = e.alumno("E6B" + e.marca, e.sB, true);
		e.aNada = e.alumno("E6NADA" + e.marca, null, true);
		e.aInactivoA = e.alumno("E6INACTIVOA" + e.marca, e.sA, false);
		e.miembro(e.gA, e.aGrupoA);
		e.miembro(e.gB, e.aGrupoA);
		e.miembro(e.gA, e.aSinSede);
		e.miembro(e.gB, e.aB);

		e.tA = e.turno(e.gA);
		e.tB = e.turno(e.gB);
		e.tSin = e.turno(null);
		e.enTurno(e.tA, e.aA);
		e.enTurno(e.tB, e.aB);

		e.taekwondo(e.aA, true);
		e.taekwondo(e.aB, true);
		e.taekwondo(e.aGrupoA, false);

		e.respA = e.cuentaFamilia(usuarios, "respa");
		e.propioA = e.ultimoAlumnoVinculado(e.respA);
		e.responsable(e.respA, e.sA);
		e.respAC = e.cuentaFamilia(usuarios, "respac");
		e.propioAC = e.ultimoAlumnoVinculado(e.respAC);
		e.responsable(e.respAC, e.sA, e.sC);
		e.gestor = e.cuenta(usuarios, "gestor");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				e.gestor, e.org);
		return e;
	}

	String token(JwtService jwt, UsuarioRepository usuarios, long usuarioId) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return jwt.generateToken(usuario, 600_000, org);
	}

	Long sedeDe(long alumno) {
		return jdbc.queryForObject("SELECT sede_principal_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	// ------------------------------------------------------------------ datos

	private long sede(String nombre) {
		String n = "IT E6 " + nombre + " " + marca;
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", org, n);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org, n);
	}

	private long grupo(GrupoService grupos, String nombre, Long sede) {
		GrupoConAlumnosDTO dto = new GrupoConAlumnosDTO();
		dto.setNombre(nombre);
		dto.setDeporte("TAEKWONDO");
		dto.setSedeId(sede);
		return TenantContext.calcularCon(org, () -> grupos.crearGrupo(dto).getId());
	}

	long alumno(String nombre, Long sedePrincipal, boolean activo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (?, 'PRUEBA', 1, 'Calle 1', '2010-01-01', ?, ?, '600000000', 0, ?, ?)""",
				activo, nombre, expediente, org, sedePrincipal);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private void miembro(long grupo, long alumno) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
	}

	private long turno(Long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', NULL, ?, ?)""", grupo, org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	private void enTurno(long turno, long alumno) {
		jdbc.update("INSERT INTO alumno_turno (turno_id, alumno_id) VALUES (?, ?)", turno, alumno);
	}

	private void taekwondo(long alumno, boolean apto) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, ?, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?)""", apto, alumno, org, disciplina);
	}

	private long cuenta(UsuarioRepository usuarios, String nombre) {
		String email = "it-e6-" + nombre + "-" + marca + "@example.test";
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
	}

	/** Cuenta FAMILIA con un alumno propio vinculado (abre la puerta de entrada de familia). */
	private long cuentaFamilia(UsuarioRepository usuarios, String nombre) {
		long id = cuenta(usuarios, nombre);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", id, org);
		long propio = alumno("E6PROPIO" + nombre.toUpperCase() + marca, null, true);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", id, propio, org);
		return id;
	}

	private void responsable(long usuario, long... sedes) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'RESPONSABLE_SEDE')",
				usuario, org);
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, usuario, org);
		for (long sede : sedes) {
			jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
		}
	}

	private long ultimoAlumnoVinculado(long usuarioId) {
		return jdbc.queryForObject("SELECT MAX(alumno_id) FROM usuario_alumno WHERE usuario_id = ?", Long.class,
				usuarioId);
	}
}
