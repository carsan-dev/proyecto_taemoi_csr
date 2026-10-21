package com.taemoi.project.plataforma;

/**
 * Escuela (organización) vista por el administrador de plataforma: identificador y nombre; desde la E7-7a, también si
 * tiene logo subido ({@code organizacion.logo_imagen_id}) y si usa la marca heredada de Moiskimdo
 * ({@code comportamiento_legado_taemoi}: sus textos de siempre; desde la E7-8 admite logo subido y, sin él, sus documentos
 * llevan su logo de siempre). Desde la 8b-T, el color de la escuela tal cual se guardó ({@code organizacion.color_primario},
 * {@code #RRGGBB} o {@code null}); lo derivado lo calcula {@code ReglaContraste} al leer.
 */
public record EscuelaPlataformaDTO(Long id, String nombre, boolean tieneLogo, boolean marcaHeredada,
		String colorPrimario) {
}
