import { eur } from '../formato/formato-es';

/** Los estados del filtro de hoy («Estado»), que las pestañas sustituyen (8b-3d). */
export type EstadoFiltro = 'TODOS' | 'PENDIENTES' | 'PAGADOS';

/** Pestañas Pendientes | Cobrados | Todos (DESIGN.md), con el estado de hoy que corresponde a cada una. */
export const PESTANAS: ReadonlyArray<{ estado: EstadoFiltro; texto: string; clave: string }> = [
  { estado: 'PENDIENTES', texto: 'Pendientes', clave: 'pendientes' },
  { estado: 'PAGADOS', texto: 'Cobrados', clave: 'cobrados' },
  { estado: 'TODOS', texto: 'Todos', clave: 'todos' },
];

export const MESES: ReadonlyArray<{ value: number; label: string }> = [
  { value: 1, label: 'Enero' },
  { value: 2, label: 'Febrero' },
  { value: 3, label: 'Marzo' },
  { value: 4, label: 'Abril' },
  { value: 5, label: 'Mayo' },
  { value: 6, label: 'Junio' },
  { value: 7, label: 'Julio' },
  { value: 8, label: 'Agosto' },
  { value: 9, label: 'Septiembre' },
  { value: 10, label: 'Octubre' },
  { value: 11, label: 'Noviembre' },
  { value: 12, label: 'Diciembre' },
];

/** El `pagado` que la pantalla de hoy manda a la API por cada estado (`convertirEstadoAPagado`). */
export function pagadoDeEstado(estado: EstadoFiltro): boolean | undefined {
  switch (estado) {
    case 'PENDIENTES':
      return false;
    case 'PAGADOS':
      return true;
    default:
      return undefined;
  }
}

export interface FiltrosExportacion {
  mes: number | null;
  ano: number | null;
  deporte: string;
  estado: EstadoFiltro;
  soloActivos: boolean;
}

/** El nombre de archivo de hoy (`obtenerNombreCSV` / `obtenerNombrePDF`). */
export function nombreExportacion(f: FiltrosExportacion, formato: 'csv' | 'pdf'): string {
  const anoSegment = f.ano === null ? 'total' : f.ano.toString();
  const mesSegment = f.mes === null ? 'todos_meses' : f.mes.toString().padStart(2, '0');
  const estadoSegment = f.estado.toLowerCase();
  const deporteSegment = f.deporte.toLowerCase();
  const alumnosSegment = f.soloActivos ? 'activos' : 'todos_alumnos';
  return `tesoreria_${anoSegment}_${mesSegment}_${deporteSegment}_${estadoSegment}_${alumnosSegment}.${formato}`;
}

/** «Totales (todos los años)», «Año 2026» u «Octubre 2026», como hoy (`getPeriodoLabel`). */
export function etiquetaPeriodo(mes: number | null, ano: number | null): string {
  if (ano === null) {
    return 'Totales (todos los años)';
  }
  if (mes === null) {
    return `Año ${ano}`;
  }
  return `${MESES.find((m) => m.value === mes)?.label ?? '-'} ${ano}`;
}

const dos = (n: number) => String(n).padStart(2, '0');

/** «22/10/2026 10:00» (el `date:'dd/MM/yyyy HH:mm'` de hoy). */
export function fechaHora(d: Date): string {
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()} ${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

/** El valor inicial de la fecha de pago de hoy (`editarFechaPago`): la del pago en ISO, o la de hoy. */
export function fechaPagoParaCampo(fechaPago: string | Date | null | undefined): string {
  const actual = fechaPago ? new Date(fechaPago) : new Date();
  return Number.isNaN(actual.getTime()) ? new Date().toISOString().slice(0, 10) : actual.toISOString().slice(0, 10);
}

export interface BarraCobro {
  /** Ancho de cada parte en %; suman 100 salvo sin importes. */
  cobrado: number;
  pendiente: number;
  vacia: boolean;
  /** Texto equivalente de la barra (dataviz: nunca solo el color). */
  texto: string;
}

/** Un % entero; con una décima si el entero borraría una parte que existe: se muestra entre 0,1 % y 99,9 %. */
function porcentaje(parte: number, total: number): number {
  const p = (parte / total) * 100;
  if (p > 0 && p < 100) {
    if (p < 1 || p > 99) {
      return Math.min(Math.max(Math.round(p * 10) / 10, 0.1), 99.9);
    }
    return Math.round(p);
  }
  return Math.round(p);
}

export function textoPorcentaje(p: number): string {
  return `${String(p).replace('.', ',')} %`;
}

/** La barra apilada cobrado / pendiente del resumen. */
export function barraCobro(cobrado: number, pendiente: number): BarraCobro {
  const c = Math.max(Number(cobrado) || 0, 0);
  const p = Math.max(Number(pendiente) || 0, 0);
  const total = c + p;
  if (total === 0) {
    return { cobrado: 0, pendiente: 0, vacia: true, texto: 'Sin importes en el periodo' };
  }
  const pc = porcentaje(c, total);
  const pp = Math.round((100 - pc) * 10) / 10;
  return {
    cobrado: pc,
    pendiente: pp,
    vacia: false,
    texto: `Cobrado ${eur(c)} (${textoPorcentaje(pc)}), pendiente ${eur(p)} (${textoPorcentaje(pp)})`,
  };
}
