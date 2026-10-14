package com.taemoi.project.cifrado;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Cifrado en reposo de textos sensibles (datos de salud) con AES-256-GCM.
 *
 * <p>Formato guardado: {@code v1:} + base64(IV de 12 bytes || texto cifrado || etiqueta de 16 bytes).
 * El prefijo es la versión de la clave: hoy solo existe {@code v1}; rotar la clave (fuera de alcance)
 * consistirá en añadir {@code v2} y descifrar cada valor con la clave de su prefijo.
 *
 * <p>Dato autenticado (AAD) = prefijo + {@code contexto}, p. ej. {@code alumno_salud.descripcion#42}
 * (tabla.columna#id de la fila): un valor copiado a otra fila u otra columna, o con otro prefijo, no
 * se descifra.
 *
 * <p>La clave sale de {@code app.cifrado.clave} (variable {@code APP_CIFRADO_CLAVE}): 32 bytes en
 * base64. Sin clave válida la aplicación no arranca ({@code @Lazy(false)}: también con
 * {@code spring.main.lazy-initialization=true}). Ningún mensaje de error incluye el texto ni la clave.
 */
@Component
@Lazy(false)
public class CifradorDatos {

	static final String VERSION_CLAVE = "v1";
	private static final String PREFIJO = VERSION_CLAVE + ":";
	private static final String ALGORITMO = "AES/GCM/NoPadding";
	private static final int BYTES_CLAVE = 32;
	private static final int BYTES_IV = 12;
	private static final int BITS_ETIQUETA = 128;

	private final SecretKey clave;
	private final SecureRandom aleatorio = new SecureRandom();

	public CifradorDatos(@Value("${app.cifrado.clave:}") String claveBase64) {
		this.clave = leerClave(claveBase64);
	}

	private static SecretKey leerClave(String claveBase64) {
		if (claveBase64 == null || claveBase64.isBlank()) {
			throw new IllegalStateException("Falta la clave de cifrado de datos sensibles: define APP_CIFRADO_CLAVE "
					+ "(app.cifrado.clave) con 32 bytes aleatorios en base64, p. ej. 'openssl rand -base64 32'.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(claveBase64.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("app.cifrado.clave no es base64 válido.");
		}
		if (bytes.length != BYTES_CLAVE) {
			throw new IllegalStateException("app.cifrado.clave debe tener 32 bytes (AES-256); tiene " + bytes.length + ".");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	/** Contexto (AAD) de una columna de una fila: {@code tabla.columna#id}. */
	public static String contexto(String tabla, String columna, Object id) {
		if (id == null) {
			throw new IllegalArgumentException("El contexto de cifrado necesita el id de la fila.");
		}
		return tabla + "." + columna + "#" + id;
	}

	/** Cifra un texto ligado a {@code contexto}. {@code null} se guarda como {@code null}. */
	public String cifrar(String texto, String contexto) {
		if (texto == null) {
			return null;
		}
		try {
			byte[] iv = new byte[BYTES_IV];
			aleatorio.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITMO);
			cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
			cipher.updateAAD(aad(contexto));
			byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return PREFIJO + Base64.getEncoder().encodeToString(salida);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo cifrar un dato sensible.", e);
		}
	}

	/**
	 * Descifra un valor guardado por {@link #cifrar} con el mismo {@code contexto}. Falla con
	 * {@link DatoCifradoIlegibleException} (sin contenido) si la clave, el contexto o el dato no cuadran.
	 */
	public String descifrar(String guardado, String contexto) {
		if (guardado == null) {
			return null;
		}
		if (!guardado.startsWith(PREFIJO)) {
			throw new DatoCifradoIlegibleException("Dato cifrado con una versión de clave desconocida.");
		}
		try {
			byte[] entrada = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
			if (entrada.length < BYTES_IV + BITS_ETIQUETA / 8) {
				throw new DatoCifradoIlegibleException("Dato cifrado incompleto.");
			}
			Cipher cipher = Cipher.getInstance(ALGORITMO);
			cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, entrada, 0, BYTES_IV));
			cipher.updateAAD(aad(contexto));
			byte[] claro = cipher.doFinal(entrada, BYTES_IV, entrada.length - BYTES_IV);
			return new String(claro, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// Sin causa encadenada con datos: el mensaje no debe revelar nada del contenido.
			throw new DatoCifradoIlegibleException(
					"No se pudo descifrar un dato sensible (clave, fila o columna incorrecta, o dato alterado).");
		}
	}

	private static byte[] aad(String contexto) {
		if (contexto == null || contexto.isBlank()) {
			throw new IllegalArgumentException("Falta el contexto de cifrado.");
		}
		return (PREFIJO + contexto).getBytes(StandardCharsets.UTF_8);
	}
}
