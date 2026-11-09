/**
 * Ajustes del índice (P2, desde 768 px). «Roles y permisos» salió en la pieza 9-8: los roles se gestionan en Miembros.
 * «Rallas hacia el examen» (decisiones 510-513) solo se ve si el API la da.
 */
export type SeccionConfiguracion = 'turno' | 'rallas' | 'legal';
export const SECCIONES: ReadonlyArray<{ clave: SeccionConfiguracion; titulo: string }> = [
  { clave: 'turno', titulo: 'Alumnos por turno' },
  { clave: 'rallas', titulo: 'Rallas hacia el examen' },
  { clave: 'legal', titulo: 'Avisos legales y cookies' },
];

/**
 * El nuevo límite escrito en el campo. Hoy el aviso salta con un valor menor que 1; aquí, además, sin número no se
 * puede guardar (hoy, un campo vacío daba el mismo aviso) y el límite es un número entero, como lo guarda la API.
 */
export function validarLimite(texto: string): { valor: number | null; error: string | null } {
  const limpio = (texto ?? '').trim();
  if (limpio === '') {
    return { valor: null, error: 'Escribe cuántos alumnos caben en un turno.' };
  }
  if (!/^-?\d+$/.test(limpio)) {
    return { valor: null, error: 'Escribe un número entero, por ejemplo 36.' };
  }
  const valor = Number(limpio);
  if (valor < 1) {
    return { valor: null, error: 'El límite debe ser al menos 1.' };
  }
  return { valor, error: null };
}
