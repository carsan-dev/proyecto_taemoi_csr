import { numero, plural } from '../formato/formato-es';

/**
 * Ocupación de la semana y clases de hoy del Inicio nuevo (8b-2, decisiones 245 y 254). Puro: sin Angular ni HTTP.
 *
 * No hay inscritos por turno: cada turno de un grupo cuenta los alumnos del grupo (`numAlumnos` de
 * `GET /api/grupos/resumen`), así que un grupo con dos días cuenta sus alumnos en los dos. Las plazas son el límite
 * común por turno (`GET /api/turnos/limite`) por el número de turnos de la celda; cuando cada sede tenga su aforo (E9),
 * será la ocupación real.
 */

export interface TurnoOcupacion {
  id: number;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
}

export interface GrupoOcupacion {
  id: number;
  nombre: string;
  deporte?: string;
  numAlumnos: number;
  turnos: TurnoOcupacion[];
}

/** Pasos de la rampa: menos del 25 %, 25-49 %, 50-74 % y 75 % o más. */
export type PasoOcupacion = 1 | 2 | 3 | 4;

export interface DiaOcupacion {
  clave: string;
  corto: string;
  largo: string;
}

export interface CeldaOcupacion {
  dia: string;
  franja: string;
  turnos: number;
  inscritos: number;
  plazas: number;
  /** Redondeado; el paso se calcula con el valor exacto. */
  porcentaje: number;
  /** null: sin clase. */
  paso: PasoOcupacion | null;
  /** «6/36»; vacío sin clase. */
  texto: string;
  /** «Lunes, 17:00: 6 inscritos de 36 plazas (17 %), 1 turno» o «Martes, 09:30: sin clase». */
  descripcion: string;
}

export interface FilaOcupacion {
  franja: string;
  celdas: CeldaOcupacion[];
}

export interface Ocupacion {
  dias: DiaOcupacion[];
  franjas: string[];
  filas: FilaOcupacion[];
  inscritos: number;
  plazas: number;
}

export interface ClaseDeHoy {
  turnoId: number;
  grupoId: number;
  grupoNombre: string;
  deporte: string | null;
  horaInicio: string;
  horaFin: string;
  inscritos: number;
  plazas: number;
  siguiente: boolean;
}

const DIAS: readonly DiaOcupacion[] = [
  { clave: 'lunes', corto: 'Lun', largo: 'Lunes' },
  { clave: 'martes', corto: 'Mar', largo: 'Martes' },
  { clave: 'miercoles', corto: 'Mié', largo: 'Miércoles' },
  { clave: 'jueves', corto: 'Jue', largo: 'Jueves' },
  { clave: 'viernes', corto: 'Vie', largo: 'Viernes' },
  { clave: 'sabado', corto: 'Sáb', largo: 'Sábado' },
  { clave: 'domingo', corto: 'Dom', largo: 'Domingo' },
];

/** `Date.getDay()` (0 = domingo) a la clave del día. */
const CLAVE_POR_GETDAY = ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'];

/** «Miércoles», «MIERCOLES» o una fecha a «miercoles». */
export function claveDia(valor: string | Date): string {
  if (valor instanceof Date) {
    return CLAVE_POR_GETDAY[valor.getDay()];
  }
  return (valor ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim()
    .toLowerCase();
}

/** «9:30» o «17:00:00» a «09:30»; vacío si no es una hora. */
export function normalizarHora(hora: string | null | undefined): string {
  const m = /^(\d{1,2}):(\d{2})/.exec((hora ?? '').trim());
  return m ? `${m[1].padStart(2, '0')}:${m[2]}` : '';
}

function minutos(hora: string): number {
  const [h, m] = hora.split(':').map(Number);
  return h * 60 + m;
}

/** Paso de la rampa por la fracción exacta de inscritos sobre plazas. */
export function pasoDe(inscritos: number, plazas: number): PasoOcupacion {
  const fraccion = plazas > 0 ? inscritos / plazas : 0;
  if (fraccion >= 0.75) {
    return 4;
  }
  if (fraccion >= 0.5) {
    return 3;
  }
  return fraccion >= 0.25 ? 2 : 1;
}

export function calcularOcupacion(grupos: readonly GrupoOcupacion[], limite: number): Ocupacion {
  const plazasPorTurno = Number.isFinite(limite) && limite > 0 ? limite : 0;
  // clave «dia|franja» → turnos e inscritos.
  const acumulado = new Map<string, { turnos: number; inscritos: number }>();
  const franjas = new Set<string>();
  const diasConClase = new Set<string>();
  for (const grupo of grupos ?? []) {
    for (const turno of grupo.turnos ?? []) {
      const dia = claveDia(turno.diaSemana);
      const franja = normalizarHora(turno.horaInicio);
      if (!DIAS.some((d) => d.clave === dia) || !franja) {
        continue;
      }
      franjas.add(franja);
      diasConClase.add(dia);
      const clave = `${dia}|${franja}`;
      const a = acumulado.get(clave) ?? { turnos: 0, inscritos: 0 };
      a.turnos += 1;
      a.inscritos += Math.max(0, grupo.numAlumnos ?? 0);
      acumulado.set(clave, a);
    }
  }

  const dias = DIAS.filter((d) => d.clave !== 'domingo' || diasConClase.has('domingo'));
  const ordenadas = [...franjas].sort((a, b) => minutos(a) - minutos(b));
  let inscritos = 0;
  let plazas = 0;
  const filas = ordenadas.map((franja) => ({
    franja,
    celdas: dias.map((dia) => {
      const a = acumulado.get(`${dia.clave}|${franja}`);
      if (!a) {
        return {
          dia: dia.clave,
          franja,
          turnos: 0,
          inscritos: 0,
          plazas: 0,
          porcentaje: 0,
          paso: null,
          texto: '',
          descripcion: `${dia.largo}, ${franja}: sin clase`,
        } satisfies CeldaOcupacion;
      }
      const plazasCelda = a.turnos * plazasPorTurno;
      inscritos += a.inscritos;
      plazas += plazasCelda;
      const porcentaje = plazasCelda > 0 ? Math.round((a.inscritos / plazasCelda) * 100) : 0;
      return {
        dia: dia.clave,
        franja,
        turnos: a.turnos,
        inscritos: a.inscritos,
        plazas: plazasCelda,
        porcentaje,
        paso: pasoDe(a.inscritos, plazasCelda),
        texto: `${numero(a.inscritos)}/${numero(plazasCelda)}`,
        descripcion:
          `${dia.largo}, ${franja}: ${plural(a.inscritos, 'inscrito', 'inscritos')} de ` +
          `${plural(plazasCelda, 'plaza', 'plazas')} (${numero(porcentaje)} %), ${plural(a.turnos, 'turno', 'turnos')}`,
      } satisfies CeldaOcupacion;
    }),
  }));
  return { dias, franjas: ordenadas, filas, inscritos, plazas };
}

/** Las clases del día de `ahora`, por hora de inicio; la siguiente es la primera que aún no ha empezado. */
export function clasesDeHoy(grupos: readonly GrupoOcupacion[], limite: number, ahora: Date): ClaseDeHoy[] {
  const hoy = claveDia(ahora);
  const plazas = Number.isFinite(limite) && limite > 0 ? limite : 0;
  const clases: ClaseDeHoy[] = [];
  for (const grupo of grupos ?? []) {
    for (const turno of grupo.turnos ?? []) {
      const inicio = normalizarHora(turno.horaInicio);
      if (claveDia(turno.diaSemana) !== hoy || !inicio) {
        continue;
      }
      clases.push({
        turnoId: turno.id,
        grupoId: grupo.id,
        grupoNombre: grupo.nombre,
        deporte: grupo.deporte ?? null,
        horaInicio: inicio,
        horaFin: normalizarHora(turno.horaFin),
        inscritos: Math.max(0, grupo.numAlumnos ?? 0),
        plazas,
        siguiente: false,
      });
    }
  }
  clases.sort((a, b) => minutos(a.horaInicio) - minutos(b.horaInicio) || a.grupoNombre.localeCompare(b.grupoNombre, 'es'));
  const ahoraMin = ahora.getHours() * 60 + ahora.getMinutes();
  const siguiente = clases.find((c) => minutos(c.horaInicio) > ahoraMin);
  if (siguiente) {
    siguiente.siguiente = true;
  }
  return clases;
}
