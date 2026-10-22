import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';

// Solo para las pantallas del panel nuevo: el LOCALE_ID global no se toca hasta 8b-Z.
registerLocaleData(localeEs, 'es-ES');

const LOCALE = 'es-ES';

// Intl no agrupa los miles de cuatro cifras en es-ES (1044) salvo con useGrouping 'always', que necesita
// Firefox 116 (el proyecto admite Firefox 104). Se formatea sin agrupar y los miles los pone agruparMiles.
const formatoEur = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: 'EUR', useGrouping: false });

const formatoNumero = new Intl.NumberFormat(LOCALE, { useGrouping: false });

const formatoFecha = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', year: 'numeric' });

const formatoMes = new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric' });

const formatoFechaLarga = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** «1044» pasa a «1.044»; conserva el signo menos. Solo para enteros en cadena de dígitos. */
export function agruparMiles(entero: string): string {
  const negativo = entero.startsWith('-');
  const digitos = negativo ? entero.slice(1) : entero;
  return (negativo ? '-' : '') + digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** Formatea con Intl sin agrupar, agrupa los miles a mano y quita el signo de un cero («-0,00»). */
function formatear(formato: Intl.NumberFormat, valor: number): string {
  const partes = formato.formatToParts(valor);
  const esCero = partes
    .filter((parte) => parte.type === 'integer' || parte.type === 'fraction')
    .every((parte) => /^0+$/.test(parte.value));
  return partes
    .map((parte) => {
      if (parte.type === 'integer') {
        return agruparMiles(parte.value);
      }
      return parte.type === 'minusSign' && esCero ? '' : parte.value;
    })
    .join('');
}

function esNumero(valor: number | null | undefined): valor is number {
  return typeof valor === 'number' && Number.isFinite(valor);
}

/** Las fechas ISO sin hora («2026-10-01») se leen como día local para no desplazarlas por la zona horaria. */
function aFecha(valor: Date | string | null | undefined): Date | null {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  let fecha: Date;
  if (valor instanceof Date) {
    fecha = valor;
  } else {
    const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
    fecha = iso ? new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3])) : new Date(valor);
  }
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/** «1.010,00 €» con espacio duro antes del símbolo. Vacío si no hay número. */
export function eur(valor: number | null | undefined): string {
  return esNumero(valor) ? formatear(formatoEur, valor) : '';
}

/** «1.044»: agrupa los miles también con cuatro cifras. */
export function numero(valor: number | null | undefined): string {
  return esNumero(valor) ? formatear(formatoNumero, valor) : '';
}

/** «05/03/2026». Vacío si no hay fecha o no es válida. */
export function fecha(valor: Date | string | null | undefined): string {
  const f = aFecha(valor);
  return f ? formatoFecha.format(f) : '';
}

/** «octubre de 2026». Vacío si no hay fecha o no es válida. */
export function mes(valor: Date | string | null | undefined): string {
  const f = aFecha(valor);
  return f ? formatoMes.format(f) : '';
}

/** «Miércoles, 21 de octubre de 2026». Vacío si no hay fecha o no es válida. */
export function fechaLarga(valor: Date | string | null | undefined): string {
  const f = aFecha(valor);
  if (!f) {
    return '';
  }
  const texto = formatoFechaLarga.format(f);
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** «1 clase», «7 clases»: el singular solo con 1. Vacío si la cantidad no es un número finito. */
export function plural(cantidad: number, singular: string, pluralTexto: string): string {
  if (!esNumero(cantidad)) {
    return '';
  }
  return `${numero(cantidad)} ${cantidad === 1 ? singular : pluralTexto}`;
}

/**
 * Una frase entera que concuerda con la cantidad («Se genera 1 PDF.» / «Se generan 2 PDF, uno por día.»): el singular
 * solo con 1 y `{n}` con la cifra es-ES. Vacío si la cantidad no es un número finito.
 */
export function frasePlural(cantidad: number, singular: string, pluralTexto: string): string {
  if (!esNumero(cantidad)) {
    return '';
  }
  return (cantidad === 1 ? singular : pluralTexto).replace('{n}', numero(cantidad));
}
