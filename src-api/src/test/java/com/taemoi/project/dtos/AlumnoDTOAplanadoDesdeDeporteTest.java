package com.taemoi.project.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Date;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.taemoi.project.dtos.response.AlumnoConDeportesDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoCortoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.legado.TipoTarifa;

/**
 * Los campos "planos" por deporte de las respuestas (deporte, grado, tarifa, licencia...) salen
 * del AlumnoDeporte principal; el alumno ya no tiene columnas propias de deporte único.
 */
class AlumnoDTOAplanadoDesdeDeporteTest {

	@Test
	void deAlumnoUsaElDeportePrincipalAunqueNoSeaElMasAntiguo() {
		Alumno alumno = alumnoBase();
		AlumnoDeporte antiguo = deporte(Deporte.TAEKWONDO, TipoGrado.AZUL, TipoTarifa.ADULTO, 40.0, "2018-09-01");
		AlumnoDeporte principal = deporte(Deporte.KICKBOXING, TipoGrado.AMARILLO, TipoTarifa.KICKBOXING, 35.0,
				"2022-09-01");
		principal.setPrincipal(true);
		principal.setRolFamiliar(RolFamiliar.HIJO);
		principal.setGrupoFamiliar("FAM-7");
		principal.setTieneLicencia(true);
		principal.setNumeroLicencia(4321);
		principal.setFechaLicencia(Date.valueOf("2023-01-10"));
		principal.setCompetidor(true);
		principal.setPeso(55.5);
		principal.setFechaPeso(Date.valueOf("2023-02-01"));
		principal.setAptoParaExamen(true);
		alumno.addDeporte(antiguo);
		alumno.addDeporte(principal);

		AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);

		assertEquals(Deporte.KICKBOXING.name(), dto.getDeporte());
		assertEquals(TipoGrado.AMARILLO.name(), dto.getGrado());
		assertEquals(Date.valueOf("2022-09-01"), dto.getFechaGrado());
		assertEquals(TipoTarifa.KICKBOXING.name(), dto.getTipoTarifa());
		assertEquals(35.0, dto.getCuantiaTarifa());
		assertEquals(RolFamiliar.HIJO, dto.getRolFamiliar());
		assertEquals("FAM-7", dto.getGrupoFamiliar());
		assertEquals(Boolean.TRUE, dto.getTieneLicencia());
		assertEquals(4321, dto.getNumeroLicencia());
		assertEquals(Date.valueOf("2023-01-10"), dto.getFechaLicencia());
		assertEquals(Boolean.TRUE, dto.getCompetidor());
		assertEquals(55.5, dto.getPeso());
		assertEquals(Date.valueOf("2023-02-01"), dto.getFechaPeso());
		assertEquals(Boolean.TRUE, dto.getAptoParaExamen());

		AlumnoConDeportesDTO conDeportes = AlumnoConDeportesDTO.deAlumno(alumno);
		assertEquals(TipoTarifa.KICKBOXING.name(), conDeportes.getTipoTarifa());
		assertEquals(35.0, conDeportes.getCuantiaTarifa());
		assertEquals(RolFamiliar.HIJO, conDeportes.getRolFamiliar());
		assertEquals(Boolean.TRUE, conDeportes.getCompetidor());
		assertEquals(4321, conDeportes.getNumeroLicencia());
		assertEquals(2, conDeportes.getDeportes().size());

		assertEquals(TipoGrado.AMARILLO.name(), AlumnoConGruposDTO.deAlumnoConGrupos(alumno).getGrado());
		assertEquals(TipoGrado.AMARILLO.getNombre(), AlumnoCortoDTO.deAlumno(alumno).getGrado());
	}

	@Test
	void sinPrincipalMarcadoUsaElDeporteActivoMasAntiguo() {
		Alumno alumno = alumnoBase();
		alumno.addDeporte(deporte(Deporte.PILATES, null, TipoTarifa.PILATES, 25.0, "2024-01-01"));
		alumno.addDeporte(deporte(Deporte.TAEKWONDO, TipoGrado.VERDE, TipoTarifa.ADULTO, 40.0, "2019-01-01"));

		AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);

		assertEquals(Deporte.TAEKWONDO.name(), dto.getDeporte());
		assertEquals(TipoGrado.VERDE.name(), dto.getGrado());
		assertEquals(40.0, dto.getCuantiaTarifa());
	}

	@Test
	void sinDeportesLosCamposPlanosVanVacios() {
		Alumno alumno = alumnoBase();

		AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);

		assertEquals("Ana", dto.getNombre());
		assertNull(dto.getDeporte());
		assertNull(dto.getGrado());
		assertNull(dto.getTipoTarifa());
		assertNull(dto.getCuantiaTarifa());
		assertNull(dto.getTieneLicencia());
		assertNull(dto.getAptoParaExamen());
		assertNull(AlumnoConDeportesDTO.deAlumno(alumno).getTipoTarifa());
		assertNull(AlumnoCortoDTO.deAlumno(alumno).getGrado());
	}

	@Test
	void deAlumnoPorDeporteUsaEseDeporte() {
		Alumno alumno = alumnoBase();
		AlumnoDeporte taekwondo = deporte(Deporte.TAEKWONDO, TipoGrado.AZUL, TipoTarifa.ADULTO, 40.0, "2018-09-01");
		taekwondo.setPrincipal(true);
		alumno.addDeporte(taekwondo);
		alumno.addDeporte(deporte(Deporte.KICKBOXING, TipoGrado.NARANJA, TipoTarifa.KICKBOXING, 35.0, "2022-09-01"));

		AlumnoDTO dto = AlumnoDTO.deAlumno(alumno, Deporte.KICKBOXING.name());

		assertEquals(Deporte.KICKBOXING.name(), dto.getDeporte());
		assertEquals(TipoGrado.NARANJA.name(), dto.getGrado());
		assertEquals(35.0, dto.getCuantiaTarifa());
	}

	private static Alumno alumnoBase() {
		Alumno alumno = new Alumno();
		alumno.setId(1L);
		alumno.setNombre("Ana");
		alumno.setApellidos("Prueba");
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 1, 1)));
		alumno.setTelefono("600000000");
		return alumno;
	}

	private static AlumnoDeporte deporte(Deporte deporte, TipoGrado tipoGrado, TipoTarifa tarifa, Double cuantia,
			String fechaAlta) {
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(deporte.name()));
		ad.setActivo(true);
		ad.setTarifa(tarifa == null ? null : com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.tarifaTaemoi(tarifa.name()));
		ad.setCuantiaTarifa(cuantia);
		ad.setFechaAlta(Date.valueOf(fechaAlta));
		ad.setFechaAltaInicial(Date.valueOf(fechaAlta));
		if (tipoGrado != null) {
			Grado grado = new Grado();
			grado.setCodigo(tipoGrado.name());
			ad.setGrado(grado);
			ad.setFechaGrado(Date.valueOf(fechaAlta));
		}
		return ad;
	}
}
