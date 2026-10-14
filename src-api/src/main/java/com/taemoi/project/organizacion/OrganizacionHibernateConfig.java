package com.taemoi.project.organizacion;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.taemoi.project.TaeMoiApplication;

/**
 * Registra {@link ResolutorOrganizacion} en Hibernate y crea los repositorios con
 * {@link RepositorioConOrganizacionFactoryBean} (sustituye a la autoconfiguración de repositorios de
 * Spring Boot, con el mismo paquete base). Los tests de slice JPA ({@code @DataJpaTest}) deben
 * importar esta configuración.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = TaeMoiApplication.class,
		repositoryFactoryBeanClass = RepositorioConOrganizacionFactoryBean.class)
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
