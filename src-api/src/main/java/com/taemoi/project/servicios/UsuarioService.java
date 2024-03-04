package com.taemoi.project.servicios;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetailsService;
import com.taemoi.project.dtos.UsuarioDTO;

public interface UsuarioService {
	UserDetailsService userDetailsService();

	List<UsuarioDTO> obtenerTodos();
}
