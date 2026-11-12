import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  ViewEncapsulation,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { irAlAncla } from '../../../comun/ancla';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { paginaWeb } from '../../../comun/pagina-web';
import { WebPublicaService } from '../../../datos/web-publica.service';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { TextosTema } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { textosLegales, vistaLegales } from './legales';

/**
 * Legales del tema «La línea comarcal» (`/legales`, boceto `fase4-web/boceto-linea-comarcal-legales.html`): aviso legal,
 * privacidad, cookies y normas del club en una página, con anclas `#aviso-legal`, `#privacidad`, `#cookies` y `#normas`
 * (las rutas legales de Moiskimdo redirigen aquí). El texto es el del boceto con los datos de
 * `GET /api/publico/web/legal`; «Última actualización», la fecha de publicación (§9 n.º 14). El índice marca la sección
 * que se está leyendo. Sin normas, su sección no se pinta.
 */
@Component({
  selector: 'app-lc-legales',
  standalone: true,
  imports: [VistaPreviaPipe, MarcoLineaComarcalComponent, RouterLink, SinPublicarComponent],
  templateUrl: './legales.component.html',
  styleUrl: './legales.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalesLineaComarcalComponent {
  private readonly web = inject(WebPublicaService);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  protected readonly pagina = paginaWeb('legales', () => '/legales');
  /** La petición se lanza (o se reutiliza de la caché) fuera del contexto reactivo: escribe en signals. */
  private readonly recurso = computed(() => {
    const previa = this.pagina.vistaPrevia();
    return untracked(() => this.web.legal(previa));
  });
  protected readonly vista = computed(() => {
    const actual = this.recurso()();
    return actual.estado === 'listo' ? vistaLegales(actual.datos) : null;
  });
  /** W12d-bis: los textos legales de la escuela (`legal.*`) con los del tema por defecto. */
  private readonly textosPagina = computed(() =>
    textosLegales(new TextosTema(this.pagina.sitio()), this.vista()?.titular ?? ''),
  );
  protected readonly indice = computed(() => {
    const t = this.textosPagina();
    const secciones = [
      { id: 'aviso-legal', texto: t.avisoLegal },
      { id: 'privacidad', texto: t.privacidad },
      { id: 'cookies', texto: t.cookies },
    ];
    return this.vista()?.normas.length ? [...secciones, { id: 'normas', texto: t.normas }] : secciones;
  });
  protected readonly seccionActual = signal('aviso-legal');

  /** Lo que lee la plantilla (`textos.x`); al leer la señal, la plantilla se repinta si cambian los textos. */
  protected get textos(): ReturnType<typeof textosLegales> {
    return this.textosPagina();
  }

  constructor() {
    const pintada = computed(() => !!this.pagina.sitio() && !!this.vista());
    irAlAncla(pintada);

    let observador: IntersectionObserver | null = null;
    effect(() => {
      if (!pintada()) {
        return;
      }
      untracked(() =>
        afterNextRender(
          () => {
            const fragmento = this.documento.defaultView?.location.hash.slice(1);
            if (fragmento && this.indice().some((x) => x.id === fragmento)) {
              this.seccionActual.set(fragmento);
            }
            observador?.disconnect();
            observador = this.observarSecciones();
          },
          { injector: this.injector },
        ),
      );
    });
    inject(DestroyRef).onDestroy(() => observador?.disconnect());
  }

  /** Marca en el índice la primera sección visible bajo la cabecera (como el boceto). */
  private observarSecciones(): IntersectionObserver | null {
    const Observador = this.documento.defaultView?.IntersectionObserver;
    if (!Observador) {
      return null;
    }
    const visibles: Record<string, boolean> = {};
    const observador = new Observador(
      (entradas) => {
        for (const entrada of entradas) {
          visibles[entrada.target.id] = entrada.isIntersecting;
        }
        const primera = this.indice().find((x) => visibles[x.id]);
        if (primera) {
          this.seccionActual.set(primera.id);
        }
      },
      { rootMargin: `-${72 + 24}px 0px -55% 0px` },
    );
    for (const seccion of Array.from(this.documento.querySelectorAll('.p-legales .legal-seccion'))) {
      observador.observe(seccion);
    }
    return observador;
  }
}
