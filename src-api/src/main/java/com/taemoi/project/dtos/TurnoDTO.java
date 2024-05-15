package com.taemoi.project.dtos;

import com.taemoi.project.entidades.Turno;

public class TurnoDTO {
    
    private String diaSemana;
    private String horaInicio;
    private String horaFin;

    public TurnoDTO() {}

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

	public static TurnoDTO deTurno(Turno turno) {
        if (turno == null) {
            return null;
        }

        TurnoDTO turnoDTO = new TurnoDTO();
        turnoDTO.setDiaSemana(turno.getDiaSemana());
        turnoDTO.setHoraInicio(turno.getHoraInicio());
        turnoDTO.setHoraFin(turno.getHoraFin());
        
        return turnoDTO;
    }
}