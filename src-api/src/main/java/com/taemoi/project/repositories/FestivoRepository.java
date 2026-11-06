package com.taemoi.project.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Festivo;
import com.taemoi.project.entities.TipoFestivo;

import jakarta.persistence.LockModeType;

/** Festivos de la escuela activa ({@code @TenantId}). */
@Repository
public interface FestivoRepository extends JpaRepository<Festivo, Long> {

	/** Por id con una consulta (filtrada por la escuela activa), como {@code SedeRepository#buscarEnEscuelaActiva}. */
	@Query("SELECT f FROM Festivo f WHERE f.id = :id")
	Optional<Festivo> buscarEnEscuelaActiva(@Param("id") Long id);

	/** Ídem, bloqueado ({@code SELECT ... FOR UPDATE}) para cambiarlo o quitarlo sin que dos peticiones se mezclen. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT f FROM Festivo f WHERE f.id = :id")
	Optional<Festivo> bloquearEnEscuelaActiva(@Param("id") Long id);

	/** Los de hoy en adelante ({@code fechaHasta >= hoy}) y todos los anuales. Una consulta. */
	@Query("SELECT f FROM Festivo f WHERE f.tipo = :anual OR f.fechaHasta >= :hoy")
	List<Festivo> deHoyEnAdelante(@Param("hoy") LocalDate hoy, @Param("anual") TipoFestivo anual);

	/** Los ya pasados ({@code fechaHasta < hoy}), sin los anuales. Una consulta. */
	@Query("SELECT f FROM Festivo f WHERE f.tipo <> :anual AND f.fechaHasta < :hoy")
	List<Festivo> pasados(@Param("hoy") LocalDate hoy, @Param("anual") TipoFestivo anual);

	/**
	 * Filas {@code [sedeId, festivo]} de los festivos de esas sedes que pueden caer entre {@code desde} y {@code hasta},
	 * ambas incluidas: los de fechas que se solapan con el intervalo y todos los anuales (la fecha exacta se calcula en
	 * memoria). Una consulta para todas las sedes, sin N+1.
	 */
	@Query("""
			SELECT fs.sedeId, f FROM FestivoSede fs JOIN Festivo f ON f.id = fs.festivoId
			WHERE fs.sedeId IN :sedes
			  AND (f.tipo = :anual OR (f.fechaDesde <= :hasta AND f.fechaHasta >= :desde))""")
	List<Object[]> deLasSedesEntre(@Param("sedes") Collection<Long> sedes, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta, @Param("anual") TipoFestivo anual);
}
