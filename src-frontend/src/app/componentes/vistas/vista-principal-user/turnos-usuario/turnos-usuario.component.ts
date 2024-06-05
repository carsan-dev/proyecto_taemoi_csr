import { CommonModule, Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';

@Component({
  selector: 'app-turnos-usuario',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-usuario.component.html',
  styleUrl: './turnos-usuario.component.scss'
})
export class TurnosUsuarioComponent implements OnInit {
  turnos: any[] = [];
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves'];

  constructor(
    private route: ActivatedRoute,
    private endpointsService: EndpointsService,
    private location: Location
  ) {}

  ngOnInit(): void {
    const token = localStorage.getItem('token');
    const grupoId = this.route.snapshot.paramMap.get('id');

    if (token && grupoId) {
      this.cargarTurnosDelGrupo(Number(grupoId), token);
    }
  }

  cargarTurnosDelGrupo(grupoId: number, token: string): void {
    this.endpointsService.obtenerTurnosDelGrupo(grupoId, token).subscribe({
      next: (turnos) => {
        this.turnos = turnos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los turnos del grupo.',
          icon: 'error',
        });
      },
    });
  }

  obtenerTurnosPorDia(diaSemana: string): any[] {
    return this.turnos.filter((turno) => turno.diaSemana === diaSemana);
  }

  volver() {
    this.location.back();
  }
}
