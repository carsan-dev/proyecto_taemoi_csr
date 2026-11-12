import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { irAlAncla } from '../../../comun/ancla';
import { EditorWebService } from '../../../comun/editor-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { paginaWeb } from '../../../comun/pagina-web';
import { WebPublicaService } from '../../../datos/web-publica.service';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { TextosTema } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { textosEventos, vistaEventos } from './eventos';

/**
 * Lista de eventos del tema «La línea comarcal» (`/eventos`, boceto `fase4-web/boceto-linea-comarcal-eventos.html`) con
 * `GET /api/publico/web/eventos`: el primer próximo destacado, el resto en filas y los anteriores por año. Sin
 * próximos, «No hay eventos próximos»; sin ninguno, tampoco la sección de anteriores. Sin publicar, «Web aún no
 * publicada»; mientras carga o con error, nada.
 */
@Component({
  selector: 'app-lc-eventos',
  standalone: true,
  imports: [VistaPreviaPipe, MarcoLineaComarcalComponent, NgTemplateOutlet, RouterLink, SinPublicarComponent],
  templateUrl: './eventos.component.html',
  styleUrl: './eventos.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventosLineaComarcalComponent {
  private readonly web = inject(WebPublicaService);
  /** «Web · Contenido» (W12c-bis): en el editor, «Ver el caso» puede cambiar la lista en memoria. */
  private readonly editor = inject(EditorWebService);

  /** La petición se lanza (o se reutiliza de la caché) fuera del contexto reactivo: escribe en signals. */
  private readonly recurso = computed(() => {
    const previa = this.pagina.vistaPrevia();
    return untracked(() => this.web.eventos(previa));
  });
  private readonly eventos = computed(() => {
    const actual = this.recurso()();
    return actual.estado === 'listo' ? this.editor.aplicarEventos(actual.datos) : null;
  });
  protected readonly pagina = paginaWeb('eventos', () => '/eventos', {
    eventos: computed(() => this.eventos()?.proximos ?? null),
  });
  /** W12d-bis: los textos de la escuela (`eventos.*`) con los del tema por defecto. */
  private readonly textosTema = computed(() => new TextosTema(this.pagina.sitio()));
  private readonly textosPagina = computed(() => textosEventos(this.textosTema()));
  protected readonly vista = computed(() => {
    const eventos = this.eventos();
    return eventos ? vistaEventos(eventos, this.textosTema()) : null;
  });

  /** Lo que lee la plantilla (`textos.x`); al leer la señal, la plantilla se repinta si cambian los textos. */
  protected get textos(): ReturnType<typeof textosEventos> {
    return this.textosPagina();
  }

  constructor() {
    irAlAncla(computed(() => !!this.pagina.sitio() && !!this.vista()));
  }
}
