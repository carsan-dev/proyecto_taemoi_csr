import { DOCUMENT } from '@angular/common';
import { Injector, Signal, afterNextRender, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

/**
 * Lleva al ancla de la URL (`/legales#privacidad`, `/eventos#anteriores`) cuando la página ya la ha pintado: la
 * aplicación tiene `anchorScrolling` desactivado y el contenido llega con los datos. `listo` dice cuándo está pintada.
 * Con «reducir movimiento», sin animación; cuando cargan las fuentes, se corrige sin animación.
 */
export function irAlAncla(listo: Signal<boolean>): void {
  const documento = inject(DOCUMENT);
  const injector = inject(Injector);
  const fragmento = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });

  effect(() => {
    const destino = fragmento();
    if (!destino || !listo()) {
      return;
    }
    untracked(() =>
      afterNextRender(
        () => {
          const elemento = documento.getElementById(destino);
          if (!elemento || typeof elemento.scrollIntoView !== 'function') {
            return;
          }
          const reducir = documento.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
          elemento.scrollIntoView({ behavior: reducir ? 'auto' : 'smooth', block: 'start' });
          // Las fuentes del tema pueden llegar después y mover el texto: se vuelve al ancla sin animación.
          if (documento.fonts && documento.fonts.status !== 'loaded') {
            documento.fonts.ready.then(() => elemento.scrollIntoView({ behavior: 'auto', block: 'start' }));
          }
        },
        { injector },
      ),
    );
  });
}
