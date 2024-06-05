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
  styleUrl: './horarios.component.scss',
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
}
