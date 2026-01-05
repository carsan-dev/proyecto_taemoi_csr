import { Component, OnDestroy, OnInit } from '@angular/core';
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
  modalAlt: string = 'Vista ampliada del evento';
  private readonly subscriptions: Subscription = new Subscription();
  private readonly visibilidadOverrides = new Map<number, boolean>();

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
  }

  drop(event: CdkDragDrop<any[]>): void {
    if (this.cargando || !this.eventos || this.eventos.length === 0) {
      return;
    }
    moveItemInArray(this.eventos, event.previousIndex, event.currentIndex);
    this.ordenPendiente = true;
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
}
