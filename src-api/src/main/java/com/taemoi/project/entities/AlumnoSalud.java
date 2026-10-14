package com.taemoi.project.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datos de salud de un alumno (decisión 7 del modelo multi-escuela): tabla aparte, acceso
 * restringido, sin caché, lectura auditada y textos cifrados en reposo (AES-GCM).
 *
 * <p>Los campos {@code *Cifrado} guardan el texto YA cifrado ({@code v1:...}), ligado a esta fila y
 * columna ({@code CifradorDatos.contexto("alumno_salud", columna, alumnoId)}). Solo
 * {@code AlumnoSaludService} cifra y descifra; esta entidad nunca contiene texto en claro.
 */
@Entity
@Table(name = "alumno_salud")
public class AlumnoSalud extends EntidadOrganizacion {

	@Id
	@Column(name = "alumno_id")
	private Long alumnoId;

	@Column(name = "lesion_enfermedad")
	private Boolean lesionEnfermedad;

	@Column(name = "descripcion", columnDefinition = "TEXT")
	private String descripcionCifrada;

	@Column(name = "impide_actividad")
	private Boolean impideActividad;

	@Column(name = "impide_detalle", columnDefinition = "TEXT")
	private String impideDetalleCifrado;

	@Column(name = "alergias", columnDefinition = "TEXT")
	private String alergiasCifradas;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "ENUM('FICHA_PAPEL','PREINSCRIPCION','APP')")
	private OrigenDato origen;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	protected AlumnoSalud() {
	}

	public AlumnoSalud(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public Boolean getLesionEnfermedad() {
		return lesionEnfermedad;
	}

	public void setLesionEnfermedad(Boolean lesionEnfermedad) {
		this.lesionEnfermedad = lesionEnfermedad;
	}

	public String getDescripcionCifrada() {
		return descripcionCifrada;
	}

	public void setDescripcionCifrada(String descripcionCifrada) {
		this.descripcionCifrada = descripcionCifrada;
	}

	public Boolean getImpideActividad() {
		return impideActividad;
	}

	public void setImpideActividad(Boolean impideActividad) {
		this.impideActividad = impideActividad;
	}

	public String getImpideDetalleCifrado() {
		return impideDetalleCifrado;
	}

	public void setImpideDetalleCifrado(String impideDetalleCifrado) {
		this.impideDetalleCifrado = impideDetalleCifrado;
	}

	public String getAlergiasCifradas() {
		return alergiasCifradas;
	}

	public void setAlergiasCifradas(String alergiasCifradas) {
		this.alergiasCifradas = alergiasCifradas;
	}

	public OrigenDato getOrigen() {
		return origen;
	}

	public void setOrigen(OrigenDato origen) {
		this.origen = origen;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public void setActualizadoEn(Instant actualizadoEn) {
		this.actualizadoEn = actualizadoEn;
	}
}
