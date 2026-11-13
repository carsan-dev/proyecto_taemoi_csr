import { HttpErrorResponse } from '@angular/common/http';

import { esFotoGaleria } from '../../../web-plantilla/editor/fotos-galeria';
import { MAX_DESCRIPCION_FOTO, esFotoEditable, esFotoSuelta, queFoto as queFotoDe } from '../../../web-plantilla/editor/fotos-sueltas';
import type { EstadoSubidaFoto } from '../../../web-plantilla/editor/puente-editor';
import { cambiosGaleria, puestoGaleria } from './web-contenido-galeria';
import type { BloqueContenido } from './web-contenido-vista';

/**
 * Fotos sueltas de «Web · Contenido» (W12f-1; boceto `boceto-panel-contenido-web-comarcal.html`: `fotoCapa`,
 * `subidaHtml`, `abrirMenuFoto`, `abrirDescribir`, `simularSubida`, `cambios`, `esCambiada`; plan `fase4-w12-plan.md`,
 * W12f y W12a «Endpoints»). Lógica pura sobre el borrador.
 *
 * Una foto suelta es una fila de imagen: `imagenId` (de `POST /api/escuela/web/imagenes`), `imagenUrl` para la vista
 * previa y `texto` = su descripción. Quitarla es quitar la fila (la API no admite una fila de imagen sin foto).
 */

export type FalloSubida = Extract<EstadoSubidaFoto, { estado: 'error' }>;

/** El límite de la API (`spring.servlet.multipart.max-file-size=50MB`). */
export const MAX_BYTES_FOTO = 50 * 1024 * 1024;
const MB = 1024 * 1024;
const TIPOS = new Set(['image/jpeg', 'image/png', 'image/webp']);
const EXTENSION = /\.(jpe?g|png|webp)$/i;
const HEIC = /\.(heic|heif)$/i;
const TIPO_HEIC = /^image\/hei[cf](-sequence)?$/i;

export const TEXTOS_FOTOS = {
  demasiadoGrande: 'Demasiado grande',
  formato: 'Formato no admitido',
  noSubida: 'No se pudo subir la foto',
  /** `DocumentoSecurityUtils.MENSAJE_HEIC` de la API. */
  heic: 'Convierte la foto a JPG o desactiva HEIF en la cámara.',
  /** `ImagenServiceImpl.MENSAJE_FOTO_BLOQUE_FORMATO` de la API. */
  soloFormatos: 'La foto tiene que ser JPG, PNG o WebP.',
  reintentar: 'Inténtalo de nuevo.',
  describe: 'Describe la foto.',
  barraSubiendo: 'Se está subiendo una foto. Podrás guardar cuando termine.',
  vozSubiendo: 'Subiendo la foto.',
  vozSubida: 'Foto subida. Describe qué se ve.',
  vozCancelada: 'Subida cancelada.',
  vozAnterior: 'Vuelve la foto anterior.',
  vozQuitada: 'Foto quitada. Se quita de la web al guardar.',
  /** «Describir la foto» como hoja del panel (boceto `abrirDescribir`). */
  describirEtiqueta: 'Descripción',
  describirObligatorio: 'obligatorio',
  describirAyuda: 'Qué se ve, para quien no puede verla. Sin nombres de alumnos.',
  describirListo: 'Listo',
} as const;

/** Boceto `abrirDescribir`: «N / 150» y, al llegar, « · máximo». */
export function contadorDescripcion(n: number): string {
  return `${n} / ${MAX_DESCRIPCION_FOTO}${n >= MAX_DESCRIPCION_FOTO ? ' · máximo' : ''}`;
}

const pesa = (nombre: string, bytes: number) => `«${nombre}» pesa ${Math.ceil(bytes / MB)} MB. Usa una foto de menos de 50 MB.`;
const conNombre = (nombre: string, mensaje: string) => `«${nombre}»: ${mensaje.charAt(0).toLowerCase()}${mensaje.slice(1)}`;

/** Lo que se ve antes de llamar a la API (boceto `?subida=grande|tipo`): formato y tamaño. Null si vale. */
export function comprobarArchivo(archivo: Pick<File, 'name' | 'type' | 'size'>): FalloSubida | null {
  const { name: nombre, type: tipo } = archivo;
  if (HEIC.test(nombre) || TIPO_HEIC.test(tipo)) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.formato, msg: conNombre(nombre, TEXTOS_FOTOS.heic) };
  }
  // La API mira el contenido; aquí basta con el tipo o la extensión (hay navegadores que no dan el tipo).
  if (!TIPOS.has(tipo.toLowerCase()) && !EXTENSION.test(nombre)) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.formato, msg: conNombre(nombre, TEXTOS_FOTOS.soloFormatos) };
  }
  if (archivo.size > MAX_BYTES_FOTO) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.demasiadoGrande, msg: pesa(nombre, archivo.size) };
  }
  return null;
}

/**
 * El fallo de `POST /api/escuela/web/imagenes` con el texto del boceto: 413 (pasa de 50 MB), 400 de formato (HEIC u otro)
 * o de tamaño (más de 60 megapíxeles) y, si no, «No se pudo subir la foto» con el mensaje de la API o «Inténtalo de
 * nuevo.».
 */
export function falloDeSubida(archivo: Pick<File, 'name' | 'size'>, error: unknown): FalloSubida {
  const nombre = archivo.name;
  const http = error instanceof HttpErrorResponse ? error : null;
  if (http?.status === 413) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.demasiadoGrande, msg: pesa(nombre, archivo.size) };
  }
  const cuerpo = http?.status === 400 ? (http.error as { mensaje?: unknown } | null) : null;
  const mensaje = typeof cuerpo?.mensaje === 'string' ? cuerpo.mensaje.trim() : '';
  if (mensaje && /HEIF|JPG, PNG o WebP/.test(mensaje)) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.formato, msg: conNombre(nombre, mensaje) };
  }
  if (mensaje && /demasiado grande/i.test(mensaje)) {
    return { estado: 'error', titulo: TEXTOS_FOTOS.demasiadoGrande, msg: conNombre(nombre, mensaje) };
  }
  return { estado: 'error', titulo: TEXTOS_FOTOS.noSubida, msg: conNombre(nombre, mensaje || TEXTOS_FOTOS.reintentar) };
}

const fila = (filas: readonly BloqueContenido[], clave: string) => filas.find((f) => f.clave === clave);

/** Boceto `simularSubida`: la foto subida entra en el borrador, sin descripción (se describe a continuación). */
export function ponerFoto(filas: readonly BloqueContenido[], clave: string, imagenId: number, imagenUrl: string): BloqueContenido[] {
  return fila(filas, clave)
    ? filas.map((f) => (f.clave === clave ? { ...f, imagenId, imagenUrl, texto: '' } : f))
    : [...filas, { clave, texto: '', imagenId, imagenUrl, orden: 0, oculto: false, permiso: null }];
}

/** «Quitar foto»: sin foto no hay fila (la API no admite una fila de imagen sin `imagenId`). */
export function quitarFoto(filas: readonly BloqueContenido[], clave: string): BloqueContenido[] {
  return filas.filter((f) => f.clave !== clave);
}

/** «Volver a la anterior»: la foto guardada con su descripción (en la galería, en el puesto que tiene ahora). */
export function volverALaAnterior(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  clave: string,
): BloqueContenido[] {
  const anterior = fila(guardado, clave);
  if (!anterior) {
    return [...filas];
  }
  return fila(filas, clave) ? filas.map((f) => (f.clave === clave ? { ...anterior, orden: f.orden } : f)) : [...filas, anterior];
}

/** La descripción (en la fila: `texto`), como mucho 150. Sin foto no hay nada que describir. */
export function describirFoto(filas: readonly BloqueContenido[], clave: string, texto: string): BloqueContenido[] {
  const recortado = texto.slice(0, MAX_DESCRIPCION_FOTO);
  return filas.map((f) => (f.clave === clave && f.imagenId !== null ? { ...f, texto: recortado } : f));
}

const clavesDeFotos = (a: readonly BloqueContenido[], b: readonly BloqueContenido[], esDeAqui: (clave: string) => boolean = esFotoEditable) =>
  [...new Set([...a, ...b].map((f) => f.clave).filter(esDeAqui))];

/**
 * Boceto `cambios`: cada foto suelta cuenta uno si cambia la foto y otro si cambia su descripción (`clave` y
 * `clave.alt`); la galería, como dice `cambiosGaleria` (añadidas, quitadas, orden, foto y descripción).
 */
export function fotosCambiadas(guardado: readonly BloqueContenido[], borrador: readonly BloqueContenido[]): string[] {
  const cambios: string[] = [];
  for (const clave of clavesDeFotos(guardado, borrador, esFotoSuelta)) {
    const g = fila(guardado, clave);
    const b = fila(borrador, clave);
    if ((g?.imagenId ?? null) !== (b?.imagenId ?? null)) {
      cambios.push(clave);
    }
    if ((g?.texto ?? '') !== (b?.texto ?? '')) {
      cambios.push(`${clave}.alt`);
    }
  }
  return [...cambios, ...cambiosGaleria(guardado, borrador)];
}

/**
 * Boceto `esCambiada` («Nueva, sin guardar»: tiene foto y no es la guardada) y «Volver a la anterior» (cambiada y con
 * una guardada).
 */
export function estadoFotos(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
): { cambiadas: string[]; anteriores: string[] } {
  const cambiadas: string[] = [];
  const anteriores: string[] = [];
  for (const clave of clavesDeFotos(guardado, borrador)) {
    const g = fila(guardado, clave);
    const b = fila(borrador, clave);
    if (b?.imagenId != null && b.imagenId !== (g?.imagenId ?? null)) {
      cambiadas.push(clave);
      if (g?.imagenId != null) {
        anteriores.push(clave);
      }
    }
  }
  return { cambiadas, anteriores };
}

/**
 * Tras guardar, lo que cambió en las fotos mientras salía el `PUT` se vuelve a poner sobre lo guardado (como los
 * textos tecleados durante «Guardando…»).
 */
export function conFotosCambiadasDesde(
  base: readonly BloqueContenido[],
  enviado: readonly BloqueContenido[],
  actual: readonly BloqueContenido[],
): BloqueContenido[] {
  let filas = [...base];
  for (const clave of clavesDeFotos(enviado, actual)) {
    const e = fila(enviado, clave);
    const a = fila(actual, clave);
    // En la galería también cuenta el puesto (movida o reordenada al quitar otra).
    const galeria = esFotoGaleria(clave);
    if (e?.imagenId === a?.imagenId && e?.texto === a?.texto && e?.imagenUrl === a?.imagenUrl && (!galeria || e?.orden === a?.orden)) {
      continue;
    }
    filas = filas.filter((f) => f.clave !== clave);
    if (a) {
      const g = fila(base, clave);
      filas.push(g ? { ...g, imagenId: a.imagenId, imagenUrl: a.imagenUrl, texto: a.texto, ...(galeria ? { orden: a.orden } : {}) } : a);
    }
  }
  return filas;
}

/** Boceto `queFoto` sobre el borrador (el título del «Texto con foto», el puesto en la galería, el nombre del profesor). */
export function queFoto(clave: string, borrador: readonly BloqueContenido[]): string {
  return queFotoDe(
    clave,
    (bloque) => fila(borrador, `${bloque}.titulo`)?.texto ?? '',
    (foto) => puestoGaleria(borrador, foto),
    (foto) => fila(borrador, foto.replace(/\.foto$/, '.nombre'))?.texto ?? '',
  );
}
