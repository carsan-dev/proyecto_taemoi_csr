import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, Subscription, interval } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../../interfaces/auditoria-evento-detalle';
import { PaginatedResponse } from '../../../interfaces/paginated-response';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BloqueVacioComponent } from '../../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { LecturasPanelService } from '../../carga/lecturas-panel.service';
import { paginasVisibles } from '../../convocatorias/convocatorias-vista';
import { numero, plural } from '../../formato/formato-es';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import {
  ACCIONES,
  AccionFiltro,
  DiaRegistro,
  FiltrosRegistro,
  INTERVALO_ACTUALIZACION_MS,
  MODULO_SALUD,
  TAMANO_PAGINA,
  agruparPorDia,
  describirAccion,
  fechaCompleta,
  filtrosParaConsulta,
  filtrosVacios,
  formatearJson,
  hora,
  numeroFiltrosActivos,
  textoCuenta,
  tieneDatosTecnicos,
} from './registro-vista';

/**
 * Registro de acciones del panel nuevo (8b-3k, P2 «cronología por días con detalle»). Los días como título y, por
 * acción, la hora, la pastilla del tipo, qué, el módulo y «Cuenta N» (el identificador: ni nombre ni email); el detalle,
 * con los datos técnicos desplegables, en un panel al lado. Los filtros (desde, hasta, acción, módulo, persona y buscar)
 * se pliegan en «Filtros».
 *
 * Las MISMAS lecturas que `AuditoriaSistemaComponent` (con `LecturasPanelService`: la misma petición con
 * `ERROR_HANDLED_LOCALLY`): módulos al entrar, la página con `resultado=EXITO` y los filtros de hoy, el detalle al
 * pulsar una acción, «Actualizar ahora» y la relectura cada 30 s. El detalle enseña lo que hoy enseña, tal como llega
 * redactado del backend, salvo la persona, que pasa a «Cuenta N». Nada va a la URL ni al almacenamiento.
 */
@Component({
  selector: 'app-registro-acciones',
  standalone: true,
  imports: [AcentoEscuelaDirective, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent, BotonOcupadoComponent, PanelLateralComponent],
  templateUrl: './registro.component.html',
  styleUrls: ['./registro.component.scss'],
})
export class RegistroAccionesComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private consultaEventos?: Subscription;
  private consultaDetalle?: Subscription;

  readonly acciones = ACCIONES;
  readonly moduloSalud = MODULO_SALUD;
  readonly describirAccion = describirAccion;
  readonly textoCuenta = textoCuenta;
  readonly hora = hora;
  readonly fechaCompleta = fechaCompleta;
  readonly formatearJson = formatearJson;
  readonly tieneDatosTecnicos = tieneDatosTecnicos;
  readonly numero = numero;
  readonly plural = plural;

  modulos: string[] = [];
  /** Lo que se escribe en el formulario; `aplicados`, lo que se pidió por última vez. */
  filtros: FiltrosRegistro = filtrosVacios();
  aplicados: FiltrosRegistro = filtrosVacios();
  filtrosAbiertos = false;

  eventos: AuditoriaEvento[] | null = null;
  dias: DiaRegistro[] = [];
  cargando = true;
  error = false;
  pagina = 1;
  totalPaginas = 0;
  total = 0;

  detalle: AuditoriaEventoDetalle | null = null;
  /** La acción cuyo detalle se está leyendo. */
  leyendoDetalle: number | null = null;

  ngOnInit(): void {
    this.lecturas
      .auditoriaModulos()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (modulos) => (this.modulos = modulos ?? []),
        error: () => (this.modulos = []),
      });
    this.cargar();
    interval(INTERVALO_ACTUALIZACION_MS)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.cargando && this.leyendoDetalle === null) {
          this.cargar();
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.consultaEventos?.unsubscribe();
    this.consultaDetalle?.unsubscribe();
  }

  get numeroFiltros(): number {
    return numeroFiltrosActivos(this.aplicados);
  }

  get ocupado(): boolean {
    return this.cargando || this.leyendoDetalle !== null;
  }

  get paginas(): (number | null)[] {
    return paginasVisibles(this.pagina, this.totalPaginas);
  }

  alternarFiltros(): void {
    this.filtrosAbiertos = !this.filtrosAbiertos;
  }

  ponerFiltro<K extends keyof FiltrosRegistro>(clave: K, evento: Event): void {
    const valor = (evento.target as HTMLInputElement | HTMLSelectElement).value;
    this.filtros = { ...this.filtros, [clave]: (clave === 'desde' || clave === 'hasta') && !valor ? null : (valor as FiltrosRegistro[K]) };
  }

  ponerAccion(evento: Event): void {
    this.filtros = { ...this.filtros, accion: (evento.target as HTMLSelectElement).value as AccionFiltro };
  }

  aplicarFiltros(evento?: Event): void {
    evento?.preventDefault();
    this.aplicados = { ...this.filtros };
    this.pagina = 1;
    this.detalle = null;
    this.cargar();
  }

  restablecer(): void {
    this.filtros = filtrosVacios();
    this.aplicarFiltros();
  }

  actualizarAhora(): void {
    if (this.ocupado) {
      return;
    }
    this.cargar();
  }

  irAPagina(n: number): void {
    if (n < 1 || n > this.totalPaginas || n === this.pagina || this.cargando) {
      return;
    }
    this.pagina = n;
    this.cargar();
  }

  /** Lee la página con los filtros aplicados; el detalle abierto se queda (como hoy al actualizar). */
  cargar(): void {
    this.cargando = true;
    this.error = false;
    this.consultaEventos?.unsubscribe();
    this.consultaEventos = this.lecturas
      .auditoriaEventos(filtrosParaConsulta(this.aplicados), this.pagina, TAMANO_PAGINA)
      .subscribe({
        next: (respuesta: PaginatedResponse<AuditoriaEvento>) => {
          this.eventos = respuesta?.content ?? [];
          this.dias = agruparPorDia(this.eventos);
          this.totalPaginas = respuesta?.totalPages ?? 0;
          this.total = respuesta?.totalElements ?? this.eventos.length;
          this.cargando = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.cargando = false;
          this.error = true;
          this.eventos = null;
          this.dias = [];
          this.totalPaginas = 0;
          this.total = 0;
          this.cdr.detectChanges();
        },
      });
  }

  verDetalle(evento: AuditoriaEvento): void {
    if (this.leyendoDetalle !== null) {
      return;
    }
    this.leyendoDetalle = evento.id;
    this.consultaDetalle = this.lecturas.auditoriaEventoDetalle(evento.id).subscribe({
      next: (detalle) => {
        this.leyendoDetalle = null;
        this.detalle = detalle;
        this.cdr.detectChanges();
      },
      error: () => {
        this.leyendoDetalle = null;
        this.avisos.error('No se pudo cargar el detalle de la acción.');
        this.cdr.detectChanges();
      },
    });
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }
}
