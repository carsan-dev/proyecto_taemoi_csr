import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule, Location } from '@angular/common';
import { EndpointsService } from '../../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Turno } from '../../../../../interfaces/turno';
import { AlumnoDTO } from '../../../../../interfaces/alumno-dto';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs/internal/Subscription';
import { SkeletonCardComponent } from '../../../../generales/skeleton-card/skeleton-card.component';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-gestionar-turnos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, SkeletonCardComponent],
  templateUrl: './gestionar-turnos-alumno.component.html',
  styleUrl: './gestionar-turnos-alumno.component.scss',
})
export class GestionarTurnosAlumnoComponent implements OnInit, OnDestroy {
  alumnoId!: number;
  alumno: AlumnoDTO = {} as AlumnoDTO;
  turnos: Turno[] = [];
  turnosDisponibles: Turno[] = [];
  turnoSeleccionado!: number;
  private readonly subscriptions: Subscription = new Subscription();
  cargando: boolean = true;

  constructor(
    private readonly route: ActivatedRoute,
    public readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.alumnoId = +params['alumnoId'];
      this.cargarAlumno();
      this.cargarTurnos();
      this.endpointsService.obtenerTurnos();
    });
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarAlumno(): void {
    this.cargando = true;
    this.endpointsService.obtenerAlumnoPorId(this.alumnoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (alumno: AlumnoDTO) => {
          this.alumno = alumno;
        },
        error: () => {
          this.cargando = false;
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido obtener los datos del alumno',
            icon: 'error',
          });
        },
      });
  }

  cargarTurnos(): void {
    // Suscribirse al Observable expuesto por el BehaviorSubject
    const turnosSubscription = this.endpointsService.turnosDelAlumno$.subscribe(
      {
        next: (turnos: Turno[]) => {
          this.turnos = turnos;
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido obtener los turnos del alumno',
            icon: 'error',
          });
        },
      }
    );

    this.subscriptions.add(turnosSubscription);

    // Llamar al método que inicia la carga de datos
    this.endpointsService.obtenerTurnosDelAlumno(this.alumnoId);
  }

  asignarTurno(): void {
    if (this.turnoSeleccionado) {
      this.endpointsService
        .asignarAlumnoATurno(this.alumnoId, this.turnoSeleccionado)
        .subscribe({
          next: () => {
            Swal.fire({
              title: 'Turno asignado',
              text: 'El turno ha sido asignado al alumno con éxito.',
              icon: 'success',
              timer: 2000,
            });
            this.cargarTurnos();
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
              this.turnos = turnosActualizados;
              Swal.fire({
                title: 'Turno removido',
                text: 'El turno ha sido removido del alumno con éxito.',
                icon: 'success',
                timer: 2000,
              });
              this.cargarTurnos();
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
