import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import Swal from 'sweetalert2';

import { EstadoCobroCargo, EstadoSolicitud, SegmentoSolicitudes, Solicitud, TipoSolicitud } from '../../../interfaces/solicitud';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { SolicitudesContadorService } from '../../../servicios/endpoints/solicitudes-contador.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { escaparHtml, euros } from '../../generales/pedir-al-propietario/pedir-al-propietario';
import {
  ETIQUETA_COBRO,
  MAX_MOTIVO,
  PASOS_ENCARGO,
  encargoCaido,
  estadoCobroEncargo,
  indicePasoEncargo,
} from '../mi-caja/mi-caja-pedidos';

interface EstadoSegmento {
  lista: Solicitud[] | null;
  cargando: boolean;
  error: string | null;
}

const ETIQUETA_TIPO: Record<TipoSolicitud, string> = {
  REVERTIR_COBRO: 'Revertir cobro',
  DESHACER_COBRO_ENTREGADO: 'Deshacer cobro entregado',
  ANULAR_ENTREGA: 'Anular entrega',
  ANULAR_MOVIMIENTO: 'Anular movimiento',
  ENCARGO: 'Encargo',
};

const ETIQUETA_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADA: 'Aceptada',
  DENEGADA: 'Denegada',
  RETIRADA: 'Retirada',
  NO_APLICABLE: 'No aplicable',
  PEDIDO: 'Pedido',
  ENCARGADO: 'Encargado',
  RECIBIDO: 'Recibido',
  ENTREGADO: 'Entregado',
  RECHAZADO: 'Rechazado',
};

/** Verbo de «Aceptar y …», título de la confirmación, qué pasa y el aviso al terminar, por tipo. */
const ACEPTAR: Record<Exclude<TipoSolicitud, 'ENCARGO'>, { verbo: string; titulo: string; efecto: string; hecho: string }> = {
  REVERTIR_COBRO: {
    verbo: 'Aceptar y revertir',
    titulo: 'Aceptar y revertir el cobro',
    efecto: 'El cargo pasará a pendiente, como al revertirlo desde la tesorería.',
    hecho: 'Cobro revertido',
  },
  DESHACER_COBRO_ENTREGADO: {
    verbo: 'Aceptar y deshacer',
    titulo: 'Aceptar y deshacer el cobro',
    efecto: 'El cobro se anula y su entrega apunta la corrección; la entrega sigue en pie.',
    hecho: 'Cobro deshecho',
  },
  ANULAR_ENTREGA: {
    verbo: 'Aceptar y anular',
    titulo: 'Aceptar y anular la entrega',
    efecto: 'Sus cobros vuelven a estar pendientes de entregar.',
    hecho: 'Entrega anulada',
  },
  ANULAR_MOVIMIENTO: {
    verbo: 'Aceptar y anular',
    titulo: 'Aceptar y anular el movimiento',
    efecto: 'Deja de contar en la caja.',
    hecho: 'Movimiento anulado',
  },
};

const SEGMENTOS: SegmentoSolicitudes[] = ['PENDIENTES', 'ENCARGOS', 'RESUELTAS'];

/** 403 al registrar la devolución: el cobro es de otra sede (nota F2 de la 6c-5; la caja es la del cobro). */
export const DEVOLUCION_OTRA_SEDE =
  'El cobro de este encargo es de otra sede: la devolución la registra la gestión o el propietario.';

/** Avanzar un encargo: título, qué pasa, verbo y aviso, por el estado al que pasa (§6.2). */
const AVANCE: Record<'ENCARGADO' | 'RECIBIDO', { titulo: string; efecto: string; verbo: string; hecho: string }> = {
  ENCARGADO: {
    titulo: 'Marcar como encargado',
    efecto: 'Ya se ha pedido al proveedor.',
    verbo: 'Marcar encargado',
    hecho: 'Encargo marcado como encargado',
  },
  RECIBIDO: {
    titulo: 'Marcar como recibido',
    efecto: 'Ha llegado y se le da al profesor, que lo marcará entregado al dárselo al alumno.',
    verbo: 'Marcar recibido',
    hecho: 'Encargo marcado como recibido',
  },
};

/**
 * Bandeja de solicitudes al propietario (pieza 6c-7, fase3-6c-solicitudes.md §5.1): pendientes, encargos en curso y
 * resueltas. El propietario acepta (la API ejecuta y puede cerrarla como NO_APLICABLE) o deniega con respuesta; quien
 * pidió retira la suya mientras está pendiente. Pieza 6c-9: quien atiende los encargos (propietario, gestión y
 * responsable de la sede) los marca encargados o recibidos, los rechaza con motivo y registra la devolución de los que
 * cayeron pagados con el dinero ya en la sede (195).
 */
@Component({
  selector: 'app-solicitudes-bandeja',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './solicitudes-bandeja.component.html',
  styleUrl: './solicitudes-bandeja.component.scss',
})
export class SolicitudesBandejaComponent implements OnInit {
  private readonly solicitudes = inject(SolicitudesService);
  private readonly contador = inject(SolicitudesContadorService);
  private readonly auth = inject(AuthenticationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly segmentos = SEGMENTOS;
  readonly pasosEncargo = PASOS_ENCARGO;
  segmento: SegmentoSolicitudes = 'PENDIENTES';
  esPropietario = false;

  readonly estado: Record<SegmentoSolicitudes, EstadoSegmento> = {
    PENDIENTES: { lista: null, cargando: false, error: null },
    ENCARGOS: { lista: null, cargando: false, error: null },
    RESUELTAS: { lista: null, cargando: false, error: null },
  };

  /** Solicitudes con una acción en curso: sus botones quedan deshabilitados (sin doble clic). */
  private readonly enCurso = new Set<number>();

  ngOnInit(): void {
    this.resolverPermisos();
    this.cargar('PENDIENTES');
    this.cargar('ENCARGOS');
  }

  // ------------------------------------------------------------------ segmentos

  elegirSegmento(segmento: SegmentoSolicitudes): void {
    this.segmento = segmento;
    if (this.estado[segmento].lista === null && !this.estado[segmento].cargando) {
      this.cargar(segmento);
    }
  }

  moverSegmento(evento: KeyboardEvent): void {
    const paso = evento.key === 'ArrowRight' ? 1 : evento.key === 'ArrowLeft' ? -1 : 0;
    if (!paso) {
      return;
    }
    evento.preventDefault();
    const i = SEGMENTOS.indexOf(this.segmento);
    const siguiente = SEGMENTOS[(i + paso + SEGMENTOS.length) % SEGMENTOS.length];
    this.elegirSegmento(siguiente);
    setTimeout(() => document.getElementById(`segmento-${siguiente}`)?.focus());
  }

  etiquetaSegmento(segmento: SegmentoSolicitudes): string {
    const lista = this.estado[segmento].lista;
    switch (segmento) {
      case 'PENDIENTES':
        return lista ? `Pendientes (${lista.length})` : 'Pendientes';
      case 'ENCARGOS':
        return lista ? `Encargos en curso (${lista.length})` : 'Encargos en curso';
      default:
        return 'Resueltas';
    }
  }

  cargar(segmento: SegmentoSolicitudes): void {
    const estado = this.estado[segmento];
    estado.cargando = true;
    estado.error = null;
    this.solicitudes
      .listar(segmento)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          estado.lista = lista;
          estado.cargando = false;
        },
        error: (error) => {
          estado.cargando = false;
          estado.error = mensajeDeError(error) ?? 'No se han podido cargar las solicitudes.';
        },
      });
  }

  /** Tras resolver una: los segmentos ya cargados y el contador de la cabecera. */
  recargar(): void {
    for (const segmento of SEGMENTOS) {
      if (segmento !== 'RESUELTAS' || this.estado.RESUELTAS.lista !== null) {
        this.cargar(segmento);
      }
    }
    this.contador.refrescar();
  }

  // ------------------------------------------------------------------ presentación

  etiquetaTipo(tipo: TipoSolicitud): string {
    return ETIQUETA_TIPO[tipo] ?? tipo;
  }

  etiquetaEstado(estado: EstadoSolicitud): string {
    return ETIQUETA_ESTADO[estado] ?? estado;
  }

  claseEstado(estado: EstadoSolicitud): string {
    switch (estado) {
      case 'ACEPTADA':
      case 'ENTREGADO':
        return 'estado-hecho';
      case 'DENEGADA':
      case 'RECHAZADO':
        return 'estado-no';
      case 'NO_APLICABLE':
        return 'estado-aviso';
      case 'RETIRADA':
        return 'estado-neutro';
      default:
        return 'estado-curso';
    }
  }

  /** Lo principal de «sobre qué»: el alumno o, si no hay, el recurso. */
  sobreQue(s: Solicitud): string {
    const alumno = [s.alumnoNombre, s.alumnoApellidos].filter((p) => p?.trim()).join(' ').trim();
    if (alumno) {
      return alumno;
    }
    if (s.tipo === 'ANULAR_ENTREGA') {
      return 'Entrega de caja';
    }
    if (s.tipo === 'ANULAR_MOVIMIENTO') {
      return 'Movimiento de caja';
    }
    return 'Sin alumno';
  }

  euros(importe: number | null): string {
    return importe === null ? '' : euros(importe);
  }

  esPendiente(s: Solicitud): boolean {
    return s.estado === 'PENDIENTE' && s.tipo !== 'ENCARGO';
  }

  puedeResolver(s: Solicitud): boolean {
    return this.esPropietario && this.esPendiente(s);
  }

  puedeRetirar(s: Solicitud): boolean {
    return s.propia && this.esPendiente(s);
  }

  verboAceptar(s: Solicitud): string {
    return s.tipo === 'ENCARGO' ? '' : ACEPTAR[s.tipo].verbo;
  }

  ocupada(s: Solicitud): boolean {
    return this.enCurso.has(s.id);
  }

  // ------------------------------------------------------------------ encargos (6c-9)

  esEncargo(s: Solicitud): boolean {
    return s.tipo === 'ENCARGO';
  }

  encargoCaido(s: Solicitud): boolean {
    return encargoCaido(s);
  }

  indicePaso(s: Solicitud): number {
    return indicePasoEncargo(s);
  }

  estadoCobro(s: Solicitud): EstadoCobroCargo | null {
    return estadoCobroEncargo(s);
  }

  etiquetaCobro(estado: EstadoCobroCargo): string {
    return ETIQUETA_COBRO[estado];
  }

  puedeMarcarEncargado(s: Solicitud): boolean {
    return this.esEncargo(s) && s.estado === 'PEDIDO';
  }

  puedeMarcarRecibido(s: Solicitud): boolean {
    return this.esEncargo(s) && s.estado === 'ENCARGADO';
  }

  puedeRechazar(s: Solicitud): boolean {
    return this.esEncargo(s) && (s.estado === 'PEDIDO' || s.estado === 'ENCARGADO' || s.estado === 'RECIBIDO');
  }

  /** Cayó pagado y el dinero ya está en la sede: la devolución la registra quien atiende (195). */
  puedeRegistrarDevolucion(s: Solicitud): boolean {
    return this.esEncargo(s) && s.devolucionPendiente && s.devolucionEnSede === true;
  }

  /** Cayó pagado y el profesor aún lleva el dinero: la registra él desde Mi caja. */
  devolucionDelProfesor(s: Solicitud): boolean {
    return this.esEncargo(s) && s.devolucionPendiente && s.devolucionEnSede === false;
  }

  // ------------------------------------------------------------------ acciones

  async aceptar(s: Solicitud): Promise<void> {
    if (!this.puedeResolver(s) || this.ocupada(s) || s.tipo === 'ENCARGO') {
      return;
    }
    const textos = ACEPTAR[s.tipo];
    const confirmacion = await Swal.fire({
      title: textos.titulo,
      text: `${this.descripcion(s)}. ${textos.efecto}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: textos.verbo,
      cancelButtonText: 'Volver',
      confirmButtonColor: '#284747',
    });
    if (!confirmacion.isConfirmed) {
      return;
    }
    await this.ejecutar(s, async () => {
      const resultado = await firstValueFrom(this.solicitudes.aceptar(s.id));
      if (resultado.estado === 'NO_APLICABLE') {
        await Swal.fire({
          icon: 'warning',
          title: 'No se ha aplicado',
          text: resultado.respuesta?.trim() || 'El estado cambió desde que se pidió.',
          confirmButtonText: 'Entendido',
          confirmButtonColor: '#284747',
        });
      } else {
        showSuccessToast(textos.hecho);
      }
    }, 'No se ha podido aceptar la solicitud.');
  }

  async denegar(s: Solicitud): Promise<void> {
    if (!this.puedeResolver(s) || this.ocupada(s)) {
      return;
    }
    const resultado = await Swal.fire({
      title: 'Denegar la solicitud',
      text: this.descripcion(s),
      input: 'textarea',
      inputLabel: 'Respuesta',
      inputPlaceholder: 'Por qué no se hace; quien lo pidió lo verá',
      inputAttributes: { maxlength: '500', 'aria-label': 'Respuesta a quien lo pidió' },
      inputValidator: (valor: string) => ((valor ?? '').trim() ? null : 'Escribe la respuesta para quien lo pidió'),
      showCancelButton: true,
      confirmButtonText: 'Denegar',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#c53030',
    });
    if (!resultado.isConfirmed) {
      return;
    }
    const respuesta = String(resultado.value ?? '').trim();
    await this.ejecutar(s, async () => {
      await firstValueFrom(this.solicitudes.denegar(s.id, respuesta));
      showSuccessToast('Solicitud denegada');
    }, 'No se ha podido denegar la solicitud.');
  }

  async retirar(s: Solicitud): Promise<void> {
    if (!this.puedeRetirar(s) || this.ocupada(s)) {
      return;
    }
    await this.ejecutar(s, async () => {
      await firstValueFrom(this.solicitudes.retirar(s.id));
      showSuccessToast('Solicitud retirada');
    }, 'No se ha podido retirar la solicitud.');
  }

  marcarEncargado(s: Solicitud): Promise<void> {
    return this.avanzar(s, 'ENCARGADO');
  }

  marcarRecibido(s: Solicitud): Promise<void> {
    return this.avanzar(s, 'RECIBIDO');
  }

  private async avanzar(s: Solicitud, destino: 'ENCARGADO' | 'RECIBIDO'): Promise<void> {
    const puede = destino === 'ENCARGADO' ? this.puedeMarcarEncargado(s) : this.puedeMarcarRecibido(s);
    if (!puede || this.ocupada(s)) {
      return;
    }
    const textos = AVANCE[destino];
    const confirmacion = await Swal.fire({
      title: textos.titulo,
      text: `${this.descripcion(s)}. ${textos.efecto}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: textos.verbo,
      cancelButtonText: 'Volver',
      confirmButtonColor: '#284747',
    });
    if (!confirmacion.isConfirmed) {
      return;
    }
    await this.ejecutar(s, async () => {
      await firstValueFrom(
        destino === 'ENCARGADO' ? this.solicitudes.marcarEncargado(s.id) : this.solicitudes.marcarRecibido(s.id)
      );
      showSuccessToast(textos.hecho);
    }, 'No se ha podido actualizar el encargo.');
  }

  /** Rechazar con motivo obligatorio (D6c-1), con el diálogo de «Denegar…». Cae el encargo (§6.3). */
  async rechazar(s: Solicitud): Promise<void> {
    if (!this.puedeRechazar(s) || this.ocupada(s)) {
      return;
    }
    const efecto = this.estadoCobro(s) === 'COBRADO'
      ? 'El cargo ya está cobrado: quedará una devolución pendiente.'
      : 'El cargo del alumno se cancelará.';
    const resultado = await Swal.fire({
      title: 'Rechazar el encargo',
      text: `${this.descripcion(s)}. ${efecto}`,
      input: 'textarea',
      inputLabel: 'Motivo',
      inputPlaceholder: 'Por qué no se puede atender; el profesor lo verá',
      inputAttributes: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Motivo del rechazo' },
      inputValidator: (valor: string) => ((valor ?? '').trim() ? null : 'Escribe el motivo para el profesor'),
      showCancelButton: true,
      confirmButtonText: 'Rechazar',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#c53030',
    });
    if (!resultado.isConfirmed) {
      return;
    }
    const motivo = String(resultado.value ?? '').trim();
    await this.ejecutar(s, async () => {
      await firstValueFrom(this.solicitudes.rechazar(s.id, motivo));
      showSuccessToast('Encargo rechazado');
    }, 'No se ha podido rechazar el encargo.');
  }

  /**
   * «Registrar devolución» (195, D6c-11): confirma alumno, concepto e importe (el cobro entero; no se pide) y una nota
   * opcional. Un 403 es la nota F2 de la 6c-5: el cobro es de otra sede, así que se explica quién la registra.
   */
  async registrarDevolucion(s: Solicitud): Promise<void> {
    if (!this.puedeRegistrarDevolucion(s) || this.ocupada(s)) {
      return;
    }
    const datos: Array<[string, string]> = [
      ['Alumno', this.sobreQue(s)],
      ['Concepto', s.concepto?.trim() ?? ''],
      ['Importe', this.euros(s.importe)],
    ];
    const filas = datos
      .filter(([, valor]) => valor)
      .map(([etiqueta, valor]) => `<dt>${escaparHtml(etiqueta)}</dt><dd>${escaparHtml(valor)}</dd>`)
      .join('');
    const resultado = await Swal.fire({
      title: 'Registrar devolución',
      html:
        `<dl class="solicitud-datos">${filas}</dl>` +
        '<p class="solicitud-nota"><i class="bi bi-info-circle" aria-hidden="true"></i> ' +
        'Se devuelve el cobro entero y sale de la caja de la sede.</p>',
      input: 'textarea',
      inputLabel: 'Nota (opcional)',
      inputPlaceholder: 'A quién y cómo se ha devuelto',
      inputAttributes: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Nota de la devolución' },
      showCancelButton: true,
      confirmButtonText: 'Registrar devolución',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#284747',
      customClass: { popup: 'swal-solicitud' },
    });
    if (!resultado.isConfirmed) {
      return;
    }
    const nota = String(resultado.value ?? '').trim() || null;
    await this.ejecutar(s, async () => {
      try {
        await firstValueFrom(this.solicitudes.registrarDevolucion(s.id, nota));
      } catch (error) {
        if (error instanceof HttpErrorResponse && error.status === 403) {
          await Swal.fire({
            icon: 'info',
            title: 'No puedes registrarla',
            text: DEVOLUCION_OTRA_SEDE,
            confirmButtonText: 'Entendido',
            confirmButtonColor: '#284747',
          });
          return;
        }
        throw error;
      }
      showSuccessToast('Devolución registrada');
    }, 'No se ha podido registrar la devolución.');
  }

  private async ejecutar(s: Solicitud, accion: () => Promise<void>, porDefecto: string): Promise<void> {
    this.enCurso.add(s.id);
    try {
      await accion();
    } catch (error) {
      showErrorToast(mensajeDeError(error) ?? porDefecto);
    } finally {
      this.enCurso.delete(s.id);
      this.recargar();
    }
  }

  private descripcion(s: Solicitud): string {
    return [this.sobreQue(s), s.concepto?.trim(), this.euros(s.importe)].filter((p) => p).join(', ');
  }

  private resolverPermisos(): void {
    if (this.auth.rolesEstanCargados()) {
      this.esPropietario = this.auth.tieneRolAdmin();
      return;
    }
    this.auth
      .obtenerRoles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => (this.esPropietario = this.auth.tieneRolAdmin()),
        error: () => (this.esPropietario = false),
      });
  }
}
