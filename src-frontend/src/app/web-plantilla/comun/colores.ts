/**
 * Colores derivados del color de la escuela con contraste WCAG 2.x AA (fase4-web-plan.md §6, «Datos»). Es la función
 * `derivarColores` del boceto `fase4-web/boceto-linea-comarcal.html` (bloque «color:inicio … color:fin»), pasada a
 * TypeScript sin cambiar sus reglas. Sin estilo: cada tema decide en qué variables CSS los pone.
 */

export const BLANCO = '#FFFFFF';
export const ANDEN = '#ECEEF0';
export const TINTA = '#101418';
/** Color de la escuela cuando no tiene uno válido (el del boceto). */
export const COLOR_POR_DEFECTO = '#0B5CAD';

type Rgb = [number, number, number];

export interface ColoresDerivados {
  base: string;
  /** Trazo, anillos de parada y días: ≥ 3:1 sobre blanco y sobre el gris de andén. */
  trazo: string;
  /** Botón primario: fondo y texto con ≥ 4,5:1. */
  botonFondo: string;
  botonTinta: string;
  /** Hover del botón: se aleja del texto, así el contraste nunca baja. */
  botonHover: string;
  /** Enlaces y botón secundario: ≥ 4,5:1 sobre blanco y andén. */
  texto: string;
  /** Sección de tarifas a sangre del color. */
  tarifaFondo: string;
  tarifaTinta: string;
  /** Texto del botón blanco de tarifas. */
  tarifaBotonTinta: string;
  cTrazo: number;
  cBoton: number;
  cBotonHover: number;
  cTexto: number;
  cTarifa: number;
  cTarifaBoton: number;
}

export function esColorHex(valor: string | null | undefined): valor is string {
  return valor != null && /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.test(valor.trim());
}

export function hexARgb(hex: string): Rgb {
  let h = String(hex).trim().replace('#', '');
  if (h.length === 3) {
    h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
  }
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

export function rgbAHex(c: readonly number[]): string {
  return (
    '#' +
    c
      .map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase()
  );
}

export function luminancia(hex: string): number {
  const c = hexARgb(hex).map((v) => {
    const s = v / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}

/** Relación de contraste WCAG entre dos colores (1 a 21). */
export function contraste(a: string, b: string): number {
  const la = luminancia(a);
  const lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function rgbAHsl(c: Rgb): Rgb {
  const r = c[0] / 255;
  const g = c[1] / 255;
  const b = c[2] / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  let h = 0;
  let s = 0;
  if (d) {
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    h /= 6;
  }
  return [h, s, l];
}

function hslARgb(h: number, s: number, l: number): Rgb {
  const f = (p: number, q: number, t: number): number => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  if (!s) {
    return [l * 255, l * 255, l * 255];
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  return [f(p, q, h + 1 / 3) * 255, f(p, q, h) * 255, f(p, q, h - 1 / 3) * 255];
}

/** Oscurece (baja la luminosidad, mantiene el tono) hasta cumplir el objetivo sobre todos los fondos. */
export function oscurecer(hex: string, objetivo: number, fondos: readonly string[]): string {
  const hsl = rgbAHsl(hexARgb(hex));
  let actual = rgbAHex(hexARgb(hex));
  const cumple = (c: string) => fondos.every((f) => contraste(c, f) >= objetivo);
  while (!cumple(actual) && hsl[2] > 0) {
    hsl[2] = Math.max(0, hsl[2] - 0.005);
    actual = rgbAHex(hslARgb(hsl[0], hsl[1], hsl[2]));
  }
  return actual;
}

export function mezclar(a: string, b: string, t: number): string {
  const x = hexARgb(a);
  const y = hexARgb(b);
  return rgbAHex(x.map((v, i) => v + (y[i] - v) * t));
}

/**
 * Las variantes accesibles del color de la escuela (reglas del boceto). Un color que no es hexadecimal (o null) usa
 * {@link COLOR_POR_DEFECTO}.
 */
export function derivarColores(color: string | null | undefined): ColoresDerivados {
  const base = rgbAHex(hexARgb(esColorHex(color) ? color : COLOR_POR_DEFECTO));
  const trazo = oscurecer(base, 3, [BLANCO, ANDEN]);

  let botonFondo = trazo;
  let botonTinta: string;
  if (contraste(BLANCO, trazo) >= 4.5) {
    botonTinta = BLANCO;
  } else if (contraste(TINTA, trazo) >= 4.5) {
    botonTinta = TINTA;
  } else {
    botonFondo = oscurecer(trazo, 4.5, [BLANCO]);
    botonTinta = BLANCO;
  }
  const botonHover = botonTinta === BLANCO ? mezclar(botonFondo, '#000000', 0.16) : mezclar(botonFondo, '#FFFFFF', 0.2);

  const texto = oscurecer(base, 4.5, [BLANCO, ANDEN]);

  let tarifaFondo = base;
  let tarifaTinta: string;
  if (contraste(BLANCO, base) >= 4.5) {
    tarifaTinta = BLANCO;
  } else if (contraste(TINTA, base) >= 4.5) {
    tarifaTinta = TINTA;
  } else {
    tarifaFondo = oscurecer(base, 4.5, [BLANCO]);
    tarifaTinta = BLANCO;
  }
  const tarifaBotonTinta = contraste(tarifaFondo, BLANCO) >= 4.5 ? tarifaFondo : TINTA;

  return {
    base,
    trazo,
    botonFondo,
    botonTinta,
    botonHover,
    texto,
    tarifaFondo,
    tarifaTinta,
    tarifaBotonTinta,
    cTrazo: Math.min(contraste(trazo, BLANCO), contraste(trazo, ANDEN)),
    cBoton: contraste(botonFondo, botonTinta),
    cBotonHover: contraste(botonHover, botonTinta),
    cTexto: Math.min(contraste(texto, BLANCO), contraste(texto, ANDEN)),
    cTarifa: contraste(tarifaFondo, tarifaTinta),
    cTarifaBoton: contraste(tarifaBotonTinta, BLANCO),
  };
}
