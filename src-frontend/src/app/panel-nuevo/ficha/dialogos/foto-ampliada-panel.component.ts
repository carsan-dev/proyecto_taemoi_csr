import { DOCUMENT } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';

import { PilaCapas, contenerFoco, devolverFoco, disparadorActual, esClicEnElVelo } from '../../capas/capa-modal';

/**
 * Foto del alumno ampliada en el panel nuevo (8b-D2b): una caja de luz sobre la capa modal (`<dialog>` con
 * `showModal()`), con el velo, la foto centrada de `min(90vw, 960px)` × `90dvh` como mucho y una X de 44 px. Esc, la X y
 * el clic en el velo la cierran (`cerrar`; quien la usa la quita con un `@if`) y el foco vuelve a quien la abrió. La
 * ficha de hoy sigue con su `<dialog id="imageModal">` (`FotoAmpliadaComponent`).
 */
@Component({
  selector: 'pn-foto-ampliada',
  standalone: true,
  template: `<dialog #caja class="pn-foto" aria-label="Foto ampliada del alumno" (cancel)="alCancelar($event)"
    (close)="alCerrarNativo()" (click)="alClic($event)" (keydown)="alTeclear($event)">
    <img class="pn-foto__imagen" [src]="url" [alt]="alt" decoding="async" />
    <button #botonCerrar type="button" class="pn-foto__cerrar" aria-label="Cerrar foto ampliada" (click)="pedirCierre()">
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <path d="M6 6l12 12M18 6 6 18"></path>
      </svg>
    </button>
  </dialog>`,
  styleUrls: ['./foto-ampliada-panel.component.scss'],
})
export class FotoAmpliadaPanelComponent implements AfterViewInit, OnDestroy {
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);

  @Input({ required: true }) url = '';
  @Input() alt = 'Foto del alumno';
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('caja', { static: true }) private cajaRef!: ElementRef<HTMLDialogElement>;
  @ViewChild('botonCerrar', { static: true }) private botonCerrar!: ElementRef<HTMLButtonElement>;

  private disparador: HTMLElement | null = null;
  private cierrePedido = false;
  private destruida = false;

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const caja = this.cajaRef.nativeElement;
    caja.showModal();
    this.pila.abrir(caja);
    this.botonCerrar.nativeElement.focus();
  }

  ngOnDestroy(): void {
    this.destruida = true;
    const caja = this.cajaRef.nativeElement;
    this.pila.cerrar(caja);
    if (caja.open) {
      caja.close();
    }
    devolverFoco(this.disparador);
  }

  alCancelar(evento: Event): void {
    evento.preventDefault();
    this.pedirCierre();
  }

  /** El navegador la cerró (un segundo Esc): cuenta como cerrar. */
  alCerrarNativo(): void {
    if (!this.destruida) {
      this.pedirCierre();
    }
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.cajaRef.nativeElement)) {
      this.pedirCierre();
    }
  }

  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.pedirCierre();
      return;
    }
    contenerFoco(evento, this.cajaRef.nativeElement);
  }

  pedirCierre(): void {
    if (this.cierrePedido || this.destruida) {
      return;
    }
    this.cierrePedido = true;
    this.cerrar.emit();
  }
}
