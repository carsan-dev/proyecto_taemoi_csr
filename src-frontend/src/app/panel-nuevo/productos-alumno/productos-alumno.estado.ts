import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { EMPTY, Observable, Subscription, concat, defer, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, tap } from 'rxjs/operators';

import { euros } from '../../componentes/generales/pedir-al-propietario/pedir-al-propietario';
import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { SolicitudesService } from '../../servicios/endpoints/solicitudes.service';
import { importeCargo } from '../../utils/importe-cargo';
import { AvisosService } from '../avisos/avisos.service';
import { CobroCargoService } from '../cobro/cobro-cargo';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import type { ParteMensaje } from '../dialogo/dialogo.component';
import { fecha } from '../formato/formato-es';
import { ProductosAlumnoApiService } from './productos-alumno-api.service';
import {
  AlumnoProductos,
  CambiosCargo,
  ErrorCampoCargo,
  EstadoPago,
  ProductoCatalogo,
  TAMANO_PAGINA,
  TEXTOS_PRODUCTOS_ALUMNO as T,
  cuerpoAsignacion,
  diaLocal,
  esMatricula,
  estaCancelado,
  estadoPago,
  fechaConDia,
  filtrarCatalogo,
  guardarPagina,
  leerCantidad,
  leerPaginaGuardada,
  leerPrecio,
  paginaValida,
  precioParaCampo,
  textoCancelacion,
  textoFallosGuardado,
  textoSalirSinGuardar,
  tieneCobroActivo,
} from './productos-alumno-vista';

/** Cambios sin guardar de un cargo: el texto de cada campo tocado (solo los que difieren de lo guardado). */
export interface EdicionCargo {
  /** `yyyy-MM-dd`, siempre un día válido. */
  fecha?: string;
  cantidad?: string;
  precio?: string;
  notas?: string;
}

/** Una asignación pendiente de guardar («Nuevo»), arriba de la lista. */
export interface AsignacionNueva {
  /** Identificador local (no es un id del servidor). */
  clave: number;
  producto: ProductoCatalogo;
  /** Solo en matrículas: el código del deporte del alumno. */
  deporte: string | null;
  /** El error del servidor del último guardado (decisión 296). */
  error: string | null;
  /**
   * Ronda 3: fecha de asignación que se enseña mientras no se guarda, «dd/mm/aaaa» del día local en que se añadió a la
   * lista. Solo para la vista: no se envía (la fecha real la pone el servidor al crear el cargo, decisión 285).
   */
  fechaPrevista: string;
}

/** Todo lo que la plantilla necesita de un cargo, ya calculado. */
export interface FilaCargo {
  id: number;
  /** El cargo tal como está guardado. */
  cargo: ProductoAlumnoDTO;
  concepto: string;
  /** Valores de los campos (lo editado o lo guardado). */
  fecha: string;
  cantidad: string;
  precio: string;
  notas: string;
  /** Regla 297 con los valores guardados o editados; nulo si un campo editado no vale. */
  total: number | null;
  /** «dd/mm/aaaa» o vacío. */
  fechaPago: string;
  estado: EstadoPago;
  textoCancelacion: string;
  /** Tiene cambios sin guardar (punto de acento). */
  modificada: boolean;
  marcadaBorrar: boolean;
  /** Fecha de asignación y notas. */
  editable: boolean;
  /** Precio y cantidad (decisión 292: no con un cobro activo). */
  precioCantidadEditables: boolean;
  /** «Revierte el cobro para cambiar precio o cantidad» en los pagados; si no, nulo. */
  notaPrecioCantidad: string | null;
  errores: { cantidad: ErrorCampoCargo | null; precio: ErrorCampoCargo | null };
  /** El error del servidor del último guardado (decisión 296). */
  errorServidor: string | null;
  /** Hay una acción en vuelo sobre este cargo (cobrar, cancelar, reactivar…). */
  ocupada: boolean;
  /**
   * Ronda 3: el `PUT` del cobro de este cargo está en vuelo («Cobrando…»). No con el diálogo de cobro abierto, ni al
   * cancelar, reactivar o releer (para eso, `ocupada`).
   */
  cobrando: boolean;
  /** El cargo está pendiente: lleva «Cobrar» (habilitado o no según `puedeCobrar`). */
  muestraCobrar: boolean;
  puedeCobrar: boolean;
  /** «Guarda los cambios antes de cobrar» (decisión 295); si no, nulo. */
  notaCobrar: string | null;
  puedeEditarNotas: boolean;
  puedeCancelar: boolean;
  puedeReactivar: boolean;
  puedeEliminar: boolean;
  puedeDeshacerBorrar: boolean;
  puedePedirReversion: boolean;
  /** Gestión, cargo pagado (no devuelto) y no cancelado: «Descargar recibo». */
  puedeDescargarRecibo: boolean;
}

export type EstadoCarga = 'cargando' | 'listo' | 'error';

/** Campos de la edición y su clave en el cuerpo del `PUT`. */
const CAMPOS_CUERPO: ReadonlyArray<[keyof EdicionCargo, keyof CambiosCargo]> = [
  ['fecha', 'fechaAsignacion'],
  ['cantidad', 'cantidad'],
  ['precio', 'precio'],
  ['notas', 'notas'],
];

/**
 * El conjunto que `CobroCargoService` llena con los cargos cuyo `PUT` de cobro está en vuelo (lo añade al lanzar la
 * llamada y lo quita al acabar): avisa de cada cambio, para que la fila sepa que está «Cobrando…».
 */
class ConjuntoAvisado extends Set<number> {
  private readonly alCambiar?: () => void;

  constructor(alCambiar: () => void) {
    super();
    this.alCambiar = alCambiar;
  }

  override add(id: number): this {
    super.add(id);
    this.alCambiar?.();
    return this;
  }

  override delete(id: number): boolean {
    const habia = super.delete(id);
    if (habia) {
      this.alCambiar?.();
    }
    return habia;
  }
}

type Resultado<V> = { ok: true; valor: V } | { ok: false };

function resultado<V>(peticion: Observable<V>): Observable<Resultado<V>> {
  return peticion.pipe(
    map((valor): Resultado<V> => ({ ok: true, valor })),
    catchError(() => of<Resultado<V>>({ ok: false }))
  );
}

/**
 * Estado de «Productos y cargos» de un alumno (8b-3n, boceto P1 de la ronda 4, decisiones 282 y 285 a 296). Se provee
 * en el componente: un estado por pantalla. Edición en lote: los cambios de los campos, los borrados marcados y las
 * asignaciones nuevas no llaman al servidor hasta `guardar()`. Cancelar, reactivar, cobrar y pedir la reversión van al
 * momento y conservan los demás cambios. Contrato completo en el informe de la pieza (`3n-contrato.md`).
 */
@Injectable()
export class ProductosAlumnoEstado {
  private readonly api = inject(ProductosAlumnoApiService);
  private readonly auth = inject(AuthenticationService);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly cobro = inject(CobroCargoService);
  private readonly solicitudes = inject(SolicitudesService);

  private revision = 0;
  private siguienteClave = 1;
  private destruido = false;
  private carga?: Subscription;
  private lote?: Subscription;
  private refresco?: Subscription;
  private readonly suscripciones = new Subscription();
  /** Los cargos con un cobro en vuelo, para `CobroCargoService`. */
  private readonly cobrando = new ConjuntoAvisado(() => this.avisarCobros());
  /** Los cargos dentro de `cobrar()` (del clic hasta que acaba): solo ellos cuentan como «Cobrando…», no «Deshacer». */
  private readonly enCobro = new Set<number>();

  /*
   * Ronda 3 (defecto 1): una relectura que empezó antes de que acabara la última mutación (guardar, cancelar,
   * reactivar, cobrar) puede traer datos viejos y pisar lo que respondió el servidor (un precio guardado, una
   * cancelación…). Se marcan en un mismo reloj el inicio de cada relectura y el final de cada mutación: la relectura
   * solo se aplica si no hay mutaciones en vuelo y ninguna acabó después de que empezara; si no, se vuelve a leer al
   * acabar la última.
   */
  private reloj = 0;
  private finUltimaMutacion = 0;
  private mutacionesEnVuelo = 0;
  private releerAlAcabar = false;
  /** Cargos que siguen ocupados hasta que se aplique una relectura (tras cobrar), aunque esta se repita. */
  private readonly ocupadosHastaRelectura = new Set<number>();

  // ------------------------------------------------------------------ estado de lectura

  readonly alumnoId = signal(0);
  readonly estado = signal<EstadoCarga>('cargando');
  /** Título del bloque de error de carga (los textos de hoy). */
  readonly errorCarga = signal('');
  readonly alumno = signal<AlumnoProductos | null>(null);
  /** Los cargos guardados, lo más reciente primero (el orden de hoy: el de la API al revés). */
  readonly cargos = signal<ProductoAlumnoDTO[]>([]);
  readonly catalogo = signal<ProductoCatalogo[]>([]);
  /** Propietario (`ROLE_ADMIN`): el único que elimina cargos. Falla cerrado. */
  readonly esPropietario = signal(false);
  readonly rolesCargados = signal(false);
  /** Propietario o administración (`ROLE_ADMIN` o `ROLE_MANAGER`): ve «Descargar recibo» (pieza 279). */
  readonly esGestion = signal(false);

  // ------------------------------------------------------------------ cambios pendientes

  readonly ediciones = signal<ReadonlyMap<number, EdicionCargo>>(new Map());
  readonly borrados = signal<ReadonlySet<number>>(new Set());
  readonly nuevas = signal<AsignacionNueva[]>([]);
  readonly erroresServidor = signal<ReadonlyMap<number, string>>(new Map());
  readonly guardando = signal(false);
  readonly ocupados = signal<ReadonlySet<number>>(new Set());
  /** Cargos con el `PUT` de su cobro en vuelo (`FilaCargo.cobrando`). */
  readonly cobrosEnVuelo = signal<ReadonlySet<number>>(new Set());
  readonly pagina = signal(1);

  // ------------------------------------------------------------------ bloque «Asignar producto»

  readonly busqueda = signal('');
  readonly productoElegido = signal<number | null>(null);
  readonly deporteElegido = signal<string | null>(null);
  /** Lo que falta al pulsar «Añadir a la lista»: `producto` («Seleccione un producto») o `deporte`. */
  readonly errorAsignacion = signal<'producto' | 'deporte' | null>(null);

  // ------------------------------------------------------------------ derivados

  readonly nombreAlumno = computed(() => {
    const a = this.alumno();
    return a ? `${a.nombre ?? ''} ${a.apellidos ?? ''}`.trim() : '';
  });
  /** «Volver a la ficha»: `/alumnosEditar/{id}?pestana=pagos`. */
  readonly rutaFicha = computed(() => ['/alumnosEditar', this.alumnoId()]);
  readonly parametrosFicha = { pestana: 'pagos' } as const;

  /** Códigos de los deportes del alumno (los de la matrícula). */
  readonly deportes = computed(() => (this.alumno()?.deportes ?? []).map((d) => d.deporte).filter((d) => !!d));
  readonly opcionesProducto = computed(() => filtrarCatalogo(this.catalogo(), this.busqueda()));
  readonly productoSeleccionado = computed(
    () => this.catalogo().find((p) => p.id === this.productoElegido()) ?? null
  );
  readonly matriculaElegida = computed(() => esMatricula(this.productoSeleccionado()?.concepto));
  readonly sinDeportes = computed(() => this.matriculaElegida() && this.deportes().length === 0);
  readonly puedeAnadir = computed(
    () =>
      this.estado() === 'listo' &&
      !this.guardando() &&
      this.productoSeleccionado() !== null &&
      (!this.matriculaElegida() || this.deportes().includes(this.deporteElegido() ?? ''))
  );

  readonly filas = computed<FilaCargo[]>(() => {
    const ediciones = this.ediciones();
    const borrados = this.borrados();
    const errores = this.erroresServidor();
    const ocupados = this.ocupados();
    const cobros = this.cobrosEnVuelo();
    const guardando = this.guardando();
    const propietario = this.esPropietario();
    const roles = this.rolesCargados();
    return this.cargos().map((cargo) =>
      this.fila(cargo, ediciones.get(cargo.id), borrados.has(cargo.id), errores.get(cargo.id) ?? null,
        ocupados.has(cargo.id), cobros.has(cargo.id), guardando, propietario, roles)
    );
  });
  readonly totalCargos = computed(() => this.cargos().length);
  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.totalCargos() / TAMANO_PAGINA)));
  readonly filasPagina = computed(() => {
    const inicio = (this.pagina() - 1) * TAMANO_PAGINA;
    return this.filas().slice(inicio, inicio + TAMANO_PAGINA);
  });
  /** «1-10 de N»: `desde` es 0 sin cargos. */
  readonly desde = computed(() => (this.totalCargos() === 0 ? 0 : (this.pagina() - 1) * TAMANO_PAGINA + 1));
  readonly hasta = computed(() => Math.min(this.pagina() * TAMANO_PAGINA, this.totalCargos()));

  /** Cambios sin guardar: filas modificadas, borrados marcados y asignaciones nuevas. */
  readonly pendientes = computed(() => {
    const borrados = this.borrados();
    let modificadas = 0;
    for (const id of this.ediciones().keys()) {
      if (!borrados.has(id)) {
        modificadas++;
      }
    }
    return modificadas + borrados.size + this.nuevas().length;
  });
  readonly hayCambios = computed(() => this.pendientes() > 0);
  /** Algún campo modificado no vale (cantidad o precio): no se guarda. */
  readonly hayErrores = computed(() =>
    this.filas().some((f) => f.modificada && (f.errores.cantidad !== null || f.errores.precio !== null))
  );
  readonly puedeGuardar = computed(
    () => this.estado() === 'listo' && this.hayCambios() && !this.hayErrores() && !this.guardando() && this.ocupados().size === 0
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destruido = true;
      this.revision++;
      this.carga?.unsubscribe();
      this.lote?.unsubscribe();
      this.refresco?.unsubscribe();
      this.suscripciones.unsubscribe();
    });
    this.resolverPropietario();
  }

  // ------------------------------------------------------------------ carga

  /** Abre la pantalla del alumno `alumnoId` (desde la ruta). */
  iniciar(alumnoId: number): void {
    this.alumnoId.set(alumnoId);
    this.cargar();
  }

  /**
   * Lee a la vez cargos, catálogo y alumno, y espera a los tres. Decisión 293: un alumno eliminado anonimizando se
   * enseña igual que uno que no existe (el mismo error de carga del alumno, que manda sobre los demás). Decisión 299:
   * lo dice la API (`alumnoEliminado` de `GET /alumnos/{id}`), no el nombre.
   */
  cargar(): void {
    this.revision++;
    this.carga?.unsubscribe();
    this.lote?.unsubscribe();
    this.refresco?.unsubscribe();
    this.mutacionesEnVuelo = 0;
    this.releerAlAcabar = false;
    this.ocupadosHastaRelectura.clear();
    this.limpiarPendientes();
    this.guardando.set(false);
    this.ocupados.set(new Set());
    this.alumno.set(null);
    this.cargos.set([]);
    this.catalogo.set([]);
    this.errorCarga.set('');
    this.estado.set('cargando');
    const id = this.alumnoId();
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.fallarCarga(T.errorAlumno);
      return;
    }
    this.carga = forkJoin({
      cargos: resultado(this.api.cargos(id)),
      catalogo: resultado(this.api.catalogo()),
      alumno: resultado(this.api.alumno(id)),
    }).subscribe(({ cargos, catalogo, alumno }) => {
      if (!alumno.ok || !alumno.valor || alumno.valor.alumnoEliminado === true) {
        this.fallarCarga(T.errorAlumno);
        return;
      }
      if (!cargos.ok) {
        this.fallarCarga(T.errorCargos);
        return;
      }
      if (!catalogo.ok) {
        this.fallarCarga(T.errorCatalogo);
        return;
      }
      this.alumno.set(alumno.valor);
      this.cargos.set(this.ordenar(cargos.valor));
      this.catalogo.set(Array.isArray(catalogo.valor) ? catalogo.valor : []);
      this.pagina.set(paginaValida(leerPaginaGuardada(id), this.totalCargos()));
      guardarPagina(id, this.pagina());
      this.estado.set('listo');
    });
  }

  private fallarCarga(texto: string): void {
    this.alumno.set(null);
    this.cargos.set([]);
    this.catalogo.set([]);
    this.errorCarga.set(texto);
    this.estado.set('error');
  }

  private ordenar(lista: ProductoAlumnoDTO[] | null | undefined): ProductoAlumnoDTO[] {
    return Array.isArray(lista) ? [...lista].reverse() : [];
  }

  /**
   * Vuelve a leer los cargos y los mezcla con lo pendiente: lo editado, marcado y nuevo se conserva (decisión 296);
   * solo cae lo de cargos que ya no existen. `ocupar`: cargo que sigue ocupado hasta que se aplique una relectura.
   * Ronda 3: una lectura que empezó antes de que acabara la última mutación, o que llega con una en vuelo, no se
   * aplica y se repite (al momento, o al acabar la mutación en vuelo).
   */
  private refrescar(ocupar?: number): void {
    const revision = this.revision;
    const id = this.alumnoId();
    if (ocupar !== undefined) {
      this.ocupadosHastaRelectura.add(ocupar);
      this.ocupar(ocupar, true);
    }
    this.releerAlAcabar = false;
    const inicio = ++this.reloj;
    this.refresco?.unsubscribe();
    this.refresco = this.api.cargos(id).subscribe({
      next: (lista) => {
        if (revision !== this.revision) {
          return;
        }
        if (this.mutacionesEnVuelo > 0 || this.finUltimaMutacion > inicio) {
          this.releerAlAcabar = true;
          if (this.mutacionesEnVuelo === 0) {
            this.refrescar();
          }
          return;
        }
        this.cargos.set(this.ordenar(lista));
        this.liberarTrasRelectura();
        this.depurarPendientes();
      },
      error: () => {
        if (revision === this.revision) {
          this.liberarTrasRelectura();
          this.avisos.error(T.errorCargos);
        }
      },
    });
  }

  /** Deja libres los cargos que esperaban una relectura (tras cobrar). */
  private liberarTrasRelectura(): void {
    for (const id of this.ocupadosHastaRelectura) {
      this.ocupar(id, false);
    }
    this.ocupadosHastaRelectura.clear();
  }

  /** Empieza una mutación (guardar, cancelar, reactivar): ninguna relectura se aplica hasta que acabe. */
  private empezarMutacion(): void {
    this.mutacionesEnVuelo++;
  }

  /** Acaba una mutación: si una relectura se descartó por ella (o `releer`), se vuelve a leer al acabar la última. */
  private acabarMutacion(releer = false): void {
    this.mutacionesEnVuelo = Math.max(0, this.mutacionesEnVuelo - 1);
    this.finUltimaMutacion = ++this.reloj;
    if (releer) {
      this.releerAlAcabar = true;
    }
    if (this.mutacionesEnVuelo === 0 && this.releerAlAcabar) {
      this.refrescar();
    }
  }

  /** `CobroCargoService` lanzó o acabó el `PUT` de un cobro: solo cuentan los de `cobrar()` (no «Deshacer»). */
  private avisarCobros(): void {
    this.cobrosEnVuelo.set(new Set([...this.cobrando].filter((id) => this.enCobro.has(id))));
  }

  /**
   * Quita lo pendiente de cargos que ya no están y lo editado que ya coincide con lo guardado. Ronda 3 (defecto 3): de
   * un cargo que la relectura trae cancelado se descartan fecha, cantidad y precio (el servidor los rechazaría siempre)
   * y se conservan las notas; la fila lo dice con `errorCanceladoDescartado`.
   */
  private depurarPendientes(): void {
    const porId = new Map(this.cargos().map((c) => [c.id, c]));
    const errores = new Map([...this.erroresServidor()].filter(([id]) => porId.has(id)));
    const ediciones = new Map<number, EdicionCargo>();
    for (const [id, edicion] of this.ediciones()) {
      const cargo = porId.get(id);
      if (!cargo) {
        continue;
      }
      let conservada = edicion;
      if (
        estaCancelado(cargo) &&
        (edicion.fecha !== undefined || edicion.cantidad !== undefined || edicion.precio !== undefined)
      ) {
        conservada = edicion.notas !== undefined ? { notas: edicion.notas } : {};
        errores.set(id, T.errorCanceladoDescartado);
      }
      const limpia = this.normalizar(cargo, conservada);
      if (limpia) {
        ediciones.set(id, limpia);
      }
    }
    this.ediciones.set(ediciones);
    this.borrados.set(new Set([...this.borrados()].filter((id) => porId.has(id))));
    this.erroresServidor.set(errores);
    this.irAPagina(this.pagina());
  }

  // ------------------------------------------------------------------ paginación

  irAPagina(pagina: number): void {
    const valida = paginaValida(pagina, this.totalCargos());
    this.pagina.set(valida);
    if (this.alumnoId() > 0) {
      guardarPagina(this.alumnoId(), valida);
    }
  }

  paginaAnterior(): void {
    this.irAPagina(this.pagina() - 1);
  }

  paginaSiguiente(): void {
    this.irAPagina(this.pagina() + 1);
  }

  // ------------------------------------------------------------------ filas

  private fila(
    cargo: ProductoAlumnoDTO,
    edicion: EdicionCargo | undefined,
    marcada: boolean,
    errorServidor: string | null,
    ocupada: boolean,
    cobrando: boolean,
    guardando: boolean,
    propietario: boolean,
    rolesCargados: boolean
  ): FilaCargo {
    const estado = estadoPago(cargo);
    const cancelado = estado === 'cancelado';
    const libre = !ocupada && !guardando;
    const editable = !cancelado && !marcada && libre;
    const conCobro = !cancelado && tieneCobroActivo(cargo);
    const cantidad = edicion?.cantidad ?? String(cargo.cantidad ?? '');
    const precio = edicion?.precio ?? precioParaCampo(cargo.precio);
    const lecturaCantidad = leerCantidad(cantidad);
    const lecturaPrecio = leerPrecio(precio);
    const modificada = !!edicion && !marcada;
    const pendiente = estado === 'pendiente';
    return {
      id: cargo.id,
      cargo,
      concepto: cargo.concepto ?? '',
      fecha: edicion?.fecha ?? diaLocal(cargo.fechaAsignacion),
      cantidad,
      precio,
      notas: edicion?.notas ?? cargo.notas ?? '',
      total:
        (edicion?.cantidad !== undefined && lecturaCantidad.valor === null) ||
        (edicion?.precio !== undefined && lecturaPrecio.valor === null)
          ? null
          : importeCargo(
              edicion?.precio !== undefined ? lecturaPrecio.valor : cargo.precio,
              edicion?.cantidad !== undefined ? lecturaCantidad.valor : cargo.cantidad
            ),
      fechaPago: cargo.fechaPago ? fecha(new Date(cargo.fechaPago)) : '',
      estado,
      textoCancelacion: textoCancelacion(cargo),
      modificada,
      marcadaBorrar: marcada,
      editable,
      precioCantidadEditables: editable && !conCobro,
      notaPrecioCantidad: conCobro ? T.notaPrecioConCobro : null,
      errores: {
        cantidad: edicion?.cantidad !== undefined ? lecturaCantidad.error : null,
        precio: edicion?.precio !== undefined ? lecturaPrecio.error : null,
      },
      errorServidor,
      ocupada,
      cobrando,
      muestraCobrar: pendiente,
      puedeCobrar: pendiente && !marcada && !modificada && libre,
      notaCobrar: pendiente && modificada ? T.notaCobrarConCambios : null,
      puedeEditarNotas: editable,
      puedeCancelar: pendiente && !marcada && libre,
      puedeReactivar: cancelado && libre,
      puedeEliminar: propietario && !cancelado && !cargo.pagado && !marcada && libre,
      puedeDeshacerBorrar: marcada && libre,
      puedePedirReversion: rolesCargados && !propietario && estado === 'pagado' && libre,
      puedeDescargarRecibo: rolesCargados && this.esGestion() && estado === 'pagado' && libre,
    };
  }

  private filaDe(id: number): FilaCargo | undefined {
    return this.filas().find((f) => f.id === id);
  }

  private cargoDe(id: number): ProductoAlumnoDTO | undefined {
    return this.cargos().find((c) => c.id === id);
  }

  /** Deja en la edición solo lo que difiere de lo guardado; nada → nulo. */
  private normalizar(cargo: ProductoAlumnoDTO, edicion: EdicionCargo): EdicionCargo | null {
    const limpia: EdicionCargo = { ...edicion };
    if (limpia.fecha !== undefined && limpia.fecha === diaLocal(cargo.fechaAsignacion)) {
      delete limpia.fecha;
    }
    if (limpia.cantidad !== undefined) {
      const valor = leerCantidad(limpia.cantidad).valor;
      if (valor !== null && valor === cargo.cantidad) {
        delete limpia.cantidad;
      }
    }
    if (limpia.precio !== undefined) {
      const valor = leerPrecio(limpia.precio).valor;
      if (valor !== null && typeof cargo.precio === 'number' && valor === cargo.precio) {
        delete limpia.precio;
      }
    }
    if (limpia.notas !== undefined && limpia.notas === (cargo.notas ?? '')) {
      delete limpia.notas;
    }
    return Object.keys(limpia).length > 0 ? limpia : null;
  }

  private editar(id: number, campo: keyof EdicionCargo, valor: string, requierePrecioCantidad = false): boolean {
    const fila = this.filaDe(id);
    if (!fila || !fila.editable || (requierePrecioCantidad && !fila.precioCantidadEditables)) {
      return false;
    }
    const cargo = fila.cargo;
    const edicion = this.normalizar(cargo, { ...(this.ediciones().get(id) ?? {}), [campo]: valor });
    const ediciones = new Map(this.ediciones());
    if (edicion) {
      ediciones.set(id, edicion);
    } else {
      ediciones.delete(id);
    }
    this.ediciones.set(ediciones);
    return true;
  }

  /**
   * Fecha de asignación (`yyyy-MM-dd` del campo). Un día vacío o que no existe no cambia nada (como hoy); al guardar
   * se cambia el día local y se conserva la hora (sin fecha, el mediodía).
   */
  cambiarFecha(id: number, dia: string): boolean {
    const cargo = this.cargoDe(id);
    if (!cargo || fechaConDia(cargo.fechaAsignacion, dia) === null) {
      return false;
    }
    return this.editar(id, 'fecha', dia);
  }

  /** Cantidad (texto del campo). No en cancelados, marcados ni con cobro activo (decisión 292). */
  cambiarCantidad(id: number, texto: string): boolean {
    return this.editar(id, 'cantidad', texto ?? '', true);
  }

  /** Precio (texto es-ES del campo). No en cancelados, marcados ni con cobro activo (decisión 292). */
  cambiarPrecio(id: number, texto: string): boolean {
    return this.editar(id, 'precio', texto ?? '', true);
  }

  /** Notas (lo que devuelve «Editar notas»): cuenta como cambio pendiente. */
  cambiarNotas(id: number, texto: string): boolean {
    return this.editar(id, 'notas', texto ?? '');
  }

  /** Deshace los cambios de los campos de un cargo. */
  deshacerFila(id: number): void {
    if (this.guardando() || !this.ediciones().has(id)) {
      return;
    }
    const ediciones = new Map(this.ediciones());
    ediciones.delete(id);
    this.ediciones.set(ediciones);
  }

  /** «Eliminar»: solo el propietario, en cargos sin pagar ni cancelados. Marca la fila; se borra al guardar. */
  marcarBorrar(id: number): boolean {
    if (!this.filaDe(id)?.puedeEliminar) {
      return false;
    }
    this.borrados.set(new Set([...this.borrados(), id]));
    return true;
  }

  /** «Deshacer» del borrado marcado: vuelven sus cambios de campos, si los tenía. */
  deshacerBorrar(id: number): void {
    if (!this.filaDe(id)?.puedeDeshacerBorrar) {
      return;
    }
    this.borrados.set(new Set([...this.borrados()].filter((b) => b !== id)));
  }

  // ------------------------------------------------------------------ asignar

  /** Ronda 3 (defecto 4): si la búsqueda deja fuera el producto elegido, se quita la elección (y su deporte). */
  buscarProducto(texto: string): void {
    this.busqueda.set(texto ?? '');
    const elegido = this.productoElegido();
    if (elegido !== null && !this.opcionesProducto().some((p) => p.id === elegido)) {
      this.elegirProducto(null);
    }
  }

  elegirProducto(productoId: number | null): void {
    this.productoElegido.set(productoId);
    this.deporteElegido.set(null);
    this.errorAsignacion.set(null);
  }

  elegirDeporte(deporte: string | null): void {
    this.deporteElegido.set(deporte || null);
    if (this.errorAsignacion() === 'deporte') {
      this.errorAsignacion.set(null);
    }
  }

  /** «Añadir a la lista»: la asignación queda arriba como «Nuevo» y se crea al guardar. */
  anadirALista(): boolean {
    if (this.estado() !== 'listo' || this.guardando()) {
      return false;
    }
    const producto = this.productoSeleccionado();
    if (!producto) {
      this.errorAsignacion.set('producto');
      return false;
    }
    const matricula = esMatricula(producto.concepto);
    const deporte = this.deporteElegido();
    if (matricula && (!deporte || !this.deportes().includes(deporte))) {
      this.errorAsignacion.set('deporte');
      return false;
    }
    this.nuevas.set([
      { clave: this.siguienteClave++, producto, deporte: matricula ? deporte : null, error: null, fechaPrevista: fecha(new Date()) },
      ...this.nuevas(),
    ]);
    this.productoElegido.set(null);
    this.deporteElegido.set(null);
    this.busqueda.set('');
    this.errorAsignacion.set(null);
    return true;
  }

  /** La X de una asignación nueva. */
  quitarNueva(clave: number): void {
    if (this.guardando()) {
      return;
    }
    this.nuevas.set(this.nuevas().filter((n) => n.clave !== clave));
  }

  /** «Descartar»: todo lo pendiente fuera; lo guardado no cambia. */
  descartar(): void {
    if (this.guardando()) {
      return;
    }
    this.limpiarPendientes();
  }

  private limpiarPendientes(): void {
    this.ediciones.set(new Map());
    this.borrados.set(new Set());
    this.nuevas.set([]);
    this.erroresServidor.set(new Map());
    this.productoElegido.set(null);
    this.deporteElegido.set(null);
    this.busqueda.set('');
    this.errorAsignacion.set(null);
  }

  // ------------------------------------------------------------------ guardar

  /**
   * El cuerpo del `PUT` (decisión 298): SOLO los campos editados de la fila (fecha de asignación, cantidad, precio o
   * notas), nunca el pago ni el resto del cargo; el servidor deja como está lo que no llega. La fecha cambia el día
   * local y conserva la hora (sin fecha, el mediodía). Precio y cantidad no van si la fila ya no los deja cambiar.
   */
  cuerpoActualizacion(cargo: ProductoAlumnoDTO, edicion: EdicionCargo): CambiosCargo {
    const cuerpo: CambiosCargo = {};
    if (edicion.fecha !== undefined) {
      const fechaAsignacion = fechaConDia(cargo.fechaAsignacion, edicion.fecha);
      if (fechaAsignacion !== null) {
        cuerpo.fechaAsignacion = fechaAsignacion;
      }
    }
    const precioCantidad = !estaCancelado(cargo) && !tieneCobroActivo(cargo);
    if (precioCantidad && edicion.cantidad !== undefined) {
      const cantidad = leerCantidad(edicion.cantidad).valor;
      if (cantidad !== null) {
        cuerpo.cantidad = cantidad;
      }
    }
    if (precioCantidad && edicion.precio !== undefined) {
      const precio = leerPrecio(edicion.precio).valor;
      if (precio !== null) {
        cuerpo.precio = precio;
      }
    }
    if (edicion.notas !== undefined) {
      cuerpo.notas = edicion.notas;
    }
    return cuerpo;
  }

  /**
   * Ronda 3 (defecto 2): por qué no se envía el `PUT` de una fila, o nulo si se envía. Nunca va un cuerpo vacío ni uno
   * que deje fuera un campo editado (el servidor respondería bien y el cambio se perdería sin aviso): un cargo que se
   * cobró mientras tanto con precio o cantidad pendientes da `errorCobradoPrecioCantidad`; cualquier otro hueco, el
   * error genérico. La fila conserva su cambio y cuenta como fallo.
   */
  private rechazoActualizacion(cargo: ProductoAlumnoDTO, edicion: EdicionCargo, cuerpo: CambiosCargo): string | null {
    const faltan = CAMPOS_CUERPO.filter(([campo, clave]) => edicion[campo] !== undefined && !(clave in cuerpo)).map(
      ([campo]) => campo
    );
    if (faltan.length === 0 && Object.keys(cuerpo).length > 0) {
      return null;
    }
    const precioCantidad = faltan.includes('precio') || faltan.includes('cantidad');
    return precioCantidad && !estaCancelado(cargo) && tieneCobroActivo(cargo) ? T.errorCobradoPrecioCantidad : T.errorGuardarCambio;
  }

  /**
   * «Guardar cambios», en secuencia: `PUT` de los modificados, `DELETE` de los marcados y `POST` de las asignaciones.
   * Sigue tras cada fallo. Decisión 296: lo que falla conserva su cambio, su punto y el error del servidor en la fila,
   * con el aviso «N cambios no se pudieron guardar»; lo que sale bien se aplica. Volver a guardar reintenta.
   */
  guardar(): void {
    if (!this.puedeGuardar()) {
      return;
    }
    const revision = this.revision;
    const alumnoId = this.alumnoId();
    const borrados = this.borrados();
    const modificados = this.filas().filter((f) => f.modificada);
    const marcados = this.cargos().filter((c) => borrados.has(c.id));
    const nuevas = [...this.nuevas()].reverse();
    let fallos = 0;
    // Ronda 3 (defecto 1): la relectura en curso es de antes de este guardado; se corta y se relee al acabar.
    this.refresco?.unsubscribe();
    this.empezarMutacion();
    this.guardando.set(true);
    this.erroresServidor.set(new Map());
    this.nuevas.set(this.nuevas().map((n) => ({ ...n, error: null })));

    const vigente = () => revision === this.revision;
    const fallo = (id: number, error: unknown, texto?: string) => {
      fallos++;
      if (vigente()) {
        const mensaje = texto ?? mensajeDeError(error) ?? T.errorGuardarCambio;
        this.erroresServidor.set(new Map(this.erroresServidor()).set(id, mensaje));
      }
      return EMPTY;
    };

    const operaciones: Observable<unknown>[] = [
      ...modificados.map((f) =>
        defer(() => {
          const edicion = this.ediciones().get(f.id);
          const cargo = this.cargoDe(f.id);
          if (!edicion || !cargo) {
            return EMPTY;
          }
          const cuerpo = this.cuerpoActualizacion(cargo, edicion);
          const rechazo = this.rechazoActualizacion(cargo, edicion, cuerpo);
          return rechazo ? fallo(f.id, null, rechazo) : this.api.actualizar(f.id, cuerpo);
        }).pipe(
          tap((actualizado) => {
            if (!vigente()) {
              return;
            }
            const ediciones = new Map(this.ediciones());
            ediciones.delete(f.id);
            this.ediciones.set(ediciones);
            this.sustituirCargo(f.id, actualizado);
          }),
          catchError((error) => fallo(f.id, error))
        )
      ),
      ...marcados.map((c) =>
        defer(() => this.api.eliminar(c.id)).pipe(
          tap(() => {
            if (!vigente()) {
              return;
            }
            this.cargos.set(this.cargos().filter((x) => x.id !== c.id));
            this.borrados.set(new Set([...this.borrados()].filter((id) => id !== c.id)));
            const ediciones = new Map(this.ediciones());
            ediciones.delete(c.id);
            this.ediciones.set(ediciones);
          }),
          catchError((error) => fallo(c.id, error))
        )
      ),
      ...nuevas.map((n) =>
        defer(() => this.api.asignar(alumnoId, n.producto.id, n.deporte, cuerpoAsignacion(alumnoId, n.producto.id))).pipe(
          tap((creado) => {
            if (!vigente()) {
              return;
            }
            this.nuevas.set(this.nuevas().filter((x) => x.clave !== n.clave));
            if (creado && typeof creado.id === 'number') {
              this.cargos.set([creado, ...this.cargos().filter((x) => x.id !== creado.id)]);
            }
          }),
          catchError((error) => {
            fallos++;
            if (vigente()) {
              const texto = mensajeDeError(error) ?? T.errorGuardarCambio;
              this.nuevas.set(this.nuevas().map((x) => (x.clave === n.clave ? { ...x, error: texto } : x)));
            }
            return EMPTY;
          })
        )
      ),
    ];

    this.lote = concat(...operaciones)
      .pipe(
        finalize(() => {
          if (!vigente()) {
            return;
          }
          this.guardando.set(false);
          if (fallos === 0) {
            this.avisos.exito(T.guardado);
          } else {
            this.avisos.error(textoFallosGuardado(fallos));
          }
          this.irAPagina(this.pagina());
          this.acabarMutacion(true);
        })
      )
      .subscribe();
  }

  /** Cambia un cargo guardado por la respuesta del servidor (si no trae cargo, se queda el que había). */
  private sustituirCargo(id: number, actualizado: ProductoAlumnoDTO | null | undefined): void {
    if (!actualizado || actualizado.id !== id) {
      return;
    }
    this.cargos.set(this.cargos().map((c) => (c.id === id ? actualizado : c)));
  }

  /** Tras cancelar o reactivar: el cargo nuevo y fuera sus cambios pendientes (ese cargo solo; los demás, intactos). */
  private aplicarAccion(actualizado: ProductoAlumnoDTO | null | undefined, id: number): void {
    this.sustituirCargo(id, actualizado);
    const ediciones = new Map(this.ediciones());
    ediciones.delete(id);
    this.ediciones.set(ediciones);
    this.borrados.set(new Set([...this.borrados()].filter((b) => b !== id)));
    const errores = new Map(this.erroresServidor());
    errores.delete(id);
    this.erroresServidor.set(errores);
  }

  private ocupar(id: number, si: boolean): void {
    const ocupados = new Set(this.ocupados());
    if (si) {
      ocupados.add(id);
    } else {
      ocupados.delete(id);
    }
    this.ocupados.set(ocupados);
  }

  // ------------------------------------------------------------------ acciones inmediatas

  /**
   * «Cobrar»: el diálogo y la llamada de la tesorería (`CobroCargoService`). Decisión 294: el importe es el `precio` del
   * cargo (lo que registra la caja), también con cantidad mayor que 1. Decisión 295: no con cambios sin guardar en la
   * fila. Cancelar el diálogo no envía nada. Tras cobrar (o deshacer), se relee y la fila sigue ocupada hasta entonces.
   */
  async cobrar(id: number): Promise<void> {
    const fila = this.filaDe(id);
    if (!fila?.puedeCobrar) {
      return;
    }
    const revision = this.revision;
    // Tras cobrar, la relectura deja libre la fila al llegar (no antes: sin ella, la fila seguiría «Pendiente»).
    let releyendo = false;
    this.ocupar(id, true);
    this.enCobro.add(id);
    try {
      await this.cobro.cobrar(
        {
          productoAlumnoId: id,
          concepto: fila.cargo.concepto,
          importe: fila.cargo.precio,
          alumnoNombre: this.nombreAlumno(),
        },
        this.cobrando,
        () => {
          if (revision === this.revision && !this.destruido) {
            // Ronda 3 (defecto 1): el cobro (o su «Deshacer») acaba de cambiar el cargo; una relectura anterior ya no vale.
            this.finUltimaMutacion = ++this.reloj;
            releyendo = true;
            this.refrescar(id);
          }
        }
      );
    } finally {
      this.enCobro.delete(id);
      this.avisarCobros();
      if (revision === this.revision && !releyendo) {
        this.ocupar(id, false);
      }
    }
  }

  /** «Cancelar cargo» (al momento): motivo obligatorio, recortado, de 500 caracteres como mucho. */
  async cancelar(id: number): Promise<void> {
    const fila = this.filaDe(id);
    if (!fila?.puedeCancelar) {
      return;
    }
    const revision = this.revision;
    const r = await this.confirmacion.confirmar({
      titulo: 'Cancelar cargo',
      mensaje: `«${fila.concepto}». No se borra: queda tachado en la ficha y se puede reactivar.`,
      accion: 'Cancelar cargo',
      cancelar: 'Volver',
      destructivo: true,
      nombre: 'cancelar-cargo',
      campos: [
        {
          id: 'motivo',
          tipo: 'textarea',
          etiqueta: 'Motivo',
          valor: '',
          placeholder: 'Por qué se cancela este cargo',
          atributos: { maxlength: '500', 'aria-label': 'Motivo de la cancelación' },
        },
      ],
      comprobar: (v) => {
        const motivo = (v['motivo'] ?? '').trim();
        if (!motivo) {
          return { campo: 'motivo', mensaje: 'Indica el motivo de la cancelación' };
        }
        return motivo.length > 500 ? { campo: 'motivo', mensaje: 'El motivo admite como mucho 500 caracteres' } : null;
      },
    });
    if (revision !== this.revision || this.destruido || !r.confirmado) {
      return;
    }
    const motivo = (r.valores?.['motivo'] ?? '').trim();
    if (!motivo || motivo.length > 500 || !this.filaDe(id)?.puedeCancelar) {
      return;
    }
    this.ocupar(id, true);
    this.empezarMutacion();
    this.suscripciones.add(
      this.api
        .cancelar(id, motivo)
        .pipe(
          finalize(() => {
            if (revision === this.revision) {
              this.ocupar(id, false);
              this.acabarMutacion();
            }
          })
        )
        .subscribe({
          next: (actualizado) => {
            if (revision !== this.revision) {
              return;
            }
            this.aplicarAccion(actualizado, id);
            this.avisos.exito(T.cancelado);
          },
          error: (error) => {
            if (revision === this.revision) {
              this.avisos.error(mensajeDeError(error) ?? 'No se pudo cancelar el cargo');
            }
          },
        })
    );
  }

  /** «Reactivar» (al momento, con confirmación): el cargo cancelado vuelve a pendiente. */
  async reactivar(id: number): Promise<void> {
    const fila = this.filaDe(id);
    if (!fila?.puedeReactivar) {
      return;
    }
    const revision = this.revision;
    let enviado = false;
    const r = await this.confirmacion.confirmar<ProductoAlumnoDTO>({
      titulo: 'Reactivar cargo',
      mensaje: `«${fila.concepto}» vuelve a estar pendiente de pago.`,
      accion: 'Reactivar',
      cancelar: 'Volver',
      nombre: 'reactivar-cargo',
      ejecutar: () => {
        this.ocupar(id, true);
        if (revision === this.revision && !enviado) {
          enviado = true;
          this.empezarMutacion();
        }
        return this.api.reactivar(id);
      },
    });
    if (revision !== this.revision || this.destruido) {
      return;
    }
    this.ocupar(id, false);
    if (!r.confirmado || r.error !== undefined) {
      if (enviado) {
        this.acabarMutacion();
      }
      if (r.confirmado) {
        this.avisos.error(mensajeDeError(r.error) ?? 'No se pudo reactivar el cargo');
      }
      return;
    }
    this.aplicarAccion(r.respuesta, id);
    if (enviado) {
      this.acabarMutacion();
    }
    this.avisos.exito(T.reactivado);
  }

  /**
   * «Pedir que se revierta el cobro» (gestor, cargo pagado): la solicitud `REVERTIR_COBRO` de la 6c con el diálogo de
   * la tesorería nueva (mismos datos, nota y motivo obligatorio). El importe es el del cobro (`precio`, decisión 294).
   */
  async pedirReversion(id: number): Promise<void> {
    const fila = this.filaDe(id);
    if (!fila?.puedePedirReversion) {
      return;
    }
    const revision = this.revision;
    const pago = fila.cargo.fechaPago ? new Date(fila.cargo.fechaPago) : null;
    const datos: [string, string][] = [
      ['Concepto', fila.concepto],
      ['Importe', euros(fila.cargo.precio)],
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
    if (revision !== this.revision || this.destruido || !r.confirmado || !r.valores) {
      return;
    }
    const motivo = (r.valores['motivo'] ?? '').trim();
    if (!motivo) {
      return;
    }
    this.suscripciones.add(
      this.solicitudes.crear({ tipo: 'REVERTIR_COBRO', productoAlumnoId: id, motivo }).subscribe({
        next: () => this.avisos.exito('Solicitud enviada al propietario'),
        error: (error) => this.avisos.error(mensajeDeError(error) ?? 'No se ha podido enviar la solicitud.'),
      })
    );
  }

  // ------------------------------------------------------------------ salir

  /**
   * Salir de la pantalla (`canDeactivate`): sin cambios, sí; con cambios (o guardando), «¿Salir sin guardar?» con los
   * cambios pendientes contados.
   */
  async confirmarSalida(): Promise<boolean> {
    if (!this.hayCambios() && !this.guardando()) {
      return true;
    }
    const pendientes = this.pendientes();
    const r = await this.confirmacion.confirmar({
      titulo: '¿Salir sin guardar?',
      ...(pendientes > 0 ? { mensaje: textoSalirSinGuardar(pendientes) } : {}),
      accion: 'Salir sin guardar',
      cancelar: 'Seguir editando',
      destructivo: true,
      nombre: 'salir-sin-guardar',
    });
    return r.confirmado;
  }

  // ------------------------------------------------------------------ roles

  private resolverPropietario(): void {
    if (this.auth.rolesEstanCargados()) {
      this.esPropietario.set(this.auth.tieneRolAdmin());
      this.esGestion.set(this.auth.tieneAccesoAdmin());
      this.rolesCargados.set(true);
      return;
    }
    this.suscripciones.add(
      this.auth.obtenerRoles().subscribe({
        next: () => {
          this.esPropietario.set(this.auth.tieneRolAdmin());
          this.esGestion.set(this.auth.tieneAccesoAdmin());
          this.rolesCargados.set(true);
        },
        error: () => {
          this.esPropietario.set(false);
          this.esGestion.set(false);
          this.rolesCargados.set(false);
        },
      })
    );
  }
}
