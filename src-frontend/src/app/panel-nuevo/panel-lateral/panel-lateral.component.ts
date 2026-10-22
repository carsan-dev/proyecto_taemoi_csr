import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';

import {
  CONSULTA_HOJA,
  PilaCapas,
  anchoDeHoja,
  contenerFoco,
  devolverFoco,
  disparadorActual,
  enfocables,
  esClicEnElVelo,
} from '../capas/capa-modal';

let siguienteId = 0;

/**
 * Panel lateral del panel nuevo (8b-D1, decisión 270; boceto `InformesPanel.dc.html`): un `<dialog>` modal de 480 px a la
 * derecha, bajo la cabecera, con cabecera (título, descripción y X), cuerpo con desplazamiento propio y pie fijo
 * (`[pnPie]`). Por debajo de 768 px es una hoja inferior. Esc, el clic fuera y la X emiten `cerrar`; quien lo usa lo
 * quita con un `@if` y, al quitarse, el foco vuelve a quien lo abrió.
 */
@Component({
  selector: 'pn-panel-lateral',
  standalone: true,
  templateUrl: './panel-lateral.component.html',
  styleUrls: ['./panel-lateral.component.scss'],
})
export class PanelLateralComponent implements AfterViewInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('panel', { static: true }) private panelRef!: ElementRef<HTMLDialogElement>;

  readonly id = `pn-panel-${++siguienteId}`;
  hoja = anchoDeHoja();
  private disparador: HTMLElement | null = null;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const panel = this.panelRef.nativeElement;
    panel.showModal();
    this.pila.abrir(panel);
    // El foco, en el primer campo del cuerpo (o en la X si no tiene).
    const cuerpo = panel.querySelector<HTMLElement>('.pn-panel__cuerpo');
    const primero = (cuerpo ? enfocables(cuerpo)[0] : undefined) ?? enfocables(panel)[0];
    primero?.focus();
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
  }

  ngOnDestroy(): void {
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    const panel = this.panelRef.nativeElement;
    this.pila.cerrar(panel);
    if (panel.open) {
      panel.close();
    }
    devolverFoco(this.disparador);
  }

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.cerrar.emit();
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.panelRef.nativeElement)) {
      this.cerrar.emit();
    }
  }

  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrar.emit();
      return;
    }
    contenerFoco(evento, this.panelRef.nativeElement);
  }
}
