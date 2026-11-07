package com.taemoi.project.web;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.entities.WebBloque;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Consultas de la web por plantilla (pieza W1). Todas JPQL sobre entidades con {@code @TenantId}: Hibernate las filtra
 * por la escuela activa (nunca sale nada de otra escuela). Cada una es UNA sentencia, sea cual sea el número de sedes,
 * grupos o eventos (sin N+1).
 */
@Repository
public class WebPublicaConsultas {

	@PersistenceContext
	private EntityManager em;

	/** Bloques de la escuela, por orden y clave. */
	public List<WebBloque> bloques() {
		return em.createQuery("SELECT b FROM WebBloque b ORDER BY b.orden, b.clave", WebBloque.class).getResultList();
	}

	/** Filas {@code [id, url]} de las imágenes dadas de la escuela activa (las de otra escuela no salen). */
	public List<Object[]> urlsDeImagenes(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return em.createQuery("SELECT i.id, i.url FROM Imagen i WHERE i.id IN :ids", Object[].class)
				.setParameter("ids", ids).getResultList();
	}

	/** Grupos de las sedes dadas con su disciplina (activa), en una consulta. */
	public List<Grupo> gruposDeSedes(Collection<Long> sedes) {
		if (sedes.isEmpty()) {
			return List.of();
		}
		return em.createQuery("""
				SELECT g FROM Grupo g JOIN FETCH g.disciplina d
				WHERE g.sedeId IN :sedes AND d.activa = true ORDER BY g.id""", Grupo.class)
				.setParameter("sedes", sedes).getResultList();
	}

	/** Tarifas activas con su disciplina, en el orden del catálogo; las de una disciplina inactiva no salen. */
	public List<Tarifa> tarifasActivas() {
		return em.createQuery("""
				SELECT t FROM Tarifa t LEFT JOIN FETCH t.disciplina d
				WHERE t.activa = true AND (d IS NULL OR d.activa = true) ORDER BY t.orden, t.id""", Tarifa.class)
				.getResultList();
	}

	/** Filas {@code [codigo de la disciplina, concepto, precio]}: la licencia de cada disciplina activa que la tiene. */
	public List<Object[]> licencias() {
		return em.createQuery("""
				SELECT d.codigo, p.concepto, p.precio FROM Disciplina d JOIN d.productoLicencia p
				WHERE d.activa = true ORDER BY d.orden, d.id""", Object[].class).getResultList();
	}

	/**
	 * Menor precio (mayor que 0) de los productos de examen generales o de una disciplina activa; {@code null} si no
	 * hay.
	 */
	public Double examenDesde() {
		return em.createQuery("""
				SELECT MIN(p.precio) FROM Producto p LEFT JOIN p.disciplina d
				WHERE p.tipo = :tipo AND p.precio > 0 AND (d IS NULL OR d.activa = true)""", Double.class)
				.setParameter("tipo", TipoProducto.EXAMEN).getSingleResult();
	}

	/**
	 * Filas {@code [id, titulo, fecha, id de la foto o null, hora, lugar, id de la sede, nombre de la sede]} de los
	 * {@code maximo} próximos eventos visibles (fecha de hoy o posterior), del más cercano al más lejano. La sede, solo
	 * si es de la misma escuela que el evento (si no, {@code null} las dos).
	 */
	public List<Object[]> proximosEventos(LocalDate hoy, int maximo) {
		return em.createQuery("""
				SELECT e.id, e.titulo, e.fechaEvento, f.id, e.hora, e.lugar, s.id, s.nombre
				FROM Evento e LEFT JOIN e.fotoEvento f
				  LEFT JOIN Sede s ON s.id = e.sedeId AND s.organizacionId = e.organizacionId
				WHERE e.visible = true AND e.fechaEvento >= :hoy ORDER BY e.fechaEvento, e.id""", Object[].class)
				.setParameter("hoy", hoy).setMaxResults(maximo).getResultList();
	}

	/** Plantillas de preinscripción activas de las disciplinas activas, por disciplina y de la versión más alta a la más baja. */
	public List<PlantillaPreinscripcion> plantillasActivas() {
		return em.createQuery("""
				SELECT p FROM PlantillaPreinscripcion p JOIN FETCH p.disciplina d
				WHERE p.activa = true AND d.activa = true ORDER BY d.orden, d.id, p.version DESC""",
				PlantillaPreinscripcion.class).getResultList();
	}
}
