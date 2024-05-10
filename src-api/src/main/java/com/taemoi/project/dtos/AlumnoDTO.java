package com.taemoi.project.dtos;

import java.util.Date;

import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.TipoTarifa;

public class AlumnoDTO {
	private final Long id;
	private String nombre;
	private String apellidos;
	private Date fechaNacimiento;
	private String numeroExpediente;
	private String nif;
	private String direccion;
	private String email;
	private Integer telefono;
	private Double cuantiaTarifa;
	private TipoTarifa tipoTarifa;
	private Date fechaAlta;
	private Date fechaBaja;
	private String categoria;
	private String grado;
	private String fotoAlumno;

	public AlumnoDTO(final Long id, String nombre, String apellidos, Date fechaNacimiento, String numeroExpediente, String nif,
			String direccion, String email, Integer telefono, Double cuantiaTarifa, TipoTarifa tipoTarifa,
			Date fechaAlta, Date fechaBaja, String categoria, String grado, String fotoAlumno) {
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fechaNacimiento = fechaNacimiento;
		this.numeroExpediente = numeroExpediente;
		this.nif = nif;
		this.direccion = direccion;
		this.email = email;
		this.telefono = telefono;
		this.cuantiaTarifa = cuantiaTarifa;
		this.tipoTarifa = tipoTarifa;
		this.fechaAlta = fechaAlta;
		this.fechaBaja = fechaBaja;
		this.categoria = categoria;
		this.grado = grado;
		this.fotoAlumno = fotoAlumno;
	}
	
	public AlumnoDTO() {
		this.id = null;
	}

	public Long getId() {
		return id;
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

	public Date getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(Date fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Integer getTelefono() {
		return telefono;
	}

	public void setTelefono(Integer telefono) {
		this.telefono = telefono;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
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

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public String getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(String numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public String getNif() {
		return nif;
	}

	public void setNif(String nif) {
		this.nif = nif;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	/**
	 * Convierte un objeto Alumno en un objeto AlumnoDTO.
	 *
	 * @param alumno El objeto Alumno a convertir.
	 * @return El objeto AlumnoDTO resultante, o null si el parámetro alumno es null.
	 */
	public static AlumnoDTO deAlumno(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		String categoriaNombre = alumno.getCategoria() != null ? alumno.getCategoria().getNombre() : null;
		String gradoTipo = alumno.getGrado() != null && alumno.getGrado().getTipoGrado() != null
				? alumno.getGrado().getTipoGrado().name()
				: null;
		
	    Integer telefono = null;
	    if (alumno.getTelefono() != null) {
	        telefono = Integer.valueOf(alumno.getTelefono());
	    }

		return new AlumnoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFechaNacimiento(),
				alumno.getNumeroExpediente(), alumno.getNif(), alumno.getDireccion(), alumno.getEmail(),
				telefono, alumno.getCuantiaTarifa(), alumno.getTipoTarifa(),
				alumno.getFechaAlta(), alumno.getFechaBaja(), categoriaNombre, gradoTipo, alumno.getFotoAlumno());
	}
}
