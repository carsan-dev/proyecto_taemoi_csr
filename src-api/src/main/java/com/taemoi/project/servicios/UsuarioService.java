package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetailsService;
import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.entidades.Usuario;

public interface UsuarioService {
	UserDetailsService userDetailsService();

	List<UsuarioDTO> obtenerTodos();
	
	Optional<Usuario> encontrarPorEmail(String email);
}
