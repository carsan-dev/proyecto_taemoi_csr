import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { AlumnoDTO } from '../../../../interfaces/alumno-dto';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { GrupoDTO } from '../../../../interfaces/grupo-dto';
import { PaginacionComponent } from '../../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-gestionar-alumnos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, PaginacionComponent],
  templateUrl: './gestionar-alumnos.component.html',
  styleUrl: './gestionar-alumnos.component.scss',
})
export class GestionarAlumnosComponent implements OnInit {
  grupo!: GrupoDTO;
  grupoId!: number;
  alumnos: AlumnoDTO[] = [];
  alumnosPaginados: AlumnoDTO[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.grupoId = +params['id'];
      this.cargarGrupo();
    });
  }

  cargarGrupo(): void {
    this.endpointsService.obtenerGrupoPorId(this.grupoId).subscribe({
      next: (grupo: GrupoDTO) => {
        this.grupo = grupo;
        this.alumnos = grupo.alumnos;
        this.totalPaginas = Math.ceil(this.alumnos.length / this.tamanoPagina);
        this.cambiarPagina(1);
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido conectar con el servidor',
          icon: 'error',
        });
      },
    });
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    const startIndex = (pageNumber - 1) * this.tamanoPagina;
    const endIndex = startIndex + this.tamanoPagina;
    this.alumnosPaginados = this.alumnos.slice(startIndex, endIndex);
  }

  redirigirSeleccionarAlumnos(): void {
    this.router.navigate(['/seleccionarAlumnos', this.grupoId]);
  }

  eliminarAlumno(alumnoId: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'No podrás revertir esta acción',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarAlumnoDeGrupo(this.grupoId, alumnoId)
          .subscribe({
            next: () => {
              Swal.fire({
                title: '¡Eliminado!',
                text: 'Alumno correctamente eliminado del grupo.',
                icon: 'success',
                timer: 2000,
              });
              this.alumnos = this.alumnos.filter(
                (alumno) => alumno.id !== alumnoId
              );
            },
            error: () => {
              Swal.fire({
                title: 'Error al eliminar alumno',
                text: 'No hemos podido eliminar el alumno',
                icon: 'error',
              });
            },
          });
      }
    });
  }
  gestionarTurnos(alumnoId: number): void {
    this.router.navigate([`/gestionarTurnosAlumno`, alumnoId]);
  }

  volver() {
    this.location.back();
  }
}
