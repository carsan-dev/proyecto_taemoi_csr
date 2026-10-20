package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.taemoi.project.entities.ParentescoTutor;
import com.taemoi.project.entities.RolAsignacionProfesor;

/**
 * Respuestas de las rutas del profesor ({@code /api/profesor/**}, entrega 7, fase3-e7-profesor.md D7-12). Reducidas a
 * lo que pintan las pantallas §5.1-§5.3, con los nombres de grado ya resueltos (el catálogo sigue cerrado al profesor).
 * <b>Nunca</b> NIF, email, dirección, observaciones, tarifa, cobros ni fecha de nacimiento: un campo nuevo aquí se
 * revisa contra esa lista ({@code ProfesorGruposIntegrationTest.fichaSinNifEmailDireccion} comprueba las claves).
 * Las fechas de grado salen como {@code "yyyy-MM-dd"} (no como el array por defecto de la aplicación).
 */
public final class ProfesorDTOs {

	private ProfesorDTOs() {
	}

	public record Sede(Long id, String nombre) {
	}

	public record Disciplina(String codigo, String nombre) {
	}

	public record Grado(String codigo, String nombre) {
	}

	/**
	 * Un grupo del profesor (§5.1). {@code sede} nula si el grupo no tiene sede; {@code rol} es el suyo en el grupo
	 * (titular y ayudante tienen los mismos permisos, D7-4); {@code alumnosActivos} no cuenta los dados de baja.
	 */
	public record Grupo(Long id, String nombre, Sede sede, RolAsignacionProfesor rol, long alumnosActivos,
			Disciplina disciplina) {
	}

	/** Un alumno activo del grupo (§5.2), con sus deportes activos. */
	public record AlumnoDeGrupo(Long id, String nombre, String apellidos, List<DeporteEnLista> deportes) {
	}

	/**
	 * Un deporte activo del alumno en la lista del grupo. {@code grado} nulo si no tiene; {@code delGrupo}: es la
	 * disciplina del grupo.
	 */
	public record DeporteEnLista(String deporte, String deporteNombre, Grado grado,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaGrado, boolean apto,
			boolean delGrupo) {
	}

	/**
	 * Ficha reducida (§5.3). {@code edad} en años cumplidos (nula sin fecha de nacimiento); {@code grupos}: solo los que
	 * comparte con el profesor.
	 */
	public record Ficha(Long id, String nombre, String apellidos, Integer edad, List<GrupoCompartido> grupos,
			List<Contacto> contacto, List<DeporteDelAlumno> deportes) {
	}

	public record GrupoCompartido(Long id, String nombre, Sede sede) {
	}

	/**
	 * Un teléfono de contacto (D7-2): del alumno ({@code tipo} {@code ALUMNO}, sin parentesco) o de un tutor
	 * ({@code TUTOR}, con nombre, apellidos y parentesco).
	 */
	public record Contacto(String tipo, String nombre, String apellidos, ParentescoTutor parentesco, String telefono) {
	}

	/**
	 * Un deporte activo del alumno en la ficha. {@code deMisGrupos}: la disciplina es la de algún grupo que comparte con
	 * el profesor (§11, pregunta 2). {@code admitePases}: además la disciplina tiene grados, hay grado actual y siguiente
	 * grado (el apto va aparte). {@code siguienteGrado} nulo si no lo hay. {@code pideRojoBordado}: el pase pide elegir
	 * «Rojo» o «Rojo bordado», como en gestión.
	 */
	public record DeporteDelAlumno(String deporte, String deporteNombre, Grado grado,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaGrado, boolean apto,
			boolean deMisGrupos, boolean admitePases, Grado siguienteGrado, boolean pideRojoBordado) {
	}
}
