import { ErrorHandler } from '@angular/core';

/**
 * Decisión 173: tras un despliegue, los chunks perezosos de la versión anterior ya no existen y la navegación
 * falla al importarlos. Se recarga la página UNA vez para traer la versión nueva; una marca de tiempo en
 * sessionStorage impide recargar otra vez dentro de la ventana (sin bucles si el fallo es otro).
 */
export const CLAVE_RECARGA_CHUNK = 'taemoi.recargaPorChunk';
export const VENTANA_RECARGA_MS = 10_000;

const PATRONES_CHUNK = [
  /failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /importing a module script failed/i,
  /loading chunk [\w-]+ failed/i,
];

interface AlmacenSesion {
  getItem(clave: string): string | null;
  setItem(clave: string, valor: string): void;
}

/** ¿Es un fallo al cargar un chunk perezoso? Mira dentro de `rejection` (promesas) y `error` (NavigationError). */
export function esErrorDeCargaDeChunk(error: unknown, profundidad = 0): boolean {
  if (error === null || error === undefined || profundidad > 3) {
    return false;
  }
  if (typeof error === 'string') {
    return PATRONES_CHUNK.some((patron) => patron.test(error));
  }
  if (typeof error !== 'object') {
    return false;
  }
  const objeto = error as { name?: unknown; message?: unknown; rejection?: unknown; error?: unknown };
  if (objeto.name === 'ChunkLoadError') {
    return true;
  }
  if (typeof objeto.message === 'string' && PATRONES_CHUNK.some((patron) => patron.test(objeto.message as string))) {
    return true;
  }
  return esErrorDeCargaDeChunk(objeto.rejection, profundidad + 1) || esErrorDeCargaDeChunk(objeto.error, profundidad + 1);
}

/**
 * Recarga si no se recargó por esto en los últimos {@link VENTANA_RECARGA_MS}. Sin sessionStorage utilizable no
 * recarga: no podría evitar el bucle. Devuelve si ha recargado.
 */
export function recargarUnaVezPorChunk(
  almacen: AlmacenSesion | null | undefined,
  ahora: number,
  recargar: () => void
): boolean {
  if (!almacen) {
    return false;
  }
  try {
    const anterior = Number(almacen.getItem(CLAVE_RECARGA_CHUNK));
    if (Number.isFinite(anterior) && anterior > 0 && ahora - anterior < VENTANA_RECARGA_MS) {
      return false;
    }
    almacen.setItem(CLAVE_RECARGA_CHUNK, String(ahora));
  } catch {
    return false;
  }
  recargar();
  return true;
}

/** Recarga de la página real (navegador); en SSR o sin `window`, nada. */
export function recargarPaginaPorChunk(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  let almacen: AlmacenSesion | null = null;
  try {
    almacen = window.sessionStorage;
  } catch {
    almacen = null;
  }
  return recargarUnaVezPorChunk(almacen, Date.now(), () => window.location.reload());
}

/**
 * ErrorHandler global: un fallo de chunk recarga una vez; el resto, como el ErrorHandler de Angular. Se provee con
 * `useFactory` (ver app.config.ts).
 */
export class RecargaPorChunkErrorHandler extends ErrorHandler {
  constructor(private readonly recargar: () => boolean = recargarPaginaPorChunk) {
    super();
  }

  override handleError(error: unknown): void {
    if (esErrorDeCargaDeChunk(error) && this.recargar()) {
      return;
    }
    super.handleError(error);
  }
}
