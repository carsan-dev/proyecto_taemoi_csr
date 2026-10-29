import { calcularEntrega, formatearEuros, importeConSignoTexto } from '../../componentes/endpoints/caja-sede/caja-calculos';
import { valorDeHoy } from '../../testing/oraculo-hoy';
import { HOY } from './caja-sede-vista.hoy';
import { CobroCaja, EntregaCaja, MovimientoCaja, MovimientoDeEntrega, PendienteCobrador } from '../../interfaces/caja';
import {
  CLAVE_ULTIMA_SEDE,
  PESTANAS,
  accionEntrega,
  accionMovimiento,
  avisoCuadre,
  avisoEfectivo,
  cifrasResumen,
  construirPeriodos,
  cuadre,
  datosPedirEntrega,
  datosPedirMovimiento,
  diferenciaVista,
  etiquetaPestana,
  fechaHora,
  firmado,
  mensajeAnularEntrega,
  mensajeAnularMovimiento,
  nombreAlumnoCobro,
  numeroPestana,
  textoDesde,
  textoMostrando,
  textoMovimientoDeCobro,
  tonoImporte,
} from './caja-sede-vista';
import { eur } from '../formato/formato-es';

/** Sin espacios duros: Intl separa el símbolo con U+00A0 y la vista con el mismo (se comparan iguales). */
const plano = (t: string): string => t.replace(/[  ]/g, ' ');

export const cobroVista = (cambios: Partial<CobroCaja> = {}): CobroCaja => ({
  cobroId: 1, cargoId: 11, alumnoId: 21, alumnoNombre: 'Ana', alumnoApellidos: 'Prueba', concepto: 'MENSUALIDAD', importe: 30,
  fechaCobro: '2026-10-10T08:00:00.000Z', sedeId: 5, ...cambios,
});

export const profesorVista = (cambios: Partial<PendienteCobrador> = {}): PendienteCobrador => ({
  usuarioId: 9, nombre: 'Profe Prueba', numeroCobros: 2, importe: 45, cobroMasAntiguo: '2026-10-14T08:00:00.000Z',
  cobros: [cobroVista(), cobroVista({ cobroId: 2, cargoId: 12, alumnoId: 22, alumnoNombre: 'Leo', concepto: 'LICENCIA', importe: 15 })],
  ...cambios,
});

export const entregaVista = (cambios: Partial<EntregaCaja> = {}): EntregaCaja => ({
  id: 7, sedeId: 5, entregadoPorUsuarioId: 9, entregadoPorNombre: 'Profe Prueba', recibidoPorUsuarioId: 3,
  recibidoPorNombre: 'Gestora Prueba', importeDeclarado: 20, importeContado: 19, diferencia: -1,
  fecha: '2026-10-12T16:05:00.000Z', nota: 'faltaba 1', estado: 'ACTIVA', cobroIds: [5], movimientoId: 4,
  anuladaPorUsuarioId: null, anuladaEn: null, motivoAnulacion: null, autoentrega: false, movimientosDeCobro: [], ...cambios,
});

export const movimientoVista = (cambios: Partial<MovimientoCaja> = {}): MovimientoCaja => ({
  id: 5, sedeId: 5, tipo: 'OTRO', importe: 10, alumnoId: null, entregaCajaId: null, nota: 'cambio', creadoPorUsuarioId: 3,
  fecha: '2026-10-13T08:00:00.000Z', estado: 'ACTIVO', anuladoPorUsuarioId: null, anuladoEn: null, motivoAnulacion: null, ...cambios,
});

// «De hoy»: lo que daba la pantalla anterior (retirada en 8b-Z), grabado con ella en `HOY` (caja-sede-vista.hoy.ts).
const IMPORTES = [0, 0.004, 0.005, 0.01, 0.1, 0.105, 1, 1.005, 9.99, 12.345, 45.5, 999.995, 1000, 1240, 12345.6, 1234567.89, -0.01, -1, -1240.5];

describe('Caja de sede del panel nuevo: vista pura (8b-3j)', () => {
  it('las tres pestañas de P2, en su orden, abriendo en «Por recibir»', () => {
    expect(PESTANAS).toEqual(['recibir', 'entregas', 'movimientos']);
    expect(PESTANAS.map(etiquetaPestana)).toEqual(['Por recibir', 'Entregas', 'Movimientos']);
  });

  it('el número de cada pestaña: profesores con efectivo, entregas en total y movimientos del periodo; nada sin cargar', () => {
    const datos = { pendientes: [profesorVista(), profesorVista({ usuarioId: 10 })], totalEntregas: 51, movimientos: [movimientoVista()] };
    expect(numeroPestana('recibir', datos)).toBe(2);
    expect(numeroPestana('entregas', datos)).toBe(51);
    expect(numeroPestana('movimientos', datos)).toBe(1);
    const vacio = { pendientes: null, totalEntregas: null, movimientos: null };
    expect(PESTANAS.map((p) => numeroPestana(p, vacio))).toEqual([null, null, null]);
    expect(numeroPestana('recibir', { ...vacio, pendientes: [] })).toBe(0);
  });

  it('importes con eur (es-ES) iguales a los de hoy, céntimo a céntimo; el firmado, con el mismo signo que hoy', () => {
    for (const importe of IMPORTES) {
      expect(plano(eur(importe))).withContext(String(importe)).toBe(plano(formatearEuros(importe)));
      expect(plano(firmado(importe))).withContext(String(importe)).toBe(plano(importeConSignoTexto(importe)));
    }
    expect(plano(firmado(-1))).toBe('−1,00 €');
    expect(plano(firmado(50))).toBe('+50,00 €');
    expect(plano(firmado(0))).toBe('0,00 €');
    expect(tonoImporte(10)).toBe('entra');
    expect(tonoImporte(-0.01)).toBe('sale');
    expect(tonoImporte(0.004)).toBeNull();
  });

  it('el resumen: saldo, pendiente de entregar (ocre), recibido en el periodo y diferencias con signo, con los textos de hoy', () => {
    const cifras = cifrasResumen(
      { sedeId: 5, desde: '2026-10-01', hasta: '2026-10-31', pendienteDeEntregar: 45, recibidoEnPeriodo: 1240, diferenciasEnPeriodo: -1, saldoCaja: 28 },
      'Octubre 2026'
    );
    expect(cifras.map((c) => [c.titulo, plano(c.valor), c.detalle, c.tono])).toEqual([
      ['Saldo de caja', '28,00 €', 'Efectivo que debería haber en la sede', null],
      ['Pendiente de entregar', '45,00 €', 'En mano de los profesores', 'atencion'],
      ['Recibido este periodo', '1.240,00 €', 'Octubre 2026', null],
      ['Diferencias', '−1,00 €', 'Sobrantes y faltantes de Octubre 2026', 'peligro'],
    ]);
    expect(cifrasResumen({ sedeId: 5, desde: '', hasta: '', pendienteDeEntregar: 0, recibidoEnPeriodo: 0, diferenciasEnPeriodo: 0.5, saldoCaja: 0 }, 'X')[3].tono)
      .toBe('bien');
    expect(cifrasResumen(null, 'Octubre 2026').map((c) => c.valor)).toEqual(['—', '—', '—', '—']);
  });

  it('el tono de las diferencias del resumen sigue el de hoy (claseDiferenciasResumen) en céntimos', () => {
    for (const d of IMPORTES) {
      const clase = valorDeHoy<string>(HOY);
      const tono = cifrasResumen({ sedeId: 1, desde: '', hasta: '', pendienteDeEntregar: 0, recibidoEnPeriodo: 0, diferenciasEnPeriodo: d, saldoCaja: 0 }, '')[3].tono;
      expect(tono).withContext(String(d)).toBe(clase === 'valor-negativo' ? 'peligro' : clase === 'valor-positivo' ? 'bien' : null);
    }
  });

  it('la diferencia de cada entrega: la de hoy (descontando la devolución activa) con «cuadra», «falta» y «sobra»', () => {
    const casos: EntregaCaja[] = [
      entregaVista(),
      entregaVista({ diferencia: 0 }),
      entregaVista({ diferencia: 0.5 }),
      entregaVista({ diferencia: 0.004 }),
      entregaVista({ diferencia: -25, movimientosDeCobro: [{ id: 41, tipo: 'DEVOLUCION', importe: -25, cobroId: 10, estado: 'ACTIVO' }] }),
      entregaVista({ diferencia: -25, movimientosDeCobro: [{ id: 42, tipo: 'DEVOLUCION', importe: -25, cobroId: 11, estado: 'ANULADO' }] }),
      entregaVista({ diferencia: 0, movimientosDeCobro: [{ id: 40, tipo: 'ERROR', importe: 12, cobroId: 9, estado: 'ACTIVO' }] }),
      entregaVista({ diferencia: -10, movimientosDeCobro: [{ id: 43, tipo: 'DEVOLUCION', importe: -4, cobroId: 12, estado: 'ACTIVO' }] }),
      { ...entregaVista({ diferencia: -2 }), movimientosDeCobro: undefined },
    ];
    for (const e of casos) {
      const d = diferenciaVista(e);
      expect(d).withContext(JSON.stringify(e.movimientosDeCobro)).toBe(valorDeHoy(HOY));
      expect(plano(cuadre(d).texto)).toBe(plano(valorDeHoy(HOY)));
      const clase = valorDeHoy<string>(HOY);
      expect(cuadre(d).estado).toBe(clase === 'chip-cuadra' ? 'cuadra' : clase === 'chip-falta' ? 'falta' : 'sobra');
    }
    expect(plano(cuadre(-1).texto)).toBe('falta 1,00 €');
  });

  it('las correcciones de una entrega, con el texto de hoy', () => {
    const movs: MovimientoDeEntrega[] = [
      { id: 1, tipo: 'ERROR', importe: 12, cobroId: 9, estado: 'ACTIVO' },
      { id: 2, tipo: 'DEVOLUCION', importe: -25, cobroId: 10, estado: 'ACTIVO' },
      { id: 3, tipo: 'SOBRANTE', importe: 0, cobroId: null, estado: 'ANULADO' },
      { id: 4, tipo: 'OTRO', importe: -3.5, cobroId: null, estado: 'ACTIVO' },
    ];
    for (const m of movs) {
      expect(plano(textoMovimientoDeCobro(m))).toBe(plano(valorDeHoy(HOY)));
    }
    expect(plano(textoMovimientoDeCobro(movs[0]))).toBe('Cobro deshecho +12,00 €');
  });

  it('cálculo de «Recibir entrega»: lo de hoy (calcularEntrega) con sus avisos de falta, sobra e importe no válido', () => {
    const cobros = profesorVista().cobros;
    const todos = new Set([1, 2]);
    expect(avisoCuadre(calcularEntrega(cobros, todos, '45,00'))).toBeNull();
    expect(plano(avisoCuadre(calcularEntrega(cobros, todos, '44'))!.texto)).toBe('Faltan 1,00 €. Se apuntará un faltante en la caja.');
    expect(avisoCuadre(calcularEntrega(cobros, todos, '44'))!.tono).toBe('falta');
    expect(plano(avisoCuadre(calcularEntrega(cobros, todos, '46,5'))!.texto)).toBe('Sobran 1,50 €. Se apuntará un sobrante en la caja.');
    expect(avisoCuadre(calcularEntrega(cobros, todos, 'abc'))!.texto).toBe('Escribe un importe válido, por ejemplo 45,50.');
    expect(avisoCuadre(calcularEntrega(cobros, todos, '-1'))!.tono).toBe('invalido');
    // Nota obligatoria solo si no cuadra al céntimo (la de hoy, sin copiarla).
    expect(calcularEntrega(cobros, todos, '45,001').contado).toBeNull();
    expect(calcularEntrega(cobros, todos, '45,01').notaObligatoria).toBeTrue();
    expect(calcularEntrega(cobros, todos, '1.045,00').notaObligatoria).toBeTrue();
    expect(calcularEntrega(cobros, new Set([1]), '30').notaObligatoria).toBeFalse();
  });

  it('anular frente a pedir anular, por rol y estado: la tabla de hoy en todos los casos', () => {
    let casos = 0;
    for (const propietario of [true, false]) {
      for (const estado of ['ACTIVA', 'ANULADA'] as const) {
        const e = entregaVista({ estado });
        const esperada = valorDeHoy(HOY);
        expect(accionEntrega(e, propietario)).withContext(`${propietario} ${estado}`).toBe(esperada);
        casos++;
      }
      for (const estado of ['ACTIVO', 'ANULADO'] as const) {
        for (const entregaCajaId of [null, 7]) {
          const m = movimientoVista({ estado, entregaCajaId });
          const esperada = valorDeHoy(HOY);
          expect(accionMovimiento(m, propietario)).withContext(`${propietario} ${estado} ${entregaCajaId}`).toBe(esperada);
          casos++;
        }
      }
    }
    expect(casos).toBe(12);
    expect(accionEntrega(entregaVista(), true)).toBe('anular');
    expect(accionEntrega(entregaVista(), false)).toBe('pedir');
    expect(accionMovimiento(movimientoVista({ entregaCajaId: 7 }), true)).toBeNull();
  });

  it('«Mostrando X de Y entregas» al pie, con cifras es-ES', () => {
    expect(textoMostrando(25, 51)).toBe('Mostrando 25 de 51 entregas');
    expect(textoMostrando(1, 1)).toBe('Mostrando 1 de 1 entregas');
    expect(textoMostrando(25, 1044)).toBe('Mostrando 25 de 1.044 entregas');
  });

  it('desde cuándo tiene el profesor el efectivo y el aviso de más de 7 días, como hoy', () => {
    const hoy = new Date(2026, 9, 23, 12, 0);
    const hace = (dias: number) => new Date(2026, 9, 23 - dias, 9, 0).toISOString();
    expect(textoDesde(profesorVista({ cobroMasAntiguo: hace(0) }), hoy)).toBe('hoy');
    expect(textoDesde(profesorVista({ cobroMasAntiguo: hace(1) }), hoy)).toBe('ayer');
    expect(textoDesde(profesorVista({ cobroMasAntiguo: hace(9) }), hoy)).toBe('hace 9 días');
    expect(textoDesde(profesorVista({ cobroMasAntiguo: null }), hoy)).toBe('—');
    expect(avisoEfectivo(profesorVista({ cobroMasAntiguo: hace(7) }), hoy)).toBeFalse();
    expect(avisoEfectivo(profesorVista({ cobroMasAntiguo: hace(8) }), hoy)).toBeTrue();
    expect(avisoEfectivo(profesorVista({ cobroMasAntiguo: null }), hoy)).toBeFalse();
  });

  it('periodos y clave de la última sede: los de hoy', () => {
    for (const fecha of [new Date(2026, 0, 15), new Date(2026, 9, 31), new Date(2024, 1, 29)]) {
      expect(construirPeriodos(fecha)).toEqual(valorDeHoy(HOY));
    }
    expect(construirPeriodos(new Date(2026, 0, 15), 3).map((p) => p.etiqueta)).toEqual(['Enero 2026', 'Diciembre 2025', 'Noviembre 2025']);
    expect(CLAVE_ULTIMA_SEDE).toBe(valorDeHoy(HOY));
  });

  it('confirmaciones: los textos de anular de hoy', () => {
    const e = entregaVista({ entregadoPorNombre: null, importeContado: 1240 });
    const m = movimientoVista({ tipo: 'FALTANTE', importe: -1 });
    expect(plano(mensajeAnularEntrega(e))).toBe(plano(valorDeHoy(HOY)));
    expect(plano(mensajeAnularMovimiento(m))).toBe(plano(valorDeHoy(HOY)));
  });

  it('pedir anulación: los datos de hoy (fecha en es-ES)', () => {
    const e = entregaVista({ fecha: new Date(2026, 9, 12, 18, 0).toISOString() });
    expect(datosPedirEntrega(e).map(([k, v]) => [k, plano(v)])).toEqual([
      ['Entregó', 'Profe Prueba'], ['Recibió', 'Gestora Prueba'], ['Contado', '19,00 €'], ['Fecha', '12/10/2026'],
    ]);
    const m = movimientoVista({ fecha: new Date(2026, 9, 13, 10, 0).toISOString(), importe: -1, tipo: 'FALTANTE', nota: null });
    expect(datosPedirMovimiento(m).map(([k, v]) => [k, plano(v)])).toEqual([
      ['Tipo', 'Faltante'], ['Importe', '−1,00 €'], ['Fecha', '13/10/2026'], ['Nota', ''],
    ]);
  });

  it('fecha y hora es-ES de la tabla y el nombre del alumno de un cobro', () => {
    expect(fechaHora(new Date(2026, 9, 12, 18, 5).toISOString())).toBe('12/10/2026 18:05');
    expect(fechaHora('')).toBe('');
    expect(nombreAlumnoCobro(cobroVista())).toBe('Ana Prueba');
    expect(nombreAlumnoCobro(cobroVista({ alumnoNombre: null, alumnoApellidos: null }))).toBe('—');
  });
});
