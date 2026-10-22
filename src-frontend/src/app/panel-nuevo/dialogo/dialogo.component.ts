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

import { seguirAcento } from '../acento-escuela.directive';
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

/** Un párrafo o una lista de la explicación (8b-D2b: el html de los diálogos de la ficha, sin su maquetación). */
export type BloqueMensaje = { tipo: 'parrafo'; partes: ParteMensaje[] } | { tipo: 'lista'; elementos: ParteMensaje[][] };

/** Tipos de campo del diálogo (8b-D2b). Las pastillas, `role=radiogroup`; el resto, controles nativos. */
export type TipoCampoDialogo = 'pastillas' | 'select' | 'text' | 'number' | 'date' | 'textarea' | 'email';

/**
 * Campo del diálogo (8b-D2b), además del grupo de pastillas de `campo`. Su valor llega en `valores[id]` (texto; vacío
 * si no hay). El control lleva `id` como id del DOM (en las pastillas, un `<input type="hidden">` con ese id lleva el
 * valor elegido), así que quien lee el DOM por id (un `preConfirm` de SweetAlert2) lee lo mismo.
 */
export interface CampoDialogo {
  id: string;
  tipo: TipoCampoDialogo;
  /** Etiqueta visible encima; sin ella, el campo lo nombra el título del diálogo. */
  etiqueta?: string;
  opciones?: OpcionPastilla[];
  valor?: string | null;
  /** Texto de ayuda del control; en el desplegable, su primera opción vacía. */
  placeholder?: string;
  /** Atributos del control (maxlength, min, max, step…). */
  atributos?: Record<string, string>;
}

/** Error de validación en línea: bajo el campo `campo` o, sin campo, bajo la explicación. */
export interface ErrorCampo {
  campo?: string;
  mensaje: string;
}

/** Valores de los campos al confirmar, por id. */
export type ValoresDialogo = Record<string, string>;

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
  /** Párrafos y listas tras la frase (8b-D2b). */
  bloques?: BloqueMensaje[];
  /** Campos del formulario (8b-D2b). */
  campos?: CampoDialogo[];
  /** Segunda acción («denegar» de SweetAlert2): cierra con `negado`. Destructiva, va en peligro y el foco en «Cancelar». */
  accionSecundaria?: { texto: string; destructivo?: boolean };
  /**
   * Comprobación al pulsar la acción (8b-D2b): un error deja el diálogo abierto con el error en línea; una promesa pone
   * el botón ocupado (sin Esc, velo, X ni «Cancelar») hasta que resuelve. Si la promesa falla, el diálogo se cierra
   * con `error`.
   */
  comprobar?: (valores: ValoresDialogo) => ErrorCampo | null | PromiseLike<ErrorCampo | null>;
  /** Nombre del diálogo en `data-dialogo` (para pruebas y capturas). */
  nombre?: string;
  /** Traducción genérica de un diálogo que no se pudo pintar con sus campos (`data-generico`). */
  generico?: boolean;
}

export interface ResultadoConfirmacion<R = unknown> {
  confirmado: boolean;
  valor: string | null;
  /** Lo que devolvió `ejecutar`. */
  respuesta?: R;
  /** El error de `ejecutar`: el diálogo se cierra y quien lo abrió lo avisa. */
  error?: unknown;
  /** Se pulsó la segunda acción (`accionSecundaria`). */
  negado?: boolean;
  /** Los valores de `campos` al confirmar. */
  valores?: ValoresDialogo;
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
  /** 8b-C: el acento de la escuela en el `.pn` de la capa. */
  private readonly acento = seguirAcento();

  @Input({ required: true }) opciones!: OpcionesConfirmacion;
  @Output() readonly cerrado = new EventEmitter<ResultadoConfirmacion>();

  @ViewChild('dialogo', { static: true }) private dialogoRef!: ElementRef<HTMLDialogElement>;
  @ViewChild('botonAccion', { read: ElementRef }) private botonAccion?: ElementRef<HTMLButtonElement>;
  @ViewChild('botonCancelar') private botonCancelar?: ElementRef<HTMLButtonElement>;

  readonly id = `pn-dialogo-${++siguienteId}`;
  hoja = anchoDeHoja();
  valor: string | null = null;
  ocupado = false;
  /** Valores de `campos`, por id. */
  valores: ValoresDialogo = {};
  /** Error de validación en línea. */
  errorCampo: ErrorCampo | null = null;

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

  /** La frase y, si los hay, los párrafos y listas que la siguen. */
  get descritoPor(): string | null {
    const ids = [
      this.partes.length ? `${this.id}-mensaje` : null,
      this.opciones.bloques?.length ? `${this.id}-bloques` : null,
    ].filter((i): i is string => i !== null);
    return ids.length ? ids.join(' ') : null;
  }

  /** Con alguna acción destructiva, el foco inicial va a «Cancelar». */
  get conPeligro(): boolean {
    return !!this.opciones.destructivo || !!this.opciones.accionSecundaria?.destructivo;
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
    this.valores = {};
    for (const c of this.opciones.campos ?? []) {
      this.valores[c.id] = c.valor ?? '';
    }
    this.cdr.detectChanges();
    const dialogo = this.dialogoRef.nativeElement;
    dialogo.showModal();
    this.pila.abrir(dialogo);
    const inicial = this.conPeligro || this.accionDeshabilitada ? this.botonCancelar : this.botonAccion;
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
    const comprobar = this.opciones.comprobar;
    if (!comprobar) {
      this.seguirConfirmando();
      return;
    }
    const valores = { ...this.valores };
    const comprobacion = comprobar(valores);
    if (!esPromesa(comprobacion)) {
      this.trasComprobar(comprobacion);
      return;
    }
    this.ocupado = true;
    this.cdr.detectChanges();
    comprobacion.then(
      (error) => {
        this.ocupado = false;
        this.trasComprobar(error);
      },
      (error: unknown) => {
        this.ocupado = false;
        this.terminar({ confirmado: true, valor: this.valor, valores, error });
      }
    );
  }

  /** «Denegar»: la segunda acción. */
  negar(): void {
    if (this.ocupado) {
      return;
    }
    this.terminar({ confirmado: false, negado: true, valor: null });
  }

  private trasComprobar(error: ErrorCampo | null): void {
    if (this.destruido || this.terminado) {
      return;
    }
    if (error) {
      this.mostrarError(error);
      return;
    }
    this.seguirConfirmando();
  }

  /** Pinta el error bajo su campo (o bajo la explicación) y lleva el foco al campo. */
  private mostrarError(error: ErrorCampo): void {
    this.errorCampo = error;
    this.cdr.detectChanges();
    const dialogo = this.dialogoRef.nativeElement;
    const control = error.campo ? dialogo.querySelector<HTMLElement>(`[data-campo="${error.campo}"]`) : null;
    const destino = control?.matches('[role="radiogroup"]')
      ? control.querySelector<HTMLElement>('[role="radio"][tabindex="0"]')
      : control;
    (destino ?? this.botonAccion?.nativeElement)?.focus();
  }

  private seguirConfirmando(): void {
    const valor = this.valor;
    const valores = this.opciones.campos ? { ...this.valores } : undefined;
    const ejecutar = this.opciones.ejecutar;
    if (!ejecutar) {
      this.terminar({ confirmado: true, valor, valores });
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

  // ------------------------------------------------------------------ campos (8b-D2b)

  /** Cambia el valor de un campo y quita su error. */
  ponerValor(id: string, valor: string): void {
    this.valores[id] = valor;
    if (this.errorCampo && (this.errorCampo.campo === id || !this.errorCampo.campo)) {
      this.errorCampo = null;
    }
    this.cdr.detectChanges();
  }

  /** Lee el valor de un control nativo (input, select o textarea). */
  alEscribir(id: string, evento: Event): void {
    this.ponerValor(id, (evento.target as HTMLInputElement).value);
  }

  errorDe(id: string): string | null {
    return this.errorCampo?.campo === id && this.errorCampo.mensaje ? this.errorCampo.mensaje : null;
  }

  get errorGeneral(): string | null {
    return this.errorCampo && !this.errorCampo.campo && this.errorCampo.mensaje ? this.errorCampo.mensaje : null;
  }

  /** Tabindex itinerante del grupo de pastillas de un campo. */
  indiceTabCampo(campo: CampoDialogo, i: number): number {
    const opciones = campo.opciones ?? [];
    const elegida = opciones.findIndex((o) => o.valor === this.valores[campo.id]);
    return i === (elegida >= 0 ? elegida : 0) ? 0 : -1;
  }

  /** Flechas (con vuelta), Inicio y Fin en las pastillas de un campo, como un grupo de radios nativo. */
  teclaPastillasCampo(evento: KeyboardEvent, campo: CampoDialogo): void {
    const opciones = campo.opciones ?? [];
    const actual = Math.max(
      opciones.findIndex((o) => o.valor === this.valores[campo.id]),
      0
    );
    const siguiente = siguienteIndice(evento.key, actual, opciones.length);
    if (siguiente === null) {
      return;
    }
    evento.preventDefault();
    this.ponerValor(campo.id, opciones[siguiente].valor);
    const grupo = evento.currentTarget as HTMLElement;
    grupo.querySelectorAll<HTMLElement>('[role="radio"]')[siguiente]?.focus();
  }

  atributo(campo: CampoDialogo, nombre: string): string | null {
    return campo.atributos?.[nombre] ?? null;
  }

  esLista(bloque: BloqueMensaje): bloque is { tipo: 'lista'; elementos: ParteMensaje[][] } {
    return bloque.tipo === 'lista';
  }

  private terminar(resultado: ResultadoConfirmacion): void {
    // Sin claves vacías: el resultado de hoy no las tenía.
    resultado = Object.fromEntries(
      Object.entries(resultado).filter(([, v]) => v !== undefined)
    ) as unknown as ResultadoConfirmacion;
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

function esPromesa<T>(valor: T | PromiseLike<T>): valor is PromiseLike<T> {
  return !!valor && typeof (valor as PromiseLike<T>).then === 'function';
}

/** El índice al que mueven las flechas (con vuelta), Inicio y Fin en un grupo de `total` radios; nulo con otra tecla. */
function siguienteIndice(tecla: string, actual: number, total: number): number | null {
  if (total === 0) {
    return null;
  }
  switch (tecla) {
    case 'ArrowRight':
    case 'ArrowDown':
      return (actual + 1) % total;
    case 'ArrowLeft':
    case 'ArrowUp':
      return (actual - 1 + total) % total;
    case 'Home':
      return 0;
    case 'End':
      return total - 1;
    default:
      return null;
  }
}
