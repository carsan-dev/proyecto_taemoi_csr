import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
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
  expandedGrupoIndex: number | null = null; // Variable para manejar el acordeón

  constructor(
    public endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    const alumnoId = this.authService.getAlumnoId();
    if (alumnoId) {
      this.alumnoId = alumnoId;
      this.cargarGruposDelAlumno();
    } else {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo obtener el ID del alumno autenticado.',
        icon: 'error',
      });
    }
  }

  cargarGruposDelAlumno(): void {
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
    this.endpointsService.obtenerGruposDelAlumno(this.alumnoId);
  }

  toggleVerTurnos(grupoId: number, index: number): void {
    // Si el grupo ya está expandido y seleccionado, lo colapsamos
    if (this.grupoSeleccionadoId === grupoId && this.expandedGrupoIndex === index) {
      this.grupoSeleccionadoId = null;
      this.expandedGrupoIndex = -1;
      this.turnos = [];
    } else {
      // De lo contrario, expandimos el grupo y cargamos los turnos
      this.grupoSeleccionadoId = grupoId;
      this.expandedGrupoIndex = index;

      const alumnoId = this.authService.getAlumnoId();
      if (alumnoId) {
        const turnosSubscription =
          this.endpointsService.turnosDelGrupo$.subscribe({
            next: (response) => {
              this.turnos = response;
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

  volver(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}