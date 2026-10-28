/**
 * Importe de un cargo (decisión 297): la misma regla que `ImporteCargo` de la API. El precio nulo cuenta como 0 y la
 * cantidad nula o menor que 1 como 1; el importe es precio × cantidad, sin redondear (se formatea al mostrar).
 */
export function importeCargo(precio: number | null | undefined, cantidad: number | null | undefined): number {
  const unitario = precio ?? 0;
  const unidades = cantidad == null || cantidad < 1 ? 1 : cantidad;
  return unitario * unidades;
}
