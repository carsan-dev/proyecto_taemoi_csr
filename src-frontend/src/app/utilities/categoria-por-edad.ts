import { calcularEdad } from './calcular-edad';

/**
 * Edad a partir de la cual un alumno es considerado adulto según el deporte.
 * - Taekwondo: 14 años
 * - Kickboxing: 15 años
 */
export const EDAD_ADULTO_TAEKWONDO = 14;
export const EDAD_ADULTO_KICKBOXING = 15;

/**
 * Determina si un alumno es considerado "menor" según las reglas del deporte especificado.
 *
 * Para Taekwondo: menor si edad < 13 o (edad == 13 y no cumple 14 este año)
 * Para Kickboxing: menor si edad < 14 o (edad == 14 y no cumple 15 este año)
 *
 * @param fechaNacimiento La fecha de nacimiento del alumno.
 * @param deporte El deporte para aplicar la regla correcta (opcional, por defecto TAEKWONDO).
 * @returns true si el alumno es considerado menor, false si es adulto.
 */
export function esMenor(
  fechaNacimiento: string | Date | null | undefined,
  deporte: string = 'TAEKWONDO'
): boolean {
  if (!fechaNacimiento) {
    return true; // Por defecto, si no hay fecha, consideramos menor
  }

  const fecha =
    fechaNacimiento instanceof Date
      ? fechaNacimiento.toISOString()
      : fechaNacimiento;

  const edad = calcularEdad(fecha);
  if (Number.isNaN(edad)) {
    return true;
  }

  const edadAdulto =
    deporte === 'KICKBOXING' ? EDAD_ADULTO_KICKBOXING : EDAD_ADULTO_TAEKWONDO;
  const edadLimiteMenor = edadAdulto - 1; // 13 para taekwondo, 14 para kickboxing

  if (edad < edadLimiteMenor) {
    return true;
  }

  if (edad === edadLimiteMenor) {
    // Solo es adulto si cumple edadAdulto este año
    return !cumpleEdadEsteAnio(fechaNacimiento, edadAdulto);
  }

  return false; // edad >= edadAdulto
}

/**
 * Verifica si el alumno cumple una edad específica en el año actual.
 */
function cumpleEdadEsteAnio(
  fechaNacimiento: string | Date,
  edad: number
): boolean {
  const fechaNac =
    fechaNacimiento instanceof Date
      ? fechaNacimiento
      : new Date(fechaNacimiento);
  const fechaCumple = new Date(fechaNac);
  fechaCumple.setFullYear(fechaNac.getFullYear() + edad);
  return fechaCumple.getFullYear() === new Date().getFullYear();
}

/**
 * Calcula la categoría de competidor según la edad del alumno.
 * Las categorías son las mismas para todos los deportes, pero la edad límite
 * para ser considerado menor varía según el deporte.
 *
 * @param fechaNacimiento La fecha de nacimiento del alumno.
 * @param deporte El deporte (opcional, por defecto TAEKWONDO). No afecta las categorías,
 *                pero se incluye para consistencia con la API.
 * @returns La categoría correspondiente a la edad.
 */
export function calcularCategoriaPorEdad(
  fechaNacimiento: string | Date | null | undefined,
  deporte: string = 'TAEKWONDO'
): string {
  if (!fechaNacimiento) {
    return '';
  }

  const fecha =
    fechaNacimiento instanceof Date
      ? fechaNacimiento.toISOString()
      : fechaNacimiento;

  const edad = calcularEdad(fecha);
  if (Number.isNaN(edad)) {
    return '';
  }

  // Las categorías de competición son las mismas para todos los deportes
  if (edad <= 9) {
    return 'Infantil';
  }
  if (edad <= 11) {
    return 'Precadete';
  }
  if (edad <= 13) {
    return 'Cadete';
  }
  if (edad <= 16) {
    return 'Junior';
  }
  return 'Senior';
}
