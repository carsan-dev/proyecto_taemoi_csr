import type { DestinoPanel } from './puente-editor';

/**
 * Lo que la capa de edición marca sobre «La línea comarcal» (W12c; boceto `boceto-panel-contenido-web-comarcal.html`,
 * `envolver`, `envolverFija`, `dato()`; d517). Selectores del DOM del tema construido: la capa no toca el tema, lo
 * anota al pintarse.
 */

/** Dónde se cambia un dato: una pantalla del panel, el equipo de Taemoi o la propia web. */
export type DondeDato = { pantalla: DestinoPanel } | 'equipo' | 'web';

/** Nombre de cada pantalla en el aviso «X · Se cambia en Y» (textos del boceto). */
export const NOMBRE_DESTINO: Readonly<Record<DestinoPanel, string>> = {
  sedes: 'Sedes',
  grupos: 'Grupos',
  eventos: 'Eventos',
  productos: 'Tesorería · Productos',
  'datos-escuela': 'Escuela · Datos de la escuela',
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

/** Tabla de candados de la página Inicio (con cabecera y pie), en el orden del boceto. */
export const CANDADOS_LINEA_COMARCAL: readonly Candado[] = [
  // Cabecera
  { selector: '.lc > .cabecera .logo', que: 'Logo y color', donde: 'equipo', caja: true },
  { selector: '.lc > .cabecera .marca-nombre', que: 'Nombre de la escuela', donde: 'equipo', caja: true },
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
  // Pie
  { selector: '.pie-marca', que: 'Nombre de la escuela', donde: 'equipo' },
  { selector: '.pie-fila > p:first-child', que: 'Nombre legal', donde: ESCUELA },
];

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

/** Bloques y partes fijas de Inicio (`PARTE_N` y `TIPO_N` del boceto). */
export const PARTES_LINEA_COMARCAL: readonly Parte[] = [
  { selector: '.lc > .cabecera', id: 'cabecera', fija: true, nombre: 'Cabecera', nota: NOTA_FIJA },
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
  { selector: '.lc > .pie', id: 'pie', fija: true, nombre: 'Pie', nota: NOTA_FIJA },
];
