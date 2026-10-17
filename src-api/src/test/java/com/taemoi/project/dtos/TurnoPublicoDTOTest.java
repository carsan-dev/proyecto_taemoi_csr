package com.taemoi.project.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.dtos.response.TurnoPublicoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Turno;

/**
 * Respuesta pública de {@code GET /api/turnos/dto}: sin alumnos, con el rango de edad de los alumnos que
 * cuentan calculado en el servidor exactamente como lo calculaba /horarios en el navegador (mismos alumnos
 * que TurnoCortoDTO: activos y, si el grupo tiene deporte, con ese deporte activo; edad en años cumplidos).
 * La fecha de referencia se pasa explícita: nada depende de la hora local de la máquina.
 */
class TurnoPublicoDTOTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 17);

	@Test
	void copiaLosCamposNoPersonalesDelTurno() {
		Grupo grupo = grupo("TAEKWONDO");
		grupo.setId(7L);
		Turno turno = new Turno("Lunes", "18:00", "19:00", grupo);
		turno.setId(11L);
		turno.setTipo("Taekwondo infantil");

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertEquals(11L, dto.getId());
		assertEquals("Lunes", dto.getDiaSemana());
		assertEquals("18:00", dto.getHoraInicio());
		assertEquals("19:00", dto.getHoraFin());
		assertEquals(7L, dto.getGrupoId());
		assertEquals("Taekwondo infantil", dto.getTipoGrupo());
	}

	@Test
	void sinAlumnosNoHayEdades() {
		Turno turno = turno(grupo("TAEKWONDO"));
		assertNull(TurnoPublicoDTO.deTurno(turno, HOY).getEdadMinAlumnos());
		assertNull(TurnoPublicoDTO.deTurno(turno, HOY).getEdadMaxAlumnos());

		turno.setAlumnos(null);
		assertNull(TurnoPublicoDTO.deTurno(turno, HOY).getEdadMinAlumnos());
		assertNull(TurnoPublicoDTO.deTurno(turno, HOY).getEdadMaxAlumnos());
	}

	@Test
	void minimoYMaximoDeLosAlumnosActivosDelDeporteDelGrupo() {
		Turno turno = turno(grupo("TAEKWONDO"));
		turno.getAlumnos().add(alumno(LocalDate.of(2016, 1, 10), true, "TAEKWONDO", true)); // 10
		turno.getAlumnos().add(alumno(LocalDate.of(2012, 3, 1), true, "TAEKWONDO", true)); // 14
		// No cuentan: inactivo, de otro deporte y con el deporte del grupo dado de baja.
		turno.getAlumnos().add(alumno(LocalDate.of(1980, 1, 1), false, "TAEKWONDO", true));
		turno.getAlumnos().add(alumno(LocalDate.of(1970, 1, 1), true, "KICKBOXING", true));
		turno.getAlumnos().add(alumno(LocalDate.of(2024, 1, 1), true, "TAEKWONDO", false));

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertEquals(10, dto.getEdadMinAlumnos());
		assertEquals(14, dto.getEdadMaxAlumnos());
	}

	@Test
	void soloAlumnosQueNoCuentanEsComoSinAlumnos() {
		Turno turno = turno(grupo("TAEKWONDO"));
		turno.getAlumnos().add(alumno(LocalDate.of(1980, 1, 1), false, "TAEKWONDO", true));
		turno.getAlumnos().add(alumno(LocalDate.of(1970, 1, 1), true, "KICKBOXING", true));

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertNull(dto.getEdadMinAlumnos());
		assertNull(dto.getEdadMaxAlumnos());
	}

	@Test
	void unSoloAlumnoDaMinimoIgualAMaximo() {
		Turno turno = turno(grupo("TAEKWONDO"));
		turno.getAlumnos().add(alumno(LocalDate.of(2000, 5, 5), true, "TAEKWONDO", true));

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertEquals(26, dto.getEdadMinAlumnos());
		assertEquals(26, dto.getEdadMaxAlumnos());
	}

	@Test
	void turnoSinGrupoCuentaATodosLosActivos() {
		Turno turno = turno(null);
		turno.getAlumnos().add(alumno(LocalDate.of(2010, 1, 1), true, "KICKBOXING", true)); // 16
		turno.getAlumnos().add(alumno(LocalDate.of(1990, 1, 1), true, null, true)); // 36
		turno.getAlumnos().add(alumno(LocalDate.of(1950, 1, 1), false, null, true));

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertNull(dto.getGrupoId());
		assertEquals(16, dto.getEdadMinAlumnos());
		assertEquals(36, dto.getEdadMaxAlumnos());
	}

	@Test
	void conRangoConfiguradoNoSeExponenLasEdadesDeLosAlumnos() {
		Grupo soloMinimo = grupo("TAEKWONDO");
		soloMinimo.setRangoEdadMin(6);
		Grupo soloMaximo = grupo("TAEKWONDO");
		soloMaximo.setRangoEdadMax(12);
		Grupo ambos = grupo("TAEKWONDO");
		ambos.setRangoEdadMin(6);
		ambos.setRangoEdadMax(12);
		for (Grupo grupo : List.of(soloMinimo, soloMaximo, ambos)) {
			Turno turno = turno(grupo);
			turno.getAlumnos().add(alumno(LocalDate.of(2016, 1, 10), true, "TAEKWONDO", true));

			TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

			assertEquals(grupo.getRangoEdadMin(), dto.getRangoEdadMin());
			assertEquals(grupo.getRangoEdadMax(), dto.getRangoEdadMax());
			assertNull(dto.getEdadMinAlumnos());
			assertNull(dto.getEdadMaxAlumnos());
		}
	}

	/** Misma regla que calcularEdad() de horarios: años de diferencia, menos uno si aún no ha cumplido. */
	@Test
	void edadEnAniosCumplidosComoElNavegador() {
		assertEquals(10, TurnoPublicoDTO.edad(LocalDate.of(2016, 10, 17), HOY)); // cumple hoy
		assertEquals(9, TurnoPublicoDTO.edad(LocalDate.of(2016, 10, 18), HOY)); // cumple mañana
		assertEquals(10, TurnoPublicoDTO.edad(LocalDate.of(2016, 10, 16), HOY));
		assertEquals(9, TurnoPublicoDTO.edad(LocalDate.of(2016, 11, 1), HOY)); // mes posterior
		assertEquals(10, TurnoPublicoDTO.edad(LocalDate.of(2016, 9, 30), HOY)); // mes anterior
		assertEquals(0, TurnoPublicoDTO.edad(LocalDate.of(2026, 10, 17), HOY));
		// 29 de febrero: en año no bisiesto no cumple hasta el 1 de marzo.
		assertEquals(12, TurnoPublicoDTO.edad(LocalDate.of(2012, 2, 29), LocalDate.of(2025, 2, 28)));
		assertEquals(13, TurnoPublicoDTO.edad(LocalDate.of(2012, 2, 29), LocalDate.of(2025, 3, 1)));
	}

	/** Hibernate devuelve java.sql.Date para @Temporal(DATE); la fecha es la de la base de datos. */
	@Test
	void leeLaFechaDeNacimientoComoLaSerializabaJackson() {
		assertEquals(LocalDate.of(2010, 5, 3), TurnoPublicoDTO.fechaLocal(java.sql.Date.valueOf("2010-05-03")));
		java.util.Date medianocheMadrid = java.util.Date.from(LocalDate.of(2010, 5, 3)
				.atStartOfDay(java.time.ZoneId.of("Europe/Madrid")).toInstant());
		assertEquals(LocalDate.of(2010, 5, 3), TurnoPublicoDTO.fechaLocal(medianocheMadrid));
	}

	@Test
	void alumnoSinFechaDeNacimientoNoCuenta() {
		Turno turno = turno(grupo("TAEKWONDO"));
		Alumno sinFecha = alumno(LocalDate.of(2000, 1, 1), true, "TAEKWONDO", true);
		sinFecha.setFechaNacimiento(null);
		turno.getAlumnos().add(sinFecha);
		turno.getAlumnos().add(alumno(LocalDate.of(2016, 1, 10), true, "TAEKWONDO", true));

		TurnoPublicoDTO dto = TurnoPublicoDTO.deTurno(turno, HOY);

		assertEquals(10, dto.getEdadMinAlumnos());
		assertEquals(10, dto.getEdadMaxAlumnos());
	}

	private static Grupo grupo(String deporte) {
		Grupo grupo = new Grupo();
		grupo.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(deporte));
		grupo.setTurnos(new ArrayList<>());
		return grupo;
	}

	private static Turno turno(Grupo grupo) {
		Turno turno = new Turno("Lunes", "18:00", "19:00", grupo);
		turno.setAlumnos(new ArrayList<>());
		return turno;
	}

	private static Alumno alumno(LocalDate nacimiento, boolean activo, String deporte, boolean deporteActivo) {
		Alumno alumno = new Alumno();
		alumno.setActivo(activo);
		alumno.setFechaNacimiento(java.sql.Date.valueOf(nacimiento));
		List<AlumnoDeporte> deportes = new ArrayList<>();
		if (deporte != null) {
			AlumnoDeporte ad = new AlumnoDeporte();
			ad.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(deporte));
			ad.setActivo(deporteActivo);
			deportes.add(ad);
		}
		alumno.setDeportes(deportes);
		return alumno;
	}
}
