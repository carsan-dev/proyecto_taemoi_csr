package com.taemoi.project.dtos.response;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.taemoi.project.dtos.AlumnoDeporteDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoTarifa;

/**
 * DTO que representa un alumno con todos sus deportes
 * Usado para respuestas detalladas de la API
 */
public class AlumnoConDeportesDTO {

	private Long id;
	private String nombre;
	private String apellidos;
	private Integer numeroExpediente;
	private Date fechaNacimiento;
	private String nif;
	private String direccion;
	private Integer telefono;
	private String email;
	private TipoTarifa tipoTarifa;
	private Double cuantiaTarifa;
	private RolFamiliar rolFamiliar;
	private String grupoFamiliar;
	private Date fechaAlta;
	private Date fechaAltaInicial;
	private Boolean activo;
	private Date fechaBaja;
	private Boolean autorizacionWeb;
	private Boolean competidor;
	private Double peso;
	private Date fechaPeso;
	private Boolean tieneLicencia;
	private Integer numeroLicencia;
	private Date fechaLicencia;
	private Boolean tieneDiscapacidad;
	private Boolean tieneDerechoExamen;

	// Nuevo: Lista de deportes del alumno
	private List<AlumnoDeporteDTO> deportes = new ArrayList<>();

	// Constructor vacío
	public AlumnoConDeportesDTO() {
	}

	/**
	 * Convierte una entidad Alumno a DTO con todos sus deportes
	 */
	public static AlumnoConDeportesDTO deAlumno(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		AlumnoConDeportesDTO dto = new AlumnoConDeportesDTO();

		// Datos básicos
		dto.setId(alumno.getId());
		dto.setNombre(alumno.getNombre());
		dto.setApellidos(alumno.getApellidos());
		dto.setNumeroExpediente(alumno.getNumeroExpediente());
		dto.setFechaNacimiento(alumno.getFechaNacimiento());
		dto.setNif(alumno.getNif());
		dto.setDireccion(alumno.getDireccion());
		dto.setTelefono(alumno.getTelefono());
		dto.setEmail(alumno.getEmail());

		// Tarifa
		dto.setTipoTarifa(alumno.getTipoTarifa());
		dto.setCuantiaTarifa(alumno.getCuantiaTarifa());
		dto.setRolFamiliar(alumno.getRolFamiliar());
		dto.setGrupoFamiliar(alumno.getGrupoFamiliar());

		// Fechas y estado
		dto.setFechaAlta(alumno.getFechaAlta());
		dto.setFechaAltaInicial(alumno.getFechaAltaInicial());
		dto.setActivo(alumno.getActivo());
		dto.setFechaBaja(alumno.getFechaBaja());

		// Otros campos
		dto.setAutorizacionWeb(alumno.getAutorizacionWeb());
		dto.setCompetidor(alumno.getCompetidor());
		dto.setPeso(alumno.getPeso());
		dto.setFechaPeso(alumno.getFechaPeso());
		dto.setTieneLicencia(alumno.getTieneLicencia());
		dto.setNumeroLicencia(alumno.getNumeroLicencia());
		dto.setFechaLicencia(alumno.getFechaLicencia());
		dto.setTieneDiscapacidad(alumno.getTieneDiscapacidad());
		dto.setTieneDerechoExamen(alumno.getTieneDerechoExamen());

		// Convertir deportes a DTOs
		if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
			dto.setDeportes(alumno.getDeportes().stream()
					.map(AlumnoDeporteDTO::deAlumnoDeporte)
					.collect(Collectors.toList()));
		}

		return dto;
	}

	// Getters y Setters

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public Date getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(Date fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getNif() {
		return nif;
	}

	public void setNif(String nif) {
		this.nif = nif;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public Integer getTelefono() {
		return telefono;
	}

	public void setTelefono(Integer telefono) {
		this.telefono = telefono;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	public RolFamiliar getRolFamiliar() {
		return rolFamiliar;
	}

	public void setRolFamiliar(RolFamiliar rolFamiliar) {
		this.rolFamiliar = rolFamiliar;
	}

	public String getGrupoFamiliar() {
		return grupoFamiliar;
	}

	public void setGrupoFamiliar(String grupoFamiliar) {
		this.grupoFamiliar = grupoFamiliar;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public Date getFechaAltaInicial() {
		return fechaAltaInicial;
	}

	public void setFechaAltaInicial(Date fechaAltaInicial) {
		this.fechaAltaInicial = fechaAltaInicial;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public Boolean getAutorizacionWeb() {
		return autorizacionWeb;
	}

	public void setAutorizacionWeb(Boolean autorizacionWeb) {
		this.autorizacionWeb = autorizacionWeb;
	}

	public Boolean getCompetidor() {
		return competidor;
	}

	public void setCompetidor(Boolean competidor) {
		this.competidor = competidor;
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

	public Boolean getTieneDiscapacidad() {
		return tieneDiscapacidad;
	}

	public void setTieneDiscapacidad(Boolean tieneDiscapacidad) {
		this.tieneDiscapacidad = tieneDiscapacidad;
	}

	public Boolean getTieneDerechoExamen() {
		return tieneDerechoExamen;
	}

	public void setTieneDerechoExamen(Boolean tieneDerechoExamen) {
		this.tieneDerechoExamen = tieneDerechoExamen;
	}

	public List<AlumnoDeporteDTO> getDeportes() {
		return deportes;
	}

	public void setDeportes(List<AlumnoDeporteDTO> deportes) {
		this.deportes = deportes;
	}
}
