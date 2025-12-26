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
import { Subject, forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { getGradoTextStyle } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';
import { LoadingService } from '../../../servicios/generales/loading.service';

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
  private searchSubject = new Subject<string>();
  mesAnoSeleccionado: string = '';
  deporteSeleccionado: string = 'TODOS';
  alumnoSeleccionado: number | null = null;
  mesAnoSeleccionadoIndividual: string = '';
  deporteSeleccionadoIndividual: string = 'TODOS';
  mostrarModalInforme: boolean = false;
  modalTitle: string = '';
  opcionesInforme: Array<{ value: string; label: string }> = [];
  mesAnoAsistencia!: string;
  mesAnoMensualidad!: string;
  grupos = ['lunes', 'martes', 'miércoles', 'jueves'];
  turnosMap: Record<string, string[]> = {
    lunes: ['17:00–18:00', '18:00–19:00'],
    martes: ['17:30–18:30', '18:30–19:30'],
    miércoles: ['16:00–17:00', '17:00–18:00'],
    jueves: ['19:00–20:00', '20:00–21:00'],
  };
  turnosDisponibles: string[] = [];
  grupoSeleccionado!: string;
  turnoSeleccionado: string | null = null;

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

    this.obtenerAlumnos();
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
    const containerWidth = window.innerWidth - 340;
    const cardMinWidth = 320;
    const gap = 24;
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
    const containerWidth = window.innerWidth - 340;
    const cardMinWidth = 320;
    const gap = 24;
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

  onGrupoChange() {
    this.turnosDisponibles = this.turnosMap[this.grupoSeleccionado] || [];
    this.turnoSeleccionado = null;
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
            if (alumno.deportes && alumno.deportes.length > 0) {
              this.deportesPorAlumno.set(alumno.id, alumno.deportes);
            }
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

    // Filter by inactive status (use 'activo' field, not 'fechaBaja')
    if (!this.mostrarInactivos) {
      filtrados = filtrados.filter((alumno) => alumno.activo === true);
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
    // Filter to show only active sports in the list
    return deportes.filter((d) => d.activo !== false);
  }

  /**
   * Get deporte label for display
   */
  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
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

    // If filtering by sport, we need to load all students and paginate client-side
    if (this.deporteFiltro !== 'TODOS') {
      this.obtenerTodosLosAlumnosConDeportes();
    } else {
      // If showing all sports, use backend pagination
      this.obtenerAlumnos();
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

  cambiarPagina(pageNumber: number): void {
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
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de alta.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de alta',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeAltaAlumno(alumnoId).subscribe({
          next: () => {
            showSuccessToast('Alumno dado de alta correctamente');
            // Reload data using the current pagination mode
            if (this.usandoPaginacionCliente) {
              this.obtenerTodosLosAlumnosConDeportes();
            } else {
              this.obtenerAlumnos();
            }
          },
          error: () => {
            showErrorToast('Error al dar de alta al alumno');
          },
        });
      }
    });
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

    // Determine which service method to call based on sport selection
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

  generarListadoAsistencia() {
    const [year, month] = this.mesAnoAsistencia.split('-').map((v) => +v);
    this.loadingService.show();
    this.endpointsService
      .descargarAsistencia(
        year,
        month,
        this.grupoSeleccionado,
        this.turnoSeleccionado!
      )
      .pipe(finalize(() => this.loadingService.hide()))
      .subscribe({
        next: (blob: Blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `Asistencia-${this.grupoSeleccionado}-${this.mesAnoAsistencia}.pdf`;
          a.click();
          globalThis.URL.revokeObjectURL(url);
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
