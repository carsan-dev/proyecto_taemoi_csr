import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-listado-eventos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './listado-eventos.component.html',
  styleUrl: './listado-eventos.component.scss',
})
export class ListadoEventosComponent implements OnInit {
  eventos: any[] = [];

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerEventos();
    }
  }

  obtenerEventos(): void {
    this.endpointsService.obtenerEventos().subscribe({
      next: (response) => {
        this.eventos = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los eventos',
          icon: 'error',
        });
      },
    });
  }

  eliminarEvento(id: number): void {
    const token = localStorage.getItem('token');
    if (token) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'No podrás revertir esto',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlo',
        cancelButtonText: 'Cancelar',
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService.eliminarEvento(id, token).subscribe({
            next: () => {
              Swal.fire(
                'Eliminado!',
                'El evento ha sido eliminado correctamente.',
                'success'
              );
              this.obtenerEventos();
            },
            error: (error) => {
              Swal.fire({
                title: 'Error en la petición',
                text: 'No hemos podido eliminar el evento',
                icon: 'error',
              });
            },
          });
        }
      });
    }
  }
}
