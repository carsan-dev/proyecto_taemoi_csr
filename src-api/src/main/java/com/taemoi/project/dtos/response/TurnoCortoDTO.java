package com.taemoi.project.dtos.response;

import java.util.List;
import java.util.stream.Collectors;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Turno;

public class TurnoCortoDTO {
	private Long id;
	private String diaSemana;
	private String horaInicio;
	private String horaFin;
	private Long grupoId;
	private String tipoGrupo;
	private List<AlumnoDTO> alumnos;

	public TurnoCortoDTO() {
	}

	public String getDiaSemana() {
		return diaSemana;
	}

	public void setDiaSemana(String diaSemana) {
		this.diaSemana = diaSemana;
	}

	public String getHoraInicio() {
		return horaInicio;
	}

	public void setHoraInicio(String horaInicio) {
		this.horaInicio = horaInicio;
	}

	public String getHoraFin() {
		return horaFin;
	}

	public void setHoraFin(String horaFin) {
		this.horaFin = horaFin;
	}

	public Long getGrupoId() {
		return grupoId;
	}

	public void setGrupoId(Long grupoId) {
		this.grupoId = grupoId;
	}

	public String getTipoGrupo() {
		return tipoGrupo;
	}

	public void setTipoGrupo(String tipoGrupo) {
		this.tipoGrupo = tipoGrupo;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public List<AlumnoDTO> getAlumnos() {
		return alumnos;
	}

	public void setAlumnos(List<AlumnoDTO> alumnos) {
		this.alumnos = alumnos;
	}

	public static TurnoCortoDTO deTurno(Turno turno) {
		if (turno == null) {
			return null;
		}

		TurnoCortoDTO turnoDTO = new TurnoCortoDTO();
		turnoDTO.setId(turno.getId());
		turnoDTO.setDiaSemana(turno.getDiaSemana());
		turnoDTO.setHoraInicio(turno.getHoraInicio());
		turnoDTO.setHoraFin(turno.getHoraFin());
		turnoDTO.setGrupoId(turno.getGrupo() != null ? turno.getGrupo().getId() : null);
		turnoDTO.setTipoGrupo(turno.getTipo());

		// Map alumnos to AlumnoDTO
		if (turno.getAlumnos() != null) {
			turnoDTO.setAlumnos(turno.getAlumnos().stream()
					.map(AlumnoDTO::deAlumno)
					.collect(Collectors.toList()));
		}

		return turnoDTO;
	}
}
