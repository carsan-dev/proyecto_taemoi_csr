import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, filter } from 'rxjs/operators';

import { SeccionHerramientas } from '../../componentes/endpoints/listado-alumnos/herramientas-alumnos/herramientas-alumnos.component';
import { AlumnoService } from '../../features/alumno/services/alumno.service';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { InformePdfService } from '../../servicios/generales/informe-pdf.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { calcularEdad } from '../../utilities/calcular-edad';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { CobroCargoService, teclaPanelCargos } from '../cobro/cobro-cargo';
import { eur, fecha, numero, plural } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import {
  EstadoLicencia,
  antiguedad,
  estadoLicencia,
  fotoDe,
  GradoFila,
  gradoFila,
  iniciales,
  ordenarDeportes,
  principalDe,
  tiempoConGrado,
} from './alumno-vista';
import { VisorPdfComponent } from '../visor-pdf/visor-pdf.component';
import { BajasAltasPanel } from './bajas-altas-panel';
import { GenerarInformePanelComponent } from './herramientas/generar-informe-panel.component';
import { HerramientaPanelComponent } from './herramientas/herramienta-panel.component';
import { InformePdfPanelService } from './herramientas/informe-pdf-panel.service';
import { CargoPendiente, PendienteAlumno, PendientesAlumnoService } from './pendientes-alumno.service';

/** Clave de localStorage de «Filas | Tarjetas». */
export const CLAVE_VISTA = 'panelAlumnosVista';

/** 25 por página (decisión 253): la búsqueda, los filtros y la paginación van al servidor. */
export const POR_PAGINA = 25;

type Vista = 'filas' | 'tarjetas';
type Estado = 'cargando' | 'listo' | 'error';
type FiltroEstado = 'activos' | 'inactivos' | 'todos';

const OPCIONES_ESTADO: ReadonlyArray<{ valor: FiltroEstado; etiqueta: string }> = [
  { valor: 'activos', etiqueta: 'Activos' },
  { valor: 'inactivos', etiqueta: 'Inactivos' },
  { valor: 'todos', etiqueta: 'Todos' },
];

interface DeporteFila {
  codigo: string;
  nombre: string;
  claseDisciplina: string;
  principal: boolean;
  activo: boolean;
  grado: GradoFila | null;
  /** Disciplina con grados y sin grado asignado: hoy se ve «Sin grado». */
  sinGrado: boolean;
  tiempoConGrado: string;
  tarifa: string;
  alta: string;
  antiguedad: string;
  /** Solo en las disciplinas con grados, como hoy; «N/A» si no hay. */
  categoria: string | null;
}

interface FilaAlumno {
  id: number;
  nombre: string;
  iniciales: string;
  foto: string | null;
  expediente: string;
  edad: string;
  fechaBaja: string;
  /** Con al menos un deporte activo (el criterio de hoy para «Dar de baja» o «Dar de alta»). */
  activo: boolean;
  /** Deportes activos, la principal primero (línea 2). */
  deportes: DeporteFila[];
  /** Detalle de la fila abierta: los activos; de un alumno de baja, todos. */
  detalle: DeporteFila[];
  tarifa: string;
  antiguedad: string;
  licencia: EstadoLicencia | null;
}

const OPCIONES_HERRAMIENTAS: ReadonlyArray<{ seccion: SeccionHerramientas; etiqueta: string }> = [
  { seccion: 'mensualidades', etiqueta: 'Gestión de mensualidades y licencias' },
  { seccion: 'asistencia', etiqueta: 'Listado de asistencia' },
  { seccion: 'listado-mensualidades', etiqueta: 'Listado de mensualidades' },
];

/**
 * Listado de alumnos C del panel nuevo (8b-3a; decisiones 251 y 253). Filas de dos líneas o tarjetas, 25 por página con
 * búsqueda, filtros y paginación en el servidor (el mismo `GET /api/alumnos` que el listado de hoy), el pendiente vencido
 * de cada alumno de la página (`GET /api/tesoreria/pendientes-por-alumno`) con «Cobrar» como la tesorería de hoy, la fila
 * desplegable con el detalle por deporte y sus acciones y el menú «Informes». Va en su propio chunk detrás de
 * `estilosPanelGuard`.
 *
 * 8b-D1 (decisiones 269 y 270): «Cobrar», «Dar de baja» y «Dar de alta» con el diálogo del panel nuevo, avisos propios,
 * esqueleto, error y vacío comunes, y las tres herramientas del pie, cada una en el panel lateral desde «Informes» (el
 * listado acaba en la paginación).
 *
 * 8b-D2a: «Generar informe…» abre su sección en el panel lateral (no el diálogo de hoy), los PDF se ven en el visor del
 * panel nuevo, sin cargador global, y la carga del listado no abre además el SweetAlert2 del interceptor (su fallo ya lo
 * dice el bloque de error).
 */
@Component({
  selector: 'app-alumnos',
  standalone: true,
  imports: [
    RouterLink,
    GenerarInformePanelComponent,
    VisorPdfComponent,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    HerramientaPanelComponent,
    AcentoEscuelaDirective,
  ],
  // El de siempre, con los avisos del panel nuevo (un SweetAlert2 quedaría detrás del panel lateral).
  providers: [{ provide: InformePdfService, useClass: InformePdfPanelService }],
  templateUrl: './alumnos.component.html',
  styleUrls: ['./alumnos.component.scss'],
})
export class AlumnosComponent implements OnInit, OnDestroy {
  readonly informes = inject(InformePdfService);
  readonly catalogo = inject(CatalogoService);
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly alumnoService = inject(AlumnoService);
  private readonly pendientesApi = inject(PendientesAlumnoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cobros = inject(CobroCargoService);
  private readonly bajasAltas = inject(BajasAltasPanel);
  private readonly avisos = inject(AvisosService);

  readonly eur = eur;
  readonly fecha = fecha;
  readonly numero = numero;
  readonly plural = plural;
  readonly porPagina = POR_PAGINA;
  /**
   * Responsable de sede (decisión 370): sin bajas definitivas, sin informes de escuela (`/api/informes/**` sin sede), sin
   * generar mensualidades y sin la vista de familia, que él no puede abrir.
   */
  readonly panelSede = inject(PermisosSesionService).tiene('PANEL_SEDE');
  readonly opcionesHerramientas = this.panelSede
    ? OPCIONES_HERRAMIENTAS.filter((o) => o.seccion !== 'mensualidades')
    : OPCIONES_HERRAMIENTAS;

  @ViewChild('menuInformes') private menuInformes?: ElementRef<HTMLElement>;
  @ViewChild('botonInformes') private botonInformes?: ElementRef<HTMLButtonElement>;

  // Filtros (al servidor).
  nombre = '';
  deporte = '';
  /** Qué alumnos lista la pantalla: solo activos (por defecto), solo inactivos o todos. */
  filtroEstado: FiltroEstado = 'activos';
  aptos = false;
  pagina = 1;

  vista: Vista = 'filas';
  estado: Estado = 'cargando';
  estadoPendientes: Estado = 'cargando';
  total = 0;
  totalPaginas = 0;
  activos: number | null = null;
  filas: FilaAlumno[] = [];
  private alumnos: any[] = [];
  private readonly pendientes = new Map<number, PendienteAlumno>();

  /** Filas desplegadas. */
  readonly abiertas = new Set<number>();
  /** Alumno con el panel de cargos abierto (varios cargos). */
  panelDe: number | null = null;
  /** Cargos con un cobro en vuelo. */
  readonly cobrando = new Set<number>();
  menuAbierto = false;
  /** Herramienta abierta en el panel lateral (decisión 270). */
  herramientaAbierta: SeccionHerramientas | null = null;
  /** «Generar informe» abierto en el panel lateral (8b-D2a). */
  informeAbierto = false;

  private readonly busqueda = new Subject<string>();
  private readonly suscripciones = new Subscription();
  private carga?: Subscription;
  /** Tras la primera carga, cada cambio de búsqueda, filtro, página o vista se escribe en la dirección. */
  private escribirDireccion = false;
  private cargaPendientes?: Subscription;
  /** Botón «Cobrar» que abrió el panel: recibe el foco al cerrarlo. */
  private disparadorPanel: HTMLElement | null = null;

  readonly opcionesEstado = OPCIONES_ESTADO;

  /** Con «Inactivos» o «Todos» salen alumnos dados de baja. */
  get inactivos(): boolean {
    return this.filtroEstado !== 'activos';
  }

  get hayFiltros(): boolean {
    return this.nombre.trim() !== '' || this.deporte !== '' || this.inactivos || this.aptos;
  }

  get contador(): string {
    return this.filtroEstado === 'inactivos' ? plural(this.total, 'inactivo', 'inactivos') : plural(this.total, 'alumno', 'alumnos');
  }

  get subtitulo(): string {
    const porPagina = `${numero(POR_PAGINA)} por página`;
    return this.activos === null ? porPagina : `${plural(this.activos, 'alumno activo', 'alumnos activos')}, ${porPagina}`;
  }

  get rango(): string {
    if (this.total === 0) {
      return '';
    }
    const desde = (this.pagina - 1) * POR_PAGINA + 1;
    const hasta = Math.min(this.pagina * POR_PAGINA, this.total);
    return `${numero(desde)}–${numero(hasta)} de ${numero(this.total)}`;
  }

  /** Páginas a la vista: la primera, la última y las vecinas de la actual; `null` es un salto («…»). */
  get paginas(): (number | null)[] {
    const total = this.totalPaginas;
    const visibles = new Set([1, total, this.pagina - 1, this.pagina, this.pagina + 1].filter((p) => p >= 1 && p <= total));
    const orden = [...visibles].sort((a, b) => a - b);
    const salida: (number | null)[] = [];
    orden.forEach((p, i) => {
      if (i > 0 && p - orden[i - 1] > 1) {
        salida.push(null);
      }
      salida.push(p);
    });
    return salida;
  }

  ngOnInit(): void {
    // La dirección manda (volver desde la ficha, recargar o compartir el enlace); si no trae vista, la recordada.
    const parametros = this.route.snapshot?.queryParamMap;
    this.nombre = (parametros?.get('nombre') ?? '').trim();
    this.deporte = parametros?.get('deporte') ?? '';
    // `inactivos=true` es el parámetro de antes de existir `estado`: abría todos los alumnos.
    const estadoUrl = parametros?.get('estado');
    this.filtroEstado =
      estadoUrl === 'inactivos' || estadoUrl === 'todos'
        ? estadoUrl
        : estadoUrl === null && parametros?.get('inactivos') === 'true'
          ? 'todos'
          : 'activos';
    this.aptos = parametros?.get('aptoParaExamen') === 'true';
    const pagina = Number(parametros?.get('pagina'));
    this.pagina = Number.isInteger(pagina) && pagina > 1 ? pagina : 1;
    const vista = parametros?.get('vista');
    this.vista = vista === 'tarjetas' || vista === 'filas' ? vista : leerVista();
    this.suscripciones.add(
      // Solo si el texto cambia frente al filtro vigente: «Quitar filtros» vacía la búsqueda sin repetir la carga.
      this.busqueda.pipe(debounceTime(300), filter((texto) => texto !== this.nombre)).subscribe((texto) => {
        this.nombre = texto;
        this.pagina = 1;
        this.cargar();
      })
    );
    this.contarActivos();
    this.cargar();
    this.escribirDireccion = true;
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.carga?.unsubscribe();
    this.cargaPendientes?.unsubscribe();
    this.busqueda.complete();
  }

  // ------------------------------------------------------------------ datos

  /**
   * El mismo `GET /api/alumnos` paginado que el listado de hoy, con los filtros en el servidor. Su fallo lo dice el
   * bloque de error con «Reintentar»: sin el SweetAlert2 del interceptor (8b-D2a).
   */
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargaPendientes?.unsubscribe();
    this.estado = 'cargando';
    if (this.escribirDireccion) {
      this.escribirEstado();
    }
    this.carga = this.lecturas
      .alumnos(this.pagina, POR_PAGINA, this.nombre.trim(), this.inactivos, this.aptos, this.deporte || null, this.filtroEstado)
      .subscribe({
        next: (respuesta) => {
          this.alumnos = Array.isArray(respuesta?.content) ? respuesta.content : [];
          this.total = Number(respuesta?.totalElements ?? this.alumnos.length) || 0;
          this.totalPaginas = Number(respuesta?.totalPages ?? 0) || (this.total > 0 ? Math.ceil(this.total / POR_PAGINA) : 0);
          this.abiertas.clear();
          this.panelDe = null;
          this.reconstruir();
          this.estado = 'listo';
          this.cargarPendientes();
        },
        error: () => {
          this.alumnos = [];
          this.filas = [];
          this.estado = 'error';
        },
      });
  }

  private contarActivos(): void {
    this.suscripciones.add(
      this.endpoints.obtenerAlumnos(1, 1, '', false, false).subscribe({
        next: (r) => (this.activos = Number(r?.totalElements ?? 0) || 0),
        error: () => (this.activos = null),
      })
    );
  }

  private cargarPendientes(): void {
    this.pendientes.clear();
    const ids = this.alumnos.map((a) => a.id).filter((id) => typeof id === 'number');
    if (ids.length === 0) {
      this.estadoPendientes = 'listo';
      return;
    }
    this.estadoPendientes = 'cargando';
    this.cargaPendientes = this.pendientesApi.obtener(ids).subscribe({
      next: (lista) => {
        (lista ?? []).forEach((p) => this.pendientes.set(p.alumnoId, p));
        this.estadoPendientes = 'listo';
      },
      error: () => (this.estadoPendientes = 'error'),
    });
  }

  /** Tras un cobro: solo el pendiente de ese alumno. */
  private refrescarPendiente(alumnoId: number): void {
    // En `suscripciones`: si la pantalla se cierra antes de la respuesta, no se toca una vista destruida.
    this.suscripciones.add(this.pendientesApi.obtener([alumnoId]).subscribe({
      next: (lista) => {
        const suyo = (lista ?? []).find((p) => p.alumnoId === alumnoId);
        if (suyo && suyo.cargos > 0) {
          this.pendientes.set(alumnoId, suyo);
        } else {
          this.pendientes.delete(alumnoId);
          if (this.panelDe === alumnoId) {
            this.cerrarPanel(false);
            this.enfocarEnFila(alumnoId, '.alumnos__desplegar');
          }
        }
        this.cdr.markForCheck();
      },
      error: () => this.avisos.error('No se pudo actualizar el pendiente del alumno'),
    }));
  }

  pendienteDe(alumnoId: number): PendienteAlumno | null {
    const p = this.pendientes.get(alumnoId);
    return p && p.cargos > 0 ? p : null;
  }

  // ------------------------------------------------------------------ filtros

  buscar(texto: string): void {
    this.busqueda.next(texto);
  }

  elegirDeporte(codigo: string): void {
    this.deporte = codigo;
    this.pagina = 1;
    this.cargar();
  }

  elegirEstado(estado: FiltroEstado): void {
    if (estado === this.filtroEstado) {
      return;
    }
    this.filtroEstado = estado;
    this.pagina = 1;
    this.cargar();
  }

  /** Como hoy: «Solo aptos» también queda en la dirección (`?aptoParaExamen=true`), con el resto del estado. */
  alternarAptos(): void {
    this.aptos = !this.aptos;
    this.pagina = 1;
    this.cargar();
  }

  quitarFiltros(): void {
    const buscador = this.host.nativeElement.querySelector<HTMLInputElement>('#alumnos-buscar');
    if (buscador) {
      buscador.value = '';
    }
    this.busqueda.next('');
    this.nombre = '';
    this.deporte = '';
    this.filtroEstado = 'activos';
    if (this.aptos) {
      this.alternarAptos();
      return;
    }
    this.pagina = 1;
    this.cargar();
  }

  irAPagina(pagina: number): void {
    if (pagina < 1 || pagina > this.totalPaginas || pagina === this.pagina || this.estado === 'cargando') {
      return;
    }
    this.pagina = pagina;
    this.cargar();
  }

  cambiarVista(vista: Vista): void {
    this.vista = vista;
    guardarVista(vista);
    this.escribirEstado();
  }

  /**
   * Búsqueda, disciplina, «Activos | Inactivos | Todos», «Solo aptos», página y vista en la dirección (sin apilar historial): al volver
   * desde la ficha («← Alumnos») el listado sale como estaba. Solo los valores que no son los de por defecto.
   */
  private escribirEstado(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        nombre: this.nombre.trim() || null,
        deporte: this.deporte || null,
        estado: this.filtroEstado === 'activos' ? null : this.filtroEstado,
        // Se limpia el parámetro de antes (`inactivos=true`) al reescribir la dirección.
        inactivos: null,
        aptoParaExamen: this.aptos ? 'true' : null,
        pagina: this.pagina > 1 ? String(this.pagina) : null,
        vista: this.vista === 'tarjetas' ? 'tarjetas' : null,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  // ------------------------------------------------------------------ fila desplegable

  estaAbierta(id: number): boolean {
    return this.abiertas.has(id);
  }

  alternarFila(id: number): void {
    if (this.abiertas.has(id)) {
      this.abiertas.delete(id);
    } else {
      this.abiertas.add(id);
    }
  }

  /** El mismo `PUT /alumnos/{id}/deportes/{deporte}/principal` que hoy. */
  marcarPrincipal(alumnoId: number, codigo: string): void {
    const alumno = this.alumnos.find((a) => a.id === alumnoId);
    const deportes: AlumnoDeporteDTO[] = alumno?.deportes ?? [];
    const elegido = deportes.find((d) => d.deporte === codigo);
    if (!elegido || elegido.activo === false) {
      this.avisos.error('No se puede marcar como principal un deporte inactivo');
      return;
    }
    this.alumnoService.establecerDeportePrincipal(alumnoId, codigo).subscribe({
      next: () => {
        alumno.deportes = deportes.map((d) => ({ ...d, principal: d.deporte === codigo }));
        this.reconstruir();
        this.avisos.exito(`Deporte principal actualizado a ${this.catalogo.etiquetaDisciplina(codigo)}`);
      },
      error: (error) =>
        this.avisos.error(
          typeof error?.error === 'string' && error.error ? error.error : 'No se pudo actualizar el deporte principal'
        ),
    });
  }

  darDeBaja(id: number): void {
    const nombre = this.filas.find((f) => f.id === id)?.nombre ?? '';
    void this.bajasAltas.darDeBaja(id, nombre, () => this.cargar());
  }

  darDeAlta(id: number): void {
    this.bajasAltas.darDeAlta(id, () => this.cargar());
  }

  // ------------------------------------------------------------------ cobrar

  /** Un cargo: la confirmación de hoy. Varios: el panel con cada cargo. */
  pulsarCobrar(alumnoId: number, evento: Event): void {
    const pendiente = this.pendienteDe(alumnoId);
    if (!pendiente) {
      return;
    }
    if (pendiente.detalle.length === 1) {
      this.cobrar(alumnoId, pendiente.detalle[0]);
      return;
    }
    if (this.panelDe === alumnoId) {
      this.cerrarPanel(true);
      return;
    }
    this.disparadorPanel = evento.currentTarget as HTMLElement;
    this.panelDe = alumnoId;
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('.alumnos__cargos .alumnos__cargo button')?.focus();
  }

  cerrarPanel(devolverFoco: boolean): void {
    if (this.panelDe === null) {
      return;
    }
    this.panelDe = null;
    const disparador = this.disparadorPanel;
    this.disparadorPanel = null;
    this.cdr.detectChanges();
    if (devolverFoco) {
      disparador?.focus();
    }
  }

  /** Panel modal: Esc lo cierra y devuelve el foco a «Cobrar»; Tab y Mayús+Tab dan la vuelta dentro del panel. */
  teclaPanel(evento: KeyboardEvent): void {
    teclaPanelCargos(evento, () => this.cerrarPanel(true));
  }

  /** El diálogo nuevo, con la misma llamada y el mismo cuerpo que «Marcar pagado» en la tesorería de hoy. */
  cobrar(alumnoId: number, cargo: CargoPendiente): void {
    const nombre = this.filas.find((f) => f.id === alumnoId)?.nombre ?? '';
    void this.cobros.cobrar({ ...cargo, alumnoNombre: nombre, tipo: null }, this.cobrando, () => {
      this.refrescarPendiente(alumnoId);
      this.cdr.markForCheck();
    });
  }

  private enfocarEnFila(alumnoId: number, selector: string): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`[data-alumno="${alumnoId}"] ${selector}`)?.focus();
  }

  // ------------------------------------------------------------------ informes

  alternarMenu(evento?: KeyboardEvent): void {
    this.menuAbierto = !this.menuAbierto;
    if (this.menuAbierto) {
      this.cdr.detectChanges();
      enfocarOpcion(this.menuInformes?.nativeElement ?? null, evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
    }
  }

  teclaBotonMenu(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuAbierto) {
      evento.preventDefault();
      this.alternarMenu(evento);
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const menu = this.menuInformes?.nativeElement;
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenu(true);
    } else if (accion === 'salir') {
      this.cerrarMenu(false);
    }
  }

  cerrarMenu(devolverFoco: boolean): void {
    this.menuAbierto = false;
    if (devolverFoco) {
      this.botonInformes?.nativeElement.focus();
    }
  }

  /** Fuera del menú, el clic lo cierra. */
  alSalirDelMenu(evento: FocusEvent): void {
    const zona = (evento.currentTarget as HTMLElement | null) ?? null;
    const destino = evento.relatedTarget as Node | null;
    if (this.menuAbierto && zona && destino && !zona.contains(destino)) {
      this.menuAbierto = false;
    }
  }

  /** «Generar informe…»: su sección en el panel lateral (hoja inferior en el móvil). */
  abrirInformes(): void {
    this.cerrarMenu(false);
    this.informeAbierto = true;
  }

  /** Al cerrar el panel, el foco vuelve a «Informes». */
  cerrarInformes(): void {
    this.informeAbierto = false;
    this.cdr.detectChanges();
    this.botonInformes?.nativeElement.focus();
  }

  /** Desde «Informes»: la herramienta en el panel lateral (hoja inferior en el móvil). */
  abrirHerramienta(seccion: SeccionHerramientas): void {
    this.cerrarMenu(false);
    this.herramientaAbierta = seccion;
  }

  /** Al cerrar el panel, el foco vuelve a «Informes» (la opción del menú que lo abrió ya no está). */
  cerrarHerramienta(): void {
    this.herramientaAbierta = null;
    this.cdr.detectChanges();
    this.botonInformes?.nativeElement.focus();
  }

  // ------------------------------------------------------------------ vista

  private reconstruir(): void {
    const hoy = new Date();
    this.filas = this.alumnos.map((a) => this.fila(a, hoy));
  }

  private fila(a: any, hoy: Date): FilaAlumno {
    const todos: AlumnoDeporteDTO[] = Array.isArray(a?.deportes) ? a.deportes : [];
    const activos = ordenarDeportes(todos.filter((d) => d.activo !== false));
    const principal = principalDe(activos);
    const detalle = a?.activo === false ? ordenarDeportes(todos) : activos;
    const edad = a?.fechaNacimiento ? calcularEdad(a.fechaNacimiento) : NaN;
    return {
      id: a.id,
      nombre: `${a?.nombre ?? ''} ${a?.apellidos ?? ''}`.trim(),
      iniciales: iniciales(a?.nombre, a?.apellidos),
      foto: fotoDe(a),
      expediente: a?.numeroExpediente != null ? String(a.numeroExpediente) : '',
      edad: Number.isFinite(edad) ? plural(edad, 'año', 'años') : '',
      fechaBaja: a?.fechaBaja ? fecha(a.fechaBaja) : '',
      activo: activos.length > 0,
      deportes: activos.map((d, i) => this.deporteFila(d, i === 0, hoy)),
      detalle: detalle.map((d, i) => this.deporteFila(d, i === 0 && d.activo !== false, hoy)),
      tarifa: principal ? this.nombreTarifa(principal.tipoTarifa) : '',
      antiguedad: principal ? antiguedad(principal.fechaAltaInicial || principal.fechaAlta, hoy) : '',
      licencia: estadoLicencia(principal, principal ? this.catalogo.requiereLicencia(principal.deporte) : false, hoy),
    };
  }

  private deporteFila(d: AlumnoDeporteDTO, principal: boolean, hoy: Date): DeporteFila {
    const conGrados = !this.catalogo.sinGrados(d.deporte);
    return {
      codigo: d.deporte,
      nombre: this.catalogo.etiquetaDisciplina(d.deporte),
      claseDisciplina: this.claseDisciplina(d.deporte),
      principal,
      activo: d.activo !== false,
      grado: conGrados && d.grado ? gradoFila(d.grado, d.deporte) : null,
      sinGrado: conGrados && !d.grado,
      tiempoConGrado: conGrados ? tiempoConGrado(d.fechaGrado, hoy) : '',
      tarifa: this.nombreTarifa(d.tipoTarifa),
      alta: fecha(d.fechaAltaInicial || d.fechaAlta),
      antiguedad: antiguedad(d.fechaAltaInicial || d.fechaAlta, hoy),
      categoria: conGrados ? d.categoria || 'N/A' : null,
    };
  }

  private nombreTarifa(codigo: string | null | undefined): string {
    if (!codigo) {
      return 'Sin tarifa';
    }
    return this.catalogo.tarifa(codigo)?.nombre ?? codigo;
  }

  /** «disc-1» a «disc-4» por el orden del catálogo (DESIGN.md); una quinta disciplina, sin color propio. */
  private claseDisciplina(codigo: string): string {
    const i = this.catalogo.disciplinasActivas().findIndex((d) => d.codigo === codigo);
    return i >= 0 && i < 4 ? `alumnos__disc--${i + 1}` : 'alumnos__disc--otra';
  }
}

function leerVista(): Vista {
  try {
    return localStorage.getItem(CLAVE_VISTA) === 'tarjetas' ? 'tarjetas' : 'filas';
  } catch {
    return 'filas';
  }
}

function guardarVista(vista: Vista): void {
  try {
    localStorage.setItem(CLAVE_VISTA, vista);
  } catch {
    // Sin almacenamiento (modo privado o bloqueado): la vista vale para esta visita.
  }
}
