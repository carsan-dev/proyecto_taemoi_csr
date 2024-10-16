import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Router } from '@angular/router';

@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './eventos.component.html',
  styleUrl: './eventos.component.scss',
})
export class EventosComponent implements OnInit {
  eventos: any[] = [];
  usuarioLogueado: boolean = false;

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.obtenerEventos();
  }

  obtenerEventos() {
    this.endpointsService.obtenerEventos().subscribe({
      next: (response) => {
        this.eventos = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los eventos.',
          icon: 'error',
        });
      },
    });
  }

  verDetalle(id: number) {
    this.router.navigate(['/eventos', id]);
  }
}
