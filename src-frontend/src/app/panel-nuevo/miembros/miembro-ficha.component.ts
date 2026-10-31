import { ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { Observable, Subscription } from 'rxjs';

import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { RolAsignacionProfesor } from '../../interfaces/profesor';
import { SedeVisible } from '../../servicios/authentication/permisos-sesion.service';
import {
  FichaCompleta,
  GrupoDeMiembro,
  InvitacionCancelada,
  Miembro,
  MiembrosService,
  RolMembresia,
} from '../../servicios/endpoints/miembros.service';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import {
  AVISO_CONTRASENA_GUARDADA,
  AYUDA_FAMILIA,
  AYUDA_SOLO_PROPIETARIO,
  CARGO_MAXIMO,
  FormContrasena,
  MENSAJE_CONTRASENAS_DISTINTAS,
  MENSAJE_CONTRASENA_DEBIL,
  MENSAJE_SIN_SEDES,
  REGLAS_CONTRASENA,
  ROLES_ASIGNACION,
  ROLES_PERSONAL,
  TEXTO_CANCELAR_INVITACION,
  TEXTO_CONTRASENA_GOOGLE,
  TEXTO_CONTRASENA_OTRA_ESCUELA,
  TEXTO_QUITAR,
  TEXTO_QUITAR_FAMILIA,
  asignacionesDe,
  contrasenaCumpleReglas,
  estadoHttp,
  formContrasenaVacio,
  etiquetaAsignacion,
  etiquetaGrupo,
  etiquetaRol,
  invitacionPendienteAqui,
  mensajeError,
  mensajeErrorContrasena,
  mismasSedes,
  mismosGrupos,
  nombreCompleto,
  puedeCambiarRol,
  puedeCancelarInvitacion,
  rolDe,
  tieneRolActivo,
  tituloCancelarInvitacion,
  tituloQuitar,
} from './miembros-vista';

/** Lo que la pantalla sabe de la sesión y pasa a la ficha: quién es, y las sedes que ve. */
export interface ContextoMiembros {
  /** La sesión es propietaria (capacidad DAR_GESTION): solo ella da o quita Propietario y Gestor. */
  propietario: boolean;
  sedes: readonly SedeVisible[];
  variasSedes: boolean;
}

type Estado = 'cargando' | 'listo' | 'error';
type Seccion = 'sedes' | 'grupos' | RolMembresia;

/**
 * Ficha de un miembro en el panel lateral (E9, pieza 9-4). Cuatro secciones que guardan cada una por su cuenta y
 * enseñan su propio error: Datos (email, invitación), Roles (casilla y cargo por rol), Sedes (solo con Responsable de
 * sede activo) y Grupos (solo con Profesor activo; `PUT /grupos` reemplaza el conjunto). Cada respuesta es la ficha
 * completa y la refresca. Solo el propietario ve «Restablecer contraseña» (en Datos, plegable ahí mismo) y Familia como
 * casilla (decisión 396); el gestor ve Familia en texto.
 */
@Component({
  selector: 'pn-miembro-ficha',
  standalone: true,
  imports: [PanelLateralComponent, BotonOcupadoComponent, BloqueErrorComponent, EsqueletoComponent],
  templateUrl: './miembro-ficha.component.html',
  styleUrls: ['./miembro-panel.scss'],
})
export class MiembroFichaComponent implements OnInit, OnDestroy {
  private readonly api = inject(MiembrosService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly suscripciones = new Subscription();
  private destruido = false;

  /** La fila pulsada: da el título mientras llega la ficha. */
  @Input({ required: true }) fila!: Miembro;
  @Input({ required: true }) contexto!: ContextoMiembros;
  @Output() readonly cerrar = new EventEmitter<void>();
  /** La ficha que devolvió la API tras un cambio: la lista se pone al día. */
  @Output() readonly cambiada = new EventEmitter<FichaCompleta>();
  /** Llamada cuando una escritura termina con el panel ya cerrado (los `@Output` ya no llegan): la lista se refresca. */
  @Input() alTerminarCerrado: (() => void) | null = null;

  readonly rolesPersonal = ROLES_PERSONAL;
  readonly rolesAsignacion = ROLES_ASIGNACION;
  readonly cargoMaximo = CARGO_MAXIMO;
  readonly ayudaSoloPropietario = AYUDA_SOLO_PROPIETARIO;
  readonly ayudaFamilia = AYUDA_FAMILIA;
  readonly reglas = REGLAS_CONTRASENA;
  readonly textoContrasenaGoogle = TEXTO_CONTRASENA_GOOGLE;
  readonly textoContrasenaOtraEscuela = TEXTO_CONTRASENA_OTRA_ESCUELA;
  readonly etiquetaRol = etiquetaRol;
  readonly etiquetaAsignacion = etiquetaAsignacion;

  estado: Estado = 'cargando';
  ficha: FichaCompleta | null = null;

  rolOcupado: Partial<Record<RolMembresia, boolean>> = {};
  rolError: Partial<Record<RolMembresia, string>> = {};
  cargos: Partial<Record<RolMembresia, string>> = {};
  /** Responsable de sede marcado sin sedes guardadas: se activa con las sedes que se elijan. */
  responsablePendiente = false;

  datosError: string | null = null;
  /** El bloque «Restablecer contraseña»: `null` mientras está plegado. Sus valores no salen de este componente. */
  contrasena: FormContrasena | null = null;
  reenviando = false;
  /** Diálogo de cancelar abierto: no se abre otro. */
  private confirmandoCancelar = false;
  /** La cancelación ya confirmada, en vuelo. */
  cancelando = false;

  sedesElegidas: number[] = [];
  sedesOcupado = false;
  sedesError: string | null = null;

  gruposElegidos: GrupoDeMiembro[] = [];
  gruposOcupado = false;
  gruposError: string | null = null;
  catalogo: GrupoResumen[] | null = null;
  catalogoError = false;
  grupoNuevo = '';
  rolNuevo: RolAsignacionProfesor = 'TITULAR';

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.vaciarContrasena();
    this.suscripciones.unsubscribe();
  }

  get nombre(): string {
    return nombreCompleto(this.ficha ?? this.fila);
  }

  get ocupado(): boolean {
    return (
      this.reenviando ||
      this.cancelando ||
      !!this.contrasena?.enviando ||
      this.sedesOcupado ||
      this.gruposOcupado ||
      Object.values(this.rolOcupado).some((v) => v === true)
    );
  }

  cargar(): void {
    this.estado = 'cargando';
    this.suscripciones.add(
      this.api.ficha(this.fila.usuarioId).subscribe({
        next: (ficha) => {
          this.reponer(ficha, true);
          this.estado = 'listo';
          this.cdr.detectChanges();
        },
        error: () => {
          this.estado = 'error';
          this.cdr.detectChanges();
        },
      })
    );
  }

  // ------------------------------------------------------------------ estado derivado

  activo(rol: RolMembresia): boolean {
    return !!this.ficha && tieneRolActivo(this.ficha, rol);
  }

  /** La casilla: el rol activo, o Responsable de sede a la espera de sus sedes. */
  marcado(rol: RolMembresia): boolean {
    return this.activo(rol) || (rol === 'RESPONSABLE_SEDE' && this.responsablePendiente);
  }

  /** Familia solo la cambia el propietario (decisión 396); `puedeCambiarRol` sigue siendo la regla del alta. */
  puedeCambiar(rol: RolMembresia): boolean {
    return rol === 'FAMILIA' ? this.contexto.propietario : puedeCambiarRol(rol, this.contexto.propietario);
  }

  /** Lo que se enseña bajo el email: solo para el propietario; la invitación pendiente no enseña nada (valen Reenviar y Cancelar). */
  get estadoContrasena(): 'RESTABLECIBLE' | 'GOOGLE' | 'OTRA_ESCUELA' | null {
    const estado = this.ficha?.contrasena;
    if (!this.contexto.propietario || !estado || estado === 'INVITACION_PENDIENTE') {
      return null;
    }
    return estado;
  }

  get verInvitacion(): boolean {
    return !!this.ficha && invitacionPendienteAqui(this.ficha);
  }

  get verCancelarInvitacion(): boolean {
    return !!this.ficha && puedeCancelarInvitacion(this.ficha, this.contexto.propietario);
  }

  /** Para el gestor: la fila de texto de Familia solo si la cuenta tiene esa membresía. */
  get tieneFamilia(): boolean {
    return !!this.ficha && rolDe(this.ficha, 'FAMILIA') !== null;
  }

  get verSedes(): boolean {
    return this.activo('RESPONSABLE_SEDE') || this.responsablePendiente;
  }

  get verGrupos(): boolean {
    return this.activo('PROFESOR');
  }

  cargoGuardado(rol: RolMembresia): string {
    return (this.ficha ? rolDe(this.ficha, rol)?.cargo : null) ?? '';
  }

  cargoCambiado(rol: RolMembresia): boolean {
    return (this.cargos[rol] ?? '').trim() !== this.cargoGuardado(rol).trim();
  }

  /** Las sedes que se pueden marcar: las activas y, para no perderlas sin querer, las guardadas aunque estén inactivas. */
  get sedesMarcables(): { id: number; nombre: string; inactiva: boolean }[] {
    const lista = this.contexto.sedes.filter((s) => s.activa).map((s) => ({ id: s.id, nombre: s.nombre, inactiva: false }));
    // Sin datos de sedes (los permisos fallaron o tardan) no se sabe que esté inactiva: sin etiqueta.
    const conocidas = this.contexto.sedes.length > 0;
    for (const sede of this.ficha?.sedes ?? []) {
      if (!lista.some((s) => s.id === sede.id)) {
        lista.push({ id: sede.id, nombre: sede.nombre, inactiva: conocidas });
      }
    }
    return lista;
  }

  get sedesCambiadas(): boolean {
    return this.responsablePendiente || !mismasSedes(this.sedesElegidas, (this.ficha?.sedes ?? []).map((s) => s.id));
  }

  get gruposCambiados(): boolean {
    return !mismosGrupos(
      this.gruposElegidos.map((g) => ({ grupoId: g.grupoId, rol: g.rol })),
      this.ficha ? asignacionesDe(this.ficha) : []
    );
  }

  /** Los grupos del catálogo que aún no tiene. */
  get gruposDisponibles(): GrupoResumen[] {
    const tiene = new Set(this.gruposElegidos.map((g) => g.grupoId));
    return (this.catalogo ?? []).filter((g) => !tiene.has(g.id));
  }

  etiquetaDeGrupo(grupo: GrupoDeMiembro): string {
    const nombres = new Map(this.contexto.sedes.map((s) => [s.id, s.nombre] as [number, string]));
    return etiquetaGrupo(grupo, nombres, this.contexto.variasSedes);
  }

  // ------------------------------------------------------------------ refresco

  /**
   * Pone la ficha que devolvió la API y repone los borradores. Los de las secciones tocadas (`tocadas`) se reponen
   * siempre; los demás solo si no tienen cambios sin guardar. `todo` (primera carga) los repone todos.
   */
  private reponer(ficha: FichaCompleta, todo: boolean, tocadas: readonly Seccion[] = []): void {
    const sedesLimpias = todo || tocadas.includes('sedes') || !this.sedesCambiadas;
    const gruposLimpios = todo || tocadas.includes('grupos') || !this.gruposCambiados;
    for (const rol of ROLES_PERSONAL) {
      if (todo || tocadas.includes(rol) || !this.cargoCambiado(rol)) {
        this.cargos[rol] = (rolDe(ficha, rol)?.cargo ?? '') as string;
      }
    }
    this.ficha = ficha;
    if (this.responsablePendiente && tieneRolActivo(ficha, 'RESPONSABLE_SEDE')) {
      this.responsablePendiente = false;
    }
    if (sedesLimpias) {
      this.sedesElegidas = (ficha.sedes ?? []).map((s) => s.id);
    }
    if (gruposLimpios) {
      this.gruposElegidos = [...(ficha.grupos ?? [])];
    }
    if (tieneRolActivo(ficha, 'PROFESOR') && this.catalogo === null) {
      this.cargarCatalogo();
    }
  }

  private aplicar(ficha: FichaCompleta, tocadas: readonly Seccion[]): void {
    this.reponer(ficha, false, tocadas);
    this.cambiada.emit(ficha);
    this.cdr.detectChanges();
  }

  cargarCatalogo(): void {
    this.catalogoError = false;
    this.suscripciones.add(
      this.lecturas.resumenGrupos().subscribe({
        next: (grupos) => {
          this.catalogo = grupos ?? [];
          this.cdr.detectChanges();
        },
        error: () => {
          this.catalogoError = true;
          this.cdr.detectChanges();
        },
      })
    );
  }

  /** El éxito llegó con el panel cerrado: se avisa igualmente y la lista se refresca. */
  private terminarCerrado(aviso?: string): void {
    if (aviso) {
      this.avisos.exito(aviso);
    }
    this.alTerminarCerrado?.();
  }

  /** Lanza un cambio con su ocupado y su error; la respuesta refresca la ficha. */
  private escribir(
    peticion: Observable<FichaCompleta>,
    tocadas: readonly Seccion[],
    ocupado: (valor: boolean) => void,
    fallo: (mensaje: string) => void,
    errorPorDefecto: string,
    aviso?: string
  ): void {
    ocupado(true);
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    peticion.subscribe({
      next: (ficha) => {
        ocupado(false);
        if (this.destruido) {
          this.terminarCerrado(aviso);
          return;
        }
        this.aplicar(ficha, tocadas);
        if (aviso) {
          this.avisos.exito(aviso);
        }
      },
      error: (error) => {
        ocupado(false);
        if (this.destruido) {
          return;
        }
        fallo(mensajeError(error, errorPorDefecto));
        this.cdr.detectChanges();
      },
    });
  }

  // ------------------------------------------------------------------ datos

  reenviar(): void {
    if (!this.ficha || this.reenviando || this.cancelando) {
      return;
    }
    this.datosError = null;
    this.escribir(
      this.api.reenviarConFicha(this.ficha.usuarioId),
      [],
      (v) => (this.reenviando = v),
      (m) => (this.datosError = m),
      'No se pudo reenviar la invitación.',
      'Invitación reenviada.'
    );
  }

  /**
   * Cancela la invitación (decisión 389). La cuenta puede haberse borrado: al acabar bien no se pide ni se emite ninguna
   * ficha; se cierra el panel, se avisa y la lista se recarga.
   */
  async cancelarInvitacion(): Promise<void> {
    const ficha = this.ficha;
    if (!ficha || this.ocupado || this.confirmandoCancelar || !this.verCancelarInvitacion) {
      return;
    }
    this.datosError = null;
    this.confirmandoCancelar = true;
    const resultado = await this.confirmacion.confirmar<InvitacionCancelada>({
      titulo: tituloCancelarInvitacion(nombreCompleto(ficha)),
      mensaje: TEXTO_CANCELAR_INVITACION,
      accion: 'Cancelar invitación',
      accionOcupada: 'Cancelando…',
      cancelar: 'Volver',
      destructivo: true,
      nombre: 'cancelar-invitacion',
      ejecutar: () => {
        this.cancelando = true;
        this.cdr.detectChanges();
        return this.api.cancelarInvitacion(ficha.usuarioId);
      },
    });
    this.confirmandoCancelar = false;
    this.cancelando = false;
    if (!resultado.confirmado) {
      return;
    }
    if (resultado.error) {
      this.fallarCancelacion(resultado.error, ficha.usuarioId);
      return;
    }
    if (!this.destruido) {
      this.cerrar.emit();
    }
    this.terminarCerrado('Invitación cancelada.');
  }

  /**
   * El mensaje en Datos. Un 409 dice que la ficha quedó vieja: se relee sin esqueleto, para no perder el mensaje. Un 404
   * dice que la cuenta ya no existe: no hay ficha que releer; se cierra, se avisa y la lista se recarga.
   */
  private fallarCancelacion(error: unknown, usuarioId: number): void {
    const mensaje = mensajeError(error, 'No se pudo cancelar la invitación.');
    if (estadoHttp(error) === 404) {
      if (!this.destruido) {
        this.cerrar.emit();
      }
      this.avisos.error(mensaje);
      this.alTerminarCerrado?.();
      return;
    }
    if (this.destruido) {
      return;
    }
    this.datosError = mensaje;
    this.cdr.detectChanges();
    if (estadoHttp(error) !== 409) {
      return;
    }
    this.suscripciones.add(
      this.api.ficha(usuarioId).subscribe({
        next: (ficha) => this.aplicar(ficha, []),
        // La relectura es un extra: si falla, sigue el mensaje de la API.
        error: () => undefined,
      })
    );
  }

  // ------------------------------------------------------------------ restablecer contraseña

  abrirContrasena(): void {
    if (this.estadoContrasena !== 'RESTABLECIBLE' || this.contrasena || this.ocupado) {
      return;
    }
    this.contrasena = formContrasenaVacio();
    this.cdr.detectChanges();
    this.enfocar('#miembro-contrasena-nueva');
  }

  cancelarContrasena(): void {
    if (this.contrasena?.enviando) {
      return;
    }
    this.vaciarContrasena();
    this.cdr.detectChanges();
    this.enfocar('#miembro-restablecer');
  }

  escribirContrasena(campo: 'nueva' | 'confirmar', evento: Event): void {
    if (!this.contrasena) {
      return;
    }
    this.contrasena[campo] = (evento.target as HTMLInputElement).value;
    this.contrasena.error = null;
  }

  alternarMostrar(evento: Event): void {
    if (this.contrasena) {
      this.contrasena.mostrar = (evento.target as HTMLInputElement).checked;
    }
  }

  guardarContrasena(evento?: Event): void {
    evento?.preventDefault();
    const form = this.contrasena;
    const ficha = this.ficha;
    if (!form || !ficha || form.enviando || this.estadoContrasena !== 'RESTABLECIBLE') {
      return;
    }
    if (!contrasenaCumpleReglas(form.nueva)) {
      form.error = MENSAJE_CONTRASENA_DEBIL;
      this.cdr.detectChanges();
      this.enfocar('#miembro-contrasena-nueva');
      return;
    }
    if (form.nueva !== form.confirmar) {
      form.error = MENSAJE_CONTRASENAS_DISTINTAS;
      this.cdr.detectChanges();
      this.enfocar('#miembro-contrasena-nueva');
      return;
    }
    form.enviando = true;
    form.error = null;
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    this.api.restablecerContrasena(ficha.usuarioId, form.nueva).subscribe({
      next: (respuesta) => {
        if (this.destruido) {
          this.vaciarContrasena();
          this.terminarCerrado(AVISO_CONTRASENA_GUARDADA);
          return;
        }
        this.vaciarContrasena();
        this.aplicar(respuesta, []);
        this.avisos.exito(AVISO_CONTRASENA_GUARDADA);
        this.enfocar('#miembro-restablecer');
      },
      error: (error) => {
        form.enviando = false;
        if (this.destruido) {
          return;
        }
        // El error se queda dentro del bloque y lo escrito se conserva.
        form.error = mensajeErrorContrasena(error);
        this.cdr.detectChanges();
        this.enfocar('#miembro-contrasena-nueva');
      },
    });
  }

  /** Cierra la ficha vaciando antes los campos de contraseña. */
  cerrarFicha(): void {
    if (this.contrasena?.enviando) {
      return;
    }
    this.vaciarContrasena();
    this.cerrar.emit();
  }

  private vaciarContrasena(): void {
    if (this.contrasena) {
      this.contrasena.nueva = '';
      this.contrasena.confirmar = '';
      this.contrasena = null;
    }
  }

  private enfocar(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }

  // ------------------------------------------------------------------ roles

  alternarRol(rol: RolMembresia, evento: Event): void {
    const casilla = evento.target as HTMLInputElement;
    const quiere = casilla.checked;
    // La casilla no es la fuente de verdad: si no se llega a cambiar, vuelve a lo que dice la ficha.
    casilla.checked = this.marcado(rol);
    if (!this.ficha || this.rolOcupado[rol] || !this.puedeCambiar(rol)) {
      return;
    }
    this.rolError[rol] = undefined;
    if (quiere) {
      this.darRol(rol);
    } else if (rol === 'RESPONSABLE_SEDE' && this.responsablePendiente) {
      this.responsablePendiente = false;
      this.sedesError = null;
      this.sedesElegidas = [];
    } else {
      void this.quitarRol(rol);
    }
  }

  private darRol(rol: RolMembresia): void {
    const ficha = this.ficha!;
    if (rol === 'RESPONSABLE_SEDE' && (ficha.sedes ?? []).length === 0) {
      // Sin sedes guardadas la API exige indicarlas: se eligen antes de dar el rol.
      this.responsablePendiente = true;
      this.sedesElegidas = [];
      this.sedesError = null;
      return;
    }
    this.escribir(
      rol === 'FAMILIA' ? this.api.darFamilia(ficha.usuarioId) : this.api.darRol(ficha.usuarioId, rol),
      [rol],
      (v) => (this.rolOcupado[rol] = v),
      (m) => (this.rolError[rol] = m),
      `No se pudo dar el rol de ${etiquetaRol(rol).toLowerCase()}.`
    );
  }

  private async quitarRol(rol: RolMembresia): Promise<void> {
    const ficha = this.ficha!;
    const resultado = await this.confirmacion.confirmar<FichaCompleta>({
      titulo: tituloQuitar(rol, nombreCompleto(ficha)),
      mensaje: rol === 'FAMILIA' ? TEXTO_QUITAR_FAMILIA : TEXTO_QUITAR,
      accion: 'Quitar rol',
      accionOcupada: 'Quitando…',
      ...(rol === 'FAMILIA' ? { cancelar: 'Volver' } : {}),
      nombre: 'quitar-rol',
      ejecutar: () => this.api.quitarRol(ficha.usuarioId, rol),
    });
    if (!resultado.confirmado) {
      return;
    }
    if (this.destruido) {
      if (!resultado.error) {
        this.terminarCerrado();
      }
      return;
    }
    if (resultado.error) {
      // El 409 de último propietario (y el 403 de D8) se ven junto a la línea del rol.
      this.rolError[rol] = mensajeError(resultado.error, `No se pudo quitar el rol de ${etiquetaRol(rol).toLowerCase()}.`);
      this.cdr.detectChanges();
      return;
    }
    if (resultado.respuesta) {
      this.aplicar(resultado.respuesta, [rol]);
    }
  }

  escribirCargo(rol: RolMembresia, evento: Event): void {
    this.cargos[rol] = (evento.target as HTMLInputElement).value;
    this.rolError[rol] = undefined;
  }

  guardarCargo(rol: RolMembresia, evento?: Event): void {
    evento?.preventDefault();
    if (!this.ficha || this.rolOcupado[rol] || !this.cargoCambiado(rol)) {
      return;
    }
    const cargo = (this.cargos[rol] ?? '').trim();
    if ([...cargo].length > CARGO_MAXIMO) {
      this.rolError[rol] = `El cargo no puede pasar de ${CARGO_MAXIMO} caracteres.`;
      return;
    }
    this.rolError[rol] = undefined;
    // Un cargo en blanco lo borra (la API conserva el que no llega).
    this.escribir(
      this.api.darRol(this.ficha.usuarioId, rol, { cargo }),
      [rol],
      (v) => (this.rolOcupado[rol] = v),
      (m) => (this.rolError[rol] = m),
      'No se pudo guardar el cargo.',
      'Cargo guardado.'
    );
  }

  // ------------------------------------------------------------------ sedes

  alternarSede(id: number, marcada: boolean): void {
    this.sedesError = null;
    this.sedesElegidas = marcada ? [...this.sedesElegidas.filter((s) => s !== id), id] : this.sedesElegidas.filter((s) => s !== id);
  }

  guardarSedes(): void {
    const ficha = this.ficha;
    if (!ficha || this.sedesOcupado) {
      return;
    }
    if (this.sedesElegidas.length === 0) {
      this.sedesError = MENSAJE_SIN_SEDES;
      return;
    }
    this.sedesError = null;
    const aviso = this.responsablePendiente ? 'Rol dado.' : 'Sedes guardadas.';
    const peticion = this.responsablePendiente
      ? this.api.darRol(ficha.usuarioId, 'RESPONSABLE_SEDE', { sedeIds: [...this.sedesElegidas] })
      : this.api.reemplazarSedes(ficha.usuarioId, [...this.sedesElegidas]);
    this.escribir(
      peticion,
      ['sedes', 'RESPONSABLE_SEDE'],
      (v) => (this.sedesOcupado = v),
      (m) => (this.sedesError = m),
      'No se pudieron guardar las sedes.',
      aviso
    );
  }

  // ------------------------------------------------------------------ grupos

  cambiarRolDeGrupo(grupoId: number, rol: string): void {
    this.gruposError = null;
    this.gruposElegidos = this.gruposElegidos.map((g) => (g.grupoId === grupoId ? { ...g, rol: rol as RolAsignacionProfesor } : g));
  }

  quitarGrupo(grupoId: number): void {
    this.gruposError = null;
    this.gruposElegidos = this.gruposElegidos.filter((g) => g.grupoId !== grupoId);
  }

  anadirGrupo(): void {
    const id = Number(this.grupoNuevo);
    const grupo = this.gruposDisponibles.find((g) => g.id === id);
    if (!grupo) {
      return;
    }
    this.gruposError = null;
    this.gruposElegidos = [...this.gruposElegidos, { grupoId: grupo.id, nombre: grupo.nombre, sedeId: null, rol: this.rolNuevo }];
    this.grupoNuevo = '';
  }

  guardarGrupos(): void {
    const ficha = this.ficha;
    if (!ficha || this.gruposOcupado) {
      return;
    }
    this.gruposError = null;
    this.escribir(
      this.api.reemplazarGrupos(
        ficha.usuarioId,
        this.gruposElegidos.map((g) => ({ grupoId: g.grupoId, rol: g.rol }))
      ),
      ['grupos'],
      (v) => (this.gruposOcupado = v),
      (m) => (this.gruposError = m),
      'No se pudieron guardar los grupos.',
      'Grupos guardados.'
    );
  }
}
