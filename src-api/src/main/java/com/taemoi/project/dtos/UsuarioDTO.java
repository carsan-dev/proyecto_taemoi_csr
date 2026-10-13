package com.taemoi.project.dtos;

import java.util.EnumSet;
import java.util.Set;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;

public class UsuarioDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private String email;
	private String rol;
	private String authProvider;

	public UsuarioDTO() {

	}

	public UsuarioDTO(Long id, String nombre, String apellidos, String email, String rol) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.email = email;
		this.rol = rol;
	}

	public UsuarioDTO(Long id, String nombre, String apellidos, String email, String rol, String authProvider) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.email = email;
		this.rol = rol;
		this.authProvider = authProvider;
	}

	public UsuarioDTO(String nombre, String apellidos, String email, String rol) {
		super();
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.email = email;
		this.rol = rol;
	}

	/**
	 * Convierte una entidad Usuario a UsuarioDTO.
	 *
	 * @param usuario La entidad Usuario a convertir.
	 * @param roles   autoridades del usuario en la organización activa (de sus membresías); el
	 *                campo {@code rol} mantiene el formato de siempre, p. ej. {@code [ROLE_USER, ROLE_ADMIN]}.
	 * @return Un objeto UsuarioDTO.
	 */
	public static UsuarioDTO deUsuario(Usuario usuario, Set<Roles> roles) {
		if (usuario == null) {
			return null;
		}
		Set<Roles> ordenados = roles == null || roles.isEmpty() ? EnumSet.noneOf(Roles.class) : EnumSet.copyOf(roles);
		return new UsuarioDTO(usuario.getId(), usuario.getNombre(), usuario.getApellidos(), usuario.getEmail(),
				ordenados.toString(), usuario.getAuthProvider().name());
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getRol() {
		return rol;
	}

	public void setRol(String rol) {
		this.rol = rol;
	}

	public String getAuthProvider() {
		return authProvider;
	}

	public void setAuthProvider(String authProvider) {
		this.authProvider = authProvider;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}
}
