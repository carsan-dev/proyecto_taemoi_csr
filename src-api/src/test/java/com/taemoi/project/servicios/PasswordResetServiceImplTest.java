package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.entities.*;
import com.taemoi.project.repositories.*;
import com.taemoi.project.services.*;
import com.taemoi.project.services.impl.*;

class PasswordResetServiceImplTest {
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Usuario user = new Usuario();
    private PasswordResetServiceImpl reset;
    private AuthenticationServiceImpl auth;

    @BeforeEach
    void setup() {
        user.setEmail("alumno@example.test");
        user.setContrasena(encoder.encode("Anterior-123!"));
        user.setRoles(Set.of(Roles.ROLE_USER));
        user.setAlumno(new Alumno());
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByResetTokenHash(anyString())).thenAnswer(call ->
            call.getArgument(0).equals(user.getResetTokenHash()) ? Optional.of(user) : Optional.empty());
        reset = new PasswordResetServiceImpl(users, encoder, email);
        ReflectionTestUtils.setField(reset, "frontendBaseUrl", "https://example.test");
        ReflectionTestUtils.setField(reset, "resetTokenHours", 1L);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(name -> user);
        provider.setPasswordEncoder(encoder);
        auth = new AuthenticationServiceImpl(users, alumnos, new LoginAttemptServiceImpl(), encoder,
            mock(JwtService.class), new ProviderManager(provider));
        when(alumnos.existsByEmailIgnoreCaseAndActivoTrue(user.getEmail())).thenReturn(true);
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private String requestToken() {
        reset.solicitarResetContrasena(" ALUMNO@EXAMPLE.TEST ");
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).sendEmail(eq(user.getEmail()), anyString(), html.capture());
        var matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(html.getValue());
        assertTrue(matcher.find());
        String token = matcher.group(1);
        assertNotEquals(token, user.getResetTokenHash());
        assertTrue(user.getResetTokenExpiresAt().isAfter(LocalDateTime.now()));
        return token;
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void resetAllowsRealPasswordLoginAndGooglePreservesPassword(AuthProvider origin) {
        user.setAuthProvider(origin);
        Alumno linked = user.getAlumno();
        String token = requestToken();
        reset.resetearContrasena(token, "Nueva-456!");
        assertEquals(origin, user.getAuthProvider());
        assertSame(linked, user.getAlumno());
        assertEquals(Set.of(Roles.ROLE_USER), user.getRoles());
        assertNull(user.getResetTokenHash());
        assertNull(user.getResetTokenExpiresAt());
        assertThrows(IllegalArgumentException.class, () -> reset.resetearContrasena(token, "Otra-789!"));
        assertThrows(BadCredentialsException.class, () -> auth.signin(new LoginRequest(user.getEmail(), "Anterior-123!")));
        assertDoesNotThrow(() -> auth.signin(new LoginRequest(user.getEmail(), "Nueva-456!")));
        String hash = user.getContrasena();
        OAuth2User google = mock(OAuth2User.class);
        when(google.getAttribute("email")).thenReturn(user.getEmail());
        assertSame(user, new OAuth2UserServiceImpl(alumnos, users, encoder).processOAuth2Login(google, "google"));
        assertEquals(hash, user.getContrasena());
        assertDoesNotThrow(() -> auth.signin(new LoginRequest(user.getEmail(), "Nueva-456!")));
        when(alumnos.existsByEmailIgnoreCaseAndActivoTrue(user.getEmail())).thenReturn(false);
        assertThrows(DisabledException.class, () -> auth.signin(new LoginRequest(user.getEmail(), "Nueva-456!")));
        user.setRoles(Set.of(Roles.ROLE_ADMIN, Roles.ROLE_USER));
        assertDoesNotThrow(() -> auth.signin(new LoginRequest(user.getEmail(), "Nueva-456!")));
        assertSame(user, new OAuth2UserServiceImpl(alumnos, users, encoder).processOAuth2Login(google, "google"));
        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class, () -> auth.signin(new LoginRequest(user.getEmail(), "Incorrecta!")));
        }
        assertThrows(LockedException.class, () -> auth.signin(new LoginRequest(user.getEmail(), "Nueva-456!")));
    }

    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void invalidAndExpiredTokensCannotChangePassword(AuthProvider origin) {
        user.setAuthProvider(origin);
        String hash = user.getContrasena();
        String token = requestToken();
        for (String invalid : new String[] {null, "", "invalid"}) {
            assertThrows(IllegalArgumentException.class, () -> reset.resetearContrasena(invalid, "Nueva-456!"));
        }
        user.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> reset.resetearContrasena(token, "Nueva-456!"));
        assertNull(user.getResetTokenHash());
        assertNull(user.getResetTokenExpiresAt());
        assertEquals(hash, user.getContrasena());
    }
}
