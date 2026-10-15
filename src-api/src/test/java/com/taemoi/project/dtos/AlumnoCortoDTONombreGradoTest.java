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
 * El nombre del grado en los listados cortos es siempre el del catálogo ({@code grado.nombre}), también en
 * Taemoi (decisión D3; V30 escribe los nombres «BLANCO-AMARILLO», «ROJO-NEGRO 1º PUM», «MARRÓN» en kickboxing).
 */
class AlumnoCortoDTONombreGradoTest {

	private final CatalogoTaemoiEnMemoria catalogo = new CatalogoTaemoiEnMemoria();

	@Test
	void kickboxingRojoMuestraMarron() {
		Alumno alumno = alumnoCon("KICKBOXING", "ROJO");

		assertEquals("MARRÓN", AlumnoCortoDTO.deAlumno(alumno).getGrado());
		assertEquals("MARRÓN", AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0)).getGrado());
	}

	@Test
	void taekwondoMuestraElNombreDelCatalogo() {
		assertEquals("ROJO", AlumnoCortoDTO.deAlumno(alumnoCon("TAEKWONDO", "ROJO")).getGrado());
		assertEquals("AZUL-ROJO", AlumnoCortoDTO.deAlumno(alumnoCon("TAEKWONDO", "AZUL_ROJO")).getGrado());
		Alumno pum = alumnoCon("TAEKWONDO", "ROJO_NEGRO_1_PUM");
		assertEquals("ROJO-NEGRO 1º PUM", AlumnoCortoDTO.deAlumnoDeporte(pum.getDeportes().get(0)).getGrado());
		assertEquals("NEGRO 2º DAN", AlumnoCortoDTO.deAlumno(alumnoCon("TAEKWONDO", "NEGRO_2_DAN")).getGrado());
	}

	@Test
	void otraEscuelaMuestraSuNombreTalCual() {
		Alumno alumno = alumnoCon("TAEKWONDO", "ROJO");
		alumno.getDeportes().get(0).getGrado().setNombre("Cinturón rojo");

		assertEquals("Cinturón rojo", AlumnoCortoDTO.deAlumno(alumno).getGrado());
		assertEquals("Cinturón rojo", AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0)).getGrado());
	}

	@Test
	void sinGradoNoHayNombre() {
		Alumno alumno = alumnoCon("TAEKWONDO", null);

		assertNull(AlumnoCortoDTO.deAlumno(alumno).getGrado());
		assertNull(AlumnoCortoDTO.deAlumnoDeporte(alumno.getDeportes().get(0)).getGrado());
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
