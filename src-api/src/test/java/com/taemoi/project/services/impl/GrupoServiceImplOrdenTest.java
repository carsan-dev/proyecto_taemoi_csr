package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoCortoDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;

/**
 * D2 (R2-P1): el servidor ordena, al construir los DTO, los alumnos de un grupo por nombre y apellidos y los grupos
 * de un alumno por nombre (sin distinguir mayúsculas ni tildes; desempate por id), sea cual sea el orden de la
 * colección de la entidad (un {@code Set} sin orden).
 */
@ExtendWith(MockitoExtension.class)
class GrupoServiceImplOrdenTest {

	@Mock GrupoRepository grupoRepository;
	@Mock AlumnoRepository alumnoRepository;
	@InjectMocks GrupoServiceImpl service;

	@Test
	void alumnosDeUnGrupoPorNombreYApellidos() {
		Grupo grupo = grupo(1L, "Taekwondo");
		// Orden de inserción (el de la colección) deliberadamente distinto del esperado.
		grupo.setAlumnos(List.of(alumno(10L, "Zoe", "Abad"), alumno(11L, "ana", "Ruiz"), alumno(12L, "Álvaro", "Pérez"),
				alumno(13L, "Ana", "Abad"), alumno(9L, "ANA", "ruiz"), alumno(14L, null, "Abad")));

		List<Long> ids = service.convertirEntidadADTO(grupo).getAlumnos().stream().map(AlumnoCortoDTO::getId).toList();

		assertEquals(List.of(12L, 13L, 9L, 11L, 10L, 14L), ids);
	}

	@Test
	void gruposDeUnAlumnoPorNombre() {
		Alumno alumno = alumno(5L, "Ana", "Abad");
		alumno.setGrupos(List.of(grupo(3L, "Taekwondo Infantil"), grupo(1L, "kickboxing"), grupo(2L, "Ábaco"),
				grupo(4L, "Kickboxing")));
		when(alumnoRepository.findById(5L)).thenReturn(Optional.of(alumno));

		List<Long> ids = service.obtenerGruposDelAlumno(5L).stream().map(GrupoResponseDTO::getId).toList();

		assertEquals(List.of(2L, 1L, 4L, 3L), ids);
	}

	@Test
	void gruposDeAlumnoConGruposDTOPorNombre() {
		Alumno alumno = alumno(5L, "Ana", "Abad");
		alumno.setGrupos(List.of(grupo(3L, "Taekwondo Infantil"), grupo(1L, "kickboxing"), grupo(2L, "Ábaco"),
				grupo(4L, "Kickboxing")));

		assertEquals(List.of(2L, 1L, 4L, 3L), ids(AlumnoConGruposDTO.deAlumnoConGrupos(alumno)));
		assertEquals(List.of(2L, 1L, 4L, 3L), ids(AlumnoConGruposDTO.deAlumnoConGrupos(alumno, null)));
	}

	private static List<Long> ids(AlumnoConGruposDTO dto) {
		return dto.getGrupos().stream().map(GrupoResponseDTO::getId).toList();
	}

	private static Alumno alumno(Long id, String nombre, String apellidos) {
		Alumno alumno = new Alumno();
		alumno.setId(id);
		alumno.setNombre(nombre);
		alumno.setApellidos(apellidos);
		alumno.setActivo(true);
		return alumno;
	}

	private static Grupo grupo(Long id, String nombre) {
		Grupo grupo = new Grupo();
		grupo.setId(id);
		grupo.setNombre(nombre);
		return grupo;
	}
}
