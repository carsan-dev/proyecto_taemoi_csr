import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../../../utils/toast.util';
import { AlumnoDTO } from '../../../../../interfaces/alumno-dto';
import { EndpointsService } from '../../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { PaginacionComponent } from '../../../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { SkeletonCardComponent } from '../../../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-seleccionar-alumnos',
  standalone: true,
  imports: [CommonModule, PaginacionComponent, FormsModule, SkeletonCardComponent],
  templateUrl: './seleccionar-alumnos.component.html',
  styleUrl: './seleccionar-alumnos.component.scss',
})
export class SeleccionarAlumnosComponent implements OnInit, OnDestroy {
  alumnos: AlumnoDTO[] = [];
  grupoId!: number;
  alumnosSeleccionados: number[] = [];
  paginaActual: number = 1;
  totalPaginas: number = 0;
  tamanoPagina: number = 10;
  alumnosEnGrupo: AlumnoDTO[] = [];
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  cargando: boolean = true;
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
      this.cargarGrupoYAlumnos();
    });

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500), // Wait 500ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.paginaActual = 1;
      this.cargarAlumnos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  cargarGrupoYAlumnos(): void {
    this.cargando = true;
    this.endpointsService.obtenerGrupoPorId(this.grupoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (grupo) => {
          this.alumnosEnGrupo = grupo.alumnos;
          this.cargarAlumnos();
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

  cargarAlumnos(): void {
    this.cargando = true;
    this.endpointsService
      .obtenerAlumnos(
        this.paginaActual,
        this.tamanoPagina,
        this.nombreFiltro,
        this.mostrarInactivos
      )
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (response) => {
          const idsAlumnosEnGrupo = this.alumnosEnGrupo.map(
            (alumno) => alumno.id
          );
          this.alumnos = response.content.filter(
            (alumno: { id: number }) => !idsAlumnosEnGrupo.includes(alumno.id)
          );
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
    if (this.alumnosSeleccionados.length > 0) {
      this.endpointsService
        .agregarAlumnosAGrupo(this.grupoId, this.alumnosSeleccionados)
        .subscribe({
          next: () => {
            showSuccessToast('Alumnos agregados al grupo');
            this.router.navigate(['/gestionarAlumnos', this.grupoId]);
          },
          error: () => {
            showErrorToast('No se pudieron agregar los alumnos');
          },
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
    this.cargarAlumnos();
  }

  filtrarPorNombre(): void {
    this.searchSubject.next(this.nombreFiltro);
  }

  volver() {
    this.location.back();
  }
}
