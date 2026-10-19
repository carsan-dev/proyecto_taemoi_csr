/**
 * Indica si una opción de un `<select>` es un marcador («Seleccione un producto»,
 * «Elige un alumno», etc.) y no un valor elegido de verdad. Las opciones «Todos…»
 * con valor real (p. ej. «TODOS») no son marcador.
 *
 * Es marcador la opción cuyo valor es '', 'null' o el '0: null' / '1: undefined' que
 * genera Angular con [ngValue]. Un marcador no se escribe en el buscador como valor
 * (el campo queda vacío y su texto pasa a ser el placeholder) y se oculta al filtrar.
 *
 * Excepción: una opción con el atributo `data-opcion-real` nunca es marcador, aunque su
 * valor esté vacío. Sirve para opciones con significado propio, como «Sin grupo asignado»,
 * que sí deben mostrarse como valor elegido.
 */
export function esOpcionMarcador(option: HTMLOptionElement): boolean {
  if (option.hasAttribute('data-opcion-real')) {
    return false;
  }
  const value = option.value;
  return value === '' || value === 'null' || /^\d+:\s*(null|undefined)$/.test(value);
}
