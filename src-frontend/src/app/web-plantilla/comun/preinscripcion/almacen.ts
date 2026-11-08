import { DATOS_VACIOS, DatosPreinscripcion } from './preinscripcion';

/**
 * Almacenamiento técnico de la preinscripción (fase4-web-plan.md §6, «Legales»: sin aviso de cookies): el borrador va en
 * `sessionStorage` (se pierde al cerrar la pestaña) y la última sede en `localStorage`. Sin almacenamiento (modo
 * privado, bloqueado), no pasa nada: se sigue sin guardar.
 */

/** El borrador guardado, con lo que falte o no sea del tipo esperado a vacío. La firma nunca se guarda. */
export function leerBorrador(almacen: Storage | null | undefined, clave: string): DatosPreinscripcion {
  let guardado: unknown = null;
  try {
    guardado = JSON.parse(almacen?.getItem(clave) ?? 'null');
  } catch {
    guardado = null;
  }
  const datos: DatosPreinscripcion = { ...DATOS_VACIOS };
  if (!guardado || typeof guardado !== 'object') {
    return datos;
  }
  const origen = guardado as Record<string, unknown>;
  for (const k of Object.keys(DATOS_VACIOS) as (keyof DatosPreinscripcion)[]) {
    const valor = origen[k];
    if (typeof DATOS_VACIOS[k] === 'boolean') {
      if (typeof valor === 'boolean') {
        (datos as unknown as Record<string, unknown>)[k] = valor;
      }
    } else if (typeof valor === 'string') {
      (datos as unknown as Record<string, unknown>)[k] = valor;
    }
  }
  if (datos.discapacidad !== 'si' && datos.discapacidad !== 'no') {
    datos.discapacidad = '';
  }
  return datos;
}

export function guardarBorrador(almacen: Storage | null | undefined, clave: string, datos: DatosPreinscripcion): void {
  try {
    almacen?.setItem(clave, JSON.stringify(datos));
  } catch {
    /* sin almacenamiento */
  }
}

export function borrarBorrador(almacen: Storage | null | undefined, clave: string): void {
  try {
    almacen?.removeItem(clave);
  } catch {
    /* sin almacenamiento */
  }
}

export function leerValor(almacen: Storage | null | undefined, clave: string): string | null {
  try {
    return almacen?.getItem(clave) ?? null;
  } catch {
    return null;
  }
}

export function guardarValor(almacen: Storage | null | undefined, clave: string, valor: string): void {
  try {
    almacen?.setItem(clave, valor);
  } catch {
    /* sin almacenamiento */
  }
}
