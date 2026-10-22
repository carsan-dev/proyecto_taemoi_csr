import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { fecha, numero } from '../../formato/formato-es';
import { fechaParaCampo } from '../ficha-vista';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

/**
 * Pestaña «Documentos y notas» de la ficha nueva (8b-3b): información adicional (deportes activos, baja general,
 * autorización web y observaciones con su «Guardar») y documentos (ver, descargar, eliminar, ver más, subir y WhatsApp).
 * Todo llama a los métodos de la ficha de hoy (`ficha`): mismos envíos, mismos diálogos.
 */
@Component({
  selector: 'app-ficha-documentos',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './ficha-documentos.component.html',
  styleUrls: ['./ficha-documentos.component.scss'],
})
export class FichaDocumentosComponent {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  readonly fecha = fecha;

  get verMas(): string {
    const ocultos = this.ficha.documentosAlumno.length - this.ficha.documentosVisiblesInicial;
    return `Ver ${numero(ocultos)} más`;
  }

  /** «PDF», «Imagen», «Word»…: el tipo legible del documento; si no se reconoce, su MIME. */
  tipo(mime: string | null | undefined): string {
    const m = (mime ?? '').split(';')[0].trim().toLowerCase();
    if (m === 'application/pdf') return 'PDF';
    if (m.startsWith('image/')) return 'Imagen';
    if (m.includes('word')) return 'Word';
    if (m.includes('sheet') || m.includes('excel')) return 'Excel';
    if (m === 'text/csv') return 'CSV';
    if (m.startsWith('text/')) return 'Texto';
    return m || 'Documento';
  }

  /** Valor de la fecha de baja general para el campo (como hoy, «yyyy-MM-dd»). */
  get fechaBajaCampo(): string {
    return fechaParaCampo(this.ficha.alumno?.fechaBaja);
  }
}
