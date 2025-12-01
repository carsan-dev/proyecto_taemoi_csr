import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { Subscription } from 'rxjs/internal/Subscription';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
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
  allTurnos: Turno[] = [];
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

  cargarTodosTurnos(): void {
    const alumnoId = this.authService.getAlumnoId();
    if (!alumnoId) return;

    this.grupos.forEach((grupo) => {
      const turnosSubscription = this.endpointsService.turnosDelGrupo$.subscribe({
        next: (response) => {
          response.forEach((turno) => {
            if (
              !this.allTurnos.find(
                (t) =>
                  t.diaSemana === turno.diaSemana &&
                  t.horaInicio === turno.horaInicio &&
                  t.horaFin === turno.horaFin &&
                  t.tipoGrupo === turno.tipoGrupo
              )
            ) {
              this.allTurnos.push(turno);
            }
          });
        },
      });
      this.subscriptions.add(turnosSubscription);
      this.endpointsService.obtenerTurnosDelAlumnoEnGrupo(grupo.id, alumnoId);
    });
  }

  getDeportesUnicos(): string[] {
    const deportesSet = new Set(this.allTurnos.map((turno) => this.normalizarDeporte(turno.tipoGrupo)));
    return Array.from(deportesSet).sort();
  }

  getTurnosPorDeporte(deporte: string): Turno[] {
    return this.allTurnos
      .filter((turno) => this.normalizarDeporte(turno.tipoGrupo) === deporte)
      .sort((a, b) => {
        const diaOrder = this.diasSemana.indexOf(a.diaSemana) - this.diasSemana.indexOf(b.diaSemana);
        if (diaOrder !== 0) return diaOrder;
        return a.horaInicio.localeCompare(b.horaInicio);
      });
  }

  getDeporteInfo(tipoGrupo: string): any {
    const key = this.normalizarDeporte(tipoGrupo);
    if (key.includes('competici')) {
      return {
        nombre: 'Taekwondo Competición',
        nombreCorto: 'Taekwondo Competición',
        icono: 'bi bi-trophy-fill',
        emoji: '🏆',
        color: '#f28b8b',
      };
    }
    if (key.includes('taekwondo')) {
      return { nombre: 'Taekwondo', nombreCorto: 'Taekwondo', icono: 'bi bi-shield-shaded', emoji: '🥋', color: '#a6bfe3' };
    }
    if (key.includes('kickboxing')) {
      return { nombre: 'Kickboxing', nombreCorto: 'Kickboxing', icono: 'bi bi-lightning-charge-fill', emoji: '🥊', color: '#ffa573' };
    }
    if (key.includes('pilates')) {
      return { nombre: 'Pilates', nombreCorto: 'Pilates', icono: 'bi bi-peace-fill', emoji: '🧘', color: '#a8d2d4' };
    }
    if (key.includes('defensa personal')) {
      return {
        nombre: 'Defensa Personal Femenina',
        nombreCorto: 'D.P. Femenina',
        icono: 'bi bi-shield-lock-fill',
        emoji: '🛡️',
        color: '#f8bbd0',
      };
    }
    return { nombre: tipoGrupo, nombreCorto: tipoGrupo, icono: 'bi bi-star-fill', emoji: '⭐', color: '#6c757d' };
  }

  getTurnosSorted(): Turno[] {
    return this.allTurnos.sort((a, b) => {
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

  private normalizarDeporte(valor: string): string {
    return (valor || '').toString().toLowerCase().trim();
  }
}
