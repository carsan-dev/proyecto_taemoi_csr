import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  METODOS_PAGO_COBRO,
  cambiosDeCobro,
} from '../../componentes/endpoints/tesoreria-cobros/confirmar-cobro';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { eur } from '../formato/formato-es';

export type TipoCargo = 'MENSUALIDAD' | 'EXAMEN';

/** Lo que hace falta para cobrar un cargo: el de la tesorería de hoy, más quién y cuánto para el diálogo. */
export interface CargoACobrar {
  productoAlumnoId: number;
  concepto: string;
  importe: number;
  alumnoNombre: string;
  /** Si se sabe (el Inicio lo trae), el título lo dice; si no, «cargo». */
  tipo?: TipoCargo | null;
}

/** Título del diálogo y qué deja de figurar como pendiente, según el tipo del cargo. */
export function textosCobro(tipo: TipoCargo | null | undefined): { titulo: string; que: string } {
  switch (tipo) {
    case 'MENSUALIDAD':
      return { titulo: 'Cobrar mensualidad', que: 'la mensualidad' };
    case 'EXAMEN':
      return { titulo: 'Cobrar derecho de examen', que: 'el derecho de examen' };
    default:
      return { titulo: 'Cobrar cargo', que: 'el cargo' };
  }
}

/**
 * «Cobrar» un cargo vencido desde las pantallas nuevas (8b-2, 8b-3a, 8b-3b y 8b-D1; punto de revisión 8). El diálogo es
 * el nuevo (decisión 269, boceto `Main.dc.html`), con el método como pastillas y «Sin indicar» por defecto, pero la
 * llamada y el cuerpo son los de «Marcar pagado» en la tesorería de hoy: `PUT /api/productos-alumno/{id}` con
 * `cambiosDeCobro(metodo)`. Tras cobrar, aviso de éxito; quien hoy puede «Revertir» un cobro en la tesorería (el
 * propietario, `ROLE_ADMIN`) tiene además «Deshacer», con la misma llamada que «Revertir» (`{pagado: false,
 * motivoCambio: null}`, el motivo vacío de hoy).
 */
@Injectable({ providedIn: 'root' })
export class CobroCargoService {
  private readonly endpoints = inject(EndpointsService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly auth = inject(AuthenticationService);

  /**
   * `cobrando` guarda los cargos con un cobro en vuelo (no se cobra dos veces); `alCambiar` se llama tras cobrar y tras
   * deshacer, para volver a leer.
   */
  async cobrar(cargo: CargoACobrar, cobrando: Set<number>, alCambiar: () => void): Promise<void> {
    const id = cargo.productoAlumnoId;
    if (cobrando.has(id)) {
      return;
    }
    const { titulo, que } = textosCobro(cargo.tipo);
    const nombre = (cargo.alumnoNombre ?? '').trim();
    const resultado = await this.confirmacion.confirmar({
      titulo,
      mensaje: nombre
        ? ['Se registra el pago de ', { fuerte: nombre }, ` y ${que} deja de figurar como pendiente.`]
        : `Se registra el pago y ${que} deja de figurar como pendiente.`,
      destacado: { etiqueta: cargo.concepto, valor: eur(cargo.importe) },
      campo: {
        etiqueta: 'Método (opcional)',
        opciones: METODOS_PAGO_COBRO.map(([valor, texto]) => ({ valor, texto })),
        valor: '',
      },
      accion: 'Marcar como pagado',
      accionOcupada: 'Cobrando…',
      ejecutar: (metodo) => {
        cobrando.add(id);
        return this.endpoints.actualizarCobroTesoreria(id, cambiosDeCobro(metodo));
      },
    });
    cobrando.delete(id);
    if (!resultado.confirmado) {
      return;
    }
    if (resultado.error !== undefined) {
      this.avisos.error(mensajeDeError(resultado.error as HttpErrorResponse, 'No se pudo actualizar el estado del cobro'));
      return;
    }
    const texto = `Cobro registrado: ${eur(cargo.importe)}${nombre ? ` · ${nombre}` : ''}`;
    if (this.auth.tieneRolAdmin()) {
      this.avisos.exito(texto, { texto: 'Deshacer', alPulsar: () => this.deshacer(cargo, cobrando, alCambiar) });
    } else {
      this.avisos.exito(texto);
    }
    alCambiar();
  }

  /** «Deshacer»: la misma llamada que «Revertir» de la tesorería de hoy, sin motivo. */
  private deshacer(cargo: CargoACobrar, cobrando: Set<number>, alCambiar: () => void): void {
    const id = cargo.productoAlumnoId;
    if (cobrando.has(id)) {
      return;
    }
    cobrando.add(id);
    this.endpoints.actualizarCobroTesoreria(id, { pagado: false, motivoCambio: null }).subscribe({
      next: () => {
        cobrando.delete(id);
        this.avisos.exito('Cobro revertido a pendiente');
        alCambiar();
      },
      error: (error: HttpErrorResponse) => {
        cobrando.delete(id);
        this.avisos.error(mensajeDeError(error, 'No se pudo revertir el cobro'));
      },
    });
  }
}

/** Los textos de error de la tesorería de hoy: un 403 dice quién puede. */
function mensajeDeError(error: HttpErrorResponse | undefined, porDefecto: string): string {
  return error?.status === 403 ? 'Solo administradores pueden revertir cobros' : porDefecto;
}

/**
 * Teclado del panel de cargos (varios cargos): Esc lo cierra (y devuelve el foco a «Cobrar», cosa de `cerrar`); Tab y
 * Mayús+Tab dan la vuelta dentro del panel. Se engancha al `keydown` del propio panel.
 */
export function teclaPanelCargos(evento: KeyboardEvent, cerrar: () => void): void {
  if (evento.key === 'Escape') {
    evento.preventDefault();
    evento.stopPropagation();
    cerrar();
    return;
  }
  if (evento.key !== 'Tab') {
    return;
  }
  const panel = evento.currentTarget instanceof HTMLElement ? evento.currentTarget : null;
  const enfocables = Array.from(panel?.querySelectorAll<HTMLButtonElement>('button:not([disabled])') ?? []);
  if (enfocables.length === 0) {
    evento.preventDefault();
    return;
  }
  const primero = enfocables[0];
  const ultimo = enfocables[enfocables.length - 1];
  const activo = panel?.ownerDocument.activeElement;
  if (evento.shiftKey && (activo === primero || !panel?.contains(activo ?? null))) {
    evento.preventDefault();
    ultimo.focus();
  } else if (!evento.shiftKey && (activo === ultimo || !panel?.contains(activo ?? null))) {
    evento.preventDefault();
    primero.focus();
  }
}
