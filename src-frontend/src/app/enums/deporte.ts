import { catalogoActual } from '../features/catalogo/catalogo-actual';

/**
 * Etiqueta de una disciplina: `disciplina.nombre` del catálogo cargado (D2: «Defensa Personal Femenina»).
 * Código desconocido (inscripciones antiguas) o catálogo sin cargar: el propio código (D15, nunca etiquetas de Taemoi).
 * Los componentes con `CatalogoService` pueden usar `etiquetaDisciplina`; esta función es para código sin inyección
 * (Swal, utilidades); sigue en uso en cinco componentes.
 */
export function getDeporteLabel(deporte: string): string {
  return catalogoActual()?.disciplinas.find((d) => d.codigo === deporte)?.nombre || deporte;
}
