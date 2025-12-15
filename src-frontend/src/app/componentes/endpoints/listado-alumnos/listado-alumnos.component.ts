import { Component, OnInit, OnDestroy } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { calcularEdad } from '../../../utilities/calcular-edad';
import { RouterLink } from '@angular/router';
import { InformeModalComponent } from '../../generales/informe-modal/informe-modal.component';
import { Subject, forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { getGradoTextStyle } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';

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
  mostrarModalInforme: boolean = false;
  modalTitle: string = '';
  opcionesInforme: Array<{ value: string; label: string }> = [];
  mesAnoAsistencia!: string;
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

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly alumnoService: AlumnoService
  ) {}

  ngOnInit(): void {
    // Load saved view preference
    const savedView = localStorage.getItem('listadoAlumnosView');
    if (savedView === 'cards' || savedView === 'table') {
      this.vistaActual = savedView;
    }

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
        this.mostrarInactivos
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

    // Filter by inactive status
    if (!this.mostrarInactivos) {
      filtrados = filtrados.filter((alumno) => !alumno.fechaBaja);
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

    if (tipo === 'general') {
      this.endpointsService
        .generarInformeAlumnosPorGrado(soloActivos)
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = URL.createObjectURL(pdfBlob);
            window.open(fileURL, '_blank');
          },
          error: () => {
            Swal.fire(
              'Error',
              'No se pudo generar el informe general',
              'error'
            );
          },
        });
    } else if (tipo === 'taekwondo') {
      this.endpointsService
        .generarInformeTaekwondoPorGrado(soloActivos)
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = URL.createObjectURL(pdfBlob);
            window.open(fileURL, '_blank');
          },
          error: () => {
            Swal.fire(
              'Error',
              'No se pudo generar el informe de Taekwondo',
              'error'
            );
          },
        });
    } else if (tipo === 'kickboxing') {
      this.endpointsService
        .generarInformeKickboxingPorGrado(soloActivos)
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = URL.createObjectURL(pdfBlob);
            window.open(fileURL, '_blank');
          },
          error: () => {
            Swal.fire(
              'Error',
              'No se pudo generar el informe de Kickboxing',
              'error'
            );
          },
        });
    } else if (tipo === 'licencias') {
      this.endpointsService
        .generarInformeLicencias(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'infantiles') {
      this.endpointsService
        .generarInformeInfantilesAPromocionar(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'adultos') {
      this.endpointsService
        .generarInformeAdultosAPromocionar(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'infantiles-taekwondo') {
      this.endpointsService
        .generarInformeInfantilesAPromocionarTaekwondo(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'infantiles-kickboxing') {
      this.endpointsService
        .generarInformeInfantilesAPromocionarKickboxing(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'adultos-taekwondo') {
      this.endpointsService
        .generarInformeAdultosAPromocionarTaekwondo(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'adultos-kickboxing') {
      this.endpointsService
        .generarInformeAdultosAPromocionarKickboxing(soloActivos)
        .subscribe((blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'deudas') {
      // Show SweetAlert to choose format
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
          // Generate PDF
          this.endpointsService.generarInformeDeudas(soloActivos).subscribe({
            next: (pdfBlob: Blob) => {
              const fileURL = URL.createObjectURL(pdfBlob);
              window.open(fileURL, '_blank');
            },
            error: () => {
              Swal.fire(
                'Error',
                'No se pudo generar el informe de deudas en PDF',
                'error'
              );
            },
          });
        } else if (result.isDenied) {
          // Generate CSV
          this.endpointsService.generarInformeDeudasCSV(soloActivos).subscribe({
            next: (csvBlob: Blob) => {
              const url = globalThis.URL.createObjectURL(csvBlob);
              const a = document.createElement('a');
              a.href = url;
              a.download = 'informe_deudas_alumnos.csv';
              document.body.appendChild(a);
              a.click();
              a.remove();
              globalThis.URL.revokeObjectURL(url);
              Swal.fire({
                title: 'Descarga Completada',
                text: 'El archivo CSV se ha descargado correctamente',
                icon: 'success',
                timer: 2000,
              });
            },
            error: () => {
              Swal.fire(
                'Error',
                'No se pudo generar el informe de deudas en CSV',
                'error'
              );
            },
          });
        }
      });
    } else if (tipo === 'mensualidades') {
      this.endpointsService.generarInformeMensualidades(soloActivos).subscribe({
        next: (pdfBlob: Blob) => {
          const fileURL = URL.createObjectURL(pdfBlob);
          window.open(fileURL, '_blank');
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo generar el informe de mensualidades',
            'error'
          );
        },
      });
    } else if (tipo === 'mensualidades-taekwondo') {
      this.endpointsService
        .generarInformeMensualidadesTaekwondo(soloActivos)
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = URL.createObjectURL(pdfBlob);
            window.open(fileURL, '_blank');
          },
          error: () => {
            Swal.fire(
              'Error',
              'No se pudo generar el informe de mensualidades de Taekwondo',
              'error'
            );
          },
        });
    } else if (tipo === 'mensualidades-kickboxing') {
      this.endpointsService
        .generarInformeMensualidadesKickboxing(soloActivos)
        .subscribe({
          next: (pdfBlob: Blob) => {
            const fileURL = URL.createObjectURL(pdfBlob);
            window.open(fileURL, '_blank');
          },
          error: () => {
            Swal.fire(
              'Error',
              'No se pudo generar el informe de mensualidades de Kickboxing',
              'error'
            );
          },
        });
    }
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
            Swal.fire({
              title: 'Alumno dado de alta',
              text: 'El alumno ha sido dado de alta correctamente.',
              icon: 'success',
              timer: 2000,
            });
            // Reload data using the current pagination mode
            if (this.usandoPaginacionCliente) {
              this.obtenerTodosLosAlumnosConDeportes();
            } else {
              this.obtenerAlumnos();
            }
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de alta',
              text: 'Ha ocurrido un error al intentar dar de alta al alumno.',
              icon: 'error',
            });
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
            Swal.fire({
              title: 'Alumno dado de baja',
              text: 'El alumno ha sido dado de baja correctamente.',
              icon: 'success',
              timer: 2000,
            });
            // Reload data using the current pagination mode
            if (this.usandoPaginacionCliente) {
              this.obtenerTodosLosAlumnosConDeportes();
            } else {
              this.obtenerAlumnos();
            }
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de baja',
              text: 'Ha ocurrido un error al intentar dar de baja al alumno.',
              icon: 'error',
            });
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
        ? 'todos los alumnos'
        : `alumnos de ${this.deporteSeleccionado}`;

    serviceCall.subscribe({
      next: () => {
        Swal.fire({
          title: 'Éxito',
          text: `Las mensualidades se han asignado correctamente a ${deporteTexto}.`,
          icon: 'success',
          timer: 2000,
        });
        // Reload data using the current pagination mode
        if (this.usandoPaginacionCliente) {
          this.obtenerTodosLosAlumnosConDeportes();
        } else {
          this.obtenerAlumnos();
        }
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'Ocurrió un error al asignar las mensualidades.',
          icon: 'error',
        });
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

    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual
      )
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Mensualidad cargada correctamente.',
            icon: 'success',
            timer: 2000,
          });
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

    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual,
        true
      )
      .subscribe({
        next: () => {
          Swal.fire('Éxito', 'Mensualidad cargada correctamente.', 'success');
        },
        error: () => {
          Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
        },
      });
  }

  generarListadoAsistencia() {
    const [year, month] = this.mesAnoAsistencia.split('-').map((v) => +v);
    this.endpointsService
      .descargarAsistencia(
        year,
        month,
        this.grupoSeleccionado,
        this.turnoSeleccionado!
      )
      .subscribe((blob: Blob) => {
        const url = globalThis.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Asistencia-${this.grupoSeleccionado}-${this.mesAnoAsistencia}.pdf`;
        a.click();
        globalThis.URL.revokeObjectURL(url);
      });
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
