import { environment } from '../../../../../environments/environment';
import { EventoListaWeb, EventoWeb, EventosWeb } from '../../../datos/modelos';
import { TextosTema, rellenarHuecos } from '../contenido';
import { TEXTOS_PAGINAS_LINEA_COMARCAL as P } from '../textos';

/**
 * Lo que pintan las páginas de eventos del tema «La línea comarcal» (bocetos `fase4-web/boceto-linea-comarcal-eventos.html`
 * y `-evento.html`) a partir de `GET /api/publico/web/eventos` y `/eventos/{id}`. Sin Angular: los componentes solo lo
 * pintan.
 *
 * Hora, lugar y sede son opcionales (decisión 466): si faltan, no se pinta nada. Los bocetos no los tienen; se pintan
 * como en la portada («lugar, sede · hora»).
 */

const MES_CORTO = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const MES_LARGO = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];
const DIA_LARGO = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** El bloque de fecha del boceto: día grande, mes abreviado y año; la fecha larga, para lectores de pantalla. */
export interface FechaEvento {
  /** `yyyy-MM-dd` (`datetime`). */
  iso: string;
  dia: number;
  /** «nov». */
  mes: string;
  anio: number;
  /** «sábado, 14 de noviembre de 2026». */
  larga: string;
  /** «Sábado». */
  semana: string;
  /** «14 nov 2026» (la rejilla de anteriores). */
  corta: string;
}

/** `2026-11-14` → su {@link FechaEvento}, sin pasar por la zona horaria; null si no es una fecha. */
export function fechaEvento(iso: string | null | undefined): FechaEvento | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  if (!partes) {
    return null;
  }
  const [anio, mes, dia] = partes.slice(1).map(Number);
  const fecha = new Date(anio, mes - 1, dia);
  if (fecha.getMonth() !== mes - 1 || fecha.getDate() !== dia) {
    return null;
  }
  const semana = DIA_LARGO[fecha.getDay()];
  return {
    iso: iso!,
    dia,
    mes: MES_CORTO[mes - 1],
    anio,
    larga: `${semana}, ${dia} de ${MES_LARGO[mes - 1]} de ${anio}`,
    semana: semana.charAt(0).toUpperCase() + semana.slice(1),
    corta: `${dia} ${MES_CORTO[mes - 1]} ${anio}`,
  };
}

/** «2026-11-02» → «2 de noviembre de 2026»; null si no es una fecha. */
export function fechaLargaSinDia(iso: string | null | undefined): string | null {
  const fecha = fechaEvento(iso);
  return fecha ? `${fecha.dia} de ${MES_LARGO[Number(fecha.iso.slice(5, 7)) - 1]} de ${fecha.anio}` : null;
}

/** «Pabellón municipal, Estepa»: lugar y sede, los que haya; null sin ninguno. */
export function lugarEvento(evento: EventoWeb): string | null {
  const lugar = [evento.lugar, evento.sedeNombre].map((v) => v?.trim()).filter(Boolean).join(', ');
  return lugar || null;
}

/** El cartel (`GET /api/eventos/{id}/imagen`); null sin imagen. */
export function imagenEvento(evento: EventoWeb): string | null {
  return evento.conImagen ? `${environment.apiUrl}/eventos/${evento.id}/imagen` : null;
}

export interface VistaEventoLista {
  id: number;
  titulo: string;
  fecha: FechaEvento;
  descripcion: string | null;
  imagenUrl: string | null;
  lugar: string | null;
  hora: string | null;
}

export interface VistaEventos {
  /** Null sin próximos: «No hay eventos próximos». */
  proximos: { destacado: VistaEventoLista; resto: VistaEventoLista[]; cuenta: string } | null;
  /** Los anteriores por año, del más reciente al más antiguo. */
  anios: { anio: number; eventos: VistaEventoLista[] }[];
  /** Sin próximos ni anteriores: la sección de anteriores no se pinta. */
  vacia: boolean;
}

export function vistaEventoLista(evento: EventoListaWeb): VistaEventoLista | null {
  const fecha = fechaEvento(evento.fecha);
  if (!fecha) {
    return null;
  }
  return {
    id: evento.id,
    titulo: evento.titulo,
    fecha,
    descripcion: evento.descripcion?.trim() || null,
    imagenUrl: imagenEvento(evento),
    lugar: lugarEvento(evento),
    hora: evento.hora?.trim() || null,
  };
}

/**
 * Los textos que la plantilla de la lista pinta tal cual (W12d-bis): los de la escuela (`eventos.*`, con el del tema
 * por defecto) y los fijos sin clave. Mismos nombres que antes en la plantilla.
 */
export function textosEventos(textos: TextosTema) {
  return {
    ...P,
    tituloEventos: textos.t('eventos.pag_titulo'),
    entradaEventos: textos.t('eventos.pag_entrada'),
    proximos: textos.t('eventos.proximos'),
    anteriores: textos.t('eventos.anteriores'),
    sinProximosTitular: textos.t('eventos.sin_proximos_titular'),
    sinProximosTexto: textos.t('eventos.sin_proximos_texto'),
    verAnteriores: textos.t('eventos.ver_anteriores'),
    sinAnteriores: textos.t('eventos.sin_anteriores'),
    // El mismo texto que el enlace de cada evento en Inicio.
    verEvento: textos.t('eventos.enlace_ver'),
  };
}

/** La lista del boceto: el primer próximo destacado, el resto en filas y los anteriores por año. */
export function vistaEventos(datos: EventosWeb, textos: TextosTema): VistaEventos {
  const lista = (eventos: EventoListaWeb[]) =>
    eventos.map(vistaEventoLista).filter((e): e is VistaEventoLista => e !== null);
  const proximos = lista(datos.proximos);
  const anteriores = lista(datos.anteriores);

  const anios: VistaEventos['anios'] = [];
  for (const evento of anteriores) {
    const ultimo = anios[anios.length - 1];
    if (ultimo?.anio === evento.fecha.anio) {
      ultimo.eventos.push(evento);
    } else {
      anios.push({ anio: evento.fecha.anio, eventos: [evento] });
    }
  }

  return {
    proximos: proximos.length
      ? {
          destacado: proximos[0],
          resto: proximos.slice(1),
          cuenta:
            proximos.length === 1
              ? textos.t('eventos.un_evento')
              : rellenarHuecos(textos.t('eventos.n_eventos'), { n: proximos.length }),
        }
      : null,
    anios,
    vacia: !proximos.length && !anteriores.length,
  };
}
