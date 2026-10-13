package com.taemoi.project.organizacion;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;

/**
 * Crea los repositorios Spring Data con una organización fijada.
 *
 * <p>Al crear un repositorio, Spring Data valida cada {@code @Query} abriendo un EntityManager, y
 * Hibernate pide la organización al abrir cualquier sesión. Como {@link ResolutorOrganizacion} falla
 * cerrado, la creación se hace con la organización por defecto fijada explícitamente: esa sesión de
 * validación no lee ni escribe datos. Si el repositorio se crea dentro de una petición o tarea que ya
 * tiene organización, se restaura al terminar.
 */
public class RepositorioConOrganizacionFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
		extends JpaRepositoryFactoryBean<T, S, ID> {

	private ResolutorOrganizacion resolutor;

	public RepositorioConOrganizacionFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
	}

	@Autowired
	public void setResolutorOrganizacion(ResolutorOrganizacion resolutor) {
		this.resolutor = resolutor;
	}

	@Override
	public void afterPropertiesSet() {
		if (resolutor == null) {
			throw new IllegalStateException("ResolutorOrganizacion no inyectado en " + getClass().getSimpleName());
		}
		TenantContext.ejecutarCon(resolutor.getOrganizacionPorDefecto(), super::afterPropertiesSet);
	}
}
