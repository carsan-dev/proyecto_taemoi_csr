import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TesoreriaMovimiento } from '../../../interfaces/tesoreria-movimiento';
import { TesoreriaResumen } from '../../../interfaces/tesoreria-resumen';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { PaginatedResponse } from '../../../interfaces/paginated-response';

type EstadoFiltro = 'TODOS' | 'PENDIENTES' | 'PAGADOS';

@Component({
  selector: 'app-tesoreria-cobros',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SkeletonCardComponent, PaginacionComponent],
  templateUrl: './tesoreria-cobros.component.html',
  styleUrl: './tesoreria-cobros.component.scss',
})
export class TesoreriaCobrosComponent implements OnInit, OnDestroy {
  readonly meses = [
    { value: 1, label: 'Enero' },
    { value: 2, label: 'Febrero' },
    { value: 3, label: 'Marzo' },
    { value: 4, label: 'Abril' },
    { value: 5, label: 'Mayo' },
    { value: 6, label: 'Junio' },
    { value: 7, label: 'Julio' },
    { value: 8, label: 'Agosto' },
    { value: 9, label: 'Septiembre' },
    { value: 10, label: 'Octubre' },
    { value: 11, label: 'Noviembre' },
    { value: 12, label: 'Diciembre' },
  ];

  readonly deportes = [
    { value: 'TODOS', label: 'Todos los deportes' },
    { value: 'TAEKWONDO', label: 'Taekwondo' },
    { value: 'KICKBOXING', label: 'Kickboxing' },
    { value: 'PILATES', label: 'Pilates' },
    { value: 'DEFENSA_PERSONAL_FEMENINA', label: 'Defensa Personal Femenina' },
  ];

  readonly estados: Array<{ value: EstadoFiltro; label: string }> = [
    { value: 'TODOS', label: 'Todos' },
    { value: 'PENDIENTES', label: 'Pendientes' },
    { value: 'PAGADOS', label: 'Pagados' },
  ];

  anosDisponibles: number[] = [];

  filtroMes: number | null = new Date().getMonth() + 1;
  filtroAno: number | null = new Date().getFullYear();
  filtroDeporte: string = 'TODOS';
  filtroEstado: EstadoFiltro = 'PENDIENTES';
  filtroTexto: string = '';

  cargando: boolean = true;
  exportandoInforme: boolean = false;
  procesandoCobroId: number | null = null;
  fechaCarga: Date = new Date();

  resumen: TesoreriaResumen = {
    mes: this.filtroMes,
    ano: this.filtroAno,
    deporte: 'TODOS',
    totalMovimientos: 0,
    totalPagados: 0,
    totalPendientes: 0,
    importeTotal: 0,
    importePagado: 0,
    importePendiente: 0,
    alumnosConPendientes: 0,
  };

  movimientos: TesoreriaMovimiento[] = [];
  movimientosFiltrados: TesoreriaMovimiento[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 25;
  totalPaginas: number = 0;
  totalMovimientos: number = 0;
  private readonly destroy$ = new Subject<void>();
  private readonly textoFiltroSubject = new Subject<string>();

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.configurarBusquedaTexto();
    this.inicializarPantalla();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  aplicarFiltros(): void {
    this.normalizarPeriodo();
    this.paginaActual = 1;
    this.cargarDatosTesoreria();
  }

  limpiarFiltros(): void {
    this.filtroMes = new Date().getMonth() + 1;
    this.filtroAno = this.obtenerAnoPorDefecto();
    this.filtroDeporte = 'TODOS';
    this.filtroEstado = 'PENDIENTES';
    this.filtroTexto = '';
    this.paginaActual = 1;
    this.cargarDatosTesoreria();
  }

  onTextoFiltroChange(): void {
    this.paginaActual = 1;
    this.textoFiltroSubject.next(this.filtroTexto);
  }

  marcarComoPagado(movimiento: TesoreriaMovimiento): void {
    if (movimiento.pagado || this.procesandoCobroId === movimiento.productoAlumnoId) {
      return;
    }

    Swal.fire({
      title: 'Confirmar cobro',
      text: `Marcar como pagado "${movimiento.concepto}"?`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Si, marcar pagado',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#2f855a',
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      this.procesandoCobroId = movimiento.productoAlumnoId;
      this.endpointsService
        .actualizarEstadoCobro(movimiento.productoAlumnoId, true)
        .pipe(finalize(() => (this.procesandoCobroId = null)))
        .subscribe({
          next: () => {
            showSuccessToast('Cobro actualizado como pagado');
            this.cargarDatosTesoreria();
          },
          error: () => {
            showErrorToast('No se pudo actualizar el estado del cobro');
          },
        });
    });
  }

  exportarInformeDeudasPDF(): void {
    this.exportarInformeDeudas('pdf');
  }

  exportarInformeDeudasCSV(): void {
    this.exportarInformeDeudas('csv');
  }

  getMesLabel(mes: number | null): string {
    if (!mes) {
      return 'Todos los meses';
    }
    return this.meses.find((m) => m.value === mes)?.label ?? '-';
  }

  getPeriodoLabel(mes: number | null, ano: number | null): string {
    if (ano === null) {
      return 'Totales (todos los años)';
    }

    if (mes === null) {
      return `Año ${ano}`;
    }

    return `${this.getMesLabel(mes)} ${ano}`;
  }

  getDeporteLabel(deporte: string): string {
    return (
      this.deportes.find((item) => item.value === deporte)?.label ??
      (deporte === 'GENERAL' ? 'General' : deporte)
    );
  }

  onAnoChange(): void {
    this.normalizarPeriodo();
  }

  trackByMovimientoId(_: number, movimiento: TesoreriaMovimiento): number {
    return movimiento.productoAlumnoId;
  }

  cambiarPagina(pageNumber: number): void {
    if (this.cargando || pageNumber === this.paginaActual) {
      return;
    }
    if (pageNumber < 1 || pageNumber > this.totalPaginas) {
      return;
    }
    this.paginaActual = pageNumber;
    this.cargarDatosTesoreria(false);
  }

  private cargarDatosTesoreria(recargarResumen: boolean = true): void {
    this.normalizarPeriodo();
    this.cargando = true;
    const pagado = this.convertirEstadoAPagado(this.filtroEstado);
    const movimientos$ = this.endpointsService.obtenerTesoreriaMovimientos(
      this.filtroMes,
      this.filtroAno,
      this.filtroDeporte,
      pagado,
      this.filtroTexto,
      this.paginaActual,
      this.tamanoPagina
    );

    if (!recargarResumen) {
      movimientos$
        .pipe(finalize(() => (this.cargando = false)))
        .subscribe({
          next: (respuesta) => {
            this.actualizarMovimientosDesdeRespuesta(respuesta);
            this.fechaCarga = new Date();
          },
          error: () => {
            showErrorToast('No se pudieron cargar los movimientos de tesoreria');
          },
        });
      return;
    }

    forkJoin({
      resumen: this.endpointsService.obtenerTesoreriaResumen(
        this.filtroMes,
        this.filtroAno,
        this.filtroDeporte
      ),
      movimientos: movimientos$,
    })
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: ({ resumen, movimientos: respuesta }) => {
          this.resumen = resumen;
          this.actualizarMovimientosDesdeRespuesta(respuesta);
          this.fechaCarga = new Date();
        },
        error: () => {
          showErrorToast('No se pudo cargar la informacion de tesoreria');
        },
      });
  }

  private aplicarFiltrosDesdeQueryParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const mesParam = this.parsearEntero(params.get('mes'));
    const anoParam = this.parsearEntero(params.get('ano'));
    const estadoParam = params.get('estado');
    const deporteParam = params.get('deporte');

    if (anoParam !== null && anoParam >= 1900 && anoParam <= 2200) {
      this.filtroAno = anoParam;
    }

    if (mesParam !== null && mesParam >= 1 && mesParam <= 12) {
      this.filtroMes = mesParam;
    }

    if (
      estadoParam &&
      this.estados.some((estado) => estado.value === estadoParam)
    ) {
      this.filtroEstado = estadoParam as EstadoFiltro;
    }

    if (
      deporteParam &&
      this.deportes.some((deporte) => deporte.value === deporteParam)
    ) {
      this.filtroDeporte = deporteParam;
    }

    this.normalizarPeriodo();
  }

  private exportarInformeDeudas(formato: 'pdf' | 'csv'): void {
    if (this.exportandoInforme) {
      return;
    }

    this.exportandoInforme = true;
    const pagado = this.convertirEstadoAPagado(this.filtroEstado);

    if (formato === 'pdf') {
      this.endpointsService
        .exportarTesoreriaPDF(
          this.filtroMes,
          this.filtroAno,
          this.filtroDeporte,
          pagado,
          this.filtroTexto
        )
        .pipe(finalize(() => (this.exportandoInforme = false)))
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = globalThis.URL.createObjectURL(pdfBlob);
            globalThis.open(fileURL, '_blank');
            showSuccessToast('Informe PDF de tesoreria abierto correctamente');
          },
          error: () => {
            showErrorToast('No se pudo generar el informe PDF de tesoreria');
          },
        });
      return;
    }

    this.endpointsService
      .exportarTesoreriaCSV(
        this.filtroMes,
        this.filtroAno,
        this.filtroDeporte,
        pagado,
        this.filtroTexto
      )
      .pipe(finalize(() => (this.exportandoInforme = false)))
      .subscribe({
        next: (csvBlob: Blob) => {
          const url = globalThis.URL.createObjectURL(csvBlob);
          const a = document.createElement('a');
          a.href = url;
          a.download = this.obtenerNombreCSV();
          a.click();
          globalThis.URL.revokeObjectURL(url);
          showSuccessToast('CSV de tesoreria descargado correctamente');
        },
        error: () => {
          showErrorToast('No se pudo generar el informe CSV de tesoreria');
        },
      });
  }

  private convertirEstadoAPagado(estado: EstadoFiltro): boolean | undefined {
    switch (estado) {
      case 'PENDIENTES':
        return false;
      case 'PAGADOS':
        return true;
      default:
        return undefined;
    }
  }

  private inicializarPantalla(): void {
    this.anosDisponibles = [new Date().getFullYear()];
    this.filtroAno = this.obtenerAnoPorDefecto();
    this.aplicarFiltrosDesdeQueryParams();
    this.cargarDatosTesoreria();

    this.endpointsService.obtenerTesoreriaAniosDisponibles().subscribe({
      next: (anios) => {
        this.anosDisponibles = this.normalizarAnios(anios);
        this.incluirAnoSeleccionadoEnOpciones();
      },
      error: () => {},
    });
  }

  private configurarBusquedaTexto(): void {
    this.textoFiltroSubject
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.cargarDatosTesoreria(false);
      });
  }

  private actualizarMovimientosDesdeRespuesta(
    respuesta: PaginatedResponse<TesoreriaMovimiento> | null | undefined
  ): void {
    const contenido = respuesta?.content ?? [];
    this.movimientos = contenido;
    this.totalPaginas = respuesta?.totalPages ?? 0;
    this.totalMovimientos = respuesta?.totalElements ?? contenido.length;

    if (this.totalPaginas === 0) {
      this.paginaActual = 1;
    } else if (this.paginaActual > this.totalPaginas) {
      this.paginaActual = this.totalPaginas;
    }

    this.movimientosFiltrados = [...contenido];
  }

  private normalizarAnios(anios: number[] | null | undefined): number[] {
    const aniosValidos = (anios ?? [])
      .filter((ano) => Number.isInteger(ano) && ano >= 1900 && ano <= 2200)
      .sort((a, b) => a - b);

    if (aniosValidos.length > 0) {
      return aniosValidos;
    }

    return [new Date().getFullYear()];
  }

  private obtenerAnoPorDefecto(): number | null {
    const anoActual = new Date().getFullYear();
    if (this.anosDisponibles.includes(anoActual)) {
      return anoActual;
    }

    if (this.anosDisponibles.length > 0) {
      return this.anosDisponibles[this.anosDisponibles.length - 1];
    }

    return anoActual;
  }

  private incluirAnoSeleccionadoEnOpciones(): void {
    if (this.filtroAno === null) {
      return;
    }

    if (!this.anosDisponibles.includes(this.filtroAno)) {
      this.anosDisponibles = [...this.anosDisponibles, this.filtroAno].sort((a, b) => a - b);
    }
  }

  private normalizarPeriodo(): void {
    if (this.filtroAno === null) {
      this.filtroMes = null;
      return;
    }

    if (this.filtroMes !== null && (this.filtroMes < 1 || this.filtroMes > 12)) {
      this.filtroMes = null;
    }
  }

  private parsearEntero(value: string | null): number | null {
    if (value === null || value.trim() === '') {
      return null;
    }

    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
  }

  private obtenerNombreCSV(): string {
    const anoSegment = this.filtroAno === null ? 'total' : this.filtroAno.toString();
    const mesSegment = this.filtroMes === null ? 'todos_meses' : this.filtroMes.toString().padStart(2, '0');
    const estadoSegment = this.filtroEstado.toLowerCase();
    const deporteSegment = this.filtroDeporte.toLowerCase();
    return `tesoreria_${anoSegment}_${mesSegment}_${deporteSegment}_${estadoSegment}.csv`;
  }
}
