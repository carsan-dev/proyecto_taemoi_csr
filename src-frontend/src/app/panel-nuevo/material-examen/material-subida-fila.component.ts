import { Component, EventEmitter, Input, Output } from '@angular/core';

import { SubidaVideo } from './cola-subidas';
import { porcentaje, textoProgreso } from './material-examen-vista';

/** Una fila de la cola de vídeos: en curso (`Bloque.dc.html`) o fallida (`Primera.dc.html`). Solo presenta. */
@Component({
  selector: 'pn-material-subida-fila',
  standalone: true,
  templateUrl: './material-subida-fila.component.html',
  styleUrls: ['./material-subida-fila.component.scss'],
})
export class MaterialSubidaFilaComponent {
  @Input({ required: true }) item!: SubidaVideo;
  @Input() movil = false;
  /** Quita la fila; si está en curso, corta el envío. */
  @Output() readonly cancelar = new EventEmitter<void>();
  @Output() readonly reintentar = new EventEmitter<void>();

  get valor(): number {
    return porcentaje(this.item.cargados, this.item.total);
  }

  get texto(): string {
    return textoProgreso(this.item.cargados, this.item.total, this.movil);
  }
}
