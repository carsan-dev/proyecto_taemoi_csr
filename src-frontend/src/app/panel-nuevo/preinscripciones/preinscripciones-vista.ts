import { fechaApi } from '../../utils/fecha-api';
import { numero, plural } from '../formato/formato-es';

/**
 * Lo puro de Preinscripciones del panel nuevo (8b-3h, P1 «lista con detalle al lado»): textos de la fila y del detalle,
 * «hace cuánto llegó», la paginación, las temporadas del desplegable y las reglas de hoy para cambiar la asignación de
 * turnos (copiadas de `PreinscripcionesAdminComponent`, con su spec que las compara con las de hoy). Sin Angular.
 */

/** Una solicitud tal como llega del listado o del detalle de la API (`PreinscripcionController.resumen`). */
export type Solicitud = Record<string, any>;

export type EstadoPreinscripcion = 'PENDIENTE' | 'EN_LISTA_ESPERA' | 'FINALIZADA' | 'CANCELADA';

/** Los estados del filtro de hoy, en su orden; el valor es el de la API. */
export const ESTADOS: { valor: EstadoPreinscripcion; etiqueta: string }[] = [
  { valor: 'PENDIENTE', etiqueta: 'Pendiente' },
  { valor: 'EN_LISTA_ESPERA', etiqueta: 'En lista de espera' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizada' },
  { valor: 'CANCELADA', etiqueta: 'Cancelada' },
];

/** Filas por página: las de hoy. */
export const TAMANO_PAGINA = 25;

/** Los filtros de hoy, con sus nombres de parámetro (`PreinscripcionService.listar`). */
export interface Filtros {
  temporada: string;
  deporte: string;
  estado: string;
  q: string;
}

export const FILTROS_VACIOS: Filtros = { temporada: '', deporte: '', estado: '', q: '' };

/** Un estado que la pantalla no conoce sale tal cual: nunca se esconde. */
export function etiquetaEstado(estado: string | null | undefined): string {
  return ESTADOS.find((e) => e.valor === estado)?.etiqueta ?? estado ?? '';
}

/** La pastilla de la lista: el estado y, en lista de espera, su puesto (hoy «LISTA DE ESPERA · 3»). */
export function textoPastilla(s: Solicitud): string {
  const etiqueta = etiquetaEstado(s?.['estado']);
  const puesto = s?.['posicionListaEspera'];
  return s?.['estado'] === 'EN_LISTA_ESPERA' && puesto !== null && puesto !== undefined && puesto !== ''
    ? `${etiqueta} · puesto ${puesto}`
    : etiqueta;
}

export type TonoEstado = 'atencion' | 'espera' | 'bien' | 'neutro';

/** Pendiente en ocre (hay que hacer algo), finalizada en «bien»; lista de espera y cancelada, neutras. Siempre con texto. */
export function tonoEstado(estado: string | null | undefined): TonoEstado {
  switch (estado) {
    case 'PENDIENTE':
      return 'atencion';
    case 'EN_LISTA_ESPERA':
      return 'espera';
    case 'FINALIZADA':
      return 'bien';
    default:
      return 'neutro';
  }
}

const MINUTO = 60_000;
const HORA = 60 * MINUTO;

/** Medianoche local del día de `d`, en ms. */
function medianoche(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/**
 * «Hace cuánto llegó» (añadido en P1): minutos y horas por lo transcurrido; desde un día, por el calendario («Ayer»,
 * «Hace 3 días», semanas, meses de 30 días y años de 365). Vacío sin fecha; una fecha en el futuro (un reloj
 * adelantado) da «Ahora mismo».
 */
export function haceCuanto(valor: unknown, ahora: Date): string {
  const fecha = fechaApi(valor);
  if (!fecha) {
    return '';
  }
  const transcurrido = ahora.getTime() - fecha.getTime();
  if (transcurrido < MINUTO) {
    return 'Ahora mismo';
  }
  if (transcurrido < HORA) {
    return `Hace ${Math.floor(transcurrido / MINUTO)} min`;
  }
  if (transcurrido < 24 * HORA) {
    return `Hace ${Math.floor(transcurrido / HORA)} h`;
  }
  const dias = Math.max(1, Math.round((medianoche(ahora) - medianoche(fecha)) / (24 * HORA)));
  if (dias === 1) {
    return 'Ayer';
  }
  if (dias < 7) {
    return `Hace ${dias} días`;
  }
  if (dias < 30) {
    const semanas = Math.floor(dias / 7);
    return semanas === 1 ? 'Hace 1 semana' : `Hace ${semanas} semanas`;
  }
  if (dias < 365) {
    const meses = Math.floor(dias / 30);
    return meses === 1 ? 'Hace 1 mes' : `Hace ${meses} meses`;
  }
  const anos = Math.floor(dias / 365);
  return anos === 1 ? 'Hace 1 año' : `Hace ${anos} años`;
}

const formatoFechaHora = new Intl.DateTimeFormat('es-ES', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** «20/10/2026, 18:00». Vacío sin fecha. Para las fechas de los avisos y el `title` de la llegada. */
export function fechaHora(valor: unknown): string {
  const fecha = fechaApi(valor);
  return fecha ? formatoFechaHora.format(fecha) : '';
}

export function nombreCompleto(s: Solicitud): string {
  return [s?.['nombre'], s?.['apellidos']].filter((x) => x !== null && x !== undefined && String(x).trim() !== '').join(' ');
}

/** Como hoy en la lista: el DNI o «Sin DNI». */
export function textoDni(s: Solicitud): string {
  return s?.['dni'] || 'Sin DNI';
}

/** Como hoy: cualquier observación no vacía marca la fila «Con observación». */
export function conObservacion(s: Solicitud): boolean {
  return Boolean(s?.['observaciones']);
}

export interface FilaPreinscripcion {
  referencia: string;
  nombre: string;
  dni: string;
  disciplina: string;
  estado: string;
  pastilla: string;
  tono: TonoEstado;
  conObservacion: boolean;
  llegada: string;
  llegadaCompleta: string;
  llegadaIso: string | null;
}

/** La fila de la lista. El orden es el de la API (la más reciente primero): la vista no reordena. */
export function filaLista(s: Solicitud, ahora: Date, disciplina: (codigo: string) => string): FilaPreinscripcion {
  const llegada = fechaApi(s?.['creadaEn']);
  return {
    referencia: s?.['referencia'],
    nombre: nombreCompleto(s),
    dni: textoDni(s),
    disciplina: s?.['deporte'] ? disciplina(s['deporte']) : '',
    estado: s?.['estado'],
    pastilla: textoPastilla(s),
    tono: tonoEstado(s?.['estado']),
    conObservacion: conObservacion(s),
    llegada: haceCuanto(s?.['creadaEn'], ahora),
    llegadaCompleta: fechaHora(s?.['creadaEn']),
    llegadaIso: llegada ? llegada.toISOString() : null,
  };
}

/** Estados de los avisos por correo de la API (`EstadoEmailCambioTurnos`, `…Finalizacion`, `…Cancelacion`). */
const ESTADOS_EMAIL: Record<string, string> = {
  NO_ENVIADO: 'Sin enviar',
  PENDIENTE: 'En cola',
  ENVIADO: 'Enviado',
  ERROR: 'Con error',
};

export function etiquetaEmail(estado: string | null | undefined): string {
  if (estado === null || estado === undefined || estado === '') {
    return 'Sin dato';
  }
  return ESTADOS_EMAIL[estado] ?? estado;
}

/** El email inicial, como hoy en la lista: «Enviado» o «Pendiente». */
export function textoEmailInicial(s: Solicitud): string {
  return s?.['emailEnviado'] ? 'Enviado' : 'Pendiente';
}

export function textoIntentos(intentos: number | null | undefined): string {
  return plural(Number(intentos) || 0, 'intento', 'intentos');
}

/** «1–25 de 60» con la página de la API (desde 0), como el resumen de hoy. */
export function textoRango(pagina: number, tamano: number, total: number): string {
  if (!total) {
    return '0 de 0';
  }
  const primero = pagina * tamano + 1;
  const ultimo = Math.min((pagina + 1) * tamano, total);
  return `${numero(primero)}–${numero(ultimo)} de ${numero(total)}`;
}

const TEMPORADA = /^\d{4}-\d{4}$/;

/** La temporada de una fecha, con la regla de `TemporadaService.paraFecha` (empieza el 30 de junio). */
export function temporadaDe(fecha: Date): string {
  let ano = fecha.getFullYear();
  if (fecha < new Date(ano, 5, 30)) {
    ano--;
  }
  return `${ano}-${ano + 1}`;
}

/**
 * Las temporadas del desplegable (hoy, un campo de texto): la actual y las cuatro anteriores, más las que traen las
 * filas leídas y la elegida, de la más reciente a la más antigua y sin repetir.
 */
export function opcionesTemporada(hoy: Date, vistas: unknown[], elegida: string): string[] {
  const actual = Number(temporadaDe(hoy).slice(0, 4));
  const todas = new Set<string>(Array.from({ length: 5 }, (_, i) => `${actual - i}-${actual - i + 1}`));
  for (const t of [...vistas, elegida]) {
    if (typeof t === 'string' && TEMPORADA.test(t)) {
      todas.add(t);
    }
  }
  return [...todas].sort((a, b) => b.localeCompare(a));
}

// ------------------------------------------------------------------ acciones según el estado (como hoy)

export function puedeCambiarAsignacion(s: Solicitud | null | undefined): boolean {
  return s?.['estado'] === 'PENDIENTE' || s?.['estado'] === 'EN_LISTA_ESPERA';
}

export function puedeReenviarCambio(s: Solicitud | null | undefined): boolean {
  return s?.['estado'] === 'PENDIENTE' && Boolean(s?.['turnosModificadosEn']);
}

export function puedeEliminar(s: Solicitud | null | undefined, esAdmin: boolean): boolean {
  return s?.['estado'] === 'CANCELADA' && esAdmin;
}

// ------------------------------------------------------------------ «Cambiar la asignación» (turnos), como hoy

const DIAS = ['lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo'];

export function normalizarDia(dia: string): string {
  return (dia || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function ordenDia(dia: string): number {
  const orden = DIAS.indexOf(normalizarDia(dia));
  return orden < 0 ? 99 : orden;
}

/** De lunes a domingo y, en cada día, por hora de inicio. */
export function ordenarTurnos<T extends Record<string, any>>(turnos: T[]): T[] {
  return [...turnos].sort(
    (a, b) => ordenDia(a['diaSemana']) - ordenDia(b['diaSemana']) || (a['horaInicio'] || '').localeCompare(b['horaInicio'] || '')
  );
}

/** Marca o desmarca un turno; al marcar, quita el otro del mismo día (un turno por día). */
export function alternarTurno(ids: number[], turno: Record<string, any>, disponibles: Record<string, any>[], marcado: boolean): number[] {
  let nuevos = ids.filter((id) => id !== turno['id']);
  if (marcado) {
    const dia = normalizarDia(turno['diaSemana']);
    const mismoDia = new Set(disponibles.filter((t) => normalizarDia(t['diaSemana']) === dia).map((t) => t['id']));
    nuevos = nuevos.filter((id) => !mismoDia.has(id));
    nuevos.push(turno['id']);
  }
  return nuevos;
}

/** Edad de la cohorte: año de inicio de la temporada menos año de nacimiento. */
function edadCohorte(s: Solicitud): number | null {
  const inicio = Number.parseInt(String(s?.['temporada'] || '').slice(0, 4), 10);
  const nacimiento = Number.parseInt(String(s?.['fechaNacimiento'] || '').slice(0, 4), 10);
  return Number.isInteger(inicio) && Number.isInteger(nacimiento) ? inicio - nacimiento : null;
}

export function turnoFueraEdad(turno: Record<string, any>, s: Solicitud): boolean {
  const edad = edadCohorte(s);
  return edad !== null && (edad < (turno['rangoEdadMin'] ?? 0) || edad > (turno['rangoEdadMax'] ?? 99));
}

export function esAsignacionActual(turno: Record<string, any>, s: Solicitud): boolean {
  return (s?.['turnosSolicitados'] || []).some((actual: any) => actual.id === turno['id']);
}

/** Un turno completo que ya es la plaza de una solicitud pendiente: «Plaza actual» en vez de «Sin plaza». */
export function esPlazaActual(turno: Record<string, any>, s: Solicitud): boolean {
  return !!turno['completo'] && s?.['estado'] === 'PENDIENTE' && esAsignacionActual(turno, s);
}

export function turnosCambiados(s: Solicitud, ids: number[]): boolean {
  const actuales = (s?.['turnosSolicitados'] || []).map((t: any) => t.id).sort((a: number, b: number) => a - b);
  const nuevos = [...ids].sort((a, b) => a - b);
  return actuales.length !== nuevos.length || actuales.some((id: number, i: number) => id !== nuevos[i]);
}

/** Como hoy: hay que forzar si algún turno NUEVO va sin plaza o fuera de la edad recomendada. */
export function necesitaForzar(s: Solicitud, disponibles: Record<string, any>[], ids: number[]): boolean {
  return disponibles
    .filter((t) => ids.includes(t['id']) && !esAsignacionActual(t, s))
    .some((t) => !!t['completo'] || turnoFueraEdad(t, s));
}
