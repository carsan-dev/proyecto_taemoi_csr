import { Directive, ElementRef, effect, inject } from '@angular/core';

import { MarcaEscuelaPanelService } from './marca-escuela-panel.service';

/**
 * El acento de la escuela (8b-C) en el `.pn` de una pantalla o capa del panel nuevo. No pide la marca: escribe el
 * acento que dejó la cabecera al aplicarla (`MarcaEscuelaPanelService.acento`). Mientras no hay marca, o con otra
 * sesión, quita las variables y el `.pn` vuelve al acento por defecto de `panel-nuevo.scss`. Solo el acento: el botón
 * principal y el resto del tema de cada pantalla no cambian (decisión 273).
 */
@Directive({
  selector: '[pnAcento]',
  standalone: true,
})
export class AcentoEscuelaDirective {
  constructor() {
    const marca = inject(MarcaEscuelaPanelService);
    const elemento: HTMLElement = inject(ElementRef).nativeElement;
    effect(() => marca.escribirAcento(elemento, marca.acento()));
  }
}
