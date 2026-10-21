import { HttpErrorResponse } from '@angular/common/http';

/**
 * Preparación común de los archivos que se suben (documentos, fotos de alumno, imágenes de evento, logos).
 *
 * Chrome en Android lee los content:// de la galería o de Drive al ENVIAR la petición y ahí falla
 * (NotReadableError / estado 0). Por eso el archivo se copia en memoria al elegirlo: si no se puede leer,
 * se avisa en ese momento y lo que se envía es la copia, ya legible.
 */

export const MOTIVO_LECTURA =
  'No se pudo leer el archivo. Si está en Drive o en la galería, guárdalo primero en el teléfono y vuelve a elegirlo.';

/** Decisión 227 del propietario. */
export const MOTIVO_HEIC = 'Convierte la foto a JPG o desactiva HEIF en la cámara.';

/** spring.servlet.multipart.max-file-size=50MB y client_max_body_size 50M (nginx). */
export const MAX_BYTES_SUBIDA = 50 * 1024 * 1024;

export interface OpcionesSubida {
  /** Extensión (sin punto, en minúsculas) → tipos MIME que el backend acepta para ella. */
  formatos: Readonly<Record<string, readonly string[]>>;
  /**
   * Exigir que el MIME declarado case con la extensión. Solo en documentos: el backend lo comprueba ahí
   * (DocumentoSecurityUtils); las imágenes y los logos los valida por el contenido.
   */
  comprobarMime: boolean;
  maxBytes: number;
  motivoFormato: string;
  motivoTamano: string;
}

export type ResultadoPreparacion = { ok: true; file: File } | { ok: false; motivo: string };

const JPEG = ['image/jpeg', 'image/pjpeg'];
const ZIP = ['application/zip', 'application/x-zip-compressed'];

/** Misma lista que DocumentoSecurityUtils (backend). */
export const SUBIDA_DOCUMENTO: OpcionesSubida = {
  formatos: {
    pdf: ['application/pdf'],
    txt: ['text/plain'],
    csv: ['text/csv', 'application/csv', 'application/vnd.ms-excel'],
    jpg: JPEG,
    jpeg: JPEG,
    png: ['image/png'],
    webp: ['image/webp'],
    gif: ['image/gif'],
    doc: ['application/msword', 'application/vnd.ms-word', 'application/x-msword'],
    docx: ['application/vnd.openxmlformats-officedocument.wordprocessingml.document', ...ZIP],
    xls: ['application/vnd.ms-excel', 'application/xls', 'application/x-excel'],
    xlsx: ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', ...ZIP],
  },
  comprobarMime: true,
  maxBytes: MAX_BYTES_SUBIDA,
  motivoFormato: 'Este tipo de archivo no se admite. Sube un PDF, una imagen (JPG, PNG, WebP o GIF), un texto (TXT o CSV), un Word o un Excel.',
  motivoTamano: 'El archivo pesa más de 50 MB. Elige uno más ligero.',
};

/** Foto de alumno e imagen de evento: las que el backend decodifica con ImageIO (con el plugin WebP). */
export const SUBIDA_IMAGEN: OpcionesSubida = {
  formatos: {
    jpg: JPEG,
    jpeg: JPEG,
    png: ['image/png'],
    webp: ['image/webp'],
    gif: ['image/gif'],
  },
  comprobarMime: false,
  maxBytes: MAX_BYTES_SUBIDA,
  motivoFormato: 'Esta imagen no se admite. Usa una foto JPG, PNG, WebP o GIF.',
  motivoTamano: 'La imagen pesa más de 50 MB. Elige una más ligera.',
};

/** Logo de escuela: LogoEscuelaProcesador admite PNG, JPEG y SVG de hasta 1 MB. */
export const SUBIDA_LOGO: OpcionesSubida = {
  formatos: {
    png: ['image/png'],
    jpg: JPEG,
    jpeg: JPEG,
    svg: ['image/svg+xml'],
  },
  comprobarMime: false,
  maxBytes: 1024 * 1024,
  motivoFormato: 'El logo debe ser una imagen PNG, JPG o SVG.',
  motivoTamano: 'El archivo pesa más de 1 MB. Elige una imagen más ligera.',
};

const EXTENSION_POR_MIME: Readonly<Record<string, string>> = {
  'application/pdf': 'pdf',
  'text/plain': 'txt',
  'text/csv': 'csv',
  'image/jpeg': 'jpg',
  'image/pjpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/x-png': 'png',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'image/svg+xml': 'svg',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
};

const EXTENSIONES_HEIC = new Set(['heic', 'heif']);
const MIMES_HEIC = new Set(['image/heic', 'image/heif', 'image/heic-sequence', 'image/heif-sequence']);
const MARCAS_HEIC = new Set(['heic', 'heix', 'hevc', 'heim', 'heis', 'mif1', 'msf1']);

function extension(nombre: string): string {
  const punto = nombre.lastIndexOf('.');
  return punto > 0 && punto < nombre.length - 1 ? nombre.slice(punto + 1).toLowerCase() : '';
}

/** Quita los puntos finales («foto.» → «foto») y da base a un nombre que solo es extensión («.jpg» → «archivo.jpg»). */
function normalizarNombre(nombre: string): string {
  const limpio = (nombre ?? '').trim().replace(/\.+$/, '');
  if (!limpio) {
    return 'archivo';
  }
  return /^\.[^.]+$/.test(limpio) ? `archivo${limpio}` : limpio;
}

function normalizarMime(tipo: string): string {
  return (tipo ?? '').split(';')[0].trim().toLowerCase();
}

function esCabeceraHeic(contenido: ArrayBuffer): boolean {
  if (contenido.byteLength < 12) {
    return false;
  }
  const texto = String.fromCharCode(...new Uint8Array(contenido, 4, 8));
  return texto.startsWith('ftyp') && MARCAS_HEIC.has(texto.slice(4).toLowerCase());
}

/** Formato por la firma de los primeros bytes; solo los inequívocos (nunca docx/xlsx/zip). */
function deducirPorFirma(contenido: ArrayBuffer): { extension: string; tipo: string } | null {
  const b = new Uint8Array(contenido, 0, Math.min(contenido.byteLength, 12));
  const texto = (desde: number, hasta: number) => String.fromCharCode(...b.slice(desde, hasta));
  if (b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) {
    return { extension: 'jpg', tipo: 'image/jpeg' };
  }
  if (b[0] === 0x89 && texto(1, 4) === 'PNG') {
    return { extension: 'png', tipo: 'image/png' };
  }
  if (texto(0, 4) === '%PDF') {
    return { extension: 'pdf', tipo: 'application/pdf' };
  }
  if (texto(0, 4) === 'GIF8') {
    return { extension: 'gif', tipo: 'image/gif' };
  }
  if (texto(0, 4) === 'RIFF' && texto(8, 12) === 'WEBP') {
    return { extension: 'webp', tipo: 'image/webp' };
  }
  return null;
}

function formatoPermitido(nombre: string, mime: string, opciones: OpcionesSubida): boolean {
  const permitidos = opciones.formatos[extension(nombre)];
  if (!permitidos) {
    return false;
  }
  return !opciones.comprobarMime || !mime || mime === 'application/octet-stream' || permitidos.includes(mime);
}

/**
 * Valida el archivo elegido y devuelve una copia en memoria lista para enviar, o el motivo para el usuario.
 */
export async function prepararFicheroParaSubida(file: File, opciones: OpcionesSubida): Promise<ResultadoPreparacion> {
  const mime = normalizarMime(file.type);
  let nombre = normalizarNombre(file.name);
  let tipo = file.type;

  if (EXTENSIONES_HEIC.has(extension(nombre)) || MIMES_HEIC.has(mime)) {
    return { ok: false, motivo: MOTIVO_HEIC };
  }

  // La galería de Android puede entregar el archivo sin extensión; el backend la exige.
  if (!extension(nombre) && EXTENSION_POR_MIME[mime]) {
    nombre = `${nombre}.${EXTENSION_POR_MIME[mime]}`;
  }

  // Sin extensión ni MIME útil solo quedan los bytes: el formato se deduce tras leerlos.
  const porFirma = !extension(nombre) && (!mime || mime === 'application/octet-stream');
  if (!porFirma && !formatoPermitido(nombre, mime, opciones)) {
    return { ok: false, motivo: opciones.motivoFormato };
  }

  if (file.size > opciones.maxBytes) {
    return { ok: false, motivo: opciones.motivoTamano };
  }

  let contenido: ArrayBuffer;
  try {
    contenido = await file.arrayBuffer();
  } catch {
    return { ok: false, motivo: MOTIVO_LECTURA };
  }
  if (contenido.byteLength === 0) {
    return { ok: false, motivo: MOTIVO_LECTURA };
  }
  if (contenido.byteLength > opciones.maxBytes) {
    return { ok: false, motivo: opciones.motivoTamano };
  }
  if (esCabeceraHeic(contenido)) {
    return { ok: false, motivo: MOTIVO_HEIC };
  }

  if (porFirma) {
    const deducido = deducirPorFirma(contenido);
    if (!deducido) {
      return { ok: false, motivo: opciones.motivoFormato };
    }
    nombre = `${nombre}.${deducido.extension}`;
    tipo = deducido.tipo;
    if (!formatoPermitido(nombre, deducido.tipo, opciones)) {
      return { ok: false, motivo: opciones.motivoFormato };
    }
  }

  try {
    return { ok: true, file: new File([contenido], nombre, { type: tipo, lastModified: file.lastModified }) };
  } catch {
    return { ok: false, motivo: MOTIVO_LECTURA };
  }
}

/** Vacía el selector para que elegir el mismo archivo otra vez vuelva a disparar «change». */
export function reiniciarSelectorArchivo(entrada: HTMLInputElement | null | undefined): void {
  if (entrada) {
    entrada.value = '';
  }
}

/** El «mensaje» que manda el backend (p. ej. en un 400) o, si no hay, el texto por defecto. */
export function mensajeErrorSubida(error: unknown, porDefecto: string): string {
  const mensaje = (error as HttpErrorResponse)?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : porDefecto;
}
