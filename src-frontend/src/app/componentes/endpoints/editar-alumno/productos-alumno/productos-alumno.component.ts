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
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-productos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductosAlumnoNotasComponent, PaginacionComponent, SkeletonCardComponent],
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

  mostrarModalNotas = false;
  productoSeleccionado!: ProductoAlumnoDTO;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.alumnoId = +this.route.snapshot.paramMap.get('id')!;
    this.obtenerProductosAlumno(this.alumnoId);
    this.obtenerProductos();
  }

  obtenerProductosAlumno(alumnoId: number) {
    this.cargando = true;
    this.endpointsService.obtenerProductosDelAlumno(alumnoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (productos) => {
          this.productosAlumno = productos;
          this.totalPaginas = Math.ceil(this.productosAlumno.length / this.tamanoPagina);
          this.cambiarPagina(1);
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
    this.paginaActual = pageNumber;
    const startIndex = (pageNumber - 1) * this.tamanoPagina;
    const endIndex = startIndex + this.tamanoPagina;
    this.productosPaginados = this.productosAlumno.slice(startIndex, endIndex);
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

  asignarProducto(alumnoId: number) {
    if (this.selectedProductoId == null) {
      Swal.fire({
        title: 'Error',
        text: 'Seleccione un producto',
        icon: 'error',
      });
      return;
    }

    const detalles: ProductoAlumnoDTO = {
      id: 0,
      productoId: this.selectedProductoId,
      alumnoId: alumnoId,
      concepto: '',
      fechaAsignacion: new Date(),
      cantidad: 1,
      precio: 0,
      pagado: false,
      fechaPago: null,
      notas: '',
    };

    this.endpointsService
      .asignarProductoAAlumno(alumnoId, this.selectedProductoId, detalles)
      .subscribe({
        next: () => {
          this.obtenerProductosAlumno(alumnoId);
          showSuccessToast('Producto asignado correctamente');
        },
        error: () => {
          showErrorToast('No se pudo asignar el producto');
        },
      });

    this.selectedProductoId = null;
  }

  eliminarProducto(alumnoId: number, productoAlumnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'Esta acción no se puede deshacer',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Si, eliminar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarProductoAlumno(productoAlumnoId)
          .subscribe({
            next: () => {
              this.obtenerProductosAlumno(alumnoId);
              showSuccessToast('Producto eliminado correctamente');
            },
            error: () => {
              showErrorToast('No se pudo eliminar el producto');
            },
          });
      }
    });
  }

  actualizarProducto(alumnoId: number, productoAlumno: ProductoAlumnoDTO) {
    this.endpointsService
      .actualizarProductoAlumno(productoAlumno.id, productoAlumno)
      .subscribe({
        next: () => {
          showSuccessToast('Producto actualizado correctamente');
          this.obtenerProductosAlumno(alumnoId);
        },
        error: () => {
          showErrorToast('No se pudo actualizar el producto');
        },
      });
  }

  abrirModalNotas(productoAlumno: ProductoAlumnoDTO) {
    this.productoSeleccionado = { ...productoAlumno };
    this.mostrarModalNotas = true;
  }

  cerrarModalNotas() {
    this.mostrarModalNotas = false;
  }

  guardarNotas(productoActualizado: ProductoAlumnoDTO) {
    this.endpointsService
      .actualizarProductoAlumno(productoActualizado.id, productoActualizado)
      .subscribe({
        next: () => {
          showSuccessToast('Notas actualizadas correctamente');
          this.obtenerProductosAlumno(this.alumnoId);
          this.cerrarModalNotas();
        },
        error: () => {
          showErrorToast('No se pudieron actualizar las notas');
        },
      });
  }

  calcularTotal(precio: number, cantidad: number): number {
    return precio * cantidad;
  }

  volver() {
    this.location.back();
  }
}
