import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import Swal from 'sweetalert2';

import { EstadoSolicitud, SegmentoSolicitudes, Solicitud, TipoSolicitud } from '../../../interfaces/solicitud';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { SolicitudesContadorService } from '../../../servicios/endpoints/solicitudes-contador.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { euros } from '../../generales/pedir-al-propietario/pedir-al-propietario';

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

/**
 * Bandeja de solicitudes al propietario (pieza 6c-7, fase3-6c-solicitudes.md §5.1): pendientes, encargos en curso y
 * resueltas. El propietario acepta (la API ejecuta y puede cerrarla como NO_APLICABLE) o deniega con respuesta; quien
 * pidió retira la suya mientras está pendiente. Los encargos solo se listan hasta la 6c-5.
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
