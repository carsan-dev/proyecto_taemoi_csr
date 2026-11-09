import { DOCUMENT } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';

import { CinturonComponent } from '../cinturon/cinturon.component';
import {
  MEDIAS_COMPLETAS, PASOS_RALLAS, notaPasos, pasoBloqueado, textoApto, textoCuentaRallas, textoFaltaRallas,
} from '../cinturon/rallas-textos';
import { CONSULTA_HOJA, PilaCapas, anchoDeHoja, contenerFoco, devolverFoco, disparadorActual, esClicEnElVelo } from '../capas/capa-modal';
import { PasoRallas } from '../../interfaces/asistencia';

let siguienteId = 0;

/**
 * Hoja de rallas de Pasar lista (decisiones 510-513; boceto `boceto-rallas-cinturon.html`, estados hoja-0 a hoja-5). Se
 * abre al tocar la punta del cinturón: la punta grande (150 × 30), la cuenta y lo que falta, y cuatro pasos («− Una»,
 * «− Media», «+ Media», «+ Una»). Un paso que no cabe va con `aria-disabled` (sigue enfocable) y su nota. Con las 5
 * rallas, «Marcar apto/apta para examen». La forma es la del diálogo del panel (440 px; hoja inferior por debajo de
 * 768). Cada paso lo guarda quien la abre, al momento; esta hoja solo pinta y avisa.
 */
@Component({
  selector: 'pn-hoja-rallas',
  standalone: true,
  imports: [CinturonComponent],
  templateUrl: './hoja-rallas.component.html',
  styleUrls: ['./hoja-rallas.component.scss'],
})
export class HojaRallasComponent implements AfterViewInit, OnChanges, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);

  /** Nombre y apellidos (título). */
  @Input({ required: true }) nombre = '';
  /** El nombre de pila («Lucía ha completado sus 5 rallas.»). */
  @Input({ required: true }) nombreCorto = '';
  /** «Taekwondo · Verde». */
  @Input() subtitulo = '';
  @Input() color1: string | null = null;
  @Input() color2: string | null = null;
  @Input({ required: true }) medias = 0;
  @Input() genero: string | null = null;
  /** Un paso en vuelo: los toques se ignoran hasta que acabe. */
  @Input() enCurso = false;
  @Output() readonly paso = new EventEmitter<PasoRallas>();
  @Output() readonly apto = new EventEmitter<void>();
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('dialogo', { static: true }) private dialogoRef!: ElementRef<HTMLDialogElement>;

  readonly id = `pn-rallas-${++siguienteId}`;
  readonly pasos = PASOS_RALLAS;
  hoja = anchoDeHoja();
  private disparador: HTMLElement | null = null;
  private destruido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  get completa(): boolean { return this.medias >= MEDIAS_COMPLETAS; }
  get cuenta(): string { return textoCuentaRallas(this.medias); }
  get falta(): string { return textoFaltaRallas(this.medias); }
  get nota(): string { return notaPasos(this.medias); }
  get textoAptoBoton(): string { return `Marcar ${textoApto(this.genero)} para examen`; }

  bloqueado(paso: number): boolean { return pasoBloqueado(this.medias, paso); }

  ngOnChanges(cambios: SimpleChanges): void {
    // Al llegar a las 5 rallas, el foco pasa a «Marcar apto» (el paso que lo ha llevado queda deshabilitado).
    const m = cambios['medias'];
    if (m && !m.firstChange && m.currentValue >= MEDIAS_COMPLETAS && m.previousValue < MEDIAS_COMPLETAS) {
      queueMicrotask(() => this.dialogoRef.nativeElement.querySelector<HTMLElement>('[data-apto]')?.focus());
    }
  }

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const dialogo = this.dialogoRef.nativeElement;
    dialogo.showModal();
    this.pila.abrir(dialogo);
    // El foco, en el primer paso que se puede dar (como en el boceto).
    dialogo.querySelector<HTMLElement>('[data-paso]:not([aria-disabled="true"])')?.focus();
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    const dialogo = this.dialogoRef.nativeElement;
    this.pila.cerrar(dialogo);
    if (dialogo.open) {
      dialogo.close();
    }
    devolverFoco(this.disparador);
  }

  dar(paso: PasoRallas): void {
    if (this.enCurso || this.bloqueado(paso)) return;
    this.paso.emit(paso);
  }

  marcarApto(): void {
    if (this.enCurso || !this.completa) return;
    this.apto.emit();
  }

  cancelar(): void {
    if (this.destruido) return;
    this.cerrar.emit();
  }

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.cancelar();
  }

  alCerrarNativo(): void {
    if (!this.destruido) this.cancelar();
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
}
