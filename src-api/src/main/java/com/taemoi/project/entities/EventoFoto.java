package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Decisión 342: una foto de la galería de un evento (V31_16). El cartel sigue en {@link Evento#getFotoEvento()}.
 *
 * <p>No se serializa nunca: lleva a una {@link Imagen} con la ruta del fichero, que la API no publica (ver
 * {@code EventoFotoDTO}). {@code Evento} no tiene la colección, para no cargarla ni serializarla con él.
 */
@Entity
@Table(name = "evento_foto")
public class EventoFoto extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "evento_id", nullable = false, updatable = false)
	private Evento evento;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "imagen_id", nullable = false, updatable = false)
	private Imagen imagen;

	/** La del mosaico: WebP no se reduce al servir. Fichero y fila propios, y las mismas reglas que {@link #imagen}. */
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "miniatura_id", nullable = false, updatable = false)
	private Imagen miniatura;

	@Column(nullable = false)
	private Integer orden;

	protected EventoFoto() {
	}

	public EventoFoto(Evento evento, Imagen imagen, Imagen miniatura, Integer orden) {
		this.evento = evento;
		this.imagen = imagen;
		this.miniatura = miniatura;
		this.orden = orden;
	}

	public Long getId() {
		return id;
	}

	public Evento getEvento() {
		return evento;
	}

	public Imagen getImagen() {
		return imagen;
	}

	public Imagen getMiniatura() {
		return miniatura;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}
}
