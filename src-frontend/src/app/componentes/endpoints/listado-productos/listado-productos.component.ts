import { Component, OnInit, OnDestroy } from '@angular/core';
import { Producto } from '../../../interfaces/producto';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { RouterModule } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-listado-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent, RouterModule],
  templateUrl: './listado-productos.component.html',
  styleUrl: './listado-productos.component.scss',
})
export class ListadoProductosComponent implements OnInit, OnDestroy {
  productos: Producto[] = [];
  conceptoFiltro: string = '';
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  totalElementos: number = 0;
  orderBy: string = 'id';
  order: string = 'asc';
  private searchSubject = new Subject<string>();

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerProductos();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500), // Wait 500ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.paginaActual = 1;
      this.obtenerProductos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  obtenerProductos(): void {
    this.endpointsService
      .obtenerTodosLosProductosPaginado(
        this.conceptoFiltro,
        this.paginaActual,
        this.tamanoPagina,
        this.orderBy,
        this.order
      )
      .subscribe({
        next: (page: any) => {
          this.productos = page.content;
          this.totalPaginas = page.totalPages;
          this.totalElementos = page.totalElements;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron cargar los productos',
            icon: 'error',
          });
        },
      });
  }

  eliminarProducto(id: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'Esta acción no se puede deshacer',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarProducto(id).subscribe({
          next: () => {
            Swal.fire({
              title: 'Eliminado',
              text: 'El producto ha sido eliminado correctamente',
              icon: 'success',
              timer: 2000,
            });
            // Recargar la lista de productos
            this.obtenerProductos();
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
    });
  }

  filtrarPorConcepto(): void {
    this.searchSubject.next(this.conceptoFiltro);
  }

  cambiarPagina(pagina: number): void {
    this.paginaActual = pagina;
    this.obtenerProductos();
  }

  ordenarPor(campo: string): void {
    if (this.orderBy === campo) {
      this.order = this.order === 'asc' ? 'desc' : 'asc';
    } else {
      this.orderBy = campo;
      this.order = 'asc';
    }
    this.obtenerProductos();
  }
}
