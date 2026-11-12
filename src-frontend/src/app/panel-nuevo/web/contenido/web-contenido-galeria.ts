import { ordenCompleto } from '../../../web-plantilla/editor/bloques-editor';
import { MAX_GALERIA, MAX_NUMERO_GALERIA, numeroGaleria, ordenGaleria } from '../../../web-plantilla/editor/fotos-galeria';
import { conOrden } from './web-contenido-bloques';
import type { BloqueContenido } from './web-contenido-vista';

/**
 * Galería de «Web · Contenido» (W12f-2a; boceto `boceto-panel-contenido-web-comarcal.html`: `galeriaHtml`,
 * `simularSubida`, Mover y Quitar de `abrirMenuFoto`, `cambios`). Lógica pura sobre el borrador.
 *
 * Cada foto es una fila `galeria.<n>` con `orden` = su puesto visible (de 1 a 6). Añadir, mover y quitar vuelven a
 * numerar los puestos. Antes, los bloques de Inicio reciben su cabecera con el orden que ya tenían (como al ocultar uno,
 * `alternarOculto`): sin cabeceras, el tema coloca la galería por el menor `orden` de sus filas y cambiar los puestos la
 * movería de sitio.
 */

const numeros = (filas: readonly BloqueContenido[]) =>
  filas.map((f) => numeroGaleria(f.clave)).filter((n): n is number => n !== null);

/** El puesto de la foto (de 1 a 6), o 0 si no está. */
export const puestoGaleria = (filas: readonly BloqueContenido[], clave: string) => ordenGaleria(filas).indexOf(clave) + 1;

/**
 * El número de una foto nueva: uno más que el mayor de lo guardado, del borrador y de lo ya usado en esta sesión
 * (`usados`), sin rellenar huecos. Así una foto nueva nunca toma la clave de una guardada ni de una quitada sin guardar
 * («Volver a la anterior» no se cruza con otra foto). La API admite de 1 a 99 (`ClavesWeb.GALERIA`): si se llega al 99,
 * el menor libre (primero sin lo usado; luego, el menor que no esté guardado ni en el borrador).
 */
export function numeroGaleriaNuevo(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  usados: Iterable<number> = [],
): number | null {
  const vigentes = new Set([...numeros(filas), ...numeros(guardado)]);
  const todos = new Set([...vigentes, ...usados]);
  const mayor = Math.max(0, ...todos);
  if (mayor < MAX_NUMERO_GALERIA) {
    return mayor + 1;
  }
  for (const ocupados of [todos, vigentes]) {
    for (let n = 1; n <= MAX_NUMERO_GALERIA; n++) {
      if (!ocupados.has(n)) {
        return n;
      }
    }
  }
  return null;
}

/** Fija el orden de los bloques de Inicio con sus cabeceras y pone a cada foto de `claves` su puesto (1, 2…). */
function enPuestos(filas: readonly BloqueContenido[], claves: readonly string[]): BloqueContenido[] {
  const puesto = new Map(claves.map((clave, i) => [clave, i + 1]));
  return filas.map((f) => (puesto.has(f.clave) ? { ...f, orden: puesto.get(f.clave)! } : f));
}

const conCabeceras = (filas: readonly BloqueContenido[]) => conOrden(filas, ordenCompleto(filas));

/**
 * Boceto `simularSubida` con `galeria.nueva`: la foto subida entra la última, sin descripción. Null si la galería ya
 * tiene 6 fotos o no queda número.
 */
export function anadirFotoGaleria(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  usados: Iterable<number>,
  imagenId: number,
  imagenUrl: string,
): { filas: BloqueContenido[]; clave: string } | null {
  const lista = ordenGaleria(filas);
  const n = lista.length < MAX_GALERIA ? numeroGaleriaNuevo(filas, guardado, usados) : null;
  if (n === null) {
    return null;
  }
  const clave = `galeria.${n}`;
  const nueva: BloqueContenido = { clave, texto: '', imagenId, imagenUrl, orden: 0, oculto: false, permiso: null };
  return { filas: enPuestos([...conCabeceras(filas), nueva], [...lista, clave]), clave };
}

/** «Mover antes» (-1) o «Mover después» (1): cambia el puesto con la vecina. Null en los extremos. */
export function moverFotoGaleria(
  filas: readonly BloqueContenido[],
  clave: string,
  paso: -1 | 1,
): { filas: BloqueContenido[]; puesto: number; total: number } | null {
  const lista = ordenGaleria(filas);
  const i = lista.indexOf(clave);
  const j = i + paso;
  if (i < 0 || j < 0 || j >= lista.length) {
    return null;
  }
  [lista[i], lista[j]] = [lista[j], lista[i]];
  return { filas: enPuestos(conCabeceras(filas), lista), puesto: j + 1, total: lista.length };
}

/** «Quitar de la galería»: fuera la fila (se quita de la web al guardar) y las demás suben de puesto. */
export function quitarFotoGaleria(filas: readonly BloqueContenido[], clave: string): BloqueContenido[] {
  if (!filas.some((f) => f.clave === clave)) {
    return [...filas];
  }
  const resto = conCabeceras(filas).filter((f) => f.clave !== clave);
  return enPuestos(resto, ordenGaleria(resto));
}

/** Tras quitar una foto, a dónde va el foco: la que ocupa su puesto, la de antes o (sin fotos) el botón de añadir. */
export function vecinaGaleria(filas: readonly BloqueContenido[], clave: string): string | null {
  const lista = ordenGaleria(filas);
  const i = lista.indexOf(clave);
  return i < 0 ? null : (lista[i + 1] ?? lista[i - 1] ?? null);
}

/**
 * Boceto `cambios` de la galería: cada foto añadida o quitada cuenta uno (`galeria.<n>`); el orden, uno
 * (`galeria.orden`); y en cada foto que sigue, uno si cambia la foto (`galeria.<n>`) y otro si cambia su descripción
 * (`galeria.<n>.alt`).
 */
export function cambiosGaleria(guardado: readonly BloqueContenido[], borrador: readonly BloqueContenido[]): string[] {
  const antes = ordenGaleria(guardado);
  const ahora = ordenGaleria(borrador);
  const cambios = [...ahora.filter((c) => !antes.includes(c)), ...antes.filter((c) => !ahora.includes(c))];
  const comunes = ahora.filter((c) => antes.includes(c));
  if (comunes.join() !== antes.filter((c) => ahora.includes(c)).join()) {
    cambios.push('galeria.orden');
  }
  for (const clave of comunes) {
    const g = guardado.find((f) => f.clave === clave)!;
    const b = borrador.find((f) => f.clave === clave)!;
    if (g.imagenId !== b.imagenId) {
      cambios.push(clave);
    }
    if ((g.texto ?? '') !== (b.texto ?? '')) {
      cambios.push(`${clave}.alt`);
    }
  }
  return cambios;
}
