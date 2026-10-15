package com.taemoi.project.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

/**
 * Categoría de competición de una disciplina (catálogo, V28). Sustituye a {@code TipoCategoria}: la
 * categoría que se asigna por edad a un competidor es la de su disciplina cuyo rango
 * [{@code edadMin}, {@code edadMax}] (extremos nulos = abiertos) contiene su edad, por {@code orden}.
 *
 * <p>El JSON sigue siendo {@code {id, nombre, tipoCategoria}}: {@code tipoCategoria} es {@code codigo}
 * (el antiguo {@code tipo_categoria}, nulo si no lo tenía).
 */
@Entity
public class Categoria extends EntidadOrganizacion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@Column(length = 40)
	private String codigo;

	@NotBlank(message = "El nombre de la categoría no puede estar en blanco")
	private String nombre;

	/** Disciplina de la categoría; sin disciplina no se asigna por edad. */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "disciplina_id")
	private Disciplina disciplina;

	/** Edad mínima (años cumplidos, incluida); nula = sin mínimo. */
	@JsonIgnore
	@Column(name = "edad_min")
	private Integer edadMin;

	/** Edad máxima (años cumplidos, incluida); nula = sin máximo. */
	@JsonIgnore
	@Column(name = "edad_max")
	private Integer edadMax;

	@JsonIgnore
	@Column(nullable = false)
	private Integer orden = 0;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	/** El valor que la API devolvía (y devuelve) en el campo {@code tipoCategoria}. */
	@JsonProperty("tipoCategoria")
	public String getTipoCategoria() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
	}

	public Integer getEdadMin() {
		return edadMin;
	}

	public void setEdadMin(Integer edadMin) {
		this.edadMin = edadMin;
	}

	public Integer getEdadMax() {
		return edadMax;
	}

	public void setEdadMax(Integer edadMax) {
		this.edadMax = edadMax;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}

	/** Si la edad (años cumplidos) está en el rango de la categoría. */
	public boolean incluyeEdad(int edad) {
		return (edadMin == null || edad >= edadMin) && (edadMax == null || edad <= edadMax);
	}
}
