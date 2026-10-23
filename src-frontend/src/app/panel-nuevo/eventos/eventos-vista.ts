import { ValidationErrors } from '@angular/forms';

import { numero, plural } from '../formato/formato-es';

/**
 * Lo puro de Eventos del panel nuevo (8b-3f): estado y textos de la fecha, URL de la imagen, orden, nombres de descarga
 * y los cuerpos que se envían. Copia de lo que hacen hoy `ListadoEventosComponent`, `CrearEventoComponent` y
 * `EditarEventoComponent`, que no se tocan.
 */

export type EstadoFecha = 'proximo' | 'hoy' | 'realizado' | 'sin-fecha';

/** Un evento tal como llega de la API (`/eventos/admin/todos` y `/eventos/{id}`). */
export interface EventoPanel {
  id: number;
  titulo: string;
  descripcion: string;
  fechaEvento?: string | number[] | null;
  visible?: boolean;
  orden?: number | null;
  fotoEvento?: { id?: number; nombre?: string; url?: string | null; tipo?: string; ruta?: string | null } | null;
  documentos?: DocumentoEvento[];
}

export interface DocumentoEvento {
  id: number;
  nombre: string;
  tipo: string;
  url?: string;
  ruta?: string;
}

/** La imagen de hoy cuando el evento no tiene foto (relativa a la base de la aplicación). */
export const IMAGEN_POR_DEFECTO = 'assets/media/default.webp';

/** Ancho de la miniatura del listado y de la imagen del formulario, los de hoy. */
export const ANCHO_MINIATURA = 480;
export const ANCHO_FORMULARIO = 920;

const formatoCorto = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
const formatoLargo = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

/** `parsearFechaEvento` de hoy: «AAAA-MM-DD» o `[año, mes, día]`; nula si no es una fecha válida. */
export function parsearFecha(valor: unknown): Date | null {
  let anio: number;
  let mes: number;
  let dia: number;
  if (typeof valor === 'string' && valor.trim()) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
    if (!m) {
      return null;
    }
    [anio, mes, dia] = [Number(m[1]), Number(m[2]), Number(m[3])];
  } else if (Array.isArray(valor) && valor.length >= 3) {
    [anio, mes, dia] = [Number(valor[0]), Number(valor[1]), Number(valor[2])];
  } else {
    return null;
  }
  if ([anio, mes, dia].some(Number.isNaN)) {
    return null;
  }
  const f = new Date(anio, mes - 1, dia);
  if (f.getFullYear() !== anio || f.getMonth() !== mes - 1 || f.getDate() !== dia) {
    return null;
  }
  return f;
}

function sinHora(f: Date): number {
  return new Date(f.getFullYear(), f.getMonth(), f.getDate()).getTime();
}

export function estadoFecha(valor: unknown, hoy: Date): EstadoFecha {
  const f = parsearFecha(valor);
  if (!f) {
    return 'sin-fecha';
  }
  const diferencia = sinHora(f) - sinHora(hoy);
  if (diferencia === 0) {
    return 'hoy';
  }
  return diferencia > 0 ? 'proximo' : 'realizado';
}

export function etiquetaEstado(estado: EstadoFecha): string {
  switch (estado) {
    case 'proximo':
      return 'Próximo';
    case 'hoy':
      return 'Hoy';
    case 'realizado':
      return 'Realizado';
    default:
      return 'Sin fecha';
  }
}

/** «14 nov 2026», como la etiqueta de hoy. */
export function fechaCorta(valor: unknown): string | null {
  const f = parsearFecha(valor);
  return f ? formatoCorto.format(f) : null;
}

/** «Próximo: Sábado, 14 de noviembre de 2026», el título (tooltip) de hoy. */
export function fechaTitulo(valor: unknown, hoy: Date): string | null {
  const f = parsearFecha(valor);
  if (!f) {
    return null;
  }
  const larga = formatoLargo.format(f);
  return `${etiquetaEstado(estadoFecha(valor, hoy))}: ${larga.charAt(0).toUpperCase()}${larga.slice(1)}`;
}

/** `normalizarFechaEventoInput` de hoy: el valor del campo de fecha. */
export function fechaParaCampo(valor: unknown): string {
  if (typeof valor === 'string') {
    return valor;
  }
  if (Array.isArray(valor) && valor.length >= 3) {
    const [a, m, d] = [Number(valor[0]), Number(valor[1]), Number(valor[2])];
    if (![a, m, d].some(Number.isNaN)) {
      return `${String(a).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }
  }
  return '';
}

/** `actualizarParametroUrl` de hoy. */
export function conParametro(url: string, clave: string, valor: string): string {
  const seguro = encodeURIComponent(valor);
  const regex = new RegExp(`([?&])${clave}=[^&]*`);
  if (regex.test(url)) {
    return url.replace(regex, `$1${clave}=${seguro}`);
  }
  return `${url}${url.includes('?') ? '&' : '?'}${clave}=${seguro}`;
}

/**
 * La URL de la imagen de hoy: con `ancho`, la de la miniatura (480) o la del formulario (920), y la imagen por defecto
 * si no hay foto; sin `ancho`, la ampliada (sin cambiar «w»), o nula si no hay foto.
 */
export function urlImagen(evento: Pick<EventoPanel, 'fotoEvento'> | null | undefined, ancho: number | null): string | null {
  const url = evento?.fotoEvento?.url;
  if (!url) {
    return ancho === null ? null : IMAGEN_POR_DEFECTO;
  }
  const version = String(evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0');
  const conAncho = ancho === null ? url : conParametro(url, 'w', String(ancho));
  return conParametro(conAncho, 'v', version);
}

/** Una lista nueva con el elemento de `desde` en `hasta`; fuera de rango, la misma lista. */
export function moverEn<T>(lista: readonly T[], desde: number, hasta: number): T[] {
  if (desde < 0 || desde >= lista.length || hasta < 0 || hasta >= lista.length || desde === hasta) {
    return lista as T[];
  }
  const copia = [...lista];
  const [movido] = copia.splice(desde, 1);
  copia.splice(hasta, 0, movido);
  return copia;
}

const EXTENSION_POR_MIME: Readonly<Record<string, string>> = {
  'application/pdf': 'pdf',
  'text/csv': 'csv',
  'text/plain': 'txt',
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
};

/** `obtenerNombreDescarga` de hoy: el nombre guardado; sin extensión, la de su tipo; sin nombre, «documento». */
export function nombreDescarga(nombre: string | null | undefined, tipo: string | null | undefined): string {
  const base = (nombre ?? '').trim() || 'documento';
  if (base.includes('.')) {
    return base;
  }
  const extension = EXTENSION_POR_MIME[(tipo ?? '').split(';')[0].trim().toLowerCase()];
  return extension ? `${base}.${extension}` : base;
}

/** «17 bytes», «1,5 KB», «5 MB». */
export function tamanoArchivo(bytes: number): string {
  if (!bytes) {
    return '0 bytes';
  }
  const unidades = ['bytes', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), unidades.length - 1);
  const valor = Number.parseFloat((bytes / Math.pow(1024, i)).toFixed(2));
  return `${valor.toLocaleString('es-ES', { maximumFractionDigits: 2 })} ${unidades[i]}`;
}

export interface ValoresEvento {
  titulo: string;
  descripcion: string;
  fechaEvento: string;
}

/** El JSON de «nuevo» de hoy (`CrearEventoComponent.onSubmit`): el valor del formulario y la fecha vacía como nula. */
export function cuerpoCrear(v: ValoresEvento & { visible: boolean }) {
  return { titulo: v.titulo, descripcion: v.descripcion, fechaEvento: v.fechaEvento || null, visible: v.visible };
}

/** El JSON de «eventoEditado» de hoy (`EditarEventoComponent.actualizarEvento`), con su `fotoEvento: null`. */
export function cuerpoEditar(v: ValoresEvento) {
  return { titulo: v.titulo, descripcion: v.descripcion, fechaEvento: v.fechaEvento || null, fotoEvento: null };
}

export type CampoConError = 'titulo' | 'descripcion';

/** Los mensajes de hoy de cada campo. */
export function mensajesError(campo: CampoConError, errores: ValidationErrors | null): string[] {
  if (!errores) {
    return [];
  }
  const mensajes: string[] = [];
  if (errores['required']) {
    mensajes.push(campo === 'titulo' ? 'El título es obligatorio' : 'La descripción es obligatoria');
  }
  if (errores['maxlength']) {
    mensajes.push(campo === 'titulo' ? 'Máximo 100 caracteres' : 'Máximo 500 caracteres');
  }
  return mensajes;
}

/** «5 eventos, 4 visibles en la web». */
export function resumenEventos(total: number, visibles: number): string {
  if (total === 0) {
    return 'Ningún evento';
  }
  const parteVisibles = visibles === 0 ? 'ninguno visible' : `${numero(visibles)} ${visibles === 1 ? 'visible' : 'visibles'}`;
  return `${plural(total, 'evento', 'eventos')}, ${parteVisibles} en la web`;
}
