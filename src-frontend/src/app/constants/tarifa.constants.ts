/**
 * Tarifas estándar por tipo de tarifa.
 * Estos valores coinciden con los definidos en TarifaConfig.java en el backend.
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
  DEFENSA_PERSONAL_FEMENINA: 30.0,
};

/**
 * Obtiene la cuantía estándar para un tipo de tarifa.
 * Considera el rol familiar para PADRES_HIJOS.
 */
export function obtenerCuantiaTarifaEstandar(
  tipoTarifa: string,
  rolFamiliar?: string
): number {
  // Para PADRES_HIJOS, considerar el rol familiar
  if (tipoTarifa === 'PADRES_HIJOS' && rolFamiliar) {
    if (rolFamiliar === 'PADRE' || rolFamiliar === 'MADRE') {
      return 28.0;
    } else if (rolFamiliar === 'HIJO' || rolFamiliar === 'HIJA') {
      return 26.0;
    }
  }

  return TARIFAS_ESTANDAR[tipoTarifa] || 0.0;
}
