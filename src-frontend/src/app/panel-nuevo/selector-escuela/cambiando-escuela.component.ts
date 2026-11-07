import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { EntrandoEscuelaComponent } from './entrando-escuela.component';
import { SelectorEscuelaService } from './selector-escuela.service';

/**
 * Ruta neutra del cambio de escuela (`RUTA_CAMBIANDO_ESCUELA`): `SelectorEscuelaService.cambiar` sale aquí ANTES de
 * pedir el cambio, así la pantalla anterior pregunta por sus cambios sin guardar y deja de existir. Pinta «Entrando en
 * …» hasta que la app se recarga; si el cambio falla, el servicio vuelve a la pantalla de antes. Sin un cambio en vuelo
 * (alguien escribe la URL), a la portada.
 */
@Component({
  selector: 'app-cambiando-escuela',
  standalone: true,
  imports: [EntrandoEscuelaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (selector.entrando(); as escuela) {
    <pn-entrando-escuela [escuela]="escuela" />
    }
  `,
})
export class CambiandoEscuelaComponent implements OnInit {
  readonly selector = inject(SelectorEscuelaService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (!this.selector.ocupado) {
      void this.router.navigateByUrl('/', { replaceUrl: true });
    }
  }
}

/** Mientras dura el cambio no se sale de la ruta neutra (Atrás, un 401 que lleva al login…): la app se va a recargar. */
export const sinSalirDuranteElCambio = (): boolean => !inject(SelectorEscuelaService).ocupado;
