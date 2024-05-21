package com.taemoi.project.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class CategoriaTest {

    private static Validator validator;

    @BeforeAll
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testCategoria_EntidadValida() {
        Categoria categoria = new Categoria();
        categoria.setTipoCategoria(TipoCategoria.INFANTIL);
        categoria.setNombre("PreCadete");

        Set<ConstraintViolation<Categoria>> violations = validator.validate(categoria);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testCategoria_NombreInvalido() {
        Categoria categoria = new Categoria();
        categoria.setTipoCategoria(TipoCategoria.INFANTIL);
        categoria.setNombre("");

        Set<ConstraintViolation<Categoria>> violations = validator.validate(categoria);

        assertEquals(1, violations.size());
        ConstraintViolation<Categoria> violation = violations.iterator().next();
        assertEquals("El nombre de la categoría no puede estar en blanco", violation.getMessage());
    }
}