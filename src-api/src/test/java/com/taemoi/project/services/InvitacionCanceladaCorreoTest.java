package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Decisión 389: el correo de «Invitación cancelada», con la marca de la escuela y el texto exacto; sin enlace ni botón
 * (la invitación ya no vale); lo que llega del alta, escapado en el HTML y en claro en el asunto.
 */
class InvitacionCanceladaCorreoTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
	private static final String LOGO = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);

	private static MarcaEscuela moiskimdo() {
		return new MarcaEscuela(1L, true, "Moiskimdo", LOGO, LOGO, PNG, null, null);
	}

	private static MarcaEscuela otra(String nombre, String logo) {
		return new MarcaEscuela(7L, false, nombre, logo, logo, null, null, null);
	}

	@Test
	void textoExacto_conLaMarcaDeLaEscuela() {
		InvitacionCorreo.Correo correo = InvitacionCanceladaCorreo.de(otra("Sierra Sur", LOGO), "Lucía");

		assertEquals("Invitación cancelada · Sierra Sur", correo.asunto());
		assertTrue(correo.html().contains("Hola, Lucía."), correo.html());
		assertTrue(correo.html().contains("Sierra Sur ha cancelado tu invitación. El enlace que recibiste ya no sirve "
				+ "para entrar en Sierra Sur."), correo.html());
		assertTrue(correo.html().contains("Si crees que es un error, ponte en contacto con la escuela."), correo.html());
		assertTrue(correo.html().contains("src=\"cid:" + InvitacionCorreo.CID_LOGO + "\""));
		assertArrayEquals(PNG, correo.logo());
		assertEquals("image/png", correo.tipoLogo());
		assertEquals("Sierra Sur", correo.remitente());
	}

	@Test
	void moiskimdo_suNombreDeCorreoYSuRemitenteDeSiempre() {
		InvitacionCorreo.Correo correo = InvitacionCanceladaCorreo.de(moiskimdo(), "Lucía");

		assertEquals("Invitación cancelada · Club Moiskimdo Taekwondo", correo.asunto());
		assertTrue(correo.html().contains("Club Moiskimdo Taekwondo ha cancelado tu invitación."), correo.html());
		assertNull(correo.remitente());
	}

	@Test
	void sinLogo_soloElNombre() {
		InvitacionCorreo.Correo correo = InvitacionCanceladaCorreo.de(otra("Sierra Sur", ""), "Lucía");

		assertFalse(correo.html().contains("<img"), correo.html());
		assertNull(correo.logo());
		assertNull(correo.tipoLogo());
	}

	@Test
	void nombreYEscuelaEscapados_elAsuntoEsTexto() {
		InvitacionCorreo.Correo correo = InvitacionCanceladaCorreo.de(otra("Club <Sierra> & Sur", ""),
				"<script>alert('x')</script>");

		assertFalse(correo.html().contains("<script>"), correo.html());
		assertFalse(correo.html().contains("<Sierra>"), correo.html());
		assertTrue(correo.html().contains("Hola, &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;."), correo.html());
		assertTrue(correo.html().contains("Club &lt;Sierra&gt; &amp; Sur ha cancelado tu invitación."), correo.html());
		assertEquals("Invitación cancelada · Club <Sierra> & Sur", correo.asunto());
	}

	@Test
	void sinNombre_noPintaNull() {
		assertFalse(InvitacionCanceladaCorreo.de(otra("Sierra Sur", ""), null).html().contains("null"));
	}

	@Test
	void noLlevaEnlaceNiBoton() {
		String html = InvitacionCanceladaCorreo.de(otra("Sierra Sur", LOGO), "Lucía").html();

		assertFalse(html.contains("<a "), html);
		assertFalse(html.contains("crear-contrasena"), html);
		assertFalse(html.contains("Crear mi contraseña"), html);
	}
}
