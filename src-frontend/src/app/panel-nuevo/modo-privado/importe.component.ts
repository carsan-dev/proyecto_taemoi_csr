import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';

import { ModoPrivadoService } from './modo-privado.service';

/**
 * Un importe que se tapa en el modo privado (decisión 419): `<pn-importe>{{ eur(total) }}</pn-importe>`. Va en línea y
 * hereda la tipografía y el color del padre. Con el atributo `bloque` envuelve una gráfica entera.
 */
@Component({
  selector: 'pn-importe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="importe" [class.importe--tapado]="modo.tapado()" [attr.aria-hidden]="modo.tapado() ? 'true' : null"><ng-content /></span>@if (modo.tapado()) {<span class="importe__oculto">Importe oculto</span>}`,
  styles: `
    @use '../capas/capas' as c;

    :host {
      display: inline;
    }

    :host([bloque]),
    :host([bloque]) .importe {
      display: block;
    }

    .importe {
      transition: filter 160ms ease-out;
    }

    .importe--tapado {
      filter: blur(7px);
      user-select: none;
    }

    .importe__oculto {
      @include c.oculto;
    }

    @media (prefers-reduced-motion: reduce) {
      .importe {
        transition: none;
      }
    }

    // Con colores forzados el difuminado puede no bastar: la cifra se convierte en una barra del color del texto.
    @media (forced-colors: active) {
      .importe--tapado {
        forced-color-adjust: none;
        color: transparent;
        background: CanvasText;
      }
    }
  `,
})
export class ImporteComponent implements OnInit {
  readonly modo = inject(ModoPrivadoService);

  // Un importe puede pintarse antes que el botón.
  ngOnInit(): void {
    this.modo.cargar();
  }
}
