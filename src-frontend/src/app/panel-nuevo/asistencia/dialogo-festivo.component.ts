import { DOCUMENT } from '@angular/common';
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

import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { CONSULTA_HOJA, PilaCapas, anchoDeHoja, contenerFoco, devolverFoco, disparadorActual, esClicEnElVelo } from '../capas/capa-modal';
import { ClaseConLista, ImpactoFestivo, NOMBRE_MAXIMO } from '../festivos/festivos-vista';

let siguienteId = 0;

const clases = (n: number) => `${n} ${n === 1 ? 'clase' : 'clases'}`;

/**
 * «Marcar festivo» desde Pasar lista (decisiones 456-459; boceto `boceto-pasar-lista-motivos-festivos.html`, estados
 * `festivo-confirmar` y `festivo-aviso`). El diálogo del panel (440 px; hoja inferior por debajo de 768) con lo que
 * no tiene el de confirmación: las clases que ya tienen lista, la nota de que se quedan como están y el nombre opcional
 * con su contador. Primario y no rojo: es reversible. Esc, el velo y la X cancelan, salvo con el envío en vuelo.
 */
@Component({
  selector: 'pn-dialogo-festivo',
  standalone: true,
  imports: [BotonOcupadoComponent],
  templateUrl: './dialogo-festivo.component.html',
  styleUrls: ['./dialogo-festivo.component.scss'],
})
export class DialogoFestivoComponent implements AfterViewInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);

  /** «el jueves 5 de noviembre». */
  @Input({ required: true }) fecha = '';
  @Input({ required: true }) sede = '';
  @Input({ required: true }) impacto!: ImpactoFestivo;
  /** Para marcar «(esta)» en la fila de la clase abierta, si ya tiene lista. */
  @Input() grupo = '';
  @Input() horaInicio = '';
  @Input() enviando = false;
  @Output() readonly confirmar = new EventEmitter<string>();
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('dialogo', { static: true }) private dialogoRef!: ElementRef<HTMLDialogElement>;

  readonly id = `pn-festivo-${++siguienteId}`;
  readonly maximo = NOMBRE_MAXIMO;
  hoja = anchoDeHoja();
  nombre = '';
  private disparador: HTMLElement | null = null;
  private destruido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  get titulo(): string {
    return `¿Marcar festivo ${this.fecha} en ${this.sede}?`;
  }

  /** Las que se quedan sin lista: todas menos las que ya la tienen. */
  get afectadas(): number {
    return Math.max(this.impacto.clases - this.impacto.conLista, 0);
  }

  get textoAfecta(): string {
    return this.impacto.conLista ? `${this.afectadas} de sus ${clases(this.impacto.clases)}` : clases(this.impacto.clases);
  }

  get textoNota(): string {
    const n = this.impacto.conLista;
    return n === 1 ? '1 clase ya tiene lista y se queda como está.' : `${n} clases ya tienen lista y se quedan como están.`;
  }

  esEsta(c: ClaseConLista): boolean {
    return !!this.grupo && c.grupo === this.grupo && c.horaInicio === this.horaInicio;
  }

  ngAfterViewInit(): void {
    this.disparador = disparadorActual(this.documento);
    const dialogo = this.dialogoRef.nativeElement;
    dialogo.showModal();
    this.pila.abrir(dialogo);
    dialogo.querySelector<HTMLInputElement>('input')?.focus();
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

  escribir(evento: Event): void {
    this.nombre = (evento.target as HTMLInputElement).value.slice(0, NOMBRE_MAXIMO);
  }

  aceptar(): void {
    if (this.enviando) return;
    this.confirmar.emit(this.nombre.trim());
  }

  cancelar(): void {
    if (this.enviando || this.destruido) return;
    this.cerrar.emit();
  }

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.cancelar();
  }

  /** Cerrado por el navegador (un segundo Esc): con el envío en vuelo se reabre; si no, cuenta como cancelar. */
  alCerrarNativo(): void {
    if (this.destruido) return;
    if (this.enviando) {
      this.dialogoRef.nativeElement.showModal();
      return;
    }
    this.cancelar();
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
