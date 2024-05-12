package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Horario;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

}
