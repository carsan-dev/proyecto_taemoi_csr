import { DOCUMENT } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
} from '@angular/core';

import { PilaCapas, contenerFoco, devolverFoco, disparadorActual, esClicEnElVelo } from '../capas/capa-modal';

let siguienteId = 0;

/**
 * Capa grande de Eventos (8b-3f) sobre lo común de las capas de 8b-D1: «Ver cómo queda en la web» y la imagen ampliada
 * de la miniatura. Un `<dialog>` con `showModal()` (el resto queda inerte), foco contenido, Esc, el clic en el velo y la
 * X la cierran (emite `cerrar`) y, al quitarse, el foco vuelve a quien la abrió. Por debajo de 768 px, a pantalla
 * completa.
 */
@Component({
  selector: 'pn-eventos-capa',
  standalone: true,
  template: `
    <dialog #capa class="eventos-capa" [class.eventos-capa--imagen]="tipo === 'imagen'" [attr.aria-labelledby]="id + '-titulo'"
      [attr.aria-describedby]="descripcion ? id + '-descripcion' : null" (cancel)="alCancelar($event)" (close)="alCerrarNativo()"
      (click)="alClic($event)" (keydown)="alTeclear($event)">
      <div class="eventos-capa__caja">
        <header class="eventos-capa__cabecera">
          <div class="eventos-capa__titulos">
            <h2 class="eventos-capa__titulo" [id]="id + '-titulo'">{{ titulo }}</h2>
            @if (descripcion) {
            <p class="eventos-capa__descripcion" [id]="id + '-descripcion'">{{ descripcion }}</p>
            }
          </div>
          <button type="button" class="eventos-capa__cerrar" aria-label="Cerrar" (click)="pedirCierre()">
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M6 6l12 12M18 6 6 18"></path>
            </svg>
          </button>
        </header>
        <div class="eventos-capa__cuerpo" tabindex="-1">
          <ng-content />
        </div>
      </div>
    </dialog>
  `,
  styleUrls: ['./eventos-capa.component.scss'],
})
export class EventosCapaComponent implements AfterViewInit, OnDestroy {
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  /** `web`: grande, con la vista pública; `imagen`: la imagen ampliada. */
  @Input() tipo: 'web' | 'imagen' = 'web';
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('capa', { static: true }) private capaRef!: ElementRef<HTMLDialogElement>;

  readonly id = `eventos-capa-${++siguienteId}`;
  private disparador: HTMLElement | null = null;
  private cierrePedido = false;
  private destruida = false;

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const capa = this.capaRef.nativeElement;
    capa.showModal();
    this.pila.abrir(capa);
    capa.querySelector<HTMLElement>('.eventos-capa__cerrar')?.focus();
  }

  ngOnDestroy(): void {
    this.destruida = true;
    const capa = this.capaRef.nativeElement;
    this.pila.cerrar(capa);
    if (capa.open) {
      capa.close();
    }
    devolverFoco(this.disparador);
  }

  alCancelar(evento: Event): void {
    evento.preventDefault();
    this.pedirCierre();
  }

  alCerrarNativo(): void {
    this.pedirCierre();
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.capaRef.nativeElement)) {
      this.pedirCierre();
    }
  }

  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.pedirCierre();
      return;
    }
    contenerFoco(evento, this.capaRef.nativeElement);
  }

  pedirCierre(): void {
    if (this.cierrePedido || this.destruida) {
      return;
    }
    this.cierrePedido = true;
    this.cerrar.emit();
  }
}
