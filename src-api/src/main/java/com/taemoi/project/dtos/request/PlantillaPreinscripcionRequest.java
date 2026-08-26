package com.taemoi.project.dtos.request;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record PlantillaPreinscripcionRequest(
 @NotBlank @Size(max=120) String cabecera,
 @NotBlank @Size(max=180) String contacto,
 @NotBlank @Size(max=180) String titulo,
 @NotBlank @Size(max=1500) String consentimiento,
 @NotEmpty @Size(max=30) List<@NotBlank @Size(max=2000) String> normas,
 @NotBlank @Size(max=3000) String importes,
 @NotBlank @Size(max=3000) String instrucciones
) {}
