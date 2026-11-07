package com.taemoi.project.entities;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Evento extends EntidadOrganizacion {
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

	@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonManagedReference("evento-documentos")
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private List<Documento> documentos = new ArrayList<>();

	@Column(name = "fecha_evento")
	private LocalDate fechaEvento;

	private Boolean visible = true;
	private Integer orden;

	/**
	 * Web por plantilla (W2, decisión 466): hora, lugar y sede del evento, opcionales. Sin valor no salen en el JSON
	 * ({@code NON_NULL}): el de los eventos que no los tienen, como los de Moiskimdo, sigue siendo el de siempre.
	 */
	@Column(name = "hora")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
	private LocalTime hora;

	@Column(name = "lugar", length = 160)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String lugar;

	/** Sede de la misma escuela (lo comprueba {@code EventoServiceImpl}). */
	@Column(name = "sede_id")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long sedeId;

	/** Decisión 342: lo rellenan las listas de eventos con una sola consulta; en el resto de respuestas no aparece. */
	@Transient
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private Integer numeroFotos;

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

	public LocalDate getFechaEvento() {
		return fechaEvento;
	}

	public void setFechaEvento(LocalDate fechaEvento) {
		this.fechaEvento = fechaEvento;
	}

	public Imagen getFotoEvento() {
		return fotoEvento;
	}

	public void setFotoEvento(Imagen fotoEvento) {
		this.fotoEvento = fotoEvento;
	}

	public Boolean getVisible() {
		return visible;
	}

	public void setVisible(Boolean visible) {
		this.visible = visible;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}

	public List<Documento> getDocumentos() {
		return documentos;
	}

	public void setDocumentos(List<Documento> documentos) {
		this.documentos = documentos;
	}

	public LocalTime getHora() {
		return hora;
	}

	public void setHora(LocalTime hora) {
		this.hora = hora;
	}

	public String getLugar() {
		return lugar;
	}

	public void setLugar(String lugar) {
		this.lugar = lugar;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public Integer getNumeroFotos() {
		return numeroFotos;
	}

	public void setNumeroFotos(Integer numeroFotos) {
		this.numeroFotos = numeroFotos;
	}
}
