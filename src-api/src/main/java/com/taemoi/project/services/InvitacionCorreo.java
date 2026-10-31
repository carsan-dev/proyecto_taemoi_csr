package com.taemoi.project.services;

import java.util.Base64;

import com.taemoi.project.entities.RolMembresia;

/**
 * Pieza E8-4 (D8-13, fase3-e8-miembros.md §5.3): el correo de invitación con la marca de la escuela
 * ({@link MarcaEscuela}). Cabecera con el logo (el de los informes: en Moiskimdo, el de siempre salvo que el superadmin
 * suba otro) como imagen en línea {@value #CID_LOGO} y el nombre de los correos de cuenta; sin logo, solo el nombre
 * (decisión 211). «Hola, {nombre}.», «Te han dado acceso como {rol} en {escuela}.», el botón «Crear mi contraseña» y
 * «El enlace caduca en 7 días.». Todo lo que llega del alta va escapado. Sin estado: solo construye el correo.
 */
public final class InvitacionCorreo {

	/** {@code Content-ID} del logo en línea. */
	public static final String CID_LOGO = "logo-escuela";

	/**
	 * El correo listo para {@link EmailService#sendEmailConImagen}: {@code logo} y {@code tipoLogo} nulos sin logo;
	 * {@code remitente} nulo en Moiskimdo (el de siempre).
	 */
	public record Correo(String asunto, String html, byte[] logo, String tipoLogo, String remitente) {
	}

	private InvitacionCorreo() {
	}

	public static Correo de(MarcaEscuela marca, String nombre, RolMembresia rol, String enlace) {
		String escuelaHtml = marca.clubCorreoCuentaHtml();
		String escuelaTexto = nombreEscuela(marca);
		Logo logo = Logo.de(marca.logoInformes());
		String cabecera = logo == null ? ""
				: "<img src=\"cid:" + CID_LOGO + "\" alt=\"\" width=\"56\" height=\"56\""
						+ " style=\"display:block;margin:0 0 12px;border:0;width:56px;height:56px;\">";
		String enlaceHtml = escapar(enlace);
		String html = """
				<!doctype html>
				<html lang="es">
				<head>
				  <meta charset="UTF-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1.0">
				</head>
				<body style="margin:0;padding:0;background-color:#f5f7fa;">
				  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color:#f5f7fa;padding:24px 12px;">
				    <tr>
				      <td align="center">
				        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.08);">
				          <tr>
				            <td style="background:#1b2b2e;color:#ffffff;padding:24px 28px;font-family:Arial,sans-serif;">
				              {{LOGO}}
				              <div style="font-size:18px;font-weight:700;">{{ESCUELA}}</div>
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:28px;color:#1f2933;font-family:Arial,sans-serif;font-size:15px;line-height:1.6;">
				              <p style="margin:0 0 16px;">Hola, {{NOMBRE}}.</p>
				              <p style="margin:0 0 18px;">Te han dado acceso como {{ROL}} en {{ESCUELA}}.</p>
				              <div style="text-align:center;margin:24px 0;">
				                <a href="{{ENLACE}}" style="background:#0d47a1;color:#ffffff;text-decoration:none;padding:12px 26px;border-radius:999px;display:inline-block;font-weight:600;">
				                  Crear mi contraseña
				                </a>
				              </div>
				              <p style="margin:0 0 12px;">El enlace caduca en 7 días.</p>
				              <p style="margin:18px 0 6px;font-size:13px;color:#6b7280;">Si el botón no funciona, copia y pega este enlace:</p>
				              <p style="margin:0 0 18px;word-break:break-all;">
				                <a href="{{ENLACE}}" style="color:#0d47a1;text-decoration:none;">{{ENLACE}}</a>
				              </p>
				              <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;">
				              <p style="margin:0;font-size:12px;color:#9aa2a9;">
				                Si no esperabas este correo, puedes ignorarlo.
				              </p>
				            </td>
				          </tr>
				        </table>
				        <div style="font-size:11px;color:#9aa2a9;margin-top:16px;font-family:Arial,sans-serif;">
				          © {{ESCUELA}}
				        </div>
				      </td>
				    </tr>
				  </table>
				</body>
				</html>
				"""
				.replace("{{LOGO}}", cabecera)
				.replace("{{ROL}}", enPantalla(rol))
				.replace("{{NOMBRE}}", escapar(nombre == null ? "" : nombre))
				.replace("{{ENLACE}}", enlaceHtml)
				.replace("{{ESCUELA}}", escuelaHtml);
		return new Correo("Tu acceso a " + escuelaTexto, html, logo == null ? null : logo.bytes(),
				logo == null ? null : logo.tipo(), marca.remitente());
	}

	/**
	 * El nombre de la escuela en texto (sin escapar), el mismo del correo: Moiskimdo, el de sus correos de cuenta; otra
	 * escuela, {@code organizacion.nombre}.
	 */
	public static String nombreEscuela(MarcaEscuela marca) {
		return marca.legado() ? MarcaEscuela.LEGADO_CLUB_CORREO_CUENTA : marca.nombre();
	}

	/** Cómo se llama el rol en pantalla (las mismas palabras que los mensajes de la API de miembros). */
	static String enPantalla(RolMembresia rol) {
		return switch (rol) {
			case PROPIETARIO -> "propietario";
			case ADMINISTRADOR -> "gestor";
			case RESPONSABLE_SEDE -> "responsable de sede";
			case PROFESOR -> "profesor";
			case FAMILIA -> "familia";
		};
	}

	static String escapar(String valor) {
		return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	/** Los bytes y el tipo de un data URI en Base64 ({@code data:image/png;base64,…}); {@code null} si no lo es. */
	record Logo(byte[] bytes, String tipo) {
		static Logo de(String dataUri) {
			if (dataUri == null || !dataUri.startsWith("data:")) {
				return null;
			}
			int coma = dataUri.indexOf(',');
			int base64 = dataUri.indexOf(";base64");
			if (coma < 0 || base64 < 0 || base64 > coma) {
				return null;
			}
			try {
				byte[] bytes = Base64.getDecoder().decode(dataUri.substring(coma + 1));
				return bytes.length == 0 ? null : new Logo(bytes, dataUri.substring(5, base64));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}
}
