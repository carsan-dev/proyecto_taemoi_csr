import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Subscription } from 'rxjs/internal/Subscription';
import { Turno } from '../../../../interfaces/turno';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';

@Component({
  selector: 'app-turnos-usuario',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-usuario.component.html',
  styleUrls: ['./turnos-usuario.component.scss'],
})
export class TurnosUsuarioComponent implements OnInit, OnDestroy {
  grupos: any[] = [];
  private readonly subscriptions: Subscription = new Subscription();
  turnos: Turno[] = [];
  diasSemana: string[] = [
    'Lunes',
    'Martes',
    'Miércoles',
    'Jueves',
    'Viernes',
    'Sábado',
    'Domingo',
  ];
  alumnoId!: number;
  grupoSeleccionadoId: number | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    public endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    const alumnoId = this.authService.getAlumnoId(); // Obtener el alumno ID del servicio de autenticación

    if (alumnoId) {
      this.alumnoId = alumnoId;
      // Suscribirse a los grupos del alumno
      const gruposSubscription = this.endpointsService.gruposDelAlumno$.subscribe({
        next: (grupos) => {
          this.grupos = grupos;
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'Error al obtener los grupos del alumno.',
            icon: 'error',
          });
        },
      });

      this.subscriptions.add(gruposSubscription);

      // Cargar los grupos del alumno
      this.endpointsService.obtenerGruposDelAlumno(this.alumnoId);
    } else {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo obtener el ID del alumno autenticado.',
        icon: 'error',
      });
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  verTurnos(grupoId: number): void {
    if (this.grupoSeleccionadoId === grupoId) {
      // Deselecciona el grupo y limpia los turnos
      this.grupoSeleccionadoId = null;
      this.turnos = [];
    } else {
      this.grupoSeleccionadoId = grupoId;

      // Obtener el ID del alumno autenticado
      const alumnoId = this.authService.getAlumnoId();

      if (alumnoId) {
        // Suscribirse a los turnos del alumno en el grupo
        const turnosSubscription = this.endpointsService.turnosDelGrupo$.subscribe({
          next: (response) => {
            this.turnos = response; // Ya estamos recibiendo solo los turnos del alumno
          },
          error: () => {
            Swal.fire({
              title: 'Error en la petición',
              text: 'Error al obtener los turnos del alumno en el grupo.',
              icon: 'error',
            });
          },
        });

        this.subscriptions.add(turnosSubscription);

        // Llamar al nuevo método que obtiene los turnos del alumno en el grupo
        this.endpointsService.obtenerTurnosDelAlumnoEnGrupo(grupoId, alumnoId);
      } else {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener el ID del alumno autenticado.',
          icon: 'error',
        });
      }
    }
  }

  obtenerTurnosPorDia(turnos: Turno[], diaSemana: string): Turno[] {
    return turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  volver() {
    this.location.back();
  }
}