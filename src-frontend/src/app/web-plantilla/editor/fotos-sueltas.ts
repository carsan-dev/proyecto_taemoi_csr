import { recortar } from './textos-editor';

/**
 * Fotos sueltas del editor «Web · Contenido» (W12f-1; boceto `boceto-panel-contenido-web-comarcal.html`: `fotoCapa`,
 * `queFoto`, `abrirMenuFoto`, `abrirDescribir`). Lógica pura que comparten el panel y la capa de edición.
 *
 * Solo las tres fotos sueltas: la de la portada, la de la presentación y la de cada «Texto con foto». La galería y los
 * profesores van en W12f-2 (d539) y no se tocan aquí. En la fila de una foto, `texto` es su descripción (W12a).
 */

const FOTO_SUELTA = /^(?:portada|presentacion|texto_foto\.[1-9][0-9]?)\.foto$/;

/** `portada.foto`, `presentacion.foto` o `texto_foto.<n>.foto`. */
export function esFotoSuelta(clave: unknown): clave is string {
  return typeof clave === 'string' && FOTO_SUELTA.test(clave);
}

/** La clave del error «falta describirla» de una foto (`portada.foto.alt`); no es una clave de la API. */
export const claveDescripcion = (foto: string) => `${foto}.alt`;

/** La foto de una clave de error «falta describirla», o null. */
export function fotoDeDescripcion(clave: string): string | null {
  const foto = clave.endsWith('.alt') ? clave.slice(0, -4) : '';
  return esFotoSuelta(foto) ? foto : null;
}

/** Máximo de la descripción (boceto `MAX_ALT`; W12a: 150). */
export const MAX_DESCRIPCION_FOTO = 150;

/** Boceto `FORMATOS`. El límite es el de la API (`spring.servlet.multipart.max-file-size=50MB`). */
export const FORMATOS_FOTO = 'JPG, PNG o WebP, hasta 50 MB';

/** La foto es obligatoria en la portada: no se quita (boceto `abrirMenuFoto`, `opcional = k !== 'portada'`). */
export const fotoOpcional = (clave: string) => clave !== 'portada.foto';

/** El bloque «Texto con foto» de su foto (`texto_foto.3`), o null. */
export function bloqueDeFoto(clave: string): string | null {
  const m = /^(texto_foto\.[1-9][0-9]?)\.foto$/.exec(clave);
  return m ? m[1] : null;
}

/**
 * Boceto `queFoto`: «de la portada», «de la presentación» y, en un «Texto con foto», «de «su título»» (sin título,
 * «de Texto con foto»). `titulo` da el título del bloque.
 */
export function queFoto(clave: string, titulo: (bloque: string) => string): string {
  if (clave === 'portada.foto') {
    return 'de la portada';
  }
  const bloque = bloqueDeFoto(clave);
  if (bloque) {
    const t = titulo(bloque).trim();
    return t ? `de «${recortar(t, 40)}»` : 'de Texto con foto';
  }
  return 'de la presentación';
}

/** Lo que pone la pista de una foto vacía (boceto: `figura(…, vacio)`). */
export function textoFotoVacia(clave: string): string {
  if (clave === 'portada.foto') {
    return 'Añade la foto de portada';
  }
  return clave === 'presentacion.foto' ? 'Añade una foto del club' : 'Añade una foto';
}
