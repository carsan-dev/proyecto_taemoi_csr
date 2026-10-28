package com.taemoi.project.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.mockito.MockedStatic;

/**
 * Caracterización del contenido de los PDF (antes del rediseño de la decisión 314): fija en un fichero dorado
 * ({@code src/test/resources/pdf-contenido/<informe>.txt}, UTF-8 y LF) el texto de cada PDF para detectar cualquier
 * pérdida de información al cambiar su aspecto.
 *
 * <h2>Extracción</h2>
 * {@link PDFTextStripper} con orden por posición; en cada línea los espacios (también los duros) se reducen a uno y se
 * recortan; las líneas vacías se descartan. En los PDF con formulario (certificado de cobros) se añaden los pares
 * {@code nombre=valor} de todos los campos del AcroForm.
 *
 * <h2>Formato del dorado</h2>
 * <ul>
 * <li>{@code # …}: comentario (cabecera del fichero), se ignora.</li>
 * <li>{@code ~ …}: línea de PRESENTACIÓN que el rediseño puede cambiar de forma (título del informe, nombre de la
 * escuela en la cabecera, «Página X de Y», fecha de generación).</li>
 * <li>{@code @campo nombre=valor}: campo del AcroForm.</li>
 * <li>Cualquier otra: línea de DATOS (nombres, cifras, importes, fechas, conceptos, estados, cabeceras de columna,
 * rótulos de grupo, totales, textos legales, número de alumnos…).</li>
 * </ul>
 *
 * <h2>Regla de comparación</h2>
 * <ol>
 * <li><b>Datos</b>: cada línea de datos del dorado debe aparecer en la salida nueva conservando el orden relativo
 * (subsecuencia). Se compara por contenido normalizado: minúsculas y SIN espacios, sobre el texto nuevo entero
 * concatenado; así da igual que el rediseño cambie el caso de rótulos y cabeceras o parta o junte líneas, pero no que
 * falte un dato o cambie de orden. En detalle: las palabras de la línea (con sus repeticiones) deben estar todas
 * juntas, en cualquier orden, en un tramo del texto nuevo de {@code 2 × longitud + 40} caracteres; y ese tramo debe
 * empezar después del comienzo de la PENÚLTIMA línea de datos encontrada. Esa holgura de una línea y el orden libre
 * dentro del tramo son necesarios porque el orden por posición entrelaza las celdas de una tabla que ocupan varias
 * líneas (cabeceras partidas, nombres de grado bajo el cinturón) y el rediseño cambiará los anchos de columna; a
 * cambio, el test no detecta el intercambio de dos líneas CONTIGUAS, pero sí cualquier pérdida y cualquier
 * desplazamiento mayor (un alumno en otro grupo, un total antes de su tabla…).</li>
 * <li><b>Presentación</b> ({@code ~ }): se comprueban de forma laxa, en cualquier sitio del texto nuevo y sin orden.
 * Las fechas deben seguir apareciendo (vale cualquier formato: «15 de octubre de 2026», 15/10/2026, 2026-10-15…); los
 * «Página X de Y», los números X e Y juntos (vale «1 de 2», «1 / 2»…); y el resto de palabras y números de la línea
 * (el nombre de la escuela, las palabras del título), como palabras sueltas en cualquier caso y orden.</li>
 * <li><b>Campos del formulario</b>: deben conservarse todos, con el mismo nombre y el mismo valor (normalizado).</li>
 * </ol>
 *
 * <h2>Regenerar</h2>
 * Con {@code -Dpdf.contenido.regenerar=true} el test escribe el dorado en lugar de comparar (las líneas de
 * presentación se marcan con la {@link Presentacion} de cada informe). Solo debe regenerarse con el código ANTERIOR al
 * cambio: regenerar después del rediseño borraría justo lo que este test protege.
 *
 * <h2>Fechas</h2>
 * Los generadores toman «hoy» de {@code LocalDate.now()} sin {@link Clock} (fecha de generación, edades, meses en el
 * grado, licencias caducadas, mayoría de edad por año natural). {@link #conHoy} lo fija con un mock estático de
 * {@link LocalDate} y fija también el {@link Locale} por defecto ({@code String.format("%.2f")} depende de él). La
 * fecha de generación queda marcada como presentación.
 */
public final class ContenidoPdf {

	/** Fecha fija de «hoy» en los tests de contenido. */
	public static final LocalDate HOY = LocalDate.of(2026, 10, 15);

	static final String PRESENTACION = "~ ";
	static final String CAMPO = "@campo ";
	static final String COMENTARIO = "#";

	private static final Path CARPETA = Path.of("src", "test", "resources", "pdf-contenido");
	private static final Locale ES = Locale.of("es", "ES");
	private static final Pattern ESPACIOS = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");
	private static final String MESES = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre";
	private static final Pattern FECHA_LARGA = Pattern.compile(
			"(?:(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),?\\s+)?(\\d{1,2}) de (" + MESES
					+ ") de (\\d{4})");
	private static final Pattern FECHA_CORTA = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
	private static final Pattern PAGINA = Pattern.compile("p\\S{0,3}gina\\s+(\\d+)\\s+de\\s+(\\d+)");
	private static final Pattern PALABRA = Pattern.compile("[\\p{L}\\p{N}]+");
	private static final Set<String> VACIAS = Set.of("de", "del", "la", "las", "el", "los", "y", "a", "en", "por",
			"para", "con", "al");

	private ContenidoPdf() {
	}

	@FunctionalInterface
	public interface Generador {
		byte[] generar() throws Exception;
	}

	/**
	 * Genera el PDF con «hoy» = {@link #HOY} (también {@code LocalDate.now(zona)}) y {@link Locale#ROOT} como locale por
	 * defecto (el de un servidor Linux sin locale: decimales con punto).
	 */
	public static byte[] conHoy(Generador generador) throws Exception {
		Locale anterior = Locale.getDefault();
		Locale.setDefault(Locale.ROOT);
		try (MockedStatic<LocalDate> fecha = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
			fecha.when(LocalDate::now).thenReturn(HOY);
			fecha.when(() -> LocalDate.now(any(ZoneId.class))).thenReturn(HOY);
			fecha.when(() -> LocalDate.now(any(Clock.class))).thenReturn(HOY);
			return generador.generar();
		} finally {
			Locale.setDefault(anterior);
		}
	}

	/** Qué líneas son de presentación al regenerar un dorado. */
	public static final class Presentacion {
		private final List<String> literales;

		private Presentacion(List<String> literales) {
			this.literales = literales.stream().map(l -> normaliza(l).toLowerCase(ES)).toList();
		}

		/**
		 * Presentación: las líneas iguales (sin distinguir mayúsculas) a uno de estos literales (títulos, nombre de la
		 * escuela), las que llevan «Página X de Y» y las que llevan la fecha de generación ({@link #HOY} en formato
		 * largo, dd/MM/yyyy o yyyy-MM-dd).
		 */
		public static Presentacion de(String... literales) {
			return new Presentacion(List.of(literales));
		}

		boolean es(String linea) {
			String minuscula = linea.toLowerCase(ES);
			return literales.contains(normaliza(linea).toLowerCase(ES)) || PAGINA.matcher(minuscula).find()
					|| minuscula.contains(HOY.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES)))
					|| minuscula.contains(HOY.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
					|| minuscula.contains(HOY.toString());
		}
	}

	// ------------------------------------------------------------------ extracción

	/** Líneas de texto del PDF (orden por posición), con los espacios normalizados y sin las vacías. */
	public static List<String> lineas(byte[] pdf) throws IOException {
		try (PDDocument documento = PDDocument.load(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(documento).lines().map(ContenidoPdf::normaliza).filter(l -> !l.isEmpty())
					.toList();
		}
	}

	/** Campos del AcroForm (nombre completo → valor), en el orden del árbol; vacío si no hay formulario. */
	public static Map<String, String> campos(byte[] pdf) throws IOException {
		Map<String, String> campos = new LinkedHashMap<>();
		try (PDDocument documento = PDDocument.load(pdf)) {
			PDAcroForm formulario = documento.getDocumentCatalog().getAcroForm();
			if (formulario != null) {
				for (PDField campo : formulario.getFieldTree()) {
					campos.put(campo.getFullyQualifiedName(), normaliza(campo.getValueAsString()));
				}
			}
		}
		return campos;
	}

	// ------------------------------------------------------------------ dorado

	/**
	 * Regenera (con {@code -Dpdf.contenido.regenerar=true}) o compara el contenido del PDF con su dorado
	 * {@code pdf-contenido/<informe>.txt}.
	 *
	 * @param notas comentarios para la cabecera del dorado (líneas volátiles, datos de prueba…)
	 */
	public static void comprobar(String informe, byte[] pdf, Presentacion presentacion, String... notas)
			throws IOException {
		List<String> lineas = lineas(pdf);
		Map<String, String> campos = campos(pdf);
		Path dorado = CARPETA.resolve(informe + ".txt");
		String volcado = System.getProperty("pdf.contenido.volcar");
		if (volcado != null && !volcado.isBlank()) {
			// Revisión a mano: -Dpdf.contenido.volcar=<carpeta> deja también cada PDF generado.
			Files.createDirectories(Path.of(volcado));
			Files.write(Path.of(volcado, informe + ".pdf"), pdf);
		}
		if (Boolean.getBoolean("pdf.contenido.regenerar")) {
			escribir(dorado, informe, lineas, campos, presentacion, notas);
			return;
		}
		assertTrue(Files.exists(dorado), "Falta el dorado " + dorado.toAbsolutePath()
				+ ": genéralo con -Dpdf.contenido.regenerar=true (con el código anterior al cambio)");
		comparar(informe, Files.readAllLines(dorado, StandardCharsets.UTF_8), lineas, campos);
	}

	private static void escribir(Path dorado, String informe, List<String> lineas, Map<String, String> campos,
			Presentacion presentacion, String... notas) throws IOException {
		StringBuilder texto = new StringBuilder();
		texto.append("# Contenido del PDF «").append(informe).append("» (caracterización, decisión 314).\n");
		texto.append("# Regla de comparación y formato: ContenidoPdf. «~ » = presentación (comprobación laxa).\n");
		for (String nota : notas) {
			texto.append("# ").append(nota).append('\n');
		}
		for (String linea : lineas) {
			if (linea.startsWith(COMENTARIO) || linea.startsWith(PRESENTACION.trim()) || linea.startsWith(CAMPO.trim())) {
				fail("Línea de datos con un prefijo reservado en " + informe + ": " + linea);
			}
			texto.append(presentacion.es(linea) ? PRESENTACION : "").append(linea).append('\n');
		}
		campos.forEach((nombre, valor) -> texto.append(CAMPO).append(nombre).append('=').append(valor).append('\n'));
		Files.createDirectories(dorado.getParent());
		Files.writeString(dorado, texto.toString(), StandardCharsets.UTF_8);
	}

	/** Aplica la regla de comparación (ver la documentación de la clase); falla con todas las diferencias. */
	static void comparar(String informe, List<String> dorado, List<String> lineas, Map<String, String> campos) {
		String nuevo = compacta(String.join("", lineas));
		String nuevoConEspacios = String.join(" ", lineas).toLowerCase(ES);
		Set<String> palabras = new java.util.HashSet<>();
		Matcher palabra = PALABRA.matcher(nuevoConEspacios);
		while (palabra.find()) {
			palabras.add(palabra.group());
		}

		List<String> errores = new ArrayList<>();
		int penultima = 0;
		int ultima = 0;
		for (int i = 0; i < dorado.size(); i++) {
			String linea = dorado.get(i);
			int numero = i + 1;
			if (linea.isBlank() || linea.startsWith(COMENTARIO)) {
				continue;
			}
			if (linea.startsWith(PRESENTACION)) {
				String falta = faltaPresentacion(linea.substring(PRESENTACION.length()), nuevoConEspacios, palabras);
				if (falta != null) {
					errores.add("línea " + numero + " (presentación) «" + linea + "»: falta " + falta);
				}
				continue;
			}
			if (linea.startsWith(CAMPO)) {
				String par = linea.substring(CAMPO.length());
				int igual = par.indexOf('=');
				String nombre = par.substring(0, igual);
				String valor = par.substring(igual + 1);
				if (!campos.containsKey(nombre)) {
					errores.add("línea " + numero + ": falta el campo del formulario «" + nombre + "»");
				} else if (!compacta(valor).equals(compacta(campos.get(nombre)))) {
					errores.add("línea " + numero + ": el campo «" + nombre + "» vale «" + campos.get(nombre)
							+ "» y no «" + valor + "»");
				}
				continue;
			}
			List<String> fichas = List.of(linea.toLowerCase(ES).split("[\\s\\u00A0\\u2007\\u202F]+"));
			int tramo = 2 * compacta(linea).length() + 40;
			int comienzo = buscarTramo(nuevo, fichas, penultima, tramo);
			if (comienzo < 0) {
				errores.add("línea " + numero + " «" + linea + "»: " + (buscarTramo(nuevo, fichas, 0, tramo) < 0
						? "no aparece (o sus datos están separados)" : "fuera de orden"));
			} else {
				penultima = ultima;
				ultima = comienzo;
			}
		}
		if (!errores.isEmpty()) {
			fail("Contenido perdido o desordenado en el PDF «" + informe + "» (" + errores.size() + "):\n  "
					+ String.join("\n  ", errores) + "\n--- texto nuevo ---\n" + String.join("\n", lineas)
					+ "\n--- campos nuevos ---\n" + campos);
		}
	}

	/**
	 * Comienzo del primer tramo de {@code texto} (compactado), desde {@code desde}, de {@code longitud} caracteres que
	 * contiene todas las fichas con sus repeticiones (en cualquier orden); -1 si no hay ninguno.
	 */
	private static int buscarTramo(String texto, List<String> fichas, int desde, int longitud) {
		Map<String, Integer> veces = new LinkedHashMap<>();
		for (String ficha : fichas) {
			if (!ficha.isEmpty()) {
				veces.merge(ficha, 1, Integer::sum);
			}
		}
		if (veces.isEmpty()) {
			return desde;
		}
		java.util.TreeSet<Integer> candidatos = new java.util.TreeSet<>();
		for (String ficha : veces.keySet()) {
			for (int p = texto.indexOf(ficha, desde); p >= 0; p = texto.indexOf(ficha, p + 1)) {
				candidatos.add(p);
			}
		}
		for (int comienzo : candidatos) {
			int fin = Math.min(texto.length(), comienzo + longitud);
			boolean todas = true;
			for (Map.Entry<String, Integer> ficha : veces.entrySet()) {
				int encontradas = 0;
				for (int p = texto.indexOf(ficha.getKey(), comienzo); p >= 0
						&& p + ficha.getKey().length() <= fin; p = texto.indexOf(ficha.getKey(), p + ficha.getKey().length())) {
					encontradas++;
				}
				if (encontradas < ficha.getValue()) {
					todas = false;
					break;
				}
			}
			if (todas) {
				return comienzo;
			}
		}
		return -1;
	}

	/** Qué dato de una línea de presentación falta en el texto nuevo; nulo si están todos. */
	private static String faltaPresentacion(String linea, String nuevo, Set<String> palabras) {
		String resto = linea.toLowerCase(ES);
		Matcher larga = FECHA_LARGA.matcher(resto);
		while (larga.find()) {
			LocalDate fecha = LocalDate.of(Integer.parseInt(larga.group(3)), mes(larga.group(2)),
					Integer.parseInt(larga.group(1)));
			if (!contieneFecha(nuevo, fecha)) {
				return "la fecha " + fecha;
			}
		}
		resto = FECHA_LARGA.matcher(resto).replaceAll(" ");
		Matcher corta = FECHA_CORTA.matcher(resto);
		while (corta.find()) {
			LocalDate fecha = LocalDate.of(Integer.parseInt(corta.group(3)), Integer.parseInt(corta.group(2)),
					Integer.parseInt(corta.group(1)));
			if (!contieneFecha(nuevo, fecha)) {
				return "la fecha " + fecha;
			}
		}
		resto = FECHA_CORTA.matcher(resto).replaceAll(" ");
		Matcher pagina = PAGINA.matcher(resto);
		while (pagina.find()) {
			Pattern numeros = Pattern.compile("(?<!\\d)" + pagina.group(1) + "\\D{1,15}?(?<!\\d)" + pagina.group(2)
					+ "(?!\\d)");
			if (!numeros.matcher(nuevo).find()) {
				return "la página " + pagina.group(1) + " de " + pagina.group(2);
			}
		}
		resto = PAGINA.matcher(resto).replaceAll(" ");
		Matcher palabra = PALABRA.matcher(resto);
		while (palabra.find()) {
			if (!VACIAS.contains(palabra.group()) && !palabras.contains(palabra.group())) {
				return "«" + palabra.group() + "»";
			}
		}
		return null;
	}

	private static boolean contieneFecha(String texto, LocalDate fecha) {
		for (String patron : List.of("d 'de' MMMM 'de' yyyy", "dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy",
				"dd.MM.yyyy", "d MMMM yyyy", "d MMM yyyy")) {
			if (texto.contains(fecha.format(DateTimeFormatter.ofPattern(patron, ES)).toLowerCase(ES))) {
				return true;
			}
		}
		return false;
	}

	private static int mes(String nombre) {
		return List.of(MESES.split("\\|")).indexOf(nombre) + 1;
	}

	private static String normaliza(String texto) {
		return texto == null ? "" : ESPACIOS.matcher(texto).replaceAll(" ").trim();
	}

	/** Contenido normalizado para comparar: minúsculas y sin espacios. */
	static String compacta(String texto) {
		return ESPACIOS.matcher(texto == null ? "" : texto).replaceAll("").toLowerCase(ES);
	}
}
