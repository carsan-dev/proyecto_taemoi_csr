package com.taemoi.project.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@SpringBootTest
public class AlumnoTest {

    private static Validator validator;

    @BeforeAll
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testAlumno_EntidadValida() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testAlumno_NombreInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("El nombre no puede estar en blanco", violation.getMessage());
    }

    @Test
    void testAlumno_ApellidosInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("Los apellidos no pueden estar en blanco", violation.getMessage());
    }

  /*  @Test
    void testAlumno_NumeroExpedienteInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(null);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("El número de expediente no puede estar en blanco", violation.getMessage());
    }*/
    
    @Test
    void testAlumno_FechaNacimientoInvalida() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(null);
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("La fecha de nacimiento no puede ser nula", violation.getMessage());
    }
    
    /*
    @Test
    void testAlumno_NifInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("El NIF no puede estar en blanco", violation.getMessage());
    }*/
    
    @Test
    void testAlumno_DireccionInvalida() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("La dirección no puede estar en blanco", violation.getMessage());
    }
    
    @Test
    void testAlumno_TelefonoInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(null);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("El teléfono no puede ser nulo", violation.getMessage());
    }
    
    @Test
    void testAlumno_EmailInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("emailinvalido.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("La dirección de correo electrónico debe ser válida", violation.getMessage());
    }
    
    @Test
    void testAlumno_TipoTarifaInvalido() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(123456789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(null);
        alumno.setCuantiaTarifa(30.0);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("El tipo de tarifa no puede ser nulo", violation.getMessage());
    }
    
/*    @Test
    void testAlumno_CuantiaTarifaInvalida() {
        Alumno alumno = new Alumno();
        alumno.setNombre("John");
        alumno.setApellidos("Doe");
        alumno.setNumeroExpediente(12345);
        alumno.setFechaNacimiento(new Date());
        alumno.setNif("12345678A");
        alumno.setDireccion("Calle Principal, 123");
        alumno.setTelefono(12345789);
        alumno.setEmail("john@example.com");
        alumno.setTipoTarifa(TipoTarifa.ADULTO);
        alumno.setCuantiaTarifa(null);

        Set<ConstraintViolation<Alumno>> violations = validator.validate(alumno);

        assertEquals(1, violations.size());
        ConstraintViolation<Alumno> violation = violations.iterator().next();
        assertEquals("La cuantía de la tarifa no puede ser nula", violation.getMessage());
    }
    */
}