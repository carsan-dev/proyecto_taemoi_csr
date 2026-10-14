import { catalogoActual, disciplinaActual, legadoTaemoiActual } from '../features/catalogo/catalogo-actual';
import { gradosDeDisciplina, esMenor, siguienteGrado } from '../features/catalogo/catalogo-reglas';
import { requiereAvisoRojoBordado } from '../features/catalogo/legado-taemoi';

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
