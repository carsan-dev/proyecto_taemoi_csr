/**
 * Regla de contraste del color de la escuela (pieza 8b-T, fase3-e8b-plan.md §4): la MISMA que `ReglaContraste.java`,
 * para la vista previa de «Logo y color» mientras el superadmin elige el color (sin pedir nada al servidor). Lo que
 * vale es lo que calcula el servidor al leer; los vectores de `regla-contraste.spec.ts` y de `ReglaContrasteTest`
 * son los mismos para que no se separen.
 *
 * - Texto: blanco si da ≥ 4,5:1; si no, la tinta `#1A2230`; si ninguno llega, el primario se oscurece en OKLCH (misma
 *   tonalidad y croma, luz −0,01 por paso, recortado a sRGB) hasta que el blanco llegue.
 * - Secundario: el texto con el 25 % del primario (bajando de 5 en 5) mientras dé ≥ 4,5:1.
 * - Tinta: el primario oscurecido igual hasta ≥ 4,5:1 sobre blanco.
 * - Suave: el primario al 10 % sobre blanco, con la tinta ≥ 4,5:1 encima.
 */

export const BLANCO = '#FFFFFF';
export const TINTA = '#1A2230';
/** Decisión 257: una escuela sin color guardado. */
export const POR_DEFECTO = '#1A2230';
export const MINIMO = 4.5;

const PASO_LUZ = 0.01;
const MEZCLA_SECUNDARIO_MAXIMA = 0.25;
const PASO_MEZCLA = 0.05;
const MEZCLA_SUAVE = 0.1;
const PASO_SUAVE = 0.01;
const FORMATO = /^#[0-9A-Fa-f]{6}$/;

/** `TemaEscuela` del servidor. */
export interface TemaColor {
  primario: string;
  sobrePrimario: string;
  sobrePrimario2: string;
  primarioTinta: string;
  primarioSuave: string;
  ajustado: boolean;
}

export function esColorValido(color: string | null | undefined): color is string {
  return typeof color === 'string' && FORMATO.test(color);
}

/** Los colores del panel para un color (`null`: el de por defecto). Lanza si el formato no vale. */
export function temaDe(colorGuardado: string | null): TemaColor {
  const original = colorGuardado === null ? POR_DEFECTO : normalizar(colorGuardado);
  let primario = original;
  let sobre: string;
  if (contraste(BLANCO, original) >= MINIMO) {
    sobre = BLANCO;
  } else if (contraste(TINTA, original) >= MINIMO) {
    sobre = TINTA;
  } else {
    primario = oscurecerHastaBlanco(original);
    sobre = BLANCO;
  }
  return {
    primario,
    sobrePrimario: sobre,
    sobrePrimario2: secundario(sobre, primario),
    primarioTinta: oscurecerHastaBlanco(primario),
    primarioSuave: suave(primario),
    ajustado: primario !== original,
  };
}

/** Razón de contraste de WCAG 2.2 entre dos `#RRGGBB` (de 1 a 21). */
export function contraste(a: string, b: string): number {
  const la = luminancia(normalizar(a));
  const lb = luminancia(normalizar(b));
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** «14,66:1»: truncada a dos decimales, para no mostrar «4,50:1» de algo que no llega. */
export function formatoRazon(razon: number): string {
  const truncada = Math.floor(razon * 100 + 1e-9) / 100;
  return `${truncada.toFixed(2).replace('.', ',')}:1`;
}

export function normalizar(color: string): string {
  if (!esColorValido(color)) {
    throw new Error('El color debe tener el formato #RRGGBB.');
  }
  return color.toUpperCase();
}

function oscurecerHastaBlanco(color: string): string {
  if (contraste(BLANCO, color) >= MINIMO) {
    return color;
  }
  const [luz0, croma, tono] = oklch(color);
  for (let paso = 1; ; paso++) {
    const luz = luz0 - paso * PASO_LUZ;
    if (luz <= 0) {
      return '#000000';
    }
    const candidato = desdeOklch(luz, croma, tono);
    if (contraste(BLANCO, candidato) >= MINIMO) {
      return candidato;
    }
  }
}

function secundario(texto: string, primario: string): string {
  for (let i = Math.round(MEZCLA_SECUNDARIO_MAXIMA / PASO_MEZCLA); i > 0; i--) {
    const mezcla = mezclar(texto, primario, i * PASO_MEZCLA);
    if (contraste(mezcla, primario) >= MINIMO) {
      return mezcla;
    }
  }
  return texto;
}

function suave(primario: string): string {
  for (let i = Math.round(MEZCLA_SUAVE / PASO_SUAVE); i > 0; i--) {
    const mezcla = mezclar(BLANCO, primario, i * PASO_SUAVE);
    if (contraste(TINTA, mezcla) >= MINIMO) {
      return mezcla;
    }
  }
  return BLANCO;
}

/** `base` con la parte `t` de `otro`, canal a canal en sRGB (redondeo como `Math.round` de Java). */
function mezclar(base: string, otro: string, t: number): string {
  const a = canales(base);
  const b = canales(otro);
  return hex(a.map((c, i) => redondear(c * (1 - t) + b[i] * t)));
}

function luminancia(color: string): number {
  const [r, g, b] = canales(color).map(lineal);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function lineal(canal: number): number {
  const c = canal / 255;
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function gamma(c: number): number {
  const recortado = Math.min(1, Math.max(0, c));
  return recortado <= 0.0031308 ? 12.92 * recortado : 1.055 * Math.pow(recortado, 1 / 2.4) - 0.055;
}

function oklch(color: string): [number, number, number] {
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

function desdeOklch(luz: number, croma: number, tono: number): string {
  const rad = (tono * Math.PI) / 180;
  const a = croma * Math.cos(rad);
  const b = croma * Math.sin(rad);
  const l = (luz + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (luz - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (luz - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
  const g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
  const bl = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;
  return hex([r, g, bl].map((c) => redondear(gamma(c) * 255)));
}

/** Como `Math.round` de Java (mitades hacia arriba); para valores ≥ 0 coincide con el de JS. */
function redondear(x: number): number {
  return Math.floor(x + 0.5);
}

function canales(color: string): number[] {
  return [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
}

function hex(c: number[]): string {
  return `#${c.map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}
