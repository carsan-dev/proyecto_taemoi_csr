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

	/** Web por plantilla (W2, V32_6): dirección de su página, {@code /sedes/<slug>}; única en la escuela. */
	@Column(length = 60)
	private String slug;

	/** Web por plantilla (W2): «Pabellón cubierto municipal». */
	@Column(length = 120)
	private String lugar;

	/** Web por plantilla (W2): «Entrada por la calle lateral…». */
	@Column(name = "indicaciones_acceso", length = 300)
	private String indicacionesAcceso;

	/** Web por plantilla (W2): fotos de la sede, imágenes de la misma escuela. Solo lectura en la API de sedes. */
	@Column(name = "foto_imagen_id")
	private Long fotoImagenId;

	@Column(name = "foto2_imagen_id")
	private Long foto2ImagenId;

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

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getLugar() {
		return lugar;
	}

	public void setLugar(String lugar) {
		this.lugar = lugar;
	}

	public String getIndicacionesAcceso() {
		return indicacionesAcceso;
	}

	public void setIndicacionesAcceso(String indicacionesAcceso) {
		this.indicacionesAcceso = indicacionesAcceso;
	}

	public Long getFotoImagenId() {
		return fotoImagenId;
	}

	public void setFotoImagenId(Long fotoImagenId) {
		this.fotoImagenId = fotoImagenId;
	}

	public Long getFoto2ImagenId() {
		return foto2ImagenId;
	}

	public void setFoto2ImagenId(Long foto2ImagenId) {
		this.foto2ImagenId = foto2ImagenId;
	}
}
