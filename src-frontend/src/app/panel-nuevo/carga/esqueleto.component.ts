import { ChangeDetectorRef, Component, Input, OnDestroy, OnInit, inject } from '@angular/core';

import { menosMovimiento } from '../capas/capa-modal';

/** Una carga más corta no enseña esqueleto: evita el parpadeo. */
export const RETRASO_ESQUELETO = 300;

/**
 * Esqueleto de carga del panel nuevo (8b-D1, boceto `Carga.dc.html`, a y b). Pinta la forma del contenido que le da
 * cada pantalla (los «huesos», con `serie-pista`) con un pulso de opacidad, a partir de los 300 ms y sin pulso con
 * `prefers-reduced-motion`. La forma se oculta a los lectores de pantalla; a ellos les llega «Cargando…» en un
 * `role=status`.
 */
@Component({
  selector: 'pn-esqueleto',
  standalone: true,
  template: `@if (visible) {<p class="pn-esqueleto__aviso" role="status">{{ etiqueta }}</p><div class="pn-esqueleto__forma" [class.pn-esqueleto__forma--quieta]="quieta" aria-hidden="true"><ng-content /></div>}`,
  styles: `
    :host {
      display: block;
    }

    .pn-esqueleto__aviso {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    .pn-esqueleto__forma {
      animation: pn-pulso 1.6s ease-in-out infinite;
    }

    .pn-esqueleto__forma--quieta {
      animation: none;
    }

    @keyframes pn-pulso {
      0%,
      100% {
        opacity: 1;
      }

      50% {
        opacity: 0.55;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .pn-esqueleto__forma {
        animation: none;
      }
    }
  `,
})
export class EsqueletoComponent implements OnInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);

  /** Lo que se está cargando, para los lectores de pantalla («Cargando alumnos»). */
  @Input() etiqueta = 'Cargando…';

  visible = false;
  readonly quieta = menosMovimiento();
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.visible = true;
      this.cdr.markForCheck();
      this.cdr.detectChanges();
    }, RETRASO_ESQUELETO);
  }

  ngOnDestroy(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
    }
  }
}
