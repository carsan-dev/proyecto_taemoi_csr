import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, Input, OnDestroy, inject } from '@angular/core';

import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../../avisos/avisos.service';
import { ReciboPagoService } from '../../cobro/recibo-pago.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { eur, fecha, numero, plural } from '../../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../../navegacion/teclado-menu';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

/**
 * Pestaña «Pagos» de la ficha nueva (8b-3b): la tabla de productos y pagos de hoy (4 por página, lo más reciente
 * primero), «Cobrar» de cada cargo con el diálogo y el PUT de la ficha de hoy (`pagarProducto`), «Gestionar productos»
 * y «Reservar plaza». Por debajo de 768 px la tabla pasa a filas de dos líneas.
 */
@Component({
  selector: 'app-ficha-pagos',
  standalone: true,
  templateUrl: './ficha-pagos.component.html',
  styleUrls: ['./ficha-pagos.component.scss'],
})
export class FichaPagosComponent implements OnDestroy {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  private readonly auth = inject(AuthenticationService);
  readonly recibos = inject(ReciboPagoService);
  private readonly endpoints = inject(EndpointsService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Cargos con un «Revertir» en vuelo: sin doble envío. */
  readonly revirtiendo = new Set<number>();
  /** Cargo con el menú «⋯» abierto. */
  menuAbierto: number | null = null;
  private escuchaFuera = false;

  readonly eur = eur;
  readonly fecha = fecha;
  readonly numero = numero;

  /** «6 cargos, 60,00 € pendiente»; sin nada sin pagar, «todo pagado». */
  get resumen(): string {
    const productos = this.ficha.productosAlumno;
    if (productos.length === 0) {
      return '';
    }
    const pendiente = productos
      .filter((p) => !p.pagado)
      .reduce((suma, p) => suma + this.ficha.calcularTotal(p.precio, p.cantidad), 0);
    const cargos = plural(productos.length, 'cargo', 'cargos');
    return pendiente > 0 ? `${cargos}, ${eur(pendiente)} pendiente` : `${cargos}, todo pagado`;
  }

  /** «Descargar recibo» (pieza 279): gestión y responsable de sede, y cargo cobrado, no cancelado ni devuelto. */
  puedeDescargarRecibo(p: ProductoAlumnoDTO): boolean {
    return (this.auth.tieneAccesoAdmin() || this.ficha.panelSede) && !!p.pagado && !p.canceladoEn && !p.devuelto;
  }

  descargarRecibo(p: ProductoAlumnoDTO): void {
    if (this.puedeDescargarRecibo(p)) {
      void this.recibos.descargar(p.id);
    }
  }

  /**
   * «Revertir» (propietario, como Tesorería: `tieneRolAdmin`): cargo pagado y sin devolución. El servidor decide el
   * resto (un cobro ya incluido en una entrega de caja da 409).
   */
  puedeRevertir(p: ProductoAlumnoDTO): boolean {
    return this.auth.tieneRolAdmin() && !!p.pagado && !p.devuelto;
  }

  private menuDe(id: number): HTMLElement | null {
    return this.anfitrion.nativeElement.querySelector<HTMLElement>(`#pagos-menu-${id}`);
  }

  private botonMas(id: number): HTMLButtonElement | null {
    return this.anfitrion.nativeElement.querySelector<HTMLButtonElement>(`[data-mas="${id}"]`);
  }

  alternarMenu(id: number, evento?: KeyboardEvent): void {
    if (this.menuAbierto === id) {
      this.menuAbierto = null;
      this.sincronizarEscucha();
      return;
    }
    this.menuAbierto = id;
    this.sincronizarEscucha();
    this.cdr.detectChanges();
    enfocarOpcion(this.menuDe(id), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMas(evento: KeyboardEvent, id: number): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && this.menuAbierto !== id) {
      evento.preventDefault();
      this.alternarMenu(id, evento);
    }
  }

  teclaMenu(evento: KeyboardEvent, id: number): void {
    const menu = this.menuDe(id);
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenu(true);
    } else if (accion === 'salir') {
      this.cerrarMenu(false);
    }
  }

  cerrarMenu(devolverFoco: boolean): void {
    const id = this.menuAbierto;
    this.menuAbierto = null;
    this.sincronizarEscucha();
    if (devolverFoco && id !== null) {
      this.cdr.detectChanges();
      this.botonMas(id)?.focus();
    }
  }

  /** Fuera del menú (foco), se cierra. */
  alSalirDeZona(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.menuAbierto = null;
      this.sincronizarEscucha();
    }
  }

  /** El clic fuera de un menú abierto lo cierra, también en zonas sin foco. */
  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.menuAbierto !== null ? this.botonMas(this.menuAbierto)?.closest('.pagos__menu-zona') : null;
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.menuAbierto = null;
    this.sincronizarEscucha();
    this.cdr.markForCheck();
  };

  private sincronizarEscucha(): void {
    const abierto = this.menuAbierto !== null;
    if (abierto && !this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = true;
    } else if (!abierto && this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  ngOnDestroy(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  /** Cada opción cierra el menú y deja el foco en «⋯», para que el diálogo lo devuelva allí al cerrarse. */
  opcionMenu(p: ProductoAlumnoDTO, opcion: 'recibo' | 'revertir'): void {
    this.cerrarMenu(true);
    if (opcion === 'recibo') {
      this.descargarRecibo(p);
    } else {
      void this.revertir(p);
    }
  }

  /** «Revertir»: el diálogo de Tesorería (destructivo, motivo opcional) y `{pagado: false, motivoCambio}`. */
  async revertir(p: ProductoAlumnoDTO): Promise<void> {
    if (!this.puedeRevertir(p) || this.revirtiendo.has(p.id)) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Revertir cobro',
      mensaje: `El cobro "${p.concepto}" pasará a pendiente.`,
      accion: 'Sí, revertir',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'revertir',
      campos: [
        { id: 'motivo', tipo: 'textarea', etiqueta: 'Motivo (opcional)', valor: '', placeholder: 'Ejemplo: error de marcación' },
      ],
    });
    if (!r.confirmado || this.revirtiendo.has(p.id)) {
      return;
    }
    const motivoCambio = (r.valores?.['motivo'] ?? '').toString().trim() || null;
    this.revirtiendo.add(p.id);
    this.endpoints.actualizarCobroTesoreria(p.id, { pagado: false, motivoCambio }).subscribe({
      next: (respuesta) => {
        this.revirtiendo.delete(p.id);
        const final: ProductoAlumnoDTO =
          respuesta?.id === p.id ? respuesta : { ...p, pagado: false, fechaPago: null, devuelto: undefined };
        this.ficha.actualizarProductoEnListas(final);
        this.ficha.releerPendiente();
        this.avisos.exito('Cobro revertido a pendiente');
      },
      error: (error: HttpErrorResponse) => {
        this.revirtiendo.delete(p.id);
        this.avisos.error(error?.status === 403 ? 'Solo administradores pueden revertir cobros' : 'No se pudo revertir el cobro');
      },
    });
  }

  total(p: ProductoAlumnoDTO): string {
    return eur(this.ficha.calcularTotal(p.precio, p.cantidad));
  }

  estadoPagado(p: ProductoAlumnoDTO): string {
    const cuando = fecha(p.fechaPago);
    return cuando ? `Pagado ${cuando}` : 'Pagado';
  }

  get posicion(): string {
    return `${numero(this.ficha.paginaActualProductos)} de ${numero(this.ficha.totalPaginasProductos)}`;
  }
}
