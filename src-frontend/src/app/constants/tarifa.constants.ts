import { catalogoActual } from '../features/catalogo/catalogo-actual';
import { cuantiaEstandar } from '../features/catalogo/catalogo-reglas';

/**
 * Tarifas estándar por tipo de tarifa (Taemoi). Ya no la lee ninguna función de este archivo.
 * @deprecated Los importes son `tarifa.importe*` del catálogo. Se borra en la rebanada 6 (tarifas) / 8 (limpieza).
 */
export const TARIFAS_ESTANDAR: { [key: string]: number } = {
  FAMILIAR: 0.0,
  PADRES_HIJOS: 0.0, // Varia segun rol (PADRE/MADRE: 28.0, HIJO/HIJA: 26.0)
  ADULTO_GRUPO: 20.0,
  INFANTIL_GRUPO: 20.0,
  HERMANOS: 26.0,
  INFANTIL: 28.0,
  ADULTO: 30.0,
  KICKBOXING: 30.0,
  PILATES: 30.0,
  DEFENSA_PERSONAL_FEMENINA: 25.0,
};

/**
 * Cuantía estándar de una tarifa según el rol familiar, leída del catálogo cargado (`cuantiaEstandar`).
 * Tarifa desconocida o catálogo sin cargar (también para quien no es gestor: el catálogo no trae tarifas): 0.
 */
export function obtenerCuantiaTarifaEstandar(tipoTarifa: string, rolFamiliar?: string): number {
  const tarifa = tipoTarifa ? catalogoActual()?.tarifas.find((t) => t.codigo === tipoTarifa) : undefined;
  return cuantiaEstandar(tarifa, rolFamiliar);
}
