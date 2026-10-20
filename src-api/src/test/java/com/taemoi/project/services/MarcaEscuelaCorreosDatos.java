package com.taemoi.project.services;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.legado.Deporte;

/** Pieza E7-7a: la solicitud ficticia (sin fechas variables) de los dorados de correos de {@code dorado/marca}. */
final class MarcaEscuelaCorreosDatos {

	private MarcaEscuelaCorreosDatos() {
	}

	static Preinscripcion preinscripcion() {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-2026-MARCA");
		p.setEstado(EstadoPreinscripcion.PENDIENTE);
		p.setTemporada("2026-2027");
		p.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		p.setNombre("María");
		p.setApellidos("Prueba Ficticia");
		p.setFechaNacimiento(LocalDate.of(2015, 1, 1));
		p.setEmail("familia@example.test");
		p.setGrupoSnapshot("Taekwondo infantil · Lunes 18:00-19:00");
		p.setObservaciones("Llega tarde los lunes & <pregunta> \"si\" 'puede'");
		p.setEmailCambioTurnosAnteriorSnapshot("Lunes 18:00-19:00");
		p.setEmailCambioTurnosNuevoSnapshot("Miércoles 18:00-19:00");
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setInstrucciones("Trae ropa cómoda.");
		plantilla.setContenido("{}");
		p.setPlantilla(plantilla);
		Grupo grupo = new Grupo();
		grupo.setId(7L);
		grupo.setNombre("Taekwondo infantil");
		Turno lunes = turno(11L, "Lunes", grupo);
		Turno miercoles = turno(12L, "Miércoles", grupo);
		p.setGrupo(grupo);
		p.setTurnos(new LinkedHashSet<>(List.of(lunes, miercoles)));
		Alumno alumno = new Alumno();
		alumno.setEmail("familia@example.test");
		p.setAlumno(alumno);
		return p;
	}

	private static Turno turno(Long id, String dia, Grupo grupo) {
		Turno turno = new Turno();
		turno.setId(id);
		turno.setDiaSemana(dia);
		turno.setHoraInicio("18:00");
		turno.setHoraFin("19:00");
		turno.setGrupo(grupo);
		return turno;
	}
}
