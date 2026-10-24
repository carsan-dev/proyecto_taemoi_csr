/**
 * Las poses de la gota de esfuerzo (pieza 8b-M, `mascota-app/`). Cada una es un módulo TypeScript con su SVG como cadena
 * y se pide con un `import()` propio: Angular lo parte en un chunk por pose y ninguna entra en el bundle inicial ni en el
 * de la pantalla que la usa; solo viaja la que se pinta.
 *
 * `cumpleanos-inicio` es el fotograma de partida de la animación de cumpleaños y `compacta` el dibujo de 32 px (sin
 * brazos y con el trazo reforzado: no es una reducción de saludar). Ninguno de los dos es una pose que se pida por nombre.
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

export type ClaveSvgMascota = PoseMascota | 'cumpleanos-inicio' | 'compacta';

export type CargadoresMascota = Record<ClaveSvgMascota, () => Promise<string>>;

export const CARGADORES_MASCOTA: CargadoresMascota = {
  saludar: () => import('./svg/saludar').then((m) => m.SVG),
  explicar: () => import('./svg/explicar').then((m) => m.SVG),
  celebrar: () => import('./svg/celebrar').then((m) => m.SVG),
  dudar: () => import('./svg/dudar').then((m) => m.SVG),
  descansar: () => import('./svg/descansar').then((m) => m.SVG),
  cumpleanos: () => import('./svg/cumpleanos').then((m) => m.SVG),
  'cumpleanos-inicio': () => import('./svg/cumpleanos-inicio').then((m) => m.SVG),
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
