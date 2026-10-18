package com.taemoi.project.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

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
 * Un cobro de un cargo ({@code producto_alumno}), pieza 6b-1 (V31_5). Lo crea y lo anula solo {@code CobroService}: al
 * pasar un cargo a pagado se registra uno ACTIVO; al revertirlo o borrar el cargo, pasa a ANULADO. Como mucho uno ACTIVO
 * por cargo (clave única sobre la columna generada {@code producto_alumno_activo_id}, que no se mapea).
 * {@code producto_alumno.pagado} y {@code fecha_pago} siguen siendo el resumen que lee la tesorería.
 *
 * <p>Ids simples (cargo, sede, alumno, usuarios, entrega), como {@link TarifaSede}: sin relaciones que cargar. Sede,
 * alumno, concepto e importe son una instantánea del cargo al cobrar. No se expone en JSON.
 */
@Entity
@Table(name = "cobro")
public class Cobro extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** El cargo cobrado; NULL si se borró. */
	@Column(name = "producto_alumno_id")
	private Long productoAlumnoId;

	@Column(name = "sede_id")
	private Long sedeId;

	@Column(name = "alumno_id")
	private Long alumnoId;

	@Column(length = 255)
	private String concepto;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal importe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MetodoPago metodo = MetodoPago.SIN_INDICAR;

	/** Mismo tipo y valor que {@code producto_alumno.fecha_pago}. */
	@Column(name = "fecha_cobro", nullable = false)
	private Date fechaCobro;

	@Column(name = "cobrado_por_usuario_id")
	private Long cobradoPorUsuarioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrigenCobro origen;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoCobro estado = EstadoCobro.ACTIVO;

	@Column(name = "anulado_por_usuario_id")
	private Long anuladoPorUsuarioId;

	@Column(name = "anulado_en")
	private LocalDateTime anuladoEn;

	@Column(name = "motivo_anulacion", length = 500)
	private String motivoAnulacion;

	/** Entrega de caja en la que va (6b-2). Con entrega, el cobro ya no se puede anular. */
	@Column(name = "entrega_caja_id")
	private Long entregaCajaId;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	public Long getId() {
		return id;
	}

	public Long getProductoAlumnoId() {
		return productoAlumnoId;
	}

	public void setProductoAlumnoId(Long productoAlumnoId) {
		this.productoAlumnoId = productoAlumnoId;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public BigDecimal getImporte() {
		return importe;
	}

	public void setImporte(BigDecimal importe) {
		this.importe = importe;
	}

	public MetodoPago getMetodo() {
		return metodo;
	}

	public void setMetodo(MetodoPago metodo) {
		this.metodo = metodo;
	}

	public Date getFechaCobro() {
		return fechaCobro;
	}

	public void setFechaCobro(Date fechaCobro) {
		this.fechaCobro = fechaCobro;
	}

	public Long getCobradoPorUsuarioId() {
		return cobradoPorUsuarioId;
	}

	public void setCobradoPorUsuarioId(Long cobradoPorUsuarioId) {
		this.cobradoPorUsuarioId = cobradoPorUsuarioId;
	}

	public OrigenCobro getOrigen() {
		return origen;
	}

	public void setOrigen(OrigenCobro origen) {
		this.origen = origen;
	}

	public EstadoCobro getEstado() {
		return estado;
	}

	public void setEstado(EstadoCobro estado) {
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

	public Long getEntregaCajaId() {
		return entregaCajaId;
	}

	public void setEntregaCajaId(Long entregaCajaId) {
		this.entregaCajaId = entregaCajaId;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public Long getVersion() {
		return version;
	}
}
