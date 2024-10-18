import { Component, OnDestroy, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';

@Component({
  selector: 'app-listado-eventos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './listado-eventos.component.html',
  styleUrl: './listado-eventos.component.scss',
})
export class ListadoEventosComponent implements OnInit, OnDestroy {
  eventos: any[] = [];
  private readonly subscriptions: Subscription = new Subscription();
  
  constructor(public endpointsService: EndpointsService) {}

  ngOnInit(): void {
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los eventos',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(eventosSubscription);

    // Iniciar la carga de eventos
    this.endpointsService.obtenerEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
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
        Swal.fire(
          'Eliminado!',
          'El evento ha sido eliminado correctamente.',
          'success'
        );
      }
    });
  }

  abrirModal(imagenUrl: string | null) {
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
}
