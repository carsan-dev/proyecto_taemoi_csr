import { SedeWeb, SitioWeb, TarifaWeb } from '../datos/modelos';

/**
 * Tarifas por sede de la web por plantilla (fase4-web-plan.md §9 n.º 6 y §10 n.º 8), sin estilo: la API da, por tarifa
 * activa y sede activa, la vigente hoy y las futuras (`TarifaSede.vigenteDesde`). Lo comparten todos los temas.
 */

export interface TarifaEnSede {
  sede: SedeWeb;
  /** Lo que se paga hoy en la sede: el importe propio vigente o, sin él, el de la tarifa. */
  importe: number;
  /** Si hoy la sede paga distinto de la tarifa general. */
  propia: boolean;
  /** El próximo cambio anunciado (fecha posterior a hoy con otro importe); null si no hay. */
  cambio: { importe: number; desde: string } | null;
}

/** `yyyy-MM-dd` de una fecha local. */
export function fechaIso(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

/**
 * La tarifa `tarifa` en cada sede del sitio, en el orden de las sedes. Vigente hoy: la de fecha más reciente que no
 * pasa de hoy (sin fecha cuenta como la más antigua). Cambio: la primera futura con un importe distinto del de hoy.
 */
export function tarifaPorSede(sitio: SitioWeb, tarifa: TarifaWeb, hoy: Date): TarifaEnSede[] {
  const hoyIso = fechaIso(hoy);
  const general = tarifa.importe;
  return sitio.sedes.map((sede) => {
    const propias = sitio.tarifasSede.filter((ts) => ts.tarifaId === tarifa.id && ts.sedeId === sede.id && ts.importe != null);
    const vigente = propias
      .filter((ts) => ts.vigenteDesde == null || ts.vigenteDesde <= hoyIso)
      .sort((a, b) => (a.vigenteDesde ?? '').localeCompare(b.vigenteDesde ?? ''))
      .pop();
    const importe = vigente?.importe ?? general ?? 0;
    const futura = propias
      .filter((ts) => ts.vigenteDesde != null && ts.vigenteDesde > hoyIso && ts.importe !== importe)
      .sort((a, b) => a.vigenteDesde!.localeCompare(b.vigenteDesde!))[0];
    return {
      sede,
      importe,
      propia: general != null && importe !== general,
      cambio: futura ? { importe: futura.importe!, desde: futura.vigenteDesde! } : null,
    };
  });
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** `2027-01-01` → «enero de 2027». */
export function mesYAnio(iso: string): string {
  const [anio, mes] = iso.split('-').map(Number);
  return `${MESES[mes - 1] ?? ''} de ${anio}`;
}
