import { FormControl, FormGroup } from '@angular/forms';

import {
  ERROR_ALTA_ANTERIOR,
  MENSAJE_ALTA_ANTERIOR,
  ReglasCuerpo,
  cuerpoAlta,
  destinoErrorServidor,
  edadEnAnios,
  esMenorDeEdad,
  fechaAltaNoAnteriorANacimiento,
  fechaHoy,
  mensajesDeporte,
  mensajesPersona,
  textoAnadirDeporte,
  textoEdad,
  textoErrorAlta,
  tituloFaltan,
} from './alta-alumno-vista';

describe('alta-alumno-vista (8b-3l)', () => {
  const reglas: ReglasCuerpo = {
    permiteCompeticion: (d) => d === 'TAEKWONDO',
    requiereLicencia: (d) => d === 'TAEKWONDO',
    primeraDisciplinaActiva: () => 'TAEKWONDO',
    categoriaPorEdad: () => 'Infantil',
    hoy: '2026-10-24',
  };

  function deporte(extra: Record<string, any> = {}): Record<string, any> {
    return {
      deporte: 'TAEKWONDO',
      grado: 'AMARILLO',
      fechaGrado: '2026-09-01',
      fechaAlta: '2026-09-01',
      fechaAltaInicial: '',
      tipoTarifa: 'MENSUAL_INFANTIL',
      cuantiaTarifa: 30,
      rolFamiliar: '',
      grupoFamiliar: '',
      categoria: '',
      competidor: false,
      fechaAltaCompeticion: '',
      fechaAltaCompetidorInicial: '',
      peso: null,
      fechaPeso: '2026-10-24',
      tieneLicencia: false,
      numeroLicencia: null,
      fechaLicencia: '2026-10-24',
      ...extra,
    };
  }

  function crudo(extra: Record<string, any> = {}): Record<string, any> {
    return {
      nombre: 'Lucía',
      apellidos: 'Ortega Ramos',
      fechaNacimiento: '2017-03-14',
      nif: ' 12345678z ',
      sede: '',
      direccion: 'Calle Mayor 14',
      telefono: '611234567',
      telefono2: '',
      email: 'marta@correo.es',
      responsableLegalNombre: 'Marta Ramos',
      responsableLegalNif: ' x1234567l ',
      autorizacionWeb: true,
      rolFamiliar: 'NINGUNO',
      grupoFamiliar: '',
      tieneDiscapacidad: false,
      deportesInicial: [deporte()],
      ...extra,
    };
  }

  it('mensajes de hoy de los datos personales y del deporte, y el error del servidor', () => {
    expect(mensajesPersona('nombre', { required: true })).toEqual(['El nombre del alumno es obligatorio']);
    expect(mensajesPersona('telefono', { required: true, pattern: true, maxlength: true })).toEqual([
      'El teléfono es obligatorio',
      'Solo números permitidos',
      'Máximo 9 caracteres',
    ]);
    expect(mensajesPersona('email', { email: true })).toEqual(['Formato de email inválido']);
    expect(mensajesPersona('nif', { pattern: true })).toEqual(['Formato inválido (8 números y una letra)']);
    expect(mensajesPersona('responsableLegalNif', { pattern: true })).toEqual(['Introduce un DNI/NIE válido.']);
    expect(mensajesPersona('sede', { required: true })).toEqual(['Indica la sede.']);
    expect(mensajesPersona('nif', { servidor: 'El alumno con NIF 1 ya existe.' })).toEqual(['El alumno con NIF 1 ya existe.']);
    expect(mensajesPersona('nombre', null)).toEqual([]);
    expect(mensajesDeporte('grado', { required: true })).toEqual(['Selecciona el grado']);
    expect(mensajesDeporte('deporte', { required: true })).toEqual(['Selecciona el deporte']);
    expect(mensajesDeporte('fechaAlta', { [ERROR_ALTA_ANTERIOR]: true })).toEqual([MENSAJE_ALTA_ANTERIOR]);
  });

  it('edad y categoría junto a la fecha; menor de 18', () => {
    const hoy = new Date(2026, 9, 24);
    expect(edadEnAnios('2017-03-14', hoy)).toBe(9);
    expect(edadEnAnios('2017-10-24', hoy)).toBe(9);
    expect(edadEnAnios('2017-10-25', hoy)).toBe(8);
    expect(edadEnAnios('', hoy)).toBeNull();
    expect(edadEnAnios('2030-01-01', hoy)).toBeNull();
    expect(textoEdad(9, 'Infantil')).toBe('9 años · categoría infantil');
    expect(textoEdad(1, '')).toBe('1 año');
    expect(textoEdad(null, 'Infantil')).toBe('');
    expect(esMenorDeEdad(17)).toBeTrue();
    expect(esMenorDeEdad(18)).toBeFalse();
    expect(esMenorDeEdad(null)).toBeFalse();
  });

  it('fecha de alta ≥ fecha de nacimiento (P-2): el mismo día vale, el anterior no', () => {
    const alta = new FormControl('2017-03-13', fechaAltaNoAnteriorANacimiento());
    const form = new FormGroup({ fechaNacimiento: new FormControl('2017-03-14'), alta });
    alta.updateValueAndValidity();
    expect(alta.errors).toEqual({ [ERROR_ALTA_ANTERIOR]: true });
    alta.setValue('2017-03-14');
    expect(alta.errors).toBeNull();
    form.controls.fechaNacimiento.setValue('');
    alta.setValue('2000-01-01');
    expect(alta.errors).toBeNull();
  });

  it('el cuerpo es el de hoy: NIF y DNI/NIE normalizados, antigüedad = alta, competición limpia, sin sede', () => {
    const cuerpo = cuerpoAlta(crudo(), reglas, false);
    expect(cuerpo['nif']).toBe('12345678Z');
    expect(cuerpo['responsableLegalNif']).toBe('X1234567L');
    expect(cuerpo['aptoParaExamen']).toBeFalse();
    expect('sede' in cuerpo).toBeFalse();
    expect('sedePrincipalId' in cuerpo).toBeFalse();
    expect(cuerpo['rolFamiliar']).toBe('NINGUNO');
    const d = cuerpo['deportesInicial'][0];
    expect(d.fechaAltaInicial).toBe('2026-09-01');
    expect(d.categoria).toBe('');
    expect(d.fechaAltaCompeticion).toBeNull();
    expect(d.fechaAltaCompetidorInicial).toBeNull();
    expect(d.peso).toBeNull();
    expect(cuerpoAlta(crudo({ nif: '  ', responsableLegalNif: '' }), reglas, false)['nif']).toBeNull();
  });

  it('competidor: sin licencia en una disciplina que la pide, no compite; con licencia, rellena fechas y categoría', () => {
    const sinLicencia = cuerpoAlta(crudo({ deportesInicial: [deporte({ competidor: true, tieneLicencia: false })] }), reglas, false);
    expect(sinLicencia['deportesInicial'][0].competidor).toBeFalse();

    const con = cuerpoAlta(
      crudo({ deportesInicial: [deporte({ competidor: true, tieneLicencia: true, fechaAltaInicial: '2025-01-10', peso: '31' })] }),
      reglas,
      false
    )['deportesInicial'][0];
    expect(con.competidor).toBeTrue();
    expect(con.fechaAltaCompeticion).toBe('2026-09-01');
    expect(con.fechaAltaCompetidorInicial).toBe('2025-01-10');
    expect(con.categoria).toBe('Infantil');
    expect(con.peso).toBe(31);
  });

  it('con varias sedes, la sede elegida va como sedePrincipalId (número)', () => {
    expect(cuerpoAlta(crudo({ sede: '7' }), reglas, true)['sedePrincipalId']).toBe(7);
    expect('sedePrincipalId' in cuerpoAlta(crudo({ sede: '7' }), reglas, false)).toBeFalse();
  });

  it('texto y destino del error del servidor', () => {
    expect(textoErrorAlta({ error: 'El alumno con NIF 1 ya existe.' })).toBe('El alumno con NIF 1 ya existe.');
    expect(textoErrorAlta({ error: { mensaje: 'Foto no válida' } })).toBe('Foto no válida');
    expect(textoErrorAlta({ error: null })).toBe('No has completado todos los campos requeridos');
    const deportes = [{ codigo: 'TAEKWONDO', nombre: 'Taekwondo' }, { codigo: 'PILATES', nombre: 'Pilates' }];
    expect(destinoErrorServidor(409, 'El alumno con NIF 1 ya existe.', deportes)).toEqual({ tipo: 'persona', campo: 'nif' });
    expect(destinoErrorServidor(400, 'Indica la sede.', deportes)).toEqual({ tipo: 'persona', campo: 'sede' });
    expect(destinoErrorServidor(400, 'El deporte PILATES está repetido en el alta.', deportes)).toEqual({ tipo: 'deporte', indice: 1 });
    expect(destinoErrorServidor(400, 'Error al procesar la solicitud', deportes)).toBeNull();
  });

  it('textos del resumen y del botón de añadir', () => {
    expect(tituloFaltan(1)).toBe('Falta 1 dato');
    expect(tituloFaltan(3)).toBe('Faltan 3 datos');
    expect(textoAnadirDeporte(1)).toBe('Añadir otro deporte (1 de 4)');
    expect(fechaHoy(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
