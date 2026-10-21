package com.taemoi.project.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taemoi.project.entities.DisenoPanelUsuario;

/**
 * Interruptor del diseño nuevo (pieza 8b-I). {@code @TenantId}: toda consulta va filtrada por la escuela activa; la fila
 * de la misma cuenta en otra escuela no existe aquí.
 */
public interface DisenoPanelUsuarioRepository extends JpaRepository<DisenoPanelUsuario, Long> {

	Optional<DisenoPanelUsuario> findByUsuarioId(Long usuarioId);

	List<DisenoPanelUsuario> findByUsuarioIdIn(Collection<Long> usuarioIds);
}
