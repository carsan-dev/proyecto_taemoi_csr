import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-listado-turnos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './listado-turnos.component.html',
  styleUrl: './listado-turnos.component.scss',
})
export class ListadoTurnosComponent implements OnInit {
  turnos: any[] = [];

  constructor(private endpointsService: EndpointsService) {}

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

  eliminarTurno(turnoId: number): void {
    const token = localStorage.getItem('token');
    if (token) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'No podrás revertir esta acción',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlo'
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService.eliminarTurno(turnoId, token).subscribe({
            next: () => {
              Swal.fire(
                'Eliminado',
                'El turno ha sido eliminado',
                'success'
              );
              this.obtenerTurnos();
            },
            error: (error) => {
              Swal.fire({
                title: 'Error en la eliminación',
                text: 'No hemos podido eliminar el turno',
                icon: 'error',
              });
            }
          });
        }
      });
    }
  }
}
