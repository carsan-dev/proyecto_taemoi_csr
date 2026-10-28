package com.taemoi.project.services;

import java.util.Locale;

/**
 * Pieza E7-7a (decisión 211): nombre y logo con los que sale una escuela en sus PDF y correos. La escuela con el
 * comportamiento heredado de Taemoi (Moiskimdo) sale EXACTAMENTE como antes: los textos fijos de siempre y los logos
 * de classpath (con logo subido, ese; E7-8, decisión 213). El resto, con {@code organizacion.nombre} y su logo si lo
 * tiene (sin logo, solo el nombre); nunca con el nombre ni el logo de Moiskimdo. Los métodos {@code …Html} devuelven texto listo para HTML (el de Moiskimdo, el
 * literal de siempre; el de otra escuela, escapado).
 *
 * @param organizacionId   escuela
 * @param legado           si es la escuela con la marca {@code comportamiento_legado_taemoi}
 * @param nombre           {@code organizacion.nombre}
 * @param logoInformes     data URI PNG de la cabecera de los informes y de la hoja de asistencia; vacío sin logo
 * @param logoSolicitud    data URI de la solicitud de preinscripción firmada; vacío sin logo
 * @param logoCertificado  imagen (JPEG o PNG) del certificado de cobros; {@code null} sin logo
 * @param legal            datos legales de la escuela para el certificado (solo los de una escuela sin legado)
 * @param remitente        nombre visible del remitente de sus correos; {@code null} en Moiskimdo (el de siempre,
 *                         {@code app.mail.from-name})
 * @param colorPrimario    decisiones 309 y 315: {@code organizacion.color_primario} en {@code #rrggbb} (minúsculas), el
 *                         filete bajo la cabecera de los PDF; si falta o no es {@code #RRGGBB}, {@value #COLOR_POR_DEFECTO}
 */
public record MarcaEscuela(Long organizacionId, boolean legado, String nombre, String logoInformes,
		String logoSolicitud, byte[] logoCertificado, DatosLegales legal, String remitente, String colorPrimario) {

	/** Color de una escuela sin color guardado (o con uno inválido): la tinta del panel (decisión 257). */
	public static final String COLOR_POR_DEFECTO = "#1a2230";

	private static final java.util.regex.Pattern COLOR = java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

	/** Textos de Moiskimdo, idénticos a los que había fijos en el código antes de la E7-7a. */
	static final String LEGADO_CABECERA_INFORMES = "Moi's Kim Do Taekwondo";
	static final String LEGADO_ASISTENCIA_PRINCIPAL = "CLUB MOI'S KIM DO";
	static final String LEGADO_ASISTENCIA_SUBTITULO = "Tae Kwon Do";
	static final String LEGADO_CABECERA_SOLICITUD = "Escuela Moi Kim Do";
	static final String LEGADO_FIRMA_CORREO = "Moi's Kim Do";
	static final String LEGADO_CLUB_CORREO_CUENTA = "Club Moiskimdo Taekwondo";

	/** Datos de {@code organizacion} para el certificado de cobros; cualquiera puede faltar ({@code null}). */
	public record DatosLegales(String nombreLegal, String cif, String localidad, String firmanteNombre,
			String firmanteNif, String firmanteCargo) {
	}

	public MarcaEscuela {
		nombre = nombre == null ? "" : nombre.trim();
		logoInformes = logoInformes == null ? "" : logoInformes;
		logoSolicitud = logoSolicitud == null ? "" : logoSolicitud;
		// Remitente: Moiskimdo, null (el de siempre); otra escuela, el suyo si está lleno o su nombre.
		remitente = legado ? null : remitente == null || remitente.isBlank() ? nombre : remitente.trim();
		colorPrimario = colorPrimario != null && COLOR.matcher(colorPrimario.trim()).matches()
				? colorPrimario.trim().toLowerCase(Locale.ROOT)
				: COLOR_POR_DEFECTO;
	}

	/** Sin color de escuela (tests y llamadas anteriores a las decisiones 309 y 315): {@value #COLOR_POR_DEFECTO}. */
	public MarcaEscuela(Long organizacionId, boolean legado, String nombre, String logoInformes, String logoSolicitud,
			byte[] logoCertificado, DatosLegales legal, String remitente) {
		this(organizacionId, legado, nombre, logoInformes, logoSolicitud, logoCertificado, legal, remitente, null);
	}

	/** Nombre de la cabecera de los informes, sin escapar (también el del pie de página). */
	public String cabeceraInformes() {
		return legado ? LEGADO_CABECERA_INFORMES : nombre;
	}

	/** Nombre de la cabecera de los informes. */
	public String cabeceraInformesHtml() {
		return legado ? LEGADO_CABECERA_INFORMES : esc(nombre);
	}

	/** Nombre de la cabecera de la hoja de asistencia, sin escapar (también el del pie de página). */
	public String asistenciaPrincipal() {
		return legado ? LEGADO_ASISTENCIA_PRINCIPAL : nombre;
	}

	/**
	 * Nombre de la cabecera de la hoja de asistencia: Moiskimdo, el de siempre; otra escuela, su nombre tal cual (desde
	 * la decisión 315 sin pasarlo a mayúsculas, como en el resto de cabeceras).
	 */
	public String asistenciaPrincipalHtml() {
		return legado ? LEGADO_ASISTENCIA_PRINCIPAL : esc(nombre);
	}

	/** Segunda línea de la hoja de asistencia; {@code null} (no se pinta) fuera de Moiskimdo. */
	public String asistenciaSubtituloHtml() {
		return legado ? LEGADO_ASISTENCIA_SUBTITULO : null;
	}

	/** Cabecera de la solicitud cuando la plantilla no trae una (SIN escapar: quien la pinta la escapa, como antes). */
	public String cabeceraSolicitudPorDefecto() {
		return legado ? LEGADO_CABECERA_SOLICITUD : nombre;
	}

	/** «Un saludo, …» de los correos de preinscripción. */
	public String firmaCorreoHtml() {
		return legado ? LEGADO_FIRMA_CORREO : esc(nombre);
	}

	/** Nombre de la cabecera y del pie de los correos de cuenta (registro, contraseña, racha). */
	public String clubCorreoCuentaHtml() {
		return legado ? LEGADO_CLUB_CORREO_CUENTA : esc(nombre);
	}

	private static String esc(String valor) {
		return valor == null ? "" : valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}
}
