package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.RegistroSolicitudRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.events.InvitacionEnviableEvent;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.organizacion.UrlsEscuela;
import com.taemoi.project.organizacion.UrlsEscuelaPruebas;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.impl.PasswordResetServiceImpl;
import com.taemoi.project.services.impl.RecordatorioRachaServiceImpl;
import com.taemoi.project.services.impl.RegistroServiceImpl;

/**
 * Pieza 2 de la fase 4: cada correo con enlace al frontend lo construye con la base de SU escuela. La escuela 1 (la
 * global, https://moiskimdo.es) da el texto de siempre; la 2 su dominio principal; la 3, sin fila, la base global.
 */
class EnlacesCorreoPorEscuelaTest {

	private static final String GLOBAL = "https://moiskimdo.es";
	private static final UrlsEscuela URLS = UrlsEscuelaPruebas.conPrincipales(GLOBAL,
			Map.of(1L, "moiskimdo.es", 2L, "escuela2.example.test"));

	/** Escuela → base esperada en el enlace. */
	private static final Map<Long, String> ESPERADA = Map.of(1L, "https://moiskimdo.es", 2L,
			"https://escuela2.example.test", 3L, GLOBAL);

	private static void paraCadaEscuela(BiConsumer<Long, String> comprobacion) {
		ESPERADA.forEach(comprobacion);
	}

	private static PasswordResetServiceImpl reset(UsuarioRepository usuarios, EmailService email) {
		return new PasswordResetServiceImpl(usuarios, NoOpPasswordEncoder.getInstance(), email,
				MarcaEscuelaPruebas.soloMoiskimdo(), mock(MembresiaRepository.class), new ResolutorOrganizacion(1L),
				URLS);
	}

	@Test
	void restablecerContrasenaUsaLaEscuelaDeLaCuenta() {
		paraCadaEscuela((escuela, base) -> {
			UsuarioRepository usuarios = mock(UsuarioRepository.class);
			EmailService email = mock(EmailService.class);
			Usuario usuario = new Usuario();
			usuario.setId(5L);
			usuario.setEmail("cuenta@example.test");
			usuario.setUltimaOrganizacionId(escuela);
			when(usuarios.findByEmailIgnoreCase("cuenta@example.test")).thenReturn(Optional.of(usuario));

			reset(usuarios, email).solicitarResetContrasena("cuenta@example.test");

			ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
			verify(email).sendEmail(eq("cuenta@example.test"), anyString(), html.capture());
			assertTrue(html.getValue().contains("href=\"" + base + "/reset-password?token=")
					|| html.getValue().contains("'" + base + "/reset-password?token=")
					|| html.getValue().contains(base + "/reset-password?token="), "escuela " + escuela);
		});
	}

	@Test
	void restablecerContrasenaSinUltimaEscuelaUsaLaPorDefecto() {
		UsuarioRepository usuarios = mock(UsuarioRepository.class);
		EmailService email = mock(EmailService.class);
		Usuario usuario = new Usuario();
		usuario.setId(5L);
		usuario.setEmail("cuenta@example.test");
		when(usuarios.findByEmailIgnoreCase("cuenta@example.test")).thenReturn(Optional.of(usuario));

		reset(usuarios, email).solicitarResetContrasena("cuenta@example.test");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmail(eq("cuenta@example.test"), anyString(), html.capture());
		assertTrue(html.getValue().contains("https://moiskimdo.es/reset-password?token="));
	}

	@Test
	void solicitudDeRegistroUsaLaEscuelaDelAlumno() {
		paraCadaEscuela((escuela, base) -> {
			RegistroPendienteRepository pendientes = mock(RegistroPendienteRepository.class);
			AlumnoRepository alumnos = mock(AlumnoRepository.class);
			UsuarioRepository usuarios = mock(UsuarioRepository.class);
			EmailService email = mock(EmailService.class);
			Alumno alumno = new Alumno();
			alumno.setId(5L);
			alumno.setEmail("familia@example.test");
			alumno.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(2015, 3, 4)));
			ReflectionTestUtils.setField(alumno, "organizacionId", escuela);
			when(usuarios.findByEmailIgnoreCase("familia@example.test")).thenReturn(Optional.empty());
			when(alumnos.findAllByEmailIgnoreCase("familia@example.test")).thenReturn(List.of(alumno));
			when(pendientes.findByEmail("familia@example.test")).thenReturn(Optional.empty());
			RegistroServiceImpl registro = new RegistroServiceImpl(pendientes, alumnos, usuarios,
					NoOpPasswordEncoder.getInstance(), email, null, null, MarcaEscuelaPruebas.soloMoiskimdo(), URLS);
			RegistroSolicitudRequest peticion = new RegistroSolicitudRequest();
			peticion.setEmail("familia@example.test");
			peticion.setFechaNacimiento("2015-03-04");
			peticion.setContrasena("Clave-1234");

			registro.solicitarRegistro(peticion);

			ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
			verify(email).sendEmail(eq("familia@example.test"), anyString(), html.capture());
			assertTrue(html.getValue().contains(base + "/registro-confirmar?token="), "escuela " + escuela);
		});
	}

	@Test
	void recordatorioDeRachaUsaLaEscuelaDelJob() throws Exception {
		Class<?> clase = Class.forName(RecordatorioRachaServiceImpl.class.getName() + "$AlumnoRachaEnRiesgo");
		var constructor = clase.getDeclaredConstructor(String.class, String.class, int.class);
		constructor.setAccessible(true);
		Object alumnoEnRiesgo = constructor.newInstance("Ana", "Activa", 5);
		paraCadaEscuela((escuela, base) -> {
			RecordatorioRachaServiceImpl racha = new RecordatorioRachaServiceImpl(null, null,
					mock(EmailService.class), MarcaEscuelaPruebas.soloMoiskimdo(), URLS);
			MarcaEscuela marca = new MarcaEscuela(escuela, true, "Escuela", "", "", null, null, null);
			Usuario usuario = new Usuario();
			usuario.setNombre("María");

			String html = ReflectionTestUtils.invokeMethod(racha, "construirHtmlRecordatorio", usuario,
					List.of(alumnoEnRiesgo), 3, marca);

			assertTrue(html.contains(base + "/userpage"), "escuela " + escuela);
		});
	}

	@Test
	void invitacionUsaLaEscuelaDelEventoSinPeticion() {
		paraCadaEscuela((escuela, base) -> {
			EmailService email = mock(EmailService.class);
			InvitacionCorreoListener listener = new InvitacionCorreoListener(email,
					MarcaEscuelaPruebas.soloMoiskimdo(), URLS);

			listener.alConfirmar(new InvitacionEnviableEvent(9L, escuela, "nuevo@example.test", "Nuevo",
					RolMembresia.FAMILIA, "tok123"));

			ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
			verify(email).sendEmailConImagen(eq("nuevo@example.test"), anyString(), html.capture(), any(), any(),
					any(), any());
			assertTrue(html.getValue().contains(base + "/crear-contrasena?token=tok123"), "escuela " + escuela);
		});
	}

	@Test
	void avisoAlBuzonDeLaPreinscripcionUsaLaEscuelaDeLaSolicitud() {
		paraCadaEscuela((escuela, base) -> {
			AforoPreinscripcionService aforo = mock(AforoPreinscripcionService.class);
			PreinscripcionService alta = new PreinscripcionService(mock(PreinscripcionRepository.class), null, null,
					null, null, null, null, null, null, mock(EmailService.class), null, new ObjectMapper(), aforo,
					null, null, null, null, null, null, null, null, MarcaEscuelaPruebas.soloMoiskimdo(), null, null,
					URLS, mock(com.taemoi.project.repositories.SedeRepository.class));

			String html = ReflectionTestUtils.invokeMethod(alta, "htmlAdmin", solicitud(escuela));

			assertTrue(html.contains("<a href='" + base + "/preinscripciones'>" + base + "/preinscripciones</a>"),
					"escuela " + escuela);
		});
	}

	@Test
	void finalizacionDeLaPreinscripcionUsaLaEscuelaDeLaSolicitud() {
		paraCadaEscuela((escuela, base) -> {
			PreinscripcionFinalizacionEmailService fin = new PreinscripcionFinalizacionEmailService(
					mock(PreinscripcionRepository.class), mock(UsuarioRepository.class), mock(EmailService.class),
					MarcaEscuelaPruebas.soloMoiskimdo(), URLS);

			String sinCuenta = fin.html(solicitud(escuela), "familia@example.test", null);
			String conCuenta = fin.html(solicitud(escuela), "familia@example.test", new Usuario());

			assertTrue(sinCuenta.contains(base + "/login?modo=registro"), "escuela " + escuela);
			assertTrue(conCuenta.contains("href='" + base + "/login'"), "escuela " + escuela);
		});
	}

	private static Preinscripcion solicitud(Long escuela) {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-2026-ABC");
		p.setNombre("Ana");
		p.setApellidos("García");
		p.setTemporada("2026/2027");
		p.setEstado(EstadoPreinscripcion.PENDIENTE);
		p.setGrupoSnapshot("Taekwondo");
		p.setEmail("familia@example.test");
		p.setTelefono("600000000");
		p.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi("TAEKWONDO"));
		ReflectionTestUtils.setField(p, "organizacionId", escuela);
		return p;
	}
}
