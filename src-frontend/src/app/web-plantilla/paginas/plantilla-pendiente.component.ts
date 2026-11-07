import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { PaginaWeb, SeoEscuelaService } from '../comun/seo-escuela.service';
import { WebPublicaService } from '../datos/web-publica.service';

/**
 * Pantalla técnica provisional del armazón (fase4-web-plan.md §6, «Armazón»): todas las rutas de la plantilla apuntan
 * aquí hasta que llegue el primer tema (W5). Sin estilo ni texto a propósito: el diseño es de los temas.
 *
 * Carga los datos de su página (`data.pagina` de la ruta; Legales pide también `/legal`) y pone su SEO. Con
 * `?vista-previa=<tema>`, la vista previa (§3.3). Al salir de la plantilla quita sus etiquetas.
 */
@Component({
  selector: 'app-plantilla-pendiente',
  standalone: true,
  template: '<main id="contenido"></main>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlantillaPendienteComponent {
  private readonly ruta = inject(ActivatedRoute);
  private readonly web = inject(WebPublicaService);
  private readonly seo = inject(SeoEscuelaService);

  private readonly pagina = this.ruta.snapshot.data['pagina'] as PaginaWeb;
  private readonly parametros = toSignal(this.ruta.paramMap, { requireSync: true });
  private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
  private readonly vistaPrevia = computed(() => this.consulta().get('vista-previa'));

  /** La petición se lanza (o se reutiliza de la caché) fuera del contexto reactivo: escribe en signals. */
  private readonly sitio = computed(() => {
    const vistaPrevia = this.vistaPrevia();
    return untracked(() => this.web.sitio(vistaPrevia));
  });
  private readonly legal = computed(() => {
    const vistaPrevia = this.vistaPrevia();
    return this.pagina === 'legales' ? untracked(() => this.web.legal(vistaPrevia)) : null;
  });

  constructor() {
    effect(() => {
      this.legal();
      const estado = this.sitio()();
      const parametros = this.parametros();
      const vistaPrevia = this.vistaPrevia();
      untracked(() => {
        switch (estado.estado) {
          case 'listo': {
            const slug = parametros.get('slug');
            const idTexto = parametros.get('id');
            const id = idTexto === null ? Number.NaN : Number(idTexto);
            this.seo.aplicar({
              pagina: this.pagina,
              sitio: estado.datos,
              ruta: this.rutaCanonica(slug, idTexto),
              vistaPrevia: this.web.esVistaPrevia(vistaPrevia),
              slug,
              eventoId: Number.isInteger(id) ? id : null,
            });
            break;
          }
          case 'no-publicada':
            this.seo.noPublicada();
            break;
          case 'error':
            this.seo.limpiar();
            break;
        }
      });
    });
    inject(DestroyRef).onDestroy(() => this.seo.limpiar());
  }

  /** La ruta de la página sin parámetros de consulta ni ancla. */
  private rutaCanonica(slug: string | null, id: string | null): string {
    switch (this.pagina) {
      case 'portada':
        return '/';
      case 'sede':
        return `/sedes/${encodeURIComponent(slug ?? '')}`;
      case 'evento':
        return `/eventos/${encodeURIComponent(id ?? '')}`;
      default:
        return `/${this.pagina}`;
    }
  }
}
