import { DestroyRef, Signal, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { EstadoRecurso, EventoDetalleWeb, EventoListaWeb, SitioWeb } from '../datos/modelos';
import { WebPublicaService } from '../datos/web-publica.service';
import { EditorWebService, PARAMETRO_EDITOR } from './editor-web';
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
 * - página de evento (`opciones.evento`): con sus datos, su SEO (`Event` con descripción y lugar); si la API no lo da
 *   (`no-encontrado`), el de la 404 del tema; mientras carga o con error, sin etiquetas de la plantilla;
 * - página de eventos (`opciones.eventos`): el `Event` de cada próximo de la lista, cuando llega;
 * - web sin publicar (404 de la API): solo `noindex`; la página pinta «Web aún no publicada» (§9 n.º 10, boceto
 *   `boceto-login-404-sin-publicar.html`, sección 3) con `SinPublicarComponent`, en la misma URL;
 * - error: nada visible (los bocetos no tienen ese estado) y sin etiquetas de la plantilla.
 *
 * Al salir de la página quita sus etiquetas.
 */
export function paginaWeb(
  pagina: PaginaWeb | 'no-encontrada',
  ruta: () => string,
  opciones: {
    slug?: Signal<string | null>;
    evento?: Signal<EstadoRecurso<EventoDetalleWeb>>;
    eventos?: Signal<EventoListaWeb[] | null>;
  } = {},
): PaginaWebEstado {
  const activa = inject(ActivatedRoute);
  const web = inject(WebPublicaService);
  const seo = inject(SeoEscuelaService);
  const editor = inject(EditorWebService);

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
    return actual.estado === 'listo' ? editor.aplicar(actual.datos) : null;
  });

  // W12c: el modo editor de «Web · Contenido» (solo si la API confirma la vista previa de la gestión).
  effect(() => {
    const actual = recurso()();
    const pedido = consulta().get(PARAMETRO_EDITOR);
    untracked(() => editor.activarSiToca(pedido, actual.estado === 'listo' ? actual.datos : null));
  });

  effect(() => {
    const actual = recurso()();
    const previa = vistaPrevia();
    const slug = opciones.slug?.() ?? null;
    const evento = opciones.evento?.() ?? null;
    const eventos = opciones.eventos?.() ?? null;
    const destino = ruta();
    untracked(() => {
      switch (actual.estado) {
        case 'listo':
          if (
            pagina === 'no-encontrada' ||
            (pagina === 'sede' && !actual.datos.sedes.some((s) => s.slug != null && s.slug === slug)) ||
            evento?.estado === 'no-encontrado'
          ) {
            seo.noEncontrada(actual.datos);
          } else if (evento && evento.estado !== 'listo') {
            seo.limpiar();
          } else {
            seo.aplicar({
              pagina,
              sitio: actual.datos,
              ruta: destino,
              vistaPrevia: web.esVistaPrevia(previa),
              slug,
              eventoId: evento?.datos.id ?? null,
              evento: evento?.datos ?? null,
              eventos,
            });
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
