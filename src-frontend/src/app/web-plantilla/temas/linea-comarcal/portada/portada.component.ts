import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  ViewEncapsulation,
  afterNextRender,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { RELOJ_WEB } from '../../../comun/reloj';
import { vistaPortada } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { LineaSedesComponent } from './linea-sedes.component';

/**
 * Portada del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal.html`), con los datos de
 * `GET /api/publico/web`: portada y línea de sedes, presentación, disciplinas, profesores, galería, 3 próximos
 * eventos, tarifas y contacto. Una sede o varias salen de los datos. Mientras carga no pinta nada (el boceto no tiene
 * estado de carga); sin publicar, la página «Web aún no publicada».
 *
 * Las anclas (`/#linea`, `/#contacto`…) se buscan a mano: el router de la aplicación no hace `anchorScrolling`.
 */
@Component({
  selector: 'app-lc-portada',
  standalone: true,
  imports: [VistaPreviaPipe, MarcoLineaComarcalComponent, LineaSedesComponent, NgTemplateOutlet, RouterLink, SinPublicarComponent],
  templateUrl: './portada.component.html',
  styleUrl: './portada.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortadaLineaComarcalComponent {
  private readonly reloj = inject(RELOJ_WEB);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly fragmento = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });

  protected readonly pagina = paginaWeb('portada', () => '/');
  protected readonly vista = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? vistaPortada(sitio, this.reloj()) : null;
  });

  constructor() {
    effect(() => {
      const fragmento = this.fragmento();
      const vista = this.vista();
      if (!fragmento || !vista) {
        return;
      }
      untracked(() => afterNextRender(() => this.irA(fragmento), { injector: this.injector }));
    });
  }

  private irA(fragmento: string): void {
    const destino = this.documento.getElementById(fragmento);
    if (!destino || typeof destino.scrollIntoView !== 'function') {
      return;
    }
    const reducir = this.documento.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
    destino.scrollIntoView({ behavior: reducir ? 'auto' : 'smooth', block: 'start' });
  }
}
