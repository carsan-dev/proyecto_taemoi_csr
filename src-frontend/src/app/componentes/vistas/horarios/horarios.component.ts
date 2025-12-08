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
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
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
        console.log('Turnos recibidos:', response);
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
    return this.turnos
      .filter((turno) => turno.diaSemana === diaSemana)
      .filter((turno) => !this.esCompeticion(this.obtenerCategoria(turno)));
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
    if (this.esCompeticion(categoria)) return 'Taekwondo Competición';
    if (categoria.includes('taekwondo')) return 'Taekwondo';
    if (categoria.includes('kickboxing')) return 'Kickboxing';
    if (categoria.includes('pilates')) return 'Pilates';
    if (categoria.includes('defensa personal')) return 'Defensa Personal Femenina';
    return categoriaOriginal;
  }

  private esCompeticion(categoria: string): boolean {
    const normalizado = categoria.toLowerCase().trim();
    return normalizado.includes('competici');
  }

  obtenerColorDeporte(deporte: string): string {
    const deporteNormalizado = deporte.toLowerCase().trim();
    if (deporteNormalizado.includes('pilates')) return '#A8D2D4';
    if (deporteNormalizado.includes('kickboxing')) return '#FFA573';
    if (this.esCompeticion(deporteNormalizado)) return '#F28B8B';
    if (deporteNormalizado.includes('defensa personal')) return '#F8BBD0';
    return '#A6BFE3'; // Taekwondo default
  }

  obtenerEmoticonoCategoria(deporte: string): string {
    const deporteNormalizado = deporte.toLowerCase().trim();
    if (deporteNormalizado.includes('pilates')) return '🧘';
    if (deporteNormalizado.includes('kickboxing')) return '🥊';
    if (deporteNormalizado.includes('taekwondo') && !this.esCompeticion(deporteNormalizado)) return '🥋';
    if (this.esCompeticion(deporteNormalizado)) return '🏆';
    if (deporteNormalizado.includes('defensa personal')) return '🛡️';
    return '🙂';
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
