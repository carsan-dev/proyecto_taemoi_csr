package com.taemoi.project.organizacion;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
public class OrganizacionWebConfig {

	/** Antes de Spring Security (orden -100) para que todo lo que corre dentro tenga organización. */
	static final int ORDEN = SecurityProperties.DEFAULT_FILTER_ORDER - 10;

	@Bean
	FilterRegistrationBean<OrganizacionRequestFilter> organizacionRequestFilter(ResolutorOrganizacion resolutor) {
		FilterRegistrationBean<OrganizacionRequestFilter> registro =
				new FilterRegistrationBean<>(new OrganizacionRequestFilter(resolutor));
		registro.setOrder(ORDEN);
		registro.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
		registro.addUrlPatterns("/*");
		return registro;
	}
}
