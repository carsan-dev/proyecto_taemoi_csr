package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.taemoi.project.legado.Deporte;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.GrupoRepository;

/**
 * Smoke test: el contexto completo arranca contra MySQL real, Flyway aplica V1..V19
 * sobre una base vacía, Hibernate valida el esquema y un repositorio hace ida y vuelta.
 */
@SpringBootTest
class MySqlSmokeIntegrationTest extends MySqlIntegrationTest {

	@Autowired Flyway flyway;
	@Autowired GrupoRepository grupos;
	@Autowired com.taemoi.project.repositories.DisciplinaRepository disciplinas;
	@Autowired JdbcTemplate jdbc;

	@Test
	void flywayDejaLaBaseEnLaUltimaVersionSinMigracionesPendientes() {
		MigrationInfo actual = flyway.info().current();

		assertNotNull(actual);
		assertEquals("29", actual.getVersion().getVersion());
		assertEquals(0, flyway.info().pending().length);
	}

	@Test
	void usaMySqlReal() {
		String version = jdbc.queryForObject("SELECT VERSION()", String.class);

		assertTrue(version.startsWith("8.0"), "Versión MySQL inesperada: " + version);
	}

	@Test
	void repositorioGuardaYLeeUnGrupo() {
		// Fuera de una petición no hay organización: el test la fija explícitamente (fallo cerrado).
		TenantContext.ejecutarCon(1L, this::guardarYLeerGrupo);
	}

	private void guardarYLeerGrupo() {
		Grupo grupo = new Grupo();
		grupo.setNombre("IT smoke grupo");
		grupo.setTipo("Pilates");
		grupo.setDisciplina(disciplinas.findByCodigo("PILATES").orElseThrow());

		Long id = grupos.save(grupo).getId();
		try {
			Grupo leido = grupos.findById(id).orElseThrow();
			assertEquals("IT smoke grupo", leido.getNombre());
			assertEquals(Deporte.PILATES.name(), leido.getDeporte());
		} finally {
			grupos.deleteById(id);
		}
	}
}
