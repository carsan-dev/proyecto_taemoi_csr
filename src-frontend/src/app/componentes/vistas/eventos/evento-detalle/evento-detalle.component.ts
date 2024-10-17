import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Evento } from '../../../../interfaces/evento';
import { CommonModule } from '@angular/common';
import { NgxSpinnerModule, NgxSpinnerService } from 'ngx-spinner';

@Component({
  selector: 'app-evento-detalle',
  standalone: true,
  imports: [CommonModule, NgxSpinnerModule, RouterLink],
  templateUrl: './evento-detalle.component.html',
  styleUrl: './evento-detalle.component.scss',
})
export class EventoDetalleComponent implements OnInit {
  evento: Evento | null = null; // Ahora usamos la interfaz Evento
  eventoId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly spinner: NgxSpinnerService
  ) {}

  ngOnInit(): void {
    this.eventoId = +this.route.snapshot.paramMap.get('eventoId')!;
    this.obtenerEvento(this.eventoId);
  }

  obtenerEvento(id: number): void {
    this.spinner.show(); // Mostrar el spinner cuando comience la carga

    this.endpointsService.obtenerEventoPorId(id).subscribe({
      next: (response: Evento) => {
        this.evento = response;
        this.spinner.hide(); // Ocultar el spinner cuando se haya cargado el evento
      },
      error: () => {
        this.spinner.hide(); // Ocultar el spinner si hay un error
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los detalles del evento.',
          icon: 'error',
        });
      },
    });
  }
}
