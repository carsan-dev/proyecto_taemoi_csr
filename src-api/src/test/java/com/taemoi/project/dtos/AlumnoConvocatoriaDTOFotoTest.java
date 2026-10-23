package com.taemoi.project.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Imagen;

/**
 * La foto de cada alumno en la lista de una convocatoria: la misma URL que el listado de alumnos
 * ({@code /api/alumnos/{id}/imagen}, que comprueba quién puede verla), sin la ruta del disco y sin tocar la entidad.
 */
class AlumnoConvocatoriaDTOFotoTest {

	@Test
	void sinFoto_null() {
		Alumno alumno = new Alumno();
		alumno.setId(7L);

		assertNull(AlumnoConvocatoriaDTO.fotoDe(alumno));
		assertNull(AlumnoConvocatoriaDTO.fotoDe(null));
	}

	@Test
	void conFoto_laRutaDeLaApiSinLaRutaDelDisco() {
		Alumno alumno = alumnoConFoto();

		Imagen foto = AlumnoConvocatoriaDTO.fotoDe(alumno);

		assertEquals(31L, foto.getId());
		assertEquals("foto.jpg", foto.getNombre());
		assertEquals("image/jpeg", foto.getTipo());
		assertEquals("/api/alumnos/7/imagen", foto.getUrl());
		assertNull(foto.getRuta());
	}

	@Test
	void conFoto_noModificaLaImagenDelAlumno() {
		Alumno alumno = alumnoConFoto();

		Imagen foto = AlumnoConvocatoriaDTO.fotoDe(alumno);

		assertNotSame(alumno.getFotoAlumno(), foto);
		assertEquals("/imagenes/org-1/alumnos/foto.jpg", alumno.getFotoAlumno().getUrl());
		assertEquals("org-1/alumnos/foto.jpg", alumno.getFotoAlumno().getRuta());
	}

	@Test
	void json_saleEnLaRespuestaPeroNoSeLeeDeLaPeticion() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		AlumnoConvocatoriaDTO dto = new AlumnoConvocatoriaDTO();
		dto.setFotoAlumno(AlumnoConvocatoriaDTO.fotoDe(alumnoConFoto()));

		JsonNode salida = mapper.readTree(mapper.writeValueAsString(dto));
		assertEquals("/api/alumnos/7/imagen", salida.get("fotoAlumno").get("url").asText());
		assertFalse(salida.get("fotoAlumno").has("organizacionId"));

		AlumnoConvocatoriaDTO entrada = mapper.readValue(
				"{\"cuantiaExamen\":25.0,\"pagado\":true,\"fotoAlumno\":{\"url\":\"/x\"}}", AlumnoConvocatoriaDTO.class);
		assertNull(entrada.getFotoAlumno());
		assertTrue(entrada.getPagado());
	}

	private static Alumno alumnoConFoto() {
		Imagen imagen = new Imagen("foto.jpg", "image/jpeg", "/imagenes/org-1/alumnos/foto.jpg", "org-1/alumnos/foto.jpg");
		imagen.setId(31L);
		Alumno alumno = new Alumno();
		alumno.setId(7L);
		alumno.setFotoAlumno(imagen);
		return alumno;
	}
}
