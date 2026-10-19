import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../../utils/toast.util';
import { mensajeDeError } from '../../../../servicios/endpoints/caja.service';
import { Producto } from '../../../../interfaces/producto';
import type { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { CommonModule, formatDate, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { ProductosAlumnoNotasComponent } from '../../../generales/productos-alumno-notas/productos-alumno-notas.component';
import { PaginacionComponent } from '../../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../../generales/skeleton-card/skeleton-card.component';
import { Observable, concat, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { SearchableSelectDirective } from '../../../../directives/searchable-select.directive';
import { fechaApi } from '../../../../utils/fecha-api';

interface AsignacionPendiente {
  productoId: number;
  deporte: string | null;
}

@Component({
  selector: 'app-productos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductosAlumnoNotasComponent, PaginacionComponent, SkeletonCardComponent, SearchableSelectDirective],
  templateUrl: './productos-alumno.component.html',
  styleUrl: './productos-alumno.component.scss',
})
export class ProductosAlumnoComponent implements OnInit {
  alumnoId!: number;
  productosAlumno: ProductoAlumnoDTO[] = [];
  productosPaginados: ProductoAlumnoDTO[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  products: Producto[] = [];
  selectedProductoId: number | null = null;
  cargando: boolean = true;
  private storageKey = '';
  private productosSnapshot = new Map<number, string>();
  private pendingUpdates = new Set<number>();
  private pendingDeletes = new Set<number>();
  pendingAsignaciones: AsignacionPendiente[] = [];
  deportesAlumno: AlumnoDeporteDTO[] = [];
  selectedDeporte: string | null = null;

  /** Cargos con un cancelar o reactivar en curso: su botón queda deshabilitado hasta la respuesta (sin doble clic). */
  private readonly accionesEnCurso = new Set<number>();

  /** Propietario (ROLE_ADMIN): el único que borra cargos (pieza 6b-2c). */
  esPropietario = false;

  mostrarModalNotas = false;
  productoSeleccionado!: ProductoAlumnoDTO;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.resolverPermisos();
    this.alumnoId = +this.route.snapshot.paramMap.get('id')!;
    this.storageKey = `productosAlumnoEstado_${this.alumnoId}`;
    this.restaurarEstadoPaginacion();
    this.obtenerProductosAlumno(this.alumnoId);
    this.obtenerProductos();
    this.endpointsService.obtenerAlumnoPorId(this.alumnoId).subscribe({
      next: (alumno) => { this.deportesAlumno = alumno.deportes ?? []; },
      error: () => { showErrorToast('No se han podido obtener los deportes del alumno'); },
    });
  }

  obtenerProductosAlumno(alumnoId: number) {
    this.cargando = true;
    this.endpointsService.obtenerProductosDelAlumno(alumnoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (productos) => {
          // Reverse order to show most recent products first
          this.productosAlumno = productos.reverse();
          this.resetPendingChanges();
          this.productosSnapshot.clear();
          this.productosAlumno.forEach((producto) => {
            this.productosSnapshot.set(producto.id, this.getProductoSnapshot(producto));
          });
          this.totalPaginas = Math.ceil(this.productosAlumno.length / this.tamanoPagina);
          if (this.totalPaginas === 0) {
            this.paginaActual = 1;
            this.productosPaginados = [];
            this.guardarEstadoPaginacion();
            return;
          }
          if (this.paginaActual > this.totalPaginas) {
            this.paginaActual = this.totalPaginas;
          }
          this.cambiarPagina(this.paginaActual);
        },
        error: (error) => {
          this.cargando = false;
          Swal.fire({
            title: 'Error al obtener',
            text: 'No se han podido obtener los productos del alumno',
            icon: 'error',
          });
        },
      });
  }

  cambiarPagina(pageNumber: number): void {
    if (pageNumber < 1 || pageNumber > this.totalPaginas) {
      return;
    }
    this.paginaActual = pageNumber;
    const startIndex = (pageNumber - 1) * this.tamanoPagina;
    const endIndex = startIndex + this.tamanoPagina;
    this.productosPaginados = this.productosAlumno.slice(startIndex, endIndex);
    this.guardarEstadoPaginacion();
  }

  obtenerProductos() {
    this.endpointsService.obtenerTodosLosProductos().subscribe({
      next: (productos) => {
        this.products = productos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error al obtener',
          text: 'No se han podido obtener los productos',
          icon: 'error',
        });
      },
    });
  }

  asignarProducto() {
    if (this.selectedProductoId == null) {
      Swal.fire({
        title: 'Error',
        text: 'Seleccione un producto',
        icon: 'error',
      });
      return;
    }
    const esMatricula = this.esMatriculaSeleccionada();
    if (esMatricula && !this.deportesAlumno.some((item) => item.deporte === this.selectedDeporte)) {
      Swal.fire({ title: 'Seleccione un deporte', text: 'La matrícula debe asignarse a un deporte del alumno.', icon: 'error' });
      return;
    }
    this.pendingAsignaciones.push({ productoId: this.selectedProductoId, deporte: esMatricula ? this.selectedDeporte : null });
    this.selectedProductoId = null;
    this.selectedDeporte = null;
  }

  esMatriculaSeleccionada(): boolean {
    const concepto = this.products.find((item) => item.id === this.selectedProductoId)?.concepto ?? '';
    return /\bMATRICULA\b/.test(concepto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toUpperCase());
  }

  /** Pieza 6b-3: el cargo está cancelado (queda tachado en la ficha). */
  estaCancelado(producto: ProductoAlumnoDTO): boolean {
    return !!producto.canceladoEn;
  }

  /** Cancelar: solo pendientes (ni pagados ni ya cancelados). Gestión y propietario. */
  puedeCancelar(producto: ProductoAlumnoDTO): boolean {
    return !producto.pagado && !this.estaCancelado(producto) && !this.isPendingDelete(producto.id);
  }

  /** Borrar: solo el propietario y solo cargos no pagados ni cancelados (el servidor rechaza los ya cobrados). */
  puedeEliminar(producto: ProductoAlumnoDTO): boolean {
    return this.esPropietario && !producto.pagado && !this.estaCancelado(producto);
  }

  /** «Cancelado por <nombre> el dd/mm/aaaa · «motivo»» (sin nombre: «Cancelado el …»). */
  textoCancelacion(producto: ProductoAlumnoDTO): string {
    if (!producto.canceladoEn) {
      return '';
    }
    // LocalDateTime: la API lo manda como [año, mes, día, hora, …] (ver fechaApi).
    const cuando = fechaApi(producto.canceladoEn);
    const fecha = cuando ? formatDate(cuando, 'dd/MM/yyyy', 'en-US') : '';
    const quien = producto.canceladoPorNombre?.trim();
    const sufijo = fecha ? ` el ${fecha}` : '';
    const base = quien ? `Cancelado por ${quien}${sufijo}` : `Cancelado${sufijo}`;
    const motivo = producto.motivoCancelacion?.trim();
    return motivo ? `${base} · «${motivo}»` : base;
  }

  accionEnCurso(productoId: number): boolean {
    return this.accionesEnCurso.has(productoId);
  }

  cancelarCargo(producto: ProductoAlumnoDTO): void {
    if (!this.puedeCancelar(producto) || this.accionEnCurso(producto.id)) {
      return;
    }
    Swal.fire({
      title: 'Cancelar cargo',
      text: `«${producto.concepto}». No se borra: queda tachado en la ficha y se puede reactivar.`,
      icon: 'warning',
      input: 'textarea',
      inputLabel: 'Motivo',
      inputPlaceholder: 'Por qué se cancela este cargo',
      inputAttributes: { maxlength: '500', 'aria-label': 'Motivo de la cancelación' },
      inputValidator: (valor: string) => {
        const limpio = (valor ?? '').trim();
        if (!limpio) {
          return 'Indica el motivo de la cancelación';
        }
        return limpio.length > 500 ? 'El motivo admite como mucho 500 caracteres' : null;
      },
      showCancelButton: true,
      confirmButtonText: 'Cancelar cargo',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#c53030',
    }).then((resultado) => {
      if (!resultado.isConfirmed) {
        return;
      }
      if (this.accionEnCurso(producto.id)) {
        return;
      }
      const motivo = String(resultado.value ?? '').trim();
      this.accionesEnCurso.add(producto.id);
      this.endpointsService.cancelarProductoAlumno(producto.id, motivo)
        .pipe(finalize(() => this.accionesEnCurso.delete(producto.id)))
        .subscribe({
        next: (actualizado) => {
          this.aplicarCargoActualizado(actualizado);
          showSuccessToast('Cargo cancelado');
        },
        // 400/409 con {mensaje}: los muestra el interceptor de errores.
        error: () => undefined,
      });
    });
  }

  reactivarCargo(producto: ProductoAlumnoDTO): void {
    if (!this.estaCancelado(producto) || this.accionEnCurso(producto.id)) {
      return;
    }
    Swal.fire({
      title: 'Reactivar cargo',
      text: `«${producto.concepto}» vuelve a estar pendiente de pago.`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Reactivar',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#2f855a',
    }).then((resultado) => {
      if (!resultado.isConfirmed) {
        return;
      }
      if (this.accionEnCurso(producto.id)) {
        return;
      }
      this.accionesEnCurso.add(producto.id);
      this.endpointsService.reactivarProductoAlumno(producto.id)
        .pipe(finalize(() => this.accionesEnCurso.delete(producto.id)))
        .subscribe({
        next: (actualizado) => {
          this.aplicarCargoActualizado(actualizado);
          showSuccessToast('Cargo reactivado');
        },
        error: () => undefined,
      });
    });
  }

  /** Cambia la fila por la respuesta del servidor sin recargar (los demás cambios pendientes se conservan). */
  private aplicarCargoActualizado(actualizado: ProductoAlumnoDTO): void {
    const indice = this.productosAlumno.findIndex((item) => item.id === actualizado.id);
    if (indice < 0) {
      return;
    }
    this.productosAlumno[indice] = { ...actualizado };
    this.productosSnapshot.set(actualizado.id, this.getProductoSnapshot(this.productosAlumno[indice]));
    this.pendingUpdates.delete(actualizado.id);
    this.pendingDeletes.delete(actualizado.id);
    this.cambiarPagina(this.paginaActual);
  }

  resolverPermisos(): void {
    if (this.authService.rolesEstanCargados()) {
      this.esPropietario = this.authService.tieneRolAdmin();
      return;
    }
    this.authService.obtenerRoles().subscribe({
      next: () => (this.esPropietario = this.authService.tieneRolAdmin()),
      error: () => (this.esPropietario = false),
    });
  }

  marcarProductoParaEliminar(productoAlumnoId: number) {
    const producto = this.productosAlumno.find((item) => item.id === productoAlumnoId);
    if (!producto || !this.puedeEliminar(producto)) {
      return;
    }
    this.pendingDeletes.add(productoAlumnoId);
    this.pendingUpdates.delete(productoAlumnoId);
  }

  deshacerEliminarProducto(productoAlumnoId: number) {
    this.pendingDeletes.delete(productoAlumnoId);
    const producto = this.productosAlumno.find((item) => item.id === productoAlumnoId);
    if (producto) {
      this.onProductoChange(producto);
    }
  }

  abrirModalNotas(productoAlumno: ProductoAlumnoDTO) {
    this.productoSeleccionado = { ...productoAlumno };
    this.mostrarModalNotas = true;
  }

  cerrarModalNotas() {
    this.mostrarModalNotas = false;
  }

  guardarNotas(productoActualizado: ProductoAlumnoDTO) {
    const producto = this.productosAlumno.find((item) => item.id === productoActualizado.id);
    if (producto) {
      producto.notas = productoActualizado.notas;
      this.onProductoChange(producto);
    }
    this.cerrarModalNotas();
  }

  calcularTotal(precio: number, cantidad: number): number {
    return precio * cantidad;
  }

  volver() {
    this.location.back();
  }

  getFechaAsignacionInput(producto: ProductoAlumnoDTO): string {
    return producto.fechaAsignacion
      ? formatDate(producto.fechaAsignacion, 'yyyy-MM-dd', 'en-US')
      : '';
  }

  onFechaAsignacionChange(producto: ProductoAlumnoDTO, value: string): void {
    // The calendar follows DatePipe's local day, not the ISO timestamp prefix.
    // Do not rewrite unchanged timestamps or clear legacy dates.
    if (this.isPendingDelete(producto.id) || !/^\d{4}-\d{2}-\d{2}$/.test(value)
      || value === this.getFechaAsignacionInput(producto)) {
      return;
    }
    const [year, month, day] = value.split('-').map(Number);
    const fecha = producto.fechaAsignacion ? new Date(producto.fechaAsignacion) : new Date();
    if (!producto.fechaAsignacion) {
      fecha.setHours(12, 0, 0, 0);
    }
    // Retain local time (and milliseconds); noon is safe for legacy null dates.
    fecha.setFullYear(year, month - 1, day);
    if (formatDate(fecha, 'yyyy-MM-dd', 'en-US') !== value) {
      return;
    }
    producto.fechaAsignacion = fecha;
    this.onProductoChange(producto);
  }

  onProductoChange(productoAlumno: ProductoAlumnoDTO): void {
    if (this.pendingDeletes.has(productoAlumno.id)) {
      return;
    }
    const snapshot = this.productosSnapshot.get(productoAlumno.id);
    const currentSnapshot = this.getProductoSnapshot(productoAlumno);
    if (snapshot && snapshot !== currentSnapshot) {
      this.pendingUpdates.add(productoAlumno.id);
    } else {
      this.pendingUpdates.delete(productoAlumno.id);
    }
  }

  isPendingDelete(productoAlumnoId: number): boolean {
    return this.pendingDeletes.has(productoAlumnoId);
  }

  removePendingAsignacion(index: number): void {
    this.pendingAsignaciones.splice(index, 1);
  }

  hasPendingChanges(): boolean {
    return (
      this.pendingUpdates.size > 0 ||
      this.pendingDeletes.size > 0 ||
      this.pendingAsignaciones.length > 0
    );
  }

  confirmarCambiosPendientes(): void {
    if (!this.hasPendingChanges()) {
      return;
    }

    const operaciones: Array<Observable<unknown>> = [];
    let hasErrors = false;

    const actualizaciones = Array.from(this.pendingUpdates).filter(
      (productoId) => !this.pendingDeletes.has(productoId)
    );

    actualizaciones.forEach((productoId) => {
      const producto = this.productosAlumno.find((item) => item.id === productoId);
      if (!producto) {
        return;
      }
      operaciones.push(
        this.endpointsService.actualizarProductoAlumno(producto.id, producto).pipe(
          catchError((e) => {
            hasErrors = true;
            showErrorToast(mensajeDeError(e) ?? 'No se pudo actualizar un producto');
            return of(null);
          })
        )
      );
    });

    this.pendingDeletes.forEach((productoId) => {
      operaciones.push(
        this.endpointsService.eliminarProductoAlumno(productoId).pipe(
          catchError(() => {
            hasErrors = true;
            showErrorToast('No se pudo eliminar un producto');
            return of(null);
          })
        )
      );
    });

    this.pendingAsignaciones.forEach(({ productoId, deporte }) => {
      const detalles: ProductoAlumnoDTO = {
        id: 0,
        productoId: productoId,
        alumnoId: this.alumnoId,
        concepto: '',
        fechaAsignacion: new Date(),
        cantidad: 1,
        precio: 0,
        pagado: false,
        fechaPago: null,
        notas: '',
      };
      const asignacion = deporte
        ? this.endpointsService.asignarProductoAAlumnoDeporte(this.alumnoId, productoId, deporte, detalles)
        : this.endpointsService.asignarProductoAAlumno(this.alumnoId, productoId, detalles);
      operaciones.push(
        asignacion.pipe(
          catchError(() => {
            hasErrors = true;
            showErrorToast('No se pudo asignar un producto');
            return of(null);
          })
        )
      );
    });

    if (operaciones.length === 0) {
      return;
    }

    this.cargando = true;
    concat(...operaciones)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        complete: () => {
          this.obtenerProductosAlumno(this.alumnoId);
          if (!hasErrors) {
            showSuccessToast('Cambios aplicados correctamente');
          }
        },
      });
  }

  cancelarCambiosPendientes(): void {
    this.resetPendingChanges();
    this.obtenerProductosAlumno(this.alumnoId);
    this.selectedProductoId = null;
    this.selectedDeporte = null;
  }

  getProductoPendiente(productoId: number): Producto | undefined {
    return this.products.find((item) => item.id === productoId);
  }

  private resetPendingChanges(): void {
    this.pendingUpdates.clear();
    this.pendingDeletes.clear();
    this.pendingAsignaciones = [];
  }

  private getProductoSnapshot(producto: ProductoAlumnoDTO): string {
    return JSON.stringify({
      cantidad: producto.cantidad,
      precio: producto.precio,
      pagado: producto.pagado,
      fechaPago: producto.fechaPago,
      fechaAsignacion: producto.fechaAsignacion,
      notas: producto.notas ?? '',
    });
  }

  private guardarEstadoPaginacion(): void {
    if (!this.storageKey) {
      return;
    }
    const estado = {
      paginaActual: this.paginaActual,
      tamanoPagina: this.tamanoPagina,
    };
    sessionStorage.setItem(this.storageKey, JSON.stringify(estado));
  }

  private restaurarEstadoPaginacion(): void {
    if (!this.storageKey) {
      return;
    }
    const estadoGuardado = sessionStorage.getItem(this.storageKey);
    if (!estadoGuardado) {
      return;
    }
    try {
      const estado = JSON.parse(estadoGuardado);
      this.paginaActual = estado.paginaActual || 1;
      this.tamanoPagina = estado.tamanoPagina || this.tamanoPagina;
    } catch (error) {
      console.error('Error parsing saved pagination state:', error);
    }
  }
}
