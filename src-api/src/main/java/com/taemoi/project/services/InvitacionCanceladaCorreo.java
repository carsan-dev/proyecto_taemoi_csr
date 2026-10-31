package com.taemoi.project.services;

/**
 * Decisión 389: el correo de «Invitación cancelada» con la marca de la escuela ({@link MarcaEscuela}) y la misma
 * cabecera que {@link InvitacionCorreo}: logo como imagen en línea {@value InvitacionCorreo#CID_LOGO} y nombre de los
 * correos de cuenta; sin logo, solo el nombre. Sin enlace ni botón: la invitación ya no vale. El nombre y la escuela van
 * escapados en el HTML. Sin estado: solo construye el correo.
 */
public final class InvitacionCanceladaCorreo {

	private InvitacionCanceladaCorreo() {
	}

	public static InvitacionCorreo.Correo de(MarcaEscuela marca, String nombre) {
		InvitacionCorreo.Logo logo = InvitacionCorreo.Logo.de(marca.logoInformes());
		String cabecera = logo == null ? ""
				: "<img src=\"cid:" + InvitacionCorreo.CID_LOGO + "\" alt=\"\" width=\"56\" height=\"56\""
						+ " style=\"display:block;margin:0 0 12px;border:0;width:56px;height:56px;\">";
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
				              <p style="margin:0 0 16px;">{{ESCUELA}} ha cancelado tu invitación. El enlace que recibiste ya no sirve para entrar en {{ESCUELA}}.</p>
				              <p style="margin:0;">Si crees que es un error, ponte en contacto con la escuela.</p>
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
				.replace("{{NOMBRE}}", InvitacionCorreo.escapar(nombre == null ? "" : nombre))
				.replace("{{ESCUELA}}", marca.clubCorreoCuentaHtml());
		return new InvitacionCorreo.Correo("Invitación cancelada · " + InvitacionCorreo.nombreEscuela(marca), html,
				logo == null ? null : logo.bytes(), logo == null ? null : logo.tipo(), marca.remitente());
	}
}
