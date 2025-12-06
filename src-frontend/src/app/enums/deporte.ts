/**
 * Enum for sport types (Deporte)
 */
export enum Deporte {
  TAEKWONDO = 'TAEKWONDO',
  KICKBOXING = 'KICKBOXING',
  PILATES = 'PILATES',
  DEFENSA_PERSONAL_FEMENINA = 'DEFENSA_PERSONAL_FEMENINA'
}

/**
 * Human-readable labels for each sport
 */
export const DeporteLabels: Record<Deporte, string> = {
  [Deporte.TAEKWONDO]: 'Taekwondo',
  [Deporte.KICKBOXING]: 'Kickboxing',
  [Deporte.PILATES]: 'Pilates',
  [Deporte.DEFENSA_PERSONAL_FEMENINA]: 'Defensa Personal Femenina'
};

/**
 * Array of all sports for iteration
 */
export const DeportesArray: Deporte[] = [
  Deporte.TAEKWONDO,
  Deporte.KICKBOXING,
  Deporte.PILATES,
  Deporte.DEFENSA_PERSONAL_FEMENINA
];

/**
 * Utility function to get label for a sport
 */
export function getDeporteLabel(deporte: string | Deporte): string {
  return DeporteLabels[deporte as Deporte] || deporte;
}

/**
 * Utility function to check if a string is a valid Deporte
 */
export function isValidDeporte(value: string): value is Deporte {
  return Object.values(Deporte).includes(value as Deporte);
}
