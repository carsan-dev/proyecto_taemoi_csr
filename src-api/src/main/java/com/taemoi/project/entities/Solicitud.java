package com.taemoi.project.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.taemoi.project.dtos.request.CajaRequests;

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
 * Una solicitud al propietario (tipos 1 a 3) o un encargo del profesor (tipo 4): pieza 6c-2, tabla {@code solicitud}
 * de V31_8 (fase3-6c-solicitudes.md §6.1). Ids simples, sin relaciones, como {@link Cobro} o {@link MovimientoCaja}.
 * Sede, alumno, concepto e importe son una instantánea del recurso al pedir. Las columnas generadas de «una pendiente
 * por recurso» ({@code cobro_pendiente_id}, {@code entrega_pendiente_id}, {@code movimiento_pendiente_id} y
 * {@code cargo_pendiente_id}) no se mapean: las mantiene MySQL.
 *
 * <p>Solo la crea y la cambia {@code SolicitudService}, con la fila bloqueada y las transiciones de
 * {@code SolicitudTransiciones}. {@code solicitado_en} y las demás fechas son la hora local de la escuela
 * (Europe/Madrid). No se expone en JSON.
 */
@Entity
@Table(name = "solicitud")
public class Solicitud extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sede_id")
	private Long sedeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TipoSolicitud tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoSolicitud estado;

	@Column(name = "solicitado_por_usuario_id")
	private Long solicitadoPorUsuarioId;

	@Column(name = "solicitado_en", nullable = false)
	private LocalDateTime solicitadoEn;

	@Column(nullable = false, length = 500)
	private String motivo;

	@Column(name = "resuelto_por_usuario_id")
	private Long resueltoPorUsuarioId;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	@Column(length = 500)
	private String respuesta;

	@Column(name = "cobro_id")
	private Long cobroId;

	@Column(name = "entrega_caja_id")
	private Long entregaCajaId;

	@Column(name = "movimiento_caja_id")
	private Long movimientoCajaId;

	@Column(name = "producto_alumno_id")
	private Long productoAlumnoId;

	@Column(name = "producto_id")
	private Long productoId;

	@Column(name = "alumno_id")
	private Long alumnoId;

	@Column(length = 255)
	private String concepto;

	@Column(precision = 10, scale = 2)
	private BigDecimal importe;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_deshacer", length = 20)
	private CajaRequests.MotivoDeshacer motivoDeshacer;

	@Column(name = "encargado_en")
	private LocalDateTime encargadoEn;

	@Column(name = "encargado_por_usuario_id")
	private Long encargadoPorUsuarioId;

	@Column(name = "recibido_en")
	private LocalDateTime recibidoEn;

	@Column(name = "recibido_por_usuario_id")
	private Long recibidoPorUsuarioId;

	@Column(name = "entregado_en")
	private LocalDateTime entregadoEn;

	@Column(name = "devolucion_pendiente", nullable = false)
	private Boolean devolucionPendiente = Boolean.FALSE;

	@Column(name = "movimiento_devolucion_id")
	private Long movimientoDevolucionId;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	public Long getId() {
		return id;
	}

	public Long getSedeId() {
		return sedeId;
	}

	public void setSedeId(Long sedeId) {
		this.sedeId = sedeId;
	}

	public TipoSolicitud getTipo() {
		return tipo;
	}

	public void setTipo(TipoSolicitud tipo) {
		this.tipo = tipo;
	}

	public EstadoSolicitud getEstado() {
		return estado;
	}

	public void setEstado(EstadoSolicitud estado) {
		this.estado = estado;
	}

	public Long getSolicitadoPorUsuarioId() {
		return solicitadoPorUsuarioId;
	}

	public void setSolicitadoPorUsuarioId(Long solicitadoPorUsuarioId) {
		this.solicitadoPorUsuarioId = solicitadoPorUsuarioId;
	}

	public LocalDateTime getSolicitadoEn() {
		return solicitadoEn;
	}

	public void setSolicitadoEn(LocalDateTime solicitadoEn) {
		this.solicitadoEn = solicitadoEn;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public Long getResueltoPorUsuarioId() {
		return resueltoPorUsuarioId;
	}

	public void setResueltoPorUsuarioId(Long resueltoPorUsuarioId) {
		this.resueltoPorUsuarioId = resueltoPorUsuarioId;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}

	public void setResueltoEn(LocalDateTime resueltoEn) {
		this.resueltoEn = resueltoEn;
	}

	public String getRespuesta() {
		return respuesta;
	}

	public void setRespuesta(String respuesta) {
		this.respuesta = respuesta;
	}

	public Long getCobroId() {
		return cobroId;
	}

	public void setCobroId(Long cobroId) {
		this.cobroId = cobroId;
	}

	public Long getEntregaCajaId() {
		return entregaCajaId;
	}

	public void setEntregaCajaId(Long entregaCajaId) {
		this.entregaCajaId = entregaCajaId;
	}

	public Long getMovimientoCajaId() {
		return movimientoCajaId;
	}

	public void setMovimientoCajaId(Long movimientoCajaId) {
		this.movimientoCajaId = movimientoCajaId;
	}

	public Long getProductoAlumnoId() {
		return productoAlumnoId;
	}

	public void setProductoAlumnoId(Long productoAlumnoId) {
		this.productoAlumnoId = productoAlumnoId;
	}

	public Long getProductoId() {
		return productoId;
	}

	public void setProductoId(Long productoId) {
		this.productoId = productoId;
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

	public CajaRequests.MotivoDeshacer getMotivoDeshacer() {
		return motivoDeshacer;
	}

	public void setMotivoDeshacer(CajaRequests.MotivoDeshacer motivoDeshacer) {
		this.motivoDeshacer = motivoDeshacer;
	}

	public LocalDateTime getEncargadoEn() {
		return encargadoEn;
	}

	public void setEncargadoEn(LocalDateTime encargadoEn) {
		this.encargadoEn = encargadoEn;
	}

	public Long getEncargadoPorUsuarioId() {
		return encargadoPorUsuarioId;
	}

	public void setEncargadoPorUsuarioId(Long encargadoPorUsuarioId) {
		this.encargadoPorUsuarioId = encargadoPorUsuarioId;
	}

	public LocalDateTime getRecibidoEn() {
		return recibidoEn;
	}

	public void setRecibidoEn(LocalDateTime recibidoEn) {
		this.recibidoEn = recibidoEn;
	}

	public Long getRecibidoPorUsuarioId() {
		return recibidoPorUsuarioId;
	}

	public void setRecibidoPorUsuarioId(Long recibidoPorUsuarioId) {
		this.recibidoPorUsuarioId = recibidoPorUsuarioId;
	}

	public LocalDateTime getEntregadoEn() {
		return entregadoEn;
	}

	public void setEntregadoEn(LocalDateTime entregadoEn) {
		this.entregadoEn = entregadoEn;
	}

	public boolean isDevolucionPendiente() {
		return Boolean.TRUE.equals(devolucionPendiente);
	}

	public void setDevolucionPendiente(boolean devolucionPendiente) {
		this.devolucionPendiente = devolucionPendiente;
	}

	public Long getMovimientoDevolucionId() {
		return movimientoDevolucionId;
	}

	public void setMovimientoDevolucionId(Long movimientoDevolucionId) {
		this.movimientoDevolucionId = movimientoDevolucionId;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}
}
