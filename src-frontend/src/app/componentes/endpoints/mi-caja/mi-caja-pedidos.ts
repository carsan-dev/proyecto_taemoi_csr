import { EstadoCobroCargo, EstadoSolicitud, Solicitud } from '../../../interfaces/solicitud';

/**
 * Reglas de la pestaña «Pedidos» de Mi caja (pieza 6c-8): encargos (decisión 183, §6.2) y solicitudes de deshacer un
 * cobro entregado (tipo 2, decisión 193). Funciones puras para poder probarlas sin la pantalla.
 */

/** Nota del encargo: va también a las notas del cargo (VARCHAR 255), así que manda el menor (6c-5). */
export const MAX_NOTA_ENCARGO = 255;
/** Motivo de una solicitud y nota de una devolución (D6c-1, 6c-6). */
export const MAX_MOTIVO = 500;

/** Pasos del recorrido de un encargo, en orden. */
export const PASOS_ENCARGO: ReadonlyArray<{ estado: EstadoSolicitud; etiqueta: string }> = [
  { estado: 'PEDIDO', etiqueta: 'Pedido' },
  { estado: 'ENCARGADO', etiqueta: 'Encargado' },
  { estado: 'RECIBIDO', etiqueta: 'Recibido' },
  { estado: 'ENTREGADO', etiqueta: 'Entregado' },
];

export const ETIQUETA_COBRO: Record<EstadoCobroCargo, string> = {
  PENDIENTE: 'Sin cobrar',
  COBRADO: 'Cobrado',
  DEVUELTO: 'Devuelto',
  CANCELADO: 'Cargo cancelado',
};

export function esEncargo(s: Solicitud): boolean {
  return s.tipo === 'ENCARGO';
}

export function esDeshacer(s: Solicitud): boolean {
  return s.tipo === 'DESHACER_COBRO_ENTREGADO';
}

/** Retirado o rechazado: el encargo ya no sigue. */
export function encargoCaido(s: Solicitud): boolean {
  return s.estado === 'RETIRADA' || s.estado === 'RECHAZADO';
}

/** Índice del paso actual en {@link PASOS_ENCARGO}; -1 si el encargo cayó. */
export function indicePasoEncargo(s: Solicitud): number {
  return PASOS_ENCARGO.findIndex((p) => p.estado === s.estado);
}

/** Estado del cobro del cargo del encargo, tal como lo da la API (`null` si el cargo ya no existe). */
export function estadoCobroEncargo(s: Solicitud): EstadoCobroCargo | null {
  return esEncargo(s) ? s.estadoCobro ?? null : null;
}

/** El encargo cayó pagado y el profesor aún lleva el dinero: la devolución la registra él (195). */
export function puedeRegistrarDevolucion(s: Solicitud): boolean {
  return esEncargo(s) && s.propia && s.devolucionPendiente && s.devolucionEnSede === false;
}

/** El encargo cayó pagado y el dinero ya está en la sede: la devolución la registra la sede (195). */
export function devolucionEnLaSede(s: Solicitud): boolean {
  return esEncargo(s) && s.devolucionPendiente && s.devolucionEnSede !== false;
}

/** «Retirar»: su encargo mientras está PEDIDO o su solicitud de deshacer mientras está PENDIENTE. */
export function puedeRetirar(s: Solicitud): boolean {
  return s.propia && ((esEncargo(s) && s.estado === 'PEDIDO') || (esDeshacer(s) && s.estado === 'PENDIENTE'));
}

export function puedeMarcarEntregado(s: Solicitud): boolean {
  return esEncargo(s) && s.propia && s.estado === 'RECIBIDO';
}

/** Lo que espera su acción (el `.contador` de «Pedidos»): marcar entregado o registrar la devolución. */
export function requiereAccion(s: Solicitud): boolean {
  return puedeMarcarEntregado(s) || puedeRegistrarDevolucion(s);
}

/** En curso: aún avanza o le falta la devolución. */
export function encargoEnCurso(s: Solicitud): boolean {
  return esEncargo(s) && (s.estado === 'PEDIDO' || s.estado === 'ENCARGADO' || s.estado === 'RECIBIDO' || s.devolucionPendiente);
}

/** Respuesta de una solicitud de deshacer, para su chip. */
export function etiquetaDeshacer(estado: EstadoSolicitud): string {
  switch (estado) {
    case 'PENDIENTE':
      return 'Esperando al propietario';
    case 'ACEPTADA':
      return 'Aceptada: cobro deshecho';
    case 'DENEGADA':
      return 'Denegada';
    case 'NO_APLICABLE':
      return 'No se aplicó';
    case 'RETIRADA':
      return 'Retirada';
    default:
      return estado;
  }
}

/** La API pide la sede del encargo (D6c-9): el alumno está con él en grupos de varias sedes. */
export function pideSede(mensaje: string | null): boolean {
  return !!mensaje && /varias sedes/i.test(mensaje);
}
