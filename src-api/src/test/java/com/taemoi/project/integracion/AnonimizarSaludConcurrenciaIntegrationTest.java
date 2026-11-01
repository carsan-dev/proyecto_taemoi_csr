package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.dtos.request.AlumnoSaludRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.TipoDiscapacidad;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AlumnoSaludService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.GrupoService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Guardar la salud y eliminar anonimizando contra un MySQL real, con transacciones de verdad en hilos distintos. Las dos
 * operaciones se serializan con el bloqueo de la fila de {@code alumno}. Sin él, {@code guardar} podía comprobar que el
 * alumno no estaba anonimizado, dejar pasar la anonimización y reescribir después toda la ficha (Hibernate actualiza todas
 * las columnas y {@code Alumno} no tiene versión), con el nombre, el DNI y {@code anonimizado_en = NULL}, y recrear la fila
 * de {@code alumno_salud}. Datos inventados.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnonimizarSaludConcurrenciaIntegrationTest extends MySqlIntegrationTest {

	private static final int ESPERA_SEGUNDOS = 30;
	private static final String NIF = "12345678Z";

	@Autowired JdbcTemplate jdbc;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired AlumnoSaludService salud;
	@Autowired AlumnoService alumnoService;
	@Autowired AlumnoRepository alumnos;
	@Autowired TransactionTemplate tx;
	@Autowired EntityManagerFactory emf;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	ExecutorService hilos;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-salud-carrera");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-salud-carrera-otra");
		hilos = Executors.newCachedThreadPool();
	}

	@AfterAll
	void cerrar() {
		hilos.shutdownNow();
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		assertFalse(TransactionSynchronizationManager.hasResource(emf), "El test dejó una sesión ligada al hilo");
	}

	// ------------------------------------------------------------------ dos transacciones reales

	@Test
	void conLaFilaBloqueadaPorAnonimizar_guardarEspera_yAlConfirmarSeRechaza() throws Exception {
		long alumno = alumnoConDatos();
		CountDownLatch bTieneElBloqueo = new CountDownLatch(1);
		CountDownLatch soltarB = new CountDownLatch(1);

		Future<Boolean> b = hilos.submit(() -> enEscuela(e, () -> tx.execute(s -> {
			boolean anonimizado = alumnoService.anonimizarAlumno(alumno);
			bTieneElBloqueo.countDown();
			esperar(soltarB);
			return anonimizado;
		})));
		try {
			esperar(bTieneElBloqueo);

			Future<?> a = hilos.submit(() -> enEscuela(e, () -> salud.guardar(alumno, conDiscapacidad())));
			esperarBloqueado(a);
			assertFalse(a.isDone(), "mientras B tiene la fila, A espera");

			soltarB.countDown();
			assertTrue(b.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
			assertRechazoPorAnonimizado(a);
		} finally {
			soltarB.countDown();
		}

		assertAnonimizadoSinSalud(alumno);
	}

	@Test
	void conGuardarEnCurso_anonimizarEspera_yBorraLoGuardado() throws Exception {
		long alumno = alumnoConDatos();
		CountDownLatch aTieneElBloqueo = new CountDownLatch(1);
		CountDownLatch soltarA = new CountDownLatch(1);

		Future<?> a = hilos.submit(() -> enEscuela(e, () -> tx.execute(s -> {
			salud.guardar(alumno, conDiscapacidad());
			aTieneElBloqueo.countDown();
			esperar(soltarA);
			return null;
		})));
		try {
			esperar(aTieneElBloqueo);

			Future<Boolean> b = hilos.submit(() -> enEscuela(e, () -> alumnoService.anonimizarAlumno(alumno)));
			esperarBloqueado(b);
			assertFalse(b.isDone(), "mientras A tiene la fila, B espera");

			soltarA.countDown();
			a.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
			assertTrue(b.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
		} finally {
			soltarA.countDown();
		}

		assertAnonimizadoSinSalud(alumno);
	}

	/** Dos {@code guardar} a la vez: el bloqueo también los pone en fila, sin choque de claves al crear la de salud. */
	@Test
	void dosGuardarALaVez_elSegundoEspera_yAmbosTerminan() throws Exception {
		long alumno = alumnoConDatos();
		CountDownLatch primeroDentro = new CountDownLatch(1);
		CountDownLatch soltarPrimero = new CountDownLatch(1);

		Future<?> primero = hilos.submit(() -> enEscuela(e, () -> tx.execute(s -> {
			salud.guardar(alumno, conDiscapacidad());
			primeroDentro.countDown();
			esperar(soltarPrimero);
			return null;
		})));
		try {
			esperar(primeroDentro);
			Future<?> segundo = hilos.submit(() -> enEscuela(e, () -> salud.guardar(alumno, sinCambiarDiscapacidad())));
			esperarBloqueado(segundo);

			soltarPrimero.countDown();
			primero.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
			segundo.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
		} finally {
			soltarPrimero.countDown();
		}

		assertEquals(1, filasDeSalud(alumno));
	}

	// ------------------------------------------------------------------ sesión que ya tenía la ficha (open-in-view)

	/** La comprobación no puede fiarse de la instancia de la sesión: se hizo antes de que B confirmara. */
	@Test
	void conLaFichaYaCargadaEnLaSesion_guardarVeLaAnonimizacion_yNoRestauraNada() throws Exception {
		long alumno = alumnoConDatos();
		agregarFoto(alumno);

		enSesionDePeticion(e, () -> {
			Alumno ficha = alumnos.buscarPorIdEnOrganizacionActiva(alumno).orElseThrow();
			assertNull(ficha.getAnonimizadoEn());
			assertNotNull(ficha.getFotoAlumno(), "la sesión tiene la foto que B va a borrar");

			assertTrue(enOtroHilo(() -> enEscuela(e, () -> alumnoService.anonimizarAlumno(alumno))));

			IllegalStateException error = assertThrows(IllegalStateException.class,
					() -> salud.guardar(alumno, conDiscapacidad()));
			assertEquals(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA, error.getMessage());
		});

		assertAnonimizadoSinSalud(alumno);
	}

	@Test
	void conLaFichaYaCargadaEnLaSesion_repetirAnonimizarNoEscribeNada() throws Exception {
		long alumno = alumnoConDatos();
		agregarFoto(alumno);

		enSesionDePeticion(e, () -> {
			alumnos.buscarPorIdEnOrganizacionActiva(alumno).orElseThrow();

			assertTrue(enOtroHilo(() -> enEscuela(e, () -> alumnoService.anonimizarAlumno(alumno))));
			Object anonimizadoEn = jdbc.queryForObject("SELECT anonimizado_en FROM alumno WHERE id = ?", Object.class,
					alumno);

			assertTrue(alumnoService.anonimizarAlumno(alumno));

			assertEquals(anonimizadoEn,
					jdbc.queryForObject("SELECT anonimizado_en FROM alumno WHERE id = ?", Object.class, alumno));
		});

		assertAnonimizadoSinSalud(alumno);
	}

	/** Lo que ya cargó la sesión tampoco pisa lo confirmado por otros: la ficha se relee antes de escribir el booleano. */
	@Test
	void conLaFichaYaCargadaEnLaSesion_guardarNoPisaLoConfirmadoEntretanto() throws Exception {
		long alumno = alumnoConDatos();

		enSesionDePeticion(e, () -> {
			alumnos.buscarPorIdEnOrganizacionActiva(alumno).orElseThrow();
			jdbc.update("UPDATE alumno SET email = 'nuevo@example.test' WHERE id = ?", alumno);

			salud.guardar(alumno, conDiscapacidad());
		});

		assertEquals("nuevo@example.test",
				jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, alumno));
		assertEquals(Boolean.TRUE,
				jdbc.queryForObject("SELECT tiene_discapacidad FROM alumno WHERE id = ?", Boolean.class, alumno));
	}

	// ------------------------------------------------------------------ escuela activa

	/** El bloqueo sigue el filtro de la escuela activa: la ficha de otra escuela es «no encontrada» y no se toca. */
	@Test
	void conOtraEscuelaActiva_niGuardaNiAnonimiza() {
		long alumno = alumnoConDatos();

		assertThrows(AlumnoNoEncontradoException.class,
				() -> enEscuela(otra, () -> salud.guardar(alumno, conDiscapacidad())));
		assertFalse(enEscuela(otra, () -> alumnoService.anonimizarAlumno(alumno)));

		assertNull(jdbc.queryForObject("SELECT anonimizado_en FROM alumno WHERE id = ?", Object.class, alumno));
		assertEquals("Lucia", jdbc.queryForObject("SELECT nombre FROM alumno WHERE id = ?", String.class, alumno));
		assertEquals(0, filasDeSalud(alumno));
	}

	/** Las dos consultas de la cadena de bloqueo filtran por la escuela activa. */
	@Test
	void conOtraEscuelaActiva_releerNoDevuelveLaFicha() {
		long alumno = alumnoConDatos();

		assertTrue(enEscuela(otra, () -> tx.execute(s -> alumnos.releerBloqueado(alumno))).isEmpty());
		assertTrue(enEscuela(otra, () -> tx.execute(s -> alumnos.bloquearEstadoDeSede(alumno))).isEmpty());
	}

	// ------------------------------------------------------------------ utilidades

	private long alumnoConDatos() {
		long alumno = e.alumno("CARRERA" + UUID.randomUUID().toString().substring(0, 8), e.sA, true);
		jdbc.update("""
				UPDATE alumno SET nombre = 'Lucia', apellidos = 'Ficticia', nif = ?, email = 'lucia@example.test',
				  telefono2 = '611111111', tiene_discapacidad = 0 WHERE id = ?""", NIF, alumno);
		return alumno;
	}

	private void agregarFoto(long alumno) {
		String nombre = "it-carrera-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/jpeg', ?, ?)", nombre,
				"org-" + e.org + "/alumnos/" + nombre, "/imagenes/org-" + e.org + "/alumnos/" + nombre, e.org);
		long imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("UPDATE alumno SET foto_alumno_id = ? WHERE id = ?", imagen, alumno);
	}

	private static AlumnoSaludRequest conDiscapacidad() {
		return new AlumnoSaludRequest(null, "Descripcion ficticia", null, null, "Alergia ficticia", null, true,
				List.of(TipoDiscapacidad.VISUAL), null);
	}

	private static AlumnoSaludRequest sinCambiarDiscapacidad() {
		return new AlumnoSaludRequest(null, null, null, null, "Alergia ficticia", null, null, null, null);
	}

	private static <T> T enEscuela(EscuelaConSedesDePrueba escuela, Supplier<T> accion) {
		return TenantContext.calcularCon(escuela.org, accion);
	}

	/** Lo que hace open-in-view: una sesión ligada al hilo, sin transacción, antes de entrar en el servicio. */
	private void enSesionDePeticion(EscuelaConSedesDePrueba escuela, AccionConSesion accion) throws Exception {
		TenantContext.calcularCon(escuela.org, () -> {
			EntityManager sesion = emf.createEntityManager();
			TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(sesion));
			try {
				accion.ejecutar();
				return null;
			} catch (RuntimeException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			} finally {
				TransactionSynchronizationManager.unbindResource(emf);
				sesion.close();
			}
		});
	}

	private <T> T enOtroHilo(java.util.concurrent.Callable<T> accion) throws Exception {
		return hilos.submit(accion).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
	}

	private static void esperar(CountDownLatch cerrojo) {
		try {
			if (!cerrojo.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("El otro hilo no llegó a tiempo.");
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Espera a que MySQL tenga una transacción en LOCK WAIT, sin dormir un tiempo fijo. {@code information_schema.innodb_trx}
	 * exige el privilegio PROCESS, que el usuario de la aplicación no tiene: se mira con root, en una conexión aparte.
	 * Si {@code tarea} acaba antes, no esperó.
	 */
	private void esperarBloqueado(Future<?> tarea) throws Exception {
		long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
		try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement consulta = root.createStatement()) {
			while (System.nanoTime() < limite) {
				try (ResultSet fila = consulta
						.executeQuery("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT'")) {
					fila.next();
					if (fila.getInt(1) > 0) {
						return;
					}
				}
				if (tarea.isDone()) {
					fail("Terminó sin esperar el bloqueo de la fila.");
				}
			}
		}
		fail("Nadie llegó a esperar el bloqueo de la fila.");
	}

	private static void assertRechazoPorAnonimizado(Future<?> tarea) throws Exception {
		ExecutionException fallo = assertThrows(ExecutionException.class,
				() -> tarea.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS), "A debe rechazarse");
		assertInstanceOf(IllegalStateException.class, fallo.getCause());
		assertEquals(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA, fallo.getCause().getMessage());
	}

	private void assertAnonimizadoSinSalud(long alumno) {
		Map<String, Object> ficha = jdbc.queryForMap(
				"SELECT nombre, apellidos, nif, email, telefono2, tiene_discapacidad, anonimizado_en FROM alumno WHERE id = ?",
				alumno);
		assertNotNull(ficha.get("anonimizado_en"), "anonimizado_en vuelve a NULL");
		assertEquals("Alumno", ficha.get("nombre"));
		assertEquals("eliminado", ficha.get("apellidos"));
		assertNull(ficha.get("nif"), "el DNI vuelve a la ficha");
		assertNull(ficha.get("email"));
		assertNull(ficha.get("telefono2"));
		assertEquals(Boolean.FALSE, ficha.get("tiene_discapacidad"));
		assertEquals(0, filasDeSalud(alumno), "la fila de salud se recrea tras el borrado");
	}

	private int filasDeSalud(long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM alumno_salud WHERE alumno_id = ?", Integer.class, alumno);
	}

	@FunctionalInterface
	private interface AccionConSesion {
		void ejecutar() throws Exception;
	}
}
