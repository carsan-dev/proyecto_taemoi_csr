package com.taemoi.project.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo de las claves de {@code web_bloque} que escribe el editor del contenido de la web (pieza W12a,
 * fase4-w12-plan.md). Lo fija el boceto aprobado {@code fase4-web/boceto-panel-contenido-web-comarcal.html} (d517,
 * d525): los textos de las 7 páginas de su {@code TX} (d519) con su máximo y si son obligatorios, y los campos de los
 * cinco tipos de bloque (d518) de su {@code CAMPOS}. Los mensajes de error del formulario de preinscripción no son
 * editables (d527) y no tienen clave. Una clave fuera del catálogo no se guarda (400).
 *
 * <ul>
 * <li>Cabeceras de sección ({@link Tipo#CABECERA}, sin texto ni imagen): llevan el {@code orden} de la sección y si está
 * {@code oculto}. Movibles: {@code presentacion}, {@code profesores}, {@code galeria}, {@code sedes} (Fotos de las sedes)
 * y {@code texto_foto.<n>} (repetible). Fijas: {@code disciplinas}, {@code eventos} y {@code tarifas} (solo el orden;
 * nunca se ocultan).</li>
 * <li>Imágenes ({@link Tipo#IMAGEN}): el texto es su descripción (obligatoria, hasta {@value #MAXIMO_DESCRIPCION}).</li>
 * <li>Sedes de un profesor ({@link Tipo#SEDES}, d520): ids de sedes de la escuela separados por comas.</li>
 * <li>{@code <n>}: de 1 a 99; {@code fila<n>} y {@code punto<n>}: de 1 a 9; {@code <codigo>}: el código de una disciplina
 * de la escuela en minúsculas (como lo lee el tema). La clave entera, hasta {@value #MAXIMO_CLAVE} (la columna).</li>
 * <li>Obligatorio: si la clave se guarda, no puede ir vacía (vacía, el tema no la pinta). Si no se guarda, el tema pone
 * su texto por defecto. La portada es la excepción: su titular y su foto tienen que estar (boceto, {@code errores()}).</li>
 * </ul>
 */
public final class ClavesWeb {

	public enum Tipo {
		CABECERA, TEXTO, IMAGEN, SEDES
	}

	/**
	 * @param seccion    cabecera de la sección movible a la que pertenece (la que se puede ocultar); {@code null} si no es
	 *                   de ninguna (textos de las páginas, portada, secciones fijas).
	 * @param disciplina código de la disciplina en minúsculas en {@code disciplina.<codigo>.*}; {@code null} en las demás.
	 * @param profesor   número del profesor en {@code profesor.<n>.*}; {@code null} en las demás.
	 */
	public record Clave(String clave, Tipo tipo, int maximo, boolean obligatorio, String seccion, String disciplina,
			Integer profesor) {

		public boolean esCabecera() {
			return tipo == Tipo.CABECERA;
		}

		public boolean esImagen() {
			return tipo == Tipo.IMAGEN;
		}

		/** {@code profesor.<n>.nombre}: la fila que lleva la constancia del permiso. */
		public boolean esNombreDeProfesor() {
			return profesor != null && clave.endsWith(".nombre");
		}
	}

	public static final int MAXIMO_CLAVE = 60;
	public static final int MAXIMO_DESCRIPCION = 150;
	public static final int MAXIMO_GALERIA = 6;
	public static final int MAXIMO_SEDES_PROFESOR = 300;

	/** Secciones fijas del tema: se pueden mover, no ocultar. */
	public static final Set<String> SECCIONES_FIJAS = Set.of("disciplinas", "eventos", "tarifas");
	/** Secciones movibles que solo pueden estar una vez. */
	public static final Set<String> SECCIONES_UNICAS = Set.of("presentacion", "profesores", "galeria", "sedes");
	/** Lo que tiene que estar siempre (la portada no se puede quitar). */
	public static final Set<String> SIEMPRE = Set.of("portada.titular", "portada.foto");

	private static final String N = "([1-9][0-9]?)";
	private static final Pattern TEXTO_FOTO = Pattern
			.compile("^(texto_foto\\." + N + ")(?:\\.(rotulo|titulo|texto|foto))?$");
	private static final Pattern PROFESOR = Pattern.compile("^profesor\\." + N + "\\.(nombre|cargo|foto|sedes)$");
	private static final Pattern GALERIA = Pattern.compile("^galeria\\." + N + "$");
	private static final Pattern DISCIPLINA = Pattern
			.compile("^disciplina\\.([a-z0-9_]{1,40})\\.(descripcion|foto|fila([1-9])\\.(rotulo|texto))$");
	private static final Pattern PUNTO_TARIFAS = Pattern.compile("^tarifas\\.punto([1-9])$");

	/** Claves fijas: cabeceras, campos de los bloques y de la portada y textos de las páginas. */
	private static final Map<String, Clave> FIJAS = new HashMap<>();
	/** Textos de las páginas (boceto, {@code TX}): clave, máximo y si es obligatorio. */
	private static final String TEXTOS = """
			menu.horarios                        20  obligatorio
			menu.sedes                           20  obligatorio
			menu.tarifas                         20  obligatorio
			menu.eventos                         20  obligatorio
			menu.contacto                        20  obligatorio
			cabecera.boton                       24  obligatorio
			cabecera.menu                        12  obligatorio
			portada.boton_horarios               24  obligatorio
			portada.enlace_tarifas               24  obligatorio
			horarios.titulo                      24  obligatorio
			horarios.apoyo                      120  opcional
			sedes.proxima_rotulo                 24  opcional
			horarios.ver_sede                    24  obligatorio
			horarios.como_llegar                 24  obligatorio
			horarios.lleno                       24  obligatorio
			horarios.espera                      24  obligatorio
			horarios.boton                       24  obligatorio
			horarios.boton_espera                32  obligatorio
			disciplinas.titulo                   24  obligatorio
			sedes.ver_sede                       24  obligatorio
			eventos.rotulo                       24  obligatorio
			eventos.titulo                       60  obligatorio
			eventos.enlace_ver                   24  obligatorio
			tarifas.titulo                       24  obligatorio
			tarifas.al_mes                       16  obligatorio
			tarifas.igual                        40  opcional
			tarifas.boton_preinscripcion         24  obligatorio
			contacto.rotulo                      24  obligatorio
			contacto.titulo                      60  obligatorio
			contacto.correo_rotulo               24  obligatorio
			contacto.telefono_rotulo             24  obligatorio
			contacto.sedes_titulo                40  obligatorio
			pie.aviso_legal                      24  obligatorio
			pie.privacidad                       24  obligatorio
			pie.cookies                          24  obligatorio
			pie.creditos                        300  opcional
			sede.anterior                        16  obligatorio
			sede.siguiente                       16  obligatorio
			sede.preinscribirme_en               40  obligatorio
			sede.ver_grupos                      24  obligatorio
			sede.rotulo_horarios                 40  obligatorio
			sede.titulo_horarios                 60  obligatorio
			sede.cabeza_hora                     20  obligatorio
			sede.cabeza_grupo                    20  obligatorio
			sede.cabeza_dias                     20  obligatorio
			sede.leyenda_quedan                  60  obligatorio
			sede.leyenda_lleno                   60  obligatorio
			sede.leyenda_espera                  60  obligatorio
			sede.rotulo_llegar                   40  obligatorio
			sede.titulo_llegar                   60  obligatorio
			sede.ficha_lugar                     24  obligatorio
			sede.ficha_entrada                   24  obligatorio
			sede.ficha_telefono                  24  obligatorio
			sede.abrir_mapa                      24  obligatorio
			sede.rotulo_tarifa                   24  obligatorio
			sede.rotulo_ruta                     24  obligatorio
			sede.titulo_ruta                     60  obligatorio
			sede.estas_aqui                      16  obligatorio
			tarifas.cuota_igual                  80  obligatorio
			tarifas.tambien                      40  obligatorio
			tarifas.incluye_aparte               60  obligatorio
			tarifas.ver_horarios                 24  obligatorio
			tarifas.rotulo_cuadro                24  obligatorio
			tarifas.titulo_cuadro                60  obligatorio
			tarifas.leyenda_cuadro               48  obligatorio
			tarifas.col_tarifa                   20  obligatorio
			tarifas.col_quien                    20  obligatorio
			tarifas.col_importe                  20  obligatorio
			tarifas.cada_alumno                  40  obligatorio
			tarifas.hermanos                     40  obligatorio
			tarifas.pie_cuadro                  160  opcional
			tarifas.rotulo_sede                  24  obligatorio
			tarifas.titulo_sede                  60  obligatorio
			tarifas.rotulo_aparte                24  obligatorio
			tarifas.titulo_aparte                60  obligatorio
			tarifas.licencia                     32  obligatorio
			tarifas.examen                       32  obligatorio
			tarifas.examen_desde                 40  obligatorio
			tarifas.nota_aparte                 160  opcional
			eventos.pag_titulo                   40  obligatorio
			eventos.pag_entrada                 160  opcional
			eventos.proximos                     24  obligatorio
			eventos.n_eventos                    24  obligatorio
			eventos.anteriores                   24  obligatorio
			evento.volver                        24  obligatorio
			evento.documentos                    24  obligatorio
			evento.galeria                       24  obligatorio
			evento.n_fotos                       24  obligatorio
			legal.titulo                         40  obligatorio
			legal.entrada                       200  opcional
			legal.actualizada                    30  obligatorio
			legal.en_esta_pagina                 24  obligatorio
			pre.atras                            16  obligatorio
			pre.paso_n                           24  obligatorio
			pre.paso_pueblo                      16  obligatorio
			pre.paso_grupo                       16  obligatorio
			pre.paso_datos                       16  obligatorio
			pre.titulo_pueblo                    40  obligatorio
			pre.pista_pueblo                    160  opcional
			pre.continuar                        24  obligatorio
			pre.titulo_grupo                     40  obligatorio
			pre.cambiar_pueblo                   24  obligatorio
			pre.fecha_leyenda                    60  obligatorio
			pre.fecha_pista                     160  opcional
			pre.dia                              12  obligatorio
			pre.mes                              12  obligatorio
			pre.anio                             12  obligatorio
			pre.ver_grupos                       24  obligatorio
			pre.espera_fecha                    120  opcional
			pre.titulo_datos                     40  obligatorio
			pre.pista_datos                     200  opcional
			pre.bloque_alumno                    40  obligatorio
			pre.nombre                           24  obligatorio
			pre.apellidos                        24  obligatorio
			pre.bloque_contacto                  40  obligatorio
			pre.telefono                         24  obligatorio
			pre.email                            24  obligatorio
			pre.email_pista                     120  opcional
			pre.bloque_normas                    40  obligatorio
			pre.acepto                           80  obligatorio
			pre.fotos                            80  obligatorio
			pre.bloque_firma                     40  obligatorio
			pre.firma_pista                      80  opcional
			pre.enviar                           32  obligatorio
			pre.fin_plaza                        40  obligatorio
			pre.referencia                       24  obligatorio
			pre.estado_plaza                    160  obligatorio
			pre.que_ahora                        40  obligatorio
			pre.paso_correo                      40  obligatorio
			pre.paso_correo_texto               200  opcional
			pre.paso_revisa                      40  obligatorio
			pre.paso_revisa_texto               200  opcional
			pre.descargar                        40  obligatorio
			pre.volver_inicio                    24  obligatorio
			pre.resumen_titulo                   32  obligatorio
			pre.resumen_pueblo                   16  obligatorio
			pre.resumen_grupo                    16  obligatorio
			pre.resumen_dias                     16  obligatorio
			pre.sin_elegir                       24  obligatorio
			pre.tarifa_igual                     80  opcional
			legal.ficha_titular                  24  obligatorio
			legal.ficha_cif                      24  obligatorio
			legal.ficha_domicilio                24  obligatorio
			legal.ficha_correo                   24  obligatorio
			sede.sin_clases                      40  obligatorio
			tarifas.vacio_titular                40  obligatorio
			tarifas.vacio_texto                 160  obligatorio
			tarifas.vacio_correo                 24  obligatorio
			tarifas.vacio_telefono               24  obligatorio
			eventos.sin_proximos_titular         40  obligatorio
			eventos.sin_proximos_texto          160  opcional
			eventos.ver_anteriores               40  obligatorio
			eventos.un_evento                    24  obligatorio
			eventos.sin_anteriores              120  obligatorio
			evento.una_foto                      24  obligatorio
			pre.vacio_titulo                     60  obligatorio
			pre.vacio_otras                     160  opcional
			pre.vacio_llamar                     80  obligatorio
			pre.vacio_llamar_fin                 40  obligatorio
			pre.revisar_fecha                    40  obligatorio
			pre.espera_titulo                    80  obligatorio
			pre.espera_texto                    160  opcional
			pre.enviar_espera                    48  obligatorio
			pre.fin_espera                       40  obligatorio
			pre.estado_espera                   160  obligatorio
			pre.paso_correo_espera              200  opcional
			legal.aviso                          40  obligatorio
			legal.aviso.p0                     1200  opcional
			legal.aviso.h1                       80  obligatorio
			legal.aviso.p1                     1200  opcional
			legal.aviso.h2                       80  obligatorio
			legal.aviso.p2                     1200  opcional
			legal.aviso.h3                       80  obligatorio
			legal.aviso.p3                     1200  opcional
			legal.privacidad                     40  obligatorio
			legal.priv.p0                      1200  opcional
			legal.priv.h1                        80  obligatorio
			legal.priv.p1                      1200  opcional
			legal.priv.h2                        80  obligatorio
			legal.priv.p2                      1200  opcional
			legal.cookies                        40  obligatorio
			legal.cookies.p0                   1200  opcional
			legal.normas                         40  obligatorio
			legal.normas.p0                    1200  opcional
			""";

	static {
		for (String seccion : SECCIONES_UNICAS) {
			fija(seccion, Tipo.CABECERA, 0, false, seccion);
		}
		for (String seccion : SECCIONES_FIJAS) {
			fija(seccion, Tipo.CABECERA, 0, false, null);
		}
		fija("portada.titular", Tipo.TEXTO, 60, true, null);
		fija("portada.frase", Tipo.TEXTO, 160, false, null);
		fija("portada.foto", Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, null);
		fija("presentacion.rotulo", Tipo.TEXTO, 24, false, "presentacion");
		fija("presentacion.titulo", Tipo.TEXTO, 60, true, "presentacion");
		fija("presentacion.texto", Tipo.TEXTO, 1200, false, "presentacion");
		fija("presentacion.foto", Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, "presentacion");
		fija("sedes.rotulo", Tipo.TEXTO, 24, false, "sedes");
		fija("sedes.titulo", Tipo.TEXTO, 60, true, "sedes");
		fija("profesores.rotulo", Tipo.TEXTO, 24, false, "profesores");
		fija("profesores.titulo", Tipo.TEXTO, 60, true, "profesores");
		fija("galeria.rotulo", Tipo.TEXTO, 24, true, "galeria");
		for (String linea : TEXTOS.strip().split("\\R")) {
			String[] partes = linea.strip().split("\\s+");
			// «Ver la sede» de la línea de fotos de las sedes es de ese bloque: se oculta con él.
			fija(partes[0], Tipo.TEXTO, Integer.parseInt(partes[1]), "obligatorio".equals(partes[2]),
					"sedes.ver_sede".equals(partes[0]) ? "sedes" : null);
		}
	}

	private ClavesWeb() {
	}

	private static void fija(String clave, Tipo tipo, int maximo, boolean obligatorio, String seccion) {
		if (FIJAS.put(clave, new Clave(clave, tipo, maximo, obligatorio, seccion, null, null)) != null) {
			throw new IllegalStateException("Clave repetida en el catálogo: " + clave);
		}
	}

	/** La definición de {@code clave}; vacío si no está en el catálogo o pasa de {@value #MAXIMO_CLAVE}. */
	public static Optional<Clave> de(String clave) {
		if (clave == null || clave.isEmpty() || clave.length() > MAXIMO_CLAVE) {
			return Optional.empty();
		}
		Clave fija = FIJAS.get(clave);
		if (fija != null) {
			return Optional.of(fija);
		}
		Matcher m = TEXTO_FOTO.matcher(clave);
		if (m.matches()) {
			String seccion = m.group(1);
			String campo = m.group(3);
			if (campo == null) {
				return Optional.of(new Clave(clave, Tipo.CABECERA, 0, false, seccion, null, null));
			}
			return Optional.of(switch (campo) {
			case "rotulo" -> new Clave(clave, Tipo.TEXTO, 24, false, seccion, null, null);
			case "titulo" -> new Clave(clave, Tipo.TEXTO, 60, true, seccion, null, null);
			case "texto" -> new Clave(clave, Tipo.TEXTO, 600, false, seccion, null, null);
			default -> new Clave(clave, Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, seccion, null, null);
			});
		}
		m = PROFESOR.matcher(clave);
		if (m.matches()) {
			Integer n = Integer.valueOf(m.group(1));
			return Optional.of(switch (m.group(2)) {
			case "nombre" -> new Clave(clave, Tipo.TEXTO, 60, true, "profesores", null, n);
			case "cargo" -> new Clave(clave, Tipo.TEXTO, 40, false, "profesores", null, n);
			case "sedes" -> new Clave(clave, Tipo.SEDES, MAXIMO_SEDES_PROFESOR, false, "profesores", null, n);
			default -> new Clave(clave, Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, "profesores", null, n);
			});
		}
		if (GALERIA.matcher(clave).matches()) {
			return Optional.of(new Clave(clave, Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, "galeria", null, null));
		}
		m = DISCIPLINA.matcher(clave);
		if (m.matches()) {
			String codigo = m.group(1);
			String campo = m.group(4) != null ? m.group(4) : m.group(2);
			return Optional.of(switch (campo) {
			case "descripcion" -> new Clave(clave, Tipo.TEXTO, 600, false, null, codigo, null);
			case "rotulo" -> new Clave(clave, Tipo.TEXTO, 24, false, null, codigo, null);
			case "texto" -> new Clave(clave, Tipo.TEXTO, 120, false, null, codigo, null);
			default -> new Clave(clave, Tipo.IMAGEN, MAXIMO_DESCRIPCION, true, null, codigo, null);
			});
		}
		if (PUNTO_TARIFAS.matcher(clave).matches()) {
			return Optional.of(new Clave(clave, Tipo.TEXTO, 80, false, null, null, null));
		}
		return Optional.empty();
	}

	/**
	 * Cabecera de la sección movible de {@code clave} (la que se oculta con ella); {@code null} si no es de ninguna o no
	 * está en el catálogo.
	 */
	public static String seccionDe(String clave) {
		return de(clave).map(Clave::seccion).orElse(null);
	}

	/** Número de claves fijas del catálogo (para los tests). */
	static int clavesFijas() {
		return FIJAS.size();
	}
}
