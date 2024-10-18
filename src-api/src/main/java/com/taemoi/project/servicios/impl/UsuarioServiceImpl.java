package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;
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

/**
 * Implementación del servicio de usuario que proporciona operaciones
 * relacionadas con usuarios y carga de detalles de usuario para autenticación.
 */
@Service
public class UsuarioServiceImpl implements UsuarioService, UserDetailsService {

    /**
     * Inyección del repositorio de usuario.
     */
    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Carga los detalles del usuario por su nombre de usuario (email) para autenticación.
     * Este método es usado por Spring Security para autenticación.
     *
     * @param email El email utilizado como nombre de usuario.
     * @return Un objeto UserDetails (Usuario en este caso).
     * @throws UsernameNotFoundException Si el usuario no se encuentra.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + email));
    }

    /**
     * Obtiene todos los usuarios y los convierte en una lista de objetos
     * UsuarioDTO.
     *
     * @return Una lista de todos los usuarios convertidos en objetos UsuarioDTO.
     */
    @Override
    public List<UsuarioDTO> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(usuario -> new UsuarioDTO(usuario.getNombre(), usuario.getApellidos(), usuario.getEmail(),
                        usuario.getRoles().toString()))
                .collect(Collectors.toList());
    }

    /**
     * Encuentra un usuario por su email.
     *
     * @param email El email del usuario.
     * @return Un Optional que contiene el usuario si existe.
     */
    @Override
    public Optional<Usuario> encontrarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    /**
     * Guarda un nuevo usuario en la base de datos.
     * 
     * @param usuario El objeto Usuario que se va a guardar.
     * @return El usuario guardado.
     */
    @Override
    public Usuario guardarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    /**
     * Verifica si un usuario con el email dado ya existe.
     *
     * @param email El email a verificar.
     * @return true si existe un usuario con ese email, de lo contrario false.
     */
    @Override
    public boolean existePorEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    /**
     * Actualiza un usuario existente en la base de datos.
     *
     * @param usuario El objeto Usuario con los nuevos datos a actualizar.
     * @return El usuario actualizado.
     */
    @Override
    public Usuario actualizarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    /**
     * Elimina un usuario por su ID.
     *
     * @param id El ID del usuario a eliminar.
     */
    @Override
    public void eliminarUsuario(Long id) {
        usuarioRepository.deleteById(id);
    }
}
