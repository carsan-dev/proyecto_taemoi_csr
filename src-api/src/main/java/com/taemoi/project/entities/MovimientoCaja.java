package com.taemoi.project.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Movimiento de caja de una sede (pieza 6b-2, tabla {@code movimiento_caja} de V31_5): sobrantes, faltantes,
 * devoluciones, errores y otros, con importe CON SIGNO (positivo entra, negativo sale) y nota obligatoria. Las reglas
 * de signo y alumno por tipo, en {@link TipoMovimientoCaja}. Con {@code entregaCajaId}, es el SOBRANTE o FALTANTE
 * automático de esa entrega: se anula con ella, nunca suelto. Solo lo crea y lo anula {@code CajaService}.
 *
 * <p>{@code fecha} es la hora local de la escuela (Europe/Madrid) al apuntarlo.
 */
@Entity
@Table(name = "movimiento_caja")
public class MovimientoCaja extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sede_id", nullable = false)
	private Long sedeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimientoCaja tipo;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal importe;

	@Column(name = "alumno_id")
	private Long alumnoId;

	@Column(name = "entrega_caja_id")
	private Long entregaCajaId;

	@Column(nullable = false, length = 500)
	private String nota;

	@Column(name = "creado_por_usuario_id")
	private Long creadoPorUsuarioId;

	@Column(nullable = false)
	private LocalDateTime fecha;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoMovimientoCaja estado = EstadoMovimientoCaja.ACTIVO;

	@Column(name = "anulado_por_usuario_id")
	private Long anuladoPorUsuarioId;

	@Column(name = "anulado_en")
	private LocalDateTime anuladoEn;

	@Column(name = "motivo_anulacion", length = 500)
	private String motivoAnulacion;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	/**
	 * Pieza 6c-2 (V31_8): cobro devuelto (DEVOLUCION, decisión 153) o deshecho (ERROR de la 193). Lo usan la 6c-4 y la
	 * 6c-6; hasta entonces siempre NULL. La clave única de V31_8 deja una DEVOLUCION ACTIVA por cobro (D6c-11).
	 */
	@Column(name = "cobro_id")
	private Long cobroId;

	/** Pieza 6c-2 (V31_8): el cargo de ese cobro (153: «enlazado al cargo»). Hasta la 6c-6, siempre NULL. */
	@Column(name = "producto_alumno_id")
	private Long productoAlumnoId;

	/**
	 * Pieza 6c-2 (V31_8, D6c-13): método del dinero que se mueve. Todas las filas son EFECTIVO; la 6c-6 hace que el
	 * saldo de caja sume solo los EFECTIVO y que una devolución tome el método de su cobro.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MetodoPago metodo = MetodoPago.EFECTIVO;

	public Long getId() {
		return id;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public TipoMovimientoCaja getTipo() {
		return tipo;
	}

	public void setTipo(TipoMovimientoCaja tipo) {
		this.tipo = tipo;
	}

	public BigDecimal getImporte() {
		return importe;
	}

	public void setImporte(BigDecimal importe) {
		this.importe = importe;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public Long getEntregaCajaId() {
		return entregaCajaId;
	}

	public void setEntregaCajaId(Long entregaCajaId) {
		this.entregaCajaId = entregaCajaId;
	}

	public String getNota() {
		return nota;
	}

	public void setNota(String nota) {
		this.nota = nota;
	}

	public Long getCreadoPorUsuarioId() {
		return creadoPorUsuarioId;
	}

	public void setCreadoPorUsuarioId(Long creadoPorUsuarioId) {
		this.creadoPorUsuarioId = creadoPorUsuarioId;
	}

	public LocalDateTime getFecha() {
		return fecha;
	}

	public void setFecha(LocalDateTime fecha) {
		this.fecha = fecha;
	}

	public EstadoMovimientoCaja getEstado() {
		return estado;
	}

	public void setEstado(EstadoMovimientoCaja estado) {
		this.estado = estado;
	}

	public Long getAnuladoPorUsuarioId() {
		return anuladoPorUsuarioId;
	}

	public void setAnuladoPorUsuarioId(Long anuladoPorUsuarioId) {
		this.anuladoPorUsuarioId = anuladoPorUsuarioId;
	}

	public LocalDateTime getAnuladoEn() {
		return anuladoEn;
	}

	public void setAnuladoEn(LocalDateTime anuladoEn) {
		this.anuladoEn = anuladoEn;
	}

	public String getMotivoAnulacion() {
		return motivoAnulacion;
	}

	public void setMotivoAnulacion(String motivoAnulacion) {
		this.motivoAnulacion = motivoAnulacion;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public Long getVersion() {
		return version;
	}

	public Long getCobroId() {
		return cobroId;
	}

	public void setCobroId(Long cobroId) {
		this.cobroId = cobroId;
	}

	public Long getProductoAlumnoId() {
		return productoAlumnoId;
	}

	public void setProductoAlumnoId(Long productoAlumnoId) {
		this.productoAlumnoId = productoAlumnoId;
	}

	public MetodoPago getMetodo() {
		return metodo;
	}

	public void setMetodo(MetodoPago metodo) {
		this.metodo = metodo;
	}
}
