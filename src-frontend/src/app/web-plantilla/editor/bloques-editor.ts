import { BloquesWeb, ordenarSecciones } from '../comun/bloques';
import type { BloqueWeb } from '../datos/modelos';
import { NOMBRE_PARTE, bloqueTextoFoto, defTexto, recortar } from './textos-editor';
import { bloqueDeFoto } from './fotos-sueltas';

/**
 * Bloques de Inicio en «Web · Contenido» (W12e; boceto `boceto-panel-contenido-web-comarcal.html`: `TIPOS`, `ID_UNICO`,
 * `FIJOS`, `nombreBloque`, `quitable`, `estaVacio`, `barraHtml`, `abrirMenuAnadir`). Lógica pura que comparten la capa
 * de edición (dentro del iframe) y el panel.
 *
 * El id de un bloque es la clave de su fila de cabecera (W12a, `ClavesWeb`): las movibles `presentacion`,
 * `profesores`, `galeria`, `sedes` y `texto_foto.<n>` (de 1 a 99), y las fijas `disciplinas`, `eventos` y `tarifas`,
 * que se pueden saltar al subir o bajar pero no se mueven ni se ocultan. En el DOM de la capa, el «Texto con foto» va
 * con el id de su sección (`texto-foto-<n>`).
 */

/** Las fijas de Inicio (boceto `FIJOS`), en el orden del tema. */
export const FIJOS_INICIO = ['disciplinas', 'eventos', 'tarifas'] as const;

/** Los tipos de bloque del menú «Añadir bloque» (boceto `TIPOS`, d518). */
export const TIPOS_BLOQUE = ['presentacion', 'profesores', 'galeria', 'sedes', 'texto_foto'] as const;
export type TipoBloque = (typeof TIPOS_BLOQUE)[number];

/** Boceto `TIPOS`: nombre, descripción e icono de cada tipo en el menú. */
export const MENU_TIPOS: readonly { tipo: TipoBloque; nombre: string; desc: string; icono: 'texto' | 'foto' | 'alumnos' }[] = [
  { tipo: 'presentacion', nombre: 'Presentación', desc: 'Quiénes sois: rótulo, título, texto y una foto grande.', icono: 'texto' },
  { tipo: 'profesores', nombre: 'Profesores', desc: 'Una tarjeta por profesor, con foto, nombre, grado y sus sedes.', icono: 'alumnos' },
  { tipo: 'galeria', nombre: 'Galería', desc: 'Hasta 6 fotos numeradas de clases, exámenes y campeonatos.', icono: 'foto' },
  { tipo: 'sedes', nombre: 'Fotos de las sedes', desc: 'La línea con la foto de cada sede. Las fotos se ponen en Sedes.', icono: 'foto' },
  { tipo: 'texto_foto', nombre: 'Texto con foto', desc: 'Un título, un texto y una foto. Puedes poner varios.', icono: 'texto' },
];

/** Boceto `abrirMenuAnadir`: un tipo único que ya está. */
export const YA_ESTA = 'Ya está en tu web. Si no lo quieres, ocúltalo.';
/** Boceto `barraHtml`: la portada no tiene acciones. */
export const NOTA_PORTADA = 'Siempre arriba. Pulsa un texto o la foto para cambiarlos.';

/** Lo que se puede hacer con un bloque desde su barra o desde la hoja. */
export const ACCIONES_BLOQUE = ['subir', 'bajar', 'ocultar', 'mostrar', 'quitar'] as const;
export type AccionBloque = (typeof ACCIONES_BLOQUE)[number];

/** Como mucho, los puestos de Inicio: 4 únicos, 3 fijos y 99 «Texto con foto». */
export const MAX_BLOQUES = 106;

const TEXTO_FOTO = /^texto_foto\.([1-9][0-9]?)$/;
const UNICOS: readonly string[] = ['presentacion', 'profesores', 'galeria', 'sedes'];

export const esFijo = (id: string): boolean => (FIJOS_INICIO as readonly string[]).includes(id);
export const esTextoFoto = (id: string): boolean => TEXTO_FOTO.test(id);

/** Un id de bloque de Inicio (cabecera movible o fija). */
export function esIdBloque(valor: unknown): boolean {
  return typeof valor === 'string' && (UNICOS.includes(valor) || esFijo(valor) || esTextoFoto(valor));
}

/** Un bloque que se mueve y se oculta (no fijo). */
export const esMovible = (id: string): boolean => esIdBloque(id) && !esFijo(id);

export function esTipoBloque(valor: unknown): valor is TipoBloque {
  return typeof valor === 'string' && (TIPOS_BLOQUE as readonly string[]).includes(valor);
}

/** El id del bloque en el DOM de la capa (`texto_foto.3` → `texto-foto-3`). */
export const idEnDom = (id: string): string => (esTextoFoto(id) ? id.replace(/[_.]/g, '-') : id);

/** El id de un bloque de Inicio a partir del de la capa (`texto-foto-3` → `texto_foto.3`); null si no es un bloque. */
export function idDeDom(dom: string | undefined): string | null {
  if (!dom) {
    return null;
  }
  const tf = /^texto-foto-([1-9][0-9]?)$/.exec(dom);
  const id = tf ? `texto_foto.${tf[1]}` : dom;
  return esIdBloque(id) ? id : null;
}

type FilaBloque = Pick<BloqueWeb, 'clave' | 'texto' | 'imagenUrl' | 'orden' | 'oculto'>;

/**
 * Los bloques de Inicio en el orden en que los pinta el tema (`seccionesDelTema` y `ordenarSecciones` de «La línea
 * comarcal»): presentación, profesores y galería siempre; «Fotos de las sedes» si tiene cabecera; cada «Texto con foto»
 * con alguna fila; y las tres fijas. Contacto no se mueve y no está.
 */
export function ordenCompleto(filas: readonly FilaBloque[]): string[] {
  const bloques = new BloquesWeb(filas as readonly BloqueWeb[]);
  const secciones = [
    { clave: 'presentacion', movible: true },
    { clave: 'disciplinas', movible: false },
    ...(bloques.tiene('sedes') ? [{ clave: 'sedes', movible: true }] : []),
    { clave: 'profesores', movible: true, prefijo: 'profesor' },
    { clave: 'galeria', movible: true },
    ...bloques.numeros('texto_foto').map((n) => ({ clave: `texto_foto.${n}`, movible: true })),
    { clave: 'eventos', movible: false },
    { clave: 'tarifas', movible: false },
  ];
  return ordenarSecciones(secciones, bloques).map((s) => s.clave);
}

/** Si el bloque de ese tipo ya está (boceto `ID_UNICO`): presentación, profesores y galería, siempre (el tema los tiene). */
export function existeTipo(filas: readonly Pick<FilaBloque, 'clave'>[], tipo: TipoBloque): boolean {
  if (tipo === 'texto_foto') {
    return false;
  }
  return tipo === 'sedes' ? filas.some((f) => f.clave === 'sedes') : true;
}

/** Las filas de «Fotos de las sedes» (las suyas en `ClavesWeb`: se ocultan y se quitan con ella). */
const FILAS_SEDES = new Set(['sedes', 'sedes.rotulo', 'sedes.titulo', 'sedes.ver_sede']);

/** Si la fila es del bloque: la cabecera y todas sus filas (`texto_foto.<n>.*`). */
export function esFilaDelBloque(clave: string, id: string): boolean {
  if (id === 'sedes') {
    return FILAS_SEDES.has(clave);
  }
  return clave === id || (esTextoFoto(id) && clave.startsWith(`${id}.`));
}

/**
 * La parte de Inicio donde sale una fila (para «Sin guardar»): el bloque, la portada o la parte fija del texto; null si
 * no es de Inicio.
 */
export function parteDeFila(clave: string): string | null {
  if (esIdBloque(clave)) {
    return clave;
  }
  const tf = bloqueTextoFoto(clave) ?? bloqueDeFoto(clave);
  if (tf) {
    return tf;
  }
  if (FILAS_SEDES.has(clave)) {
    return 'sedes';
  }
  if (/^profesor\.\d+\./.test(clave)) {
    return 'profesores';
  }
  if (/^galeria\.\d+$/.test(clave)) {
    return 'galeria';
  }
  if (clave.startsWith('disciplina.')) {
    return 'disciplinas';
  }
  const def = defTexto(clave);
  if (def) {
    return def.parte;
  }
  const primera = clave.split('.')[0];
  return primera === 'portada' || primera === 'presentacion' || primera === 'profesores' || primera === 'galeria' ? primera : null;
}

/** Boceto `nombreBloque`: «Texto con foto «título»» (recortado a 40) o el nombre del tipo. */
export function nombreBloque(id: string, filas: readonly Pick<FilaBloque, 'clave' | 'texto'>[]): string {
  if (esTextoFoto(id)) {
    const titulo = (filas.find((f) => f.clave === `${id}.titulo`)?.texto ?? '').trim();
    return titulo ? `Texto con foto «${recortar(titulo, 40)}»` : 'Texto con foto';
  }
  return NOMBRE_PARTE[id] ?? id;
}

/** El nombre del tipo (boceto `TIPO_N`), el del botón de la barra. */
export const nombreTipo = (id: string): string => NOMBRE_PARTE[esTextoFoto(id) ? 'texto_foto' : id] ?? id;

/** Si el bloque está oculto (su cabecera). */
export const estaOculto = (filas: readonly Pick<FilaBloque, 'clave' | 'oculto'>[], id: string): boolean =>
  filas.some((f) => f.clave === id && f.oculto === true);

const conTexto = (filas: readonly FilaBloque[], clave: string) => !!(filas.find((f) => f.clave === clave)?.texto ?? '').trim();
const conFoto = (filas: readonly FilaBloque[], clave: string) => !!filas.find((f) => f.clave === clave)?.imagenUrl;

/**
 * Boceto `estaVacio`: no sale en la web porque no tiene nada que pintar. La presentación sin fila de texto lleva el
 * texto del tema (no está vacía). `sedesConFoto`: si alguna sede tiene su foto 1 (dato de Sedes).
 */
export function bloqueVacio(id: string, filas: readonly FilaBloque[], sedesConFoto: boolean): boolean {
  if (id === 'presentacion') {
    const texto = filas.find((f) => f.clave === 'presentacion.texto');
    return !!texto && !(texto.texto ?? '').trim() && !conFoto(filas, 'presentacion.foto');
  }
  if (id === 'profesores') {
    return !filas.some((f) => /^profesor\.\d+\.nombre$/.test(f.clave) && (f.texto ?? '').trim());
  }
  if (id === 'galeria') {
    return !filas.some((f) => /^galeria\.\d+$/.test(f.clave) && !!f.imagenUrl);
  }
  if (id === 'sedes') {
    return !sedesConFoto;
  }
  if (esTextoFoto(id)) {
    return !conTexto(filas, `${id}.titulo`) && !conTexto(filas, `${id}.texto`) && !conFoto(filas, `${id}.foto`);
  }
  return false;
}

/** Una parte de Inicio con barra (bloque o parte fija), para «Sin guardar». */
export function esParteInicio(valor: unknown): boolean {
  return esIdBloque(valor) || (typeof valor === 'string' && valor !== 'texto_foto' && Object.prototype.hasOwnProperty.call(NOMBRE_PARTE, valor));
}

/**
 * A dónde va el foco tras una acción (boceto `pintar(foco)`): un botón de la barra de un bloque (`subir:galeria`), su
 * nombre (`sel:galeria`), el primer texto vacío de un bloque recién añadido (`editar:texto_foto.2`) o el hueco «Añadir bloque» de un puesto (`a:3`).
 */
export function esFocoBloque(valor: unknown): valor is string {
  if (typeof valor !== 'string') {
    return false;
  }
  const hueco = /^a:(\d{1,3})$/.exec(valor);
  if (hueco) {
    return Number(hueco[1]) <= MAX_BLOQUES;
  }
  const m = /^(sel|editar|subir|bajar|vis|quitar):(.+)$/.exec(valor);
  return !!m && esIdBloque(m[2]);
}

/** Textos de la barra, las pastillas y la hoja (boceto `barraHtml`, `estadosHtml`, `pintarHoja`). */
export const TEXTOS_BLOQUES = {
  subir: 'Subir',
  bajar: 'Bajar',
  ocultar: 'Ocultar',
  mostrar: 'Mostrar',
  quitar: 'Quitar',
  sinGuardar: 'Sin guardar',
  ocultoEnLaWeb: 'Oculto en la web',
  vacio: 'Vacío: no sale en la web',
  seVe: 'Se ve en la web',
  anadir: 'Añadir bloque',
  listo: 'Listo',
} as const;

/** Boceto `barraHtml`: «Subir el bloque Galería». */
export const etiquetaAccion = (texto: string, nombre: string) => `${texto} el bloque ${nombre}`;
/** Boceto `hueco`: «Añadir bloque en el puesto 3». */
export const tituloMenuAnadir = (pos: number) => `Añadir bloque en el puesto ${pos + 1}`;
