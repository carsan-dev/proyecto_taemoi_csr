package com.taemoi.project.dtos.request;

/**
 * Cuerpo de {@code PUT /api/panel/mi-perfil} (decisión 322): sustituye los tres valores. Sin ids: opera siempre sobre la
 * cuenta de la sesión. Las reglas están en {@code PerfilCuentaService}.
 */
public record PerfilCuentaRequest(String nombrePreferido, Integer cumpleDia, Integer cumpleMes) {
}
