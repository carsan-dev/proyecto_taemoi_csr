import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, forkJoin, interval, of } from 'rxjs';
import { catchError, finalize, map, skip, switchMap, take } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { cambiosDeCobro, opcionesConfirmarCobro } from '../../componentes/endpoints/tesoreria-cobros/confirmar-cobro';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { PlataformaService } from '../../servicios/plataforma/plataforma.service';
import { showErrorToast, showSuccessToast } from '../../utils/toast.util';
import { DIAS_VENTANA_ERRORES, ventanaUltimosDias } from '../../utils/ventana-fechas.util';
import { eur, fecha, fechaLarga, mes, numero, plural } from '../formato/formato-es';
import { ClaseDeHoy, Ocupacion, calcularOcupacion, clasesDeHoy } from './ocupacion';
import { CargoVencido, PendienteVencido, PendienteVencidoService, TipoPendiente } from './pendiente-vencido.service';

type Estado = 'cargando' | 'listo' | 'error';

interface Acceso {
  clave: string;
  etiqueta: string;
  descripcion?: string;
  ruta: string;
  icono: string;
  queryParams?: Record<string, string>;
  /** Solo ROLE_ADMIN (adminOnlyGuard devolvería al gestor a la raíz). */
  soloAdmin?: boolean;
}

interface Aviso {
  id: string;
  texto: string;
  accion: string;
  ruta: string;
  icono: string;
}

interface FilaDisciplina {
  codigo: string;
  nombre: string;
  alumnos: number;
  porcentaje: number;
}

interface EventoActivo {
  id: number;
  titulo: string;
  miniatura: string;
}

const ETIQUETA_TIPO: Record<TipoPendiente, string> = {
  MENSUALIDAD: 'Mensualidades',
  EXAMEN: 'Derechos de examen',
};

const ETIQUETA_TIPO_CARGO: Record<TipoPendiente, string> = {
  MENSUALIDAD: 'Mensualidad',
  EXAMEN: 'Derecho de examen',
};

/** Operaciones prioritarias del Inicio de hoy, con el mismo destino (Anexo A: «Operaciones y accesos»). */
const OPERACIONES: readonly Acceso[] = [
  {
    clave: 'aptos',
    etiqueta: 'Revisar aptos para examen',
    descripcion: 'Alumnos listos para promocionar en la siguiente convocatoria.',
    ruta: '/alumnosListar',
    queryParams: { aptoParaExamen: 'true' },
    icono: 'bi-award',
  },
  {
    clave: 'bajas',
    etiqueta: 'Gestionar bajas y altas',
    descripcion: 'Control rápido de alumnos inactivos y eliminaciones.',
    ruta: '/alumnosEliminar',
    icono: 'bi-person-x',
  },
  {
    clave: 'convocatorias',
    etiqueta: 'Preparar convocatorias',
    descripcion: 'Crear convocatorias, pagos e informe de examen.',
    ruta: '/convocatoriasListar',
    icono: 'bi-clipboard-check',
  },
  {
    clave: 'tesoreria',
    etiqueta: 'Tesorería y cobros',
    descripcion: 'Revisión de deudas, pendientes y cobros por mes.',
    ruta: '/tesoreriaCobros',
    icono: 'bi-cash-stack',
  },
  {
    clave: 'registro',
    etiqueta: 'Registro de acciones',
    descripcion: 'Lo que se ha hecho en tu escuela.',
    ruta: '/auditoriaSistema',
    icono: 'bi-journal-text',
    soloAdmin: true,
  },
  {
    clave: 'configuracion',
    etiqueta: 'Roles y configuración',
    descripcion: 'Permisos de usuarios y límite global de turno.',
    ruta: '/configuracion-sistema',
    icono: 'bi-gear',
    soloAdmin: true,
  },
];

/** Acceso rápido del Inicio de hoy (7 entradas con el registro). */
const SECCIONES: readonly Acceso[] = [
  { clave: 'alumnos', etiqueta: 'Alumnos', descripcion: 'Gestiona información de los alumnos', ruta: '/alumnosListar', icono: 'bi-people' },
  { clave: 'grupos', etiqueta: 'Grupos y turnos', descripcion: 'Organiza grupos y gestiona horarios', ruta: '/gruposListar', icono: 'bi-collection' },
  { clave: 'eventos', etiqueta: 'Eventos', descripcion: 'Crea y administra eventos especiales', ruta: '/eventosListar', icono: 'bi-calendar-check' },
  { clave: 'productos', etiqueta: 'Productos', descripcion: 'Gestión de productos y servicios', ruta: '/productosListar', icono: 'bi-box-seam' },
  { clave: 'convocatorias', etiqueta: 'Convocatorias', descripcion: 'Publica y gestiona convocatorias', ruta: '/convocatoriasListar', icono: 'bi-clipboard-check' },
  { clave: 'tesoreria', etiqueta: 'Tesorería', descripcion: 'Control mensual de cobros y deudas', ruta: '/tesoreriaCobros', icono: 'bi-cash-coin' },
  {
    clave: 'registro',
    etiqueta: 'Registro de acciones',
    descripcion: 'Altas, cambios y bajas hechas en tu escuela',
    ruta: '/auditoriaSistema',
    icono: 'bi-journal-text',
    soloAdmin: true,
  },
];

const MINIATURA_POR_DEFECTO = 'assets/media/default.webp';

/**
 * Inicio I1 del panel nuevo (8b-2; decisiones 238, 242, 244, 245, 254 y 267). Base C con la ocupación día × franja de
 * B: ocupación de la semana, «Hoy», pendiente de cobro vencido con «Cobrar», accesos con cifra, la escuela en cifras,
 * eventos activos y operaciones y accesos. Va en su propio chunk detrás de `disenoNuevoGuard`: con el interruptor
 * apagado sigue la pantalla de hoy. Lee los mismos datos que la de hoy y, además, `GET /api/tesoreria/pendiente-vencido`.
 * «Cobrar» abre el mismo diálogo y hace el mismo PUT que la tesorería de hoy.
 */
@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './inicio.component.html',
  styleUrls: ['./inicio.component.scss'],
})
export class InicioComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly pendienteApi = inject(PendienteVencidoService);
  private readonly auth = inject(AuthenticationService);
  private readonly plataforma = inject(PlataformaService);
  private readonly catalogo = inject(CatalogoService);
  private readonly suscripciones = new Subscription();
  private readonly autoRefrescoMs = 30000;
  private carga?: Subscription;
  private cargaPendiente?: Subscription;

  readonly eur = eur;
  readonly numero = numero;
  readonly plural = plural;
  readonly etiquetaTipo = ETIQUETA_TIPO;

  ahora = new Date();
  estado: Estado = 'cargando';
  estadoEventos: Estado = 'cargando';
  estadoPendiente: Estado = 'cargando';
  esAdmin = false;

  limite = 0;
  ocupacion: Ocupacion = { dias: [], franjas: [], filas: [], inscritos: 0, plazas: 0 };
  clases: ClaseDeHoy[] = [];
  pendiente: PendienteVencido | null = null;
  /** Cargos con un cobro en vuelo: su botón queda bloqueado. */
  readonly cobrando = new Set<number>();

  alumnosTotales = 0;
  alumnosActivos = 0;
  totalGrupos = 0;
  totalTurnos = 0;
  aptos = 0;
  totalEventos = 0;
  eventosVisibles = 0;
  erroresPlataforma = 0;
  disciplinas: FilaDisciplina[] = [];
  totalInscripciones = 0;
  eventos: EventoActivo[] = [];
  avisos: Aviso[] = [];

  get fechaHoy(): string {
    return fechaLarga(this.ahora);
  }

  get diaHoy(): string {
    return this.fechaHoy.split(',')[0].toLowerCase();
  }

  get claveHoy(): number {
    return this.ahora.getDay();
  }

  get cargando(): boolean {
    return this.estado === 'cargando' || this.estadoPendiente === 'cargando';
  }

  get alumnosInactivos(): number {
    return Math.max(this.alumnosTotales - this.alumnosActivos, 0);
  }

  get eventosSinPublicar(): number {
    return Math.max(this.totalEventos - this.eventosVisibles, 0);
  }

  get operaciones(): Acceso[] {
    return OPERACIONES.filter((o) => !o.soloAdmin || this.esAdmin);
  }

  get secciones(): Acceso[] {
    return SECCIONES.filter((s) => !s.soloAdmin || this.esAdmin);
  }

  ngOnInit(): void {
    this.suscripciones.add(this.auth.getRoles().subscribe((roles) => (this.esAdmin = (roles ?? []).includes('ROLE_ADMIN'))));
    this.suscripciones.add(
      this.endpoints.eventos$.pipe(skip(1)).subscribe((eventos) => this.leerEventos(Array.isArray(eventos) ? eventos : []))
    );
    this.actualizar();
    this.suscripciones.add(
      interval(this.autoRefrescoMs).subscribe(() => {
        if (!this.cargando && this.cobrando.size === 0) {
          this.actualizar();
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.carga?.unsubscribe();
    this.cargaPendiente?.unsubscribe();
  }

  actualizar(): void {
    this.ahora = new Date();
    this.cargarDatos();
    this.cargarPendiente();
    this.estadoEventos = 'cargando';
    this.endpoints.obtenerTodosLosEventos();
  }

  // ------------------------------------------------------------------ datos

  private cargarDatos(): void {
    this.carga?.unsubscribe();
    this.estado = 'cargando';
    const o = <T>(fuente: Observable<T>) => fuente.pipe(catchError(() => of(null)));
    this.carga = forkJoin({
      totales: o(this.endpoints.obtenerAlumnos(1, 1, '', true, false)),
      activos: o(this.endpoints.obtenerAlumnos(1, 1, '', false, false)),
      grupos: o(this.endpoints.obtenerResumenGrupos()),
      turnos: o(this.endpoints.obtenerResumenTurnos()),
      limite: o(this.endpoints.obtenerLimiteTurno()),
      aptos: o(this.endpoints.obtenerAlumnosAptosParaExamen()),
      distribucion: o(this.endpoints.obtenerDistribucionAlumnosPorDeporte()),
      errores: this.contarErroresPlataforma(),
    }).subscribe((d) => {
      if (d.grupos === null && d.turnos === null && d.totales === null) {
        this.estado = 'error';
        return;
      }
      this.alumnosTotales = d.totales?.totalElements ?? 0;
      this.alumnosActivos = d.activos?.totalElements ?? 0;
      const grupos: GrupoResumen[] = d.grupos ?? [];
      this.totalGrupos = grupos.length;
      this.totalTurnos = (d.turnos ?? []).length;
      this.aptos = (d.aptos ?? []).length;
      this.limite = Number(d.limite) > 0 ? Number(d.limite) : 0;
      this.erroresPlataforma = d.errores;
      this.ocupacion = calcularOcupacion(grupos, this.limite);
      this.clases = clasesDeHoy(grupos, this.limite, this.ahora);
      this.leerDistribucion(d.distribucion ?? {});
      this.estado = 'listo';
      this.calcularAvisos();
    });
  }

  private cargarPendiente(): void {
    this.cargaPendiente?.unsubscribe();
    this.estadoPendiente = 'cargando';
    this.cargaPendiente = this.pendienteApi.obtener().subscribe({
      next: (p) => {
        this.pendiente = p;
        this.estadoPendiente = 'listo';
      },
      error: () => {
        this.pendiente = null;
        this.estadoPendiente = 'error';
      },
    });
  }

  private leerDistribucion(distribucion: Record<string, number>): void {
    const filas = Object.entries(distribucion)
      .map(([codigo, total]) => ({ codigo, alumnos: Math.floor(Number(total)) }))
      .filter((f) => Number.isFinite(f.alumnos) && f.alumnos > 0);
    this.totalInscripciones = filas.reduce((s, f) => s + f.alumnos, 0);
    this.disciplinas = filas.map((f) => ({
      codigo: f.codigo,
      nombre: this.catalogo.etiquetaDisciplina(f.codigo),
      alumnos: f.alumnos,
      porcentaje: this.totalInscripciones > 0 ? Math.round((f.alumnos / this.totalInscripciones) * 100) : 0,
    }));
  }

  private leerEventos(eventos: any[]): void {
    this.totalEventos = eventos.length;
    const visibles = eventos.filter((e) => e?.visible);
    this.eventosVisibles = visibles.length;
    this.eventos = visibles.slice(0, 4).map((e) => ({
      id: e.id,
      titulo: e.titulo ?? '',
      miniatura: miniaturaEvento(e),
    }));
    this.estadoEventos = 'listo';
    this.calcularAvisos();
  }

  /** «Otras alertas según el estado» de la pantalla de hoy (Anexo A); aptos y eventos van en «Accesos». */
  private calcularAvisos(): void {
    if (this.estado !== 'listo') {
      return;
    }
    const avisos: Aviso[] = [];
    if (this.totalGrupos === 0) {
      avisos.push({ id: 'sin-grupos', texto: 'No hay grupos configurados', accion: 'Crear grupo', ruta: '/gruposCrear', icono: 'bi-collection' });
    }
    if (this.totalTurnos === 0) {
      avisos.push({ id: 'sin-turnos', texto: 'No hay turnos creados', accion: 'Crear turno', ruta: '/turnosCrear', icono: 'bi-calendar-plus' });
    }
    if (this.alumnosInactivos > 0) {
      avisos.push({
        id: 'inactivos',
        texto: `${plural(this.alumnosInactivos, 'alumno inactivo', 'alumnos inactivos')} por revisar`,
        accion: 'Ir a bajas y altas',
        ruta: '/alumnosEliminar',
        icono: 'bi-person-x',
      });
    }
    if (this.erroresPlataforma > 0) {
      avisos.push({
        id: 'errores-plataforma',
        texto: `${plural(this.erroresPlataforma, 'error', 'errores')} de la API en ${DIAS_VENTANA_ERRORES} días, en todas las escuelas`,
        accion: 'Ver errores de plataforma',
        ruta: '/plataformaErrores',
        icono: 'bi-exclamation-octagon',
      });
    }
    this.avisos = avisos;
  }

  /** Como la pantalla de hoy: 0 si la sesión no es de plataforma o si la consulta falla. */
  private contarErroresPlataforma(): Observable<number> {
    return this.plataforma.esAdminPlataforma().pipe(
      take(1),
      switchMap((esPlataforma) => {
        if (!esPlataforma) {
          return of(0);
        }
        const ventana = ventanaUltimosDias();
        const contar = (estado: '4xx' | '5xx') =>
          this.plataforma.errores({ ...ventana, estado, page: 1, size: 1 }).pipe(map((r) => r?.totalElements ?? 0));
        return forkJoin([contar('4xx'), contar('5xx')]).pipe(map(([a, b]) => a + b));
      }),
      catchError(() => of(0))
    );
  }

  // ------------------------------------------------------------------ cobrar

  /** El mismo diálogo, la misma llamada y el mismo cuerpo que «Marcar pagado» en la tesorería de hoy. */
  cobrar(cargo: CargoVencido): void {
    const id = cargo.productoAlumnoId;
    if (this.cobrando.has(id)) {
      return;
    }
    Swal.fire(opcionesConfirmarCobro(cargo.concepto)).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.cobrando.add(id);
      this.endpoints
        .actualizarCobroTesoreria(id, cambiosDeCobro(result.value))
        .pipe(finalize(() => this.cobrando.delete(id)))
        .subscribe({
          next: () => {
            showSuccessToast('Cobro actualizado como pagado');
            this.cargarPendiente();
          },
          error: (error: HttpErrorResponse) => {
            showErrorToast(
              error?.status === 403 ? 'Solo administradores pueden revertir cobros' : 'No se pudo actualizar el estado del cobro'
            );
          },
        });
    });
  }

  // ------------------------------------------------------------------ vista

  /** «disc-1» a «disc-4» por el orden del catálogo; una quinta disciplina, sin color propio. */
  claseDisciplina(codigo: string | null): string {
    const i = this.catalogo.disciplinasActivas().findIndex((d) => d.codigo === codigo);
    return i >= 0 && i < 4 ? `inicio__disc--${i + 1}` : 'inicio__disc--otra';
  }

  nombreDisciplina(codigo: string | null): string {
    return codigo ? this.catalogo.etiquetaDisciplina(codigo) : '';
  }

  esHoy(clave: string): boolean {
    return clave === ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'][this.claveHoy];
  }

  mesLargo(clave: string): string {
    return mes(`${clave}-01`);
  }

  detalleCargo(cargo: CargoVencido): string {
    const tipo = ETIQUETA_TIPO_CARGO[cargo.tipo] ?? '';
    return `${tipo}, ${this.mesLargo(cargo.mes)} · fuera de plazo desde el ${fecha(cargo.vencidoDesde)}`;
  }
}

function miniaturaEvento(evento: any): string {
  const url: string | undefined = evento?.fotoEvento?.url;
  if (!url) {
    return MINIATURA_POR_DEFECTO;
  }
  const version = String(evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0');
  return conParametro(conParametro(url, 'w', '160'), 'v', version);
}

function conParametro(url: string, clave: string, valor: string): string {
  const seguro = encodeURIComponent(valor);
  const regex = new RegExp(`([?&])${clave}=[^&]*`);
  if (regex.test(url)) {
    return url.replace(regex, `$1${clave}=${seguro}`);
  }
  return `${url}${url.includes('?') ? '&' : '?'}${clave}=${seguro}`;
}
