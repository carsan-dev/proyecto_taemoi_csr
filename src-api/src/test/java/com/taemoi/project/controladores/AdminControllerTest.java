package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.controllers.AdminController;
import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.exceptions.usuario.ListaUsuariosVaciaException;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.UsuarioService;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private MembresiaService membresiaService;

	@Mock
	private ResolutorOrganizacion resolutorOrganizacion;

	@InjectMocks
	private AdminController adminController;

	@Test
	public void testMostrarUsuarios() {
		List<UsuarioDTO> usuariosMock = new ArrayList<>();
		usuariosMock.add(new UsuarioDTO());
		usuariosMock.add(new UsuarioDTO());
		usuariosMock.add(new UsuarioDTO());

		when(usuarioService.obtenerTodos()).thenReturn(usuariosMock);

		ResponseEntity<List<UsuarioDTO>> responseEntity = adminController.mostrarUsuarios();

		verify(usuarioService, times(1)).obtenerTodos();

		assertEquals(usuariosMock, responseEntity.getBody());
	}

	@Test
	public void testMostrarUsuariosListaVacia() {
		when(usuarioService.obtenerTodos()).thenReturn(new ArrayList<>());

		ListaUsuariosVaciaException exception = assertThrows(ListaUsuariosVaciaException.class, () -> {
			adminController.mostrarUsuarios();
		});

		assertEquals("No hay usuarios registrados en el sistema.", exception.getMessage());
	}

	@Test
	void actualizarRolesConUnRolNuevoSeRechazaAntesDeLlamarAlServicio() {
		for (Roles nuevo : Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_RESPONSABLE_SEDE)) {
			assertThrows(IllegalArgumentException.class,
					() -> adminController.actualizarRolesUsuario(5L, Set.of(Roles.ROLE_USER, nuevo)), nuevo.name());
		}
		verify(usuarioService, never()).encontrarPorId(any());
		verify(membresiaService, never()).reemplazarAutoridades(any(), any(), any());
	}

	@Test
	void actualizarRolesConUnNullSeRechazaAntesDeLlamarAlServicio() {
		Set<Roles> conNull = new java.util.HashSet<>(java.util.Arrays.asList(Roles.ROLE_USER, null));
		assertEquals("La lista de roles contiene un valor no válido.", assertThrows(IllegalArgumentException.class,
				() -> adminController.actualizarRolesUsuario(5L, conNull)).getMessage());
		verify(membresiaService, never()).reemplazarAutoridades(any(), any(), any());
	}
}
