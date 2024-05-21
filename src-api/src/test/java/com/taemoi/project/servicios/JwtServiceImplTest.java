package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;

import com.taemoi.project.entidades.CustomUserDetails;
import com.taemoi.project.servicios.impl.JwtServiceImpl;

@SpringBootTest
public class JwtServiceImplTest {

	@Autowired
    private JwtServiceImpl jwtService = new JwtServiceImpl();

    @Test
    public void testGenerarToken() {
        UserDetails userDetails = new CustomUserDetails("John", "password");
        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
    }

    @Test
    public void testEsValidoToken_TokenValido() {
        UserDetails userDetails = new CustomUserDetails("John", "password");
        String token = jwtService.generateToken(userDetails);

        boolean isValid = jwtService.isTokenValid(token, userDetails);
        assertTrue(isValid);
    }

    @Test
    public void testIsTokenValid_UsuarioIncorrecto() {
        UserDetails userDetails = new CustomUserDetails("John", "password");
        String token = jwtService.generateToken(userDetails);
        
        UserDetails invalidUserDetails = new CustomUserDetails("Alice", "password");

        boolean isValid = jwtService.isTokenValid(token, invalidUserDetails);
        assertFalse(isValid);
    }
}