import { SECCIONES, validarLimite } from './configuracion-vista';

describe('configuracion-vista (8b-3k, 9-8)', () => {
  it('el índice tiene dos secciones, sin Roles y permisos (9-8)', () => {
    expect(SECCIONES.map((s) => s.titulo)).toEqual(['Alumnos por turno', 'Avisos legales y cookies']);
  });

  it('el límite: menor que 1 da el error de hoy; vacío o con decimales tampoco se guarda', () => {
    expect(validarLimite('40')).toEqual({ valor: 40, error: null });
    expect(validarLimite(' 1 ')).toEqual({ valor: 1, error: null });
    expect(validarLimite('0').error).toBe('El límite debe ser al menos 1.');
    expect(validarLimite('-3').error).toBe('El límite debe ser al menos 1.');
    expect(validarLimite('').error).toBe('Escribe cuántos alumnos caben en un turno.');
    expect(validarLimite('2.5').error).toBe('Escribe un número entero, por ejemplo 36.');
  });
});
