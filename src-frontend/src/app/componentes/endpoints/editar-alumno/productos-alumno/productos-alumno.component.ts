import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { Producto } from '../../../../interfaces/producto';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProductosAlumnoNotasComponent } from '../../../generales/productos-alumno-notas/productos-alumno-notas.component';

@Component({
  selector: 'app-productos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductosAlumnoNotasComponent],
  templateUrl: './productos-alumno.component.html',
  styleUrl: './productos-alumno.component.scss',
})
export class ProductosAlumnoComponent implements OnInit {
  alumnoId!: number;
  productosAlumno: ProductoAlumnoDTO[] = [];
  products: Producto[] = [];
  selectedProductoId: number | null = null;

  mostrarModalNotas = false;
  productoSeleccionado!: ProductoAlumnoDTO;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location,
    private readonly modalService: NgbModal
  ) {}

  ngOnInit(): void {
    this.alumnoId = +this.route.snapshot.paramMap.get('id')!;
    this.obtenerProductosAlumno(this.alumnoId);
    this.obtenerProductos();
  }

  obtenerProductosAlumno(alumnoId: number) {
    this.endpointsService.obtenerProductosDelAlumno(alumnoId).subscribe({
      next: (productos) => {
        this.productosAlumno = productos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error al obtener',
          text: 'No se han podido obtener los productos del alumno',
          icon: 'error',
        });
      },
    });
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
          Swal.fire({
            title: 'Éxito',
            text: 'Producto asignado correctamente',
            icon: 'success',
          });
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudo asignar el producto',
            icon: 'error',
          });
        },
      });

    this.selectedProductoId = null;
  }

  eliminarProducto(alumnoId: number, productoAlumnoId: number) {
    this.endpointsService.eliminarProductoAlumno(productoAlumnoId).subscribe({
      next: () => {
        this.obtenerProductosAlumno(alumnoId);
        Swal.fire({
          title: 'Éxito',
          text: 'Producto eliminado correctamente',
          icon: 'success',
        });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo eliminar el producto',
          icon: 'error',
        });
      },
    });
  }

  actualizarProducto(alumnoId: number, productoAlumno: ProductoAlumnoDTO) {
    this.endpointsService
      .actualizarProductoAlumno(productoAlumno.id, productoAlumno)
      .subscribe({
        next: (productoActualizado) => {
          Swal.fire({
            title: 'Éxito',
            text: 'Producto actualizado correctamente',
            icon: 'success',
            timer: 2000,
            showConfirmButton: false,
          });
          this.obtenerProductosAlumno(alumnoId);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudo actualizar el producto',
            icon: 'error',
          });
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
          Swal.fire({
            title: 'Éxito',
            text: 'Notas actualizadas correctamente',
            icon: 'success',
            timer: 2000,
            showConfirmButton: false,
          });
          this.obtenerProductosAlumno(this.alumnoId);
          this.cerrarModalNotas();
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron actualizar las notas',
            icon: 'error',
          });
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
