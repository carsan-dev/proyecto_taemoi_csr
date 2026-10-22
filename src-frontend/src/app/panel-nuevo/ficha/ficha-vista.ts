import { PendienteAlumno } from '../alumnos/pendientes-alumno.service';
import { fecha, plural } from '../formato/formato-es';

/**
 * Cálculos puros del carnet de la ficha nueva (8b-3b). Los textos salen con los ayudantes es-ES del panel nuevo.
 */

/** Luminancia relativa de WCAG 2.x de un color «#rrggbb» o «#rgb». */
function luminancia(color: string): number {
  let hex = color.trim().replace(/^#/, '');
  if (hex.length === 3) {
    hex = hex.split('').map((c) => c + c).join('');
  }
  const canales = [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = canales.map((c) => (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Razón de contraste de WCAG entre dos colores (1 a 21), sea cual sea el orden. */
export function contraste(a: string, b: string): number {
  const la = luminancia(a);
  const lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * «3 cargos, el más antiguo vencido desde 06/09/2026»; con uno, «1 cargo, vencido desde 06/10/2026». Sin detalle,
 * solo los cargos.
 */
export function resumenPendiente(pendiente: PendienteAlumno): string {
  const cargos = plural(pendiente.cargos, 'cargo', 'cargos');
  const fechas = (pendiente.detalle ?? []).map((c) => c.vencidoDesde).filter(Boolean).sort();
  if (fechas.length === 0) {
    return cargos;
  }
  const desde = fecha(fechas[0]);
  return pendiente.cargos === 1 ? `${cargos}, vencido desde ${desde}` : `${cargos}, el más antiguo vencido desde ${desde}`;
}

/** «BLANCO, 3 años y 6 meses»; el mismo día, «NARANJA, desde hoy». */
export function lineaGrado(grado: string, tiempo: string): string {
  if (!grado) {
    return '';
  }
  if (!tiempo) {
    return grado;
  }
  return `${grado}, ${tiempo === 'Desde hoy' ? 'desde hoy' : tiempo}`;
}

/**
 * Valor de un `<input type="date">` («yyyy-MM-dd» en la hora local), como el `date: 'yyyy-MM-dd'` de la ficha de hoy:
 * admite la fecha ISO, el instante en milisegundos que manda la API o un `Date`. Vacío si no hay fecha válida.
 */
export function fechaParaCampo(valor: unknown): string {
  if (valor === null || valor === undefined || valor === '') {
    return '';
  }
  if (typeof valor === 'string') {
    const iso = /^(\d{4}-\d{2}-\d{2})$/.exec(valor);
    if (iso) {
      return iso[1];
    }
  }
  const f = valor instanceof Date ? valor : new Date(valor as string | number);
  if (Number.isNaN(f.getTime())) {
    return '';
  }
  const mm = String(f.getMonth() + 1).padStart(2, '0');
  const dd = String(f.getDate()).padStart(2, '0');
  return `${f.getFullYear()}-${mm}-${dd}`;
}
