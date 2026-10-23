import { LiveAnnouncer } from '@angular/cdk/a11y';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { EventosVistaComponent } from '../../componentes/vistas/eventos/eventos-vista.component';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { menosMovimiento } from '../capas/capa-modal';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { EventosCapaComponent } from './eventos-capa.component';
import {
  ANCHO_MINIATURA,
  EstadoFecha,
  EventoPanel,
  estadoFecha,
  etiquetaEstado,
  fechaCorta,
  fechaTitulo,
  moverEn,
  resumenEventos,
  urlImagen,
} from './eventos-vista';

type Estado = 'cargando' | 'listo' | 'error';
/** El mando de orden con el foco: tras mover, el foco vuelve al mismo de la fila movida. */
type MandoOrden = 'asa' | 'subir' | 'bajar';

/**
 * Eventos del panel nuevo (8b-3f, P1 «lista densa ordenable»): una fila por evento con asa y flechas de orden,
 * miniatura (se amplía), título, descripción en dos líneas, fecha con su estado, interruptor «Visible en la web»,
 * «Editar» a la vista y «Eliminar» en «⋯»; un aviso bajo el título que pasa a «Guardar orden» al reordenar y «Ver cómo
 * queda en la web». El orden se cambia arrastrando el asa, con las flechas o con el teclado (flechas arriba y abajo con
 * el foco en el asa o en los botones), anunciado con `LiveAnnouncer`. Las MISMAS peticiones que la pantalla de hoy
 * (`ListadoEventosComponent`): orden, visibilidad y borrado; con el diálogo, los avisos y los estados de carga del panel
 * nuevo, sin SweetAlert2 ni cargador global. Va en su propio chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-eventos-panel',
  standalone: true,
  imports: [
    RouterLink,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    EventosCapaComponent,
    EventosVistaComponent,
  ],
  templateUrl: './eventos.component.html',
  styleUrls: ['./eventos.component.scss'],
})
export class EventosPanelComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly anunciador = inject(LiveAnnouncer);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly etiquetaEstado = etiquetaEstado;

  estado: Estado = 'cargando';
  eventos: EventoPanel[] = [];
  /** El orden se ha cambiado y aún no se ha guardado. */
  ordenPendiente = false;
  guardandoOrden = false;
  /** Eventos con el cambio de visibilidad en vuelo: sin doble envío. */
  readonly cambiandoVisibilidad = new Set<number>();
  /** Fila con el menú «⋯» abierto. */
  menuAbierto: number | null = null;
  /** Capas: la vista de la web y la imagen ampliada. */
  verWeb = false;
  ampliada: EventoPanel | null = null;
  /** La fila recién movida, resaltada un momento. */
  movida: number | null = null;

  private carga?: Subscription;
  /** Las escrituras no se cancelan al salir (acaban y limpian la caché); lo que toca la vista, sí se protege. */
  private destruido = false;
  private escuchaFuera = false;
  private temporizadorMovida: ReturnType<typeof setTimeout> | null = null;

  get resumen(): string {
    return resumenEventos(this.eventos.length, this.eventos.filter((e) => this.visible(e)).length);
  }

  /** Lo que enseña «Ver cómo queda en la web»: los visibles, en el orden actual (también el aún sin guardar), como hoy. */
  get eventosWeb(): EventoPanel[] {
    return this.eventos.filter((e) => this.visible(e));
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.carga?.unsubscribe();
    this.quitarEscucha();
    if (this.temporizadorMovida) {
      clearTimeout(this.temporizadorMovida);
    }
    this.destruido = true;
  }

  // ------------------------------------------------------------------ lectura

  /** `GET /eventos/admin/todos` (todos, también los ocultos), como hoy. `silencioso`: sin esqueleto (tras guardar). */
  cargar(silencioso = false): void {
    this.carga?.unsubscribe();
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.carga = this.lecturas.eventosAdmin().subscribe({
      next: (eventos) => {
        this.eventos = Array.isArray(eventos) ? (eventos as EventoPanel[]) : [];
        this.ordenPendiente = false;
        this.estado = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        if (!silencioso) {
          this.estado = 'error';
        }
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ fila

  visible(e: EventoPanel): boolean {
    return e.visible !== false;
  }

  miniatura(e: EventoPanel): string {
    return urlImagen(e, ANCHO_MINIATURA)!;
  }

  tieneFoto(e: EventoPanel): boolean {
    return !!e.fotoEvento?.url;
  }

  imagenAmpliada(e: EventoPanel): string {
    return urlImagen(e, null) ?? '';
  }

  estadoDe(e: EventoPanel): EstadoFecha {
    return estadoFecha(e.fechaEvento, new Date());
  }

  fechaDe(e: EventoPanel): string | null {
    return fechaCorta(e.fechaEvento);
  }

  tituloFecha(e: EventoPanel): string | null {
    return fechaTitulo(e.fechaEvento, new Date());
  }

  // ------------------------------------------------------------------ orden

  /** Subir y bajar (botones y teclado). */
  mover(indice: number, delta: number, mando?: MandoOrden): void {
    const destino = indice + delta;
    const e = this.eventos[indice];
    if (!e || this.guardandoOrden) {
      return;
    }
    if (destino < 0 || destino >= this.eventos.length) {
      this.anunciar(`${e.titulo} ya es la ${destino < 0 ? 'primera' : 'última'}.`);
      return;
    }
    this.aplicarOrden(indice, destino);
    if (mando) {
      this.cdr.detectChanges();
      this.enfocarMando(e.id, mando);
    }
  }

  /** Flechas arriba y abajo con el foco en el asa o en los botones de subir y bajar. */
  teclaOrden(evento: KeyboardEvent, indice: number, mando: MandoOrden): void {
    if (evento.key !== 'ArrowUp' && evento.key !== 'ArrowDown') {
      return;
    }
    evento.preventDefault();
    this.mover(indice, evento.key === 'ArrowUp' ? -1 : 1, mando);
  }

  /** El arrastre del asa (`cdkDropListDropped`). */
  soltar(evento: CdkDragDrop<EventoPanel[]>): void {
    if (evento.previousIndex === evento.currentIndex || this.guardandoOrden) {
      return;
    }
    this.aplicarOrden(evento.previousIndex, evento.currentIndex);
  }

  private aplicarOrden(desde: number, hasta: number): void {
    const e = this.eventos[desde];
    this.eventos = moverEn(this.eventos, desde, hasta);
    this.ordenPendiente = true;
    this.marcarMovida(e.id);
    this.anunciar(`${e.titulo}, posición ${hasta + 1} de ${this.eventos.length}.`);
  }

  private anunciar(texto: string): void {
    void this.anunciador.announce(texto);
  }

  private marcarMovida(id: number): void {
    if (menosMovimiento()) {
      return;
    }
    this.movida = id;
    if (this.temporizadorMovida) {
      clearTimeout(this.temporizadorMovida);
    }
    this.temporizadorMovida = setTimeout(() => {
      this.movida = null;
      this.temporizadorMovida = null;
      this.cdr.markForCheck();
    }, 700);
  }

  private enfocarMando(id: number, mando: MandoOrden): void {
    const fila = this.host.nativeElement.querySelector<HTMLElement>(`.eventos__fila[data-evento="${id}"]`);
    const objetivo = fila?.querySelector<HTMLButtonElement>(`.eventos__${mando}`);
    if (objetivo && !objetivo.disabled) {
      objetivo.focus();
    } else {
      // En el extremo, el botón queda deshabilitado: el foco pasa al asa de la misma fila.
      fila?.querySelector<HTMLElement>('.eventos__asa')?.focus();
    }
  }

  /** «Guardar orden»: el `PUT /eventos/orden` de hoy con los ids en el orden de la lista; después, la relectura de hoy. */
  guardarOrden(): void {
    if (!this.ordenPendiente || this.guardandoOrden) {
      return;
    }
    this.guardandoOrden = true;
    this.endpoints
      .actualizarOrdenEventos(this.eventos.map((e) => e.id))
      .subscribe({
        next: () => {
          this.guardandoOrden = false;
          this.ordenPendiente = false;
          this.avisos.exito('Orden guardado.');
          if (!this.destruido) {
            this.cargar(true);
          }
        },
        error: () => {
          this.guardandoOrden = false;
          this.avisos.error('No se pudieron guardar los cambios.');
          this.cdr.markForCheck();
        },
      });
  }

  // ------------------------------------------------------------------ visible en la web

  ocupadoVisibilidad(e: EventoPanel): boolean {
    return this.cambiandoVisibilidad.has(e.id);
  }

  /** El interruptor: la MISMA llamada de hoy (`PUT /eventos/{id}/toggle-visibilidad`), al momento. */
  alternarVisible(e: EventoPanel): void {
    if (this.ocupadoVisibilidad(e)) {
      return;
    }
    this.cambiandoVisibilidad.add(e.id);
    this.endpoints
      .toggleVisibilidadEvento(e.id)
      .subscribe({
        next: () => {
          this.cambiandoVisibilidad.delete(e.id);
          const visible = !this.visible(e);
          this.eventos = this.eventos.map((x) => (x.id === e.id ? { ...x, visible } : x));
          this.avisos.exito(visible ? `${e.titulo} ya se ve en la web.` : `${e.titulo} ya no se ve en la web.`);
          this.cdr.markForCheck();
        },
        error: () => {
          this.cambiandoVisibilidad.delete(e.id);
          this.avisos.error('No se pudo cambiar la visibilidad del evento.');
          this.cdr.markForCheck();
        },
      });
  }

  // ------------------------------------------------------------------ «⋯» y eliminar

  private menuRef(id: number): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`#eventos-menu-${id}`);
  }

  private botonMenu(id: number): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>(`#eventos-mas-${id}`);
  }

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.menuAbierto === null ? null : this.botonMenu(this.menuAbierto)?.closest('.eventos__menu-zona');
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.menuAbierto = null;
    this.quitarEscucha();
    this.cdr.markForCheck();
  };

  private quitarEscucha(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  alternarMenu(e: EventoPanel, evento?: KeyboardEvent): void {
    if (this.menuAbierto === e.id) {
      this.cerrarMenu(false);
      return;
    }
    this.menuAbierto = e.id;
    if (!this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = true;
    }
    this.cdr.detectChanges();
    enfocarOpcion(this.menuRef(e.id), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMenu(e: EventoPanel, evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && this.menuAbierto !== e.id) {
      evento.preventDefault();
      this.alternarMenu(e, evento);
    }
  }

  teclaMenu(e: EventoPanel, evento: KeyboardEvent): void {
    const menu = this.menuRef(e.id);
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenu(true);
    } else if (accion === 'salir') {
      this.cerrarMenu(false);
    }
  }

  cerrarMenu(devolverFoco: boolean): void {
    const id = this.menuAbierto;
    this.menuAbierto = null;
    this.quitarEscucha();
    if (devolverFoco && id !== null) {
      this.cdr.detectChanges();
      this.botonMenu(id)?.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrarMenu(false);
    }
  }

  /** «Eliminar»: confirmación destructiva (foco en «Cancelar») y el `DELETE /eventos/{id}` de hoy. */
  async eliminar(e: EventoPanel): Promise<void> {
    this.cerrarMenu(true);
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar evento',
      mensaje: `Vas a eliminar «${e.titulo}». No podrás revertir esto.`,
      accion: 'Sí, eliminarlo',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-evento',
    });
    if (!r.confirmado) {
      return;
    }
    this.endpoints.eliminarEventoConRespuesta(e.id).subscribe({
      next: () => {
        this.eventos = this.eventos.filter((x) => x.id !== e.id);
        this.avisos.exito('Evento eliminado correctamente.');
        this.cdr.markForCheck();
      },
      error: () => {
        this.avisos.error('No se pudo eliminar el evento.');
        this.cdr.markForCheck();
      },
    });
  }
}
