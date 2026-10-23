import { describirAccion as describirAccionDeHoy } from '../../../componentes/endpoints/auditoria-sistema/auditoria-sistema.component';
import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import {
  agruparPorDia,
  describirAccion,
  fechaCompleta,
  filtrosParaConsulta,
  filtrosVacios,
  formatearJson,
  hora,
  numeroFiltrosActivos,
  textoCuenta,
  tieneDatosTecnicos,
} from './registro-vista';

export function eventoVista(cambios: Partial<AuditoriaEvento> = {}): AuditoriaEvento {
  return {
    id: 1,
    fechaEvento: '2026-10-23T10:42:00',
    accion: 'UPDATE',
    metodoHttp: 'PUT',
    endpoint: '/api/alumnos/7',
    modulo: 'alumnos',
    recursoId: 7,
    estadoHttp: 200,
    usuarioId: 12,
    usuarioEmail: 'persona@ejemplo.test',
    usuarioNombre: 'Persona Ficticia',
    resumen: 'Cambio en la ficha de un alumno',
    payloadTruncado: false,
    ...cambios,
  };
}

describe('registro-vista (8b-3k)', () => {
  it('la pastilla de cada acción es la de hoy', () => {
    const casos: [string | null, string | null][] = [
      ['CREATE', 'alumnos'], ['UPDATE', null], ['DELETE', 'grupos'], ['READ', 'salud'], ['READ', 'SALUD'], ['READ', 'alumnos'],
      ['otra', 'x'], [null, null], ['create', 'x'],
    ];
    for (const [accion, modulo] of casos) {
      expect(describirAccion(accion, modulo)).toEqual(describirAccionDeHoy(accion, modulo));
    }
  });

  it('el filtro va a la API como hoy: resultado EXITO, «Todas» sin acción y salud fijo con las consultas', () => {
    expect(filtrosParaConsulta(filtrosVacios())).toEqual({
      desde: null, hasta: null, resultado: 'EXITO', accion: null, modulo: null, usuario: null, texto: null,
    });
    expect(
      filtrosParaConsulta({ desde: '2026-10-01', hasta: '2026-10-23', accion: 'READ', modulo: 'alumnos', usuario: 'ana', texto: 'cinto' })
    ).toEqual({ desde: '2026-10-01', hasta: '2026-10-23', resultado: 'EXITO', accion: 'READ', modulo: 'salud', usuario: 'ana', texto: 'cinto' });
    expect(filtrosParaConsulta({ ...filtrosVacios(), accion: 'DELETE', modulo: 'grupos' }).modulo).toBe('grupos');
  });

  it('cuenta los filtros que cambian la consulta', () => {
    expect(numeroFiltrosActivos(filtrosVacios())).toBe(0);
    expect(numeroFiltrosActivos({ ...filtrosVacios(), desde: '2026-10-01', modulo: 'grupos', texto: ' ' })).toBe(2);
    expect(numeroFiltrosActivos({ ...filtrosVacios(), accion: 'READ', modulo: 'grupos' })).toBe(1);
  });

  it('«Cuenta N» con el identificador, nunca el nombre ni el email', () => {
    expect(textoCuenta(12)).toBe('Cuenta 12');
    expect(textoCuenta(1044)).toBe('Cuenta 1044');
    expect(textoCuenta(null)).toBe('Sin cuenta');
  });

  it('agrupa por día local en el orden de llegada: Hoy, Ayer y la fecha larga', () => {
    const ahora = new Date(2026, 9, 23, 12, 0);
    const dias = agruparPorDia(
      [
        eventoVista({ id: 1, fechaEvento: '2026-10-23T10:42:00' }),
        eventoVista({ id: 2, fechaEvento: '2026-10-23T08:05:00' }),
        eventoVista({ id: 3, fechaEvento: '2026-10-22T19:03:00' }),
        eventoVista({ id: 4, fechaEvento: '2026-10-20T07:00:00' }),
      ],
      ahora
    );
    expect(dias.map((d) => [d.titulo, d.fecha, d.eventos.map((e) => e.id)])).toEqual([
      ['Hoy', 'Viernes, 23 de octubre de 2026', [1, 2]],
      ['Ayer', 'Jueves, 22 de octubre de 2026', [3]],
      ['Martes, 20 de octubre de 2026', '', [4]],
    ]);
    expect(hora('2026-10-23T08:05:00')).toBe('08:05');
    expect(fechaCompleta('2026-10-23T08:05:09')).toBe('23/10/2026 08:05:09');
  });

  it('datos técnicos y JSON como hoy', () => {
    expect(tieneDatosTecnicos(null)).toBeFalse();
    const detalle = { ...eventoVista(), ipCliente: null, userAgent: null, queryParamsJson: null, payloadJson: null };
    expect(tieneDatosTecnicos(detalle)).toBeFalse();
    expect(tieneDatosTecnicos({ ...detalle, payloadJson: '{}' })).toBeTrue();
    expect(formatearJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(formatearJson('no es json')).toBe('no es json');
    expect(formatearJson(null)).toBe('-');
  });
});
