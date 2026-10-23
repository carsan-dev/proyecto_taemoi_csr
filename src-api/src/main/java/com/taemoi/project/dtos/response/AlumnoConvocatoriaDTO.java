package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Imagen;

public class AlumnoConvocatoriaDTO {
	private Long id;

	private Long alumnoId;

	private String nombre;

	private String apellidos;

	private Double cuantiaExamen;

	private String gradoActual;

	private String gradoSiguiente;

	private Boolean pagado;

	public AlumnoConvocatoriaDTO(Long id, Long alumnoId, String nombre, String apellidos, Double cuantiaExamen,
			String gradoActual, String gradoSiguiente, Boolean pagado) {
		this.id = id;
		this.alumnoId = alumnoId;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.cuantiaExamen = cuantiaExamen;
		this.gradoActual = gradoActual;
		this.gradoSiguiente = gradoSiguiente;
		this.pagado = pagado;
	}

	public AlumnoConvocatoriaDTO() {
		// TODO Auto-generated constructor stub
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
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

	public Double getCuantiaExamen() {
		return cuantiaExamen;
	}

	public void setCuantiaExamen(Double cuantiaExamen) {
		this.cuantiaExamen = cuantiaExamen;
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

	/**
	 * Pieza 6b-1, opcional y solo de entrada (PUT /api/convocatorias/alumno/{id}): método con el que se cobra al pasar
	 * a pagado el cargo del examen. Ausente o nulo: {@code SIN_INDICAR}; un valor que no existe, 400. No sale en las
	 * respuestas.
	 */
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
	private com.taemoi.project.entities.MetodoPago metodoPago;

	public com.taemoi.project.entities.MetodoPago getMetodoPago() {
		return metodoPago;
	}

	public void setMetodoPago(com.taemoi.project.entities.MetodoPago metodoPago) {
		this.metodoPago = metodoPago;
	}

	public Boolean getPagado() {
		return pagado;
	}

	public void setPagado(Boolean pagado) {
		this.pagado = pagado;
	}

	/**
	 * Foto del alumno para la lista de la convocatoria, solo de salida (ver {@link #fotoDe}). Nula si no tiene.
	 */
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	private Imagen fotoAlumno;

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}

	/**
	 * La foto del alumno como la lleva el listado de alumnos ({@code AlumnoDTO}): una {@link Imagen} nueva (la de la
	 * entidad no se toca) cuya {@code url} es {@code /api/alumnos/{id}/imagen}, que {@code UrlArchivoSerializer}
	 * hace absoluta. Esa ruta decide por sí misma quién ve la foto (gestión, responsable de la sede del alumno,
	 * familia); quien lee la lista de una convocatoria ya puede leerla de cada alumno que recibe. Sin la ruta del
	 * fichero en disco. Nula si el alumno no tiene foto.
	 */
	public static Imagen fotoDe(Alumno alumno) {
		if (alumno == null || alumno.getId() == null || alumno.getFotoAlumno() == null) {
			return null;
		}
		Imagen original = alumno.getFotoAlumno();
		Imagen foto = new Imagen(original.getNombre(), original.getTipo(), "/api/alumnos/" + alumno.getId() + "/imagen",
				null);
		foto.setId(original.getId());
		return foto;
	}

}
