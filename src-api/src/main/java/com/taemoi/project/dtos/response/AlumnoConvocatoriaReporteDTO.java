package com.taemoi.project.dtos.response;


public class AlumnoConvocatoriaReporteDTO {
	private Long alumnoId;
	private String nombreCompleto;
	private Integer numeroExpediente;
	private Integer numeroLicencia;
	private Integer edad;
	private String categoria;
	private Double peso;
	private Boolean pagado;
	private String gradoActual;
	private String gradoSiguiente;

	public AlumnoConvocatoriaReporteDTO() {
	}

	public AlumnoConvocatoriaReporteDTO(Long alumnoId, String nombreCompleto, Integer numeroExpediente,
			Integer numeroLicencia, Integer edad, String categoria, Double peso, Boolean pagado,
			String gradoActual, String gradoSiguiente) {
		this.alumnoId = alumnoId;
		this.nombreCompleto = nombreCompleto;
		this.numeroExpediente = numeroExpediente;
		this.numeroLicencia = numeroLicencia;
		this.edad = edad;
		this.categoria = categoria;
		this.peso = peso;
		this.pagado = pagado;
		this.gradoActual = gradoActual;
		this.gradoSiguiente = gradoSiguiente;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public String getNombreCompleto() {
		return nombreCompleto;
	}

	public void setNombreCompleto(String nombreCompleto) {
		this.nombreCompleto = nombreCompleto;
	}

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public Integer getNumeroLicencia() {
		return numeroLicencia;
	}

	public void setNumeroLicencia(Integer numeroLicencia) {
		this.numeroLicencia = numeroLicencia;
	}

	public Integer getEdad() {
		return edad;
	}

	public void setEdad(Integer edad) {
		this.edad = edad;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public Double getPeso() {
		return peso;
	}

	public void setPeso(Double peso) {
		this.peso = peso;
	}

	public Boolean getPagado() {
		return pagado;
	}

	public void setPagado(Boolean pagado) {
		this.pagado = pagado;
	}

	public String getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(String gradoActual) {
		this.gradoActual = gradoActual;
	}

	public String getGradoSiguiente() {
		return gradoSiguiente;
	}

	public void setGradoSiguiente(String gradoSiguiente) {
		this.gradoSiguiente = gradoSiguiente;
	}
}
