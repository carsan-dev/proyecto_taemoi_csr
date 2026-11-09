package com.taemoi.project.marca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.response.EscuelaPublicaDTO;
import com.taemoi.project.dtos.response.MarcaPanelDTO;
import com.taemoi.project.entities.Mascota;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.ImagenService;

/**
 * V32_7: la mascota del panel sale de la escuela ACTIVA ({@code organizacion.mascota}). Moiskimdo (organización 1)
 * lleva MAKI; cualquier otra, TAMI, también una escuela nueva sin tocar el campo. Se serializa como texto y va al final
 * de la marca; la marca pública no la lleva. Sin base de datos: la migración la prueba
 * {@code MascotaV32_7MigracionIntegrationTest}.
 */
class MarcaPanelServiceMascotaTest {

	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private MarcaPanelService servicio;

	@BeforeEach
	void preparar() {
		servicio = new MarcaPanelService(organizaciones, mock(ImagenRepository.class), mock(ImagenService.class),
				mock(AuditoriaService.class), mock(TransaccionEnEscuela.class));
	}

	@Test
	void unaEscuelaNueva_llevaTamiPorDefecto() {
		assertEquals(Mascota.TAMI, new Organizacion().getMascota());
	}

	@Test
	void moiskimdo_maki() {
		Organizacion moiskimdo = escuela(1L, "Moi's Kim Do", Mascota.MAKI);
		moiskimdo.setComportamientoLegadoTaemoi(Boolean.TRUE);
		MarcaPanelDTO marca = TenantContext.calcularCon(1L, servicio::marcaActiva);
		assertEquals(Mascota.MAKI, marca.mascota());
		assertEquals(MarcaPanelDTO.Logo.LEGADO, marca.logo());
	}

	@Test
	void otraEscuela_tami() {
		escuela(7L, "Escuela Ficticia", null);
		MarcaPanelDTO marca = TenantContext.calcularCon(7L, servicio::marcaActiva);
		assertEquals(Mascota.TAMI, marca.mascota());
		assertEquals("Escuela Ficticia", marca.nombre());
	}

	@Test
	void json_mascotaComoTexto_alFinal_yLaMarcaPublicaNoLaLleva() throws Exception {
		escuela(1L, "Moi's Kim Do", Mascota.MAKI);
		MarcaPanelDTO marca = TenantContext.calcularCon(1L, servicio::marcaActiva);
		ObjectMapper json = new ObjectMapper();
		JsonNode nodo = json.valueToTree(marca);
		assertEquals("MAKI", nodo.get("mascota").asText());
		List<String> campos = new java.util.ArrayList<>();
		nodo.fieldNames().forEachRemaining(campos::add);
		assertEquals(List.of("nombre", "logo", "logoVersion", "tema", "mascota"), campos);

		JsonNode publica = json.valueToTree(EscuelaPublicaDTO.de(marca));
		List<String> camposPublicos = new java.util.ArrayList<>();
		publica.fieldNames().forEachRemaining(camposPublicos::add);
		assertEquals(List.of("nombre", "logo", "logoVersion", "tema"), camposPublicos);
	}

	private Organizacion escuela(long id, String nombre, Mascota mascota) {
		Organizacion escuela = new Organizacion();
		ReflectionTestUtils.setField(escuela, "id", id);
		escuela.setSlug("escuela-" + id);
		escuela.setNombre(nombre);
		if (mascota != null) {
			escuela.setMascota(mascota);
		}
		when(organizaciones.findById(id)).thenReturn(Optional.of(escuela));
		return escuela;
	}
}
