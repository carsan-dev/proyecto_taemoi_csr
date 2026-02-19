import { Component, ElementRef, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Subscription, concat, forkJoin, of } from 'rxjs';
import { finalize, catchError, tap } from 'rxjs/operators';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { RolFamiliar } from '../../../enums/rol-familiar';
import { TipoGrado } from '../../../enums/tipo-grado';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { Deporte, DeporteLabels, getDeporteLabel } from '../../../enums/deporte';
import { formatDate } from '../../../utilities/formatear-fecha';
import { getGradoTextStyle, getGradoNombreParaDeporte } from '../../../utilities/grado-colors';
import { calcularCategoriaPorEdad } from '../../../utilities/categoria-por-edad';
import { esSiguienteGradoRojo } from '../../../utilities/grado-progresion';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { obtenerCuantiaTarifaEstandar } from '../../../constants/tarifa.constants';
import { ScrollService } from '../../../servicios/generales/scroll.service';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { attachSwalSelectSearch } from '../../../utils/swal-search.util';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';

@Component({
  selector: 'app-editar-alumno',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    PaginacionComponent,
    SearchableSelectDirective,
  ],
  templateUrl: './editar-alumno.component.html',
  styleUrls: ['./editar-alumno.component.scss'],
})
export class EditarAlumnoComponent implements OnInit, OnDestroy {
  alumno: any = null;
  cargando: boolean = true; // Loading state - start as true to show skeleton immediately

  // Pagination - each page represents one alumno
  paginaActual: number = 1;
  totalPaginas: number = 0;
  tamanoPagina: number = 1;
  alumnosIds: number[] = []; // List of all alumno IDs for navigation

  // Subscriptions management
  private routeSubscription?: Subscription;

  // Control de formulario
  mostrarFormulario: boolean = false;
  alumnoForm: FormGroup;
  imagenPreview: string | null = null;

  // Otras propiedades
  alumnoId: number | null = null;
  tipoTarifaEditado: boolean = false;
  mostrarInactivos: boolean = false;
  observacionesDraft: string = '';
  guardandoObservaciones: boolean = false;

  // Opciones de dropdown
  tiposTarifa = Object.values(TipoTarifa);
  rolesFamiliares = Object.values(RolFamiliar);
  tiposGrado = Object.values(TipoGrado);
  deportes = [
    'TAEKWONDO',
    'KICKBOXING',
  ];
  todosLosGrados: any[] = [];
  grados: any[] = [];

  // Productos del alumno
  productosAlumno: ProductoAlumnoDTO[] = [];
  productosPaginados: ProductoAlumnoDTO[] = [];
  paginaActualProductos = 1;
  tamanoPaginaProductos = 4;
  totalPaginasProductos = 0;
  pagandoProductos = new Set<number>();

  // Convocatorias
  convocatoriasDisponibles: any[] = [];
  convocatoriasDelAlumno: any[] = [];
  proximaConvocatoriaPorDeporte: Record<string, any | null> = {};
  cantidadConvocatoriasProximasPorDeporte: Record<string, number> = {};
  convocatoriaActual: any | null = null;
  mostrarModalConvocatorias = false;
  mostrarModalEliminarConvocatorias = false;
  documentosAlumno: any[] = [];
  mostrarTodosDocumentos = false;
  readonly documentosVisiblesInicial = 5;
  mostrarModalWhatsapp = false;
  whatsappTelefonoSeleccionado: string | number | null = null;
  whatsappMensaje = '';
  whatsappDocumentosSeleccionados = new Set<number>();
  private readonly whatsappMensajePlantilla = 'Hola, te env\u00edo la documentaci\u00f3n.';

  // Multi-sport properties
  deportesDelAlumno: AlumnoDeporteDTO[] = [];
  deporteActivo: string = Deporte.TAEKWONDO;
  mostrarModalAgregarDeporte = false;
  mostrarModalActualizarGrado = false;
  nuevoDeporte: string = '';
  gradoInicialDeporte: string = '';
  fechaAltaDeporte: string = '';
  fechaAltaInicialDeporte: string = '';
  fechaGradoDeporte: string = '';
  deporteParaActualizarGrado: string = '';
  nuevoGradoActualizar: string = '';
  fechaGradoActualizar: string = '';
  private readonly deportesSinGrado = new Set(['PILATES', 'DEFENSA_PERSONAL_FEMENINA']);
  deporteParaConvocatoria: string = '';
  convocatoriasFiltradasPorDeporte: any[] = [];

  // Pending changes for batch updates (per deporte)
  pendingTarifaChanges: Map<string, {
    tipoTarifa?: string;
    cuantiaTarifa?: number;
    rolFamiliar?: string;
    grupoFamiliar?: string;
  }> = new Map();

  pendingLicenciaChanges: Map<string, {
    tieneLicencia?: boolean;
    numeroLicencia?: number;
    fechaLicencia?: string;
  }> = new Map();

  pendingCompetidorChanges: Map<string, {
    competidor?: boolean;
    fechaAltaCompeticion?: string;
    fechaAltaCompetidorInicial?: string;
    categoria?: string;
    peso?: number;
    fechaPeso?: string;
  }> = new Map();

  pendingGradoChanges: Map<string, {
    fechaGrado?: string;
  }> = new Map();

  pendingEstadisticasChanges: Map<string, {
    fechaAlta?: string;
    fechaBaja?: string;
    fechaAltaInicial?: string;
  }> = new Map();

  deportesDisponibles: Deporte[] = [];

  // Pending changes for basic info (inline editing)
  pendingBasicInfoChanges: {
    nombre?: string;
    apellidos?: string;
    direccion?: string;
    fechaNacimiento?: string;
    nif?: string;
    email?: string;
    telefono?: string;
    telefono2?: string;
    tieneDiscapacidad?: boolean;
    autorizacionWeb?: boolean;
    fechaBaja?: string;
    observaciones?: string;
  } = {};
  editingBasicInfo: boolean = false;

  // Validation errors for basic info fields
  basicInfoErrors: {
    nombre?: string;
    apellidos?: string;
    direccion?: string;
    fechaNacimiento?: string;
    nif?: string;
    email?: string;
    telefono?: string;
    telefono2?: string;
  } = {};
  private readonly basicInfoValidators: Record<string, (value: any) => string | null> = {
    nombre: (value) => (this.isBlank(value) ? 'El nombre es obligatorio' : null),
    apellidos: (value) => (this.isBlank(value) ? 'Los apellidos son obligatorios' : null),
    direccion: (value) => (this.isBlank(value) ? 'La dirección es obligatoria' : null),
    fechaNacimiento: (value) => (value ? null : 'La fecha de nacimiento es obligatoria'),
    nif: (value) => this.validateNif(value),
    email: (value) => this.validateEmail(value),
    telefono: (value) => this.validateTelefono(value, true),
    telefono2: (value) => this.validateTelefono(value, false),
  };

  // Categorias for Taekwondo competitors (must match database categoria.nombre values)
  categorias = [
    'Infantil',
    'Precadete',
    'Cadete',
    'Junior',
    'Senior'
  ];
  DeporteEnum = Deporte;
  DeporteLabels = DeporteLabels;

  // Grupos y Turnos
  gruposDelAlumno: any[] = [];
  turnosDelAlumno: any[] = [];
  todosLosGrupos: any[] = [];
  todosLosTurnos: any[] = [];
  private catalogoGruposTurnosCargado: boolean = false;

  // Para manipular el input file
  @ViewChild('inputFile', { static: false }) inputFile!: ElementRef;

  // Alumno a editar (cuando abrimos el formulario)
  alumnoEditado: any = {
    tipoTarifa: null,
    tipoGrado: null,
  };

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly alumnoService: AlumnoService,
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly location: Location,
    private readonly scrollService: ScrollService
  ) {
    this.alumnoForm = this.fb.group(
      {
        nombre: ['', Validators.required],
        apellidos: ['', Validators.required],
        direccion: ['', Validators.required],
        fechaNacimiento: ['', Validators.required],
        nif: [
          '',
          [Validators.required, Validators.pattern(String.raw`^\d{8}[A-Za-z]$`)],
        ],
        email: ['', [Validators.required, Validators.email]],
        telefono: [
          '',
          [
            Validators.required,
            Validators.pattern(String.raw`^\d+$`),
            Validators.maxLength(9),
          ],
        ],
        telefono2: [
          '',
          [
            Validators.pattern(String.raw`^\d+$`),
            Validators.maxLength(9),
          ],
        ],
        deporte: [''], // DEPRECATED: No longer required - managed via tabs
        fechaBaja: [''],
        autorizacionWeb: [true, Validators.required],
        grado: [''], // DEPRECATED: No longer required - managed via tabs
        aptoParaExamen: [false], // DEPRECATED: No longer required - managed via tabs
        tieneDiscapacidad: [false],
        observaciones: [''],
      }
    );
  }

  ngOnInit(): void {
    this.cargarGrados();
    this.cargarCatalogoGruposYTurnos();

    // Load the list of IDs first, then handle route changes
    this.cargarTodosLosAlumnosIds();

    // DEPRECATED: Deporte is now managed via tabs, not through form
    // this.alumnoForm.get('deporte')?.valueChanges.subscribe((valor) => {
    //   this.onDeporteChange(valor);
    // });

    this.alumnoForm.get('fechaNacimiento')?.valueChanges.subscribe((valor) => {
      if (valor) {
        this.obtenerGradosDisponibles(valor);
      }
    });

    // Per-sport fields (tipoTarifa, competidor, tieneLicencia) are now managed in sport tabs
    // No longer need valueChanges subscriptions in the general form
  }


  /**
   * Loads all alumno IDs to know the total count and which alumno corresponds to each page
   * Page 1 = first alumno, Page 2 = second alumno, etc.
   * @param callback Optional callback to execute after IDs are loaded
   */
  cargarTodosLosAlumnosIds(callback?: () => void): void {
    this.endpointsService.obtenerAlumnosSinPaginar(this.mostrarInactivos).subscribe({
      next: (alumnos: any[]) => {
        // Extract IDs from the alumnos array
        this.alumnosIds = alumnos.map(alumno => alumno.id);
        this.totalPaginas = this.alumnosIds.length;

        // Set up route parameter subscription (only once)
        if (!this.routeSubscription) {
          this.setupRouteSubscription();
        }

        // Execute callback if provided
        if (callback) {
          callback();
        }
      },
      error: () => {
        this.cargando = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener la lista de alumnos.',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Sets up subscription to route parameters to handle navigation
   */
  private setupRouteSubscription(): void {
    // Unsubscribe from previous subscription if it exists
    if (this.routeSubscription) {
      this.routeSubscription.unsubscribe();
    }

    // Create new subscription
    this.routeSubscription = this.route.params.subscribe((params) => {
      const idParam = +params['id'];

      if (idParam) {
        this.procesarParametroRuta(idParam);
      } else if (this.alumnosIds.length > 0) {
        // No ID in URL, navigate to first alumno by its ID
        const firstAlumnoId = this.alumnosIds[0];
        this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
      }
    });
  }

  ngOnDestroy(): void {
    // Clean up subscription
    if (this.routeSubscription) {
      this.routeSubscription.unsubscribe();
    }
  }

  /**
   * Process the route parameter - always treated as an alumno ID
   */
  private procesarParametroRuta(idParam: number): void {
    // Set loading to true and clear alumno data immediately when route changes
    this.cargando = true;
    this.alumno = null;

    // Check if this alumno ID exists in our current list
    const pageIndex = this.alumnosIds.indexOf(idParam);

    if (pageIndex === -1) {
      // ID not found in current list - might be a new alumno or inactive alumno
      // Try to load it directly to see if it exists
      this.intentarCargarAlumnoNoEncontrado(idParam);
    } else {
      // It's an alumno ID that exists in our list
      // Calculate the page number (1-indexed) for pagination display
      const pageNumber = pageIndex + 1;
      this.paginaActual = pageNumber;

      // Load this alumno
      this.cargarAlumno(idParam);
    }
  }

  /**
   * Attempts to load an alumno that wasn't found in the current alumnosIds array.
   * This handles cases where:
   * - A newly created alumno is being accessed
   * - An inactive alumno is being accessed while mostrarInactivos is false
   */
  private intentarCargarAlumnoNoEncontrado(alumnoId: number): void {
    this.endpointsService.obtenerAlumnoPorId(alumnoId).subscribe({
        next: (alumnoResponse: any) => {
          // Alumno exists! Check if we need to toggle inactive filter
          const esInactivo = alumnoResponse.activo === false || alumnoResponse.fechaBaja != null;

          if (esInactivo && !this.mostrarInactivos) {
            // This is an inactive alumno but we're not showing inactive ones
            // Toggle the filter and reload the list
            this.mostrarInactivos = true;
            this.cargarTodosLosAlumnosIds(() => {
              this.navegarAAlumno(alumnoId);
            });
          } else {
            // Alumno exists and matches our filter, but wasn't in the array
            // This means the array needs to be refreshed (e.g., newly created alumno)
            this.cargarTodosLosAlumnosIds(() => {
              this.navegarAAlumno(alumnoId);
            });
          }
        },
        error: () => {
          // Alumno doesn't exist - stop loading
          this.cargando = false;

          // Show error and navigate to first alumno if available
          Swal.fire({
            title: 'Alumno no encontrado',
            text: 'El alumno solicitado no existe.',
            icon: 'error',
            timer: 2000,
          });
          if (this.alumnosIds.length > 0) {
            const firstAlumnoId = this.alumnosIds[0];
            this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
          }
        },
      });
  }

  /**
   * Navigates to a specific alumno by ID, updating the page number accordingly
   */
  private navegarAAlumno(alumnoId: number): void {
    const pageIndex = this.alumnosIds.indexOf(alumnoId);
    if (pageIndex === -1) {
      // Still not found after refresh - navigate to first alumno if available
      if (this.alumnosIds.length > 0) {
        const firstAlumnoId = this.alumnosIds[0];
        this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
      }
    } else {
      const pageNumber = pageIndex + 1;
      this.paginaActual = pageNumber;
      this.router.navigate(['/alumnosEditar', alumnoId], { replaceUrl: true });
      this.cargarAlumno(alumnoId);
    }
  }

  cargarAlumno(id: number): void {
    this.cargando = true;
    this.endpointsService.obtenerAlumnoPorId(id)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (alumnoResponse: any) => {
          this.alumno = alumnoResponse;
          this.alumnoId = this.alumno.id;
          this.observacionesDraft = this.alumno.observaciones ?? '';

          // Obtener productos del alumno, convocatorias, etc.
          if (this.alumnoId) {
            this.cargarDocumentosAlumno(this.alumnoId);
            this.obtenerProductosAlumno(this.alumnoId);
            this.cargarConvocatoriasDelAlumno(this.alumnoId);
            this.cargarCatalogoGruposYTurnos();
            this.cargarDeportesDelAlumno(this.alumnoId);
            this.cargarGruposYTurnos(this.alumnoId);
          }
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: `No se pudo cargar el alumno con ID ${id}`,
            icon: 'error',
          });
        },
    });
  }

  private cargarDocumentosAlumno(alumnoId: number): void {
    this.endpointsService.obtenerDocumentosDeAlumno(alumnoId)
      .subscribe({
        next: (docs) => {
          this.documentosAlumno = docs;
        },
        error: () => {
          this.documentosAlumno = [];
        },
      });
  }


  /**
   * Changes to a different page (alumno) in the pagination
   * @param pageNumber The page number (1-indexed)
   */
  cambiarPagina(pageNumber: number): void {
    if (this.cargando || pageNumber === this.paginaActual) {
      return;
    }
    if (pageNumber < 1 || pageNumber > this.totalPaginas) {
      return; // Invalid page number
    }

    // Convert page number to alumno ID
    const alumnoId = this.alumnosIds[pageNumber - 1]; // Arrays are 0-indexed

    // Navigate using the alumno ID, not the page number
    this.router.navigate(['/alumnosEditar', alumnoId]);
  }

  obtenerProductosAlumno(alumnoId: number) {
    this.endpointsService.obtenerProductosDelAlumno(alumnoId).subscribe({
      next: (productos) => {
        // Reverse order to show most recent products first
        const productosOrdenados = [...productos].reverse();
        this.productosAlumno = productosOrdenados;
        this.totalPaginasProductos = Math.ceil(this.productosAlumno.length / this.tamanoPaginaProductos);
        this.cambiarPaginaProductos(1);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron obtener los productos del alumno.',
          icon: 'error',
        });
      },
    });
  }

  cambiarPaginaProductos(pageNumber: number): void {
    if (pageNumber >= 1 && pageNumber <= this.totalPaginasProductos) {
      this.paginaActualProductos = pageNumber;
      const start = (pageNumber - 1) * this.tamanoPaginaProductos;
      const end = start + this.tamanoPaginaProductos;
      this.productosPaginados = this.productosAlumno.slice(start, end);
    }
  }

  pagarProducto(producto: ProductoAlumnoDTO): void {
    if (producto.pagado || this.pagandoProductos.has(producto.id)) {
      return;
    }

    const scrollPositions = this.captureScrollPositions();

    Swal.fire({
      title: 'Marcar como pagado',
      text: `¿Quieres marcar como pagado "${producto.concepto}"?`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, marcar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#28a745',
      focusConfirm: false,
      returnFocus: false,
      didOpen: () => this.restoreScrollPositions(scrollPositions),
      didClose: () => this.restoreScrollPositions(scrollPositions),
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      this.pagandoProductos.add(producto.id);
      const productoActualizado: ProductoAlumnoDTO = {
        ...producto,
        pagado: true,
        fechaPago: producto.fechaPago ?? new Date(),
      };

      this.endpointsService
        .actualizarProductoAlumno(producto.id, productoActualizado)
        .pipe(finalize(() => this.pagandoProductos.delete(producto.id)))
        .subscribe({
          next: (respuesta) => {
            const productoFinal = respuesta ?? productoActualizado;
            this.actualizarProductoEnListas(productoFinal);
            if (this.alumnoId && this.isProductoQueAfectaGrado(productoFinal)) {
              this.cargarDeportesDelAlumno(this.alumnoId, true);
            }
            showSuccessToast('Producto marcado como pagado y grado actualizado');
          },
          error: () => {
            showErrorToast('No se pudo marcar como pagado');
          },
        });
    });
  }

  isPagandoProducto(productoId: number): boolean {
    return this.pagandoProductos.has(productoId);
  }

  private actualizarProductoEnListas(productoActualizado: ProductoAlumnoDTO): void {
    const index = this.productosAlumno.findIndex((item) => item.id === productoActualizado.id);
    if (index !== -1) {
      this.productosAlumno[index] = productoActualizado;
    }
    const indexPaginado = this.productosPaginados.findIndex((item) => item.id === productoActualizado.id);
    if (indexPaginado !== -1) {
      this.productosPaginados[indexPaginado] = productoActualizado;
    }
  }

  private isProductoQueAfectaGrado(producto: ProductoAlumnoDTO): boolean {
    const concepto = (producto?.concepto ?? '').toUpperCase();
    return concepto.includes('RECOMPENSA') || concepto.includes('DERECHO A EXAMEN');
  }

  private captureScrollPositions(): {
    windowY: number;
    scrollingElementY: number;
    documentElementY: number;
    bodyY: number;
    contentY: number;
  } {
    const doc = document;
    const content = doc.getElementById('content');
    const scrollingElement = doc.scrollingElement as HTMLElement | null;
    return {
      windowY: window.scrollY || 0,
      scrollingElementY: scrollingElement?.scrollTop ?? 0,
      documentElementY: doc.documentElement?.scrollTop ?? 0,
      bodyY: doc.body?.scrollTop ?? 0,
      contentY: content?.scrollTop ?? 0,
    };
  }

  private restoreScrollPositions(positions: {
    windowY: number;
    scrollingElementY: number;
    documentElementY: number;
    bodyY: number;
    contentY: number;
  }): void {
    const doc = document;
    const content = doc.getElementById('content');
    const scrollingElement = doc.scrollingElement as HTMLElement | null;

    if (scrollingElement) {
      scrollingElement.scrollTop = positions.scrollingElementY;
    }
    if (doc.documentElement) {
      doc.documentElement.scrollTop = positions.documentElementY;
    }
    if (doc.body) {
      doc.body.scrollTop = positions.bodyY;
    }
    if (content) {
      content.scrollTop = positions.contentY;
    }
    window.scrollTo({ top: positions.windowY, left: 0 });
  }

  /**
   * Carga la lista de convocatorias a las que pertenece el alumno.
   */
  cargarConvocatoriasDelAlumno(alumnoId: number): void {
    this.endpointsService.obtenerConvocatoriasDeAlumno(alumnoId).subscribe({
      next: (convocatorias) => {
        this.convocatoriasDelAlumno = convocatorias;
        this.recalcularResumenConvocatoriasPorDeporte();
      },
      error: () => {
        this.convocatoriasDelAlumno = [];
        this.recalcularResumenConvocatoriasPorDeporte();
      },
    });
  }

  getProximaConvocatoriaPorDeporte(deporte: string): any | null {
    const key = this.normalizarClaveDeporte(deporte);
    return this.proximaConvocatoriaPorDeporte[key] ?? null;
  }

  getCantidadConvocatoriasProximasPorDeporte(deporte: string): number {
    const key = this.normalizarClaveDeporte(deporte);
    return this.cantidadConvocatoriasProximasPorDeporte[key] ?? 0;
  }

  private recalcularResumenConvocatoriasPorDeporte(): void {
    this.proximaConvocatoriaPorDeporte = {};
    this.cantidadConvocatoriasProximasPorDeporte = {};

    const inicioHoy = new Date();
    inicioHoy.setHours(0, 0, 0, 0);
    const inicioHoyTs = inicioHoy.getTime();

    const agrupadas = new Map<string, any[]>();
    for (const convocatoria of this.convocatoriasDelAlumno ?? []) {
      const deporteKey = this.normalizarClaveDeporte(convocatoria?.deporte);
      if (!deporteKey) {
        continue;
      }
      if (!agrupadas.has(deporteKey)) {
        agrupadas.set(deporteKey, []);
      }
      agrupadas.get(deporteKey)!.push(convocatoria);
    }

    agrupadas.forEach((convocatorias, deporteKey) => {
      const proximas = convocatorias
        .map((conv) => ({
          convocatoria: conv,
          timestamp: this.obtenerTimestampConvocatoria(conv),
        }))
        .filter((item) => item.timestamp !== null && item.timestamp >= inicioHoyTs)
        .sort((a, b) => a.timestamp! - b.timestamp!);

      this.cantidadConvocatoriasProximasPorDeporte[deporteKey] = proximas.length;
      this.proximaConvocatoriaPorDeporte[deporteKey] = proximas[0]?.convocatoria ?? null;
    });
  }

  private normalizarClaveDeporte(deporte: unknown): string {
    return String(deporte ?? '').trim().toUpperCase();
  }

  private obtenerTimestampConvocatoria(convocatoria: any): number | null {
    if (!convocatoria?.fechaConvocatoria) {
      return null;
    }
    const timestamp = new Date(convocatoria.fechaConvocatoria).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
  }

  // ========== GRUPOS Y TURNOS ==========

  /**
   * Carga los grupos y turnos del alumno y todos los disponibles.
   */
  cargarGruposYTurnos(alumnoId: number): void {
    this.cargarCatalogoGruposYTurnos();

    forkJoin({
      grupos: this.endpointsService
        .obtenerGruposDelAlumnoObservable(alumnoId)
        .pipe(catchError(() => of([]))),
      turnos: this.endpointsService
        .obtenerTurnosDelAlumnoObservable(alumnoId)
        .pipe(catchError(() => of([]))),
    }).subscribe({
      next: ({ grupos, turnos }) => {
        this.gruposDelAlumno = grupos || [];
        this.turnosDelAlumno = turnos || [];
      },
      error: () => {
        this.gruposDelAlumno = [];
        this.turnosDelAlumno = [];
      },
    });
  }

  private cargarCatalogoGruposYTurnos(): void {
    if (this.catalogoGruposTurnosCargado) {
      return;
    }

    let huboError = false;
    forkJoin({
      grupos: this.endpointsService.obtenerTodosLosGrupos().pipe(
        catchError(() => {
          huboError = true;
          return of([]);
        })
      ),
      turnos: this.endpointsService.obtenerTurnosDTO().pipe(
        catchError(() => {
          huboError = true;
          return of([]);
        })
      ),
    }).subscribe({
      next: ({ grupos, turnos }) => {
        this.todosLosGrupos = grupos || [];
        this.todosLosTurnos = turnos || [];
        this.catalogoGruposTurnosCargado = !huboError;
      },
      error: () => {
        this.todosLosGrupos = [];
        this.todosLosTurnos = [];
        this.catalogoGruposTurnosCargado = false;
      },
    });
  }

  /**
   * Obtiene los grupos del alumno filtrados por deporte.
   */
  getGruposDelDeporte(deporte: string): any[] {
    return this.gruposDelAlumno.filter((g) => g.deporte === deporte);
  }

  /**
   * Obtiene los turnos del alumno filtrados por deporte.
   */
  getTurnosDelDeporte(deporte: string): any[] {
    const gruposDelDeporte = this.getGruposDelDeporte(deporte);
    const gruposIds = new Set(gruposDelDeporte.map((g) => g.id));
    return this.turnosDelAlumno.filter(
      (t) => t.grupoId && gruposIds.has(t.grupoId)
    );
  }

  /**
   * Obtiene los grupos disponibles para añadir (no asignados al alumno) filtrados por deporte.
   */
  getGruposDisponiblesDelDeporte(deporte: string): any[] {
    const gruposAsignados = new Set(this.getGruposDelDeporte(deporte).map((g) => g.id));
    return this.todosLosGrupos.filter(
      (g) => g.deporte === deporte && !gruposAsignados.has(g.id)
    );
  }

  /**
   * Obtiene los turnos disponibles para añadir (no asignados al alumno) filtrados por deporte.
   */
  getTurnosDisponiblesDelDeporte(deporte: string): any[] {
    const gruposDelDeporte = this.getGruposDelDeporte(deporte);
    const gruposIds = new Set(gruposDelDeporte.map((g) => g.id));
    const turnosAsignados = new Set(this.getTurnosDelDeporte(deporte).map((t) => t.id));

    // Solo mostrar turnos que pertenecen a grupos del alumno en este deporte
    return this.todosLosTurnos.filter(
      (t) =>
        t.grupoId &&
        gruposIds.has(t.grupoId) &&
        !turnosAsignados.has(t.id)
    );
  }

  /**
   * Formatea el nombre del turno para mostrar.
   */
  formatTurnoNombre(turno: any): string {
    if (turno.nombre) return turno.nombre;
    const parts = [];
    if (turno.diaSemana) parts.push(turno.diaSemana);
    if (turno.horaInicio) parts.push(turno.horaInicio);
    if (turno.horaFin) parts.push(`- ${turno.horaFin}`);
    return parts.join(' ') || `Turno ${turno.id}`;
  }

  /**
   * Obtiene los turnos del alumno que pertenecen a un grupo específico.
   */
  getTurnosDelGrupo(grupoId: number): any[] {
    return this.turnosDelAlumno.filter((t) => t.grupoId === grupoId);
  }

  /**
   * Obtiene los turnos disponibles para añadir de un grupo específico.
   */
  getTurnosDisponiblesDelGrupo(grupoId: number): any[] {
    const turnosAsignados = new Set(this.getTurnosDelGrupo(grupoId).map((t) => t.id));
    return this.todosLosTurnos.filter(
      (t) => t.grupoId === grupoId && !turnosAsignados.has(t.id)
    );
  }

  /**
   * Añade un grupo al alumno.
   */
  agregarGrupoAlumno(grupoId: number): void {
    if (!this.alumnoId || !grupoId) return;

    this.endpointsService.asignarAlumnoAGrupo(this.alumnoId, grupoId).subscribe({
      next: () => {
        showSuccessToast('Grupo añadido');
        this.cargarGruposYTurnos(this.alumnoId!);
      },
      error: (error) => {
        showErrorToast(error.error?.message || 'No se pudo añadir al grupo');
      },
    });
  }

  /**
   * Elimina un grupo del alumno.
   */
  eliminarGrupoAlumno(grupoId: number): void {
    if (!this.alumnoId || !grupoId) return;

    this.endpointsService.removerAlumnoDeGrupo(this.alumnoId, grupoId).subscribe({
      next: () => {
        showSuccessToast('Grupo eliminado');
        this.cargarGruposYTurnos(this.alumnoId!);
      },
      error: (error) => {
        showErrorToast(error.error?.message || 'No se pudo eliminar del grupo');
      },
    });
  }

  /**
   * Añade un turno al alumno.
   */
  agregarTurnoAlumno(turnoId: number): void {
    if (!this.alumnoId || !turnoId) return;

    this.endpointsService.asignarAlumnoATurno(this.alumnoId, turnoId).subscribe({
      next: () => {
        showSuccessToast('Turno añadido');
        this.cargarGruposYTurnos(this.alumnoId!);
      },
      error: (error) => {
        showErrorToast(error.error?.message || 'No se pudo añadir al turno');
      },
    });
  }

  /**
   * Elimina un turno del alumno.
   */
  eliminarTurnoAlumno(turnoId: number): void {
    if (!this.alumnoId || !turnoId) return;

    this.endpointsService.removerAlumnoDeTurno(this.alumnoId, turnoId).subscribe({
      next: () => {
        showSuccessToast('Turno eliminado');
        this.cargarGruposYTurnos(this.alumnoId!);
      },
      error: (error) => {
        showErrorToast(error.error?.message || 'No se pudo eliminar del turno');
      },
    });
  }

  /**
   * Abre el formulario de edición o lo cierra, en base a `this.mostrarFormulario`.
   */
  alternarFormulario(alumno: any): void {
    this.mostrarFormulario = !this.mostrarFormulario;

    if (this.mostrarFormulario) {
      // Copiamos el objeto actual del alumno en alumnoEditado
      this.alumnoEditado = { ...alumno };
      this.imagenPreview = null;
      this.tipoTarifaEditado = false;
      this.configurarFormulario(alumno);

      // No need to navigate - we're already on the correct page
      // The URL already shows the page number (e.g., /alumnosEditar/5)
    } else {
      // No need to navigate - stay on the same page when closing the form
    }
  }

  /**
   * Rellena el formulario reactivo con los datos del alumno a editar.
   */
  configurarFormulario(alumno: any): void {
    const fechaNacimiento = formatDate(alumno.fechaNacimiento);
    const fechaBaja = alumno.fechaBaja ? formatDate(alumno.fechaBaja) : '';

    // Cargar grados disponibles en base a la fecha de nacimiento
    if (fechaNacimiento) {
      this.obtenerGradosDisponibles(fechaNacimiento);
    }

    // Only set general form fields (not per-sport fields)
    this.alumnoForm.patchValue({
      nombre: alumno.nombre,
      apellidos: alumno.apellidos,
      direccion: alumno.direccion,
      nif: alumno.nif,
      email: alumno.email,
      telefono: alumno.telefono,
      telefono2: alumno.telefono2,
      fechaNacimiento,
      fechaBaja,
      autorizacionWeb: alumno.autorizacionWeb,
      tieneDiscapacidad: alumno.tieneDiscapacidad,
      observaciones: alumno.observaciones ?? '',
    });

    // Per-sport fields (tarifa, licencia, fechaAlta, etc.) are now managed in sport tabs
  }

  /**
   * Al enviar el formulario, se confirma con SweetAlert y después
   * se llama a actualizarAlumno().
   */
  confirmarYActualizarAlumno(id: number, alumno: any) {
    if (this.alumnoForm.invalid) {
      Swal.fire({
        title: 'Formulario inválido',
        text: 'Por favor, complete todos los campos requeridos correctamente',
        icon: 'error',
      });
      return;
    }

    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno se actualizará con la información introducida',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, actualizarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actualizarAlumno(id);
      }
    });
  }

  /**
   * Llamada final al servicio para actualizar en el backend.
   */
  actualizarAlumno(id: number) {
    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.alumnoForm.value));

    // Si el usuario ha eliminado la imagen (o nunca la tuvo), gestionamos el file 'null'
    if (this.alumnoEditado.fotoAlumno === null) {
      formData.append('file', 'null');
    } else if (this.alumnoEditado.fotoAlumno) {
      // Guardar el nuevo fichero
      formData.append('file', this.alumnoEditado.fotoAlumno);
    }

    this.endpointsService.actualizarAlumno(id, formData).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Bien!',
          text: '¡Alumno actualizado correctamente!',
          icon: 'success',
          timer: 2000,
        });
        this.mostrarFormulario = false;
        // Volvemos a recargar este mismo alumno
        this.cargarAlumno(id);
      },
      error: () => {
        Swal.fire({
          title: 'Error al actualizar',
          text: 'Error al actualizar al alumno',
          icon: 'error',
        });
      },
    });
  }

  onDocumentoSelected(event: any) {
    const file: File = event.target.files[0];
    if (!file) return;

    // Llamamos al servicio
    this.endpointsService.subirDocumentoAlumno(this.alumnoId!, file).subscribe({
      next: (doc) => {
        Swal.fire({
          title: 'Documento subido',
          text: 'El documento se ha subido correctamente.',
          icon: 'success',
          timer: 2000,
        });
        this.cargarDocumentosAlumno(this.alumnoId!);
      },
      error: (err) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo subir el documento',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Elimina la foto del alumno actual.
   */
  eliminarFoto(id: number) {
    this.endpointsService.eliminarImagenAlumno(id).subscribe({
      next: () => {
        this.inputFile.nativeElement.value = '';
        this.alumnoEditado.fotoAlumno = null;
        this.imagenPreview = '../../../../assets/media/default.webp';
        this.cargarAlumno(id);
        showSuccessToast('Imagen eliminada');
      },
      error: () => {
        showErrorToast('No se pudo eliminar la imagen');
      },
    });
  }

  eliminarDocumento(documentoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'Eliminarás este documento permanentemente',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarDocumentoAlumno(this.alumnoId!, documentoId)
          .subscribe({
            next: () => {
              Swal.fire({
                title: 'Eliminado',
                text: 'Documento eliminado correctamente.',
                icon: 'success',
                timer: 2000,
              });
              // Quitar el doc de la lista actual
              this.documentosAlumno = this.documentosAlumno.filter(
                (d) => d.id !== documentoId
              );
            },
            error: () => {
              Swal.fire({
                title: 'Error',
                text: 'No se pudo eliminar el documento.',
                icon: 'error',
              });
            },
          });
      }
    });
  }

  abrirDocumento(doc: any) {
    if (!this.esDocumentoPrevisualizable(doc?.tipo)) {
      this.descargarDocumento(doc);
      return;
    }
    this.descargarDocumento(doc, true);
  }

  descargarDocumento(doc: any, abrirEnNuevaPestana: boolean = false) {
    const alumnoId = this.alumno?.id ?? this.alumnoId;

    if (!doc?.id || !alumnoId) {
      Swal.fire({
        icon: 'error',
        title: 'No se pudo descargar',
        text: 'Falta la información necesaria del documento o del alumno.',
      });
      return;
    }

    const forzarDescarga = !abrirEnNuevaPestana;
    if (this.esDispositivoIOS()) {
      const downloadUrl = this.endpointsService.obtenerUrlDescargaDocumentoAlumno(
        alumnoId,
        doc.id,
        forzarDescarga
      );
      if (abrirEnNuevaPestana) {
        globalThis.window?.open(downloadUrl, '_blank', 'noopener');
      } else {
        globalThis.window?.location.assign(downloadUrl);
      }
      return;
    }

    this.endpointsService.descargarDocumentoAlumno(alumnoId, doc.id, forzarDescarga).subscribe({
      next: (blob) => {
        const url = globalThis.URL.createObjectURL(blob);

        if (abrirEnNuevaPestana) {
          globalThis.window?.open(url, '_blank', 'noopener');
          setTimeout(() => globalThis.URL.revokeObjectURL(url), 60_000);
          return;
        }

        const link = document.createElement('a');
        link.href = url;
        link.download = this.obtenerNombreDescarga(doc?.nombre, doc?.tipo);
        link.click();
        globalThis.URL.revokeObjectURL(url);
      },
      error: (error) => {
        console.error('Download error details:', error);
        Swal.fire({
          icon: 'error',
          title: 'Descarga fallida',
          text: `Error ${error.status}: ${error.statusText || 'No se pudo descargar el documento.'}`,
        });
      },
    });
  }

  private esDispositivoIOS(): boolean {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef) {
      return false;
    }

    const userAgent = navigatorRef.userAgent ?? '';
    const esIOSClasico = /iPad|iPhone|iPod/.test(userAgent);
    const esIPadOS = navigatorRef.platform === 'MacIntel' && navigatorRef.maxTouchPoints > 1;
    return esIOSClasico || esIPadOS;
  }

  private obtenerNombreDescarga(
    nombre: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const base = (nombre ?? '').trim() || 'documento';
    if (base.includes('.')) {
      return base;
    }
    const extension = this.obtenerExtensionDesdeMime(mimeType);
    return extension ? `${base}.${extension}` : base;
  }

  esDocumentoPrevisualizable(mimeType: string | null | undefined): boolean {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    if (!mime) {
      return false;
    }

    if (mime.startsWith('image/')) {
      return true;
    }

    switch (mime) {
      case 'application/pdf':
      case 'text/plain':
      case 'text/csv':
      case 'application/json':
      case 'text/xml':
      case 'application/xml':
        return true;
      default:
        return false;
    }
  }

  private obtenerExtensionDesdeMime(mimeType: string | null | undefined): string | null {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    switch (mime) {
      case 'application/pdf':
        return 'pdf';
      case 'text/csv':
        return 'csv';
      case 'text/plain':
        return 'txt';
      case 'image/jpeg':
        return 'jpg';
      case 'image/png':
        return 'png';
      case 'image/webp':
        return 'webp';
      case 'image/gif':
        return 'gif';
      case 'application/msword':
        return 'doc';
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return 'docx';
      case 'application/vnd.ms-excel':
        return 'xls';
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return 'xlsx';
      default:
        return null;
    }
  }

  /**
   * Abre el flujo de WhatsApp con el telefono del alumno.
   * Si tiene dos telefonos, muestra un selector para elegir cual usar.
   */
  abrirWhatsApp(): void {
    const telefono1 = this.alumno?.telefono;
    const telefono2 = this.alumno?.telefono2;

    if (!telefono1 && !telefono2) {
      Swal.fire({
        icon: 'warning',
        title: 'Sin telefono',
        text: 'Este alumno no tiene ningun teléfono registrado.',
      });
      return;
    }

    // Si solo hay un telefono, abrir modal directamente
    if (telefono1 && !telefono2) {
      this.abrirModalWhatsapp(telefono1);
      return;
    }

    if (!telefono1 && telefono2) {
      this.abrirModalWhatsapp(telefono2);
      return;
    }

    // Si hay dos telefonos, mostrar selector
    Swal.fire({
      title: 'Seleccionar telefono',
      text: 'A qué número deseas enviar el mensaje de WhatsApp?',
      icon: 'question',
      showCancelButton: true,
      showDenyButton: true,
      confirmButtonText: `Principal: ${telefono1}`,
      denyButtonText: `Secundario: ${telefono2}`,
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#25D366',
      denyButtonColor: '#128C7E',
    }).then((result) => {
      if (result.isConfirmed) {
        this.abrirModalWhatsapp(telefono1);
      } else if (result.isDenied) {
        this.abrirModalWhatsapp(telefono2);
      }
    });
  }

  private abrirModalWhatsapp(telefono: string | number): void {
    this.whatsappTelefonoSeleccionado = telefono;
    this.whatsappMensaje = this.buildWhatsappMensajePlantilla();
    this.whatsappDocumentosSeleccionados = new Set<number>();
    this.mostrarModalWhatsapp = true;
  }

  cerrarModalWhatsapp(): void {
    this.mostrarModalWhatsapp = false;
  }

  isWhatsappDocumentoSeleccionado(documentoId: number): boolean {
    return this.whatsappDocumentosSeleccionados.has(documentoId);
  }

  toggleWhatsappDocumento(documentoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const seleccionados = new Set(this.whatsappDocumentosSeleccionados);
    if (input.checked) {
      seleccionados.add(documentoId);
    } else {
      seleccionados.delete(documentoId);
    }
    this.whatsappDocumentosSeleccionados = seleccionados;
  }

  enviarWhatsappConDocumentos(): void {
    if (!this.whatsappTelefonoSeleccionado) {
      showErrorToast('No se pudo seleccionar teléfono');
      return;
    }

    const documentosSeleccionados = this.documentosAlumno.filter(
      (doc) => this.whatsappDocumentosSeleccionados.has(doc.id)
    );
    documentosSeleccionados.forEach((doc) => this.descargarDocumento(doc));

    const mensaje = (this.whatsappMensaje || '').trim();
    const telefono = this.whatsappTelefonoSeleccionado;
    this.cerrarModalWhatsapp();
    this.abrirWhatsAppConTelefono(telefono, mensaje);
  }

  private buildWhatsappMensajePlantilla(): string {
    const nombreCompleto = `${this.alumno?.nombre ?? ''} ${this.alumno?.apellidos ?? ''}`.trim();
    if (nombreCompleto) {
      return `Hola, te env\u00edo la documentaci\u00f3n de ${nombreCompleto}. Saludos \u{1F94B}\u{1F618}`;
    }
    return this.whatsappMensajePlantilla;
  }

  /**
   * Abre WhatsApp Web con el telefono proporcionado.
   */
  private abrirWhatsAppConTelefono(telefono: string | number, mensaje?: string): void {
    // Convertir a string y limpiar de espacios y caracteres no numericos
    let telefonoLimpio = String(telefono).replaceAll(/\D/g, '');

    // Anadir codigo de pais de Espana si no lo tiene
    if (!telefonoLimpio.startsWith('34') && telefonoLimpio.length === 9) {
      telefonoLimpio = '34' + telefonoLimpio;
    }

    let whatsappUrl = `https://wa.me/${telefonoLimpio}`;
    const texto = mensaje?.trim();
    if (texto) {
      whatsappUrl = `${whatsappUrl}?text=${encodeURIComponent(texto)}`;
    }
    window.open(whatsappUrl, '_blank');
  }

  /**
   * Devuelve los documentos visibles según el estado de mostrarTodosDocumentos.
   */
  get documentosVisibles(): any[] {
    if (this.mostrarTodosDocumentos || this.documentosAlumno.length <= this.documentosVisiblesInicial) {
      return this.documentosAlumno;
    }
    return this.documentosAlumno.slice(0, this.documentosVisiblesInicial);
  }

  /**
   * Alterna entre mostrar todos los documentos o solo los iniciales.
   * Al colapsar, hace scroll a la sección de documentos.
   */
  toggleMostrarDocumentos(): void {
    const estabaExpandido = this.mostrarTodosDocumentos;
    this.mostrarTodosDocumentos = !this.mostrarTodosDocumentos;

    // Si estaba expandido y ahora se colapsa, hacer scroll a la sección
    if (estabaExpandido) {
      setTimeout(() => {
        this.scrollService.scrollToElement('documentos-section');
      }, 50);
    }
  }

  /**
   * Para cargar los diferentes grados disponibles (según tu lógica de backend).
   */
  cargarGrados(): void {
    this.endpointsService.obtenerGrados().subscribe({
      next: (grados) => {
        this.todosLosGrados = grados;
        this.grados = grados;
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los grados',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Obtiene desde el backend los grados disponibles para la fecha de nacimiento dada.
   */
  obtenerGradosDisponibles(fechaNacimiento: string) {
    this.endpointsService
      .obtenerGradosPorFechaNacimiento(fechaNacimiento)
      .subscribe({
        next: (tiposGrado: TipoGrado[]) => {
          this.tiposGrado = tiposGrado;
          this.aplicarFiltrosGrado();
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron cargar los grados disponibles.',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Aplica filtros de deporte y edad para determinar los grados disponibles.
   */
  aplicarFiltrosGrado(): void {
    const deporteSeleccionado = this.alumnoForm.get('deporte')?.value;
    let gradosFiltrados = this.todosLosGrados;

    // First, filter by sport
    if (deporteSeleccionado === 'KICKBOXING') {
      const gradosKickboxing = new Set<string> ([
        'BLANCO',
        'AMARILLO',
        'NARANJA',
        'VERDE',
        'AZUL',
        'ROJO',
        'NEGRO_1_DAN',
        'NEGRO_2_DAN',
        'NEGRO_3_DAN',
        'NEGRO_4_DAN',
        'NEGRO_5_DAN',
      ]);
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        gradosKickboxing.has(grado.tipoGrado)
      );
    }

    // Then, filter by age if tiposGrado has values
    if (this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    this.grados = gradosFiltrados;
  }

  /**
   * Renueva la licencia del alumno.
   */
  renovarLicencia(alumnoId: number): void {
    this.endpointsService.renovarLicencia(alumnoId).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Licencia renovada!',
          text: 'Se ha añadido la licencia al perfil del alumno.',
          icon: 'success',
        });
        this.cargarAlumno(alumnoId);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo renovar la licencia.',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Controla el check «competidor». Habilita/deshabilita campos del formulario.
   */
  handleCompetidorFields(isCompetidor: boolean) {
    const pesoControl = this.alumnoForm.get('peso');
    const fechaPesoControl = this.alumnoForm.get('fechaPeso');

    if (isCompetidor) {
      pesoControl?.setValidators([Validators.required]);
      fechaPesoControl?.setValidators([Validators.required]);
      pesoControl?.enable();
      fechaPesoControl?.enable();

      if (!fechaPesoControl?.value) {
        fechaPesoControl?.setValue(this.getFechaActual());
      }
    } else {
      pesoControl?.clearValidators();
      fechaPesoControl?.clearValidators();
      pesoControl?.disable();
      fechaPesoControl?.disable();
      pesoControl?.setValue(null);
      fechaPesoControl?.setValue(null);
    }

    pesoControl?.updateValueAndValidity();
    fechaPesoControl?.updateValueAndValidity();
  }

  /**
   * Controla el check «tieneLicencia». Habilita/deshabilita campos del formulario.
   */
  handleLicenciaFields(tieneLicencia: boolean) {
    const numeroLicenciaControl = this.alumnoForm.get('numeroLicencia');
    const fechaLicenciaControl = this.alumnoForm.get('fechaLicencia');

    if (tieneLicencia) {
      numeroLicenciaControl?.setValidators([Validators.required]);
      fechaLicenciaControl?.setValidators([Validators.required]);
      numeroLicenciaControl?.enable();
      fechaLicenciaControl?.enable();
    } else {
      numeroLicenciaControl?.clearValidators();
      fechaLicenciaControl?.clearValidators();
      numeroLicenciaControl?.disable();
      fechaLicenciaControl?.disable();
      numeroLicenciaControl?.setValue(null);
      fechaLicenciaControl?.setValue(null);
    }
    numeroLicenciaControl?.updateValueAndValidity();
    fechaLicenciaControl?.updateValueAndValidity();
  }

  /**
   * Al elegir un deporte, se adaptan validaciones y campos (licencia, competidor, etc.)
   */
  onDeporteChange(deporteSeleccionado: string): void {
    this.resetFormControls();

    if (deporteSeleccionado === 'TAEKWONDO') {
      this.showAllFields();
      // Tarifas para Taekwondo
      this.tiposTarifa = [
        TipoTarifa.ADULTO,
        TipoTarifa.ADULTO_GRUPO,
        TipoTarifa.INFANTIL,
        TipoTarifa.INFANTIL_GRUPO,
        TipoTarifa.HERMANOS,
        TipoTarifa.PADRES_HIJOS,
        TipoTarifa.FAMILIAR,
      ];
      this.aplicarFiltrosGrado();
    } else if (deporteSeleccionado === 'KICKBOXING') {
      this.showAllFields();
      // Tarifas específicas para Kickboxing
      this.tiposTarifa = [
        TipoTarifa.KICKBOXING,
      ];
      this.aplicarFiltrosGrado();
    }
  }

  /**
   * Cuando el usuario selecciona un nuevo archivo de imagen.
   */
  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    if (!file || !this.alumnoId || !this.alumno) {
      return;
    }
    this.alumnoEditado.fotoAlumno = file;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      this.imagenPreview = e.target.result;
    };
    reader.readAsDataURL(file);

    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.buildAlumnoUpdatePayloadForImage()));
    formData.append('file', file);

    this.endpointsService.actualizarAlumno(this.alumnoId, formData).subscribe({
      next: () => {
        showSuccessToast('Imagen actualizada');
        this.cargarAlumno(this.alumnoId!);
      },
      error: () => {
        this.imagenPreview = null;
        showErrorToast('No se pudo actualizar la imagen');
      },
    });
  }

  private buildAlumnoUpdatePayloadForImage(): any {
    return {
      nombre: this.alumno.nombre,
      apellidos: this.alumno.apellidos,
      fechaNacimiento: this.alumno.fechaNacimiento ? formatDate(this.alumno.fechaNacimiento) : null,
      nif: this.alumno.nif,
      direccion: this.alumno.direccion,
      email: this.alumno.email,
      telefono: this.alumno.telefono,
      telefono2: this.alumno.telefono2,
      tipoTarifa: this.alumno.tipoTarifa,
      cuantiaTarifa: this.alumno.cuantiaTarifa,
      rolFamiliar: this.alumno.rolFamiliar,
      grupoFamiliar: this.alumno.grupoFamiliar,
      fechaAlta: this.alumno.fechaAlta ? formatDate(this.alumno.fechaAlta) : null,
      fechaAltaInicial: this.alumno.fechaAltaInicial ? formatDate(this.alumno.fechaAltaInicial) : null,
      fechaBaja: this.alumno.fechaBaja ? formatDate(this.alumno.fechaBaja) : null,
      autorizacionWeb: this.alumno.autorizacionWeb,
      tieneDiscapacidad: this.alumno.tieneDiscapacidad,
    };
  }

  /**
   * Abre el modal con la imagen ampliada (si hay URL disponible).
   */
  abrirModal(imagenUrl: string | null | undefined) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('imgAmpliada') as HTMLImageElement;

    // Use default image if no URL is provided
    const urlToShow = imagenUrl || '../../../../assets/media/default.webp';

    if (modal && modalImg) {
      modal.style.display = 'block';
      modalImg.src = urlToShow;
    }
  }

  cerrarModal() {
    const modal = document.getElementById('imageModal');
    if (modal) {
      modal.style.display = 'none';
    }
  }

  /**
   * Resetea validaciones y habilita campos.
   */
  resetFormControls(): void {
    this.alumnoForm.get('tipoTarifa')?.clearValidators();
    this.alumnoForm.get('grado')?.clearValidators();
    this.alumnoForm.get('competidor')?.clearValidators();
    this.alumnoForm.get('tieneLicencia')?.clearValidators();
    this.alumnoForm.get('numeroLicencia')?.clearValidators();
    this.alumnoForm.get('fechaLicencia')?.clearValidators();

    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
    this.alumnoForm.get('competidor')?.updateValueAndValidity();
    this.alumnoForm.get('tieneLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('fechaLicencia')?.updateValueAndValidity();

    // Habilita todos
    this.alumnoForm.get('tipoTarifa')?.enable();
    this.alumnoForm.get('grado')?.enable();
    this.alumnoForm.get('competidor')?.enable();
    this.alumnoForm.get('tieneLicencia')?.enable();
    this.alumnoForm.get('numeroLicencia')?.enable();
    this.alumnoForm.get('fechaLicencia')?.enable();
  }

  showAllFields(): void {
    this.alumnoForm.get('tipoTarifa')?.setValidators(Validators.required);
    this.alumnoForm.get('grado')?.setValidators(Validators.required);

    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
  }

  getGradoNombre(grado: any, deporte?: string): string {
    const deporteSeleccionado = deporte || this.alumnoForm.get('deporte')?.value;
    const tipoGrado = typeof grado === 'string' ? grado : grado?.tipoGrado;
    return getGradoNombreParaDeporte(tipoGrado, deporteSeleccionado);
  }

  getGradoStyle(tipoGrado: string): string {
    return getGradoTextStyle(tipoGrado);
  }

  /**
   * Utilidad: calcular el total = precio * cantidad
   */
  calcularTotal(precio: number, cantidad: number): number {
    return precio * cantidad;
  }

  /**
   * Lógica de reserva de plaza con selección de deporte.
   */
  reservarPlaza(alumnoId: number) {
    const anoActual = new Date().getFullYear();
    const proximoAno = anoActual + 1;

    // Build options from alumno's active sports
    const deportesActivos = this.deportesDelAlumno.filter((d: AlumnoDeporteDTO) => d.activo !== false);

    if (deportesActivos.length === 0) {
      Swal.fire({
        title: 'Sin deportes',
        text: 'El alumno no tiene deportes activos para reservar plaza.',
        icon: 'warning',
      });
      return;
    }

    // If only one sport, skip selection
    if (deportesActivos.length === 1) {
      this.confirmarReservaConDeporte(alumnoId, deportesActivos[0].deporte, anoActual, proximoAno);
      return;
    }

    // Build input options for sport selection
    const inputOptions: Record<string, string> = {
      'TODOS': 'Todos los deportes'
    };
    deportesActivos.forEach((d: AlumnoDeporteDTO) => {
      inputOptions[d.deporte] = this.getDeporteLabel(d.deporte);
    });

    Swal.fire({
      title: '¿Para qué deporte?',
      text: `Temporada: ${anoActual}/${proximoAno}`,
      icon: 'question',
      input: 'select',
      inputOptions: inputOptions,
      inputPlaceholder: 'Selecciona un deporte',
      showCancelButton: true,
      confirmButtonText: 'Continuar',
      cancelButtonText: 'Cancelar',
      inputValidator: (value) => {
        if (value) {
          return null;
        }
        return 'Debes seleccionar un deporte';
      }
    }).then((resultado) => {
      if (resultado.isConfirmed && resultado.value) {
        if (resultado.value === 'TODOS') {
          // Reserve for all sports
          this.reservarPlazaTodosDeportes(alumnoId, deportesActivos, anoActual, proximoAno);
        } else {
          this.confirmarReservaConDeporte(alumnoId, resultado.value, anoActual, proximoAno);
        }
      }
    });
  }

  /**
   * Confirms reservation for a specific sport
   */
  private confirmarReservaConDeporte(alumnoId: number, deporte: string, anoActual: number, proximoAno: number): void {
    Swal.fire({
      title: '¿Quiere añadir una reserva de plaza?',
      text: `${this.getDeporteLabel(deporte)} - Temporada: ${anoActual}/${proximoAno}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí',
      cancelButtonText: 'No',
    }).then((resultado) => {
      if (resultado.isConfirmed) {
        this.confirmarPagoReservaDeporte(alumnoId, deporte);
      }
    });
  }

  /**
   * Reserves plaza for all active sports
   */
  private reservarPlazaTodosDeportes(alumnoId: number, deportes: any[], anoActual: number, proximoAno: number): void {
    Swal.fire({
      title: '¿Reservar para todos los deportes?',
      text: `Se crearán ${deportes.length} reservas - Temporada: ${anoActual}/${proximoAno}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí',
      cancelButtonText: 'No',
    }).then((resultado) => {
      if (resultado.isConfirmed) {
        this.confirmarPagoReservaTodosDeportes(alumnoId, deportes);
      }
    });
  }

  /**
   * Confirma pago y crea reservas para todos los deportes
   */
  private confirmarPagoReservaTodosDeportes(alumnoId: number, deportes: any[]): void {
    Swal.fire({
      title: '¿Han sido abonadas las reservas?',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, pagadas',
      cancelButtonText: 'No',
    }).then((resultadoPago) => {
      if (resultadoPago.isConfirmed || resultadoPago.isDismissed) {
        const pagado = resultadoPago.isConfirmed;
        this.crearReservasParaDeportes(alumnoId, deportes, pagado);
      }
    });
  }

  /**
   * Creates reservations for all sports sequentially
   */
  private crearReservasParaDeportes(alumnoId: number, deportes: any[], pagado: boolean): void {
    let completadas = 0;
    let errores = 0;

    deportes.forEach(d => {
      this.endpointsService.reservarPlazaPorDeporte(alumnoId, d.deporte, pagado).subscribe({
        next: () => {
          completadas++;
          if (completadas + errores === deportes.length) {
            this.mostrarResultadoReservasMultiples(completadas, errores);
            this.obtenerProductosAlumno(alumnoId);
          }
        },
        error: () => {
          errores++;
          if (completadas + errores === deportes.length) {
            this.mostrarResultadoReservasMultiples(completadas, errores);
            this.obtenerProductosAlumno(alumnoId);
          }
        },
      });
    });
  }

  /**
   * Shows result of multiple reservations
   */
  private mostrarResultadoReservasMultiples(completadas: number, errores: number): void {
    if (errores === 0) {
      Swal.fire({
        title: 'Reservas creadas',
        text: `Se han creado ${completadas} reservas correctamente.`,
        icon: 'success',
        timer: 2000,
      });
    } else {
      Swal.fire({
        title: 'Reservas parciales',
        text: `Completadas: ${completadas}, Errores: ${errores}`,
        icon: 'warning',
      });
    }
  }

  /**
   * Confirma si la reserva ha sido pagada (por deporte)
   */
  private confirmarPagoReservaDeporte(alumnoId: number, deporte: string): void {
    Swal.fire({
      title: '¿Ha sido abonada la reserva?',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, pagada',
      cancelButtonText: 'No',
    }).then((resultadoPago) => {
      if (resultadoPago.isConfirmed || resultadoPago.isDismissed) {
        const pagado = resultadoPago.isConfirmed;
        this.intentarCrearReservaDeporte(alumnoId, deporte, pagado);
      }
    });
  }

  /**
   * Intenta crear la reserva por deporte
   */
  private intentarCrearReservaDeporte(alumnoId: number, deporte: string, pagado: boolean): void {
    this.endpointsService.reservarPlazaPorDeporte(alumnoId, deporte, pagado).subscribe({
      next: () => {
        this.mostrarReservaExitosa(deporte);
        this.obtenerProductosAlumno(alumnoId);
      },
      error: (err) => {
        this.manejarErrorReservaDeporte(err, alumnoId, deporte, pagado);
      },
    });
  }

  /**
   * Maneja errores al crear la reserva por deporte
   */
  private manejarErrorReservaDeporte(err: any, alumnoId: number, deporte: string, pagado: boolean): void {
    if (err.status === 409) {
      this.confirmarReservaForzadaDeporte(alumnoId, deporte, pagado);
    } else {
      this.mostrarErrorReserva();
    }
  }

  /**
   * Confirma si desea forzar la creación de la reserva por deporte
   */
  private confirmarReservaForzadaDeporte(alumnoId: number, deporte: string, pagado: boolean): void {
    Swal.fire({
      title: 'Reserva existente',
      text: `Ya existe una reserva de plaza para ${this.getDeporteLabel(deporte)}. ¿Quieres proceder de todas formas?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, proceder',
      cancelButtonText: 'No',
    }).then((respuesta) => {
      if (respuesta.isConfirmed) {
        this.crearReservaForzadaDeporte(alumnoId, deporte, pagado);
      }
    });
  }

  /**
   * Crea la reserva forzada por deporte
   */
  private crearReservaForzadaDeporte(alumnoId: number, deporte: string, pagado: boolean): void {
    this.endpointsService.reservarPlazaPorDeporte(alumnoId, deporte, pagado, true).subscribe({
      next: () => {
        this.mostrarReservaExitosa(deporte);
        this.obtenerProductosAlumno(alumnoId);
      },
      error: () => {
        this.mostrarErrorReserva();
      },
    });
  }

  /**
   * Muestra mensaje de éxito al crear la reserva
   */
  private mostrarReservaExitosa(deporte?: string): void {
    const texto = deporte
      ? `La reserva de plaza para ${this.getDeporteLabel(deporte)} ha sido añadida correctamente.`
      : 'La reserva de plaza ha sido añadida correctamente.';
    Swal.fire({
      title: 'Reserva creada',
      text: texto,
      icon: 'success',
      timer: 2000,
    });
  }

  /**
   * Muestra mensaje de error al crear la reserva
   */
  private mostrarErrorReserva(): void {
    Swal.fire({
      title: 'Error',
      text: 'No se pudo crear la reserva.',
      icon: 'error',
    });
  }

  /**
   * Valida si la licencia está en vigor comparando años.
   */
  licenciaEnVigor(fechaLicencia: Date): boolean {
    const fechaActual = new Date();
    const fechaLicenciaDate = new Date(fechaLicencia);
    return fechaLicenciaDate.getFullYear() >= fechaActual.getFullYear();
  }

  /**
   * Abre el modal para seleccionar el tipo de convocatoria (examen normal o por recompensa).
   */
  seleccionarTipoConvocatoria(alumno: any): void {
    this.abrirModalConvocatoriasConTipo(alumno, false);
  }

  abrirModalConvocatoriasConTipo(alumno: any, porRecompensa: boolean): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDisponibles(alumno);
    this.mostrarModalConvocatorias = true;
    this.alumnoEditado.porRecompensa = porRecompensa;
  }

  cargarConvocatoriasDisponibles(alumno: any): void {
    const deporte = this.deporteParaConvocatoria || this.deporteActivo || this.deportesDelAlumno[0]?.deporte;
    if (!deporte) {
      this.convocatoriasDisponibles = [];
      return;
    }
    this.endpointsService.obtenerConvocatorias(deporte).subscribe({
      next: (convocatorias) => {
        const hoy = new Date();
        this.convocatoriaActual = convocatorias.find((conv: any) => {
          const fechaConvocatoria = new Date(conv.fechaConvocatoria);
          return (
            fechaConvocatoria.getFullYear() === hoy.getFullYear() &&
            fechaConvocatoria.getMonth() === hoy.getMonth() &&
            fechaConvocatoria.getDate() === hoy.getDate()
          );
        });
        this.convocatoriasDisponibles = convocatorias.filter(
          (convocatoria: any) => convocatoria.id !== this.convocatoriaActual?.id
        );
      },
      error: () => {
        console.error('Error al obtener convocatorias disponibles');
      },
    });
  }

  agregarAConvocatoriaEspecifica(convocatoria: any): void {
    if (!this.alumnoId) return;

    const porRecompensa = false;
    this.endpointsService
      .agregarAlumnoAConvocatoria(this.alumnoId, convocatoria.id, porRecompensa)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Alumno agregado',
            text: 'El alumno ha sido agregado correctamente a la convocatoria.',
            icon: 'success',
            timer: 2000,
          });
          this.cerrarModalConvocatorias();
          if (this.alumnoId) {
            this.cargarConvocatoriasDelAlumno(this.alumnoId);
            this.obtenerProductosAlumno(this.alumnoId);
          }
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error,
            icon: 'error',
          });
        },
      });
  }

  eliminarDeConvocatoriaSeleccionada(convocatoria: any): void {
    if (!this.alumnoId) return;

    this.cerrarModalEliminarConvocatorias();
    Swal.fire({
      title: '¿Estás seguro?',
      text: `Eliminarás al alumno de la convocatoria de ${convocatoria.deporte}
             del ${formatDate(convocatoria.fechaConvocatoria)}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarAlumnoDeConvocatoria(this.alumnoId!, convocatoria.id)
          .subscribe({
            next: () => {
              Swal.fire({
                title: 'Eliminado',
                text: 'El alumno ha sido eliminado de la convocatoria.',
                icon: 'success',
                timer: 2000,
              });
              this.cargarConvocatoriasDelAlumno(this.alumnoId!);
              this.obtenerProductosAlumno(this.alumnoId!);
            },
            error: () => {
              Swal.fire({
                title: 'Error',
                text: 'No se pudo eliminar al alumno de la convocatoria.',
                icon: 'error',
              });
            },
          });
      }
    });
  }

  abrirModalConvocatorias(alumno: any): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDisponibles(alumno);
    this.mostrarModalConvocatorias = true;
  }

  cerrarModalConvocatorias(): void {
    this.mostrarModalConvocatorias = false;
  }

  abrirModalEliminarConvocatorias(alumno: any): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDelAlumno(alumno.id);
    this.mostrarModalEliminarConvocatorias = true;
  }

  cerrarModalEliminarConvocatorias(): void {
    this.mostrarModalEliminarConvocatorias = false;
  }

  /**
   * Asigna el valor de la cuantía de la tarifa según el tipoTarifa.
   */
  asignarCuantiaTarifa(tipoTarifa: TipoTarifa): number {
    switch (tipoTarifa) {
      case TipoTarifa.ADULTO:
        return 30;
      case TipoTarifa.ADULTO_GRUPO:
        return 20;
      case TipoTarifa.FAMILIAR:
        return 0;
      case TipoTarifa.INFANTIL:
        return 28;
      case TipoTarifa.INFANTIL_GRUPO:
        return 20;
      case TipoTarifa.HERMANOS:
        return 26;
      case TipoTarifa.PADRES_HIJOS:
        // For PADRES_HIJOS, the price depends on the rol
        { const rolFamiliar = this.alumnoForm.get('rolFamiliar')?.value;
        if (rolFamiliar === RolFamiliar.PADRE || rolFamiliar === RolFamiliar.MADRE) {
          return 28;
        } else if (rolFamiliar === RolFamiliar.HIJO || rolFamiliar === RolFamiliar.HIJA) {
          return 26;
        }
        return 0; }
      case TipoTarifa.KICKBOXING:
        return 30;
      default:
        throw new Error('Tipo de descuento no válido');
    }
  }

  // onTipoTarifaChange method removed - now handled by per-sport batch updates

  /**
   * Valida que la fecha de baja sea posterior a la fecha de alta.
   */
  fechaBajaPosteriorAFechaAltaValidator(formGroup: AbstractControl) {
    const fechaAlta = formGroup.get('fechaAlta')!.value;
    const fechaBaja = formGroup.get('fechaBaja')!.value;

    if (!fechaAlta || !fechaBaja) {
      return null;
    }

    const fechaAltaDate = new Date(fechaAlta);
    const fechaBajaDate = new Date(fechaBaja);

    return fechaBajaDate > fechaAltaDate
      ? null
      : { fechaBajaAnteriorAFechaAlta: true };
  }

  /**
   * Valida que la fecha de nacimiento sea anterior a la fecha de alta.
   */
  fechaNacimientoPosteriorAFechaAltaValidator(formGroup: AbstractControl) {
    const fechaAlta = formGroup.get('fechaAlta')!.value;
    const fechaNacimiento = formGroup.get('fechaNacimiento')!.value;

    if (!fechaAlta || !fechaNacimiento) {
      return null;
    }

    const fechaAltaDate = new Date(fechaAlta);
    const fechaNacimientoDate = new Date(fechaNacimiento);

    return fechaAltaDate > fechaNacimientoDate
      ? null
      : { fechaAltaAnteriorAFechaNacimiento: true };
  }

  private getFechaActual(): string {
    const today = new Date();
    return today.toISOString().split('T')[0];
  }

  navegarAGestionProductos(alumnoId: number) {
    this.router.navigate(['/alumnos', alumnoId, 'productos']);
  }

  /**
   * Botón «Volver» que simplemente navega hacia atrás en el historial.
   */
  volver() {
    this.location.back();
  }

  /**
   * Toggle showing inactive alumnos and reload the list
   */
  alternarInactivos(): void {
    const alumnoIdActual = this.alumno?.id;
    this.mostrarInactivos = !this.mostrarInactivos;

    // Reload the IDs list with new filter
    this.cargarTodosLosAlumnosIds(() => {
      // If we have a current alumno, try to stay on it
      if (alumnoIdActual) {
        const pageIndex = this.alumnosIds.indexOf(alumnoIdActual);
        if (pageIndex === -1) {
          // Alumno not in filtered list, navigate to first alumno
          if (this.alumnosIds.length > 0) {
            const firstAlumnoId = this.alumnosIds[0];
            this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
          }
        } else {
          // Alumno still in list, update page number and stay on this alumno
          const pageNumber = pageIndex + 1;
          this.paginaActual = pageNumber;
          this.router.navigate(['/alumnosEditar', alumnoIdActual], { replaceUrl: true });
        }
      }
    });
  }

  // ==================== MULTI-SPORT METHODS ====================

  /**
   * Load all sports for the current alumno
   * @param preserveActiveTab If true, keeps the current active tab after reload
   */
  cargarDeportesDelAlumno(alumnoId: number, preserveActiveTab: boolean = false): void {
    // Save the current active sport before reloading
    const currentActiveDeporte = this.deporteActivo;

    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        const deportesOrdenados = this.ordenarDeportesPorPrincipal(deportes);
        this.deportesDelAlumno = deportesOrdenados;
        const deportesActivos = deportesOrdenados.filter(d => d.activo !== false);
        const deportesPreferidos = deportesActivos.length > 0 ? deportesActivos : deportesOrdenados;
        const deportePorDefecto = deportesPreferidos[0]?.deporte;

        // Set active sport
        if (deportes.length > 0) {
          if (preserveActiveTab && currentActiveDeporte) {
            // Try to preserve the current active tab
            const stillExists = deportes.find(d => d.deporte === currentActiveDeporte);
            if (stillExists) {
              this.deporteActivo = currentActiveDeporte;
            } else {
              // Current tab no longer exists, fall back to first available
              this.deporteActivo = deportePorDefecto;
            }
          } else {
            // Default behavior: set to first available
            this.deporteActivo = deportePorDefecto;
          }
        }

        // Calculate available sports to add
        this.deportesDisponibles = this.getDeportesDisponiblesParaAgregar();
      },
      error: () => {
        this.deportesDelAlumno = [];
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los deportes del alumno',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Get list of sports available to add (not already assigned)
   */
  getDeportesDisponiblesParaAgregar(): Deporte[] {
    const deportesAsignados = new Set(this.deportesDelAlumno.map(d => d.deporte));
    return Object.values(Deporte).filter(d => !deportesAsignados.has(d));
  }

  private ordenarDeportesPorPrincipal(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO[] {
    if (!deportes || deportes.length === 0) {
      return [];
    }
    const principal = this.seleccionarDeportePrincipal(deportes);
    return [...deportes].sort((a, b) => {
      if (principal) {
        if (a.deporte === principal.deporte) return -1;
        if (b.deporte === principal.deporte) return 1;
      }
      const aActivo = a.activo !== false;
      const bActivo = b.activo !== false;
      if (aActivo !== bActivo) {
        return aActivo ? -1 : 1;
      }
      return this.obtenerFechaOrdenDeporte(a) - this.obtenerFechaOrdenDeporte(b);
    });
  }

  private seleccionarDeportePrincipal(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO | null {
    if (!deportes || deportes.length === 0) {
      return null;
    }
    const activos = deportes.filter(d => d.activo !== false);
    const principalActivo = this.seleccionarDeportePrincipalDesdeLista(activos);
    if (principalActivo) {
      return principalActivo;
    }
    return this.seleccionarDeportePrincipalDesdeLista(deportes);
  }

  private seleccionarDeportePrincipalDesdeLista(deportes: AlumnoDeporteDTO[]): AlumnoDeporteDTO | null {
    if (!deportes || deportes.length === 0) {
      return null;
    }
    const principales = deportes.filter(d => d.principal === true);
    const candidatos = principales.length > 0 ? principales : deportes;
    return [...candidatos].sort((a, b) => {
      return this.obtenerFechaOrdenDeporte(a) - this.obtenerFechaOrdenDeporte(b);
    })[0];
  }

  private obtenerFechaOrdenDeporte(deporte: AlumnoDeporteDTO): number {
    const fecha = deporte.fechaAltaInicial || deporte.fechaAlta;
    return fecha ? new Date(fecha).getTime() : Number.MAX_SAFE_INTEGER;
  }

  isDeportePrincipal(deporte: string): boolean {
    const principal = this.seleccionarDeportePrincipal(this.deportesDelAlumno);
    return principal?.deporte === deporte;
  }

  marcarDeportePrincipal(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    if (!deporteData || deporteData.activo === false) {
      showErrorToast('No se puede marcar como principal un deporte inactivo');
      return;
    }
    if (this.isDeportePrincipal(deporte)) {
      return;
    }
    this.alumnoService.establecerDeportePrincipal(this.alumnoId, deporte).subscribe({
      next: () => {
        showSuccessToast(`Deporte principal actualizado a ${getDeporteLabel(deporte)}`);
        this.cargarDeportesDelAlumno(this.alumnoId!, true);
      },
      error: (error) => {
        showErrorToast(error?.error || 'No se pudo actualizar el deporte principal');
      },
    });
  }

  /**
   * Change the active sport tab
   */
  cambiarTab(deporte: string): void {
    this.deporteActivo = deporte;
  }

  /**
   * Open modal to add a new sport
   */
  abrirModalAgregarDeporte(): void {
    this.deportesDisponibles = this.getDeportesDisponiblesParaAgregar();
    if (this.deportesDisponibles.length === 0) {
      Swal.fire({
        title: 'No hay deportes disponibles',
        text: 'El alumno ya está inscrito en todos los deportes disponibles',
        icon: 'info',
      });
      return;
    }
    this.nuevoDeporte = this.deportesDisponibles[0];
    this.gradoInicialDeporte = 'BLANCO';

    // Set default dates to today
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    const todayFormatted = `${yyyy}-${mm}-${dd}`;

    this.fechaAltaDeporte = todayFormatted;
    this.fechaAltaInicialDeporte = '';
    this.fechaGradoDeporte = todayFormatted;
    this.onNuevoDeporteChange(this.nuevoDeporte);
    this.mostrarModalAgregarDeporte = true;
  }

  /**
   * Close modal to add sport
   */
  cerrarModalAgregarDeporte(): void {
    this.mostrarModalAgregarDeporte = false;
    this.nuevoDeporte = '';
    this.gradoInicialDeporte = '';
    this.fechaAltaDeporte = '';
    this.fechaAltaInicialDeporte = '';
    this.fechaGradoDeporte = '';
  }

  esDeporteSinGrado(deporte?: string | null): boolean {
    if (!deporte) {
      return false;
    }
    return this.deportesSinGrado.has(deporte);
  }

  requiereGradoNuevoDeporte(): boolean {
    return !this.esDeporteSinGrado(this.nuevoDeporte);
  }

  onNuevoDeporteChange(deporte: string): void {
    if (this.esDeporteSinGrado(deporte)) {
      this.gradoInicialDeporte = '';
      this.fechaGradoDeporte = '';
      return;
    }

    if (!this.gradoInicialDeporte) {
      this.gradoInicialDeporte = 'BLANCO';
    }

    if (!this.fechaGradoDeporte) {
      this.fechaGradoDeporte = this.fechaAltaDeporte || this.getFechaActual();
    }
  }

  /**
   * Add a new sport to the alumno
   */
  agregarDeporte(): void {
    const requiereGrado = this.requiereGradoNuevoDeporte();
    if (!this.nuevoDeporte || !this.fechaAltaDeporte || !this.alumnoId) {
      return;
    }
    if (requiereGrado && (!this.gradoInicialDeporte || !this.fechaGradoDeporte)) {
      return;
    }

    const gradoInicial = requiereGrado ? this.gradoInicialDeporte : null;
    const fechaGrado = requiereGrado ? this.fechaGradoDeporte : null;
    const fechaAltaInicial = this.fechaAltaInicialDeporte || null;

    // Save the new sport to switch to it after adding
    const deporteToActivate = this.nuevoDeporte;

    this.alumnoService
      .agregarDeporteAAlumno(
        this.alumnoId,
        this.nuevoDeporte,
        gradoInicial,
        this.fechaAltaDeporte,
        fechaGrado,
        fechaAltaInicial
      )
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Deporte agregado!',
            text: `El deporte ${getDeporteLabel(deporteToActivate)} ha sido agregado exitosamente`,
            icon: 'success',
            timer: 2000,
          });
          this.cerrarModalAgregarDeporte();
          // Reload and switch to the newly added sport
          if (this.alumnoId) {
            this.alumnoService.obtenerDeportesDelAlumno(this.alumnoId).subscribe({
              next: (deportes: AlumnoDeporteDTO[]) => {
                this.deportesDelAlumno = this.ordenarDeportesPorPrincipal(deportes);
                // Switch to the newly added sport
                this.deporteActivo = deporteToActivate;
                this.deportesDisponibles = this.getDeportesDisponiblesParaAgregar();
              }
            });
          }
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo agregar el deporte',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Remove a sport from the alumno
   */
  removerDeporte(deporte: string): void {
    if (this.deportesDelAlumno.length === 1) {
      Swal.fire({
        title: 'No se puede eliminar',
        text: 'El alumno debe tener al menos un deporte asignado',
        icon: 'warning',
      });
      return;
    }

    // Verificar si el deporte está activo o inactivo
    const deporteItem = this.deportesDelAlumno.find(d => d.deporte === deporte);
    const estaInactivo = deporteItem?.activo === false;

    if (estaInactivo) {
      // Si ya está inactivo, solo mostrar opción de eliminar completamente
      Swal.fire({
        title: '¿Eliminar deporte?',
        html: `
          <p>El deporte <strong>${getDeporteLabel(deporte)}</strong> será eliminado completamente.</p>
          <p style="color: #d33; margin-top: 1rem;"><strong>Esta acción es irreversible</strong> y eliminará todos los datos asociados (grado, historial, etc.).</p>
        `,
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Sí, eliminar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#d33',
        cancelButtonColor: '#6c757d',
      }).then((result) => {
        if (result.isConfirmed && this.alumnoId) {
          this.ejecutarEliminacionDeporte(deporte);
        }
      });
    } else {
      // Si está activo, mostrar opciones de desactivar o eliminar
      Swal.fire({
        title: '¿Qué deseas hacer?',
        html: `
          <p>Selecciona una opción para el deporte <strong>${getDeporteLabel(deporte)}</strong>:</p>
          <ul style="text-align: left; margin-top: 1rem;">
            <li><strong>Desactivar:</strong> Mantiene todos los datos (grado, historial) pero marca el deporte como inactivo</li>
            <li><strong>Eliminar:</strong> Elimina completamente el deporte y todos sus datos (irreversible)</li>
          </ul>
        `,
        icon: 'question',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Desactivar',
        denyButtonText: 'Eliminar completamente',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#ffa500',
        denyButtonColor: '#d33',
        cancelButtonColor: '#6c757d',
      }).then((result) => {
        if (result.isConfirmed && this.alumnoId) {
          // Desactivar (soft delete)
          this.alumnoService.desactivarDeporteDeAlumno(this.alumnoId, deporte).subscribe({
            next: () => {
              Swal.fire({
                title: '¡Desactivado!',
                text: 'El deporte ha sido desactivado. Puedes reactivarlo cuando quieras.',
                icon: 'success',
                timer: 2000,
              });
              this.cargarAlumno(this.alumnoId!);
              this.cargarDeportesDelAlumno(this.alumnoId!);
            },
            error: (error) => {
              Swal.fire({
                title: 'Error',
                text: error.error || 'No se pudo desactivar el deporte',
                icon: 'error',
              });
            },
          });
        } else if (result.isDenied && this.alumnoId) {
          this.ejecutarEliminacionDeporte(deporte);
        }
      });
    }
  }

  private ejecutarEliminacionDeporte(deporte: string): void {
    this.alumnoService.removerDeporteDeAlumno(this.alumnoId!, deporte).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Eliminado!',
          text: 'El deporte ha sido eliminado completamente',
          icon: 'success',
          timer: 2000,
        });

        // If we removed the active sport, switch to the first remaining one
        if (this.deporteActivo === deporte && this.deportesDelAlumno.length > 1) {
          const otherDeporte = this.deportesDelAlumno.find(d => d.deporte !== deporte);
          if (otherDeporte) {
            this.deporteActivo = otherDeporte.deporte;
          }
        }

        this.cargarAlumno(this.alumnoId!);
        this.cargarDeportesDelAlumno(this.alumnoId!);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'No se pudo eliminar el deporte',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Activate an inactive sport
   */
  activarDeporte(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    Swal.fire({
      title: '¿Activar deporte?',
      text: `¿Deseas reactivar el deporte ${getDeporteLabel(deporte)}? Se mantendrán todos los datos (grado, historial, etc.).`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, activar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (result.isConfirmed && this.alumnoId) {
        this.alumnoService.activarDeporteDeAlumno(this.alumnoId, deporte).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Activado!',
              text: 'El deporte ha sido reactivado correctamente',
              icon: 'success',
              timer: 2000,
            });
            this.cargarAlumno(this.alumnoId!);
            this.cargarDeportesDelAlumno(this.alumnoId!);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error || 'No se pudo activar el deporte',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  /**
   * Deactivate a sport (soft delete - keeps all data but marks as inactive)
   */
  desactivarDeporte(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    // Verificar que hay más de un deporte activo
    if (this.getDeportesActivosCount() <= 1) {
      Swal.fire({
        title: 'No se puede desactivar',
        text: 'No se puede desactivar el último deporte activo del alumno.',
        icon: 'warning',
      });
      return;
    }

    Swal.fire({
      title: '¿Desactivar deporte?',
      text: `¿Deseas desactivar el deporte ${getDeporteLabel(deporte)}? Se mantendrán todos los datos (grado, historial, etc.) y podrás reactivarlo cuando quieras.`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, desactivar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#ffa500',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (result.isConfirmed && this.alumnoId) {
        this.alumnoService.desactivarDeporteDeAlumno(this.alumnoId, deporte).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Desactivado!',
              text: 'El deporte ha sido desactivado. Puedes reactivarlo cuando quieras.',
              icon: 'success',
              timer: 2000,
            });
            this.cargarAlumno(this.alumnoId!);
            this.cargarDeportesDelAlumno(this.alumnoId!);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error || 'No se pudo desactivar el deporte',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  /**
   * Eliminar, dar de baja o dar de alta al alumno
   * Muestra opciones diferentes según si el alumno está activo o inactivo
   */
  eliminarODarDeBajaAlumno(): void {
    if (!this.alumnoId) {
      return;
    }

    const alumnoActivo = this.isAlumnoActivo();

    if (alumnoActivo) {
      // Alumno ACTIVO - Mostrar opciones de dar de baja o eliminar
      Swal.fire({
        title: '¿Qué deseas hacer?',
        html: `
          <p>Selecciona una opción para <strong>${this.alumno.nombre} ${this.alumno.apellidos}</strong>:</p>
          <ul style="text-align: left; margin-top: 1rem;">
            <li><strong>Dar de baja:</strong> Desactiva todos los deportes activos y mantiene todos los datos (historial, grupos, productos, etc.)</li>
            <li><strong>Eliminar completamente:</strong> Elimina permanentemente al alumno y toda su información (irreversible)</li>
          </ul>
        `,
        icon: 'warning',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Dar de baja',
        denyButtonText: 'Eliminar completamente',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#ffa500',
        denyButtonColor: '#d33',
        cancelButtonColor: '#6c757d',
      }).then((result) => {
        if (result.isConfirmed) {
          // Dar de baja (soft delete)
          this.darDeBajaAlumno();
        } else if (result.isDenied) {
          // Eliminar completamente (hard delete)
          this.eliminarAlumnoCompletamente();
        }
      });
    } else {
      // Alumno INACTIVO - Mostrar opciones de dar de alta o eliminar
      Swal.fire({
        title: '¿Qué deseas hacer?',
        html: `
          <p>Selecciona una opción para <strong>${this.alumno.nombre} ${this.alumno.apellidos}</strong> (actualmente inactivo):</p>
          <ul style="text-align: left; margin-top: 1rem;">
            <li><strong>Dar de alta:</strong> Reactiva al alumno en uno de sus deportes</li>
            <li><strong>Eliminar completamente:</strong> Elimina permanentemente al alumno y toda su información (irreversible)</li>
          </ul>
        `,
        icon: 'question',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Dar de alta',
        denyButtonText: 'Eliminar completamente',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#28a745',
        denyButtonColor: '#d33',
        cancelButtonColor: '#6c757d',
      }).then((result) => {
        if (result.isConfirmed) {
          // Dar de alta
          this.darDeAltaAlumno();
        } else if (result.isDenied) {
          // Eliminar completamente (hard delete)
          this.eliminarAlumnoCompletamente();
        }
      });
    }
  }

  /**
   * Dar de baja al alumno (soft delete)
   * Usa el endpoint del backend que maneja correctamente la desactivación de todos los deportes
   */
  private darDeBajaAlumno(): void {
    if (!this.alumnoId) {
      return;
    }

    // Obtener todos los deportes activos para mostrar feedback
    const deportesActivos = this.deportesDelAlumno.filter(d => d.activo !== false);

    if (deportesActivos.length === 0) {
      Swal.fire({
        title: 'Información',
        text: 'El alumno ya está inactivo (no tiene deportes activos)',
        icon: 'info',
      });
      return;
    }

    // Usar el endpoint del backend que maneja la lógica completa de dar de baja
    this.endpointsService.darDeBajaAlumno(this.alumnoId).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Dado de baja!',
          text: `El alumno ha sido dado de baja en todos sus deportes (${deportesActivos.length} ${deportesActivos.length === 1 ? 'deporte' : 'deportes'}).`,
          icon: 'success',
          timer: 3000,
        });
        this.cargarAlumno(this.alumnoId!);
        this.cargarDeportesDelAlumno(this.alumnoId!);
      },
      error: (error: any) => {
        Swal.fire({
          title: 'Error',
          text: error.error?.message || 'No se pudo dar de baja al alumno',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Eliminar completamente al alumno (hard delete)
   */
  private eliminarAlumnoCompletamente(): void {
    if (!this.alumnoId) {
      return;
    }

    Swal.fire({
      title: '¿Estás completamente seguro?',
      html: `
        <p>Esta acción <strong>NO SE PUEDE DESHACER</strong>.</p>
        <p>Se eliminarán permanentemente:</p>
        <ul style="text-align: left; margin-top: 1rem;">
          <li>Todos los datos personales</li>
          <li>Historial de deportes y grados</li>
          <li>Asignaciones a grupos y turnos</li>
          <li>Productos y pagos</li>
          <li>Documentos adjuntos</li>
          <li>Convocatorias de examen</li>
        </ul>
      `,
      icon: 'error',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar permanentemente',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#d33',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarAlumnos(this.alumnoId!).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Eliminado!',
              text: 'El alumno ha sido eliminado completamente del sistema.',
              icon: 'success',
              timer: 2000,
            }).then(() => {
              this.router.navigate(['/alumnosListar']);
            });
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error?.message || 'No se pudo eliminar al alumno',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  /**
   * Dar de alta al alumno
   * Permite elegir qué deporte activar si tiene varios inactivos
   */
  private darDeAltaAlumno(): void {
    if (!this.alumnoId) {
      return;
    }

    // Obtener todos los deportes inactivos
    const deportesInactivos = this.deportesDelAlumno.filter(d => d.activo === false);

    if (deportesInactivos.length === 0) {
      Swal.fire({
        title: 'Información',
        text: 'El alumno ya está activo (tiene deportes activos)',
        icon: 'info',
      });
      return;
    }

    // Si tiene un solo deporte, activarlo directamente
    if (deportesInactivos.length === 1) {
      this.activarDeporteYLimpiarFechas(deportesInactivos[0].deporte);
      return;
    }

    // Si tiene múltiples deportes, mostrar selector
    const deportesOptions = deportesInactivos.map(d =>
      `<option value="${d.deporte}">${getDeporteLabel(d.deporte)}</option>`
    ).join('');

    Swal.fire({
      title: 'Selecciona el deporte a activar',
      html: `
        <p>El alumno tiene ${deportesInactivos.length} deportes inactivos.</p>
        <p>¿Qué deporte deseas activar?</p>
        <select id="deporte-select" class="swal2-input" style="width: 80%;">
          <option value="">Selecciona un deporte...</option>
          <option value="TODOS">✓ Todos los deportes</option>
          ${deportesOptions}
        </select>
      `,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Activar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
      didOpen: () => {
        attachSwalSelectSearch({ selectId: 'deporte-select', placeholder: 'Buscar deporte...' });
      },
      preConfirm: () => {
        const deporteSelect = document.getElementById('deporte-select') as HTMLSelectElement;
        if (!deporteSelect.value) {
          Swal.showValidationMessage('Debes seleccionar un deporte');
          return false;
        }
        return deporteSelect.value;
      },
    }).then((result) => {
      if (result.isConfirmed && result.value) {
        if (result.value === 'TODOS') {
          this.activarTodosLosDeportesYLimpiarFechas(deportesInactivos);
        } else {
          this.activarDeporteYLimpiarFechas(result.value);
        }
      }
    });
  }

  /**
   * Activa un deporte específico y limpia las fechas de baja
   */
  private activarDeporteYLimpiarFechas(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    // Activar el deporte seleccionado - el backend ya actualiza el alumno automáticamente
    this.alumnoService.activarDeporteDeAlumno(this.alumnoId, deporte).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Dado de alta!',
          text: `El alumno ha sido dado de alta en ${getDeporteLabel(deporte)}.`,
          icon: 'success',
          timer: 3000,
        });
        this.cargarAlumno(this.alumnoId!);
        this.cargarDeportesDelAlumno(this.alumnoId!);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'No se pudo activar el deporte',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Activa todos los deportes inactivos y limpia las fechas de baja
   */
  private activarTodosLosDeportesYLimpiarFechas(deportesInactivos: AlumnoDeporteDTO[]): void {
    if (!this.alumnoId || deportesInactivos.length === 0) {
      return;
    }

    // Crear array de observables para activar cada deporte
    const activarDeportesObservables = deportesInactivos.map(deporte =>
      this.alumnoService.activarDeporteDeAlumno(this.alumnoId!, deporte.deporte)
    );

    // Ejecutar todas las activaciones secuencialmente
    concat(...activarDeportesObservables).subscribe({
      next: () => {
        // Se ejecuta después de cada activación individual
      },
      error: (error: any) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'No se pudieron activar algunos deportes',
          icon: 'error',
        });
      },
      complete: () => {
        // El backend ya actualiza el alumno automáticamente al activar cada deporte
        // Solo recargamos los datos para reflejar los cambios
        const deportesLabels = deportesInactivos.map(d => getDeporteLabel(d.deporte)).join(', ');
        Swal.fire({
          title: '¡Dado de alta!',
          text: `El alumno ha sido dado de alta en todos sus deportes: ${deportesLabels}.`,
          icon: 'success',
          timer: 3000,
        });
        this.cargarAlumno(this.alumnoId!);
        this.cargarDeportesDelAlumno(this.alumnoId!);
      },
    });
  }

  /**
   * Get the AlumnoDeporteDTO for the active sport
   */
  getDeporteActivo(): AlumnoDeporteDTO | undefined {
    return this.deportesDelAlumno.find(d => d.deporte === this.deporteActivo);
  }

  /**
   * Get the grade name for a specific sport
   */
  getGradoDelDeporte(deporte: string): string {
    const alumnoDeporte = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return alumnoDeporte?.grado || 'Sin grado';
  }

  /**
   * Get deporte label (for display)
   */
  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  /**
   * Get the count of active sports for this alumno
   */
  getDeportesActivosCount(): number {
    return this.deportesDelAlumno.filter(d => d.activo !== false).length;
  }

  /**
   * Check if alumno is active based on sport count
   * Alumno is active if has at least 1 active sport
   */
  isAlumnoActivo(): boolean {
    return this.getDeportesActivosCount() > 0;
  }

  /**
   * Open modal to update grade for a specific sport
   */
  abrirModalActualizarGrado(deporte: string): void {
    this.deporteParaActualizarGrado = deporte;
    const deporteActual = this.deportesDelAlumno.find(d => d.deporte === deporte);
    this.nuevoGradoActualizar = deporteActual?.grado || 'BLANCO';
    this.fechaGradoActualizar =
      this.formatDateForInput(deporteActual?.fechaGrado) || this.formatDateForInput(new Date());
    this.mostrarModalActualizarGrado = true;
  }

  /**
   * Close grade update modal
   */
  cerrarModalActualizarGrado(): void {
    this.mostrarModalActualizarGrado = false;
    this.deporteParaActualizarGrado = '';
    this.nuevoGradoActualizar = '';
    this.fechaGradoActualizar = '';
  }

  /**
   * Update grade for a specific sport
   */
  actualizarGradoDeporte(): void {
    if (!this.deporteParaActualizarGrado || !this.nuevoGradoActualizar || !this.alumnoId) {
      return;
    }

    const updates = [
      this.alumnoService.actualizarGradoPorDeporte(
        this.alumnoId,
        this.deporteParaActualizarGrado,
        this.nuevoGradoActualizar
      ),
    ];

    if (this.fechaGradoActualizar) {
      updates.push(
        this.alumnoService.actualizarFechaGrado(
          this.alumnoId,
          this.deporteParaActualizarGrado,
          this.fechaGradoActualizar
        )
      );
    }

    concat(...updates).subscribe({
      complete: () => {
        const deporteLabel = getDeporteLabel(this.deporteParaActualizarGrado);
        const mensaje = this.fechaGradoActualizar
          ? `El grado y la fecha para ${deporteLabel} han sido actualizados a ${this.nuevoGradoActualizar}`
          : `El grado para ${deporteLabel} ha sido actualizado a ${this.nuevoGradoActualizar}`;

        Swal.fire({
          title: 'Grado actualizado!',
          text: mensaje,
          icon: 'success',
          timer: 2000,
        });
        this.cerrarModalActualizarGrado();
        // Reload sports data while preserving the current active tab
        this.cargarDeportesDelAlumno(this.alumnoId!, true);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'No se pudo actualizar el grado o la fecha',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Update exam eligibility for a specific sport
   */
  actualizarAptoParaExamen(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const aptoParaExamen = event.target.checked;

    this.alumnoService
      .actualizarAptoParaExamen(this.alumnoId, deporte, aptoParaExamen)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Actualizado!',
            text: `Estado de aptitud para examen actualizado en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          // Reload sports data while preserving the current active tab
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          // Revert the checkbox
          event.target.checked = !aptoParaExamen;
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el estado',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Update grade date for a specific sport
   */
  actualizarFechaGrado(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const fechaGrado = event.target.value;
    if (!fechaGrado) {
      return;
    }

    this.alumnoService
      .actualizarFechaGrado(this.alumnoId, deporte, fechaGrado)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Fecha actualizada!',
            text: `Fecha de grado actualizada en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          // Reload sports data while preserving the current active tab
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar la fecha',
            icon: 'error',
          });
          // Reload to revert the date input, preserve tab
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update the student's initial enrollment date (fechaAltaInicial) PER SPORT
   */
  actualizarFechaAltaInicial(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const fechaAltaInicial = event.target.value;
    if (!fechaAltaInicial) {
      return;
    }

    this.alumnoService
      .actualizarFechaAltaInicialDeporte(this.alumnoId, deporte, fechaAltaInicial)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Fecha actualizada',
            text: `Fecha de alta inicial actualizada en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar la fecha',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update tipo de tarifa for a specific sport
   */
  /**
   * Handle tipo tarifa change - updates pending changes and autofills cuantia
   */
  onTipoTarifaChange(deporte: string, event: any): void {
    const tipoTarifa = event.target.value;

    // Get or create pending changes for this deporte
    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.tipoTarifa = tipoTarifa;

    // Get current deporte data
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    const rolFamiliar = pending.rolFamiliar || deporteData?.rolFamiliar || undefined;

    // Always autofill cuantia with the standard amount for the new tipoTarifa
    // User can manually change it afterwards if they want a custom amount
    const cuantiaEstandar = obtenerCuantiaTarifaEstandar(tipoTarifa, rolFamiliar);
    pending.cuantiaTarifa = cuantiaEstandar;

    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle tipo tarifa change from ngModel - accepts value directly
   */
  onTipoTarifaModelChange(deporte: string, tipoTarifa: string): void {
    // Skip if empty value (user selected "Seleccionar tarifa")
    if (!tipoTarifa) {
      return;
    }

    // Get current deporte data
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);

    // Skip if same as current value (no actual change)
    if (deporteData?.tipoTarifa === tipoTarifa) {
      // Remove from pending if it was previously changed
      const existing = this.pendingTarifaChanges.get(deporte);
      if (existing) {
        delete existing.tipoTarifa;
        delete existing.cuantiaTarifa;
        if (Object.keys(existing).length === 0) {
          this.pendingTarifaChanges.delete(deporte);
        }
      }
      return;
    }

    // Get or create pending changes for this deporte
    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.tipoTarifa = tipoTarifa;

    const rolFamiliar = pending.rolFamiliar || deporteData?.rolFamiliar || undefined;

    // Always autofill cuantia with the standard amount for the new tipoTarifa
    // User can manually change it afterwards if they want a custom amount
    const cuantiaEstandar = obtenerCuantiaTarifaEstandar(tipoTarifa, rolFamiliar);
    pending.cuantiaTarifa = cuantiaEstandar;

    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle cuantia tarifa change - updates pending changes
   */
  onCuantiaTarifaChange(deporte: string, event: any): void {
    const cuantiaTarifa = Number.parseFloat(event.target.value);
    if (Number.isNaN(cuantiaTarifa)) {
      return;
    }

    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.cuantiaTarifa = cuantiaTarifa;
    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle cuantia tarifa change from ngModel - accepts value directly
   */
  onCuantiaTarifaModelChange(deporte: string, value: number | string): void {
    const cuantiaTarifa = typeof value === 'string' ? Number.parseFloat(value) : value;
    if (Number.isNaN(cuantiaTarifa)) {
      return;
    }

    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.cuantiaTarifa = cuantiaTarifa;
    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle rol familiar change - updates pending changes and recalculates cuantia for PADRES_HIJOS
   */
  onRolFamiliarChange(deporte: string, event: any): void {
    const rolFamiliar = event.target.value;

    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.rolFamiliar = rolFamiliar;

    // If it's PADRES_HIJOS tarifa, recalculate cuantia based on rol
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    const tipoTarifa = pending.tipoTarifa || deporteData?.tipoTarifa;

    if (tipoTarifa === 'PADRES_HIJOS') {
      const cuantiaEstandar = obtenerCuantiaTarifaEstandar(tipoTarifa, rolFamiliar);
      pending.cuantiaTarifa = cuantiaEstandar;
    }

    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle rol familiar change from ngModel - accepts value directly
   */
  onRolFamiliarModelChange(deporte: string, rolFamiliar: string): void {
    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.rolFamiliar = rolFamiliar;

    // If it's PADRES_HIJOS tarifa, recalculate cuantia based on rol
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    const tipoTarifa = pending.tipoTarifa || deporteData?.tipoTarifa;

    if (tipoTarifa === 'PADRES_HIJOS') {
      const cuantiaEstandar = obtenerCuantiaTarifaEstandar(tipoTarifa, rolFamiliar);
      pending.cuantiaTarifa = cuantiaEstandar;
    }

    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle grupo familiar change - updates pending changes
   */
  onGrupoFamiliarChange(deporte: string, event: any): void {
    const grupoFamiliar = event.target.value;

    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.grupoFamiliar = grupoFamiliar;
    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Handle grupo familiar change from ngModel - accepts value directly
   */
  onGrupoFamiliarModelChange(deporte: string, grupoFamiliar: string): void {
    const pending = this.pendingTarifaChanges.get(deporte) || {};
    pending.grupoFamiliar = grupoFamiliar;
    this.pendingTarifaChanges.set(deporte, pending);
  }

  /**
   * Update tiene licencia for a specific sport
   */
  /**
   * Handle tieneLicencia toggle - now uses pending changes pattern
   * to allow batch update with numeroLicencia and fechaLicencia
   */
  onTieneLicenciaChange(deporte: string, event: any): void {
    const tieneLicencia = event.target.checked;

    const pending = this.pendingLicenciaChanges.get(deporte) || {};
    pending.tieneLicencia = tieneLicencia;
    this.pendingLicenciaChanges.set(deporte, pending);
  }

  /**
   * @deprecated Use onTieneLicenciaChange instead - kept for backward compatibility
   */
  actualizarTieneLicencia(deporte: string, event: any): void {
    this.onTieneLicenciaChange(deporte, event);
  }

  /**
   * Update numero de licencia for a specific sport
   */
  actualizarNumeroLicencia(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const numeroLicencia = Number.parseInt(event.target.value, 10);
    if (Number.isNaN(numeroLicencia)) {
      return;
    }

    this.alumnoService
      .actualizarNumeroLicenciaDeporte(this.alumnoId, deporte, numeroLicencia)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Número actualizado',
            text: `Número de licencia actualizado en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el número de licencia',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update fecha de licencia for a specific sport
   */
  actualizarFechaLicencia(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const fechaLicencia = event.target.value;
    if (!fechaLicencia) {
      return;
    }

    this.alumnoService
      .actualizarFechaLicenciaDeporte(this.alumnoId, deporte, fechaLicencia)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Fecha actualizada',
            text: `Fecha de licencia actualizada en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar la fecha',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update competidor status for a specific sport
   */
  actualizarCompetidor(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const competidor = event.target.checked;

    this.alumnoService
      .actualizarCompetidorDeporte(this.alumnoId, deporte, competidor)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Competidor actualizado',
            text: `Estado de competidor actualizado en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el estado de competidor',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update peso for a specific sport
   */
  actualizarPeso(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const peso = Number.parseFloat(event.target.value);
    if (Number.isNaN(peso)) {
      return;
    }

    this.alumnoService
      .actualizarPesoDeporte(this.alumnoId, deporte, peso)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Peso actualizado',
            text: `Peso actualizado en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el peso',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Update fecha de peso for a specific sport
   */
  actualizarFechaPeso(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const fechaPeso = event.target.value;
    if (!fechaPeso) {
      return;
    }

    this.alumnoService
      .actualizarFechaPesoDeporte(this.alumnoId, deporte, fechaPeso)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Fecha actualizada',
            text: `Fecha de peso actualizada en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar la fecha',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
        },
      });
  }

  /**
   * Open convocatoria modal for a specific sport
   * Shows a selection swal to choose or create a convocatoria
   */
  abrirModalConvocatoriasPorDeporte(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    this.deporteParaConvocatoria = deporte;

    this.endpointsService.obtenerConvocatorias(deporte).subscribe({
      next: (convocatorias: any[]) => {
        const convocatoriasOrdenadas = [...convocatorias].sort((a, b) =>
          new Date(b.fechaConvocatoria).getTime() - new Date(a.fechaConvocatoria).getTime()
        );

        if (convocatoriasOrdenadas.length === 0) {
          this.abrirSwalCrearConvocatoria(deporte);
          return;
        }

        const inputOptions: Record<string, string> = {};
        convocatoriasOrdenadas.forEach((convocatoria) => {
          const fecha = this.formatDateForInput(convocatoria.fechaConvocatoria);
          inputOptions[String(convocatoria.id)] = `${fecha}`;
        });

        const ahora = new Date();
        const proximas = convocatoriasOrdenadas
          .filter((conv) => new Date(conv.fechaConvocatoria) >= ahora)
          .sort((a, b) =>
            new Date(a.fechaConvocatoria).getTime() - new Date(b.fechaConvocatoria).getTime()
          );
        const convocatoriaDefault = proximas[0] ?? convocatoriasOrdenadas[0];

        Swal.fire({
          title: `Selecciona convocatoria (${this.getDeporteLabel(deporte)})`,
          input: 'select',
          inputOptions,
          inputPlaceholder: 'Selecciona una convocatoria',
          inputValue: String(convocatoriaDefault.id),
          showCancelButton: true,
          showDenyButton: true,
          confirmButtonText: 'Añadir',
          denyButtonText: 'Crear nueva',
          cancelButtonText: 'Cancelar',
          inputValidator: (value) => {
            if (value) {
              return null;
            }
            return 'Debes seleccionar una convocatoria';
          },
        }).then((result) => {
          if (result.isConfirmed && result.value) {
            const convocatoriaSeleccionada = convocatoriasOrdenadas.find(
              (conv) => String(conv.id) === String(result.value)
            );
            if (convocatoriaSeleccionada) {
              this.agregarAConvocatoriaPorDeporte(convocatoriaSeleccionada);
            }
          } else if (result.isDenied) {
            this.abrirSwalCrearConvocatoria(deporte);
          }
        });
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar las convocatorias',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Load convocatorias filtered by sport and open the modal
   */
  private cargarConvocatoriasFiltradasPorDeporte(deporte: string, porRecompensa: boolean): void {
    this.deporteParaConvocatoria = deporte;
    this.alumnoEditado.porRecompensa = porRecompensa;

    // Load all convocatorias and filter by sport
    this.endpointsService.obtenerConvocatorias().subscribe({
      next: (convocatorias: any[]) => {
        // Filter convocatorias by the selected sport
        this.convocatoriasFiltradasPorDeporte = convocatorias.filter(
          (conv) => conv.deporte === deporte
        );

        // Find the current convocatoria for this sport
        const ahora = new Date();
        this.convocatoriaActual = this.convocatoriasFiltradasPorDeporte.find((conv) => {
          const fechaConv = new Date(conv.fechaConvocatoria);
          return fechaConv >= ahora;
        }) || null;

        // Remove current from available list
        if (this.convocatoriaActual) {
          this.convocatoriasFiltradasPorDeporte = this.convocatoriasFiltradasPorDeporte.filter(
            (conv) => conv.id !== this.convocatoriaActual.id
          );
        }

        this.mostrarModalConvocatorias = true;
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar las convocatorias',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Add alumno to convocatoria for a specific sport
   */
  agregarAConvocatoriaPorDeporte(convocatoria: any): void {
    if (!this.alumnoId) {
      return;
    }

    // Find the AlumnoDeporte ID for the selected sport
    const deporteSeleccionado = this.deporteParaConvocatoria || convocatoria.deporte;
    const deporteData = this.deportesDelAlumno.find(
      (d) => d.deporte === deporteSeleccionado
    );

    if (!deporteData) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo encontrar el registro del deporte',
        icon: 'error',
      });
      return;
    }

    // Siempre usar el producto de derecho de examen (no recompensa)
    const porRecompensa = false;

    const agregarAlumno = (rojoBordado: boolean) => {
      const alumnoConvocatoriaData = {
        alumno: { id: this.alumnoId },
        convocatoria: { id: convocatoria.id },
        alumnoDeporte: { id: deporteData.id }, // Link to the specific sport
      };

      this.endpointsService
        .agregarAlumnoAConvocatoriaMultiDeporte(
          convocatoria.id,
          alumnoConvocatoriaData,
          porRecompensa,
          rojoBordado
        )
        .subscribe({
          next: () => {
            Swal.fire({
              title: 'Agregado!',
              text: `El alumno ha sido agregado a la convocatoria de ${getDeporteLabel(
                deporteSeleccionado
              )}`,
              icon: 'success',
              timer: 2000,
            });
            this.cerrarModalConvocatorias();
            this.cargarConvocatoriasDelAlumno(this.alumnoId!);
            this.obtenerProductosAlumno(this.alumnoId!);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error?.message || 'No se pudo agregar a la convocatoria',
              icon: 'error',
            });
          },
        });
    };

    if (this.requiereSeleccionCinturonRojo(deporteSeleccionado, deporteData.grado)) {
      this.solicitarTipoCinturonRojo(
        'Selecciona cinturon rojo',
        `El alumno pasara a rojo en ${getDeporteLabel(deporteSeleccionado)}. Selecciona el tipo de cinturon.`
      ).then((rojoBordado) => {
        if (rojoBordado === null) {
          return;
        }
        agregarAlumno(rojoBordado);
      });
      return;
    }

    agregarAlumno(false);
  }

  private requiereSeleccionCinturonRojo(deporte: string, gradoActual?: string | null): boolean {
    return esSiguienteGradoRojo(deporte, gradoActual ?? null, this.alumno?.fechaNacimiento);
  }

  private solicitarTipoCinturonRojo(titulo: string, texto: string): Promise<boolean | null> {
    return Swal.fire({
      title: titulo,
      text: texto,
      input: 'radio',
      inputOptions: {
        normal: 'Rojo (sin bordar)',
        bordado: 'Rojo bordado',
      },
      inputValue: 'normal',
      showCancelButton: true,
      confirmButtonText: 'Continuar',
      cancelButtonText: 'Cancelar',
      inputValidator: (value) => {
        if (value) {
          return null;
        }
        return 'Debes seleccionar una opcion';
      },
    }).then((result) => {
      if (!result.isConfirmed) {
        return null;
      }
      return result.value === 'bordado';
    });
  }


  // ========== BATCH UPDATE METHODS FOR PENDING CHANGES ==========

  /**
   * Handle numero licencia change - updates pending changes
   */
  onNumeroLicenciaChange(deporte: string, event: any): void {
    const numeroLicencia = Number.parseInt(event.target.value, 10);
    if (Number.isNaN(numeroLicencia)) {
      return;
    }
    const pending = this.pendingLicenciaChanges.get(deporte) || {};
    pending.numeroLicencia = numeroLicencia;
    this.pendingLicenciaChanges.set(deporte, pending);
  }

  /**
   * Handle fecha licencia change - updates pending changes
   */
  onFechaLicenciaChange(deporte: string, event: any): void {
    const fechaLicencia = event.target.value;

    const pending = this.pendingLicenciaChanges.get(deporte) || {};
    pending.fechaLicencia = fechaLicencia;
    this.pendingLicenciaChanges.set(deporte, pending);
  }

  /**
   * Handle competidor change - updates pending changes
   */
  onCompetidorChange(deporte: string, event: any): void {
    const competidor = event.target.checked;

    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.competidor = competidor;
    if (competidor) {
      const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
      const categoriaActual = pending.categoria ?? deporteData?.categoria;
      const categoriaVacia =
        !categoriaActual || (typeof categoriaActual === 'string' && categoriaActual.trim() === '');
      if (categoriaVacia) {
        const categoriaPorEdad = this.obtenerCategoriaPorEdad(deporte);
        if (categoriaPorEdad) {
          pending.categoria = categoriaPorEdad;
        }
      }
    }
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Handle peso change - updates pending changes
   */
  onPesoChange(deporte: string, event: any): void {
    const peso = Number.parseFloat(event.target.value);
    if (Number.isNaN(peso)) {
      return;
    }
    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.peso = peso;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Handle fecha peso change - updates pending changes
   */
  onFechaPesoChange(deporte: string, event: any): void {
    const fechaPeso = event.target.value;

    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.fechaPeso = fechaPeso;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Handle fecha alta competicion change - updates pending changes
   */
  onFechaAltaCompeticionChange(deporte: string, event: any): void {
    const fechaAltaCompeticion = event.target.value;

    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.fechaAltaCompeticion = fechaAltaCompeticion;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Handle fecha alta competidor inicial change - updates pending changes
   */
  onFechaAltaCompetidorInicialChange(deporte: string, event: any): void {
    const fechaAltaCompetidorInicial = event.target.value;

    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.fechaAltaCompetidorInicial = fechaAltaCompetidorInicial;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Handle categoria change - updates pending changes
   */
  onCategoriaChange(deporte: string, event: any): void {
    const categoria = event.target.value;

    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.categoria = categoria;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  /**
   * Get current categoria value for a deporte (pending or actual)
   */
  getCategoriaValue(deporte: string): string {
    const deporteItem = this.deportesDelAlumno.find(d => d.deporte === deporte);
    const pending = this.pendingCompetidorChanges.get(deporte);
    const categoriaActual = pending?.categoria ?? deporteItem?.categoria ?? '';
    if (!categoriaActual) {
      const categoriaPorEdad = this.obtenerCategoriaPorEdad(deporte);
      return categoriaPorEdad || '';
    }
    return categoriaActual;
  }

  private obtenerCategoriaPorEdad(deporte: string = 'TAEKWONDO'): string {
    return calcularCategoriaPorEdad(this.alumno?.fechaNacimiento ?? null, deporte);
  }

  /**
   * Handle categoria change via ngModel
   */
  onCategoriaModelChange(deporte: string, categoria: string): void {
    const pending = this.pendingCompetidorChanges.get(deporte) || {};
    pending.categoria = categoria;
    this.pendingCompetidorChanges.set(deporte, pending);
  }

  // ========== HELPER METHODS TO GET DISPLAYED VALUES ==========

  /**
   * Get displayed value for tarifa fields (pending or current)
   */
  getDisplayedTipoTarifa(deporte: string): string {
    const pending = this.pendingTarifaChanges.get(deporte);
    if (pending?.tipoTarifa !== undefined) {
      return pending.tipoTarifa;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.tipoTarifa || '';
  }

  getDisplayedCuantiaTarifa(deporte: string): number | string {
    const pending = this.pendingTarifaChanges.get(deporte);
    if (pending?.cuantiaTarifa !== undefined) {
      return pending.cuantiaTarifa;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.cuantiaTarifa ?? '';
  }

  getDisplayedRolFamiliar(deporte: string): string {
    const pending = this.pendingTarifaChanges.get(deporte);
    if (pending?.rolFamiliar !== undefined) {
      return pending.rolFamiliar;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.rolFamiliar || '';
  }

  getDisplayedGrupoFamiliar(deporte: string): string {
    const pending = this.pendingTarifaChanges.get(deporte);
    if (pending?.grupoFamiliar !== undefined) {
      return pending.grupoFamiliar;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.grupoFamiliar || '';
  }

  getDisplayedTieneLicencia(deporte: string): boolean {
    const pending = this.pendingLicenciaChanges.get(deporte);
    if (pending?.tieneLicencia !== undefined) {
      return pending.tieneLicencia;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.tieneLicencia || false;
  }

  getDisplayedNumeroLicencia(deporte: string): number | null {
    const pending = this.pendingLicenciaChanges.get(deporte);
    if (pending?.numeroLicencia !== undefined) {
      return pending.numeroLicencia;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.numeroLicencia || null;
  }

  getDisplayedFechaLicencia(deporte: string): string {
    const pending = this.pendingLicenciaChanges.get(deporte);
    if (pending?.fechaLicencia !== undefined) {
      return pending.fechaLicencia;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return this.formatDateForInput(deporteData?.fechaLicencia);
  }

  getDisplayedCompetidor(deporte: string): boolean {
    const pending = this.pendingCompetidorChanges.get(deporte);
    if (pending?.competidor !== undefined) {
      return pending.competidor;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.competidor || false;
  }

  getDisplayedPeso(deporte: string): number | null {
    const pending = this.pendingCompetidorChanges.get(deporte);
    if (pending?.peso !== undefined) {
      return pending.peso;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return deporteData?.peso || null;
  }

  getDisplayedFechaPeso(deporte: string): string {
    const pending = this.pendingCompetidorChanges.get(deporte);
    if (pending?.fechaPeso !== undefined) {
      return pending.fechaPeso;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return this.formatDateForInput(deporteData?.fechaPeso);
  }

  // ========== CHECK IF THERE ARE PENDING CHANGES ==========

  hasPendingTarifaChanges(deporte: string): boolean {
    return this.pendingTarifaChanges.has(deporte);
  }

  hasPendingLicenciaChanges(deporte: string): boolean {
    return this.pendingLicenciaChanges.has(deporte);
  }

  hasPendingCompetidorChanges(deporte: string): boolean {
    return this.pendingCompetidorChanges.has(deporte);
  }

  // ========== APPLY PENDING CHANGES TO BACKEND ==========

  /**
   * Apply pending tarifa changes for a specific deporte
   * Updates are made SEQUENTIALLY to avoid race conditions where one update
   * overwrites another's changes on the same entity
   */
  applyTarifaChanges(deporte: string): void {
    const alumnoId = this.alumnoId;
    if (!alumnoId || !this.hasPendingTarifaChanges(deporte)) {
      return;
    }

    const pending = this.pendingTarifaChanges.get(deporte)!;
    const updates: any[] = [];

    // Build array of sequential updates
    if (pending.tipoTarifa !== undefined && pending.tipoTarifa !== '') {
      updates.push(
        this.alumnoService
          .actualizarTipoTarifaDeporte(alumnoId, deporte, pending.tipoTarifa)
          .pipe(
            tap(() => console.log('tipoTarifa updated successfully')),
            catchError((error) => {
              this.handleUpdateError('tipo de tarifa', error);
              return of(null);
            })
          )
      );
    }

    if (pending.cuantiaTarifa !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarCuantiaTarifaDeporte(alumnoId, deporte, pending.cuantiaTarifa)
          .pipe(
            tap(() => console.log('cuantiaTarifa updated successfully')),
            catchError((error) => {
              this.handleUpdateError('cuantía de tarifa', error);
              return of(null);
            })
          )
      );
    }

    if (pending.rolFamiliar !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarRolFamiliarDeporte(alumnoId, deporte, pending.rolFamiliar)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('rol familiar', error);
              return of(null);
            })
          )
      );
    }

    if (pending.grupoFamiliar !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarGrupoFamiliarDeporte(alumnoId, deporte, pending.grupoFamiliar)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('grupo familiar', error);
              return of(null);
            })
          )
      );
    }

    // Execute updates sequentially using concat
    if (updates.length > 0) {
      concat(...updates).pipe(
        finalize(() => this.onAllTarifaUpdatesComplete(deporte))
      ).subscribe();
    }
  }

  private onAllTarifaUpdatesComplete(deporte: string): void {
    this.pendingTarifaChanges.delete(deporte);
    Swal.fire({
      title: 'Tarifa actualizada',
      text: `Información de tarifa actualizada en ${getDeporteLabel(deporte)}`,
      icon: 'success',
      timer: 1500,
      showConfirmButton: false,
    });
    const alumnoId = this.alumnoId;
    if (alumnoId) {
      this.cargarDeportesDelAlumno(alumnoId, true);
    }
  }

  /**
   * Apply pending licencia changes for a specific deporte
   * Updates are made SEQUENTIALLY to avoid race conditions
   */
  applyLicenciaChanges(deporte: string): void {
    const alumnoId = this.alumnoId;
    if (!alumnoId || !this.hasPendingLicenciaChanges(deporte)) {
      return;
    }

    const pending = this.pendingLicenciaChanges.get(deporte)!;
    const updates: any[] = [];

    // Build array of sequential updates
    if (pending.tieneLicencia !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarTieneLicenciaDeporte(alumnoId, deporte, pending.tieneLicencia)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('estado de licencia', error);
              return of(null);
            })
          )
      );
    }

    if (pending.numeroLicencia !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarNumeroLicenciaDeporte(alumnoId, deporte, pending.numeroLicencia)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('número de licencia', error);
              return of(null);
            })
          )
      );
    }

    if (pending.fechaLicencia !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarFechaLicenciaDeporte(alumnoId, deporte, pending.fechaLicencia)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('fecha de licencia', error);
              return of(null);
            })
          )
      );
    }

    // Execute updates sequentially using concat
    if (updates.length > 0) {
      concat(...updates).pipe(
        finalize(() => this.onAllLicenciaUpdatesComplete(deporte))
      ).subscribe();
    }
  }

  private onAllLicenciaUpdatesComplete(deporte: string): void {
    this.pendingLicenciaChanges.delete(deporte);
    Swal.fire({
      title: 'Licencia actualizada',
      text: `Información de licencia actualizada en ${getDeporteLabel(deporte)}`,
      icon: 'success',
      timer: 1500,
      showConfirmButton: false,
    });
    const alumnoId = this.alumnoId;
    if (alumnoId) {
      this.cargarDeportesDelAlumno(alumnoId, true);
    }
  }

  /**
   * Apply pending competidor changes for a specific deporte
   * Uses a single endpoint to update all fields in one transaction (avoids race conditions)
   */
  applyCompetidorChanges(deporte: string): void {
    const alumnoId = this.alumnoId;
    if (!alumnoId || !this.hasPendingCompetidorChanges(deporte)) {
      return;
    }

    const pending = this.pendingCompetidorChanges.get(deporte)!;
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);

    // Build the request object with all pending changes
    const datosCompetidor: {
      competidor?: boolean;
      fechaAltaCompeticion?: string;
      fechaAltaCompetidorInicial?: string;
      categoria?: string;
      peso?: number;
      fechaPeso?: string;
    } = {};

    if (pending.competidor !== undefined) {
      datosCompetidor.competidor = pending.competidor;
    }
    if (pending.fechaAltaCompeticion !== undefined) {
      datosCompetidor.fechaAltaCompeticion = pending.fechaAltaCompeticion;
    }
    if (pending.fechaAltaCompetidorInicial !== undefined) {
      datosCompetidor.fechaAltaCompetidorInicial = pending.fechaAltaCompetidorInicial;
    }
    if (pending.categoria !== undefined) {
      datosCompetidor.categoria = pending.categoria;
    }
    if (pending.peso !== undefined) {
      datosCompetidor.peso = pending.peso;
    }
    if (pending.fechaPeso !== undefined) {
      datosCompetidor.fechaPeso = pending.fechaPeso;
    }

    const competidorActual = pending.competidor ?? deporteData?.competidor;
    const categoriaActual = pending.categoria ?? deporteData?.categoria;
    const categoriaVacia =
      !categoriaActual || (typeof categoriaActual === 'string' && categoriaActual.trim() === '');

    // Asignar categoría automáticamente para competidores si no tiene una
    if (competidorActual && categoriaVacia) {
      const categoriaPorEdad = this.obtenerCategoriaPorEdad(deporte);
      if (categoriaPorEdad) {
        datosCompetidor.categoria = categoriaPorEdad;
      }
    }

    // Single request to update all fields
    this.alumnoService
      .actualizarDatosCompetidor(alumnoId, deporte, datosCompetidor)
      .subscribe({
        next: () => {
          this.onAllCompetidorUpdatesComplete(deporte);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudieron actualizar los datos de competidor',
            icon: 'error',
          });
          this.cargarDeportesDelAlumno(alumnoId, true);
        },
      });
  }

  private onAllCompetidorUpdatesComplete(deporte: string): void {
    // Reload the data first, then delete pending changes in the callback
    // This ensures the checkbox doesn't reset to old value during reload
    const currentActiveDeporte = this.deporteActivo;

    this.alumnoService.obtenerDeportesDelAlumno(this.alumnoId!).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        this.deportesDelAlumno = this.ordenarDeportesPorPrincipal(deportes);

        // Preserve the current active tab
        const stillExists = this.deportesDelAlumno.find(d => d.deporte === currentActiveDeporte);
        if (stillExists) {
          this.deporteActivo = currentActiveDeporte;
        } else if (this.deportesDelAlumno.length > 0) {
          this.deporteActivo = this.deportesDelAlumno[0].deporte;
        }

        // NOW delete pending changes after data has been reloaded
        this.pendingCompetidorChanges.delete(deporte);

        Swal.fire({
          title: 'Competidor actualizado',
          text: `Información de competidor actualizada en ${getDeporteLabel(deporte)}`,
          icon: 'success',
          timer: 1500,
          showConfirmButton: false,
        });
      },
      error: (error) => {
        console.error('Error reloading deportes after competidor update:', error);
        // Still delete pending changes even if reload fails
        this.pendingCompetidorChanges.delete(deporte);
      }
    });
  }

  /**
   * Cancel pending changes for a specific deporte
   */
  cancelTarifaChanges(deporte: string): void {
    this.pendingTarifaChanges.delete(deporte);
  }

  cancelLicenciaChanges(deporte: string): void {
    this.pendingLicenciaChanges.delete(deporte);
  }

  cancelCompetidorChanges(deporte: string): void {
    this.pendingCompetidorChanges.delete(deporte);
  }

  cancelGradoChanges(deporte: string): void {
    this.pendingGradoChanges.delete(deporte);
  }

  cancelEstadisticasChanges(deporte: string): void {
    this.pendingEstadisticasChanges.delete(deporte);
  }

  // ========== GRADO PENDING CHANGES ==========

  onFechaGradoChange(deporte: string, event: any): void {
    const fechaGrado = event.target.value;
    if (!fechaGrado) {
      return;
    }

    const pending = this.pendingGradoChanges.get(deporte) || {};
    pending.fechaGrado = fechaGrado;
    this.pendingGradoChanges.set(deporte, pending);
  }

  getDisplayedFechaGrado(deporte: string): string {
    const pending = this.pendingGradoChanges.get(deporte);
    if (pending?.fechaGrado !== undefined) {
      return pending.fechaGrado;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    if (deporteData?.fechaGrado) {
      return this.formatDateForInput(deporteData.fechaGrado);
    }
    return '';
  }

  hasPendingGradoChanges(deporte: string): boolean {
    return this.pendingGradoChanges.has(deporte);
  }

  applyGradoChanges(deporte: string): void {
    if (!this.alumnoId || !this.hasPendingGradoChanges(deporte)) {
      return;
    }

    const pending = this.pendingGradoChanges.get(deporte)!;

    if (pending.fechaGrado !== undefined) {
      this.alumnoService
        .actualizarFechaGrado(this.alumnoId, deporte, pending.fechaGrado)
        .subscribe({
          next: () => {
            this.pendingGradoChanges.delete(deporte);
            Swal.fire({
              title: 'Fecha de grado actualizada',
              text: `Fecha de grado actualizada en ${getDeporteLabel(deporte)}`,
              icon: 'success',
              timer: 1500,
              showConfirmButton: false,
            });
            this.cargarDeportesDelAlumno(this.alumnoId!, true);
          },
          error: this.handleUpdateError.bind(this, 'fecha de grado'),
        });
    }
  }

  // ========== ESTADISTICAS PENDING CHANGES ==========

  onFechaAltaChange(deporte: string, event: any): void {
    const fechaAlta = event.target.value;
    if (!fechaAlta) {
      return;
    }

    const pending = this.pendingEstadisticasChanges.get(deporte) || {};
    pending.fechaAlta = fechaAlta;
    this.pendingEstadisticasChanges.set(deporte, pending);
  }

  getDisplayedFechaAlta(deporte: string): string {
    const pending = this.pendingEstadisticasChanges.get(deporte);
    if (pending?.fechaAlta !== undefined) {
      return pending.fechaAlta;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    if (deporteData?.fechaAlta) {
      return this.formatDateForInput(deporteData.fechaAlta);
    }
    return '';
  }

  onFechaBajaChange(deporte: string, event: any): void {
    const fechaBaja = event.target.value;
    const pending = this.pendingEstadisticasChanges.get(deporte) || {};
    pending.fechaBaja = fechaBaja;
    this.pendingEstadisticasChanges.set(deporte, pending);
  }

  getDisplayedFechaBaja(deporte: string): string {
    const pending = this.pendingEstadisticasChanges.get(deporte);
    if (pending?.fechaBaja !== undefined) {
      return pending.fechaBaja;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    if (deporteData?.fechaBaja) {
      return this.formatDateForInput(deporteData.fechaBaja);
    }
    return '';
  }

  onFechaAltaInicialChange(deporte: string, event: any): void {
    const fechaAltaInicial = event.target.value;
    if (!fechaAltaInicial) {
      return;
    }

    const pending = this.pendingEstadisticasChanges.get(deporte) || {};
    pending.fechaAltaInicial = fechaAltaInicial;
    this.pendingEstadisticasChanges.set(deporte, pending);
  }

  getDisplayedFechaAltaInicial(deporte: string): string {
    const pending = this.pendingEstadisticasChanges.get(deporte);
    if (pending?.fechaAltaInicial !== undefined) {
      return pending.fechaAltaInicial;
    }
    const deporteData = this.deportesDelAlumno.find(d => d.deporte === deporte);
    if (deporteData?.fechaAltaInicial) {
      return this.formatDateForInput(deporteData.fechaAltaInicial);
    }
    return '';
  }

  hasPendingEstadisticasChanges(deporte: string): boolean {
    return this.pendingEstadisticasChanges.has(deporte);
  }

  applyEstadisticasChanges(deporte: string): void {
    if (!this.alumnoId || !this.hasPendingEstadisticasChanges(deporte)) {
      return;
    }

    const pending = this.pendingEstadisticasChanges.get(deporte)!;

    const updates: any[] = [];

    if (pending.fechaAlta !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarFechaAltaDeporte(this.alumnoId, deporte, pending.fechaAlta)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('fecha de alta', error);
              return of(null);
            })
          )
      );
    }

    if (pending.fechaBaja !== undefined) {
      const fechaBajaValue = pending.fechaBaja === '' ? null : pending.fechaBaja;
      updates.push(
        this.alumnoService
          .actualizarFechaBajaDeporte(this.alumnoId, deporte, fechaBajaValue)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('fecha de baja', error);
              return of(null);
            })
          )
      );
    }

    if (pending.fechaAltaInicial !== undefined) {
      updates.push(
        this.alumnoService
          .actualizarFechaAltaInicialDeporte(this.alumnoId, deporte, pending.fechaAltaInicial)
          .pipe(
            catchError((error) => {
              this.handleUpdateError('fecha de alta inicial', error);
              return of(null);
            })
          )
      );
    }

    if (updates.length > 0) {
      concat(...updates)
        .pipe(finalize(() => this.onAllEstadisticasUpdatesComplete(deporte)))
        .subscribe();
    }
  }

  private onAllEstadisticasUpdatesComplete(deporte: string): void {
    this.pendingEstadisticasChanges.delete(deporte);
    Swal.fire({
      title: 'Fechas actualizadas',
      text: `Estadísticas actualizadas en ${getDeporteLabel(deporte)}`,
      icon: 'success',
      timer: 1500,
      showConfirmButton: false,
    });
    this.cargarDeportesDelAlumno(this.alumnoId!, true);
  }

  private handleUpdateError(fieldName: string, error: any): void {
    Swal.fire({
      title: 'Error',
      text: error.error || `No se pudo actualizar ${fieldName}`,
      icon: 'error',
    });
  }

  /**
   * Formats a date for use in input[type="date"] fields (yyyy-MM-dd format)
   * Handles timezone issues by working with local date components instead of UTC
   */
  private formatDateForInput(fecha: Date | string | null | undefined): string {
    if (!fecha) {
      return '';
    }

    // If it's already a string in ISO format, extract just the date part
    if (typeof fecha === 'string') {
      // Handle ISO strings like "2025-01-03T00:00:00.000Z" or "2025-01-03"
      if (fecha.includes('T')) {
        // Parse without timezone conversion
        const datePart = fecha.split('T')[0];
        return datePart;
      }
      // Already in yyyy-MM-dd format
      if (/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
        return fecha;
      }
    }

    // For Date objects, use local date components to avoid timezone shifts
    const date = fecha instanceof Date ? fecha : new Date(fecha);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  // ==================== BASIC INFO INLINE EDITING ====================

  /**
   * Toggle edit mode for basic info section
   */
  toggleEditBasicInfo(): void {
    if (this.editingBasicInfo) {
      // Exiting edit mode - cancel changes
      this.cancelBasicInfoChanges();
    } else {
      // Entering edit mode - initialize pending changes with current values
      this.editingBasicInfo = true;
      this.pendingBasicInfoChanges = {};
    }
  }

  /**
   * Get displayed value for a basic info field (pending or current)
   */
  getDisplayedBasicInfo(field: string): any {
    if (this.pendingBasicInfoChanges.hasOwnProperty(field)) {
      return (this.pendingBasicInfoChanges as any)[field];
    }
    return this.alumno ? this.alumno[field] : '';
  }

  /**
   * Handle change for basic info fields
   */
  onBasicInfoChange(field: string, event: Event): void {
    const target = event.target as HTMLInputElement;
    let value: any;

    if (target.type === 'checkbox') {
      value = target.checked;
    } else if (target.type === 'date') {
      value = target.value;
    } else {
      value = target.value;
    }

    (this.pendingBasicInfoChanges as any)[field] = value;
    this.validateBasicInfoField(field, value);
  }

  /**
   * Validate a basic info field
   */
  validateBasicInfoField(field: string, value: any): void {
    // Clear previous error
    delete (this.basicInfoErrors as any)[field];

    const validator = this.basicInfoValidators[field];
    if (!validator) {
      return;
    }

    const errorMessage = validator(value);
    if (errorMessage) {
      (this.basicInfoErrors as any)[field] = errorMessage;
    }
  }

  private isBlank(value: any): boolean {
    return !value || (typeof value === 'string' && value.trim() === '');
  }

  // ==================== OBSERVACIONES ====================

  hasObservacionesChanges(): boolean {
    const actual = this.alumno?.observaciones ?? '';
    return (this.observacionesDraft ?? '') !== actual;
  }

  guardarObservaciones(): void {
    if (!this.alumnoId || !this.alumno || this.guardandoObservaciones || !this.hasObservacionesChanges()) {
      return;
    }

    this.guardandoObservaciones = true;
    const observaciones = this.observacionesDraft ?? '';
    this.endpointsService.actualizarObservacionesAlumno(this.alumnoId, observaciones)
      .pipe(finalize(() => (this.guardandoObservaciones = false)))
      .subscribe({
        next: () => {
          showSuccessToast('Observaciones actualizadas');
          this.alumno.observaciones = observaciones;
        },
        error: () => {
          showErrorToast('Error al actualizar las observaciones');
        },
      });
  }

  /**
   * Crea una nueva convocatoria desde un swal y agrega al alumno directamente
   */
  private abrirSwalCrearConvocatoria(deporte: string): void {
    this.deporteParaConvocatoria = deporte;
    Swal.fire({
      title: `Nueva convocatoria (${this.getDeporteLabel(deporte)})`,
      input: 'date',
      inputLabel: 'Fecha de la convocatoria',
      inputValue: this.formatDateForInput(new Date()),
      showCancelButton: true,
      confirmButtonText: 'Crear y añadir',
      cancelButtonText: 'Cancelar',
      inputValidator: (value) => {
        if (!value) {
          return 'Debes seleccionar una fecha';
        }
        return null;
      },
    }).then((result) => {
      if (result.isConfirmed && result.value) {
        const nuevaConvocatoria = {
          fechaConvocatoria: result.value,
          deporte,
        };

        this.endpointsService.crearConvocatoria(nuevaConvocatoria).subscribe({
          next: (convocatoriaCreada) => {
            this.agregarAConvocatoriaPorDeporte(convocatoriaCreada);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error?.message || 'No se pudo crear la convocatoria',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  /**
   * Pasa de grado por recompensa en el deporte indicado
   */
  pasarGradoPorRecompensa(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    const ejecutarPase = (rojoBordado: boolean) => {
      this.endpointsService.pasarGradoPorRecompensa(this.alumnoId!, deporte, rojoBordado).subscribe({
        next: () => {
          Swal.fire({
            title: 'Grado actualizado!',
            text: `El alumno ha pasado de grado por recompensa en ${this.getDeporteLabel(deporte)}.`,
            icon: 'success',
            timer: 2000,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
          this.obtenerProductosAlumno(this.alumnoId!);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo pasar de grado por recompensa',
            icon: 'error',
          });
        },
      });
    };

    const gradoActual = this.deportesDelAlumno.find((d) => d.deporte === deporte)?.grado;
    if (this.requiereSeleccionCinturonRojo(deporte, gradoActual)) {
      this.solicitarTipoCinturonRojo(
        'Pasar a rojo por recompensa',
        `Se actualizará el grado en ${this.getDeporteLabel(deporte)} una vez realizado el pago y se añadirá el producto de recompensa. Selecciona el tipo de cinturón.`
      ).then((rojoBordado) => {
        if (rojoBordado === null) {
          return;
        }
        ejecutarPase(rojoBordado);
      });
      return;
    }

    Swal.fire({
      title: 'Pasar de grado por recompensa',
      text: `Se actualizará el grado en ${this.getDeporteLabel(deporte)} una vez realizado el pago y se añadirá el producto de recompensa.`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Confirmar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      ejecutarPase(false);
    });
  }

  /**
   * Pasa de grado con derecho de examen en el deporte indicado (para Kickboxing)
   */
  pasarGradoConDerechoExamen(deporte: string): void {
    if (!this.alumnoId) {
      return;
    }

    const ejecutarPase = (rojoBordado: boolean) => {
      this.endpointsService.pasarGradoConDerechoExamen(this.alumnoId!, deporte, rojoBordado).subscribe({
        next: () => {
          Swal.fire({
            title: 'Derecho de examen añadido',
            text: `Se ha añadido el producto de derecho de examen para ${this.getDeporteLabel(deporte)}. El grado se actualizará cuando se marque como pagado.`,
            icon: 'success',
            timer: 3000,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!, true);
          this.obtenerProductosAlumno(this.alumnoId!);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo añadir el derecho de examen',
            icon: 'error',
          });
        },
      });
    };

    const gradoActual = this.deportesDelAlumno.find((d) => d.deporte === deporte)?.grado;
    if (this.requiereSeleccionCinturonRojo(deporte, gradoActual)) {
      this.solicitarTipoCinturonRojo(
        'Pasar a rojo',
        `Se añadirá el producto de derecho de examen para ${this.getDeporteLabel(deporte)}. El grado se actualizará cuando se marque como pagado. Selecciona el tipo de cinturón.`
      ).then((rojoBordado) => {
        if (rojoBordado === null) {
          return;
        }
        ejecutarPase(rojoBordado);
      });
      return;
    }

    Swal.fire({
      title: 'Añadir derecho de examen',
      text: `Se añadirá el producto de derecho de examen para ${this.getDeporteLabel(deporte)}. El grado se actualizará cuando se marque como pagado.`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Confirmar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      ejecutarPase(false);
    });
  }


  private validateNif(value: any): string | null {
    if (this.isBlank(value)) {
      return 'El DNI es obligatorio';
    }
    const nifValue = String(value).trim();
    if (!/^\d{8}[A-Za-z]$/.test(nifValue)) {
      return 'Formato inválido (8 números y una letra)';
    }
    return null;
  }

  private validateEmail(value: any): string | null {
    if (this.isBlank(value)) {
      return 'El email es obligatorio';
    }
    const emailValue = String(value).trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailValue)) {
      return 'Formato de email inválido';
    }
    return null;
  }

  private validateTelefono(value: any, required: boolean): string | null {
    if (this.isBlank(value)) {
      return required ? 'El teléfono es obligatorio' : null;
    }
    const telefonoValue = String(value).trim();
    if (!/^\d+$/.test(telefonoValue)) {
      return 'Solo se permiten números';
    }
    if (telefonoValue.length > 9) {
      return 'Máximo 9 dígitos';
    }
    return null;
  }
  /**
   * Check if a basic info field has an error
   */
  hasBasicInfoError(field: string): boolean {
    return !!(this.basicInfoErrors as any)[field];
  }

  /**
   * Get basic info field error message
   */
  getBasicInfoError(field: string): string {
    return (this.basicInfoErrors as any)[field] || '';
  }

  /**
   * Check if there are any validation errors
   */
  hasBasicInfoErrors(): boolean {
    return Object.keys(this.basicInfoErrors).length > 0;
  }

  /**
   * Check if there are pending basic info changes
   */
  hasPendingBasicInfoChanges(): boolean {
    return Object.keys(this.pendingBasicInfoChanges).length > 0;
  }

  /**
   * Apply pending basic info changes
   */
  applyBasicInfoChanges(): void {
    if (!this.hasPendingBasicInfoChanges() || !this.alumnoId) {
      return;
    }

    // Validate all pending changes before applying
    if (this.hasBasicInfoErrors()) {
      showErrorToast('Por favor, corrige los errores antes de guardar');
      return;
    }

    // Get telefono value - ensure it's a number
    const telefonoValue = this.pendingBasicInfoChanges.telefono ?? this.alumno.telefono;
    const telefonoInt = typeof telefonoValue === 'string'
      ? Number.parseInt(telefonoValue, 10)
      : telefonoValue;

    // Get telefono2 value - ensure it's a number or null
    const telefono2Value = this.pendingBasicInfoChanges.telefono2 ?? this.alumno.telefono2;
    let telefono2Int: number | null = null;
    if (telefono2Value !== null && telefono2Value !== undefined && telefono2Value !== '') {
      const telefono2Number =
        typeof telefono2Value === 'string'
          ? Number.parseInt(telefono2Value, 10)
          : Number(telefono2Value);
      telefono2Int = Number.isNaN(telefono2Number) ? null : telefono2Number;
    }

    // Format fechaBaja - use null if empty string
    const fechaBajaValue = this.pendingBasicInfoChanges.fechaBaja ?? (this.alumno.fechaBaja ? formatDate(this.alumno.fechaBaja) : null);
    const fechaBaja = fechaBajaValue === '' ? null : fechaBajaValue;

    // Merge pending changes with current alumno data - include ALL required fields for backend validation
    const updatedData = {
      // Basic info fields (editable)
      nombre: this.pendingBasicInfoChanges.nombre ?? this.alumno.nombre,
      apellidos: this.pendingBasicInfoChanges.apellidos ?? this.alumno.apellidos,
      direccion: this.pendingBasicInfoChanges.direccion ?? this.alumno.direccion,
      fechaNacimiento: this.pendingBasicInfoChanges.fechaNacimiento ?? formatDate(this.alumno.fechaNacimiento),
      nif: this.pendingBasicInfoChanges.nif ?? this.alumno.nif,
      email: this.pendingBasicInfoChanges.email ?? this.alumno.email,
      telefono: telefonoInt,
      telefono2: telefono2Int,
      tieneDiscapacidad: this.pendingBasicInfoChanges.tieneDiscapacidad ?? this.alumno.tieneDiscapacidad,
      autorizacionWeb: this.pendingBasicInfoChanges.autorizacionWeb ?? this.alumno.autorizacionWeb,
      fechaBaja: fechaBaja,
      observaciones: this.pendingBasicInfoChanges.observaciones ?? this.alumno.observaciones,
      // Required fields for backend validation (preserve current values)
      tipoTarifa: this.alumno.tipoTarifa,
      fechaAlta: this.alumno.fechaAlta ? formatDate(this.alumno.fechaAlta) : null,
      cuantiaTarifa: this.alumno.cuantiaTarifa,
      rolFamiliar: this.alumno.rolFamiliar,
      grupoFamiliar: this.alumno.grupoFamiliar,
    };

    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(updatedData));

    this.endpointsService.actualizarAlumno(this.alumnoId, formData).subscribe({
      next: () => {
        showSuccessToast('Información actualizada correctamente');
        this.pendingBasicInfoChanges = {};
        this.editingBasicInfo = false;
        // Reload alumno data
        this.cargarAlumno(this.alumnoId!);
      },
      error: (error) => {
        showErrorToast('Error al actualizar la información');
      },
    });
  }

  /**
   * Cancel pending basic info changes
   */
  cancelBasicInfoChanges(): void {
    this.pendingBasicInfoChanges = {};
    this.basicInfoErrors = {};
    this.editingBasicInfo = false;
  }
}


