import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild,
} from '@angular/core';

import { LogoEscuelaComponent } from './logo-escuela.component';
import { EscuelaUsuario } from './selector-escuela.service';

/**
 * «Entrando en …» (boceto del selector): tapa toda la pantalla desde que se pide el cambio hasta que la app se recarga en
 * la escuela nueva, para que no se vea ni se pueda tocar nada de la anterior. Es un `<dialog>` abierto con
 * `showModal()`: el resto de la página queda inerte (ni clic ni teclado), Esc no lo cierra y el foco va al texto, que se
 * anuncia (`role="status"`). Si el cambio falla, quien lo pinta lo quita.
 */
@Component({
  selector: 'pn-entrando-escuela',
  standalone: true,
  imports: [LogoEscuelaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialogo class="pn pn-entrando" aria-labelledby="pn-entrando-texto" aria-busy="true"
      (cancel)="$event.preventDefault()" (close)="abrir()">
      <div class="pn-entrando__caja">
        <pn-logo-escuela class="pn-entrando__logo" [escuela]="escuela" />
        <p #texto class="pn-entrando__texto" role="status" tabindex="-1">
          <svg class="pn-entrando__giro" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="2.5" stroke-linecap="round"><path d="M12 3a9 9 0 1 1-9 9"></path></svg>
          <span id="pn-entrando-texto">Entrando en {{ escuela.nombre }}…</span>
        </p>
      </div>
    </dialog>
  `,
  styles: `
    :host {
      display: contents;
    }

    .pn-entrando {
      position: fixed;
      inset: 0;
      width: 100%;
      max-width: none;
      height: 100%;
      max-height: none;
      margin: 0;
      padding: var(--pn-espacio-l);
      border: 0;
      background: var(--pn-fondo);
      color: var(--pn-tinta);
      text-align: center;

      &[open] {
        display: grid;
        place-items: center;
      }

      &::backdrop {
        background: var(--pn-fondo, #fff);
      }
    }

    .pn-entrando__caja {
      display: grid;
      justify-items: center;
      gap: 14px;
      max-width: 420px;
    }

    .pn-entrando__logo {
      --pn-logo-escuela: 64px;
    }

    .pn-entrando__texto {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font: var(--pn-titulo-bloque);
      overflow-wrap: anywhere;
      text-wrap: balance;

      &:focus {
        outline: none;
      }
    }

    .pn-entrando__giro {
      flex: none;
      width: 18px;
      height: 18px;
      color: var(--pn-tinta-2);
      animation: pn-entrando-giro 0.8s linear infinite;
    }

    @keyframes pn-entrando-giro {
      to {
        transform: rotate(360deg);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .pn-entrando__giro {
        animation: none;
      }
    }
  `,
})
export class EntrandoEscuelaComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) escuela!: EscuelaUsuario;

  @ViewChild('dialogo', { static: true }) private readonly dialogo!: ElementRef<HTMLDialogElement>;
  @ViewChild('texto', { static: true }) private readonly texto!: ElementRef<HTMLElement>;
  private destruido = false;

  ngAfterViewInit(): void {
    this.abrir();
    this.texto.nativeElement.focus();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    const dialogo = this.dialogo.nativeElement;
    if (dialogo.open) {
      dialogo.close();
    }
  }

  /** Abre (o vuelve a abrir, si el navegador lo cerró con un segundo Esc) mientras dure el cambio. */
  abrir(): void {
    const dialogo = this.dialogo.nativeElement;
    if (this.destruido || dialogo.open || !dialogo.isConnected) {
      return;
    }
    try {
      dialogo.showModal();
    } catch {
      dialogo.setAttribute('open', '');
    }
  }
}
