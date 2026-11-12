import {
  type TipoBloque,
  esFijo,
  esFilaDelBloque,
  esMovible,
  esTextoFoto,
  estaOculto,
  existeTipo,
  nombreBloque,
  ordenCompleto,
  parteDeFila,
} from '../../../web-plantilla/editor/bloques-editor';
import { NOMBRE_PARTE } from '../../../web-plantilla/editor/textos-editor';
import type { BloqueContenido } from './web-contenido-vista';

/**
 * Bloques de Inicio en el borrador de «Web · Contenido» (W12e; boceto `boceto-panel-contenido-web-comarcal.html`:
 * `mover`, `alternarOculto`, `quitarBloque`, `anadirBloque`, `nuevoBloque`, `cambios`). Lógica pura.
 *
 * El orden y lo oculto van en las filas de cabecera (W12a): mover cambia el `orden` de las cabeceras; ocultar, el
 * `oculto` de la suya; añadir pone su cabecera (un «Texto con foto» nuevo, con el menor número libre); quitar quita la
 * cabecera y todas sus filas. La primera vez que se toca un bloque, todos los de Inicio reciben su cabecera con el orden
 * que ya tenían (sin cabeceras, el tema ordena de otra forma: mezclarlas cambiaría el orden).
 *
 * `presentes`: los bloques que la capa dice que hay en la página. Las fijas sin datos no se pintan y no cuentan como
 * puesto; las movibles se pintan siempre en el editor (también ocultas o vacías).
 */

/** El mayor número de un «Texto con foto» (`ClavesWeb`: de 1 a 99). */
const MAX_TEXTO_FOTO = 99;

const cabecera = (clave: string, orden: number): BloqueContenido => ({
  clave,
  texto: null,
  imagenId: null,
  imagenUrl: null,
  orden,
  oculto: false,
  permiso: null,
});

/** Los bloques en sus puestos (boceto `s.datos.orden`): el orden del tema sin las fijas que no se pintan. */
export function ordenVisible(filas: readonly BloqueContenido[], presentes: ReadonlySet<string> | null): string[] {
  return ordenCompleto(filas).filter((id) => !esFijo(id) || !presentes || presentes.has(id));
}

/** Pone a cada bloque de `ids` su cabecera con `orden` = su puesto (las que faltan, nuevas y visibles). */
export function conOrden(filas: readonly BloqueContenido[], ids: readonly string[]): BloqueContenido[] {
  const puesto = new Map(ids.map((id, i) => [id, i]));
  const resto = filas.map((f) => (puesto.has(f.clave) ? { ...f, orden: puesto.get(f.clave)! } : f));
  const faltan = ids.filter((id) => !filas.some((f) => f.clave === id)).map((id) => cabecera(id, puesto.get(id)!));
  return [...resto, ...faltan];
}

/** Boceto `mover`: cambia el bloque con el de al lado en los puestos (las fijas se saltan, no se mueven). */
export function moverBloque(
  filas: readonly BloqueContenido[],
  id: string,
  paso: -1 | 1,
  presentes: ReadonlySet<string> | null,
): { filas: BloqueContenido[]; puesto: number; total: number } | null {
  const visible = ordenVisible(filas, presentes);
  const i = visible.indexOf(id);
  const j = i + paso;
  if (!esMovible(id) || i < 0 || j < 0 || j >= visible.length) {
    return null;
  }
  const vecino = visible[j];
  const todos = ordenCompleto(filas).filter((x) => x !== id);
  todos.splice(todos.indexOf(vecino) + (paso > 0 ? 1 : 0), 0, id);
  return { filas: conOrden(filas, todos), puesto: j, total: visible.length };
}

/** Boceto `alternarOculto`. Solo los movibles. */
export function alternarOculto(filas: readonly BloqueContenido[], id: string): BloqueContenido[] {
  if (!esMovible(id)) {
    return [...filas];
  }
  const oculto = !estaOculto(filas, id);
  return conOrden(filas, ordenCompleto(filas)).map((f) => (f.clave === id ? { ...f, oculto } : f));
}

/**
 * El número del «Texto con foto» nuevo: el menor libre de 1 a 99 que no usa el borrador ni lo guardado (uno quitado y
 * sin guardar no se reutiliza: sería el mismo bloque con otro contenido). Si ya no queda, el menor libre del borrador.
 */
export function numeroTextoFotoLibre(filas: readonly BloqueContenido[], guardado: readonly BloqueContenido[]): number | null {
  const usados = (lista: readonly BloqueContenido[]) =>
    new Set(lista.map((f) => /^texto_foto\.([1-9][0-9]?)(\.|$)/.exec(f.clave)).filter((m) => !!m).map((m) => Number(m![1])));
  const borrador = usados(filas);
  const todos = new Set([...borrador, ...usados(guardado)]);
  for (const libres of [todos, borrador]) {
    for (let n = 1; n <= MAX_TEXTO_FOTO; n++) {
      if (!libres.has(n)) {
        return n;
      }
    }
  }
  return null;
}

/** Boceto `anadirBloque`: su cabecera en el puesto `pos` de los visibles (al final, tras el último). */
export function anadirBloque(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  pos: number,
  tipo: TipoBloque,
  presentes: ReadonlySet<string> | null,
): { filas: BloqueContenido[]; id: string } | null {
  if (existeTipo(filas, tipo)) {
    return null;
  }
  const n = tipo === 'texto_foto' ? numeroTextoFotoLibre(filas, guardado) : null;
  if (tipo === 'texto_foto' && n === null) {
    return null;
  }
  const id = tipo === 'texto_foto' ? `texto_foto.${n}` : tipo;
  const visible = ordenVisible(filas, presentes);
  const p = Math.max(0, Math.min(pos, visible.length));
  const todos = ordenCompleto(filas);
  const donde = p < visible.length ? todos.indexOf(visible[p]) : visible.length ? todos.indexOf(visible[visible.length - 1]) + 1 : todos.length;
  todos.splice(donde, 0, id);
  return { filas: conOrden([...filas, cabecera(id, 0)], todos), id };
}

/** Boceto `quitable`: cada «Texto con foto» y los bloques añadidos que no están guardados. */
export function esQuitable(id: string, guardado: readonly BloqueContenido[]): boolean {
  return esTextoFoto(id) || (id === 'sedes' && !guardado.some((f) => f.clave === 'sedes'));
}

/** Boceto `quitarBloque`: fuera la cabecera y todas las filas del bloque. */
export function quitarBloque(filas: readonly BloqueContenido[], id: string): BloqueContenido[] {
  return filas.filter((f) => !esFilaDelBloque(f.clave, id));
}

/** Las filas del bloque (para cortar sus subidas y sus errores al quitarlo). */
export const filasDelBloque = (filas: readonly BloqueContenido[], id: string): string[] =>
  filas.filter((f) => esFilaDelBloque(f.clave, id)).map((f) => f.clave);

export interface CambiosBloques {
  /** Cambios que cuentan (boceto `cambios`): el orden, uno; cada oculto cambiado, uno; cada añadido o quitado, uno. */
  n: number;
  /** Bloques con «Sin guardar» por el orden, lo oculto o por ser nuevos. */
  bloques: Set<string>;
  /** Bloques añadidos o quitados: sus textos y fotos no cuentan aparte (el boceto cuenta el bloque entero). */
  fuera: Set<string>;
}

export function cambiosBloques(guardado: readonly BloqueContenido[], borrador: readonly BloqueContenido[]): CambiosBloques {
  const antes = ordenCompleto(guardado);
  const ahora = ordenCompleto(borrador);
  const bloques = new Set<string>();
  const fuera = new Set<string>();
  let n = 0;
  if (antes.join() !== ahora.join()) {
    n++;
    ahora.forEach((id, i) => {
      if (antes[i] !== id && !esFijo(id)) {
        bloques.add(id);
      }
    });
  }
  for (const id of ahora) {
    if (!antes.includes(id)) {
      n++;
      bloques.add(id);
      fuera.add(id);
    } else if (esMovible(id) && estaOculto(guardado, id) !== estaOculto(borrador, id)) {
      n++;
      bloques.add(id);
    }
  }
  for (const id of antes) {
    if (!ahora.includes(id)) {
      n++;
      fuera.add(id);
    }
  }
  return { n, bloques, fuera };
}

/** Si la clave cambiada es de un bloque añadido o quitado (no cuenta aparte). */
export const esDeBloqueFuera = (clave: string, fuera: ReadonlySet<string>): boolean => {
  const parte = parteDeFila(clave);
  return !!parte && fuera.has(parte);
};

/** Las partes con «Sin guardar»: los bloques de `cambiosBloques` y las de cada texto o foto cambiados. */
export function partesSinGuardar(cambios: CambiosBloques, claves: readonly string[]): string[] {
  const partes = new Set(cambios.bloques);
  for (const clave of claves) {
    const parte = parteDeFila(clave);
    if (parte && parte !== 'texto_foto' && (parte in NOMBRE_PARTE || /^texto_foto\.\d+$/.test(parte))) {
      partes.add(parte);
    }
  }
  return [...partes];
}

/** Boceto `pintarHoja`: «Puesto 2 de 7 · Oculto en la web». */
export function lineaHoja(puesto: number, total: number, oculto: boolean, vacio: boolean): string {
  return `Puesto ${puesto + 1} de ${total} · ${oculto ? 'Oculto en la web' : vacio ? 'Vacío: no sale en la web' : 'Se ve en la web'}`;
}

/** Voces del boceto (`mover`, `alternarOculto`, `quitarBloque`, `anadirBloque`). */
export const vozMover = (nombre: string, puesto: number, total: number) => `${nombre} pasa al puesto ${puesto + 1} de ${total}.`;
export const vozOculto = (nombre: string, oculto: boolean) =>
  oculto ? `${nombre}: oculto. Al guardar deja de salir en tu web.` : `${nombre}: visible. Al guardar vuelve a salir en tu web.`;
export const vozQuitado = (nombre: string) => `${nombre}: quitado. Se quita de la web al guardar.`;
export const vozAnadido = (tipo: TipoBloque, pos: number) => `${NOMBRE_PARTE[tipo]} añadido en el puesto ${pos + 1}. Escribe su contenido.`;

/** Boceto `quitarBloque`: la confirmación («¿Quitar «título» de tu web?»). */
export function confirmacionQuitar(id: string, filas: readonly BloqueContenido[]): { titulo: string; mensaje: string; accion: string; cancelar: string } {
  const nombre = nombreBloque(id, filas).replace(/^Texto con foto «(.*)»$/, '$1');
  return {
    titulo: `¿Quitar «${nombre}» de tu web?`,
    mensaje: 'Se quita al guardar. Hasta entonces, «Descartar» lo recupera.',
    accion: 'Quitar bloque',
    cancelar: 'Cancelar',
  };
}
