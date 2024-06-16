package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.errores.usuario.ListaUsuariosVaciaException;
import com.taemoi.project.servicios.UsuarioService;


@SpringBootTest
class AdminControllerTest {

	@Mock
	private UsuarioService usuarioService;

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
}