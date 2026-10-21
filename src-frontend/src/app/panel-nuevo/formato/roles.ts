/**
 * Nombre visible de un rol en las pantallas nuevas (decisiones 262 y 263). Solo cambia el texto: los valores internos
 * (`ROLE_*` de la sesión y los roles de membresía de la API de miembros) y lo que se envía a la API no cambian. Las
 * pantallas de hoy no lo usan.
 */
const ETIQUETAS: Readonly<Record<string, string>> = {
  ROLE_USER: 'Familia',
  FAMILIA: 'Familia',
  ROLE_MANAGER: 'Gestión',
  ADMINISTRADOR: 'Gestión',
  ROLE_ADMIN: 'Administración',
  PROPIETARIO: 'Administración',
  ROLE_PROFESOR: 'Profesorado',
  PROFESOR: 'Profesorado',
  ROLE_RESPONSABLE_SEDE: 'Responsable de sede',
  RESPONSABLE_SEDE: 'Responsable de sede',
};

/** «Familia», «Gestión», «Administración», «Profesorado» o «Responsable de sede»; vacío si no es un rol conocido. */
export function etiquetaRol(rol: string | null | undefined): string {
  return (rol && Object.prototype.hasOwnProperty.call(ETIQUETAS, rol) ? ETIQUETAS[rol] : '') ?? '';
}
