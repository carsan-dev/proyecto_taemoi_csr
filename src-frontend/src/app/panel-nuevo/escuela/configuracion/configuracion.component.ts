import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { CONSULTA_HOJA, anchoDeHoja, cerrarSweetAlertAbierto } from '../../capas/capa-modal';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BloqueVacioComponent } from '../../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { LecturasPanelService } from '../../carga/lecturas-panel.service';
import { iniciales, paginasVisibles } from '../../convocatorias/convocatorias-vista';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { numero, plural } from '../../formato/formato-es';
import { etiquetaRol } from '../../formato/roles';
import { ENLACES_LEGALES } from '../../navegacion/menu-panel';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import {
  CUENTAS_POR_PAGINA,
  CuentaConfiguracion,
  REGLAS_CONTRASENA,
  ROLES_ASIGNABLES,
  RolAsignable,
  SECCIONES,
  SeccionConfiguracion,
  SeleccionRoles,
  algunRol,
  contrasenaCumpleReglas,
  cuerpoRoles,
  esCuentaGoogle,
  filtrarCuentas,
  listaRoles,
  mensajeErrorContrasena,
  mensajeErrorRoles,
  mismaSeleccion,
  nombreCompleto,
  pastillasRoles,
  seleccionDe,
  validarLimite,
} from './configuracion-vista';

/** La página de cuentas, como hoy (`configuracionSistemaUsuariosEstado`), con su propia clave: nada va a la URL. */
const CLAVE_ESTADO = 'panelConfiguracionUsuariosEstado';

interface FormContrasena {
  cuenta: CuentaConfiguracion;
  nueva: string;
  confirmar: string;
  mostrar: boolean;
  enviando: boolean;
  error: string | null;
}

/**
 * Configuración del panel nuevo (8b-3k). Un solo componente con dos distribuciones según el ancho (decisión 261): por
 * debajo de 768 px, P1, las secciones «Alumnos por turno» y «Roles y permisos» seguidas; desde 768 px, P2, un índice de
 * ajustes a la izquierda (con «Avisos legales y cookies») y la sección elegida a la derecha. Las secciones son las mismas
 * plantillas (`ng-template`) en las dos: sin lógica duplicada.
 *
 * Las MISMAS llamadas que `ConfiguracionSistemaComponent`: `GET /turnos/limite` y `GET /admin/users` (con
 * `LecturasPanelService`, la misma petición con `ERROR_HANDLED_LOCALLY`), y `actualizarLimiteTurno`,
 * `actualizarRolesUsuario` (el cuerpo con los `ROLE_*`, en el orden de hoy) y `actualizarContrasenaUsuario` de
 * `EndpointsService`. Solo cambia el nombre visible de los roles (decisiones 262 y 263): Familia, Gestión y
 * Administración se pueden dar; Profesorado y Responsable de sede se ven, pero no se ofrecen (la ruta de roles no los
 * acepta, como hoy). Las confirmaciones, con el diálogo del panel; los avisos, los del panel. Solo del propietario
 * (`adminOnlyGuard` en la ruta).
 */
@Component({
  selector: 'app-configuracion-panel',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    RouterLink,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    PanelLateralComponent,
  ],
  templateUrl: './configuracion.component.html',
  styleUrls: ['./configuracion.component.scss'],
})
export class ConfiguracionPanelComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly endpoints = inject(EndpointsService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroy$ = new Subject<void>();
  private destruido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.ancho = !e.matches;
    this.cdr.detectChanges();
  };

  readonly secciones = SECCIONES;
  readonly rolesAsignables = ROLES_ASIGNABLES;
  readonly reglas = REGLAS_CONTRASENA;
  readonly legales = ENLACES_LEGALES;
  readonly etiquetaRol = etiquetaRol;
  readonly pastillasRoles = pastillasRoles;
  readonly nombreCompleto = nombreCompleto;
  readonly esCuentaGoogle = esCuentaGoogle;
  readonly iniciales = iniciales;
  readonly numero = numero;
  readonly plural = plural;

  /** Desde 768 px, P2 con índice; por debajo, P1. */
  ancho = !anchoDeHoja();
  seccion: SeccionConfiguracion = 'turno';

  // Alumnos por turno
  limiteOriginal: number | null = null;
  textoLimite = '';
  cargandoLimite = true;
  errorLimite = false;

  // Roles y permisos
  cuentas: CuentaConfiguracion[] | null = null;
  cargandoCuentas = true;
  errorCuentas = false;
  filtro = '';
  pagina = 1;
  readonly seleccion = new Map<number, SeleccionRoles>();

  formContrasena: FormContrasena | null = null;

  ngOnInit(): void {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
    this.restaurarPagina();
    this.cargarLimite();
    this.cargarCuentas();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ índice (P2)

  elegirSeccion(clave: SeccionConfiguracion): void {
    this.seccion = clave;
  }

  /** Flechas arriba y abajo (con vuelta), Inicio y Fin, como un grupo de pestañas vertical. */
  teclaIndice(evento: KeyboardEvent): void {
    const claves = this.secciones.map((s) => s.clave);
    const actual = claves.indexOf(this.seccion);
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowDown':
      case 'ArrowRight':
        siguiente = (actual + 1) % claves.length;
        break;
      case 'ArrowUp':
      case 'ArrowLeft':
        siguiente = (actual - 1 + claves.length) % claves.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = claves.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.seccion = claves[siguiente];
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#conf-tab-${this.seccion}`)?.focus();
  }

  // ------------------------------------------------------------------ alumnos por turno

  cargarLimite(): void {
    this.cargandoLimite = true;
    this.errorLimite = false;
    this.lecturas
      .limiteTurno()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (limite) => {
          this.limiteOriginal = limite;
          this.textoLimite = String(limite);
          this.cargandoLimite = false;
        },
        error: () => {
          this.cargandoLimite = false;
          this.errorLimite = true;
        },
      });
  }

  get validacionLimite(): { valor: number | null; error: string | null } {
    return validarLimite(this.textoLimite);
  }

  /** Lo escrito no es lo guardado (vale también para un valor que no se puede guardar). */
  get limiteModificado(): boolean {
    return this.limiteOriginal !== null && this.textoLimite.trim() !== String(this.limiteOriginal);
  }

  get puedeGuardarLimite(): boolean {
    const { valor, error } = this.validacionLimite;
    return !error && valor !== null && valor !== this.limiteOriginal;
  }

  escribirLimite(evento: Event): void {
    this.textoLimite = (evento.target as HTMLInputElement).value;
  }

  restaurarLimite(): void {
    if (this.limiteOriginal !== null) {
      this.textoLimite = String(this.limiteOriginal);
    }
  }

  async guardarLimite(): Promise<void> {
    const { valor } = this.validacionLimite;
    if (!this.puedeGuardarLimite || valor === null || this.limiteOriginal === null) {
      return;
    }
    const resultado = await this.confirmacion.confirmar({
      titulo: 'Cambiar el límite por turno',
      mensaje: `El límite de alumnos por turno pasa de ${this.limiteOriginal} a ${valor}.`,
      accion: 'Guardar',
      accionOcupada: 'Guardando…',
      nombre: 'limite-turno',
      ejecutar: () => this.endpoints.actualizarLimiteTurno(valor),
    });
    if (!resultado.confirmado || this.destruido) {
      return;
    }
    if (resultado.error) {
      this.avisos.error('No se pudo guardar la configuración.');
      this.cdr.detectChanges();
      return;
    }
    this.limiteOriginal = valor;
    this.textoLimite = String(valor);
    this.avisos.exito(`Límite por turno guardado: ${plural(valor, 'alumno', 'alumnos')}.`);
    this.cdr.detectChanges();
  }

  // ------------------------------------------------------------------ roles y permisos

  cargarCuentas(): void {
    this.cargandoCuentas = true;
    this.errorCuentas = false;
    this.lecturas
      .usuarios()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cuentas) => {
          this.cuentas = (cuentas ?? []) as CuentaConfiguracion[];
          this.seleccion.clear();
          this.cuentas.forEach((c) => this.seleccion.set(c.id, seleccionDe(c.rol)));
          this.cargandoCuentas = false;
          this.ajustarPagina();
          this.cdr.detectChanges();
        },
        error: () => {
          this.cargandoCuentas = false;
          this.errorCuentas = true;
          this.cdr.detectChanges();
        },
      });
  }

  get filtradas(): CuentaConfiguracion[] {
    return filtrarCuentas(this.cuentas ?? [], this.filtro);
  }

  get totalPaginas(): number {
    return Math.ceil(this.filtradas.length / CUENTAS_POR_PAGINA);
  }

  get paginadas(): CuentaConfiguracion[] {
    const inicio = (this.pagina - 1) * CUENTAS_POR_PAGINA;
    return this.filtradas.slice(inicio, inicio + CUENTAS_POR_PAGINA);
  }

  get paginas(): (number | null)[] {
    return paginasVisibles(this.pagina, this.totalPaginas);
  }

  buscar(evento: Event): void {
    this.filtro = (evento.target as HTMLInputElement).value;
    this.pagina = 1;
    this.ajustarPagina();
  }

  limpiarBusqueda(): void {
    this.filtro = '';
    this.pagina = 1;
    this.ajustarPagina();
    this.host.nativeElement.querySelector<HTMLInputElement>('#conf-buscar')?.focus();
  }

  irAPagina(n: number): void {
    if (n < 1 || n > this.totalPaginas || n === this.pagina) {
      return;
    }
    this.pagina = n;
    this.guardarPagina();
  }

  marcado(cuenta: CuentaConfiguracion, rol: RolAsignable): boolean {
    return this.seleccion.get(cuenta.id)?.[rol] ?? false;
  }

  /** Como hoy: una cuenta tiene al menos un rol; quitar el último lo vuelve a marcar con un aviso. */
  cambiarRol(cuenta: CuentaConfiguracion, rol: RolAsignable, evento: Event): void {
    const casilla = evento.target as HTMLInputElement;
    const actual = this.seleccion.get(cuenta.id) ?? seleccionDe(cuenta.rol);
    const siguiente = { ...actual, [rol]: casilla.checked };
    if (!algunRol(siguiente)) {
      casilla.checked = true;
      this.avisos.info('Una cuenta debe tener al menos un rol.');
      return;
    }
    this.seleccion.set(cuenta.id, siguiente);
  }

  rolesCambiados(cuenta: CuentaConfiguracion): boolean {
    const actual = this.seleccion.get(cuenta.id);
    return !!actual && !mismaSeleccion(actual, seleccionDe(cuenta.rol));
  }

  async guardarRoles(cuenta: CuentaConfiguracion): Promise<void> {
    const actual = this.seleccion.get(cuenta.id);
    if (!actual || !this.rolesCambiados(cuenta)) {
      return;
    }
    const cuerpo = cuerpoRoles(actual);
    const nombre = nombreCompleto(cuenta);
    const resultado = await this.confirmacion.confirmar({
      titulo: 'Cambiar roles',
      mensaje: ['Se cambiarán los roles de ', { fuerte: nombre }, ` a ${listaRoles(cuerpo)}.`],
      accion: 'Guardar roles',
      accionOcupada: 'Guardando…',
      nombre: 'roles',
      ejecutar: () => this.endpoints.actualizarRolesUsuario(cuenta.id, cuerpo),
    });
    if (!resultado.confirmado || this.destruido) {
      return;
    }
    if (resultado.error) {
      // Como hoy: las casillas vuelven a lo guardado y se enseña el `mensaje` (el 409 de último propietario).
      this.seleccion.set(cuenta.id, seleccionDe(cuenta.rol));
      this.avisos.error(mensajeErrorRoles(resultado.error));
      this.cdr.detectChanges();
      return;
    }
    this.avisos.exito(`Roles de ${nombre} actualizados.`);
    this.cargarCuentas();
  }

  // ------------------------------------------------------------------ restablecer contraseña

  abrirContrasena(cuenta: CuentaConfiguracion): void {
    if (esCuentaGoogle(cuenta)) {
      this.avisos.info('Esta cuenta usa Google. El restablecimiento manual no aplica.');
      return;
    }
    this.formContrasena = { cuenta, nueva: '', confirmar: '', mostrar: false, enviando: false, error: null };
  }

  cerrarContrasena(): void {
    if (this.formContrasena?.enviando) {
      return;
    }
    this.formContrasena = null;
  }

  escribirContrasena(campo: 'nueva' | 'confirmar', evento: Event): void {
    if (!this.formContrasena) {
      return;
    }
    this.formContrasena[campo] = (evento.target as HTMLInputElement).value;
    this.formContrasena.error = null;
  }

  alternarMostrar(evento: Event): void {
    if (this.formContrasena) {
      this.formContrasena.mostrar = (evento.target as HTMLInputElement).checked;
    }
  }

  guardarContrasena(evento?: Event): void {
    evento?.preventDefault();
    const form = this.formContrasena;
    if (!form || form.enviando) {
      return;
    }
    if (!contrasenaCumpleReglas(form.nueva)) {
      form.error = 'La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).';
      this.enfocarError();
      return;
    }
    if (form.nueva !== form.confirmar) {
      form.error = 'Las contraseñas no coinciden';
      this.enfocarError();
      return;
    }
    form.enviando = true;
    form.error = null;
    // Sin takeUntil: salir de la pantalla no cancela una escritura en vuelo.
    this.endpoints.actualizarContrasenaUsuario(form.cuenta.id, form.nueva).subscribe({
      next: () => {
        if (this.destruido) {
          return;
        }
        this.formContrasena = null;
        this.avisos.exito('Contraseña actualizada correctamente.');
        this.cdr.detectChanges();
      },
      error: (error) => {
        if (this.destruido) {
          return;
        }
        // El error se ve en el panel: el SweetAlert2 del interceptor quedaría detrás del <dialog> modal.
        cerrarSweetAlertAbierto(this.router);
        form.enviando = false;
        form.error = mensajeErrorContrasena(error);
        this.cdr.detectChanges();
        this.enfocarError();
      },
    });
  }

  private enfocarError(): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('#conf-nueva')?.focus();
  }

  // ------------------------------------------------------------------ página guardada (como hoy, en la sesión)

  private ajustarPagina(): void {
    const total = this.totalPaginas;
    if (total === 0) {
      this.pagina = 1;
    } else if (this.pagina > total) {
      this.pagina = total;
    }
    this.guardarPagina();
  }

  private guardarPagina(): void {
    try {
      sessionStorage.setItem(CLAVE_ESTADO, JSON.stringify({ pagina: this.pagina }));
    } catch {
      // Sin almacenamiento, la página no se recuerda.
    }
  }

  private restaurarPagina(): void {
    try {
      const guardado = JSON.parse(sessionStorage.getItem(CLAVE_ESTADO) ?? 'null');
      const pagina = Number(guardado?.pagina);
      this.pagina = Number.isInteger(pagina) && pagina > 0 ? pagina : 1;
    } catch {
      this.pagina = 1;
    }
  }
}
