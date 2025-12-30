import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';

interface TimeSlot {
  horaInicio: string;
  horaFin: string;
  display: string;
}

interface TurnoEnCelda {
  turno: any;
  sport: string;
  emoji: string;
  displayName: string;
}

@Component({
  selector: 'app-horarios',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './horarios.component.html',
  styleUrls: ['./horarios.component.scss'],
})
export class HorariosComponent implements OnInit {
  usuarioLogueado: boolean = false;
  turnos: any[] = [];
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
  diasCortos: string[] = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie'];
  isLoading: boolean = true;
  selectedTurno: any = null;
  showModal: boolean = false;
  limiteTurno: number = 36;

  // Timetable data
  timeSlots: TimeSlot[] = [];
  timetableGrid: Map<string, Map<string, TurnoEnCelda | null>> = new Map();

  // Mobile: selected day for tab view
  selectedDayIndex: number = 0;

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.cargarLimiteTurno();
    this.obtenerTurnos();
  }

  cargarLimiteTurno(): void {
    this.endpointsService.obtenerLimiteTurno().subscribe({
      next: (limite) => {
        this.limiteTurno = limite;
      },
      error: (error) => {
        console.error('Error al cargar el límite de turno, usando valor por defecto:', error);
        this.limiteTurno = 36;
      }
    });
  }

  obtenerTurnos() {
    this.isLoading = true;
    this.endpointsService.obtenerTurnosDTO().subscribe({
      next: (response) => {
        // Filter out competition classes
        this.turnos = response.filter((turno: any) => {
          const categoria = this.obtenerCategoria(turno).toLowerCase();
          return !categoria.includes('competici');
        });
        this.buildTimetable();
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener el horario.',
          icon: 'error',
        });
      },
    });
  }

  buildTimetable(): void {
    // Extract unique time slots and sort them
    const timeSlotSet = new Map<string, TimeSlot>();

    this.turnos.forEach(turno => {
      const key = `${turno.horaInicio}-${turno.horaFin}`;
      if (!timeSlotSet.has(key)) {
        timeSlotSet.set(key, {
          horaInicio: turno.horaInicio,
          horaFin: turno.horaFin,
          display: `${turno.horaInicio} - ${turno.horaFin}`
        });
      }
    });

    // Sort by start time
    this.timeSlots = Array.from(timeSlotSet.values()).sort((a, b) => {
      return a.horaInicio.localeCompare(b.horaInicio);
    });

    // Build the grid: timeSlot -> day -> turno
    this.timetableGrid = new Map();

    this.timeSlots.forEach(slot => {
      const slotKey = `${slot.horaInicio}-${slot.horaFin}`;
      const dayMap = new Map<string, TurnoEnCelda | null>();

      this.diasSemana.forEach(dia => {
        const turno = this.turnos.find(t =>
          t.diaSemana === dia &&
          t.horaInicio === slot.horaInicio &&
          t.horaFin === slot.horaFin
        );

        if (turno) {
          dayMap.set(dia, {
            turno: turno,
            sport: this.obtenerEtiquetaColor(turno),
            emoji: this.obtenerEmoticonoCategoria(this.obtenerEtiquetaColor(turno)),
            displayName: this.obtenerNombreMostrado(turno)
          });
        } else {
          dayMap.set(dia, null);
        }
      });

      this.timetableGrid.set(slotKey, dayMap);
    });
  }

  getTurnoEnCelda(timeSlot: TimeSlot, dia: string): TurnoEnCelda | null {
    const slotKey = `${timeSlot.horaInicio}-${timeSlot.horaFin}`;
    const dayMap = this.timetableGrid.get(slotKey);
    return dayMap?.get(dia) || null;
  }

  // Mobile: get turnos for selected day
  getTurnosForDay(dia: string): TurnoEnCelda[] {
    const result: TurnoEnCelda[] = [];
    this.timeSlots.forEach(slot => {
      const celda = this.getTurnoEnCelda(slot, dia);
      if (celda) {
        result.push(celda);
      }
    });
    return result;
  }

  selectDay(index: number): void {
    this.selectedDayIndex = index;
  }

  obtenerNombreMostrado(turno: any): string {
    const categoriaOriginal = this.obtenerCategoria(turno);
    const categoria = categoriaOriginal.toLowerCase().trim();
    if (categoria.includes('taekwondo') && !this.esCompeticion(categoria)) return 'Taekwondo';
    if (categoria.includes('kickboxing')) return 'Kickboxing';
    if (categoria.includes('pilates')) return 'Pilates';
    if (categoria.includes('defensa personal')) return 'D.P. Femenina';
    return categoriaOriginal;
  }

  obtenerCategoria(turno: any): string {
    return turno.tipoGrupo || 'Taekwondo';
  }

  obtenerEtiquetaColor(turno: any): string {
    const categoriaOriginal = this.obtenerCategoria(turno);
    const categoria = categoriaOriginal.toLowerCase().trim();
    if (this.esCompeticion(categoria)) return 'competicion';
    if (categoria.includes('taekwondo')) return 'taekwondo';
    if (categoria.includes('kickboxing')) return 'kickboxing';
    if (categoria.includes('pilates')) return 'pilates';
    if (categoria.includes('defensa personal')) return 'defensa';
    return 'taekwondo';
  }

  private esCompeticion(categoria: string): boolean {
    const normalizado = categoria.toLowerCase().trim();
    return normalizado.includes('competici');
  }

  obtenerEmoticonoCategoria(sport: string): string {
    const sportNormalizado = sport.toLowerCase().trim();
    if (sportNormalizado.includes('pilates')) return '🧘';
    if (sportNormalizado.includes('kickboxing')) return '🥊';
    if (sportNormalizado.includes('competicion')) return '🏆';
    if (sportNormalizado.includes('defensa')) return '🛡️';
    return '🥋'; // Taekwondo default
  }

  onTurnoClick(turno: any): void {
    this.selectedTurno = turno;
    this.showModal = true;
  }

  onCeldaClick(celda: TurnoEnCelda | null): void {
    if (celda) {
      this.onTurnoClick(celda.turno);
    }
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedTurno = null;
  }

  calcularEdad(fechaNacimiento: Date): number {
    const today = new Date();
    const birthDate = new Date(fechaNacimiento);
    let age = today.getFullYear() - birthDate.getFullYear();
    const monthDiff = today.getMonth() - birthDate.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age;
  }

  obtenerRangoEdades(turno: any): string {
    const rangoMin = turno?.rangoEdadMin;
    const rangoMax = turno?.rangoEdadMax;
    if (rangoMin != null || rangoMax != null) {
      if (rangoMin != null && rangoMax != null) {
        return `${rangoMin} - ${rangoMax} años`;
      }
      if (rangoMin != null) {
        return `Desde ${rangoMin} años`;
      }
      return `Hasta ${rangoMax} años`;
    }

    if (!turno.alumnos || turno.alumnos.length === 0) {
      return 'Sin alumnos';
    }

    const edades = turno.alumnos.map((alumno: any) =>
      this.calcularEdad(alumno.fechaNacimiento)
    );

    const minEdad = Math.min(...edades);
    const maxEdad = Math.max(...edades);

    if (minEdad === maxEdad) {
      return `${minEdad} años`;
    }

    return `${minEdad} - ${maxEdad} años`;
  }

  obtenerNumeroAlumnos(turno: any): number {
    return turno.alumnos ? turno.alumnos.length : 0;
  }

  quedanPocasPlazas(turno: any): boolean {
    const numAlumnos = this.obtenerNumeroAlumnos(turno);
    const UMBRAL_POCAS_PLAZAS = Math.ceil(this.limiteTurno * 0.8);
    return numAlumnos >= UMBRAL_POCAS_PLAZAS && numAlumnos < this.limiteTurno;
  }

  estaLleno(turno: any): boolean {
    return this.obtenerNumeroAlumnos(turno) >= this.limiteTurno;
  }

  navegarAContacto(): void {
    this.closeModal();
    this.router.navigate(['/contacto']);
  }

  obtenerTextoBoton(turno: any): string {
    if (this.estaLleno(turno)) {
      return 'Escríbenos ahora';
    } else if (this.quedanPocasPlazas(turno)) {
      return 'Inscríbete ahora';
    } else {
      return 'Inscríbete aquí';
    }
  }

  getAvailabilityClass(turno: any): string {
    if (this.estaLleno(turno)) return 'full';
    if (this.quedanPocasPlazas(turno)) return 'warning';
    return 'available';
  }
}
