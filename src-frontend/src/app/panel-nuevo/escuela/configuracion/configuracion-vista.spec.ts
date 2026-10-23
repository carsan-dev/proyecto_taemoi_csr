import {
  REGLAS_CONTRASENA,
  algunRol,
  contrasenaCumpleReglas,
  cuerpoRoles,
  filtrarCuentas,
  listaRoles,
  mensajeErrorContrasena,
  mensajeErrorRoles,
  mismaSeleccion,
  pastillasRoles,
  rolesDeTexto,
  seleccionDe,
  validarLimite,
} from './configuracion-vista';

describe('configuracion-vista (8b-3k)', () => {
  it('lee los roles con el formato de siempre y marca las casillas como parseRoles de hoy', () => {
    expect(rolesDeTexto('[ROLE_USER, ROLE_ADMIN]')).toEqual(['ROLE_USER', 'ROLE_ADMIN']);
    expect(rolesDeTexto('[]')).toEqual([]);
    expect(rolesDeTexto(null)).toEqual([]);
    expect(seleccionDe('[ROLE_MANAGER, ROLE_PROFESOR]')).toEqual({ ROLE_USER: false, ROLE_MANAGER: true, ROLE_ADMIN: false });
  });

  it('el cuerpo lleva los ROLE_* marcados en el orden de hoy (ROLE_USER, ROLE_MANAGER, ROLE_ADMIN)', () => {
    expect(cuerpoRoles({ ROLE_ADMIN: true, ROLE_USER: true, ROLE_MANAGER: true })).toEqual(['ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN']);
    expect(cuerpoRoles({ ROLE_ADMIN: true, ROLE_USER: false, ROLE_MANAGER: false })).toEqual(['ROLE_ADMIN']);
    expect(algunRol({ ROLE_ADMIN: false, ROLE_USER: false, ROLE_MANAGER: false })).toBeFalse();
    expect(mismaSeleccion(seleccionDe('[ROLE_USER]'), { ROLE_USER: true, ROLE_MANAGER: false, ROLE_ADMIN: false })).toBeTrue();
  });

  it('pastillas con los nombres visibles (decisiones 262 y 263), en el orden de hoy y sin roles desconocidos', () => {
    expect(pastillasRoles('[ROLE_USER, ROLE_PROFESOR, ROLE_ADMIN, ROLE_MANAGER, ROLE_RESPONSABLE_SEDE, ROLE_RARO]')).toEqual([
      { rol: 'ROLE_ADMIN', texto: 'Administración' },
      { rol: 'ROLE_MANAGER', texto: 'Gestión' },
      { rol: 'ROLE_USER', texto: 'Familia' },
      { rol: 'ROLE_RESPONSABLE_SEDE', texto: 'Responsable de sede' },
      { rol: 'ROLE_PROFESOR', texto: 'Profesorado' },
    ]);
    expect(listaRoles(['ROLE_USER'])).toBe('Familia');
    expect(listaRoles(['ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN'])).toBe('Familia, Gestión y Administración');
  });

  it('el buscador filtra por nombre y apellidos o email, sin acentos ni mayúsculas', () => {
    const cuentas = [
      { id: 1, nombre: 'Ana', apellidos: 'Prueba Núñez', email: 'ana@ejemplo.test', rol: '[ROLE_USER]' },
      { id: 2, nombre: 'Leo', apellidos: 'Prueba', email: 'leo@ejemplo.test', rol: '[ROLE_ADMIN]' },
    ];
    expect(filtrarCuentas(cuentas, ' NUNEZ ').map((c) => c.id)).toEqual([1]);
    expect(filtrarCuentas(cuentas, 'leo@').map((c) => c.id)).toEqual([2]);
    expect(filtrarCuentas(cuentas, '').map((c) => c.id)).toEqual([1, 2]);
  });

  it('el límite: menor que 1 da el error de hoy; vacío o con decimales tampoco se guarda', () => {
    expect(validarLimite('40')).toEqual({ valor: 40, error: null });
    expect(validarLimite(' 1 ')).toEqual({ valor: 1, error: null });
    expect(validarLimite('0').error).toBe('El límite debe ser al menos 1.');
    expect(validarLimite('-3').error).toBe('El límite debe ser al menos 1.');
    expect(validarLimite('').error).toBe('Escribe cuántos alumnos caben en un turno.');
    expect(validarLimite('2.5').error).toBe('Escribe un número entero, por ejemplo 36.');
  });

  it('las reglas de la contraseña son las de hoy', () => {
    expect(REGLAS_CONTRASENA.map((r) => r.texto)).toEqual([
      'Mínimo 8 caracteres', 'Al menos 1 mayúscula', 'Al menos 1 minúscula', 'Al menos 1 número', 'Coinciden',
    ]);
    expect(contrasenaCumpleReglas('Abcdefg1')).toBeTrue();
    expect(contrasenaCumpleReglas('abcdefg1')).toBeFalse();
    expect(contrasenaCumpleReglas('Abcdef1')).toBeFalse();
    const coinciden = REGLAS_CONTRASENA.find((r) => r.clave === 'coinciden')!;
    expect(coinciden.cumple('', '')).toBeFalse();
    expect(coinciden.cumple('Abcdefg1', 'Abcdefg1')).toBeTrue();
  });

  it('los mensajes de error: el «mensaje» del servidor (409 de último propietario) o los de hoy', () => {
    expect(mensajeErrorRoles({ status: 409, error: { mensaje: 'La escuela no puede quedarse sin propietario.' } })).toBe(
      'La escuela no puede quedarse sin propietario.'
    );
    expect(mensajeErrorRoles({ status: 500 })).toBe('No se pudieron actualizar los roles del usuario.');
    expect(mensajeErrorContrasena({ status: 403 })).toBe('No tienes permisos para restablecer contraseñas.');
    expect(mensajeErrorContrasena({ status: 400, error: { message: 'Muy corta' } })).toBe('Muy corta');
    expect(mensajeErrorContrasena({ status: 500 })).toBe('No se pudo actualizar la contraseña.');
  });
});
