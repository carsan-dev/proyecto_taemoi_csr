package com.taemoi.project.organizacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * URL públicas de una escuela (fase 4, escuela por dominio; {@code docs/multi-escuela/fase4-dominio-plan.md} §4.2).
 *
 * <p>{@link #baseFrontend(Long)}: base de los enlaces de correo. {@code https://} + el host principal de la escuela en
 * {@code organizacion_dominio} (en memoria, vía {@link ResolutorEscuelaPorHost}: ni una sentencia por correo). Una
 * escuela sin dominio principal usa {@code app.frontend.base-url}, como hasta ahora. Moiskimdo (la por defecto) usa
 * la global, que en producción es {@code https://moiskimdo.es}.
 */
@Component
public class UrlsEscuela {

	private final ResolutorEscuelaPorHost resolutor;
	private final String baseFrontendGlobal;

	public UrlsEscuela(ResolutorEscuelaPorHost resolutor,
			@Value("${app.frontend.base-url:http://localhost:4200}") String baseFrontendGlobal) {
		this.resolutor = resolutor;
		this.baseFrontendGlobal = baseFrontendGlobal;
	}

	/**
	 * Base del frontend para los correos de la escuela, sin barra final garantizada (la global se devuelve tal cual).
	 * La escuela por defecto ({@code app.organizacion.por-defecto}) usa SIEMPRE la global, tenga o no filas de dominio:
	 * así local y pruebas (con una copia de la base de producción) siguen enlazando a su {@code app.frontend.base-url}.
	 */
	public String baseFrontend(Long organizacionId) {
		if (organizacionId != null && organizacionId.equals(resolutor.getOrganizacionPorDefecto())) {
			return baseFrontendGlobal;
		}
		return resolutor.hostPrincipalDe(organizacionId).map(host -> "https://" + host).orElse(baseFrontendGlobal);
	}
}
