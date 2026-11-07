import { DestroyRef, Signal, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { EstadoRecurso, SitioWeb } from '../datos/modelos';
import { WebPublicaService } from '../datos/web-publica.service';
import { PaginaWeb, SeoEscuelaService } from './seo-escuela.service';

export interface PaginaWebEstado {
  /** Los datos de la web; null mientras carga, sin publicar o con error. */
  sitio: Signal<SitioWeb | null>;
  estado: Signal<EstadoRecurso<SitioWeb>['estado']>;
  vistaPrevia: Signal<string | null>;
}

/**
 * Datos y SEO de una página de un tema (fase4-web-plan.md §3.1, «comun/»), en el contexto de inyección del componente:
 *
 * - pide `GET /api/publico/web` (o la vista previa, con `?vista-previa=`) con `WebPublicaService`;
 * - con datos, pone el SEO de la página (`ruta` da el `canonical`); la 404 del tema, título y `noindex`;
 * - página de sede (`opciones.slug`): si el slug no es de ninguna sede, el SEO de la 404 del tema (`noindex`);
 * - web sin publicar (404 de la API): solo `noindex`; la página pinta «Web aún no publicada» (§9 n.º 10, boceto
 *   `boceto-login-404-sin-publicar.html`, sección 3) con `SinPublicarComponent`, en la misma URL;
 * - error: nada visible (los bocetos no tienen ese estado) y sin etiquetas de la plantilla.
 *
 * Al salir de la página quita sus etiquetas.
 */
export function paginaWeb(
  pagina: PaginaWeb | 'no-encontrada',
  ruta: () => string,
  opciones: { slug?: Signal<string | null> } = {},
): PaginaWebEstado {
  const activa = inject(ActivatedRoute);
  const web = inject(WebPublicaService);
  const seo = inject(SeoEscuelaService);

  const consulta = toSignal(activa.queryParamMap, { requireSync: true });
  const vistaPrevia = computed(() => consulta().get('vista-previa'));
  /** La petición se lanza (o se reutiliza de la caché) fuera del contexto reactivo: escribe en signals. */
  const recurso = computed(() => {
    const valor = vistaPrevia();
    return untracked(() => web.sitio(valor));
  });
  const estado = computed(() => recurso()().estado);
  const sitio = computed(() => {
    const actual = recurso()();
    return actual.estado === 'listo' ? actual.datos : null;
  });

  effect(() => {
    const actual = recurso()();
    const previa = vistaPrevia();
    const slug = opciones.slug?.() ?? null;
    const destino = ruta();
    untracked(() => {
      switch (actual.estado) {
        case 'listo':
          if (
            pagina === 'no-encontrada' ||
            (pagina === 'sede' && !actual.datos.sedes.some((s) => s.slug != null && s.slug === slug))
          ) {
            seo.noEncontrada(actual.datos);
          } else {
            seo.aplicar({ pagina, sitio: actual.datos, ruta: destino, vistaPrevia: web.esVistaPrevia(previa), slug });
          }
          break;
        case 'no-publicada':
          seo.noPublicada();
          break;
        case 'error':
          seo.limpiar();
          break;
      }
    });
  });
  inject(DestroyRef).onDestroy(() => seo.limpiar());

  return { sitio, estado, vistaPrevia };
}
