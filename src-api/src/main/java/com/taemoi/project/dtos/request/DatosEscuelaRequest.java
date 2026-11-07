package com.taemoi.project.dtos.request;

import java.util.Locale;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/escuela/datos} (pieza 394): sustituye TODOS los datos de la escuela activa (formulario
 * completo; un campo ausente o vacío queda vacío y, si es obligatorio, da 400). Sin ids: opera siempre sobre la escuela de
 * la sesión. Campos, límites y mensajes del boceto {@code docs/multi-escuela/boceto-escuela-datos.html}; los límites caben
 * en las columnas de {@code organizacion} (nombre_legal 200, cif 16, direccion 255, email_contacto 180, telefono 20,
 * firmante_nombre 180, firmante_cargo 100, firmante_nif 16).
 *
 * <p>Normaliza al leer el cuerpo, antes de validar: recorta los espacios de los extremos (vacío pasa a {@code null}) y
 * pone en mayúsculas el CIF y el NIF. El CIF y el NIF solo se comprueban por formato (no la letra de control).
 *
 * @param cif                  CIF de entidad (letra, 7 cifras y control) o NIF/NIE de persona, 9 caracteres
 * @param firmanteNif          NIF o NIE del firmante: 8 cifras (o X, Y, Z y 7 cifras) y una letra
 * @param diaLimiteMensualidad último día del mes para pagar la mensualidad, de 1 a 28 (vence el día siguiente)
 */
public record DatosEscuelaRequest(
		@NotBlank(message = "El nombre legal es obligatorio.")
		@Size(max = 150, message = "El nombre legal no puede tener más de 150 caracteres.")
		@Pattern(regexp = SIN_CONTROL, message = "El nombre legal tiene caracteres no válidos.")
		String nombreLegal,

		@NotBlank(message = "El CIF es obligatorio.")
		@Pattern(regexp = CIF, message = "El CIF no tiene un formato válido.")
		String cif,

		@NotBlank(message = "La dirección fiscal es obligatoria.")
		@Size(max = 255, message = "La dirección fiscal no puede tener más de 255 caracteres.")
		@Pattern(regexp = SIN_CONTROL, message = "La dirección fiscal tiene caracteres no válidos.")
		String direccion,

		@NotBlank(message = "El email de contacto es obligatorio.")
		@Size(max = 180, message = "El email no puede tener más de 180 caracteres.")
		@Pattern(regexp = EMAIL, message = "El email no tiene un formato válido.")
		String emailContacto,

		@Pattern(regexp = TELEFONO, message = "El teléfono solo lleva números y espacios.")
		String telefono,

		@NotBlank(message = "El nombre del firmante es obligatorio.")
		@Size(max = 100, message = "El nombre del firmante no puede tener más de 100 caracteres.")
		@Pattern(regexp = SIN_CONTROL, message = "El nombre del firmante tiene caracteres no válidos.")
		String firmanteNombre,

		@NotBlank(message = "El cargo es obligatorio.")
		@Size(max = 60, message = "El cargo no puede tener más de 60 caracteres.")
		@Pattern(regexp = SIN_CONTROL, message = "El cargo tiene caracteres no válidos.")
		String firmanteCargo,

		@NotBlank(message = "El NIF del firmante es obligatorio.")
		@Pattern(regexp = NIF, message = "El NIF no tiene un formato válido.")
		String firmanteNif,

		@NotNull(message = DIA_NO_VALIDO)
		@Min(value = 1, message = DIA_NO_VALIDO)
		@Max(value = 28, message = DIA_NO_VALIDO)
		Integer diaLimiteMensualidad) {

	/** CIF de entidad o NIF/NIE de persona (el boceto: «CIF o NIF»). */
	public static final String CIF = "[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]|[0-9XYZ][0-9]{7}[A-Z]";
	/** NIF (DNI) o NIE de una persona. */
	public static final String NIF = "[0-9XYZ][0-9]{7}[A-Z]";
	public static final String EMAIL = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+";
	public static final String TELEFONO = "\\+?[0-9 ]{9,15}";
	/**
	 * Sin saltos de línea, tabuladores ni otros caracteres de control, de formato o invisibles (los datos salen en
	 * recibos y documentos): fuera la categoría Unicode C (control, formato como U+202E o U+200B, privados, sueltos y sin
	 * asignar) y los separadores de línea y de párrafo (U+2028, U+2029). Letras con tilde, ñ, º, ª y puntuación valen.
	 */
	public static final String SIN_CONTROL = "[^\\p{C}\\p{Zl}\\p{Zp}]*";
	public static final String DIA_NO_VALIDO = "Escribe un día del 1 al 28.";

	public DatosEscuelaRequest {
		nombreLegal = recortar(nombreLegal);
		cif = mayusculas(recortar(cif));
		direccion = recortar(direccion);
		emailContacto = recortar(emailContacto);
		telefono = recortar(telefono);
		firmanteNombre = recortar(firmanteNombre);
		firmanteCargo = recortar(firmanteCargo);
		firmanteNif = mayusculas(recortar(firmanteNif));
	}

	private static String recortar(String valor) {
		if (valor == null) {
			return null;
		}
		String recortado = valor.strip();
		return recortado.isEmpty() ? null : recortado;
	}

	private static String mayusculas(String valor) {
		return valor == null ? null : valor.toUpperCase(Locale.ROOT);
	}
}
