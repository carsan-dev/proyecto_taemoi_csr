import { Component, ElementRef, Input, OnChanges, SimpleChanges, inject } from '@angular/core';

/**
 * Botón ocupado del panel nuevo (8b-D1, boceto `Carga.dc.html`, c): mientras la acción está en vuelo conserva su ancho,
 * se deshabilita, lleva `aria-busy` y cambia su texto por el de la acción en curso («Cobrando…») con un indicador de
 * 16 px. Se pone sobre el `<button>` de siempre: `<button pnOcupado [ocupado]="…" textoOcupado="Cobrando…">`.
 */
@Component({
  selector: 'button[pnOcupado]',
  standalone: true,
  template: `@if (ocupado) {<svg class="pn-ocupado__giro" aria-hidden="true" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-opacity=".35" stroke-width="2.5"></circle><path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"></path></svg><span>{{ textoOcupado }}</span>} @else {<ng-content />}`,
  styles: `
    .pn-ocupado__giro {
      width: 16px;
      height: 16px;
      flex: none;
      animation: pn-ocupado-giro 0.8s linear infinite;
    }

    @keyframes pn-ocupado-giro {
      to {
        transform: rotate(360deg);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .pn-ocupado__giro {
        animation: none;
      }
    }
  `,
  host: {
    '[disabled]': 'ocupado || deshabilitado',
    '[attr.aria-busy]': "ocupado ? 'true' : null",
    '[style.width.px]': 'ancho',
  },
})
export class BotonOcupadoComponent implements OnChanges {
  private readonly host = inject<ElementRef<HTMLButtonElement>>(ElementRef);

  @Input() ocupado = false;
  @Input() textoOcupado = '';
  @Input() deshabilitado = false;

  /** El ancho del botón antes de ocuparse: el texto nuevo no lo mueve. */
  ancho: number | null = null;

  ngOnChanges(cambios: SimpleChanges): void {
    if (!cambios['ocupado']) {
      return;
    }
    // Se mide antes de pintar el texto de ocupado: en este momento el botón aún muestra el de siempre.
    const ancho = this.host.nativeElement.getBoundingClientRect().width;
    this.ancho = this.ocupado && ancho > 0 ? ancho : null;
  }
}
