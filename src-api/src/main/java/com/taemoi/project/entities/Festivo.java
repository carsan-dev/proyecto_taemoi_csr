package com.taemoi.project.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Días sin clase en una o varias sedes (decisiones 455-463, tabla {@code festivo} de V32_2). Sus sedes, en
 * {@link FestivoSede}. Ids simples, sin relaciones, como {@link ClaseAsistencia}. Las reglas de forma (DIA, RANGO, ANUAL)
 * las comprueba {@code FestivoService}, el único que lo cambia. Fechas locales de la escuela (Europe/Madrid).
 */
@Entity
@Table(name = "festivo")
public class Festivo extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TipoFestivo tipo;

	@Column(name = "fecha_desde")
	private LocalDate fechaDesde;

	@Column(name = "fecha_hasta")
	private LocalDate fechaHasta;

	@JdbcTypeCode(SqlTypes.TINYINT)
	private Integer mes;

	@JdbcTypeCode(SqlTypes.TINYINT)
	private Integer dia;

	@Column(length = 60)
	private String nombre;

	@Column(name = "creado_por_usuario_id")
	private Long creadoPorUsuarioId;

	@Column(name = "creado_en", nullable = false)
	private LocalDateTime creadoEn;

	@Column(name = "modificado_por_usuario_id")
	private Long modificadoPorUsuarioId;

	@Column(name = "modificado_en")
	private LocalDateTime modificadoEn;

	protected Festivo() {
	}

	public Festivo(Long creadoPorUsuarioId, LocalDateTime creadoEn) {
		this.creadoPorUsuarioId = creadoPorUsuarioId;
		this.creadoEn = creadoEn;
	}

	/** Forma ya validada: DIA y RANGO con fechas y sin mes ni día; ANUAL con mes y día y sin fechas. */
	public void definir(TipoFestivo tipo, LocalDate fechaDesde, LocalDate fechaHasta, Integer mes, Integer dia,
			String nombre) {
		this.tipo = tipo;
		this.fechaDesde = fechaDesde;
		this.fechaHasta = fechaHasta;
		this.mes = mes;
		this.dia = dia;
		this.nombre = nombre;
	}

	public void marcarModificado(Long usuarioId, LocalDateTime en) {
		this.modificadoPorUsuarioId = usuarioId;
		this.modificadoEn = en;
	}

	public Long getId() {
		return id;
	}

	public TipoFestivo getTipo() {
		return tipo;
	}

	public LocalDate getFechaDesde() {
		return fechaDesde;
	}

	public LocalDate getFechaHasta() {
		return fechaHasta;
	}

	public Integer getMes() {
		return mes;
	}

	public Integer getDia() {
		return dia;
	}

	public String getNombre() {
		return nombre;
	}

	public Long getCreadoPorUsuarioId() {
		return creadoPorUsuarioId;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public Long getModificadoPorUsuarioId() {
		return modificadoPorUsuarioId;
	}

	public LocalDateTime getModificadoEn() {
		return modificadoEn;
	}
}
