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
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import Swal from 'sweetalert2';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { GrupoProfesoresService } from '../../../servicios/endpoints/grupo-profesores.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { ProfesorDeGrupo } from '../../../interfaces/grupo-profesor';
import { RolAsignacionProfesor } from '../../../interfaces/profesor';
import { GrupoResumen } from '../../../interfaces/grupo-resumen';
import { AltaDeProfesor, MiembrosService } from '../../../servicios/endpoints/miembros.service';

/** Estado del alta: normal, email sin cuenta (se invita con nombre y apellidos) o rol de profesor desactivado. */
type ModoAlta = 'normal' | 'sinCuenta' | 'desactivado';

const SIN_CUENTA = 'No hay ninguna cuenta con ese email';
const ROL_DESACTIVADO = 'desactivado en esta escuela';

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
  // Alta con POST /api/miembros (D8-12): este grupo y los marcados en «También en estos grupos», con el mismo rol.
  private readonly miembrosService = inject(MiembrosService);
  private gruposDeLaEscuela: GrupoResumen[] = [];
  gruposMarcados = new Set<number>();
  modoAlta: ModoAlta = 'normal';
  nuevoNombre = '';
  nuevosApellidos = '';

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
    this.cargarGruposDeLaEscuela();
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

  /** Los demás grupos de la escuela para «También en estos grupos». Si la lista falla, la fila no sale. */
  private cargarGruposDeLaEscuela(): void {
    if (!this.puedeGestionarProfesores) {
      return;
    }
    this.endpointsService
      .obtenerResumenGrupos()
      .pipe(
        catchError(() => of([] as GrupoResumen[])),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((grupos) => (this.gruposDeLaEscuela = grupos ?? []));
  }

  get otrosGrupos(): GrupoResumen[] {
    return this.gruposDeLaEscuela.filter((g) => g.id !== this.grupoId);
  }

  estaMarcado(grupoId: number): boolean {
    return this.gruposMarcados.has(grupoId);
  }

  alternarGrupo(grupoId: number): void {
    if (this.enviandoAlta) {
      return;
    }
    if (this.gruposMarcados.has(grupoId)) {
      this.gruposMarcados.delete(grupoId);
    } else {
      this.gruposMarcados.add(grupoId);
    }
  }

  /** Grupos marcados que siguen en la lista (este grupo nunca cuenta dos veces). */
  private marcadosVigentes(): number[] {
    return this.otrosGrupos.filter((g) => this.gruposMarcados.has(g.id)).map((g) => g.id);
  }

  get textoBotonAlta(): string {
    if (this.modoAlta === 'sinCuenta') {
      return 'Enviar invitación';
    }
    if (this.modoAlta === 'desactivado') {
      return 'Reactivar y añadir';
    }
    const marcados = this.marcadosVigentes().length;
    return marcados === 0 ? 'Añadir' : `Añadir a ${marcados + 1} grupos`;
  }

  get puedeEnviarAlta(): boolean {
    if (this.enviandoAlta || !this.nuevoEmail.trim()) {
      return false;
    }
    return this.modoAlta !== 'sinCuenta' || (!!this.nuevoNombre.trim() && !!this.nuevosApellidos.trim());
  }

  /** Otro email es otra cuenta: el aviso de «sin cuenta» o de rol desactivado ya no vale. */
  emailCambiado(): void {
    if (this.modoAlta !== 'normal') {
      this.modoAlta = 'normal';
      this.nuevoNombre = '';
      this.nuevosApellidos = '';
    }
  }

  anadirProfesor(): void {
    const email = this.nuevoEmail.trim();
    if (!this.puedeEnviarAlta) {
      return;
    }
    const modo = this.modoAlta;
    const cuerpo: AltaDeProfesor = {
      email,
      rol: 'PROFESOR',
      grupos: [this.grupoId, ...this.marcadosVigentes()].map((grupoId) => ({ grupoId, rol: this.nuevoRol })),
    };
    if (modo === 'sinCuenta') {
      cuerpo.nombre = this.nuevoNombre.trim();
      cuerpo.apellidos = this.nuevosApellidos.trim();
    } else if (modo === 'desactivado') {
      cuerpo.reactivar = true;
    }
    this.enviandoAlta = true;
    this.errorAlta = null;
    this.miembrosService
      .alta(cuerpo)
      .pipe(finalize(() => (this.enviandoAlta = false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.vaciarAlta();
          this.cargarProfesores();
          showSuccessToast(modo === 'sinCuenta' ? 'Invitación enviada' : 'Profesor añadido');
        },
        error: (error) => {
          const mensaje = mensajeDeError(error);
          const status = error instanceof HttpErrorResponse ? error.status : 0;
          if (modo === 'normal' && status === 404 && mensaje?.startsWith(SIN_CUENTA)) {
            this.modoAlta = 'sinCuenta';
          } else if (modo !== 'sinCuenta' && status === 409 && mensaje?.includes(ROL_DESACTIVADO)) {
            this.modoAlta = 'desactivado';
          } else {
            this.errorAlta = mensaje ?? 'No se ha podido añadir al profesor. Inténtalo de nuevo.';
          }
        },
      });
  }

  private vaciarAlta(): void {
    this.nuevoEmail = '';
    this.nuevoRol = 'TITULAR';
    this.nuevoNombre = '';
    this.nuevosApellidos = '';
    this.gruposMarcados.clear();
    this.modoAlta = 'normal';
  }

  reenviarInvitacion(profesor: ProfesorDeGrupo): void {
    if (this.usuarioEnCurso !== null) {
      return;
    }
    this.usuarioEnCurso = profesor.usuarioId;
    this.errorAlta = null;
    this.miembrosService
      .reenviarInvitacion(profesor.usuarioId)
      .pipe(finalize(() => (this.usuarioEnCurso = null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => showSuccessToast('Invitación reenviada'),
        error: (error) => {
          this.errorAlta = mensajeDeError(error) ?? 'No se ha podido reenviar la invitación. Inténtalo de nuevo.';
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
