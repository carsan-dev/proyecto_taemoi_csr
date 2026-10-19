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
import { debounceTime, finalize } from 'rxjs/operators';
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
  /** Filtro «Tipo»: nombre del enum del backend, o '' para todos. */
  tipoFiltro: string = '';
  /** Filtro «Mi caja»: 'true' (sí), 'false' (no) o '' para todos. */
  miCajaFiltro: '' | 'true' | 'false' = '';
  readonly tiposProducto: ReadonlyArray<{ valor: string; etiqueta: string }> = [
    { valor: 'MENSUALIDAD', etiqueta: 'Mensualidad' },
    { valor: 'RESERVA_PLAZA', etiqueta: 'Reserva de plaza' },
    { valor: 'LICENCIA', etiqueta: 'Licencia' },
    { valor: 'COMPETIDOR', etiqueta: 'Competidor' },
    { valor: 'EXAMEN', etiqueta: 'Examen' },
    { valor: 'RECOMPENSA', etiqueta: 'Recompensa' },
    { valor: 'OTRO', etiqueta: 'Otro' },
  ];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  totalElementos: number = 0;
  orderBy: string = 'concepto';
  order: string = 'asc';
  cargando: boolean = true; // Local loading state
  guardandoIds = new Set<number>();
  guardandoBloque = false;
  private searchSubject = new Subject<string>();
  /** Concepto con el que se cargó la lista por última vez (para no repetir la petición al teclear lo mismo). */
  private conceptoAplicado = '';
  private readonly storageKey = 'listadoProductosEstado';

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.restaurarEstadoPaginacion();
    this.conceptoAplicado = this.conceptoFiltro;
    this.obtenerProductos();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500) // Wait 500ms after user stops typing
    ).subscribe(() => {
      // Solo recarga si el concepto cambió de verdad respecto a la última carga
      if (this.conceptoFiltro === this.conceptoAplicado) {
        return;
      }
      this.recargarDesdeLaPrimeraPagina();
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
        this.order,
        this.tipoFiltro || undefined,
        this.miCajaFiltro === '' ? null : this.miCajaFiltro === 'true'
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

  get numSeleccionados(): number {
    return this.productos.filter((producto) => producto.selected).length;
  }

  private mensajeError(error: any, porDefecto: string): string {
    const mensaje = error?.error?.mensaje;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
  }

  cambiarMiCaja(producto: Producto & { selected?: boolean }, event: Event): void {
    const input = event.target as HTMLInputElement;
    const nuevoValor = input.checked;
    const valorAnterior = !!producto.vendibleProfesor;
    if (this.guardandoIds.has(producto.id)) {
      input.checked = valorAnterior;
      return;
    }
    this.guardandoIds = new Set(this.guardandoIds).add(producto.id);
    this.endpointsService.cambiarVendibleProfesor(producto.id, nuevoValor).subscribe({
      next: (actualizado) => {
        producto.vendibleProfesor = !!actualizado.vendibleProfesor;
        input.checked = producto.vendibleProfesor;
        this.terminarGuardado(producto.id);
        showSuccessToast(
          producto.vendibleProfesor
            ? `«${producto.concepto}»: ya se puede vender desde Mi caja`
            : `«${producto.concepto}»: ya no sale en Mi caja`
        );
      },
      error: (error) => {
        producto.vendibleProfesor = valorAnterior;
        input.checked = valorAnterior;
        this.terminarGuardado(producto.id);
        showErrorToast(
          this.mensajeError(error, `No se pudo cambiar «Mi caja» de «${producto.concepto}»`)
        );
      },
    });
  }

  private terminarGuardado(id: number): void {
    const restantes = new Set(this.guardandoIds);
    restantes.delete(id);
    this.guardandoIds = restantes;
  }

  cambiarMiCajaSeleccionados(vendibleProfesor: boolean): void {
    const seleccionados = this.productos.filter((producto) => producto.selected);
    if (seleccionados.length === 0 || this.guardandoBloque) {
      return;
    }
    this.guardandoBloque = true;
    this.endpointsService
      .cambiarVendibleProfesorEnBloque(
        seleccionados.map((producto) => producto.id),
        vendibleProfesor
      )
      .pipe(finalize(() => (this.guardandoBloque = false)))
      .subscribe({
        next: () => {
          const n = seleccionados.length;
          seleccionados.forEach((producto) => {
            producto.vendibleProfesor = vendibleProfesor;
          });
          this.productos.forEach((producto) => (producto.selected = false));
          showSuccessToast(
            vendibleProfesor
              ? `${n} ${n === 1 ? 'producto' : 'productos'} ahora en Mi caja`
              : `${n} ${n === 1 ? 'producto quitado' : 'productos quitados'} de Mi caja`
          );
        },
        error: (error) => {
          showErrorToast(
            this.mensajeError(error, 'No se pudo cambiar «Mi caja» de los productos seleccionados')
          );
        },
      });
  }

  filtrarPorConcepto(): void {
    this.searchSubject.next(this.conceptoFiltro);
  }

  /** Cambia un desplegable: recarga desde la página 1. */
  alCambiarFiltro(): void {
    this.recargarDesdeLaPrimeraPagina();
  }

  limpiarFiltros(): void {
    this.conceptoFiltro = '';
    this.tipoFiltro = '';
    this.miCajaFiltro = '';
    this.recargarDesdeLaPrimeraPagina();
  }

  quitarFiltro(filtro: 'concepto' | 'tipo' | 'miCaja'): void {
    if (filtro === 'concepto') {
      this.conceptoFiltro = '';
    } else if (filtro === 'tipo') {
      this.tipoFiltro = '';
    } else {
      this.miCajaFiltro = '';
    }
    this.recargarDesdeLaPrimeraPagina();
  }

  get hayFiltros(): boolean {
    return this.filtrosActivos.length > 0;
  }

  get filtrosActivos(): Array<{ clave: 'concepto' | 'tipo' | 'miCaja'; texto: string }> {
    const activos: Array<{ clave: 'concepto' | 'tipo' | 'miCaja'; texto: string }> = [];
    const concepto = this.conceptoFiltro.trim();
    if (concepto) {
      activos.push({ clave: 'concepto', texto: `"${concepto}"` });
    }
    if (this.tipoFiltro) {
      const tipo = this.tiposProducto.find((t) => t.valor === this.tipoFiltro);
      activos.push({ clave: 'tipo', texto: `Tipo: ${tipo ? tipo.etiqueta : this.tipoFiltro}` });
    }
    if (this.miCajaFiltro) {
      activos.push({ clave: 'miCaja', texto: `Mi caja: ${this.miCajaFiltro === 'true' ? 'Sí' : 'No'}` });
    }
    return activos;
  }

  get textoTotal(): string {
    return `${this.totalElementos} ${this.totalElementos === 1 ? 'producto' : 'productos'}`;
  }

  private recargarDesdeLaPrimeraPagina(): void {
    this.conceptoAplicado = this.conceptoFiltro;
    this.paginaActual = 1;
    this.guardarEstadoPaginacion();
    this.obtenerProductos();
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
      tipoFiltro: this.tipoFiltro,
      miCajaFiltro: this.miCajaFiltro,
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
      this.tipoFiltro = this.tiposProducto.some((t) => t.valor === estado.tipoFiltro) ? estado.tipoFiltro : '';
      this.miCajaFiltro = estado.miCajaFiltro === 'true' || estado.miCajaFiltro === 'false' ? estado.miCajaFiltro : '';
      this.orderBy = estado.orderBy || 'concepto';
      this.order = estado.order || 'asc';
    } catch (error) {
      console.error('Error parsing saved pagination state:', error);
    }
  }
}
