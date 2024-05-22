package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Implementación del servicio de usuario que proporciona operaciones
 * relacionadas con usuarios.
 */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

	/**
	 * Inyección del repositorio de usuario.
	 */
	@Autowired
	private UsuarioRepository usuarioRepository;
	
	/**
	 * Crea un nuevo objeto UsuarioServiceImpl (Es para las pruebas).
	 */
	public UsuarioServiceImpl(UsuarioRepository usuarioRepository2) {

	}

	/**
	 * Retorna un objeto UserDetailsService que carga los detalles de usuario por
	 * nombre de usuario.
	 *
	 * @return Un objeto UserDetailsService para cargar los detalles de usuario.
	 */
	@Override
	public UserDetailsService userDetailsService() {
	    return new UserDetailsService() {
	        @Override
	        public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
	            Usuario usuario = usuarioRepository.findByEmail(email)
	                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	            // Aquí devolvemos la instancia de Usuario directamente
	            return usuario;
	        }
	    };
	}

	/**
	 * Obtiene todos los usuarios y los convierte en una lista de objetos
	 * UsuarioDTO.
	 *
	 * @return Una lista de todos los usuarios convertidos en objetos UsuarioDTO.
	 */
	@Override
	public List<UsuarioDTO> obtenerTodos() {
		List<UsuarioDTO> usuarios = usuarioRepository.findAll().stream()
				.map(usuario -> new UsuarioDTO(usuario.getNombre(), usuario.getApellidos(), usuario.getEmail(),
						usuario.getRoles().toString()))
				.collect(Collectors.toList());
		return usuarios;
	}
}
