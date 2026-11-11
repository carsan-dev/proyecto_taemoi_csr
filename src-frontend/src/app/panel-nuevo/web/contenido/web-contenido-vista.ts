import { InjectionToken } from '@angular/core';

import { EventoListaWeb, SedeWeb, TemaWeb } from '../../../web-plantilla/datos/modelos';
import type { PaginaEditor } from '../../../web-plantilla/editor/candados-linea-comarcal';
import type { AcentoEditor, BloqueBorrador, CasoVista, DestinoPanel, PasoPreinscripcion } from '../../../web-plantilla/editor/puente-editor';
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

/**
 * `/<ruta>?vista-previa=<tema>&editor=1`: la plantilla activa la capa de edición solo si la API confirma la gestión.
 * «Una sede» y «Un evento» necesitan cuál (`sub`, el slug o el id); sin él, null.
 */
export function direccionEditor(tema: TemaWeb, pagina: PaginaEditor = 'inicio', sub: string | null = null): string | null {
  const consulta = `?vista-previa=${encodeURIComponent(tema)}&editor=1`;
  switch (pagina) {
    case 'inicio':
      return `/${consulta}`;
    case 'sede':
      return sub ? `/sedes/${encodeURIComponent(sub)}${consulta}` : null;
    case 'evento':
      return sub ? `/eventos/${encodeURIComponent(sub)}${consulta}` : null;
    default:
      return `/${pagina}${consulta}`;
  }
}

// ------------------------------------------------------------------ selector de página (d519)

export interface PaginaContenido {
  id: PaginaEditor;
  /** La opción del selector (`PAGINAS` del boceto). */
  opcion: string;
  /** En el rótulo del marco, el anuncio y el menú de casos (`nombrePagina` del boceto). */
  nombre: string;
}

export const PAGINAS_CONTENIDO: readonly PaginaContenido[] = [
  { id: 'inicio', opcion: 'Inicio, con cabecera y pie', nombre: 'Inicio' },
  { id: 'sede', opcion: 'Una sede', nombre: 'Una sede' },
  { id: 'tarifas', opcion: 'Tarifas', nombre: 'Tarifas' },
  { id: 'eventos', opcion: 'Eventos', nombre: 'Eventos' },
  { id: 'evento', opcion: 'Un evento', nombre: 'Un evento' },
  { id: 'legales', opcion: 'Legales', nombre: 'Legales' },
  { id: 'preinscripcion', opcion: 'Preinscripción', nombre: 'Preinscripción' },
];

export function esPaginaContenido(valor: unknown): valor is PaginaEditor {
  return PAGINAS_CONTENIDO.some((p) => p.id === valor);
}

export function nombrePagina(pagina: PaginaEditor): string {
  return PAGINAS_CONTENIDO.find((p) => p.id === pagina)!.nombre;
}

/** Una opción del segundo selector («Sede» o «Evento»). */
export interface OpcionSub {
  valor: string;
  texto: string;
  desactivada: boolean;
}

/** Las sedes de la escuela; la que no tiene dirección de página sale «(sin página)» y no se elige (boceto `SUBS`). */
export function opcionesSede(sedes: readonly Pick<SedeWeb, 'nombre' | 'slug'>[]): OpcionSub[] {
  return sedes.map((s) => ({
    valor: s.slug ?? '',
    texto: s.slug ? s.nombre : `${s.nombre} (sin página)`,
    desactivada: !s.slug,
  }));
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const FECHA = /^\d{4}-(\d{2})-(\d{2})$/;

/** «Examen de cinturones (14 nov)» (boceto `SUBS`). */
export function opcionesEvento(eventos: readonly Pick<EventoListaWeb, 'id' | 'titulo' | 'fecha'>[]): OpcionSub[] {
  return eventos.map((e) => {
    const partes = FECHA.exec(e.fecha ?? '');
    const fecha = partes ? ` (${Number(partes[2])} ${MESES[Number(partes[1]) - 1]})` : '';
    return { valor: String(e.id), texto: `${e.titulo}${fecha}`, desactivada: false };
  });
}

/** Los pasos de la preinscripción del selector «Paso» (boceto `SUBS.preinscripcion`). Ninguno se desactiva. */
export const PASOS_CONTENIDO: readonly (OpcionSub & { paso: PasoPreinscripcion })[] = [
  { paso: 1, valor: '1', texto: '1 · Pueblo', desactivada: false },
  { paso: 2, valor: '2', texto: '2 · Grupo', desactivada: false },
  { paso: 3, valor: '3', texto: '3 · Datos y firma', desactivada: false },
  { paso: 4, valor: '4', texto: 'Enviada', desactivada: false },
];

/**
 * Con una sola sede, como la web real (decisión del propietario, 11/11): sin «Pueblo» y renumerados. Con dos o más,
 * `PASOS_CONTENIDO`.
 */
export const PASOS_UNA_SEDE: readonly (OpcionSub & { paso: PasoPreinscripcion })[] = [
  { paso: 2, valor: '2', texto: '1 · Grupo', desactivada: false },
  { paso: 3, valor: '3', texto: '2 · Datos y firma', desactivada: false },
  { paso: 4, valor: '4', texto: 'Enviada', desactivada: false },
];

export const pasosContenido = (unaSede: boolean) => (unaSede ? PASOS_UNA_SEDE : PASOS_CONTENIDO);

/** La primera que se puede elegir; null si no hay ninguna. */
export function primeraOpcion(opciones: readonly OpcionSub[]): string | null {
  return opciones.find((o) => !o.desactivada)?.valor ?? null;
}

/** La nota bajo el selector (boceto `pintarSelector`); en la de sede, los nombres reales de las sedes sin página. */
export function notaPagina(pagina: PaginaEditor, sedesSinPagina: readonly string[]): string | null {
  if (pagina === 'sede') {
    const cuales = sedesSinPagina.length ? ` (${sedesSinPagina.join(', ')})` : '';
    return `Sus textos valen para todas las sedes. Una sede sin dirección de página${cuales} no tiene página: se la pones en Sedes.`;
  }
  if (pagina === 'evento') {
    return 'Sus textos valen para todos los eventos. El nombre, la fecha, las fotos y los documentos se cambian en Eventos.';
  }
  return null;
}

// ------------------------------------------------------------------ «Ver el caso» (d528)

export interface CasoPagina {
  id: CasoVista;
  nombre: string;
  /** Cuándo pasa. */
  cuando: string;
  /** El paso de la preinscripción en el que sale (al elegirlo, el selector «Paso» va a él). */
  paso?: PasoPreinscripcion;
}

/**
 * Los casos de cada página (`CASOS` del boceto). W12c-ter: «Sin clases esta semana» de una sede y los tres de la
 * preinscripción, cada uno con su paso; «Grupo completo» es el caso `espera`, como el boceto.
 */
export const CASOS_PAGINA: Readonly<Record<PaginaEditor, readonly CasoPagina[]>> = {
  inicio: [
    { id: 'lleno', nombre: 'Grupo lleno', cuando: 'Un grupo sin plazas libres' },
    { id: 'espera', nombre: 'Lista de espera', cuando: 'Un grupo que ya tiene lista de espera' },
  ],
  sede: [
    { id: 'lleno', nombre: 'Grupo lleno', cuando: 'Un grupo sin plazas libres' },
    { id: 'espera', nombre: 'Lista de espera', cuando: 'Un grupo que ya tiene lista de espera' },
    { id: 'sin-clases', nombre: 'Sin clases esta semana', cuando: 'Vacaciones o festivos: no hay próxima clase' },
  ],
  tarifas: [{ id: 'sin-tarifas', nombre: 'Sin tarifas publicadas', cuando: 'Aún no hay mensualidad en Tesorería' }],
  eventos: [
    { id: 'sin-proximos', nombre: 'Sin eventos próximos', cuando: 'Hay eventos pasados, ninguno por venir' },
    { id: 'sin-eventos', nombre: 'Sin eventos', cuando: 'Todavía no hay ninguno' },
    { id: 'un-evento', nombre: 'Un solo evento', cuando: 'La cuenta dice «1 evento»' },
    { id: 'sin-anteriores', nombre: 'Sin eventos anteriores', cuando: 'Hay próximos, ninguno pasado' },
  ],
  evento: [{ id: 'una-foto', nombre: 'Una sola foto', cuando: 'La galería tiene una foto' }],
  legales: [],
  preinscripcion: [
    { id: 'sin-grupo', nombre: 'Sin grupo para su edad', cuando: 'Paso 2: su sede no tiene grupo para esa edad', paso: 2 },
    { id: 'espera', nombre: 'Grupo completo', cuando: 'Paso 3: se apunta a la lista de espera', paso: 3 },
    { id: 'fin-espera', nombre: 'En la lista de espera', cuando: 'Paso 4: enviada a la lista de espera', paso: 4 },
  ],
};

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
  plantillas: '/preinscripciones?tab=plantillas',
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
  etiquetaPagina: 'Página',
  etiquetaSede: 'Sede',
  etiquetaEvento: 'Evento',
  etiquetaPaso: 'Paso',
  verCaso: 'Ver el caso',
  datosReales: 'Datos reales',
  datosRealesCuando: 'Tu web como está ahora',
  ayudaCasos: 'Textos que solo salen en casos poco frecuentes. Elige uno para verlo y cambiar sus textos.',
  ayudaSinCasos: 'Esta página no tiene textos de casos poco frecuentes.',
  casoNoGuarda: 'Solo para ver y cambiar sus textos: no es un cambio y no se guarda.',
  volverReales: 'Volver a datos reales',
  vozReales: 'Vuelves a los datos reales.',
} as const;

export const tituloMenuCasos = (pagina: PaginaEditor) => `Ver un caso de ${nombrePagina(pagina)}`;
export const vozPagina = (pagina: PaginaEditor) => `Vista previa de ${nombrePagina(pagina)}.`;
export const vozCaso = (caso: CasoPagina) => `Viendo: ${caso.nombre}. No es un cambio y no se guarda.`;
