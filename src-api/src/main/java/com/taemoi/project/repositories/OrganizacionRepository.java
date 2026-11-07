package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.Organizacion;

import jakarta.persistence.LockModeType;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {

	List<Organizacion> findByActivaTrueOrderByIdAsc();

	Optional<Organizacion> findBySlug(String slug);

	/** Pieza E7-7a: la escuela bloqueada para cambiar su logo (dos subidas a la vez no dejan un logo huérfano). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Organizacion o WHERE o.id = :id")
	Optional<Organizacion> findByIdParaActualizar(@Param("id") Long id);

	/**
	 * Fase 4, pieza F4-2: alta de una escuela con el id YA RESERVADO ({@code AltaEscuelaService}): la transacción del alta
	 * se abre con esa escuela fijada (Hibernate resuelve la escuela al abrir la sesión), así que el id debe conocerse
	 * antes. SQL nativo sobre una tabla global (sin {@code organizacion_id}); el resto de columnas, sus valores por
	 * defecto. Un slug repetido da {@code DataIntegrityViolationException}.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO organizacion (id, slug, nombre, email_contacto, zona_horaria, activa, comportamiento_legado_taemoi)
			VALUES (:id, :slug, :nombre, :emailContacto, :zonaHoraria, TRUE, FALSE)""", nativeQuery = true)
	int insertarConId(@Param("id") Long id, @Param("slug") String slug, @Param("nombre") String nombre,
			@Param("emailContacto") String emailContacto, @Param("zonaHoraria") String zonaHoraria);

	/**
	 * Selector de escuela: marca de varias escuelas de una vez. Filas {@code [organizacionId, imagenId|null]}: el id de
	 * la imagen del logo SOLO si pertenece a esa escuela ({@code i.organizacion_id = o.id}). SQL nativo: {@code imagen}
	 * lleva {@code @TenantId} y aquí se leen escuelas que no son la activa. Sin datos personales.
	 */
	@Query(value = """
			SELECT o.id, i.id FROM organizacion o
			LEFT JOIN imagen i ON i.id = o.logo_imagen_id AND i.organizacion_id = o.id
			WHERE o.id IN (:ids)""", nativeQuery = true)
	List<Object[]> logosDeEscuelas(@Param("ids") java.util.Collection<Long> ids);

	/**
	 * Selector de escuela: sedes activas por escuela, de una vez. Filas {@code [organizacionId, total]}; una escuela sin
	 * sedes activas no sale. SQL nativo por el mismo motivo que {@link #logosDeEscuelas}.
	 */
	@Query(value = """
			SELECT s.organizacion_id, COUNT(*) FROM sede s
			WHERE s.activa = TRUE AND s.organizacion_id IN (:ids)
			GROUP BY s.organizacion_id""", nativeQuery = true)
	List<Object[]> sedesActivasPorEscuela(@Param("ids") java.util.Collection<Long> ids);
}
