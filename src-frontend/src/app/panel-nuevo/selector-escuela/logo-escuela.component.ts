import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';

import { EscuelaUsuario, coloresIniciales, inicialesEscuela, urlLogoEscuela } from './selector-escuela.service';

/**
 * Logo de una escuela de la cuenta en el selector: el subido, el de siempre (LEGADO) o, sin él o si no carga, sus
 * iniciales sobre su color (a 4,5:1). Decorativo: el nombre de la escuela va siempre al lado. El tamaño lo pone quien
 * lo usa con `--pn-logo-escuela` (46 px por defecto).
 */
@Component({
  selector: 'pn-logo-escuela',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (url(); as src) {
      <img class="pn-logo-escuela__imagen" [src]="src" alt="" (error)="fallo.set(true)" />
    } @else {
      <span class="pn-logo-escuela__iniciales" aria-hidden="true" [style.background-color]="colores().fondo"
        [style.color]="colores().texto">{{ iniciales() }}</span>
    }
  `,
  styles: `
    :host {
      display: grid;
      flex: none;
      place-items: center;
      width: var(--pn-logo-escuela, 46px);
      height: var(--pn-logo-escuela, 46px);
    }

    .pn-logo-escuela__imagen {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    .pn-logo-escuela__iniciales {
      display: grid;
      place-items: center;
      width: 100%;
      height: 100%;
      border-radius: var(--pn-radio-pastilla);
      font: 700 calc(var(--pn-logo-escuela, 46px) * 0.33) / 1 var(--pn-fuente-titulo);
      letter-spacing: 0.02em;
    }
  `,
})
export class LogoEscuelaComponent {
  private readonly datos = signal<EscuelaUsuario | null>(null);
  readonly fallo = signal(false);

  @Input({ required: true })
  set escuela(valor: EscuelaUsuario) {
    this.datos.set(valor);
    this.fallo.set(false);
  }

  readonly url = computed(() => {
    const escuela = this.datos();
    return escuela && !this.fallo() ? urlLogoEscuela(escuela) : null;
  });
  readonly iniciales = computed(() => inicialesEscuela(this.datos()?.nombre));
  readonly colores = computed(() => coloresIniciales(this.datos()?.colorPrimario ?? null));
}
