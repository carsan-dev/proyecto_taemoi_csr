import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { RELOJ_WEB } from '../../../comun/reloj';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { TextosTema } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { textosTarifas, vistaTarifas } from './tarifas';

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

  protected readonly pagina = paginaWeb('tarifas', () => '/tarifas');
  protected readonly vista = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? vistaTarifas(sitio, this.reloj()) : null;
  });
  /** W12d-bis: los textos de la escuela (`tarifas.*`) con los del tema por defecto. */
  private readonly textosPagina = computed(() => textosTarifas(new TextosTema(this.pagina.sitio())));

  /** Lo que lee la plantilla (`textos.x`); al leer la señal, la plantilla se repinta si cambian los textos. */
  protected get textos(): ReturnType<typeof textosTarifas> {
    return this.textosPagina();
  }

  /** «al mes» del cuadro y de «Por sede»: `tarifas.al_mes`, como en la banda. */
  protected get alMes(): string {
    return this.textosPagina().alMes;
  }
}
