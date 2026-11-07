package com.taemoi.project.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.taemoi.project.archivos.UrlArchivoSerializer;

/**
 * Respuesta de {@code GET /api/publico/web} (fase4-web-plan.md §5, pieza W1): lo que pintan las páginas de la web por
 * plantilla de la escuela activa. Anónimo: SOLO datos públicos. Nunca CIF, firmante, buzones, correo de las sedes,
 * aforo u ocupación, marcas internas de tarifas ni datos personales de alumnos o profesores (los profesores son
 * bloques editables).
 *
 * @param escuela          nombre, color y versión del logo.
 * @param tema             tema publicado; en la vista previa de la gestión, el elegido en el panel si lo pasa
 *                         ({@code tema}, pieza W3); {@code null} en una vista previa sin web aún y sin tema.
 * @param publicada        si la web está publicada (en una respuesta anónima, siempre {@code true}).
 * @param vistaPrevia      si es la vista previa de la gestión (no se guarda en cachés compartidas).
 * @param temporada        temporada en curso («2026-2027»).
 * @param bloques          bloques editables, por {@code orden} y clave.
 * @param sedes            sedes activas, por id, con sus grupos (de disciplinas activas, con turnos, visibles en la
 *                         preinscripción).
 * @param tarifas          tarifas activas, por {@code orden} del catálogo.
 * @param tarifasSede      tarifas por sede vigentes hoy y futuras, de las tarifas y sedes que salen.
 * @param licencias        la licencia de cada disciplina activa que la tiene.
 * @param examenDesde      menor precio de examen (mayor que 0); {@code null} sin exámenes.
 * @param proximosEventos  los 3 próximos eventos visibles, del más cercano al más lejano.
 * @param contacto         correo y teléfono de contacto de la escuela.
 */
public record WebPublicaDTO(Escuela escuela, String tema, boolean publicada, boolean vistaPrevia, String temporada,
		List<Bloque> bloques, List<Sede> sedes, List<Tarifa> tarifas, List<TarifaSede> tarifasSede,
		List<Licencia> licencias, Double examenDesde, List<Evento> proximosEventos, Contacto contacto) {

	/**
	 * @param logoVersion id de la imagen del logo subido (versión para la caché de la URL pública del logo,
	 *                    {@code GET /api/publico/escuela/logo} de la pieza 4 del plan de dominio); {@code null} sin logo.
	 */
	public record Escuela(String nombre, String colorPrimario, Long logoVersion) {
	}

	/** @param imagenUrl URL de la imagen del bloque (absoluta con {@code app.base.url}); {@code null} sin imagen. */
	public record Bloque(String clave, String texto, @JsonSerialize(using = UrlArchivoSerializer.class) String imagenUrl,
			int orden) {
	}

	public record Sede(Long id, String nombre, String direccion, String localidad, String codigoPostal, String telefono,
			List<Grupo> grupos) {
	}

	/**
	 * @param plazas el peor de sus turnos.
	 */
	public record Grupo(Long id, String nombre, String disciplina, String disciplinaNombre, Integer rangoEdadMin,
			Integer rangoEdadMax, PlazasWeb plazas, List<Turno> turnos) {
	}

	public record Turno(Long id, String diaSemana, String horaInicio, String horaFin, PlazasWeb plazas) {
	}

	/** @param disciplina código de la disciplina; {@code null} si la tarifa no es de una disciplina. */
	public record Tarifa(Long id, String codigo, String nombre, String disciplina, BigDecimal importe,
			BigDecimal importeAdulto, BigDecimal importeMenor) {
	}

	public record TarifaSede(Long tarifaId, Long sedeId, BigDecimal importe, BigDecimal importeAdulto,
			BigDecimal importeMenor,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate vigenteDesde) {
	}

	public record Licencia(String disciplina, String concepto, Double precio) {
	}

	/** @param conImagen si tiene foto ({@code GET /api/eventos/{id}/imagen}). */
	public record Evento(Long id, String titulo,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha, boolean conImagen) {
	}

	public record Contacto(String email, String telefono) {
	}
}
