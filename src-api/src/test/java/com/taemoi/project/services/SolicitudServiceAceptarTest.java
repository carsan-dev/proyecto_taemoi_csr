package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.response.SolicitudDTOs;
import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.Solicitud;
import com.taemoi.project.entities.TipoSolicitud;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.EntregaCajaRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SolicitudRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * Pieza 6c-3: la red de {@link SolicitudService#aceptar}. Si la ejecución lanza un error de negocio pese a la
 * revalidación, la transacción se deshace y la solicitud se cierra NO_APLICABLE con ese mensaje en otra (no se queda
 * PENDIENTE ni da 409). La transición sigue siendo la única puerta: una solicitud ya resuelta da 409 sin ejecutar nada.
 */
class SolicitudServiceAceptarTest {

	private static final long PROPIETARIO = 5L;

	private SolicitudRepository solicitudes;
	private ProductoAlumnoService productosAlumno;
	private RegistroAccionesSolicitudes registro;
	private PlatformTransactionManager transacciones;
	private SolicitudService servicio;
	private Solicitud solicitud;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void preparar() {
		solicitudes = mock(SolicitudRepository.class);
		productosAlumno = mock(ProductoAlumnoService.class);
		registro = mock(RegistroAccionesSolicitudes.class);
		transacciones = mock(PlatformTransactionManager.class);
		when(transacciones.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
		servicio = new SolicitudService(solicitudes, mock(CobroRepository.class), mock(ProductoAlumnoRepository.class),
				mock(EntregaCajaRepository.class), mock(MovimientoCajaRepository.class), mock(GestionPorSede.class),
				registro, productosAlumno, mock(CajaService.class), mock(CobroService.class), transacciones);
		EntityManager em = mock(EntityManager.class);
		TypedQuery<Object[]> consulta = mock(TypedQuery.class);
		when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(consulta);
		when(consulta.setParameter(anyString(), any())).thenReturn(consulta);
		when(consulta.getResultList()).thenReturn(List.of());
		ReflectionTestUtils.setField(servicio, "em", em);

		solicitud = new Solicitud();
		ReflectionTestUtils.setField(solicitud, "id", 7L);
		solicitud.setTipo(TipoSolicitud.REVERTIR_COBRO);
		solicitud.setEstado(EstadoSolicitud.PENDIENTE);
		solicitud.setSolicitadoPorUsuarioId(9L);
		solicitud.setSolicitadoEn(LocalDateTime.now());
		solicitud.setMotivo("motivo");
		solicitud.setCobroId(11L);
		solicitud.setProductoAlumnoId(12L);
		when(solicitudes.bloquearPorId(7L)).thenReturn(Optional.of(solicitud));

		Usuario propietario = new Usuario();
		propietario.setId(PROPIETARIO);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(propietario, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void ejecucionQueLanza_seDeshaceYQuedaNoAplicableConElMensaje() {
		when(productosAlumno.revertirPorSolicitud(12L, 11L, 7L, "motivo"))
				.thenThrow(new IllegalStateException("El cargo está cancelado."));

		SolicitudDTOs.Solicitud r = servicio.aceptar(7L);

		assertEquals(EstadoSolicitud.NO_APLICABLE, r.estado());
		assertEquals("El cargo está cancelado.", r.respuesta());
		assertEquals(PROPIETARIO, solicitud.getResueltoPorUsuarioId());
		verify(transacciones, times(1)).rollback(any());
		verify(transacciones, times(1)).commit(any());
		verify(registro).registrar(eq(RegistroAccionesSolicitudes.Accion.SOLICITUD_NO_APLICABLE), anyString(),
				eq(solicitud));
		verify(registro, never()).registrar(eq(RegistroAccionesSolicitudes.Accion.SOLICITUD_ACEPTADA), anyString(),
				any(Solicitud.class));
		verify(productosAlumno, never()).aplicarEfectosTrasSolicitud(any());
	}

	@Test
	void yaResuelta_409SinEjecutarNiRegistrar() {
		solicitud.setEstado(EstadoSolicitud.ACEPTADA);

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> servicio.aceptar(7L));

		assertEquals(SolicitudTransiciones.NO_PENDIENTE, error.getMessage());
		verify(productosAlumno, never()).revertirPorSolicitud(any(), any(), anyLong(), any());
		verify(registro, never()).registrar(any(), anyString(), any(Solicitud.class));
	}
}
