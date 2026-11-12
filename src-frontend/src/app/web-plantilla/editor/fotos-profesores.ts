/**
 * Profesores del editor «Web · Contenido» (W12f-2b; boceto `boceto-panel-contenido-web-comarcal.html`: `profeHtml`,
 * `profesoresHtml`, `anadirProfe`, `quitarProfe`, `abrirMenuFoto`, `queFoto`, `errores`, `nombreError`). Lógica pura que
 * comparten el panel y la capa de edición.
 *
 * Cada profesor es un número `n` (`ClavesWeb.PROFESOR`, de 1 a 99) con sus filas `profesor.<n>.nombre` (obligatorio,
 * lleva la constancia del permiso), `.cargo`, `.foto` (texto = descripción) y `.sedes` (W12f-3: ids de sede separados por
 * comas, sin espacios ni repetidos; los elige el panel, ver `SedeElegible` y `EstadoProfesor.sedes`).
 * La constancia del permiso (quién y cuándo, d521) solo se ve en el editor: nunca llega al tema.
 */

/** El mayor número de un profesor (`ClavesWeb`: de 1 a 99). */
export const MAX_NUMERO_PROFESOR = 99;

const FOTO_PROFESOR = /^profesor\.([1-9][0-9]?)\.foto$/;
const FILA_PROFESOR = /^profesor\.([1-9][0-9]?)\.(nombre|cargo|foto|sedes)$/;
const PERMISO = /^profesor\.([1-9][0-9]?)\.permiso$/;

/** `profesor.<n>.foto`. */
export function esFotoProfesor(clave: unknown): clave is string {
  return typeof clave === 'string' && FOTO_PROFESOR.test(clave);
}

/** El número del profesor de una fila (`profesor.3.cargo` → 3), o null. */
export function numeroProfesor(clave: string): number | null {
  const m = FILA_PROFESOR.exec(clave);
  return m ? Number(m[1]) : null;
}

/** Un número de profesor válido para la API (de 1 a 99). */
export const esNumeroProfesor = (n: unknown): n is number =>
  typeof n === 'number' && Number.isInteger(n) && n >= 1 && n <= MAX_NUMERO_PROFESOR;

/** La clave del error del permiso (`ContenidoWebService`: `profesor.<n>.permiso`); no es una fila. */
export const clavePermiso = (n: number) => `profesor.${n}.permiso`;

/** El número de una clave de permiso (`profesor.3.permiso` → 3), o null. */
export function numeroPermiso(clave: unknown): number | null {
  const m = typeof clave === 'string' ? PERMISO.exec(clave) : null;
  return m ? Number(m[1]) : null;
}

/** Boceto `profeHtml`: el nombre o, sin él, «Profesor nuevo». */
export const nombreProfesor = (nombre: string | null | undefined) => (nombre ?? '').trim() || 'Profesor nuevo';

/** Boceto `queFoto` de un profesor: «de Antonio Ruiz» o «de el profesor nuevo». */
export const queFotoProfesor = (nombre: string | null | undefined) => `de ${(nombre ?? '').trim() || 'el profesor nuevo'}`;

/** Textos del boceto (`profeHtml`, `profesoresHtml`, `anadirProfe`, `quitarProfe`, `errores`, `abrirMenuFoto`). */
export const TEXTOS_PROFESORES = {
  anadir: 'Añadir profesor',
  anadirPrimero: 'Añade el primer profesor',
  quitar: 'Quitar profesor',
  permiso: 'Tengo su permiso para publicar su nombre y su foto',
  obligatorio: '(obligatorio)',
  permisoAyuda: 'Se guarda quién lo marca y cuándo. No sale en la web.',
  /** `ContenidoWebService.PERMISO_FALTA`. */
  faltaPermiso: 'Marca la casilla si tienes su permiso. Sin él no puede salir en la web.',
  describeSuFoto: 'Describe su foto.',
  quitarFotoPie: 'Sale con sus iniciales',
  anadirFoto: 'Añadir foto',
  vozAnadido: 'Profesor añadido. Escribe su nombre y marca el permiso.',
  vozMarcado: 'Permiso marcado. Se guarda con tu nombre y la fecha.',
  vozDesmarcado: 'Permiso desmarcado.',
  confirmarTexto: 'Deja de salir al guardar. Su ficha en el equipo no cambia.',
  confirmarAccion: 'Quitar profesor',
  // W12f-3 (boceto `profeHtml`, `abrirSedesProfe`, «Listo» de `listo-sedes`).
  elegirSedes: 'Elegir sus sedes',
  cambiarSedes: 'Cambiar sus sedes',
  sinSedes: 'Sin sedes: su tarjeta sale sin la línea.',
  sedesAyuda: 'Salen en su tarjeta, sobre la línea. No cambia en qué grupos da clase.',
  sedesLeyenda: 'Sedes donde da clase',
  listo: 'Listo',
  vozSinSedes: 'Sin sedes.',
} as const;

/** W12f-3 (boceto `abrirSedesProfe`): «Sedes de Antonio Ruiz» o, sin nombre, «Sedes de el profesor nuevo». */
export const tituloSedesProfesor = (nombre: string | null | undefined) => `Sedes de ${(nombre ?? '').trim() || 'el profesor nuevo'}`;
/** W12f-3 (boceto `listo-sedes`): «Sedes: Casariche, Estepa.» o «Sin sedes.». */
export const vozSedesProfesor = (nombres: readonly string[]) =>
  nombres.length ? `Sedes: ${nombres.join(', ')}.` : TEXTOS_PROFESORES.vozSinSedes;

const SEDES = /^profesor\.([1-9][0-9]?)\.sedes$/;

/** W12f-3: el número de una clave de sedes (`profesor.3.sedes` → 3), o null (enlace del resumen de errores). */
export function numeroSedes(clave: unknown): number | null {
  const m = typeof clave === 'string' ? SEDES.exec(clave) : null;
  return m ? Number(m[1]) : null;
}

/** Boceto `profeHtml`: «Quitar a Antonio Ruiz de la web». */
export const etiquetaQuitarProfesor = (nombre: string | null | undefined) => `Quitar a ${nombreProfesor(nombre)} de la web`;
/** Boceto `profeHtml`: el monograma de un profesor sin foto. */
export const etiquetaMonograma = (nombre: string | null | undefined) => `Sin foto: iniciales de ${nombreProfesor(nombre)}`;
/** Boceto `quitarProfe`: «¿Quitar a Antonio Ruiz de la web?» (sin nombre, «el profesor nuevo»). */
export const tituloQuitarProfesor = (nombre: string | null | undefined) =>
  `¿Quitar a ${(nombre ?? '').trim() || 'el profesor nuevo'} de la web?`;
/** Boceto `quitarProfe`: «Antonio Ruiz: quitado. Se quita de la web al guardar.». */
export const vozQuitadoProfesor = (nombre: string | null | undefined) =>
  `${(nombre ?? '').trim() || 'el profesor nuevo'}: quitado. Se quita de la web al guardar.`;

/**
 * Lo que la capa pinta del permiso de un profesor: la casilla obligatoria (profesor nuevo o con el nombre cambiado) o la
 * constancia guardada, ya en texto («Permiso marcado por María Ruiz el 03/11/2026»). Solo viaja a la capa del editor.
 */
export type PermisoProfesor = { tipo: 'casilla'; marcada: boolean } | { tipo: 'constancia'; texto: string };

/** Un profesor del borrador para la capa (mensaje `profesores`). */
export interface EstadoProfesor {
  n: number;
  /** El nombre del borrador (para las etiquetas: «Quitar a … de la web»). */
  nombre: string;
  permiso: PermisoProfesor;
  /**
   * W12f-3: las sedes marcadas en el borrador, solo las que están en `MensajeProfesores.sedes` y en el orden de esa lista
   * (las sedes inactivas o desconocidas de la fila guardada no viajan, pero el panel las conserva si no se tocan).
   */
  sedes: number[];
}

/**
 * W12f-3: una sede que se puede elegir para un profesor: las activas de la escuela que da la vista previa de gestión
 * (`GET /api/publico/web?vistaPrevia=true`), en el orden de la API. El panel la manda a la capa en `profesores`.
 */
export interface SedeElegible {
  id: number;
  nombre: string;
}

/**
 * W12f-3: tope de sedes en un mensaje del puente. `ClavesWeb`: `profesor.<n>.sedes` tiene como mucho 300 caracteres, así
 * que no caben más de 150 ids («1,2,…»).
 */
export const MAX_SEDES_PROFESOR = 150;

/** W12f-3: un id de sede: entero positivo seguro. */
export const esIdSede = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v > 0;
