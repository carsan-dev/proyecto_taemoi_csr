/** Lo que tarda la hoja en terminar de salir antes de avisar (directiva `pnAsaHoja`), con margen. */
export const SALIDA_HOJA_MS = 200;

/**
 * Pruebas (d546): arrastra el asa de una hoja 600 px hacia abajo y la suelta, más que cualquier umbral de cierre. Sin
 * menos movimiento, la hoja avisa pasados `SALIDA_HOJA_MS` (`tick` en `fakeAsync`).
 */
export function deslizarAsa(asa: Element): void {
  const base = { pointerId: 31, isPrimary: true, button: 0, bubbles: true, cancelable: true };
  asa.dispatchEvent(new PointerEvent('pointerdown', { ...base, clientY: 10 }));
  asa.dispatchEvent(new PointerEvent('pointermove', { ...base, clientY: 610 }));
  asa.dispatchEvent(new PointerEvent('pointerup', { ...base, clientY: 610 }));
}
