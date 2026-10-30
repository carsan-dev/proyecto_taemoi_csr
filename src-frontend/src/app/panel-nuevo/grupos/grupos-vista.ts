import { GrupoResumen, TurnoDeGrupoResumen } from '../../interfaces/grupo-resumen';
import { plural } from '../formato/formato-es';

/** Orden de la semana y letra de cada día (la «X» del miércoles, como en los horarios de la escuela). */
const DIAS: ReadonlyArray<[string, string]> = [
  ['lunes', 'L'],
  ['martes', 'M'],
  ['miércoles', 'X'],
  ['jueves', 'J'],
  ['viernes', 'V'],
  ['sábado', 'S'],
  ['domingo', 'D'],
];

function claveDia(dia: string | null | undefined): string {
  const d = (dia ?? '').trim().toLowerCase();
  return d === 'miercoles' ? 'miércoles' : d === 'sabado' ? 'sábado' : d;
}

function ordenDia(dia: string): number {
  const i = DIAS.findIndex(([c]) => c === claveDia(dia));
  return i < 0 ? DIAS.length : i;
}

function letraDia(dia: string): string {
  return DIAS.find(([c]) => c === claveDia(dia))?.[1] ?? dia.slice(0, 3);
}

/** «18:00:00» → «18:00»; lo demás, tal cual. */
export function hora(valor: string | null | undefined): string {
  const h = (valor ?? '').trim();
  return /^\d{1,2}:\d{2}:\d{2}$/.test(h) ? h.slice(0, -3) : h;
}

/** «Lunes 18:00–19:00». */
export function textoTurno(t: Pick<TurnoDeGrupoResumen, 'diaSemana' | 'horaInicio' | 'horaFin'>): string {
  return `${t.diaSemana} ${hora(t.horaInicio)}–${hora(t.horaFin)}`;
}

/** Los mismos cuatro casos que hoy («Rango edad»), con la raya de intervalo: «6–12 años». */
export function rangoEdad(min: number | null | undefined, max: number | null | undefined): string {
  const conMin = min !== null && min !== undefined;
  const conMax = max !== null && max !== undefined;
  if (conMin && conMax) {
    return `${min}–${max} años`;
  }
  if (conMin) {
    return `Desde ${min} años`;
  }
  if (conMax) {
    return `Hasta ${max} años`;
  }
  return 'Sin rango';
}

/** «L, X y V»: la lista con la «y» del español. */
function lista(partes: string[]): string {
  return partes.length < 2 ? partes.join('') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`;
}

/**
 * Horario a la vista de la tarjeta: con la misma hora de inicio, los días ordenados por la semana y la hora
 * («L y X · 18:00»); con horas distintas, cuántos turnos («3 turnos»); sin turnos, «Sin turnos». Mi caja lo pinta tras
 * el nombre del grupo, que ya lleva el punto medio, y pasa un espacio de `separador` («L y X 18:00»).
 */
export function resumenHorario(turnos: ReadonlyArray<TurnoDeGrupoResumen>, separador = ' · '): string {
  if (turnos.length === 0) {
    return 'Sin turnos';
  }
  const horas = new Set(turnos.map((t) => hora(t.horaInicio)));
  if (horas.size > 1) {
    return plural(turnos.length, 'turno', 'turnos');
  }
  const dias = [...turnos].sort((a, b) => ordenDia(a.diaSemana) - ordenDia(b.diaSemana)).map((t) => letraDia(t.diaSemana));
  return `${lista(dias)}${separador}${hora(turnos[0].horaInicio)}`;
}

export interface SeccionDisciplina {
  /** Código de la disciplina; vacío para los grupos sin disciplina. */
  codigo: string;
  nombre: string;
  /** «1» a «4» por el orden del catálogo (disc-1 a disc-4); «otra» sin color propio. */
  color: string;
  grupos: GrupoResumen[];
  alumnos: number;
}

/**
 * Los grupos por disciplina: primero cada disciplina activa del catálogo, en su orden (aunque no tenga grupos), después
 * las que no están en el catálogo en el orden en que aparecen y al final «Sin disciplina». Dentro de cada una, los
 * grupos en el orden de hoy (el de la API).
 */
export function seccionesPorDisciplina(
  grupos: ReadonlyArray<GrupoResumen>,
  disciplinas: ReadonlyArray<{ codigo: string; nombre: string }>,
  etiqueta: (codigo: string) => string
): SeccionDisciplina[] {
  const secciones = disciplinas.map<SeccionDisciplina>((d, i) => ({
    codigo: d.codigo,
    nombre: d.nombre,
    color: i < 4 ? String(i + 1) : 'otra',
    grupos: [],
    alumnos: 0,
  }));
  const fuera: SeccionDisciplina[] = [];
  let sinDisciplina: SeccionDisciplina | null = null;
  for (const g of grupos) {
    const codigo = g.deporte ?? '';
    let s = secciones.find((x) => x.codigo === codigo) ?? fuera.find((x) => x.codigo === codigo);
    if (!s && codigo === '') {
      s = sinDisciplina ??= { codigo: '', nombre: 'Sin disciplina', color: 'otra', grupos: [], alumnos: 0 };
    }
    if (!s) {
      s = { codigo, nombre: etiqueta(codigo), color: 'otra', grupos: [], alumnos: 0 };
      fuera.push(s);
    }
    s.grupos.push(g);
    s.alumnos += g.numAlumnos ?? 0;
  }
  return [...secciones, ...fuera, ...(sinDisciplina ? [sinDisciplina] : [])];
}
