import { Pipe, PipeTransform, Signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params } from '@angular/router';

/** Nombre del parámetro de la URL que pide la vista previa del tema (`?vista-previa=<tema>`). */
export const PARAMETRO_VISTA_PREVIA = 'vista-previa';

/**
 * Suma `vista-previa` a los `queryParams` de un enlace cuando la página lo lleva (`previa` no es null; un valor vacío
 * también cuenta). Sin vista previa devuelve `base` tal cual (o null), así que la web publicada genera los mismos enlaces
 * de siempre. Solo conserva este parámetro: los demás de la URL actual no se arrastran.
 */
export function conVistaPrevia(base: Params | null | undefined, previa: string | null): Params | null {
  if (previa === null) {
    return base ?? null;
  }
  return { ...base, [PARAMETRO_VISTA_PREVIA]: previa };
}

/** Valor de `vista-previa` en la URL de la página activa (null si no está), en el contexto de inyección del componente. */
export function valorVistaPrevia(): Signal<string | null> {
  const consulta = toSignal(inject(ActivatedRoute).queryParamMap, { requireSync: true });
  return computed(() => consulta().get(PARAMETRO_VISTA_PREVIA));
}

/**
 * Pieza común de la navegación interna de la plantilla: `[queryParams]="propios | vistaPrevia"` (o `null | vistaPrevia`
 * si el enlace no tiene) hace que el enlace conserve `vista-previa` mientras la página lo lleve. No es pura porque lee la
 * ruta activa, pero devuelve el mismo objeto mientras no cambien ni `propios` ni la vista previa.
 */
@Pipe({ name: 'vistaPrevia', standalone: true, pure: false })
export class VistaPreviaPipe implements PipeTransform {
  private readonly previa = valorVistaPrevia();
  private ultimaBase: Params | null | undefined;
  private ultimaPrevia: string | null | undefined;
  private ultimo: Params | null = null;
  private calculado = false;

  transform(base: Params | null | undefined): Params | null {
    const previa = this.previa();
    if (!this.calculado || base !== this.ultimaBase || previa !== this.ultimaPrevia) {
      this.ultimo = conVistaPrevia(base, previa);
      this.ultimaBase = base;
      this.ultimaPrevia = previa;
      this.calculado = true;
    }
    return this.ultimo;
  }
}
