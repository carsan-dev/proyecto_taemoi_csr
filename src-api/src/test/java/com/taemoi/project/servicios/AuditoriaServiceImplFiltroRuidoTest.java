package com.taemoi.project.servicios;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.entities.AuditoriaEvento;
import com.taemoi.project.repositories.AuditoriaEventoRepository;
import com.taemoi.project.services.UsuarioService;
import com.taemoi.project.services.VistaAuditoria;
import com.taemoi.project.services.impl.AuditoriaServiceImpl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/**
 * Forma de la consulta de «Eventos de auditoría»: el filtro de ruido de escáner (≈19 {@code LIKE} sobre el
 * endpoint, que obliga a recorrer la tabla en el COUNT) solo puede descartar filas con estado 401/403/404/405/415.
 * Si el filtro de resultado ya deja solo 2xx (EXITO), no debe aplicarse; con ERROR o sin filtro, sí.
 * La equivalencia de filas y totales contra MySQL está en {@code AuditoriaEventosConsultaIntegrationTest}.
 */
class AuditoriaServiceImplFiltroRuidoTest {

	private AuditoriaEventoRepository repositorio;
	private AuditoriaServiceImpl servicio;

	@BeforeEach
	void setUp() {
		repositorio = mock(AuditoriaEventoRepository.class);
		servicio = new AuditoriaServiceImpl(repositorio, mock(UsuarioService.class));
		when(repositorio.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
	}

	@Test
	void exitoSinRuidoNoAplicaElFiltroDeRuido() {
		CriteriaBuilder cb = evaluar("EXITO", false);

		verify(cb).between(any(Expression.class), eq(200), eq(299));
		verify(cb, never()).like(any(Expression.class), anyString());
	}

	@Test
	void exitoMinusculasConEspaciosTampocoLoAplica() {
		CriteriaBuilder cb = evaluar(" exito ", null);

		verify(cb).between(any(Expression.class), eq(200), eq(299));
		verify(cb, never()).like(any(Expression.class), anyString());
	}

	@Test
	void errorSinRuidoSigueAplicandoElFiltroDeRuido() {
		CriteriaBuilder cb = evaluar("ERROR", false);

		verify(cb, atLeastOnce()).like(any(Expression.class), anyString());
	}

	@Test
	void sinFiltroDeResultadoSigueAplicandoElFiltroDeRuido() {
		verify(evaluar(null, false), atLeastOnce()).like(any(Expression.class), anyString());
		verify(evaluar("TODOS", false), atLeastOnce()).like(any(Expression.class), anyString());
	}

	@Test
	void incluirRuidoNuncaAplicaElFiltro() {
		verify(evaluar("ERROR", true), never()).like(any(Expression.class), anyString());
		verify(evaluar(null, true), never()).like(any(Expression.class), anyString());
	}

	/** Llama al servicio, captura la Specification y la evalúa contra un CriteriaBuilder simulado. */
	@SuppressWarnings("unchecked")
	private CriteriaBuilder evaluar(String resultado, Boolean incluirRuido) {
		org.mockito.Mockito.clearInvocations(repositorio);
		servicio.obtenerEventos(VistaAuditoria.COMPLETA, null, null, resultado, null, null, null, null, null, 1, 25, incluirRuido);

		ArgumentCaptor<Specification<AuditoriaEvento>> captor = ArgumentCaptor.forClass(Specification.class);
		verify(repositorio).findAll(captor.capture(), any(Pageable.class));

		Root<AuditoriaEvento> root = mock(Root.class, RETURNS_DEEP_STUBS);
		CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
		CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		captor.getValue().toPredicate(root, query, cb);
		return cb;
	}
}
