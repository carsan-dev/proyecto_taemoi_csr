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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.EstadoEmailCancelacion;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.repositories.PreinscripcionRepository;

@ExtendWith(MockitoExtension.class)
class PreinscripcionCancelacionEmailServiceTest {
	@Mock PreinscripcionRepository preinscripciones;
	@Mock EmailService email;

	private PreinscripcionCancelacionEmailService service;
	private Preinscripcion preinscripcion;

	@BeforeEach
	void setUp() {
		service = new PreinscripcionCancelacionEmailService(preinscripciones, email);
		preinscripcion = solicitudCancelada();
		lenient().when(preinscripciones.findByReferenciaForUpdate("PRE-1")).thenReturn(Optional.of(preinscripcion));
	}

	@Test
	void envioCorrectoUsaAsuntoYCuerpoAprobadosYRegistraElEnvio() {
		service.enviar("PRE-1");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(eq("familia@example.com"), eq("Tu preinscripción PRE-1 ha sido cancelada"),
				html.capture());
		String cuerpo = html.getValue();
		assertTrue(cuerpo.contains("<p>Hola,</p>"), cuerpo);
		assertTrue(cuerpo.contains("Te informamos de que la preinscripción de <strong>Ana García López</strong> en "
				+ "<strong>Taekwondo</strong> (referencia <strong>PRE-1</strong>) ha sido cancelada."), cuerpo);
		assertTrue(cuerpo.contains("El grupo de los lunes se ha cerrado."), cuerpo);
		assertTrue(cuerpo.contains("Si tienes cualquier duda, puedes responder a este correo o ponerte en contacto "
				+ "con la escuela."), cuerpo);
		assertTrue(cuerpo.contains("Un saludo,<br><strong>Moi's Kim Do</strong>"), cuerpo);
		assertEquals(EstadoEmailCancelacion.ENVIADO, preinscripcion.getEstadoEmailCancelacion());
		assertEquals(1, preinscripcion.getEmailCancelacionIntentos());
		assertNotNull(preinscripcion.getEmailCancelacionEnviadoEn());
		assertNull(preinscripcion.getEmailCancelacionUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	@Test
	void escapaElMotivoYLosDatosAlConstruirElHtml() {
		preinscripcion.setMotivoCancelacion("Aforo > 20 <script>alert('x')</script> & \"fin\"");
		preinscripcion.setNombre("<b>Ana</b>");

		service.enviar("PRE-1");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(anyString(), anyString(), html.capture());
		String cuerpo = html.getValue();
		assertFalse(cuerpo.contains("<script>"), cuerpo);
		assertFalse(cuerpo.contains("<b>Ana</b>"), cuerpo);
		assertTrue(cuerpo.contains("Aforo &gt; 20 &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;fin&quot;"),
				cuerpo);
		assertTrue(cuerpo.contains("&lt;b&gt;Ana&lt;/b&gt; García López"), cuerpo);
	}

	@Test
	void sinMotivoGuardadoUsaElMotivoPorDefecto() {
		preinscripcion.setMotivoCancelacion(null);

		service.enviar("PRE-1");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(anyString(), anyString(), html.capture());
		assertTrue(html.getValue().contains(PreinscripcionService.MOTIVO_CANCELACION_POR_DEFECTO));
	}

	@Test
	void falloSmtpNoDeshaceLaCancelacionYRegistraElError() {
		doThrow(new IllegalStateException("SMTP no disponible\r\nsegunda línea")).when(email)
				.sendEmailSync(eq("familia@example.com"), anyString(), anyString());

		service.enviar("PRE-1");

		assertEquals(EstadoPreinscripcion.CANCELADA, preinscripcion.getEstado());
		assertEquals("El grupo de los lunes se ha cerrado.", preinscripcion.getMotivoCancelacion());
		assertEquals(EstadoEmailCancelacion.ERROR, preinscripcion.getEstadoEmailCancelacion());
		assertEquals(1, preinscripcion.getEmailCancelacionIntentos());
		assertNull(preinscripcion.getEmailCancelacionEnviadoEn());
		assertEquals("SMTP no disponible segunda línea", preinscripcion.getEmailCancelacionUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	@Test
	void reintentoIncrementaIntentosAunqueLaColumnaVengaNulaDeFilasAntiguas() {
		preinscripcion.setEmailCancelacionIntentos(null);
		preinscripcion.setEstadoEmailCancelacion(null);

		service.enviar("PRE-1");

		assertEquals(1, preinscripcion.getEmailCancelacionIntentos());
		assertEquals(EstadoEmailCancelacion.ENVIADO, preinscripcion.getEstadoEmailCancelacion());
	}

	@Test
	void sinCorreoRegistraErrorSinLanzar() {
		preinscripcion.setEmail("  ");

		service.enviar("PRE-1");

		assertEquals(EstadoEmailCancelacion.ERROR, preinscripcion.getEstadoEmailCancelacion());
		assertNotNull(preinscripcion.getEmailCancelacionUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	private Preinscripcion solicitudCancelada() {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		p.setEstado(EstadoPreinscripcion.CANCELADA);
		Disciplina disciplina = CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name());
		disciplina.setNombre("Taekwondo"); // nombre público (V30), distinto del código
		p.setDisciplina(disciplina);
		p.setNombre("Ana");
		p.setApellidos("García López");
		p.setEmail("familia@example.com");
		p.setMotivoCancelacion("El grupo de los lunes se ha cerrado.");
		p.setEstadoEmailCancelacion(EstadoEmailCancelacion.PENDIENTE);
		return p;
	}
}
