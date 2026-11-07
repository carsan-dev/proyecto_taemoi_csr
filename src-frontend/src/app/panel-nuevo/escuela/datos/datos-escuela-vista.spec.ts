import {
  CAMPOS_DATOS,
  DIA_NO_VALIDO,
  DatosEscuela,
  clavesCambiadas,
  erroresDeApi,
  erroresDeDatos,
  errorDeDato,
  escuelaSinRellenar,
  recortar,
  solicitudDe,
  valoresDe,
  valoresVacios,
} from './datos-escuela-vista';

const DATOS: DatosEscuela = {
  nombreLegal: 'Club Deportivo de Ejemplo',
  cif: 'G00000000',
  direccion: 'Calle Ejemplo 1, 41570 Casariche (Sevilla)',
  emailContacto: 'secretaria@club.example',
  telefono: '600 000 000',
  firmanteNombre: 'María Ruiz Ejemplo',
  firmanteCargo: 'Presidenta',
  firmanteNif: '00000000T',
  diaLimiteMensualidad: 5,
};

describe('datos-escuela-vista (pieza 394)', () => {
  it('unos datos completos y válidos no dan ningún error', () => {
    expect(erroresDeDatos(valoresDe(DATOS))).toEqual({});
  });

  it('los obligatorios vacíos dan el mensaje de la API; el teléfono vacío vale', () => {
    const errores = erroresDeDatos(valoresVacios());
    expect(errores).toEqual({
      nombreLegal: 'El nombre legal es obligatorio.',
      cif: 'El CIF es obligatorio.',
      direccion: 'La dirección fiscal es obligatoria.',
      emailContacto: 'El email de contacto es obligatorio.',
      firmanteNombre: 'El nombre del firmante es obligatorio.',
      firmanteCargo: 'El cargo es obligatorio.',
      firmanteNif: 'El NIF del firmante es obligatorio.',
    });
  });

  it('solo espacios cuenta como vacío', () => {
    expect(errorDeDato('nombreLegal', '   ')).toBe('El nombre legal es obligatorio.');
  });

  it('el firmante vacío de una escuela como Moiskimdo solo se reclama al validar', () => {
    const valores = valoresDe({ ...DATOS, firmanteNombre: null, firmanteCargo: null, firmanteNif: null });
    expect(valores.firmanteNombre).toBe('');
    expect(Object.keys(erroresDeDatos(valores))).toEqual(['firmanteNombre', 'firmanteCargo', 'firmanteNif']);
  });

  it('respeta los máximos de la API: 150, 255, 100, 60 y 180', () => {
    expect(errorDeDato('nombreLegal', 'a'.repeat(150))).toBe('');
    expect(errorDeDato('nombreLegal', 'a'.repeat(151))).toBe('El nombre legal no puede tener más de 150 caracteres.');
    expect(errorDeDato('direccion', 'a'.repeat(255))).toBe('');
    expect(errorDeDato('direccion', 'a'.repeat(256))).toBe('La dirección fiscal no puede tener más de 255 caracteres.');
    expect(errorDeDato('firmanteNombre', 'a'.repeat(101))).toBe('El nombre del firmante no puede tener más de 100 caracteres.');
    expect(errorDeDato('firmanteCargo', 'a'.repeat(61))).toBe('El cargo no puede tener más de 60 caracteres.');
    expect(errorDeDato('emailContacto', 'a'.repeat(170) + '@club.example')).toBe('El email no puede tener más de 180 caracteres.');
  });

  it('CIF: entidad o persona, por formato; en minúsculas vale (la API lo pasa a mayúsculas)', () => {
    for (const bueno of ['G00000000', 'B1234567J', '00000000T', 'X0000000T', 'g00000000']) {
      expect(errorDeDato('cif', bueno)).withContext(bueno).toBe('');
    }
    for (const malo of ['G0000000', 'G-0000000', 'I00000000', 'G000000000', 'ABC']) {
      expect(errorDeDato('cif', malo)).withContext(malo).toBe('El CIF no tiene un formato válido.');
    }
  });

  it('NIF del firmante: 8 cifras (o X, Y, Z y 7) y una letra', () => {
    expect(errorDeDato('firmanteNif', '00000000T')).toBe('');
    expect(errorDeDato('firmanteNif', 'Y0000000T')).toBe('');
    expect(errorDeDato('firmanteNif', 'G00000000')).toBe('El NIF no tiene un formato válido.');
    expect(errorDeDato('firmanteNif', '0000000T')).toBe('El NIF no tiene un formato válido.');
  });

  it('email y teléfono', () => {
    expect(errorDeDato('emailContacto', 'secretaria@club.example')).toBe('');
    expect(errorDeDato('emailContacto', 'secretaria@club')).toBe('El email no tiene un formato válido.');
    expect(errorDeDato('telefono', '+34 600 000 000')).toBe('');
    expect(errorDeDato('telefono', '600-000-000')).toBe('El teléfono solo lleva números y espacios.');
    expect(errorDeDato('telefono', '12345')).toBe('El teléfono solo lleva números y espacios.');
  });

  it('día límite: de 1 a 28', () => {
    for (const bueno of ['1', '5', '28']) {
      expect(errorDeDato('diaLimiteMensualidad', bueno)).withContext(bueno).toBe('');
    }
    for (const malo of ['0', '29', '-1', '1.5', 'a', '']) {
      expect(errorDeDato('diaLimiteMensualidad', malo)).withContext(malo).toBe(DIA_NO_VALIDO);
    }
  });

  it('caracteres de control o invisibles no valen en los textos libres', () => {
    expect(errorDeDato('nombreLegal', 'Club‮Ejemplo')).toBe('El nombre legal tiene caracteres no válidos.');
    expect(errorDeDato('direccion', 'Calle\nEjemplo')).toBe('La dirección fiscal tiene caracteres no válidos.');
    expect(errorDeDato('firmanteCargo', 'Presidenta​')).toBe('El cargo tiene caracteres no válidos.');
    expect(errorDeDato('nombreLegal', 'Peña Ñandú, S.L. (nº 1)')).toBe('');
  });

  it('escuelaSinRellenar: solo si faltan los cuatro datos fiscales y de contacto', () => {
    expect(escuelaSinRellenar(valoresVacios())).toBeTrue();
    expect(escuelaSinRellenar(valoresDe({ ...DATOS, firmanteNombre: null }))).toBeFalse();
    expect(escuelaSinRellenar(valoresDe({ ...DATOS, nombreLegal: null, cif: null, direccion: null, emailContacto: null }))).toBeTrue();
  });

  it('clavesCambiadas compara con lo guardado', () => {
    const guardados = valoresDe(DATOS);
    expect(clavesCambiadas({ ...guardados }, guardados)).toEqual([]);
    expect(clavesCambiadas({ ...guardados, telefono: '600 000 001', diaLimiteMensualidad: '10' }, guardados)).toEqual([
      'telefono',
      'diaLimiteMensualidad',
    ]);
  });

  it('solicitudDe recorta, pasa vacío a null, pone CIF y NIF en mayúsculas y manda el día como número', () => {
    const solicitud = solicitudDe({
      ...valoresDe(DATOS),
      nombreLegal: '  Club de Ejemplo  ',
      cif: ' g00000000 ',
      telefono: '   ',
      firmanteNif: '00000000t',
      diaLimiteMensualidad: ' 10 ',
    });
    expect(solicitud).toEqual({
      ...DATOS,
      nombreLegal: 'Club de Ejemplo',
      cif: 'G00000000',
      telefono: null,
      firmanteNif: '00000000T',
      diaLimiteMensualidad: 10,
    });
  });

  it('teléfono: + y 15 cifras son válidos (16 caracteres, como la API) y el maxlength no los trunca', () => {
    expect(CAMPOS_DATOS.telefono.maxlength).toBe(16);
    expect(errorDeDato('telefono', '+123456789012345')).toBe('');
    expect(errorDeDato('telefono', '+1234567890123456')).toBe('El teléfono solo lleva números y espacios.');
    expect(erroresDeDatos(valoresDe({ ...DATOS, telefono: '+123456789012345' }))).toEqual({});
    expect(solicitudDe({ ...valoresDe(DATOS), telefono: '+123456789012345' }).telefono).toBe('+123456789012345');
  });

  it('email con U+00A0 dentro: válido, como en la API (\\s de Java no incluye NBSP)', () => {
    expect(errorDeDato('emailContacto', 'a b@club.example')).toBe('');
  });

  it('se recortan los espacios de Java en los extremos, también U+001C-U+001F', () => {
    expect(errorDeDato('nombreLegal', '\u001CClub\u001C')).toBe('');
    expect(solicitudDe({ ...valoresDe(DATOS), nombreLegal: '\u001CClub\u001C' }).nombreLegal).toBe('Club');
    expect(recortar(' 　Club ')).toBe('Club');
  });

  it('U+00A0, U+2007 y U+202F en los extremos no se recortan (no son espacios para la API)', () => {
    expect(recortar(' Club ')).toBe(' Club ');
    expect(recortar(' Club ')).toBe(' Club ');
    expect(solicitudDe({ ...valoresDe(DATOS), nombreLegal: ' Club ' }).nombreLegal).toBe(' Club ');
  });

  it('erroresDeApi toma los errores por campo del 400 e ignora lo que no conoce', () => {
    expect(erroresDeApi({ mensaje: 'x', errores: { cif: 'El CIF no tiene un formato válido.', raro: 'y', telefono: '' } })).toEqual({
      cif: 'El CIF no tiene un formato válido.',
    });
    expect(erroresDeApi({ mensaje: 'Faltan los datos de la escuela o no son válidos.' })).toEqual({});
    expect(erroresDeApi(null)).toEqual({});
  });
});
