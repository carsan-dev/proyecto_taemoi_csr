package com.taemoi.project.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una clase con lista guardada (decisión 324, fase3-pasar-lista.md, tabla {@code clase_asistencia} de V31_15): un turno y
 * una fecha, como cada columna del papel. Sin fila, la clase está «sin lista» y no cuenta. Ids simples, sin relaciones,
 * como {@link Solicitud}: {@code turnoId} queda {@code null} al borrar el turno y el histórico sigue, con el horario
 * copiado al guardar. {@code modificadoEn} y {@code modificadoPorUsuarioId} quedan {@code null} hasta la primera
 * corrección. Las fechas y horas son las locales de la escuela (Europe/Madrid). Solo la cambia {@code AsistenciaService}.
 */
@Entity
@Table(name = "clase_asistencia")
public class ClaseAsistencia extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "grupo_id", nullable = false)
	private Long grupoId;

	@Column(name = "turno_id")
	private Long turnoId;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(name = "hora_inicio", nullable = false, length = 5)
	private String horaInicio;

	@Column(name = "hora_fin", nullable = false, length = 5)
	private String horaFin;

	@Column(name = "registrado_por_usuario_id")
	private Long registradoPorUsuarioId;

	@Column(name = "registrado_en", nullable = false)
	private LocalDateTime registradoEn;

	@Column(name = "modificado_por_usuario_id")
	private Long modificadoPorUsuarioId;

	@Column(name = "modificado_en")
	private LocalDateTime modificadoEn;

	protected ClaseAsistencia() {
	}

	public ClaseAsistencia(Long grupoId, Long turnoId, LocalDate fecha, String horaInicio, String horaFin,
			Long registradoPorUsuarioId, LocalDateTime registradoEn) {
		this.grupoId = grupoId;
		this.turnoId = turnoId;
		this.fecha = fecha;
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
		this.registradoPorUsuarioId = registradoPorUsuarioId;
		this.registradoEn = registradoEn;
	}

	/** Última corrección de la lista. */
	public void marcarModificada(Long usuarioId, LocalDateTime en) {
		this.modificadoPorUsuarioId = usuarioId;
		this.modificadoEn = en;
	}

	public Long getId() {
		return id;
	}

	public Long getGrupoId() {
		return grupoId;
	}

	public Long getTurnoId() {
		return turnoId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public String getHoraInicio() {
		return horaInicio;
	}

	public String getHoraFin() {
		return horaFin;
	}

	public Long getRegistradoPorUsuarioId() {
		return registradoPorUsuarioId;
	}

	public LocalDateTime getRegistradoEn() {
		return registradoEn;
	}

	public Long getModificadoPorUsuarioId() {
		return modificadoPorUsuarioId;
	}

	public LocalDateTime getModificadoEn() {
		return modificadoEn;
	}
}
