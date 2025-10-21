package com.taemoi.project.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Usuario;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class UsuarioTest {

	private static Validator validator;

	@BeforeAll
	public static void setUpValidator() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	void testUsuario_EntidadValida() {
		Usuario usuario = new Usuario();
		usuario.setNombre("John");
		usuario.setApellidos("Doe");
		usuario.setEmail("john.doe@example.com");
		usuario.setContrasena("password");

		Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

		assertTrue(violations.isEmpty());
	}

	@Test
	void testUsuario_NombreInvalido() {
		Usuario usuario = new Usuario();
		usuario.setNombre("");
		usuario.setApellidos("Doe");
		usuario.setEmail("john.doe@example.com");
		usuario.setContrasena("password");

		Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

		assertEquals(1, violations.size());
		ConstraintViolation<Usuario> violation = violations.iterator().next();
		assertEquals("El nombre no puede estar en blanco", violation.getMessage());
	}

	@Test
	void testUsuario_ApellidosInvalido() {

		Usuario usuario = new Usuario();
		usuario.setNombre("John");
		usuario.setApellidos("");
		usuario.setEmail("john.doe@example.com");
		usuario.setContrasena("password");

		Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

		assertEquals(1, violations.size());
		ConstraintViolation<Usuario> violation = violations.iterator().next();
		assertEquals("Los apellidos no pueden estar en blanco", violation.getMessage());
	}

	@Test
	void testUsuario_EmailInvalido() {
		Usuario usuario = new Usuario();
		usuario.setNombre("John");
		usuario.setApellidos("Doe");
		usuario.setEmail("invalidemail");
		usuario.setContrasena("password");

		Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

		assertEquals(1, violations.size());
		ConstraintViolation<Usuario> violation = violations.iterator().next();
		assertEquals("La dirección de correo electrónico debe ser válida", violation.getMessage());
	}

	/*
	 * @Test void testUsuario_ContrasenaInvalida() { Usuario usuario = new
	 * Usuario(); usuario.setNombre("John"); usuario.setApellidos("Doe");
	 * usuario.setEmail("john.doe@example.com"); usuario.setContrasena("");
	 * 
	 * Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);
	 * 
	 * assertEquals(1, violations.size()); ConstraintViolation<Usuario> violation =
	 * violations.iterator().next();
	 * assertEquals("La contraseña no puede estar en blanco",
	 * violation.getMessage()); }
	 */
}