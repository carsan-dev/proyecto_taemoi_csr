package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Grupo;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {

}
