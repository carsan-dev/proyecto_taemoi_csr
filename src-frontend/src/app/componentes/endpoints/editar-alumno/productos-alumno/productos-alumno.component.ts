import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { Producto } from '../../../../interfaces/producto';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-productos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './productos-alumno.component.html',
  styleUrl: './productos-alumno.component.scss'
})
export class ProductosAlumnoComponent implements OnInit {
  alumnoId!: number;
  productosAlumno: Producto[] = [];
  products: Producto[] = [];
  selectedProductoId: number | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) { }

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
      }
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
      }
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

    this.endpointsService
      .asignarProductoAAlumno(alumnoId, this.selectedProductoId)
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

  actualizarProducto(alumnoId: number, producto: Producto) {
    this.endpointsService.actualizarProducto(producto.id, producto).subscribe({
      next: (productoActualizado: Producto) => {
        Swal.fire({
          title: 'Éxito',
          text: 'Producto actualizado correctamente',
          icon: 'success',
        });
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

  eliminarProducto(alumnoId: number, productoId: number) {
    this.endpointsService
      .eliminarProductoDeAlumno(alumnoId, productoId)
      .subscribe({
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

  
  volver() {
    this.location.back();
  }
}