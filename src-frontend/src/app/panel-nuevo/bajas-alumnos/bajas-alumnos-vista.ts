import { fotoDe, iniciales } from '../alumnos/alumno-vista';
import { eur, numero, plural } from '../formato/formato-es';

/**
 * Lógica pura de «Bajas y eliminaciones» (8b-3l, P1 de la decisión 285): las filas, el estado guardado (la MISMA clave y
 * forma que la pantalla de hoy) y las frases de los diálogos (recuento, aviso de pagos y cargos de P-3, progreso y
 * resumen final).
 */

/** 10 por página, como hoy. */
export const POR_PAGINA = 10;

/** La clave de sessionStorage de hoy: las dos pantallas recuerdan lo mismo. */
export const CLAVE_ESTADO = 'eliminarAlumnoEstado';

export interface EstadoBajas {
  paginaActual: number;
  tamanoPagina: number;
  nombreFiltro: string;
  mostrarInactivos: boolean;
}

/** El estado guardado por cualquiera de las dos pantallas; si no hay o no se lee, el de por defecto. */
export function leerEstado(texto: string | null): EstadoBajas {
  const defecto: EstadoBajas = { paginaActual: 1, tamanoPagina: POR_PAGINA, nombreFiltro: '', mostrarInactivos: false };
  if (!texto) {
    return defecto;
  }
  try {
    const e = JSON.parse(texto);
    return {
      paginaActual: Number.isInteger(e?.paginaActual) && e.paginaActual > 0 ? e.paginaActual : 1,
      tamanoPagina: POR_PAGINA,
      nombreFiltro: typeof e?.nombreFiltro === 'string' ? e.nombreFiltro : '',
      mostrarInactivos: e?.mostrarInactivos === true,
    };
  } catch {
    return defecto;
  }
}

export interface FilaBaja {
  id: number;
  nombre: string;
  apellidos: string;
  nombreCompleto: string;
  iniciales: string;
  foto: string | null;
  expediente: string;
  dni: string;
  /** El `activo` del alumno, como hoy (sin dato, activo). */
  activo: boolean;
}

export function filaBaja(a: any): FilaBaja {
  const nombre = (a?.nombre ?? '').trim();
  const apellidos = (a?.apellidos ?? '').trim();
  return {
    id: a?.id,
    nombre,
    apellidos,
    nombreCompleto: [nombre, apellidos].filter(Boolean).join(' '),
    iniciales: iniciales(nombre, apellidos),
    foto: fotoDe(a),
    expediente: a?.numeroExpediente === null || a?.numeroExpediente === undefined ? '' : String(a.numeroExpediente),
    dni: (a?.nif ?? '').trim(),
    activo: a?.activo ?? true,
  };
}

/** «Exp. 1042 · DNI 12345678Z» (sin los que falten). */
export function lineaDatos(f: FilaBaja): string {
  return [f.expediente ? `Exp. ${f.expediente}` : '', f.dni ? `DNI ${f.dni}` : ''].filter(Boolean).join(' · ');
}

/** `GET /api/alumnos/cargos-resumen` (8b-3l, P-3). */
export interface ResumenCargos {
  alumnoId: number;
  cargos: number;
  pagados: number;
  pendientes: number;
  importePagado: number;
  importePendiente: number;
  /**
   * 8b-3l bis (decisión 287): cuántos pagos tiene (en cualquier estado). Con alguno no se elimina definitivamente: se
   * elimina anonimizando.
   */
  pagos?: number;
}

/** 8b-3l bis: con algún pago, el alumno se elimina anonimizando (el servidor responde 409 al borrado definitivo). */
export function tienePagos(r: ResumenCargos | undefined): boolean {
  return (r?.pagos ?? 0) > 0;
}

/** «se elimina» / «se anonimiza (tiene pagos)»: lo que le pasa a cada alumno en el diálogo de varios. */
export function destinoAlumno(anonimiza: boolean): string {
  return anonimiza ? 'se anonimiza (tiene pagos)' : 'se elimina';
}

/** «¿Eliminar anonimizando a Lucía Ortega?». */
export function tituloAnonimizar(nombre: string): string {
  return `¿Eliminar anonimizando a ${nombre}?`;
}

/**
 * La frase de consecuencia de eliminar anonimizando a un alumno: «Sus datos personales se borran para siempre. Sus 3
 * pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.».
 */
export function mensajeAnonimizar(pagos: number): string {
  const suyos = pagos === 1 ? 'Su pago se queda' : `Sus ${numero(pagos)} pagos se quedan`;
  return `Sus datos personales se borran para siempre. ${suyos} en Tesorería como «Alumno eliminado». No se puede deshacer.`;
}

/** Varios, con alguno con pagos: la frase de consecuencia del diálogo. */
export function mensajeLoteMixto(anonimizados: number, total: number): string {
  if (anonimizados === total) {
    return 'Sus datos personales se borran para siempre. Sus pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.';
  }
  return 'Los que no tienen pagos se borran para siempre. De los que tienen pagos se borran sus datos personales y sus pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.';
}

/** «3 alumnos» o «1 alumno». */
export function alumnos(n: number): string {
  return plural(n, 'alumno', 'alumnos');
}

/** «4 cargos» de un alumno, para su línea del diálogo; vacío sin resumen. */
export function cargosDe(r: ResumenCargos | undefined): string {
  if (!r) {
    return '';
  }
  return r.cargos === 0 ? 'Sin cargos' : plural(r.cargos, 'cargo', 'cargos');
}

/**
 * P-3: la línea de pagos y cargos del diálogo destructivo. Uno: «Tiene 14 cargos: 12 pagados (360,00 €) y 2 pendientes
 * (60,00 €). Se borran con el alumno.»; varios: «Entre los 3 tienen…». Sin cargos: «No tiene pagos ni cargos.».
 */
export function fraseCargos(
  resumenes: ResumenCargos[],
  numAlumnos: number,
  destino: 'borran' | 'conservan' | 'mixto' = 'borran'
): string {
  const suma = resumenes.reduce(
    (t, r) => ({
      cargos: t.cargos + (r.cargos || 0),
      pagados: t.pagados + (r.pagados || 0),
      pendientes: t.pendientes + (r.pendientes || 0),
      importePagado: t.importePagado + Number(r.importePagado || 0),
      importePendiente: t.importePendiente + Number(r.importePendiente || 0),
    }),
    { cargos: 0, pagados: 0, pendientes: 0, importePagado: 0, importePendiente: 0 }
  );
  const uno = numAlumnos === 1;
  if (suma.cargos === 0) {
    return uno ? 'No tiene pagos ni cargos.' : 'Ninguno tiene pagos ni cargos.';
  }
  const partes: string[] = [];
  if (suma.pagados > 0) {
    partes.push(`${plural(suma.pagados, 'pagado', 'pagados')} (${eur(suma.importePagado)})`);
  }
  if (suma.pendientes > 0) {
    partes.push(`${plural(suma.pendientes, 'pendiente', 'pendientes')} (${eur(suma.importePendiente)})`);
  }
  const quien = uno ? 'Tiene' : `Entre los ${numero(numAlumnos)} tienen`;
  const inicio = `${quien} ${plural(suma.cargos, 'cargo', 'cargos')}: ${partes.join(' y ')}.`;
  // 8b-3l bis: los cargos de un alumno con pagos se conservan (se elimina anonimizando).
  if (destino === 'conservan') {
    return `${inicio} ${suma.cargos === 1 ? 'Se conserva' : 'Se conservan'}.`;
  }
  if (destino === 'mixto') {
    return `${inicio} Los de los alumnos con pagos se conservan; los demás se borran con ellos.`;
  }
  const verbo = suma.cargos === 1 ? 'Se borra' : 'Se borran';
  const con = uno ? 'con el alumno' : 'con ellos';
  return `${inicio} ${verbo} ${con}.`;
}

export type TipoLote = 'baja' | 'eliminar';

/** «¿Eliminar a 3 alumnos?», «¿Eliminar a Lucía Ortega?», «¿Dar de baja a 3 alumnos?». */
export function tituloLote(tipo: TipoLote, nombres: string[]): string {
  const quien = nombres.length === 1 ? nombres[0] : alumnos(nombres.length);
  return tipo === 'eliminar' ? `¿Eliminar a ${quien}?` : `¿Dar de baja a ${quien}?`;
}

/** La frase de consecuencia: la de hoy para la baja y la del boceto para el borrado. */
export function mensajeLote(tipo: TipoLote, n: number): string {
  if (tipo === 'eliminar') {
    return n === 1 ? 'Se borra para siempre. No se puede deshacer.' : 'Se borran para siempre. No se puede deshacer.';
  }
  return n === 1 ? 'El alumno será dado de baja.' : 'Los alumnos seleccionados serán dados de baja.';
}

/** «Eliminar 3 alumnos» / «Eliminar definitivamente» / «Dar de baja a 3 alumnos» / «Dar de baja». */
export function accionLote(tipo: TipoLote, n: number): string {
  if (tipo === 'eliminar') {
    return n === 1 ? 'Eliminar definitivamente' : `Eliminar ${alumnos(n)}`;
  }
  return n === 1 ? 'Dar de baja' : `Dar de baja a ${alumnos(n)}`;
}

/** «Eliminando 2 de 3…» / «Dando de baja 1 de 1…». */
export function progresoLote(tipo: TipoLote, actual: number, total: number): string {
  return `${tipo === 'eliminar' ? 'Eliminando' : 'Dando de baja'} ${numero(actual)} de ${numero(total)}…`;
}

/** Al terminar con fallos: «No se pudo eliminar a 1 de 3». */
export function tituloFallos(tipo: TipoLote, fallos: number, total: number): string {
  const verbo = tipo === 'eliminar' ? 'eliminar' : 'dar de baja';
  return total === 1 ? `No se pudo ${verbo} al alumno` : `No se pudo ${verbo} a ${numero(fallos)} de ${numero(total)}`;
}

/** El aviso de éxito al terminar sin fallos (los textos de hoy; 8b-3l bis: uno anonimizado, el suyo). */
export function exitoLote(tipo: TipoLote, n: number, anonimizados = 0): string {
  if (tipo === 'eliminar' && n === 1 && anonimizados === 1) {
    return 'El alumno ha sido eliminado anonimizando. Sus pagos siguen en Tesorería.';
  }
  if (tipo === 'eliminar') {
    return n === 1 ? 'El alumno ha sido eliminado correctamente.' : 'Los alumnos seleccionados han sido eliminados correctamente.';
  }
  return n === 1 ? 'El alumno ha sido dado de baja correctamente.' : 'Los alumnos seleccionados han sido dados de baja correctamente.';
}

/** El texto de hoy del fallo de un alumno; con el motivo del servidor si lo da. */
export function motivoFallo(tipo: TipoLote, error: any): string {
  const cuerpo = error?.error;
  const servidor =
    typeof cuerpo === 'string' && cuerpo.trim()
      ? cuerpo.trim()
      : typeof cuerpo?.mensaje === 'string' && cuerpo.mensaje.trim()
        ? cuerpo.mensaje.trim()
        : '';
  if (servidor) {
    return servidor;
  }
  return tipo === 'eliminar'
    ? 'Ha ocurrido un error al intentar eliminar al alumno.'
    : 'Ha ocurrido un error al intentar dar de baja al alumno.';
}
