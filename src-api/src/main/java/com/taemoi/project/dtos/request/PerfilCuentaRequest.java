package com.taemoi.project.dtos.request;

/**
 * Cuerpo de {@code PUT /api/panel/mi-perfil} (decisión 322): sustituye los tres valores del perfil. Sin ids: opera siempre
 * sobre la cuenta de la sesión. Las reglas están en {@code PerfilCuentaService}.
 *
 * @param importesTapados decisión 419. A diferencia del resto, ausente o {@code null} conserva lo guardado: un cliente
 *                        antiguo no lo manda y no debe apagarlo.
 */
public record PerfilCuentaRequest(String nombrePreferido, Integer cumpleDia, Integer cumpleMes,
		Boolean importesTapados) {
}
