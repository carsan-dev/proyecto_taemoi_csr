package com.taemoi.project.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Date;

import org.junit.jupiter.api.Test;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.dtos.response.AlumnoCortoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;

/**
 * El nombre del grado en los listados cortos solo es el heredado de Taemoi cuando la organización conserva
 * ese comportamiento; las demás escuelas muestran el nombre de su catálogo (en kickboxing, ROJO = «MARRÓN»).
 */
class AlumnoCortoDTONombreGradoTest {

	private final CatalogoTaemoiEnMemoria catalogo = new CatalogoTaemoiEnMemoria();

	@Test
	void organizacionLegadaMuestraElNombreHeredadoTambienEnKickboxing() {
		Alumno alumno = alumnoCon("KICKBOXING", "ROJO");

		assertEquals("Rojo", AlumnoCortoDTO.deAlumno(alumno, true).getGrado());
		assertEquals("Rojo", AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0), true).getGrado());
	}

	@Test
	void organizacionSinMarcaLegadaMuestraElNombreDelCatalogo() {
		Alumno alumno = alumnoCon("KICKBOXING", "ROJO");

		assertEquals("MARRÓN", AlumnoCortoDTO.deAlumno(alumno, false).getGrado());
		assertEquals("MARRÓN", AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0), false).getGrado());
	}

	@Test
	void sinGradoNoHayNombreEnNingunModo() {
		Alumno alumno = alumnoCon("TAEKWONDO", null);

		assertNull(AlumnoCortoDTO.deAlumno(alumno, true).getGrado());
		assertNull(AlumnoCortoDTO.deAlumno(alumno, false).getGrado());
		assertNull(AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0), true).getGrado());
		assertNull(AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0), false).getGrado());
	}

	private Alumno alumnoCon(String disciplina, String codigoGrado) {
		Alumno alumno = new Alumno();
		alumno.setId(1L);
		alumno.setNombre("Ana");
		alumno.setApellidos("Prueba");
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDisciplina(CatalogoTaemoiEnMemoria.disciplinaTaemoi(disciplina));
		ad.setActivo(true);
		ad.setFechaAlta(Date.valueOf("2022-09-01"));
		ad.setFechaAltaInicial(Date.valueOf("2022-09-01"));
		if (codigoGrado != null) {
			ad.setGrado(catalogo.grado(disciplina, codigoGrado));
		}
		alumno.addDeporte(ad);
		return alumno;
	}
}
