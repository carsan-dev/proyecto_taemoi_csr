import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import Swal from 'sweetalert2';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Turno } from '../../../../interfaces/turno';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { SkeletonCardComponent } from '../../../generales/skeleton-card/skeleton-card.component';

interface TimeSlot {
  horaInicio: string;
  horaFin: string;
  display: string;
}

interface TurnoEnCelda {
  turno: Turno;
  sportKey: string;
  iconClass: string;
  color: string;
  displayName: string;
}

interface DeporteInfo {
  key: string;
  nombre: string;
  nombreCorto: string;
  iconClass: string;
  color: string;
}

@Component({
  selector: 'app-turnos-usuario',
  standalone: true,
  imports: [CommonModule, SkeletonCardComponent],
  templateUrl: './turnos-usuario.component.html',
  styleUrls: ['./turnos-usuario.component.scss'],
})
export class TurnosUsuarioComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  selectedAlumno: any = null;
  grupos: any[] = [];
  cargando: boolean = true;
  allTurnos: Turno[] = [];
  deportesUnicos: DeporteInfo[] = [];
  diasSemana: string[] = ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes'];
  diasCortos: string[] = ['Lun', 'Mar', 'Mie', 'Jue', 'Vie'];
  timeSlots: TimeSlot[] = [];
  timetableGrid: Map<string, Map<string, TurnoEnCelda[]>> = new Map();
  selectedDayIndex: number = 0;
  alumnoId!: number;

  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.inicializarAlumnos();
  }

  private inicializarAlumnos(): void {
    const alumnosSubscription = this.authService.obtenerTodosLosAlumnos().subscribe({
      next: (alumnos) => {
        if (Array.isArray(alumnos) && alumnos.length > 0) {
          this.alumnos = alumnos;
          const alumnoInicial = this.obtenerAlumnoInicial(alumnos);
          if (alumnoInicial) {
            this.seleccionarAlumno(alumnoInicial);
            return;
          }
        }

        this.inicializarAlumnoFallback();
      },
      error: () => {
        this.inicializarAlumnoFallback();
      },
    });

    this.subscriptions.add(alumnosSubscription);
  }

  private obtenerAlumnoInicial(alumnos: any[]): any | null {
    const alumnoId = this.authService.getAlumnoId();
    if (alumnoId) {
      return alumnos.find((alumno) => alumno?.id === alumnoId) ?? alumnos[0];
    }
    return alumnos[0] ?? null;
  }

  private inicializarAlumnoFallback(): void {
    const alumnoId = this.authService.getAlumnoId();
    if (alumnoId) {
      this.alumnoId = alumnoId;
      if (!this.selectedAlumno) {
        this.selectedAlumno = this.alumnos.find((alumno) => alumno?.id === alumnoId) ?? null;
      }
      this.cargarHorariosDelAlumno();
      return;
    }

    const usuarioSubscription = this.authService.obtenerUsuarioAutenticado().subscribe({
      next: (usuario) => {
        const resolvedId = usuario?.alumnoDTO?.id ?? this.authService.getAlumnoId();
        if (resolvedId) {
          this.alumnoId = resolvedId;
          this.selectedAlumno =
            this.alumnos.find((alumno) => alumno?.id === resolvedId) ??
            usuario?.alumnoDTO ??
            this.selectedAlumno;
          this.cargarHorariosDelAlumno();
          return;
        }

        this.cargando = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener el ID del alumno autenticado.',
          icon: 'error',
        });
      },
      error: () => {
        this.cargando = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener el ID del alumno autenticado.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(usuarioSubscription);
  }

  seleccionarAlumno(alumno: any): void {
    const nuevoAlumnoId = alumno?.id;
    if (!nuevoAlumnoId) {
      return;
    }
    if (this.alumnoId === nuevoAlumnoId && this.selectedAlumno?.id === nuevoAlumnoId) {
      return;
    }

    this.selectedAlumno = alumno;
    this.alumnoId = nuevoAlumnoId;
    this.cargarHorariosDelAlumno();
  }

  cargarHorariosDelAlumno(): void {
    this.cargando = true;

    const grupos$ = this.endpointsService.obtenerGruposDelAlumnoObservable(this.alumnoId);
    const turnos$ = this.endpointsService.obtenerTurnosDelAlumnoObservable(this.alumnoId);

    const subscription = forkJoin({ grupos: grupos$, turnos: turnos$ }).subscribe({
      next: ({ grupos, turnos }) => {
        this.grupos = Array.isArray(grupos) ? grupos : [];
        this.allTurnos = this.normalizarTurnos(Array.isArray(turnos) ? turnos : []);
        this.deportesUnicos = this.buildDeportesUnicos();
        this.buildTimetable();
        this.selectedDayIndex = this.obtenerPrimerDiaConTurnos();
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        this.deportesUnicos = [];
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron obtener los horarios del alumno.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(subscription);
  }

  buildTimetable(): void {
    const timeSlotSet = new Map<string, TimeSlot>();

    this.allTurnos.forEach((turno) => {
      if (!turno) return;
      const key = `${turno.horaInicio}-${turno.horaFin}`;
      if (!timeSlotSet.has(key)) {
        timeSlotSet.set(key, {
          horaInicio: turno.horaInicio,
          horaFin: turno.horaFin,
          display: `${turno.horaInicio} - ${turno.horaFin}`,
        });
      }
    });

    this.timeSlots = Array.from(timeSlotSet.values()).sort((a, b) =>
      a.horaInicio.localeCompare(b.horaInicio)
    );

    this.timetableGrid = new Map();

    this.timeSlots.forEach((slot) => {
      const slotKey = `${slot.horaInicio}-${slot.horaFin}`;
      const dayMap = new Map<string, TurnoEnCelda[]>();

      this.diasSemana.forEach((dia) => {
        const diaKey = this.normalizarDia(dia);
        const celdas = this.allTurnos
          .filter(
            (turno) =>
              this.normalizarDia(turno.diaSemana) === diaKey &&
              turno.horaInicio === slot.horaInicio &&
              turno.horaFin === slot.horaFin
          )
          .map((turno) => this.mapTurnoToCelda(turno))
          .sort((a, b) => a.displayName.localeCompare(b.displayName));

        dayMap.set(dia, celdas);
      });

      this.timetableGrid.set(slotKey, dayMap);
    });
  }

  getTurnosEnCelda(slot: TimeSlot, dia: string): TurnoEnCelda[] {
    const slotKey = `${slot.horaInicio}-${slot.horaFin}`;
    const dayMap = this.timetableGrid.get(slotKey);
    return dayMap?.get(dia) || [];
  }

  getTurnosForDay(dia: string): TurnoEnCelda[] {
    const result: TurnoEnCelda[] = [];
    this.timeSlots.forEach((slot) => {
      result.push(...this.getTurnosEnCelda(slot, dia));
    });
    return result;
  }

  getCantidadTurnosDia(dia: string): number {
    return this.getTurnosForDay(dia).length;
  }

  selectDay(index: number): void {
    this.selectedDayIndex = index;
  }

  volver(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private buildDeportesUnicos(): DeporteInfo[] {
    const deportesMap = new Map<string, DeporteInfo>();

    this.allTurnos.forEach((turno) => {
      const info = this.getDeporteInfo(turno.tipoGrupo);
      if (!deportesMap.has(info.key)) {
        deportesMap.set(info.key, info);
      }
    });

    return Array.from(deportesMap.values()).sort((a, b) => a.nombre.localeCompare(b.nombre));
  }

  private obtenerPrimerDiaConTurnos(): number {
    for (let i = 0; i < this.diasSemana.length; i += 1) {
      if (this.getTurnosForDay(this.diasSemana[i]).length > 0) {
        return i;
      }
    }
    return 0;
  }

  private normalizarTurnos(turnos: Turno[]): Turno[] {
    const seen = new Set<number>();
    const resultado: Turno[] = [];

    turnos.forEach((turno) => {
      if (!turno) return;
      if (!this.esDiaVisible(turno.diaSemana)) return;
      if (turno.id != null) {
        if (seen.has(turno.id)) return;
        seen.add(turno.id);
      }
      resultado.push(turno);
    });

    return resultado;
  }

  private mapTurnoToCelda(turno: Turno): TurnoEnCelda {
    const info = this.getDeporteInfo(turno.tipoGrupo);
    return {
      turno,
      sportKey: info.key,
      iconClass: info.iconClass,
      color: info.color,
      displayName: info.nombreCorto || info.nombre,
    };
  }

  private getDeporteInfo(tipoGrupo: string): DeporteInfo {
    const key = this.normalizarDeporte(tipoGrupo);

    if (key.includes('competici')) {
      return {
        key: 'competicion',
        nombre: 'Taekwondo Competicion',
        nombreCorto: 'Tkd. Competicion',
        iconClass: 'bi-trophy-fill',
        color: '#7b1fa2',
      };
    }

    if (key.includes('taekwondo')) {
      return {
        key: 'taekwondo',
        nombre: 'Taekwondo',
        nombreCorto: 'Taekwondo',
        iconClass: 'bi-lightning-charge-fill',
        color: '#0d47a1',
      };
    }

    if (key.includes('kickboxing')) {
      return {
        key: 'kickboxing',
        nombre: 'Kickboxing',
        nombreCorto: 'Kickboxing',
        iconClass: 'bi-fire',
        color: '#ff4500',
      };
    }

    if (key.includes('pilates')) {
      return {
        key: 'pilates',
        nombre: 'Pilates',
        nombreCorto: 'Pilates',
        iconClass: 'bi-heart-pulse-fill',
        color: '#57a2a8',
      };
    }

    if (key.includes('defensa personal') || key.includes('defensa')) {
      return {
        key: 'defensa',
        nombre: 'Defensa Personal Femenina',
        nombreCorto: 'D.P. Femenina',
        iconClass: 'bi-shield-fill-check',
        color: '#c2185b',
      };
    }

    const fallbackName = (tipoGrupo || 'Clase').toString().trim() || 'Clase';
    return {
      key: key || 'otro',
      nombre: fallbackName,
      nombreCorto: fallbackName,
      iconClass: 'bi-star-fill',
      color: '#6c757d',
    };
  }

  private normalizarDeporte(valor: string): string {
    return (valor || '').toString().toLowerCase().trim();
  }

  private normalizarDia(valor: string): string {
    return (valor || '')
      .toString()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }

  private esDiaVisible(dia: string): boolean {
    const diaKey = this.normalizarDia(dia);
    return this.diasSemana.some((item) => this.normalizarDia(item) === diaKey);
  }
}
