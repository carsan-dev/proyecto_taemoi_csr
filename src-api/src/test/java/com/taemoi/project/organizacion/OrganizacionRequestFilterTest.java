package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;

import jakarta.servlet.ServletException;

@ExtendWith(OutputCaptureExtension.class)
class OrganizacionRequestFilterTest {

	private final OrganizacionRequestFilter filtro = new OrganizacionRequestFilter(new ResolutorOrganizacion(7L),
			IdentificadorSesion.ANONIMO);

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void fijaLaOrganizacionDuranteLaPeticionYLaLimpiaAlTerminar() throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();

		filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(req, res) -> vista.set(TenantContext.get()));

		assertEquals(7L, vista.get());
		assertNull(TenantContext.get());
	}

	@Test
	void limpiaLaOrganizacionAunqueLaPeticionFalle() {
		assertThrows(ServletException.class, () -> filtro.doFilter(new MockHttpServletRequest(),
				new MockHttpServletResponse(), (req, res) -> {
					throw new ServletException("fallo");
				}));

		assertNull(TenantContext.get());
	}

	@Test
	void resolutorUsaElContextoYFallaCerradoSinEl() {
		ResolutorOrganizacion resolutor = new ResolutorOrganizacion(1L);

		assertEquals(5L, TenantContext.calcularCon(5L, resolutor::resolveCurrentTenantIdentifier));
		assertNull(TenantContext.get());
		IllegalStateException sinContexto = assertThrows(IllegalStateException.class,
				resolutor::resolveCurrentTenantIdentifier);
		assertTrue(sinContexto.getMessage().contains("organización"));
		assertThrows(IllegalStateException.class, resolutor::organizacionActual);
		assertEquals(1L, resolutor.getOrganizacionPorDefecto(), "La por defecto solo se usa a propósito");
	}

	@Test
	void sesionVerificadaFijaSuOrganizacionYLaIdentificacionCorreConLaPorDefecto() throws Exception {
		AtomicReference<Long> durante = new AtomicReference<>();
		AtomicReference<Long> vista = new AtomicReference<>();
		IdentificadorSesion identificador = request -> {
			durante.set(TenantContext.get());
			return Optional.of(new SesionVerificada(new Usuario(), 3L, Set.of(Roles.ROLE_ADMIN), null));
		};
		OrganizacionRequestFilter conSesion = new OrganizacionRequestFilter(new ResolutorOrganizacion(7L),
				identificador);
		MockHttpServletRequest request = new MockHttpServletRequest();

		conSesion.doFilter(request, new MockHttpServletResponse(), (req, res) -> vista.set(TenantContext.get()));

		assertEquals(7L, durante.get(), "La búsqueda de la sesión (tablas globales) usa la organización por defecto");
		assertEquals(3L, vista.get());
		assertTrue(request.getAttribute(SesionVerificada.ATRIBUTO) instanceof SesionVerificada);
		assertNull(TenantContext.get());
	}

	@Test
	void elDespachoErrorTieneOrganizacionYNoVuelveAVerificarElToken() throws Exception {
		java.util.concurrent.atomic.AtomicInteger verificaciones = new java.util.concurrent.atomic.AtomicInteger();
		OrganizacionRequestFilter conSesion = new OrganizacionRequestFilter(new ResolutorOrganizacion(7L), request -> {
			verificaciones.incrementAndGet();
			return Optional.of(new SesionVerificada(new Usuario(), 3L, Set.of(Roles.ROLE_USER), null));
		});
		MockHttpServletRequest request = new MockHttpServletRequest();
		AtomicReference<Long> enError = new AtomicReference<>();

		conSesion.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
		request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
		conSesion.doFilter(request, new MockHttpServletResponse(), (req, res) -> enError.set(TenantContext.get()));

		assertEquals(3L, enError.get(), "/error corre en la organización de la sesión");
		assertEquals(1, verificaciones.get(), "El token se verifica una sola vez por petición");
		assertNull(TenantContext.get());
	}

	@Test
	void sinSesionVerificadaLaPeticionEsAnonimaEnLaPorDefecto() throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();
		MockHttpServletRequest request = new MockHttpServletRequest();

		filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> vista.set(TenantContext.get()));

		assertEquals(7L, vista.get());
		assertNull(request.getAttribute(SesionVerificada.ATRIBUTO));
		assertEquals(Boolean.TRUE, request.getAttribute(SesionVerificada.ATRIBUTO_RESUELTA));
	}

	@Test
	void decoradorNoHeredaNiDejaOrganizacionEnElHiloDelPool() {
		OrganizacionTaskDecorator decorador = new OrganizacionTaskDecorator();
		AtomicReference<Long> vista = new AtomicReference<>();
		Runnable conOrganizacion = TenantContext.calcularCon(3L,
				() -> decorador.decorate(() -> vista.set(TenantContext.get())));
		Runnable sinOrganizacion = decorador.decorate(() -> vista.set(TenantContext.get()));

		TenantContext.set(9L); // organización residual de una tarea anterior en el hilo del pool
		conOrganizacion.run();
		assertEquals(3L, vista.get());
		sinOrganizacion.run();
		assertNull(vista.get());
		assertEquals(9L, TenantContext.get(), "Se restaura lo que había en el hilo");
	}

	// ------------------------------------------------- fase 4, escuela por dominio (pieza 1)

	/** Por defecto 7; moiskimdo.es es la 7 (como la por defecto) y x.test la 5. */
	private final ResolutorEscuelaPorHost porHost = new ResolutorEscuelaPorHost(
			() -> java.util.Map.of("moiskimdo.es", 7L, "x.test", 5L), 7L);

	private OrganizacionRequestFilter filtroPorHost(IdentificadorSesion identificador) {
		return new OrganizacionRequestFilter(new ResolutorOrganizacion(7L), identificador, porHost);
	}

	private static MockHttpServletRequest conHost(String host) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Host", host);
		return request;
	}

	private static IdentificadorSesion sesionEn(long organizacion) {
		return request -> Optional.of(new SesionVerificada(new Usuario(), organizacion, Set.of(Roles.ROLE_ADMIN), null));
	}

	@Test
	void anonimaConHostRegistrado_vaASuEscuelaYLaGuardaEnLaPeticion() throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();
		MockHttpServletRequest request = conHost("X.TEST:443");

		filtroPorHost(IdentificadorSesion.ANONIMO).doFilter(request, new MockHttpServletResponse(),
				(req, res) -> vista.set(TenantContext.get()));

		assertEquals(5L, vista.get());
		assertEquals(5L, request.getAttribute(ResolutorEscuelaPorHost.ATRIBUTO));
		assertNull(TenantContext.get());
	}

	@Test
	void anonimaConElHostDeLaPorDefectoODesconocido_vaALaPorDefectoComoHastaAhora() throws Exception {
		for (String host : java.util.List.of("moiskimdo.es", "desconocido.test", "127.0.0.1", "localhost")) {
			AtomicReference<Long> vista = new AtomicReference<>();
			MockHttpServletRequest request = conHost(host);
			filtroPorHost(IdentificadorSesion.ANONIMO).doFilter(request, new MockHttpServletResponse(),
					(req, res) -> vista.set(TenantContext.get()));
			assertEquals(7L, vista.get(), host);
			assertEquals(7L, request.getAttribute(ResolutorEscuelaPorHost.ATRIBUTO), host);
		}
	}

	@Test
	void conSesion_mandaLaSesionSobreElHostYAvisaUnaVezPorPeticion(CapturedOutput salida) throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();
		AtomicReference<Long> enError = new AtomicReference<>();
		OrganizacionRequestFilter conSesion = filtroPorHost(sesionEn(3L));
		MockHttpServletRequest request = conHost("x.test");

		conSesion.doFilter(request, new MockHttpServletResponse(), (req, res) -> vista.set(TenantContext.get()));
		request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
		conSesion.doFilter(request, new MockHttpServletResponse(), (req, res) -> enError.set(TenantContext.get()));

		assertEquals(3L, vista.get(), "la sesión manda");
		assertEquals(3L, enError.get());
		assertEquals(5L, request.getAttribute(ResolutorEscuelaPorHost.ATRIBUTO), "la escuela del host queda guardada");
		assertEquals(1, ocurrencias(salida.getOut() + salida.getErr(), ResolutorEscuelaPorHost.AVISO_SESION_Y_HOST),
				"un aviso por petición");
		assertNull(TenantContext.get());
	}

	@Test
	void conSesionEnElHostDeLaPorDefecto_noAvisa(CapturedOutput salida) throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();

		filtroPorHost(sesionEn(3L)).doFilter(conHost("moiskimdo.es"), new MockHttpServletResponse(),
				(req, res) -> vista.set(TenantContext.get()));
		filtroPorHost(sesionEn(5L)).doFilter(conHost("x.test"), new MockHttpServletResponse(), (req, res) -> { });

		assertEquals(3L, vista.get(), "multiescuela en el host de siempre: igual que hoy");
		assertEquals(0, ocurrencias(salida.getOut() + salida.getErr(), ResolutorEscuelaPorHost.AVISO_SESION_Y_HOST));
	}

	@Test
	void constructorSinResolutorDeHost_esElComportamientoDeSiempre() throws Exception {
		AtomicReference<Long> vista = new AtomicReference<>();
		MockHttpServletRequest request = conHost("x.test");

		filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> vista.set(TenantContext.get()));

		assertEquals(7L, vista.get());
		assertEquals(7L, request.getAttribute(ResolutorEscuelaPorHost.ATRIBUTO));
	}

	private static int ocurrencias(String texto, String patron) {
		int total = 0;
		for (int i = texto.indexOf(patron); i >= 0; i = texto.indexOf(patron, i + patron.length())) {
			total++;
		}
		return total;
	}
}
