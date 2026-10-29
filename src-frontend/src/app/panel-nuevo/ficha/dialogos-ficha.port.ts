import { InjectionToken } from '@angular/core';
import Swal, { SweetAlertOptions, SweetAlertResult } from 'sweetalert2';

import { showErrorToast, showSuccessToast } from '../../utils/toast.util';

/**
 * Puerto de los diálogos de la ficha del alumno (8b-D2b). La ficha de hoy (`EditarAlumnoComponent`) y la página de
 * productos del alumno abren sus diálogos por aquí y no con SweetAlert2 directamente:
 * - por defecto (`DIALOGOS_SWEETALERT`, en toda la aplicación) es SweetAlert2 tal cual: el mismo objeto de opciones va
 *   a `Swal.fire`, así que la ficha de hoy no cambia;
 * - la ficha nueva del panel (`FichaAlumnoComponent`, que hereda de la de hoy) da su propia implementación en sus
 *   `providers` y traduce esas opciones a las piezas del panel nuevo (diálogo, avisos), con un resultado igual al de
 *   SweetAlert2 para que el `.then` de cada llamada siga igual.
 */
export interface DialogosFichaPort {
  /**
   * `Swal.fire(opciones)`. `id`, opcional, nombra el diálogo para la implementación del panel (los que llevan html o
   * leen el DOM); la de SweetAlert2 lo ignora.
   */
  fire(opciones: SweetAlertOptions, id?: string): Promise<SweetAlertResult<any>>;
  /** `Swal.close()`. */
  close(): void;
  /** `Swal.showValidationMessage(mensaje)` (dentro de un `preConfirm`). */
  showValidationMessage(mensaje: string): void;
  /** `showSuccessToast(mensaje)` de toast.util. */
  toastExito(mensaje: string): void;
  /** `showErrorToast(mensaje)` de toast.util. */
  toastError(mensaje: string): void;
}

/** SweetAlert2 y los toasts de hoy, sin tocar nada: lo que hacía cada llamada antes del puerto. */
export const DIALOGOS_SWEETALERT: DialogosFichaPort = {
  fire: (opciones: SweetAlertOptions) => Swal.fire(opciones),
  close: () => Swal.close(),
  showValidationMessage: (mensaje: string) => Swal.showValidationMessage(mensaje),
  toastExito: (mensaje: string) => showSuccessToast(mensaje),
  toastError: (mensaje: string) => showErrorToast(mensaje),
};

export const DIALOGOS_FICHA = new InjectionToken<DialogosFichaPort>('DIALOGOS_FICHA', {
  providedIn: 'root',
  factory: () => DIALOGOS_SWEETALERT,
});
