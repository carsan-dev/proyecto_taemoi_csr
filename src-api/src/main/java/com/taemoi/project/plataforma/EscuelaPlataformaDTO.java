package com.taemoi.project.plataforma;

/**
 * Escuela (organización) vista por el administrador de plataforma: identificador y nombre; desde la E7-7a, también si
 * tiene logo subido ({@code organizacion.logo_imagen_id}) y si usa la marca heredada de Moiskimdo
 * ({@code comportamiento_legado_taemoi}: sus PDF y correos salen siempre con su logo de siempre y no admite subir otro).
 */
public record EscuelaPlataformaDTO(Long id, String nombre, boolean tieneLogo, boolean marcaHeredada) {
}
