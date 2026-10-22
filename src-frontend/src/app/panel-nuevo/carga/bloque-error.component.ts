import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Error al cargar un bloque del panel nuevo (8b-D1, boceto `Carga.dc.html`, d): icono de peligro, qué falló, cómo
 * seguir y «Reintentar», que vuelve a pedir solo ese bloque.
 */
@Component({
  selector: 'pn-bloque-error',
  standalone: true,
  template: `
    <div class="pn-bloque-error" role="alert">
      <svg class="pn-bloque-error__icono" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="1.75" stroke-linecap="round"><circle cx="12" cy="12" r="9"></circle><path d="M12 7.5v5.5M12 16.5v.01"></path></svg>
      <p class="pn-bloque-error__titulo">{{ titulo }}</p>
      <p class="pn-bloque-error__texto">{{ texto }}</p>
      <button type="button" class="pn-bloque-error__reintentar" (click)="reintentar.emit()">
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6"></path></svg>
        <span>Reintentar</span>
      </button>
    </div>
  `,
  styleUrls: ['./bloque-carga.scss'],
})
export class BloqueErrorComponent {
  @Input({ required: true }) titulo = '';
  @Input() texto = 'Comprueba la conexión y vuelve a intentarlo.';
  @Output() readonly reintentar = new EventEmitter<void>();
}
