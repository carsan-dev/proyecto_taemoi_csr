package com.taemoi.project.entidades;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "El título no puede estar en blanco")
    @Size(max = 100, message = "El título no puede tener más de 100 caracteres")
    private String titulo;
    
    @NotBlank(message = "La descripción no puede estar en blanco")
    @Size(max = 500, message = "La descripción no puede tener más de 500 caracteres")
    private String descripcion;
    
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "foto_evento_id")
    private Imagen fotoEvento;
    
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getTitulo() {
		return titulo;
	}
	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}
	public String getDescripcion() {
		return descripcion;
	}
	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}
	public Imagen getFotoEvento() {
		return fotoEvento;
	}
	public void setFotoEvento(Imagen fotoEvento) {
		this.fotoEvento = fotoEvento;
	}
}