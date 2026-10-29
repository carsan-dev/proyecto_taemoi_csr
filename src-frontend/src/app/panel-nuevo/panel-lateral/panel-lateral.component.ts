import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  HostListener,
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
 * quita con un `@if` y, al quitarse, el foco vuelve a quien lo abrió. Con `bloqueado` (un envío en vuelo) no se cierra
 * por ninguna vía: la X va deshabilitada, Esc y el velo no emiten y, si el navegador lo cierra igualmente, se reabre.
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
  /** Un envío en vuelo: el panel no se puede cerrar (hoy, el cargador global tapaba toda la pantalla). */
  @Input() bloqueado = false;
  /** Ancho en px a escritorio (por defecto 480, el del CSS). En la hoja inferior no se usa. */
  @Input() ancho: number | null = null;
  /**
   * `false`: panel no modal (`show()`): sin velo ni foco atrapado, y lo de detrás sigue visible e interactivo. Esc, la X y
   * «cerrar» lo cierran igualmente. Por defecto, modal. Se lee al abrir; no cambia en vida del panel.
   */
  @Input() modal = true;
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('panel', { static: true }) private panelRef!: ElementRef<HTMLDialogElement>;

  readonly id = `pn-panel-${++siguienteId}`;
  hoja = anchoDeHoja();
  private disparador: HTMLElement | null = null;
  private destruido = false;
  private cierrePedido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const panel = this.panelRef.nativeElement;
    this.abrirCapa(panel);
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
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    const panel = this.panelRef.nativeElement;
    if (this.modal) {
      this.pila.cerrar(panel);
    }
    if (panel.open) {
      panel.close();
    }
    devolverFoco(this.disparador);
  }

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.pedirCierre();
  }

  /**
   * El `<dialog>` se cerró sin quitarse (el navegador solo deja cancelar el evento `cancel` una vez por activación del
   * usuario: un segundo Esc lo cierra igualmente). Bloqueado, se vuelve a abrir; si no, se pide el cierre.
   */
  alCerrarNativo(): void {
    if (this.destruido) {
      return;
    }
    if (this.bloqueado) {
      this.abrirCapa(this.panelRef.nativeElement);
      return;
    }
    this.pedirCierre();
  }

  private abrirCapa(panel: HTMLDialogElement): void {
    if (this.modal) {
      panel.showModal();
      this.pila.abrir(panel);
    } else {
      panel.show();
    }
  }

  /**
   * Sin modal, Esc cierra el panel aunque el foco esté en la lista de al lado. Si hay una capa modal encima (un diálogo
   * de confirmación) o Esc ya se atendió, no hace nada.
   */
  @HostListener('document:keydown.escape', ['$event'])
  alEscapeEnElDocumento(evento: Event): void {
    if (this.modal || evento.defaultPrevented || this.pila.arriba()) {
      return;
    }
    if (!this.panelRef.nativeElement.contains(evento.target as Node)) {
      this.pedirCierre();
    }
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.panelRef.nativeElement)) {
      this.pedirCierre();
    }
  }

  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.pedirCierre();
      return;
    }
    if (this.modal) {
      contenerFoco(evento, this.panelRef.nativeElement);
    }
  }

  /** Esc, el velo, la X y el cierre del navegador: emite `cerrar` una sola vez, y nada si está bloqueado. */
  pedirCierre(): void {
    if (this.bloqueado || this.cierrePedido || this.destruido) {
      return;
    }
    this.cierrePedido = true;
    this.cerrar.emit();
  }
}
