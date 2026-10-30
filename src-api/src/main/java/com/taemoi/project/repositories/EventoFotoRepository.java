package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taemoi.project.entities.EventoFoto;

/** Fotos de eventos; todas las consultas van filtradas por la escuela activa ({@code @TenantId}). */
public interface EventoFotoRepository extends JpaRepository<EventoFoto, Long> {

	/** Fotos de un evento, para la lista de eventos. */
	interface NumeroFotos {
		Long getEventoId();

		Integer getTotal();
	}

	/** El mismo orden en todas las lecturas: dos fotos con el mismo orden se deshacen por id. */
	@Query("SELECT f FROM EventoFoto f WHERE f.evento.id = :eventoId ORDER BY f.orden ASC, f.id ASC")
	List<EventoFoto> listarDeEvento(@Param("eventoId") Long eventoId);

	@Query("SELECT f FROM EventoFoto f JOIN FETCH f.imagen WHERE f.evento.id = :eventoId ORDER BY f.orden ASC, f.id ASC")
	List<EventoFoto> listarDeEventoConImagen(@Param("eventoId") Long eventoId);

	/** Vacío si la foto no existe, es de otra escuela o es de otro evento. */
	@Query("SELECT f FROM EventoFoto f JOIN FETCH f.imagen WHERE f.id = :fotoId AND f.evento.id = :eventoId")
	Optional<EventoFoto> buscarConImagen(@Param("eventoId") Long eventoId, @Param("fotoId") Long fotoId);

	@Query("SELECT COUNT(f) FROM EventoFoto f WHERE f.evento.id = :eventoId")
	long contarDeEvento(@Param("eventoId") Long eventoId);

	@Query("SELECT COALESCE(MAX(f.orden), 0) FROM EventoFoto f WHERE f.evento.id = :eventoId")
	int ultimoOrden(@Param("eventoId") Long eventoId);

	/** Una sola consulta para toda la lista de eventos: sin fila, el evento no tiene fotos. */
	@Query("SELECT f.evento.id AS eventoId, CAST(COUNT(f) AS Integer) AS total FROM EventoFoto f GROUP BY f.evento.id")
	List<NumeroFotos> contarPorEvento();
}
