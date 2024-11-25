package com.taemoi.project.entidades;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class Producto {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String concepto;

	private Double precio;

	@OneToMany(mappedBy = "producto")
	@JsonIgnore
	private List<ProductoAlumno> productosAlumno = new ArrayList<>();

	public Producto(String concepto, double precio, String notas) {
		this.concepto = concepto;
		this.precio = precio;
	}

	public Producto() {
		// TODO Auto-generated constructor stub
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Double getPrecio() {
		return precio;
	}

	public void setPrecio(Double precio) {
		this.precio = precio;
	}

	public List<ProductoAlumno> getProductosAlumno() {
		return productosAlumno;
	}

	public void setProductosAlumno(List<ProductoAlumno> productosAlumno) {
		this.productosAlumno = productosAlumno;
	}
}
