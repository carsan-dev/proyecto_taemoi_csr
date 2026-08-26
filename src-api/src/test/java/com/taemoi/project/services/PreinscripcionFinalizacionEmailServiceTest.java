package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.EstadoEmailFinalizacion;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PreinscripcionFinalizacionEmailServiceTest {
	@Mock PreinscripcionRepository preinscripciones;
	@Mock UsuarioRepository usuarios;
	@Mock EmailService email;

	private PreinscripcionFinalizacionEmailService service;
	private Preinscripcion preinscripcion;

	@BeforeEach
	void setUp() {
		service = new PreinscripcionFinalizacionEmailService(preinscripciones, usuarios, email);
		ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://portal.example.com/");
		preinscripcion = preinscripcionFinalizada();
		when(preinscripciones.findByReferenciaForUpdate("PRE-2026-ABC")).thenReturn(Optional.of(preinscripcion));
	}

	@Test
	void cuentaLocalRecibeLoginYTodosLosDatosSinAdjunto() {
		Usuario usuario = usuario(AuthProvider.LOCAL);
		when(usuarios.findByEmailIgnoreCase("familia@example.com")).thenReturn(Optional.of(usuario));

		service.enviar("PRE-2026-ABC");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(eq("familia@example.com"), eq("Inscripción confirmada · PRE-2026-ABC"), html.capture());
		verify(email, never()).sendEmailConAdjunto(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
		assertTrue(html.getValue().contains("Ana García"));
		assertTrue(html.getValue().contains("Taekwondo infantil"));
		assertTrue(html.getValue().contains("Lunes"));
		assertTrue(html.getValue().contains("Miércoles"));
		assertTrue(html.getValue().contains("todos los alumnos vinculados a este correo"));
		assertTrue(html.getValue().contains("Iniciar sesión"));
		assertEquals(EstadoEmailFinalizacion.ENVIADO, preinscripcion.getEstadoEmailFinalizacion());
		assertEquals(1, preinscripcion.getEmailFinalizacionIntentos());
		assertNotNull(preinscripcion.getEmailFinalizacionEnviadoEn());
		assertNull(preinscripcion.getEmailFinalizacionUltimoError());
	}

	@Test
	void cuentaGoogleExplicaElAccesoConGoogle() {
		when(usuarios.findByEmailIgnoreCase("familia@example.com"))
				.thenReturn(Optional.of(usuario(AuthProvider.GOOGLE)));

		service.enviar("PRE-2026-ABC");

		verify(email).sendEmailSync(eq("familia@example.com"), anyString(), contains("Continuar con Google"));
	}

	@Test
	void cuentaInexistenteRecibeEnlaceDeRegistroConEmailCodificado() {
		preinscripcion.getAlumno().setEmail("familia+tkd@example.com");
		when(usuarios.findByEmailIgnoreCase("familia+tkd@example.com")).thenReturn(Optional.empty());

		service.enviar("PRE-2026-ABC");

		verify(email).sendEmailSync(eq("familia+tkd@example.com"), anyString(),
				contains("/login?modo=registro&amp;email=familia%2Btkd%40example.com"));
	}

	@Test
	void errorSmtpNoCambiaLaFinalizacionYRegistraElError() {
		when(usuarios.findByEmailIgnoreCase("familia@example.com")).thenReturn(Optional.empty());
		doThrow(new IllegalStateException("SMTP no disponible")).when(email)
				.sendEmailSync(eq("familia@example.com"), anyString(), anyString());

		service.enviar("PRE-2026-ABC");

		assertEquals(EstadoPreinscripcion.FINALIZADA, preinscripcion.getEstado());
		assertEquals(EstadoEmailFinalizacion.ERROR, preinscripcion.getEstadoEmailFinalizacion());
		assertEquals(1, preinscripcion.getEmailFinalizacionIntentos());
		assertNull(preinscripcion.getEmailFinalizacionEnviadoEn());
		assertEquals("SMTP no disponible", preinscripcion.getEmailFinalizacionUltimoError());
		verify(preinscripciones).save(preinscripcion);
	}

	@Test
	void cadaReenvioIncrementaLosIntentos() {
		preinscripcion.setEmailFinalizacionIntentos(2);
		when(usuarios.findByEmailIgnoreCase("familia@example.com")).thenReturn(Optional.empty());

		service.enviar("PRE-2026-ABC");

		assertEquals(3, preinscripcion.getEmailFinalizacionIntentos());
		assertEquals(EstadoEmailFinalizacion.ENVIADO, preinscripcion.getEstadoEmailFinalizacion());
	}

	private Usuario usuario(AuthProvider provider) {
		Usuario usuario = new Usuario();
		usuario.setEmail("familia@example.com");
		usuario.setAuthProvider(provider);
		return usuario;
	}

	private Preinscripcion preinscripcionFinalizada() {
		Grupo grupo = new Grupo();
		grupo.setId(7L);
		grupo.setNombre("Taekwondo infantil");
		grupo.setDeporte(Deporte.TAEKWONDO);
		Turno lunes = turno(11L, "Lunes", grupo);
		Turno miercoles = turno(12L, "Miércoles", grupo);
		Alumno alumno = new Alumno();
		alumno.setEmail("familia@example.com");
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-2026-ABC");
		p.setNombre("Ana");
		p.setApellidos("García");
		p.setDeporte(Deporte.TAEKWONDO);
		p.setTemporada("2026/2027");
		p.setEstado(EstadoPreinscripcion.FINALIZADA);
		p.setAlumno(alumno);
		p.setGrupo(grupo);
		p.setTurnos(new LinkedHashSet<>(List.of(lunes, miercoles)));
		return p;
	}

	private Turno turno(Long id, String dia, Grupo grupo) {
		Turno turno = new Turno();
		turno.setId(id);
		turno.setDiaSemana(dia);
		turno.setHoraInicio("18:00");
		turno.setHoraFin("19:00");
		turno.setGrupo(grupo);
		return turno;
	}
}
