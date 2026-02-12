package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.impl.ProductoAlumnoServiceImpl;

@ExtendWith(MockitoExtension.class)
class ProductoAlumnoServiceImplTest {

	@Mock
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Mock
	private ProductoRepository productoRepository;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Mock
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Mock
	private AlumnoDeporteService alumnoDeporteService;

	@InjectMocks
	private ProductoAlumnoServiceImpl productoAlumnoService;

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void actualizarProductoAlumno_managerNoPuedeRevertirPagado() {
		ProductoAlumno productoAlumno = crearProductoPagado();
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setPagado(false);
		cambios.setMotivoCambio("error");

		when(productoAlumnoRepository.findById(1L)).thenReturn(Optional.of(productoAlumno));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"manager@test.com",
				null,
				java.util.List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> productoAlumnoService.actualizarProductoAlumno(1L, cambios));

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		verify(productoAlumnoRepository, never()).save(any(ProductoAlumno.class));
	}

	@Test
	void actualizarProductoAlumno_adminRevertirLimpiaFechaPago() {
		ProductoAlumno productoAlumno = crearProductoPagado();
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setPagado(false);
		cambios.setMotivoCambio("reversion");

		when(productoAlumnoRepository.findById(1L)).thenReturn(Optional.of(productoAlumno));
		when(productoAlumnoRepository.save(any(ProductoAlumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(alumnoConvocatoriaRepository.findByProductoAlumnoId(1L)).thenReturn(Optional.empty());
		when(alumnoRepository.save(any(Alumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin@test.com",
				null,
				java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		ProductoAlumnoDTO resultado = productoAlumnoService.actualizarProductoAlumno(1L, cambios);

		assertFalse(resultado.getPagado());
		assertNull(resultado.getFechaPago());
		verify(productoAlumnoRepository).save(any(ProductoAlumno.class));
	}

	private ProductoAlumno crearProductoPagado() {
		Alumno alumno = new Alumno();
		alumno.setId(10L);
		alumno.setNombre("Alumno");
		alumno.setApellidos("Test");
		alumno.setTieneDerechoExamen(true);

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setId(1L);
		productoAlumno.setAlumno(alumno);
		productoAlumno.setConcepto("MENSUALIDAD ENERO 2026");
		productoAlumno.setPagado(true);
		productoAlumno.setFechaPago(new Date());
		productoAlumno.setPrecio(35.0);
		productoAlumno.setCantidad(1);
		return productoAlumno;
	}
}
