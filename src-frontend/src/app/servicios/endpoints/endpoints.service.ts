import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { environment } from '../../../environments/environment';
import { Turno } from '../../interfaces/turno';
import { BehaviorSubject } from 'rxjs/internal/BehaviorSubject';
import { Observable } from 'rxjs/internal/Observable';
import { of } from 'rxjs/internal/observable/of';
import { throwError } from 'rxjs/internal/observable/throwError';
import { catchError } from 'rxjs/internal/operators/catchError';
import { finalize } from 'rxjs/internal/operators/finalize';
import { shareReplay } from 'rxjs/internal/operators/shareReplay';
import { tap } from 'rxjs/internal/operators/tap';
import { Producto } from '../../interfaces/producto';
import { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { ConvocatoriaDTO } from '../../interfaces/convocatoria-dto';
import { Documento } from '../../interfaces/documento';
import { RetoDiarioEstado } from '../../interfaces/reto-diario-estado';
import { TesoreriaResumen } from '../../interfaces/tesoreria-resumen';
import { TesoreriaMovimiento } from '../../interfaces/tesoreria-movimiento';
import { PaginatedResponse } from '../../interfaces/paginated-response';
import { AuditoriaEvento } from '../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../interfaces/auditoria-evento-detalle';

@Injectable({
  providedIn: 'root',
})
export class EndpointsService {
  private readonly urlBase = environment.apiUrl;

  // Headers to skip global loading spinner
  private readonly skipLoadingHeaders = new HttpHeaders({
    'X-Skip-Loading': 'true'
  });

  constructor(private readonly http: HttpClient) {}

  private readonly gruposDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public gruposDelAlumno$ = this.gruposDelAlumnoSubject.asObservable();

  private readonly turnosDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelAlumno$ = this.turnosDelAlumnoSubject.asObservable();

  private readonly conteoAlumnosPorGrupoSubject = new BehaviorSubject<any>({});
  public conteoAlumnosPorGrupo$ =
    this.conteoAlumnosPorGrupoSubject.asObservable();
  public conteoAlumnosPorGrupo: any = {};

  private readonly turnosDelGrupoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelGrupo$ = this.turnosDelGrupoSubject.asObservable();

  private readonly turnosSubject = new BehaviorSubject<Turno[]>([]);
  public turnos$ = this.turnosSubject.asObservable();

  private readonly eventosSubject = new BehaviorSubject<any[]>([]);
  public eventos$ = this.eventosSubject.asObservable();

  private turnosDtoCache: any[] | null = null;
  private turnosDtoRequest$: Observable<any[]> | null = null;
  private eventosPublicosCache: any[] | null = null;
  private eventosPublicosRequest$: Observable<any[]> | null = null;

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }

  private limpiarCacheTurnosDTO(): void {
    this.turnosDtoCache = null;
    this.turnosDtoRequest$ = null;
  }

  private limpiarCacheEventosPublicos(): void {
    this.eventosPublicosCache = null;
    this.eventosPublicosRequest$ = null;
  }

  obtenerAlumnos(
    page: number,
    size: number,
    nombre: string = '',
    incluirInactivos: boolean = false,
    aptoParaExamen: boolean = false
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('incluirInactivos', incluirInactivos.toString());

    if (nombre) {
      params = params.set('nombre', nombre);
    }

    if (aptoParaExamen) {
      params = params.set('aptoParaExamen', 'true');
    }

    return this.http
      .get<any>(`${this.urlBase}/alumnos`, {
        params,
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAlumnosSinPaginar(
    incluirInactivos: boolean = false
  ): Observable<any[]> {
    const params = new HttpParams().set(
      'incluirInactivos',
      incluirInactivos.toString()
    );

    return this.http
      .get<any[]>(`${this.urlBase}/alumnos`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosAlumnosSinPaginar(
    mostrarInactivos: boolean = false
  ): Observable<any[]> {
    const params = new HttpParams().set(
      'incluirInactivos',
      mostrarInactivos.toString()
    );

    return this.http
      .get<any[]>(`${this.urlBase}/alumnos`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAlumnoPorId(id: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  countAlumnos(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/count`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerDistribucionAlumnosPorDeporte(): Observable<Record<string, number>> {
    return this.http
      .get<Record<string, number>>(`${this.urlBase}/alumnos/deportes/distribucion`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGruposDelAlumno(alumnoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}/grupos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (grupos) => {
          this.gruposDelAlumnoSubject.next(grupos);
        },
        error: (error) => {
          console.error('Error al obtener los grupos del alumno:', error);
        },
      });
  }

  obtenerTurnosDelAlumnoEnGrupo(grupoId: number, alumnoId: number): void {
    this.http
      .get<any[]>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}/turnos`,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosDelGrupoSubject.next(turnos); // Actualiza el BehaviorSubject con los turnos filtrados
        },
        error: (error) => {
          console.error(
            'Error al obtener los turnos del alumno en el grupo:',
            error
          );
        },
      });
  }

  asignarAlumnoATurno(alumnoId: number, turnoId: number): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${alumnoId}/turnos/${turnoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  removerAlumnoDeTurno(alumnoId: number, turnoId: number): Observable<Turno[]> {
    return this.http
      .delete<Turno[]>(
        `${this.urlBase}/alumnos/${alumnoId}/turnos/${turnoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelAlumno(alumnoId: number): void {
    this.http
      .get<any[]>(`${this.urlBase}/alumnos/${alumnoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosDelAlumnoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del alumno:', error);
        },
      });
  }

  // Observable-returning versions for direct use in components
  obtenerGruposDelAlumnoObservable(alumnoId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/${alumnoId}/grupos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelAlumnoObservable(alumnoId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/${alumnoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  asignarAlumnoAGrupo(alumnoId: number, grupoId: number): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${alumnoId}/grupos/${grupoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  removerAlumnoDeGrupo(alumnoId: number, grupoId: number): Observable<any> {
    return this.http
      .delete<any>(
        `${this.urlBase}/alumnos/${alumnoId}/grupos/${grupoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  crearAlumno(alumnoData: any, imagen: File | null): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(alumnoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/alumnos/crear`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarAlumno(id: number, formData: FormData): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${id}`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarObservacionesAlumno(id: number, observaciones: string | null): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${id}/observaciones`, { observaciones }, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarImagenAlumno(id: number): Observable<any> {
    return this.http
      .delete(`${this.urlBase}/alumnos/${id}/imagen`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnos(id: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/alumnos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  darDeBajaAlumno(alumnoId: number): Observable<any> {
    return this.http
      .put<any>(
        `${this.urlBase}/alumnos/${alumnoId}/baja`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  darDeAltaAlumno(alumnoId: number): Observable<any> {
    return this.http
      .put<any>(
        `${this.urlBase}/alumnos/${alumnoId}/alta`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  // Obtener todos los alumnos aptos para examen (genérico)
  obtenerAlumnosAptosParaExamen(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/aptos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  // Obtener alumnos aptos para examen por deporte
  obtenerAlumnosAptosPorDeporte(deporte: string): Observable<any[]> {
    const params = new HttpParams().set('deporte', deporte);
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/aptos/deporte`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  // Obtener un alumno apto para examen por su ID
  obtenerAlumnoAptoPorId(id: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/aptos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  // Obtener alumnos elegibles para una convocatoria específica basándose en el deporte
  obtenerAlumnosElegiblesParaConvocatoria(deporte: string): Observable<any[]> {
    const params = new HttpParams().set('deporte', deporte);
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/aptos/convocatoria`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnoAConvocatoria(
    alumnoId: number,
    convocatoriaId: number,
    porRecompensa: boolean,
    rojoBordado?: boolean
  ): Observable<any> {
    const payload: any = { porRecompensa };
    if (rojoBordado !== undefined) {
      payload.rojoBordado = rojoBordado;
    }
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${convocatoriaId}/alumno/${alumnoId}`,
        payload, // Enviar como JSON
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  /**
   * Add alumno to convocatoria with multi-sport support
   * @param convocatoriaId ID of the convocatoria
   * @param alumnoConvocatoriaData Data including alumno, convocatoria, and alumnoDeporte IDs
   * @param porRecompensa Whether the exam is by reward or seniority
   */
  agregarAlumnoAConvocatoriaMultiDeporte(
    convocatoriaId: number,
    alumnoConvocatoriaData: any,
    porRecompensa: boolean,
    rojoBordado?: boolean
  ): Observable<any> {
    const alumnoId = alumnoConvocatoriaData.alumno.id;
    const payload: any = {
      porRecompensa,
      alumnoDeporteId: alumnoConvocatoriaData.alumnoDeporte.id
    };
    if (rojoBordado !== undefined) {
      payload.rojoBordado = rojoBordado;
    }
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${convocatoriaId}/alumno/${alumnoId}`,
        payload,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnoDeConvocatoria(
    alumnoId: number,
    convocatoriaId: number
  ): Observable<any> {
    return this.http
      .delete<any>(
        `${this.urlBase}/alumnos/${convocatoriaId}/alumno/${alumnoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  pasarGradoPorRecompensa(
    alumnoId: number,
    deporte: string,
    rojoBordado?: boolean
  ): Observable<any> {
    const payload = rojoBordado === undefined ? null : { rojoBordado };
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${alumnoId}/deportes/${deporte}/pase-recompensa`,
        payload,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  pasarGradoConDerechoExamen(
    alumnoId: number,
    deporte: string,
    rojoBordado?: boolean
  ): Observable<any> {
    const payload = rojoBordado === undefined ? null : { rojoBordado };
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${alumnoId}/deportes/${deporte}/pase-examen`,
        payload,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerConvocatoriasDeAlumno(
    alumnoId: number
  ): Observable<ConvocatoriaDTO[]> {
    return this.http
      .get<ConvocatoriaDTO[]>(
        `${this.urlBase}/convocatorias/alumnos/${alumnoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosProductos(): Observable<Producto[]> {
    return this.http
      .get<Producto[]>(`${this.urlBase}/productos/todos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosProductosPaginado(
    concepto: string,
    page: number,
    size: number,
    orderBy: string,
    order: string
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('orderBy', orderBy)
      .set('order', order);

    if (concepto) {
      params = params.set('concepto', concepto);
    }

    return this.http
      .get<any>(`${this.urlBase}/productos`, {
        params: params,
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerProductoPorId(id: number): Observable<Producto> {
    return this.http
      .get<Producto>(`${this.urlBase}/productos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearProducto(producto: Producto): Observable<Producto> {
    return this.http
      .post<Producto>(`${this.urlBase}/productos`, producto, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarProducto(id: number, formData: FormData): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/productos/${id}`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarProducto(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.urlBase}/productos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerProductosDelAlumno(alumnoId: number): Observable<ProductoAlumnoDTO[]> {
    return this.http
      .get<ProductoAlumnoDTO[]>(
        `${this.urlBase}/productos-alumno/alumno/${alumnoId}`,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError));
  }

  asignarProductoAAlumno(
    alumnoId: number,
    productoId: number,
    detalles: ProductoAlumnoDTO
  ): Observable<ProductoAlumnoDTO> {
    return this.http
      .post<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/alumno/${alumnoId}/producto/${productoId}`,
        detalles,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  actualizarProductoAlumno(
    id: number,
    detalles: Partial<ProductoAlumnoDTO>
  ): Observable<ProductoAlumnoDTO> {
    return this.http
      .put<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${id}`,
        detalles,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError));
  }

  actualizarEstadoCobro(
    productoAlumnoId: number,
    pagado: boolean,
    fechaPago?: string | Date | null,
    motivoCambio?: string | null
  ): Observable<ProductoAlumnoDTO> {
    const payload: Partial<ProductoAlumnoDTO> = { pagado };
    if (fechaPago !== undefined) {
      payload.fechaPago = fechaPago as any;
    }
    if (motivoCambio !== undefined) {
      payload.motivoCambio = motivoCambio;
    }

    return this.http
      .put<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${productoAlumnoId}`,
        payload,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError));
  }

  actualizarCobroTesoreria(
    productoAlumnoId: number,
    cambios: Partial<ProductoAlumnoDTO>
  ): Observable<ProductoAlumnoDTO> {
    return this.http
      .put<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${productoAlumnoId}`,
        cambios,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError));
  }

  eliminarProductoAlumno(id: number): Observable<any> {
    return this.http
      .delete<void>(`${this.urlBase}/productos-alumno/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  reservarPlaza(
    alumnoId: number,
    pagado: boolean,
    forzar: boolean = false
  ): Observable<ProductoAlumnoDTO> {
    const params = new HttpParams()
      .set('pagado', pagado.toString())
      .set('forzar', forzar.toString());
    return this.http
      .post<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${alumnoId}/reservar-plaza`,
        null,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  reservarPlazaPorDeporte(
    alumnoId: number,
    deporte: string,
    pagado: boolean,
    forzar: boolean = false
  ): Observable<ProductoAlumnoDTO> {
    const params = new HttpParams()
      .set('deporte', deporte)
      .set('pagado', pagado.toString())
      .set('forzar', forzar.toString());
    return this.http
      .post<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${alumnoId}/reservar-plaza-deporte`,
        null,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  cargarMensualidadesGenerales(
    mesAno: string,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams();
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(`${this.urlBase}/productos-alumno/mensualidades/general`, mesAno, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  cargarMensualidadesPorDeporte(
    mesAno: string,
    deporte: string,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams().set('deporte', deporte);
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(`${this.urlBase}/productos-alumno/mensualidades/deporte`, mesAno, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  cargarMensualidadIndividual(
    alumnoId: number,
    mesAno: string,
    forzar: boolean = false,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams()
      .set('alumnoId', alumnoId.toString())
      .set('forzar', forzar.toString());
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(
        `${this.urlBase}/productos-alumno/mensualidades/individual`,
        mesAno,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  cargarLicenciasGenerales(
    ano: number,
    deporte: string,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams()
      .set('ano', ano.toString())
      .set('deporte', deporte);
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(
        `${this.urlBase}/productos-alumno/licencias/general`,
        null,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  cargarLicenciaIndividual(
    alumnoId: number,
    ano: number,
    deporte: string,
    forzar: boolean = false,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams()
      .set('alumnoId', alumnoId.toString())
      .set('ano', ano.toString())
      .set('deporte', deporte)
      .set('forzar', forzar.toString());
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(
        `${this.urlBase}/productos-alumno/licencias/individual`,
        null,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  cargarMensualidadesMultiDeporte(mesAno: string): Observable<any> {
    return this.http
      .post(`${this.urlBase}/productos-alumno/mensualidades/multi-deporte`, mesAno, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  cargarMensualidadIndividualPorDeporte(
    alumnoId: number,
    mesAno: string,
    deporte: string,
    forzar: boolean = false,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams()
      .set('alumnoId', alumnoId.toString())
      .set('deporte', deporte)
      .set('forzar', forzar.toString());
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http
      .post(
        `${this.urlBase}/productos-alumno/mensualidades/individual-deporte`,
        mesAno,
        { params, withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  renovarLicencia(alumnoId: number): Observable<ProductoAlumnoDTO> {
    return this.http
      .post<ProductoAlumnoDTO>(
        `${this.urlBase}/productos-alumno/${alumnoId}/renovar-licencia`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerTesoreriaResumen(
    mes: number | null,
    ano: number | null,
    deporte: string = 'TODOS',
    soloActivos: boolean = true
  ): Observable<TesoreriaResumen> {
    const params = this.construirParamsTesoreria(mes, ano, deporte, undefined, undefined, soloActivos);

    return this.http
      .get<TesoreriaResumen>(`${this.urlBase}/tesoreria/resumen`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTesoreriaMovimientos(
    mes: number | null,
    ano: number | null,
    deporte: string = 'TODOS',
    pagado?: boolean,
    texto?: string,
    soloActivos: boolean = true,
    page: number = 1,
    size: number = 25
  ): Observable<PaginatedResponse<TesoreriaMovimiento>> {
    const params = this.construirParamsTesoreria(mes, ano, deporte, pagado, texto, soloActivos);
    const paramsConPaginacion = params
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http
      .get<PaginatedResponse<TesoreriaMovimiento>>(`${this.urlBase}/tesoreria/movimientos`, {
        params: paramsConPaginacion,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTesoreriaAniosDisponibles(): Observable<number[]> {
    return this.http
      .get<number[]>(`${this.urlBase}/tesoreria/anios-disponibles`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  exportarTesoreriaPDF(
    mes: number | null,
    ano: number | null,
    deporte: string = 'TODOS',
    pagado?: boolean,
    texto?: string,
    soloActivos: boolean = true
  ): Observable<Blob> {
    const params = this.construirParamsTesoreria(mes, ano, deporte, pagado, texto, soloActivos);

    return this.http
      .get(`${this.urlBase}/tesoreria/export/pdf`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  exportarTesoreriaCSV(
    mes: number | null,
    ano: number | null,
    deporte: string = 'TODOS',
    pagado?: boolean,
    texto?: string,
    soloActivos: boolean = true
  ): Observable<Blob> {
    const params = this.construirParamsTesoreria(mes, ano, deporte, pagado, texto, soloActivos);

    return this.http
      .get(`${this.urlBase}/tesoreria/export/csv`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  private construirParamsTesoreria(
    mes: number | null,
    ano: number | null,
    deporte: string,
    pagado?: boolean,
    texto?: string,
    soloActivos?: boolean
  ): HttpParams {
    let params = new HttpParams();

    if (ano !== null && ano !== undefined) {
      params = params.set('ano', ano.toString());
    }

    if (mes !== null && mes !== undefined) {
      params = params.set('mes', mes.toString());
    }

    if (deporte && deporte !== 'TODOS') {
      params = params.set('deporte', deporte);
    }

    if (pagado !== undefined && pagado !== null) {
      params = params.set('pagado', pagado.toString());
    }

    const textoNormalizado = texto?.trim();
    if (textoNormalizado) {
      params = params.set('texto', textoNormalizado);
    }

    if (soloActivos !== undefined && soloActivos !== null) {
      params = params.set('soloActivos', soloActivos.toString());
    }

    return params;
  }

  obtenerAuditoriaEventos(
    filtros: {
      desde?: string | null;
      hasta?: string | null;
      resultado?: string | null;
      accion?: string | null;
      modulo?: string | null;
      usuario?: string | null;
      endpoint?: string | null;
      texto?: string | null;
    },
    page: number = 1,
    size: number = 25
  ): Observable<PaginatedResponse<AuditoriaEvento>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (filtros.desde) {
      params = params.set('desde', filtros.desde);
    }
    if (filtros.hasta) {
      params = params.set('hasta', filtros.hasta);
    }
    if (filtros.resultado) {
      params = params.set('resultado', filtros.resultado);
    }
    if (filtros.accion) {
      params = params.set('accion', filtros.accion);
    }
    if (filtros.modulo) {
      params = params.set('modulo', filtros.modulo);
    }
    if (filtros.usuario) {
      params = params.set('usuario', filtros.usuario.trim());
    }
    if (filtros.endpoint) {
      params = params.set('endpoint', filtros.endpoint.trim());
    }
    if (filtros.texto) {
      params = params.set('texto', filtros.texto.trim());
    }

    return this.http
      .get<PaginatedResponse<AuditoriaEvento>>(`${this.urlBase}/admin/auditoria/eventos`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAuditoriaEventoDetalle(id: number): Observable<AuditoriaEventoDetalle> {
    return this.http
      .get<AuditoriaEventoDetalle>(`${this.urlBase}/admin/auditoria/eventos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAuditoriaModulos(): Observable<string[]> {
    return this.http
      .get<string[]>(`${this.urlBase}/admin/auditoria/modulos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrados(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGradosPorFechaNacimiento(fechaNacimiento: string, deporte: string = 'TAEKWONDO'): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados/disponibles/${fechaNacimiento}?deporte=${deporte}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosGrupos(): Observable<GrupoDTO[]> {
    return this.http
      .get<GrupoDTO[]>(`${this.urlBase}/grupos`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrupoPorId(id: number): Observable<GrupoDTO> {
    return this.http
      .get<GrupoDTO>(`${this.urlBase}/grupos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerConteoAlumnosPorGrupo(): void {
    this.http
      .get<any>(`${this.urlBase}/grupos/conteo-alumnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (conteo) => {
          this.conteoAlumnosPorGrupoSubject.next(conteo);
          this.conteoAlumnosPorGrupo = conteo;
        },
        error: (error) => {
          console.error(
            'Error al obtener el conteo de alumnos por grupo:',
            error
          );
        },
      });
  }

  obtenerAlumnosPorTipo(tipo: string): Observable<any[]> {
    const encodedTipo = encodeURIComponent(tipo);
    return this.http
      .get<any[]>(`${this.urlBase}/grupos/tipo/${encodedTipo}/alumnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearGrupo(grupoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos`, grupoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarGrupo(id: number, grupoData: GrupoDTO): Observable<GrupoDTO> {
    return this.http
      .put<GrupoDTO>(`${this.urlBase}/grupos/${id}`, grupoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarGrupo(id: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnoAGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnosAGrupo(grupoId: number, alumnosIds: number[]): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/alumnos`, alumnosIds, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnoDeGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelGrupo(grupoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          console.log('Turnos recibidos:', turnos);
          this.turnosDelGrupoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del grupo:', error);
        },
      });
  }

  obtenerTurnosDelGrupoPorId(grupoId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError));
  }

  agregarTurnoAGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, null, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  eliminarTurnoDeGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  obtenerTurnos(): void {
    this.http
      .get<any>(`${this.urlBase}/turnos`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del alumno:', error);
        },
      });
  }

  obtenerTurnosDTO(forceRefresh: boolean = false): Observable<any[]> {
    if (forceRefresh) {
      this.limpiarCacheTurnosDTO();
    }

    if (this.turnosDtoCache) {
      return of(this.turnosDtoCache);
    }

    if (this.turnosDtoRequest$) {
      return this.turnosDtoRequest$;
    }

    const request$ = this.http
      .get<any[]>(`${this.urlBase}/turnos/dto`)
      .pipe(
        tap((turnos) => {
          this.turnosDtoCache = Array.isArray(turnos) ? turnos : [];
        }),
        finalize(() => {
          this.turnosDtoRequest$ = null;
        }),
        shareReplay(1),
        catchError((error) => {
          this.turnosDtoCache = null;
          return this.manejarError(error);
        })
      );

    this.turnosDtoRequest$ = request$;
    return request$;
  }

  obtenerTodosLosTurnos(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/turnos`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnoPorId(turnoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearTurnoSinGrupo(turnoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear`, turnoData, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  crearTurnoConGrupo(turnoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear-asignando-grupo`, turnoData, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  actualizarTurno(turnoId: number, turnoData: any): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/turnos/${turnoId}`, turnoData, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  eliminarTurno(turnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheTurnosDTO()),
        catchError(this.manejarError)
      );
  }

  obtenerEventos(forceRefresh: boolean = false): void {
    if (forceRefresh) {
      this.limpiarCacheEventosPublicos();
    }

    if (this.eventosPublicosCache) {
      this.eventosSubject.next(this.eventosPublicosCache);
      return;
    }

    if (this.eventosPublicosRequest$) {
      return;
    }

    const request$ = this.http
      .get<any[]>(`${this.urlBase}/eventos`)
      .pipe(
        tap((eventos) => {
          const eventosNormalizados = Array.isArray(eventos) ? eventos : [];
          this.eventosPublicosCache = eventosNormalizados;
          this.eventosSubject.next(eventosNormalizados);
        }),
        finalize(() => {
          this.eventosPublicosRequest$ = null;
        }),
        shareReplay(1),
        catchError((error) => {
          this.eventosPublicosCache = null;
          this.eventosSubject.next([]);
          return this.manejarError(error);
        })
      );

    this.eventosPublicosRequest$ = request$;
    request$.subscribe({
      error: (error) => {
        console.error('Error al obtener los eventos:', error);
      },
    });
  }

  obtenerTodosLosEventos(): void {
    this.http
      .get<any[]>(`${this.urlBase}/eventos/admin/todos`, {
        withCredentials: true,
        headers: this.skipLoadingHeaders
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (eventos) => {
          this.eventosSubject.next(Array.isArray(eventos) ? eventos : []);
        },
        error: (error) => {
          console.error('Error al obtener todos los eventos:', error);
          this.eventosSubject.next([]);
        },
      });
  }

  obtenerEventoPorId(eventoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/eventos/${eventoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearEvento(eventoData: any, imagen: File | null): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(eventoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/eventos/crear`, formData, {
        withCredentials: true,
      })
      .pipe(
        tap((nuevoEvento) => {
          this.limpiarCacheEventosPublicos();
          const eventosActuales = this.eventosSubject.getValue();
          this.eventosSubject.next([...eventosActuales, nuevoEvento]);
        }),
        catchError(this.manejarError)
      );
  }

  actualizarEvento(id: number, formData: FormData): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/eventos/${id}`, formData, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheEventosPublicos()),
        catchError(this.manejarError)
      );
  }

  eliminarImagenEvento(id: number): Observable<any> {
    return this.http
      .delete(`${this.urlBase}/eventos/${id}/imagen`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarEvento(id: number): void {
    this.http
      .delete<any>(`${this.urlBase}/eventos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: () => {
          this.limpiarCacheEventosPublicos();
          const eventosActuales = this.eventosSubject.getValue();
          const eventosActualizados = eventosActuales.filter(
            (evento) => evento.id !== id
          );
          this.eventosSubject.next(eventosActualizados);
        },
        error: (error) => {
          console.error('Error al eliminar el evento:', error);
        },
      });
  }

  toggleVisibilidadEvento(id: number): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/eventos/${id}/toggle-visibilidad`, null, {
        withCredentials: true,
      })
      .pipe(
        tap(() => {
          this.limpiarCacheEventosPublicos();
          const eventosActuales = this.eventosSubject.getValue();
          const eventosActualizados = eventosActuales.map((evento) =>
            evento.id === id ? { ...evento, visible: !evento.visible } : evento
          );
          this.eventosSubject.next(eventosActualizados);
        }),
        catchError(this.manejarError)
      );
  }

  actualizarOrdenEventos(ordenIds: number[]): Observable<void> {
    return this.http
      .put<void>(`${this.urlBase}/eventos/orden`, ordenIds, {
        withCredentials: true,
      })
      .pipe(
        tap(() => this.limpiarCacheEventosPublicos()),
        catchError(this.manejarError)
      );
  }

  // ==================== DOCUMENTOS DE EVENTOS ====================

  obtenerDocumentosEvento(eventoId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/eventos/${eventoId}/documentos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  subirDocumentoEvento(eventoId: number, archivo: File): Observable<any> {
    const formData = new FormData();
    formData.append('archivo', archivo);

    return this.http
      .post<any>(`${this.urlBase}/eventos/${eventoId}/documentos`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarDocumentoEvento(eventoId: number, documentoId: number): Observable<any> {
    return this.http
      .delete(`${this.urlBase}/eventos/${eventoId}/documentos/${documentoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerUrlDescargaDocumentoEvento(eventoId: number, documentoId: number): string {
    return `${this.urlBase}/eventos/${eventoId}/documentos/${documentoId}/descargar`;
  }

  descargarDocumentoEvento(eventoId: number, documentoId: number): Observable<Blob> {
    return this.http
      .get(this.obtenerUrlDescargaDocumentoEvento(eventoId, documentoId), {
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerConvocatorias(deporte?: string): Observable<any[]> {
    let params = new HttpParams();
    if (deporte) {
      params = params.set('deporte', deporte);
    }
    return this.http
      .get<any[]>(`${this.urlBase}/convocatorias`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  // Obtener convocatoria por ID
  obtenerConvocatoriaPorId(id: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/convocatorias/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  // Crear una nueva convocatoria
  crearConvocatoria(convocatoria: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/convocatorias`, convocatoria, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAlumnosDeConvocatoria(convocatoriaId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/convocatorias/${convocatoriaId}/alumnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerReporteDeConvocatoria(convocatoriaId: number): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/convocatorias/${convocatoriaId}/reporte`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  descargarInformePDFConvocatoria(convocatoriaId: number): Observable<Blob> {
    return this.http
      .get(`${this.urlBase}/convocatorias/${convocatoriaId}/informe-pdf`, {
        responseType: 'blob',
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarConvocatoria(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.urlBase}/convocatorias/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarGradosDeConvocatoria(convocatoriaId: number): Observable<void> {
    return this.http
      .put<void>(
        `${this.urlBase}/convocatorias/${convocatoriaId}/actualizar-grados`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  actualizarAlumnoConvocatoria(
    alumnoConvocatoriaId: number,
    datos: any
  ): Observable<void> {
    return this.http
      .put<void>(
        `${this.urlBase}/convocatorias/alumno/${alumnoConvocatoriaId}`,
        datos,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerDocumentosDeAlumno(alumnoId: number): Observable<Documento[]> {
    return this.http
      .get<Documento[]>(`${this.urlBase}/alumnos/${alumnoId}/documentos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  subirDocumentoAlumno(alumnoId: number, archivo: File): Observable<Documento> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http
      .post<Documento>(
        `${this.urlBase}/alumnos/${alumnoId}/documentos`,
        formData,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  eliminarDocumentoAlumno(
    alumnoId: number,
    documentoId: number
  ): Observable<void> {
    return this.http
      .delete<void>(
        `${this.urlBase}/alumnos/${alumnoId}/documentos/${documentoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerUrlDescargaDocumentoAlumno(alumnoId: number, documentoId: number): string {
    return `${this.urlBase}/alumnos/${alumnoId}/documentos/${documentoId}/descargar`;
  }

  descargarDocumentoAlumno(
    alumnoId: number,
    documentoId: number
  ): Observable<Blob> {
    return this.http
      .get(`${this.urlBase}/alumnos/${alumnoId}/documentos/${documentoId}/descargar`, {
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeAlumnosPorGrado(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/alumnosPorGrado`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeTaekwondoPorGrado(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/taekwondoPorGrado`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeKickboxingPorGrado(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/kickboxingPorGrado`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeLicencias(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/licencias`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeInfantilesAPromocionar(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/infantilesAPromocionar`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeAdultosAPromocionar(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/adultosAPromocionar`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeInfantilesAPromocionarTaekwondo(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/infantilesAPromocionarTaekwondo`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeInfantilesAPromocionarKickboxing(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/infantilesAPromocionarKickboxing`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeAdultosAPromocionarTaekwondo(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/adultosAPromocionarTaekwondo`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeAdultosAPromocionarKickboxing(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/adultosAPromocionarKickboxing`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeDeudas(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/deudas`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeDeudasCSV(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/deudas/csv`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerEstadoRetoDiario(alumnoId: number): Observable<RetoDiarioEstado> {
    return this.http
      .get<RetoDiarioEstado>(`${this.urlBase}/alumnos/${alumnoId}/reto-diario`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  completarRetoDiario(alumnoId: number): Observable<RetoDiarioEstado> {
    return this.http
      .put<RetoDiarioEstado>(`${this.urlBase}/alumnos/${alumnoId}/reto-diario/completar`, {}, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeProductos(): Observable<Blob> {
    return this.http
      .get(`${this.urlBase}/informes/productos`, {
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeMensualidades(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/mensualidades`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeMensualidadesTaekwondo(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/mensualidades/taekwondo`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeMensualidadesKickboxing(soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams().set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/mensualidades/kickboxing`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarListadoMensualidadMensual(mesAno: string, soloActivos: boolean = true): Observable<Blob> {
    const params = new HttpParams()
      .set('mesAno', mesAno)
      .set('soloActivos', soloActivos.toString());
    return this.http
      .get(`${this.urlBase}/informes/listado-mensualidad-mensual`, {
        params,
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  generarInformeCompetidores(): Observable<Blob> {
    return this.http
      .get(`${this.urlBase}/informes/competidores`, {
        withCredentials: true,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  descargarAsistencia(
    year: number,
    month: number,
    grupo: string | string[],
    deporte?: string
  ): Observable<Blob> {
    let params = new HttpParams()
      .set('year', year.toString())
      .set('month', month.toString());

    if (Array.isArray(grupo)) {
      grupo.forEach((dia) => {
        params = params.append('grupos', dia);
      });
    } else {
      params = params.set('grupo', grupo);
    }

    if (deporte) {
      params = params.set('deporte', deporte);
    }

    return this.http
      .get(`${this.urlBase}/informes/asistencia`, {
        params,
        responseType: 'blob',
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerUsuarios(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/admin/users`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarRolesUsuario(userId: number, roles: string[]): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/admin/users/${userId}/roles`, roles, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarContrasenaUsuario(userId: number, nuevaContrasena: string): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/admin/users/${userId}/password`, { nuevaContrasena }, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerLimiteTurno(): Observable<number> {
    return this.http
      .get<number>(`${this.urlBase}/turnos/limite`)
      .pipe(catchError(this.manejarError));
  }

  actualizarLimiteTurno(nuevoLimite: number): Observable<number> {
    return this.http
      .put<number>(`${this.urlBase}/admin/configuracion/limite-turno`, nuevoLimite, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }
}
