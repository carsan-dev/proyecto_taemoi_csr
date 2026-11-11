import { InjectionToken } from '@angular/core';

import { TemaWeb } from '../../../web-plantilla/datos/modelos';
import type { AcentoEditor, BloqueBorrador, DestinoPanel } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela, nombreTema, temaInicial, temaPublicado } from '../tema/web-tema-vista';

/**
 * Lógica pura de «Web · Contenido» (W12c; boceto `docs/multi-escuela/fase4-web/boceto-panel-contenido-web-comarcal.html`,
 * plan `fase4-w12-plan.md`): estado de la web, textos, ancho y escala de la vista previa, dirección del iframe en modo
 * editor y destino de cada candado. Textos literales del boceto.
 */

/** Una fila de `GET /api/escuela/web/contenido` (W12a, `ContenidoWebDTO.Bloque`). */
export interface BloqueContenido {
  clave: string;
  texto: string | null;
  imagenId: number | null;
  imagenUrl: string | null;
  orden: number;
  oculto: boolean;
  /** Constancia del permiso de un profesor (d521): solo para el editor; no viaja al iframe. */
  permiso: { origen: 'PANEL' | 'ALTA'; por: string | null; en: string } | null;
}

/** `GET /api/escuela/web/contenido`. */
export interface ContenidoWeb {
  version: number;
  bloques: BloqueContenido[];
}

/** El borrador que viaja al iframe: lo que pinta el tema, sin ids de imagen ni constancias de permiso. */
export function bloquesParaVistaPrevia(bloques: readonly BloqueContenido[]): BloqueBorrador[] {
  return bloques.map(({ clave, texto, imagenUrl, orden, oculto }) => ({ clave, texto, imagenUrl, orden, oculto }));
}

/** El tema con el que se ve la vista previa: el publicado o, sin publicar, el primero construido (como «Web · Tema»). */
export function temaDeLaVista(estado: EstadoWebEscuela): TemaWeb {
  return temaInicial(temaPublicado(estado));
}

/** «Publicada con «La línea comarcal».» (sin publicar, el texto lo pone la plantilla con su enlace a Tema). */
export function nombrePublicada(estado: EstadoWebEscuela): string | null {
  const tema = temaPublicado(estado);
  return tema ? `«${nombreTema(tema)}»` : null;
}

/**
 * Lo que va delante de la ruta de la plantilla en el `src` del iframe: vacío (mismo origen). Las pruebas lo cambian
 * para que el iframe no cargue la página de Karma.
 */
export const ORIGEN_EDITOR = new InjectionToken<string>('ORIGEN_EDITOR', { factory: () => '' });

/** `/?vista-previa=<tema>&editor=1`: la plantilla activa la capa de edición solo si la API confirma la gestión. */
export function direccionEditor(tema: TemaWeb): string {
  return `/?vista-previa=${encodeURIComponent(tema)}&editor=1`;
}

// ------------------------------------------------------------------ Móvil | Escritorio

export type AnchoVista = 'movil' | 'escritorio';

/** Ancho del iframe en cada vista (boceto: 390 y 1200 px). */
export const ANCHO_VISTA: Readonly<Record<AnchoVista, number>> = { movil: 390, escritorio: 1200 };

/** Alto del iframe hasta que la capa de edición manda el de la página. */
export const ALTO_INICIAL = 800;

/** Escala para que el ancho quepa: nunca mayor que 1. */
export function escalaVista(disponible: number, ancho: number): number {
  return disponible > 0 ? Math.min(1, disponible / ancho) : 1;
}

/** «Escritorio, 1200 px · al 62 %» (sin el porcentaje si cabe entera). */
export function textoMarco(vista: AnchoVista, escala: number): string {
  const ancho = ANCHO_VISTA[vista];
  return `${vista === 'movil' ? 'Móvil' : 'Escritorio'}, ${ancho} px${escala < 0.995 ? ` · al ${Math.round(escala * 100)} %` : ''}`;
}

/** Escritorio si la ventana mide al menos 1024 px; si no, móvil (como «Web · Tema»). */
export function vistaInicial(ventana: Window | null): AnchoVista {
  return ventana && typeof ventana.matchMedia === 'function' && ventana.matchMedia('(min-width: 1024px)').matches
    ? 'escritorio'
    : 'movil';
}

// ------------------------------------------------------------------ candados

/** Ruta del panel de cada destino de un candado. */
export const RUTA_DESTINO: Readonly<Record<DestinoPanel, string>> = {
  sedes: '/sedes',
  grupos: '/gruposListar',
  eventos: '/eventosListar',
  productos: '/productosListar',
  'datos-escuela': '/datos-escuela',
};

const HEX = /^#[0-9a-f]{6}$/i;

/** El acento del `.pn` de la pantalla para la capa de edición; null si alguno no es un hex de 6 cifras. */
export function acentoDe(estilo: Pick<CSSStyleDeclaration, 'getPropertyValue'>): AcentoEditor | null {
  const acento = estilo.getPropertyValue('--pn-acento').trim();
  const borde = estilo.getPropertyValue('--pn-acento-borde').trim();
  const suave = estilo.getPropertyValue('--pn-acento-suave').trim();
  return [acento, borde, suave].every((c) => HEX.test(c)) ? { acento, borde, suave } : null;
}

// ------------------------------------------------------------------ textos

export const TEXTOS_WEB_CONTENIDO = {
  cargando: 'Cargando el contenido de tu web…',
  /** Anuncio por voz tras «Reintentar» (boceto). */
  cargado: 'Contenido cargado.',
  errorTitulo: 'No se pudo cargar el contenido de tu web',
  errorTexto: 'Tu web publicada no cambia. Vuelve a intentarlo en unos segundos.',
  ayuda: 'Pulsa un texto o una foto para cambiarlo. Lo que tiene candado se cambia en otra pantalla.',
  ayudaNueva: 'Lo que guardes saldrá al publicar tu web.',
  ayudaPublicada: 'Lo que guardes se publica al momento.',
  marco: 'Vista previa de tu web · ',
  pagina: 'Inicio',
  tituloIframe: 'Vista previa editable de tu web',
} as const;
