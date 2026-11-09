import { TEMAS_WEB, TemaWeb, esTemaWeb } from '../../../web-plantilla/datos/modelos';
import type { BloqueMensaje, ParteMensaje } from '../../dialogo/dialogo.component';

/**
 * Lógica pura de «Web · Tema» (W7; boceto `docs/multi-escuela/fase4-web/boceto-panel-tema-web.html`): qué temas se
 * enseñan, el tema publicado y el elegido, los textos de la barra, del diálogo y de la vista previa, y la dirección
 * del iframe. Textos literales del boceto.
 */

/** `GET` y `PUT /api/escuela/web` (W3, `EscuelaWebController`). */
export interface EstadoWebEscuela {
  tema: string | null;
  publicada: boolean;
  /** ISO; null sin publicar. */
  publicadaEn: string | null;
  temasDisponibles: string[];
}

/** Piezas de la mini-maqueta decorativa de cada tema (clases del CSS del boceto). */
export type MiniTema = 'linea' | 'chandal' | 'sede' | 'estandar';

export interface FichaTema {
  id: TemaWeb;
  nombre: string;
  descripcion: string;
  mini: MiniTema;
  piezas: readonly string[];
}

/** Los cuatro temas aprobados, con el nombre, la descripción y la mini-maqueta del boceto. */
export const FICHAS_TEMA: Readonly<Record<TemaWeb, FichaTema>> = {
  'linea-comarcal': {
    id: 'linea-comarcal',
    nombre: 'La línea comarcal',
    descripcion: 'Las sedes como paradas de una línea, con sus horarios.',
    mini: 'linea',
    piezas: ['m-cab', 'm-linea', 'm-paradas', 'm-tabla'],
  },
  chandal: {
    id: 'chandal',
    nombre: 'El chándal del club',
    descripcion: 'Franja del color de la escuela y un parche por sede.',
    mini: 'chandal',
    piezas: ['m-franja', 'm-titulo', 'm-parches'],
  },
  'sede-por-pantalla': {
    id: 'sede-por-pantalla',
    nombre: 'Una sede por pantalla',
    descripcion: 'La foto de cada sede a pantalla completa.',
    mini: 'sede',
    piezas: ['m-foto', 'm-cab', 'm-nombre', 'm-boton', 'm-puntos'],
  },
  estandar: {
    id: 'estandar',
    nombre: 'El estándar del sector',
    descripcion: 'Foto de acción, lema y las secciones de siempre.',
    mini: 'estandar',
    piezas: ['m-cab', 'm-heroe', 'm-lema', 'm-boton', 'm-secciones'],
  },
};

/** Los temas que ya tiene la plantilla (§9 n.º 12): la pantalla solo enseña estos (y el publicado, si fuera otro). */
export const TEMAS_CONSTRUIDOS: readonly TemaWeb[] = ['linea-comarcal'];

/** Los temas de la pantalla, en el orden de los bocetos: los construidos y, si no lo está, el publicado. */
export function temasVisibles(publicado: TemaWeb | null): FichaTema[] {
  return TEMAS_WEB.filter((t) => TEMAS_CONSTRUIDOS.includes(t) || t === publicado).map((t) => FICHAS_TEMA[t]);
}

export function nombreTema(tema: TemaWeb): string {
  return FICHAS_TEMA[tema].nombre;
}

/** El tema con el que se ve la web hoy; null si no está publicada (o la API da un tema desconocido). */
export function temaPublicado(estado: EstadoWebEscuela): TemaWeb | null {
  return estado.publicada && esTemaWeb(estado.tema) ? estado.tema : null;
}

/** El elegido al abrir: el publicado; si no hay, el primero construido. */
export function temaInicial(publicado: TemaWeb | null): TemaWeb {
  return publicado ?? TEMAS_CONSTRUIDOS[0];
}

/** Barra inferior: oculta (elegido = publicado), «cambios» (publicada y otro elegido) o «primera» (sin publicar). */
export type ModoBarra = 'oculta' | 'cambios' | 'primera';

export function modoBarra(publicado: TemaWeb | null, elegido: TemaWeb): ModoBarra {
  if (publicado === null) {
    return 'primera';
  }
  return elegido === publicado ? 'oculta' : 'cambios';
}

/** «Salir sin guardar»: solo con la web publicada y otro tema elegido (la primera vez no avisa). */
export function hayCambiosSinPublicar(publicado: TemaWeb | null, elegido: TemaWeb): boolean {
  return modoBarra(publicado, elegido) === 'cambios';
}

/** Comillas tipográficas del boceto. */
export function entreComillas(texto: string): string {
  return `“${texto}”`;
}

/** Título, frase y párrafo del diálogo de publicar. */
export interface TextosPublicar {
  titulo: string;
  mensaje: ParteMensaje[];
  bloques: BloqueMensaje[];
}

export function textosPublicar(elegido: TemaWeb, publicado: TemaWeb | null): TextosPublicar {
  const nuevo = { fuerte: entreComillas(nombreTema(elegido)) };
  if (publicado !== null) {
    return {
      titulo: `¿Publicar tu web con ${entreComillas(nombreTema(elegido))}?`,
      mensaje: ['Las familias verán tu web con el tema ', nuevo, ` desde ahora, en lugar de ${entreComillas(nombreTema(publicado))}.`],
      bloques: [{ tipo: 'parrafo', partes: ['Tus sedes, horarios, logo y color no cambian.'] }],
    };
  }
  return {
    titulo: '¿Publicar tu web?',
    mensaje: ['Las familias verán tu web con el tema ', nuevo, ' desde ahora.'],
    bloques: [{ tipo: 'parrafo', partes: ['Hasta ahora tu web no estaba publicada.'] }],
  };
}

export function textoPublicada(tema: TemaWeb): string {
  return `Tu web ya se ve con ${entreComillas(nombreTema(tema))}.`;
}

// ------------------------------------------------------------------ vista previa

export type Dispositivo = 'movil' | 'escritorio';
export type IdPaginaPrevia = 'portada' | 'sede' | 'preinscripcion' | 'tarifas' | 'eventos' | 'legales';

export interface PaginaPrevia {
  id: IdPaginaPrevia;
  /** La opción del selector. */
  etiqueta: string;
  /** En el título del iframe y en el aviso de lista («una sede»). */
  nombre: string;
}

export const PAGINAS_PREVIA: readonly PaginaPrevia[] = [
  { id: 'portada', etiqueta: 'Portada', nombre: 'portada' },
  { id: 'sede', etiqueta: 'Una sede', nombre: 'una sede' },
  { id: 'preinscripcion', etiqueta: 'Preinscripción', nombre: 'preinscripción' },
  { id: 'tarifas', etiqueta: 'Tarifas', nombre: 'tarifas' },
  { id: 'eventos', etiqueta: 'Eventos', nombre: 'eventos' },
  { id: 'legales', etiqueta: 'Legales', nombre: 'legales' },
];

export function paginaPrevia(id: IdPaginaPrevia): PaginaPrevia {
  return PAGINAS_PREVIA.find((p) => p.id === id)!;
}

/** Tamaño de la ventana que se simula en cada dispositivo (se escala al ancho disponible). */
export const VIEWPORT: Readonly<Record<Dispositivo, { ancho: number; alto: number }>> = {
  escritorio: { ancho: 1280, alto: 800 },
  movil: { ancho: 375, alto: 700 },
};

/** Lo que el marco añade a la ventana: 10 px de carcasa por lado en el móvil y el filete de 1 px en el escritorio. */
export const MARCO: Readonly<Record<Dispositivo, number>> = { escritorio: 2, movil: 20 };

/** Escala de la vista previa: nunca mayor que 1. */
export function escala(disponible: number, ancho: number): number {
  return disponible > 0 ? Math.min(1, disponible / ancho) : 1;
}

/** Ruta de la plantilla de cada página; null para «Una sede» sin sede con página. */
export function rutaPagina(pagina: IdPaginaPrevia, slugSede: string | null): string | null {
  switch (pagina) {
    case 'portada':
      return '/';
    case 'sede':
      return slugSede ? `/sedes/${encodeURIComponent(slugSede)}` : null;
    default:
      return `/${pagina}`;
  }
}

/** `/<ruta>?vista-previa=<tema>` (§3.3); null si la página no se puede ver. */
export function direccionVistaPrevia(pagina: IdPaginaPrevia, tema: TemaWeb, slugSede: string | null): string | null {
  const ruta = rutaPagina(pagina, slugSede);
  return ruta === null ? null : `${ruta}?vista-previa=${encodeURIComponent(tema)}`;
}

export function tituloVistaPrevia(tema: TemaWeb, pagina: IdPaginaPrevia, dispositivo: Dispositivo): string {
  return `Vista previa de tu web: ${nombreTema(tema)}, ${paginaPrevia(pagina).nombre}, ${dispositivo === 'movil' ? 'móvil' : 'escritorio'}`;
}

export function textoVistaLista(tema: TemaWeb, pagina: IdPaginaPrevia): string {
  return `Vista previa: ${nombreTema(tema)}, ${paginaPrevia(pagina).nombre}.`;
}

/** Retrasos de la vista previa: el esqueleto a los 300 ms (DESIGN.md, Carga) y el error si no carga en 15 s. */
export const RETRASO_ESQUELETO_PREVIA = 300;
export const LIMITE_CARGA_PREVIA = 15000;

export const TEXTOS_WEB_TEMA = {
  errorCarga: 'No se ha podido leer el estado de tu web.',
  estadoSinPublicar: 'Tu web aún no está publicada. Elige un tema, revísalo y publícalo.',
  cargandoPantalla: 'Cargando el tema de tu web…',
  cargandoVista: 'Cargando la vista previa…',
  errorVistaTitulo: 'No se pudo cargar la vista previa',
  errorVistaTexto: 'Tu web publicada no cambia. Vuelve a intentarlo en unos segundos.',
  errorPublicar: 'No se ha podido publicar la web.',
  errorPublicarDetalle: 'Comprueba la conexión y vuelve a intentarlo.',
  publicarCambios: 'Publicar con este tema',
  publicarPrimera: 'Publicar la web con este tema',
} as const;
