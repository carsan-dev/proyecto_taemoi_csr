package com.taemoi.project.servicios;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;

public interface AuthenticationService {

	JwtAuthenticationResponse signup(RegistroRequest request);

	JwtAuthenticationResponse signin(LoginRequest request);
}
