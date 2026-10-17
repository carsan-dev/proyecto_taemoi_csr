package com.taemoi.project.repositorios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.taemoi.project.repositories.ProductoAlumnoRepository;

/**
 * Entrega 6-3 (entrega6-plan.md §4.3, riesgo «consultas gemelas»): las consultas de la tesorería llevan TODAS el mismo
 * filtro de sede, y las de totales por sede comparten el {@code FROM ... WHERE} de las de totales. Un filtro olvidado
 * en una sola daría totales distintos entre pantallas. Las cifras se comprueban contra MySQL en
 * {@code MoiskimdoTesoreriaIdenticaIntegrationTest} y {@code ResponsableSedeTesoreriaIntegrationTest}.
 */
class ProductoAlumnoRepositoryConsultasTest {

	private static final String FILTRO_SEDE = "AND (:todasLasSedes = true OR pa.sedeId IN :sedes)";

	private static final List<String> CON_FILTRO = List.of("findMovimientosTesoreriaPaginados",
			"findMovimientosTesoreriaFiltrados", "findMovimientosTesoreriaPeriodoBase", "contarMovimientosTesoreria",
			"sumarImporteMovimientosTesoreria", "contarAlumnosConPendientesTesoreria",
			"resumirMovimientosTesoreriaPorSede", "contarAlumnosConPendientesTesoreriaPorSede");

	@Test
	void todasLasConsultasDeTesoreriaFiltranPorSedeUnaSolaVez() {
		for (String nombre : CON_FILTRO) {
			Query query = query(nombre);
			assertEquals(1, ocurrencias(query.value(), FILTRO_SEDE), nombre);
			if (!query.countQuery().isEmpty()) {
				assertEquals(1, ocurrencias(query.countQuery(), FILTRO_SEDE), nombre + " (countQuery)");
			}
		}
	}

	@Test
	void totalesPorSede_mismoFromYWhereQueLosTotales() {
		String contar = desdeFrom(query("contarMovimientosTesoreria").value());
		assertEquals(contar, desdeFrom(query("sumarImporteMovimientosTesoreria").value()));
		assertEquals(contar + " GROUP BY pa.sedeId", desdeFrom(query("resumirMovimientosTesoreriaPorSede").value()));
		assertEquals(desdeFrom(query("contarAlumnosConPendientesTesoreria").value()) + " GROUP BY pa.sedeId",
				desdeFrom(query("contarAlumnosConPendientesTesoreriaPorSede").value()));
	}

	@Test
	void listadoYRecuentoPaginados_mismoWhere() {
		Query paginados = query("findMovimientosTesoreriaPaginados");
		String where = paginados.value().substring(paginados.value().indexOf("WHERE "),
				paginados.value().indexOf(" ORDER BY"));
		assertTrue(paginados.countQuery().endsWith(where), "la countQuery cuenta lo mismo que la página");
		assertTrue(query("findMovimientosTesoreriaFiltrados").value().contains(where));
		assertTrue(query("findMovimientosTesoreriaPeriodoBase").value().contains(where));
	}

	private static String desdeFrom(String jpql) {
		return jpql.substring(jpql.indexOf("FROM ProductoAlumno pa")).trim();
	}

	private static int ocurrencias(String texto, String buscado) {
		int n = 0;
		for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + 1)) {
			n++;
		}
		return n;
	}

	private static Query query(String metodo) {
		for (Method m : ProductoAlumnoRepository.class.getDeclaredMethods()) {
			if (m.getName().equals(metodo)) {
				return m.getAnnotation(Query.class);
			}
		}
		throw new AssertionError("No existe " + metodo);
	}
}
