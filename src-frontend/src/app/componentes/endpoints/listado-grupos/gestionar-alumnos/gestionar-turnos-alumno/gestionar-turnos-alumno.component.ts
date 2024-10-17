import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule, Location } from '@angular/common';
import { EndpointsService } from '../../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Turno } from '../../../../../interfaces/turno';
import { AlumnoDTO } from '../../../../../interfaces/alumno-dto';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-gestionar-turnos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestionar-turnos-alumno.component.html',
  styleUrl: './gestionar-turnos-alumno.component.scss',
})
export class GestionarTurnosAlumnoComponent implements OnInit {
  alumnoId!: number;
  alumno: AlumnoDTO = {} as AlumnoDTO;
  turnos: Turno[] = [];
  turnosDisponibles: Turno[] = [];
  turnoSeleccionado!: number;

  constructor(
    private route: ActivatedRoute,
    private endpointsService: EndpointsService,
    private router: Router,
    private location: Location
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.alumnoId = +params['alumnoId'];
      this.cargarAlumno();
      this.cargarTurnos();
      this.cargarTurnosDisponibles();
    });
  }

  cargarAlumno(): void {
    this.endpointsService.obtenerAlumnoPorId(this.alumnoId).subscribe({
      next: (alumno: AlumnoDTO) => {
        this.alumno = alumno;
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los datos del alumno',
          icon: 'error',
        });
      },
    });
  }

  cargarTurnos(): void {
    this.endpointsService.obtenerTurnosDelAlumno(this.alumnoId).subscribe({
      next: (turnos: Turno[]) => {
        this.turnos = turnos; // Asegúrate de que esta propiedad contiene solo los turnos del alumno
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los turnos del alumno',
          icon: 'error',
        });
      },
    });
  }

  cargarTurnosDisponibles(): void {
    this.endpointsService.obtenerTurnos().subscribe({
      next: (turnos: Turno[]) => {
        this.turnosDisponibles = turnos; // Esto debería ser solo para turnos disponibles, no afecta a `this.turnos`
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido obtener los turnos disponibles',
          icon: 'error',
        });
      },
    });
  }

  asignarTurno(): void {
    if (this.turnoSeleccionado) {
      this.endpointsService
        .asignarAlumnoATurno(this.alumnoId, this.turnoSeleccionado)
        .subscribe({
          next: () => {
            Swal.fire(
              'Turno asignado',
              'El turno ha sido asignado al alumno con éxito',
              'success'
            );
            this.cargarTurnos(); // Recargar la lista de turnos
          },
          error: (err) => {
            console.error('Error al asignar turno:', err);
            let errorMessage = 'No hemos podido asignar el turno al alumno';
            if (err.error && typeof err.error === 'object') {
              errorMessage = err.error.message || errorMessage;
            }
            Swal.fire({
              title: 'Error al asignar turno',
              text: errorMessage,
              icon: 'error',
            });
          },
        });
    }
  }

  removerTurno(turnoId: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El turno será removido del alumno. Esta acción no se puede deshacer.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, remover',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .removerAlumnoDeTurno(this.alumnoId, turnoId)
          .subscribe({
            next: (turnosActualizados: Turno[]) => {
              this.turnos = turnosActualizados; // Actualizar la lista de turnos
              Swal.fire(
                'Turno removido',
                'El turno ha sido removido del alumno con éxito',
                'success'
              );
            },
            error: () => {
              Swal.fire({
                title: 'Error al remover turno',
                text: 'No hemos podido remover el turno del alumno',
                icon: 'error',
              });
            },
          });
      }
    });
  }

  volver() {
    this.location.back();
  }
}
