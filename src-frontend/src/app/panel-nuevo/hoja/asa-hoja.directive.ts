import { Directive, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, inject } from '@angular/core';

import { menosMovimiento } from '../capas/capa-modal';

/** Distancia que cierra la hoja: 120 px, o el 30 % de su alto si es más baja. */
const UMBRAL_PX = 120;
const UMBRAL_PROPORCION = 0.3;
/** Gesto rápido: al soltar baja a 0,5 px/ms o más y ya recorrió 24 px. */
const VELOCIDAD_CIERRE = 0.5;
const RECORRIDO_RAPIDO = 24;
/** Ventana con la que se mide la velocidad final del dedo. */
const VENTANA_VELOCIDAD_MS = 100;
const SALIDA_MS = 160;
const VUELTA_MS = 180;

interface Muestra {
  t: number;
  dy: number;
}

/**
 * Decisión d546: las hojas inferiores del panel se cierran deslizando el asa hacia abajo. Va en el elemento del asa y
 * recibe la hoja que se desplaza (`[pnAsaHoja]="hoja"`, el elemento que se ve como hoja). La hoja sigue al dedo solo hacia
 * abajo; al soltar, se cierra si bajó `min(120 px, 30 % de su alto)` o si el gesto fue rápido (≥ 0,5 px/ms con ≥ 24 px):
 * termina de salir (160 ms) y emite `deslizada`, que cada hoja conecta con su camino de cerrar sin elegir. Si no, vuelve
 * (180 ms). Con `prefers-reduced-motion: reduce`, sin animaciones. Al acabar quita sus estilos en línea: la hoja se
 * puede volver a abrir. Lo que evita que se mueva la página de detrás es el `touch-action: none` del asa (mixin
 * `asa-deslizable` de `capas/_capas.scss`).
 */
@Directive({
  selector: '[pnAsaHoja]',
  standalone: true,
})
export class AsaHojaDirective implements OnDestroy {
  private readonly asa: HTMLElement = inject(ElementRef<HTMLElement>).nativeElement;

  /** La hoja que se desplaza con el dedo. */
  @Input({ alias: 'pnAsaHoja', required: true }) hoja: HTMLElement | null | undefined = null;
  /** La hoja se deslizó fuera: hay que cerrarla. */
  @Output() readonly deslizada = new EventEmitter<void>();

  private puntero: number | null = null;
  /** La hoja con la que empezó el gesto (aunque cambie la entrada a mitad). */
  private arrastrada: HTMLElement | null = null;
  private inicioY = 0;
  private dy = 0;
  private muestras: Muestra[] = [];
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private saliendo = false;

  @HostListener('pointerdown', ['$event'])
  alBajar(evento: PointerEvent): void {
    const hoja = this.hoja;
    if (!evento.isPrimary || evento.button !== 0 || this.puntero !== null || this.saliendo || !hoja) {
      return;
    }
    // Si la hoja aún volvía a su sitio, se recoge donde está.
    const desde = this.temporizador !== null ? this.desplazamientoActual(hoja) : 0;
    this.pararTemporizador();
    evento.preventDefault();
    this.puntero = evento.pointerId;
    this.arrastrada = hoja;
    this.inicioY = evento.clientY - desde;
    this.dy = desde;
    this.muestras = [{ t: this.ahora(), dy: desde }];
    try {
      this.asa.setPointerCapture(evento.pointerId);
    } catch {
      // Sin captura (un puntero que ya no existe): el gesto sigue mientras lleguen eventos al asa.
    }
    hoja.style.willChange = 'transform';
    hoja.style.transition = 'none';
    if (desde > 0) {
      this.mover(hoja, desde);
    }
  }

  @HostListener('pointermove', ['$event'])
  alMover(evento: PointerEvent): void {
    if (evento.pointerId !== this.puntero || !this.arrastrada) {
      return;
    }
    this.anotar(evento.clientY);
    this.mover(this.arrastrada, this.dy);
  }

  @HostListener('pointerup', ['$event'])
  alSoltar(evento: PointerEvent): void {
    const hoja = this.arrastrada;
    if (evento.pointerId !== this.puntero || !hoja) {
      return;
    }
    this.anotar(evento.clientY);
    this.puntero = null;
    if (this.debeCerrar(hoja)) {
      this.cerrar(hoja);
    } else {
      this.volver(hoja);
    }
  }

  @HostListener('pointercancel', ['$event'])
  @HostListener('lostpointercapture', ['$event'])
  alCancelar(evento: PointerEvent): void {
    if (evento.pointerId !== this.puntero || !this.arrastrada) {
      return;
    }
    this.puntero = null;
    this.volver(this.arrastrada);
  }

  ngOnDestroy(): void {
    this.pararTemporizador();
    if (this.puntero !== null) {
      try {
        this.asa.releasePointerCapture(this.puntero);
      } catch {
        // Ya no la tenía.
      }
    }
    this.puntero = null;
    this.saliendo = false;
    if (this.arrastrada) {
      this.limpiar(this.arrastrada);
    }
    this.arrastrada = null;
  }

  /** Reloj del gesto (en las pruebas se sustituye). */
  protected ahora(): number {
    return performance.now();
  }

  private anotar(y: number): void {
    this.dy = Math.max(0, y - this.inicioY);
    const t = this.ahora();
    this.muestras.push({ t, dy: this.dy });
    // Solo lo reciente cuenta para la velocidad final; siempre quedan al menos dos muestras.
    while (this.muestras.length > 2 && this.muestras[0].t < t - VENTANA_VELOCIDAD_MS) {
      this.muestras.shift();
    }
  }

  private debeCerrar(hoja: HTMLElement): boolean {
    if (this.dy <= 0) {
      return false;
    }
    const alto = hoja.getBoundingClientRect().height;
    const umbral = alto > 0 ? Math.min(UMBRAL_PX, alto * UMBRAL_PROPORCION) : UMBRAL_PX;
    return this.dy >= umbral || (this.dy >= RECORRIDO_RAPIDO && this.velocidadFinal() >= VELOCIDAD_CIERRE);
  }

  /** px/ms en los últimos 100 ms del gesto (0 si el dedo llevaba quieto más tiempo). */
  private velocidadFinal(): number {
    const ultima = this.muestras[this.muestras.length - 1];
    const primera = this.muestras.find((m) => m.t >= ultima.t - VENTANA_VELOCIDAD_MS) ?? ultima;
    const tiempo = ultima.t - primera.t;
    return tiempo > 0 ? (ultima.dy - primera.dy) / tiempo : 0;
  }

  private cerrar(hoja: HTMLElement): void {
    if (menosMovimiento()) {
      this.terminarCierre(hoja);
      return;
    }
    this.saliendo = true;
    hoja.style.transition = `transform ${SALIDA_MS}ms ease-in`;
    hoja.style.transform = 'translateY(100%)';
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.saliendo = false;
      this.terminarCierre(hoja);
    }, SALIDA_MS);
  }

  /**
   * Avisa y quita los estilos en el mismo turno: quien cierra quita la hoja antes de que se pinte otra vez; si no la
   * quita (un envío en vuelo, cambios sin guardar), la hoja queda en su sitio.
   */
  private terminarCierre(hoja: HTMLElement): void {
    this.arrastrada = null;
    this.deslizada.emit();
    this.limpiar(hoja);
  }

  private volver(hoja: HTMLElement): void {
    if (menosMovimiento()) {
      this.arrastrada = null;
      this.limpiar(hoja);
      return;
    }
    hoja.style.transition = `transform ${VUELTA_MS}ms ease-out`;
    this.mover(hoja, 0);
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.arrastrada = null;
      this.limpiar(hoja);
    }, VUELTA_MS);
  }

  private mover(hoja: HTMLElement, dy: number): void {
    hoja.style.transform = `translateY(${dy}px)`;
  }

  private desplazamientoActual(hoja: HTMLElement): number {
    const transformada = getComputedStyle(hoja).transform;
    if (!transformada || transformada === 'none' || typeof DOMMatrixReadOnly === 'undefined') {
      return 0;
    }
    return Math.max(0, new DOMMatrixReadOnly(transformada).m42);
  }

  private limpiar(hoja: HTMLElement): void {
    hoja.style.removeProperty('transform');
    hoja.style.removeProperty('transition');
    hoja.style.removeProperty('will-change');
  }

  private pararTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
