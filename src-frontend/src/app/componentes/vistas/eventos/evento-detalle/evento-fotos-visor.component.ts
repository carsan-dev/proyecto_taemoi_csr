import { DOCUMENT } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ScrollLockRelease, ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { urlFotoConAncho } from './evento-fotos-url';

/** Una foto del visor: la URL base de la API (sin `?w=`) y su texto alternativo. */
export interface FotoVisor {
  url: string;
  alt: string;
}

const ANCHO_VISOR = 1800;
const UMBRAL_DESLIZAR = 50;

/**
 * Visor de fotos a pantalla completa de la página pública del evento (E8c · F3).
 * Sin bucle: en la primera y en la última se oculta la flecha que no lleva a ningún sitio.
 */
@Component({
  selector: 'app-evento-fotos-visor',
  standalone: true,
  templateUrl: './evento-fotos-visor.component.html',
  styleUrl: './evento-fotos-visor.component.scss',
})
export class EventoFotosVisorComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input({ required: true }) fotos: FotoVisor[] = [];
  @Input() titulo = '';
  /** Elemento al que volver el foco al cerrar. Si falta, el que tenía el foco al abrir. */
  @Input() retornoFoco: HTMLElement | null = null;
  @Input() set indiceInicial(valor: number) {
    this.indiceArranque = valor;
  }
  @Output() cerrar = new EventEmitter<void>();

  readonly indice = signal(0);

  private indiceArranque = 0;
  private readonly document = inject(DOCUMENT);
  private readonly scrollLock = inject(ScrollLockService);
  private readonly dialogo = viewChild<ElementRef<HTMLElement>>('dialogo');
  private readonly botonCerrar = viewChild<ElementRef<HTMLButtonElement>>('botonCerrar');
  private liberarScroll: ScrollLockRelease | null = null;
  private previo: HTMLElement | null = null;
  private inicioToque: { x: number; y: number } | null = null;
  private temporizadorFoco: ReturnType<typeof setTimeout> | null = null;

  get actual(): FotoVisor | undefined {
    return this.fotos[this.indice()];
  }

  get urlActual(): string {
    return urlFotoConAncho(this.actual?.url ?? '', ANCHO_VISOR);
  }

  get urlSiguiente(): string | null {
    return this.urlVecina(this.indice() + 1);
  }

  get urlAnterior(): string | null {
    return this.urlVecina(this.indice() - 1);
  }

  get hayAnterior(): boolean {
    return this.indice() > 0;
  }

  get haySiguiente(): boolean {
    return this.indice() < this.fotos.length - 1;
  }

  ngOnInit(): void {
    this.indice.set(this.acotar(this.indiceArranque));
    const activo = this.document.activeElement;
    this.previo = activo instanceof HTMLElement ? activo : null;
    this.liberarScroll = this.scrollLock.lock();
  }

  ngAfterViewInit(): void {
    this.botonCerrar()?.nativeElement.focus();
  }

  ngOnDestroy(): void {
    if (this.temporizadorFoco) {
      clearTimeout(this.temporizadorFoco);
    }
    this.liberarScroll?.();
    this.liberarScroll = null;
    const destino = this.retornoFoco ?? this.previo;
    if (destino?.isConnected) {
      destino.focus();
    }
  }

  anterior(): void {
    this.ir(this.indice() - 1);
  }

  siguiente(): void {
    this.ir(this.indice() + 1);
  }

  @HostListener('document:keydown', ['$event'])
  alTeclear(evento: KeyboardEvent): void {
    switch (evento.key) {
      case 'Escape':
        evento.preventDefault();
        this.cerrar.emit();
        break;
      case 'ArrowLeft':
        evento.preventDefault();
        this.anterior();
        break;
      case 'ArrowRight':
        evento.preventDefault();
        this.siguiente();
        break;
      case 'Tab':
        this.atraparFoco(evento);
        break;
    }
  }

  alPulsarEscenario(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.cerrar.emit();
    }
  }

  alEmpezarToque(evento: TouchEvent): void {
    const toque = evento.changedTouches[0];
    this.inicioToque = toque ? { x: toque.clientX, y: toque.clientY } : null;
  }

  alTerminarToque(evento: TouchEvent): void {
    const toque = evento.changedTouches[0];
    const inicio = this.inicioToque;
    this.inicioToque = null;
    if (!toque || !inicio) {
      return;
    }
    const dx = toque.clientX - inicio.x;
    const dy = toque.clientY - inicio.y;
    if (Math.abs(dx) < UMBRAL_DESLIZAR || Math.abs(dx) < Math.abs(dy) * 1.5) {
      return;
    }
    if (dx < 0) {
      this.siguiente();
    } else {
      this.anterior();
    }
  }

  private ir(destino: number): void {
    if (destino < 0 || destino > this.fotos.length - 1 || destino === this.indice()) {
      return;
    }
    this.indice.set(destino);
    // Si la flecha pulsada desaparece (primera o última), el foco vuelve al diálogo en vez de perderse.
    this.temporizadorFoco = setTimeout(() => {
      const raiz = this.dialogo()?.nativeElement;
      if (raiz && !raiz.contains(this.document.activeElement)) {
        this.botonCerrar()?.nativeElement.focus();
      }
    });
  }

  private atraparFoco(evento: KeyboardEvent): void {
    const raiz = this.dialogo()?.nativeElement;
    if (!raiz) {
      return;
    }
    const enfocables = Array.from(raiz.querySelectorAll<HTMLElement>('button'));
    if (enfocables.length === 0) {
      evento.preventDefault();
      return;
    }
    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];
    const activo = this.document.activeElement;
    if (!raiz.contains(activo)) {
      evento.preventDefault();
      (evento.shiftKey ? ultimo : primero).focus();
    } else if (evento.shiftKey && activo === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && activo === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }

  private urlVecina(posicion: number): string | null {
    const foto = this.fotos[posicion];
    return foto ? urlFotoConAncho(foto.url, ANCHO_VISOR) : null;
  }

  private acotar(valor: number): number {
    return Math.min(Math.max(0, Math.floor(valor) || 0), Math.max(0, this.fotos.length - 1));
  }
}
