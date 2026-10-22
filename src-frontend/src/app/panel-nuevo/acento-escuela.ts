/**
 * Acento de la escuela (pieza 8b-C, decisión 273, variante «B · acento medido»; DESIGN.md, «The Una Escuela Rule»).
 * TypeScript puro y sin Angular: del color de la escuela P (tono H y croma C en OKLCH) a los tres colores del acento
 * que pinta el panel nuevo bajo `.pn` (`--pn-acento`, `--pn-acento-borde` y `--pn-acento-suave`).
 *
 * - `acentoSuave`: tono H, luz 0,95 y croma 0,035.
 * - `acento`: tono H, croma máx(C, 0,09) recortado a sRGB y la luz bajando desde 0,50 hasta dar ≥ 4,5:1 sobre blanco y
 *   sobre `acentoSuave` (texto, enlaces e iconos).
 * - `acentoBorde`: tono H, croma 0,08 y la luz más alta que dé ≥ 3:1 sobre blanco, sobre el fondo del panel y sobre
 *   `acentoSuave` (bordes de controles y anillo de foco, WCAG 1.4.11).
 * - Un color sin tono (C < 0,01) se queda con la tinta y el suave de su tema, y el borde sale de la misma búsqueda de L
 *   con C = 0 (≥ 3:1 sobre blanco, el fondo y ese suave; `borde-control` daba 2,99:1 sobre `#F2F2F2`).
 * - Una entrada que no es `#RRGGBB` (vacía, de tres cifras, con letras que no son hexadecimales, `null`) da el acento
 *   por defecto, el de `#1A2230`.
 *
 * Las razones se miden sobre el color ya redondeado a `#RRGGBB`, que es el que se pinta (WCAG 2.2, 1.4.3).
 */

export interface AcentoEscuela {
  acento: string;
  acentoSuave: string;
  acentoBorde: string;
}

/** Lo que el acento necesita del tema de la escuela (`TemaMarca`). */
export interface TemaParaAcento {
  primario: string;
  primarioTinta: string;
  primarioSuave: string;
}

export const BLANCO = '#FFFFFF';
/** `fondo` del panel (DESIGN.md). */
export const FONDO = '#F5F5F2';
/** `borde-control` (DESIGN.md): el borde de hoy de los controles. */
export const BORDE_CONTROL = '#868D97';

const CROMA_SIN_TONO = 0.01;
const CROMA_ACENTO_MINIMO = 0.09;
const LUZ_ACENTO = 0.5;
const LUZ_SUAVE = 0.95;
const CROMA_SUAVE = 0.035;
const LUZ_BORDE = 0.95;
const CROMA_BORDE = 0.08;
const PASO_LUZ = 0.0025;
const PASO_CROMA = 0.0005;
const TEXTO = 4.5;
const CONTROL = 3;

const HEX = /^#[0-9a-f]{6}$/i;

/** Los tres colores del acento para un tema; con un primario que no es `#RRGGBB`, el acento por defecto. */
export function acentoDe(tema: TemaParaAcento): AcentoEscuela {
  if (typeof tema?.primario !== 'string' || !HEX.test(tema.primario)) {
    return { ...ACENTO_POR_DEFECTO };
  }
  return calcular(tema);
}

/**
 * El de una escuela sin color (decisión 257): el de `#1A2230`, que es también el tema por defecto de `panel-nuevo.scss`
 * (`--pn-acento: #466396`, `--pn-acento-suave: #e6efff` y `--pn-acento-borde: #6f8abb`). Sale de `calcular` y no de
 * `acentoDe`, que lo devuelve para una entrada no válida.
 */
export const ACENTO_POR_DEFECTO: AcentoEscuela = Object.freeze(
  calcular({ primario: '#1A2230', primarioTinta: '#1A2230', primarioSuave: '#E8E9EA' })
);

function calcular(tema: TemaParaAcento): AcentoEscuela {
  const [, croma, tono] = oklch(tema.primario);
  if (croma < CROMA_SIN_TONO) {
    const acentoBorde = bajarLuz(LUZ_BORDE, 0, 0, CONTROL, [BLANCO, FONDO, tema.primarioSuave]);
    return { acento: tema.primarioTinta, acentoSuave: tema.primarioSuave, acentoBorde };
  }
  const acentoSuave = desdeOklch(LUZ_SUAVE, CROMA_SUAVE, tono);
  const acento = bajarLuz(LUZ_ACENTO, Math.max(croma, CROMA_ACENTO_MINIMO), tono, TEXTO, [BLANCO, acentoSuave]);
  const acentoBorde = bajarLuz(LUZ_BORDE, CROMA_BORDE, tono, CONTROL, [BLANCO, FONDO, acentoSuave]);
  return { acento, acentoSuave, acentoBorde };
}

/** La luz más alta, desde `desde` y en pasos de 0,0025, con la que el color llega a `minimo` sobre cada fondo. */
function bajarLuz(desde: number, croma: number, tono: number, minimo: number, fondos: string[]): string {
  for (let paso = 0; ; paso++) {
    const luz = desde - paso * PASO_LUZ;
    if (luz <= 0) {
      return '#000000';
    }
    const candidato = desdeOklch(luz, croma, tono);
    if (fondos.every((fondo) => contraste(candidato, fondo) >= minimo)) {
      return candidato;
    }
  }
}

// ------------------------------------------------------------------ color

/** Razón de contraste de WCAG 2.2 entre dos `#RRGGBB` (de 1 a 21). */
export function contraste(a: string, b: string): number {
  const la = luminancia(a);
  const lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** `[L, C, h°]` en OKLCH de un `#RRGGBB`. */
export function oklch(color: string): [number, number, number] {
  const [r, g, b] = canales(color).map(lineal);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const luz = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
  const tono = (Math.atan2(bb, a) * 180) / Math.PI;
  return [luz, Math.hypot(a, bb), tono < 0 ? tono + 360 : tono];
}

/** `#RRGGBB` de un OKLCH; si cae fuera de sRGB, se baja el croma (mismo tono y luz) hasta que entre. */
export function desdeOklch(luz: number, croma: number, tono: number): string {
  const radianes = (tono * Math.PI) / 180;
  let c = croma;
  let rgb = lineales(luz, c, radianes);
  while (!enGama(rgb) && c > 0) {
    c = Math.max(0, c - PASO_CROMA);
    rgb = lineales(luz, c, radianes);
  }
  return hex(rgb.map((canal) => Math.round(gamma(canal) * 255)));
}

function lineales(luz: number, croma: number, radianes: number): number[] {
  const a = croma * Math.cos(radianes);
  const b = croma * Math.sin(radianes);
  const l = (luz + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (luz - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (luz - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

function enGama(rgb: number[]): boolean {
  return rgb.every((canal) => canal >= -1e-6 && canal <= 1 + 1e-6);
}

function luminancia(color: string): number {
  const [r, g, b] = canales(color).map(lineal);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function lineal(canal: number): number {
  const c = canal / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function gamma(c: number): number {
  const recortado = Math.min(1, Math.max(0, c));
  return recortado <= 0.0031308 ? 12.92 * recortado : 1.055 * recortado ** (1 / 2.4) - 0.055;
}

function canales(color: string): number[] {
  return [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
}

function hex(canales: number[]): string {
  return `#${canales.map((c) => c.toString(16).padStart(2, '0')).join('')}`.toUpperCase();
}
