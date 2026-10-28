import { Component, Input, inject } from '@angular/core';

import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ReciboPagoService } from '../../cobro/recibo-pago.service';
import { eur, fecha, numero, plural } from '../../formato/formato-es';
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
export class FichaPagosComponent {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  private readonly auth = inject(AuthenticationService);
  readonly recibos = inject(ReciboPagoService);

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

  /** «Descargar recibo» (pieza 279): gestión (propietario y administración) y cargo cobrado, no cancelado ni devuelto. */
  puedeDescargarRecibo(p: ProductoAlumnoDTO): boolean {
    return this.auth.tieneAccesoAdmin() && !!p.pagado && !p.canceladoEn && !p.devuelto;
  }

  descargarRecibo(p: ProductoAlumnoDTO): void {
    if (this.puedeDescargarRecibo(p)) {
      void this.recibos.descargar(p.id);
    }
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
