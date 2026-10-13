package com.taemoi.project.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entities.Disciplina;

/** Disciplinas de la organización activa (filtradas por {@code @TenantId}). */
@Repository
public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {

	Optional<Disciplina> findByCodigo(String codigo);

	List<Disciplina> findByActivaTrueOrderByOrdenAscIdAsc();
}
