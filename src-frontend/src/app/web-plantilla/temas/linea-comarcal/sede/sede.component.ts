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
import { map } from 'rxjs';

import { EditorWebService } from '../../../comun/editor-web';
import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { RELOJ_WEB } from '../../../comun/reloj';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { CLAVE_SEDE_RECORDADA, claveSede } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { ContenidoNoEncontradaLineaComarcalComponent } from '../no-encontrada/no-encontrada.component';
import { TEXTOS_PAGINAS_LINEA_COMARCAL } from '../textos';
import { sedeDelSlug, vistaPaginaSede } from './sede';

/**
 * Página de una sede del tema «La línea comarcal» (`/sedes/:slug`, boceto `fase4-web/boceto-linea-comarcal-sede.html`)
 * con los datos de `GET /api/publico/web`. Un slug que no es de ninguna sede pinta la 404 del tema (`noindex`). La sede
 * vista se recuerda, como al abrirla en la línea de la portada. Al pasar de una sede a otra el componente se reutiliza:
 * todo sale del slug de la ruta.
 */
@Component({
  selector: 'app-lc-sede',
  standalone: true,
  imports: [VistaPreviaPipe, 
    MarcoLineaComarcalComponent,
    ContenidoNoEncontradaLineaComarcalComponent,
    NgTemplateOutlet,
    RouterLink,
    SinPublicarComponent,
  ],
  templateUrl: './sede.component.html',
  styleUrl: './sede.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SedeLineaComarcalComponent {
  private readonly reloj = inject(RELOJ_WEB);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly activa = inject(ActivatedRoute);
  private readonly editor = inject(EditorWebService);
  private readonly fragmento = toSignal(this.activa.fragment, { initialValue: null });
  private readonly slug = toSignal(this.activa.paramMap.pipe(map((p) => p.get('slug'))), { requireSync: true });

  protected readonly textos = TEXTOS_PAGINAS_LINEA_COMARCAL;
  protected readonly pagina = paginaWeb('sede', () => `/sedes/${encodeURIComponent(this.slug() ?? '')}`, { slug: this.slug });
  private readonly sede = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? sedeDelSlug(sitio, this.slug()) : null;
  });
  protected readonly vista = computed(() => {
    const sitio = this.pagina.sitio();
    const sede = this.sede();
    // W12c-ter: «Sin clases esta semana» en el editor de la web; fuera de él, la vista tal cual.
    return sitio && sede ? this.editor.aplicarVistaSede(vistaPaginaSede(sitio, sede, this.reloj())) : null;
  });

  constructor() {
    effect(() => {
      const sede = this.sede();
      if (sede) {
        untracked(() => this.recordar(claveSede(sede)));
      }
    });
    effect(() => {
      const fragmento = this.fragmento();
      if (!fragmento || !this.vista()) {
        return;
      }
      untracked(() => afterNextRender(() => this.irA(fragmento), { injector: this.injector }));
    });
  }

  protected gruposDe(nombre: string): string {
    return this.textos.gruposDe.replace('{x}', nombre);
  }

  private recordar(clave: string): void {
    try {
      this.documento.defaultView?.localStorage.setItem(CLAVE_SEDE_RECORDADA, clave);
    } catch {
      /* sin almacenamiento */
    }
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
