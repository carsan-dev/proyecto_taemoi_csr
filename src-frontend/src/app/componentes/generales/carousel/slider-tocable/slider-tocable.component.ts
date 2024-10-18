import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Subscription } from 'rxjs/internal/Subscription';

@Component({
  selector: 'app-slider-tocable',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './slider-tocable.component.html',
  styleUrl: './slider-tocable.component.scss',
})
export class SliderTocableComponent implements OnInit, OnDestroy {
  eventos: any[] = [];
  defaultFotos: any[] = [
    {
      titulo: 'Evento 1',
      descripcion: 'Descripción del evento 1',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 2',
      descripcion: 'Descripción del evento 2',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 3',
      descripcion: 'Descripción del evento 3',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 4',
      descripcion: 'Descripción del evento 4',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 5',
      descripcion: 'Descripción del evento 5',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
  ];
  private subscriptions: Subscription = new Subscription();

  constructor(public endpointsService: EndpointsService) {}

  ngOnInit(): void {
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos.length ? eventos.slice(0, 5) : this.defaultFotos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los eventos.',
          icon: 'error',
        });
        this.eventos = this.defaultFotos;
      },
    });

    this.subscriptions.add(eventosSubscription);

    // Iniciar la carga de eventos
    this.endpointsService.obtenerEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
