package com.taemoi.project.dtos;

import java.util.List;
import java.util.stream.Collectors;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Turno;

public class TurnoDTO {

	private Long id;
	private String diaSemana;
	private String horaInicio;
	private String horaFin;
	private Long grupoId;
	private String grupoNombre;
	private String tipoGrupo;
	private List<AlumnoDTO> alumnos;

	public TurnoDTO() {
	}

	public TurnoDTO(Long id, String diaSemana, String horaInicio, String horaFin, Long grupoId, String grupoNombre,
			String tipoGrupo) {
		this.id = id;
		this.diaSemana = diaSemana;
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
		this.grupoId = grupoId;
		this.grupoNombre = grupoNombre;
		this.tipoGrupo = tipoGrupo;
	}

	public TurnoDTO(Long id, String diaSemana, String horaInicio, String horaFin, String tipoGrupo) {
		this.id = id;
		this.diaSemana = diaSemana;
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
		this.tipoGrupo = tipoGrupo;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getGrupoNombre() {
		return grupoNombre;
	}

	public void setGrupoNombre(String grupoNombre) {
		this.grupoNombre = grupoNombre;
	}

	public String getTipoGrupo() {
		return tipoGrupo;
	}

	public void setTipoGrupo(String tipoGrupo) {
		this.tipoGrupo = tipoGrupo;
	}

	public List<AlumnoDTO> getAlumnos() {
		return alumnos;
	}

	public void setAlumnos(List<AlumnoDTO> alumnos) {
		this.alumnos = alumnos;
	}

	public static TurnoDTO deTurno(Turno turno) {
		if (turno == null) {
			return null;
		}

		TurnoDTO turnoDTO = new TurnoDTO();
		turnoDTO.setId(turno.getId());
		turnoDTO.setDiaSemana(turno.getDiaSemana());
		turnoDTO.setHoraInicio(turno.getHoraInicio());
		turnoDTO.setHoraFin(turno.getHoraFin());

		// Mapear el grupoId y grupoNombre
		turnoDTO.setGrupoId(turno.getGrupo() != null ? turno.getGrupo().getId() : null);
		turnoDTO.setGrupoNombre(turno.getGrupo() != null ? turno.getGrupo().getNombre() : "Sin grupo");
		turnoDTO.setTipoGrupo(turno.getTipo());

		// Mapear la lista de alumnos a AlumnoDTO
		if (turno.getAlumnos() != null) {
			Deporte deporteGrupo = turno.getGrupo() != null ? turno.getGrupo().getDeporte() : null;
			turnoDTO.setAlumnos(turno.getAlumnos().stream()
					.filter(alumno -> Boolean.TRUE.equals(alumno.getActivo()))
					.filter(alumno -> {
						if (deporteGrupo == null) {
							return true;
						}
						if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
							return alumno.getDeportes().stream()
									.anyMatch(ad -> deporteGrupo.equals(ad.getDeporte())
											&& Boolean.TRUE.equals(ad.getActivo()));
						}
						return alumno.getDeporte() != null && alumno.getDeporte().equals(deporteGrupo);
					})
					.map(AlumnoDTO::deAlumno)
					.collect(Collectors.toList()));
		}

		return turnoDTO;
	}
}
