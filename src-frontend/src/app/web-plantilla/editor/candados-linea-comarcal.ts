import type { DestinoPanel } from './puente-editor';

/**
 * Lo que la capa de edición marca sobre «La línea comarcal» (W12c; boceto `boceto-panel-contenido-web-comarcal.html`,
 * `envolver`, `envolverFija`, `dato()`; d517). Selectores del DOM del tema construido: la capa no toca el tema, lo
 * anota al pintarse. W12c-bis (d519): las otras seis páginas, cada una con su tabla; la cabecera y el pie, en todas.
 */

/** Las siete páginas del editor (`PAGINAS` del boceto). */
export const PAGINAS_EDITOR = ['inicio', 'sede', 'tarifas', 'eventos', 'evento', 'legales', 'preinscripcion'] as const;
export type PaginaEditor = (typeof PAGINAS_EDITOR)[number];
type PaginaInterior = Exclude<PaginaEditor, 'inicio'>;

/** La raíz de cada página interior del tema; sin ninguna, es Inicio. */
const RAIZ_PAGINA: Readonly<Record<PaginaInterior, string>> = {
  sede: '.p-sede',
  tarifas: '.p-tarifas',
  eventos: '.p-eventos',
  evento: '.p-evento',
  legales: '.p-legales',
  preinscripcion: '.p-preinscripcion',
};

/** La página que pinta el documento, por la raíz del tema. */
export function paginaDelDocumento(doc: Pick<Document, 'querySelector'>): PaginaEditor {
  for (const [pagina, selector] of Object.entries(RAIZ_PAGINA)) {
    if (doc.querySelector(selector)) {
      return pagina as PaginaInterior;
    }
  }
  return 'inicio';
}

/** Dónde se cambia un dato: una pantalla del panel, el equipo de Taemoi o la propia web. */
export type DondeDato = { pantalla: DestinoPanel } | 'equipo' | 'web';

/** Nombre de cada pantalla en el aviso «X · Se cambia en Y» (textos del boceto). */
export const NOMBRE_DESTINO: Readonly<Record<DestinoPanel, string>> = {
  sedes: 'Sedes',
  grupos: 'Grupos',
  eventos: 'Eventos',
  productos: 'Tesorería · Productos',
  'datos-escuela': 'Escuela · Datos de la escuela',
  plantillas: 'Preinscripciones · Plantillas',
};

export const NOMBRE_EQUIPO = 'el equipo de Taemoi';

export interface Candado {
  selector: string;
  /** El dato, como lo nombra el boceto. */
  que: string;
  donde: DondeDato;
  /** Dato en línea que necesita caja (`ed-dato--caja` del boceto). */
  caja?: boolean;
}

const SEDES: DondeDato = { pantalla: 'sedes' };
const GRUPOS: DondeDato = { pantalla: 'grupos' };
const EVENTOS: DondeDato = { pantalla: 'eventos' };
const PRODUCTOS: DondeDato = { pantalla: 'productos' };
const ESCUELA: DondeDato = { pantalla: 'datos-escuela' };
const PLANTILLAS: DondeDato = { pantalla: 'plantillas' };

/** Cabecera de todas las páginas. */
const CANDADOS_CABECERA: readonly Candado[] = [
  { selector: '.lc > .cabecera .logo', que: 'Logo y color', donde: 'equipo', caja: true },
  { selector: '.lc > .cabecera .marca-nombre', que: 'Nombre de la escuela', donde: 'equipo', caja: true },
];

/** Pie de todas las páginas. */
const CANDADOS_PIE: readonly Candado[] = [
  { selector: '.pie-marca', que: 'Nombre de la escuela', donde: 'equipo' },
  { selector: '.pie-fila > p:first-child', que: 'Nombre legal', donde: ESCUELA },
];

/** Tabla de candados de la página Inicio (con cabecera y pie), en el orden del boceto. */
export const CANDADOS_LINEA_COMARCAL: readonly Candado[] = [
  ...CANDADOS_CABECERA,
  // Portada
  { selector: '.portada .rotulo-disciplina', que: 'Disciplina', donde: 'equipo' },
  { selector: '.portada .rotulo-sedes', que: 'Número de sedes', donde: SEDES },
  // Horarios (la línea)
  { selector: '.parada-nombre', que: 'Nombre de la sede', donde: SEDES, caja: true },
  { selector: '.parada-cuando', que: 'Próxima clase', donde: GRUPOS },
  { selector: '.tablero-sede', que: 'Nombre de la sede', donde: SEDES },
  { selector: '.tablero-lugar', que: 'Lugar y dirección', donde: SEDES },
  { selector: '.salida-hora', que: 'Horario del grupo', donde: GRUPOS },
  { selector: '.salida-grupo', que: 'Grupo y edades', donde: GRUPOS },
  { selector: '.salida-detalle .dias-p', que: 'Días de clase', donde: GRUPOS },
  { selector: '.plazas--quedan', que: 'Plazas libres', donde: GRUPOS },
  // Presentación: el club en cifras
  { selector: '.datos [data-dato="sedes"]', que: 'Número de sedes', donde: SEDES },
  { selector: '.datos [data-dato="edad"]', que: 'Edad de inicio', donde: GRUPOS },
  { selector: '.datos [data-dato="mensualidad"]', que: 'Mensualidad', donde: PRODUCTOS },
  { selector: '.datos [data-dato="grupos"]', que: 'Grupos por sede', donde: GRUPOS },
  // Disciplinas
  { selector: '.disciplina .titulo-seccion', que: 'Nombre de la disciplina', donde: 'equipo' },
  // Fotos de las sedes
  { selector: '.fs-titulo', que: 'Nombre y lugar de la sede', donde: SEDES },
  { selector: '.fs-foto', que: 'Foto de la sede', donde: SEDES },
  // Próximos eventos
  { selector: '.evento-fecha', que: 'Fecha del evento', donde: EVENTOS },
  { selector: '.evento > div', que: 'Evento: nombre, lugar y hora', donde: EVENTOS },
  // Tarifas
  { selector: '.tarifas .tarifa-importe', que: 'Importe de la mensualidad', donde: PRODUCTOS },
  // Contacto
  { selector: '.contacto-datos dd:has(> a[href^="mailto:"])', que: 'Correo de contacto', donde: ESCUELA },
  { selector: '.contacto-datos dd:has(> a.cifras)', que: 'Teléfono de contacto', donde: ESCUELA },
  { selector: '.ruta-sede', que: 'Sede: nombre y dirección', donde: SEDES },
  ...CANDADOS_PIE,
];

/**
 * Las otras seis páginas (`sedePagHtml`, `tarifasPagHtml`, `eventosPagHtml`, `eventoPagHtml`, `legalesHtml` y
 * `preinscripcionHtml` del boceto). Los datos que el boceto pone dentro de un texto como ficha («Preinscribirme en
 * [Herrera]», «desde [30 €]») son de W12d.
 */
const CANDADOS_PAGINAS: Readonly<Record<PaginaInterior, readonly Candado[]>> = {
  sede: [
    { selector: '.p-sede .tramo-parada:not(.tramo-parada--actual) .tramo-nombre', que: 'Nombre de la sede', donde: SEDES },
    { selector: '.p-sede .sede-titular', que: 'Nombre de la sede', donde: SEDES },
    { selector: '.p-sede .sede-lugar', que: 'Lugar y dirección', donde: SEDES },
    { selector: '.p-sede .sede-foto, .p-sede .llegar-foto', que: 'Foto de la sede', donde: SEDES },
    { selector: '.p-sede .proxima-cuando', que: 'Próxima clase', donde: GRUPOS },
    { selector: '.p-sede .salida-hora', que: 'Horario del grupo', donde: GRUPOS },
    { selector: '.p-sede .salida-grupo', que: 'Grupo y edades', donde: GRUPOS },
    { selector: '.p-sede .salida-detalle .dias-p', que: 'Días de clase', donde: GRUPOS },
    { selector: '.p-sede .plazas--quedan', que: 'Plazas libres', donde: GRUPOS },
    { selector: '.p-sede .ficha [data-dato="lugar"]', que: 'Lugar y dirección', donde: SEDES },
    { selector: '.p-sede .ficha [data-dato="acceso"]', que: 'Indicaciones de acceso', donde: SEDES },
    { selector: '.p-sede .ficha [data-dato="telefono"]', que: 'Teléfono de la sede', donde: SEDES },
    { selector: '.p-sede #tarifas .tarifa-importe', que: 'Importe de la mensualidad', donde: PRODUCTOS },
    { selector: '.p-sede .ruta-nombre', que: 'Nombre de la sede', donde: SEDES },
    { selector: '.p-sede .ruta-dir', que: 'Lugar y dirección', donde: SEDES },
  ],
  tarifas: [
    { selector: '.p-tarifas #tarifas .tarifa-importe', que: 'Importe de la mensualidad', donde: PRODUCTOS },
    { selector: '.p-tarifas .tarifa-contacto dd:has(> a[href^="mailto:"])', que: 'Correo de contacto', donde: ESCUELA },
    { selector: '.p-tarifas .tarifa-contacto dd:has(> a.cifras)', que: 'Teléfono de contacto', donde: ESCUELA },
    { selector: '.p-tarifas .cuadro tbody th', que: 'Nombre de la tarifa', donde: PRODUCTOS },
    { selector: '.p-tarifas .cuadro td.importe', que: 'Importe de la tarifa', donde: PRODUCTOS },
    { selector: '.p-tarifas .lt-nombre', que: 'Nombre de la sede', donde: SEDES },
    { selector: '.p-tarifas .lt-importe', que: 'Mensualidad en la sede', donde: PRODUCTOS },
    { selector: '.p-tarifas .aparte [data-dato="licencia"]', que: 'Precio de la licencia', donde: PRODUCTOS },
  ],
  eventos: [
    { selector: '.p-eventos #proximos .fecha', que: 'Fecha del evento', donde: EVENTOS },
    { selector: '.p-eventos .destacado-foto, .p-eventos .ev-miniatura', que: 'Foto del evento', donde: EVENTOS },
    {
      selector:
        '.p-eventos .destacado-titulo, .p-eventos .destacado-cuerpo > .ev-donde, .p-eventos .destacado-desc, .p-eventos .ev-texto',
      que: 'Evento: nombre y descripción',
      donde: EVENTOS,
    },
    { selector: '.p-eventos .ev-mini', que: 'Evento anterior', donde: EVENTOS },
  ],
  evento: [
    { selector: '.p-evento .evento-rotulo', que: 'Fecha, hora y lugar del evento', donde: EVENTOS },
    { selector: '.p-evento .evento-titular', que: 'Nombre del evento', donde: EVENTOS },
    { selector: '.p-evento .evento-foto', que: 'Foto del evento', donde: EVENTOS },
    { selector: '.p-evento .evento-desc', que: 'Descripción del evento', donde: EVENTOS },
    { selector: '.p-evento .docs', que: 'Documentos del evento', donde: EVENTOS },
    { selector: '.p-evento .rejilla-fotos', que: 'Fotos del evento', donde: EVENTOS },
  ],
  legales: [
    { selector: '.p-legales .actualizado time', que: 'Fecha de la última publicación', donde: 'web' },
    { selector: '.p-legales .ficha [data-dato="titular"]', que: 'Nombre legal', donde: ESCUELA },
    { selector: '.p-legales .ficha [data-dato="cif"]', que: 'CIF', donde: ESCUELA },
    { selector: '.p-legales .ficha [data-dato="domicilio"]', que: 'Domicilio', donde: ESCUELA },
    { selector: '.p-legales .ficha [data-dato="correo"]', que: 'Correo de contacto', donde: ESCUELA },
    { selector: '.p-legales .normas-lista', que: 'Normas del club', donde: PLANTILLAS },
  ],
  preinscripcion: [
    {
      selector: '.p-preinscripcion .selector-linea .sel-nombre, .p-preinscripcion .selector-linea .sel-dato',
      que: 'Sede, grupos y próxima clase',
      donde: SEDES,
    },
    { selector: '.p-preinscripcion .contexto > span', que: 'Sede elegida', donde: SEDES },
    {
      selector: '.p-preinscripcion .resumen .resumen-fila:first-child > dd:not(.resumen-vacio):not(.resumen-cambiar)',
      que: 'Sede elegida',
      donde: SEDES,
    },
  ],
};

/** Los candados de una página, con la cabecera y el pie. */
export function candadosDe(pagina: PaginaEditor): readonly Candado[] {
  return pagina === 'inicio' ? CANDADOS_LINEA_COMARCAL : [...CANDADOS_CABECERA, ...CANDADOS_PAGINAS[pagina], ...CANDADOS_PIE];
}

/**
 * Dónde no se pone el enlace oculto de un tipo de dato (va en el siguiente del mismo tipo): dentro de un enlace o un
 * botón del tema (no se anidan controles) o en el horario plegado de una parada. Si todos los de un tipo están dentro
 * de un enlace o un botón («Próxima clase», en el botón de la parada), la capa lo pone justo después de ese control.
 */
export const SIN_ENLACE_LINEA_COMARCAL = 'a, button, .horario:not(.abierto)';

export const NOTA_FIJA = 'Su sitio lo pone el tema. Pulsa un texto para cambiarlo.';
export const NOTA_FIJA_FOTO = 'Su sitio lo pone el tema. Pulsa un texto o la foto para cambiarlos.';

export interface Parte {
  selector: string;
  /** Id del boceto (`data-bloque`). */
  id: string;
  /** Parte fija del tema (con su nota) o bloque que el propietario mueve (W12e). */
  fija: boolean;
  /** Nombre visible del botón. */
  nombre: string;
  nota?: string;
}

const PARTE_CABECERA: Parte = { selector: '.lc > .cabecera', id: 'cabecera', fija: true, nombre: 'Cabecera', nota: NOTA_FIJA };
const PARTE_PIE: Parte = { selector: '.lc > .pie', id: 'pie', fija: true, nombre: 'Pie', nota: NOTA_FIJA };
const fija = (selector: string, id: string, nombre: string, nota = NOTA_FIJA): Parte => ({ selector, id, fija: true, nombre, nota });

/** Bloques y partes fijas de Inicio (`PARTE_N` y `TIPO_N` del boceto). */
export const PARTES_LINEA_COMARCAL: readonly Parte[] = [
  PARTE_CABECERA,
  { selector: '.portada > .portada-rejilla', id: 'portada', fija: false, nombre: 'Portada' },
  { selector: 'app-lc-linea-sedes', id: 'horarios', fija: true, nombre: 'Horarios (la línea)', nota: NOTA_FIJA },
  { selector: 'section#presentacion', id: 'presentacion', fija: false, nombre: 'Presentación' },
  { selector: 'section#disciplinas', id: 'disciplinas', fija: true, nombre: 'Disciplinas', nota: NOTA_FIJA_FOTO },
  { selector: 'section#fotos-sedes', id: 'sedes', fija: false, nombre: 'Fotos de las sedes' },
  { selector: 'section#profesores', id: 'profesores', fija: false, nombre: 'Profesores' },
  { selector: 'section#galeria', id: 'galeria', fija: false, nombre: 'Galería' },
  { selector: 'section[id^="texto-foto-"]', id: 'texto_foto', fija: false, nombre: 'Texto con foto' },
  { selector: 'section#eventos', id: 'eventos', fija: true, nombre: 'Próximos eventos', nota: NOTA_FIJA },
  { selector: 'section#tarifas', id: 'tarifas', fija: true, nombre: 'Tarifas', nota: NOTA_FIJA },
  { selector: 'section#contacto', id: 'contacto', fija: true, nombre: 'Contacto', nota: NOTA_FIJA },
  PARTE_PIE,
];

/** Partes fijas de las otras seis páginas (`envolverFija` y `PARTE_N` del boceto). */
const PARTES_PAGINAS: Readonly<Record<PaginaInterior, readonly Parte[]>> = {
  sede: [
    fija('.p-sede > .sede-portada', 'sp-cartel', 'Cartel de la sede'),
    fija('.p-sede > #horarios', 'sp-horarios', 'Grupos y horas'),
    fija('.p-sede > #llegar', 'sp-llegar', 'Cómo llegar'),
    fija('.p-sede > #tarifas', 'sp-tarifa', 'Tarifa'),
    fija('.p-sede > #sedes', 'sp-ruta', 'Todas las sedes'),
  ],
  tarifas: [
    fija('.p-tarifas > #tarifas', 'tp-banda', 'Cuota'),
    fija('.p-tarifas > #cuadro', 'tp-cuadro', 'Cuadro de tarifas'),
    fija('.p-tarifas > #por-sede', 'tp-sede', 'Por sede'),
    fija('.p-tarifas > #aparte', 'tp-aparte', 'Licencia y exámenes'),
  ],
  eventos: [
    fija('.p-eventos > .pagina-cabeza', 'ep-cabeza', 'Cabeza de la página'),
    fija('.p-eventos > #proximos', 'ep-proximos', 'Próximos'),
    fija('.p-eventos > #anteriores', 'ep-anteriores', 'Anteriores'),
  ],
  evento: [
    fija('.p-evento > .evento-cabeza', 'ev-cabeza', 'Evento'),
    fija('.p-evento > .evento-cuerpo', 'ev-cuerpo', 'Descripción y documentos'),
    fija('.p-evento > #galeria', 'ev-galeria', 'Galería del evento'),
  ],
  legales: [
    fija('.p-legales > .legal-cabeza', 'lg-cabeza', 'Cabeza de la página'),
    fija('.p-legales > .legal-rejilla', 'lg-texto', 'Texto legal', 'Su sitio lo pone el tema. El índice repite los títulos de las secciones.'),
  ],
  preinscripcion: [fija('.p-preinscripcion > .flujo', 'pr-flujo', 'Preinscripción')],
};

/** Las partes y bloques de una página, con la cabecera y el pie. */
export function partesDe(pagina: PaginaEditor): readonly Parte[] {
  return pagina === 'inicio' ? PARTES_LINEA_COMARCAL : [PARTE_CABECERA, ...PARTES_PAGINAS[pagina], PARTE_PIE];
}
