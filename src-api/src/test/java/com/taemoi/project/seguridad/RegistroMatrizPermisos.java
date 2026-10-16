package com.taemoi.project.seguridad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lee y escribe el registro de políticas {@code src/test/resources/seguridad/matriz-permisos.txt}.
 *
 * <p>Formato: una línea por {@code MÉTODO RUTA}, seguida de una decisión ({@code PERMITE}/{@code DENIEGA}) por
 * cada columna de {@link RolMatriz}, en su orden. Separador: espacios o tabuladores. {@code #} inicia un
 * comentario (línea completa o al final de la línea).
 */
final class RegistroMatrizPermisos {

	static final String RECURSO = "seguridad/matriz-permisos.txt";
	static final String FUENTE = "src/test/resources/" + RECURSO;

	private RegistroMatrizPermisos() {
	}

	record Linea(int numero, String metodo, String ruta, Map<RolMatriz, Decision> decisiones) {
		String clave() {
			return metodo + " " + ruta;
		}
	}

	record Fila(String metodo, String ruta, Map<RolMatriz, Decision> decisiones, String comentario) {
	}

	/** Errores de formato acumulados (no lanza al primero, para listarlos todos). */
	static List<Linea> leer(InputStream entrada, List<String> errores) throws IOException {
		List<Linea> lineas = new ArrayList<>();
		try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			String texto;
			int numero = 0;
			while ((texto = lector.readLine()) != null) {
				numero++;
				int comentario = texto.indexOf('#');
				String util = (comentario >= 0 ? texto.substring(0, comentario) : texto).trim();
				if (util.isEmpty()) {
					continue;
				}
				String[] partes = util.split("\\s+");
				RolMatriz[] roles = RolMatriz.values();
				if (partes.length != 2 + roles.length) {
					errores.add("línea " + numero + ": se esperaban MÉTODO RUTA y " + roles.length
							+ " decisiones, hay " + partes.length + " campos: " + util);
					continue;
				}
				Map<RolMatriz, Decision> decisiones = new EnumMap<>(RolMatriz.class);
				boolean valida = true;
				for (int i = 0; i < roles.length; i++) {
					try {
						decisiones.put(roles[i], Decision.valueOf(partes[2 + i]));
					} catch (IllegalArgumentException e) {
						errores.add("línea " + numero + ": decisión desconocida '" + partes[2 + i] + "' para "
								+ roles[i] + " (usa PERMITE o DENIEGA)");
						valida = false;
					}
				}
				if (valida) {
					lineas.add(new Linea(numero, partes[0].toUpperCase(), partes[1], decisiones));
				}
			}
		}
		return lineas;
	}

	static String formatearFila(Fila fila, int anchoMetodo, int anchoRuta) {
		StringBuilder sb = new StringBuilder();
		sb.append(rellenar(fila.metodo(), anchoMetodo)).append(' ').append(rellenar(fila.ruta(), anchoRuta));
		for (RolMatriz rol : RolMatriz.values()) {
			sb.append(' ').append(rellenar(fila.decisiones().get(rol).name(), anchoColumna(rol)));
		}
		if (fila.comentario() != null && !fila.comentario().isBlank()) {
			sb.append(" # ").append(fila.comentario());
		}
		return sb.toString().stripTrailing();
	}

	static String formatear(List<Fila> filas) {
		int anchoMetodo = "# METODO".length();
		int anchoRuta = "RUTA".length();
		for (Fila fila : filas) {
			anchoMetodo = Math.max(anchoMetodo, fila.metodo().length());
			anchoRuta = Math.max(anchoRuta, fila.ruta().length());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("""
				# Matriz de permisos por endpoint y rol (caracterización del comportamiento ACTUAL).
				# La valida com.taemoi.project.seguridad.MatrizPermisosTest; léase docs/multi-escuela/matriz-endpoints.md.
				#
				# Columnas: MÉTODO RUTA y una decisión (PERMITE/DENIEGA) por rol:
				#   ANONIMO         sin sesión
				#   FAMILIA         ROLE_USER
				#   GESTOR          ROLE_MANAGER (administrador, responsable de sede, profesor)
				#   PROPIETARIO     ROLE_ADMIN
				#   SOLO_PLATAFORMA solo ROLE_ADMIN_PLATAFORMA (administrador de plataforma sin membresía)
				# La decisión combina la regla de URL (SecurityConfiguration) y la de método (@PreAuthorize).
				# RUTA es el patrón del controlador tal cual; para manejadores comodín (/imagenes/**, /documentos/**)
				# cada línea es una ruta de ejemplo concreta que ese manejador atiende.
				# El comentario final (Controlador#método) es informativo.
				#
				""");
		StringBuilder cabecera = new StringBuilder();
		cabecera.append(rellenar("# METODO", anchoMetodo)).append(' ').append(rellenar("RUTA", anchoRuta));
		for (RolMatriz rol : RolMatriz.values()) {
			cabecera.append(' ').append(rellenar(rol.name(), anchoColumna(rol)));
		}
		sb.append(cabecera.toString().stripTrailing()).append('\n');
		for (Fila fila : filas) {
			sb.append(formatearFila(fila, anchoMetodo, anchoRuta)).append('\n');
		}
		return sb.toString();
	}

	private static int anchoColumna(RolMatriz rol) {
		return Math.max(rol.name().length(), "DENIEGA".length());
	}

	private static String rellenar(String texto, int ancho) {
		if (texto.length() >= ancho) {
			return texto;
		}
		return texto + " ".repeat(ancho - texto.length());
	}
}
