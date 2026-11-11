import {
  CAMPOS_SEDE,
  SLUG_NO_VALIDO,
  Sede,
  activarCreaSegunda,
  altPorDefecto,
  campoWebDeError,
  camposDe,
  camposWebDe,
  cuerpoSede,
  cuerpoSedeWeb,
  falloAlElegir,
  esUnicaActiva,
  estadoHttp,
  lineaSecundaria,
  mensajeError,
  pesoFoto,
  seraSegundaSede,
  totalActivas,
  validarCampos,
  validarCamposWeb,
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
  it('los campos llevan las etiquetas y los máximos aprobados', () => {
    expect(CAMPOS_SEDE.map((c) => [c.etiqueta, c.maximo])).toEqual([
      ['Nombre (obligatorio)', 100],
      ['Dirección', 255],
      ['Localidad', 100],
      ['Código postal', 10],
      ['Teléfono', 20],
      ['Email', 180],
      ['Código del club en la federación', 40],
      ['Aforo por turno (opcional)', 6],
    ]);
    const aforo = CAMPOS_SEDE.find((c) => c.clave === 'aforoTurno');
    expect(aforo?.ayuda).toBe('Si lo dejas vacío, se usa el límite común de la escuela.');
    expect(aforo?.modoEntrada).toBe('numeric');
  });

  it('cuerpoSede recorta, envía los vacíos como null y el aforo como número', () => {
    const campos = { ...camposDe(null), nombre: '  Mollina ', telefono: ' 600 ', direccion: '   ', aforoTurno: ' 25 ' };
    expect(cuerpoSede(campos)).toEqual({
      nombre: 'Mollina',
      direccion: null,
      localidad: null,
      codigoPostal: null,
      telefono: '600',
      email: null,
      codigoClubFederacion: null,
      aforoTurno: 25,
    });
    expect(cuerpoSede({ ...campos, aforoTurno: '  ' }).aforoTurno).toBeNull();
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

  it('9-7: el aforo es opcional, entero y mayor que cero', () => {
    const base = { ...camposDe(null), nombre: 'A' };
    expect(camposDe(sede({ aforoTurno: 25 })).aforoTurno).toBe('25');
    expect(camposDe(sede({ aforoTurno: null })).aforoTurno).toBe('');
    expect(validarCampos({ ...base, aforoTurno: '' })).toEqual({});
    expect(validarCampos({ ...base, aforoTurno: ' 12 ' })).toEqual({});
    for (const malo of ['0', '00', '-3', '1.5', '1,5', 'doce']) {
      expect(validarCampos({ ...base, aforoTurno: malo }).aforoTurno).withContext(malo).toBe('Escribe un número entero mayor que 0 o déjalo vacío.');
    }
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

describe('sedes-vista: grupo «En la web» (W12g)', () => {
  it('cuerpoSedeWeb: los de siempre y los de la web, recortados, vacíos como null y la dirección en minúsculas', () => {
    const cuerpo = cuerpoSedeWeb(camposDe(sede({ nombre: 'Herrera' })), {
      slug: ' Herrera ',
      lugar: '',
      indicacionesAcceso: ' Por el patio ',
      fotoAlt: '  ',
      foto2Alt: 'Entrada',
    });
    expect(cuerpo).toEqual(
      jasmine.objectContaining({ nombre: 'Herrera', slug: 'herrera', lugar: null, indicacionesAcceso: 'Por el patio', fotoAlt: null, foto2Alt: 'Entrada' })
    );
  });

  it('camposWebDe: lo que trae la sede, o vacío', () => {
    expect(camposWebDe(null)).toEqual({ slug: '', lugar: '', indicacionesAcceso: '', fotoAlt: '', foto2Alt: '' });
    expect(camposWebDe(sede({ slug: 'casariche', fotoAlt: 'Tatami' })).slug).toBe('casariche');
  });

  it('validarCamposWeb: formato y repetida de la dirección, máximos', () => {
    const sedes = [sede({ id: 1, slug: 'casariche' }), sede({ id: 2, nombre: 'Estepa', slug: 'estepa' })];
    const vacio = camposWebDe(null);
    expect(validarCamposWeb(vacio, sedes, 1)).toEqual({});
    expect(validarCamposWeb({ ...vacio, slug: 'casariche' }, sedes, 1)).toEqual({});
    expect(validarCamposWeb({ ...vacio, slug: 'ESTEPA' }, sedes, 1).slug).toBe('Ya la usa la sede de Estepa. Elige otra.');
    expect(validarCamposWeb({ ...vacio, slug: 'roda--x' }, sedes, null).slug).toBe(SLUG_NO_VALIDO);
    expect(validarCamposWeb({ ...vacio, slug: 'a'.repeat(61) }, sedes, null).slug).toBe(SLUG_NO_VALIDO);
    expect(validarCamposWeb({ ...vacio, lugar: 'x'.repeat(101) }, sedes, null).lugar).toBe('No puede pasar de 100 caracteres.');
    // Un lugar ya guardado de 101-120 caracteres (el servidor admite 120) no se bloquea: se queda igual o se acorta.
    expect(validarCamposWeb({ ...vacio, lugar: 'x'.repeat(110) }, sedes, 1, 110)).toEqual({});
    expect(validarCamposWeb({ ...vacio, lugar: 'x'.repeat(105) }, sedes, 1, 110)).toEqual({});
    expect(validarCamposWeb({ ...vacio, lugar: 'x'.repeat(111) }, sedes, 1, 110).lugar).toBe('No puede pasar de 110 caracteres.');
    expect(validarCamposWeb({ ...vacio, lugar: 'x'.repeat(101) }, sedes, 1, 50).lugar).toBe('No puede pasar de 100 caracteres.');
    expect(validarCamposWeb({ ...vacio, foto2Alt: 'x'.repeat(151) }, sedes, null).foto2Alt).toBe('No puede pasar de 150 caracteres.');
  });

  it('campoWebDeError: cada mensaje del servidor a su campo', () => {
    expect(campoWebDeError('Ya hay una sede con esa dirección web.')).toBe('slug');
    expect(campoWebDeError(SLUG_NO_VALIDO)).toBe('slug');
    expect(campoWebDeError('El lugar de la sede no puede pasar de 120 caracteres.')).toBe('lugar');
    expect(campoWebDeError('Las indicaciones de acceso no puede pasar de 300 caracteres.')).toBe('indicacionesAcceso');
    expect(campoWebDeError('La descripción de la foto 1 no puede pasar de 150 caracteres.')).toBe('fotoAlt');
    expect(campoWebDeError('La descripción de la foto 2 no puede pasar de 150 caracteres.')).toBe('foto2Alt');
    expect(campoWebDeError('Ya hay una sede con ese nombre.')).toBeNull();
  });

  it('falloAlElegir y los pesos', () => {
    expect(falloAlElegir('tamano', { name: 'a.jpg', size: 64 * 1024 * 1024 })).toEqual({
      titulo: 'Demasiado grande',
      texto: '«a.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.',
    });
    expect(falloAlElegir('formato', { name: 'a.gif', size: 1 }).titulo).toBe('Formato no admitido');
    expect(pesoFoto(2.4 * 1024 * 1024)).toBe('2,4 MB');
    expect(pesoFoto(500)).toBe('1 KB');
    expect(altPorDefecto('Herrera')).toBe('Foto de la sede de Herrera');
    expect(altPorDefecto('')).toBe('Foto de la sede de esta sede');
  });
});
