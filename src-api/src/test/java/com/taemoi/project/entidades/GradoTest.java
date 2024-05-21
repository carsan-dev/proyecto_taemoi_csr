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

public class GradoTest {

    private static Validator validator;

    @BeforeAll
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testGrado_EntidadValida() {
        Grado grado = new Grado();
        grado.setTipoGrado(TipoGrado.BLANCO);

        Set<ConstraintViolation<Grado>> violations = validator.validate(grado);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testGrado_TipoGradoInvalido() {
        Grado grado = new Grado();
        grado.setTipoGrado(null);

        Set<ConstraintViolation<Grado>> violations = validator.validate(grado);

        assertEquals(1, violations.size());
        ConstraintViolation<Grado> violation = violations.iterator().next();
        assertEquals("El tipo de grado no puede ser nulo", violation.getMessage());
    }
}