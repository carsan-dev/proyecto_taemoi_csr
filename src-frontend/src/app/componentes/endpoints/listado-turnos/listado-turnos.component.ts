import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
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
  conteoAlumnosPorGrupo: any = {};
  gruposMostrar: string[] = [
    'Taekwondo',
    'Taekwondo Competición',
    'Pilates',
    'Kickboxing',
  ];

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerTurnos();
    this.obtenerGrupos();
    this.obtenerConteoAlumnosPorGrupo();
  }

  obtenerTurnos(): void {
    this.endpointsService.obtenerTurnos().subscribe({
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

  obtenerGrupos(): void {
    this.endpointsService.obtenerTodosLosGrupos().subscribe({
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

  obtenerConteoAlumnosPorGrupo(): void {
    this.endpointsService.obtenerConteoAlumnosPorGrupo().subscribe({
      next: (response) => {
        this.conteoAlumnosPorGrupo = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener el conteo de alumnos por grupo',
          icon: 'error',
        });
      },
    });
  }

  obtenerTurnosPorDia(diaSemana: string): any[] {
    return this.turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  obtenerTotalAlumnos(turno: any): number {
    return turno.alumnos ? turno.alumnos.length : 0;
  }

  obtenerConteoAlumnos(grupoNombre: string): number {
    return this.conteoAlumnosPorGrupo[grupoNombre] || 0;
  }

  eliminarTurno(turnoId: number): void {
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
        this.endpointsService.eliminarTurno(turnoId).subscribe({
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
