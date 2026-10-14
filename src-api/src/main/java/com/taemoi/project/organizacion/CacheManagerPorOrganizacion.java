package com.taemoi.project.organizacion;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Decora un {@link CacheManager} dinámico para que cada caché sea independiente por
 * organización: {@code getCache("alumnos")} devuelve la instancia {@code alumnos@org-<id>}
 * de la organización activa. Así ninguna clave (tampoco las explícitas de
 * {@code @Cacheable(key = ...)}) puede cruzar escuelas, y {@code clear()} /
 * {@code @CacheEvict(allEntries = true)} solo vacía la caché de la organización activa.
 */
public class CacheManagerPorOrganizacion implements CacheManager {

	private final CacheManager delegado;
	private final List<String> nombres;
	private final Set<String> nombresPermitidos;
	private final Supplier<Long> organizacionActual;

	public CacheManagerPorOrganizacion(CacheManager delegado, Collection<String> nombres,
			Supplier<Long> organizacionActual) {
		this.delegado = delegado;
		this.nombres = List.copyOf(nombres);
		this.nombresPermitidos = Set.copyOf(nombres);
		this.organizacionActual = organizacionActual;
	}

	@Override
	public Cache getCache(String nombre) {
		if (!nombresPermitidos.contains(nombre)) {
			return null;
		}
		return delegado.getCache(nombreInterno(nombre, organizacionActual.get()));
	}

	@Override
	public Collection<String> getCacheNames() {
		return nombres;
	}

	static String nombreInterno(String nombre, Long organizacionId) {
		if (organizacionId == null) {
			throw new IllegalStateException("No hay organización activa para la caché " + nombre);
		}
		return nombre + "@org-" + organizacionId;
	}
}
