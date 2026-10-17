package com.taemoi.project.acceso;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.organizacion.AlcanceSesion;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Entrega 6 (entrega6-plan.md §2.3): con alcance total, los predicados de los listados del responsable no restringen
 * nada ({@link Specification#unrestricted()}, sin predicado): la consulta de propietario y gestor no cambia.
 */
class PredicadosAlcanceTest {

	@Test
	void deSusSedes_conAlcanceTotal_noAnadenPredicado() {
		assertSinPredicado(PredicadosAlcance.alumnosDeSusSedes(AlcanceSesion.TOTAL));
		assertSinPredicado(PredicadosAlcance.gruposDeSusSedes(AlcanceSesion.TOTAL));
		assertSinPredicado(PredicadosAlcance.turnosDeSusSedes(AlcanceSesion.TOTAL));
	}

	@Test
	void deSusSedes_sinAlcanceOVacio_noDejanPasarNada() {
		CriteriaBuilder cb = mock(CriteriaBuilder.class, invocacion -> mock(jakarta.persistence.criteria.Predicate.class));
		for (AlcanceSesion alcance : new AlcanceSesion[] { null, AlcanceSesion.NINGUNO,
				AlcanceSesion.limitado(Set.of(), Set.of(5L)) }) {
			// Un alcance solo de profesor (grupos) tampoco abre nada: estos listados solo miran sedes.
			assertNotNull(PredicadosAlcance.alumnosDeSusSedes(alcance).toPredicate(raiz(), consulta(), cb));
			assertNotNull(PredicadosAlcance.gruposDeSusSedes(alcance).toPredicate(raiz(), consulta(), cb));
			assertNotNull(PredicadosAlcance.turnosDeSusSedes(alcance).toPredicate(raiz(), consulta(), cb));
		}
	}

	private static <T> void assertSinPredicado(Specification<T> especificacion) {
		assertNull(especificacion.toPredicate(raiz(), consulta(), mock(CriteriaBuilder.class)));
	}

	@SuppressWarnings("unchecked")
	private static <T> Root<T> raiz() {
		return mock(Root.class);
	}

	private static CriteriaQuery<?> consulta() {
		return mock(CriteriaQuery.class);
	}
}
