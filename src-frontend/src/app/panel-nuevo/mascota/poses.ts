/**
 * Las 14 poses animadas de la gota de esfuerzo (pieza 8b-M2, mascota-app/animaciones/).
 * Cada SVG conserva su gesto y reposo en un módulo con import() propio: un chunk por pose,
 * sin dibujos en el bundle inicial. Solo viaja la pose que se pinta.
 * compacta es el dibujo estático de 32 px, no una pose que se pida por nombre.
 */
export const POSES_MASCOTA = [
  'saludar',
  'explicar',
  'celebrar',
  'dudar',
  'descansar',
  'cumpleanos',
  'prepararse',
  'animar',
  'leer',
  'confirmar',
  'buscar',
  'esperar',
  'invitar',
  'agradecer',
] as const;

export type PoseMascota = (typeof POSES_MASCOTA)[number];

export type ClaveSvgMascota = PoseMascota | 'compacta';

export type CargadoresMascota = Record<ClaveSvgMascota, () => Promise<string>>;

export const CARGADORES_MASCOTA: CargadoresMascota = {
  saludar: () => import('./svg/saludar').then((m) => m.SVG),
  explicar: () => import('./svg/explicar').then((m) => m.SVG),
  celebrar: () => import('./svg/celebrar').then((m) => m.SVG),
  dudar: () => import('./svg/dudar').then((m) => m.SVG),
  descansar: () => import('./svg/descansar').then((m) => m.SVG),
  cumpleanos: () => import('./svg/cumpleanos').then((m) => m.SVG),
  prepararse: () => import('./svg/prepararse').then((m) => m.SVG),
  animar: () => import('./svg/animar').then((m) => m.SVG),
  leer: () => import('./svg/leer').then((m) => m.SVG),
  confirmar: () => import('./svg/confirmar').then((m) => m.SVG),
  buscar: () => import('./svg/buscar').then((m) => m.SVG),
  esperar: () => import('./svg/esperar').then((m) => m.SVG),
  invitar: () => import('./svg/invitar').then((m) => m.SVG),
  agradecer: () => import('./svg/agradecer').then((m) => m.SVG),
  compacta: () => import('./svg/compacta').then((m) => m.SVG),
};

export function esPoseMascota(valor: unknown): valor is PoseMascota {
  return typeof valor === 'string' && (POSES_MASCOTA as readonly string[]).includes(valor);
}
