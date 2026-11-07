import { environment } from '../../../../../environments/environment';
import { DocumentoEventoWeb, EventoDetalleWeb } from '../../../datos/modelos';
import { FechaEvento, fechaEvento, imagenEvento, lugarEvento } from '../eventos/eventos';
import { TEXTOS_PAGINAS_LINEA_COMARCAL as TEXTOS } from '../textos';

/**
 * Lo que pinta la página de un evento del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal-evento.html`)
 * a partir de `GET /api/publico/web/eventos/{id}`: fecha y día, título, cartel, descripción, documentos y galería
 * numerada. Sin documentos o sin fotos, su bloque no se pinta (como el boceto). Los ficheros, por los endpoints públicos
 * de eventos (la API solo da los ids).
 */

export interface VistaDocumento {
  nombre: string;
  /** «PDF», «Word»…; null si no se sabe. */
  tipo: string | null;
  url: string;
}

export interface VistaFoto {
  /** «01». */
  numero: string;
  miniatura: string;
  grande: string;
  /** «Ampliar la foto 1 de 7». */
  ampliar: string;
  /** «Foto 1 de 7» (el `alt` en el visor). */
  alt: string;
}

export interface VistaEventoDetalle {
  titulo: string;
  fecha: FechaEvento | null;
  /** «Sábado», con la hora si la tiene: «Sábado · 10:00». */
  cuando: string | null;
  /** «Pabellón municipal, Estepa»; null sin lugar ni sede. */
  lugar: string | null;
  descripcion: string | null;
  imagenUrl: string | null;
  documentos: VistaDocumento[];
  fotos: VistaFoto[];
  /** «7 fotos». */
  cuentaFotos: string;
}

export function vistaEventoDetalle(evento: EventoDetalleWeb): VistaEventoDetalle {
  const fecha = fechaEvento(evento.fecha);
  const hora = evento.hora?.trim() || null;
  const cuando = [fecha?.semana, hora].filter(Boolean).join(' · ');
  const base = `${environment.apiUrl}/eventos/${evento.id}`;
  const total = evento.fotos.length;
  const contar = (texto: string, n: number) => texto.replace('{n}', String(n)).replace('{total}', String(total));
  return {
    titulo: evento.titulo,
    fecha,
    cuando: cuando || null,
    lugar: lugarEvento(evento),
    descripcion: evento.descripcion?.trim() || null,
    imagenUrl: imagenEvento(evento),
    documentos: evento.documentos.map((d) => ({
      nombre: d.nombre,
      tipo: tipoDocumento(d),
      url: `${base}/documentos/${d.id}/descargar?download=true`,
    })),
    fotos: evento.fotos.map((f, i) => ({
      numero: String(i + 1).padStart(2, '0'),
      miniatura: `${base}/fotos/${f.id}?w=640`,
      grande: `${base}/fotos/${f.id}`,
      ampliar: contar(TEXTOS.ampliarFoto, i + 1),
      alt: contar(TEXTOS.fotoN, i + 1),
    })),
    cuentaFotos: total === 1 ? TEXTOS.unaFoto : TEXTOS.nFotos.replace('{n}', String(total)),
  };
}

/** El tipo como lo dice el boceto («PDF», «Word»): por el tipo MIME y, si no, por la extensión del nombre. */
export function tipoDocumento(documento: Pick<DocumentoEventoWeb, 'nombre' | 'tipo'>): string | null {
  const mime = (documento.tipo ?? '').toLowerCase();
  if (mime.includes('pdf')) {
    return 'PDF';
  }
  if (mime.includes('msword') || mime.includes('wordprocessingml')) {
    return 'Word';
  }
  if (mime.includes('ms-excel') || mime.includes('spreadsheetml')) {
    return 'Excel';
  }
  const extension = /\.([a-z0-9]{1,5})$/i.exec(documento.nombre.trim())?.[1];
  return extension ? extension.toUpperCase() : null;
}
