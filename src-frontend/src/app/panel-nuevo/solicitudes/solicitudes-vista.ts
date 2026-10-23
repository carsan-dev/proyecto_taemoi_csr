import { EstadoCobroCargo, EstadoSolicitud, SegmentoSolicitudes, Solicitud, TipoSolicitud } from '../../interfaces/solicitud';
import { fechaApi } from '../../utils/fecha-api';
import { eur, fecha } from '../formato/formato-es';

/**
 * Reglas puras de Solicitudes del panel nuevo (8b-3i, P1 «acciones en la fila»). Textos, criterios por rol y
 * confirmaciones copiados de `SolicitudesBandejaComponent` (6c-7 y 6c-9): la pantalla nueva no decide nada distinto,
 * solo lo pinta en el lenguaje de C. Sin dependencias de Angular para probarlas sin la pantalla.
 */

/** Los tres segmentos de hoy, en su orden. */
export const SEGMENTOS: SegmentoSolicitudes[] = ['PENDIENTES', 'ENCARGOS', 'RESUELTAS'];

const ETIQUETA_SEGMENTO: Record<SegmentoSolicitudes, string> = {
  PENDIENTES: 'Pendientes',
  ENCARGOS: 'Encargos en curso',
  RESUELTAS: 'Resueltas',
};

const ETIQUETA_TIPO: Record<TipoSolicitud, string> = {
  REVERTIR_COBRO: 'Revertir cobro',
  DESHACER_COBRO_ENTREGADO: 'Deshacer cobro entregado',
  ANULAR_ENTREGA: 'Anular entrega',
  ANULAR_MOVIMIENTO: 'Anular movimiento',
  ENCARGO: 'Encargo',
};

const ETIQUETA_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADA: 'Aceptada',
  DENEGADA: 'Denegada',
  RETIRADA: 'Retirada',
  NO_APLICABLE: 'No aplicable',
  PEDIDO: 'Pedido',
  ENCARGADO: 'Encargado',
  RECIBIDO: 'Recibido',
  ENTREGADO: 'Entregado',
  RECHAZADO: 'Rechazado',
};

/** Hoy en `mi-caja-pedidos.ts` (`ETIQUETA_COBRO`); copiado para no arrastrar Mi caja a este chunk. */
const ETIQUETA_COBRO: Record<EstadoCobroCargo, string> = {
  PENDIENTE: 'Sin cobrar',
  COBRADO: 'Cobrado',
  DEVUELTO: 'Devuelto',
  CANCELADO: 'Cargo cancelado',
};

/** Pasos del recorrido de un encargo (hoy `PASOS_ENCARGO`). */
const PASOS: ReadonlyArray<{ estado: EstadoSolicitud; etiqueta: string }> = [
  { estado: 'PEDIDO', etiqueta: 'Pedido' },
  { estado: 'ENCARGADO', etiqueta: 'Encargado' },
  { estado: 'RECIBIDO', etiqueta: 'Recibido' },
  { estado: 'ENTREGADO', etiqueta: 'Entregado' },
];

/** Motivo de un rechazo, respuesta de una denegación y nota de una devolución (D6c-1, 6c-6; hoy `MAX_MOTIVO`). */
export const MAX_MOTIVO = 500;

/** 403 al registrar la devolución: el cobro es de otra sede (nota F2 de la 6c-5), con el texto de hoy. */
export const DEVOLUCION_OTRA_SEDE =
  'El cobro de este encargo es de otra sede: la devolución la registra la gestión o el propietario.';

/** Verbo de «Aceptar y …», título de la confirmación, qué pasa y el aviso al terminar, por tipo (como hoy). */
export const ACEPTAR: Record<Exclude<TipoSolicitud, 'ENCARGO'>, { verbo: string; titulo: string; efecto: string; hecho: string }> = {
  REVERTIR_COBRO: {
    verbo: 'Aceptar y revertir',
    titulo: 'Aceptar y revertir el cobro',
    efecto: 'El cargo pasará a pendiente, como al revertirlo desde la tesorería.',
    hecho: 'Cobro revertido',
  },
  DESHACER_COBRO_ENTREGADO: {
    verbo: 'Aceptar y deshacer',
    titulo: 'Aceptar y deshacer el cobro',
    efecto: 'El cobro se anula y su entrega apunta la corrección; la entrega sigue en pie.',
    hecho: 'Cobro deshecho',
  },
  ANULAR_ENTREGA: {
    verbo: 'Aceptar y anular',
    titulo: 'Aceptar y anular la entrega',
    efecto: 'Sus cobros vuelven a estar pendientes de entregar.',
    hecho: 'Entrega anulada',
  },
  ANULAR_MOVIMIENTO: {
    verbo: 'Aceptar y anular',
    titulo: 'Aceptar y anular el movimiento',
    efecto: 'Deja de contar en la caja.',
    hecho: 'Movimiento anulado',
  },
};

/** Avanzar un encargo: título, qué pasa, verbo y aviso, por el estado al que pasa (§6.2, como hoy). */
export const AVANCE: Record<'ENCARGADO' | 'RECIBIDO', { titulo: string; efecto: string; verbo: string; hecho: string }> = {
  ENCARGADO: {
    titulo: 'Marcar como encargado',
    efecto: 'Ya se ha pedido al proveedor.',
    verbo: 'Marcar encargado',
    hecho: 'Encargo marcado como encargado',
  },
  RECIBIDO: {
    titulo: 'Marcar como recibido',
    efecto: 'Ha llegado y se le da al profesor, que lo marcará entregado al dárselo al alumno.',
    verbo: 'Marcar recibido',
    hecho: 'Encargo marcado como recibido',
  },
};

/** Lo que se puede hacer con una solicitud desde la fila, en el orden de los botones de hoy. */
export type AccionSolicitud = 'aceptar' | 'denegar' | 'retirar' | 'encargado' | 'recibido' | 'rechazar' | 'devolucion';

/** Tono de una pastilla: `tipo` para el tipo (neutro); el resto, las familias de estado de hoy. */
export type TonoPastilla = 'tipo' | 'bien' | 'peligro' | 'atencion' | 'neutro' | 'curso';

export interface Pastilla {
  texto: string;
  tono: TonoPastilla;
}

export interface PasoEncargo {
  etiqueta: string;
  estado: 'hecho' | 'actual' | 'pendiente';
}

// ------------------------------------------------------------------ segmentos

export function etiquetaSegmento(segmento: SegmentoSolicitudes): string {
  return ETIQUETA_SEGMENTO[segmento];
}

/** Número de la pestaña: Pendientes y Encargos en curso, una vez cargadas; Resueltas nunca lleva (como hoy). */
export function numeroSegmento(segmento: SegmentoSolicitudes, lista: Solicitud[] | null): number | null {
  return segmento === 'RESUELTAS' || lista === null ? null : lista.length;
}

export function subtitulo(esPropietario: boolean): string {
  return esPropietario
    ? 'Lo que la gestión te pide revertir o anular, y los encargos de material.'
    : 'Lo que has pedido al propietario y los encargos de material.';
}

/** El vacío de cada segmento, con el texto de hoy. */
export function vacio(segmento: SegmentoSolicitudes, esPropietario: boolean): { titulo: string; texto: string } {
  switch (segmento) {
    case 'PENDIENTES':
      return {
        titulo: 'No hay solicitudes pendientes.',
        texto: esPropietario
          ? 'Cuando la gestión pida revertir un cobro o anular algo de la caja, aparecerá aquí.'
          : 'Lo que pidas al propietario desde la tesorería, la ficha o la caja de sede aparecerá aquí.',
      };
    case 'ENCARGOS':
      return { titulo: 'No hay encargos en curso.', texto: '' };
    default:
      return { titulo: 'Todavía no hay solicitudes resueltas.', texto: '' };
  }
}

// ------------------------------------------------------------------ pastillas

export function etiquetaTipo(tipo: TipoSolicitud): string {
  return ETIQUETA_TIPO[tipo] ?? tipo;
}

export function etiquetaEstado(estado: EstadoSolicitud): string {
  return ETIQUETA_ESTADO[estado] ?? estado;
}

/** Las familias de hoy (`estado-hecho`, `-no`, `-aviso`, `-neutro` y `-curso`) con los estados de DESIGN.md. */
export function tonoEstado(estado: EstadoSolicitud): TonoPastilla {
  switch (estado) {
    case 'ACEPTADA':
    case 'ENTREGADO':
      return 'bien';
    case 'DENEGADA':
    case 'RECHAZADO':
      return 'peligro';
    case 'NO_APLICABLE':
      return 'atencion';
    case 'RETIRADA':
      return 'neutro';
    default:
      return 'curso';
  }
}

/**
 * Tipo, estado y «Devolución pendiente». En la lista de pendientes no va el estado (todas lo son), como hoy en el
 * segmento Pendientes.
 */
export function pastillas(s: Solicitud, enPendientes: boolean): Pastilla[] {
  const lista: Pastilla[] = [{ texto: etiquetaTipo(s.tipo), tono: 'tipo' }];
  if (!enPendientes) {
    lista.push({ texto: etiquetaEstado(s.estado), tono: tonoEstado(s.estado) });
  }
  if (s.devolucionPendiente) {
    lista.push({ texto: 'Devolución pendiente', tono: 'atencion' });
  }
  return lista;
}

/** El cargo de un encargo, tal como lo da la API; nada si no es un encargo o el cargo ya no existe. */
export function pastillaCobro(s: Solicitud): Pastilla | null {
  const estado = s.tipo === 'ENCARGO' ? s.estadoCobro ?? null : null;
  if (!estado) {
    return null;
  }
  const tono: TonoPastilla = estado === 'COBRADO' ? 'bien' : estado === 'PENDIENTE' ? 'atencion' : 'neutro';
  return { texto: ETIQUETA_COBRO[estado] ?? estado, tono };
}

/** Recorrido del encargo con el paso actual; `null` si no es un encargo o cayó (retirado o rechazado). */
export function pasosEncargo(s: Solicitud): PasoEncargo[] | null {
  if (s.tipo !== 'ENCARGO' || s.estado === 'RETIRADA' || s.estado === 'RECHAZADO') {
    return null;
  }
  const actual = PASOS.findIndex((p) => p.estado === s.estado);
  if (actual < 0) {
    return null;
  }
  return PASOS.map((p, i) => ({ etiqueta: p.etiqueta, estado: i < actual ? 'hecho' : i === actual ? 'actual' : 'pendiente' }));
}

// ------------------------------------------------------------------ acciones (criterios de hoy)

export function verboAceptar(tipo: TipoSolicitud): string {
  return tipo === 'ENCARGO' ? '' : ACEPTAR[tipo]?.verbo ?? '';
}

export function textoAccion(accion: AccionSolicitud, s: Solicitud): string {
  switch (accion) {
    case 'aceptar':
      return verboAceptar(s.tipo);
    case 'denegar':
      return 'Denegar…';
    case 'retirar':
      return 'Retirar';
    case 'encargado':
      return AVANCE.ENCARGADO.verbo;
    case 'recibido':
      return AVANCE.RECIBIDO.verbo;
    case 'rechazar':
      return 'Rechazar…';
    default:
      return 'Registrar devolución';
  }
}

function esPendiente(s: Solicitud): boolean {
  return s.estado === 'PENDIENTE' && s.tipo !== 'ENCARGO';
}

/**
 * Las acciones que la fila enseña, con los criterios de hoy: aceptar y denegar, solo el propietario (`ROLE_ADMIN`) en
 * las pendientes de los tipos 1 a 3; retirar, quien la pidió (`propia`) mientras está pendiente, con cualquier rol; los
 * encargos, a quien ve la bandeja (propietario y gestión por igual; la API decide la sede): encargado desde PEDIDO,
 * recibido desde ENCARGADO, rechazar en PEDIDO, ENCARGADO o RECIBIDO, y registrar la devolución con el dinero ya en
 * la sede (`devolucionEnSede === true`). Lo que no sale aquí no se pinta.
 */
export function accionesDe(s: Solicitud, esPropietario: boolean): AccionSolicitud[] {
  const acciones: AccionSolicitud[] = [];
  if (esPropietario && esPendiente(s)) {
    acciones.push('aceptar', 'denegar');
  }
  if (s.propia && esPendiente(s)) {
    acciones.push('retirar');
  }
  if (s.tipo !== 'ENCARGO') {
    return acciones;
  }
  if (s.estado === 'PEDIDO') {
    acciones.push('encargado');
  }
  if (s.estado === 'ENCARGADO') {
    acciones.push('recibido');
  }
  if (s.estado === 'PEDIDO' || s.estado === 'ENCARGADO' || s.estado === 'RECIBIDO') {
    acciones.push('rechazar');
  }
  if (s.devolucionPendiente && s.devolucionEnSede === true) {
    acciones.push('devolucion');
  }
  return acciones;
}

/** Cayó pagado y el profesor aún lleva el dinero: la devolución la registra él desde Mi caja (nota, sin botón). */
export function devolucionDelProfesor(s: Solicitud): boolean {
  return s.tipo === 'ENCARGO' && s.devolucionPendiente && s.devolucionEnSede === false;
}

// ------------------------------------------------------------------ textos de la fila y de los diálogos

/** Lo principal de «sobre qué»: el alumno o, si no hay, el recurso. */
export function sobreQue(s: Solicitud): string {
  const alumno = [s.alumnoNombre, s.alumnoApellidos].filter((p) => p?.trim()).join(' ').trim();
  if (alumno) {
    return alumno;
  }
  if (s.tipo === 'ANULAR_ENTREGA') {
    return 'Entrega de caja';
  }
  if (s.tipo === 'ANULAR_MOVIMIENTO') {
    return 'Movimiento de caja';
  }
  return 'Sin alumno';
}

/** «Alumno, concepto, importe»: la frase de las confirmaciones de hoy. */
export function descripcion(s: Solicitud): string {
  return [sobreQue(s), s.concepto?.trim(), s.importe === null ? '' : eur(s.importe)].filter((p) => p).join(', ');
}

/** «19/10/2026 09:30», como `date: 'dd/MM/yyyy HH:mm'` de hoy. Vacío sin fecha. */
export function fechaHora(valor: string | null | undefined): string {
  const f = fechaApi(valor);
  if (!f) {
    return '';
  }
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha(f)} ${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

/** «Resuelta por X el …» o «Retirada …» (solo en Resueltas); `null` si no hay quién ni cuándo. */
export function textoResolucion(s: Solicitud): string | null {
  if (!s.resueltoPorNombre && !s.resueltoEn) {
    return null;
  }
  const partes = [s.estado === 'RETIRADA' ? 'Retirada' : 'Resuelta'];
  if (s.resueltoPorNombre) {
    partes.push(`por ${s.resueltoPorNombre}`);
  }
  if (s.resueltoEn) {
    partes.push(`el ${fechaHora(s.resueltoEn)}`);
  }
  return partes.join(' ');
}

export function etiquetaRespuesta(s: Solicitud): string {
  if (s.estado === 'NO_APLICABLE') {
    return 'Por qué no se aplicó:';
  }
  return s.estado === 'RECHAZADO' ? 'Motivo del rechazo:' : 'Respuesta:';
}

/** Qué pasa con el cargo al rechazar el encargo (como hoy). */
export function efectoRechazo(s: Solicitud): string {
  return s.tipo === 'ENCARGO' && s.estadoCobro === 'COBRADO'
    ?'El cargo ya está cobrado: quedará una devolución pendiente.'
    : 'El cargo del alumno se cancelará.';
}
