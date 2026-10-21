import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';

// Solo para las pantallas del panel nuevo: el LOCALE_ID global no se toca hasta 8b-Z.
registerLocaleData(localeEs, 'es-ES');

const LOCALE = 'es-ES';

// 'always' agrupa también los miles de cuatro cifras (1.044); la librería de tipos de TS aún lo declara boolean.
const AGRUPAR_SIEMPRE = 'always' as unknown as boolean;

const formatoEur = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: 'EUR',
  useGrouping: AGRUPAR_SIEMPRE,
});

const formatoNumero = new Intl.NumberFormat(LOCALE, { useGrouping: AGRUPAR_SIEMPRE });

const formatoFecha = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: '2-digit', year: 'numeric' });

const formatoMes = new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric' });

/** Las fechas ISO sin hora («2026-10-01») se leen como día local para no desplazarlas por la zona horaria. */
function aFecha(valor: Date | string): Date {
  if (valor instanceof Date) {
    return valor;
  }
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
  return iso ? new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3])) : new Date(valor);
}

/** «1.010,00 €» con espacio duro antes del símbolo. Vacío si no hay número. */
export function eur(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return '';
  }
  return formatoEur.format(valor);
}

/** «1.044»: agrupa los miles también con cuatro cifras. */
export function numero(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return '';
  }
  return formatoNumero.format(valor);
}

/** «05/03/2026». */
export function fecha(valor: Date | string): string {
  return formatoFecha.format(aFecha(valor));
}

/** «octubre de 2026». */
export function mes(valor: Date | string): string {
  return formatoMes.format(aFecha(valor));
}

/** «1 clase», «7 clases»: el singular solo con 1. */
export function plural(cantidad: number, singular: string, pluralTexto: string): string {
  return `${numero(cantidad)} ${cantidad === 1 ? singular : pluralTexto}`;
}
