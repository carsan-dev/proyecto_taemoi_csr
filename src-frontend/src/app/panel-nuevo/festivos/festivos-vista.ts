import { TipoFestivo } from '../../interfaces/asistencia';
import { fechaClase, fechaClaseCapital, nombreMesMinuscula } from '../asistencia/asistencia-textos';

/** Una sede de un festivo. */
export interface SedeFestivo {
  id: number;
  nombre: string;
}

/** `GET /api/festivos` (decisión 458). Fechas `yyyy-MM-dd`; en el anual, `mes` y `dia` sin año. */
export interface Festivo {
  id: number;
  tipo: TipoFestivo;
  fechaDesde: string | null;
  fechaHasta: string | null;
  mes: number | null;
  dia: number | null;
  /** La siguiente fecha en que cae (o la última, en los pasados). */
  proximaFecha: string | null;
  nombre: string | null;
  sedes: SedeFestivo[];
  todasLasSedes: boolean;
  /** El responsable de sede solo edita los que son solo de sus sedes; el resto, «Lo gestiona la escuela». */
  editable: boolean;
}

/** Cuerpo de `POST /api/festivos`, `PUT /api/festivos/{id}` y `POST /api/festivos/impacto`. */
export interface CuerpoFestivo {
  tipo: TipoFestivo;
  fechaDesde?: string;
  fechaHasta?: string;
  mes?: number;
  dia?: number;
  nombre?: string;
  sedeIds: number[];
}

export interface ClaseConLista {
  sedeId: number;
  sede: string;
  fecha: string;
  horaInicio: string;
  grupo: string;
}

/** Una clase que cubriría el festivo; con lista, `presentes` y `total` de esa lista (si no, null). */
export interface ClaseDeImpacto {
  sedeId: number;
  sede: string;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  grupo: string;
  conLista: boolean;
  presentes: number | null;
  total: number | null;
}

/**
 * `POST /api/festivos/impacto`: clases que cubriría y, de ellas, las que ya tienen lista (se quedan como están). No
 * cuenta las que ya son festivo por otro festivo. `clasesDetalle`: 20 como mucho, por fecha y hora.
 */
export interface ImpactoFestivo {
  clases: number;
  conLista: number;
  conListaDetalle: ClaseConLista[];
  clasesDetalle: ClaseDeImpacto[];
}

export const NOMBRE_MAXIMO = 60;

/** Pastillas del tipo en el panel, en el orden del boceto. */
export const TIPOS_FESTIVO: readonly { valor: TipoFestivo; texto: string }[] = [
  { valor: 'DIA', texto: 'Un día' },
  { valor: 'RANGO', texto: 'De una fecha a otra' },
  { valor: 'ANUAL', texto: 'Cada año la misma fecha' },
];

/** Etiqueta corta del tipo en la fila de la lista. */
export function textoTipo(tipo: TipoFestivo): string {
  return tipo === 'DIA' ? 'Un día' : tipo === 'RANGO' ? 'Varios días' : 'Cada año';
}

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function partes(iso: string): { year: number; month: number; day: number } {
  const [year, month, day] = iso.split('-').map(Number);
  return { year, month, day };
}

/** Fecha de la fila: «Jueves 5 de noviembre», «Del 23 dic al 7 ene» («Del 22 al 26 mar» en el mismo mes) u «8 de diciembre». */
export function fechaDeFila(f: Festivo): string {
  if (f.tipo === 'ANUAL') {
    return `${f.dia} de ${nombreMesMinuscula(f.mes ?? 0)}`;
  }
  if (f.tipo === 'RANGO' && f.fechaDesde && f.fechaHasta) {
    const a = partes(f.fechaDesde);
    const b = partes(f.fechaHasta);
    return a.month === b.month && a.year === b.year
      ? `Del ${a.day} al ${b.day} ${MESES_CORTOS[b.month - 1]}`
      : `Del ${a.day} ${MESES_CORTOS[a.month - 1]} al ${b.day} ${MESES_CORTOS[b.month - 1]}`;
  }
  return f.fechaDesde ? fechaClaseCapital(f.fechaDesde) : '';
}

/** Para frases: «El jueves 5 de noviembre», «Del 23 de diciembre al 7 de enero» o «Cada 8 de diciembre». */
export function fechaEnFrase(f: Festivo): string {
  if (f.tipo === 'ANUAL') {
    return `Cada ${f.dia} de ${nombreMesMinuscula(f.mes ?? 0)}`;
  }
  if (f.tipo === 'RANGO' && f.fechaDesde && f.fechaHasta) {
    const a = partes(f.fechaDesde);
    const b = partes(f.fechaHasta);
    return `Del ${a.day} de ${nombreMesMinuscula(a.month)} al ${b.day} de ${nombreMesMinuscula(b.month)}`;
  }
  return f.fechaDesde ? `El ${fechaClase(f.fechaDesde)}` : '';
}

/** «todas las sedes», «Estepa» o «Estepa y Herrera». */
export function sedesEnFrase(f: Pick<Festivo, 'sedes' | 'todasLasSedes'>): string {
  return f.todasLasSedes ? 'todas las sedes' : enumerar(f.sedes.map((s) => s.nombre));
}

/** «A», «A y B», «A, B y C». */
export function enumerar(textos: readonly string[]): string {
  if (textos.length <= 1) return textos[0] ?? '';
  return `${textos.slice(0, -1).join(', ')} y ${textos[textos.length - 1]}`;
}

export function nombreDeFila(f: Pick<Festivo, 'nombre'>): string {
  return f.nombre?.trim() || 'Festivo sin nombre';
}

/** Días de un rango con los dos extremos incluidos; 0 si falta alguno o está al revés. */
export function diasDeRango(desde: string, hasta: string): number {
  if (!FECHA_ISO.test(desde) || !FECHA_ISO.test(hasta)) return 0;
  const a = partes(desde);
  const b = partes(hasta);
  const dias = Math.round((Date.UTC(b.year, b.month - 1, b.day) - Date.UTC(a.year, a.month - 1, a.day)) / 86_400_000) + 1;
  return dias > 0 ? dias : 0;
}

/** Días del mes del festivo anual: febrero, 29 (un año bisiesto también cuenta). */
export function diasDelMes(mes: number): number {
  return mes === 2 ? 29 : [4, 6, 9, 11].includes(mes) ? 30 : 31;
}

export const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;

/** Lo que se edita en el panel lateral; `mes` y `dia`, texto del `<select>` («» sin elegir). */
export interface FormularioFestivo {
  tipo: TipoFestivo;
  fecha: string;
  desde: string;
  hasta: string;
  dia: string;
  mes: string;
  nombre: string;
  sedeIds: number[];
}

export type CampoFestivo = 'fecha' | 'desde' | 'hasta' | 'dia' | 'mes' | 'nombre' | 'sedes';

export function formularioDe(f: Festivo | null, sedeInicial: number[]): FormularioFestivo {
  if (!f) {
    return { tipo: 'DIA', fecha: '', desde: '', hasta: '', dia: '', mes: '', nombre: '', sedeIds: [...sedeInicial] };
  }
  return {
    tipo: f.tipo,
    fecha: f.tipo === 'DIA' ? (f.fechaDesde ?? '') : '',
    desde: f.tipo === 'RANGO' ? (f.fechaDesde ?? '') : '',
    hasta: f.tipo === 'RANGO' ? (f.fechaHasta ?? '') : '',
    dia: f.tipo === 'ANUAL' && f.dia ? String(f.dia) : '',
    mes: f.tipo === 'ANUAL' && f.mes ? String(f.mes) : '',
    nombre: f.nombre ?? '',
    sedeIds: f.sedes.map((s) => s.id),
  };
}

/** Errores por campo; vacío si se puede enviar. */
export function validarFestivo(f: FormularioFestivo): Partial<Record<CampoFestivo, string>> {
  const errores: Partial<Record<CampoFestivo, string>> = {};
  if (f.tipo === 'DIA' && !FECHA_ISO.test(f.fecha)) {
    errores.fecha = 'Elige el día.';
  }
  if (f.tipo === 'RANGO') {
    if (!FECHA_ISO.test(f.desde)) errores.desde = 'Elige el primer día.';
    if (!FECHA_ISO.test(f.hasta)) errores.hasta = 'Elige el último día.';
    else if (FECHA_ISO.test(f.desde) && f.hasta < f.desde) errores.hasta = 'El último día no puede ser anterior al primero.';
  }
  if (f.tipo === 'ANUAL') {
    const mes = Number(f.mes);
    const dia = Number(f.dia);
    if (!mes) errores.mes = 'Elige el mes.';
    if (!dia) errores.dia = 'Elige el día.';
    else if (mes && dia > diasDelMes(mes)) errores.dia = `${nombreMesMinuscula(mes)} no tiene día ${dia}.`.replace(/^./, (c) => c.toUpperCase());
  }
  if (f.nombre.trim().length > NOMBRE_MAXIMO) {
    errores.nombre = `Como mucho ${NOMBRE_MAXIMO} caracteres.`;
  }
  if (f.sedeIds.length === 0) {
    errores.sedes = 'Elige al menos una sede.';
  }
  return errores;
}

export function cuerpoFestivo(f: FormularioFestivo): CuerpoFestivo {
  const nombre = f.nombre.trim();
  const base: CuerpoFestivo = { tipo: f.tipo, sedeIds: [...f.sedeIds], ...(nombre ? { nombre } : {}) };
  if (f.tipo === 'DIA') return { ...base, fechaDesde: f.fecha, fechaHasta: f.fecha };
  if (f.tipo === 'RANGO') return { ...base, fechaDesde: f.desde, fechaHasta: f.hasta };
  return { ...base, mes: Number(f.mes), dia: Number(f.dia) };
}

/** «Estepa, viernes 30: Iniciación y Avanzados», por sede y día, como mucho tres. */
export function detalleConLista(detalle: readonly ClaseConLista[]): string {
  const grupos = new Map<string, { sede: string; fecha: string; grupos: string[] }>();
  for (const c of detalle) {
    const clave = `${c.sedeId}|${c.fecha}`;
    const g = grupos.get(clave) ?? { sede: c.sede, fecha: c.fecha, grupos: [] };
    g.grupos.push(c.grupo);
    grupos.set(clave, g);
  }
  const lineas = [...grupos.values()].map((g) => {
    const [, , dia] = g.fecha.split('-').map(Number);
    const semana = fechaClase(g.fecha).split(' ')[0];
    return `${g.sede}, ${semana} ${dia}: ${enumerar(g.grupos)}`;
  });
  const resto = lineas.length - 3;
  return resto > 0 ? `${lineas.slice(0, 3).join('; ')} y ${resto} más` : lineas.join('; ');
}

const clasesTexto = (n: number) => `${n} ${n === 1 ? 'clase' : 'clases'}`;

/**
 * Nota de impacto del panel: «Afecta a 9 clases; 2 ya tienen lista y se quedan como están» (y el detalle entre
 * paréntesis), «Afecta a 18 clases; ninguna tiene lista todavía.» o «No afecta a ninguna clase.».
 */
export function textoImpacto(i: Pick<ImpactoFestivo, 'clases' | 'conLista' | 'conListaDetalle'>): { fuerte: string; resto: string } {
  if (i.clases === 0) {
    return { fuerte: 'No afecta a ninguna clase.', resto: '' };
  }
  if (i.conLista === 0) {
    return { fuerte: `Afecta a ${clasesTexto(i.clases)}`, resto: '; ninguna tiene lista todavía.' };
  }
  const ya = i.conLista === 1 ? '1 ya tiene lista y se queda como está' : `${i.conLista} ya tienen lista y se quedan como están`;
  const detalle = detalleConLista(i.conListaDetalle);
  return { fuerte: `Afecta a ${clasesTexto(i.clases)}; ${ya}`, resto: detalle ? ` (${detalle}).` : '.' };
}
