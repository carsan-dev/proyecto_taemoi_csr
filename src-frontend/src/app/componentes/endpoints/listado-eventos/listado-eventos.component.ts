import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Subscription } from 'rxjs/internal/Subscription';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { finalize } from 'rxjs/operators';
import { EventosVistaComponent } from '../../vistas/eventos/eventos-vista.component';

@Component({
  selector: 'app-listado-eventos',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    SkeletonCardComponent,
    DragDropModule,
    EventosVistaComponent
  ],
  templateUrl: './listado-eventos.component.html',
  styleUrl: './listado-eventos.component.scss',
})
export class ListadoEventosComponent implements OnInit, OnDestroy {
  eventos: any[] = [];
  cargando: boolean = true; // Local loading state
  ordenPendiente: boolean = false;
  visibilidadPendiente: boolean = false;
  guardandoCambios: boolean = false;
  previewVisible: boolean = false;
  movedEventoId: number | null = null;
  modalAlt: string = 'Vista ampliada del evento';
  private readonly subscriptions: Subscription = new Subscription();
  private readonly visibilidadOverrides = new Map<number, boolean>();
  private movedTimeoutId: ReturnType<typeof setTimeout> | null = null;
  @ViewChildren('eventoCard') private eventoCards!: QueryList<ElementRef<HTMLElement>>;

  constructor(public endpointsService: EndpointsService) {}

  get eventosPreview(): any[] {
    return this.eventos.filter((evento) => this.isEventoVisible(evento));
  }

  get cambiosPendientes(): boolean {
    return this.ordenPendiente || this.visibilidadPendiente;
  }

  togglePreview(): void {
    this.previewVisible = !this.previewVisible;
  }

  isEventoVisible(evento: any): boolean {
    if (this.visibilidadOverrides.has(evento.id)) {
      return this.visibilidadOverrides.get(evento.id) as boolean;
    }
    return evento.visible !== false;
  }

  ngOnInit(): void {
    this.cargando = true;
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos;
        this.cargando = false;
        this.ordenPendiente = false;
        this.sincronizarVisibilidadPendiente();
      },
      error: (error) => {
        this.cargando = false;
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los eventos',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(eventosSubscription);

    // Iniciar la carga de todos los eventos (incluyendo ocultos)
    this.endpointsService.obtenerTodosLosEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (this.movedTimeoutId) {
      clearTimeout(this.movedTimeoutId);
      this.movedTimeoutId = null;
    }
  }

  drop(event: CdkDragDrop<any[]>): void {
    if (this.cargando || !this.eventos || this.eventos.length === 0) {
      return;
    }
    const posiciones = this.capturarPosiciones();
    moveItemInArray(this.eventos, event.previousIndex, event.currentIndex);
    this.ordenPendiente = true;
    this.animarReordenamiento(posiciones);
    const moved = this.eventos[event.currentIndex];
    if (moved?.id) {
      this.marcarMovido(moved.id);
    }
  }

  moverEvento(index: number, delta: number): void {
    if (this.cargando || !this.eventos || this.eventos.length === 0) {
      return;
    }
    const destino = index + delta;
    if (destino < 0 || destino >= this.eventos.length) {
      return;
    }
    const posiciones = this.capturarPosiciones();
    moveItemInArray(this.eventos, index, destino);
    this.ordenPendiente = true;
    this.animarReordenamiento(posiciones);
    const moved = this.eventos[destino];
    if (moved?.id) {
      this.marcarMovido(moved.id);
    }
  }

  guardarCambios(): void {
    if (!this.cambiosPendientes || this.guardandoCambios) {
      return;
    }

    const requests = [];
    if (this.ordenPendiente) {
      const ordenIds = this.eventos.map((evento) => evento.id);
      requests.push(this.endpointsService.actualizarOrdenEventos(ordenIds));
    }
    for (const id of this.visibilidadOverrides.keys()) {
      requests.push(this.endpointsService.toggleVisibilidadEvento(id));
    }

    this.guardandoCambios = true;
    forkJoin(requests)
      .pipe(finalize(() => (this.guardandoCambios = false)))
      .subscribe({
        next: () => {
          showSuccessToast('Cambios guardados');
          this.ordenPendiente = false;
          this.visibilidadOverrides.clear();
          this.visibilidadPendiente = false;
          this.endpointsService.obtenerTodosLosEventos();
        },
        error: () => {
          showErrorToast('No se pudieron guardar los cambios');
        },
      });
  }

  eliminarEvento(id: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'No podrás revertir esto',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarEvento(id);
        showSuccessToast('Evento eliminado correctamente');
      }
    });
  }

  abrirModal(imagenUrl: string | null, eventoTitulo?: string) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('imgAmpliada') as HTMLImageElement;

    if (!imagenUrl || imagenUrl.trim() === '') {
      Swal.fire({
        icon: 'error',
        title: 'Imagen no disponible',
        text: 'No hay imagen disponible para mostrar.',
      });
      return; // Detenemos la ejecución si no hay imagen
    }

    this.modalAlt = eventoTitulo ? `Vista ampliada de ${eventoTitulo}` : 'Vista ampliada del evento';
    if (modal && modalImg) {
      modal.style.display = 'block';
      modalImg.src = imagenUrl;
    }
  }

  cerrarModal() {
    const modal = document.getElementById('imageModal');
    if (modal) {
      modal.style.display = 'none';
    }
  }

  toggleVisibilidad(id: number, eventoTitulo: string): void {
    const evento = this.eventos.find((e) => e.id === id);
    if (!evento) {
      return;
    }
    const visibleActual = this.isEventoVisible(evento);
    const nuevoVisible = !visibleActual;
    const visibleOriginal = evento.visible !== false;
    if (nuevoVisible === visibleOriginal) {
      this.visibilidadOverrides.delete(id);
    } else {
      this.visibilidadOverrides.set(id, nuevoVisible);
    }
    this.visibilidadPendiente = this.visibilidadOverrides.size > 0;
  }

  private sincronizarVisibilidadPendiente(): void {
    const idsActuales = new Set(this.eventos.map((evento) => evento.id));
    for (const id of this.visibilidadOverrides.keys()) {
      if (!idsActuales.has(id)) {
        this.visibilidadOverrides.delete(id);
      }
    }
    this.visibilidadPendiente = this.visibilidadOverrides.size > 0;
  }

  private capturarPosiciones(): Map<number, DOMRect> {
    const posiciones = new Map<number, DOMRect>();
    if (!this.eventoCards) {
      return posiciones;
    }
    for (const card of this.eventoCards.toArray()) {
      const elemento = card.nativeElement;
      const id = Number(elemento.dataset['eventoId']);
      if (!Number.isNaN(id)) {
        posiciones.set(id, elemento.getBoundingClientRect());
      }
    }
    return posiciones;
  }

  private animarReordenamiento(posiciones: Map<number, DOMRect>): void {
    if (!posiciones.size || !this.eventoCards) {
      return;
    }
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    requestAnimationFrame(() => {
      for (const card of this.eventoCards.toArray()) {
        const elemento = card.nativeElement;
        const id = Number(elemento.dataset['eventoId']);
        const previo = posiciones.get(id);
        if (!previo) {
          continue;
        }
        const actual = elemento.getBoundingClientRect();
        const dx = previo.left - actual.left;
        const dy = previo.top - actual.top;
        if (dx || dy) {
          elemento.animate(
            [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0, 0)' }],
            { duration: 240, easing: 'cubic-bezier(0.2, 0, 0, 1)' }
          );
        }
      }
    });
  }

  private marcarMovido(id: number): void {
    this.movedEventoId = id;
    if (this.movedTimeoutId) {
      clearTimeout(this.movedTimeoutId);
    }
    this.movedTimeoutId = setTimeout(() => {
      this.movedEventoId = null;
      this.movedTimeoutId = null;
    }, 350);
  }

  getEventoImageUrl(evento: any): string {
    const fallback = '../../../../assets/media/default.webp';
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return fallback;
    }

    const version = this.obtenerVersionEvento(evento);
    let url = this.actualizarParametroUrl(rawUrl, 'w', '480');
    url = this.actualizarParametroUrl(url, 'v', version);
    return url;
  }

  getEventoModalUrl(evento: any): string | null {
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return null;
    }

    return this.actualizarParametroUrl(rawUrl, 'v', this.obtenerVersionEvento(evento));
  }

  private obtenerVersionEvento(evento: any): string {
    const version = evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0';
    return String(version);
  }

  private actualizarParametroUrl(url: string, key: string, value: string): string {
    const valueSeguro = encodeURIComponent(value);
    const regex = new RegExp(`([?&])${key}=[^&]*`);
    if (regex.test(url)) {
      return url.replace(regex, `$1${key}=${valueSeguro}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}${key}=${valueSeguro}`;
  }
}
