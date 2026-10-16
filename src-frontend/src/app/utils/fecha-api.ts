import { Pipe, PipeTransform } from '@angular/core';

/**
 * Por debajo de este valor un número es una marca en SEGUNDOS (hasta el año 5138); por encima, en milisegundos
 * (desde marzo de 1973). Sin solape en fechas reales.
 */
const LIMITE_SEGUNDOS = 1e11;
const NUMERO = /^-?\d+(?:\.\d+)?$/;

/**
 * Normaliza una fecha tal como llega de la API a `Date` (o `null` si no es reconocible).
 *
 * Con `@EnableWebMvc` (WebConfig) Spring MVC no usa el ObjectMapper de Boot y serializa con fechas como marca
 * de tiempo: los `Instant` llegan como número en segundos con decimales (1760000000.123456789), los
 * `java.util.Date` en milisegundos y las `LocalDate` como [año, mes, día]. El pipe `date` de Angular toma
 * cualquier número como milisegundos, así que un Instant sin normalizar sale en enero de 1970.
 * También acepta cadenas ISO por si la API cambia a ese formato.
 * Solo para Instant (segundos): un java.util.Date en ms entre 1966 y 1973 caería bajo el umbral y se leería como
 * segundos; para fechas de calendario usa {@link diaApi}.
 */
export function fechaApi(valor: unknown): Date | null {
  if (valor == null || valor === '') return null;
  if (valor instanceof Date) return Number.isNaN(valor.getTime()) ? null : valor;
  if (Array.isArray(valor)) {
    const [anio, mes = 1, dia = 1, hora = 0, minuto = 0, segundo = 0, nanos = 0] = valor.map(Number);
    if (!Number.isInteger(anio) || [mes, dia, hora, minuto, segundo, nanos].some((n) => !Number.isFinite(n))) return null;
    return new Date(anio, mes - 1, dia, hora, minuto, segundo, Math.floor(nanos / 1e6));
  }
  if (typeof valor === 'number' || (typeof valor === 'string' && NUMERO.test(valor.trim()))) {
    const numero = Number(valor);
    if (!Number.isFinite(numero)) return null;
    return new Date(Math.abs(numero) < LIMITE_SEGUNDOS ? Math.round(numero * 1000) : numero);
  }
  if (typeof valor === 'string') {
    const fecha = new Date(valor);
    return Number.isNaN(fecha.getTime()) ? null : fecha;
  }
  return null;
}

const DIA_MS = 86_400_000;
/** 'yyyy-MM-dd' solo o seguido de una hora SIN zona (LocalDate / LocalDateTime): el día es el escrito. */
const DIA_SIN_ZONA = /^(\d{4}-\d{2}-\d{2})(?:[T ]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)?$/;

/**
 * Fecha sin hora ni zona ('yyyy-MM-dd') para comparar fechas de calendario (p. ej. de nacimiento) que llegan en
 * formatos distintos: LocalDate como [año, mes, día], 'yyyy-MM-dd' o una fecha-hora sin zona dan su día tal cual
 * (sin pasar por el huso del navegador); un `Date` da su día local; una marca de tiempo (java.util.Date de una
 * columna DATE = medianoche del huso del servidor, UTC o Europe/Madrid) o una fecha-hora con zona se redondean a la
 * medianoche UTC más próxima, así el día no depende del huso del servidor ni del navegador (vale hasta ±12 h).
 */
export function diaApi(valor: unknown): string | null {
  const sinZona = typeof valor === 'string' ? DIA_SIN_ZONA.exec(valor.trim()) : null;
  if (sinZona) return sinZona[1];
  if (Array.isArray(valor) || valor instanceof Date) {
    const fecha = fechaApi(valor);
    return fecha ? diaLocal(fecha) : null;
  }
  // Un número es SIEMPRE milisegundos aquí (java.util.Date): el umbral de fechaApi leería como segundos
  // las fechas entre 1966 y 1973.
  if (typeof valor === 'number' || (typeof valor === 'string' && NUMERO.test(valor.trim()))) {
    const ms = Number(valor);
    return Number.isFinite(ms) ? new Date(Math.round(ms / DIA_MS) * DIA_MS).toISOString().slice(0, 10) : null;
  }
  const fecha = fechaApi(valor);
  if (!fecha) return null;
  return new Date(Math.round(fecha.getTime() / DIA_MS) * DIA_MS).toISOString().slice(0, 10);
}

function diaLocal(fecha: Date): string {
  return [fecha.getFullYear(), fecha.getMonth() + 1, fecha.getDate()]
    .map((n, i) => String(n).padStart(i ? 2 : 4, '0')).join('-');
}

/** `{{ valor | fechaApi | date:'medium' }}`: ver {@link fechaApi}. */
@Pipe({ name: 'fechaApi', standalone: true })
export class FechaApiPipe implements PipeTransform {
  transform(valor: unknown): Date | null {
    return fechaApi(valor);
  }
}
