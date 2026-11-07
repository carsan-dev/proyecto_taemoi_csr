package com.taemoi.project.organizacion;

import java.util.Map;

/** {@link UrlsEscuela} para tests unitarios. */
public final class UrlsEscuelaPruebas {

	private UrlsEscuelaPruebas() {
	}

	/** Sin dominios: todas las escuelas usan {@code baseGlobal} ({@code app.frontend.base-url}). */
	public static UrlsEscuela soloGlobal(String baseGlobal) {
		return new UrlsEscuela(ResolutorEscuelaPorHost.sinDominios(1L), baseGlobal);
	}

	/** Con host principal por escuela ({@code organizacion → host}); el resto, {@code baseGlobal}. */
	public static UrlsEscuela conPrincipales(String baseGlobal, Map<Long, String> principales) {
		return new UrlsEscuela(new ResolutorEscuelaPorHost(1L,
				() -> new ResolutorEscuelaPorHost.Dominios(Map.of(), principales)), baseGlobal);
	}
}
