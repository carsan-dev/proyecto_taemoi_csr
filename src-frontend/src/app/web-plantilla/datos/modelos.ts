/**
 * Modelos de la web por plantilla (fase4-web-plan.md §3.1): el JSON de los endpoints públicos de W1/W2, campo a campo
 * como los records de Java. Los importes (`BigDecimal`) llegan como número; las fechas, `yyyy-MM-dd`; la hora, `HH:mm`.
 *
 * - `GET /api/publico/web` → {@link SitioWeb} (`WebPublicaDTO`).
 * - `GET /api/publico/web/legal` → {@link Legal} (`LegalWebDTO`).
 * - `GET /api/publico/web/eventos` → {@link EventosWeb} (`EventosWebDTO`).
 * - `GET /api/publico/web/eventos/{id}` → {@link EventoDetalleWeb} (`EventoWebDTO`).
 */

/** Los cuatro temas aprobados (`TemasWeb.TODOS`), en el orden de los bocetos. */
export const TEMAS_WEB = ['linea-comarcal', 'chandal', 'sede-por-pantalla', 'estandar'] as const;
export type TemaWeb = (typeof TEMAS_WEB)[number];

export function esTemaWeb(valor: string | null | undefined): valor is TemaWeb {
  return valor != null && (TEMAS_WEB as readonly string[]).includes(valor);
}

/** `WebPublicaDTO.Escuela`. */
export interface EscuelaWeb {
  nombre: string;
  colorPrimario: string | null;
  /** Id de la imagen del logo (versión para `GET /api/publico/escuela/logo?v=`); null sin logo. */
  logoVersion: number | null;
}

/** `WebPublicaDTO.Bloque`. */
export interface BloqueWeb {
  clave: string;
  texto: string | null;
  /** Absoluta (la API antepone el host); null sin imagen. */
  imagenUrl: string | null;
  orden: number;
  /**
   * W12a: sección oculta, en su fila de cabecera (`presentacion`, `profesores`, `galeria`, `sedes`, `texto_foto.<n>`).
   * Al público solo le llega la cabecera; la vista previa de la gestión recibe también las filas, marcadas.
   */
  oculto?: boolean;
}

/** `PlazasWeb`: solo el estado y, con plazas, cuántas quedan. */
export interface Plazas {
  estado: 'QUEDAN' | 'LLENO' | 'LISTA_ESPERA';
  /** Solo con `QUEDAN`; null en los demás estados. */
  quedan: number | null;
}

/** `WebPublicaDTO.Turno`. */
export interface TurnoWeb {
  id: number;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
  plazas: Plazas;
}

/** `WebPublicaDTO.Grupo`. */
export interface GrupoWeb {
  id: number;
  nombre: string;
  disciplina: string;
  disciplinaNombre: string;
  rangoEdadMin: number | null;
  rangoEdadMax: number | null;
  /** El peor de sus turnos; null sin turnos. */
  plazas: Plazas | null;
  turnos: TurnoWeb[];
}

/** `WebPublicaDTO.Sede`. */
export interface SedeWeb {
  id: number;
  nombre: string;
  direccion: string | null;
  localidad: string | null;
  codigoPostal: string | null;
  telefono: string | null;
  /** Su página, `/sedes/<slug>`; null si no está puesto. */
  slug: string | null;
  lugar: string | null;
  indicacionesAcceso: string | null;
  fotoUrl: string | null;
  foto2Url: string | null;
  /** W12a (d523): descripción de cada foto; null o vacía = la del tema. */
  fotoAlt?: string | null;
  foto2Alt?: string | null;
  grupos: GrupoWeb[];
}

/** `WebPublicaDTO.Tarifa`. */
export interface TarifaWeb {
  id: number;
  codigo: string | null;
  nombre: string;
  /** Código de la disciplina; null si la tarifa no es de una disciplina. */
  disciplina: string | null;
  importe: number | null;
  importeAdulto: number | null;
  importeMenor: number | null;
}

/** `WebPublicaDTO.TarifaSede`. */
export interface TarifaSedeWeb {
  tarifaId: number;
  sedeId: number;
  importe: number | null;
  importeAdulto: number | null;
  importeMenor: number | null;
  /** `yyyy-MM-dd`. */
  vigenteDesde: string | null;
}

/** `WebPublicaDTO.Licencia`. */
export interface LicenciaWeb {
  disciplina: string;
  concepto: string | null;
  precio: number | null;
}

/** `WebPublicaDTO.Evento`. */
export interface EventoWeb {
  id: number;
  titulo: string;
  /** `yyyy-MM-dd`. */
  fecha: string | null;
  /** Si tiene foto (`GET /api/eventos/{id}/imagen`). */
  conImagen: boolean;
  /** `HH:mm`; null sin hora. */
  hora: string | null;
  lugar: string | null;
  sedeId: number | null;
  sedeNombre: string | null;
}

/** `EventosWebDTO.Evento`: los campos de {@link EventoWeb} y la descripción. */
export interface EventoListaWeb extends EventoWeb {
  descripcion: string | null;
}

/** `GET /api/publico/web/eventos` (`EventosWebDTO`): visibles con fecha, como mucho 60 de cada. */
export interface EventosWeb {
  /** De hoy en adelante, del más cercano al más lejano. */
  proximos: EventoListaWeb[];
  /** Anteriores a hoy, del más reciente al más antiguo. */
  anteriores: EventoListaWeb[];
}

/** `EventoWebDTO.Documento`: se descarga por `GET /api/eventos/{id}/documentos/{documentoId}/descargar`. */
export interface DocumentoEventoWeb {
  id: number;
  nombre: string;
  /** Tipo MIME (`application/pdf`…). */
  tipo: string | null;
}

/** `EventoWebDTO.Foto`: se sirve por `GET /api/eventos/{id}/fotos/{fotoId}` (`?w=640`, la miniatura). */
export interface FotoEventoWeb {
  id: number;
}

/** `GET /api/publico/web/eventos/{id}` (`EventoWebDTO`). */
export interface EventoDetalleWeb extends EventoListaWeb {
  documentos: DocumentoEventoWeb[];
  /** Por su orden. */
  fotos: FotoEventoWeb[];
}

/** `WebPublicaDTO.Contacto`. */
export interface ContactoWeb {
  email: string | null;
  telefono: string | null;
}

/** `GET /api/publico/web` (`WebPublicaDTO`). */
export interface SitioWeb {
  escuela: EscuelaWeb;
  /** Tema publicado (en la vista previa de la gestión, el pedido); null en una vista previa sin web ni tema. */
  tema: TemaWeb | null;
  publicada: boolean;
  vistaPrevia: boolean;
  /** «2026-2027». */
  temporada: string | null;
  bloques: BloqueWeb[];
  sedes: SedeWeb[];
  tarifas: TarifaWeb[];
  tarifasSede: TarifaSedeWeb[];
  licencias: LicenciaWeb[];
  /** Menor precio de examen; null sin exámenes. */
  examenDesde: number | null;
  /** Los 3 próximos, del más cercano al más lejano. */
  proximosEventos: EventoWeb[];
  contacto: ContactoWeb;
}

/** `LegalWebDTO.Titular`. */
export interface TitularLegal {
  nombre: string;
  nombreLegal: string | null;
  cif: string | null;
  direccion: string | null;
  localidad: string | null;
  provincia: string | null;
  codigoPostal: string | null;
  email: string | null;
  telefono: string | null;
}

/** `LegalWebDTO.Normas`. */
export interface NormasLegal {
  disciplina: string;
  disciplinaNombre: string;
  version: number | null;
  /** El JSON de la plantilla de preinscripción, como en `/public/configuracion/{deporte}`. */
  contenido: unknown;
  instrucciones: string | null;
}

/** `GET /api/publico/web/legal` (`LegalWebDTO`). */
export interface Legal {
  titular: TitularLegal;
  normas: NormasLegal[];
  /** `yyyy-MM-dd`; null en una vista previa sin publicar. */
  actualizadaEn: string | null;
}

/**
 * Estado de un recurso de la web: la API responde 404 si la escuela no tiene web publicada (`no-publicada`). El 404 de
 * un evento es `no-encontrado` (oculto, de otra escuela o inexistente): si además la web no está publicada, lo dice el
 * estado del sitio.
 */
export type EstadoRecurso<T> =
  | { estado: 'cargando' }
  | { estado: 'listo'; datos: T }
  | { estado: 'no-publicada' }
  | { estado: 'no-encontrado' }
  | { estado: 'error' };
