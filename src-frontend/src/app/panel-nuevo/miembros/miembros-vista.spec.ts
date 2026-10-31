import { FichaCompleta, Miembro, RolDeMiembro } from '../../servicios/endpoints/miembros.service';
import {
  AVISO_SIN_CUENTA,
  cuerpoAlta,
  etiquetaGrupo,
  etiquetaRol,
  filaDeFicha,
  lineaSecundaria,
  mismasSedes,
  mismosGrupos,
  nombreCompleto,
  pideReactivar,
  puedeCambiarRol,
  rolesActivos,
  rolesParaAlta,
  tituloQuitar,
  validarAlta,
} from './miembros-vista';

function rol(r: RolDeMiembro['rol'], activa = true, cargo: string | null = null): RolDeMiembro {
  return { rol: r, activa, cargo };
}

function miembro(cambios: Partial<Miembro> = {}): Miembro {
  return {
    usuarioId: 1,
    nombre: 'Lucía',
    apellidos: 'Martín Gil',
    email: 'lucia@ejemplo.test',
    invitacionPendiente: false,
    roles: [rol('PROFESOR')],
    sedes: [],
    numeroGrupos: 0,
    ...cambios,
  };
}

describe('miembros-vista (E9-4)', () => {
  it('los nombres de rol en pantalla: Gestor es ADMINISTRADOR', () => {
    expect(etiquetaRol('PROPIETARIO')).toBe('Propietario');
    expect(etiquetaRol('ADMINISTRADOR')).toBe('Gestor');
    expect(etiquetaRol('RESPONSABLE_SEDE')).toBe('Responsable de sede');
    expect(etiquetaRol('PROFESOR')).toBe('Profesor');
    expect(etiquetaRol('FAMILIA')).toBe('Familia');
  });

  it('nombre completo sin huecos y roles activos en el orden de la API', () => {
    expect(nombreCompleto({ nombre: ' Lucía ', apellidos: '' })).toBe('Lucía');
    expect(rolesActivos(miembro({ roles: [rol('PROPIETARIO', false), rol('PROFESOR'), rol('FAMILIA')] }))).toEqual(['PROFESOR', 'FAMILIA']);
  });

  it('línea secundaria: sedes solo si es responsable activo y «N grupos» si tiene', () => {
    const sedes = [{ id: 1, nombre: 'Casariche' }, { id: 2, nombre: 'Mollina' }];
    expect(lineaSecundaria(miembro({ roles: [rol('RESPONSABLE_SEDE'), rol('PROFESOR')], sedes, numeroGrupos: 2 }))).toBe('Casariche, Mollina · 2 grupos');
    expect(lineaSecundaria(miembro({ roles: [rol('RESPONSABLE_SEDE', false)], sedes, numeroGrupos: 1 }))).toBe('1 grupo');
    expect(lineaSecundaria(miembro())).toBe('');
  });

  it('solo el propietario cambia Propietario y Gestor (D8-8)', () => {
    expect(puedeCambiarRol('PROPIETARIO', false)).toBeFalse();
    expect(puedeCambiarRol('ADMINISTRADOR', false)).toBeFalse();
    expect(puedeCambiarRol('PROFESOR', false)).toBeTrue();
    expect(puedeCambiarRol('RESPONSABLE_SEDE', false)).toBeTrue();
    expect(puedeCambiarRol('PROPIETARIO', true)).toBeTrue();
    expect(rolesParaAlta(false)).toEqual(['RESPONSABLE_SEDE', 'PROFESOR']);
    expect(rolesParaAlta(true)).toEqual(['PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR']);
  });

  it('el título de quitar lleva el rol en minúscula y el nombre', () => {
    expect(tituloQuitar('RESPONSABLE_SEDE', 'Lucía Martín')).toBe('¿Quitar el rol de responsable de sede a Lucía Martín?');
  });

  it('grupos: mismo conjunto aunque cambie el orden; la sede solo si hay varias', () => {
    expect(mismosGrupos([{ grupoId: 1, rol: 'TITULAR' }, { grupoId: 2, rol: 'AYUDANTE' }], [{ grupoId: 2, rol: 'AYUDANTE' }, { grupoId: 1, rol: 'TITULAR' }])).toBeTrue();
    expect(mismosGrupos([{ grupoId: 1, rol: 'TITULAR' }], [{ grupoId: 1, rol: 'AYUDANTE' }])).toBeFalse();
    expect(mismasSedes([1, 2], [2, 1])).toBeTrue();
    expect(mismasSedes([1], [1, 2])).toBeFalse();
    const sedes = new Map([[7, 'Mollina']]);
    expect(etiquetaGrupo({ nombre: 'Infantil', sedeId: 7 }, sedes, true)).toBe('Infantil · Mollina');
    expect(etiquetaGrupo({ nombre: 'Infantil', sedeId: 7 }, sedes, false)).toBe('Infantil');
  });

  it('validar el alta: email, rol, sedes del responsable y nombre al invitar', () => {
    const base = { email: 'ana@ejemplo.test', rol: 'PROFESOR' as const, sedeIds: [], nombre: '', apellidos: '' };
    expect(validarAlta(base, false)).toEqual({});
    expect(validarAlta({ ...base, email: '' }, false).email).toBe('Indica el email.');
    expect(validarAlta({ ...base, email: 'ana@' }, false).email).toBe('El email no es válido.');
    expect(validarAlta({ ...base, rol: '' }, false).rol).toBe('Elige un rol.');
    expect(validarAlta({ ...base, rol: 'RESPONSABLE_SEDE' }, false).sedes).toBe('Indica al menos una sede.');
    expect(validarAlta({ ...base, rol: 'RESPONSABLE_SEDE', sedeIds: [1] }, false).sedes).toBeUndefined();
    const invitar = validarAlta(base, true);
    expect(invitar.nombre).toBe('Indica el nombre.');
    expect(invitar.apellidos).toBe('Indica los apellidos.');
  });

  it('el cuerpo del alta: sedes solo del responsable, nombre solo al invitar, reactivar solo si se ofreció', () => {
    const c = { email: ' ana@ejemplo.test ', rol: 'RESPONSABLE_SEDE' as const, sedeIds: [2], nombre: ' Ana ', apellidos: ' Ruiz ' };
    expect(cuerpoAlta(c, false, false)).toEqual({ email: 'ana@ejemplo.test', rol: 'RESPONSABLE_SEDE', sedeIds: [2] });
    expect(cuerpoAlta({ ...c, rol: 'PROFESOR' }, true, true)).toEqual({
      email: 'ana@ejemplo.test',
      rol: 'PROFESOR',
      nombre: 'Ana',
      apellidos: 'Ruiz',
      reactivar: true,
    });
  });

  it('reactivar solo con el 409 del rol desactivado; el aviso de sin cuenta es el aprobado', () => {
    expect(pideReactivar(409, 'La cuenta tiene el rol de profesor desactivado en esta escuela.')).toBeTrue();
    expect(pideReactivar(409, 'La escuela se quedaría sin propietario.')).toBeFalse();
    expect(pideReactivar(400, 'desactivado')).toBeFalse();
    expect(AVISO_SIN_CUENTA).toBe('No hay ninguna cuenta con ese email. Le enviaremos una invitación para crear su contraseña.');
  });

  it('la fila de una ficha no lleva grupos', () => {
    const ficha: FichaCompleta = { ...miembro(), grupos: [{ grupoId: 1, nombre: 'Infantil', sedeId: 1, rol: 'TITULAR' }] };
    expect('grupos' in filaDeFicha(ficha)).toBeFalse();
  });
});
