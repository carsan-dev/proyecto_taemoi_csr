import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Subscription } from 'rxjs/internal/Subscription';
import { Turno } from '../../../../interfaces/turno';

@Component({
  selector: 'app-turnos-usuario',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-usuario.component.html',
  styleUrls: ['./turnos-usuario.component.scss'],
})
export class TurnosUsuarioComponent implements OnInit, OnDestroy {
  grupos: any[] = [];
  private subscriptions: Subscription = new Subscription();
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
  alumnoId!: number;
  grupoSeleccionadoId: number | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    public endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    const alumnoId = this.route.snapshot.paramMap.get('id');

    if (alumnoId) {
      this.alumnoId = Number(alumnoId);
      // Suscribirse a los grupos del alumno
      const gruposSubscription =
        this.endpointsService.gruposDelAlumno$.subscribe({
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

      // Suscribirse a los turnos del grupo
      const turnosSubscription =
        this.endpointsService.turnosDelGrupo$.subscribe({
          next: (turnos) => {
            this.turnos = turnos;
          },
          error: () => {
            Swal.fire({
              title: 'Error en la petición',
              text: 'Error al obtener los turnos del grupo.',
              icon: 'error',
            });
          },
        });

      this.subscriptions.add(turnosSubscription);

      // Cargar los turnos del grupo seleccionado
      this.endpointsService.obtenerTurnosDelGrupo(grupoId);
    }
  }

  obtenerTurnosPorDia(turnos: Turno[], diaSemana: string): Turno[] {
    return turnos.filter((turno) => turno.diaSemana === diaSemana);
  }
  
  volver() {
    this.location.back();
  }
}
