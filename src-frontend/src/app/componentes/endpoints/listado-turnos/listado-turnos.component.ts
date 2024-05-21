import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-listado-turnos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './listado-turnos.component.html',
  styleUrl: './listado-turnos.component.scss'
})
export class ListadoTurnosComponent implements OnInit {
  turnos: any[] = [];

  constructor(private endpointsService: EndpointsService) { }

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerTurnos();
    }
  }

  obtenerTurnos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerTurnos(token).subscribe({
        next: (response) => {
          this.turnos = response;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
    }
  }

  eliminarTurno(id: number): void {
    const token = localStorage.getItem('token');
    if (token) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'No podrás revertir esto',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlo'
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService.eliminarTurno(id, token).subscribe({
            next: () => {
              Swal.fire({
                title: 'Turno eliminado',
                text: 'El turno ha sido eliminado correctamente.',
                icon: 'success',
              });
              this.obtenerTurnos();
            },
            error: () => {
              Swal.fire({
                title: 'Error al eliminar turno',
                text: 'Ha ocurrido un error al intentar eliminar el turno.',
                icon: 'error',
              });
            },
          });
        }
      });
    }
  }
}
