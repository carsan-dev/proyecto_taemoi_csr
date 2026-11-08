import { GrupoWeb, SedeWeb, TurnoWeb } from '../../datos/modelos';
import { Salida, estadoPlazas, numeroDia, salidasDeSede } from '../horario';

/**
 * Lógica sin estilo de la preinscripción de la web por plantilla (fase4-web-plan.md §3.1 «comun/» y §6
 * «Preinscripción»): datos del formulario, validación de los bocetos (`boceto-<tema>-preinscripcion.html`), elección de
 * sede, grupo y días, y la solicitud de `POST /api/preinscripciones`. La comparten todos los temas; cada uno pinta.
 *
 * La solicitud tiene la misma forma que la del formulario público de Moiskimdo (`PreinscripcionComponent`: el
 * `getRawValue()` de su formulario más `firmaBase64`) y los mismos patrones de DNI/NIE y teléfono
 * (`PreinscripcionRequest.java`). La firma es dibujada y obligatoria, como en Moiskimdo (decisión 493).
 */

/** Lo que se escribe en el paso de datos (y la fecha del paso de grupo). Es el borrador de `sessionStorage`. */
export interface DatosPreinscripcion {
  nombre: string;
  apellidos: string;
  /** Fecha de nacimiento en tres campos, tal como se escriben. */
  dia: string;
  mes: string;
  anio: string;
  dni: string;
  direccion: string;
  discapacidad: '' | 'si' | 'no';
  tutorNombre: string;
  tutorDni: string;
  telefono: string;
  telefono2: string;
  email: string;
  observaciones: string;
  normas: boolean;
  fotos: boolean;
  firmante: string;
}

export const DATOS_VACIOS: Readonly<DatosPreinscripcion> = {
  nombre: '',
  apellidos: '',
  dia: '',
  mes: '',
  anio: '',
  dni: '',
  direccion: '',
  discapacidad: '',
  tutorNombre: '',
  tutorDni: '',
  telefono: '',
  telefono2: '',
  email: '',
  observaciones: '',
  normas: false,
  fotos: false,
  firmante: '',
};

/** Un error de un campo: `id` del control que recibe el foco, `campo` del hueco del mensaje (`<campo>-error`). */
export interface ErrorCampo {
  id: string;
  campo: string;
  msg: string;
}

/** Los de Moiskimdo y `PreinscripcionRequest.java`. */
export const PATRON_DNI = /^[0-9XYZ][0-9]{7}[A-Z]$/i;
export const PATRON_TELEFONO = /^(?:(?:\+|00)34[ ]*)?[6789](?:[ ]*\d){8}$/;
const PATRON_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const MAX_OBSERVACIONES = 1000;

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Mensajes de los bocetos que no son de un campo del paso de datos. */
export const MENSAJES = {
  elegirSede: 'Elige el pueblo donde quieres entrenar.',
  elegirGrupo: 'Elige un grupo para seguir.',
  marcarDia: 'Marca al menos un día. Si no puede venir ninguno, elige otro grupo.',
  /** El de Moiskimdo, sin normas cargadas. */
  sinNormas: 'No puedes enviar la solicitud hasta que se carguen las normas.',
  /** El de Moiskimdo, si la API no da un mensaje. */
  envioFallido: 'No se pudo enviar la preinscripción.',
} as const;

const texto = (valor: unknown): string => String(valor ?? '').trim();

// ---------------------------------------------------------------------------------------------------------------
// Fecha de nacimiento y edad
// ---------------------------------------------------------------------------------------------------------------

/** El primer problema de la fecha de nacimiento (mensajes del boceto); null si es válida. */
export function errorFecha(d: Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio'>, ahora: Date): ErrorCampo | null {
  const dia = texto(d.dia);
  const mes = texto(d.mes);
  const anio = texto(d.anio);
  const error = (id: string, msg: string): ErrorCampo => ({ id, campo: 'fecha', msg });
  if (!dia && !mes && !anio) {
    return error('dia', 'Escribe la fecha de nacimiento del alumno.');
  }
  if (!dia) {
    return error('dia', 'Falta el día de la fecha de nacimiento.');
  }
  if (!mes) {
    return error('mes', 'Falta el mes de la fecha de nacimiento.');
  }
  if (!anio) {
    return error('anio', 'Falta el año de la fecha de nacimiento.');
  }
  if (!/^\d+$/.test(dia + mes + anio)) {
    return error('dia', 'Escribe la fecha solo con números, por ejemplo 14 3 2016.');
  }
  if (anio.length !== 4) {
    return error('anio', 'Escribe el año con cuatro cifras, por ejemplo 2016.');
  }
  if (+mes < 1 || +mes > 12) {
    return error('mes', 'El mes tiene que ir del 1 al 12.');
  }
  const tope = new Date(+anio, +mes, 0).getDate();
  if (+dia < 1 || +dia > tope) {
    return error('dia', `Esa fecha no existe: ${MESES[+mes - 1]} de ${anio} tiene ${tope} días.`);
  }
  if (new Date(+anio, +mes - 1, +dia) >= ahora) {
    return error('dia', 'La fecha de nacimiento tiene que ser anterior a hoy.');
  }
  if (+anio < 1900) {
    return error('anio', `Revisa el año: ${anio} no parece un año de nacimiento.`);
  }
  return null;
}

export function fechaNacimiento(d: Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio'>, ahora: Date): Date | null {
  return errorFecha(d, ahora) ? null : new Date(+texto(d.anio), +texto(d.mes) - 1, +texto(d.dia));
}

/** `yyyy-MM-dd` de la fecha escrita (la de `fechaNacimiento` de la solicitud). */
export function fechaIso(d: Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio'>): string {
  return `${texto(d.anio)}-${texto(d.mes).padStart(2, '0')}-${texto(d.dia).padStart(2, '0')}`;
}

/** «2026-2027» → 2026; null si no tiene esa forma. */
export function inicioTemporada(temporada: string | null | undefined): number | null {
  const partes = /^(\d{4})-\d{4}$/.exec(temporada?.trim() ?? '');
  return partes ? Number(partes[1]) : null;
}

/** La edad de cohorte, como Moiskimdo y la API: los años que cumple en el año en que empieza la temporada. */
export function edadTemporada(
  d: Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio'>,
  inicio: number | null,
  ahora: Date,
): number | null {
  const fecha = fechaNacimiento(d, ahora);
  return fecha && inicio != null ? inicio - fecha.getFullYear() : null;
}

/** Menor de edad hoy (pide responsable legal; el DNI/NIE del alumno pasa a opcional), como Moiskimdo. */
export function esMenor(d: Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio'>, ahora: Date): boolean {
  const fecha = fechaNacimiento(d, ahora);
  if (!fecha) {
    return false;
  }
  let edad = ahora.getFullYear() - fecha.getFullYear();
  if (ahora < new Date(ahora.getFullYear(), fecha.getMonth(), fecha.getDate())) {
    edad--;
  }
  return edad < 18;
}

/** Si el grupo es de esa edad (sin mínimo, 0; sin máximo, 99, como la API). */
export function encajaEdad(grupo: Pick<GrupoWeb, 'rangoEdadMin' | 'rangoEdadMax'>, edad: number | null): boolean {
  return edad !== null && edad >= (grupo.rangoEdadMin ?? 0) && edad <= (grupo.rangoEdadMax ?? 99);
}

// ---------------------------------------------------------------------------------------------------------------
// Validación del paso de datos
// ---------------------------------------------------------------------------------------------------------------

/**
 * Los errores del paso de datos, en el orden del formulario y con los mensajes del boceto. La firma es dibujada y
 * obligatoria (decisión 493); sin normas cargadas no se puede enviar (como Moiskimdo).
 */
export function validarDatos(
  d: DatosPreinscripcion,
  opciones: { ahora: Date; firmado: boolean; normasListas: boolean },
): ErrorCampo[] {
  const e: ErrorCampo[] = [];
  const add = (id: string, msg: string, campo = id) => e.push({ id, campo, msg });
  const menor = esMenor(d, opciones.ahora);
  if (!texto(d.nombre)) {
    add('nombre', 'Escribe el nombre del alumno.');
  }
  if (!texto(d.apellidos)) {
    add('apellidos', 'Escribe los apellidos del alumno.');
  }
  const fecha = errorFecha(d, opciones.ahora);
  if (fecha) {
    e.push(fecha);
  }
  const dni = texto(d.dni);
  if (dni ? !PATRON_DNI.test(dni) : !menor) {
    add('dni', menor ? 'Si lo indicas, debe tener un formato válido.' : 'El DNI/NIE es obligatorio y debe tener un formato válido.');
  }
  if (!texto(d.direccion)) {
    add('direccion', 'Escribe la dirección.');
  }
  if (d.discapacidad !== 'si' && d.discapacidad !== 'no') {
    add('discapacidad', 'Indica si el alumno tiene discapacidad.');
  }
  if (menor && !texto(d.tutorNombre)) {
    add('tutorNombre', 'Escribe el nombre y apellidos del responsable legal.');
  }
  if (menor && !PATRON_DNI.test(texto(d.tutorDni))) {
    add('tutorDni', 'Introduce un DNI/NIE válido del responsable legal.');
  }
  if (!PATRON_TELEFONO.test(texto(d.telefono))) {
    add('telefono', 'Introduce un teléfono de nueve dígitos; puedes añadir el prefijo +34.');
  }
  if (texto(d.telefono2) && !PATRON_TELEFONO.test(texto(d.telefono2))) {
    add('telefono2', 'Introduce un teléfono secundario de nueve dígitos, o déjalo vacío.');
  }
  if (!PATRON_EMAIL.test(texto(d.email))) {
    add('email', 'Escribe un email válido, como nombre@ejemplo.es.');
  }
  if (texto(d.observaciones).length > MAX_OBSERVACIONES) {
    add('observaciones', 'La observación no puede superar los 1.000 caracteres.');
  }
  if (!opciones.normasListas) {
    add('normas', MENSAJES.sinNormas);
  } else if (!d.normas) {
    add('normas', 'Marca que aceptas las normas de la actividad.');
  }
  if (!texto(d.firmante)) {
    add('firmante', 'Escribe el nombre y apellidos de quien firma.');
  }
  if (!opciones.firmado) {
    add('firma', 'Firma en el recuadro.');
  }
  return e;
}

// ---------------------------------------------------------------------------------------------------------------
// Sede, grupo y días
// ---------------------------------------------------------------------------------------------------------------

/** La sede de `?sede=`: por slug o por id (los enlaces del tema mandan el slug, o el id si no tiene). */
export function sedeDeParametro(sedes: readonly SedeWeb[], valor: string | null | undefined): SedeWeb | null {
  const v = valor?.trim();
  if (!v) {
    return null;
  }
  return sedes.find((s) => s.slug === v) ?? sedes.find((s) => s.slug == null && String(s.id) === v) ?? sedes.find((s) => String(s.id) === v) ?? null;
}

/** Las salidas de la sede (un grupo a una hora, con sus días) de esa edad, por hora. */
export function salidasParaEdad(sede: SedeWeb, edad: number | null): Salida[] {
  return salidasDeSede(sede).filter((s) => encajaEdad(s.grupo, edad));
}

/**
 * La salida elegida con `?grupo=&dias=`: la del grupo que tiene todos esos días; sin días (o si ninguna los tiene), la
 * primera del grupo. Null si el grupo no es de la sede.
 */
export function salidaDeParametros(sede: SedeWeb, grupo: string | null | undefined, dias: readonly number[]): Salida | null {
  const id = Number(grupo);
  if (!grupo || !Number.isSafeInteger(id)) {
    return null;
  }
  const delGrupo = salidasDeSede(sede).filter((s) => s.grupo.id === id);
  return delGrupo.find((s) => dias.length > 0 && dias.every((d) => s.dias.includes(d))) ?? delGrupo[0] ?? null;
}

/** «1,3» → [1, 3]; solo días de la semana (0 domingo … 6 sábado), sin repetir. */
export function diasDeParametro(valor: string | null | undefined): number[] {
  const dias = (valor ?? '')
    .split(',')
    .map((x) => x.trim())
    .filter((x) => /^[0-6]$/.test(x))
    .map(Number);
  return [...new Set(dias)];
}

/** El turno de la salida en ese día; null si no entrena ese día. */
export function turnoDelDia(salida: Salida, dia: number): TurnoWeb | null {
  return salida.turnos.find((t) => numeroDia(t.diaSemana) === dia) ?? null;
}

/** Plazas por día (§4.3): si algún día de la salida tiene plaza libre. */
export function algunDiaConPlaza(salida: Salida): boolean {
  return salida.dias.some((d) => estadoPlazas(turnoDelDia(salida, d)?.plazas) === 'quedan');
}

/**
 * Los días que se pueden elegir: con algún día con plaza, solo esos (un día lleno no se mezcla con uno con plaza: la
 * API daría la plaza a toda la solicitud); sin ninguno, todos, para apuntarse a la lista de espera (boceto).
 */
export function diasElegibles(salida: Salida): number[] {
  return algunDiaConPlaza(salida)
    ? salida.dias.filter((d) => estadoPlazas(turnoDelDia(salida, d)?.plazas) === 'quedan')
    : [...salida.dias];
}

/** Los días elegidos de la salida, en su orden y entre los que se pueden elegir; sin ninguno válido, todos esos. */
export function diasElegidos(salida: Salida, pedidos: readonly number[]): number[] {
  const elegibles = diasElegibles(salida);
  const validos = elegibles.filter((d) => pedidos.includes(d));
  return validos.length ? validos : elegibles;
}

/** Un turno por día elegido (la API no admite dos el mismo día). */
export function turnosElegidos(salida: Salida, dias: readonly number[]): TurnoWeb[] {
  return dias.map((dia) => turnoDelDia(salida, dia)).filter((t): t is TurnoWeb => !!t);
}

/** Sin plaza en ninguno de los turnos elegidos: la API la manda a la lista de espera (`algunaPlaza`). */
export function sinPlaza(turnos: readonly TurnoWeb[]): boolean {
  return turnos.length > 0 && !turnos.some((t) => estadoPlazas(t.plazas) === 'quedan');
}

/** Las plazas libres que quedan en los turnos elegidos con plaza (la menor); null sin ninguno con plaza. */
export function plazasLibres(turnos: readonly TurnoWeb[]): number | null {
  const libres = turnos.filter((t) => estadoPlazas(t.plazas) === 'quedan').map((t) => t.plazas.quedan ?? 0);
  return libres.length ? Math.min(...libres) : null;
}

// ---------------------------------------------------------------------------------------------------------------
// Solicitud
// ---------------------------------------------------------------------------------------------------------------

/**
 * `POST /api/preinscripciones` (`PreinscripcionRequest.java`): los campos del formulario de Moiskimdo
 * (`getRawValue()`) y `firmaBase64`, con los mismos nombres y tipos.
 */
export interface SolicitudPreinscripcion {
  deporte: string;
  turnoIds: number[];
  nombre: string;
  apellidos: string;
  dni: string;
  fechaNacimiento: string;
  direccion: string;
  telefono: string;
  telefono2: string;
  email: string;
  observaciones: string;
  tutorNombre: string;
  tutorDni: string;
  tieneDiscapacidad: boolean | null;
  consentimientoFotografico: boolean;
  aceptacionNormas: boolean;
  firmanteNombre: string;
  firmaBase64: string;
}

/** Respuesta 201 de `POST /api/preinscripciones`. */
export interface RespuestaPreinscripcion {
  referencia: string;
  temporada: string;
  tokenDocumento: string;
  /** En el alta siempre `false`: el correo sale tras guardar (R2-P7d). */
  emailEnviado: boolean;
  estado: string;
  plazaAsignada: boolean;
  posicionListaEspera: number | null;
  mensaje: string;
}

/**
 * La solicitud con los datos, el deporte del grupo, los turnos elegidos y la firma (`data:image/png;base64,…`, como
 * Moiskimdo). Un adulto no manda responsable legal (los campos van vacíos, como en Moiskimdo cuando no se rellenan).
 */
export function solicitudDe(
  d: DatosPreinscripcion,
  seleccion: { deporte: string; turnoIds: number[]; firmaBase64: string; ahora: Date },
): SolicitudPreinscripcion {
  const menor = esMenor(d, seleccion.ahora);
  return {
    deporte: seleccion.deporte,
    turnoIds: [...seleccion.turnoIds],
    nombre: texto(d.nombre),
    apellidos: texto(d.apellidos),
    dni: texto(d.dni),
    fechaNacimiento: fechaIso(d),
    direccion: texto(d.direccion),
    telefono: texto(d.telefono),
    telefono2: texto(d.telefono2),
    email: texto(d.email),
    observaciones: texto(d.observaciones),
    tutorNombre: menor ? texto(d.tutorNombre) : '',
    tutorDni: menor ? texto(d.tutorDni) : '',
    tieneDiscapacidad: d.discapacidad === 'si' ? true : d.discapacidad === 'no' ? false : null,
    consentimientoFotografico: d.fotos === true,
    aceptacionNormas: d.normas === true,
    firmanteNombre: texto(d.firmante),
    firmaBase64: seleccion.firmaBase64,
  };
}

/** Las normas de la plantilla de preinscripción (`contenido`, objeto o JSON en texto), como las lee Moiskimdo. */
export function normasDeContenido(contenido: unknown): { normas: string[]; importes: string | null } {
  let valor = contenido;
  if (typeof valor === 'string') {
    try {
      valor = JSON.parse(valor);
    } catch {
      return { normas: [], importes: null };
    }
  }
  const objeto = (valor ?? {}) as { normas?: unknown; importes?: unknown };
  const normas = Array.isArray(objeto.normas)
    ? objeto.normas.filter((n): n is string => typeof n === 'string' && n.trim().length > 0)
    : [];
  const importes = typeof objeto.importes === 'string' && objeto.importes.trim() ? objeto.importes.trim() : null;
  return { normas, importes };
}
