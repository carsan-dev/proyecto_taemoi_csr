package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.impl.PasswordResetServiceImpl;
import com.taemoi.project.services.impl.RecordatorioRachaServiceImpl;
import com.taemoi.project.services.impl.RegistroServiceImpl;

/**
 * Pieza E7-7a (decisión 211): firma y nombre de la escuela en los correos. Los dorados de {@code dorado/marca} se
 * volcaron con el código de ANTES del cambio (asunto en la primera línea cuando lo hay); Moiskimdo debe dar los mismos
 * bytes. La escuela sale de la solicitud (no del hilo), como en los avisos que se envían tras el commit.
 */
class MarcaEscuelaCorreosTest {

	private static final String NOMBRE_OTRA = "Escuela Ficticia Norte & Sur";
	private static final String NOMBRE_OTRA_HTML = "Escuela Ficticia Norte &amp; Sur";

	private final PreinscripcionRepository repo = mock(PreinscripcionRepository.class);
	private final EmailService email = mock(EmailService.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final MarcaEscuelaService marca = MarcaEscuelaPruebas.real(organizaciones, mock(ImagenRepository.class),
			mock(ImagenService.class), MarcaEscuelaPruebas.moiskimdo(), MarcaEscuelaPruebas.escuela(2L, NOMBRE_OTRA));

	@AfterEach
	void sinEscuelaEnElHilo() {
		assertNull(TenantContext.get(), "El test dejó una escuela fijada en el hilo");
	}

	@Test
	void moiskimdoCorreosIdenticos() throws Exception {
		Map<String, String> correos = correos(1L);
		assertEquals(11, correos.size());
		for (Map.Entry<String, String> correo : correos.entrySet()) {
			assertEquals(dorado(correo.getKey()), correo.getValue(), correo.getKey());
		}
	}

	@Test
	void escuelaSinLogoSoloNombre_sinRastroDeMoiskimdo() throws Exception {
		for (Map.Entry<String, String> correo : correos(2L).entrySet()) {
			String cuerpo = correo.getValue();
			assertTrue(cuerpo.contains(NOMBRE_OTRA_HTML), correo.getKey() + ": " + cuerpo);
			assertFalse(cuerpo.contains(NOMBRE_OTRA), "nombre sin escapar en " + correo.getKey());
			String normalizado = cuerpo.toUpperCase(Locale.ROOT);
			for (String rastro : List.of("MOI'S", "MOI&#39;S", "KIM DO", "MOISKIMDO")) {
				assertFalse(normalizado.contains(rastro), rastro + " en " + correo.getKey() + ": " + cuerpo);
			}
			// Mismo correo que el de Moiskimdo salvo el nombre de la escuela.
			String deMoiskimdo = dorado(correo.getKey());
			assertEquals(deMoiskimdo.replace("Club Moiskimdo Taekwondo", NOMBRE_OTRA_HTML)
					.replace("Moi's Kim Do", NOMBRE_OTRA_HTML), cuerpo, correo.getKey());
		}
	}

	@Test
	void correoAsincronoUsaLaEscuelaDeLaPreinscripcion() {
		Preinscripcion p = preinscripcion(2L);
		when(repo.findByReferenciaForUpdate("PRE-2026-MARCA")).thenReturn(Optional.of(p));
		List<Long> escuelaAlLeer = new ArrayList<>();
		when(organizaciones.findById(anyLong())).thenAnswer(inv -> {
			escuelaAlLeer.add(TenantContext.get());
			Long id = inv.getArgument(0);
			if (id != 2L) {
				return Optional.of(MarcaEscuelaPruebas.moiskimdo());
			}
			Organizacion otra = MarcaEscuelaPruebas.escuela(2L, NOMBRE_OTRA);
			// Segunda lectura: la escuela ya tiene nombre de remitente propio.
			if (escuelaAlLeer.size() > 1) {
				otra.setEmailRemitenteNombre("Club Ficticio Remitente");
			}
			return Optional.of(otra);
		});

		// Hilo sin escuela, como el aviso tras el commit.
		assertNull(TenantContext.get());
		new PreinscripcionCambioTurnosEmailService(repo, email, marca).enviar("PRE-2026-MARCA");

		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		// Remitente visible: sin nombre de remitente propio, el nombre de la escuela.
		verify(email).sendEmailSync(eq("familia@example.test"), anyString(), cuerpo.capture(), eq(NOMBRE_OTRA));
		assertTrue(cuerpo.getValue().contains("Un saludo,<br><strong>" + NOMBRE_OTRA_HTML + "</strong>"),
				cuerpo.getValue());
		assertEquals(List.of(2L), escuelaAlLeer, "la escuela se lee con la de la solicitud fijada");
		assertNull(TenantContext.get());

		// Con OTRA escuela en el hilo, manda la de la solicitud.
		marca.invalidar(2L);
		TenantContext.ejecutarCon(1L,
				() -> new PreinscripcionCancelacionEmailService(repo, email, marca).enviar("PRE-2026-MARCA"));
		ArgumentCaptor<String> cancelacion = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailSync(eq("familia@example.test"), eq("Tu preinscripción PRE-2026-MARCA ha sido cancelada"),
				cancelacion.capture(), eq("Club Ficticio Remitente"));
		assertTrue(cancelacion.getValue().contains("<strong>" + NOMBRE_OTRA_HTML + "</strong></p></div>"));
		assertEquals(List.of(2L, 2L), escuelaAlLeer);
		verify(email, never()).sendEmailSync(anyString(), anyString(), anyString());
	}

	@Test
	void moiskimdoSaleConElRemitenteDeSiempre() {
		Preinscripcion p = preinscripcion(1L);
		when(repo.findByReferenciaForUpdate("PRE-2026-MARCA")).thenReturn(Optional.of(p));
		new PreinscripcionCancelacionEmailService(repo, email, marca).enviar("PRE-2026-MARCA");
		// El método de siempre (sin remitente): EmailServiceImpl pone app.mail.from-name, «Moi's Kim Do».
		verify(email).sendEmailSync(eq("familia@example.test"), eq("Tu preinscripción PRE-2026-MARCA ha sido cancelada"),
				anyString());
		verify(email, never()).sendEmailSync(anyString(), anyString(), anyString(), any());
		assertNull(marca.de(1L).remitente());
		assertEquals(NOMBRE_OTRA, marca.de(2L).remitente());
	}

	// ------------------------------------------------------------------ correos de una escuela

	/** Los once correos con nombre de escuela, para la escuela dada (en el orden de los dorados). */
	private Map<String, String> correos(long escuela) throws Exception {
		Map<String, String> correos = new LinkedHashMap<>();
		Preinscripcion p = preinscripcion(escuela);

		AforoPreinscripcionService aforo = mock(AforoPreinscripcionService.class);
		when(aforo.posicion(any(Preinscripcion.class))).thenReturn(3L);
		CatalogoDisciplinasService catalogo = mock(CatalogoDisciplinasService.class);
		when(catalogo.obtenerPorCodigo(anyString())).thenAnswer(
				inv -> com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(inv.getArgument(0)));
		PreinscripcionService alta = new PreinscripcionService(repo, null, null, null, null, null, null, null, null,
				email, null, new ObjectMapper(), aforo, null, null, null, null, catalogo,
				new com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria().tarifas(), null, null, marca);
		correos.put("alta-pendiente.html", ReflectionTestUtils.invokeMethod(alta, "asuntoCliente", p) + "\n"
				+ ReflectionTestUtils.invokeMethod(alta, "htmlConfirmacion", p));
		p.setEstado(EstadoPreinscripcion.EN_LISTA_ESPERA);
		correos.put("alta-espera.html", ReflectionTestUtils.invokeMethod(alta, "asuntoCliente", p) + "\n"
				+ ReflectionTestUtils.invokeMethod(alta, "htmlConfirmacion", p));
		correos.put("solicitud-original.html", "Copia de la solicitud original · " + p.getReferencia() + "\n"
				+ ReflectionTestUtils.invokeMethod(alta, "htmlSolicitudOriginal", p));
		correos.put("cambio-turnos.html", "Horarios actualizados · " + p.getReferencia() + "\n"
				+ new PreinscripcionCambioTurnosEmailService(repo, email, marca).html(p));
		correos.put("cancelacion.html", new PreinscripcionCancelacionEmailService(repo, email, marca).html(p));

		PreinscripcionFinalizacionEmailService fin = new PreinscripcionFinalizacionEmailService(repo,
				mock(UsuarioRepository.class), email, marca);
		ReflectionTestUtils.setField(fin, "frontendBaseUrl", "https://frontend.example.test/");
		Usuario google = new Usuario();
		google.setAuthProvider(AuthProvider.GOOGLE);
		correos.put("finalizacion-sin-cuenta.html", fin.html(p, "familia@example.test", null));
		correos.put("finalizacion-local.html", fin.html(p, "familia@example.test", new Usuario()));
		correos.put("finalizacion-google.html", fin.html(p, "familia@example.test", google));

		MarcaEscuela deLaEscuela = marca.de(escuela);
		RegistroServiceImpl registro = new RegistroServiceImpl(null, null, null, null, email, null, null, marca);
		correos.put("registro.html", ReflectionTestUtils.invokeMethod(registro, "buildRegistroEmailHtml", "María",
				"https://frontend.example.test/confirmar?token=abc", 1L, deLaEscuela));
		PasswordResetServiceImpl reset = new PasswordResetServiceImpl(null, null, email, marca, null, null);
		correos.put("restablecer.html", ReflectionTestUtils.invokeMethod(reset, "buildResetEmailHtml", "María",
				"https://frontend.example.test/reset-password?token=abc", 1L, deLaEscuela));
		RecordatorioRachaServiceImpl racha = new RecordatorioRachaServiceImpl(null, null, email, marca);
		ReflectionTestUtils.setField(racha, "frontendBaseUrl", "https://frontend.example.test");
		Usuario usuario = new Usuario();
		usuario.setNombre("María");
		Class<?> clase = Class.forName(RecordatorioRachaServiceImpl.class.getName() + "$AlumnoRachaEnRiesgo");
		var constructor = clase.getDeclaredConstructor(String.class, String.class, int.class);
		constructor.setAccessible(true);
		correos.put("racha.html", ReflectionTestUtils.invokeMethod(racha, "construirHtmlRecordatorio", usuario,
				List.of(constructor.newInstance("Ana", "Prueba", 4)), 3, deLaEscuela));
		return correos;
	}

	private static Preinscripcion preinscripcion(long escuela) {
		Preinscripcion p = MarcaEscuelaCorreosDatos.preinscripcion();
		ReflectionTestUtils.setField(p, "organizacionId", escuela);
		return p;
	}

	private static String dorado(String nombre) throws IOException {
		try (InputStream entrada = new ClassPathResource("dorado/marca/" + nombre).getInputStream()) {
			return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
