import { Injectable, inject } from '@angular/core';

import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';

/**
 * El `InformePdfService` de siempre para el listado nuevo (8b-D1): misma generación, mismas peticiones y mismos nombres
 * de PDF, pero el aviso de un fallo (o de un PDF que no se pudo generar) va en los avisos del panel nuevo. Un SweetAlert2
 * abierto con el panel lateral delante quedaría detrás de él, sin verse ni poder cerrarse. Lo mismo el cargador global.
 */
@Injectable()
export class InformePdfPanelService extends InformePdfService {
  private readonly avisos = inject(AvisosService);

  /** El «Aviso» de hoy (warning: PDF parciales) va como información con el mismo texto; el «Error», como error. */
  protected override avisar(_titulo: string, texto: string, icono: 'warning' | 'error'): void {
    if (icono === 'warning') {
      this.avisos.info(texto);
    } else {
      this.avisos.error(texto);
    }
  }

  /**
   * Sin el cargador global (8b-D2a): quedaría detrás del panel lateral, que es una capa modal. El botón pulsado se pone
   * ocupado y el panel no se cierra mientras tanto.
   */
  protected override mostrarCarga(): void {}

  protected override ocultarCarga(): void {}
}
