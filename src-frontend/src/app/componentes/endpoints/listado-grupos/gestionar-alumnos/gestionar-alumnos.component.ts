import { Component, OnInit, OnDestroy } from '@angular/core';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../../utils/toast.util';
import { AlumnoDTO } from '../../../../interfaces/alumno-dto';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { GrupoDTO } from '../../../../interfaces/grupo-dto';
import { PaginacionComponent } from '../../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../../generales/skeleton-card/skeleton-card.component';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';

@Component({
  selector: 'app-gestionar-alumnos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, PaginacionComponent, SkeletonCardComponent, RouterLink],
  templateUrl: './gestionar-alumnos.component.html',
  styleUrl: './gestionar-alumnos.component.scss',
})
export class GestionarAlumnosComponent implements OnInit, OnDestroy {
  grupo!: GrupoDTO;
  grupoId!: number;
  alumnos: any[] = [];
  alumnosFiltrados: any[] = [];
  alumnosPaginados: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  cargando: boolean = true; // Loading state
  private searchSubject = new Subject<string>();

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

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(300), // Wait 300ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.filtrarAlumnos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  cargarGrupo(): void {
    this.cargando = true;
    this.endpointsService.obtenerGrupoPorId(this.grupoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (grupo: GrupoDTO) => {
          this.grupo = grupo;
          this.alumnos = grupo.alumnos;
          this.alumnosFiltrados = grupo.alumnos;
          this.totalPaginas = Math.ceil(this.alumnosFiltrados.length / this.tamanoPagina);
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
    this.alumnosPaginados = this.alumnosFiltrados.slice(startIndex, endIndex);
  }

  filtrarAlumnos(): void {
    if (!this.nombreFiltro || this.nombreFiltro.trim() === '') {
      this.alumnosFiltrados = this.alumnos;
    } else {
      const filtroLowerCase = this.nombreFiltro.toLowerCase().trim();
      this.alumnosFiltrados = this.alumnos.filter(alumno =>
        `${alumno.nombre} ${alumno.apellidos}`.toLowerCase().includes(filtroLowerCase)
      );
    }
    this.totalPaginas = Math.ceil(this.alumnosFiltrados.length / this.tamanoPagina);
    this.cambiarPagina(1);
  }

  onSearchChange(): void {
    this.searchSubject.next(this.nombreFiltro);
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
              showSuccessToast('Alumno eliminado del grupo');
              this.alumnos = this.alumnos.filter(
                (alumno) => alumno.id !== alumnoId
              );
              this.filtrarAlumnos();
            },
            error: () => {
              showErrorToast('No se pudo eliminar el alumno');
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
