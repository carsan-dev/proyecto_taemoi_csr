package com.taemoi.project.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Imagen {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@NotBlank(message = "El nombre no puede estar en blanco")
	@Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
    private String nombre;
	
    @NotBlank(message = "El tipo no puede estar en blanco")
    @Size(max = 50, message = "El tipo no puede tener más de 50 caracteres")
    private String tipo;
    
    @NotNull(message = "Los datos no pueden ser nulos")
    @Column(length = 50000000)
    private byte[] datos;

    public Imagen() {
    }

    public Imagen(String nombre, String tipo, byte[] datos) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.datos = datos;
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public byte[] getDatos() {
		return datos;
	}

	public void setDatos(byte[] datos) {
		this.datos = datos;
	}
}