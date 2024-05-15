package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Turno;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {

}
