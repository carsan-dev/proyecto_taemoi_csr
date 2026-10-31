import {
  CAMPOS_SEDE,
  Sede,
  activarCreaSegunda,
  camposDe,
  cuerpoSede,
  esUnicaActiva,
  estadoHttp,
  lineaSecundaria,
  mensajeError,
  seraSegundaSede,
  totalActivas,
  validarCampos,
} from './sedes-vista';

function sede(cambios: Partial<Sede> = {}): Sede {
  return {
    id: 1,
    nombre: 'Casariche',
    direccion: null,
    localidad: null,
    codigoPostal: null,
    telefono: null,
    email: null,
    codigoClubFederacion: null,
    aforoTurno: null,
    activa: true,
    ...cambios,
  };
}

describe('sedes-vista (E9-3)', () => {
  it('los campos llevan las etiquetas y los máximos aprobados, sin aforo', () => {
    expect(CAMPOS_SEDE.map((c) => [c.etiqueta, c.maximo])).toEqual([
      ['Nombre (obligatorio)', 100],
      ['Dirección', 255],
      ['Localidad', 100],
      ['Código postal', 10],
      ['Teléfono', 20],
      ['Email', 180],
      ['Código del club en la federación', 40],
    ]);
    expect(CAMPOS_SEDE.some((c) => /aforo/i.test(c.etiqueta) || (c.clave as string) === 'aforoTurno')).toBeFalse();
  });

  it('cuerpoSede recorta, envía los vacíos como null y conserva el aforo recibido', () => {
    const campos = { ...camposDe(null), nombre: '  Mollina ', telefono: ' 600 ', direccion: '   ' };
    expect(cuerpoSede(campos, 25)).toEqual({
      nombre: 'Mollina',
      direccion: null,
      localidad: null,
      codigoPostal: null,
      telefono: '600',
      email: null,
      codigoClubFederacion: null,
      aforoTurno: 25,
    });
    expect(cuerpoSede(campos, null).aforoTurno).toBeNull();
  });

  it('camposDe rellena con cadenas vacías lo que viene null', () => {
    expect(camposDe(sede({ localidad: 'Casariche' }))).toEqual(jasmine.objectContaining({ nombre: 'Casariche', localidad: 'Casariche', email: '' }));
  });

  it('validarCampos: nombre obligatorio, máximos y email', () => {
    expect(validarCampos(camposDe(null)).nombre).toBe('Indica el nombre de la sede.');
    expect(validarCampos({ ...camposDe(null), nombre: 'x'.repeat(101) }).nombre).toContain('100');
    expect(validarCampos({ ...camposDe(null), nombre: 'A', codigoPostal: '1'.repeat(11) }).codigoPostal).toContain('10');
    expect(validarCampos({ ...camposDe(null), nombre: 'A', email: 'sin-arroba' }).email).toBeTruthy();
    expect(validarCampos({ ...camposDe(null), nombre: 'A', email: 'a@b.es' })).toEqual({});
    expect(validarCampos({ ...camposDe(null), nombre: 'A', email: '' })).toEqual({});
  });

  it('reglas de activas: segunda sede, activar, única activa', () => {
    const una = [sede(), sede({ id: 2, activa: false })];
    const dos = [sede(), sede({ id: 2 }), sede({ id: 3, activa: false })];
    expect(totalActivas(dos)).toBe(2);
    expect(seraSegundaSede(una)).toBeTrue();
    expect(seraSegundaSede(dos)).toBeFalse();
    expect(seraSegundaSede([])).toBeFalse();
    expect(activarCreaSegunda(una, una[1])).toBeTrue();
    expect(activarCreaSegunda(dos, dos[2])).toBeFalse();
    expect(activarCreaSegunda(una, una[0])).toBeFalse();
    expect(esUnicaActiva(una, una[0])).toBeTrue();
    expect(esUnicaActiva(una, una[1])).toBeFalse();
    expect(esUnicaActiva(dos, dos[0])).toBeFalse();
  });

  it('lineaSecundaria: localidad y teléfono si existen', () => {
    expect(lineaSecundaria(sede({ localidad: 'Roda de Andalucía', telefono: '600 000 000' }))).toBe('Roda de Andalucía · 600 000 000');
    expect(lineaSecundaria(sede({ telefono: '600' }))).toBe('600');
    expect(lineaSecundaria(sede({ localidad: ' ' }))).toBe('');
  });

  it('mensajeError y estadoHttp', () => {
    expect(mensajeError({ error: { mensaje: 'Ya hay una sede con ese nombre.' } }, 'x')).toBe('Ya hay una sede con ese nombre.');
    expect(mensajeError({ error: {} }, 'por defecto')).toBe('por defecto');
    expect(mensajeError(null, 'por defecto')).toBe('por defecto');
    expect(estadoHttp({ status: 409 })).toBe(409);
    expect(estadoHttp({})).toBeNull();
  });
});
