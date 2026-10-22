import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { plural } from '../formato/formato-es';

/**
 * Cálculos puros del listado de alumnos nuevo (8b-3a). Los criterios son los del listado de hoy (principal, licencia,
 * antigüedad y tiempo con el grado); los textos salen con los ayudantes es-ES del panel nuevo.
 */

export type ClaveLicencia = 'con' | 'sin' | 'caducada' | 'no-aplica';

export interface EstadoLicencia {
  clave: ClaveLicencia;
  texto: string;
}

/** Fechas ISO sin hora como día local, para no desplazarlas por la zona horaria. */
function aFecha(valor: Date | string | null | undefined): Date | null {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  if (valor instanceof Date) {
    return Number.isNaN(valor.getTime()) ? null : valor;
  }
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor);
  const fecha = iso ? new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3])) : new Date(valor);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/** Años, meses y días cumplidos entre dos fechas (cero si la primera es posterior). */
function intervalo(desde: Date, hasta: Date): { anios: number; meses: number; dias: number } {
  let anios = hasta.getFullYear() - desde.getFullYear();
  let meses = hasta.getMonth() - desde.getMonth();
  let dias = hasta.getDate() - desde.getDate();
  if (dias < 0) {
    meses -= 1;
    dias += new Date(hasta.getFullYear(), hasta.getMonth(), 0).getDate();
  }
  if (meses < 0) {
    anios -= 1;
    meses += 12;
  }
  if (anios < 0) {
    return { anios: 0, meses: 0, dias: 0 };
  }
  return { anios, meses, dias };
}

function aniosYMeses(anios: number, meses: number): string {
  const a = plural(anios, 'año', 'años');
  const m = plural(meses, 'mes', 'meses');
  if (anios > 0 && meses > 0) {
    return `${a} y ${m}`;
  }
  return anios > 0 ? a : m;
}

/** «A» de «Álvaro» y «Z» de «Zamora Palomares»: «ÁZ». */
export function iniciales(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  const primera = (texto: string | null | undefined) => (texto ?? '').trim().charAt(0).toLocaleUpperCase('es-ES');
  return `${primera(nombre)}${primera(apellidos)}`;
}

/** «2 años», «1 año y 8 meses», «4 meses» o «Menos de 1 mes». Vacío sin fecha. */
export function antiguedad(desde: Date | string | null | undefined, hoy: Date): string {
  const inicio = aFecha(desde);
  if (!inicio) {
    return '';
  }
  const { anios, meses } = intervalo(inicio, hoy);
  return anios === 0 && meses === 0 ? 'Menos de 1 mes' : aniosYMeses(anios, meses);
}

/** Como hoy: años y meses; por debajo del mes, los días; el mismo día, «Desde hoy». Vacío sin fecha. */
export function tiempoConGrado(desde: Date | string | null | undefined, hoy: Date): string {
  const inicio = aFecha(desde);
  if (!inicio) {
    return '';
  }
  const { anios, meses, dias } = intervalo(inicio, hoy);
  if (anios > 0 || meses > 0) {
    return aniosYMeses(anios, meses);
  }
  return dias > 0 ? plural(dias, 'día', 'días') : 'Desde hoy';
}

function fechaOrden(deporte: AlumnoDeporteDTO): number {
  const fecha = aFecha(deporte.fechaAltaInicial || deporte.fechaAlta);
  return fecha ? fecha.getTime() : Number.MAX_SAFE_INTEGER;
}

/** La principal (la marcada o, si no hay, la más antigua), como el listado de hoy. Nula sin deportes. */
export function principalDe(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO | null {
  if (!deportes.length) {
    return null;
  }
  const marcadas = deportes.filter((d) => d.principal === true);
  return [...(marcadas.length ? marcadas : deportes)].sort((a, b) => fechaOrden(a) - fechaOrden(b))[0];
}

/** La principal primero y el resto por fecha de alta, como el listado de hoy. */
export function ordenarDeportes(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO[] {
  const principal = principalDe(deportes);
  return [...deportes].sort((a, b) => {
    if (principal && a.deporte === principal.deporte) {
      return -1;
    }
    if (principal && b.deporte === principal.deporte) {
      return 1;
    }
    return fechaOrden(a) - fechaOrden(b);
  });
}

/**
 * Licencia del deporte principal, con el criterio de hoy: no aplica si la disciplina no la pide; sin licencia; en vigor
 * si su año es el actual o posterior; si no, caducada. Nula sin deporte principal.
 */
export function estadoLicencia(
  principal: AlumnoDeporteDTO | null,
  requiereLicencia: boolean,
  hoy: Date
): EstadoLicencia | null {
  if (!principal) {
    return null;
  }
  if (!requiereLicencia) {
    return { clave: 'no-aplica', texto: 'No aplica' };
  }
  if (!principal.tieneLicencia) {
    return { clave: 'sin', texto: 'Sin licencia' };
  }
  const fecha = aFecha(principal.fechaLicencia);
  return fecha && fecha.getFullYear() >= hoy.getFullYear()
    ? { clave: 'con', texto: 'Con licencia' }
    : { clave: 'caducada', texto: 'Caducada' };
}
