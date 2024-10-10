package com.taemoi.project.dtos.response;

import com.taemoi.project.entidades.Turno;

public class TurnoCortoDTO {
    private String diaSemana;
    private String horaInicio;
    private String horaFin;
    private Long grupoId;

    public TurnoCortoDTO() {}

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

	public static TurnoCortoDTO deTurno(Turno turno) {
        if (turno == null) {
            return null;
        }

        TurnoCortoDTO turnoDTO = new TurnoCortoDTO();
        turnoDTO.setDiaSemana(turno.getDiaSemana());
        turnoDTO.setHoraInicio(turno.getHoraInicio());
        turnoDTO.setHoraFin(turno.getHoraFin());
        turnoDTO.setGrupoId(turno.getGrupo().getId());
        
        return turnoDTO;
    }
}
