package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.jobs.RachaReminderJob;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.RecordatorioRachaService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * La organización activa no se pierde ni se filtra fuera del hilo de la petición:
 * cachés separadas por escuela, {@code @Async} real (envío de correo) y tareas programadas.
 */
@SpringBootTest
@Import(OrganizacionPropagacionIntegrationTest.ConfigCache.class)
class OrganizacionPropagacionIntegrationTest extends MySqlIntegrationTest {

	@Autowired CacheManager cacheManager;
	@Autowired ServicioCacheado servicioCacheado;
	@Autowired EmailService emailService;
	@Autowired RachaReminderJob rachaReminderJob;
	@Autowired OrganizacionRepository organizaciones;

	@MockitoBean JavaMailSender mailSender;
	@MockitoBean RecordatorioRachaService recordatorioRachaService;

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get());
	}

	// ----------------------------------------------------------------------- cachés

	@Test
	void mismaCacheYMismaClaveDevuelvenElValorDeCadaOrganizacion() {
		TenantContext.ejecutarCon(1L, () -> cacheManager.getCache("grupos").put("clave", "valor-org-1"));
		TenantContext.ejecutarCon(2L, () -> cacheManager.getCache("grupos").put("clave", "valor-org-2"));

		assertEquals("valor-org-1", leer(1L, "grupos", "clave"));
		assertEquals("valor-org-2", leer(2L, "grupos", "clave"));
		assertNull(leer(3L, "grupos", "clave"));
		assertNull(cacheManager.getCache("cache-no-declarada"));
	}

	@Test
	void cacheableConClaveExplicitaNoCruzaOrganizaciones() {
		servicioCacheado.reiniciarLlamadas();
		String clave = UUID.randomUUID().toString();

		assertEquals(1L, TenantContext.calcularCon(1L, () -> servicioCacheado.valorDe(clave)));
		assertEquals(2L, TenantContext.calcularCon(2L, () -> servicioCacheado.valorDe(clave)));
		// Segunda llamada: sale de la caché de su propia organización.
		assertEquals(1L, TenantContext.calcularCon(1L, () -> servicioCacheado.valorDe(clave)));
		assertEquals(2L, TenantContext.calcularCon(2L, () -> servicioCacheado.valorDe(clave)));
		assertEquals(2, servicioCacheado.llamadas());
	}

	@Test
	void evictAllEntriesSoloVaciaLaOrganizacionActiva() {
		String clave = UUID.randomUUID().toString();
		TenantContext.calcularCon(1L, () -> servicioCacheado.valorDe(clave));
		TenantContext.calcularCon(2L, () -> servicioCacheado.valorDe(clave));

		TenantContext.ejecutarCon(1L, servicioCacheado::vaciar);

		assertNull(leer(1L, "grupos", "fija-" + clave));
		assertEquals(2L, leer(2L, "grupos", "fija-" + clave));
	}

	// ----------------------------------------------------------------------- @Async

	@Test
	void emailAsincronoSeEjecutaConLaOrganizacionDeQuienLoEncola() throws Exception {
		BlockingQueue<Object[]> ejecuciones = new LinkedBlockingQueue<>();
		when(mailSender.createMimeMessage()).thenAnswer(inv -> {
			Long org = TenantContext.get();
			ejecuciones.add(new Object[] { org == null ? "ninguna" : org, Thread.currentThread().getName() });
			return new MimeMessage((Session) null);
		});
		String hiloLlamante = Thread.currentThread().getName();

		TenantContext.ejecutarCon(2L, () -> emailService.sendEmail("a@example.test", "Asunto", "<p>x</p>"));
		Object[] primera = ejecuciones.poll(10, TimeUnit.SECONDS);

		assertNotNull(primera, "El envío asíncrono no se ejecutó");
		assertEquals(2L, primera[0]);
		assertNotEquals(hiloLlamante, primera[1], "Debía ejecutarse en otro hilo (@Async)");

		// Encolado sin organización: el hilo del pool no hereda la de la tarea anterior.
		emailService.sendEmail("b@example.test", "Asunto", "<p>x</p>");
		Object[] segunda = ejecuciones.poll(10, TimeUnit.SECONDS);
		assertNotNull(segunda);
		assertEquals("ninguna", segunda[0]);
	}

	// ----------------------------------------------------------------------- @Scheduled

	@Test
	void tareaProgramadaSeEjecutaUnaVezPorOrganizacionActivaConSuOrganizacion() {
		Long activa = crearOrganizacion(true);
		Long inactiva = crearOrganizacion(false);
		List<Long> vistas = new CopyOnWriteArrayList<>();
		doAnswer(inv -> {
			vistas.add(TenantContext.get());
			if (activa.equals(TenantContext.get())) {
				throw new IllegalStateException("fallo simulado en una organización");
			}
			return null;
		}).when(recordatorioRachaService).enviarRecordatoriosSiCorresponde();

		rachaReminderJob.ejecutarRecordatorioRacha();

		List<Long> esperadas = organizaciones.findByActivaTrueOrderByIdAsc().stream().map(Organizacion::getId).toList();
		assertEquals(esperadas, vistas, "Una pasada por organización activa, en orden y con su organización");
		assertTrue(vistas.contains(1L));
		assertTrue(vistas.contains(activa), "El fallo de una organización no debe parar a las demás");
		assertFalse(vistas.contains(inactiva));
		assertFalse(vistas.contains(null));
	}

	// ---------------------------------------------------------------------------------

	private Object leer(Long org, String cache, Object clave) {
		return TenantContext.calcularCon(org, () -> {
			Cache.ValueWrapper valor = cacheManager.getCache(cache).get(clave);
			return valor == null ? null : valor.get();
		});
	}

	private Long crearOrganizacion(boolean activa) {
		Organizacion org = new Organizacion();
		org.setSlug("it-prop-" + UUID.randomUUID().toString().substring(0, 8));
		org.setNombre("Escuela propagación");
		org.setActiva(activa);
		return organizaciones.save(org).getId();
	}

	@TestConfiguration
	static class ConfigCache {
		@Bean
		ServicioCacheado servicioCacheado() {
			return new ServicioCacheado();
		}
	}

	/** Bean con caché por clave explícita (no depende de la organización). */
	static class ServicioCacheado {
		private final AtomicInteger llamadas = new AtomicInteger();

		@Cacheable(cacheNames = "grupos", key = "'fija-' + #clave")
		public Long valorDe(String clave) {
			llamadas.incrementAndGet();
			return TenantContext.get();
		}

		@CacheEvict(cacheNames = "grupos", allEntries = true)
		public void vaciar() {
		}

		public int llamadas() {
			return llamadas.get();
		}

		public void reiniciarLlamadas() {
			llamadas.set(0);
		}
	}
}
