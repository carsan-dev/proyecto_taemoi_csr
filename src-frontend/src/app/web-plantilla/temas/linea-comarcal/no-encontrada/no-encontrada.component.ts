import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { SitioWeb } from '../../../datos/modelos';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';

/**
 * El cuerpo de la 404 del tema (dentro del marco): la línea cortada, un mensaje corto y dos salidas (inicio y
 * horarios). Lo usan la 404 y la página de una sede que no existe.
 */
@Component({
  selector: 'app-lc-no-encontrada-contenido',
  standalone: true,
  imports: [RouterLink, VistaPreviaPipe],
  template: `
    <div class="error">
      <div class="envoltura">
        <svg class="error-linea" viewBox="0 0 1000 28" preserveAspectRatio="none" aria-hidden="true">
          <line class="trazo" x1="0" y1="14" x2="560" y2="14" stroke-width="8" />
          <line class="corte" x1="600" y1="14" x2="1000" y2="14" stroke-width="3" stroke-dasharray="10 12" />
        </svg>
        <h1 class="error-titular">No encontramos esta página</h1>
        <p class="error-texto">Puede que el enlace esté mal escrito o que la página ya no exista.</p>
        <p class="error-codigo">Error 404</p>
        <div class="acciones">
          <a class="boton" routerLink="/" [queryParams]="null | vistaPrevia">Ir al inicio</a>
          @if (sitio().sedes.length) {
            <a class="boton boton--secundario" routerLink="/" fragment="linea" [queryParams]="null | vistaPrevia">Ver horarios</a>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .lc .error { padding: clamp(48px, 8vw, 112px) 0 clamp(72px, 10vw, 144px); }
    .lc .error-linea { width: 100%; height: 28px; margin-bottom: clamp(28px, 4vw, 48px); overflow: visible; }
    .lc .error-linea .trazo { stroke: var(--escuela-trazo); }
    .lc .error-linea .corte { stroke: var(--apagado); }
    .lc .error-titular {
      font: 800 clamp(56px, 14vw, 128px)/.88 var(--display); text-transform: uppercase; letter-spacing: -.01em;
      text-wrap: balance;
    }
    .lc .error-texto { margin-top: 24px; max-width: 44ch; font-size: 1.125rem; line-height: 1.5; color: var(--tinta-2); }
    .lc .error-codigo { margin-top: 10px; font-size: .9375rem; color: var(--tinta-2); font-variant-numeric: tabular-nums; }
    .lc .error .acciones { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 32px; }
  `,
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContenidoNoEncontradaLineaComarcalComponent {
  readonly sitio = input.required<SitioWeb>();
}

/**
 * 404 del tema «La línea comarcal» (boceto `fase4-web/boceto-login-404-sin-publicar.html`, §9 n.º 10): cabecera y pie
 * del tema y el cuerpo de la 404. La URL no cambia. Sin publicar, la página «Web aún no publicada»; mientras carga o
 * con error, nada.
 */
@Component({
  selector: 'app-lc-no-encontrada',
  standalone: true,
  imports: [MarcoLineaComarcalComponent, ContenidoNoEncontradaLineaComarcalComponent, SinPublicarComponent],
  template: `
    @if (pagina.estado() === 'no-publicada') {
      @defer (on immediate) {
        <app-web-sin-publicar />
      }
    } @else if (pagina.sitio(); as sitio) {
      <app-lc-marco [sitio]="sitio" pagina="otra">
        <app-lc-no-encontrada-contenido [sitio]="sitio" />
      </app-lc-marco>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoEncontradaLineaComarcalComponent {
  protected readonly pagina = paginaWeb('no-encontrada', () => '/');
}
