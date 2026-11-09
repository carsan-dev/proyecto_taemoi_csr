/**
 * Textos de las rallas hacia el examen (decisiones 510 y 513; boceto `boceto-rallas-cinturon.html`, textos aprobados).
 * Las rallas se cuentan en medias: de 0 a 10, 2 medias = 1 ralla y 10 = 5 rallas, listo para examen.
 */

export const MEDIAS_COMPLETAS = 10;

/** Las medias que se pintan: solo si las rallas se usan; si no, `null` (cinturón sin rallas). */
export function mediasVisibles(d: { mediasRallas?: number | null; aplicaRallas?: boolean | null } | null | undefined): number | null {
  if (!d?.aplicaRallas || d.mediasRallas === null || d.mediasRallas === undefined) {
    return null;
  }
  return Math.min(MEDIAS_COMPLETAS, Math.max(0, Math.trunc(d.mediasRallas)));
}

/** «media», «1», «2 y media». */
function cantidad(n: number): string {
  const enteras = n >> 1;
  const media = n & 1;
  if (n === 1) return 'media';
  return `${enteras}${media ? ' y media' : ''}`;
}

/** La cuenta de la hoja, el carnet y la ficha: «Sin rallas», «Media ralla de 5», «2 y media de 5 rallas». */
export function textoCuentaRallas(n: number): string {
  if (n === 0) return 'Sin rallas';
  if (n === 1) return 'Media ralla de 5';
  return `${cantidad(n)} de 5 rallas`;
}

/** Lo que falta para el examen; vacío con las 5 rallas. */
export function textoFaltaRallas(n: number): string {
  const resto = MEDIAS_COMPLETAS - n;
  if (resto <= 0) return '';
  if (resto === 1) return 'Le falta media para el examen.';
  if (resto === 2) return 'Le falta 1 para el examen.';
  return `Le faltan ${cantidad(resto)} para el examen.`;
}

/** Para el lector de pantalla, tras el grado: «sin rallas», «media ralla de 5», «2 y media de 5 rallas». */
export function textoOcultoRallas(n: number): string {
  if (n === 0) return 'sin rallas';
  if (n === 1) return 'media ralla de 5';
  return `${cantidad(n)} de 5 rallas`;
}

/** Femenino solo con `FEMENINO` en la ficha; sin dato, masculino genérico (decisión 513 d). */
export function esFemenino(genero: string | null | undefined): boolean {
  return genero === 'FEMENINO';
}

/** «Lista para examen» o «Listo para examen». */
export function textoListo(genero: string | null | undefined): string {
  return `${esFemenino(genero) ? 'Lista' : 'Listo'} para examen`;
}

/** «apta» o «apto». */
export function textoApto(genero: string | null | undefined): string {
  return esFemenino(genero) ? 'apta' : 'apto';
}

/** Los cuatro pasos de la hoja, en su orden: −2 («− Una»), −1 («− Media»), +1 («+ Media») y +2 («+ Una»). */
export const PASOS_RALLAS = [
  { paso: -2, texto: '− Una', etiqueta: 'Quitar una ralla' },
  { paso: -1, texto: '− Media', etiqueta: 'Quitar media ralla' },
  { paso: 1, texto: '+ Media', etiqueta: 'Añadir media ralla' },
  { paso: 2, texto: '+ Una', etiqueta: 'Añadir una ralla' },
] as const;

/** El paso no cabe: deja menos de 0 o más de 10 medias (cada botón hace siempre lo que dice; duda g). */
export function pasoBloqueado(n: number, paso: number): boolean {
  const destino = n + paso;
  return destino < 0 || destino > MEDIAS_COMPLETAS;
}

/** La nota bajo los pasos cuando alguno está deshabilitado por el límite. */
export function notaPasos(n: number): string {
  if (n === 0) return 'Aún no tiene rallas.';
  if (n === 1) return 'Solo tiene media ralla.';
  if (n === MEDIAS_COMPLETAS - 1) return 'Solo cabe media ralla más.';
  return '';
}
