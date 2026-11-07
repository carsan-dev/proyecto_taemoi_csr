package com.taemoi.project.organizacion;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.taemoi.project.entities.OrganizacionDominio;
import com.taemoi.project.repositories.OrganizacionDominioRepository;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
public class OrganizacionWebConfig {

	/** Antes de Spring Security (orden -100) para que todo lo que corre dentro tenga organización. */
	static final int ORDEN = SecurityProperties.DEFAULT_FILTER_ORDER - 10;

	@Bean
	FilterRegistrationBean<OrganizacionRequestFilter> organizacionRequestFilter(ResolutorOrganizacion resolutor,
			IdentificadorSesion identificadorSesion, ResolutorEscuelaPorHost resolutorEscuelaPorHost) {
		FilterRegistrationBean<OrganizacionRequestFilter> registro = new FilterRegistrationBean<>(
				new OrganizacionRequestFilter(resolutor, identificadorSesion, resolutorEscuelaPorHost));
		registro.setOrder(ORDEN);
		registro.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
		registro.addUrlPatterns("/*");
		return registro;
	}

	/**
	 * Tabla {@code organizacion_dominio} en memoria. Se lee con la organización por defecto fijada (Hibernate exige una al
	 * abrir la sesión; la tabla es global y no se filtra).
	 */
	@Bean
	ResolutorEscuelaPorHost resolutorEscuelaPorHost(OrganizacionDominioRepository dominios,
			ResolutorOrganizacion resolutor) {
		Long porDefecto = resolutor.getOrganizacionPorDefecto();
		return new ResolutorEscuelaPorHost(() -> TenantContext.calcularCon(porDefecto, () -> {
			Map<String, Long> tabla = new HashMap<>();
			for (OrganizacionDominio dominio : dominios.findAll()) {
				tabla.put(dominio.getHost(), dominio.getOrganizacionId());
			}
			return tabla;
		}), porDefecto);
	}

	@Bean
	RecargaDominios recargaDominios(ResolutorEscuelaPorHost resolutorEscuelaPorHost) {
		return new RecargaDominios(resolutorEscuelaPorHost);
	}

	/**
	 * Carga la tabla al arrancar (antes de la primera petición) y la recarga cada minuto en segundo plano: los hilos de
	 * las peticiones no consultan la base para saber la escuela del Host.
	 */
	static class RecargaDominios {

		private final ResolutorEscuelaPorHost resolutor;

		RecargaDominios(ResolutorEscuelaPorHost resolutor) {
			this.resolutor = resolutor;
		}

		@EventListener(ApplicationReadyEvent.class)
		void alArrancar() {
			resolutor.recargar();
		}

		@Scheduled(fixedDelayString = "${app.organizacion.dominios.recarga-ms:60000}",
				initialDelayString = "${app.organizacion.dominios.recarga-ms:60000}")
		void cadaMinuto() {
			resolutor.recargar();
		}
	}
}
