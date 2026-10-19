package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;

/**
 * Producto cobrable.
 *
 * <p>Fase de ampliación (V24): conviven {@code concepto} (lo que busca hoy el código) y las
 * columnas del catálogo ({@code codigo}, {@code tipo}, {@code disciplina}, {@code grado}), que solo leen los
 * servicios nuevos. Las columnas nuevas no se exponen en JSON para no cambiar las respuestas de la
 * API (y no se pueden fijar desde el cuerpo de una petición). Son {@code updatable = false} hasta
 * el cambio de llamadores: {@code POST /api/productos} guarda la entidad recibida, y con un id
 * existente el merge pisaría la clasificación con los valores por defecto.
 */
@Entity
public class Producto extends EntidadOrganizacion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String concepto;

	private Double precio;

	/** Identificador estable dentro de la organización (UNIQUE con organizacion_id). NULL = sin clasificar. */
	@JsonIgnore
	@Column(length = 60, updatable = false)
	private String codigo;

	@JsonIgnore
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false,
			columnDefinition = "ENUM('MENSUALIDAD','RESERVA_PLAZA','LICENCIA','COMPETIDOR','EXAMEN','RECOMPENSA','OTRO')")
	private TipoProducto tipo = TipoProducto.OTRO;

	/** Sin disciplina = general o compartido entre disciplinas (exámenes y recompensas). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "disciplina_id", updatable = false)
	private Disciplina disciplina;

	/**
	 * Grado que concede un producto de examen o recompensa (V24). Varios productos pueden conceder
	 * el mismo grado (p. ej. rojo normal y rojo bordado). Apunta al grado del sistema de taekwondo;
	 * otra disciplina que comparta el producto usa el mismo codigo de grado en su sistema.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "grado_id", updatable = false)
	private Grado grado;

	/**
	 * Pieza 6b-3b-1 (decisión 182, V31_7): la gestión marca en la ficha que el profesor lo puede vender («Vender» de
	 * «Mi caja»), en todas las sedes. Además de la marca rige la regla fija de la 6b-2 (ver
	 * {@code ProductoAlumnoServiceImpl#esVendiblePorProfesor}). Va en el JSON de {@code /api/productos}
	 * ({@code vendibleProfesor}), no en el del producto anidado en un cargo ({@code ProductoAlumno#producto}).
	 *
	 * <p>{@code null} SOLO en un producto recibido en una petición que no envía el campo: {@code PUT} y {@code POST}
	 * con un id existente conservan entonces el valor guardado ({@code ProductoServiceImpl}); un alta sin el campo
	 * queda {@code false} ({@link #valoresPorDefecto}). En la base nunca es nulo (NOT NULL DEFAULT FALSE).
	 */
	@Column(name = "vendible_profesor", nullable = false)
	private Boolean vendibleProfesor;

	@OneToMany(mappedBy = "producto")
	@JsonIgnore
	private List<ProductoAlumno> productosAlumno = new ArrayList<>();

	public Producto(String concepto, double precio, String notas) {
		this.concepto = concepto;
		this.precio = precio;
	}

	public Producto() {
		// TODO Auto-generated constructor stub
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Double getPrecio() {
		return precio;
	}

	public void setPrecio(Double precio) {
		this.precio = precio;
	}

	@JsonIgnore
	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	@JsonIgnore
	public TipoProducto getTipo() {
		return tipo;
	}

	public void setTipo(TipoProducto tipo) {
		this.tipo = tipo;
	}

	@JsonIgnore
	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
	}

	@JsonIgnore
	public Grado getGrado() {
		return grado;
	}

	public void setGrado(Grado grado) {
		this.grado = grado;
	}

	public Boolean getVendibleProfesor() {
		return vendibleProfesor;
	}

	public void setVendibleProfesor(Boolean vendibleProfesor) {
		this.vendibleProfesor = vendibleProfesor;
	}

	/** Un alta sin la marca (cualquier vía: API, catálogo, servicios) queda sin marcar. */
	@PrePersist
	void valoresPorDefecto() {
		if (vendibleProfesor == null) {
			vendibleProfesor = Boolean.FALSE;
		}
	}

	public List<ProductoAlumno> getProductosAlumno() {
		return productosAlumno;
	}

	public void setProductosAlumno(List<ProductoAlumno> productosAlumno) {
		this.productosAlumno = productosAlumno;
	}
}
