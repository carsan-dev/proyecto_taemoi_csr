package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.Date;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Turno;

/**
 * Turno tal como lo ve cualquiera en la página pública /horarios ({@code GET /api/turnos/dto}, sin sesión).
 *
 * <p>No incluye los alumnos: hasta esta versión la respuesta incrustaba cada {@code AlumnoDTO} completo (NIF,
 * dirección, contacto, responsable legal...). /horarios solo usaba de ellos la fecha de nacimiento para pintar
 * el rango de edad cuando el grupo no tiene uno configurado; ese rango se calcula ahora aquí
 * ({@link #edadMinAlumnos}/{@link #edadMaxAlumnos}) con las mismas reglas que el navegador:
 * <ul>
 * <li>cuentan los mismos alumnos que en {@link TurnoCortoDTO}: activos y, si el grupo tiene deporte, con ese
 * deporte activo (sin grupo, todos los activos);</li>
 * <li>edad en años cumplidos respecto a {@code hoy} (la fecha de Madrid, que fija el servicio);</li>
 * <li>sin alumnos que cuenten, ambos valores son {@code null} (/horarios muestra "Sin alumnos");</li>
 * <li>si el grupo tiene rango configurado (mínimo o máximo), /horarios muestra ese rango y nunca las edades de
 * los alumnos, así que no se envían ({@code null}).</li>
 * </ul>
 */
public class TurnoPublicoDTO {

	private Long id;
	private String diaSemana;
	private String horaInicio;
	private String horaFin;
	private Long grupoId;
	private String tipoGrupo;
	private Integer rangoEdadMin;
	private Integer rangoEdadMax;
	private Integer edadMinAlumnos;
	private Integer edadMaxAlumnos;
	private Integer ocupacionEfectiva;
	private Boolean completo;

	public static TurnoPublicoDTO deTurno(Turno turno, LocalDate hoy) {
		if (turno == null) {
			return null;
		}
		TurnoPublicoDTO dto = new TurnoPublicoDTO();
		dto.setId(turno.getId());
		dto.setDiaSemana(turno.getDiaSemana());
		dto.setHoraInicio(turno.getHoraInicio());
		dto.setHoraFin(turno.getHoraFin());
		dto.setGrupoId(turno.getGrupo() != null ? turno.getGrupo().getId() : null);
		dto.setTipoGrupo(turno.getTipo());
		dto.setRangoEdadMin(turno.getGrupo() != null ? turno.getGrupo().getRangoEdadMin() : null);
		dto.setRangoEdadMax(turno.getGrupo() != null ? turno.getGrupo().getRangoEdadMax() : null);

		if (dto.getRangoEdadMin() == null && dto.getRangoEdadMax() == null) {
			IntSummaryStatistics edades = alumnosQueCuentan(turno)
					.map(Alumno::getFechaNacimiento)
					.filter(Objects::nonNull)
					.mapToInt(fecha -> edad(fechaLocal(fecha), hoy))
					.summaryStatistics();
			if (edades.getCount() > 0) {
				dto.setEdadMinAlumnos(edades.getMin());
				dto.setEdadMaxAlumnos(edades.getMax());
			}
		}
		return dto;
	}

	/** Mismo filtro que {@link TurnoCortoDTO#deTurno} (y {@code TurnoDTO}): activos y con el deporte del grupo activo. */
	private static Stream<Alumno> alumnosQueCuentan(Turno turno) {
		List<Alumno> alumnos = turno.getAlumnos();
		if (alumnos == null) {
			return Stream.empty();
		}
		String deporteGrupo = turno.getGrupo() != null ? turno.getGrupo().getDeporte() : null;
		return alumnos.stream()
				.filter(alumno -> Boolean.TRUE.equals(alumno.getActivo()))
				.filter(alumno -> {
					if (deporteGrupo == null) {
						return true;
					}
					return alumno.getDeportes() != null && alumno.getDeportes().stream()
							.anyMatch(ad -> deporteGrupo.equals(ad.getDeporte())
									&& Boolean.TRUE.equals(ad.getActivo()));
				});
	}

	/**
	 * Edad en años cumplidos con la regla de {@code calcularEdad} de /horarios: diferencia de años, menos uno si
	 * en {@code hoy} aún no se ha llegado al mes y día de nacimiento (un 29 de febrero cumple el 1 de marzo).
	 */
	public static int edad(LocalDate nacimiento, LocalDate hoy) {
		int edad = hoy.getYear() - nacimiento.getYear();
		int diferenciaMeses = hoy.getMonthValue() - nacimiento.getMonthValue();
		if (diferenciaMeses < 0 || (diferenciaMeses == 0 && hoy.getDayOfMonth() < nacimiento.getDayOfMonth())) {
			edad--;
		}
		return edad;
	}

	/**
	 * Fecha de nacimiento como fecha de calendario. Hibernate la carga como {@code java.sql.Date}
	 * ({@code @Temporal(DATE)}): su fecha es la de la base de datos (la que veía el navegador en España). Un
	 * {@code java.util.Date} con hora se interpreta en Madrid. ({@code java.sql.Date#toInstant} no existe.)
	 */
	public static LocalDate fechaLocal(Date fecha) {
		if (fecha instanceof java.sql.Date sql) {
			return sql.toLocalDate();
		}
		return fecha.toInstant().atZone(ValidacionRelojConfig.ZONA).toLocalDate();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getDiaSemana() { return diaSemana; }
	public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }
	public String getHoraInicio() { return horaInicio; }
	public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
	public String getHoraFin() { return horaFin; }
	public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
	public Long getGrupoId() { return grupoId; }
	public void setGrupoId(Long grupoId) { this.grupoId = grupoId; }
	public String getTipoGrupo() { return tipoGrupo; }
	public void setTipoGrupo(String tipoGrupo) { this.tipoGrupo = tipoGrupo; }
	public Integer getRangoEdadMin() { return rangoEdadMin; }
	public void setRangoEdadMin(Integer rangoEdadMin) { this.rangoEdadMin = rangoEdadMin; }
	public Integer getRangoEdadMax() { return rangoEdadMax; }
	public void setRangoEdadMax(Integer rangoEdadMax) { this.rangoEdadMax = rangoEdadMax; }
	public Integer getEdadMinAlumnos() { return edadMinAlumnos; }
	public void setEdadMinAlumnos(Integer edadMinAlumnos) { this.edadMinAlumnos = edadMinAlumnos; }
	public Integer getEdadMaxAlumnos() { return edadMaxAlumnos; }
	public void setEdadMaxAlumnos(Integer edadMaxAlumnos) { this.edadMaxAlumnos = edadMaxAlumnos; }
	public Integer getOcupacionEfectiva() { return ocupacionEfectiva; }
	public void setOcupacionEfectiva(Integer ocupacionEfectiva) { this.ocupacionEfectiva = ocupacionEfectiva; }
	public Boolean getCompleto() { return completo; }
	public void setCompleto(Boolean completo) { this.completo = completo; }
}
