import { Directive, ElementRef, Injectable, effect, inject, signal } from '@angular/core';

import { TODAS_LAS_VARIABLES, TemaAplicado, variablesDelTema } from './tema-escuela';

/**
 * El tema y el acento de la escuela de la última marca aplicada (los pone `MarcaEscuelaPanelService.aplicarEn`); `null`
 * antes de aplicarla o tras cambiar de sesión. Sin dependencias: cualquier pantalla o capa lo lee sin pedir nada.
 */
@Injectable({ providedIn: 'root' })
export class AcentoEscuelaActual {
  private readonly valor = signal<TemaAplicado | null>(null);
  readonly aplicado = this.valor.asReadonly();

  poner(aplicado: TemaAplicado | null): void {
    this.valor.set(aplicado);
  }
}

/**
 * Escribe el tema de la escuela en el elemento `.pn`: las cinco variables de la marca y las tres del acento. Con `null`
 * quita las ocho y queda el tema por defecto de `panel-nuevo.scss`. Lanza si el elemento no es un `.pn`, o es
 * `documentElement` o `body`: nada del tema llega a `:root`.
 */
export function escribirTema(elemento: HTMLElement, aplicado: TemaAplicado | null): void {
  const documento = elemento.ownerDocument;
  if (elemento === documento.documentElement || elemento === documento.body || !elemento.classList.contains('pn')) {
    throw new Error('El tema solo se escribe en un elemento .pn del panel nuevo');
  }
  const variables = variablesDelTema(aplicado);
  for (const variable of TODAS_LAS_VARIABLES) {
    if (variable in variables) {
      elemento.style.setProperty(variable, variables[variable]);
    } else {
      elemento.style.removeProperty(variable);
    }
  }
}

/**
 * Desde el contexto de inyección de un componente o directiva: mantiene el tema y el acento de la escuela en su elemento
 * anfitrión (un `.pn`).
 * Las capas con la clase `.pn` en el anfitrión (diálogo, avisos) lo llaman en vez de `hostDirectives`, que añadiría su
 * soporte al bundle inicial.
 */
export function seguirAcento(): void {
  const actual = inject(AcentoEscuelaActual);
  const elemento: HTMLElement = inject(ElementRef).nativeElement;
  effect(() => escribirTema(elemento, actual.aplicado()));
}

/**
 * El tema y el acento de la escuela (8b-C) en el `.pn` de una pantalla o capa del panel nuevo. No pide la marca:
 * escribe lo que dejó la cabecera al aplicarla. Cada pantalla tiene su propio `.pn`, aparte del de la cabecera, y
 * `:where(.pn)` redeclara el tema por defecto en cada uno: sin esto, el botón principal saldría con `#1A2230` (decisión
 * 239: el color de la escuela va en la cabecera y en el botón principal). Mientras no hay marca, o con otra sesión,
 * quita las variables y el `.pn` vuelve al tema por defecto de `panel-nuevo.scss`.
 */
@Directive({
  selector: '[pnAcento]',
  standalone: true,
})
export class AcentoEscuelaDirective {
  constructor() {
    seguirAcento();
  }
}
