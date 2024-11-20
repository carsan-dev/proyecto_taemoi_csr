package com.taemoi.project.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taemoi.project.entidades.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long>{

}
