import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import Swal from 'sweetalert2';
import { AlumnoDTO } from '../../../../../interfaces/alumno-dto';
import { EndpointsService } from '../../../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-seleccionar-alumnos',
  standalone: true,
  imports: [CommonModule, PaginacionComponent],
  templateUrl: './seleccionar-alumnos.component.html',
  styleUrl: './seleccionar-alumnos.component.scss'
})
export class SeleccionarAlumnosComponent implements OnInit {
  alumnos: AlumnoDTO[] = [];
  grupoId!: number;
  alumnosSeleccionados: number[] = [];
  paginaActual: number = 1;
  totalPaginas: number = 0;
  tamanoPagina: number = 10;
  alumnosEnGrupo: AlumnoDTO[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.grupoId = +params['id'];
      this.cargarGrupoYAlumnos();
    });
  }

  cargarGrupoYAlumnos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerGrupoPorId(this.grupoId, token).subscribe({
        next: (grupo) => {
          this.alumnosEnGrupo = grupo.alumnos;
          this.cargarAlumnos(token);
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        }
      });
    }
  }

  cargarAlumnos(token: string): void {
    this.endpointsService.obtenerAlumnos(token, this.paginaActual, this.tamanoPagina).subscribe({
      next: (response) => {
        const idsAlumnosEnGrupo = this.alumnosEnGrupo.map(alumno => alumno.id);
        this.alumnos = response.content.filter((alumno: { id: number; }) => !idsAlumnosEnGrupo.includes(alumno.id));
        this.totalPaginas = response.totalPages;
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

  agregarAlumnos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      const requests = this.alumnosSeleccionados.map(alumnoId =>
        this.endpointsService.agregarAlumnoAGrupo(this.grupoId, alumnoId, token).toPromise()
      );

      Promise.all(requests).then(() => {
        Swal.fire({
          title: 'Alumnos agregados',
          text: 'Los alumnos han sido agregados al grupo exitosamente',
          icon: 'success',
        }).then(() => {
          this.router.navigate(['/gestionarAlumnos', this.grupoId]);
        });
      }).catch(() => {
        Swal.fire({
          title: 'Error al agregar alumnos',
          text: 'No hemos podido agregar los alumnos',
          icon: 'error',
        });
      });
    }
  }

  toggleSeleccionAlumno(alumnoId: number): void {
    const index = this.alumnosSeleccionados.indexOf(alumnoId);
    if (index === -1) {
      this.alumnosSeleccionados.push(alumnoId);
    } else {
      this.alumnosSeleccionados.splice(index, 1);
    }
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    const token = localStorage.getItem('token');
    if (token) {
      this.cargarAlumnos(token);
    }
  }
}
