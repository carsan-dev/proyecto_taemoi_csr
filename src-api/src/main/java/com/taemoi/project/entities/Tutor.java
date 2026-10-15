package com.taemoi.project.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Padre, madre o tutor de un alumno. {@code responsableLegal} marca a quien firma por un menor; el
 * primero de ellos (por {@code orden}, {@code id}) es el que la API expone como responsable legal del
 * alumno (ver {@code ResponsableLegal}).
 *
 * <p>Un responsable legal del que solo se conoce el NIF es un tutor sin nombre ({@link #tieneNombre()}
 * falso; ver {@code TutorService}). Quien lea {@code nombre} debe tratar igual {@code null} y vacío.
 */
@Entity
@Table(name = "tutor")
public class Tutor extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	// NULL para el responsable legal del que solo se conoce el NIF (V29: CHECK nombre o NIF).
	@Column(length = 180)
	private String nombre;

	@Column(length = 160)
	private String apellidos;

	@Column(length = 16)
	private String nif;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "ENUM('PADRE','MADRE','TUTOR_LEGAL','OTRO')")
	private ParentescoTutor parentesco = ParentescoTutor.OTRO;

	@Column(length = 20)
	private String telefono;

	@Column(length = 180)
	private String email;

	@Column(name = "responsable_legal", nullable = false)
	private Boolean responsableLegal = Boolean.FALSE;

	@Column(nullable = false)
	private Integer orden = 0;

	protected Tutor() {
	}

	public Tutor(Alumno alumno) {
		this.alumno = alumno;
	}

	/** Nombre y apellidos tal y como se mostraban en el campo único heredado. */
	public String getNombreCompleto() {
		String n = nombre == null ? "" : nombre.trim();
		String a = apellidos == null ? "" : apellidos.trim();
		String completo = (n + " " + a).trim();
		return completo.isEmpty() ? null : completo;
	}

	/** Falso para el responsable legal del que solo se conoce el NIF (nombre nulo o vacío). */
	public boolean tieneNombre() {
		return getNombreCompleto() != null;
	}

	public Long getId() {
		return id;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getNif() {
		return nif;
	}

	public void setNif(String nif) {
		this.nif = nif;
	}

	public ParentescoTutor getParentesco() {
		return parentesco;
	}

	public void setParentesco(ParentescoTutor parentesco) {
		this.parentesco = parentesco;
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

	public boolean isResponsableLegal() {
		return Boolean.TRUE.equals(responsableLegal);
	}

	public void setResponsableLegal(boolean responsableLegal) {
		this.responsableLegal = responsableLegal;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}
}
