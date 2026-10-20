package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.RolMembresia;

/**
 * Pieza E8-4 (D8-13, §5.3): el correo de invitación con la marca de la escuela. Moiskimdo, con su nombre de correo de
 * cuenta y su logo de siempre; otra escuela, su nombre (escapado) y su logo; sin logo, solo el nombre (decisión 211).
 * El texto cambia según el rol y todo lo que viene del alta va escapado.
 */
class InvitacionCorreoTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
	private static final String LOGO = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);
	private static final String ENLACE = "https://escuela.example.test/crear-contrasena?token=abc_DEF-123";

	private static MarcaEscuela moiskimdo() {
		return new MarcaEscuela(1L, true, "Moiskimdo", LOGO, LOGO, PNG, null, null);
	}

	private static MarcaEscuela otra(String nombre, String logo) {
		return new MarcaEscuela(7L, false, nombre, logo, logo, null, null, null);
	}

	@Test
	void moiskimdo_suNombreYSuLogoDeSiempre() {
		InvitacionCorreo.Correo correo = InvitacionCorreo.de(moiskimdo(), "Lucía", RolMembresia.PROFESOR, ENLACE);

		assertTrue(correo.html().contains("Club Moiskimdo Taekwondo"));
		assertTrue(correo.html().contains("Te han dado acceso como profesor en Club Moiskimdo Taekwondo."));
		assertTrue(correo.html().contains("src=\"cid:" + InvitacionCorreo.CID_LOGO + "\""));
		assertArrayEquals(PNG, correo.logo());
		assertEquals("image/png", correo.tipoLogo());
		assertNull(correo.remitente(), "Moiskimdo sale con el remitente de siempre");
		assertTrue(correo.asunto().contains("Club Moiskimdo Taekwondo"));
	}

	@Test
	void otraEscuela_suNombreEscapadoYSuLogo() {
		InvitacionCorreo.Correo correo = InvitacionCorreo.de(otra("Club <Sierra> & Sur", LOGO), "Ana",
				RolMembresia.PROFESOR, ENLACE);

		assertTrue(correo.html().contains("Club &lt;Sierra&gt; &amp; Sur"));
		assertFalse(correo.html().contains("<Sierra>"));
		assertFalse(correo.html().contains("Moiskimdo"));
		assertFalse(correo.html().contains("Moi's"));
		assertArrayEquals(PNG, correo.logo());
		assertEquals("Club <Sierra> & Sur", correo.remitente());
		assertTrue(correo.asunto().contains("Club <Sierra> & Sur"), "el asunto es texto, sin escapar");
	}

	@Test
	void sinLogo_soloElNombre() {
		InvitacionCorreo.Correo correo = InvitacionCorreo.de(otra("Escuela Sin Logo", ""), "Ana", RolMembresia.PROFESOR,
				ENLACE);

		assertFalse(correo.html().contains("<img"));
		assertNull(correo.logo());
		assertNull(correo.tipoLogo());
		assertTrue(correo.html().contains("Escuela Sin Logo"));
	}

	@Test
	void textoSegunElRol() {
		Map<RolMembresia, String> enPantalla = Map.of(RolMembresia.PROPIETARIO, "propietario",
				RolMembresia.ADMINISTRADOR, "gestor", RolMembresia.RESPONSABLE_SEDE, "responsable de sede",
				RolMembresia.PROFESOR, "profesor");
		enPantalla.forEach((rol, texto) -> {
			String html = InvitacionCorreo.de(otra("Escuela Dos", ""), "Ana", rol, ENLACE).html();
			assertTrue(html.contains("Hola, Ana."), rol.name());
			assertTrue(html.contains("Te han dado acceso como " + texto + " en Escuela Dos."), rol.name());
			assertTrue(html.contains("Crear mi contraseña"), rol.name());
			assertTrue(html.contains("El enlace caduca en 7 días."), rol.name());
			assertTrue(html.contains("href=\"" + ENLACE + "\""), rol.name());
		});
	}

	@Test
	void escapaElNombre() {
		String html = InvitacionCorreo.de(otra("Escuela Dos", ""), "<b>Ana</b> & \"Eva\"", RolMembresia.PROFESOR,
				ENLACE).html();

		assertTrue(html.contains("Hola, &lt;b&gt;Ana&lt;/b&gt; &amp; &quot;Eva&quot;."));
		assertFalse(html.contains("<b>Ana"));
	}
}
