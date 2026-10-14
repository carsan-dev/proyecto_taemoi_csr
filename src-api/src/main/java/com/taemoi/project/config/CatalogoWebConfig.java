package com.taemoi.project.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag del catálogo ({@code /api/catalogo} y {@code /api/catalogo/publico}): el navegador revalida
 * ({@code Cache-Control: private, no-cache}) y recibe 304 si el JSON no cambió. Limitado a esas rutas: el
 * filtro guarda la respuesta entera en memoria para calcular el hash.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogoWebConfig {

	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> etagCatalogo() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registro = new FilterRegistrationBean<>(
				new ShallowEtagHeaderFilter());
		registro.addUrlPatterns("/api/catalogo", "/api/catalogo/*");
		registro.setName("etagCatalogo");
		return registro;
	}
}
