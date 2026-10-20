import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormGroup,
  FormBuilder,
  Validators,
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { GrupoDTO } from '../../../interfaces/grupo-dto';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { CommonModule, Location } from '@angular/common';
import { Subject, of } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import Swal from 'sweetalert2';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { GrupoProfesoresService } from '../../../servicios/endpoints/grupo-profesores.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { ProfesorDeGrupo } from '../../../interfaces/grupo-profesor';
import { RolAsignacionProfesor } from '../../../interfaces/profesor';

@Component({
  selector: 'app-editar-grupo',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CommonModule],
  templateUrl: './editar-grupo.component.html',
  styleUrl: './editar-grupo.component.scss',
})
export class EditarGrupoComponent implements OnInit {
  grupoForm: FormGroup;
  grupoId!: number;
  cargando: boolean = true;

  // Profesores del grupo: petición propia, no bloquea el formulario del grupo.
  private readonly profesoresService = inject(GrupoProfesoresService);
  private readonly auth = inject(AuthenticationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargaProfesores$ = new Subject<void>();
  readonly puedeGestionarProfesores = this.auth.tieneAccesoAdmin();
  profesores: ProfesorDeGrupo[] = [];
  profesoresCargando = false;
  profesoresError: string | null = null;
  nuevoEmail = '';
  nuevoRol: RolAsignacionProfesor = 'TITULAR';
  enviandoAlta = false;
  errorAlta: string | null = null;
  usuarioEnCurso: number | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location
  ) {
    this.grupoForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(50)]],
      rangoEdadMin: [null],
      rangoEdadMax: [null],
    });
  }

  ngOnInit(): void {
    // Una sola carga viva: si llega otra petición, la anterior se cancela y su respuesta no pisa la nueva.
    this.recargaProfesores$
      .pipe(
        tap(() => {
          this.profesoresCargando = true;
          this.profesoresError = null;
        }),
        switchMap(() =>
          this.profesoresService.listar(this.grupoId).pipe(
            tap(() => (this.profesoresCargando = false)),
            catchError((error) => {
              this.profesoresCargando = false;
              this.profesoresError = mensajeDeError(error) ?? 'No se han podido cargar los profesores. Inténtalo de nuevo.';
              return of(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((lista) => {
        if (lista) {
          this.profesores = lista;
        }
      });
    this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.grupoId = +params['id'];
      this.cargarGrupo();
      this.cargarProfesores();
    });
  }

  cargarGrupo(): void {
    this.cargando = true;
    this.endpointsService
      .obtenerGrupoPorId(this.grupoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (grupo: GrupoDTO) => {
          this.grupoForm.patchValue({
            nombre: grupo.nombre,
            rangoEdadMin: grupo.rangoEdadMin ?? null,
            rangoEdadMax: grupo.rangoEdadMax ?? null,
          });
        },
        error: () => {
          this.cargando = false;
          showErrorToast('No se pudo cargar el grupo');
        }
      });
  }

  actualizarGrupo(): void {
    if (this.grupoForm.valid) {
      this.endpointsService
        .actualizarGrupo(this.grupoId, this.grupoForm.value)
        .subscribe({
          next: (response) => {
            showSuccessToast('El grupo se ha actualizado correctamente');
            this.router.navigate(['/gruposListar']);
          },
          error: (error) => {
            showErrorToast('Error al actualizar el grupo');
          },
          complete: () => {},
        });
    }
  }

  cargarProfesores(): void {
    if (this.puedeGestionarProfesores) {
      this.recargaProfesores$.next();
    }
  }

  anadirProfesor(): void {
    const email = this.nuevoEmail.trim();
    if (!email || this.enviandoAlta) {
      return;
    }
    this.enviandoAlta = true;
    this.errorAlta = null;
    this.profesoresService
      .asignar(this.grupoId, email, this.nuevoRol)
      .pipe(finalize(() => (this.enviandoAlta = false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.nuevoEmail = '';
          this.cargarProfesores();
        },
        error: (error) => {
          this.errorAlta = mensajeDeError(error) ?? 'No se ha podido añadir al profesor. Inténtalo de nuevo.';
        },
      });
  }

  cambiarRol(profesor: ProfesorDeGrupo, selector: HTMLSelectElement): void {
    const rol = selector.value;
    if (this.usuarioEnCurso !== null || rol === profesor.rol || (rol !== 'TITULAR' && rol !== 'AYUDANTE')) {
      return;
    }
    this.usuarioEnCurso = profesor.usuarioId;
    this.errorAlta = null;
    this.profesoresService
      .asignar(this.grupoId, profesor.email, rol)
      .pipe(finalize(() => (this.usuarioEnCurso = null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.cargarProfesores(),
        error: (error) => {
          selector.value = profesor.rol; // el binding no cambia: se devuelve el select al rol real
          this.errorAlta = mensajeDeError(error) ?? 'No se ha podido cambiar el rol. Inténtalo de nuevo.';
          this.cargarProfesores();
        },
      });
  }

  async quitarProfesor(profesor: ProfesorDeGrupo): Promise<void> {
    if (this.usuarioEnCurso !== null) {
      return;
    }
    const decision = await Swal.fire({
      title: `¿Quitar a ${profesor.nombre} de este grupo?`,
      text: 'Seguirá teniendo el rol de profesor en la escuela.',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Quitar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#dc3545',
    });
    if (!decision.isConfirmed) {
      return;
    }
    this.usuarioEnCurso = profesor.usuarioId;
    this.errorAlta = null;
    this.profesoresService
      .quitar(this.grupoId, profesor.usuarioId)
      .pipe(finalize(() => (this.usuarioEnCurso = null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.cargarProfesores(),
        error: (error) => {
          this.errorAlta = mensajeDeError(error) ?? 'No se ha podido quitar al profesor. Inténtalo de nuevo.';
        },
      });
  }

  volver() {
    this.location.back();
  }
}
