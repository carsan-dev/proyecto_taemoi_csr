/**
 * Galería del editor «Web · Contenido» (W12f-2a; boceto `boceto-panel-contenido-web-comarcal.html`: `galeriaHtml`,
 * `abrirMenuFoto`, `queFoto`, `cambios`). Lógica pura que comparten el panel y la capa de edición.
 *
 * Cada foto de la galería es una fila de imagen `galeria.<n>` (`ClavesWeb.GALERIA`, n de 1 a 99) con `texto` = su
 * descripción (obligatoria) y `orden` = su puesto (de 1 a 6, `ContenidoWebService`). Como mucho 6 fotos
 * (`ClavesWeb.MAXIMO_GALERIA`). El tema las pinta por `orden` y luego por número (`BloquesWeb.galeria`).
 *
 * La foto que se está subiendo y aún no tiene número va con la clave `galeria.nueva` (solo entre la capa y el panel;
 * nunca llega a la API): al terminar la subida, el panel le da su número.
 */

/** `ClavesWeb.MAXIMO_GALERIA`. */
export const MAX_GALERIA = 6;

/** El mayor número de una foto de la galería (`ClavesWeb`: de 1 a 99). */
export const MAX_NUMERO_GALERIA = 99;

/** La foto nueva mientras sube (boceto `s.subidas['galeria.nueva']`). */
export const GALERIA_NUEVA = 'galeria.nueva';

const FOTO_GALERIA = /^galeria\.([1-9][0-9]?)$/;

/** `galeria.<n>`. */
export function esFotoGaleria(clave: unknown): clave is string {
  return typeof clave === 'string' && FOTO_GALERIA.test(clave);
}

/** El número de una foto de la galería (`galeria.3` → 3), o null. */
export function numeroGaleria(clave: string): number | null {
  const m = FOTO_GALERIA.exec(clave);
  return m ? Number(m[1]) : null;
}

type FilaGaleria = { clave: string; orden: number; imagenUrl: string | null };

/** Las fotos de la galería en sus puestos, como las pinta el tema (`BloquesWeb.galeria`: por `orden` y por número). */
export function ordenGaleria(filas: readonly FilaGaleria[]): string[] {
  return filas
    .filter((f) => esFotoGaleria(f.clave) && !!f.imagenUrl)
    .map((f) => ({ clave: f.clave, orden: f.orden, n: numeroGaleria(f.clave)! }))
    .sort((a, b) => a.orden - b.orden || a.n - b.n)
    .map((f) => f.clave);
}

/** Boceto `queFoto` de una foto de la galería: «n.º 2 de la galería» (su puesto). */
export const queFotoGaleria = (puesto: number) => `n.º ${puesto} de la galería`;

/** Boceto `galeriaHtml`: el botón de añadir y la nota del tope. */
export const TEXTOS_GALERIA = {
  anadir: 'Añadir foto',
  anadirPrimera: 'Añade la primera foto',
  llena: `${MAX_GALERIA} de ${MAX_GALERIA} fotos`,
  llenaNota: 'Es el máximo. Quita una para añadir otra.',
  moverAntes: 'Mover antes',
  moverDespues: 'Mover después',
  quitar: 'Quitar de la galería',
} as const;

/** Boceto `estadosHtml`: «3 de 6 fotos» (y «· máximo» con 6). */
export const cuentaGaleria = (n: number) => `${n} de ${MAX_GALERIA} fotos${n >= MAX_GALERIA ? ' · máximo' : ''}`;

/** Boceto (Mover): «La foto pasa al puesto 2 de 5.». */
export const vozMoverFoto = (puesto: number, total: number) => `La foto pasa al puesto ${puesto} de ${total}.`;
