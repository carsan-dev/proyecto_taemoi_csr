import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, Subscription, forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, map, takeUntil } from 'rxjs/operators';

import { euros } from '../../componentes/generales/pedir-al-propietario/pedir-al-propietario';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AlumnoDTO } from '../../interfaces/alumno-dto';
import { PaginatedResponse } from '../../interfaces/paginated-response';
import { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { TesoreriaMovimiento } from '../../interfaces/tesoreria-movimiento';
import { TesoreriaResumen } from '../../interfaces/tesoreria-resumen';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../servicios/endpoints/solicitudes.service';
import { AccionInforme, InformePdfService } from '../../servicios/generales/informe-pdf.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { InformePdfPanelService } from '../alumnos/herramientas/informe-pdf-panel.service';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { MascotaComponent } from '../mascota/mascota.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { CobroCargoService } from '../cobro/cobro-cargo';
import { ReciboPagoService } from '../cobro/recibo-pago.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ParteMensaje } from '../dialogo/dialogo.component';
import { eur, fecha, frasePlural, numero } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { VisorPdfComponent } from '../visor-pdf/visor-pdf.component';
import {
  EstadoFiltro,
  MESES,
  PESTANAS,
  barraCobro,
  etiquetaPeriodo,
  fechaHora,
  fechaPagoParaCampo,
  nombreExportacion,
  pagadoDeEstado,
  textoPorcentaje,
} from './tesoreria-vista';

type Estado = 'cargando' | 'listo' | 'error';

/** 25 por página, como hoy. */
export const POR_PAGINA = 25;

const SUBTITULO = 'Control de deudas, pagos pendientes y estado mensual por deporte.';

/**
 * Tesorería del panel nuevo (8b-3d, Tesorería C): filtros en una línea, resumen con barra cobrado / pendiente, pestañas
 * Pendientes | Cobrados | Todos en lugar del filtro «Estado», filas de dos líneas con «Cobrar» y un «⋯» con notas,
 * cambiar fecha de pago y revertir o pedir revertir, y «Exportar» en un menú. Las MISMAS peticiones que la pantalla de
 * hoy (`TesoreriaCobrosComponent`): parámetros, cuerpos, nombres de archivo y regla del rol; con el diálogo, los avisos,
 * el panel lateral, el visor de PDF y los estados de carga del panel nuevo, sin SweetAlert2 ni cargador global. Va en su
 * propio chunk detrás de `estilosPanelGuard`.
 */
@Component({
  selector: 'app-tesoreria',
  standalone: true,
  imports: [
    RouterLink,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    MascotaComponent,
    PanelLateralComponent,
    VisorPdfComponent,
  ],
  providers: [{ provide: InformePdfService, useClass: InformePdfPanelService }],
  templateUrl: './tesoreria.component.html',
  styleUrls: ['./tesoreria.component.scss'],
})
export class TesoreriaComponent implements OnInit, OnDestroy {
  readonly informes = inject(InformePdfService);
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  readonly catalogo = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cobro = inject(CobroCargoService);
  private readonly recibo = inject(ReciboPagoService);
  private readonly auth = inject(AuthenticationService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly solicitudes = inject(SolicitudesService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  @ViewChild('botonExportar', { read: ElementRef }) private botonExportar?: ElementRef<HTMLButtonElement>;
  @ViewChild('menuExportarRef') private menuExportarRef?: ElementRef<HTMLElement>;

  readonly meses = MESES;
  readonly pestanas = PESTANAS;
  readonly porPagina = POR_PAGINA;
  readonly eur = eur;
  readonly fecha = fecha;
  readonly numero = numero;
  readonly etiquetaPeriodo = etiquetaPeriodo;
  readonly textoPorcentaje = textoPorcentaje;

  anosDisponibles: number[] = [];
  certAnos: number[] = [];
  filtroMes: number | null = new Date().getMonth() + 1;
  filtroAno: number | null = new Date().getFullYear();
  filtroDeporte = 'TODOS';
  filtroEstado: EstadoFiltro = 'PENDIENTES';
  filtroTexto = '';
  filtroSoloActivos = true;

  /** La carga entera (resumen y lista). */
  estado: Estado = 'cargando';
  /** La lista sola (página, búsqueda). */
  estadoLista: Estado = 'listo';
  isAdmin = false;
  /** Propietario o administración (no «solo profesor» ni familias): ve «Descargar recibo» (pieza 279). */
  esGestion = false;
  fechaCarga = new Date();
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
  /** 8b-M: el último cobro dejó el mes sin nada pendiente (una de las dos veces que la mascota celebra con dinero). */
  mesCobradoEntero = false;
  pagina = 1;
  totalPaginas = 0;
  totalMovimientos = 0;
  /** Cargos con una escritura en vuelo (cobro incluido): sin doble envío. */
  readonly enProceso = new Set<number>();

  private _menuExportar = false;
  private _menuFila: number | null = null;
  private escuchaFuera = false;

  get menuExportar(): boolean {
    return this._menuExportar;
  }

  set menuExportar(v: boolean) {
    this._menuExportar = v;
    this.sincronizarEscucha();
  }

  get menuFila(): number | null {
    return this._menuFila;
  }

  set menuFila(v: number | null) {
    this._menuFila = v;
    this.sincronizarEscucha();
  }

  /** El clic fuera de un menú abierto lo cierra, también en zonas sin foco; solo se escucha mientras hay uno abierto. */
  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zonas = [
      this._menuExportar ? this.botonExportar?.nativeElement.closest('.tesoreria__menu-zona') : null,
      this._menuFila !== null ? this.botonMas(this._menuFila)?.closest('.tesoreria__menu-zona') : null,
    ];
    if (destino && zonas.some((z) => z?.contains(destino))) {
      return;
    }
    this._menuExportar = false;
    this._menuFila = null;
    this.sincronizarEscucha();
    this.cdr.markForCheck();
  };

  private sincronizarEscucha(): void {
    const abierto = this._menuExportar || this._menuFila !== null;
    if (abierto && !this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = true;
    } else if (!abierto && this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  certAbierto = false;
  certEstado: 'inicial' | Estado = 'inicial';
  certAlumnos: AlumnoDTO[] = [];
  certSeleccion: number[] = [];
  certFiltro = '';
  certAno = new Date().getFullYear();
  generandoCert = false;

  private carga?: Subscription;
  private readonly destroy$ = new Subject<void>();
  private readonly texto$ = new Subject<string>();

  get subtitulo(): string {
    return `${SUBTITULO} Actualizado ${fechaHora(this.fechaCarga)}`;
  }

  get deportes(): Array<{ value: string; label: string }> {
    return [
      { value: 'TODOS', label: 'Todos los deportes' },
      ...this.catalogo.disciplinasActivas().map((d) => ({ value: d.codigo, label: d.nombre })),
    ];
  }

  get cargando(): boolean {
    return this.estado === 'cargando' || this.estadoLista === 'cargando';
  }

  get barra() {
    return barraCobro(this.resumen.importePagado, this.resumen.importePendiente);
  }

  get pestanaActual(): string {
    return this.pestanas.find((p) => p.estado === this.filtroEstado)?.clave ?? 'pendientes';
  }

  get mostrando(): string {
    return `Mostrando ${numero(this.movimientos.length)} de ${numero(this.totalMovimientos)} movimientos`;
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
    this.resolverPermisos();
    this.texto$.pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$)).subscribe(() => this.cargar(false));
    this.anosDisponibles = [new Date().getFullYear()];
    this.filtroAno = this.anoPorDefecto();
    this.aplicarParametros();
    this.cargar();
    this.lecturas
      .tesoreriaAnios()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (anios) => {
          this.anosDisponibles = normalizarAnios(anios);
          if (this.filtroAno !== null && !this.anosDisponibles.includes(this.filtroAno)) {
            this.anosDisponibles = [...this.anosDisponibles, this.filtroAno].sort((a, b) => a - b);
          }
        },
        // Hoy su fallo se ignora: se queda el año actual.
        error: () => undefined,
      });
  }

  ngOnDestroy(): void {
    this.carga?.unsubscribe();
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ lecturas

  /**
   * Las lecturas de hoy: resumen y movimientos juntos (`forkJoin`), o solo los movimientos al cambiar de página o al
   * buscar. `silencioso` (tras una escritura) relee sin esqueleto, como cuando hoy se vuelve a cargar tras un cambio.
   */
  cargar(conResumen = true, silencioso = false): void {
    this.normalizarPeriodo();
    this.carga?.unsubscribe();
    const movimientos$ = this.lecturas.tesoreriaMovimientos(
      this.filtroMes,
      this.filtroAno,
      this.filtroDeporte,
      pagadoDeEstado(this.filtroEstado),
      this.filtroTexto,
      this.filtroSoloActivos,
      this.pagina,
      POR_PAGINA
    );
    if (!conResumen) {
      if (!silencioso) {
        this.estadoLista = 'cargando';
      }
      this.carga = movimientos$.subscribe({
        next: (respuesta) => {
          this.ponerMovimientos(respuesta);
          this.fechaCarga = new Date();
          this.estadoLista = 'listo';
        },
        error: () => {
          this.estadoLista = 'error';
        },
      });
      return;
    }
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.carga = forkJoin({
      resumen: this.lecturas.tesoreriaResumen(this.filtroMes, this.filtroAno, this.filtroDeporte, this.filtroSoloActivos),
      movimientos: movimientos$,
    }).subscribe({
      next: ({ resumen, movimientos }) => {
        // 8b-M: «mes cobrado entero» solo en el instante en que un cobro deja el mes sin nada pendiente.
        const anterior = this.resumen;
        this.mesCobradoEntero =
          silencioso &&
          this.estado === 'listo' &&
          resumen.mes !== null &&
          anterior.mes === resumen.mes &&
          anterior.ano === resumen.ano &&
          anterior.importePendiente > 0 &&
          resumen.importePendiente === 0 &&
          resumen.totalPagados > 0;
        this.resumen = resumen;
        this.ponerMovimientos(movimientos);
        this.fechaCarga = new Date();
        this.estado = 'listo';
        this.estadoLista = 'listo';
      },
      error: () => {
        if (silencioso) {
          this.avisos.error('No se pudo cargar la información de tesorería');
          // Si la relectura cortó una carga de página, la lista no llegó a cargarse: bloque de error con «Reintentar».
          if (this.estadoLista === 'cargando') {
            this.estadoLista = 'error';
          }
        } else {
          this.estado = 'error';
        }
      },
    });
  }

  private ponerMovimientos(respuesta: PaginatedResponse<TesoreriaMovimiento> | null | undefined): void {
    const contenido = respuesta?.content ?? [];
    this.movimientos = contenido;
    this.totalPaginas = respuesta?.totalPages ?? 0;
    this.totalMovimientos = respuesta?.totalElements ?? contenido.length;
    if (this.totalPaginas === 0) {
      this.pagina = 1;
    } else if (this.pagina > this.totalPaginas) {
      this.pagina = this.totalPaginas;
    }
  }

  // ------------------------------------------------------------------ filtros

  elegirMes(valor: string): void {
    this.filtroMes = valor === '' ? null : Number(valor);
  }

  elegirAno(valor: string): void {
    this.filtroAno = valor === '' ? null : Number(valor);
    this.normalizarPeriodo();
  }

  elegirDeporte(valor: string): void {
    this.filtroDeporte = valor;
  }

  alternarActivos(evento: Event): void {
    this.filtroSoloActivos = (evento.target as HTMLInputElement).checked;
  }

  /** Como hoy: la búsqueda espera 300 ms y pide solo los movimientos, desde la página 1. */
  buscar(valor: string): void {
    this.filtroTexto = valor;
    this.pagina = 1;
    this.texto$.next(valor);
  }

  aplicar(): void {
    this.normalizarPeriodo();
    this.pagina = 1;
    this.cargar();
  }

  restablecer(): void {
    this.filtroMes = new Date().getMonth() + 1;
    this.filtroAno = this.anoPorDefecto();
    this.filtroDeporte = 'TODOS';
    this.filtroEstado = 'PENDIENTES';
    this.filtroTexto = '';
    this.filtroSoloActivos = true;
    this.pagina = 1;
    this.cargar();
  }

  etiquetaDeporte(deporte: string): string {
    if (deporte === 'TODOS') {
      return 'Todos los deportes';
    }
    if (deporte === 'GENERAL') {
      return 'General';
    }
    return this.catalogo.etiquetaDisciplina(deporte);
  }

  // ------------------------------------------------------------------ pestañas (sustituyen al filtro «Estado»)

  elegirPestana(estado: EstadoFiltro): void {
    if (estado === this.filtroEstado) {
      return;
    }
    this.filtroEstado = estado;
    this.aplicar();
  }

  teclaPestanas(evento: KeyboardEvent): void {
    const actual = Math.max(
      0,
      this.pestanas.findIndex((p) => p.estado === this.filtroEstado)
    );
    const n = this.pestanas.length;
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
        siguiente = (actual + 1) % n;
        break;
      case 'ArrowLeft':
        siguiente = (actual - 1 + n) % n;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = n - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.elegirPestana(this.pestanas[siguiente].estado);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelectorAll<HTMLElement>('[role="tab"]')[siguiente]?.focus();
  }

  irAPagina(pagina: number): void {
    if (this.cargando || pagina === this.pagina || pagina < 1 || pagina > this.totalPaginas) {
      return;
    }
    this.pagina = pagina;
    this.cargar(false);
  }

  // ------------------------------------------------------------------ fila

  meta(m: TesoreriaMovimiento): string {
    return [this.etiquetaDeporte(m.deporte), m.categoria, m.fechaAsignacion ? `Cargado ${fecha(m.fechaAsignacion)}` : '']
      .filter((p) => !!p)
      .join(' · ');
  }

  ocupado(m: TesoreriaMovimiento): boolean {
    return this.enProceso.has(m.productoAlumnoId);
  }

  sinId(m: TesoreriaMovimiento): boolean {
    return m.productoAlumnoId === null || m.productoAlumnoId === undefined;
  }

  /** «Cobrar»: el diálogo y la llamada de `CobroCargoService` (el `PUT` de «Marcar pagado» de hoy); después, relee. */
  cobrar(m: TesoreriaMovimiento): void {
    if (m.pagado || this.sinId(m) || this.ocupado(m)) {
      return;
    }
    void this.cobro.cobrar(
      { productoAlumnoId: m.productoAlumnoId, concepto: m.concepto, importe: m.precio, alumnoNombre: m.alumnoNombreCompleto },
      this.enProceso,
      () => this.cargar(true, true)
    );
  }

  // ------------------------------------------------------------------ menú «⋯» de la fila

  private menuDeFila(id: number): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`#tesoreria-menu-fila-${id}`);
  }

  private botonMas(id: number): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>(`[data-mas="${id}"]`);
  }

  alternarMenuFila(id: number, evento?: KeyboardEvent): void {
    if (this.menuFila === id) {
      this.menuFila = null;
      return;
    }
    this.menuExportar = false;
    this.menuFila = id;
    this.cdr.detectChanges();
    enfocarOpcion(this.menuDeFila(id), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonFila(evento: KeyboardEvent, id: number): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && this.menuFila !== id) {
      evento.preventDefault();
      this.alternarMenuFila(id, evento);
    }
  }

  teclaMenuFila(evento: KeyboardEvent, id: number): void {
    const menu = this.menuDeFila(id);
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenuFila(true);
    } else if (accion === 'salir') {
      this.cerrarMenuFila(false);
    }
  }

  cerrarMenuFila(devolverFoco: boolean): void {
    const id = this.menuFila;
    this.menuFila = null;
    if (devolverFoco && id !== null) {
      this.cdr.detectChanges();
      this.botonMas(id)?.focus();
    }
  }

  /** Fuera del menú (foco o clic), se cierra. */
  alSalirDeZona(evento: FocusEvent, cual: 'fila' | 'exportar'): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      if (cual === 'fila') {
        this.menuFila = null;
      } else {
        this.menuExportar = false;
      }
    }
  }

  /** Cada opción cierra el menú y deja el foco en «⋯», para que el diálogo lo devuelva allí al cerrarse. */
  opcionFila(m: TesoreriaMovimiento, opcion: 'notas' | 'fecha' | 'recibo' | 'revertir' | 'pedir'): void {
    this.cerrarMenuFila(true);
    switch (opcion) {
      case 'notas':
        void this.editarNotas(m);
        break;
      case 'fecha':
        void this.editarFechaPago(m);
        break;
      case 'recibo':
        void this.descargarRecibo(m);
        break;
      case 'revertir':
        void this.revertir(m);
        break;
      case 'pedir':
        void this.pedirRevertir(m);
        break;
    }
  }

  // ------------------------------------------------------------------ escrituras (las de hoy)

  /** «Editar notas»: el diálogo de hoy (área de 255) y `PUT /productos-alumno/{id}` con `{notas}`. */
  async editarNotas(m: TesoreriaMovimiento): Promise<void> {
    if (this.sinId(m) || this.ocupado(m)) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Editar notas',
      accion: 'Guardar',
      cancelar: 'Cancelar',
      nombre: 'notas',
      campos: [
        { id: 'notas', tipo: 'textarea', valor: m.notas ?? '', placeholder: 'Escribe una nota...', atributos: { maxlength: '255' } },
      ],
    });
    if (!r.confirmado || !r.valores) {
      return;
    }
    const notas = (r.valores['notas'] ?? '').toString();
    this.actualizar(m, { notas }, 'Notas actualizadas correctamente', 'No se pudieron actualizar las notas');
  }

  /** «Cambiar fecha de pago»: el campo de fecha de hoy, obligatorio, y `{fechaPago}` a mediodía de ese día. */
  async editarFechaPago(m: TesoreriaMovimiento): Promise<void> {
    if (!m.pagado || this.sinId(m) || this.ocupado(m)) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Editar fecha de pago',
      accion: 'Guardar',
      cancelar: 'Cancelar',
      nombre: 'fecha-pago',
      campos: [{ id: 'fechaPago', tipo: 'date', valor: fechaPagoParaCampo(m.fechaPago) }],
      comprobar: (v) => (v['fechaPago'] ? null : { campo: 'fechaPago', mensaje: 'Debes indicar una fecha de pago' }),
    });
    const valor = r.valores?.['fechaPago'];
    if (!r.confirmado || !valor) {
      return;
    }
    const fechaPago = new Date(`${valor}T12:00:00`);
    this.actualizar(m, { fechaPago } as Partial<ProductoAlumnoDTO>, 'Fecha de pago actualizada', 'No se pudo actualizar la fecha de pago');
  }

  /** «Descargar recibo» (gestión y responsable de sede) de un cobro: `GET /productos-alumno/{id}/recibo`. */
  async descargarRecibo(m: TesoreriaMovimiento): Promise<void> {
    if (!this.esGestion || !m.pagado || m.devuelto || this.sinId(m)) {
      return;
    }
    await this.recibo.descargar(m.productoAlumnoId);
  }

  /** «Revertir» (propietario): destructivo, motivo opcional y `{pagado: false, motivoCambio}`, como hoy. */
  async revertir(m: TesoreriaMovimiento): Promise<void> {
    if (!this.isAdmin || !m.pagado || m.devuelto || this.sinId(m) || this.ocupado(m)) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Revertir cobro',
      mensaje: `El cobro "${m.concepto}" pasará a pendiente.`,
      accion: 'Sí, revertir',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'revertir',
      campos: [
        { id: 'motivo', tipo: 'textarea', etiqueta: 'Motivo (opcional)', valor: '', placeholder: 'Ejemplo: error de marcación' },
      ],
    });
    if (!r.confirmado) {
      return;
    }
    const motivoCambio = (r.valores?.['motivo'] ?? '').toString().trim() || null;
    this.actualizar(m, { pagado: false, motivoCambio }, 'Cobro revertido a pendiente', 'No se pudo revertir el cobro');
  }

  /**
   * «Pedir revertir» (gestión, decisión 190): la solicitud de la 6c (`pedirAlPropietario` de hoy) con el diálogo del
   * panel: mismos datos, nota y motivo obligatorio, y el mismo `POST /solicitudes`.
   */
  async pedirRevertir(m: TesoreriaMovimiento): Promise<void> {
    if (this.isAdmin || !m.pagado || m.devuelto || this.sinId(m)) {
      return;
    }
    const pago = m.fechaPago ? new Date(m.fechaPago) : null;
    const datos: [string, string][] = [
      ['Alumno', m.alumnoNombreCompleto],
      ['Concepto', m.concepto],
      ['Importe', euros(m.precio)],
      ['Fecha de pago', pago && !Number.isNaN(pago.getTime()) ? pago.toLocaleDateString('es-ES') : ''],
    ];
    const elementos: ParteMensaje[][] = datos
      .filter(([, valor]) => valor?.trim())
      .map(([etiqueta, valor]) => [{ fuerte: etiqueta }, `: ${valor}`]);
    const r = await this.confirmacion.confirmar({
      titulo: 'Pedir al propietario que revierta este cobro',
      bloques: [
        { tipo: 'lista', elementos },
        { tipo: 'parrafo', partes: ['Mientras tanto el pago sigue marcado.'] },
      ],
      accion: 'Enviar solicitud',
      cancelar: 'Cancelar',
      nombre: 'pedir-revertir',
      campos: [
        {
          id: 'motivo',
          tipo: 'textarea',
          etiqueta: 'Motivo',
          valor: '',
          placeholder: 'Qué ha pasado y por qué hay que cambiarlo',
          atributos: { maxlength: '500', 'aria-label': 'Motivo de la solicitud' },
        },
      ],
      comprobar: (v) =>
        (v['motivo'] ?? '').trim() ? null : { campo: 'motivo', mensaje: 'Escribe el motivo: el propietario lo verá al decidir' },
    });
    if (!r.confirmado || !r.valores) {
      return;
    }
    const motivo = (r.valores['motivo'] ?? '').trim();
    this.solicitudes
      .crear({ tipo: 'REVERTIR_COBRO', productoAlumnoId: m.productoAlumnoId, motivo })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.avisos.exito('Solicitud enviada al propietario'),
        error: (error) => this.avisos.error(mensajeDeError(error) ?? 'No se ha podido enviar la solicitud.'),
      });
  }

  /** `actualizarCobro` de hoy: un cambio a la vez por cargo, aviso y relectura; el 403 dice quién puede. */
  private actualizar(m: TesoreriaMovimiento, cambios: Partial<ProductoAlumnoDTO>, ok: string, fallo: string): void {
    const id = m.productoAlumnoId;
    if (this.enProceso.has(id)) {
      return;
    }
    this.enProceso.add(id);
    this.endpoints
      .actualizarCobroTesoreria(id, cambios)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.enProceso.delete(id);
          this.avisos.exito(ok);
          this.cargar(true, true);
        },
        error: (error: HttpErrorResponse) => {
          this.enProceso.delete(id);
          this.avisos.error(error?.status === 403 ? 'Solo administradores pueden revertir cobros' : fallo);
        },
      });
  }

  // ------------------------------------------------------------------ «Exportar»

  alternarMenuExportar(evento?: KeyboardEvent): void {
    this.menuExportar = !this.menuExportar;
    if (this.menuExportar) {
      this.menuFila = null;
      this.cdr.detectChanges();
      enfocarOpcion(this.menuExportarRef?.nativeElement ?? null, evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
    }
  }

  teclaBotonExportar(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuExportar) {
      evento.preventDefault();
      this.alternarMenuExportar(evento);
    }
  }

  teclaMenuExportar(evento: KeyboardEvent): void {
    const menu = this.menuExportarRef?.nativeElement;
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenuExportar(true);
    } else if (accion === 'salir') {
      this.cerrarMenuExportar(false);
    }
  }

  cerrarMenuExportar(devolverFoco: boolean): void {
    this.menuExportar = false;
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.botonExportar?.nativeElement.focus();
    }
  }

  /** «Ver PDF», «Exportar PDF» y «Exportar CSV»: la petición, el título y el nombre de hoy, con los filtros a la vista. */
  exportar(formato: 'pdf' | 'csv', accion: AccionInforme = 'descargar'): void {
    this.cerrarMenuExportar(true);
    if (this.informes.ocupado || this.informes.destruido) {
      return;
    }
    const pagado = pagadoDeEstado(this.filtroEstado);
    const solicitud =
      formato === 'pdf'
        ? this.endpoints.exportarTesoreriaPDF(this.filtroMes, this.filtroAno, this.filtroDeporte, pagado, this.filtroTexto, this.filtroSoloActivos)
        : this.endpoints.exportarTesoreriaCSV(this.filtroMes, this.filtroAno, this.filtroDeporte, pagado, this.filtroTexto, this.filtroSoloActivos);
    const nombreArchivo = nombreExportacion(
      { mes: this.filtroMes, ano: this.filtroAno, deporte: this.filtroDeporte, estado: this.filtroEstado, soloActivos: this.filtroSoloActivos },
      formato
    );
    this.informes.generar(
      solicitud.pipe(map((blob) => ({ documentos: [{ blob, nombreArchivo, titulo: 'Deudas de tesorería' }] }))),
      accion,
      `No se pudo generar el informe ${formato.toUpperCase()} de tesoreria`
    );
  }

  // ------------------------------------------------------------------ certificados (panel lateral)

  abrirCertificados(): void {
    this.cerrarMenuExportar(false);
    this.certAbierto = true;
    this.certAno = this.filtroAno ?? new Date().getFullYear();
    // El selector enseña exactamente el año que se enviará: si no está entre los disponibles, se añade.
    this.certAnos = this.anosDisponibles.includes(this.certAno)
      ? [...this.anosDisponibles].sort((a, b) => b - a)
      : [...this.anosDisponibles, this.certAno].sort((a, b) => b - a);
    this.certFiltro = '';
    this.certSeleccion = [];
    this.cargarAlumnosCert();
  }

  cerrarCertificados(): void {
    if (this.generandoCert) {
      return;
    }
    this.certAbierto = false;
    this.cdr.detectChanges();
    this.botonExportar?.nativeElement.focus();
  }

  /** Los alumnos activos, una vez (`obtenerTodosLosAlumnosSinPaginar(false)` de hoy), por nombre. */
  cargarAlumnosCert(): void {
    if (this.certAlumnos.length > 0 || this.certEstado === 'cargando') {
      return;
    }
    this.certEstado = 'cargando';
    this.lecturas
      .alumnosSinPaginar(false)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (alumnos) => {
          this.certAlumnos = (alumnos ?? [])
            .filter((a) => a?.id)
            .sort((a, b) => nombreAlumno(a).localeCompare(nombreAlumno(b)));
          this.certEstado = 'listo';
        },
        error: () => {
          this.certEstado = 'error';
        },
      });
  }

  readonly nombreAlumno = nombreAlumno;

  get certVisibles(): AlumnoDTO[] {
    const t = this.certFiltro.trim().toLowerCase();
    return t ? this.certAlumnos.filter((a) => nombreAlumno(a).toLowerCase().includes(t)) : this.certAlumnos;
  }

  get certContador(): string {
    return frasePlural(this.certSeleccion.length, '{n} seleccionado', '{n} seleccionados');
  }

  elegirAnoCert(valor: string): void {
    this.certAno = Number(valor);
  }

  filtrarCert(valor: string): void {
    this.certFiltro = valor;
  }

  alternarCert(id: number, marcado: boolean): void {
    if (marcado) {
      if (!this.certSeleccion.includes(id)) {
        this.certSeleccion = [...this.certSeleccion, id];
      }
      return;
    }
    this.certSeleccion = this.certSeleccion.filter((x) => x !== id);
  }

  seleccionarVisiblesCert(): void {
    this.certSeleccion = Array.from(new Set([...this.certSeleccion, ...this.certVisibles.map((a) => a.id)]));
  }

  limpiarCert(): void {
    this.certSeleccion = [];
  }

  /** `POST /tesoreria/certificados-cobros` de hoy: un PDF con un alumno y un ZIP con varios, con los nombres de hoy. */
  generarCertificados(): void {
    if (this.generandoCert) {
      return;
    }
    if (!this.certAno) {
      this.avisos.error('Debes seleccionar un año');
      return;
    }
    if (this.certSeleccion.length === 0) {
      this.avisos.error('Debes seleccionar al menos un alumno');
      return;
    }
    this.generandoCert = true;
    const total = this.certSeleccion.length;
    const ano = this.certAno;
    this.endpoints
      .generarCertificadosCobros(this.certSeleccion, ano)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.generandoCert = false)))
      .subscribe({
        next: (blob: Blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = total > 1 ? `certificados_cobros_${ano}.zip` : `certificado_cobros_${ano}.pdf`;
          a.click();
          globalThis.URL.revokeObjectURL(url);
          this.generandoCert = false;
          this.cerrarCertificados();
          this.avisos.exito('Certificados generados correctamente');
        },
        error: () => {
          this.avisos.error('No se pudieron generar los certificados');
        },
      });
  }

  // ------------------------------------------------------------------ como hoy

  private aplicarParametros(): void {
    const p = this.route.snapshot?.queryParamMap;
    if (!p) {
      return;
    }
    const mes = entero(p.get('mes'));
    const ano = entero(p.get('ano'));
    const estado = p.get('estado');
    const deporte = p.get('deporte');
    const soloActivos = booleano(p.get('soloActivos'));
    if (ano !== null && ano >= 1900 && ano <= 2200) {
      this.filtroAno = ano;
    }
    if (mes !== null && mes >= 1 && mes <= 12) {
      this.filtroMes = mes;
    }
    if (estado && this.pestanas.some((x) => x.estado === estado)) {
      this.filtroEstado = estado as EstadoFiltro;
    }
    if (deporte && this.deportes.some((d) => d.value === deporte)) {
      this.filtroDeporte = deporte;
    }
    if (soloActivos !== null) {
      this.filtroSoloActivos = soloActivos;
    }
    this.normalizarPeriodo();
  }

  private anoPorDefecto(): number | null {
    const actual = new Date().getFullYear();
    if (this.anosDisponibles.includes(actual)) {
      return actual;
    }
    return this.anosDisponibles.length > 0 ? this.anosDisponibles[this.anosDisponibles.length - 1] : actual;
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

  private veRecibos(): boolean {
    return this.auth.tieneAccesoAdmin() || this.permisos.tiene('PANEL_SEDE');
  }

  private resolverPermisos(): void {
    if (this.auth.rolesEstanCargados()) {
      this.isAdmin = this.auth.tieneRolAdmin();
      this.esGestion = this.veRecibos();
      return;
    }
    this.auth
      .obtenerRoles()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.isAdmin = this.auth.tieneRolAdmin();
          this.esGestion = this.veRecibos();
        },
        error: () => {
          this.isAdmin = false;
          this.esGestion = false;
        },
      });
  }
}

function nombreAlumno(alumno: AlumnoDTO): string {
  return `${alumno.nombre ?? ''} ${alumno.apellidos ?? ''}`.trim() || `Alumno ${alumno.id}`;
}

function normalizarAnios(anios: number[] | null | undefined): number[] {
  const validos = (anios ?? []).filter((a) => Number.isInteger(a) && a >= 1900 && a <= 2200).sort((a, b) => a - b);
  return validos.length > 0 ? validos : [new Date().getFullYear()];
}

function entero(valor: string | null): number | null {
  if (valor === null || valor.trim() === '') {
    return null;
  }
  const n = Number(valor);
  return Number.isNaN(n) ? null : n;
}

function booleano(valor: string | null): boolean | null {
  const v = valor?.trim().toLowerCase();
  return v === 'true' ? true : v === 'false' ? false : null;
}
