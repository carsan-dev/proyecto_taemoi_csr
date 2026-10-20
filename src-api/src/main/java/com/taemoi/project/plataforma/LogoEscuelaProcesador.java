package com.taemoi.project.plataforma;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.batik.bridge.ExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadScriptSecurity;
import org.apache.batik.bridge.ScriptSecurity;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.ParsedURL;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Pieza E7-7a (decisión 212): valida el logo que sube el superadmin y lo deja en un PNG limpio (solo se guarda ese PNG).
 *
 * <ul>
 * <li>Tipo por CONTENIDO (firma de PNG o JPEG; SVG = XML cuya raíz es {@code <svg>} del espacio de nombres SVG), nunca
 * por extensión ni por el {@code Content-Type} declarado. Otro tipo → 400.</li>
 * <li>Como mucho {@link #TAMANO_MAXIMO} bytes; PNG/JPEG entre {@link #LADO_MINIMO} y {@link #LADO_MAXIMO_ENTRADA}
 * píxeles por lado (leídos de la cabecera, antes de decodificar). El resultado cabe en {@link #LADO_MAXIMO_SALIDA}.</li>
 * <li>SVG sin XXE ni SSRF: se rechaza cualquier {@code DOCTYPE} (y con él toda entidad), {@code <script>},
 * {@code <foreignObject>}, atributos {@code on…}, enlaces ({@code href}) que no sean internos ({@code #id}) y
 * {@code url(…)} o {@code @import} externos; después Batik rasteriza con la carga de recursos externos y de scripts
 * DESACTIVADA (segunda barrera: ni ficheros locales ni peticiones a URLs).</li>
 * </ul>
 * Cualquier rechazo es {@link IllegalArgumentException} (400 en {@code GlobalExceptionHandler}).
 */
@Component
public class LogoEscuelaProcesador {

	public static final int TAMANO_MAXIMO = 1024 * 1024;
	public static final int LADO_MINIMO = 16;
	public static final int LADO_MAXIMO_ENTRADA = 4096;
	public static final int LADO_MAXIMO_SALIDA = 600;

	static final String TIPO_NO_PERMITIDO = "El logo debe ser una imagen PNG, JPG o SVG.";
	static final String SVG_COMPLEJO = "El SVG es demasiado complejo.";
	/** Límites del SVG antes de rasterizar (contra la amplificación con {@code <use>} y el anidamiento). */
	static final int MAXIMO_USE = 100;
	static final int MAXIMO_ANIDAMIENTO = 40;
	static final int MAXIMO_ELEMENTOS = 10_000;
	static final String SVG_NO_PERMITIDO = "El SVG no se admite: no puede declarar DOCTYPE ni entidades, ni llevar "
			+ "scripts ni enlaces o recursos externos.";

	private static final String ESPACIO_SVG = "http://www.w3.org/2000/svg";
	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final Set<String> ELEMENTOS_PROHIBIDOS = Set.of("script", "foreignobject", "iframe", "embed",
			"object", "handler", "listener");
	/** {@code url(…)} cuyo destino no es un fragmento interno ({@code #id}). */
	private static final Pattern URL_CSS = Pattern.compile("url\\s*\\(\\s*['\"]?\\s*([^)'\"\\s]*)",
			Pattern.CASE_INSENSITIVE);

	/** PNG resultante y sus dimensiones. */
	public record LogoProcesado(byte[] png, int ancho, int alto) {
	}

	public LogoProcesado procesar(byte[] contenido) {
		if (contenido == null || contenido.length == 0) {
			throw new IllegalArgumentException("Falta el fichero del logo.");
		}
		if (contenido.length > TAMANO_MAXIMO) {
			throw new IllegalArgumentException("El logo no puede superar " + (TAMANO_MAXIMO / 1024) + " KB.");
		}
		BufferedImage imagen;
		if (empiezaPor(contenido, FIRMA_PNG) || esJpeg(contenido)) {
			imagen = leerRaster(contenido);
		} else if (pareceXml(contenido)) {
			imagen = leerSvg(contenido);
		} else {
			throw new IllegalArgumentException(TIPO_NO_PERMITIDO);
		}
		BufferedImage reducida = reducir(imagen);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(reducida, "png", png)) {
				throw new IllegalStateException("No hay codificador PNG");
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("No se pudo convertir el logo a PNG.");
		}
		return new LogoProcesado(png.toByteArray(), reducida.getWidth(), reducida.getHeight());
	}

	// ------------------------------------------------------------------ PNG y JPEG

	private static BufferedImage leerRaster(byte[] contenido) {
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
			Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
			if (!lectores.hasNext()) {
				throw new IllegalArgumentException(TIPO_NO_PERMITIDO);
			}
			ImageReader lector = lectores.next();
			try {
				lector.setInput(entrada, true, true);
				String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
				if (!formato.equals("png") && !formato.equals("jpeg") && !formato.equals("jpg")) {
					throw new IllegalArgumentException(TIPO_NO_PERMITIDO);
				}
				comprobarLados(lector.getWidth(0), lector.getHeight(0));
				BufferedImage imagen = lector.read(0);
				if (imagen == null) {
					throw new IllegalArgumentException(TIPO_NO_PERMITIDO);
				}
				return imagen;
			} finally {
				lector.dispose();
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof IllegalArgumentException iae) {
				throw iae;
			}
			throw new IllegalArgumentException("No se pudo leer la imagen del logo.");
		}
	}

	private static void comprobarLados(int ancho, int alto) {
		if (ancho < LADO_MINIMO || alto < LADO_MINIMO || ancho > LADO_MAXIMO_ENTRADA || alto > LADO_MAXIMO_ENTRADA) {
			throw new IllegalArgumentException("El logo debe medir entre " + LADO_MINIMO + " y " + LADO_MAXIMO_ENTRADA
					+ " píxeles por lado.");
		}
	}

	/** Cabe en {@link #LADO_MAXIMO_SALIDA} sin deformarse; con transparencia (ARGB). */
	private static BufferedImage reducir(BufferedImage original) {
		int ancho = original.getWidth();
		int alto = original.getHeight();
		double escala = Math.min(1.0, Math.min((double) LADO_MAXIMO_SALIDA / ancho, (double) LADO_MAXIMO_SALIDA / alto));
		int nuevoAncho = Math.max(1, (int) Math.round(ancho * escala));
		int nuevoAlto = Math.max(1, (int) Math.round(alto * escala));
		BufferedImage salida = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = salida.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
		g.dispose();
		return salida;
	}

	// ------------------------------------------------------------------ SVG

	private static BufferedImage leerSvg(byte[] contenido) {
		Document documento = parsearSeguro(contenido);
		Element raiz = documento.getDocumentElement();
		if (raiz == null || !"svg".equals(raiz.getLocalName()) || !ESPACIO_SVG.equals(raiz.getNamespaceURI())) {
			throw new IllegalArgumentException(TIPO_NO_PERMITIDO);
		}
		// Fuera de la raíz: instrucciones de proceso (<?xml-stylesheet href=…?> que Batik cargaría).
		NodeList nivelDocumento = documento.getChildNodes();
		for (int i = 0; i < nivelDocumento.getLength(); i++) {
			short tipo = nivelDocumento.item(i).getNodeType();
			if (tipo == Node.PROCESSING_INSTRUCTION_NODE || tipo == Node.DOCUMENT_TYPE_NODE) {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			}
		}
		revisar(raiz);
		limitarComplejidad(raiz);
		return rasterizarSvg(contenido);
	}

	/** Parser sin DOCTYPE (y por tanto sin entidades internas ni externas), sin DTD ni esquemas externos, sin XInclude. */
	private static Document parsearSeguro(byte[] contenido) {
		try {
			DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
			fabrica.setNamespaceAware(true);
			fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
			fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			fabrica.setXIncludeAware(false);
			fabrica.setExpandEntityReferences(false);
			DocumentBuilder constructor = fabrica.newDocumentBuilder();
			constructor.setEntityResolver((publico, sistema) -> {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			});
			constructor.setErrorHandler(null);
			// Bytes, no texto: la codificación se detecta igual que la detectará Batik.
			return constructor.parse(new InputSource(new ByteArrayInputStream(contenido)));
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			String mensaje = e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.ROOT);
			throw new IllegalArgumentException(mensaje.contains("DOCTYPE") ? SVG_NO_PERMITIDO : TIPO_NO_PERMITIDO);
		}
	}

	/**
	 * Como mucho {@link #MAXIMO_ELEMENTOS} elementos, {@link #MAXIMO_USE} {@code <use>} y {@link #MAXIMO_ANIDAMIENTO}
	 * niveles; y ningún {@code <use>} apunta a un elemento que contenga otro {@code <use>} (sin cadenas que se
	 * multipliquen al dibujar). Si no, 400.
	 */
	private static void limitarComplejidad(Element raiz) {
		java.util.Map<String, Element> porId = new java.util.HashMap<>();
		java.util.List<Element> usos = new java.util.ArrayList<>();
		int[] elementos = { 0 };
		recorrer(raiz, 1, porId, usos, elementos);
		if (usos.size() > MAXIMO_USE) {
			throw new IllegalArgumentException(SVG_COMPLEJO);
		}
		for (Element uso : usos) {
			String destino = enlace(uso);
			Element apuntado = destino == null ? null : porId.get(destino);
			if (apuntado != null && (esUse(apuntado) || apuntado.getElementsByTagNameNS(ESPACIO_SVG, "use").getLength() > 0)) {
				throw new IllegalArgumentException(SVG_COMPLEJO);
			}
		}
	}

	private static void recorrer(Element elemento, int nivel, java.util.Map<String, Element> porId,
			java.util.List<Element> usos, int[] elementos) {
		if (nivel > MAXIMO_ANIDAMIENTO || ++elementos[0] > MAXIMO_ELEMENTOS) {
			throw new IllegalArgumentException(SVG_COMPLEJO);
		}
		String id = elemento.getAttribute("id");
		if (!id.isEmpty()) {
			porId.putIfAbsent(id, elemento);
		}
		if (esUse(elemento)) {
			usos.add(elemento);
		}
		NodeList hijos = elemento.getChildNodes();
		for (int i = 0; i < hijos.getLength(); i++) {
			if (hijos.item(i).getNodeType() == Node.ELEMENT_NODE) {
				recorrer((Element) hijos.item(i), nivel + 1, porId, usos, elementos);
			}
		}
	}

	private static boolean esUse(Element elemento) {
		return "use".equalsIgnoreCase(elemento.getLocalName());
	}

	/** Destino interno ({@code #id}) del {@code href} o {@code xlink:href} de un elemento; {@code null} si no tiene. */
	private static String enlace(Element elemento) {
		NamedNodeMap atributos = elemento.getAttributes();
		for (int i = 0; i < atributos.getLength(); i++) {
			Node atributo = atributos.item(i);
			String local = atributo.getLocalName() == null ? atributo.getNodeName() : atributo.getLocalName();
			if ("href".equalsIgnoreCase(local)) {
				String valor = atributo.getNodeValue().trim();
				return valor.startsWith("#") ? valor.substring(1) : null;
			}
		}
		return null;
	}

	/** Recorre todo el árbol: elementos prohibidos, atributos de evento, enlaces y {@code url()} externos. */
	private static void revisar(Element elemento) {
		String nombre = (elemento.getLocalName() == null ? elemento.getNodeName() : elemento.getLocalName())
				.toLowerCase(Locale.ROOT);
		if (ELEMENTOS_PROHIBIDOS.contains(nombre)) {
			throw new IllegalArgumentException(SVG_NO_PERMITIDO);
		}
		NamedNodeMap atributos = elemento.getAttributes();
		for (int i = 0; i < atributos.getLength(); i++) {
			Node atributo = atributos.item(i);
			String local = (atributo.getLocalName() == null ? atributo.getNodeName() : atributo.getLocalName())
					.toLowerCase(Locale.ROOT);
			String valor = atributo.getNodeValue() == null ? "" : atributo.getNodeValue();
			if (local.startsWith("on")) {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			}
			if (local.equals("href") && !valor.trim().startsWith("#")) {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			}
			revisarCss(valor);
		}
		if ("style".equals(nombre)) {
			revisarCss(elemento.getTextContent());
		}
		NodeList hijos = elemento.getChildNodes();
		for (int i = 0; i < hijos.getLength(); i++) {
			Node hijo = hijos.item(i);
			if (hijo.getNodeType() == Node.ELEMENT_NODE) {
				revisar((Element) hijo);
			} else if (hijo.getNodeType() == Node.ENTITY_REFERENCE_NODE
					|| hijo.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			}
		}
	}

	private static void revisarCss(String css) {
		if (css == null || css.isEmpty()) {
			return;
		}
		if (css.toLowerCase(Locale.ROOT).contains("@import")) {
			throw new IllegalArgumentException(SVG_NO_PERMITIDO);
		}
		Matcher url = URL_CSS.matcher(css);
		while (url.find()) {
			if (!url.group(1).startsWith("#")) {
				throw new IllegalArgumentException(SVG_NO_PERMITIDO);
			}
		}
	}

	/**
	 * Batik con la carga de recursos externos y de scripts desactivada (también para los que hubieran pasado la
	 * revisión). Visible para los tests de esta segunda barrera.
	 */
	static BufferedImage rasterizarSvg(byte[] contenido) {
		PNGTranscoder transcoder = new TranscoderSinRecursosExternos();
		transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, Boolean.FALSE);
		transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.FALSE);
		transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) LADO_MAXIMO_SALIDA);
		transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_WIDTH, (float) LADO_MAXIMO_SALIDA);
		transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_HEIGHT, (float) LADO_MAXIMO_SALIDA);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		try {
			transcoder.transcode(new TranscoderInput(new ByteArrayInputStream(contenido)), new TranscoderOutput(png));
			BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png.toByteArray()));
			if (imagen == null) {
				throw new IllegalArgumentException("No se pudo convertir el SVG.");
			}
			return imagen;
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("No se pudo convertir el SVG.");
		}
	}

	/** {@link PNGTranscoder} cuyo agente niega TODO recurso externo y todo script. */
	private static final class TranscoderSinRecursosExternos extends PNGTranscoder {
		@Override
		protected UserAgent createUserAgent() {
			return new SVGAbstractTranscoderUserAgent() {
				@Override
				public ExternalResourceSecurity getExternalResourceSecurity(ParsedURL recurso, ParsedURL documento) {
					return new NoLoadExternalResourceSecurity();
				}

				@Override
				public ScriptSecurity getScriptSecurity(String tipo, ParsedURL script, ParsedURL documento) {
					return new NoLoadScriptSecurity(tipo);
				}

				@Override
				public void checkLoadExternalResource(ParsedURL recurso, ParsedURL documento) {
					throw new SecurityException("Recurso externo no permitido");
				}

				@Override
				public void checkLoadScript(String tipo, ParsedURL script, ParsedURL documento) {
					throw new SecurityException("Script no permitido");
				}
			};
		}
	}

	// ------------------------------------------------------------------ firmas

	private static boolean empiezaPor(byte[] contenido, byte[] firma) {
		if (contenido.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if (contenido[i] != firma[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean esJpeg(byte[] contenido) {
		return contenido.length >= 3 && (contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8
				&& (contenido[2] & 0xFF) == 0xFF;
	}

	/** Texto que empieza (tras BOM y espacios) por {@code <}: candidato a SVG; el parser decide. */
	private static boolean pareceXml(byte[] contenido) {
		int i = 0;
		if (contenido.length >= 3 && (contenido[0] & 0xFF) == 0xEF && (contenido[1] & 0xFF) == 0xBB
				&& (contenido[2] & 0xFF) == 0xBF) {
			i = 3;
		}
		while (i < contenido.length && (contenido[i] == ' ' || contenido[i] == '\t' || contenido[i] == '\r'
				|| contenido[i] == '\n')) {
			i++;
		}
		return i < contenido.length && contenido[i] == '<';
	}
}
