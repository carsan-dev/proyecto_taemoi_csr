import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
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
  diasSemana: string[] = [
    'Lunes',
    'Martes',
    'Miércoles',
    'Jueves',
  ];

  constructor(
    private authService: AuthenticationService,
    private endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
    this.obtenerTurnos();
  }

  obtenerTurnos() {
    this.endpointsService.obtenerTurnosDTO().subscribe({
      next: (response) => {
        this.turnos = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener el horario.',
          icon: 'error',
        });
      },
    });
  }

  obtenerTurnosPorDia(diaSemana: string): any[] {
    return this.turnos.filter(turno => turno.diaSemana === diaSemana);
  }

  obtenerCategoria(grupoId: number): string {
    switch (grupoId) {
      case 8:
        return 'Pilates';
      case 9:
        return 'Kickboxing';
      case 7:
        return 'Taekwondo Competición';
      default:
        return 'Taekwondo';
    }
  }

  obtenerColorDeporte(deporte: string): string {
    const colores: { [key: string]: string } = {
      'Pilates': '#A8D2D4',
      'Kickboxing': '#FFA573',
      'Taekwondo Competición': '#F28B8B',
      'Taekwondo': '#A6BFE3'
    };
    return colores[deporte] || '#ffffff';
  }

  obtenerEmoticonoCategoria(deporte: string): string {
    const emoticonos: { [key: string]: string } = {
      'Pilates': '🧘‍♀️',
      'Kickboxing': '🥊', // Guante de boxeo
      'Taekwondo': '🥋', // Kimono/dobok
      'Taekwondo Competición': '🥋', // Usamos el mismo emoticono de kimono/dobok como aproximación
    };
    return emoticonos[deporte] || '❓'; // Emoticono por defecto en caso de que no se encuentre el deporte
  }
  
}
