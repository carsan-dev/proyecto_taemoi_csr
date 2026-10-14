package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.entities.Imagen;

public class AlumnoCortoDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private Imagen fotoAlumno;
	private Integer numeroExpediente;
	private String grado;
	private Boolean activo;

	public AlumnoCortoDTO(Long id, String nombre, String apellidos, Imagen fotoAlumno, Integer numeroExpediente, String grado, Boolean activo) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fotoAlumno = fotoAlumno;
		this.numeroExpediente = numeroExpediente;
		this.grado = grado;
		this.activo = activo;
	}

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

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public static AlumnoCortoDTO deAlumno(Alumno alumno, boolean comportamientoLegadoTaemoi) {
		if (alumno == null) {
			return null;
		}

		String grado = null;
		AlumnoDeporte principal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());
		if (principal != null && principal.getGrado() != null) {
			grado = nombreGrado(principal.getGrado(), comportamientoLegadoTaemoi);
		}
		return new AlumnoCortoDTO(
			alumno.getId(),
			alumno.getNombre(),
			alumno.getApellidos(),
			alumno.getFotoAlumno(),
			alumno.getNumeroExpediente(),
			grado,
			alumno.getActivo()
		);
	}

	public static AlumnoCortoDTO deAlumnoDeporte(AlumnoDeporte alumnoDeporte, boolean comportamientoLegadoTaemoi) {
		if (alumnoDeporte == null || alumnoDeporte.getAlumno() == null) {
			return null;
		}

		Alumno alumno = alumnoDeporte.getAlumno();
		String grado = alumnoDeporte.getGrado() != null
				? nombreGrado(alumnoDeporte.getGrado(), comportamientoLegadoTaemoi)
				: null;

		return new AlumnoCortoDTO(
			alumno.getId(),
			alumno.getNombre(),
			alumno.getApellidos(),
			alumno.getFotoAlumno(),
			alumno.getNumeroExpediente(),
			grado,
			alumno.getActivo()
		);
	}

	/**
	 * Nombre del grado en los listados cortos. Con el comportamiento heredado de Taemoi (organización con
	 * {@code comportamiento_legado_taemoi}) es el del antiguo {@code TipoGrado.getNombre()} para los códigos de
	 * Taemoi (el mismo en todas las disciplinas: «Rojo» también en kickboxing, como antes de V28); en el resto
	 * de escuelas, y para códigos que no son de Taemoi, el del catálogo.
	 */
	private static String nombreGrado(com.taemoi.project.entities.Grado grado, boolean comportamientoLegadoTaemoi) {
		String legado = comportamientoLegadoTaemoi
				? com.taemoi.project.services.CatalogoLegadoTaemoi.nombreGradoLegado(grado.getCodigo())
				: null;
		return legado != null ? legado : grado.getNombre();
	}
}
