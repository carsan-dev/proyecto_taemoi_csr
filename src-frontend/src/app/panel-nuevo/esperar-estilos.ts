/** Ninguna hoja sin eventos debe bloquear la navegación indefinidamente. */
export const TIEMPO_MAXIMO_ESTILOS_MS = 4000;

/** Abre también si la carga falla o supera el tiempo máximo, sin retirar las hojas pendientes. */
export function esperarEstilos(carga: Promise<void>): Promise<boolean> {
  return new Promise<boolean>((resolver) => {
    const temporizador = setTimeout(() => resolver(true), TIEMPO_MAXIMO_ESTILOS_MS);
    const terminar = () => {
      clearTimeout(temporizador);
      resolver(true);
    };
    carga.then(terminar, terminar);
  });
}
