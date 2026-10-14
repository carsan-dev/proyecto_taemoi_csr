package com.taemoi.project.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
import jakarta.validation.constraints.NotNull;

/**
 * Grado de un {@link SistemaGrados}.
 *
 * <p>Desde V28 {@code codigo} es la única identificación del grado (la columna heredada
 * {@code tipo_grado} se borró). El JSON sigue siendo {@code {id, tipoGrado}}: {@code tipoGrado} es el
 * {@code codigo} ({@link #getTipoGrado()}). Las demás columnas del catálogo no se exponen.
 */
@Entity
public class Grado extends EntidadOrganizacion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sistema_grados_id")
	private SistemaGrados sistemaGrados;

	@JsonIgnore
	@NotNull(message = "El tipo de grado no puede ser nulo")
	@Column(length = 40, nullable = false)
	private String codigo;

	/** Nombre visible en su disciplina (en kickboxing, ROJO = «MARRÓN»). */
	@JsonIgnore
	@Column(length = 100)
	private String nombre;

	@JsonIgnore
	private Integer orden;

	@JsonIgnore
	@Column(name = "color_1", length = 7)
	private String color1;

	@JsonIgnore
	@Column(name = "color_2", length = 7)
	private String color2;

	@JsonIgnore
	@Enumerated(EnumType.STRING)
	@Column(name = "aplica_a", nullable = false, columnDefinition = "ENUM('TODOS','MENORES','ADULTOS','NINGUNO')")
	private AplicaGrado aplicaA = AplicaGrado.TODOS;

	/** Sin uso todavía (p. ej. PUM -> DAN al pasar a adulto). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "equivalente_adulto_id")
	private Grado equivalenteAdulto;

	@JsonIgnore
	@Column(name = "requiere_examen", nullable = false)
	private Boolean requiereExamen = Boolean.TRUE;

	/** Meses mínimos en este grado antes de examinarse, para menores. NULL = no apto. */
	@JsonIgnore
	@Column(name = "meses_minimos_menor")
	private Integer mesesMinimosMenor;

	/** Meses mínimos en este grado antes de examinarse, para adultos. NULL = no apto. */
	@JsonIgnore
	@Column(name = "meses_minimos_adulto")
	private Integer mesesMinimosAdulto;

	@JsonIgnore
	@Column(name = "bloque_temario", length = 60)
	private String bloqueTemario;

	/** Producto de derecho a examen para llegar a este grado (V24). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_examen_id")
	private Producto productoExamen;

	/** Producto de cambio de grado por recompensa para llegar a este grado (V24). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_recompensa_id")
	private Producto productoRecompensa;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	/** Código del grado: el valor que la API devolvía (y devuelve) en el campo {@code tipoGrado}. */
	@JsonProperty("tipoGrado")
	public String getTipoGrado() {
		return codigo;
	}

	public SistemaGrados getSistemaGrados() {
		return sistemaGrados;
	}

	public void setSistemaGrados(SistemaGrados sistemaGrados) {
		this.sistemaGrados = sistemaGrados;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}

	public String getColor1() {
		return color1;
	}

	public void setColor1(String color1) {
		this.color1 = color1;
	}

	public String getColor2() {
		return color2;
	}

	public void setColor2(String color2) {
		this.color2 = color2;
	}

	public AplicaGrado getAplicaA() {
		return aplicaA;
	}

	public void setAplicaA(AplicaGrado aplicaA) {
		this.aplicaA = aplicaA;
	}

	public Grado getEquivalenteAdulto() {
		return equivalenteAdulto;
	}

	public void setEquivalenteAdulto(Grado equivalenteAdulto) {
		this.equivalenteAdulto = equivalenteAdulto;
	}

	public Boolean getRequiereExamen() {
		return requiereExamen;
	}

	public void setRequiereExamen(Boolean requiereExamen) {
		this.requiereExamen = requiereExamen;
	}

	public Integer getMesesMinimosMenor() {
		return mesesMinimosMenor;
	}

	public void setMesesMinimosMenor(Integer mesesMinimosMenor) {
		this.mesesMinimosMenor = mesesMinimosMenor;
	}

	public Integer getMesesMinimosAdulto() {
		return mesesMinimosAdulto;
	}

	public void setMesesMinimosAdulto(Integer mesesMinimosAdulto) {
		this.mesesMinimosAdulto = mesesMinimosAdulto;
	}

	public String getBloqueTemario() {
		return bloqueTemario;
	}

	public void setBloqueTemario(String bloqueTemario) {
		this.bloqueTemario = bloqueTemario;
	}

	public Producto getProductoExamen() {
		return productoExamen;
	}

	public void setProductoExamen(Producto productoExamen) {
		this.productoExamen = productoExamen;
	}

	public Producto getProductoRecompensa() {
		return productoRecompensa;
	}

	public void setProductoRecompensa(Producto productoRecompensa) {
		this.productoRecompensa = productoRecompensa;
	}
}
