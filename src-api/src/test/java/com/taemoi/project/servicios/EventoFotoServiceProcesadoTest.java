package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.evento.ServidorOcupadoException;
import com.taemoi.project.exceptions.evento.TopeFotosEventoException;
import com.taemoi.project.repositories.EventoFotoRepository;
import com.taemoi.project.repositories.EventoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.services.EventoFotoService;
import com.taemoi.project.services.ImagenService;

/** Procesar una foto gasta mucha memoria: de una en una, con su propio semáforo y fuera de la transacción. */
class EventoFotoServiceProcesadoTest {

	private static final Long EVENTO = 7L;
	private static final String MENSAJE = "El servidor está ocupado. Reintenta en un momento.";

	private final EventoRepository eventos = mock(EventoRepository.class);
	private final EventoFotoRepository fotos = mock(EventoFotoRepository.class);
	private final ImagenRepository imagenes = mock(ImagenRepository.class);
	private final ImagenService imagenService = mock(ImagenService.class);
	private final ExecutorService hilos = Executors.newFixedThreadPool(4);
	private final MockMultipartFile archivo = new MockMultipartFile("archivo", "foto.png", "image/png", new byte[] { 1 });

	private EventoFotoService servicio;

	@BeforeEach
	void preparar() {
		servicio = new EventoFotoService(eventos, fotos, imagenes, imagenService, mock(PlatformTransactionManager.class));
		when(eventos.existsById(EVENTO)).thenReturn(true);
		when(eventos.findByIdParaActualizar(EVENTO)).thenReturn(Optional.of(new Evento()));
		when(imagenes.save(any(Imagen.class))).thenAnswer(llamada -> llamada.getArgument(0));
		when(fotos.save(any())).thenAnswer(llamada -> llamada.getArgument(0));
	}

	@AfterEach
	void parar() {
		hilos.shutdownNow();
		assertFalse(Thread.interrupted(), "El test dejó el hilo interrumpido");
	}

	@Test
	void esUnaAlaVez_yLaEsperaEsDe30Segundos() {
		assertEquals(1, semaforo().availablePermits());
		assertEquals(Duration.ofSeconds(30), ReflectionTestUtils.getField(servicio, "esperaProcesado"));
	}

	@Test
	void conUnaProcesando_laSegundaEsperaYSeRinde_yLaPrimeraAcaba() throws Exception {
		ReflectionTestUtils.setField(servicio, "esperaProcesado", Duration.ofMillis(100));
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		when(imagenService.guardarImagenFotoEvento(any())).thenAnswer(llamada -> {
			dentro.countDown();
			assertTrue(soltar.await(30, TimeUnit.SECONDS));
			return guardada();
		});
		Future<?> enCurso = hilos.submit(() -> subir());
		assertTrue(dentro.await(30, TimeUnit.SECONDS));

		ServidorOcupadoException error = assertThrows(ServidorOcupadoException.class, () -> servicio.subir(EVENTO, archivo));

		assertEquals(MENSAJE, error.getMessage());
		verify(imagenService, times(1)).guardarImagenFotoEvento(any());
		soltar.countDown();
		enCurso.get(30, TimeUnit.SECONDS);
		assertEquals(1, semaforo().availablePermits());
	}

	/** Si no, una subida esperaría a la imagen de otra con la fila del evento bloqueada. */
	@Test
	void elSemaforoNoSeRetieneDuranteLaTransaccion() throws Exception {
		when(imagenService.guardarImagenFotoEvento(any())).thenAnswer(llamada -> {
			assertEquals(0, semaforo().availablePermits(), "Se procesa con el semáforo cogido");
			return guardada();
		});
		when(eventos.findByIdParaActualizar(EVENTO)).thenAnswer(llamada -> {
			assertEquals(1, semaforo().availablePermits(), "La transacción empieza con el semáforo libre");
			return Optional.of(new Evento());
		});

		servicio.subir(EVENTO, archivo);

		verify(eventos).findByIdParaActualizar(EVENTO);
	}

	@Test
	void unFalloAlProcesarDevuelveElHueco() throws Exception {
		for (Throwable fallo : new Throwable[] { new IOException("disco"), new IllegalStateException("codificador"),
				new OutOfMemoryError("memoria") }) {
			doThrow(fallo).when(imagenService).guardarImagenFotoEvento(any());

			for (int i = 0; i < 3; i++) {
				assertSame(fallo, assertThrows(Throwable.class, () -> servicio.subir(EVENTO, archivo)));
				assertEquals(1, semaforo().availablePermits(), fallo.toString());
			}
		}
	}

	/** El semáforo ya se soltó al empezar la transacción: que falle no debe soltarlo otra vez. */
	@Test
	void unFalloDeLaTransaccionNoTocaLosHuecos_yBorraLosFicheros() throws Exception {
		when(imagenService.guardarImagenFotoEvento(any())).thenReturn(guardada());
		int borrados = 0;
		for (Throwable fallo : new Throwable[] { new IllegalStateException("base de datos"), new OutOfMemoryError("memoria") }) {
			doThrow(fallo).when(fotos).save(any());

			for (int i = 0; i < 3; i++) {
				assertSame(fallo, assertThrows(Throwable.class, () -> servicio.subir(EVENTO, archivo)));
				assertEquals(1, semaforo().availablePermits(), fallo.toString());
			}
			borrados += 6;
			verify(imagenService, times(borrados)).eliminarImagenDeSistema(any());
		}
	}

	/** Quien se rinde no llegó a coger hueco: soltar uno inflaría el tope. */
	@Test
	void rendirseNoInflaLosHuecos() throws Exception {
		ReflectionTestUtils.setField(servicio, "esperaProcesado", Duration.ofMillis(10));
		semaforo().acquire();

		for (int i = 0; i < 3; i++) {
			assertThrows(ServidorOcupadoException.class, () -> servicio.subir(EVENTO, archivo));
		}

		assertEquals(0, semaforo().availablePermits());
		semaforo().release();
		assertEquals(1, semaforo().availablePermits());
	}

	@Test
	void unaInterrupcionNoInflaLosHuecos() throws Exception {
		semaforo().acquire();
		Thread.currentThread().interrupt();

		assertThrows(ServidorOcupadoException.class, () -> servicio.subir(EVENTO, archivo));

		assertTrue(Thread.interrupted(), "La interrupción se conserva");
		assertEquals(0, semaforo().availablePermits());
		semaforo().release();

		Thread.currentThread().interrupt();
		assertThrows(ServidorOcupadoException.class, () -> servicio.subir(EVENTO, archivo));

		assertTrue(Thread.interrupted(), "La interrupción se conserva");
		assertEquals(1, semaforo().availablePermits(), "Interrumpida con hueco libre tampoco lo gasta");
		verify(imagenService, never()).guardarImagenFotoEvento(any());
	}

	/** Lo que ya se sabe que no entra no ocupa ni espera un hueco. */
	@Test
	void conElTopeLlenoNoSeEsperaHueco() throws Exception {
		when(fotos.contarDeEvento(EVENTO)).thenReturn(40L);
		semaforo().drainPermits();

		assertThrows(TopeFotosEventoException.class, () -> servicio.subir(EVENTO, archivo));

		verify(imagenService, never()).guardarImagenFotoEvento(any());
	}

	@Test
	void siSeInterrumpeLaEsperaEsOcupado_yConservaLaInterrupcion() throws Exception {
		semaforo().drainPermits();
		Thread.currentThread().interrupt();

		ServidorOcupadoException error = assertThrows(ServidorOcupadoException.class, () -> servicio.subir(EVENTO, archivo));

		assertEquals(MENSAJE, error.getMessage());
		assertTrue(Thread.interrupted(), "La interrupción se conserva");
		verify(imagenService, never()).guardarImagenFotoEvento(any());
	}

	private Void subir() throws IOException {
		servicio.subir(EVENTO, archivo);
		return null;
	}

	private Semaphore semaforo() {
		return (Semaphore) ReflectionTestUtils.getField(servicio, "procesado");
	}

	private static ImagenService.FotoEventoGuardada guardada() {
		return new ImagenService.FotoEventoGuardada(imagen("foto.webp"), imagen("miniatura.webp"));
	}

	private static Imagen imagen(String nombre) {
		return new Imagen(nombre, "image/webp", "/imagenes/org-1/fotos-eventos/" + nombre,
				"org-1/fotos-eventos/" + nombre);
	}
}
