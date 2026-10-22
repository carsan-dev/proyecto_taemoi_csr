import { SweetAlertOptions } from 'sweetalert2';

import { MetodoPago, ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';

/**
 * Opciones del método en «Confirmar cobro» (decisión 148); la clave vacía es «Sin indicar». Las pastillas del «Cobrar»
 * de las pantallas nuevas (8b-D1) salen de aquí.
 */
export const METODOS_PAGO_COBRO: ReadonlyArray<[string, string]> = [
  ['', 'Sin indicar'],
  ['EFECTIVO', 'Efectivo'],
  ['TRANSFERENCIA', 'Transferencia'],
  ['TARJETA', 'Tarjeta'],
  ['BIZUM', 'Bizum'],
];

/**
 * Diálogo «Confirmar cobro» de la tesorería, compartido con el Inicio nuevo (8b-2, punto de revisión 8: cobrar desde
 * las pantallas nuevas es cobrar como hoy). Se abre con `Swal.fire(opcionesConfirmarCobro(concepto))`.
 */
export function opcionesConfirmarCobro(concepto: string | null | undefined): SweetAlertOptions {
  return {
    title: 'Confirmar cobro',
    text: `Marcar como pagado "${concepto}"?`,
    icon: 'question',
    // Decisión 148: método opcional; «Sin indicar» por defecto (no se envía).
    input: 'select',
    inputLabel: 'Método (opcional)',
    inputOptions: new Map<string, string>(METODOS_PAGO_COBRO),
    inputValue: '',
    showCancelButton: true,
    confirmButtonText: 'Si, marcar pagado',
    cancelButtonText: 'Cancelar',
    confirmButtonColor: '#2f855a',
  };
}

/** Cuerpo del PUT al cobrar: `metodoPago` solo si se eligió uno (sin indicar → se omite). */
export function cambiosDeCobro(metodo: unknown): Partial<ProductoAlumnoDTO> {
  const elegido = METODOS_PAGO_COBRO.find(([valor]) => valor !== '' && valor === metodo)?.[0] as MetodoPago | undefined;
  return elegido ? { pagado: true, metodoPago: elegido } : { pagado: true };
}
