package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sede (local) de una organización (V31). Taemoi tiene una sola, «Umbrete»; las demás escuelas empiezan con
 * «Sede principal». Pertenece a la escuela activa ({@code @TenantId}).
 */
@Entity
@Table(name = "sede")
public class Sede extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	private String direccion;

	@Column(length = 100)
	private String localidad;

	@Column(name = "codigo_postal", length = 10)
	private String codigoPostal;

	@Column(length = 20)
	private String telefono;

	@Column(length = 180)
	private String email;

	/** Nombre del club en la federación, p. ej. «TKD CASARICHE». */
	@Column(name = "codigo_club_federacion", length = 40)
	private String codigoClubFederacion;

	/** Aforo por turno; {@code null} = {@code limite_turno} de la configuración de la escuela. */
	@Column(name = "aforo_turno")
	private Integer aforoTurno;

	@Column(nullable = false)
	private Boolean activa = Boolean.TRUE;

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public String getLocalidad() {
		return localidad;
	}

	public void setLocalidad(String localidad) {
		this.localidad = localidad;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}

	public void setCodigoPostal(String codigoPostal) {
		this.codigoPostal = codigoPostal;
	}

	public String getTelefono() {
		return telefono;
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getCodigoClubFederacion() {
		return codigoClubFederacion;
	}

	public void setCodigoClubFederacion(String codigoClubFederacion) {
		this.codigoClubFederacion = codigoClubFederacion;
	}

	public Integer getAforoTurno() {
		return aforoTurno;
	}

	public void setAforoTurno(Integer aforoTurno) {
		this.aforoTurno = aforoTurno;
	}

	public boolean isActiva() {
		return Boolean.TRUE.equals(activa);
	}

	public void setActiva(boolean activa) {
		this.activa = activa;
	}
}
