import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { catchError, finalize, switchMap, takeUntil, tap } from 'rxjs/operators';
import { PermisosSesionService, SedeVisible } from '../../servicios/authentication/permisos-sesion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { GrupoProfesoresService } from '../../servicios/endpoints/grupo-profesores.service';
import { AltaDeProfesor, MiembrosService } from '../../servicios/endpoints/miembros.service';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { ProfesorDeGrupo } from '../../interfaces/grupo-profesor';
import { RolAsignacionProfesor } from '../../interfaces/profesor';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { contextoErrorLocal, LecturasPanelService } from '../carga/lecturas-panel.service';

const MODOS_ALTA = { normal: 'normal', sinCuenta: 'sinCuenta', desactivado: 'desactivado' } as const;
type ModoAlta = typeof MODOS_ALTA[keyof typeof MODOS_ALTA];

@Component({
  selector: 'pn-editar-grupo',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, RouterLink, AcentoEscuelaDirective, BotonOcupadoComponent, BloqueErrorComponent, EsqueletoComponent],
  templateUrl: './editar-grupo.component.html',
  styleUrl: './editar-grupo.component.scss',
})
export class EditarGrupoComponent implements OnInit {
  private readonly endpoints = inject(EndpointsService);
  private readonly profesoresService = inject(GrupoProfesoresService);
  private readonly miembrosService = inject(MiembrosService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly cambioGrupo$ = new Subject<void>();
  private readonly recargaGrupo$ = new Subject<void>();
  private readonly recargaProfesores$ = new Subject<void>();
  readonly nombre = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] });
  /** Solo con varias sedes activas (E9-1); con una, el PUT es el de hoy. */
  pedirSede = false;
  private todasLasSedes: SedeVisible[] = [];
  readonly sede = new FormControl<number | null>(null);
  readonly roles: readonly RolAsignacionProfesor[] = ['TITULAR', 'AYUDANTE'];
  grupoId = 0;
  grupo: GrupoDTO | null = null;
  cargando = true;
  errorCarga = '';
  guardando = false;
  errorGuardar = '';
  profesores: ProfesorDeGrupo[] = [];
  profesoresCargando = true;
  profesoresError = '';
  /** Capacidad ASIGNAR_PROFESORES: la gestión; el responsable no ve el bloque ni lo pide (decisión 372). */
  puedeAsignarProfesores = false;
  gruposDeLaEscuela: GrupoResumen[] = [];
  gruposMarcados = new Set<number>();
  cambiandoRol = new Set<number>();
  reenviando = new Set<number>();
  quitando = new Set<number>();
  menuAbierto: number | null = null;
  nuevoEmail = '';
  nuevoRol: RolAsignacionProfesor = 'TITULAR';
  nuevoNombre = '';
  nuevosApellidos = '';
  modoAlta: ModoAlta = 'normal';
  enviandoAlta = false;
  errorAlta = '';

  ngOnInit(): void {
    this.recargaGrupo$.pipe(
      tap(() => { this.cargando = true; this.errorCarga = ''; }),
      switchMap(() => this.endpoints.obtenerGrupoPorId(this.grupoId, contextoErrorLocal()).pipe(
        catchError((error: unknown) => { this.errorCarga = mensajeDeError(error) ?? 'No se pudo cargar el grupo'; return of(null); }),
      )), takeUntilDestroyed(this.destroyRef),
    ).subscribe((grupo) => {
      this.cargando = false;
      this.grupo = grupo;
      if (grupo) { this.nombre.setValue(grupo.nombre); this.sede.setValue(grupo.sedeId ?? null); }
    });
    this.permisos.obtener().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (p) => {
        this.todasLasSedes = p?.sedes ?? [];
        this.pedirSede = !!p?.variasSedes && this.todasLasSedes.filter((s) => s.activa !== false).length > 1;
        this.sede.setValidators(this.pedirSede ? Validators.required : null);
        this.sede.updateValueAndValidity({ emitEvent: false });
        const yaPodia = this.puedeAsignarProfesores;
        this.puedeAsignarProfesores = !!p?.capacidades?.includes('ASIGNAR_PROFESORES');
        if (this.grupoId && !yaPodia) {
          this.cargarProfesores();
        }
      },
      // Sin permisos no se pide: el servidor conserva la sede, como hoy.
      error: () => (this.pedirSede = false),
    });
    this.recargaProfesores$.pipe(
      tap(() => { this.profesoresCargando = this.profesores.length === 0; this.profesoresError = ''; }),
      switchMap(() => this.profesoresService.listar(this.grupoId).pipe(
        catchError((error: unknown) => {
          this.profesoresError = mensajeDeError(error) ?? 'No se han podido cargar los profesores. Inténtalo de nuevo.';
          return of(null);
        }),
      )), takeUntilDestroyed(this.destroyRef),
    ).subscribe((lista) => { this.profesoresCargando = false; if (lista) this.profesores = lista; });
    this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.cambioGrupo$.next();
      this.grupoId = +params['id'];
      this.grupo = null;
      this.profesores = [];
      this.nombre.reset('');
      this.sede.reset(null);
      this.errorGuardar = '';
      this.errorAlta = '';
      this.menuAbierto = null;
      this.cambiandoRol.clear();
      this.reenviando.clear();
      this.quitando.clear();
      this.nuevoEmail = '';
      this.nuevoRol = 'TITULAR';
      this.emailCambiado();
      this.gruposMarcados.clear();
      this.cargarGrupo();
      this.cargarProfesores();
    });
    this.lecturas.resumenGrupos().pipe(catchError(() => of([] as GrupoResumen[])), takeUntilDestroyed(this.destroyRef))
      .subscribe((grupos) => { this.gruposDeLaEscuela = grupos ?? []; });
  }

  cerrarMenu(evento: FocusEvent): void {
    if (!(evento.currentTarget as HTMLElement).contains(evento.relatedTarget as Node | null)) this.menuAbierto = null;
  }
  cargarGrupo(): void { this.recargaGrupo$.next(); }
  cargarProfesores(): void { if (this.puedeAsignarProfesores) this.recargaProfesores$.next(); }
  get resumen(): string {
    const resumen = this.gruposDeLaEscuela.find((g) => g.id === this.grupoId);
    const alumnos = resumen?.numAlumnos ?? this.grupo?.alumnos?.length;
    const turnos = resumen?.turnos?.length;
    return [alumnos === undefined ? '' : `${alumnos} ${alumnos === 1 ? 'alumno' : 'alumnos'}`,
      turnos === undefined ? '' : `${turnos} ${turnos === 1 ? 'turno' : 'turnos'}`].filter(Boolean).join(' · ');
  }
  get errorNombre(): string {
    if (!this.nombre.touched) return '';
    if (this.nombre.hasError('required')) return 'El nombre del grupo es obligatorio';
    return this.nombre.hasError('maxlength') ? 'Máximo 50 caracteres' : '';
  }
  /** Las sedes activas y, si la del grupo está inactiva, también esa: así no se pierde su valor. */
  get sedesElegibles(): SedeVisible[] {
    const actual = this.grupo?.sedeId;
    return this.todasLasSedes.filter((s) => s.activa !== false || s.id === actual);
  }
  get errorSede(): string {
    return this.pedirSede && this.sede.touched && this.sede.hasError('required') ? 'Elige la sede del grupo.' : '';
  }
  guardar(): void {
    this.nombre.markAsTouched();
    this.sede.markAsTouched();
    if (this.nombre.invalid || (this.pedirSede && this.sede.invalid) || this.guardando || !this.grupo) return;
    const nombre = this.nombre.value;
    // El PUT de hoy: nombre y rango (que no se modifica); con varias sedes, además la sede. El servidor conserva lo que no llega.
    const cuerpo: { nombre: string; rangoEdadMin: number | null; rangoEdadMax: number | null; sedeId?: number } =
      { nombre, rangoEdadMin: this.grupo.rangoEdadMin ?? null, rangoEdadMax: this.grupo.rangoEdadMax ?? null };
    const sedeId = this.pedirSede ? this.sede.value : null;
    if (sedeId !== null) cuerpo.sedeId = sedeId;
    this.guardando = true;
    this.errorGuardar = '';
    this.endpoints.actualizarGrupo(this.grupoId, cuerpo as GrupoDTO, contextoErrorLocal())
      .pipe(finalize(() => { this.guardando = false; }), takeUntil(this.cambioGrupo$), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { if (this.grupo) { this.grupo.nombre = nombre; if (sedeId !== null) this.grupo.sedeId = sedeId; } this.avisos.exito('El grupo se ha actualizado correctamente'); },
        error: (error: unknown) => { this.errorGuardar = mensajeDeError(error) ?? 'Error al actualizar el grupo'; },
      });
  }
  nombreCompleto(profesor: ProfesorDeGrupo): string { return `${profesor.nombre} ${profesor.apellidos}`.trim(); }
  iniciales(profesor: ProfesorDeGrupo): string { return `${profesor.nombre.charAt(0)}${profesor.apellidos.charAt(0)}`.toUpperCase(); }
  cambiarRol(profesor: ProfesorDeGrupo, rol: RolAsignacionProfesor): void {
    if (this.cambiandoRol.has(profesor.usuarioId) || this.quitando.has(profesor.usuarioId) || rol === profesor.rol) return;
    this.cambiandoRol.add(profesor.usuarioId);
    this.errorAlta = '';
    this.profesoresService.asignar(this.grupoId, profesor.email, rol)
      .pipe(finalize(() => this.cambiandoRol.delete(profesor.usuarioId)), takeUntil(this.cambioGrupo$), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.cargarProfesores(),
        error: (error: unknown) => { this.errorAlta = mensajeDeError(error) ?? 'No se ha podido cambiar el rol. Inténtalo de nuevo.'; },
      });
  }
  reenviarInvitacion(profesor: ProfesorDeGrupo): void {
    if (this.reenviando.has(profesor.usuarioId)) return;
    this.reenviando.add(profesor.usuarioId);
    this.errorAlta = '';
    this.miembrosService.reenviarInvitacion(profesor.usuarioId)
      .pipe(finalize(() => this.reenviando.delete(profesor.usuarioId)), takeUntil(this.cambioGrupo$), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.avisos.exito('Invitación reenviada'),
        error: (error: unknown) => { this.errorAlta = mensajeDeError(error) ?? 'No se ha podido reenviar la invitación. Inténtalo de nuevo.'; },
      });
  }
  async quitarProfesor(profesor: ProfesorDeGrupo): Promise<void> {
    if (this.quitando.has(profesor.usuarioId) || this.cambiandoRol.has(profesor.usuarioId)) return;
    const grupoId = this.grupoId;
    this.menuAbierto = null;
    this.quitando.add(profesor.usuarioId);
    const resultado = await this.confirmacion.confirmar({
      titulo: `¿Quitar a ${this.nombreCompleto(profesor)} de este grupo?`,
      mensaje: 'Seguirá teniendo el rol de profesor en la escuela.', accion: 'Quitar', destructivo: false,
    });
    if (this.destroyRef.destroyed || grupoId !== this.grupoId) return;
    if (!resultado.confirmado) { this.quitando.delete(profesor.usuarioId); return; }
    this.errorAlta = '';
    this.profesoresService.quitar(this.grupoId, profesor.usuarioId)
      .pipe(finalize(() => this.quitando.delete(profesor.usuarioId)), takeUntil(this.cambioGrupo$), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.cargarProfesores(); this.avisos.exito('Profesor quitado del grupo'); },
        error: (error: unknown) => { this.errorAlta = mensajeDeError(error) ?? 'No se ha podido quitar al profesor. Inténtalo de nuevo.'; },
      });
  }
  get otrosGrupos(): GrupoResumen[] { return this.gruposDeLaEscuela.filter((g) => g.id !== this.grupoId); }
  alternarGrupo(id: number): void {
    if (this.enviandoAlta) return;
    if (this.gruposMarcados.has(id)) this.gruposMarcados.delete(id); else this.gruposMarcados.add(id);
  }
  private marcadosVigentes(): number[] { return this.otrosGrupos.filter((g) => this.gruposMarcados.has(g.id)).map((g) => g.id); }
  get textoBotonAlta(): string {
    if (this.modoAlta === 'sinCuenta') return 'Enviar invitación';
    if (this.modoAlta === 'desactivado') return 'Reactivar y añadir';
    const n = this.marcadosVigentes().length;
    return n ? `Añadir a ${n + 1} grupos` : 'Añadir';
  }
  get puedeEnviarAlta(): boolean {
    return !this.enviandoAlta && !!this.nuevoEmail.trim() && (this.modoAlta !== 'sinCuenta' ||
      (!!this.nuevoNombre.trim() && !!this.nuevosApellidos.trim() && this.nuevoNombre.length <= 50 && this.nuevosApellidos.length <= 50));
  }
  emailCambiado(): void { this.modoAlta = 'normal'; this.nuevoNombre = ''; this.nuevosApellidos = ''; this.errorAlta = ''; }
  anadirProfesor(): void {
    if (!this.puedeEnviarAlta) return;
    const modo = this.modoAlta;
    const cuerpo: AltaDeProfesor = { email: this.nuevoEmail.trim(), rol: 'PROFESOR',
      grupos: [this.grupoId, ...this.marcadosVigentes()].map((grupoId) => ({ grupoId, rol: this.nuevoRol })) };
    if (modo === 'sinCuenta') { cuerpo.nombre = this.nuevoNombre.trim(); cuerpo.apellidos = this.nuevosApellidos.trim(); }
    else if (modo === 'desactivado') cuerpo.reactivar = true;
    this.enviandoAlta = true;
    this.errorAlta = '';
    this.miembrosService.alta(cuerpo).pipe(finalize(() => { this.enviandoAlta = false; }), takeUntil(this.cambioGrupo$), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.nuevoEmail = ''; this.nuevoRol = 'TITULAR'; this.emailCambiado(); this.gruposMarcados.clear(); this.cargarProfesores();
        this.avisos.exito(modo === 'sinCuenta' ? 'Invitación enviada' : 'Profesor añadido');
      },
      error: (error: unknown) => {
        const mensaje = mensajeDeError(error);
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        if (modo === 'normal' && status === 404 && mensaje?.startsWith('No hay ninguna cuenta con ese email')) this.modoAlta = 'sinCuenta';
        else if (modo !== 'sinCuenta' && status === 409 && mensaje?.includes('desactivado en esta escuela')) this.modoAlta = 'desactivado';
        else this.errorAlta = mensaje ?? 'No se ha podido añadir al profesor. Inténtalo de nuevo.';
      },
    });
  }
}
