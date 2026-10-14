import { catalogoActual } from '../features/catalogo/catalogo-actual';

/**
 * Enum for sport types (Deporte)
 * @deprecated Las disciplinas son del catálogo (`CatalogoService.disciplinasActivas()`). Se borra en la rebanada 4 (disciplinas).
 */
export enum Deporte {
  TAEKWONDO = 'TAEKWONDO',
  KICKBOXING = 'KICKBOXING',
  PILATES = 'PILATES',
  DEFENSA_PERSONAL_FEMENINA = 'DEFENSA_PERSONAL_FEMENINA'
}

/**
 * Human-readable labels for each sport
 * @deprecated Usar `CatalogoService.etiquetaDisciplina` / `getDeporteLabel`. `editar-alumno` aún la expone a su plantilla;
 * se borra en la rebanada 4 (disciplinas).
 */
export const DeporteLabels: Record<Deporte, string> = {
  [Deporte.TAEKWONDO]: 'Taekwondo',
  [Deporte.KICKBOXING]: 'Kickboxing',
  [Deporte.PILATES]: 'Pilates',
  [Deporte.DEFENSA_PERSONAL_FEMENINA]: 'Defensa Personal Femenina'
};

/**
 * Array of all sports for iteration
 * @deprecated Usar `CatalogoService.disciplinasActivas()`. Se borra en la rebanada 4 (disciplinas).
 */
export const DeportesArray: Deporte[] = [
  Deporte.TAEKWONDO,
  Deporte.KICKBOXING,
  Deporte.PILATES,
  Deporte.DEFENSA_PERSONAL_FEMENINA
];

/**
 * Etiqueta de una disciplina: `disciplina.nombre` del catálogo cargado (D2: «Defensa Personal Femenina»).
 * Código desconocido (inscripciones antiguas) o catálogo sin cargar: el propio código (D15, nunca etiquetas de Taemoi).
 */
export function getDeporteLabel(deporte: string | Deporte): string {
  const codigo: string = deporte;
  return catalogoActual()?.disciplinas.find((d) => d.codigo === codigo)?.nombre || codigo;
}

/**
 * Utility function to check if a string is a valid Deporte
 * @deprecated Se borra en la rebanada 4 (disciplinas).
 */
export function isValidDeporte(value: string): value is Deporte {
  return Object.values(Deporte).includes(value as Deporte);
}
