import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Bloque vacío del panel nuevo (8b-D1, boceto `Carga.dc.html`, e): qué no hay, por qué y la acción que lo arregla
 * («Quitar filtros»). La acción puede ser un botón (`accion`) o un enlace propio, proyectado con `pnAccionVacio`.
 */
@Component({
  selector: 'pn-bloque-vacio',
  standalone: true,
  template: `
    <div class="pn-bloque-vacio" role="status">
      <svg class="pn-bloque-vacio__icono" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="1.75" stroke-linecap="round"><circle cx="11" cy="11" r="7"></circle><path d="m20 20-3.5-3.5M8.5 11h5"></path></svg>
      <p class="pn-bloque-vacio__titulo">{{ titulo }}</p>
      @if (texto) {
      <p class="pn-bloque-vacio__texto">{{ texto }}</p>
      }
      @if (accion) {
      <button type="button" class="pn-bloque-vacio__accion" (click)="accionar.emit()">{{ accion }}</button>
      }
      <ng-content select="[pnAccionVacio]" />
    </div>
  `,
  styleUrls: ['./bloque-carga.scss'],
})
export class BloqueVacioComponent {
  @Input({ required: true }) titulo = '';
  @Input() texto = '';
  @Input() accion = '';
  @Output() readonly accionar = new EventEmitter<void>();
}
