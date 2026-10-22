import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize, map } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { SearchableSelectDirective } from '../../../../directives/searchable-select.directive';
import { CatalogoService } from '../../../../features/catalogo/catalogo.service';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { AccionInforme, InformePdfService } from '../../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../../servicios/generales/loading.service';
import { showErrorToast, showSuccessToast } from '../../../../utils/toast.util';
import { SelectorMesAnoComponent } from '../../../generales/selector-mes-ano/selector-mes-ano.component';
import { getDeporteLabel } from '../../../../enums/deporte';
import { generarPdf } from '../informes-alumnos';

export type SeccionHerramientas = 'mensualidades' | 'asistencia' | 'listado-mensualidades';

/**
 * Las tres herramientas plegables del pie del listado de alumnos (gestión de mensualidades y licencias, listado de
 * asistencia y listado de mensualidades). Salen tal cual del listado de hoy (8b-3a) para que el listado nuevo las use sin
 * copiarlas. El {@link InformePdfService} es el del listado que las contiene (lo provee él). Tras asignar mensualidades o
 * licencias generales emite `asignado` para que el listado se recargue.
 */
@Component({
  selector: 'app-herramientas-alumnos',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectorMesAnoComponent, SearchableSelectDirective],
  templateUrl: './herramientas-alumnos.component.html',
  styleUrl: './herramientas-alumnos.component.scss',
})
export class HerramientasAlumnosComponent implements OnChanges {
  readonly informes = inject(InformePdfService);
  readonly catalogo = inject(CatalogoService);
  private readonly endpoints = inject(EndpointsService);
  private readonly loadingService = inject(LoadingService);

  /** Los del listado: con ellos cambia la lista de alumnos seleccionables, como hoy. */
  @Input() mostrarInactivos = false;
  @Input() aptoParaExamen = false;
  @Output() readonly asignado = new EventEmitter<void>();

  alumnosSeleccionables: any[] = [];
  cargandoAlumnosSeleccionables: boolean = false;
  private alumnosSeleccionablesCargados: boolean = false;
  mesAnoSeleccionado: string = '';
  deporteSeleccionado: string = 'TODOS';
  alumnoSeleccionado: number | null = null;
  mesAnoSeleccionadoIndividual: string = '';
  deporteSeleccionadoIndividual: string = 'TODOS';
  anoLicenciaSeleccionado: number = new Date().getFullYear();
  deporteLicenciaSeleccionado: string = 'TODOS';
  anoLicenciaSeleccionadoIndividual: number = new Date().getFullYear();
  deporteLicenciaSeleccionadoIndividual: string = 'TODOS';
  fechaMensualidadGeneral: string = this.obtenerFechaActualLocalYyyyMmDd();
  fechaMensualidadIndividual: string = this.obtenerFechaActualLocalYyyyMmDd();
  fechaLicenciaGeneral: string = this.obtenerFechaActualLocalYyyyMmDd();
  fechaLicenciaIndividual: string = this.obtenerFechaActualLocalYyyyMmDd();
  licenciasTab: 'general' | 'individual' = 'general';
  mensualidadesTab: 'general' | 'individual' = 'general';
  mesAnoAsistencia!: string;
  mesAnoMensualidad!: string;
  grupos = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes'];
  gruposSeleccionados: string[] = [];
  procesandoMensualidadesGenerales: boolean = false;
  procesandoMensualidadIndividual: boolean = false;
  procesandoLicenciasGenerales: boolean = false;
  procesandoLicenciaIndividual: boolean = false;

  @ViewChild('seccionMensualidades') private seccionMensualidades?: ElementRef<HTMLDetailsElement>;
  @ViewChild('seccionAsistencia') private seccionAsistencia?: ElementRef<HTMLDetailsElement>;
  @ViewChild('seccionListadoMensualidades') private seccionListadoMensualidades?: ElementRef<HTMLDetailsElement>;

  /** Desde el menú «Informes» del listado nuevo: despliega la sección, la muestra y lleva el foco a su título. */
  abrir(seccion: SeccionHerramientas): void {
    const details = {
      mensualidades: this.seccionMensualidades,
      asistencia: this.seccionAsistencia,
      'listado-mensualidades': this.seccionListadoMensualidades,
    }[seccion]?.nativeElement;
    if (!details) {
      return;
    }
    details.open = true;
    const titulo = details.querySelector<HTMLElement>('summary');
    titulo?.focus({ preventScroll: true });
    details.scrollIntoView({ block: 'start' });
  }

  ngOnChanges(cambios: SimpleChanges): void {
    const cambio = cambios['mostrarInactivos'] ?? cambios['aptoParaExamen'];
    if (cambio && !cambio.firstChange) {
      this.refrescarAlumnosSeleccionablesSiNecesario();
    }
  }

  toggleGrupoSeleccionado(grupo: string): void {
    const index = this.gruposSeleccionados.indexOf(grupo);
    if (index >= 0) {
      this.gruposSeleccionados.splice(index, 1);
    } else {
      this.gruposSeleccionados.push(grupo);
    }
  }

  cambiarMensualidadesTab(tab: 'general' | 'individual'): void {
    this.mensualidadesTab = tab;
    if (tab === 'individual') {
      this.cargarTodosLosAlumnos();
    }
  }

  cambiarLicenciasTab(tab: 'general' | 'individual'): void {
    this.licenciasTab = tab;
    if (tab === 'individual') {
      this.cargarTodosLosAlumnos();
    }
  }

  cargarTodosLosAlumnos(): void {
    if (this.alumnosSeleccionablesCargados || this.cargandoAlumnosSeleccionables) {
      return;
    }

    this.cargandoAlumnosSeleccionables = true;
    this.endpoints
      .obtenerAlumnosSinPaginar(this.mostrarInactivos)
      .pipe(finalize(() => (this.cargandoAlumnosSeleccionables = false)))
      .subscribe({
        next: (response) => {
          this.alumnosSeleccionables = response;
          this.alumnosSeleccionablesCargados = true;
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo cargar la lista completa de alumnos.',
            'error'
          );
        },
      });
  }

  private refrescarAlumnosSeleccionablesSiNecesario(): void {
    if (!this.alumnosSeleccionablesCargados) {
      return;
    }
    this.alumnosSeleccionablesCargados = false;
    this.cargarTodosLosAlumnos();
  }

  cargarMensualidadesGenerales(): void {
    if (this.procesandoMensualidadesGenerales) {
      return;
    }

    if (!this.mesAnoSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un mes y año.',
        icon: 'error',
      });
      return;
    }

    // Show loading spinner
    this.procesandoMensualidadesGenerales = true;
    this.loadingService.show();

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaMensualidadGeneral
    );

    // Determine which service method to call based on sport selection
    const serviceCall =
      this.deporteSeleccionado === 'TODOS'
        ? this.endpoints.cargarMensualidadesGenerales(
            this.mesAnoSeleccionado,
            fechaAsignacion
          )
        : this.endpoints.cargarMensualidadesPorDeporte(
            this.mesAnoSeleccionado,
            this.deporteSeleccionado,
            fechaAsignacion
          );

    const deporteTexto =
      this.deporteSeleccionado === 'TODOS'
        ? 'todos los alumnos activos'
        : `alumnos activos de ${this.deporteSeleccionado}`;

    serviceCall.pipe(
      finalize(() => {
        this.procesandoMensualidadesGenerales = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast(`Mensualidades asignadas a ${deporteTexto}`);
        // El listado se recarga con su modo de paginación de siempre
        this.asignado.emit();
      },
      error: () => {
        showErrorToast('Error al asignar las mensualidades');
      },
    });
  }

  cargarMensualidadIndividual(): void {
    if (this.procesandoMensualidadIndividual) {
      return;
    }

    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaMensualidadIndividual
    );

    // Determine which service method to call based on sport selection
    const serviceCall =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? this.endpoints.cargarMensualidadIndividual(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            false,
            fechaAsignacion
          )
        : this.endpoints.cargarMensualidadIndividualPorDeporte(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            this.deporteSeleccionadoIndividual,
            false,
            fechaAsignacion
          );

    const deporteTexto =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteSeleccionadoIndividual;

    this.procesandoMensualidadIndividual = true;
    this.loadingService.show();
    serviceCall.pipe(
      finalize(() => {
        this.procesandoMensualidadIndividual = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast(`Mensualidad de ${deporteTexto} cargada correctamente`);
      },
      error: (error) => {
        if (error.status === 409 && error.error?.accion === 'completar') {
          const html = this.construirAvisoMensualidadIncompleta(error.error);
          Swal.fire({
            title: 'Atención',
            html,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Sí, aplicar',
            cancelButtonText: 'No, cancelar',
          }).then((result) => {
            if (result.isConfirmed) {
              this.forzarCargarMensualidad();
            }
          });
          return;
        }
        if (error.status === 409 && error.error?.accion === 'confirmar') {
          Swal.fire({
            title: 'Atención',
            text: error.error?.mensaje || 'La mensualidad ya existe.',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Sí, cargar',
            cancelButtonText: 'No, cancelar',
          }).then((result) => {
            if (result.isConfirmed) {
              this.forzarCargarMensualidad();
            }
          });
          return;
        }
        Swal.fire('Error', error?.error?.mensaje || 'No se pudo cargar la mensualidad.', 'error');
      },
    });
  }

  forzarCargarMensualidad(): void {
    if (this.procesandoMensualidadIndividual) {
      return;
    }

    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaMensualidadIndividual
    );

    const serviceCall =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? this.endpoints.cargarMensualidadIndividual(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            true,
            fechaAsignacion
          )
        : this.endpoints.cargarMensualidadIndividualPorDeporte(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            this.deporteSeleccionadoIndividual,
            true,
            fechaAsignacion
          );

    const deporteTexto =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteSeleccionadoIndividual;

    this.procesandoMensualidadIndividual = true;
    this.loadingService.show();
    serviceCall.pipe(
      finalize(() => {
        this.procesandoMensualidadIndividual = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast('Productos faltantes de mensualidad y competidor cargados correctamente');
      },
      error: () => {
        showErrorToast('No se pudo cargar la mensualidad');
      },
    });
  }

  cargarLicenciasGenerales(): void {
    if (this.procesandoLicenciasGenerales) {
      return;
    }

    if (!this.anoLicenciaSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un año.',
        icon: 'error',
      });
      return;
    }

    this.procesandoLicenciasGenerales = true;
    this.loadingService.show();

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaLicenciaGeneral
    );

    const serviceCall = this.endpoints.cargarLicenciasGenerales(
      this.anoLicenciaSeleccionado,
      this.deporteLicenciaSeleccionado,
      fechaAsignacion
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionado === 'TODOS'
        ? 'alumnos activos'
        : `alumnos activos de ${this.deporteLicenciaSeleccionado}`;

    serviceCall.pipe(
      finalize(() => {
        this.procesandoLicenciasGenerales = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast(
          `Licencias ${this.anoLicenciaSeleccionado} asignadas a ${deporteTexto}`
        );
        this.asignado.emit();
      },
      error: (error) => {
        showErrorToast(
          error?.error?.mensaje || 'Error al asignar las licencias'
        );
      },
    });
  }

  cargarLicenciaIndividual(): void {
    if (this.procesandoLicenciaIndividual) {
      return;
    }

    if (!this.alumnoSeleccionado || !this.anoLicenciaSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un año.',
        icon: 'error',
      });
      return;
    }

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaLicenciaIndividual
    );

    const serviceCall = this.endpoints.cargarLicenciaIndividual(
      this.alumnoSeleccionado,
      this.anoLicenciaSeleccionadoIndividual,
      this.deporteLicenciaSeleccionadoIndividual,
      false,
      fechaAsignacion
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteLicenciaSeleccionadoIndividual;

    this.procesandoLicenciaIndividual = true;
    this.loadingService.show();
    serviceCall.pipe(
      finalize(() => {
        this.procesandoLicenciaIndividual = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast(
          `Licencia ${this.anoLicenciaSeleccionadoIndividual} de ${deporteTexto} cargada correctamente`
        );
      },
      error: (error) => {
        if (error.status === 409 && error.error.accion === 'confirmar') {
          Swal.fire({
            title: 'Atención',
            text: error.error.mensaje,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Sí, cargar',
            cancelButtonText: 'No, cancelar',
          }).then((result) => {
            if (result.isConfirmed) {
              this.forzarCargarLicenciaIndividual();
            }
          });
        } else {
          showErrorToast(
            error?.error?.mensaje || 'No se pudo cargar la licencia'
          );
        }
      },
    });
  }

  forzarCargarLicenciaIndividual(): void {
    if (this.procesandoLicenciaIndividual) {
      return;
    }

    if (!this.alumnoSeleccionado || !this.anoLicenciaSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un año.',
        icon: 'error',
      });
      return;
    }

    const fechaAsignacion = this.normalizarFechaAsignacion(
      this.fechaLicenciaIndividual
    );

    const serviceCall = this.endpoints.cargarLicenciaIndividual(
      this.alumnoSeleccionado,
      this.anoLicenciaSeleccionadoIndividual,
      this.deporteLicenciaSeleccionadoIndividual,
      true,
      fechaAsignacion
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteLicenciaSeleccionadoIndividual;

    this.procesandoLicenciaIndividual = true;
    this.loadingService.show();
    serviceCall.pipe(
      finalize(() => {
        this.procesandoLicenciaIndividual = false;
        this.loadingService.hide();
      })
    ).subscribe({
      next: () => {
        showSuccessToast(
          `Licencia ${this.anoLicenciaSeleccionadoIndividual} de ${deporteTexto} cargada correctamente`
        );
      },
      error: (error) => {
        showErrorToast(
          error?.error?.mensaje || 'No se pudo cargar la licencia'
        );
      },
    });
  }

  generarListadoAsistencia(accion: AccionInforme = 'descargar'): void {
    if (this.informes.ocupado || this.informes.destruido) { return; }
    if (!this.mesAnoAsistencia || this.gruposSeleccionados.length === 0) {
      Swal.fire('Error', 'Debes seleccionar un mes y al menos un día', 'error'); return;
    }
    const mesAno = this.mesAnoAsistencia;
    const [year, month] = mesAno.split('-').map(Number);
    const grupos = [...this.gruposSeleccionados];
    const solicitudes = grupos.map(grupo => this.endpoints.descargarAsistencia(year, month, grupo).pipe(
      map(blob => ({ grupo, blob })), catchError(() => of({ grupo, blob: null as Blob | null }))
    ));
    this.informes.generar(forkJoin(solicitudes).pipe(map(resultados => {
      const fallidos = resultados.filter(r => !r.blob).map(r => r.grupo);
      return {
        documentos: resultados.filter(r => r.blob).map(r => ({ blob: r.blob!,
          nombreArchivo: `Asistencia-${r.grupo}-${mesAno}.pdf`, titulo: `Asistencia ${r.grupo} — ${mesAno}` })),
        aviso: fallidos.length ? `No se pudo generar el PDF para: ${fallidos.join(', ')}` : '',
      };
    })), accion, 'No se pudo generar el listado de asistencia');
  }

  generarListadoMensualidadMensual(accion: AccionInforme = 'descargar'): void {
    if (this.informes.ocupado || this.informes.destruido) { return; }
    if (!this.mesAnoMensualidad) {
      Swal.fire('Error', 'Debes seleccionar un mes y año', 'error'); return;
    }
    const mesAno = this.mesAnoMensualidad;
    generarPdf(this.informes, this.endpoints.generarListadoMensualidadMensual(mesAno, true),
      'No se pudo generar el listado de mensualidad mensual',
      `listado_mensualidad_${this.formatearNombreMensualidad(mesAno)}.pdf`, accion,
      `Mensualidades ${this.formatearNombreMensualidad(mesAno)}`);
  }

  private formatearNombreMensualidad(mesAno: string): string {
    const [anio, mes] = mesAno.split('-');
    const meses = [
      'ENERO',
      'FEBRERO',
      'MARZO',
      'ABRIL',
      'MAYO',
      'JUNIO',
      'JULIO',
      'AGOSTO',
      'SEPTIEMBRE',
      'OCTUBRE',
      'NOVIEMBRE',
      'DICIEMBRE',
    ];
    return `${meses[Number.parseInt(mes, 10) - 1]} ${anio}`;
  }

  private obtenerFechaActualLocalYyyyMmDd(): string {
    const hoy = new Date();
    const year = hoy.getFullYear();
    const month = String(hoy.getMonth() + 1).padStart(2, '0');
    const day = String(hoy.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private normalizarFechaAsignacion(
    fecha: string | null | undefined
  ): string | null {
    const fechaLimpia = (fecha ?? '').trim();
    return fechaLimpia.length > 0 ? fechaLimpia : null;
  }

  private formatearListaDeportes(deportes: unknown): string {
    if (!Array.isArray(deportes) || deportes.length === 0) {
      return '';
    }
    const deportesValidos = deportes
      .filter((deporte) => typeof deporte === 'string' && deporte.trim().length > 0)
      .map((deporte) => getDeporteLabel(deporte as string));
    return deportesValidos.join(', ');
  }

  private construirAvisoMensualidadIncompleta(payload: any): string {
    const existentesMensualidad = this.formatearListaDeportes(payload?.existentes?.mensualidades);
    const existentesCompetidor = this.formatearListaDeportes(payload?.existentes?.tarifasCompetidor);
    const faltantesMensualidad = this.formatearListaDeportes(payload?.faltantes?.mensualidades);
    const faltantesCompetidor = this.formatearListaDeportes(payload?.faltantes?.tarifasCompetidor);

    const partes: string[] = [];
    if (this.mesAnoSeleccionadoIndividual) {
      partes.push(`<div><strong>Mes:</strong> ${this.formatearNombreMensualidad(this.mesAnoSeleccionadoIndividual)}</div>`);
    }
    if (existentesMensualidad || existentesCompetidor) {
      partes.push('<div style="margin-top: 0.5rem;"><strong>Ya tiene:</strong></div>');
      if (existentesMensualidad) {
        partes.push(`<div>Mensualidad: ${existentesMensualidad}</div>`);
      }
      if (existentesCompetidor) {
        partes.push(`<div>Cuota competidor: ${existentesCompetidor}</div>`);
      }
    }
    if (faltantesMensualidad || faltantesCompetidor) {
      partes.push('<div style="margin-top: 0.5rem;"><strong>Falta:</strong></div>');
      if (faltantesMensualidad) {
        partes.push(`<div>Mensualidad: ${faltantesMensualidad}</div>`);
      }
      if (faltantesCompetidor) {
        partes.push(`<div>Cuota competidor: ${faltantesCompetidor}</div>`);
      }
    }
    partes.push('<div style="margin-top: 0.75rem;">¿Quieres aplicar lo que falta?</div>');
    return partes.join('');
  }
}
