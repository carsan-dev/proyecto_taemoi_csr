import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Params, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

import { DialogosAlumnoComponent } from '../../componentes/endpoints/editar-alumno/dialogos-alumno/dialogos-alumno.component';
import { EditarAlumnoComponent } from '../../componentes/endpoints/editar-alumno/editar-alumno.component';
import { FotoAmpliadaComponent } from '../../componentes/endpoints/editar-alumno/foto-ampliada/foto-ampliada.component';
import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { calcularEdad } from '../../utilities/calcular-edad';
import { EstadoLicencia, estadoLicencia, fondoCinturon, tiempoConGrado } from '../alumnos/alumno-vista';
import { CargoPendiente, PendienteAlumno, PendientesAlumnoService } from '../alumnos/pendientes-alumno.service';
import { cobrarCargo, teclaPanelCargos } from '../cobro/cobro-cargo';
import { eur, fecha, numero, plural } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { FichaDatosComponent } from './datos/ficha-datos.component';
import { FichaDeportesComponent } from './deportes/ficha-deportes.component';
import { FichaDocumentosComponent } from './documentos/ficha-documentos.component';
import { fechaParaCampo, lineaGrado, resumenPendiente } from './ficha-vista';
import { FichaPagosComponent } from './pagos/ficha-pagos.component';

export type Pestana = 'deportes' | 'pagos' | 'datos' | 'documentos';

export const PESTANAS: ReadonlyArray<{ clave: Pestana; texto: string }> = [
  { clave: 'deportes', texto: 'Deportes' },
  { clave: 'pagos', texto: 'Pagos' },
  { clave: 'datos', texto: 'Datos y contacto' },
  { clave: 'documentos', texto: 'Documentos y notas' },
];

/** Una disciplina en el carnet: su cinturón, si es la principal y su grado con el tiempo que lleva con él. */
export interface DisciplinaCarnet {
  codigo: string;
  nombre: string;
  clase: string;
  principal: boolean;
  /** Fondo del cinturón (un color o las dos mitades); nulo si la disciplina no tiene grados o no tiene grado. */
  fondo: string | null;
  linea: string;
}

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Conjunto de productos con el cobro de hoy en vuelo («Cobrar» de la tabla de Pagos, `pagarProducto`). Al terminar
 * (bien o mal) la ficha de hoy los quita; aquí, además, se vuelve a leer el pendiente del carnet.
 */
class CobrosEnVuelo extends Set<number> {
  constructor(private readonly alTerminar: () => void) {
    super();
  }

  override delete(id: number): boolean {
    const habia = super.delete(id);
    if (habia) {
      this.alTerminar();
    }
    return habia;
  }
}

/**
 * Ficha del alumno F1 «Carnet» del panel nuevo (8b-3b; decisión 254). Carnet arriba (foto, nombre, datos, disciplinas
 * con su cinturón, pendiente con «Cobrar», licencia y acciones del alumno) y cuatro pestañas debajo, con la pestaña en
 * la dirección. Toda la lógica (cargas, guardados, validaciones, diálogos y navegación entre alumnos) es la de la ficha
 * de hoy: esta clase la hereda sin copiarla y solo cambia la plantilla. «Cobrar» del carnet es el de la tesorería
 * (`cobrarCargo`, como el listado nuevo). Va en su propio chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-ficha-alumno',
  standalone: true,
  imports: [
    RouterLink,
    FichaDeportesComponent,
    FichaPagosComponent,
    FichaDatosComponent,
    FichaDocumentosComponent,
    DialogosAlumnoComponent,
    FotoAmpliadaComponent,
  ],
  templateUrl: './ficha-alumno.component.html',
  styleUrls: ['./ficha-alumno.component.scss'],
})
export class FichaAlumnoComponent extends EditarAlumnoComponent implements OnInit, OnDestroy {
  private readonly pendientesApi = inject(PendientesAlumnoService);
  private readonly endpoints = inject(EndpointsService);
  private readonly rutaActiva = inject(ActivatedRoute);
  private readonly enrutador = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly eur = eur;
  readonly fecha = fecha;
  readonly numero = numero;
  readonly plural = plural;
  readonly pestanas = PESTANAS;

  @ViewChild('menuMas') private menuMas?: ElementRef<HTMLElement>;
  @ViewChild('botonMas') private botonMas?: ElementRef<HTMLButtonElement>;

  /**
   * Query params del listado del que se vino (búsqueda, filtros, página y vista), para que «← Alumnos» vuelva a él como
   * estaba; nulo si se llegó desde otra pantalla. Se toma al construir: pasar a otro alumno no lo cambia.
   */
  readonly volverAlListado: Params | null = parametrosDelListado(this.enrutador);

  pestana: Pestana = 'deportes';
  pendiente: PendienteAlumno | null = null;
  estadoPendiente: Estado = 'cargando';
  panelAbierto = false;
  menuAbierto = false;
  /** Cargos del carnet con un cobro en vuelo. */
  readonly cobrando = new Set<number>();
  /** Cobros de la tabla de Pagos (los de hoy): al terminar, el carnet relee el pendiente. */
  override pagandoProductos: Set<number> = new CobrosEnVuelo(() => this.releerPendiente());

  private readonly suscripciones = new Subscription();
  private destruida = false;
  private cargaPendiente?: Subscription;
  private cacheDisciplinas: { deportes: AlumnoDeporteDTO[]; lista: DisciplinaCarnet[] } | null = null;

  override ngOnInit(): void {
    super.ngOnInit();
    this.suscripciones.add(
      this.rutaActiva.queryParamMap.subscribe((parametros) => {
        const pedida = parametros.get('pestana');
        if (pedida !== null) {
          this.pestana = PESTANAS.some((p) => p.clave === pedida) ? (pedida as Pestana) : 'deportes';
        }
      })
    );
    // «1 de N» (y cualquier paso a otro alumno) navega sin la pestaña: se vuelve a poner para seguir en ella.
    this.suscripciones.add(
      this.enrutador.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe(() => {
        if (this.pestana !== 'deportes' && !this.rutaActiva.snapshot.queryParamMap.has('pestana')) {
          this.escribirPestana();
        }
      })
    );
  }

  override ngOnDestroy(): void {
    this.destruida = true;
    super.ngOnDestroy();
    this.suscripciones.unsubscribe();
    this.cargaPendiente?.unsubscribe();
  }

  /** Además de lo de hoy, el pendiente vencido del alumno para el carnet. */
  protected override aplicarAlumnoCargado(alumnoResponse: any): void {
    super.aplicarAlumnoCargado(alumnoResponse);
    this.cargarPendiente();
  }

  // ------------------------------------------------------------------ barra

  get posicion(): string {
    return this.totalPaginas > 0 ? `${numero(this.paginaActual)} de ${numero(this.totalPaginas)}` : '';
  }

  irAlAnterior(): void {
    this.cambiarPagina(this.paginaActual - 1);
  }

  irAlSiguiente(): void {
    this.cambiarPagina(this.paginaActual + 1);
  }

  // ------------------------------------------------------------------ carnet

  get fotoUrl(): string | null {
    if (this.imagenPreview) {
      return this.imagenPreview;
    }
    const foto = this.alumno?.fotoAlumno;
    if (!foto?.url) {
      return null;
    }
    const version = foto.id || foto.nombre || '0';
    return `${foto.url}?w=480&v=${version}`;
  }

  get edad(): string {
    const nacimiento = this.alumno?.fechaNacimiento;
    const anios = nacimiento ? calcularEdad(nacimiento) : NaN;
    return Number.isFinite(anios) ? plural(anios, 'año', 'años') : '';
  }

  /** El alta del alumno; si la ficha no la trae, la más antigua de sus disciplinas (alta inicial o alta). */
  get alumnoDesde(): string {
    const propia = this.alumno?.fechaAltaInicial || this.alumno?.fechaAlta;
    if (propia) {
      return fecha(propia);
    }
    const fechas = this.deportesDelAlumno
      .map((d) => fechaParaCampo(d.fechaAltaInicial || d.fechaAlta))
      .filter((f) => f !== '')
      .sort();
    return fechas.length ? fecha(fechas[0]) : '';
  }

  /** Las disciplinas activas (la principal primero, como hoy); de un alumno de baja, todas. */
  get disciplinas(): DisciplinaCarnet[] {
    const deportes = this.deportesDelAlumno;
    if (this.cacheDisciplinas?.deportes === deportes) {
      return this.cacheDisciplinas.lista;
    }
    const activas = deportes.filter((d) => d.activo !== false);
    const hoy = new Date();
    const lista = (activas.length ? activas : deportes).map((d) => this.disciplina(d, hoy));
    this.cacheDisciplinas = { deportes, lista };
    return lista;
  }

  get franja(): DisciplinaCarnet[] {
    return this.disciplinas.filter((d) => d.fondo !== null);
  }

  /** Licencia de la disciplina principal, con el criterio del listado nuevo. */
  get licencia(): EstadoLicencia | null {
    const activas = this.deportesDelAlumno.filter((d) => d.activo !== false);
    const principal = activas.find((d) => this.isDeportePrincipal(d.deporte)) ?? activas[0] ?? null;
    return estadoLicencia(principal, principal ? this.catalogo.requiereLicencia(principal.deporte) : false, new Date());
  }

  get resumenCargos(): string {
    return this.pendiente ? resumenPendiente(this.pendiente) : '';
  }

  private disciplina(d: AlumnoDeporteDTO, hoy: Date): DisciplinaCarnet {
    const conGrados = !this.catalogo.sinGrados(d.deporte);
    const grado = conGrados ? (d.grado ? this.getGradoNombre(d.grado, d.deporte) : 'Sin grado') : '';
    return {
      codigo: d.deporte,
      nombre: this.catalogo.etiquetaDisciplina(d.deporte),
      clase: this.claseDisciplina(d.deporte),
      principal: d.activo !== false && this.isDeportePrincipal(d.deporte),
      fondo: conGrados && d.grado ? fondoCinturon(d.grado, d.deporte) : null,
      linea: lineaGrado(grado, conGrados && d.grado ? tiempoConGrado(d.fechaGrado, hoy) : ''),
    };
  }

  /** «disc-1» a «disc-4» por el orden del catálogo (DESIGN.md); una quinta disciplina, sin color propio. */
  claseDisciplina(codigo: string): string {
    const i = this.catalogo.disciplinasActivas().findIndex((d) => d.codigo === codigo);
    return i >= 0 && i < 4 ? `ficha__disc--${i + 1}` : 'ficha__disc--otra';
  }

  // ------------------------------------------------------------------ pendiente y «Cobrar»

  private cargarPendiente(): void {
    const id = this.alumnoId;
    this.cargaPendiente?.unsubscribe();
    // Al cambiar de alumno, nada del anterior (importe, insignia, «Cobrar» ni su panel) mientras llega el del nuevo.
    if (this.pendiente?.alumnoId !== id) {
      this.pendiente = null;
      this.panelAbierto = false;
    }
    if (!id || this.destruida) {
      return;
    }
    this.estadoPendiente = 'cargando';
    this.cargaPendiente = this.pendientesApi.obtener([id]).subscribe({
      next: (lista) => {
        // Una respuesta que llega cuando ya se ve otro alumno se descarta.
        if (id !== this.alumnoId) {
          return;
        }
        const suyo = (lista ?? []).find((p) => p.alumnoId === id);
        this.pendiente = suyo && suyo.cargos > 0 ? suyo : null;
        this.estadoPendiente = 'listo';
        if (!this.pendiente && this.panelAbierto) {
          this.cerrarPanel(false);
          this.anfitrion.nativeElement.querySelector<HTMLElement>('.ficha__pendiente')?.focus();
        }
        this.cdr.markForCheck();
      },
      error: () => {
        if (id !== this.alumnoId) {
          return;
        }
        this.pendiente = null;
        this.estadoPendiente = 'error';
      },
    });
  }

  /** Vuelve a leer el pendiente (tras cobrar desde la tabla de Pagos). */
  releerPendiente(): void {
    this.cargarPendiente();
  }

  /** Un cargo: «Confirmar cobro» de hoy. Varios: el panel con cada cargo (como el listado nuevo). */
  pulsarCobrar(): void {
    const pendiente = this.pendiente;
    if (!pendiente) {
      return;
    }
    if (pendiente.detalle.length === 1) {
      this.cobrar(pendiente.detalle[0]);
      return;
    }
    if (this.panelAbierto) {
      this.cerrarPanel(true);
      return;
    }
    this.panelAbierto = true;
    this.cdr.detectChanges();
    this.anfitrion.nativeElement.querySelector<HTMLElement>('.ficha__cargos .ficha__cargo button')?.focus();
  }

  cerrarPanel(devolverFoco: boolean): void {
    if (!this.panelAbierto) {
      return;
    }
    this.panelAbierto = false;
    this.cdr.detectChanges();
    if (devolverFoco) {
      this.anfitrion.nativeElement.querySelector<HTMLElement>('.ficha__cobrar')?.focus();
    }
  }

  teclaPanel(evento: KeyboardEvent): void {
    teclaPanelCargos(evento, () => this.cerrarPanel(true));
  }

  cobrar(cargo: CargoPendiente): void {
    cobrarCargo(this.endpoints, cargo, this.cobrando, () => this.trasCobrar());
  }

  /** Tras cobrar: el pendiente, la tabla de pagos y los grados (el derecho de examen cobrado sube el grado). */
  private trasCobrar(): void {
    const id = this.alumnoId;
    if (!id || this.destruida) {
      return;
    }
    this.cargarPendiente();
    this.obtenerProductosAlumno(id);
    this.cargarDeportesDelAlumno(id, true);
  }

  // ------------------------------------------------------------------ acciones del alumno

  /** «Editar datos»: la pestaña Datos y contacto en edición, con el foco en el primer campo. */
  editarDatos(): void {
    this.cambiarPestana('datos');
    if (!this.editingBasicInfo) {
      this.toggleEditBasicInfo();
    }
    this.cdr.detectChanges();
    this.anfitrion.nativeElement.querySelector<HTMLInputElement>('#nombre')?.focus();
  }

  alternarMenu(evento?: KeyboardEvent): void {
    this.menuAbierto = !this.menuAbierto;
    if (this.menuAbierto) {
      this.cdr.detectChanges();
      enfocarOpcion(this.menuMas?.nativeElement ?? null, evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
    }
  }

  teclaBotonMenu(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuAbierto) {
      evento.preventDefault();
      this.alternarMenu(evento);
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const menu = this.menuMas?.nativeElement;
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
      this.botonMas?.nativeElement.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = (evento.currentTarget as HTMLElement | null) ?? null;
    const destino = evento.relatedTarget as Node | null;
    if (this.menuAbierto && zona && destino && !zona.contains(destino)) {
      this.menuAbierto = false;
    }
  }

  /** «Dar de baja o eliminar…» / «Dar de alta o eliminar…»: el diálogo de hoy, que ofrece las dos cosas. */
  bajaAltaOEliminar(): void {
    this.cerrarMenu(false);
    this.eliminarODarDeBajaAlumno();
  }

  // ------------------------------------------------------------------ pestañas

  cambiarPestana(clave: Pestana, enfocar = false): void {
    this.pestana = clave;
    this.escribirPestana();
    if (enfocar) {
      this.cdr.detectChanges();
      this.anfitrion.nativeElement.querySelector<HTMLElement>(`#ficha-pestana-${clave}`)?.focus();
    }
  }

  /** Flechas (con vuelta), Inicio y Fin; la pestaña se activa al llegar a ella. */
  teclaPestanas(evento: KeyboardEvent): void {
    const actual = PESTANAS.findIndex((p) => p.clave === this.pestana);
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
        siguiente = (actual + 1) % PESTANAS.length;
        break;
      case 'ArrowLeft':
        siguiente = (actual - 1 + PESTANAS.length) % PESTANAS.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = PESTANAS.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.cambiarPestana(PESTANAS[siguiente].clave, true);
  }

  etiquetaPestana(clave: Pestana, texto: string): string | null {
    if (clave !== 'pagos' || !this.pendiente) {
      return null;
    }
    return `${texto}, ${plural(this.pendiente.cargos, 'cargo pendiente', 'cargos pendientes')}`;
  }

  /** La pestaña en la dirección (`?pestana=`), sin apilar historial; Deportes es la de por defecto y no se escribe. */
  private escribirPestana(): void {
    void this.enrutador.navigate([], {
      relativeTo: this.rutaActiva,
      queryParams: { pestana: this.pestana === 'deportes' ? null : this.pestana },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}

/** Los query params de la navegación anterior si era el listado de alumnos; si no, nulo. */
function parametrosDelListado(enrutador: Router): Params | null {
  const anterior = enrutador.currentNavigation()?.previousNavigation?.finalUrl;
  const primero = anterior?.root.children['primary']?.segments[0]?.path;
  return primero === 'alumnosListar' ? { ...anterior!.queryParams } : null;
}
