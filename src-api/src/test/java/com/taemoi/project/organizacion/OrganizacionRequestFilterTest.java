package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class OrganizacionRequestFilterTest {

	private final OrganizacionRequestFilter filtro = new OrganizacionRequestFilter(new ResolutorOrganizacion(7L));

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
	void resolutorUsaElContextoYSiNoLaOrganizacionPorDefecto() {
		ResolutorOrganizacion resolutor = new ResolutorOrganizacion(1L);

		assertEquals(1L, resolutor.resolveCurrentTenantIdentifier());
		assertEquals(5L, TenantContext.calcularCon(5L, resolutor::resolveCurrentTenantIdentifier));
		assertNull(TenantContext.get());
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
