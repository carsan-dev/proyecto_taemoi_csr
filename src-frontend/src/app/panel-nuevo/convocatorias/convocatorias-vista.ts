import { plural } from '../formato/formato-es';

/**
 * Cálculos puros de Convocatorias del panel nuevo (8b-3e). Los criterios son los de la pantalla de hoy
 * (`ListadoConvocatoriasComponent`): cifras con el filtro por `pagado`, 5 convocatorias por página en el navegador, el
 * nombre del PDF y la fecha que se envía al crear; los textos salen con los ayudantes es-ES del panel nuevo.
 */

/** Una convocatoria tal como llega de `GET /convocatorias` (`ConvocatoriaDTO`). */
export interface ConvocatoriaPanel {
  id: number;
  fechaConvocatoria: string | Date;
  deporte: string;
}

/** Un alumno inscrito tal como llega de `GET /convocatorias/{id}/alumnos` (`AlumnoConvocatoriaDTO`). */
export interface AlumnoConvocatoria {
  /** Id de la inscripción (la ruta de `PUT /convocatorias/alumno/{id}`). */
  id: number;
  alumnoId: number;
  nombre: string;
  apellidos: string;
  cuantiaExamen: number | null;
  gradoActual: string;
  gradoSiguiente: string;
  pagado: boolean;
  /** La foto del alumno, con la `url` de `GET /alumnos/{id}/imagen` (la del listado de Alumnos); nula si no tiene. */
  fotoAlumno?: { id?: number | null; nombre?: string | null; url?: string | null } | null;
}

export interface Cifras {
  inscritos: number;
  pagados: number;
  pendientes: number;
}

export type ClaveCifra = keyof Cifras;

/** 5 convocatorias por página, como hoy. */
export const POR_PAGINA = 5;
/** Página de «Cargar más alumnos», como hoy. */
export const TAMANO_PAGINA_ALUMNOS = 10;

/** Las cifras del bloque, con el filtro de hoy: pagados con `pagado === true` y pendientes con `pagado === false`. */
export function cifrasDe(alumnos: ReadonlyArray<AlumnoConvocatoria>): Cifras {
  return {
    inscritos: alumnos.length,
    pagados: alumnos.filter((a) => a.pagado === true).length,
    pendientes: alumnos.filter((a) => a.pagado === false).length,
  };
}

const PALABRAS: Record<ClaveCifra, [string, string]> = {
  inscritos: ['inscrito', 'inscritos'],
  pagados: ['pagado', 'pagados'],
  pendientes: ['pendiente', 'pendientes'],
};

/** «1 inscrito», «12 pagados», «1.500 pendientes». */
export function textoCifra(cantidad: number, clave: ClaveCifra): string {
  const [singular, varios] = PALABRAS[clave];
  return plural(cantidad, singular, varios);
}

/** El nombre y el título del PDF de hoy: `informe_convocatoria_<deporte>_<dd_mm_aaaa>.pdf`. */
export function nombreInforme(conv: ConvocatoriaPanel): { nombreArchivo: string; titulo: string } {
  const fechaStr = new Date(conv.fechaConvocatoria).toLocaleDateString('es-ES').replaceAll('/', '_');
  return {
    nombreArchivo: `informe_convocatoria_${conv.deporte}_${fechaStr}.pdf`,
    titulo: `Convocatoria ${conv.deporte} — ${fechaStr}`,
  };
}

export function totalPaginas(total: number, porPagina: number): number {
  return Math.ceil(total / porPagina);
}

export function paginar<T>(lista: ReadonlyArray<T>, pagina: number, porPagina: number): T[] {
  const inicio = (pagina - 1) * porPagina;
  return lista.slice(inicio, inicio + porPagina);
}

/** Páginas a la vista: la primera, la última y las vecinas de la actual; `null` es un salto («…»). */
export function paginasVisibles(pagina: number, total: number): (number | null)[] {
  const visibles = new Set([1, total, pagina - 1, pagina, pagina + 1].filter((p) => p >= 1 && p <= total));
  const orden = [...visibles].sort((a, b) => a - b);
  const salida: (number | null)[] = [];
  orden.forEach((p, i) => {
    if (i > 0 && p - orden[i - 1] > 1) {
      salida.push(null);
    }
    salida.push(p);
  });
  return salida;
}

/** Las iniciales del avatar: la primera letra del nombre y la de los apellidos. */
export function iniciales(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  const letra = (t: string | null | undefined) => (t ?? '').trim().charAt(0).toLocaleUpperCase('es-ES');
  return `${letra(nombre)}${letra(apellidos)}`;
}

/** El día local en AAAA-MM-DD para el campo de fecha. */
export function diaParaCampo(dia: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${dia.getFullYear()}-${dos(dia.getMonth() + 1)}-${dos(dia.getDate())}`;
}

/** La fecha que se envía hoy al crear: la medianoche local del día elegido (`new Date(fecha + 'T00:00:00')`). */
export function fechaDeCampo(valor: string): Date {
  return new Date(`${valor}T00:00:00`);
}

/** La cuantía del campo en texto es-ES: «27,5», «1.250,00» o «27.5». Vacío, `null`; lo que no es número, `null`. */
export function cuantiaDeCampo(valor: string): number | null {
  const limpio = (valor ?? '').trim().replace(/\s|€/g, '');
  if (!limpio) {
    return null;
  }
  const normal = limpio.includes(',') ? limpio.replace(/\./g, '').replace(',', '.') : limpio;
  if (!/^-?\d+(\.\d+)?$/.test(normal)) {
    return null;
  }
  return Number(normal);
}

/** La cuantía en el campo: «25,00», sin separador de miles para que se edite sin tropiezos. Vacío si no hay. */
export function cuantiaParaCampo(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) {
    return '';
  }
  return valor.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: false });
}

export interface PestanaDisciplina {
  codigo: string;
  nombre: string;
  total: number;
  /** «1» a «4» por el orden de las disciplinas activas del catálogo (disc-1 a disc-4); «otra» sin color propio. */
  color: string;
}

/** Las pestañas: cada disciplina con grados, en su orden, con su número de convocatorias y su color de disciplina. */
export function pestanasDisciplina(
  disciplinas: ReadonlyArray<{ codigo: string; nombre: string }>,
  totales: Record<string, number>,
  activas: ReadonlyArray<string>
): PestanaDisciplina[] {
  return disciplinas.map((d) => {
    const i = activas.indexOf(d.codigo);
    return { codigo: d.codigo, nombre: d.nombre, total: totales[d.codigo] ?? 0, color: i >= 0 && i < 4 ? String(i + 1) : 'otra' };
  });
}
