import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';

import { ModoPrivadoService } from './modo-privado.service';

/**
 * «Ver importes» (decisión 419): destapa todas las cifras de la pantalla. Solo aparece con la preferencia activa, y al
 * salir de la pantalla se destruye y vuelve a tapar.
 */
@Component({
  selector: 'pn-ver-importes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (modo.disponible()) {
    <button type="button" class="ver-importes" [attr.aria-pressed]="!modo.tapado()" (click)="modo.alternar()">
      <i class="bi" [class.bi-eye]="modo.tapado()" [class.bi-eye-slash]="!modo.tapado()" aria-hidden="true"></i>
      <span>{{ modo.tapado() ? 'Ver importes' : 'Ocultar importes' }}</span>
    </button>
  }`,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }

    // Sin botón no ocupa hueco: las pantallas se ven como hoy.
    :host(:empty) {
      display: none;
    }

    .ver-importes {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: 44px;
      padding: 0 10px;
      border: 0;
      border-radius: var(--pn-radio-m);
      background: transparent;
      color: var(--pn-acento);
      font: var(--pn-etiqueta);
      font-size: 0.875rem;
      white-space: nowrap;
      cursor: pointer;
      transition: background-color var(--pn-transicion);
    }

    .ver-importes:hover {
      background: var(--pn-acento-suave);
    }

    .ver-importes:focus-visible {
      outline: 3px solid var(--pn-acento);
      outline-offset: 2px;
    }

    .bi {
      flex: none;
      font-size: 1.125rem;
      line-height: 1;
    }
  `,
})
export class VerImportesComponent implements OnInit, OnDestroy {
  readonly modo = inject(ModoPrivadoService);

  ngOnInit(): void {
    this.modo.cargar();
  }

  ngOnDestroy(): void {
    this.modo.tapar();
  }
}
