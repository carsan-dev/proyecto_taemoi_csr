import { firstValueFrom } from 'rxjs';
import Swal from 'sweetalert2';

import { CrearSolicitud, Solicitud } from '../../../interfaces/solicitud';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

export interface DatoDelRecurso {
  etiqueta: string;
  valor: string;
}

export interface OpcionesPedirAlPropietario {
  /** «Pedir al propietario que revierta este cobro», «… que anule esta entrega»… */
  titulo: string;
  /** Lo que se pide, tal como lo ve quien pide (alumno, concepto, importe, fecha…). */
  datos: DatoDelRecurso[];
  /** «Mientras tanto el pago sigue marcado.» */
  nota: string;
  peticion: Omit<CrearSolicitud, 'motivo'>;
}

const MAX_MOTIVO = 500;

export function escaparHtml(texto: string): string {
  return texto
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function euros(importe: number | null | undefined): string {
  const n = Number(importe);
  return Number.isFinite(n) ? n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' }) : '—';
}

function cuerpo(opciones: OpcionesPedirAlPropietario): string {
  const filas = opciones.datos
    .filter((dato) => dato.valor?.trim())
    .map((dato) => `<dt>${escaparHtml(dato.etiqueta)}</dt><dd>${escaparHtml(dato.valor)}</dd>`)
    .join('');
  return (
    `<dl class="solicitud-datos">${filas}</dl>` +
    `<p class="solicitud-nota"><i class="bi bi-info-circle" aria-hidden="true"></i> ${escaparHtml(opciones.nota)}</p>`
  );
}

/**
 * Diálogo «Pedir al propietario» (fase3-6c-solicitudes.md §5.2, D6c-8): datos del recurso, motivo obligatorio y la
 * nota de que mientras tanto nada cambia. Envía la solicitud y avisa; un error (p. ej. 409 «ya hay una pendiente sobre
 * este recurso») muestra el mensaje del servidor. Devuelve la solicitud creada o `null` si se cancela o falla.
 */
export async function pedirAlPropietario(
  solicitudes: SolicitudesService,
  opciones: OpcionesPedirAlPropietario
): Promise<Solicitud | null> {
  const resultado = await Swal.fire({
    title: opciones.titulo,
    html: cuerpo(opciones),
    input: 'textarea',
    inputLabel: 'Motivo',
    inputPlaceholder: 'Qué ha pasado y por qué hay que cambiarlo',
    inputAttributes: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Motivo de la solicitud' },
    inputValidator: (valor: string) =>
      (valor ?? '').trim() ? null : 'Escribe el motivo: el propietario lo verá al decidir',
    showCancelButton: true,
    confirmButtonText: 'Enviar solicitud',
    cancelButtonText: 'Cancelar',
    confirmButtonColor: '#284747',
    customClass: { popup: 'swal-solicitud' },
  });
  if (!resultado.isConfirmed) {
    return null;
  }
  const motivo = String(resultado.value ?? '').trim();
  try {
    const creada = await firstValueFrom(solicitudes.crear({ ...opciones.peticion, motivo }));
    showSuccessToast('Solicitud enviada al propietario');
    return creada;
  } catch (error) {
    showErrorToast(mensajeDeError(error) ?? 'No se ha podido enviar la solicitud.');
    return null;
  }
}
