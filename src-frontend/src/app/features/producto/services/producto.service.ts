import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Producto } from '../../../interfaces/producto';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';

/**
 * Service for managing product (Producto) operations
 * Handles products, student product assignments, monthly fees, and licenses
 */
@Injectable({
  providedIn: 'root',
})
export class ProductoService {
  private readonly urlBase = `${environment.apiUrl}/productos`;
  private readonly productoAlumnoBase = `${environment.apiUrl}/productos-alumno`;

  constructor(private readonly http: HttpClient) {}

  // ============ Product CRUD Operations ============

  /**
   * Get all products without pagination
   */
  obtenerTodosLosProductos(): Observable<Producto[]> {
    return this.http.get<Producto[]>(`${this.urlBase}/todos`, {
      withCredentials: true,
    });
  }

  /**
   * Get paginated products with filtering and sorting
   */
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

    return this.http.get<any>(this.urlBase, {
      params: params,
      withCredentials: true,
    });
  }

  /**
   * Get product by ID
   */
  obtenerProductoPorId(id: number): Observable<Producto> {
    return this.http.get<Producto>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Create a new product
   */
  crearProducto(producto: Producto): Observable<Producto> {
    return this.http.post<Producto>(this.urlBase, producto, {
      withCredentials: true,
    });
  }

  /**
   * Update an existing product
   */
  actualizarProducto(id: number, formData: FormData): Observable<any> {
    return this.http.put<any>(`${this.urlBase}/${id}`, formData, {
      withCredentials: true,
    });
  }

  /**
   * Delete a product
   */
  eliminarProducto(id: number): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  // ============ Product-Student Assignment Operations ============

  /**
   * Get all products assigned to a student
   */
  obtenerProductosDelAlumno(alumnoId: number): Observable<ProductoAlumnoDTO[]> {
    return this.http.get<ProductoAlumnoDTO[]>(
      `${this.productoAlumnoBase}/alumno/${alumnoId}`,
      { withCredentials: true }
    );
  }

  /**
   * Assign a product to a student
   */
  asignarProductoAAlumno(
    alumnoId: number,
    productoId: number,
    detalles: ProductoAlumnoDTO
  ): Observable<ProductoAlumnoDTO> {
    return this.http.post<ProductoAlumnoDTO>(
      `${this.productoAlumnoBase}/alumno/${alumnoId}/producto/${productoId}`,
      detalles,
      { withCredentials: true }
    );
  }

  /**
   * Update a product assignment
   */
  actualizarProductoAlumno(
    id: number,
    detalles: ProductoAlumnoDTO
  ): Observable<ProductoAlumnoDTO> {
    return this.http.put<ProductoAlumnoDTO>(
      `${this.productoAlumnoBase}/${id}`,
      detalles,
      { withCredentials: true }
    );
  }

  /**
   * Remove a product assignment from a student
   */
  eliminarProductoAlumno(id: number): Observable<any> {
    return this.http.delete<void>(`${this.productoAlumnoBase}/${id}`, {
      withCredentials: true,
    });
  }

  // ============ Specialized Product Operations ============

  /**
   * Reserve a spot/place for a student (plaza)
   */
  reservarPlaza(
    alumnoId: number,
    pagado: boolean,
    forzar: boolean = false
  ): Observable<ProductoAlumnoDTO> {
    const params = new HttpParams()
      .set('pagado', pagado.toString())
      .set('forzar', forzar.toString());
    return this.http.post<ProductoAlumnoDTO>(
      `${this.productoAlumnoBase}/${alumnoId}/reservar-plaza`,
      null,
      { params, withCredentials: true }
    );
  }

  /**
   * Load monthly fees for all students
   */
  cargarMensualidadesGenerales(
    mesAno: string,
    fechaAsignacion?: string | null
  ): Observable<any> {
    let params = new HttpParams();
    if (fechaAsignacion) {
      params = params.set('fechaAsignacion', fechaAsignacion);
    }

    return this.http.post(
      `${this.productoAlumnoBase}/mensualidades/general`,
      mesAno,
      { params, withCredentials: true }
    );
  }

  /**
   * Load monthly fee for a specific student
   */
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

    return this.http.post(
      `${this.productoAlumnoBase}/mensualidades/individual`,
      mesAno,
      { params, withCredentials: true }
    );
  }

  /**
   * Renew license for a student
   */
  renovarLicencia(alumnoId: number): Observable<ProductoAlumnoDTO> {
    return this.http.post<ProductoAlumnoDTO>(
      `${this.productoAlumnoBase}/${alumnoId}/renovar-licencia`,
      {},
      { withCredentials: true }
    );
  }

  // ============ MULTI-SPORT Product Operations ============

  /**
   * Assign a product to a student for a specific sport
   * @param alumnoId Student ID
   * @param productoId Product ID
   * @param deporte Sport name (TAEKWONDO, KICKBOXING, etc.)
   * @param detalles Product details
   */
  asignarProductoAAlumnoDeporte(
    alumnoId: number,
    productoId: number,
    deporte: string,
    detalles: ProductoAlumnoDTO
  ): Observable<ProductoAlumnoDTO> {
    return this.http.post<ProductoAlumnoDTO>(
      `${this.productoAlumnoBase}/alumno/${alumnoId}/producto/${productoId}/deporte/${deporte}`,
      detalles,
      { withCredentials: true }
    );
  }

  /**
   * Load monthly fees for all students, one per sport they practice
   * @param mesAno Month and year in format "MM/YYYY"
   */
  cargarMensualidadesMultiDeporte(mesAno: string): Observable<any> {
    return this.http.post(
      `${this.productoAlumnoBase}/mensualidades/multi-deporte`,
      mesAno,
      { withCredentials: true }
    );
  }

  /**
   * Load monthly fee for a specific student and sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param mesAno Month and year in format "MM/YYYY"
   * @param forzar Force creation even if already exists
   */
  cargarMensualidadIndividualPorDeporte(
    alumnoId: number,
    deporte: string,
    mesAno: string,
    forzar: boolean = false,
    fechaAsignacion?: string | null
  ): Observable<any> {
    const params = new HttpParams()
      .set('alumnoId', alumnoId.toString())
      .set('deporte', deporte)
      .set('forzar', forzar.toString());

    const paramsConFecha = fechaAsignacion
      ? params.set('fechaAsignacion', fechaAsignacion)
      : params;

    return this.http.post(
      `${this.productoAlumnoBase}/mensualidades/individual-deporte`,
      mesAno,
      { params: paramsConFecha, withCredentials: true }
    );
  }
}
