package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Turno;

/**
 * Turno de un alumno en un grupo ({@code GET /api/grupos/{grupoId}/alumnos/{alumnoId}/turnos}), endpoint que
 * también usa la familia ({@code ROLE_USER}) dueña del alumno.
 *
 * <p>No incluye los alumnos del turno: hasta esta versión la respuesta era {@link TurnoCortoDTO}, que incrusta el
 * {@code AlumnoDTO} completo de todos los compañeros (NIF, dirección, contacto, responsable legal...). El resto de
 * campos se mantiene igual que en {@link TurnoCortoDTO#deTurno}; {@code ocupacionEfectiva} y {@code completo}
 * siguen sin rellenarse aquí ({@code null}), como antes.
 */
public class TurnoDelAlumnoEnGrupoDTO {

	private Long id;
	private String diaSemana;
	private String horaInicio;
	private String horaFin;
	private Long grupoId;
	private String tipoGrupo;
	private Integer rangoEdadMin;
	private Integer rangoEdadMax;
	private Integer ocupacionEfectiva;
	private Boolean completo;

	public static TurnoDelAlumnoEnGrupoDTO deTurno(Turno turno) {
		if (turno == null) {
			return null;
		}
		TurnoDelAlumnoEnGrupoDTO dto = new TurnoDelAlumnoEnGrupoDTO();
		dto.setId(turno.getId());
		dto.setDiaSemana(turno.getDiaSemana());
		dto.setHoraInicio(turno.getHoraInicio());
		dto.setHoraFin(turno.getHoraFin());
		dto.setGrupoId(turno.getGrupo() != null ? turno.getGrupo().getId() : null);
		dto.setTipoGrupo(turno.getTipo());
		dto.setRangoEdadMin(turno.getGrupo() != null ? turno.getGrupo().getRangoEdadMin() : null);
		dto.setRangoEdadMax(turno.getGrupo() != null ? turno.getGrupo().getRangoEdadMax() : null);
		return dto;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getDiaSemana() { return diaSemana; }
	public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }
	public String getHoraInicio() { return horaInicio; }
	public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
	public String getHoraFin() { return horaFin; }
	public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
	public Long getGrupoId() { return grupoId; }
	public void setGrupoId(Long grupoId) { this.grupoId = grupoId; }
	public String getTipoGrupo() { return tipoGrupo; }
	public void setTipoGrupo(String tipoGrupo) { this.tipoGrupo = tipoGrupo; }
	public Integer getRangoEdadMin() { return rangoEdadMin; }
	public void setRangoEdadMin(Integer rangoEdadMin) { this.rangoEdadMin = rangoEdadMin; }
	public Integer getRangoEdadMax() { return rangoEdadMax; }
	public void setRangoEdadMax(Integer rangoEdadMax) { this.rangoEdadMax = rangoEdadMax; }
	public Integer getOcupacionEfectiva() { return ocupacionEfectiva; }
	public void setOcupacionEfectiva(Integer ocupacionEfectiva) { this.ocupacionEfectiva = ocupacionEfectiva; }
	public Boolean getCompleto() { return completo; }
	public void setCompleto(Boolean completo) { this.completo = completo; }
}
