import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';

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
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves'];
  isLoading: boolean = true;
  selectedTurno: any = null;
  showModal: boolean = false;
  limiteTurno: number = 36; // Valor por defecto

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
        this.limiteTurno = 36; // Valor por defecto en caso de error
      }
    });
  }

  obtenerTurnos() {
    this.isLoading = true;
    this.endpointsService.obtenerTurnosDTO().subscribe({
      next: (response) => {
        this.turnos = response;
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

  obtenerTurnosPorDia(diaSemana: string): any[] {
    return this.turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  obtenerCategoria(grupoId: number): string {
    switch (grupoId) {
      case 7:
        return 'Taekwondo Competición';
      case 8:
        return 'Pilates';
      case 9:
        return 'Kickboxing';
      case 10:
        return 'Defensa Personal Femenina';
      default:
        return 'Taekwondo';
    }
  }

  obtenerColorDeporte(deporte: string): string {
    const colores: { [key: string]: string } = {
      Pilates: '#A8D2D4',
      Kickboxing: '#FFA573',
      'Taekwondo Competición': '#F28B8B',
      Taekwondo: '#A6BFE3',
      'Defensa Personal Femenina': '#F8BBD0',
    };
    return colores[deporte] || '#ffffff';
  }

  obtenerEmoticonoCategoria(deporte: string): string {
    const emoticonos: { [key: string]: string } = {
      Pilates: '🧘‍♀️',
      Kickboxing: '🥊',
      Taekwondo: '🥋',
      'Taekwondo Competición': '🥋',
      'Defensa Personal Femenina': '🛡️',
    };
    return emoticonos[deporte] || '❓';
  }

  onTurnoClick(turno: any): void {
    this.selectedTurno = turno;
    this.showModal = true;
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
    // Quedan pocas plazas cuando el 80% o más de las plazas están ocupadas
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
}
