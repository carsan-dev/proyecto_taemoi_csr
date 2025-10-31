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
  allTurnos: Turno[] = []; // Store all turns from all groups
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

  // Sport utilities
  deportes = {
    TAEKWONDO: { nombre: 'Taekwondo', emoji: '🥋', color: '#dc3545' },
    KICKBOXING: { nombre: 'Kickboxing', emoji: '🥊', color: '#ffc107' },
    PILATES: { nombre: 'Pilates', emoji: '🧘', color: '#28a745' },
    DEFENSA_PERSONAL_FEMENINA: { nombre: 'Defensa Personal Femenina', emoji: '💪', color: '#6f42c1' }
  };

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
        // Load all turns once groups are loaded
        setTimeout(() => this.cargarTodosTurnos(), 100);
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

  obtenerTurnosPorDia(turnos: Turno[], diaSemana: string): Turno[] {
    return turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  // New methods for sport-based grouping
  cargarTodosTurnos(): void {
    const alumnoId = this.authService.getAlumnoId();
    if (!alumnoId) return;

    // Load all turns from all groups
    this.grupos.forEach(grupo => {
      const turnosSubscription = this.endpointsService.turnosDelGrupo$.subscribe({
        next: (response) => {
          // Add turns avoiding duplicates
          response.forEach(turno => {
            if (!this.allTurnos.find(t =>
              t.diaSemana === turno.diaSemana &&
              t.horaInicio === turno.horaInicio &&
              t.horaFin === turno.horaFin &&
              t.tipoGrupo === turno.tipoGrupo
            )) {
              this.allTurnos.push(turno);
            }
          });
        }
      });
      this.subscriptions.add(turnosSubscription);
      this.endpointsService.obtenerTurnosDelAlumnoEnGrupo(grupo.id, alumnoId);
    });
  }

  getDeportesUnicos(): string[] {
    const deportesSet = new Set(this.allTurnos.map(turno => turno.tipoGrupo));
    return Array.from(deportesSet).sort();
  }

  getTurnosPorDeporte(deporte: string): Turno[] {
    return this.allTurnos
      .filter(turno => turno.tipoGrupo === deporte)
      .sort((a, b) => {
        const diaOrder = this.diasSemana.indexOf(a.diaSemana) - this.diasSemana.indexOf(b.diaSemana);
        if (diaOrder !== 0) return diaOrder;
        return a.horaInicio.localeCompare(b.horaInicio);
      });
  }

  getDeporteInfo(tipoGrupo: string): any {
    return this.deportes[tipoGrupo as keyof typeof this.deportes] || {
      nombre: tipoGrupo,
      emoji: '⚽',
      color: '#6c757d'
    };
  }

  getTurnosSorted(): Turno[] {
    return this.allTurnos
      .sort((a, b) => {
        const diaOrder = this.diasSemana.indexOf(a.diaSemana) - this.diasSemana.indexOf(b.diaSemana);
        if (diaOrder !== 0) return diaOrder;
        return a.horaInicio.localeCompare(b.horaInicio);
      });
  }

  volver(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}