package com.taemoi.project.cifrado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class CifradorDatosTest {

	private static final String CLAVE = Base64.getEncoder()
			.encodeToString("clave-de-prueba-de-32-bytes-ok!!".getBytes(StandardCharsets.US_ASCII));
	private static final String OTRA_CLAVE = Base64.getEncoder()
			.encodeToString("otra-clave-de-32-bytes-distinta!".getBytes(StandardCharsets.US_ASCII));
	private static final String TEXTO = "Asma leve; alergia a la penicilina";

	private static final String CTX = CifradorDatos.contexto("alumno_salud", "descripcion", 7L);

	private final CifradorDatos cifrador = new CifradorDatos(CLAVE);

	@Test
	void cifraYDescifraConPrefijoDeVersionSinTextoEnClaro() {
		String guardado = cifrador.cifrar(TEXTO, CTX);
		assertTrue(guardado.startsWith("v1:"), guardado);
		assertFalse(guardado.contains("penicilina"));
		assertEquals(TEXTO, cifrador.descifrar(guardado, CTX));
		assertEquals("", cifrador.descifrar(cifrador.cifrar("", CTX), CTX));
		assertEquals("ñandú ü €", cifrador.descifrar(cifrador.cifrar("ñandú ü €", CTX), CTX));
	}

	@Test
	void cadaCifradoUsaUnIvDistinto() {
		assertNotEquals(cifrador.cifrar(TEXTO, CTX), cifrador.cifrar(TEXTO, CTX));
	}

	@Test
	void nuloSeQuedaEnNulo() {
		assertNull(cifrador.cifrar(null, CTX));
		assertNull(cifrador.descifrar(null, CTX));
	}

	@Test
	void conOtraClaveFallaSinRevelarElContenido() {
		String guardado = cifrador.cifrar(TEXTO, CTX);
		DatoCifradoIlegibleException e = assertThrows(DatoCifradoIlegibleException.class,
				() -> new CifradorDatos(OTRA_CLAVE).descifrar(guardado, CTX));
		assertFalse(e.getMessage().contains("penicilina"));
		assertNull(e.getCause(), "sin causa encadenada que pueda arrastrar datos");
	}

	@Test
	void unDatoAlteradoOConVersionDesconocidaFalla() {
		String guardado = cifrador.cifrar(TEXTO, CTX);
		byte[] bytes = Base64.getDecoder().decode(guardado.substring(3));
		bytes[bytes.length - 1] ^= 1;
		String alterado = "v1:" + Base64.getEncoder().encodeToString(bytes);
		assertThrows(DatoCifradoIlegibleException.class, () -> cifrador.descifrar(alterado, CTX));
		assertThrows(DatoCifradoIlegibleException.class, () -> cifrador.descifrar("v2:" + guardado.substring(3), CTX));
		assertThrows(DatoCifradoIlegibleException.class, () -> cifrador.descifrar(TEXTO, CTX), "texto en claro no se acepta");
		assertThrows(DatoCifradoIlegibleException.class, () -> cifrador.descifrar("v1:AAAA", CTX));
	}

	@Test
	void sinClaveValidaNoArranca() {
		assertThrows(IllegalStateException.class, () -> new CifradorDatos(null));
		assertThrows(IllegalStateException.class, () -> new CifradorDatos("  "));
		assertThrows(IllegalStateException.class, () -> new CifradorDatos("esto no es base64!"));
		String corta = Base64.getEncoder().encodeToString(new byte[16]);
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> new CifradorDatos(corta));
		assertFalse(e.getMessage().contains(corta), "el mensaje no incluye la clave");
	}

	@Test
	void unValorCopiadoAOtraFilaOColumnaNoSeDescifra() {
		String guardado = cifrador.cifrar(TEXTO, CTX);
		assertEquals(TEXTO, cifrador.descifrar(guardado, CifradorDatos.contexto("alumno_salud", "descripcion", 7L)));
		assertThrows(DatoCifradoIlegibleException.class,
				() -> cifrador.descifrar(guardado, CifradorDatos.contexto("alumno_salud", "descripcion", 8L)));
		assertThrows(DatoCifradoIlegibleException.class,
				() -> cifrador.descifrar(guardado, CifradorDatos.contexto("alumno_salud", "alergias", 7L)));
		assertThrows(IllegalArgumentException.class, () -> CifradorDatos.contexto("alumno_salud", "alergias", null));
	}
}
