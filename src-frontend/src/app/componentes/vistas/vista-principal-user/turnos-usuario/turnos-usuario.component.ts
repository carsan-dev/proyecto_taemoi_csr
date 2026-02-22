import { CommonModule, Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
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

interface HorariosAlumnoCache {
  grupos: any[];
  turnos: Turno[];
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
  private readonly horariosAlumnoCache = new Map<number, HorariosAlumnoCache>();
  private requestSeq: number = 0;

  constructor(
    public endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly authService: AuthenticationService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.inicializarAlumnos();
  }

  private inicializarAlumnos(): void {
    const alumnoIdSolicitado = this.obtenerAlumnoIdSolicitado();
    const tieneAccesoAdmin = this.authService.tieneAccesoAdmin();
    const forzarListadoAdmin = tieneAccesoAdmin && alumnoIdSolicitado !== null;

    const origen$ = forzarListadoAdmin
      ? this.endpointsService.obtenerAlumnosSinPaginar(true)
      : this.authService.obtenerTodosLosAlumnos();

    const alumnosSubscription = origen$.subscribe({
      next: (alumnos) => {
        const alumnosNormalizados = Array.isArray(alumnos) ? alumnos : [];
        if (alumnosNormalizados.length > 0) {
          this.alumnos = alumnosNormalizados;
          const alumnoInicial = this.obtenerAlumnoInicial(alumnosNormalizados, alumnoIdSolicitado);
          if (alumnoInicial) {
            this.seleccionarAlumno(alumnoInicial);
            return;
          }
        }

        if (!forzarListadoAdmin && tieneAccesoAdmin) {
          this.cargarAlumnosComoAdmin(alumnoIdSolicitado);
          return;
        }

        this.inicializarAlumnoFallback();
      },
      error: () => {
        if (!forzarListadoAdmin && tieneAccesoAdmin) {
          this.cargarAlumnosComoAdmin(alumnoIdSolicitado);
          return;
        }
        this.inicializarAlumnoFallback();
      },
    });

    this.subscriptions.add(alumnosSubscription);
  }

  private cargarAlumnosComoAdmin(alumnoIdSolicitado: number | null): void {
    const adminSubscription = this.endpointsService.obtenerAlumnosSinPaginar(true).subscribe({
      next: (alumnos) => {
        const alumnosNormalizados = Array.isArray(alumnos) ? alumnos : [];
        if (alumnosNormalizados.length > 0) {
          this.alumnos = alumnosNormalizados;
          const alumnoInicial = this.obtenerAlumnoInicial(alumnosNormalizados, alumnoIdSolicitado);
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
    this.subscriptions.add(adminSubscription);
  }

  private obtenerAlumnoInicial(alumnos: any[], alumnoIdSolicitado: number | null): any | null {
    if (alumnoIdSolicitado !== null) {
      const alumnoSolicitado = alumnos.find((alumno) => Number(alumno?.id) === alumnoIdSolicitado);
      if (alumnoSolicitado) {
        return alumnoSolicitado;
      }
    }

    const alumnoIdSesion = Number(this.authService.getAlumnoId());
    if (Number.isInteger(alumnoIdSesion) && alumnoIdSesion > 0) {
      return alumnos.find((alumno) => Number(alumno?.id) === alumnoIdSesion) ?? alumnos[0];
    }
    return alumnos[0] ?? null;
  }

  private obtenerAlumnoIdSolicitado(): number | null {
    const alumnoIdRaw = this.route.snapshot.queryParamMap.get('alumnoId');
    if (!alumnoIdRaw) {
      return null;
    }

    const alumnoId = Number.parseInt(alumnoIdRaw, 10);
    return Number.isInteger(alumnoId) && alumnoId > 0 ? alumnoId : null;
  }

  private inicializarAlumnoFallback(): void {
    const alumnoId = Number(this.authService.getAlumnoId());
    if (Number.isInteger(alumnoId) && alumnoId > 0) {
      this.alumnoId = alumnoId;
      if (!this.selectedAlumno) {
        this.selectedAlumno = this.alumnos.find((alumno) => Number(alumno?.id) === alumnoId) ?? null;
      }
      this.cargarHorariosDelAlumno();
      return;
    }

    const usuarioSubscription = this.authService.obtenerUsuarioAutenticado().subscribe({
      next: (usuario) => {
        const resolvedId = Number(usuario?.alumnoDTO?.id ?? this.authService.getAlumnoId());
        if (Number.isInteger(resolvedId) && resolvedId > 0) {
          this.alumnoId = resolvedId;
          this.selectedAlumno =
            this.alumnos.find((alumno) => Number(alumno?.id) === resolvedId) ??
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
    const nuevoAlumnoId = Number(alumno?.id);
    if (!Number.isInteger(nuevoAlumnoId) || nuevoAlumnoId <= 0) {
      return;
    }
    if (this.alumnoId === nuevoAlumnoId && Number(this.selectedAlumno?.id) === nuevoAlumnoId) {
      return;
    }

    this.selectedAlumno = alumno;
    this.alumnoId = nuevoAlumnoId;
    this.cargarHorariosDelAlumno();
  }

  cargarHorariosDelAlumno(): void {
    const cache = this.horariosAlumnoCache.get(this.alumnoId);
    if (cache) {
      this.aplicarHorariosAlumno(cache.grupos, cache.turnos);
      return;
    }

    const requestId = ++this.requestSeq;
    this.cargando = true;
    const subscription = this.endpointsService.obtenerTurnosDelAlumnoObservable(this.alumnoId).subscribe({
      next: (turnos) => {
        if (!this.esSolicitudVigente(requestId)) {
          return;
        }

        const turnosNormalizados = this.normalizarTurnos(Array.isArray(turnos) ? turnos : []);
        if (turnosNormalizados.length > 0) {
          this.horariosAlumnoCache.set(this.alumnoId, {
            grupos: [],
            turnos: turnosNormalizados,
          });
          this.aplicarHorariosAlumno([], turnosNormalizados);
          return;
        }

        this.cargarGruposParaHorarioVacio(requestId, turnosNormalizados);
      },
      error: () => {
        if (!this.esSolicitudVigente(requestId)) {
          return;
        }
        this.aplicarEstadoErrorHorarios();
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

  private cargarGruposParaHorarioVacio(requestId: number, turnosNormalizados: Turno[]): void {
    const subscription = this.endpointsService.obtenerGruposDelAlumnoObservable(this.alumnoId).subscribe({
      next: (grupos) => {
        if (!this.esSolicitudVigente(requestId)) {
          return;
        }

        const gruposNormalizados = Array.isArray(grupos) ? grupos : [];
        this.horariosAlumnoCache.set(this.alumnoId, {
          grupos: gruposNormalizados,
          turnos: turnosNormalizados,
        });
        this.aplicarHorariosAlumno(gruposNormalizados, turnosNormalizados);
      },
      error: () => {
        if (!this.esSolicitudVigente(requestId)) {
          return;
        }
        this.aplicarEstadoErrorHorarios();
      },
    });
    this.subscriptions.add(subscription);
  }

  private aplicarHorariosAlumno(grupos: any[], turnos: Turno[]): void {
    this.grupos = grupos;
    this.allTurnos = turnos;
    this.deportesUnicos = this.buildDeportesUnicos();
    this.buildTimetable();
    this.selectedDayIndex = this.obtenerPrimerDiaConTurnos();
    this.cargando = false;
  }

  private aplicarEstadoErrorHorarios(): void {
    this.cargando = false;
    this.grupos = [];
    this.allTurnos = [];
    this.deportesUnicos = [];
    this.timeSlots = [];
    this.timetableGrid = new Map();
    Swal.fire({
      title: 'Error',
      text: 'No se pudieron obtener los horarios del alumno.',
      icon: 'error',
    });
  }

  private esSolicitudVigente(requestId: number): boolean {
    return requestId === this.requestSeq;
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
