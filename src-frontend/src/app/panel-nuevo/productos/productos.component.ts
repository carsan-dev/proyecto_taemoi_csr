import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, Subject, Subscription, forkJoin, from, of } from 'rxjs';
import { catchError, concatMap, debounceTime, map, switchMap, takeUntil, toArray } from 'rxjs/operators';

import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { eur, numero, plural } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import {
  ClaveFiltro,
  FILAS_VISIBLES,
  Filtros,
  OPCIONES_ORDEN,
  ORDEN_POR_DEFECTO,
  Orden,
  ProductoPanel,
  TAMANO_LECTURA,
  TIPOS_PRODUCTO,
  TipoProducto,
  etiquetaOrden,
  filtrosActivos,
  miCajaParaApi,
  mismoOrden,
  ordenar,
  textoTotal,
  textoVerRestantes,
} from './productos-vista';

type Estado = 'cargando' | 'listo' | 'error';

interface Grupo {
  tipo: TipoProducto;
  titulo: string;
  /** Las filas leídas, en el orden del grupo. */
  productos: ProductoPanel[];
  /** Las que se ven: las primeras `FILAS_VISIBLES`, o todas las leídas si el grupo está abierto. */
  visibles: ProductoPanel[];
  /** Las del grupo según la API (`totalElements`), también las que aún no se han leído. */
  total: number;
  /** Quedan filas por ver: sin enseñar o sin leer. */
  hayMas: boolean;
  /** Cuántas faltan por ver (para «Ver los N restantes»). */
  restantes: number;
}

interface Lectura {
  tipo: TipoProducto;
  productos: ProductoPanel[];
  /** `totalElements` de la API. */
  total: number;
  /** `totalPages` de la API con el tamaño de lectura. */
  paginas: number;
  /** Páginas ya leídas (de la 1 a esta, seguidas). */
  leidas: number;
}

interface ResultadoLote {
  eliminados: ProductoPanel[];
  fallidos: ProductoPanel[];
}

/** Filtros y orden de cada grupo, para volver a la pantalla como se dejó (como hoy, con una clave propia). */
const CLAVE_ESTADO = 'panelProductosEstado';

const TIPOS = TIPOS_PRODUCTO.map((t) => t.valor);

/**
 * Productos del panel nuevo (8b-3g, P2 «agrupados por tipo»): un grupo por tipo con su número (el de la API); al abrir lee solo
 * la primera página de cada grupo y enseña sus 50 primeras filas; «Ver los N restantes» lee el resto de ESE grupo, una página tras otra;
 * fila corta con casilla, concepto, «nº», precio a la derecha, interruptor «Mi caja» y «⋯» (Editar, Eliminar); barra
 * fija al pie con los seleccionados («Dar a Mi caja», «Quitar de Mi caja», «Eliminar»); orden por nº, concepto o precio
 * en un menú de cada grupo; buscador, «Tipo», «Mi caja», «Limpiar filtros», total y chips. Las MISMAS escrituras que
 * `ListadoProductosComponent` (Mi caja de uno al momento y en lote, borrar uno y en lote), con el diálogo, los avisos y
 * los estados de carga del panel nuevo, sin SweetAlert2 ni cargador global. Va en su propio chunk detrás de
 * `disenoNuevoGuard`.
 */
/** Lo que acepta de una vez el PATCH de «Mi caja» en lote. */
const MAXIMO_LOTE = 200;

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [RouterLink, AcentoEscuelaDirective, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent, BotonOcupadoComponent],
  templateUrl: './productos.component.html',
  styleUrls: ['./productos.component.scss'],
})
export class ProductosComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly tipos = TIPOS_PRODUCTO;
  readonly opcionesOrden = OPCIONES_ORDEN;
  readonly eur = eur;
  readonly numero = numero;
  readonly plural = plural;
  readonly etiquetaOrden = etiquetaOrden;

  estado: Estado = 'cargando';
  filtros: Filtros = { concepto: '', tipo: '', miCaja: '' };
  grupos: Grupo[] = [];
  total = 0;
  /** Ids seleccionados (casillas). */
  readonly seleccion = new Set<number>();
  /** Productos con «Mi caja» en vuelo: sin doble envío. */
  readonly cambiandoMiCaja = new Set<number>();
  /** «Dar a Mi caja» o «Quitar de Mi caja» en lote, en vuelo. */
  accionBloque: 'dar' | 'quitar' | null = null;
  /** Menú abierto: `fila-<id>` («⋯») u `orden-<tipo>`. */
  menuAbierto: string | null = null;

  private porTipo = new Map<TipoProducto, Lectura>();
  /** Grupos abiertos con «Ver los N restantes»: enseñan todo lo leído. */
  private readonly abiertos = new Set<TipoProducto>();
  /** Grupos leyendo sus páginas restantes (botón ocupado). */
  private readonly leyendoResto = new Set<TipoProducto>();
  private readonly restos = new Map<TipoProducto, Subscription>();
  /** Concepto y Mi caja de la última lectura: las páginas restantes piden lo mismo. */
  private consulta: { concepto: string; miCaja: boolean | null } = { concepto: '', miCaja: null };
  private ordenes: Record<TipoProducto, Orden> = Object.fromEntries(TIPOS.map((t) => [t, ORDEN_POR_DEFECTO])) as Record<
    TipoProducto,
    Orden
  >;
  /** El concepto de la última lectura: no se repite al teclear lo mismo. */
  private conceptoAplicado = '';
  private carga?: Subscription;
  private readonly busqueda$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();
  /** Las escrituras no se cancelan al salir; lo que toca la vista tras destruir, sí se protege. */
  private destruido = false;
  private escuchaFuera = false;

  get activos(): { clave: ClaveFiltro; texto: string }[] {
    return filtrosActivos(this.filtros);
  }

  get hayFiltros(): boolean {
    return this.activos.length > 0;
  }

  get textoTotal(): string {
    return textoTotal(this.total);
  }

  get seleccionados(): ProductoPanel[] {
    return this.grupos.flatMap((g) => g.productos).filter((p) => this.seleccion.has(p.id));
  }

  get textoSeleccion(): string {
    return plural(this.seleccion.size, 'producto seleccionado', 'productos seleccionados');
  }

  ngOnInit(): void {
    this.restaurarEstado();
    this.busqueda$.pipe(debounceTime(300), takeUntil(this.destroy$)).subscribe(() => {
      if (this.filtros.concepto !== this.conceptoAplicado) {
        this.cargar();
      }
    });
    this.cargar();
  }

  ngOnDestroy(): void {
    this.carga?.unsubscribe();
    this.cancelarRestos();
    this.quitarEscucha();
    this.destruido = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ lectura

  /**
   * Una lectura por tipo (o solo la del tipo elegido) con la petición de hoy (`GET /productos` con concepto, tipo y
   * Mi caja), cada una con su primera página. Un grupo abierto con «Ver los N restantes» que se relee en silencio
   * (`silencioso`: sin esqueleto, tras una escritura) vuelve a leer las páginas que enseña, una tras otra, y sigue abierto;
   * una carga normal los cierra a todos.
   */
  cargar(silencioso = false): void {
    this.carga?.unsubscribe();
    this.cancelarRestos();
    this.conceptoAplicado = this.filtros.concepto;
    this.guardarEstado();
    if (!silencioso) {
      this.estado = 'cargando';
      this.abiertos.clear();
    }
    const tipos = this.filtros.tipo ? [this.filtros.tipo] : TIPOS;
    this.consulta = { concepto: this.filtros.concepto, miCaja: miCajaParaApi(this.filtros.miCaja) };
    this.carga = forkJoin(tipos.map((t) => this.leerTipo(t, this.abiertos.has(t)))).subscribe({
      next: (lecturas) => {
        this.porTipo = new Map(lecturas.map((l) => [l.tipo, l]));
        this.seleccion.clear();
        this.reconstruir();
        this.estado = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        if (silencioso) {
          this.avisos.error('No se pudo actualizar la lista de productos.');
        } else {
          this.estado = 'error';
        }
        this.cdr.markForCheck();
      },
    });
  }

  private pedir(tipo: TipoProducto, pagina: number): Observable<any> {
    // Por id (único): las páginas se leen en tandas y un orden por concepto (repetible) podría repetir u omitir filas en el
    // corte; el orden que se ve se hace después en el navegador.
    return this.lecturas.productos(this.consulta.concepto, pagina, TAMANO_LECTURA, 'id', 'asc', tipo, this.consulta.miCaja);
  }

  /** La primera página del tipo; con `completo`, también las demás, una tras otra. */
  private leerTipo(tipo: TipoProducto, completo: boolean): Observable<Lectura> {
    return this.pedir(tipo, 1).pipe(
      switchMap((primera) => {
        const paginas = Number(primera?.totalPages) || 0;
        const faltan = completo && paginas > 1 ? Array.from({ length: paginas - 1 }, (_, i) => i + 2) : [];
        return from(faltan).pipe(
          concatMap((pagina) => this.pedir(tipo, pagina).pipe(map((respuesta) => ({ pagina, respuesta })))),
          toArray(),
          map((resto) => {
            const lectura = this.nuevaLectura(tipo, primera, paginas);
            resto.forEach((r) => this.sumarPagina(lectura, r.respuesta, r.pagina));
            return lectura;
          })
        );
      })
    );
  }

  private nuevaLectura(tipo: TipoProducto, primera: any, paginas: number): Lectura {
    const lectura: Lectura = { tipo, productos: [], total: 0, paginas, leidas: 1 };
    this.sumarPagina(lectura, primera, 1);
    lectura.total = Number(primera?.totalElements) || lectura.productos.length;
    return lectura;
  }

  /** Añade las filas de una página que no estén ya (por id) y la da por leída. */
  private sumarPagina(lectura: Lectura, pagina: any, numeroPagina: number): void {
    const vistos = new Set(lectura.productos.map((p) => p.id));
    for (const p of (pagina?.content ?? []) as ProductoPanel[]) {
      if (!vistos.has(p.id)) {
        vistos.add(p.id);
        lectura.productos.push(p);
      }
    }
    lectura.leidas = numeroPagina;
  }

  private cancelarRestos(): void {
    this.restos.forEach((s) => s.unsubscribe());
    this.restos.clear();
    this.leyendoResto.clear();
  }

  private reconstruir(): void {
    this.grupos = TIPOS_PRODUCTO.filter((t) => (this.porTipo.get(t.valor)?.productos.length ?? 0) > 0).map((t) => {
      const lectura = this.porTipo.get(t.valor)!;
      const productos = ordenar(lectura.productos, this.ordenes[t.valor]);
      const visibles = this.abiertos.has(t.valor) ? productos : productos.slice(0, FILAS_VISIBLES);
      const sinLeer = lectura.leidas < lectura.paginas;
      const total = Math.max(lectura.total, productos.length);
      return {
        tipo: t.valor,
        titulo: t.grupo,
        productos,
        visibles,
        total,
        hayMas: sinLeer || visibles.length < productos.length,
        restantes: sinLeer ? total - visibles.length : productos.length - visibles.length,
      };
    });
    this.total = this.grupos.reduce((suma, g) => suma + g.total, 0);
  }

  // ------------------------------------------------------------------ «Ver los N restantes»

  textoRestantes(g: Grupo): string {
    return textoVerRestantes(g.restantes);
  }

  leyendo(g: Grupo): boolean {
    return this.leyendoResto.has(g.tipo);
  }

  /**
   * Enseña todo lo leído del grupo y lee lo que falte de ESE grupo, página a página (nunca en paralelo). Si falla, deja
   * lo leído, avisa y el botón sirve para reintentar.
   */
  verRestantes(g: Grupo): void {
    const tipo = g.tipo;
    const lectura = this.porTipo.get(tipo);
    if (!lectura || this.leyendoResto.has(tipo)) {
      return;
    }
    const primeraNueva = g.productos[g.visibles.length]?.id;
    this.abiertos.add(tipo);
    this.reconstruir();
    this.cdr.detectChanges();
    this.enfocarFila(primeraNueva);
    if (lectura.leidas >= lectura.paginas) {
      return;
    }
    this.leyendoResto.add(tipo);
    const pendientes = Array.from({ length: lectura.paginas - lectura.leidas }, (_, i) => lectura.leidas + i + 1);
    let nuevaId: number | undefined = primeraNueva;
    this.restos.set(
      tipo,
      from(pendientes)
        .pipe(concatMap((pagina) => this.pedir(tipo, pagina).pipe(map((respuesta) => ({ pagina, respuesta })))))
        .subscribe({
          next: ({ pagina, respuesta }) => {
            const antes = lectura.productos.length;
            this.sumarPagina(lectura, respuesta, pagina);
            nuevaId ??= lectura.productos[antes]?.id;
            this.reconstruir();
            this.cdr.detectChanges();
            if (nuevaId !== undefined && this.enfocarFila(nuevaId)) {
              nuevaId = undefined;
            }
          },
          error: () => {
            this.leyendoResto.delete(tipo);
            this.restos.delete(tipo);
            this.avisos.error('No se pudieron cargar los productos restantes.');
            this.cdr.markForCheck();
          },
          complete: () => {
            this.leyendoResto.delete(tipo);
            this.restos.delete(tipo);
            this.reconstruir();
            this.cdr.markForCheck();
          },
        })
    );
    this.cdr.markForCheck();
  }

  /**
   * Lleva el foco a la casilla de la primera fila recién enseñada, si el foco seguía en el botón (que puede desaparecer) o
   * en ningún sitio: nunca se lo quita a quien ya se fue a otra parte.
   */
  private enfocarFila(id: number | undefined): boolean {
    if (id === undefined) {
      return false;
    }
    const activo = document.activeElement;
    if (activo && activo !== document.body && !activo.closest('.productos__pie')) {
      return true;
    }
    const casilla = this.host.nativeElement.querySelector<HTMLInputElement>(
      `.productos__fila[data-producto="${id}"] .productos__casilla`
    );
    casilla?.focus();
    return !!casilla;
  }

  // ------------------------------------------------------------------ filtros

  buscar(valor: string): void {
    this.filtros = { ...this.filtros, concepto: valor };
    this.busqueda$.next();
  }

  elegirTipo(valor: string): void {
    this.filtros = { ...this.filtros, tipo: TIPOS.includes(valor as TipoProducto) ? (valor as TipoProducto) : '' };
    this.cargar();
  }

  elegirMiCaja(valor: string): void {
    this.filtros = { ...this.filtros, miCaja: valor === 'true' || valor === 'false' ? valor : '' };
    this.cargar();
  }

  quitarFiltro(clave: ClaveFiltro): void {
    this.filtros = { ...this.filtros, [clave]: '' };
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtros = { concepto: '', tipo: '', miCaja: '' };
    this.cargar();
  }

  private guardarEstado(): void {
    try {
      sessionStorage.setItem(CLAVE_ESTADO, JSON.stringify({ filtros: this.filtros, ordenes: this.ordenes }));
    } catch {
      // Sin almacenamiento de sesión, la pantalla funciona igual.
    }
  }

  private restaurarEstado(): void {
    let guardado: { filtros?: Partial<Filtros>; ordenes?: Partial<Record<TipoProducto, Orden>> } | null = null;
    try {
      guardado = JSON.parse(sessionStorage.getItem(CLAVE_ESTADO) ?? 'null');
    } catch {
      guardado = null;
    }
    if (!guardado || typeof guardado !== 'object') {
      return;
    }
    const f = guardado.filtros ?? {};
    this.filtros = {
      concepto: typeof f.concepto === 'string' ? f.concepto : '',
      tipo: TIPOS.includes(f.tipo as TipoProducto) ? (f.tipo as TipoProducto) : '',
      miCaja: f.miCaja === 'true' || f.miCaja === 'false' ? f.miCaja : '',
    };
    for (const t of TIPOS) {
      const o = guardado.ordenes?.[t];
      if (o && OPCIONES_ORDEN.some((x) => mismoOrden(x.orden, o))) {
        this.ordenes[t] = { campo: o.campo, sentido: o.sentido };
      }
    }
  }

  // ------------------------------------------------------------------ orden de cada grupo

  ordenDe(g: Grupo): Orden {
    return this.ordenes[g.tipo];
  }

  esOrden(g: Grupo, orden: Orden): boolean {
    return mismoOrden(this.ordenes[g.tipo], orden);
  }

  elegirOrden(g: Grupo, orden: Orden): void {
    this.ordenes = { ...this.ordenes, [g.tipo]: orden };
    this.reconstruir();
    this.guardarEstado();
    this.cerrarMenu(true);
  }

  // ------------------------------------------------------------------ selección

  seleccionado(p: ProductoPanel): boolean {
    return this.seleccion.has(p.id);
  }

  alternarSeleccion(p: ProductoPanel): void {
    if (this.seleccion.has(p.id)) {
      this.seleccion.delete(p.id);
    } else {
      this.seleccion.add(p.id);
    }
  }

  todosDe(g: Grupo): boolean {
    return g.visibles.length > 0 && g.visibles.every((p) => this.seleccion.has(p.id));
  }

  algunosDe(g: Grupo): boolean {
    return !this.todosDe(g) && g.visibles.some((p) => this.seleccion.has(p.id));
  }

  etiquetaTodos(g: Grupo): string {
    return g.visibles.length === 1
      ? `Seleccionar el producto de ${g.titulo}`
      : `Seleccionar los ${numero(g.visibles.length)} de ${g.titulo}`;
  }

  alternarGrupo(g: Grupo): void {
    const marcar = !this.todosDe(g);
    for (const p of g.visibles) {
      if (marcar) {
        this.seleccion.add(p.id);
      } else {
        this.seleccion.delete(p.id);
      }
    }
  }

  quitarSeleccion(): void {
    this.seleccion.clear();
  }

  // ------------------------------------------------------------------ Mi caja

  miCaja(p: ProductoPanel): boolean {
    return !!p.vendibleProfesor;
  }

  ocupadoMiCaja(p: ProductoPanel): boolean {
    return this.cambiandoMiCaja.has(p.id);
  }

  /** El interruptor: al momento, como hoy, con el `PATCH /productos/{id}/vendible-profesor` de hoy. */
  alternarMiCaja(p: ProductoPanel): void {
    if (this.cambiandoMiCaja.has(p.id)) {
      return;
    }
    const nuevo = !this.miCaja(p);
    this.cambiandoMiCaja.add(p.id);
    this.endpoints.cambiarVendibleProfesor(p.id, nuevo).subscribe({
      next: (actualizado) => {
        this.cambiandoMiCaja.delete(p.id);
        p.vendibleProfesor = !!actualizado?.vendibleProfesor;
        this.avisos.exito(
          p.vendibleProfesor ? `«${p.concepto}»: ya se puede vender desde Mi caja.` : `«${p.concepto}»: ya no sale en Mi caja.`
        );
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.cambiandoMiCaja.delete(p.id);
        this.avisos.error(mensajeError(error, `No se pudo cambiar «Mi caja» de «${p.concepto}».`));
        this.cdr.markForCheck();
      },
    });
  }

  /** «Dar a Mi caja» y «Quitar de Mi caja»: el `PATCH /productos/vendible-profesor` de hoy con los ids seleccionados. */
  miCajaEnLote(vendibleProfesor: boolean): void {
    const lote = this.seleccionados;
    if (lote.length === 0 || this.accionBloque) {
      return;
    }
    if (lote.length > MAXIMO_LOTE) {
      // La API es todo o nada y rechaza más de 200: mejor no enviar que fallar con la selección entera.
      this.avisos.error(`Selecciona como máximo ${MAXIMO_LOTE} productos a la vez.`);
      return;
    }
    this.accionBloque = vendibleProfesor ? 'dar' : 'quitar';
    this.endpoints
      .cambiarVendibleProfesorEnBloque(
        lote.map((p) => p.id),
        vendibleProfesor
      )
      .subscribe({
        next: () => {
          this.accionBloque = null;
          lote.forEach((p) => (p.vendibleProfesor = vendibleProfesor));
          this.seleccion.clear();
          const n = lote.length;
          this.avisos.exito(
            vendibleProfesor
              ? `${numero(n)} ${n === 1 ? 'producto' : 'productos'} ahora en Mi caja.`
              : `${numero(n)} ${n === 1 ? 'producto quitado' : 'productos quitados'} de Mi caja.`
          );
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.accionBloque = null;
          this.avisos.error(mensajeError(error, 'No se pudo cambiar «Mi caja» de los productos seleccionados.'));
          this.cdr.markForCheck();
        },
      });
  }

  // ------------------------------------------------------------------ eliminar

  /** «Eliminar» de «⋯»: confirmación destructiva y el `DELETE /productos/{id}` de hoy; luego relee, como hoy. */
  async eliminar(p: ProductoPanel): Promise<void> {
    this.cerrarMenu(true);
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar producto',
      mensaje: ['Vas a eliminar «', { fuerte: p.concepto }, '». Esta acción no se puede deshacer.'],
      accion: 'Sí, eliminar',
      accionOcupada: 'Eliminando…',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-producto',
      ejecutar: () => this.endpoints.eliminarProducto(p.id),
    });
    if (r.error) {
      this.avisos.error('No se pudo eliminar el producto.');
      this.cdr.markForCheck();
      return;
    }
    if (!r.confirmado) {
      return;
    }
    this.avisos.exito('Producto eliminado correctamente.');
    if (!this.destruido) {
      this.cargar(true);
    }
  }

  /** «Eliminar» de la barra: confirmación destructiva y un `DELETE` tras otro, como hoy; luego relee. */
  async eliminarSeleccionados(): Promise<void> {
    const lote = this.seleccionados;
    if (lote.length === 0 || this.accionBloque) {
      return;
    }
    const n = lote.length;
    const r = await this.confirmacion.confirmar<ResultadoLote>({
      titulo: n === 1 ? 'Eliminar 1 producto' : `Eliminar ${numero(n)} productos`,
      mensaje: 'Los productos seleccionados serán eliminados permanentemente. Esta acción no se puede deshacer.',
      accion: n === 1 ? 'Sí, eliminarlo' : 'Sí, eliminarlos',
      accionOcupada: 'Eliminando…',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-productos',
      ejecutar: () => this.eliminarEnSerie(lote),
    });
    if (!r.confirmado || !r.respuesta) {
      return;
    }
    const { eliminados, fallidos } = r.respuesta;
    if (eliminados.length) {
      this.avisos.exito(
        eliminados.length === 1 ? '1 producto eliminado.' : `${numero(eliminados.length)} productos eliminados.`
      );
    }
    if (fallidos.length) {
      const nombres = fallidos.map((p) => `«${p.concepto}»`).join(', ');
      this.avisos.error(
        fallidos.length === 1
          ? `No se pudo eliminar el producto ${nombres}.`
          : `No se pudieron eliminar ${numero(fallidos.length)} productos: ${nombres}.`
      );
    }
    this.seleccion.clear();
    if (!this.destruido) {
      this.cargar(true);
    }
  }

  /** Como `eliminarProductoSecuencial` de hoy: uno cada vez, del último al primero; un fallo no para el resto. */
  private eliminarEnSerie(lote: ProductoPanel[]): Observable<ResultadoLote> {
    return from([...lote].reverse()).pipe(
      concatMap((p) =>
        this.endpoints.eliminarProducto(p.id).pipe(
          map(() => ({ p, ok: true })),
          catchError(() => of({ p, ok: false }))
        )
      ),
      toArray(),
      map((resultados) => ({
        eliminados: resultados.filter((x) => x.ok).map((x) => x.p),
        fallidos: resultados.filter((x) => !x.ok).map((x) => x.p),
      }))
    );
  }

  // ------------------------------------------------------------------ menús («⋯» y orden)

  claveFila(p: ProductoPanel): string {
    return `fila-${p.id}`;
  }

  claveOrden(g: Grupo): string {
    return `orden-${g.tipo}`;
  }

  private menuRef(clave: string): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`#productos-menu-${clave}`);
  }

  private botonMenu(clave: string): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>(`#productos-boton-${clave}`);
  }

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.menuAbierto === null ? null : this.botonMenu(this.menuAbierto)?.closest('.productos__menu-zona');
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.menuAbierto = null;
    this.quitarEscucha();
    this.cdr.markForCheck();
  };

  private quitarEscucha(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  alternarMenu(clave: string, evento?: KeyboardEvent): void {
    if (this.menuAbierto === clave) {
      this.cerrarMenu(false);
      return;
    }
    this.menuAbierto = clave;
    if (!this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = true;
    }
    this.cdr.detectChanges();
    enfocarOpcion(this.menuRef(clave), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMenu(clave: string, evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && this.menuAbierto !== clave) {
      evento.preventDefault();
      this.alternarMenu(clave, evento);
    }
  }

  teclaMenu(clave: string, evento: KeyboardEvent): void {
    const menu = this.menuRef(clave);
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
    const clave = this.menuAbierto;
    this.menuAbierto = null;
    this.quitarEscucha();
    if (devolverFoco && clave !== null) {
      this.cdr.detectChanges();
      this.botonMenu(clave)?.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrarMenu(false);
    }
  }
}

/** El `mensaje` del servidor si lo trae (como hoy); si no, el de la pantalla. */
function mensajeError(error: unknown, porDefecto: string): string {
  const mensaje = (error as { error?: { mensaje?: unknown } } | null)?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
}
