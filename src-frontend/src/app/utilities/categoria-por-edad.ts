import { catalogoActual, disciplinaActual, legadoTaemoiActual } from '../features/catalogo/catalogo-actual';
import { esMenor as esMenorSegunDisciplina, nombreCategoriaPorFecha } from '../features/catalogo/catalogo-reglas';
import { categoriasParaDisciplina } from '../features/catalogo/legado-taemoi';

/**
 * Adaptador sobre `catalogo-reglas.esMenor` con la edad adulta de la disciplina del catálogo cargado.
 * Sin `deporte` se usa la primera disciplina activa (antes TAEKWONDO); disciplina desconocida o catálogo
 * sin cargar: 14 (`EDAD_ADULTO_SIN_DISCIPLINA`). Sin fecha o inválida: menor.
 *
 * @param fechaNacimiento La fecha de nacimiento del alumno.
 * @param deporte Código de la disciplina (opcional).
 * @returns true si el alumno es considerado menor, false si es adulto.
 */
export function esMenor(fechaNacimiento: string | Date | null | undefined, deporte?: string | null): boolean {
  return esMenorSegunDisciplina(fechaNacimiento, disciplinaActual(deporte));
}

/**
 * Categoría de competidor según la edad, con las categorías de la disciplina en el catálogo
 * (D6: con legado de Taemoi, una disciplina de competición sin categorías propias usa las de TAEKWONDO).
 * Sin `deporte` se usa la primera disciplina activa. Catálogo sin cargar, disciplina desconocida o sin
 * categorías: `''` (D15, nunca las constantes de Taemoi).
 *
 * @param fechaNacimiento La fecha de nacimiento del alumno.
 * @param deporte Código de la disciplina (opcional).
 */
export function calcularCategoriaPorEdad(
  fechaNacimiento: string | Date | null | undefined,
  deporte?: string | null
): string {
  const catalogo = catalogoActual();
  if (!catalogo) {
    return '';
  }
  const categorias = categoriasParaDisciplina(legadoTaemoiActual(), catalogo.categorias, disciplinaActual(deporte));
  return nombreCategoriaPorFecha(categorias, fechaNacimiento);
}
