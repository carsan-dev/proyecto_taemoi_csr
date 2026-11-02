import { HttpErrorResponse } from '@angular/common/http';

import { coloresGrado } from '../../features/catalogo/catalogo-reglas';
import { CatalogoGrado } from '../../features/catalogo/catalogo.models';
import { MAX_BYTES_SUBIDA, MOTIVO_LECTURA, OpcionesSubida, SUBIDA_DOCUMENTO, mensajeErrorSubida } from '../../utils/subida-archivos.util';
import { plural } from '../formato/formato-es';
import { DisciplinaConBloques, GradoDelBloque, VideoDetalle } from './material-examen.models';

const KB = 1024;
const MB = KB * 1024;
const GB = MB * 1024;

/** Los topes de la API (`MaterialExamenGestionService`). */
export const MAX_BYTES_VIDEO = 500 * MB;
const EXTENSIONES_VIDEO: readonly string[] = ['mp4', 'webm', 'm4v', 'mov'];
export const ACEPTA_VIDEOS = '.mp4,.webm,.m4v,.mov,video/mp4,video/webm,video/x-m4v,video/quicktime';
export const ACEPTA_PDF = '.pdf,application/pdf';
export const ACEPTA_DOCUMENTOS = Object.keys(SUBIDA_DOCUMENTO.formatos)
  .map((extension) => `.${extension}`)
  .join(',');

/** El temario es siempre un PDF: la API guarda `temario.pdf`. */
export const SUBIDA_TEMARIO: OpcionesSubida = {
  formatos: { pdf: ['application/pdf'] },
  comprobarMime: true,
  maxBytes: MAX_BYTES_SUBIDA,
  motivoFormato: 'El temario debe ser un PDF.',
  motivoTamano: SUBIDA_DOCUMENTO.motivoTamano,
};

function decimal(valor: number): string {
  return valor.toFixed(1).replace(/\.0$/, '').replace('.', ',');
}

/** «248 KB», «1,2 MB», «466 MB» o «4,1 GB». */
export function peso(bytes: number): string {
  if (bytes >= GB) {
    return `${decimal(bytes / GB)} GB`;
  }
  if (bytes >= MB) {
    const megas = bytes / MB;
    return `${megas < 10 ? decimal(megas) : Math.round(megas)} MB`;
  }
  return bytes >= KB ? `${Math.round(bytes / KB)} KB` : '0 MB';
}

/** «N vídeos» o «Sin vídeos». */
export function textoVideos(videos: number): string {
  return videos === 0 ? 'Sin vídeos' : plural(videos, 'vídeo', 'vídeos');
}

/** «11 bloques · 40 vídeos · 4,1 GB». */
export function resumenDisciplina(disciplina: DisciplinaConBloques): string {
  const { videos, bytes } = disciplina.totales;
  return `${plural(disciplina.bloques.length, 'bloque', 'bloques')} · ${plural(videos, 'vídeo', 'vídeos')} · ${peso(bytes)}`;
}

export function porcentaje(cargados: number, total: number): number {
  return total > 0 ? Math.min(Math.round((cargados / total) * 100), 100) : 0;
}

/** «88 de 142 MB»: los dos en la unidad del total. */
function deTotal(cargados: number, total: number): string {
  const [divisor, unidad] = total >= MB ? [MB, 'MB'] : [KB, 'KB'];
  return `${Math.round(cargados / divisor)} de ${Math.round(total / divisor)} ${unidad}`;
}

/** La línea bajo la barra: la del boceto de escritorio o, en móvil, su versión corta. */
export function textoProgreso(cargados: number, total: number, movil: boolean): string {
  const base = `${porcentaje(cargados, total)} % · ${deTotal(cargados, total)}`;
  return movil ? `${base}. No cierres esta pantalla.` : `${base} · No cierres esta pantalla hasta que termine.`;
}

/** El catálogo guarda los grados en mayúsculas: «ROJO-NEGRO 1º PUM» → «Rojo-negro 1º PUM». */
function nombreGrado(nombre: string): string {
  const minusculas = nombre.trim().toLocaleLowerCase('es-ES').replace(/\b(pum|dan)\b/g, (s) => s.toUpperCase());
  return minusculas.charAt(0).toLocaleUpperCase('es-ES') + minusculas.slice(1);
}

function tramoGrados(grados: readonly GradoDelBloque[]): string {
  if (grados.length === 0) {
    return '';
  }
  const primero = nombreGrado(grados[0].nombre);
  return grados.length === 1 ? primero : `${primero} a ${nombreGrado(grados[grados.length - 1].nombre)}`;
}

/**
 * «Alumnos de Blanco a Blanco-amarillo»; con un solo grado, «Alumnos de Rojo». En los bloques de negros de Taekwondo y
 * Kickboxing (`bNN_negro_N_a_negro_M`) el PUM y el DAN del mismo número comparten temario: el subtítulo va de DAN a DAN.
 * El bloque de rojo a negro (`bNN_rojo_a_negro_N`) va del rojo (marrón en kickboxing, según el catálogo) a Negro 1º DAN.
 */
export function textoGrados(
  grados: readonly GradoDelBloque[],
  bloqueId = '',
  buscar?: (codigo: string) => Pick<CatalogoGrado, 'nombre'> | undefined
): string {
  const dan = (n: string): string => {
    const nombre = buscar?.(`NEGRO_${n}_DAN`)?.nombre;
    return nombre && nombre.includes(n) ? nombreGrado(nombre) : `Negro ${n}º DAN`;
  };
  const rojoANegro = /^b\d+_rojo_a_negro_(\d+)$/.exec(bloqueId);
  if (rojoANegro) {
    const rojo = buscar?.('ROJO')?.nombre ?? grados[0]?.nombre;
    return `Alumnos de ${rojo ? nombreGrado(rojo) : 'Rojo'} a ${dan(rojoANegro[1])}`;
  }
  const negros = /^b\d+_negro_(\d+)_a_negro_(\d+)$/.exec(bloqueId);
  if (negros) {
    return `Alumnos de ${dan(negros[1])} a ${dan(negros[2])}`;
  }
  return grados.length ? `Alumnos de ${tramoGrados(grados)}` : '';
}

/** «Lo ven las familias de alumnos de blanco a blanco-amarillo.» */
export function fraseGrados(grados: readonly GradoDelBloque[]): string {
  if (!grados.length) {
    return '';
  }
  const tramo = tramoGrados(grados).replace(/(^| a )(\p{Lu})(?=\p{Ll})/gu, (_, antes: string, letra: string) => antes + letra.toLocaleLowerCase('es-ES'));
  return `Lo ven las familias de alumnos de ${tramo}.`;
}

/** Los dos cintos de la fila: del color con el que se entra en el bloque al color con el que se sale. */
export function coloresBloque(
  grados: readonly GradoDelBloque[],
  buscar: (codigo: string) => Pick<CatalogoGrado, 'color1' | 'color2'> | undefined,
  bloqueId = '',
  disciplina = ''
): [string, string] {
  // Los grados de la API son a quién llega el bloque, no su transición: los PUM también están en bloques de cinturón negro.
  const tramo = /^b\d+_(inicio|blanco|amarillo|naranja|verde|azul|rojo|negro_\d+)_a_(amarillo|naranja|verde|azul|rojo|negro_\d+|maximo)$/.exec(bloqueId);
  if (tramo && (disciplina === 'TAEKWONDO' || disciplina === 'KICKBOXING')) {
    const color = (extremo: string): string => {
      const codigo = extremo === 'inicio' ? 'BLANCO' : extremo.toUpperCase();
      const grado = codigo.startsWith('NEGRO_')
        ? buscar(codigo + '_DAN') ?? buscar('NEGRO_1_DAN')
        : buscar(codigo);
      return coloresGrado(grado).colors[0];
    };
    const origen = color(tramo[1]);
    return [origen, tramo[2] === 'maximo' ? origen : color(tramo[2])];
  }
  const primero = coloresGrado(grados.length ? buscar(grados[0].codigo) : null).colors;
  const ultimo = coloresGrado(grados.length ? buscar(grados[grados.length - 1].codigo) : null).colors;
  return [primero[0], ultimo[ultimo.length - 1]];
}

export function extensionDe(nombre: string): string {
  const punto = nombre.lastIndexOf('.');
  return punto > 0 && punto < nombre.length - 1 ? nombre.slice(punto + 1).toLowerCase() : '';
}

/** «MP4» en «MP4 · 58 MB». */
export function tipoFichero(nombre: string): string {
  return extensionDe(nombre).toUpperCase();
}

/**
 * Lo que la API rechazaría, comprobado antes de enviar: corta con 413 o 415 sin leer el cuerpo y el navegador lo vería
 * como un error de red. Al sustituir, el nombre guardado no cambia y la API mira la cabecera según su extensión: un WEBM
 * solo por otro WEBM, y MP4, M4V y MOV entre sí.
 */
export function motivoVideo(archivo: File, sustituye?: string | null): string | null {
  const extension = extensionDe(archivo.name);
  if (!EXTENSIONES_VIDEO.includes(extension)) {
    return 'Solo se admiten vídeos mp4, webm, m4v o mov.';
  }
  if (sustituye && (extensionDe(sustituye) === 'webm') !== (extension === 'webm')) {
    return `Este vídeo solo se puede sustituir por otro ${extensionDe(sustituye) === 'webm' ? 'WEBM' : 'MP4, M4V o MOV'}.`;
  }
  if (archivo.size > MAX_BYTES_VIDEO) {
    return `Pesa ${peso(archivo.size)}. El máximo es 500 MB por vídeo.`;
  }
  return archivo.size === 0 ? 'El fichero está vacío.' : null;
}

/** La API valida el tipo con la extensión del documento guardado, que no cambia al sustituirlo. */
export function motivoDocumentoSustituto(guardado: string, nuevo: string): string | null {
  const formatos = SUBIDA_DOCUMENTO.formatos;
  const actual = extensionDe(guardado);
  return formatos[actual] === formatos[extensionDe(nuevo)] ? null : `Este documento solo se puede sustituir por otro ${actual.toUpperCase()}.`;
}

export interface FalloSubida {
  motivo: string;
  reintentable: boolean;
}

/**
 * Por qué falló un envío. Estado 0: se cortó la conexión; si no había salido ni un byte, puede ser Chrome en Android, que
 * lee el `content://` al enviar (por eso no se copia el vídeo a memoria: pesaría hasta 500 MB). Con respuesta, su mensaje.
 */
export function motivoFalloSubida(error: unknown, cargados: number): FalloSubida {
  const estado = error instanceof HttpErrorResponse ? error.status : 0;
  if (estado === 0) {
    return { motivo: cargados > 0 ? `Se cortó la conexión a los ${peso(cargados)}. Vuelve a subirlo.` : MOTIVO_LECTURA, reintentable: true };
  }
  return {
    motivo: mensajeErrorSubida(error, 'No se pudo subir el vídeo.'),
    reintentable: estado === 401 || estado === 408 || estado === 429 || estado >= 500,
  };
}

export function mismoOrden(a: readonly VideoDetalle[], b: readonly VideoDetalle[]): boolean {
  return a.length === b.length && a.every((video, i) => video.fichero === b[i].fichero);
}

/** Con un orden sin guardar: lo que llega del servidor, en el orden local y lo nuevo al final. */
export function fusionarOrden(locales: readonly VideoDetalle[], servidor: readonly VideoDetalle[]): VideoDetalle[] {
  const porFichero = new Map(servidor.map((video) => [video.fichero, video]));
  const conservados = locales.flatMap((video) => porFichero.get(video.fichero) ?? []);
  const yaEstan = new Set(conservados.map((video) => video.fichero));
  return [...conservados, ...servidor.filter((video) => !yaEstan.has(video.fichero))];
}
