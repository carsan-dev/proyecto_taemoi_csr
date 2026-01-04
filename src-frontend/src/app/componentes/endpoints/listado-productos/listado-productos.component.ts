import { Component, OnInit, OnDestroy } from '@angular/core';
import { Producto } from '../../../interfaces/producto';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { RouterModule } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-listado-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent, RouterModule, SkeletonCardComponent],
  templateUrl: './listado-productos.component.html',
  styleUrl: './listado-productos.component.scss',
})
export class ListadoProductosComponent implements OnInit, OnDestroy {
  productos: Array<Producto & { selected?: boolean }> = [];
  conceptoFiltro: string = '';
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  totalElementos: number = 0;
  orderBy: string = 'concepto';
  order: string = 'asc';
  cargando: boolean = true; // Local loading state
  private searchSubject = new Subject<string>();
  private readonly storageKey = 'listadoProductosEstado';

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.restaurarEstadoPaginacion();
    this.obtenerProductos();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500), // Wait 500ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.paginaActual = 1;
      this.guardarEstadoPaginacion();
      this.obtenerProductos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  obtenerProductos(): void {
    this.cargando = true;
    this.endpointsService
      .obtenerTodosLosProductosPaginado(
        this.conceptoFiltro,
        this.paginaActual,
        this.tamanoPagina,
        this.orderBy,
        this.order
      )
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (page: any) => {
          this.productos = (page.content || []).map((producto: Producto) => ({
            ...producto,
            selected: false,
          }));
          this.totalPaginas = page.totalPages || 0;
          this.totalElementos = page.totalElements || 0;

          if (this.totalPaginas > 0 && this.paginaActual > this.totalPaginas) {
            this.paginaActual = this.totalPaginas;
            this.guardarEstadoPaginacion();
            this.obtenerProductos();
            return;
          }

          this.guardarEstadoPaginacion();
        },
        error: () => {
          showErrorToast('No se pudieron cargar los productos');
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
            showSuccessToast('Producto eliminado correctamente');
            // Recargar la lista de productos
            this.obtenerProductos();
          },
          error: () => {
            showErrorToast('No se pudo eliminar el producto');
          },
        });
      }
    });
  }

  eliminarProductosSeleccionados(): void {
    const productosSeleccionados = this.productos.filter(
      (producto) => producto.selected
    );

    if (productosSeleccionados.length === 0) {
      Swal.fire({
        title: 'No hay productos seleccionados',
        text: 'Por favor, seleccione al menos un producto para eliminar.',
        icon: 'info',
      });
      return;
    }

    Swal.fire({
      title: 'Estas seguro?',
      text: 'Los productos seleccionados seran eliminados permanentemente',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Si, eliminarlos',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.eliminarProductoSecuencial([...productosSeleccionados]);
      }
    });
  }

  private eliminarProductoSecuencial(productosSeleccionados: Array<Producto & { selected?: boolean }>): void {
    if (productosSeleccionados.length === 0) {
      showSuccessToast('Productos eliminados correctamente');
      this.obtenerProductos();
      return;
    }

    const producto = productosSeleccionados.pop();
    if (!producto) {
      showSuccessToast('Productos eliminados correctamente');
      this.obtenerProductos();
      return;
    }

    this.endpointsService.eliminarProducto(producto.id).subscribe({
      next: () => {
        this.eliminarProductoSecuencial(productosSeleccionados);
      },
      error: () => {
        showErrorToast(`No se pudo eliminar el producto ${producto.concepto}`);
        this.eliminarProductoSecuencial(productosSeleccionados);
      },
    });
  }

  filtrarPorConcepto(): void {
    this.searchSubject.next(this.conceptoFiltro);
  }

  cambiarPagina(pagina: number): void {
    if (this.cargando || pagina === this.paginaActual) {
      return;
    }
    this.paginaActual = pagina;
    this.guardarEstadoPaginacion();
    this.obtenerProductos();
  }

  ordenarPor(campo: string): void {
    if (this.orderBy === campo) {
      this.order = this.order === 'asc' ? 'desc' : 'asc';
    } else {
      this.orderBy = campo;
      this.order = 'asc';
    }
    this.guardarEstadoPaginacion();
    this.obtenerProductos();
  }

  toggleSelectAll(event: Event): void {
    const input = event.target as HTMLInputElement;
    const checked = input.checked;
    this.productos.forEach((producto) => {
      producto.selected = checked;
    });
  }

  get hasSelectedProductos(): boolean {
    return this.productos.some((producto) => producto.selected);
  }

  get allProductosSelected(): boolean {
    return this.productos.length > 0 && this.productos.every((producto) => producto.selected);
  }

  private guardarEstadoPaginacion(): void {
    const estado = {
      paginaActual: this.paginaActual,
      tamanoPagina: this.tamanoPagina,
      conceptoFiltro: this.conceptoFiltro,
      orderBy: this.orderBy,
      order: this.order,
    };
    sessionStorage.setItem(this.storageKey, JSON.stringify(estado));
  }

  private restaurarEstadoPaginacion(): void {
    const estadoGuardado = sessionStorage.getItem(this.storageKey);
    if (!estadoGuardado) {
      return;
    }
    try {
      const estado = JSON.parse(estadoGuardado);
      this.paginaActual = estado.paginaActual || 1;
      this.tamanoPagina = estado.tamanoPagina || this.tamanoPagina;
      this.conceptoFiltro = estado.conceptoFiltro || '';
      this.orderBy = estado.orderBy || 'concepto';
      this.order = estado.order || 'asc';
    } catch (error) {
      console.error('Error parsing saved pagination state:', error);
    }
  }
}
