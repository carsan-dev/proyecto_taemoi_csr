package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.entidades.Usuario;

public interface UsuarioService extends UserDetailsService {

	List<UsuarioDTO> obtenerTodos();
	
    @Override
    UserDetails loadUserByUsername(String email) throws UsernameNotFoundException;
	
	Optional<Usuario> encontrarPorEmail(String email);

	/**
	 * Elimina un usuario por su ID.
	 *
	 * @param id El ID del usuario a eliminar.
	 */
	void eliminarUsuario(Long id);

	/**
	 * Actualiza un usuario existente en la base de datos.
	 *
	 * @param usuario El objeto Usuario con los nuevos datos a actualizar.
	 * @return El usuario actualizado.
	 */
	Usuario actualizarUsuario(Usuario usuario);

	/**
	 * Verifica si un usuario con el email dado ya existe.
	 *
	 * @param email El email a verificar.
	 * @return true si existe un usuario con ese email, de lo contrario false.
	 */
	boolean existePorEmail(String email);

	/**
	 * Guarda un nuevo usuario en la base de datos.
	 * 
	 * @param usuario El objeto Usuario que se va a guardar.
	 * @return El usuario guardado.
	 */
	Usuario guardarUsuario(Usuario usuario);
}
