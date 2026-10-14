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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;

import jakarta.servlet.ServletException;

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
}
