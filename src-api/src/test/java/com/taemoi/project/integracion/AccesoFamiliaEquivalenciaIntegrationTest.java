package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.utils.EmailUtils;

/**
 * Equivalencia del acceso de familia (fase3-sedes-permisos.md §5 y D6, entrega 2a): en Taemoi (organización 1,
 * con {@code comportamiento_legado_taemoi}) el conjunto de alumnos accesibles con la regla nueva
 * ({@code usuario_alumno} + email) es EXACTAMENTE el de la regla por email de antes, cuenta por cuenta y alumno
 * por alumno; en una escuela sin la marca solo cuenta el vínculo explícito.
 *
 * <p>{@code usuario_alumno} se rellena con la MISMA sentencia de V31 (leída del script, limitada a las cuentas
 * de este test) sobre datos que cubren cada caso de §5; después se aplican los cambios que pueden ocurrir tras
 * V31 (degradar a un gestor, cambiar el email de un alumno, alta de un hermano, alumnos nuevos sin fila).
 *
 * <p>La regla antigua es la de los caminos que se sustituyen: sin membresía activa en la escuela no hay sesión
 * (y por tanto nada visible); con ella, los alumnos activos de la escuela con el email normalizado de la cuenta
 * ({@code findAllByEmailIgnoreCaseAndActivoTrue}, un email en blanco nunca vincula). La puerta de entrada
 * antigua se compara con la consulta nativa que usaba ({@code LOWER(email) = LOWER(:email)}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccesoFamiliaEquivalenciaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String V31 = "db/migration/V31__sedes_y_vinculos.sql";
	private static final Pattern FILTRO_V31 = Pattern.compile(
			"(?m)^WHERE NOT EXISTS \\(SELECT 1 FROM usuario_alumno ua");

	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired ContadorService contadores;
	@Autowired MembresiaService membresiaService;
	@Autowired AccesoFamiliaService acceso;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	/** Cuentas por caso, en orden de creación. */
	final Map<String, Long> cuentas = new LinkedHashMap<>();
	/** Alumnos por nombre, de las dos escuelas. */
	final Map<String, Long> fichas = new LinkedHashMap<>();

	@BeforeAll
	void prepararCasosRellenarComoV31YAplicarCambiosPosteriores() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion o = new Organizacion();
			o.setSlug("it-acceso-familia-" + sufijo);
			o.setNombre("Escuela acceso familia " + sufijo);
			return organizaciones.save(o).getId();
		});
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, ORG1));
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, org2));

		// ---- Taemoi (organización 1, con la marca)
		long igual = cuenta("igual", ORG1, RolMembresia.FAMILIA);
		alumno("igual-1", ORG1, email(igual));

		long mayus = cuenta("mayus", ORG1, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET email = ? WHERE id = ?", " " + email(mayus).toUpperCase() + " ", mayus);
		long mayus1 = alumno("mayus-1", ORG1, "otro-" + sufijo + "@example.test");
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", email(mayus).toUpperCase(), mayus1);

		long hermanos = cuenta("hermanos", ORG1, RolMembresia.FAMILIA);
		alumno("hermanos-1", ORG1, email(hermanos));
		alumno("hermanos-2", ORG1, email(hermanos));

		long inactivo = cuenta("inactivo", ORG1, RolMembresia.FAMILIA);
		alumno("inactivo-activo", ORG1, email(inactivo));
		long baja = alumno("inactivo-baja", ORG1, email(inactivo));
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", baja);

		long sinMembresia = cuenta("sin-membresia", null, null);
		alumno("sin-membresia-1", ORG1, email(sinMembresia));

		long membresiaInactiva = cuenta("membresia-inactiva", ORG1, RolMembresia.FAMILIA);
		desactivar(membresiaInactiva, ORG1, RolMembresia.FAMILIA);
		alumno("membresia-inactiva-1", ORG1, email(membresiaInactiva));

		long blanco = cuenta("blanco", ORG1, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET email = '  ' WHERE id = ?", blanco);
		long blanco1 = alumno("blanco-1", ORG1, "blanco-alumno-" + sufijo + "@example.test");
		jdbc.update("UPDATE alumno SET email = '' WHERE id = ?", blanco1);

		long gestorActivo = cuenta("gestor-activo", ORG1, RolMembresia.ADMINISTRADOR);
		vincularDirecto(gestorActivo, alumno("gestor-activo-otro", ORG1, "ga-otro-" + sufijo + "@example.test"));
		alumno("gestor-activo-propio", ORG1, email(gestorActivo));

		long gestorFamilia = cuenta("gestor-y-familia", ORG1, RolMembresia.ADMINISTRADOR);
		dar(gestorFamilia, ORG1, RolMembresia.FAMILIA);
		vincularDirecto(gestorFamilia, alumno("gestor-y-familia-otro", ORG1, "gf-otro-" + sufijo + "@example.test"));
		alumno("gestor-y-familia-propio", ORG1, email(gestorFamilia));

		long degradado = cuenta("gestor-degradado", ORG1, RolMembresia.ADMINISTRADOR);
		dar(degradado, ORG1, RolMembresia.FAMILIA);
		vincularDirecto(degradado, alumno("gestor-degradado-otro", ORG1, "gd-otro-" + sufijo + "@example.test"));
		alumno("gestor-degradado-propio", ORG1, email(degradado));

		long otraEscuela = cuenta("otra-escuela", ORG1, RolMembresia.FAMILIA);
		alumno("otra-escuela-1", ORG1, email(otraEscuela));
		alumno("otra-escuela-2", org2, email(otraEscuela));

		long emailCambiado = cuenta("email-cambiado", ORG1, RolMembresia.FAMILIA);
		alumno("email-cambiado-1", ORG1, email(emailCambiado));
		alumno("email-cambiado-2", ORG1, email(emailCambiado));

		long familiaR1 = cuenta("familia-r1-otro-email", ORG1, RolMembresia.FAMILIA);
		vincularDirecto(familiaR1, alumno("familia-r1-otro", ORG1, "fr1-otro-" + sufijo + "@example.test"));
		alumno("familia-r1-propio", ORG1, email(familiaR1));

		long espacio = cuenta("espacio-alumno", ORG1, RolMembresia.FAMILIA);
		long espacio1 = alumno("espacio-alumno-1", ORG1, email(espacio));
		jdbc.update("UPDATE alumno SET email = CONCAT(email, ' ') WHERE id = ?", espacio1);
		alumno("espacio-alumno-2", ORG1, email(espacio));

		long multi = cuenta("multi-escuela", ORG1, RolMembresia.FAMILIA);
		dar(multi, org2, RolMembresia.FAMILIA);
		alumno("multi-1", ORG1, email(multi));
		alumno("multi-2", org2, email(multi));

		// ---- Escuela sin la marca (antes del relleno)
		long gestor2 = cuenta("org2-gestor-activo", org2, RolMembresia.ADMINISTRADOR);
		vincularDirecto(gestor2, alumno("org2-gestor-activo-otro", org2, "g2-otro-" + sufijo + "@example.test"));

		long degradado2 = cuenta("org2-gestor-degradado", org2, RolMembresia.ADMINISTRADOR);
		dar(degradado2, org2, RolMembresia.FAMILIA);
		vincularDirecto(degradado2, alumno("org2-gestor-degradado-otro", org2, "d2-otro-" + sufijo + "@example.test"));
		alumno("org2-gestor-degradado-propio", org2, email(degradado2));

		rellenarComoV31();

		// ---- Cambios posteriores a V31
		desactivar(degradado, ORG1, RolMembresia.ADMINISTRADOR);
		desactivar(degradado2, org2, RolMembresia.ADMINISTRADOR);
		alumno("hermanos-3-tras-v31", ORG1, email(hermanos));
		alumno("igual-2-tras-v31", ORG1, email(igual));
		long mayus2 = alumno("mayus-2-tras-v31", ORG1, "otro2-" + sufijo + "@example.test");
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", email(mayus).toUpperCase(), mayus2);
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", "cambiado-nuevo-" + sufijo + "@example.test",
				fichas.get("email-cambiado-1"));
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", fichas.get("igual-1"));

		long sinFila2 = cuenta("org2-email-sin-fila", org2, RolMembresia.FAMILIA);
		alumno("org2-email-sin-fila-1", org2, email(sinFila2));

		long registro2 = cuenta("org2-registro", org2, RolMembresia.FAMILIA);
		vincular(registro2, alumno("org2-registro-1", org2, "r2-otro-" + sufijo + "@example.test"), org2, "REGISTRO");

		long manual2 = cuenta("org2-manual", org2, RolMembresia.FAMILIA);
		vincular(manual2, alumno("org2-manual-1", org2, "m2-otro-" + sufijo + "@example.test"), org2, "MANUAL");
		long manualBaja = alumno("org2-manual-baja", org2, "m2-baja-" + sufijo + "@example.test");
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", manualBaja);
		vincular(manual2, manualBaja, org2, "MANUAL");

		long sinActiva2 = cuenta("org2-membresia-inactiva", org2, RolMembresia.FAMILIA);
		desactivar(sinActiva2, org2, RolMembresia.FAMILIA);
		vincular(sinActiva2, alumno("org2-membresia-inactiva-1", org2, "mi2-otro-" + sufijo + "@example.test"),
				org2, "MANUAL");

		// Entrega 5b (D5-3): ser PROFESOR o RESPONSABLE_SEDE no cuenta como gestión. Una fila MIGRACION de otro
		// email no da acceso a quien es profesor y familia (sí a un ADMINISTRADOR: caso «org2-gestor-activo»).
		for (RolMembresia rolNuevo : List.of(RolMembresia.PROFESOR, RolMembresia.RESPONSABLE_SEDE)) {
			String caso = "org2-" + rolNuevo.name().toLowerCase().replace('_', '-') + "-familia";
			long cuentaNueva = cuenta(caso, org2, rolNuevo);
			dar(cuentaNueva, org2, RolMembresia.FAMILIA);
			vincular(cuentaNueva, alumno(caso + "-otro", org2, caso + "-otro-" + sufijo + "@example.test"), org2,
					"MIGRACION");
		}

		// Taemoi, entrega 2b: filas REGISTRO (registro o Google) de alumnos de su email; después la gestión cambia
		// el email de uno y vacía el de otro. Como antes, en Taemoi esos dos dejan de verse (la fila REGISTRO solo
		// cuenta con el email coincidente en las escuelas con la marca).
		long registroT = cuenta("taemoi-registro", ORG1, RolMembresia.FAMILIA);
		for (String ficha : List.of("taemoi-registro-igual", "taemoi-registro-cambiado", "taemoi-registro-vaciado")) {
			vincular(registroT, alumno(ficha, ORG1, email(registroT)), ORG1, "REGISTRO");
		}
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", "tr-cambiado-" + sufijo + "@example.test",
				fichas.get("taemoi-registro-cambiado"));
		jdbc.update("UPDATE alumno SET email = NULL WHERE id = ?", fichas.get("taemoi-registro-vaciado"));

		// Taemoi: un vínculo MANUAL con un alumno de otro email también cuenta.
		long manual1 = cuenta("taemoi-manual", ORG1, RolMembresia.FAMILIA);
		alumno("taemoi-manual-propio", ORG1, email(manual1));
	}

	/** Sin borrar nada: se retiran las cuentas y los alumnos para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		for (Map.Entry<String, Long> cuenta : cuentas.entrySet()) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta.getValue());
			jdbc.update("UPDATE usuario SET email = ?, alumno_id = NULL WHERE id = ?",
					"retirado-eq-" + cuenta.getKey() + "-" + sufijo + "@example.test", cuenta.getValue());
		}
		for (Map.Entry<String, Long> ficha : fichas.entrySet()) {
			jdbc.update("UPDATE alumno SET activo = FALSE, email = ? WHERE id = ?",
					"retirado-eq-alumno-" + ficha.getKey() + "-" + sufijo + "@example.test", ficha.getValue());
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void taemoi_reglaNuevaIgualALaDeEmail_cuentaPorCuentaYAlumnoPorAlumno() {
		// Como al iniciar sesión: hay una organización fijada (la 1) y la consulta filtra la suya.
		TenantContext.ejecutarCon(ORG1, this::taemoi_reglaNuevaIgualALaDeEmail_cuentaPorCuentaYAlumnoPorAlumnoCuerpo);
	}

	private void taemoi_reglaNuevaIgualALaDeEmail_cuentaPorCuentaYAlumnoPorAlumnoCuerpo() {
		List<String> diferencias = new ArrayList<>();
		for (Map.Entry<String, Long> cuenta : cuentas.entrySet()) {
			Usuario usuario = usuarios.findById(cuenta.getValue()).orElseThrow();
			Set<Long> antiguo = reglaAntigua(usuario, ORG1);
			Set<Long> nuevo = new HashSet<>(acceso.idsAccesibles(usuario, ORG1));
			Set<Long> cargados = TenantContext.calcularCon(ORG1, () -> acceso
					.alumnosAccesiblesEnOrganizacionActiva(usuario).stream().map(Alumno::getId)
					.collect(Collectors.toSet()));
			if (!antiguo.equals(nuevo) || !antiguo.equals(cargados)) {
				diferencias.add(cuenta.getKey() + ": antes " + nombres(antiguo) + ", ahora " + nombres(nuevo)
						+ " / cargados " + nombres(cargados));
			}
			if (acceso.tieneAlgunoAccesible(usuario, ORG1) == antiguo.isEmpty()) {
				diferencias.add(cuenta.getKey() + ": tieneAlgunoAccesible distinto (antes " + !antiguo.isEmpty() + ")");
			}
			if (membresiaService.puedeEntrarEn(usuario, ORG1) != puertaAntigua(usuario, ORG1)) {
				diferencias.add(cuenta.getKey() + ": puerta de entrada distinta (antes " + puertaAntigua(usuario, ORG1)
						+ ")");
			}
			for (Map.Entry<String, Long> ficha : fichas.entrySet()) {
				boolean antes = antiguo.contains(ficha.getValue());
				if (acceso.puedeAcceder(usuario, ORG1, ficha.getValue()) != antes) {
					diferencias.add(cuenta.getKey() + " -> " + ficha.getKey() + ": antes " + antes);
				}
			}
		}
		assertTrue(diferencias.isEmpty(), "Diferencias en Taemoi:\n" + String.join("\n", diferencias));
	}

	/** La equivalencia no es vacía: cada caso de §5 da en Taemoi lo esperado (y lo mismo que antes). */
	@Test
	void taemoi_cadaCasoDaLoEsperado() {
		// Como al iniciar sesión: hay una organización fijada (la 1) y la consulta filtra la suya.
		TenantContext.ejecutarCon(ORG1, this::taemoi_cadaCasoDaLoEsperadoCuerpo);
	}

	private void taemoi_cadaCasoDaLoEsperadoCuerpo() {
		esperarEnTaemoi("igual", "igual-2-tras-v31");
		esperarEnTaemoi("mayus", "mayus-1", "mayus-2-tras-v31");
		esperarEnTaemoi("hermanos", "hermanos-1", "hermanos-2", "hermanos-3-tras-v31");
		esperarEnTaemoi("inactivo", "inactivo-activo");
		esperarEnTaemoi("sin-membresia");
		esperarEnTaemoi("membresia-inactiva");
		esperarEnTaemoi("blanco");
		esperarEnTaemoi("gestor-activo", "gestor-activo-propio");
		esperarEnTaemoi("gestor-y-familia", "gestor-y-familia-propio");
		esperarEnTaemoi("gestor-degradado", "gestor-degradado-propio");
		esperarEnTaemoi("otra-escuela", "otra-escuela-1");
		esperarEnTaemoi("email-cambiado", "email-cambiado-2");
		esperarEnTaemoi("familia-r1-otro-email", "familia-r1-propio");
		esperarEnTaemoi("espacio-alumno", "espacio-alumno-2");
		esperarEnTaemoi("multi-escuela", "multi-1");
		esperarEnTaemoi("taemoi-registro", "taemoi-registro-igual");

		// Las filas que harían falta para distinguir los casos existen (V31 las creó).
		assertEquals("MIGRACION", origen("gestor-activo", "gestor-activo-otro"));
		assertEquals("MIGRACION", origen("gestor-degradado", "gestor-degradado-otro"));
		assertEquals("MIGRACION", origen("email-cambiado", "email-cambiado-1"));
		assertEquals("MIGRACION", origen("mayus", "mayus-1"));
		assertEquals("MIGRACION", origen("membresia-inactiva", "membresia-inactiva-1"));
		assertEquals("REGISTRO", origen("taemoi-registro", "taemoi-registro-cambiado"));
		assertEquals("REGISTRO", origen("taemoi-registro", "taemoi-registro-vaciado"));
		assertNull(origen("familia-r1-otro-email", "familia-r1-otro"), "R1 solo familia y otro email: sin fila");
		assertNull(origen("blanco", "blanco-1"), "email en blanco: sin fila");
		assertNull(origen("sin-membresia", "sin-membresia-1"), "R3: sin membresía, sin fila");
		assertNull(origen("otra-escuela", "otra-escuela-2"), "R3: alumno de una escuela sin membresía, sin fila");
	}

	/**
	 * Un vínculo MANUAL da acceso también en Taemoi aunque el email sea otro (REGISTRO y MIGRACION no: ver el caso
	 * «taemoi-registro»). Hoy no existe ninguno (V31 solo crea filas MIGRACION y las altas, REGISTRO): la fila se
	 * quita al terminar para no alterar la equivalencia, que describe los datos de hoy.
	 */
	@Test
	void taemoi_vinculoExplicitoConOtroEmailTambienDaAcceso() {
		// Como al iniciar sesión: hay una organización fijada (la 1) y la consulta filtra la suya.
		TenantContext.ejecutarCon(ORG1, this::taemoi_vinculoExplicitoConOtroEmailTambienDaAccesoCuerpo);
	}

	private void taemoi_vinculoExplicitoConOtroEmailTambienDaAccesoCuerpo() {
		long cuenta = cuentas.get("taemoi-manual");
		long otro = alumno("taemoi-manual-otro", ORG1, "tm-otro-" + sufijo + "@example.test");
		Usuario usuario = usuarios.findById(cuenta).orElseThrow();
		assertFalse(acceso.puedeAcceder(usuario, ORG1, otro));

		vincular(cuenta, otro, ORG1, "MANUAL");
		try {
			assertTrue(acceso.puedeAcceder(usuario, ORG1, otro));
			assertEquals(Set.of(fichas.get("taemoi-manual-propio"), otro),
					new HashSet<>(acceso.idsAccesibles(usuario, ORG1)));
		} finally {
			jdbc.update("DELETE FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?", cuenta, otro);
		}
	}

	@Test
	void escuelaSinMarca_soloCuentaElVinculoExplicito() {
		// Como al iniciar sesión: hay una organización fijada (la 1) y la consulta filtra la suya.
		TenantContext.ejecutarCon(ORG1, this::escuelaSinMarca_soloCuentaElVinculoExplicitoCuerpo);
	}

	private void escuelaSinMarca_soloCuentaElVinculoExplicitoCuerpo() {
		esperarEn(org2, "multi-escuela", "multi-2");
		esperarEn(org2, "org2-email-sin-fila");
		esperarEn(org2, "org2-registro", "org2-registro-1");
		esperarEn(org2, "org2-manual", "org2-manual-1");
		esperarEn(org2, "org2-membresia-inactiva");
		esperarEn(org2, "org2-gestor-activo", "org2-gestor-activo-otro");
		esperarEn(org2, "org2-gestor-degradado", "org2-gestor-degradado-propio");
		esperarEn(org2, "org2-profesor-familia");
		esperarEn(org2, "org2-responsable-sede-familia");
		// Cuentas solo de Taemoi: nada en la otra escuela, aunque un alumno de allí tenga su email.
		esperarEn(org2, "otra-escuela");
		esperarEn(org2, "igual");

		Usuario sinFila = usuarios.findById(cuentas.get("org2-email-sin-fila")).orElseThrow();
		assertFalse(acceso.tieneAlgunoAccesible(sinFila, org2), "email igual sin fila: no entra");
		assertFalse(membresiaService.puedeEntrarEn(sinFila, org2));
		assertFalse(acceso.puedeAcceder(sinFila, org2, fichas.get("org2-email-sin-fila-1")));

		Usuario registro = usuarios.findById(cuentas.get("org2-registro")).orElseThrow();
		assertTrue(membresiaService.puedeEntrarEn(registro, org2));
		// La fila de una escuela no sirve en otra.
		assertFalse(acceso.puedeAcceder(registro, ORG1, fichas.get("org2-registro-1")));
	}

	// ------------------------------------------------------------------ reglas antiguas (oráculo)

	private Set<Long> reglaAntigua(Usuario usuario, long organizacionId) {
		if (membresiaService.autoridadesEn(usuario.getId(), organizacionId).isEmpty()) {
			return Set.of(); // sin membresía activa no hay sesión en esa escuela
		}
		String email = EmailUtils.normalizeEmail(usuario.getEmail());
		if (!EmailUtils.puedeVincularAlumnos(email)) {
			return Set.of();
		}
		return TenantContext.calcularCon(organizacionId, () -> alumnos.findAllByEmailIgnoreCaseAndActivoTrue(email))
				.stream().map(Alumno::getId).collect(Collectors.toSet());
	}

	/** MembresiaServiceImpl.puedeEntrarCon antes de la entrega 2a, con su consulta nativa. */
	private boolean puertaAntigua(Usuario usuario, long organizacionId) {
		Set<Roles> autoridades = membresiaService.autoridadesEn(usuario.getId(), organizacionId);
		if (autoridades.isEmpty()) {
			return false;
		}
		if (!RolMembresia.requiereAlumnoActivo(autoridades)) {
			return true;
		}
		String email = EmailUtils.normalizeEmail(usuario.getEmail());
		return EmailUtils.puedeVincularAlumnos(email) && jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno
				WHERE organizacion_id = ? AND activo = TRUE AND LOWER(email) = LOWER(?)
				""", Long.class, organizacionId, email) > 0;
	}

	// ------------------------------------------------------------------ comprobaciones

	private void esperarEnTaemoi(String cuenta, String... esperados) {
		Usuario usuario = usuarios.findById(cuentas.get(cuenta)).orElseThrow();
		Set<Long> ids = ids(esperados);
		assertEquals(nombres(ids), nombres(reglaAntigua(usuario, ORG1)), "regla antigua, caso " + cuenta);
		esperarEn(ORG1, cuenta, esperados);
	}

	private void esperarEn(long organizacionId, String cuenta, String... esperados) {
		Usuario usuario = usuarios.findById(cuentas.get(cuenta)).orElseThrow();
		Set<Long> ids = ids(esperados);
		assertEquals(nombres(ids), nombres(new HashSet<>(acceso.idsAccesibles(usuario, organizacionId))),
				"regla nueva, caso " + cuenta + " en " + organizacionId);
		assertEquals(!ids.isEmpty(), acceso.tieneAlgunoAccesible(usuario, organizacionId), "caso " + cuenta);
	}

	private Set<Long> ids(String... nombres) {
		Set<Long> ids = new HashSet<>();
		for (String nombre : nombres) {
			Long id = fichas.get(nombre);
			assertTrue(id != null, "alumno desconocido: " + nombre);
			ids.add(id);
		}
		return ids;
	}

	private Set<String> nombres(Set<Long> ids) {
		Set<String> resultado = new java.util.TreeSet<>();
		for (Long id : ids) {
			String nombre = fichas.entrySet().stream().filter(e -> e.getValue().equals(id)).map(Map.Entry::getKey)
					.findFirst().orElse("ajeno#" + id);
			resultado.add(nombre);
		}
		return resultado;
	}

	private String origen(String cuenta, String ficha) {
		List<String> filas = jdbc.queryForList(
				"SELECT origen FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?", String.class,
				cuentas.get(cuenta), fichas.get(ficha));
		return filas.isEmpty() ? null : filas.get(0);
	}

	// ------------------------------------------------------------------ datos

	/** La sentencia de relleno de V31, tal cual, limitada a las cuentas de este test. */
	private void rellenarComoV31() {
		String script;
		try {
			script = new String(new ClassPathResource(V31).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		int inicio = script.indexOf("INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)");
		assertTrue(inicio >= 0, "V31 ya no tiene la sentencia de relleno esperada");
		int fin = script.indexOf(';', inicio);
		String sentencia = script.substring(inicio, fin);
		Matcher filtro = FILTRO_V31.matcher(sentencia);
		assertTrue(filtro.find(), "V31: no se encuentra el filtro final del relleno");
		String limitadas = cuentas.values().stream().map(String::valueOf).collect(Collectors.joining(","));
		String limitada = sentencia.substring(0, filtro.start()) + "WHERE v.usuario_id IN (" + limitadas
				+ ") AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua" + sentencia.substring(filtro.end());
		assertFalse(filtro.find(), "V31: el filtro final aparece más de una vez");
		jdbc.update(limitada);
	}

	private String email(long usuarioId) {
		return cuentas.entrySet().stream().filter(e -> e.getValue() == usuarioId).map(Map.Entry::getKey)
				.findFirst().map(c -> "eq-" + c + "-" + sufijo + "@example.test").orElseThrow();
	}

	private long cuenta(String caso, Long organizacionId, RolMembresia rol) {
		String email = "eq-" + caso + "-" + sufijo + "@example.test";
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Equivalencia " + caso);
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode("Clave-Segura-123"));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		cuentas.put(caso, id);
		if (organizacionId != null) {
			dar(id, organizacionId, rol);
		}
		return id;
	}

	private void dar(long usuarioId, long organizacionId, RolMembresia rol) {
		TenantContext.ejecutarCon(ORG1, () -> membresias.save(new Membresia(usuarioId, organizacionId, rol)));
	}

	private void desactivar(long usuarioId, long organizacionId, RolMembresia rol) {
		assertEquals(1, jdbc.update(
				"UPDATE membresia SET activa = FALSE WHERE usuario_id = ? AND organizacion_id = ? AND rol = ?",
				usuarioId, organizacionId, rol.name()));
	}

	private void vincularDirecto(long usuarioId, long alumnoId) {
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumnoId, usuarioId);
	}

	private void vincular(long usuarioId, long alumnoId, long organizacionId, String origen) {
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', ?)
				""", usuarioId, alumnoId, organizacionId, origen);
	}

	private long alumno(String nombre, long organizacionId, String email) {
		long id = TenantContext.calcularCon(organizacionId, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA ACCESO FAMILIA");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2012, 3, 3)));
			alumno.setDireccion("Calle Prueba 5");
			alumno.setTelefono("600000005");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		fichas.put(nombre, id);
		return id;
	}
}
