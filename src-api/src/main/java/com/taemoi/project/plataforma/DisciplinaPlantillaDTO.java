package com.taemoi.project.plataforma;

/**
 * Fase 4, pieza F4-2 (decisión 392): una disciplina de la plantilla en la pantalla «Nueva escuela», con lo que se copiaría
 * por ella (sin contar lo común a todas: ver {@link PlantillaCatalogo}).
 *
 * @param codigo    código de la disciplina ({@code TAEKWONDO}…), el que se envía en el alta
 * @param conGrados si tiene sistema de grados
 * @param tarifas   tarifas de la disciplina
 * @param productos productos de la disciplina, con los compartidos de examen y recompensa de su sistema
 */
public record DisciplinaPlantillaDTO(String codigo, String nombre, boolean conGrados, long tarifas, long productos) {
}
