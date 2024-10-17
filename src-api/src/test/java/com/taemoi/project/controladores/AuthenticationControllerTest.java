package com.taemoi.project.controladores;

public class AuthenticationControllerTest {

/*    @Test
    void testRegistro_Correcto() {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        RegistroRequest request = new RegistroRequest("John", "Doe", "john@example.com", "password");
        JwtAuthenticationResponse expectedResponse = new JwtAuthenticationResponse("jwtToken");
        when(authenticationService.signup(request)).thenReturn(expectedResponse);

        AuthenticationController controller = new AuthenticationController();
        controller.authenticationService = authenticationService;

        ResponseEntity<JwtAuthenticationResponse> responseEntity = controller.signup(request);

        verify(authenticationService).signup(request);
        assertSame(expectedResponse, responseEntity.getBody());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }

    @Test
    void testLogin_Correcto() {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        LoginRequest request = new LoginRequest("john@example.com", "password");
        JwtAuthenticationResponse expectedResponse = new JwtAuthenticationResponse("jwtToken");
        when(authenticationService.signin(request)).thenReturn(expectedResponse);

        AuthenticationController controller = new AuthenticationController();
        controller.authenticationService = authenticationService;

        ResponseEntity<JwtAuthenticationResponse> responseEntity = controller.signin(request);

        verify(authenticationService).signin(request);
        assertSame(expectedResponse, responseEntity.getBody());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }
    */
}