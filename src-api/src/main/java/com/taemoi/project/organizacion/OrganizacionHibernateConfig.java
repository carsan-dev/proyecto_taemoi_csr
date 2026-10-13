package com.taemoi.project.organizacion;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link ResolutorOrganizacion} en Hibernate. Los tests de slice JPA
 * ({@code @DataJpaTest}) deben importar esta configuración.
 */
@Configuration(proxyBeanMethods = false)
public class OrganizacionHibernateConfig {

	@Bean
	ResolutorOrganizacion resolutorOrganizacion(
			@Value("${app.organizacion.por-defecto:1}") Long organizacionPorDefecto) {
		return new ResolutorOrganizacion(organizacionPorDefecto);
	}

	@Bean
	HibernatePropertiesCustomizer organizacionHibernatePropertiesCustomizer(ResolutorOrganizacion resolutor) {
		return propiedades -> propiedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolutor);
	}
}
