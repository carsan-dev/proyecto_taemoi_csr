import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { cambiosDeCobro, opcionesConfirmarCobro } from '../../componentes/endpoints/tesoreria-cobros/confirmar-cobro';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { showErrorToast, showSuccessToast } from '../../utils/toast.util';
import { CargoPendiente } from '../alumnos/pendientes-alumno.service';

/**
 * «Cobrar» un cargo vencido desde las pantallas nuevas (8b-3a y 8b-3b; punto de revisión 8): el mismo diálogo, la misma
 * llamada y el mismo cuerpo que «Marcar pagado» en la tesorería de hoy. `cobrando` guarda los cargos con un cobro en
 * vuelo (no se cobra dos veces) y `alCobrar` se llama tras el PUT correcto.
 */
export function cobrarCargo(
  endpoints: EndpointsService,
  cargo: CargoPendiente,
  cobrando: Set<number>,
  alCobrar: () => void
): void {
  const id = cargo.productoAlumnoId;
  if (cobrando.has(id)) {
    return;
  }
  Swal.fire(opcionesConfirmarCobro(cargo.concepto)).then((resultado) => {
    if (!resultado.isConfirmed) {
      return;
    }
    cobrando.add(id);
    endpoints
      .actualizarCobroTesoreria(id, cambiosDeCobro(resultado.value))
      .pipe(finalize(() => cobrando.delete(id)))
      .subscribe({
        next: () => {
          showSuccessToast('Cobro actualizado como pagado');
          alCobrar();
        },
        error: (error: HttpErrorResponse) => {
          showErrorToast(
            error?.status === 403 ? 'Solo administradores pueden revertir cobros' : 'No se pudo actualizar el estado del cobro'
          );
        },
      });
  });
}

/**
 * Teclado del panel modal de cargos: Esc lo cierra (y devuelve el foco a «Cobrar», cosa de `cerrar`); Tab y Mayús+Tab
 * dan la vuelta dentro del panel. Se engancha al `keydown` del propio panel.
 */
export function teclaPanelCargos(evento: KeyboardEvent, cerrar: () => void): void {
  if (evento.key === 'Escape') {
    evento.preventDefault();
    evento.stopPropagation();
    cerrar();
    return;
  }
  if (evento.key !== 'Tab') {
    return;
  }
  const panel = evento.currentTarget instanceof HTMLElement ? evento.currentTarget : null;
  const enfocables = Array.from(panel?.querySelectorAll<HTMLButtonElement>('button:not([disabled])') ?? []);
  if (enfocables.length === 0) {
    evento.preventDefault();
    return;
  }
  const primero = enfocables[0];
  const ultimo = enfocables[enfocables.length - 1];
  const activo = panel?.ownerDocument.activeElement;
  if (evento.shiftKey && (activo === primero || !panel?.contains(activo ?? null))) {
    evento.preventDefault();
    ultimo.focus();
  } else if (!evento.shiftKey && (activo === ultimo || !panel?.contains(activo ?? null))) {
    evento.preventDefault();
    primero.focus();
  }
}
