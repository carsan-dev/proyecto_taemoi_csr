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
import { Observable, Subscription } from 'rxjs';

import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { CONSULTA_HOJA, PilaCapas, anchoDeHoja, contenerFoco, esClicEnElVelo } from '../capas/capa-modal';

/** Una pastilla del grupo `role=radiogroup` (método de pago, deporte que se da de alta…). */
export interface OpcionPastilla {
  valor: string;
  texto: string;
}

/** Campo opcional del diálogo: un grupo de pastillas de una sola elección. */
export interface CampoPastillas {
  etiqueta: string;
  opciones: OpcionPastilla[];
  /** La elegida al abrir; nula, ninguna. */
  valor?: string | null;
  /** Sin elección, la acción queda deshabilitada. */
  requerido?: boolean;
}

/** Trozo de la frase de consecuencia: texto o un nombre en negrita. */
export type ParteMensaje = string | { fuerte: string };

export interface OpcionesConfirmacion<R = unknown> {
  titulo: string;
  /** Frase de consecuencia: qué pasa si se confirma. */
  mensaje?: string | ParteMensaje[];
  /** Concepto e importe del cobro, con la cifra grande. */
  destacado?: { etiqueta: string; valor: string };
  campo?: CampoPastillas;
  accion: string;
  /** Texto del botón mientras `ejecutar` está en vuelo («Cobrando…»). */
  accionOcupada?: string;
  cancelar?: string;
  /** La acción va en peligro y el foco inicial en «Cancelar». */
  destructivo?: boolean;
  /** Si se da, el diálogo sigue abierto con el botón ocupado hasta que termine. */
  ejecutar?: (valor: string | null) => Observable<R>;
}

export interface ResultadoConfirmacion<R = unknown> {
  confirmado: boolean;
  valor: string | null;
  /** Lo que devolvió `ejecutar`. */
  respuesta?: R;
  /** El error de `ejecutar`: el diálogo se cierra y quien lo abrió lo avisa. */
  error?: unknown;
}

let siguienteId = 0;

/**
 * Diálogo de confirmación del panel nuevo (8b-D1, decisión 271; boceto `Main.dc.html`, `Baja.dc.html` y
 * `HojaMovil.dc.html`). Un `<dialog>` con `showModal()`, centrado y de 440 px sobre un velo de tinta al 40 %; por
 * debajo de 768 px es una hoja inferior con asa y los botones a ancho completo (la acción arriba). Esc, el clic fuera o
 * la X cancelan sin hacer nada (decisiones 235 y 248). Lo crea y lo destruye `ConfirmacionService`.
 */
@Component({
  selector: 'pn-dialogo',
  standalone: true,
  imports: [BotonOcupadoComponent],
  templateUrl: './dialogo.component.html',
  styleUrls: ['./dialogo.component.scss'],
  host: { class: 'pn pn-capa' },
})
export class DialogoComponent implements AfterViewInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);

  @Input({ required: true }) opciones!: OpcionesConfirmacion;
  @Output() readonly cerrado = new EventEmitter<ResultadoConfirmacion>();

  @ViewChild('dialogo', { static: true }) private dialogoRef!: ElementRef<HTMLDialogElement>;
  @ViewChild('botonAccion', { read: ElementRef }) private botonAccion?: ElementRef<HTMLButtonElement>;
  @ViewChild('botonCancelar') private botonCancelar?: ElementRef<HTMLButtonElement>;

  readonly id = `pn-dialogo-${++siguienteId}`;
  hoja = anchoDeHoja();
  valor: string | null = null;
  ocupado = false;

  private terminado = false;
  private destruido = false;
  private ejecucion?: Subscription;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  get partes(): ParteMensaje[] {
    const m = this.opciones.mensaje;
    return m === undefined ? [] : typeof m === 'string' ? [m] : m;
  }

  get accionDeshabilitada(): boolean {
    return !!this.opciones.campo?.requerido && this.valor === null;
  }

  esFuerte(parte: ParteMensaje): parte is { fuerte: string } {
    return typeof parte !== 'string';
  }

  ngAfterViewInit(): void {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    this.ejecucion?.unsubscribe();
    this.pila.cerrar(this.dialogoRef.nativeElement);
    if (this.dialogoRef.nativeElement.open) {
      this.dialogoRef.nativeElement.close();
    }
  }

  /** Abre el diálogo como modal y pone el foco: en «Cancelar» si es destructivo, en la acción si no. */
  abrir(): void {
    this.valor = this.opciones.campo?.valor ?? null;
    this.cdr.detectChanges();
    const dialogo = this.dialogoRef.nativeElement;
    dialogo.showModal();
    this.pila.abrir(dialogo);
    const inicial = this.opciones.destructivo || this.accionDeshabilitada ? this.botonCancelar : this.botonAccion;
    inicial?.nativeElement.focus();
  }

  /** Esc del navegador (evento `cancel`): cancela, salvo con la acción en vuelo. */
  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.cancelar();
  }

  /**
   * El `<dialog>` se cerró sin pasar por `terminar` (el navegador solo deja cancelar el evento `cancel` una vez por
   * activación del usuario: un segundo Esc lo cierra igualmente). Con la acción en vuelo se vuelve a abrir, con el foco
   * en la acción ocupada; si no, cuenta como cancelar.
   */
  alCerrarNativo(): void {
    if (this.terminado || this.destruido) {
      return;
    }
    if (this.ocupado) {
      this.dialogoRef.nativeElement.showModal();
      this.botonAccion?.nativeElement.focus();
      return;
    }
    this.terminar({ confirmado: false, valor: null });
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.dialogoRef.nativeElement)) {
      this.cancelar();
    }
  }

  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cancelar();
      return;
    }
    contenerFoco(evento, this.dialogoRef.nativeElement);
  }

  cancelar(): void {
    if (this.ocupado) {
      return;
    }
    this.terminar({ confirmado: false, valor: null });
  }

  confirmar(): void {
    if (this.ocupado || this.accionDeshabilitada) {
      return;
    }
    const valor = this.valor;
    const ejecutar = this.opciones.ejecutar;
    if (!ejecutar) {
      this.terminar({ confirmado: true, valor });
      return;
    }
    this.ocupado = true;
    this.cdr.detectChanges();
    this.ejecucion = ejecutar(valor).subscribe({
      next: (respuesta) => {
        this.ocupado = false;
        this.terminar({ confirmado: true, valor, respuesta });
      },
      error: (error) => {
        this.ocupado = false;
        this.terminar({ confirmado: true, valor, error });
      },
    });
  }

  // ------------------------------------------------------------------ pastillas (radiogroup)

  elegir(valor: string): void {
    this.valor = valor;
    this.cdr.detectChanges();
  }

  /** Tabindex itinerante: la elegida (o la primera si no hay) es la única con 0. */
  indiceTab(i: number): number {
    const opciones = this.opciones.campo?.opciones ?? [];
    const elegida = opciones.findIndex((o) => o.valor === this.valor);
    return i === (elegida >= 0 ? elegida : 0) ? 0 : -1;
  }

  /** Flechas (con vuelta), Inicio y Fin mueven la elección y el foco, como un grupo de radios nativo. */
  teclaPastillas(evento: KeyboardEvent): void {
    const opciones = this.opciones.campo?.opciones ?? [];
    if (opciones.length === 0) {
      return;
    }
    const actual = Math.max(
      opciones.findIndex((o) => o.valor === this.valor),
      0
    );
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        siguiente = (actual + 1) % opciones.length;
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        siguiente = (actual - 1 + opciones.length) % opciones.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = opciones.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.elegir(opciones[siguiente].valor);
    const grupo = evento.currentTarget as HTMLElement;
    grupo.querySelectorAll<HTMLElement>('[role="radio"]')[siguiente]?.focus();
  }

  private terminar(resultado: ResultadoConfirmacion): void {
    if (this.terminado) {
      return;
    }
    this.terminado = true;
    const dialogo = this.dialogoRef.nativeElement;
    this.pila.cerrar(dialogo);
    if (dialogo.open) {
      dialogo.close();
    }
    this.cerrado.emit(resultado);
  }
}
