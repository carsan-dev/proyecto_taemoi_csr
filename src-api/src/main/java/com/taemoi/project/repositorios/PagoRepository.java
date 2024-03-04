package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Pago;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

}
