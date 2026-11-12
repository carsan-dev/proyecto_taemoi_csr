import type { DestinoPanel } from './puente-editor';

/**
 * Los huecos de los textos editables (W12d-bis; boceto `boceto-panel-contenido-web-comarcal.html`, `conFichas` y los
 * `vals` de `texto()` en cada página). Un hueco (`{x}`, `{n}`, `{grupo}`…) es un dato de otra pantalla dentro del
 * texto: en el editor va como una ficha que no se escribe, con su candado, y al leer el texto vuelve el token. Lógica
 * pura que comparten la capa (dentro del iframe) y el panel (el diálogo «¿Quitar … de este texto?»).
 */

/** Dónde se cambia el dato de un hueco: una pantalla del panel, la propia web o otro texto de esta misma página. */
export type DondeHueco = DestinoPanel | 'web' | 'texto';

export interface DefHueco {
  /** El nombre del dato (`vals[n][1]` del boceto), como en los candados. */
  que: string;
  /** Con su artículo, en minúsculas, para la frase «En tu web, la frase saldrá sin …». */
  conArticulo: string;
  donde: DondeHueco;
  /** El dato de ejemplo (el del boceto), si la vista previa no deja leer el de la escuela. */
  ejemplo: string;
  /**
   * Lo que dice el candado tras el nombre cuando no basta el de `donde` (textos aprobados por el propietario, 12/11):
   * un dato que no se cambia en ninguna pantalla o que se cambia en otro texto de esta página.
   */
  candado?: string;
}

const h = (que: string, conArticulo: string, donde: DondeHueco, ejemplo: string, candado?: string): DefHueco =>
  candado ? { que, conArticulo, donde, ejemplo, candado } : { que, conArticulo, donde, ejemplo };

/** El curso lo pone la web con la fecha de hoy (API `TemporadaService.actual()`): no se cambia en Grupos ni en Productos. */
const CURSO = h('Curso', 'el curso', 'web', '2026-2027');
const SEDE_ELEGIDA = h('Sede elegida', 'la sede elegida', 'sedes', 'Estepa');
const GRUPO_ELEGIDO = h('Grupo elegido', 'el grupo elegido', 'grupos', 'Peques');
const DIAS_ELEGIDOS = h('Días elegidos', 'los días elegidos', 'web', 'lunes y miércoles');

/** Los huecos de cada texto. Un token que no está aquí se queda como texto escrito. */
export const HUECOS: Readonly<Record<string, Readonly<Record<string, DefHueco>>>> = {
  'sede.rotulo_horarios': { x: CURSO },
  // Enlazado con `horarios.lleno` (decisión del propietario, 12/11): la ficha enseña ese texto, también el del borrador.
  'sede.leyenda_lleno': {
    x: h('Etiqueta de grupo lleno', 'la etiqueta de grupo lleno', 'texto', 'Lleno', 'Se cambia más arriba, en la etiqueta “Lleno” de un grupo'),
  },
  'tarifas.cuota_igual': { x: h('Número de sedes', 'el número de sedes', 'sedes', 'seis') },
  'tarifas.tambien': { x: h('Otras tarifas', 'las otras tarifas', 'productos', 'tarifa familiar') },
  'tarifas.leyenda_cuadro': { x: CURSO },
  'tarifas.examen_desde': { x: h('Precio del examen', 'el precio del examen', 'productos', '30 €') },
  'eventos.n_eventos': { n: h('Número de eventos', 'el número de eventos', 'web', '3') },
  'evento.n_fotos': { n: h('Número de fotos', 'el número de fotos', 'web', '5') },
  'legal.aviso.p2': { x: h('Nombre legal', 'el nombre legal', 'datos-escuela', 'Club Deportivo Taekwondo Sierra Sur') },
  'pre.paso_n': {
    n: h('Número del paso', 'el número del paso', 'web', '1'),
    total: h('Número de pasos', 'el número de pasos', 'web', '3'),
  },
  'pre.espera_fecha': { x: SEDE_ELEGIDA },
  'pre.vacio_titulo': { x: SEDE_ELEGIDA, n: h('Edad del alumno', 'la edad del alumno', 'web', '15') },
  'pre.vacio_llamar': { x: SEDE_ELEGIDA },
  'pre.espera_texto': { grupo: GRUPO_ELEGIDO, dias: DIAS_ELEGIDOS },
  'pre.estado_plaza': { grupo: GRUPO_ELEGIDO, sede: SEDE_ELEGIDA },
  'pre.estado_espera': {
    n: h('Posición en la lista', 'la posición en la lista', 'web', '3'),
    grupo: GRUPO_ELEGIDO,
    sede: SEDE_ELEGIDA,
    dias: DIAS_ELEGIDOS,
  },
  // El teléfono que la familia escribe en el formulario (tema: `datos.telefono`); no se cambia en ninguna pantalla.
  'pre.paso_revisa_texto': {
    x: h('Teléfono de la familia', 'el teléfono de la familia', 'web', '600 000 000', 'Lo escribe la familia en el formulario'),
  },
  'pre.tarifa_igual': { x: h('Importe de la mensualidad', 'el importe de la mensualidad', 'productos', '25 €') },
};

/** El hueco `nombre` (sin llaves) del texto `clave`, o null. */
export function huecoDe(clave: string, nombre: string): DefHueco | null {
  return HUECOS[clave]?.[nombre] ?? null;
}

const lista = (partes: readonly string[]) =>
  partes.length <= 1 ? (partes[0] ?? '') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`;

/**
 * El diálogo al quitar una ficha (decisión del propietario, 12/11): título «¿Quitar «pueblo» de este texto?» y el
 * mensaje con cómo se ve hoy la frase y cómo se verá sin el dato. Null si algún hueco no es de ese texto.
 */
export function textosQuitarDato(
  clave: string,
  huecos: readonly string[],
  antes: string,
  despues: string,
): { titulo: string; mensaje: string; accion: string; cancelar: string } | null {
  const defs = huecos.map((n) => huecoDe(clave, n));
  if (!defs.length || defs.some((d) => d === null)) {
    return null;
  }
  const validas = defs as DefHueco[];
  return {
    titulo: `¿Quitar ${lista(validas.map((d) => `«${d.que.toLowerCase()}»`))} de este texto?`,
    mensaje: `En tu web, la frase saldrá sin ${lista(validas.map((d) => d.conArticulo))}. Hoy se ve así: «${antes}» y pasará a verse así: «${despues}»`,
    accion: 'Quitar dato',
    cancelar: 'Cancelar',
  };
}
