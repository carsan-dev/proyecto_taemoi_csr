package com.taemoi.project.dtos;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;

public class AlumnoDeporteDTO {

	private Long id;
	private Deporte deporte;
	private String grado; // TipoGrado como String
	private Date fechaGrado;
	private Boolean aptoParaExamen;
	private Boolean activo;
	private Date fechaAlta;
	private Date fechaBaja;
	private String antiguedad; // Tiempo en el deporte (calculado)

	// Constructor vacío
	public AlumnoDeporteDTO() {
	}

	// Constructor completo
	public AlumnoDeporteDTO(Long id, Deporte deporte, String grado, Date fechaGrado, Boolean aptoParaExamen,
			Boolean activo, Date fechaAlta, Date fechaBaja, String antiguedad) {
		this.id = id;
		this.deporte = deporte;
		this.grado = grado;
		this.fechaGrado = fechaGrado;
		this.aptoParaExamen = aptoParaExamen;
		this.activo = activo;
		this.fechaAlta = fechaAlta;
		this.fechaBaja = fechaBaja;
		this.antiguedad = antiguedad;
	}

	/**
	 * Convierte una entidad AlumnoDeporte a DTO
	 */
	public static AlumnoDeporteDTO deAlumnoDeporte(AlumnoDeporte alumnoDeporte) {
		if (alumnoDeporte == null) {
			return null;
		}

		AlumnoDeporteDTO dto = new AlumnoDeporteDTO();
		dto.setId(alumnoDeporte.getId());
		dto.setDeporte(alumnoDeporte.getDeporte());
		dto.setGrado(alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado().name() : null);
		dto.setFechaGrado(alumnoDeporte.getFechaGrado());
		dto.setAptoParaExamen(alumnoDeporte.getAptoParaExamen());
		dto.setActivo(alumnoDeporte.getActivo());
		dto.setFechaAlta(alumnoDeporte.getFechaAlta());
		dto.setFechaBaja(alumnoDeporte.getFechaBaja());
		dto.setAntiguedad(calcularAntiguedad(alumnoDeporte.getFechaAlta(), alumnoDeporte.getFechaBaja()));

		return dto;
	}

	/**
	 * Calcula la antigüedad en el deporte
	 */
	private static String calcularAntiguedad(Date fechaAlta, Date fechaBaja) {
		if (fechaAlta == null) {
			return "N/A";
		}

		// Convertir a java.util.Date si es java.sql.Date
		java.util.Date utilFechaAlta = fechaAlta instanceof java.sql.Date
				? new java.util.Date(fechaAlta.getTime())
				: fechaAlta;

		java.util.Date utilFechaBaja = fechaBaja != null
				? (fechaBaja instanceof java.sql.Date ? new java.util.Date(fechaBaja.getTime()) : fechaBaja)
				: null;

		LocalDate inicio = utilFechaAlta.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate fin = utilFechaBaja != null
				? utilFechaBaja.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
				: LocalDate.now();

		Period periodo = Period.between(inicio, fin);
		int anos = periodo.getYears();
		int meses = periodo.getMonths();

		if (anos == 0 && meses == 0) {
			return "Menos de 1 mes";
		} else if (anos == 0) {
			return meses + (meses == 1 ? " mes" : " meses");
		} else if (meses == 0) {
			return anos + (anos == 1 ? " año" : " años");
		} else {
			return anos + (anos == 1 ? " año" : " años") + " " + meses + (meses == 1 ? " mes" : " meses");
		}
	}

	// Getters y Setters

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public Date getFechaGrado() {
		return fechaGrado;
	}

	public void setFechaGrado(Date fechaGrado) {
		this.fechaGrado = fechaGrado;
	}

	public Boolean getAptoParaExamen() {
		return aptoParaExamen;
	}

	public void setAptoParaExamen(Boolean aptoParaExamen) {
		this.aptoParaExamen = aptoParaExamen;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public String getAntiguedad() {
		return antiguedad;
	}

	public void setAntiguedad(String antiguedad) {
		this.antiguedad = antiguedad;
	}
}
