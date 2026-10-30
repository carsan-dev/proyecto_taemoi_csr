import { ClaseAsistencia } from '../../interfaces/asistencia';

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const DIAS_CORTOS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

interface Partes { year: number; month: number; day: number; diaSemana: number }

/** `yyyy-MM-dd` sin pasar por zona horaria (es una fecha de calendario). */
function partes(iso: string): Partes {
  const [year, month, day] = iso.split('-').map(Number);
  return { year, month, day, diaSemana: new Date(year, month - 1, day).getDay() };
}

function mayuscula(texto: string): string {
  return texto.charAt(0).toLocaleUpperCase('es-ES') + texto.slice(1);
}

/** «miércoles 28 de octubre». */
export function fechaClase(iso: string): string {
  const p = partes(iso);
  return `${DIAS[p.diaSemana]} ${p.day} de ${MESES[p.month - 1]}`;
}

/** «Miércoles 28 de octubre». */
export function fechaClaseCapital(iso: string): string {
  return mayuscula(fechaClase(iso));
}

/** «miércoles» (para «Hoy, miércoles»). */
export function diaSemana(iso: string): string {
  return DIAS[partes(iso).diaSemana];
}

/** «lun 5». */
export function diaCorto(iso: string): string {
  const p = partes(iso);
  return `${DIAS_CORTOS[p.diaSemana]} ${p.day}`;
}

/** «lunes 5» (para el lector de pantalla). */
export function diaLargo(iso: string): string {
  const p = partes(iso);
  return `${mayuscula(DIAS[p.diaSemana])} ${p.day}`;
}

/** «18:00–19:00». */
export function franja(clase: Pick<ClaseAsistencia, 'horaInicio' | 'horaFin'>): string {
  return `${clase.horaInicio}–${clase.horaFin}`;
}

/** Una lista pasada es la que tiene `presentes`; `pasada` solo dice que la clase ya terminó. */
export function tieneLista(clase: ClaseAsistencia): boolean {
  return clase.presentes !== null;
}

/** «12 de 18», con el tamaño de ESA lista. */
export function cuenta(presentes: number, total: number): string {
  return `${presentes} de ${total}`;
}

/** Estado de una clase en la hoja «Elegir clase»: «Sin pasar» o «12 de 18». */
export function estadoClase(clase: ClaseAsistencia): string {
  return clase.presentes === null ? 'Sin pasar' : cuenta(clase.presentes, clase.total ?? clase.alumnos);
}

/** La misma clase (turno y fecha). */
export function mismaClase(a: Pick<ClaseAsistencia, 'turnoId' | 'fecha'>, b: Pick<ClaseAsistencia, 'turnoId' | 'fecha'>): boolean {
  return a.turnoId === b.turnoId && a.fecha === b.fecha;
}

/** Nombre del mes en minúscula, para «Asistencia en octubre». */
export function nombreMesMinuscula(month: number): string {
  return MESES[month - 1] ?? '';
}
