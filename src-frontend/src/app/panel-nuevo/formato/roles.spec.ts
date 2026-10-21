import { etiquetaRol } from './roles';

/** Nombres visibles de los roles en las pantallas nuevas (decisiones 262 y 263): solo cambia el texto. */
describe('etiquetaRol', () => {
  it('las autoridades de la sesión', () => {
    expect(etiquetaRol('ROLE_USER')).toBe('Familia');
    expect(etiquetaRol('ROLE_MANAGER')).toBe('Gestión');
    expect(etiquetaRol('ROLE_ADMIN')).toBe('Administración');
    expect(etiquetaRol('ROLE_PROFESOR')).toBe('Profesorado');
    expect(etiquetaRol('ROLE_RESPONSABLE_SEDE')).toBe('Responsable de sede');
  });

  it('los roles de membresía de la API de miembros, con el mismo texto que su autoridad', () => {
    expect(etiquetaRol('FAMILIA')).toBe('Familia');
    expect(etiquetaRol('ADMINISTRADOR')).toBe('Gestión');
    expect(etiquetaRol('PROPIETARIO')).toBe('Administración');
    expect(etiquetaRol('PROFESOR')).toBe('Profesorado');
    expect(etiquetaRol('RESPONSABLE_SEDE')).toBe('Responsable de sede');
  });

  it('un valor desconocido o vacío no inventa nombre', () => {
    expect(etiquetaRol('ROLE_ADMIN_PLATAFORMA')).toBe('');
    expect(etiquetaRol('')).toBe('');
    expect(etiquetaRol(null)).toBe('');
    expect(etiquetaRol(undefined)).toBe('');
  });
});
