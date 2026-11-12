import {
  NOMBRE_PARTE,
  bloqueTextoFoto,
  defTexto,
  defectoFijo,
  esTextoEditable,
  paginaDeTexto,
  recortar,
} from '../../../web-plantilla/editor/textos-editor';
import type { PaginaEditor } from '../../../web-plantilla/editor/candados-linea-comarcal';
import { bloqueDeFoto, claveDescripcion, esFotoEditable, fotoDeDescripcion } from '../../../web-plantilla/editor/fotos-sueltas';
import { TEXTOS_PROFESORES, esFotoProfesor, nombreProfesor } from '../../../web-plantilla/editor/fotos-profesores';
import { TEXTOS_FOTOS, queFoto } from './web-contenido-fotos';
import { type BloqueContenido, nombrePagina } from './web-contenido-vista';

/**
 * Borrador y guardado de «Web · Contenido» (W12d; boceto `boceto-panel-contenido-web-comarcal.html`: `cambios`,
 * `errores`, `nombreError`, `pintarResumen`, `pintarBarra`, guardar; plan `fase4-w12-plan.md`, W12a «Endpoints»).
 * Lógica pura.
 *
 * El guardado es un reemplazo completo (`PUT /api/escuela/web/contenido`): van todas las filas vigentes (las del GET con
 * los cambios), también las que esta pieza no edita (fotos, cabeceras, orden, oculto, profesores), salvo los textos
 * iguales al texto por defecto del tema. La constancia del permiso de los profesores no se reenvía nunca: la API la
 * conserva. W12f-2b: solo `permiso: true` en `profesor.<n>.nombre` de los profesores con la casilla marcada que la
 * necesitan (`permisosParaEnviar`).
 */

/** Una fila del cuerpo del `PUT` (`ContenidoWebRequest.Bloque`). */
export interface BloquePeticion {
  clave: string;
  texto: string | null;
  imagenId: number | null;
  orden: number;
  oculto: boolean;
  /** Solo `true` en `profesor.<n>.nombre` con la casilla del permiso marcada (W12f-2b); si no, no va. */
  permiso?: true;
}

export interface PeticionContenido {
  version: number;
  bloques: BloquePeticion[];
}

/** Lo que pinta el tema en las claves sin guardar cuyo texto por defecto depende de los datos (lo lee la capa). */
export type Defectos = Readonly<Record<string, string>>;

/** Pone el texto de una clave en el borrador (si no estaba, una fila nueva sin foto). */
export function ponerTexto(filas: readonly BloqueContenido[], clave: string, texto: string): BloqueContenido[] {
  return filas.some((f) => f.clave === clave)
    ? filas.map((f) => (f.clave === clave ? { ...f, texto } : f))
    : [...filas, { clave, texto, imagenId: null, imagenUrl: null, orden: 0, oculto: false, permiso: null }];
}

/** El texto que se ve: el guardado o, sin fila, el del tema (null si no se sabe). */
export function valorTexto(filas: readonly BloqueContenido[], clave: string, defectos: Defectos): string | null {
  const fila = filas.find((f) => f.clave === clave);
  if (fila) {
    return fila.texto ?? '';
  }
  return defectoFijo(clave) ?? defectos[clave] ?? null;
}

/** Los textos cambiados respecto a lo guardado (boceto `cambios`: cada texto cuenta uno). */
export function clavesCambiadas(guardado: readonly BloqueContenido[], borrador: readonly BloqueContenido[], defectos: Defectos): string[] {
  const claves = new Set([...guardado, ...borrador].map((f) => f.clave).filter(esTextoEditable));
  return [...claves].filter((clave) => (valorTexto(guardado, clave, defectos) ?? '') !== (valorTexto(borrador, clave, defectos) ?? ''));
}

/** Boceto `errores()`: lo que falta para guardar, por clave. */
export function erroresContenido(borrador: readonly BloqueContenido[], defectos: Defectos): Record<string, string> {
  const errores: Record<string, string> = {};
  const titular = valorTexto(borrador, 'portada.titular', defectos);
  if (titular !== null && !titular.trim()) {
    errores['portada.titular'] = defTexto('portada.titular')!.falta;
  }
  if (!borrador.some((f) => f.clave === 'portada.foto' && f.imagenId !== null)) {
    errores['portada.foto'] = 'Añade la foto de portada.';
  }
  // W12f-1 (boceto `errores`): cada foto suelta puesta, descrita; un «Texto con foto» con foto, con su título.
  // W12f-2a: y cada foto de la galería, descrita.
  for (const fila of borrador) {
    if (!esFotoEditable(fila.clave) || fila.imagenId === null) {
      continue;
    }
    if (!(fila.texto ?? '').trim()) {
      // W12f-2b (boceto `errores`): la de un profesor, «Describe su foto.».
      errores[claveDescripcion(fila.clave)] = esFotoProfesor(fila.clave) ? TEXTOS_PROFESORES.describeSuFoto : TEXTOS_FOTOS.describe;
    }
    const bloque = bloqueDeFoto(fila.clave);
    if (bloque && !borrador.some((f) => f.clave === `${bloque}.titulo`)) {
      errores[`${bloque}.titulo`] = defTexto(`${bloque}.titulo`)!.falta;
    }
  }
  for (const fila of borrador) {
    const def = defTexto(fila.clave);
    if (!def || !def.obligatorio || (fila.texto ?? '').trim() || errores[fila.clave]) {
      continue;
    }
    // El título de un «Texto con foto» vacío del todo no hace falta (boceto: `estaVacio`).
    const bloque = bloqueTextoFoto(fila.clave);
    if (bloque && !conContenido(borrador, bloque)) {
      continue;
    }
    errores[fila.clave] = def.falta;
  }
  return errores;
}

function conContenido(filas: readonly BloqueContenido[], bloque: string): boolean {
  return filas.some(
    (f) => (f.clave === `${bloque}.texto` && (f.texto ?? '').trim() !== '') || (f.clave === `${bloque}.foto` && f.imagenId !== null),
  );
}

/**
 * El cuerpo del `PUT`: todas las filas del borrador menos los textos iguales a los del tema. `permisos`: los números de
 * profesor cuyo nombre lleva `permiso: true` (casilla marcada donde hace falta); en los demás no va nada.
 */
export function cuerpoGuardar(
  version: number,
  borrador: readonly BloqueContenido[],
  defectos: Defectos,
  permisos: ReadonlySet<number> = new Set(),
): PeticionContenido {
  const bloques: BloquePeticion[] = [];
  for (const f of borrador) {
    const fijo = esTextoEditable(f.clave) ? defectoFijo(f.clave) : null;
    if (fijo !== null && (f.texto ?? '').trim() === fijo) {
      continue;
    }
    // Campo a campo: el objeto `permiso` del GET (la constancia) nunca se reenvía.
    const bloque: BloquePeticion = { clave: f.clave, texto: f.texto, imagenId: f.imagenId, orden: f.orden, oculto: f.oculto };
    const n = /^profesor\.([1-9][0-9]?)\.nombre$/.exec(f.clave);
    if (n && permisos.has(Number(n[1]))) {
      bloque.permiso = true;
    }
    bloques.push(bloque);
  }
  // La API pide siempre el titular: sin fila, el que enseña el tema.
  const titular = defectos['portada.titular'];
  if (!borrador.some((f) => f.clave === 'portada.titular') && titular) {
    bloques.push({ clave: 'portada.titular', texto: titular, imagenId: null, orden: 0, oculto: false });
  }
  return { version, bloques };
}

/** Boceto `nombreError`: cómo se nombra cada dato en el resumen; null si no es de esta pantalla. */
export function nombreError(clave: string, borrador: readonly BloqueContenido[]): string | null {
  if (clave === 'portada.foto') {
    return 'Portada: falta la foto';
  }
  // W12f-2b (boceto `nombreError`): «Antonio Ruiz: nombre», «Profesor nuevo: permiso para publicar».
  const profesor = /^profesor\.([1-9][0-9]?)\.(nombre|cargo|permiso)$/.exec(clave);
  if (profesor) {
    const nombre = nombreProfesor(borrador.find((f) => f.clave === `profesor.${profesor[1]}.nombre`)?.texto);
    const que = profesor[2] === 'permiso' ? 'permiso para publicar' : profesor[2] === 'nombre' ? 'nombre' : 'cargo o grado';
    return `${nombre}: ${que}`;
  }
  const foto = fotoDeDescripcion(clave);
  if (foto) {
    return `Foto ${queFoto(foto, borrador)}: falta describirla`;
  }
  const def = defTexto(clave);
  if (!def) {
    return null;
  }
  if (def.ejemplo !== undefined) {
    // W12d-bis (boceto `nombreError`): fuera de Inicio, delante el nombre de la página («Una sede · Cartel de la sede: …»).
    const pagina = paginaDeTexto(clave);
    const delante = pagina === 'inicio' ? '' : `${nombrePagina(pagina)} · `;
    return `${delante}${NOMBRE_PARTE[def.parte]}: ${def.nombre.toLowerCase()} «${recortar(def.ejemplo, 30)}»`;
  }
  return `${nombreBloque(clave, def.parte, borrador)}: ${def.nombre.toLowerCase()}`;
}

/**
 * Una línea del resumen «Faltan N datos»: el nombre del dato (con enlace al sitio) o, si no es de esta pantalla, el
 * mensaje de la API. En la foto de portada, si el error viene de la API, su mensaje («Describe la foto.»).
 */
export function entradaResumen(
  clave: string,
  mensaje: string,
  borrador: readonly BloqueContenido[],
  delServidor: boolean,
): { clave: string; texto: string; enlace: boolean } {
  const nombre = nombreError(clave, borrador);
  // W12f-1: el error de la API en una foto (suelta o de la galería) lleva a la foto.
  if (esFotoEditable(clave) && delServidor && mensaje) {
    return { clave, texto: mensaje, enlace: true };
  }
  return { clave, texto: nombre ?? mensaje, enlace: nombre !== null };
}

function nombreBloque(clave: string, parte: string, borrador: readonly BloqueContenido[]): string {
  const bloque = bloqueTextoFoto(clave);
  if (!bloque) {
    return NOMBRE_PARTE[parte];
  }
  const titulo = (borrador.find((f) => f.clave === `${bloque}.titulo`)?.texto ?? '').trim();
  return titulo ? `Texto con foto «${recortar(titulo, 40)}»` : 'Texto con foto';
}

/**
 * W12d-bis (boceto `cambios`, `porPag`): los cambios de cada página para el selector «Página». Cada texto cuenta en su
 * página; las fotos, los bloques y los textos de la cabecera y el pie, en Inicio.
 */
export function cambiosPorPagina(claves: readonly string[], nBloques: number): Partial<Record<PaginaEditor, number>> {
  const n: Partial<Record<PaginaEditor, number>> = {};
  for (const clave of claves) {
    const pagina = esTextoEditable(clave) ? paginaDeTexto(clave) : 'inicio';
    n[pagina] = (n[pagina] ?? 0) + 1;
  }
  if (nBloques) {
    n.inicio = (n.inicio ?? 0) + nBloques;
  }
  return n;
}

export const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`;

export const TEXTOS_GUARDAR = {
  guardar: 'Guardar',
  guardando: 'Guardando…',
  descartar: 'Descartar',
  publicaAlGuardar: ' · Al guardar se publican en tu web.',
  guardadoPublicada: 'Cambios guardados. Ya se ven en tu web.',
  guardadoNueva: 'Cambios guardados. Saldrán en tu web cuando la publiques.',
  errorGuardar: 'No se pudieron guardar los cambios. Tu web sigue como estaba.',
  reintentar: 'Reintentar',
  descartados: 'Cambios descartados. Todo queda como en tu web.',
  resumenTexto: 'Tu web sigue como estaba. Corrige esto y vuelve a guardar:',
  conflictoTitulo: 'Otra persona ha guardado cambios en la web',
  recargar: 'Recargar la página',
  recargada: 'Página recargada. Ya ves la web como la dejó la otra persona.',
  salirTexto: 'Si sales ahora, se pierden. Tu web sigue como estaba.',
  salir: 'Salir sin guardar',
  seguir: 'Seguir editando',
} as const;

export const tituloResumen = (n: number) => (n === 1 ? 'Falta 1 dato para guardar' : `Faltan ${n} datos para guardar`);
export const textoCambios = (n: number) => plural(n, 'cambio sin guardar', 'cambios sin guardar');
export const textoConflicto = (n: number) =>
  'Los tuyos no se han guardado, para no borrar los suyos. Recarga la página para ver la web como está ahora y vuelve a ' +
  `hacer tus cambios. Al recargar se pierden ${n === 1 ? 'el cambio que tienes sin guardar' : `los ${n} cambios que tienes sin guardar`}.`;
export const barraConflicto = (n: number) => `No se pueden guardar tus ${plural(n, 'cambio', 'cambios')}.`;
export const tituloSalir = (n: number) => `Tienes ${textoCambios(n)}`;
