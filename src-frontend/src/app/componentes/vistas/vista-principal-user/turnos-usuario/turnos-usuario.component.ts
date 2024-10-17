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
  styleUrls: ['./turnos-usuario.component.scss'],
})
export class TurnosUsuarioComponent implements OnInit {
  grupos: any[] = [];
  turnos: any[] = [];
  diasSemana: string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
  alumnoId!: number;
  grupoSeleccionadoId: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private endpointsService: EndpointsService,
    private location: Location
  ) {}

  ngOnInit(): void {
    const alumnoId = this.route.snapshot.paramMap.get('id');

    if (alumnoId) {
      this.alumnoId = Number(alumnoId);
      this.cargarGruposDelAlumno(this.alumnoId);
    }
  }

  cargarGruposDelAlumno(alumnoId: number): void {
    this.endpointsService.obtenerGruposDelAlumno(alumnoId).subscribe({
      next: (grupos) => {
        this.grupos = grupos;
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los grupos del alumno.',
          icon: 'error',
        });
      },
    });
  }

  verTurnos(grupoId: number): void {
    if (this.grupoSeleccionadoId === grupoId) {
      // Si ya está seleccionado, lo deseleccionamos
      this.grupoSeleccionadoId = null;
      this.turnos = [];
    } else {
      this.grupoSeleccionadoId = grupoId;
      this.cargarTurnosDelGrupo(grupoId);
    }
  }

  cargarTurnosDelGrupo(grupoId: number): void {
    this.endpointsService.obtenerTurnosDelGrupo(grupoId).subscribe({
      next: (turnos) => {
        this.turnos = turnos;
      },
      error: () => {
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
