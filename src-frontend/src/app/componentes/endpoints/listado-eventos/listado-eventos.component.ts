import { Component, OnDestroy, OnInit } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
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
  guardandoOrden: boolean = false;
  previewVisible: boolean = false;
  modalAlt: string = 'Vista ampliada del evento';
  private readonly subscriptions: Subscription = new Subscription();

  constructor(public endpointsService: EndpointsService) {}

  get eventosPreview(): any[] {
    return this.eventos.filter((evento) => evento.visible !== false);
  }

  togglePreview(): void {
    this.previewVisible = !this.previewVisible;
  }

  ngOnInit(): void {
    this.cargando = true;
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos;
        this.cargando = false;
        this.ordenPendiente = false;
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

  guardarOrden(): void {
    if (!this.ordenPendiente || this.guardandoOrden) {
      return;
    }

    const ordenIds = this.eventos.map((evento) => evento.id);
    this.guardandoOrden = true;
    this.endpointsService
      .actualizarOrdenEventos(ordenIds)
      .pipe(finalize(() => (this.guardandoOrden = false)))
      .subscribe({
        next: () => {
          showSuccessToast('Orden de eventos guardado');
          this.ordenPendiente = false;
          this.endpointsService.obtenerTodosLosEventos();
        },
        error: () => {
          showErrorToast('No se pudo guardar el orden de eventos');
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
    const accion = evento?.visible ? 'ocultar' : 'mostrar';

    Swal.fire({
      title: `¿${accion.charAt(0).toUpperCase() + accion.slice(1)} evento?`,
      text: `¿Deseas ${accion} "${eventoTitulo}" en la página pública?`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: `Sí, ${accion}`,
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.toggleVisibilidadEvento(id).subscribe({
          next: () => {
            showSuccessToast(`Evento ahora ${evento?.visible ? 'oculto' : 'visible'}`);
          },
          error: () => {
            showErrorToast('No se pudo cambiar la visibilidad');
          },
        });
      }
    });
  }
}
