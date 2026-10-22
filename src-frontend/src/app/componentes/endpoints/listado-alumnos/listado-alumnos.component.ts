import { inject } from '@angular/core';
import { AccionInforme, InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { InformePdfModalComponent } from '../../generales/informe-pdf-modal/informe-pdf-modal.component';
import { HerramientasAlumnosComponent } from './herramientas-alumnos/herramientas-alumnos.component';
import { BajasAltasAlumno } from './bajas-altas-alumno';
import { InformesAlumnos, OPCIONES_INFORME_ALUMNOS } from './informes-alumnos';
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
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { getGradoTextStyle, getGradoNombreParaDeporte } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';

type ResumenAlumno = {
  totalDeportes: number;
  tarifaPrincipal: string;
  antiguedadPrincipal: string | null;
  licenciaTexto: string;
  licenciaClase: string;
};

@Component({
  providers: [InformePdfService],
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [InformePdfModalComponent,
    HerramientasAlumnosComponent,
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
  readonly informes = inject(InformePdfService);
  readonly catalogo = inject(CatalogoService);
  alumnos: any[] = [];
  private peticionPorDeporte?: Subscription; // R2-P6: la última petición del listado filtrado por deporte
  paginaActual: number = 1;
  tamanoPagina: number = 9;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  cargando: boolean = true; // Local loading state
  usandoPaginacionCliente: boolean = false; // Filtrando por deporte (R2-P6: en el servidor; nombre conservado por el estado guardado)
  private readonly searchSubject = new Subject<string>();
  mostrarModalInforme: boolean = false;
  modalTitle: string = '';
  opcionesInforme: Array<{ value: string; label: string }> = [];
  // Diálogo «Generar informe»: su lógica está en informes-alumnos.ts (compartida con el listado nuevo, 8b-3a).
  private informesAlumnosInstancia?: InformesAlumnos;
  private get informesAlumnos(): InformesAlumnos {
    return (this.informesAlumnosInstancia ??= new InformesAlumnos(this.endpointsService, this.informes));
  }
  get temporadasReservasPlaza(): string[] {
    return this.informesAlumnos.temporadas;
  }
  set temporadasReservasPlaza(temporadas: string[]) {
    this.informesAlumnos.temporadas = temporadas;
  }
  get cargandoTemporadasReservasPlaza(): boolean {
    return this.informesAlumnos.cargandoTemporadas;
  }

  // «Dar de baja» y «Dar de alta»: en bajas-altas-alumno.ts (compartidas con el listado nuevo, 8b-3a).
  private bajasAltasInstancia?: BajasAltasAlumno;
  private get bajasAltas(): BajasAltasAlumno {
    return (this.bajasAltasInstancia ??= new BajasAltasAlumno(this.endpointsService, this.alumnoService, () =>
      this.recargarDatos()
    ));
  }

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
      this.obtenerAlumnosFiltradosPorDeporte();
    } else {
      this.obtenerAlumnos();
    }

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
        if (this.usandoPaginacionCliente) {
          this.obtenerAlumnosFiltradosPorDeporte();
        } else {
          this.obtenerAlumnos();
        }
      }
    }
  }

  abrirModalInforme(): void {
    this.modalTitle = 'Generar Informe';
    this.opcionesInforme = [...OPCIONES_INFORME_ALUMNOS];
    this.mostrarModalInforme = true;
    this.informesAlumnos.abrir();
  }

  cerrarModalInforme(): void {
    this.mostrarModalInforme = false;
  }

  obtenerAlumnos(): void {
    // Primero se cancela la petición por deporte pendiente: su finalize pone cargando a false.
    this.peticionPorDeporte?.unsubscribe();
    this.cargando = true;
    this.usandoPaginacionCliente = false; // Sale del modo filtrado por deporte
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

          // Sports already come embedded in each alumno of the page (AlumnoDTO.deportes)
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
      }
  }

  /**
   * Stores the sports of every alumno on the current page. They come embedded in the paginated
   * response (AlumnoDTO.deportes, all of them, active and inactive, same as GET /alumnos/{id}/deportes),
   * so no extra request per alumno is needed.
   */
  cargarDeportesDeAlumnos(): void {
    this.deportesPorAlumno.clear();
    this.alumnos.forEach((alumno) => {
      this.deportesPorAlumno.set(alumno.id, alumno.deportes ?? []);
    });
  }

  /**
   * Listado filtrado por deporte (R2-P6): el servidor filtra y pagina con el criterio que antes se aplicaba aquí
   * sobre todos los alumnos (los de baja en ese deporte que siguen activos en otro incluidos; nombre recortado,
   * en nombre o apellidos). Como antes, en este modo no se aplica «Solo aptos». Si llega una búsqueda nueva antes
   * de la respuesta, la petición anterior se cancela para no pintar resultados viejos.
   */
  obtenerAlumnosFiltradosPorDeporte(): void {
    // Primero se cancela la anterior: su finalize pone cargando a false.
    this.peticionPorDeporte?.unsubscribe();
    this.cargando = true;
    this.usandoPaginacionCliente = true;
    this.peticionPorDeporte = this.endpointsService
      .obtenerAlumnos(
        this.paginaActual,
        this.tamanoPagina,
        this.nombreFiltro,
        this.mostrarInactivos,
        false,
        this.deporteFiltro
      )
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (response) => {
          this.alumnos = response.content;
          this.totalPaginas = response.totalPages;
          this.guardarEstadoPaginacion();
          this.cargarDeportesDeAlumnos();
        },
        error: (error) => {
          console.error('Error al cargar los alumnos del deporte:', error);
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
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
      const requiereLicencia = this.catalogo.requiereLicencia(deportePrincipal.deporte);
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
      this.obtenerAlumnosFiltradosPorDeporte();
    }
  }

  /**
   * Check if a sport requires showing grade (not for disciplines without a grading system)
   */
  deporteRequiereGrado(deporte: string): boolean {
    return !this.catalogo.sinGrados(deporte);
  }

  /**
   * Check if alumno needs to show license (their main sport requires a license)
   */
  alumnoRequiereLicencia(alumnoId: number): boolean {
    const deportes = this.getDeportesDeAlumno(alumnoId);
    const principal = this.getDeportePrincipalDeLista(deportes);
    if (!principal) {
      return false;
    }
    return this.catalogo.requiereLicencia(principal.deporte);
  }

  /**
   * Get filtered alumnos - now just returns alumnos since filtering is handled elsewhere
   */
  get alumnosFiltrados(): any[] {
    // Filtra el servidor (también el deporte desde R2-P6)
    return this.alumnos;
  }

  generarInformeSeleccionado(event: {
    tipo: string;
    soloActivos: boolean;
    temporada?: string;
    accion: AccionInforme;
  }): void {
    this.informesAlumnos.generarInformeSeleccionado(event);
  }

  calcularEdad(fechaNacimiento: string): number {
    return calcularEdad(fechaNacimiento);
  }

  getGradoStyle(tipoGrado: string, deporte?: string | null): string {
    return getGradoTextStyle(tipoGrado, deporte);
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
      this.obtenerAlumnosFiltradosPorDeporte();
    } else {
      // Use backend pagination
      this.obtenerAlumnos();
    }
  }

  filtrarPorNombre(): void {
    // Filtrando por deporte: busca al momento, como antes (cada búsqueda cancela la petición anterior)
    if (this.usandoPaginacionCliente) {
      this.paginaActual = 1;
      this.guardarEstadoPaginacion(); // Save state when filtering
      this.obtenerAlumnosFiltradosPorDeporte();
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
      this.obtenerAlumnosFiltradosPorDeporte();
    } else {
      this.obtenerAlumnos();
    }
  }

  darDeAlta(alumnoId: number) {
    this.bajasAltas.darDeAlta(alumnoId);
  }

  recargarDatos(): void {
    if (this.usandoPaginacionCliente) {
      this.obtenerAlumnosFiltradosPorDeporte();
    } else {
      this.obtenerAlumnos();
    }
  }

  darDeBaja(alumnoId: number) {
    this.bajasAltas.darDeBaja(alumnoId);
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
