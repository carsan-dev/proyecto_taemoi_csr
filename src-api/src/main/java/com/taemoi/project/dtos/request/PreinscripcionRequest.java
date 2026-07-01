package com.taemoi.project.dtos.request;

import java.time.LocalDate;
import com.taemoi.project.entities.Deporte;
import jakarta.validation.constraints.*;

public record PreinscripcionRequest(
 @NotNull Deporte deporte,
 Long grupoId,
 Long turnoId,
 @NotBlank @Size(max=100) String nombre,
 @NotBlank @Size(max=160) String apellidos,
 @NotBlank @Pattern(regexp="(?i)^[0-9XYZ][0-9]{7}[A-Z]$") String dni,
 @NotNull @Past LocalDate fechaNacimiento,
 @NotBlank @Size(max=255) String direccion,
 @NotBlank @Pattern(regexp="^[0-9+ ]{9,16}$") String telefono,
 @NotBlank @Email @Size(max=180) String email,
 @Size(max=180) String tutorNombre,
 @Size(max=16) String tutorDni,
 boolean consentimientoFotografico,
 @AssertTrue(message="Es necesario aceptar las normas") boolean aceptacionNormas,
 @NotBlank @Size(max=180) String firmanteNombre,
 @NotBlank @Size(max=700000) String firmaBase64
) {
 @AssertTrue(message="Es necesario seleccionar un grupo")
 public boolean hasGrupoOTurno(){return grupoId!=null||turnoId!=null;}
}
