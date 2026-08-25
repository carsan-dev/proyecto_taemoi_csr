package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.entities.EstadoEmailCambioTurnos;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.repositories.PreinscripcionRepository;

@ExtendWith(MockitoExtension.class)
class PreinscripcionCambioTurnosEmailServiceTest {
	@Mock PreinscripcionRepository preinscripciones;
	@Mock EmailService email;

	private PreinscripcionCambioTurnosEmailService service;
	private Preinscripcion preinscripcion;

	@BeforeEach
	void setUp() {
		service = new PreinscripcionCambioTurnosEmailService(preinscripciones, email);
		preinscripcion = solicitudConCambio();
		lenient().when(preinscripciones.findByReferenciaForUpdate("PRE-1")).thenReturn(Optional.of(preinscripcion));
	}

	@Test
	void envioCorrectoRegistraEstadoIntentoYFecha() {
		service.enviar("PRE-1");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(eq("ana@example.com"), eq("Horarios actualizados · PRE-1"), html.capture());
		assertTrue(html.getValue().contains("Horario anterior"));
		assertTrue(html.getValue().contains("Horario vigente"));
		assertEquals(EstadoEmailCambioTurnos.ENVIADO, preinscripcion.getEstadoEmailCambioTurnos());
		assertEquals(1, preinscripcion.getEmailCambioTurnosIntentos());
		assertNotNull(preinscripcion.getEmailCambioTurnosEnviadoEn());
		assertNull(preinscripcion.getEmailCambioTurnosUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	@Test
	void falloSmtpNoCambiaLaAsignacionYRegistraElError() {
		doThrow(new IllegalStateException("SMTP no disponible")).when(email)
				.sendEmailSync(eq("ana@example.com"), anyString(), anyString());

		service.enviar("PRE-1");

		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
		assertEquals("Horario vigente", preinscripcion.getEmailCambioTurnosNuevoSnapshot());
		assertEquals(EstadoEmailCambioTurnos.ERROR, preinscripcion.getEstadoEmailCambioTurnos());
		assertEquals(1, preinscripcion.getEmailCambioTurnosIntentos());
		assertNull(preinscripcion.getEmailCambioTurnosEnviadoEn());
		assertEquals("SMTP no disponible", preinscripcion.getEmailCambioTurnosUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	@Test
	void reintentoIncrementaIntentosYLimpiaElError() {
		preinscripcion.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.ERROR);
		preinscripcion.setEmailCambioTurnosIntentos(2);
		preinscripcion.setEmailCambioTurnosUltimoError("fallo anterior");

		service.enviar("PRE-1");

		assertEquals(3, preinscripcion.getEmailCambioTurnosIntentos());
		assertEquals(EstadoEmailCambioTurnos.ENVIADO, preinscripcion.getEstadoEmailCambioTurnos());
		assertNull(preinscripcion.getEmailCambioTurnosUltimoError());
	}

	@Test
	void escapaHtmlPeligrosoEnObservacionYSnapshots() {
		preinscripcion.setObservaciones("Peso > 45 kg <script>alert('x')</script>");
		preinscripcion.setEmailCambioTurnosAnteriorSnapshot("Anterior <b>uno</b>");

		String html = service.html(preinscripcion);

		assertTrue(html.contains("Peso &gt; 45 kg"));
		assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
		assertTrue(html.contains("Anterior &lt;b&gt;uno&lt;/b&gt;"));
		assertFalse(html.contains("<script>"));
	}

	private Preinscripcion solicitudConCambio() {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		p.setNombre("Ana");
		p.setEmail("ana@example.com");
		p.setEstado(EstadoPreinscripcion.PENDIENTE);
		p.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.PENDIENTE);
		p.setEmailCambioTurnosAnteriorSnapshot("Horario anterior");
		p.setEmailCambioTurnosNuevoSnapshot("Horario vigente");
		return p;
	}
}
