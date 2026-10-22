import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, filter, finalize } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { BajasAltasAlumno } from '../../componentes/endpoints/listado-alumnos/bajas-altas-alumno';
import {
  HerramientasAlumnosComponent,
  SeccionHerramientas,
} from '../../componentes/endpoints/listado-alumnos/herramientas-alumnos/herramientas-alumnos.component';
import { InformesAlumnos, OPCIONES_INFORME_ALUMNOS } from '../../componentes/endpoints/listado-alumnos/informes-alumnos';
import { cambiosDeCobro, opcionesConfirmarCobro } from '../../componentes/endpoints/tesoreria-cobros/confirmar-cobro';
import { InformeModalComponent } from '../../componentes/generales/informe-modal/informe-modal.component';
import { InformePdfModalComponent } from '../../componentes/generales/informe-pdf-modal/informe-pdf-modal.component';
import { AlumnoService } from '../../features/alumno/services/alumno.service';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AccionInforme, InformePdfService } from '../../servicios/generales/informe-pdf.service';
import { calcularEdad } from '../../utilities/calcular-edad';
import { getGradoColors, getGradoNombreParaDeporte } from '../../utilities/grado-colors';
import { showErrorToast, showSuccessToast } from '../../utils/toast.util';
import { eur, fecha, numero, plural } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import {
  EstadoLicencia,
  antiguedad,
  estadoLicencia,
  iniciales,
  ordenarDeportes,
  principalDe,
  tiempoConGrado,
} from './alumno-vista';
import { CargoPendiente, PendienteAlumno, PendientesAlumnoService } from './pendientes-alumno.service';

/** Clave de localStorage de «Filas | Tarjetas». */
export const CLAVE_VISTA = 'panelAlumnosVista';

/** 25 por página (decisión 253): la búsqueda, los filtros y la paginación van al servidor. */
export const POR_PAGINA = 25;

type Vista = 'filas' | 'tarjetas';
type Estado = 'cargando' | 'listo' | 'error';

interface GradoFila {
  /** En la línea 2, en minúscula («naranja-verde»); en el detalle, el texto de hoy («NARANJA-VERDE», «NEGRO 1º DAN»). */
  nombre: string;
  nombreDetalle: string;
  /** Fondo del cinturón: un color o las dos mitades. */
  fondo: string;
}

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
 * desplegable con el detalle por deporte y sus acciones, el menú «Informes» y las herramientas del pie de hoy. Va en su
 * propio chunk detrás de `disenoNuevoGuard`: con el interruptor apagado sigue el listado de hoy.
 */
@Component({
  selector: 'app-alumnos',
  standalone: true,
  imports: [RouterLink, HerramientasAlumnosComponent, InformeModalComponent, InformePdfModalComponent],
  providers: [InformePdfService],
  templateUrl: './alumnos.component.html',
  styleUrls: ['./alumnos.component.scss'],
})
export class AlumnosComponent implements OnInit, OnDestroy {
  readonly informes = inject(InformePdfService);
  readonly catalogo = inject(CatalogoService);
  private readonly endpoints = inject(EndpointsService);
  private readonly alumnoService = inject(AlumnoService);
  private readonly pendientesApi = inject(PendientesAlumnoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly eur = eur;
  readonly fecha = fecha;
  readonly numero = numero;
  readonly plural = plural;
  readonly porPagina = POR_PAGINA;
  readonly opcionesHerramientas = OPCIONES_HERRAMIENTAS;

  @ViewChild(HerramientasAlumnosComponent) private herramientas?: HerramientasAlumnosComponent;
  @ViewChild('menuInformes') private menuInformes?: ElementRef<HTMLElement>;
  @ViewChild('botonInformes') private botonInformes?: ElementRef<HTMLButtonElement>;

  // Filtros (al servidor).
  nombre = '';
  deporte = '';
  inactivos = false;
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
  mostrarModalInforme = false;
  readonly opcionesInforme = [...OPCIONES_INFORME_ALUMNOS];

  private readonly informesAlumnos = new InformesAlumnos(this.endpoints, this.informes);
  private readonly bajasAltas = new BajasAltasAlumno(this.endpoints, this.alumnoService, () => this.cargar());
  private readonly busqueda = new Subject<string>();
  private readonly suscripciones = new Subscription();
  private carga?: Subscription;
  private cargaPendientes?: Subscription;
  /** Botón «Cobrar» que abrió el panel: recibe el foco al cerrarlo. */
  private disparadorPanel: HTMLElement | null = null;

  get temporadasReservasPlaza(): string[] {
    return this.informesAlumnos.temporadas;
  }

  get hayFiltros(): boolean {
    return this.nombre.trim() !== '' || this.deporte !== '' || this.inactivos || this.aptos;
  }

  get contador(): string {
    return plural(this.total, 'alumno', 'alumnos');
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
    this.vista = leerVista();
    this.aptos = this.route.snapshot?.queryParamMap?.get('aptoParaExamen') === 'true';
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
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.carga?.unsubscribe();
    this.cargaPendientes?.unsubscribe();
    this.busqueda.complete();
  }

  // ------------------------------------------------------------------ datos

  /** El mismo `GET /api/alumnos` paginado que el listado de hoy, con los filtros en el servidor. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargaPendientes?.unsubscribe();
    this.estado = 'cargando';
    this.carga = this.endpoints
      .obtenerAlumnos(this.pagina, POR_PAGINA, this.nombre.trim(), this.inactivos, this.aptos, this.deporte || null)
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
    this.pendientesApi.obtener([alumnoId]).subscribe({
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
      error: () => showErrorToast('No se pudo actualizar el pendiente del alumno'),
    });
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

  alternarInactivos(): void {
    this.inactivos = !this.inactivos;
    this.pagina = 1;
    this.cargar();
  }

  /** Como hoy: «Solo aptos» también queda en la dirección (`?aptoParaExamen=true`). */
  alternarAptos(): void {
    this.aptos = !this.aptos;
    this.pagina = 1;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.aptos ? { aptoParaExamen: 'true' } : {},
      queryParamsHandling: this.aptos ? 'merge' : '',
      replaceUrl: true,
    });
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
    this.inactivos = false;
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
      showErrorToast('No se puede marcar como principal un deporte inactivo');
      return;
    }
    this.alumnoService.establecerDeportePrincipal(alumnoId, codigo).subscribe({
      next: () => {
        alumno.deportes = deportes.map((d) => ({ ...d, principal: d.deporte === codigo }));
        this.reconstruir();
        showSuccessToast(`Deporte principal actualizado a ${this.catalogo.etiquetaDisciplina(codigo)}`);
      },
      error: (error) => showErrorToast(error?.error || 'No se pudo actualizar el deporte principal'),
    });
  }

  darDeBaja(id: number): void {
    this.bajasAltas.darDeBaja(id);
  }

  darDeAlta(id: number): void {
    this.bajasAltas.darDeAlta(id);
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

  teclaPanel(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      this.cerrarPanel(true);
    }
  }

  /** El mismo diálogo, la misma llamada y el mismo cuerpo que «Marcar pagado» en la tesorería de hoy. */
  cobrar(alumnoId: number, cargo: CargoPendiente): void {
    const id = cargo.productoAlumnoId;
    if (this.cobrando.has(id)) {
      return;
    }
    Swal.fire(opcionesConfirmarCobro(cargo.concepto)).then((resultado) => {
      if (!resultado.isConfirmed) {
        return;
      }
      this.cobrando.add(id);
      this.endpoints
        .actualizarCobroTesoreria(id, cambiosDeCobro(resultado.value))
        .pipe(finalize(() => this.cobrando.delete(id)))
        .subscribe({
          next: () => {
            showSuccessToast('Cobro actualizado como pagado');
            this.refrescarPendiente(alumnoId);
          },
          error: (error: HttpErrorResponse) => {
            showErrorToast(
              error?.status === 403 ? 'Solo administradores pueden revertir cobros' : 'No se pudo actualizar el estado del cobro'
            );
          },
        });
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

  abrirInformes(): void {
    this.cerrarMenu(false);
    this.mostrarModalInforme = true;
    this.informesAlumnos.abrir();
  }

  cerrarInformes(): void {
    this.mostrarModalInforme = false;
    this.botonInformes?.nativeElement.focus();
  }

  generarInforme(evento: { tipo: string; soloActivos: boolean; temporada?: string; accion: AccionInforme }): void {
    this.informesAlumnos.generarInformeSeleccionado(evento);
  }

  abrirHerramienta(seccion: SeccionHerramientas): void {
    this.cerrarMenu(false);
    this.herramientas?.abrir(seccion);
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

function gradoFila(codigo: string, deporte: string): GradoFila {
  const { colors, isSplit } = getGradoColors(codigo, deporte);
  const fondo = isSplit && colors.length > 1 ? `linear-gradient(to right, ${colors[0]} 50%, ${colors[1]} 50%)` : colors[0];
  // El texto de hoy: el nombre del catálogo o, si el código no está en él, el código sin guiones bajos.
  const nombreDetalle = getGradoNombreParaDeporte(codigo, deporte).replace(/_+/g, ' ');
  return { nombre: nombreDetalle.toLocaleLowerCase('es-ES'), nombreDetalle, fondo };
}

function fotoDe(a: any): string | null {
  const url: string | undefined = a?.fotoAlumno?.url;
  if (!url) {
    return null;
  }
  const version = encodeURIComponent(String(a.fotoAlumno.id ?? a.fotoAlumno.nombre ?? '0'));
  return `${url}${url.includes('?') ? '&' : '?'}w=180&v=${version}`;
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
