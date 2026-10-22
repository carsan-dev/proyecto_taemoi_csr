import { Injectable, inject } from '@angular/core';

import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';

/**
 * El `InformePdfService` de siempre para el listado nuevo (8b-D1): misma generación, mismas peticiones y mismos nombres
 * de PDF, pero el aviso de un fallo (o de un PDF que no se pudo generar) va en los avisos del panel nuevo. Un SweetAlert2
 * abierto con el panel lateral delante quedaría detrás de él, sin verse ni poder cerrarse.
 */
@Injectable()
export class InformePdfPanelService extends InformePdfService {
  private readonly avisos = inject(AvisosService);

  protected override avisar(_titulo: string, texto: string): void {
    this.avisos.error(texto);
  }
}
