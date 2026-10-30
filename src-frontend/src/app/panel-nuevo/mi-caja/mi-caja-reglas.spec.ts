// Reglas puras de Mi caja (cargos y pedidos). Venían de las pruebas de la pantalla de hoy, retirada en 8b-Z.
import { CargoPendienteProfesor } from '../../interfaces/caja';
import { Solicitud } from '../../interfaces/solicitud';
import { agruparPorAlumno } from './mi-caja-cargos';
import { estadoCobroEncargo, indicePasoEncargo, requiereAccion } from './mi-caja-pedidos';

function cargo(cargoId: number, alumnoId: number, nombre: string, apellidos: string, concepto: string, importe: number): CargoPendienteProfesor {
  return {
    cargoId, alumnoId, alumnoNombre: nombre, alumnoApellidos: apellidos, concepto, importe,
    fechaAsignacion: '2026-10-01T10:00:00.000Z', sedeId: 1, sedeNombre: 'Centro', vencido: false, grupos: [],
  };
}

const CARGOS = [
  cargo(1, 3, 'Lucía', 'Prado', 'Mensualidad octubre', 35),
  cargo(2, 5, 'Andrés', 'Gil', 'Mensualidad octubre', 30),
  cargo(3, 3, 'Lucía', 'Prado', 'Camiseta', 12.5),
];

function solicitud(id: number, extra: Partial<Solicitud> = {}): Solicitud {
  return {
    id, tipo: 'ENCARGO', estado: 'PEDIDO', sedeId: 1, sedeNombre: 'Sede Norte', solicitadoPorUsuarioId: 8,
    solicitadoPorNombre: 'Profe', solicitadoEn: '2026-10-18T15:30:00.000Z', motivo: 'Talla 140',
    resueltoPorUsuarioId: null, resueltoPorNombre: null, resueltoEn: null, respuesta: null, cobroId: null,
    entregaCajaId: null, movimientoCajaId: null, productoAlumnoId: 100 + id, productoId: 4, alumnoId: 3,
    alumnoNombre: 'Alumna', alumnoApellidos: 'Uno', concepto: 'Dobok', importe: 25, motivoDeshacer: null,
    encargadoEn: null, recibidoEn: null, entregadoEn: null, devolucionPendiente: false, propia: true,
    movimientoDevolucionId: null, devolucionEnSede: null, estadoCobro: 'PENDIENTE', ...extra,
  };
}

const SOLICITUDES: Solicitud[] = [
  solicitud(1), // PEDIDO, cargo pendiente
  solicitud(2, { estado: 'RECIBIDO', estadoCobro: 'COBRADO' }), // requiere su acción
  solicitud(3, {
    estado: 'RECHAZADO', respuesta: 'Sin existencias', devolucionPendiente: true, devolucionEnSede: false,
    estadoCobro: 'COBRADO',
  }),
  solicitud(4, { estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: true, estadoCobro: 'COBRADO' }),
  solicitud(5, { estado: 'RETIRADA', movimientoDevolucionId: 70, estadoCobro: 'DEVUELTO' }),
  solicitud(6, {
    tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'PENDIENTE', motivo: 'Lo cobré dos veces', cobroId: 50,
    concepto: 'Camiseta', importe: 12.5,
  }),
  solicitud(7, {
    tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'DENEGADA', motivo: 'Error', respuesta: 'Ya lo comprobé', cobroId: 51,
  }),
];

describe('Mi caja: reglas de cargos y pedidos', () => {
  it('agrupa los cargos por alumno, por nombre, y filtra sin tildes', () => {
    const grupos = agruparPorAlumno(CARGOS);
    expect(grupos.map((g) => g.nombre)).toEqual(['Andrés Gil', 'Lucía Prado']);
    expect(grupos[1].cargos.map((c) => c.cargoId)).toEqual([1, 3]);

    expect(agruparPorAlumno(CARGOS, 'lucia').map((g) => g.nombre)).toEqual(['Lucía Prado']);
  });

  it('la línea de grupos es por alumno: nombre y horario, separados por coma; sin grupos, vacía', () => {
    const turno = { id: 1, diaSemana: 'Lunes', horaInicio: '18:00', horaFin: '19:00' };
    const conGrupos = [{ ...CARGOS[0], grupos: [{ nombre: 'Infantil', turnos: [turno] }, { nombre: 'Adultos', turnos: [] }] }];
    expect(agruparPorAlumno(conGrupos)[0].lineaGrupos).toBe('Infantil · L 18:00, Adultos · Sin turnos');
    expect(agruparPorAlumno(CARGOS)[0].lineaGrupos).toBe('');
  });

  it('línea de estados: el paso de cada estado; un encargo caído no tiene paso', () => {
    expect(indicePasoEncargo(solicitud(1))).toBe(0);
    expect(indicePasoEncargo(solicitud(1, { estado: 'ENCARGADO' }))).toBe(1);
    expect(indicePasoEncargo(solicitud(1, { estado: 'RECIBIDO' }))).toBe(2);
    expect(indicePasoEncargo(solicitud(1, { estado: 'ENTREGADO' }))).toBe(3);
    expect(indicePasoEncargo(solicitud(1, { estado: 'RECHAZADO' }))).toBe(-1);
  });

  it('estado del cobro: el que da la API, solo en un encargo', () => {
    expect(estadoCobroEncargo(solicitud(1))).toBe('PENDIENTE');
    expect(estadoCobroEncargo(SOLICITUDES[4])).toBe('DEVUELTO');
    expect(estadoCobroEncargo(solicitud(8, { estado: 'RETIRADA', estadoCobro: 'CANCELADO' }))).toBe('CANCELADO');
    expect(estadoCobroEncargo(solicitud(9, { estadoCobro: null }))).toBeNull();
    expect(estadoCobroEncargo(solicitud(10, { tipo: 'DESHACER_COBRO_ENTREGADO', estadoCobro: 'COBRADO' }))).toBeNull();
  });

  it('requiere su acción: recibido, o devolución pendiente que lleva él encima', () => {
    expect(SOLICITUDES.filter(requiereAccion).map((s) => s.id)).toEqual([2, 3]);
  });
});
