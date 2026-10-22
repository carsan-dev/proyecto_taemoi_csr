import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import {
  antiguedad,
  estadoLicencia,
  gradoFila,
  gradoParaFrase,
  iniciales,
  ordenarDeportes,
  tiempoConGrado,
} from './alumno-vista';

function deporte(parcial: Partial<AlumnoDeporteDTO>): AlumnoDeporteDTO {
  return {
    id: 1,
    deporte: 'TAEKWONDO',
    grado: 'NARANJA',
    fechaGrado: null,
    aptoParaExamen: false,
    activo: true,
    principal: false,
    fechaAlta: '2024-10-10',
    fechaAltaInicial: null,
    fechaBaja: null,
    antiguedad: null,
    tipoTarifa: 'ADULTO',
    cuantiaTarifa: 30,
    rolFamiliar: null,
    grupoFamiliar: null,
    competidor: false,
    fechaAltaCompeticion: null,
    fechaAltaCompetidorInicial: null,
    antiguedadCompetidor: null,
    categoria: null,
    peso: null,
    fechaPeso: null,
    tieneLicencia: false,
    numeroLicencia: null,
    fechaLicencia: null,
    ...parcial,
  };
}

describe('alumno-vista (8b-3a, listado de alumnos)', () => {
  const hoy = new Date(2026, 9, 22);

  it('iniciales del nombre y del primer apellido, con tilde', () => {
    expect(iniciales('Álvaro', 'Zamora Palomares')).toBe('ÁZ');
    expect(iniciales('ana maría', 'vidal')).toBe('AV');
    expect(iniciales('Leo', '')).toBe('L');
  });

  it('antigüedad con plurales en singular y en plural', () => {
    expect(antiguedad('2025-10-22', hoy)).toBe('1 año');
    expect(antiguedad('2024-10-01', hoy)).toBe('2 años');
    expect(antiguedad('2025-09-10', hoy)).toBe('1 año y 1 mes');
    expect(antiguedad('2023-02-01', hoy)).toBe('3 años y 8 meses');
    expect(antiguedad('2026-05-30', hoy)).toBe('4 meses');
    expect(antiguedad('2026-10-01', hoy)).toBe('Menos de 1 mes');
    expect(antiguedad(null, hoy)).toBe('');
  });

  it('tiempo con el grado: años y meses, o días por debajo del mes', () => {
    expect(tiempoConGrado('2025-08-22', hoy)).toBe('1 año y 2 meses');
    expect(tiempoConGrado('2026-10-21', hoy)).toBe('1 día');
    expect(tiempoConGrado('2026-10-10', hoy)).toBe('12 días');
    expect(tiempoConGrado('2026-10-22', hoy)).toBe('Desde hoy');
    expect(tiempoConGrado(undefined, hoy)).toBe('');
  });

  it('la principal primero y el resto por fecha de alta, como hoy', () => {
    const lista = ordenarDeportes([
      deporte({ id: 3, deporte: 'PILATES', fechaAlta: '2020-01-01' }),
      deporte({ id: 2, deporte: 'KICKBOXING', principal: true, fechaAlta: '2025-01-01' }),
      deporte({ id: 1, deporte: 'TAEKWONDO', fechaAltaInicial: '2019-01-01' }),
    ]);
    expect(lista.map((d) => d.deporte)).toEqual(['KICKBOXING', 'TAEKWONDO', 'PILATES']);
    // Sin principal marcada, la más antigua hace de principal.
    expect(ordenarDeportes([deporte({ id: 3, fechaAlta: '2024-01-01' }), deporte({ id: 4, fechaAlta: '2022-01-01' })])
      .map((d) => d.id)).toEqual([4, 3]);
  });

  it('licencia: no aplica, sin licencia, en vigor o caducada (año anterior), como hoy', () => {
    expect(estadoLicencia(deporte({}), false, hoy)).toEqual({ clave: 'no-aplica', texto: 'No aplica' });
    expect(estadoLicencia(deporte({ tieneLicencia: false }), true, hoy)).toEqual({ clave: 'sin', texto: 'Sin licencia' });
    expect(estadoLicencia(deporte({ tieneLicencia: true, fechaLicencia: '2026-01-15' }), true, hoy))
      .toEqual({ clave: 'con', texto: 'Con licencia' });
    expect(estadoLicencia(deporte({ tieneLicencia: true, fechaLicencia: '2025-12-31' }), true, hoy))
      .toEqual({ clave: 'caducada', texto: 'Caducada' });
    expect(estadoLicencia(null, true, hoy)).toBeNull();
  });
});

describe('alumno-vista: grado compartido por el listado y el carnet (8b-3b)', () => {
  it('gradoFila: en minúscula para la línea 2, el texto de hoy para el detalle y el fondo del cinturón', () => {
    const blanco = gradoFila('BLANCO', 'TAEKWONDO');
    expect(blanco.nombre).toBe('blanco');
    expect(blanco.nombreDetalle).toBe('BLANCO');
    expect(blanco.fondo.toUpperCase()).toBe('#FFFFFF');
    const raro = gradoFila('ROJO_NEGRO_X', 'TAEKWONDO');
    expect(raro.nombre).toBe('rojo negro x');
    expect(raro.nombreDetalle).toBe('ROJO NEGRO X');
  });

  it('gradoParaFrase: el nombre con mayúscula inicial («Blanco», «Rojo negro x»)', () => {
    expect(gradoParaFrase(gradoFila('BLANCO', 'TAEKWONDO'))).toBe('Blanco');
    expect(gradoParaFrase(gradoFila('ROJO_NEGRO_X', 'TAEKWONDO'))).toBe('Rojo negro x');
  });
});
