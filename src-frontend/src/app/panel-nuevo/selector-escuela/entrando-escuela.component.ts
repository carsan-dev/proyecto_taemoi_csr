import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { LogoEscuelaComponent } from './logo-escuela.component';
import { EscuelaUsuario } from './selector-escuela.service';

/**
 * «Entrando en …» (boceto del selector): tapa toda la pantalla desde que se pide el cambio hasta que la app se recarga en
 * la escuela nueva, para que no se vea ni se pueda tocar nada de la anterior. Si el cambio falla, quien lo pinta lo quita.
 */
@Component({
  selector: 'pn-entrando-escuela',
  standalone: true,
  imports: [LogoEscuelaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pn-entrando__caja">
      <pn-logo-escuela class="pn-entrando__logo" [escuela]="escuela" />
      <p class="pn-entrando__texto" role="status">
        <svg class="pn-entrando__giro" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="2.5" stroke-linecap="round"><path d="M12 3a9 9 0 1 1-9 9"></path></svg>
        <span>Entrando en {{ escuela.nombre }}…</span>
      </p>
    </div>
  `,
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 1100;
      display: grid;
      place-items: center;
      padding: var(--pn-espacio-l);
      background: var(--pn-fondo);
      color: var(--pn-tinta);
      text-align: center;
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
export class EntrandoEscuelaComponent {
  @Input({ required: true }) escuela!: EscuelaUsuario;
}
