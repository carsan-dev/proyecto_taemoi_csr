import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { RELOJ_WEB } from '../../../comun/reloj';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { TEXTOS_FIJOS_LINEA_COMARCAL, TEXTOS_PAGINAS_LINEA_COMARCAL } from '../textos';
import { vistaTarifas } from './tarifas';

/**
 * Página de tarifas del tema «La línea comarcal» (`/tarifas`, boceto `fase4-web/boceto-linea-comarcal-tarifas.html`)
 * con los datos de `GET /api/publico/web`: banda con la mensualidad, cuadro de tarifas, mensualidad por sede con los
 * cambios anunciados, y licencia y exámenes aparte. Sin tarifas, «Pregunta la cuota en tu sede».
 */
@Component({
  selector: 'app-lc-tarifas',
  standalone: true,
  imports: [VistaPreviaPipe, MarcoLineaComarcalComponent, NgTemplateOutlet, RouterLink, SinPublicarComponent],
  templateUrl: './tarifas.component.html',
  styleUrl: './tarifas.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarifasLineaComarcalComponent {
  private readonly reloj = inject(RELOJ_WEB);

  protected readonly textos = TEXTOS_PAGINAS_LINEA_COMARCAL;
  protected readonly alMes = TEXTOS_FIJOS_LINEA_COMARCAL.alMes;
  protected readonly pagina = paginaWeb('tarifas', () => '/tarifas');
  protected readonly vista = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? vistaTarifas(sitio, this.reloj()) : null;
  });
}
