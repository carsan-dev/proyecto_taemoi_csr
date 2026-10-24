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
import { Observable, Subscription, concat, defer, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { seguirAcento } from '../acento-escuela.directive';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { CONSULTA_HOJA, PilaCapas, anchoDeHoja, contenerFoco, devolverFoco, esClicEnElVelo } from '../capas/capa-modal';
import {
  FilaBaja,
  ResumenCargos,
  TipoLote,
  accionLote,
  cargosDe,
  destinoAlumno,
  fraseCargos,
  mensajeAnonimizar,
  sinPagarDe,
  mensajeLote,
  mensajeLoteMixto,
  motivoFallo,
  progresoLote,
  tituloAnonimizar,
  tituloFallos,
  tituloLote,
} from './bajas-alumnos-vista';

type Fase = 'confirmar' | 'en-curso' | 'resumen';
type EstadoAlumno = 'pendiente' | 'en-curso' | 'hecho' | 'fallo';

/** Cómo acabó el lote: cuántos se hicieron y cuántos fallaron (0 y 0 si se canceló). */
export interface ResultadoLote {
  hechos: number;
  fallos: number;
  /** 8b-3l bis: de los hechos, cuántos se eliminaron anonimizando. */
  anonimizados?: number;
}

let siguienteId = 0;

/**
 * Diálogo de «Dar de baja» en lote y de «Eliminar definitivamente» (uno o varios) de «Bajas y eliminaciones» (8b-3l, P1
 * de la decisión 285; boceto: «confirmar borrado» y «eliminando»). Es el diálogo del panel nuevo (8b-D1: `<dialog>` de
 * 440 px, hoja inferior por debajo de 768, la misma hoja de estilos que `pn-dialogo`) con lo que el genérico no tiene:
 * la lista de los nombres, la línea de pagos y cargos (P-3), el progreso por alumno («Eliminando 2 de 3…», sin poder
 * cerrarlo mientras corre) y, si alguno falla, el resumen con el motivo en el mismo diálogo.
 *
 * El borrado es destructivo: rojo de peligro sin el color de la escuela y el foco en «Cancelar». La baja, el diálogo
 * normal. Una petición por alumno, en el orden de la lista y una detrás de otra (hoy, del último al primero).
 */
@Component({
  selector: 'pn-lote-alumnos',
  standalone: true,
  imports: [BotonOcupadoComponent],
  templateUrl: './lote-alumnos.component.html',
  styleUrls: ['../dialogo/dialogo.component.scss', './lote-alumnos.component.scss'],
  host: { class: 'pn pn-capa' },
})
export class LoteAlumnosComponent implements AfterViewInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);
  private readonly acento = seguirAcento();

  @Input({ required: true }) tipo: TipoLote = 'eliminar';
  @Input({ required: true }) alumnos: FilaBaja[] = [];
  /** La petición de cada alumno (`DELETE` o `PUT …/baja`). */
  @Input({ required: true }) ejecutar!: (id: number) => Observable<unknown>;
  /** P-3: el resumen de cargos de los elegidos (solo al eliminar). */
  @Input() cargarCargos: ((ids: number[]) => Observable<ResumenCargos[]>) | null = null;
  /**
   * 8b-3l bis (decisión 287): los pagos de cada alumno que ya conoce la pantalla (su resumen de cargos). Con alguno, al
   * eliminar ese alumno se le elimina anonimizando con {@link anonimizar}.
   */
  @Input() pagos: ReadonlyMap<number, number> = new Map();
  /** `POST /alumnos/{id}/anonimizar`, para los que tienen pagos (solo al eliminar). */
  @Input() anonimizar: ((id: number) => Observable<unknown>) | null = null;
  @Output() readonly cerrado = new EventEmitter<ResultadoLote>();

  @ViewChild('dialogo', { static: true }) private dialogoRef!: ElementRef<HTMLDialogElement>;
  @ViewChild('botonCancelar') private botonCancelar?: ElementRef<HTMLButtonElement>;
  @ViewChild('botonAccion', { read: ElementRef }) private botonAccion?: ElementRef<HTMLButtonElement>;
  @ViewChild('botonCerrarResumen') private botonCerrarResumen?: ElementRef<HTMLButtonElement>;

  readonly id = `pn-lote-${++siguienteId}`;
  hoja = anchoDeHoja();
  fase: Fase = 'confirmar';
  readonly estados = new Map<number, EstadoAlumno>();
  readonly motivos = new Map<number, string>();
  actual = 0;
  hechos = 0;
  fallos = 0;
  anonimizados = 0;

  estadoCargos: 'cargando' | 'listo' | 'error' | 'no' = 'no';
  private resumenes: ResumenCargos[] = [];

  private disparador: HTMLElement | null = null;
  private terminado = false;
  private destruido = false;
  private lote?: Subscription;
  private lecturaCargos?: Subscription;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  get peligro(): boolean {
    return this.tipo === 'eliminar';
  }

  get ocupado(): boolean {
    return this.fase === 'en-curso';
  }

  /** 8b-3l bis: este alumno se elimina anonimizando (tiene pagos, y hay con qué). */
  anonimiza(id: number): boolean {
    if (this.tipo !== 'eliminar' || !this.anonimizar) {
      return false;
    }
    return this.pagosDe(id) > 0;
  }

  /** Sus pagos: los del resumen del diálogo si ya llegó; si no, los que conoce la pantalla. */
  private pagosDe(id: number): number {
    const propio = this.estadoCargos === 'listo' ? this.resumenes.find((r) => r.alumnoId === id) : undefined;
    return propio?.pagos ?? this.pagos.get(id) ?? 0;
  }

  private get cuantosAnonimiza(): number {
    return this.alumnos.filter((a) => this.anonimiza(a.id)).length;
  }

  get titulo(): string {
    if (this.fase === 'resumen') {
      return tituloFallos(this.tipo, this.fallos, this.alumnos.length);
    }
    if (this.fase === 'en-curso') {
      return this.tipo === 'eliminar' ? 'Eliminando alumnos' : 'Dando de baja';
    }
    if (this.alumnos.length === 1 && this.anonimiza(this.alumnos[0].id)) {
      return tituloAnonimizar(this.alumnos[0].nombreCompleto);
    }
    return tituloLote(this.tipo, this.alumnos.map((a) => a.nombreCompleto));
  }

  get mensaje(): string {
    if (this.fase === 'resumen') {
      return this.hechos > 0
        ? `${this.tipo === 'eliminar' ? 'Eliminados' : 'Dados de baja'}: ${this.hechos}. Los demás siguen como estaban.`
        : 'Siguen como estaban.';
    }
    if (this.fase === 'en-curso') {
      return 'No cierres esta ventana hasta que termine.';
    }
    const anonimizados = this.cuantosAnonimiza;
    if (anonimizados > 0) {
      return this.alumnos.length === 1
        ? mensajeAnonimizar(this.pagosDe(this.alumnos[0].id))
        : mensajeLoteMixto(anonimizados, this.alumnos.length);
    }
    return mensajeLote(this.tipo, this.alumnos.length);
  }

  get textoAccion(): string {
    if (this.alumnos.length === 1 && this.anonimiza(this.alumnos[0].id)) {
      return 'Eliminar anonimizando';
    }
    return accionLote(this.tipo, this.alumnos.length);
  }

  get textoProgreso(): string {
    return progresoLote(this.tipo, Math.max(this.actual, 1), this.alumnos.length);
  }

  get lineaCargos(): string {
    switch (this.estadoCargos) {
      case 'cargando':
        return 'Comprobando sus pagos y cargos…';
      case 'error':
        // 8b-3l bis: con pagos, el servidor no lo borra (409) y el resumen enseña su motivo.
        return this.alumnos.length === 1
          ? 'No se pudieron leer sus pagos y cargos. Si tiene pagos, no se elimina y verás el motivo.'
          : 'No se pudieron leer sus pagos y cargos. Los que tengan pagos no se eliminan y verás el motivo.';
      case 'listo': {
        const anonimizados = this.cuantosAnonimiza;
        const destino = anonimizados === 0 ? 'borran' : anonimizados === this.alumnos.length ? 'conservan' : 'mixto';
        return fraseCargos(this.resumenes, this.alumnos.length, destino);
      }
      default:
        return '';
    }
  }

  cargosAlumno(id: number): string {
    if (this.alumnos.length < 2) {
      return '';
    }
    const resumen = this.estadoCargos === 'listo' ? this.resumenes.find((r) => r.alumnoId === id) : undefined;
    const cargos = cargosDe(resumen);
    // 8b-3l bis: al eliminar varios, qué le pasa a cada uno («se elimina» o «se anonimiza (tiene pagos)»).
    const destino = this.tipo === 'eliminar' && this.anonimizar ? destinoAlumno(this.anonimiza(id)) : '';
    // Decisión 287 (ronda 2): al que se anonimiza, sus cargos sin pagar se cancelan.
    const sinPagar = this.anonimiza(id) ? sinPagarDe(resumen) : '';
    return [destino, cargos, sinPagar].filter(Boolean).join(' · ');
  }

  estadoDe(id: number): EstadoAlumno {
    return this.estados.get(id) ?? 'pendiente';
  }

  textoEstado(id: number): string {
    switch (this.estadoDe(id)) {
      case 'en-curso':
        return 'En curso';
      case 'hecho':
        if (this.tipo === 'eliminar') {
          return this.anonimiza(id) ? 'Anonimizado' : 'Eliminado';
        }
        return 'Dado de baja';
      case 'fallo':
        return this.tipo === 'eliminar' ? 'No se eliminó' : 'No se dio de baja';
      default:
        return 'Pendiente';
    }
  }

  ngAfterViewInit(): void {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
    this.disparador = document.activeElement instanceof HTMLElement && document.activeElement !== document.body
      ? document.activeElement
      : null;
    if (this.cargarCargos && this.alumnos.length) {
      this.estadoCargos = 'cargando';
      this.lecturaCargos = this.cargarCargos(this.alumnos.map((a) => a.id)).subscribe({
        next: (lista) => {
          this.resumenes = Array.isArray(lista) ? lista : [];
          this.estadoCargos = 'listo';
          this.cdr.detectChanges();
        },
        error: () => {
          this.estadoCargos = 'error';
          this.cdr.detectChanges();
        },
      });
    }
    this.cdr.detectChanges();
    const dialogo = this.dialogoRef.nativeElement;
    dialogo.showModal();
    this.pila.abrir(dialogo);
    (this.peligro ? this.botonCancelar : this.botonAccion)?.nativeElement.focus();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    this.lote?.unsubscribe();
    this.lecturaCargos?.unsubscribe();
    const dialogo = this.dialogoRef.nativeElement;
    this.pila.cerrar(dialogo);
    if (dialogo.open) {
      dialogo.close();
    }
  }

  // ------------------------------------------------------------------ cierre

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    this.cancelar();
  }

  /** Cerrado por el navegador (un segundo Esc): con el lote en marcha se vuelve a abrir. */
  alCerrarNativo(): void {
    if (this.terminado || this.destruido) {
      return;
    }
    if (this.ocupado) {
      this.dialogoRef.nativeElement.showModal();
      this.botonAccion?.nativeElement.focus();
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

  /** Esc, velo, X y «Cancelar»: nada mientras corre; en el resumen, cierra con lo hecho. */
  cancelar(): void {
    if (this.ocupado) {
      return;
    }
    this.terminar();
  }

  private terminar(): void {
    if (this.terminado) {
      return;
    }
    this.terminado = true;
    const dialogo = this.dialogoRef.nativeElement;
    this.pila.cerrar(dialogo);
    if (dialogo.open) {
      dialogo.close();
    }
    devolverFoco(this.disparador);
    this.cerrado.emit({ hechos: this.hechos, fallos: this.fallos, anonimizados: this.anonimizados });
  }

  // ------------------------------------------------------------------ lote

  confirmar(): void {
    if (this.fase !== 'confirmar' || this.alumnos.length === 0) {
      return;
    }
    this.fase = 'en-curso';
    this.alumnos.forEach((a) => this.estados.set(a.id, 'pendiente'));
    this.cdr.detectChanges();
    const pasos = this.alumnos.map((a, i) =>
      defer(() => {
        this.actual = i + 1;
        this.estados.set(a.id, 'en-curso');
        this.cdr.detectChanges();
        const anonimiza = this.anonimiza(a.id);
        const peticion = anonimiza && this.anonimizar ? this.anonimizar(a.id) : this.ejecutar(a.id);
        return peticion.pipe(
          map(() => {
            if (anonimiza) {
              this.anonimizados++;
            }
            return true;
          }),
          catchError((error) => {
            this.motivos.set(a.id, motivoFallo(this.tipo, error));
            return of(false);
          })
        );
      }).pipe(
        map((bien) => {
          this.estados.set(a.id, bien ? 'hecho' : 'fallo');
          if (bien) {
            this.hechos++;
          } else {
            this.fallos++;
          }
          return bien;
        })
      )
    );
    this.lote = concat(...pasos).subscribe({
      next: () => this.cdr.detectChanges(),
      complete: () => {
        if (this.destruido) {
          return;
        }
        if (this.fallos === 0) {
          this.fase = 'resumen';
          this.terminar();
          return;
        }
        this.fase = 'resumen';
        this.cdr.detectChanges();
        this.botonCerrarResumen?.nativeElement.focus();
      },
    });
  }
}
