import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../../utils/toast.util';
import { Producto } from '../../../../interfaces/producto';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { ProductosAlumnoNotasComponent } from '../../../generales/productos-alumno-notas/productos-alumno-notas.component';
import { PaginacionComponent } from '../../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../../generales/skeleton-card/skeleton-card.component';
import { Observable, concat, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { SearchableSelectDirective } from '../../../../directives/searchable-select.directive';

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
  pendingAsignaciones: number[] = [];

  mostrarModalNotas = false;
  productoSeleccionado!: ProductoAlumnoDTO;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.alumnoId = +this.route.snapshot.paramMap.get('id')!;
    this.storageKey = `productosAlumnoEstado_${this.alumnoId}`;
    this.restaurarEstadoPaginacion();
    this.obtenerProductosAlumno(this.alumnoId);
    this.obtenerProductos();
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
    this.pendingAsignaciones.push(this.selectedProductoId);
    this.selectedProductoId = null;
  }

  marcarProductoParaEliminar(productoAlumnoId: number) {
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
          catchError(() => {
            hasErrors = true;
            showErrorToast('No se pudo actualizar un producto');
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

    this.pendingAsignaciones.forEach((productoId) => {
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
      operaciones.push(
        this.endpointsService.asignarProductoAAlumno(this.alumnoId, productoId, detalles).pipe(
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
