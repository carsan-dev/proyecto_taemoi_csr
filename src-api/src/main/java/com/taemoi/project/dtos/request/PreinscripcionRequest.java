package com.taemoi.project.dtos.request;

import java.time.LocalDate;
import java.util.List;
import com.taemoi.project.entities.Deporte;
import jakarta.validation.constraints.*;

public record PreinscripcionRequest(
 @NotNull Deporte deporte,
 @NotEmpty List<@NotNull @Positive Long> turnoIds,
 @NotBlank @Size(max=100) String nombre,
 @NotBlank @Size(max=160) String apellidos,
 @Size(max=16) @Pattern(regexp="(?i)^(?:|[0-9XYZ][0-9]{7}[A-Z])$",message="El DNI/NIE no tiene un formato válido") String dni,
 @NotNull @Past LocalDate fechaNacimiento,
 @NotBlank @Size(max=255) String direccion,
 @NotBlank @Pattern(regexp="^(?:(?:\\+|00)34[ ]*)?[6789](?:[ ]*\\d){8}$",message="El teléfono debe tener nueve dígitos y prefijo +34 opcional") String telefono,
 @Pattern(regexp="^(?:|(?:(?:\\+|00)34[ ]*)?[6789](?:[ ]*\\d){8})$",message="El teléfono secundario debe tener nueve dígitos y prefijo +34 opcional") String telefono2,
 @NotBlank @Email @Size(max=180) String email,
 @Size(max=1000) String observaciones,
 @Size(max=180) String tutorNombre,
 @Size(max=16) @Pattern(regexp="(?i)^(?:|[0-9XYZ][0-9]{7}[A-Z])$",message="El DNI/NIE del responsable no tiene un formato válido") String tutorDni,
 @NotNull Boolean tieneDiscapacidad,
 boolean consentimientoFotografico,
 @AssertTrue(message="Es necesario aceptar las normas") boolean aceptacionNormas,
 @NotBlank @Size(max=180) String firmanteNombre,
 @NotBlank @Size(max=700000) String firmaBase64
) {
 @AssertTrue(message="No se puede seleccionar el mismo turno más de una vez")
 public boolean hasTurnosUnicos(){return turnoIds==null||turnoIds.size()==turnoIds.stream().distinct().count();}
 @AssertTrue(message="El DNI/NIE es obligatorio para las personas adultas")
 public boolean isDniCompatibleConEdad(){return fechaNacimiento==null||esMenor()||!blank(dni);}
 @AssertTrue(message="Los menores deben indicar responsable legal y su DNI/NIE")
 public boolean hasResponsableLegalSiEsMenor(){return fechaNacimiento==null||!esMenor()||(!blank(tutorNombre)&&!blank(tutorDni));}
 private boolean esMenor(){return java.time.Period.between(fechaNacimiento,LocalDate.now()).getYears()<18;}
 private boolean blank(String value){return value==null||value.isBlank();}
}
