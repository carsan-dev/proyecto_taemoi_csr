import { catalogoActual, disciplinaActual, legadoTaemoiActual } from '../features/catalogo/catalogo-actual';
import { gradosDeDisciplina, esMenor, siguienteGrado } from '../features/catalogo/catalogo-reglas';
import { requiereAvisoRojoBordado } from '../features/catalogo/legado-taemoi';
import { getGradoNombreParaDeporte } from './grado-colors';

/**
 * Código del siguiente grado según la progresión (`orden` + `aplicaA`) del sistema de grados de la
 * disciplina en el catálogo. Disciplina sin sistema de grados o desconocida, grado desconocido, último
 * grado o catálogo sin cargar: `null`.
 */
export function obtenerSiguienteGrado(
  deporte: string | null | undefined,
  gradoActual: string | null | undefined,
  fechaNacimiento: string | Date | null | undefined
): string | null {
  if (!deporte || !gradoActual) {
    return null;
  }
  const catalogo = catalogoActual();
  const disciplina = disciplinaActual(deporte.toUpperCase());
  if (!catalogo || !disciplina) {
    return null;
  }
  const grados = gradosDeDisciplina(catalogo.sistemasGrados, disciplina);
  return siguienteGrado(grados, gradoActual, esMenor(fechaNacimiento, disciplina))?.codigo ?? null;
}

/** D11: aviso del cinturón rojo bordado, solo con legado de Taemoi y cuando el siguiente grado es ROJO. */
export function esSiguienteGradoRojo(
  deporte: string | null | undefined,
  gradoActual: string | null | undefined,
  fechaNacimiento: string | Date | null | undefined
): boolean {
  return requiereAvisoRojoBordado(legadoTaemoiActual(), obtenerSiguienteGrado(deporte, gradoActual, fechaNacimiento));
}

/**
 * D11 por disciplina: el color del grado `ROJO` con el nombre del catálogo de la disciplina, en minúsculas, para los textos
 * del cinturón rojo bordado: «rojo» en taekwondo y «marrón» en kickboxing (V30). Sin catálogo o sin ese grado, «rojo».
 */
export function colorGradoRojo(deporte: string | null | undefined): string {
  return getGradoNombreParaDeporte('ROJO', deporte?.toUpperCase()).toLocaleLowerCase('es-ES');
}

/** {@link colorGradoRojo} con mayúscula inicial, para empezar una opción o un título: «Rojo», «Marrón». */
export function colorGradoRojoInicial(deporte: string | null | undefined): string {
  const color = colorGradoRojo(deporte);
  return color.charAt(0).toLocaleUpperCase('es-ES') + color.slice(1);
}
