import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, Subject, Subscription, concat } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { AlumnoService } from '../../features/alumno/services/alumno.service';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { numero } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { BajasAlumnosApiService } from './bajas-alumnos-api.service';
import {
  CLAVE_ESTADO,
  FilaBaja,
  POR_PAGINA,
  TipoLote,
  alumnos as textoAlumnos,
  exitoLote,
  filaBaja,
  leerEstado,
  lineaDatos,
  motivoFallo,
} from './bajas-alumnos-vista';
import { LoteAlumnosComponent, ResultadoLote } from './lote-alumnos.component';

type Estado = 'cargando' | 'listo' | 'error';

interface LoteAbierto {
  tipo: TipoLote;
  alumnos: FilaBaja[];
}

/**
 * «Bajas y eliminaciones» del panel nuevo (8b-3l, P1 de la decisión 285; ruta de hoy `/alumnosEliminar`). Aviso neutro,
 * buscador y «Activos | Todos», filas de dos líneas (casilla, foto, nombre que enlaza a la ficha, «Exp. · DNI» y la
 * pastilla «Activo» / «De baja») y, desde 1280 px, la tabla con las columnas de hoy. Por fila, «Dar de baja» o «Dar de
 * alta» y «⋯» con «Eliminar definitivamente»; con selección, la barra «N elegidos · Quitar selección · Dar de baja ·
 * Eliminar». El borrado (uno o varios) va en el diálogo destructivo con los nombres, los pagos y cargos (P-3), el
 * progreso y el resumen de los que fallaron.
 *
 * P-4: «Eliminar definitivamente» y «Eliminar» solo los ve el propietario (`ROLE_ADMIN`); el servidor tampoco se lo
 * admite a nadie más. Las MISMAS llamadas que `EliminarAlumnoComponent` (listado paginado de 10, baja, borrado y el alta
 * por deporte) y el mismo estado en sessionStorage. Va en su chunk detrás de `estilosPanelGuard`.
 */
@Component({
  selector: 'app-bajas-alumnos',
  standalone: true,
  imports: [RouterLink, AcentoEscuelaDirective, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent, LoteAlumnosComponent],
  templateUrl: './bajas-alumnos.component.html',
  styleUrls: ['./bajas-alumnos.component.scss'],
})
export class BajasAlumnosComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly api = inject(BajasAlumnosApiService);
  private readonly alumnoService = inject(AlumnoService);
  private readonly catalogo = inject(CatalogoService);
  private readonly auth = inject(AuthenticationService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly lineaDatos = lineaDatos;
  readonly numero = numero;

  nombre = '';
  todos = false;
  pagina = 1;
  estado: Estado = 'cargando';
  filas: FilaBaja[] = [];
  total = 0;
  totalPaginas = 0;

  readonly elegidos = new Set<number>();
  /** «⋯» abierto: el alumno y en qué distribución (la fila o la tabla). */
  menuDe: { id: number; vista: 'fila' | 'tabla' } | null = null;
  lote: LoteAbierto | null = null;
  esPropietario = false;

  private readonly busqueda = new Subject<string>();
  private readonly suscripciones = new Subscription();
  private carga?: Subscription;

  // Las dos llamadas que el diálogo de lote hace por alumno.
  readonly eliminarUno = (id: number): Observable<unknown> => this.api.eliminar(id);
  readonly darDeBajaUno = (id: number): Observable<unknown> => this.api.darDeBaja(id);
  readonly leerCargos = (ids: number[]) => this.api.resumenCargos(ids);
  readonly anonimizarUno = (id: number): Observable<unknown> => this.api.anonimizar(id);

  /**
   * 8b-3l bis (decisión 287): los pagos de cada alumno de la página (solo el propietario, del resumen de cargos). Con
   * alguno, el «⋯» ofrece «Eliminar anonimizando» en lugar de «Eliminar definitivamente». Sin leerlos (o si fallan),
   * «Eliminar definitivamente»: el servidor responde 409 con su motivo, que el diálogo enseña.
   */
  readonly pagos = new Map<number, number>();
  private lecturaPagos?: Subscription;

  get hayFiltros(): boolean {
    return this.nombre.trim() !== '' || this.todos;
  }

  get rango(): string {
    if (this.total === 0) {
      return '';
    }
    const desde = (this.pagina - 1) * POR_PAGINA + 1;
    const hasta = Math.min(this.pagina * POR_PAGINA, this.total);
    return `${numero(desde)}-${numero(hasta)} de ${numero(this.total)}`;
  }

  get textoElegidos(): string {
    return `${numero(this.elegidos.size)} ${this.elegidos.size === 1 ? 'elegido' : 'elegidos'}`;
  }

  get todosLosDeLaPagina(): boolean {
    return this.filas.length > 0 && this.filas.every((f) => this.elegidos.has(f.id));
  }

  get aviso(): string {
    return this.esPropietario
      ? 'Aquí se dan de baja o se eliminan alumnos. Eliminar borra al alumno para siempre.'
      : 'Aquí se dan de baja alumnos. Eliminarlos para siempre solo puede hacerlo el propietario.';
  }

  ngOnInit(): void {
    const guardado = leerEstado(sessionStorage.getItem(CLAVE_ESTADO));
    this.pagina = guardado.paginaActual;
    this.nombre = guardado.nombreFiltro;
    this.todos = guardado.mostrarInactivos;
    this.resolverPropietario();
    this.suscripciones.add(
      this.busqueda.pipe(debounceTime(500), distinctUntilChanged()).subscribe((texto) => {
        this.nombre = texto;
        this.pagina = 1;
        this.cargar();
      })
    );
    this.cargar();
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.carga?.unsubscribe();
    this.lecturaPagos?.unsubscribe();
    this.busqueda.complete();
  }

  // ------------------------------------------------------------------ lista

  /** El `GET /api/alumnos?page&size=10&incluirInactivos&nombre` de hoy; si la página queda fuera, a la última. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.estado = 'cargando';
    this.menuDe = null;
    this.elegidos.clear();
    this.guardarEstado();
    this.carga = this.lecturas.alumnos(this.pagina, POR_PAGINA, this.nombre.trim(), this.todos).subscribe({
      next: (respuesta) => {
        const contenido = Array.isArray(respuesta?.content) ? respuesta.content : [];
        this.total = Number(respuesta?.totalElements ?? contenido.length) || 0;
        this.totalPaginas = Number(respuesta?.totalPages ?? 0) || 0;
        if (this.totalPaginas > 0 && this.pagina > this.totalPaginas) {
          this.pagina = this.totalPaginas;
          this.cargar();
          return;
        }
        this.filas = contenido.map(filaBaja);
        this.estado = 'listo';
        this.cargarPagos();
        this.cdr.markForCheck();
      },
      error: () => {
        this.filas = [];
        this.estado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  /** 8b-3l bis: el resumen de cargos de la página, para saber quién tiene pagos (solo el propietario). */
  private cargarPagos(): void {
    this.lecturaPagos?.unsubscribe();
    this.pagos.clear();
    if (!this.esPropietario || this.filas.length === 0) {
      return;
    }
    this.lecturaPagos = this.api.resumenCargos(this.filas.map((f) => f.id)).subscribe({
      next: (lista) => {
        (Array.isArray(lista) ? lista : []).forEach((r) => this.pagos.set(r.alumnoId, r.pagos ?? 0));
        this.cdr.markForCheck();
      },
      error: () => this.cdr.markForCheck(),
    });
  }

  /** 8b-3l bis: con pagos, se elimina anonimizando. */
  conPagos(id: number): boolean {
    return (this.pagos.get(id) ?? 0) > 0;
  }

  private guardarEstado(): void {
    sessionStorage.setItem(
      CLAVE_ESTADO,
      JSON.stringify({ paginaActual: this.pagina, tamanoPagina: POR_PAGINA, nombreFiltro: this.nombre, mostrarInactivos: this.todos })
    );
  }

  buscar(texto: string): void {
    this.busqueda.next(texto);
  }

  borrarBusqueda(): void {
    const campo = this.host.nativeElement.querySelector<HTMLInputElement>('#bajas-buscar');
    if (campo) {
      campo.value = '';
      campo.focus();
    }
    if (this.nombre !== '') {
      this.nombre = '';
      this.pagina = 1;
      this.busqueda.next('');
      this.cargar();
    }
  }

  elegirTodos(todos: boolean): void {
    if (this.todos === todos) {
      return;
    }
    this.todos = todos;
    this.pagina = 1;
    this.cargar();
  }

  quitarFiltros(): void {
    this.nombre = '';
    this.todos = false;
    this.pagina = 1;
    this.busqueda.next('');
    this.cargar();
  }

  irAPagina(p: number): void {
    if (this.estado === 'cargando' || p < 1 || p > this.totalPaginas || p === this.pagina) {
      return;
    }
    this.pagina = p;
    this.cargar();
  }

  // ------------------------------------------------------------------ selección

  estaElegido(id: number): boolean {
    return this.elegidos.has(id);
  }

  alternar(id: number): void {
    if (this.elegidos.has(id)) {
      this.elegidos.delete(id);
    } else {
      this.elegidos.add(id);
    }
  }

  /** «Seleccionar los de esta página» (o quitarlos si ya están todos). */
  alternarPagina(): void {
    if (this.todosLosDeLaPagina) {
      this.filas.forEach((f) => this.elegidos.delete(f.id));
    } else {
      this.filas.forEach((f) => this.elegidos.add(f.id));
    }
  }

  quitarSeleccion(): void {
    this.elegidos.clear();
  }

  private filasElegidas(): FilaBaja[] {
    return this.filas.filter((f) => this.elegidos.has(f.id));
  }

  // ------------------------------------------------------------------ «⋯»

  menuAbierto(id: number, vista: 'fila' | 'tabla'): boolean {
    return this.menuDe?.id === id && this.menuDe.vista === vista;
  }

  alternarMenu(id: number, vista: 'fila' | 'tabla', evento?: KeyboardEvent): void {
    if (this.menuAbierto(id, vista)) {
      this.menuDe = null;
      return;
    }
    this.menuDe = { id, vista };
    this.cdr.detectChanges();
    enfocarOpcion(this.menuElemento(), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMenu(evento: KeyboardEvent, id: number, vista: 'fila' | 'tabla'): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuAbierto(id, vista)) {
      evento.preventDefault();
      this.alternarMenu(id, vista, evento);
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const menu = this.menuElemento();
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

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (this.menuDe && zona && destino && !zona.contains(destino)) {
      this.menuDe = null;
    }
  }

  private cerrarMenu(devolverFoco: boolean): void {
    const abierto = this.menuDe;
    this.menuDe = null;
    if (devolverFoco && abierto) {
      this.cdr.detectChanges();
      this.host.nativeElement.querySelector<HTMLElement>(`[data-menu-boton="${abierto.vista}-${abierto.id}"]`)?.focus();
    }
  }

  private menuElemento(): HTMLElement | null {
    return this.menuDe ? this.host.nativeElement.querySelector<HTMLElement>(`#bajas-menu-${this.menuDe.vista}-${this.menuDe.id}`) : null;
  }

  // ------------------------------------------------------------------ dar de baja y eliminar

  /** «Dar de baja» de una fila: el diálogo normal con la frase de hoy y el `PUT …/baja`. */
  async darDeBaja(fila: FilaBaja): Promise<void> {
    const r = await this.confirmacion.confirmar({
      titulo: `¿Dar de baja a ${fila.nombreCompleto}?`,
      mensaje: 'El alumno será dado de baja.',
      accion: 'Dar de baja',
      accionOcupada: 'Dando de baja…',
      nombre: 'dar-de-baja',
      ejecutar: () => this.api.darDeBaja(fila.id),
    });
    if (!r.confirmado) {
      return;
    }
    if (r.error) {
      this.avisos.error(motivoFallo('baja', r.error));
      return;
    }
    this.avisos.exito(exitoLote('baja', 1));
    this.cargar();
  }

  /** «Eliminar definitivamente» de una fila (solo el propietario). */
  eliminar(fila: FilaBaja): void {
    this.menuDe = null;
    if (!this.esPropietario) {
      return;
    }
    this.lote = { tipo: 'eliminar', alumnos: [fila] };
  }

  /** «Dar de baja» de la barra de selección. */
  darDeBajaElegidos(): void {
    const elegidos = this.filasElegidas();
    if (elegidos.length) {
      this.lote = { tipo: 'baja', alumnos: elegidos };
    }
  }

  /** «Eliminar» de la barra de selección (solo el propietario). */
  eliminarElegidos(): void {
    const elegidos = this.filasElegidas();
    if (elegidos.length && this.esPropietario) {
      this.lote = { tipo: 'eliminar', alumnos: elegidos };
    }
  }

  alCerrarLote(resultado: ResultadoLote): void {
    const lote = this.lote;
    this.lote = null;
    if (!lote) {
      return;
    }
    if (resultado.hechos > 0 && resultado.fallos === 0) {
      this.avisos.exito(exitoLote(lote.tipo, resultado.hechos, resultado.anonimizados ?? 0));
    }
    if (resultado.hechos > 0 || resultado.fallos > 0) {
      this.cargar();
    }
  }

  // ------------------------------------------------------------------ dar de alta (por deporte, como hoy)

  /** Como hoy: sin deportes de baja, lo dice; con uno, lo activa; con varios, pregunta cuál (o todos). */
  darDeAlta(fila: FilaBaja): void {
    this.alumnoService.obtenerDeportesDelAlumno(fila.id).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => void this.elegirYActivar(fila.id, deportes ?? []),
      error: () => this.avisos.error('Error al obtener los deportes del alumno'),
    });
  }

  private async elegirYActivar(alumnoId: number, deportes: AlumnoDeporteDTO[]): Promise<void> {
    const inactivos = deportes.filter((d) => d.activo === false);
    if (inactivos.length === 0) {
      this.avisos.info('El alumno ya tiene todos sus deportes activos.');
      return;
    }
    if (inactivos.length === 1) {
      this.activar(alumnoId, inactivos[0].deporte);
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Selecciona el deporte a activar',
      campos: [
        {
          id: 'bajas-deporte',
          tipo: 'pastillas',
          etiqueta: 'Deporte',
          opciones: [
            { valor: 'TODOS', texto: 'Todos los deportes' },
            ...inactivos.map((d) => ({ valor: d.deporte, texto: this.catalogo.etiquetaDisciplina(d.deporte) })),
          ],
        },
      ],
      comprobar: (v) => (v['bajas-deporte'] ? null : { campo: 'bajas-deporte', mensaje: 'Por favor selecciona un deporte' }),
      accion: 'Dar de alta',
      nombre: 'dar-de-alta',
    });
    const elegido = r.valores?.['bajas-deporte'];
    if (!r.confirmado || !elegido) {
      return;
    }
    if (elegido === 'TODOS') {
      this.activarTodos(alumnoId, inactivos);
    } else {
      this.activar(alumnoId, elegido);
    }
  }

  private activar(alumnoId: number, deporte: string): void {
    this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte).subscribe({
      next: () => {
        this.avisos.exito(`El alumno ha sido dado de alta en ${this.catalogo.etiquetaDisciplina(deporte)}.`);
        this.cargar();
      },
      error: (error) => this.avisos.error(textoServidor(error) ?? 'Error al dar de alta al alumno'),
    });
  }

  private activarTodos(alumnoId: number, inactivos: AlumnoDeporteDTO[]): void {
    concat(...inactivos.map((d) => this.alumnoService.activarDeporteDeAlumno(alumnoId, d.deporte))).subscribe({
      complete: () => {
        const nombres = inactivos.map((d) => this.catalogo.etiquetaDisciplina(d.deporte)).join(', ');
        this.avisos.exito(`El alumno ha sido dado de alta en todos sus deportes: ${nombres}.`);
        this.cargar();
      },
      error: (error) => this.avisos.error(textoServidor(error) ?? 'Error al activar algunos deportes'),
    });
  }

  // ------------------------------------------------------------------ propietario

  /** Propietario = `ROLE_ADMIN`; si los roles no se pueden leer, falla cerrado (no ve «Eliminar»). */
  private resolverPropietario(): void {
    if (this.auth.rolesEstanCargados()) {
      this.esPropietario = this.auth.tieneRolAdmin();
      return;
    }
    this.suscripciones.add(
      this.auth.obtenerRoles().subscribe({
        next: () => {
          this.esPropietario = this.auth.tieneRolAdmin();
          if (this.estado === 'listo') {
            this.cargarPagos();
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.esPropietario = false;
          this.cdr.markForCheck();
        },
      })
    );
  }

  plural(n: number): string {
    return textoAlumnos(n);
  }
}

/** Hoy se muestra `error.error` si es texto (el del servidor). */
function textoServidor(error: any): string | null {
  return typeof error?.error === 'string' && error.error.trim() ? error.error.trim() : null;
}
