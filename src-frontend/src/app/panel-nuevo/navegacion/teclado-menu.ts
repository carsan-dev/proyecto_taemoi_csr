/**
 * Teclado de los menús del panel nuevo (patrón «menu button» de WAI-ARIA, WCAG 2.2 AA, decisión 243): flechas arriba y
 * abajo (con vuelta), Inicio y Fin; Esc cierra y devuelve el foco al botón; Tab cierra y deja seguir.
 */

function opciones(menu: HTMLElement): HTMLElement[] {
  return Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]')).filter(
    (o) => !(o as HTMLButtonElement).disabled
  );
}

export function enfocarOpcion(menu: HTMLElement | null, cual: 'primera' | 'ultima'): void {
  if (!menu) {
    return;
  }
  const lista = opciones(menu);
  (cual === 'primera' ? lista[0] : lista[lista.length - 1])?.focus();
}

/** Mueve el foco dentro del menú; devuelve `cerrar` (Esc) o `salir` (Tab) para que el menú se cierre. */
export function teclaEnMenu(evento: KeyboardEvent, menu: HTMLElement): 'cerrar' | 'salir' | null {
  const lista = opciones(menu);
  if (lista.length === 0) {
    return evento.key === 'Escape' ? 'cerrar' : null;
  }
  const actual = lista.indexOf(menu.ownerDocument.activeElement as HTMLElement);
  let siguiente: number | null = null;
  switch (evento.key) {
    case 'ArrowDown':
      siguiente = actual < 0 ? 0 : (actual + 1) % lista.length;
      break;
    case 'ArrowUp':
      siguiente = actual < 0 ? lista.length - 1 : (actual - 1 + lista.length) % lista.length;
      break;
    case 'Home':
      siguiente = 0;
      break;
    case 'End':
      siguiente = lista.length - 1;
      break;
    case 'Escape':
      evento.preventDefault();
      evento.stopPropagation();
      return 'cerrar';
    case 'Tab':
      return 'salir';
    default:
      return null;
  }
  evento.preventDefault();
  lista[siguiente].focus();
  return null;
}
