package com.taemoi.project.services.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.services.MarcaEscuela;

/**
 * Decisiones 309, 314 y 315: estética común de los PDF generados con HTML (OpenHTMLtoPDF). Reúne lo que comparten
 * todos: las fuentes embebidas, la cabecera (logo, nombre de la escuela, título, contexto y fecha de generación, con el
 * filete en el color de la escuela), el pie de cada página («Escuela · Informe» y «Página X de Y») y los estilos de
 * tablas, rótulos y estados. El certificado de cobros (PDFBox directo, editable) no pasa por el renderizador: dibuja
 * lo mismo con estas fuentes ({@link #fuente}) y estos colores en {@code TesoreriaServiceImpl}.
 *
 * <p>Fuentes: Montserrat Bold (títulos y rótulos) y Public Sans Regular, SemiBold y Bold (texto), TTF estáticos con
 * licencia OFL en {@code resources/fonts}. Familia de reserva: Helvetica (la estándar de PDF) para cualquier carácter
 * que no tengan.
 *
 * <p>El color de la escuela ({@link MarcaEscuela#colorPrimario()}) solo se usa en el filete bajo la cabecera.
 */
final class EstiloPdf {

	/** Familias registradas en el renderizador (nombres de {@code font-family} en el CSS). */
	static final String MONTSERRAT = "Montserrat";
	static final String PUBLIC_SANS = "Public Sans";

	/** Pila de fuentes del texto y de los títulos (con la de reserva). */
	static final String TEXTO = "'Public Sans', Helvetica, sans-serif";
	static final String TITULOS = "Montserrat, Helvetica, sans-serif";

	static final String TINTA = "#1a2230";
	static final String GRIS = "#5d6573";
	static final String GRIS_PIE = "#7a8290";
	static final String FILETE = "#e4e4df";
	static final String FILETE_CABECERA_TABLA = "#cfcfc8";
	static final String FONDO_CABECERA_TABLA = "#fafaf8";
	static final String OCRE = "#a14f0b";
	static final String FONDO_OCRE = "#fcf2e6";

	private static final Locale ES = Locale.of("es", "ES");
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);

	private record Fuente(String fichero, String familia, int peso) {
	}

	private static final List<Fuente> FUENTES = List.of(new Fuente("Montserrat-Bold.ttf", MONTSERRAT, 700),
			new Fuente("PublicSans-Regular.ttf", PUBLIC_SANS, 400),
			new Fuente("PublicSans-SemiBold.ttf", PUBLIC_SANS, 600),
			new Fuente("PublicSans-Bold.ttf", PUBLIC_SANS, 700));

	/** Bytes de cada TTF, leídos una vez del classpath. */
	private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();

	private EstiloPdf() {
	}

	// ------------------------------------------------------------------ renderizador

	/** Renderizador con las fuentes del panel registradas (embebidas como subconjunto en el PDF). */
	static PdfRendererBuilder builder() {
		PdfRendererBuilder builder = new PdfRendererBuilder();
		for (Fuente fuente : FUENTES) {
			byte[] ttf = bytes(fuente.fichero());
			builder.useFont(() -> new ByteArrayInputStream(ttf), fuente.familia(), fuente.peso(), FontStyle.NORMAL,
					true);
		}
		return builder;
	}

	/** TTF de una fuente del panel ({@code resources/fonts}) para cargarla con PDFBox (certificado de cobros). */
	static InputStream fuente(String fichero) {
		return new ByteArrayInputStream(bytes(fichero));
	}

	private static byte[] bytes(String fichero) {
		return BYTES.computeIfAbsent(fichero, f -> {
			try (InputStream entrada = EstiloPdf.class.getResourceAsStream("/fonts/" + f)) {
				if (entrada == null) {
					throw new IllegalStateException("Falta la fuente " + f + " en el classpath (resources/fonts)");
				}
				return entrada.readAllBytes();
			} catch (IOException e) {
				throw new UncheckedIOException("No se pudo leer la fuente " + f, e);
			}
		});
	}

	// ------------------------------------------------------------------ textos

	/** Fecha de generación de la cabecera («15 de octubre de 2026»). */
	static String fechaGeneracion() {
		return LocalDate.now().format(FECHA);
	}

	/** Texto escapado para HTML/XML. */
	static String esc(String valor) {
		return valor == null ? "" : valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}

	/**
	 * Cadena CSS entre comillas dobles, con todo lo que no sea letra o cifra ASCII, espacio o punto escapado en
	 * hexadecimal ({@code \26 }): vale dentro de un {@code <style>} de XHTML (sin {@code &}, {@code <} ni comillas
	 * sueltas).
	 */
	static String cadenaCss(String valor) {
		StringBuilder css = new StringBuilder("\"");
		String texto = valor == null ? "" : valor;
		texto.codePoints().forEach(c -> {
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '.') {
				css.appendCodePoint(c);
			} else {
				// El espacio final cierra el escape y CSS lo consume: «& Sur» no se queda en «&Sur».
				css.append('\\').append(Integer.toHexString(c)).append(' ');
			}
		});
		return css.append('"').toString();
	}

	// ------------------------------------------------------------------ CSS

	/**
	 * Pie de todas las páginas: a la izquierda «Escuela · Informe»; a la derecha «Página X de Y»; filete superior. Va
	 * en una regla {@code @page} propia, de modo que el informe puede fijar después sus márgenes con otra.
	 */
	static String pie(String escuela, String informe) {
		String texto = escuela == null || escuela.isBlank() ? informe : escuela + " · " + informe;
		String caja = "font-family: " + TEXTO + "; font-size: 7pt; color: " + GRIS_PIE + "; border-top: 0.5pt solid "
				+ FILETE + "; padding-top: 1.6mm; vertical-align: top;";
		return "@page { size: A4; margin: 14mm 14mm 16mm 14mm;"
				+ " @bottom-left { content: " + cadenaCss(texto) + "; " + caja + " text-align: left; }"
				+ " @bottom-center { content: \"\"; " + caja + " }"
				+ " @bottom-right { content: \"Página \" counter(page) \" de \" counter(pages); " + caja
				+ " text-align: right; white-space: nowrap; } }";
	}

	/**
	 * Estilos comunes: tipografía, cabecera, tablas, rótulos de grupo y estados. Las tablas: cabecera gris claro sin
	 * relleno de color, filetes finos entre filas, sin cebra; {@code .num} alinea a la derecha cifras, importes y
	 * fechas.
	 */
	static String cssComun() {
		return "* { box-sizing: border-box; }"
				+ " body { font-family: " + TEXTO + "; font-size: 8.5pt; line-height: 1.35; color: " + TINTA
				+ "; margin: 0; }"
				// Cabecera
				+ " .cab { border-bottom: 2pt solid " + TINTA + "; padding-bottom: 2.4mm; margin-bottom: 5mm;"
				+ " page-break-inside: avoid; page-break-after: avoid; }"
				+ " table.cab-fila { width: 100%; border-collapse: collapse; margin: 0; page-break-inside: avoid; }"
				+ " table.cab-fila td { border: none; padding: 0; background: none; vertical-align: middle;"
				+ " text-align: left; font-size: 8pt; height: auto; }"
				+ " table.cab-fila td.cab-logo { width: 1%; padding-right: 3.5mm; }"
				+ " table.cab-fila td.cab-logo img { height: 14mm; width: auto; display: block; }"
				+ " table.cab-fila td.cab-derecha { width: 1%; padding-left: 4mm; text-align: right; vertical-align: top;"
				+ " white-space: nowrap; color: " + GRIS + "; font-size: 8pt; }"
				+ " .cab-escuela { font-family: " + TITULOS + "; font-weight: 700; font-size: 13pt; line-height: 1.2;"
				+ " color: " + TINTA + "; margin: 0; }"
				+ " .cab-escuela .cab-subescuela { font-family: " + TEXTO + "; font-weight: 600; font-size: 10pt;"
				+ " color: " + GRIS + "; }"
				+ " .cab-informe { font-family: " + TEXTO + "; font-weight: 600; font-size: 10.5pt; line-height: 1.3;"
				+ " color: " + TINTA + "; margin: 0.8mm 0 0 0; }"
				+ " .cab-contexto { font-size: 8pt; line-height: 1.3; color: " + GRIS + "; margin: 0.6mm 0 0 0; }"
				// Tablas
				+ " table { width: 100%; border-collapse: collapse; margin-top: 1.5mm; page-break-inside: auto; }"
				+ " thead { display: table-header-group; }"
				+ " tr { page-break-inside: avoid; }"
				+ " th { background: " + FONDO_CABECERA_TABLA + "; color: " + GRIS + "; font-family: " + TEXTO
				+ "; font-weight: 600; font-size: 7.5pt; line-height: 1.25; text-align: left; vertical-align: bottom;"
				+ " padding: 1.5mm 2mm; border: none; border-bottom: 1pt solid " + FILETE_CABECERA_TABLA + "; }"
				+ " td { font-size: 8.5pt; text-align: left; vertical-align: middle; padding: 1.4mm 2mm; border: none;"
				+ " border-bottom: 0.5pt solid " + FILETE + "; }"
				+ " th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }"
				// Rótulos de grupo y recuentos
				+ " .rotulo { font-family: " + TITULOS + "; font-weight: 700; font-size: 10.5pt; line-height: 1.25;"
				+ " color: " + TINTA + "; margin: 5mm 0 1.5mm 0; page-break-after: avoid; }"
				+ " .recuento { font-family: " + TEXTO + "; font-weight: 400; font-size: 8pt; color: " + GRIS + "; }"
				// Estados: ocre solo para importes y estados pendientes de dinero; el resto, neutros.
				+ " .pendiente-dinero { color: " + OCRE + "; background-color: " + FONDO_OCRE + "; font-weight: 600;"
				+ " padding: 0.3mm 1.6mm; border-radius: 1.2mm; white-space: nowrap; }"
				+ " .estado { font-weight: 600; }"
				+ " .vacio { color: " + GRIS + "; margin: 4mm 0; }";
	}

	// ------------------------------------------------------------------ cabecera

	/**
	 * Cabecera común: logo a la izquierda (si lo hay), nombre de la escuela, título y, si hay, la línea de contexto
	 * (filtros); a la derecha, la fecha de generación (u otro dato del documento). Debajo, el filete de 2pt en el color
	 * de la escuela. Todos los textos llegan ya escapados para HTML.
	 *
	 * @param colorPrimario   color de la escuela ({@code #RRGGBB}, ya validado)
	 * @param logo            data URI del logo; vacío o nulo sin logo
	 * @param escuelaHtml     nombre de la escuela
	 * @param subescuelaHtml  segunda parte del nombre (Moiskimdo en la asistencia: «Tae Kwon Do»); nulo si no hay
	 * @param tituloHtml      título del documento; nulo o vacío si no hay
	 * @param contextoHtml    filtros o contexto; nulo o vacío si no hay
	 * @param derechaHtml     texto de la derecha (la fecha de generación); nulo o vacío si no hay
	 */
	static String cabecera(String colorPrimario, String logo, String escuelaHtml, String subescuelaHtml,
			String tituloHtml, String contextoHtml, String derechaHtml) {
		StringBuilder html = new StringBuilder();
		html.append("<div class='cab' style='border-bottom-color: ").append(colorPrimario).append(";'>");
		html.append("<table class='cab-fila'><tr>");
		if (logo != null && !logo.isEmpty()) {
			html.append("<td class='cab-logo'><img src='").append(logo).append("' alt='Logo' /></td>");
		}
		html.append("<td class='cab-textos'>");
		html.append("<div class='cab-escuela'>").append(escuelaHtml == null ? "" : escuelaHtml);
		if (subescuelaHtml != null && !subescuelaHtml.isEmpty()) {
			html.append(" <span class='cab-subescuela'>").append(subescuelaHtml).append("</span>");
		}
		html.append("</div>");
		if (tituloHtml != null && !tituloHtml.isEmpty()) {
			html.append("<div class='cab-informe'>").append(tituloHtml).append("</div>");
		}
		if (contextoHtml != null && !contextoHtml.isEmpty()) {
			html.append("<div class='cab-contexto'>").append(contextoHtml).append("</div>");
		}
		html.append("</td>");
		if (derechaHtml != null && !derechaHtml.isEmpty()) {
			html.append("<td class='cab-derecha'>").append(derechaHtml).append("</td>");
		}
		html.append("</tr></table></div>");
		return html.toString();
	}

	/** Cabecera de un informe: logo y nombre de los informes de la escuela, y la fecha de generación a la derecha. */
	static String cabeceraInforme(MarcaEscuela marca, String tituloHtml, String contextoHtml) {
		return cabecera(marca.colorPrimario(), marca.logoInformes(), marca.cabeceraInformesHtml(), null, tituloHtml,
				contextoHtml, esc(fechaGeneracion()));
	}

	/** Documento HTML completo: estilos comunes, pie, estilos propios y cuerpo. */
	static String documento(String escuela, String informe, String estilosPropios, String cuerpo) {
		return "<!DOCTYPE html><html><head><meta charset='UTF-8' /><style>" + pie(escuela, informe) + cssComun()
				+ (estilosPropios == null ? "" : estilosPropios) + "</style></head><body>" + cuerpo + "</body></html>";
	}
}
