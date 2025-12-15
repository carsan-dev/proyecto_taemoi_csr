package com.taemoi.project.dtos;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;

public class AlumnoDeporteDTO {

	// Identity and sport
	private Long id;
	private Deporte deporte;

	// Grade data (per-sport)
	private String grado; // TipoGrado como String
	private Date fechaGrado;
	private Boolean aptoParaExamen;

	// Status and dates
	private Boolean activo;
	private Date fechaAlta;
	private Date fechaAltaInicial; // For seniority calculation
	private Date fechaBaja;
	private String antiguedad; // Tiempo en el deporte (calculado from fechaAltaInicial)

	// Tarifa (per-sport)
	private String tipoTarifa; // TipoTarifa as String
	private Double cuantiaTarifa;
	private String rolFamiliar; // RolFamiliar as String
	private String grupoFamiliar;

	// Competitor data (per-sport)
	private Boolean competidor;
	private Date fechaAltaCompeticion;
	private Date fechaAltaCompetidorInicial;
	private String antiguedadCompetidor; // Tiempo como competidor (calculado from fechaAltaCompetidorInicial)
	private Double peso;
	private Date fechaPeso;

	// License data (per-sport)
	private Boolean tieneLicencia;
	private Integer numeroLicencia;
	private Date fechaLicencia;

	// Constructor vacío
	public AlumnoDeporteDTO() {
	}

	/**
	 * Convierte una entidad AlumnoDeporte a DTO
	 * UPDATED: Now includes all per-sport fields (tarifa, licencia, competidor, etc.)
	 * and uses fechaAltaInicial from AlumnoDeporte (not from Alumno) for antiguedad calculation
	 *
	 * @param alumnoDeporte La entidad AlumnoDeporte a convertir
	 * @return El DTO convertido
	 */
	public static AlumnoDeporteDTO deAlumnoDeporte(AlumnoDeporte alumnoDeporte) {
		if (alumnoDeporte == null) {
			return null;
		}

		AlumnoDeporteDTO dto = new AlumnoDeporteDTO();

		// Identity and sport
		dto.setId(alumnoDeporte.getId());
		dto.setDeporte(alumnoDeporte.getDeporte());

		// Grade data
		dto.setGrado(alumnoDeporte.getGrado() != null ? alumnoDeporte.getGrado().getTipoGrado().name() : null);
		dto.setFechaGrado(alumnoDeporte.getFechaGrado());
		dto.setAptoParaExamen(alumnoDeporte.getAptoParaExamen());

		// Status and dates
		dto.setActivo(alumnoDeporte.getActivo());
		dto.setFechaAlta(alumnoDeporte.getFechaAlta());
		dto.setFechaAltaInicial(alumnoDeporte.getFechaAltaInicial());
		dto.setFechaBaja(alumnoDeporte.getFechaBaja());

		// FIXED: Use fechaAltaInicial from AlumnoDeporte entity (per-sport) instead of Alumno (general)
		Date fechaParaAntiguedad = alumnoDeporte.getFechaAltaInicial() != null
			? alumnoDeporte.getFechaAltaInicial()
			: alumnoDeporte.getFechaAlta();
		dto.setAntiguedad(calcularAntiguedad(fechaParaAntiguedad, alumnoDeporte.getFechaBaja()));

		// Tarifa (per-sport)
		dto.setTipoTarifa(alumnoDeporte.getTipoTarifa() != null ? alumnoDeporte.getTipoTarifa().name() : null);
		dto.setCuantiaTarifa(alumnoDeporte.getCuantiaTarifa());
		dto.setRolFamiliar(alumnoDeporte.getRolFamiliar() != null ? alumnoDeporte.getRolFamiliar().name() : null);
		dto.setGrupoFamiliar(alumnoDeporte.getGrupoFamiliar());

		// Competitor data (per-sport)
		dto.setCompetidor(alumnoDeporte.getCompetidor());
		dto.setFechaAltaCompeticion(alumnoDeporte.getFechaAltaCompeticion());
		dto.setFechaAltaCompetidorInicial(alumnoDeporte.getFechaAltaCompetidorInicial());

		// Calculate antiguedadCompetidor if the student is a competitor
		if (Boolean.TRUE.equals(alumnoDeporte.getCompetidor())) {
			Date fechaParaAntiguedadCompetidor = alumnoDeporte.getFechaAltaCompetidorInicial() != null
				? alumnoDeporte.getFechaAltaCompetidorInicial()
				: alumnoDeporte.getFechaAltaCompeticion();
			dto.setAntiguedadCompetidor(calcularAntiguedad(fechaParaAntiguedadCompetidor, null));
		}

		dto.setPeso(alumnoDeporte.getPeso());
		dto.setFechaPeso(alumnoDeporte.getFechaPeso());

		// License data (per-sport)
		dto.setTieneLicencia(alumnoDeporte.getTieneLicencia());
		dto.setNumeroLicencia(alumnoDeporte.getNumeroLicencia());
		dto.setFechaLicencia(alumnoDeporte.getFechaLicencia());

		return dto;
	}

	/**
	 * @deprecated Use deAlumnoDeporte(AlumnoDeporte) instead.
	 * This method is kept for backward compatibility but should not be used.
	 */
	@Deprecated
	public static AlumnoDeporteDTO deAlumnoDeporte(AlumnoDeporte alumnoDeporte, Date fechaAltaInicialAlumno) {
		// Just delegate to the new method that uses fechaAltaInicial from AlumnoDeporte
		return deAlumnoDeporte(alumnoDeporte);
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

	public Date getFechaAltaInicial() {
		return fechaAltaInicial;
	}

	public void setFechaAltaInicial(Date fechaAltaInicial) {
		this.fechaAltaInicial = fechaAltaInicial;
	}

	public String getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(String tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	public String getRolFamiliar() {
		return rolFamiliar;
	}

	public void setRolFamiliar(String rolFamiliar) {
		this.rolFamiliar = rolFamiliar;
	}

	public String getGrupoFamiliar() {
		return grupoFamiliar;
	}

	public void setGrupoFamiliar(String grupoFamiliar) {
		this.grupoFamiliar = grupoFamiliar;
	}

	public Boolean getCompetidor() {
		return competidor;
	}

	public void setCompetidor(Boolean competidor) {
		this.competidor = competidor;
	}

	public Date getFechaAltaCompeticion() {
		return fechaAltaCompeticion;
	}

	public void setFechaAltaCompeticion(Date fechaAltaCompeticion) {
		this.fechaAltaCompeticion = fechaAltaCompeticion;
	}

	public Date getFechaAltaCompetidorInicial() {
		return fechaAltaCompetidorInicial;
	}

	public void setFechaAltaCompetidorInicial(Date fechaAltaCompetidorInicial) {
		this.fechaAltaCompetidorInicial = fechaAltaCompetidorInicial;
	}

	public String getAntiguedadCompetidor() {
		return antiguedadCompetidor;
	}

	public void setAntiguedadCompetidor(String antiguedadCompetidor) {
		this.antiguedadCompetidor = antiguedadCompetidor;
	}

	public Double getPeso() {
		return peso;
	}

	public void setPeso(Double peso) {
		this.peso = peso;
	}

	public Date getFechaPeso() {
		return fechaPeso;
	}

	public void setFechaPeso(Date fechaPeso) {
		this.fechaPeso = fechaPeso;
	}

	public Boolean getTieneLicencia() {
		return tieneLicencia;
	}

	public void setTieneLicencia(Boolean tieneLicencia) {
		this.tieneLicencia = tieneLicencia;
	}

	public Integer getNumeroLicencia() {
		return numeroLicencia;
	}

	public void setNumeroLicencia(Integer numeroLicencia) {
		this.numeroLicencia = numeroLicencia;
	}

	public Date getFechaLicencia() {
		return fechaLicencia;
	}

	public void setFechaLicencia(Date fechaLicencia) {
		this.fechaLicencia = fechaLicencia;
	}
}
