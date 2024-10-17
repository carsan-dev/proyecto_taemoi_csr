import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import Swal from 'sweetalert2';
import { GrupoDTO } from '../../../../interfaces/grupo-dto';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-seleccionar-grupo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './seleccionar-grupo.component.html',
  styleUrl: './seleccionar-grupo.component.scss',
})
export class SeleccionarGrupoComponent implements OnInit {
  grupos: GrupoDTO[] = [];
  turnoId!: number;

  constructor(
    private endpointsService: EndpointsService,
    private route: ActivatedRoute,
    private router: Router,
    private location: Location
  ) {}

  ngOnInit(): void {
    this.turnoId = +this.route.snapshot.paramMap.get('turnoId')!;
    this.obtenerGrupos();
  }

  obtenerGrupos(): void {
    this.endpointsService.obtenerTodosLosGrupos().subscribe({
      next: (response) => {
        this.grupos = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido conectar con el servidor',
          icon: 'error',
        });
      },
    });
  }

  asignarGrupo(grupoId: number): void {
    this.endpointsService.agregarTurnoAGrupo(grupoId, this.turnoId).subscribe({
      next: () => {
        Swal.fire({
          title: 'Asignado',
          text: 'El turno ha sido asignado al grupo',
          icon: 'success',
        }).then(() => {
          this.router.navigate(['/turnosListar']);
        });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la asignación',
          text: 'No hemos podido asignar el turno al grupo',
          icon: 'error',
        });
      },
    });
  }

  volver() {
    this.location.back();
  }
}
