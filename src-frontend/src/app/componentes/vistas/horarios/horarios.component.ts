import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-horarios',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './horarios.component.html',
  styleUrls: ['./horarios.component.scss'],
})
export class HorariosComponent implements OnInit {
  usuarioLogueado: boolean = false;
  turnos: any[] = [];
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves'];
  isLoading: boolean = true;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerTurnos();
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
}
