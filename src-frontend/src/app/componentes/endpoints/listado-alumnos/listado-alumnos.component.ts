import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { calcularEdad } from '../../../utilities/calcular-edad';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { InformeModalComponent } from '../../generales/informe-modal/informe-modal.component';
import { Subject, forkJoin, of, concat } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, map } from 'rxjs/operators';
import { getGradoTextStyle, getGradoNombreParaDeporte } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { attachSwalSelectSearch } from '../../../utils/swal-search.util';

type ResumenAlumno = {
  totalDeportes: number;
  tarifaPrincipal: string;
  antiguedadPrincipal: string | null;
  licenciaTexto: string;
  licenciaClase: string;
};

@Component({
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [
    CommonModule,
    PaginacionComponent,
    FormsModule,
    RouterLink,
    InformeModalComponent,
    SkeletonCardComponent,
    SearchableSelectDirective,
  ],
  templateUrl: './listado-alumnos.component.html',
  styleUrl: './listado-alumnos.component.scss',
})
export class ListadoAlumnosComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  alumnosCompletos: any[] = []; // Full dataset for client-side filtering
  alumnosSeleccionables: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 9;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  cargando: boolean = true; // Local loading state
  usandoPaginacionCliente: boolean = false; // Track if using client-side pagination
  private readonly searchSubject = new Subject<string>();
  mesAnoSeleccionado: string = '';
  deporteSeleccionado: string = 'TODOS';
  alumnoSeleccionado: number | null = null;
  mesAnoSeleccionadoIndividual: string = '';
  deporteSeleccionadoIndividual: string = 'TODOS';
  anoLicenciaSeleccionado: number = new Date().getFullYear();
  deporteLicenciaSeleccionado: string = 'TODOS';
  anoLicenciaSeleccionadoIndividual: number = new Date().getFullYear();
  deporteLicenciaSeleccionadoIndividual: string = 'TODOS';
  licenciasTab: 'general' | 'individual' = 'general';
  mensualidadesTab: 'general' | 'individual' = 'general';
  mostrarModalInforme: boolean = false;
  modalTitle: string = '';
  opcionesInforme: Array<{ value: string; label: string }> = [];
  mesAnoAsistencia!: string;
  mesAnoMensualidad!: string;
  grupos = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes'];
  gruposSeleccionados: string[] = [];

  // Multi-sport data
  deportesPorAlumno: Map<number, AlumnoDeporteDTO[]> = new Map();

  // View mode and filters
  vistaActual: 'cards' | 'table' = 'cards'; // Default to cards view
  deporteFiltro: string = 'TODOS'; // Filter by sport
  aptoParaExamenFiltro: boolean = false; // Filter for students eligible for exam

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly alumnoService: AlumnoService,
    private readonly loadingService: LoadingService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  /**
   * Helper method to generate PDF with loading spinner
   * @param observable$ The Observable that returns the PDF blob
   * @param errorMessage Error message to show if generation fails
   */
  private generarPdfConLoading(
    observable$: import('rxjs').Observable<Blob>,
    errorMessage: string
  ): void {
    this.loadingService.show();
    observable$.pipe(finalize(() => this.loadingService.hide())).subscribe({
      next: (pdfBlob: Blob) => {
        const fileURL = URL.createObjectURL(pdfBlob);
        window.open(fileURL, '_blank');
      },
      error: () => {
        Swal.fire('Error', errorMessage, 'error');
      },
    });
  }

  ngOnInit(): void {
    // Load saved view preference
    const savedView = localStorage.getItem('listadoAlumnosView');
    if (savedView === 'cards' || savedView === 'table') {
      this.vistaActual = savedView;
    }

    // Read query parameters for filters
    this.route.queryParams.subscribe(params => {
      if (params['aptoParaExamen'] === 'true') {
        this.aptoParaExamenFiltro = true;
      }
    });

    // Calculate initial page size for cards view BEFORE loading data
    this.calcularTamanoPaginaInicial();

    // Restore pagination state if returning from another page
    this.restaurarEstadoPaginacion();

    // If filtering by sport, use client-side pagination; otherwise use backend pagination
    if (this.deporteFiltro !== 'TODOS') {
      this.obtenerTodosLosAlumnosConDeportes();
    } else {
      this.obtenerAlumnos();
    }
    this.cargarTodosLosAlumnos();

    // Setup debounced search
    this.searchSubject
      .pipe(
        debounceTime(500), // Wait 500ms after user stops typing
        distinctUntilChanged() // Only trigger if value actually changed
      )
      .subscribe(() => {
        this.paginaActual = 1;
        this.guardarEstadoPaginacion(); // Save state when searching
        this.obtenerAlumnos();
      });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  /**
   * Calculates initial page size for cards view (called once before loading data)
   */
  private calcularTamanoPaginaInicial(): void {
    if (this.vistaActual !== 'cards') {
      return;
    }
    // Offset accounts for page padding and margins (not sidebar - it's outside the container)
    const containerWidth = window.innerWidth - 120;
    const cardMinWidth = 320;
    const gap = 24; // 1.5rem
    const cardsPerRow = Math.max(1, Math.floor((containerWidth + gap) / (cardMinWidth + gap)));
    this.tamanoPagina = cardsPerRow * 2;
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    if (this.vistaActual === 'cards') {
      this.calcularTamanoPaginaDinamico(true);
    }
  }

  /**
   * Calculates dynamic page size to fill exactly 2 rows of cards
   * @param reloadData Whether to reload data after changing page size
   */
  private calcularTamanoPaginaDinamico(reloadData = false): void {
    if (this.vistaActual !== 'cards') {
      return;
    }
    // Offset accounts for page padding and margins (not sidebar - it's outside the container)
    const containerWidth = window.innerWidth - 120;
    const cardMinWidth = 320;
    const gap = 24; // 1.5rem
    const cardsPerRow = Math.max(1, Math.floor((containerWidth + gap) / (cardMinWidth + gap)));
    const newPageSize = cardsPerRow * 2;
    if (this.tamanoPagina !== newPageSize) {
      this.tamanoPagina = newPageSize;
      if (reloadData) {
        this.paginaActual = 1;
        if (this.usandoPaginacionCliente && this.alumnosCompletos.length > 0) {
          this.actualizarAlumnosPaginadosCliente();
        } else {
          this.obtenerAlumnos();
        }
      }
    }
  }

  toggleGrupoSeleccionado(grupo: string): void {
    const index = this.gruposSeleccionados.indexOf(grupo);
    if (index >= 0) {
      this.gruposSeleccionados.splice(index, 1);
    } else {
      this.gruposSeleccionados.push(grupo);
    }
    this.gruposSeleccionados = this.ordenarGruposSeleccionados(this.gruposSeleccionados);
  }

  private ordenarGruposSeleccionados(grupos: string[]): string[] {
    return [...grupos].sort((a, b) => this.grupos.indexOf(a) - this.grupos.indexOf(b));
  }

  abrirModalInforme(): void {
    this.modalTitle = 'Generar Informe';
    this.opcionesInforme = [
      { value: 'general', label: 'Informe de Alumnos por Grado General' },
      {
        value: 'taekwondo',
        label: 'Informe de Alumnos de Taekwondo por Grado',
      },
      {
        value: 'kickboxing',
        label: 'Informe de Alumnos de Kickboxing por Grado',
      },
      { value: 'licencias', label: 'Informe de Estado de Licencias' },
      {
        value: 'infantiles',
        label:
          'Informe de Alumnos Infantiles a Promocionar (Todos los deportes)',
      },
      {
        value: 'adultos',
        label: 'Informe de Alumnos Adultos a Promocionar (Todos los deportes)',
      },
      {
        value: 'infantiles-taekwondo',
        label: 'Informe de Alumnos Infantiles a Promocionar (Taekwondo)',
      },
      {
        value: 'adultos-taekwondo',
        label: 'Informe de Alumnos Adultos a Promocionar (Taekwondo)',
      },
      {
        value: 'infantiles-kickboxing',
        label: 'Informe de Alumnos Infantiles a Promocionar (Kickboxing)',
      },
      {
        value: 'adultos-kickboxing',
        label: 'Informe de Alumnos Adultos a Promocionar (Kickboxing)',
      },
      { value: 'deudas', label: 'Informe de Deudas de Alumnos' },
      {
        value: 'mensualidades',
        label: 'Informe de Mensualidades de Todos los Alumnos',
      },
      {
        value: 'mensualidades-taekwondo',
        label: 'Informe de Mensualidades de Taekwondo',
      },
      {
        value: 'mensualidades-kickboxing',
        label: 'Informe de Mensualidades de Kickboxing',
      },
      {
        value: 'competidores',
        label: 'Informe de Competidores',
      },
    ];
    this.mostrarModalInforme = true;
  }

  cerrarModalInforme(): void {
    this.mostrarModalInforme = false;
  }

  obtenerAlumnos(): void {
    this.cargando = true;
    this.usandoPaginacionCliente = false; // Reset to backend pagination
    this.endpointsService
      .obtenerAlumnos(
        this.paginaActual,
        this.tamanoPagina,
        this.nombreFiltro,
        this.mostrarInactivos,
        this.aptoParaExamenFiltro
      )
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (response) => {
          this.alumnos = response.content;
          this.totalPaginas = response.totalPages;

          // Save current state after successful load
          this.guardarEstadoPaginacion();

          // Load sports for all alumnos on this page
          this.cargarDeportesDeAlumnos();
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

  /**
   * Toggle the aptoParaExamen filter and reload data
   */
  toggleAptoParaExamenFiltro(): void {
    this.aptoParaExamenFiltro = !this.aptoParaExamenFiltro;
    this.paginaActual = 1;

    // Update URL query params without navigation
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.aptoParaExamenFiltro ? { aptoParaExamen: 'true' } : {},
      queryParamsHandling: this.aptoParaExamenFiltro ? 'merge' : ''
    });

    this.obtenerAlumnos();
    this.cargarTodosLosAlumnos();
  }

  /**
   * Clear the aptoParaExamen filter
   */
  limpiarFiltroAptoParaExamen(): void {
    if (this.aptoParaExamenFiltro) {
      this.aptoParaExamenFiltro = false;
      this.paginaActual = 1;

      // Remove query param from URL
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {}
      });

      this.obtenerAlumnos();
      this.cargarTodosLosAlumnos();
    }
  }

  /**
   * Load sports data for all alumnos on the current page
   */
  cargarDeportesDeAlumnos(): void {
    if (this.alumnos.length === 0) {
      return;
    }

    // Create an array of observables for loading each alumno's sports
    const deportesRequests = this.alumnos.map((alumno) =>
      this.alumnoService.obtenerDeportesDelAlumno(alumno.id)
    );

    // Execute all requests in parallel
    forkJoin(deportesRequests).subscribe({
      next: (deportesArrays: AlumnoDeporteDTO[][]) => {
        // Clear the existing map
        this.deportesPorAlumno.clear();

        // Store deportes for each alumno
        deportesArrays.forEach((deportes, index) => {
          const alumnoId = this.alumnos[index].id;
          this.deportesPorAlumno.set(alumnoId, deportes);
        });
      },
      error: (error) => {
        console.error('Error loading sports data:', error);
        // Continue showing the page even if sports data fails to load
      },
    });
  }

  /**
   * Load all students with their sports data for client-side filtering
   * OPTIMIZED: Uses batch endpoint to avoid N+1 HTTP requests problem
   */
  obtenerTodosLosAlumnosConDeportes(): void {
    this.cargando = true;
    this.usandoPaginacionCliente = true;

    // Use batch endpoint that returns all students with sports embedded
    this.alumnoService
      .obtenerAlumnosConDeportes(this.mostrarInactivos)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (alumnosConDeportes) => {
          // Store complete alumnos
          this.alumnosCompletos = alumnosConDeportes;

          // Extract sports data from each alumno and store in map
          this.deportesPorAlumno.clear();
          alumnosConDeportes.forEach((alumno) => {
            this.deportesPorAlumno.set(alumno.id, alumno.deportes ?? []);
          });

          // Update alumnos array with the filtered and paginated data
          this.actualizarAlumnosPaginadosCliente();

          // Save current state after successful load
          this.guardarEstadoPaginacion();
        },
        error: (error) => {
          console.error('Error loading students with sports:', error);
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Update alumnos array with client-side pagination
   */
  actualizarAlumnosPaginadosCliente(): void {
    // Apply filters to the complete dataset
    let filtrados = this.alumnosCompletos;

    // Filter by inactive status (use sport-based active status)
    if (!this.mostrarInactivos) {
      filtrados = filtrados.filter((alumno) => this.isAlumnoActivo(alumno.id));
    }

    // Filter by sport
    if (this.deporteFiltro !== 'TODOS') {
      filtrados = filtrados.filter((alumno) =>
        this.practicaDeporte(alumno.id, this.deporteFiltro)
      );
    }

    // Filter by name if needed
    if (this.nombreFiltro && this.nombreFiltro.trim()) {
      const searchTerm = this.nombreFiltro.toLowerCase().trim();
      filtrados = filtrados.filter(
        (alumno) =>
          alumno.nombre?.toLowerCase().includes(searchTerm) ||
          alumno.apellidos?.toLowerCase().includes(searchTerm)
      );
    }

    // Sort by name ascending (to match backend pagination sorting)
    filtrados = filtrados.sort((a, b) => {
      const nombreA = (a.nombre || '').toLowerCase();
      const nombreB = (b.nombre || '').toLowerCase();
      return nombreA.localeCompare(nombreB);
    });

    // Calculate total pages
    this.totalPaginas = Math.ceil(filtrados.length / this.tamanoPagina);

    // Get the current page slice
    const startIndex = (this.paginaActual - 1) * this.tamanoPagina;
    const endIndex = startIndex + this.tamanoPagina;
    this.alumnos = filtrados.slice(startIndex, endIndex);
  }

  /**
   * Get sports for a specific alumno (only active sports)
   */
  getDeportesDeAlumno(alumnoId: number): AlumnoDeporteDTO[] {
    const deportes = this.deportesPorAlumno.get(alumnoId) || [];
    // Filter to show only active sports and sort by priority
    const deportesActivos = deportes.filter((d) => d.activo !== false);
    return this.ordenarDeportesPorPrincipal(deportesActivos);
  }

  /**
   * Check if sports data has been loaded for a specific alumno
   */
  tieneDeportesCargados(alumnoId: number): boolean {
    return this.deportesPorAlumno.has(alumnoId);
  }

  /**
   * Get sports for detail view (include inactive sports for inactive alumnos)
   */
  getDeportesDetalle(alumno: any): AlumnoDeporteDTO[] {
    if (!alumno) {
      return [];
    }
    if (alumno.activo === false) {
      const deportes = this.deportesPorAlumno.get(alumno.id) || [];
      return this.ordenarDeportesPorPrincipal(deportes);
    }
    return this.getDeportesDeAlumno(alumno.id);
  }

  private ordenarDeportesPorPrincipal(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO[] {
    if (!deportes || deportes.length === 0) {
      return [];
    }
    const principal = this.getDeportePrincipalDeLista(deportes);
    return [...deportes].sort((a, b) => {
      if (principal) {
        if (a.deporte === principal.deporte) return -1;
        if (b.deporte === principal.deporte) return 1;
      }
      return this.obtenerFechaOrdenDeporte(a) - this.obtenerFechaOrdenDeporte(b);
    });
  }

  private obtenerFechaOrdenDeporte(deporte: AlumnoDeporteDTO): number {
    const fecha = deporte.fechaAltaInicial || deporte.fechaAlta;
    return fecha ? new Date(fecha).getTime() : Number.MAX_SAFE_INTEGER;
  }

  isDeportePrincipalDeLista(alumnoId: number, deporte: string): boolean {
    const deportes = this.getDeportesDeAlumno(alumnoId);
    const principal = this.getDeportePrincipalDeLista(deportes);
    return principal?.deporte === deporte;
  }

  marcarDeportePrincipal(alumnoId: number, deporte: string): void {
    const deportes = this.deportesPorAlumno.get(alumnoId) || [];
    const deporteData = deportes.find((d) => d.deporte === deporte);
    if (!deporteData || deporteData.activo === false) {
      showErrorToast('No se puede marcar como principal un deporte inactivo');
      return;
    }
    if (this.isDeportePrincipalDeLista(alumnoId, deporte)) {
      return;
    }

    this.alumnoService.establecerDeportePrincipal(alumnoId, deporte).subscribe({
      next: () => {
        const actualizados = deportes.map((d) => ({
          ...d,
          principal: d.deporte === deporte,
        }));
        this.deportesPorAlumno.set(alumnoId, actualizados);
        showSuccessToast(`Deporte principal actualizado a ${getDeporteLabel(deporte)}`);
      },
      error: (error) => {
        showErrorToast(error?.error || 'No se pudo actualizar el deporte principal');
      },
    });
  }

  /**
   * Get deporte label for display
   */
  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  /**
   * Check if alumno is active based on sport count
   * Alumno is active if has at least 1 active sport
   */
  isAlumnoActivo(alumnoId: number): boolean {
    const deportesActivos = this.getDeportesDeAlumno(alumnoId);
    return deportesActivos.length > 0;
  }

  getResumenAlumno(alumnoId: number): ResumenAlumno {
    const deportes = this.getDeportesDeAlumno(alumnoId);
    const totalDeportes = deportes.length;
    const deportePrincipal = this.getDeportePrincipalDeLista(deportes);
    const tarifaPrincipal = deportePrincipal?.tipoTarifa || 'Sin tarifa';
    const antiguedadPrincipal = deportePrincipal
      ? this.getAntiguedadDeporte(deportePrincipal)
      : null;

    let licenciaTexto = 'Sin deportes';
    let licenciaClase = 'licencia-none';
    if (deportePrincipal) {
      const requiereLicencia = deportePrincipal.deporte === 'TAEKWONDO' || deportePrincipal.deporte === 'KICKBOXING';
      if (!requiereLicencia) {
        licenciaTexto = 'N/A';
        licenciaClase = 'licencia-none';
      } else if (!deportePrincipal.tieneLicencia) {
        licenciaTexto = 'Sin licencia';
        licenciaClase = 'licencia-off';
      } else if (this.licenciaEnVigor(deportePrincipal.fechaLicencia)) {
        licenciaTexto = 'Con licencia';
        licenciaClase = 'licencia-ok';
      } else {
        licenciaTexto = 'Caducada';
        licenciaClase = 'licencia-expired';
      }
    }

    return {
      totalDeportes,
      tarifaPrincipal,
      antiguedadPrincipal,
      licenciaTexto,
      licenciaClase,
    };
  }

  private getDeportePrincipalDeLista(
    deportes: AlumnoDeporteDTO[]
  ): AlumnoDeporteDTO | null {
    if (!deportes || deportes.length === 0) {
      return null;
    }
    const principales = deportes.filter((d) => d.principal === true);
    const candidatos = principales.length > 0 ? principales : deportes;
    return [...candidatos].sort((a, b) => {
      return this.obtenerFechaOrdenDeporte(a) - this.obtenerFechaOrdenDeporte(b);
    })[0];
  }

  private licenciaEnVigor(
    fechaLicencia: Date | string | null | undefined
  ): boolean {
    if (!fechaLicencia) {
      return false;
    }
    const fechaActual = new Date();
    const fechaLicenciaDate = new Date(fechaLicencia);
    if (Number.isNaN(fechaLicenciaDate.getTime())) {
      return false;
    }
    return fechaLicenciaDate.getFullYear() >= fechaActual.getFullYear();
  }

  getAntiguedadAlumno(alumno: any): string | null {
    if (!alumno) {
      return null;
    }

    const deportes = this.getDeportesDeAlumno(alumno.id);
    if (deportes.length > 0) {
      const deportePrincipal = this.getDeportePrincipalDeLista(deportes);
      const antiguedadPrincipal = deportePrincipal
        ? this.getAntiguedadDeporte(deportePrincipal)
        : null;
      if (antiguedadPrincipal) {
        return antiguedadPrincipal;
      }
    }

    return this.calcularAntiguedadDesdeFecha(alumno.fechaAltaInicial || alumno.fechaAlta);
  }

  getAntiguedadDeporte(deporte: AlumnoDeporteDTO): string | null {
    if (!deporte) {
      return null;
    }
    if (deporte.antiguedad) {
      return deporte.antiguedad;
    }
    return this.calcularAntiguedadDesdeFecha(
      deporte.fechaAltaInicial || deporte.fechaAlta
    );
  }

  private calcularAntiguedadDesdeFecha(
    fechaBase: Date | string | null | undefined
  ): string | null {
    if (!fechaBase) {
      return null;
    }

    const fechaInicio = new Date(fechaBase);
    if (Number.isNaN(fechaInicio.getTime())) {
      return null;
    }

    const hoy = new Date();
    let anios = hoy.getFullYear() - fechaInicio.getFullYear();
    let meses = hoy.getMonth() - fechaInicio.getMonth();

    if (hoy.getDate() < fechaInicio.getDate()) {
      meses -= 1;
    }
    if (meses < 0) {
      anios -= 1;
      meses += 12;
    }
    if (anios < 0) {
      anios = 0;
      meses = 0;
    }

    const aniosStr = anios === 1 ? '1 a\u00f1o' : `${anios} a\u00f1os`;
    const mesesStr = meses === 1 ? '1 mes' : `${meses} meses`;

    if (anios > 0 && meses > 0) {
      return `${aniosStr} y ${mesesStr}`;
    }
    if (anios > 0) {
      return aniosStr;
    }
    if (meses > 0) {
      return mesesStr;
    }
    return '0 meses';
  }

  /**
   * Calculates time since the student obtained their current grade
   * Shows days when less than 1 month
   */
  getTiempoConGrado(fechaGrado: Date | string | null | undefined): string | null {
    if (!fechaGrado) {
      return null;
    }

    const fechaInicio = new Date(fechaGrado);
    if (Number.isNaN(fechaInicio.getTime())) {
      return null;
    }

    const hoy = new Date();
    let anios = hoy.getFullYear() - fechaInicio.getFullYear();
    let meses = hoy.getMonth() - fechaInicio.getMonth();
    let dias = hoy.getDate() - fechaInicio.getDate();

    if (dias < 0) {
      meses -= 1;
      const ultimoDiaMesAnterior = new Date(hoy.getFullYear(), hoy.getMonth(), 0).getDate();
      dias += ultimoDiaMesAnterior;
    }
    if (meses < 0) {
      anios -= 1;
      meses += 12;
    }
    if (anios < 0) {
      anios = 0;
      meses = 0;
      dias = 0;
    }

    const aniosStr = anios === 1 ? '1 año' : `${anios} años`;
    const mesesStr = meses === 1 ? '1 mes' : `${meses} meses`;
    const diasStr = dias === 1 ? '1 día' : `${dias} días`;

    if (anios > 0 && meses > 0) {
      return `${aniosStr} y ${mesesStr}`;
    }
    if (anios > 0) {
      return aniosStr;
    }
    if (meses > 0) {
      return mesesStr;
    }
    if (dias > 0) {
      return diasStr;
    }
    return 'Hoy';
  }

  /**
   * Toggle between cards and table view
   */
  cambiarVista(vista: 'cards' | 'table'): void {
    this.vistaActual = vista;
    localStorage.setItem('listadoAlumnosView', vista);
    // Recalculate page size when switching to cards view
    if (vista === 'cards') {
      this.calcularTamanoPaginaDinamico(true);
    }
  }

  /**
   * Filter by sport
   */
  filtrarPorDeporte(): void {
    this.paginaActual = 1;
    this.guardarEstadoPaginacion(); // Save state when filtering

    // If showing all sports, use backend pagination
    if (this.deporteFiltro === 'TODOS') {
      this.obtenerAlumnos();
    } else {
      // If filtering by sport, we need to load all students and paginate client-side
      this.obtenerTodosLosAlumnosConDeportes();
    }
  }

  /**
   * Check if alumno practices a specific sport
   */
  practicaDeporte(alumnoId: number, deporte: string): boolean {
    const deportes = this.deportesPorAlumno.get(alumnoId) || [];
    return deportes.some((d) => d.deporte === deporte);
  }

  /**
   * Check if a sport requires showing grade (not for Pilates or Defensa Personal)
   */
  deporteRequiereGrado(deporte: string): boolean {
    return deporte !== 'PILATES' && deporte !== 'DEFENSA_PERSONAL_FEMENINA';
  }

  /**
   * Check if alumno needs to show license (has at least one sport that requires license)
   */
  alumnoRequiereLicencia(alumnoId: number): boolean {
    const deportes = this.getDeportesDeAlumno(alumnoId);
    const principal = this.getDeportePrincipalDeLista(deportes);
    if (!principal) {
      return false;
    }
    return principal.deporte === 'TAEKWONDO' || principal.deporte === 'KICKBOXING';
  }

  /**
   * Get filtered alumnos - now just returns alumnos since filtering is handled elsewhere
   */
  get alumnosFiltrados(): any[] {
    // Filtering is now handled in actualizarAlumnosPaginadosCliente for client-side pagination
    // or by the backend for server-side pagination
    return this.alumnos;
  }

  cargarTodosLosAlumnos(): void {
    this.endpointsService
      .obtenerAlumnosSinPaginar(this.mostrarInactivos)
      .subscribe({
        next: (response) => {
          this.alumnosSeleccionables = response;
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

  generarInformeSeleccionado(event: {
    tipo: string;
    soloActivos: boolean;
  }): void {
    const { tipo, soloActivos } = event;

    switch (tipo) {
      case 'general':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeAlumnosPorGrado(soloActivos),
          'No se pudo generar el informe general'
        );
        break;
      case 'taekwondo':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeTaekwondoPorGrado(soloActivos),
          'No se pudo generar el informe de Taekwondo'
        );
        break;
      case 'kickboxing':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeKickboxingPorGrado(soloActivos),
          'No se pudo generar el informe de Kickboxing'
        );
        break;
      case 'licencias':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeLicencias(soloActivos),
          'No se pudo generar el informe de licencias'
        );
        break;
      case 'infantiles':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeInfantilesAPromocionar(soloActivos),
          'No se pudo generar el informe de infantiles'
        );
        break;
      case 'adultos':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeAdultosAPromocionar(soloActivos),
          'No se pudo generar el informe de adultos'
        );
        break;
      case 'infantiles-taekwondo':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeInfantilesAPromocionarTaekwondo(soloActivos),
          'No se pudo generar el informe de infantiles de Taekwondo'
        );
        break;
      case 'infantiles-kickboxing':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeInfantilesAPromocionarKickboxing(soloActivos),
          'No se pudo generar el informe de infantiles de Kickboxing'
        );
        break;
      case 'adultos-taekwondo':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeAdultosAPromocionarTaekwondo(soloActivos),
          'No se pudo generar el informe de adultos de Taekwondo'
        );
        break;
      case 'adultos-kickboxing':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeAdultosAPromocionarKickboxing(soloActivos),
          'No se pudo generar el informe de adultos de Kickboxing'
        );
        break;
      case 'deudas':
        this.generarInformeDeudas(soloActivos);
        break;
      case 'mensualidades':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeMensualidades(soloActivos),
          'No se pudo generar el informe de mensualidades'
        );
        break;
      case 'mensualidades-taekwondo':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeMensualidadesTaekwondo(soloActivos),
          'No se pudo generar el informe de mensualidades de Taekwondo'
        );
        break;
      case 'mensualidades-kickboxing':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeMensualidadesKickboxing(soloActivos),
          'No se pudo generar el informe de mensualidades de Kickboxing'
        );
        break;
      case 'competidores':
        this.generarPdfConLoading(
          this.endpointsService.generarInformeCompetidores(),
          'No se pudo generar el informe de competidores'
        );
        break;
    }
  }

  /**
   * Handles deudas report generation with format selection dialog
   */
  private generarInformeDeudas(soloActivos: boolean): void {
    Swal.fire({
      title: 'Seleccionar Formato',
      text: '¿En qué formato deseas generar el informe de deudas?',
      icon: 'question',
      showCancelButton: true,
      showDenyButton: true,
      confirmButtonText: '<i class="bi bi-file-earmark-pdf"></i> PDF',
      denyButtonText: '<i class="bi bi-file-earmark-spreadsheet"></i> CSV',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#dc3545',
      denyButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (result.isConfirmed) {
        this.generarPdfConLoading(
          this.endpointsService.generarInformeDeudas(soloActivos),
          'No se pudo generar el informe de deudas en PDF'
        );
      } else if (result.isDenied) {
        this.loadingService.show();
        this.endpointsService
          .generarInformeDeudasCSV(soloActivos)
          .pipe(finalize(() => this.loadingService.hide()))
          .subscribe({
            next: (csvBlob: Blob) => {
              const url = globalThis.URL.createObjectURL(csvBlob);
              const a = document.createElement('a');
              a.href = url;
              a.download = 'informe_deudas_alumnos.csv';
              document.body.appendChild(a);
              a.click();
              a.remove();
              globalThis.URL.revokeObjectURL(url);
              showSuccessToast('CSV descargado correctamente');
            },
            error: () => {
              Swal.fire('Error', 'No se pudo generar el informe de deudas en CSV', 'error');
            },
          });
      }
    });
  }

  calcularEdad(fechaNacimiento: string): number {
    return calcularEdad(fechaNacimiento);
  }

  getGradoStyle(tipoGrado: string): string {
    return getGradoTextStyle(tipoGrado);
  }

  getGradoNombre(tipoGrado: string | null | undefined, deporte: string | null | undefined): string {
    return getGradoNombreParaDeporte(tipoGrado, deporte);
  }

  getGradoNombrePartes(
    tipoGrado: string | null | undefined,
    deporte: string | null | undefined
  ): { texto: string; sufijo: string | null } {
    const nombre = getGradoNombreParaDeporte(tipoGrado, deporte);
    if (!nombre) {
      return { texto: 'Sin grado', sufijo: null };
    }

    const normalizado = nombre
      .replace(/[_/\\-]+/g, ' ')
      .replace(/[º°]/g, '')
      .replace(/\s+/g, ' ')
      .trim();
    const match = normalizado.match(/(?:^|\s)(\d+)(?:\s*(PUM|DAN))?$/i);
    if (!match) {
      return { texto: normalizado, sufijo: null };
    }

    const indice = match.index ?? 0;
    const texto = normalizado.slice(0, indice).trim();
    const sufijo = match[2] ? `${match[1]} ${match[2].toUpperCase()}` : match[1];

    return { texto: texto || normalizado, sufijo };
  }

  cambiarPagina(pageNumber: number): void {
    if (this.cargando || pageNumber === this.paginaActual) {
      return;
    }
    this.paginaActual = pageNumber;
    this.guardarEstadoPaginacion(); // Save state when changing pages

    if (this.usandoPaginacionCliente) {
      // Use client-side pagination
      this.actualizarAlumnosPaginadosCliente();
    } else {
      // Use backend pagination
      this.obtenerAlumnos();
    }
  }

  filtrarPorNombre(): void {
    // If using client-side pagination, filter immediately
    if (this.usandoPaginacionCliente) {
      this.paginaActual = 1;
      this.guardarEstadoPaginacion(); // Save state when filtering
      this.actualizarAlumnosPaginadosCliente();
    } else {
      // Use debounced backend search (saves state in the subscription)
      this.searchSubject.next(this.nombreFiltro);
    }
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.paginaActual = 1; // Reset pagination
    this.guardarEstadoPaginacion(); // Save state when toggling inactive filter

    if (this.usandoPaginacionCliente) {
      // Reload all data with new inactive filter
      this.obtenerTodosLosAlumnosConDeportes();
    } else {
      this.obtenerAlumnos();
    }

    this.cargarTodosLosAlumnos();
  }

  darDeAlta(alumnoId: number) {
    // Primero obtener los deportes del alumno para ver cuáles están inactivos
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        const deportesInactivos = deportes.filter(d => d.activo === false);

        if (deportesInactivos.length === 0) {
          Swal.fire({
            title: 'Información',
            text: 'El alumno ya tiene todos sus deportes activos.',
            icon: 'info',
          });
          return;
        }

        // Si solo hay un deporte inactivo, activarlo directamente
        if (deportesInactivos.length === 1) {
          this.activarDeporteDeAlumno(alumnoId, deportesInactivos[0].deporte);
          return;
        }

        // Si hay múltiples deportes inactivos, mostrar selector con opción "Todos"
        const deportesOptions = deportesInactivos
          .map(d => `<option value="${d.deporte}">${getDeporteLabel(d.deporte)}</option>`)
          .join('');

        Swal.fire({
          title: 'Selecciona el deporte a activar',
          html: `
            <select id="deporte-select" class="swal2-input">
              <option value="">Selecciona un deporte...</option>
              <option value="TODOS">✓ Todos los deportes</option>
              ${deportesOptions}
            </select>
          `,
          showCancelButton: true,
          confirmButtonText: 'Dar de Alta',
          cancelButtonText: 'Cancelar',
          didOpen: () => {
            attachSwalSelectSearch({ selectId: 'deporte-select', placeholder: 'Buscar deporte...' });
          },
          preConfirm: () => {
            const select = document.getElementById('deporte-select') as HTMLSelectElement;
            if (!select.value) {
              Swal.showValidationMessage('Por favor selecciona un deporte');
              return false;
            }
            return select.value;
          },
        }).then((result) => {
          if (result.isConfirmed && result.value) {
            if (result.value === 'TODOS') {
              this.activarTodosLosDeportes(alumnoId, deportesInactivos);
            } else {
              this.activarDeporteDeAlumno(alumnoId, result.value);
            }
          }
        });
      },
      error: () => {
        showErrorToast('Error al obtener los deportes del alumno');
      },
    });
  }

  private activarDeporteDeAlumno(alumnoId: number, deporte: string): void {
    this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte).subscribe({
      next: () => {
        showSuccessToast(`Alumno dado de alta en ${getDeporteLabel(deporte)}`);
        this.recargarDatos();
      },
      error: (error) => {
        showErrorToast(error.error || 'Error al dar de alta al alumno');
      },
    });
  }

  private activarTodosLosDeportes(alumnoId: number, deportesInactivos: AlumnoDeporteDTO[]): void {
    const activarDeportesObservables = deportesInactivos.map(deporte =>
      this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte.deporte)
    );

    concat(...activarDeportesObservables).subscribe({
      complete: () => {
        const deportesLabels = deportesInactivos.map(d => getDeporteLabel(d.deporte)).join(', ');
        showSuccessToast(`Alumno dado de alta en todos sus deportes: ${deportesLabels}`);
        this.recargarDatos();
      },
      error: (error) => {
        showErrorToast(error.error || 'Error al activar algunos deportes');
      },
    });
  }

  private recargarDatos(): void {
    if (this.usandoPaginacionCliente) {
      this.obtenerTodosLosAlumnosConDeportes();
    } else {
      this.obtenerAlumnos();
    }
  }

  darDeBaja(alumnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de baja.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de baja',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeBajaAlumno(alumnoId).subscribe({
          next: () => {
            showSuccessToast('Alumno dado de baja correctamente');
            // Reload data using the current pagination mode
            if (this.usandoPaginacionCliente) {
              this.obtenerTodosLosAlumnosConDeportes();
            } else {
              this.obtenerAlumnos();
            }
          },
          error: () => {
            showErrorToast('Error al dar de baja al alumno');
          },
        });
      }
    });
  }

  cargarMensualidadesGenerales(): void {
    if (!this.mesAnoSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un mes y año.',
        icon: 'error',
      });
      return;
    }

    // Show loading spinner
    this.loadingService.show();

    // Determine which service method to call based on sport selection
    const serviceCall =
      this.deporteSeleccionado === 'TODOS'
        ? this.endpointsService.cargarMensualidadesGenerales(
            this.mesAnoSeleccionado
          )
        : this.endpointsService.cargarMensualidadesPorDeporte(
            this.mesAnoSeleccionado,
            this.deporteSeleccionado
          );

    const deporteTexto =
      this.deporteSeleccionado === 'TODOS'
        ? 'todos los alumnos activos'
        : `alumnos activos de ${this.deporteSeleccionado}`;

    serviceCall.pipe(finalize(() => this.loadingService.hide())).subscribe({
      next: () => {
        showSuccessToast(`Mensualidades asignadas a ${deporteTexto}`);
        // Reload data using the current pagination mode
        if (this.usandoPaginacionCliente) {
          this.obtenerTodosLosAlumnosConDeportes();
        } else {
          this.obtenerAlumnos();
        }
      },
      error: () => {
        showErrorToast('Error al asignar las mensualidades');
      },
    });
  }

  cargarMensualidadIndividual(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    // Determine which service method to call based on sport selection
    const serviceCall =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? this.endpointsService.cargarMensualidadIndividual(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual
          )
        : this.endpointsService.cargarMensualidadIndividualPorDeporte(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            this.deporteSeleccionadoIndividual
          );

    const deporteTexto =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteSeleccionadoIndividual;

    serviceCall.subscribe({
      next: () => {
        showSuccessToast(`Mensualidad de ${deporteTexto} cargada correctamente`);
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
              this.forzarCargarMensualidad();
            }
          });
        } else {
          Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
        }
      },
    });
  }

  forzarCargarMensualidad(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    const serviceCall =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? this.endpointsService.cargarMensualidadIndividual(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            true
          )
        : this.endpointsService.cargarMensualidadIndividualPorDeporte(
            this.alumnoSeleccionado,
            this.mesAnoSeleccionadoIndividual,
            this.deporteSeleccionadoIndividual,
            true
          );

    const deporteTexto =
      this.deporteSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteSeleccionadoIndividual;

    serviceCall.subscribe({
      next: () => {
        showSuccessToast(`Mensualidad de ${deporteTexto} cargada correctamente`);
      },
      error: () => {
        showErrorToast('No se pudo cargar la mensualidad');
      },
    });
  }

  cargarLicenciasGenerales(): void {
    if (!this.anoLicenciaSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un año.',
        icon: 'error',
      });
      return;
    }

    this.loadingService.show();

    const serviceCall = this.endpointsService.cargarLicenciasGenerales(
      this.anoLicenciaSeleccionado,
      this.deporteLicenciaSeleccionado
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionado === 'TODOS'
        ? 'alumnos activos'
        : `alumnos activos de ${this.deporteLicenciaSeleccionado}`;

    serviceCall.pipe(finalize(() => this.loadingService.hide())).subscribe({
      next: () => {
        showSuccessToast(
          `Licencias ${this.anoLicenciaSeleccionado} asignadas a ${deporteTexto}`
        );
        if (this.usandoPaginacionCliente) {
          this.obtenerTodosLosAlumnosConDeportes();
        } else {
          this.obtenerAlumnos();
        }
      },
      error: (error) => {
        showErrorToast(
          error?.error?.mensaje || 'Error al asignar las licencias'
        );
      },
    });
  }

  cargarLicenciaIndividual(): void {
    if (!this.alumnoSeleccionado || !this.anoLicenciaSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un año.',
        icon: 'error',
      });
      return;
    }

    const serviceCall = this.endpointsService.cargarLicenciaIndividual(
      this.alumnoSeleccionado,
      this.anoLicenciaSeleccionadoIndividual,
      this.deporteLicenciaSeleccionadoIndividual
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteLicenciaSeleccionadoIndividual;

    serviceCall.subscribe({
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
    if (!this.alumnoSeleccionado || !this.anoLicenciaSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un año.',
        icon: 'error',
      });
      return;
    }

    const serviceCall = this.endpointsService.cargarLicenciaIndividual(
      this.alumnoSeleccionado,
      this.anoLicenciaSeleccionadoIndividual,
      this.deporteLicenciaSeleccionadoIndividual,
      true
    );

    const deporteTexto =
      this.deporteLicenciaSeleccionadoIndividual === 'TODOS'
        ? 'todos los deportes'
        : this.deporteLicenciaSeleccionadoIndividual;

    serviceCall.subscribe({
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

  generarListadoAsistencia(): void {
    if (!this.mesAnoAsistencia || this.gruposSeleccionados.length === 0) {
      Swal.fire('Error', 'Debes seleccionar un mes y al menos un día', 'error');
      return;
    }

    const [year, month] = this.mesAnoAsistencia.split('-').map(Number);
    const grupos = this.ordenarGruposSeleccionados(this.gruposSeleccionados);
    const solicitudes = grupos.map((grupo) =>
      this.endpointsService.descargarAsistencia(year, month, grupo).pipe(
        map((blob) => ({ grupo, blob })),
        catchError(() => of({ grupo, blob: null as Blob | null }))
      )
    );

    this.loadingService.show();
    forkJoin(solicitudes)
      .pipe(finalize(() => this.loadingService.hide()))
      .subscribe({
        next: (resultados) => {
          const fallidos: string[] = [];
          resultados.forEach((resultado) => {
            if (!resultado.blob) {
              fallidos.push(resultado.grupo);
              return;
            }
            const url = globalThis.URL.createObjectURL(resultado.blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `Asistencia-${resultado.grupo}-${this.mesAnoAsistencia}.pdf`;
            a.click();
            globalThis.URL.revokeObjectURL(url);
          });

          if (fallidos.length > 0) {
            Swal.fire(
              'Aviso',
              `No se pudo generar el PDF para: ${fallidos.join(', ')}`,
              'warning'
            );
          }
        },
        error: () => {
          Swal.fire('Error', 'No se pudo generar el listado de asistencia', 'error');
        },
      });
  }

  generarListadoMensualidadMensual() {
    if (!this.mesAnoMensualidad) {
      Swal.fire('Error', 'Debes seleccionar un mes y año', 'error');
      return;
    }

    this.generarPdfConLoading(
      this.endpointsService.generarListadoMensualidadMensual(this.mesAnoMensualidad, true),
      'No se pudo generar el listado de mensualidad mensual'
    );
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

  /**
   * Save pagination state to sessionStorage
   */
  private guardarEstadoPaginacion(): void {
    const estado = {
      paginaActual: this.paginaActual,
      nombreFiltro: this.nombreFiltro,
      mostrarInactivos: this.mostrarInactivos,
      deporteFiltro: this.deporteFiltro,
      usandoPaginacionCliente: this.usandoPaginacionCliente,
    };
    sessionStorage.setItem('listadoAlumnosEstado', JSON.stringify(estado));
  }

  /**
   * Restore pagination state from sessionStorage
   */
  private restaurarEstadoPaginacion(): void {
    const estadoGuardado = sessionStorage.getItem('listadoAlumnosEstado');
    if (estadoGuardado) {
      try {
        const estado = JSON.parse(estadoGuardado);
        this.paginaActual = estado.paginaActual || 1;
        this.nombreFiltro = estado.nombreFiltro || '';
        this.mostrarInactivos = estado.mostrarInactivos || false;
        this.deporteFiltro = estado.deporteFiltro || 'TODOS';
        this.usandoPaginacionCliente = estado.usandoPaginacionCliente || false;
      } catch (error) {
        // If parsing fails, just use default values
        console.error('Error parsing saved pagination state:', error);
      }
    }
  }
}
