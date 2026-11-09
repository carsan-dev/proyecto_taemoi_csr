import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { DisciplinaRallas, RallasService } from '../../../servicios/endpoints/rallas.service';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { CONSULTA_HOJA, anchoDeHoja } from '../../capas/capa-modal';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { CinturonComponent } from '../../cinturon/cinturon.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { LecturasPanelService } from '../../carga/lecturas-panel.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { plural } from '../../formato/formato-es';
import { ENLACES_LEGALES } from '../../navegacion/menu-panel';
import { SECCIONES, SeccionConfiguracion, validarLimite } from './configuracion-vista';

/**
 * Configuración del panel nuevo (8b-3k). Un solo componente con dos distribuciones según el ancho (decisión 261): por
 * debajo de 768 px, P1, solo «Alumnos por turno»; desde 768 px, P2, un índice de ajustes a la izquierda («Alumnos por
 * turno» y «Avisos legales y cookies») y la sección elegida a la derecha. Las secciones son las mismas plantillas
 * (`ng-template`) en las dos: sin lógica duplicada.
 *
 * «Roles y permisos» salió en la pieza 9-8 (los roles y la contraseña se gestionan desde la ficha de Miembros). Las
 * llamadas: `GET /turnos/limite` (con `LecturasPanelService`, `ERROR_HANDLED_LOCALLY`) y `actualizarLimiteTurno` de
 * `EndpointsService`. La confirmación, con el diálogo del panel; los avisos, los del panel. Solo del propietario
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
    BotonOcupadoComponent,
    CinturonComponent,
  ],
  templateUrl: './configuracion.component.html',
  styleUrls: ['./configuracion.component.scss'],
})
export class ConfiguracionPanelComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly endpoints = inject(EndpointsService);
  private readonly rallas = inject(RallasService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroy$ = new Subject<void>();
  private destruido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.ancho = !e.matches;
    this.cdr.detectChanges();
  };

  readonly legales = ENLACES_LEGALES;
  readonly plural = plural;

  /** Desde 768 px, P2 con índice; por debajo, P1. */
  ancho = !anchoDeHoja();
  seccion: SeccionConfiguracion = 'turno';

  // Alumnos por turno
  limiteOriginal: number | null = null;
  textoLimite = '';
  cargandoLimite = true;
  errorLimite = false;

  // Rallas hacia el examen (decisiones 510-513): un interruptor por disciplina; se guarda con la barra de cambios.
  /** Lo guardado, por disciplina; null hasta que llega. */
  disciplinasRallas: DisciplinaRallas[] | null = null;
  /** Lo elegido ahora, por código de disciplina. */
  rallasElegidas: Record<string, boolean> = {};
  cargandoRallas = true;
  errorRallas = false;
  /** El API no la da (403) o no hay disciplinas con grados: la sección no se ve. */
  rallasOcultas = false;
  guardandoRallas = false;

  /** Ejemplo de la sección (decorativo): media, 2 y media y las 5 rallas, con colores del catálogo de Moiskimdo. */
  readonly ejemploRallas = [
    { texto: 'Media', color1: '#FFFF00', color2: null, medias: 1 },
    { texto: '2 y media', color1: '#FFFF00', color2: '#FFA500', medias: 5 },
    { texto: '5: listo para examen', color1: '#008000', color2: null, medias: 10 },
  ];

  ngOnInit(): void {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
    this.cargarLimite();
    this.cargarRallas();
  }

  /** Las secciones del índice; sin la de rallas si no se ve. */
  get secciones(): ReadonlyArray<{ clave: SeccionConfiguracion; titulo: string }> {
    return this.verRallas ? SECCIONES : SECCIONES.filter((s) => s.clave !== 'rallas');
  }

  get verRallas(): boolean {
    return !this.rallasOcultas;
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

  // ------------------------------------------------------------------ rallas hacia el examen

  cargarRallas(): void {
    this.cargandoRallas = true;
    this.errorRallas = false;
    this.rallas
      .configuracion()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (lista) => {
          this.ponerRallasGuardadas(lista);
          this.rallasOcultas = lista.length === 0;
          this.cargandoRallas = false;
          this.cdr.detectChanges();
        },
        error: (e) => {
          this.cargandoRallas = false;
          // Sin permiso, la sección no existe para quien entra; cualquier otro fallo, con «Reintentar».
          if (e?.status === 403) {
            this.rallasOcultas = true;
            if (this.seccion === 'rallas') this.seccion = 'turno';
          } else {
            this.errorRallas = true;
          }
          this.cdr.detectChanges();
        },
      });
  }

  private ponerRallasGuardadas(lista: DisciplinaRallas[]): void {
    this.disciplinasRallas = lista;
    this.rallasElegidas = Object.fromEntries(lista.map((d) => [d.deporte, d.activas]));
  }

  /** Las disciplinas cuyo interruptor no es el guardado (solo esas se envían). */
  get cambiosRallas(): Record<string, boolean> {
    const cambios: Record<string, boolean> = {};
    for (const d of this.disciplinasRallas ?? []) {
      const elegida = !!this.rallasElegidas[d.deporte];
      if (elegida !== d.activas) cambios[d.deporte] = elegida;
    }
    return cambios;
  }

  get numCambiosRallas(): number {
    return Object.keys(this.cambiosRallas).length;
  }

  get textoCambiosRallas(): string {
    const n = this.numCambiosRallas;
    return n === 1 ? '1 cambio sin guardar' : `${n} cambios sin guardar`;
  }

  activa(d: DisciplinaRallas): boolean {
    return !!this.rallasElegidas[d.deporte];
  }

  modificada(d: DisciplinaRallas): boolean {
    return this.activa(d) !== d.activas;
  }

  /** Apagada ahora y encendida en lo guardado: la nota de que no se borran. */
  seApaga(d: DisciplinaRallas): boolean {
    return d.activas && !this.activa(d);
  }

  /** Rojo-negro (PUM) y negro (DAN) solo existen en el sistema de grados de taekwondo. */
  descripcionRallas(d: DisciplinaRallas): string {
    return d.deporte === 'TAEKWONDO' ? 'Todos los grados de color. Rojo-negro (PUM) y negro (DAN) no llevan.' : 'Todos los grados de color.';
  }

  alternarRallas(d: DisciplinaRallas): void {
    if (this.guardandoRallas) return;
    this.rallasElegidas = { ...this.rallasElegidas, [d.deporte]: !this.activa(d) };
  }

  descartarRallas(): void {
    if (this.guardandoRallas || !this.disciplinasRallas) return;
    this.ponerRallasGuardadas(this.disciplinasRallas);
  }

  guardarRallas(): void {
    const cambios = this.cambiosRallas;
    if (this.guardandoRallas || !Object.keys(cambios).length) return;
    this.guardandoRallas = true;
    this.rallas
      .guardarConfiguracion(cambios)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (lista) => {
          this.guardandoRallas = false;
          this.ponerRallasGuardadas(lista);
          this.avisos.exito('Rallas guardadas.');
          this.cdr.detectChanges();
        },
        // Lo elegido se queda: solo se avisa.
        error: () => {
          this.guardandoRallas = false;
          this.avisos.error('No se pudo guardar la configuración.');
          this.cdr.detectChanges();
        },
      });
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
}
