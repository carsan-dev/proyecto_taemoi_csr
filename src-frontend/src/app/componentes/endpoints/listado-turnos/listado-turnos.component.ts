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
  styleUrls: ['./listado-turnos.component.scss'],
})
export class ListadoTurnosComponent implements OnInit {
  diasSemana: string[] = [
    'Lunes',
    'Martes',
    'Miércoles',
    'Jueves',
    'Viernes',
    'Sábado',
    'Domingo',
  ];
  gruposMostrar: string[] = [
    'Taekwondo',
    'Taekwondo Competición',
    'Pilates',
    'Kickboxing',
  ];

  constructor(public endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.endpointsService.obtenerTurnos();
    this.endpointsService.obtenerTodosLosGrupos();
    this.endpointsService.obtenerConteoAlumnosPorGrupo();
  }

  obtenerTurnosPorDia(turnos: any[], diaSemana: string): any[] {
    return turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  obtenerTotalAlumnos(turno: any): number {
    return turno.alumnos ? turno.alumnos.length : 0;
  }

  obtenerConteoAlumnos(grupoNombre: string): number {
    return this.endpointsService.conteoAlumnosPorGrupo[grupoNombre] || 0;
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
            this.endpointsService.obtenerTurnos(); // Recargar los turnos
          },
          error: () => {
            Swal.fire({
              title: 'Error en la eliminación',
              text: 'No hemos podido eliminar el turno',
              icon: 'error',
            });
          },
        });
      }
    });
  }
}
