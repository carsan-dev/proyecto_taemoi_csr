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
}
