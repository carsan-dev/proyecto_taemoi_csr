package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Organización (escuela). Tabla global: no lleva organizacion_id ni se filtra.
 * Taemoi es la organización 1.
 */
@Entity
@Table(name = "organizacion")
public class Organizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 60)
	private String slug;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(name = "nombre_legal", length = 200)
	private String nombreLegal;

	@Column(length = 16)
	private String cif;

	private String direccion;

	@Column(length = 100)
	private String localidad;

	@Column(length = 100)
	private String provincia;

	@Column(name = "codigo_postal", length = 10)
	private String codigoPostal;

	@Column(name = "email_contacto", length = 180)
	private String emailContacto;

	@Column(length = 20)
	private String telefono;

	@Column(name = "web_url")
	private String webUrl;

	// Solo el id: Imagen pertenece a una organización y no debe cargarse desde aquí.
	@Column(name = "logo_imagen_id")
	private Long logoImagenId;

	@Column(name = "color_primario", length = 7, columnDefinition = "CHAR(7)")
	private String colorPrimario;

	@Column(name = "email_remitente_nombre", length = 100)
	private String emailRemitenteNombre;

	@Column(name = "firmante_nombre", length = 180)
	private String firmanteNombre;

	@Column(name = "firmante_cargo", length = 100)
	private String firmanteCargo;

	@Column(name = "firmante_nif", length = 16)
	private String firmanteNif;

	@Column(name = "zona_horaria", nullable = false, length = 40)
	private String zonaHoraria = "Europe/Madrid";

	@Column(nullable = false)
	private Boolean activa = Boolean.TRUE;

	/**
	 * V28: comportamiento heredado de Taemoi para los productos con códigos de V24 (concepto literal de los
	 * cargos, competidor a 20 €, tesorería por concepto, variantes sin clasificar). TRUE solo para la
	 * organización 1; el resto usa siempre el comportamiento del catálogo ({@code CatalogoLegadoTaemoi}).
	 */
	@Column(name = "comportamiento_legado_taemoi", nullable = false)
	private Boolean comportamientoLegadoTaemoi = Boolean.FALSE;

	@Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadaEn;

	public Long getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getNombreLegal() {
		return nombreLegal;
	}

	public void setNombreLegal(String nombreLegal) {
		this.nombreLegal = nombreLegal;
	}

	public String getCif() {
		return cif;
	}

	public void setCif(String cif) {
		this.cif = cif;
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

	public String getProvincia() {
		return provincia;
	}

	public void setProvincia(String provincia) {
		this.provincia = provincia;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}

	public void setCodigoPostal(String codigoPostal) {
		this.codigoPostal = codigoPostal;
	}

	public String getEmailContacto() {
		return emailContacto;
	}

	public void setEmailContacto(String emailContacto) {
		this.emailContacto = emailContacto;
	}

	public String getTelefono() {
		return telefono;
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	public String getWebUrl() {
		return webUrl;
	}

	public void setWebUrl(String webUrl) {
		this.webUrl = webUrl;
	}

	public Long getLogoImagenId() {
		return logoImagenId;
	}

	public void setLogoImagenId(Long logoImagenId) {
		this.logoImagenId = logoImagenId;
	}

	public String getColorPrimario() {
		return colorPrimario;
	}

	public void setColorPrimario(String colorPrimario) {
		this.colorPrimario = colorPrimario;
	}

	public String getEmailRemitenteNombre() {
		return emailRemitenteNombre;
	}

	public void setEmailRemitenteNombre(String emailRemitenteNombre) {
		this.emailRemitenteNombre = emailRemitenteNombre;
	}

	public String getFirmanteNombre() {
		return firmanteNombre;
	}

	public void setFirmanteNombre(String firmanteNombre) {
		this.firmanteNombre = firmanteNombre;
	}

	public String getFirmanteCargo() {
		return firmanteCargo;
	}

	public void setFirmanteCargo(String firmanteCargo) {
		this.firmanteCargo = firmanteCargo;
	}

	public String getFirmanteNif() {
		return firmanteNif;
	}

	public void setFirmanteNif(String firmanteNif) {
		this.firmanteNif = firmanteNif;
	}

	public String getZonaHoraria() {
		return zonaHoraria;
	}

	public void setZonaHoraria(String zonaHoraria) {
		this.zonaHoraria = zonaHoraria;
	}

	public Boolean getComportamientoLegadoTaemoi() {
		return comportamientoLegadoTaemoi;
	}

	public void setComportamientoLegadoTaemoi(Boolean comportamientoLegadoTaemoi) {
		this.comportamientoLegadoTaemoi = comportamientoLegadoTaemoi;
	}

	public Boolean getActiva() {
		return activa;
	}

	public void setActiva(Boolean activa) {
		this.activa = activa;
	}

	public LocalDateTime getCreadaEn() {
		return creadaEn;
	}
}
