import { Directive, ElementRef, Injectable, effect, inject, signal } from '@angular/core';

import { AcentoEscuela } from './acento-escuela';

/** Variables del acento (8b-C, decisión 273) bajo `.pn`. */
const VARIABLES_ACENTO: Record<keyof AcentoEscuela, string> = {
  acento: '--pn-acento',
  acentoSuave: '--pn-acento-suave',
  acentoBorde: '--pn-acento-borde',
};

/**
 * El acento de la escuela de la última marca aplicada (lo pone `MarcaEscuelaPanelService.aplicarEn`); `null` antes de
 * aplicarla o tras cambiar de sesión. Sin dependencias: cualquier pantalla o capa lo lee sin pedir nada.
 */
@Injectable({ providedIn: 'root' })
export class AcentoEscuelaActual {
  private readonly valor = signal<AcentoEscuela | null>(null);
  readonly acento = this.valor.asReadonly();

  poner(acento: AcentoEscuela | null): void {
    this.valor.set(acento);
  }
}

/**
 * Escribe el acento en el elemento `.pn` (con `null` lo quita y queda el de `panel-nuevo.scss`). Lanza si el elemento no
 * es un `.pn`, o es `documentElement` o `body`: nada del acento llega a `:root`.
 */
export function escribirAcento(elemento: HTMLElement, acento: AcentoEscuela | null): void {
  const documento = elemento.ownerDocument;
  if (elemento === documento.documentElement || elemento === documento.body || !elemento.classList.contains('pn')) {
    throw new Error('El acento solo se escribe en un elemento .pn del panel nuevo');
  }
  for (const [clave, variable] of Object.entries(VARIABLES_ACENTO) as [keyof AcentoEscuela, string][]) {
    if (acento) {
      elemento.style.setProperty(variable, acento[clave]);
    } else {
      elemento.style.removeProperty(variable);
    }
  }
}

/**
 * El acento de la escuela (8b-C) en el `.pn` de una pantalla o capa del panel nuevo. No pide la marca: escribe el
 * acento que dejó la cabecera al aplicarla. Mientras no hay marca, o con otra sesión, quita las variables y el `.pn`
 * vuelve al acento por defecto de `panel-nuevo.scss`. Solo el acento: el botón principal y el resto del tema de cada
 * pantalla no cambian (decisión 273).
 */
@Directive({
  selector: '[pnAcento]',
  standalone: true,
})
export class AcentoEscuelaDirective {
  constructor() {
    const actual = inject(AcentoEscuelaActual);
    const elemento: HTMLElement = inject(ElementRef).nativeElement;
    effect(() => escribirAcento(elemento, actual.acento()));
  }
}
