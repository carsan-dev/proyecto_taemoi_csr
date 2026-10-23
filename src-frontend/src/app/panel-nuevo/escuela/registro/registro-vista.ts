import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../../interfaces/auditoria-evento-detalle';
import { fechaLarga } from '../../formato/formato-es';

export type AccionFiltro = 'TODOS' | 'CREATE' | 'UPDATE' | 'DELETE' | 'READ';

/** Las opciones del filtro «Acción», las de hoy. */
export const ACCIONES: ReadonlyArray<{ value: AccionFiltro; label: string }> = [
  { value: 'TODOS', label: 'Todas' },
  { value: 'CREATE', label: 'Altas' },
  { value: 'UPDATE', label: 'Cambios' },
  { value: 'DELETE', label: 'Bajas' },
  { value: 'READ', label: 'Consultas de salud' },
];

/** El registro solo muestra lo que se hizo con éxito: el backend lo pide con este valor (como hoy). */
export const RESULTADO_EXITO = 'EXITO';
export const MODULO_SALUD = 'salud';
/** Como hoy: 25 por página y relectura cada 30 s. */
export const TAMANO_PAGINA = 25;
export const INTERVALO_ACTUALIZACION_MS = 30000;

export interface AccionVisible {
  etiqueta: string;
  tono: 'alta' | 'cambio' | 'baja' | 'salud' | 'neutro';
}

/** La pastilla de cada acción: la misma correspondencia que `describirAccion` de la pantalla de hoy. */
export function describirAccion(accion: string | null | undefined, modulo: string | null | undefined): AccionVisible {
  switch ((accion ?? '').toUpperCase()) {
    case 'CREATE':
      return { etiqueta: 'Alta', tono: 'alta' };
    case 'UPDATE':
      return { etiqueta: 'Cambio', tono: 'cambio' };
    case 'DELETE':
      return { etiqueta: 'Baja', tono: 'baja' };
    case 'READ':
      return (modulo ?? '').toLowerCase() === MODULO_SALUD
        ? { etiqueta: 'Salud', tono: 'salud' }
        : { etiqueta: 'Consulta', tono: 'neutro' };
    default:
      return { etiqueta: accion || '-', tono: 'neutro' };
  }
}

export interface FiltrosRegistro {
  desde: string | null;
  hasta: string | null;
  accion: AccionFiltro;
  modulo: string;
  usuario: string;
  texto: string;
}

export function filtrosVacios(): FiltrosRegistro {
  return { desde: null, hasta: null, accion: 'TODOS', modulo: 'TODOS', usuario: '', texto: '' };
}

/** «Consultas de salud» son lecturas del módulo salud: el módulo queda fijo (como hoy). */
export function moduloParaConsulta(f: Pick<FiltrosRegistro, 'accion' | 'modulo'>): string | null {
  if (f.accion === 'READ') {
    return MODULO_SALUD;
  }
  return f.modulo === 'TODOS' ? null : f.modulo;
}

/** El primer argumento de `obtenerAuditoriaEventos`, igual que lo arma hoy `cargarEventos`. */
export function filtrosParaConsulta(f: FiltrosRegistro): {
  desde: string | null;
  hasta: string | null;
  resultado: string;
  accion: string | null;
  modulo: string | null;
  usuario: string | null;
  texto: string | null;
} {
  return {
    desde: f.desde,
    hasta: f.hasta,
    resultado: RESULTADO_EXITO,
    accion: f.accion === 'TODOS' ? null : f.accion,
    modulo: moduloParaConsulta(f),
    usuario: f.usuario || null,
    texto: f.texto || null,
  };
}

/** Cuántos filtros cambian la consulta (el número del botón «Filtros»). El módulo fijo de salud no cuenta aparte. */
export function numeroFiltrosActivos(f: FiltrosRegistro): number {
  return [
    !!f.desde,
    !!f.hasta,
    f.accion !== 'TODOS',
    f.accion !== 'READ' && f.modulo !== 'TODOS',
    !!f.usuario.trim(),
    !!f.texto.trim(),
  ].filter(Boolean).length;
}

/** «Cuenta 12»: el identificador de la cuenta, nunca su nombre ni su email. */
export function textoCuenta(usuarioId: number | null | undefined): string {
  return usuarioId === null || usuarioId === undefined ? 'Sin cuenta' : `Cuenta ${usuarioId}`;
}

function aFecha(valor: string | Date | null | undefined): Date | null {
  if (!valor) {
    return null;
  }
  const f = valor instanceof Date ? valor : new Date(valor);
  return Number.isNaN(f.getTime()) ? null : f;
}

const dos = (n: number): string => String(n).padStart(2, '0');

function claveDia(f: Date): string {
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}`;
}

/** «10:42». */
export function hora(valor: string | Date | null | undefined): string {
  const f = aFecha(valor);
  return f ? `${dos(f.getHours())}:${dos(f.getMinutes())}` : '-';
}

/** «23/10/2026 10:42:07»: la fecha del detalle de hoy (`date:'dd/MM/yyyy HH:mm:ss'`). */
export function fechaCompleta(valor: string | Date | null | undefined): string {
  const f = aFecha(valor);
  return f
    ? `${dos(f.getDate())}/${dos(f.getMonth() + 1)}/${f.getFullYear()} ${dos(f.getHours())}:${dos(f.getMinutes())}:${dos(f.getSeconds())}`
    : '';
}

export interface DiaRegistro {
  clave: string;
  /** «Hoy», «Ayer» o la fecha larga. */
  titulo: string;
  /** Con «Hoy» y «Ayer», la fecha larga; si no, vacío. */
  fecha: string;
  eventos: AuditoriaEvento[];
}

/** Agrupa por día local, en el orden en que llegan (la API los da del más reciente al más antiguo). */
export function agruparPorDia(eventos: readonly AuditoriaEvento[], ahora: Date = new Date()): DiaRegistro[] {
  const hoy = claveDia(ahora);
  const ayerFecha = new Date(ahora);
  ayerFecha.setDate(ahora.getDate() - 1);
  const ayer = claveDia(ayerFecha);
  const dias: DiaRegistro[] = [];
  for (const evento of eventos) {
    const f = aFecha(evento.fechaEvento);
    const clave = f ? claveDia(f) : 'sin-fecha';
    let dia = dias.find((d) => d.clave === clave);
    if (!dia) {
      const larga = f ? fechaLarga(f) : '';
      const titulo = clave === hoy ? 'Hoy' : clave === ayer ? 'Ayer' : larga || 'Sin fecha';
      dia = { clave, titulo, fecha: clave === hoy || clave === ayer ? larga : '', eventos: [] };
      dias.push(dia);
    }
    dia.eventos.push(evento);
  }
  return dias;
}

/** Igual que hoy: solo con query params o payload hay datos técnicos que enseñar. */
export function tieneDatosTecnicos(detalle: AuditoriaEventoDetalle | null): boolean {
  return !!detalle && (detalle.payloadJson != null || detalle.queryParamsJson != null);
}

/** Igual que hoy: el JSON con sangría; si no es JSON, tal cual; vacío, «-». */
export function formatearJson(valor: string | null | undefined): string {
  if (!valor) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(valor), null, 2);
  } catch {
    return valor;
  }
}
