import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { GrupoDTO } from '../../../interfaces/grupo-dto';

@Component({
  selector: 'app-listado-turnos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './listado-turnos.component.html',
  styleUrl: './listado-turnos.component.scss',
})
export class ListadoTurnosComponent implements OnInit {
  turnos: any[] = [];
  diasSemana: string[] = [
    'Lunes',
    'Martes',
    'Miércoles',
    'Jueves',
    'Viernes',
    'Sábado',
    'Domingo',
  ];
  grupos: GrupoDTO[] = [];

  constructor(private endpointsService: EndpointsService, private router: Router) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerTurnos();
      this.obtenerGrupos();
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

  obtenerGrupos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerTodosLosGrupos(token).subscribe({
        next: (response) => {
          this.grupos = response;
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

  obtenerTurnosPorDia(diaSemana: string): any[] {
    return this.turnos.filter((turno) => turno.diaSemana === diaSemana);
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
        confirmButtonText: 'Sí, eliminarlo',
        cancelButtonText: 'Cancelar',
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService.eliminarTurno(turnoId, token).subscribe({
            next: () => {
              Swal.fire({
                title: 'Eliminado',
                text: 'El turno ha sido eliminado',
                icon: 'success',
              });
              this.obtenerTurnos();
            },
            error: (error) => {
              Swal.fire({
                title: 'Error en la eliminación',
                text: 'No hemos podido eliminar el turno' + error,
                icon: 'error',
              });
            },
          });
        }
      });
    }
  }

  irASeleccionarGrupo(turnoId: number): void {
    this.router.navigate(['/seleccionarGrupo', turnoId]);
  }
}
