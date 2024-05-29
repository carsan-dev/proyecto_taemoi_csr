import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';

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
    private authService: AuthenticationService,
    private endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
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
}
