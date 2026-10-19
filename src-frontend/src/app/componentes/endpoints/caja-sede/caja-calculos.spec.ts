import { CobroCaja } from '../../../interfaces/caja';
import {
  calcularEntrega,
  claseImporte,
  importeConSignoTexto,
  diasDesde,
  estadoSeleccion,
  formatearEuros,
  importeConSigno,
  importeParaCampo,
  leerImporte,
  reglaMovimiento,
  textoRelativo,
} from './caja-calculos';

function cobro(cobroId: number, importe: number): CobroCaja {
  return {
    cobroId, cargoId: cobroId, alumnoId: 1, alumnoNombre: 'A', alumnoApellidos: 'B', concepto: 'C', importe,
    fechaCobro: '2026-10-01T10:00:00.000+00:00', sedeId: 1,
  };
}

describe('caja-calculos', () => {
  it('formatea euros en es-ES con miles', () => {
    const limpio = (t: string) => t.replace(/ | /g, ' ');
    expect(limpio(formatearEuros(1240))).toBe('1.240,00 €');
    expect(limpio(formatearEuros(12345.5))).toBe('12.345,50 €');
    expect(limpio(formatearEuros(9.5))).toBe('9,50 €');
    expect(limpio(formatearEuros(-1240))).toBe('-1.240,00 €');
  });

  it('lee importes con coma o punto y como mucho dos decimales', () => {
    expect(leerImporte('12,5')).toBe(12.5);
    expect(leerImporte('12.50')).toBe(12.5);
    expect(leerImporte('1.240,00')).toBe(1240);
    expect(leerImporte('45 €')).toBe(45);
    expect(leerImporte('1,234')).toBeNull();
    expect(leerImporte('abc')).toBeNull();
    expect(leerImporte('')).toBeNull();
    expect(leerImporte('-5')).toBeNull();
    expect(importeParaCampo(45)).toBe('45,00');
  });

  describe('calcularEntrega', () => {
    const cobros = [cobro(1, 30), cobro(2, 15.1), cobro(3, 0.2)];

    it('cuadra cuando lo contado es la suma de lo marcado (sin errores de coma flotante)', () => {
      const r = calcularEntrega(cobros, new Set([1, 2, 3]), '45,30');
      expect(r.declarado).toBe(45.3);
      expect(r.diferencia).toBe(0);
      expect(r.estado).toBe('cuadra');
      expect(r.notaObligatoria).toBeFalse();
    });

    it('faltante: contado menor, diferencia negativa y nota obligatoria', () => {
      const r = calcularEntrega(cobros, new Set([1, 2]), '44,10');
      expect(r.declarado).toBe(45.1);
      expect(r.diferencia).toBe(-1);
      expect(r.estado).toBe('falta');
      expect(r.notaObligatoria).toBeTrue();
    });

    it('sobrante: contado mayor', () => {
      const r = calcularEntrega(cobros, new Set([1]), '31');
      expect(r.diferencia).toBe(1);
      expect(r.estado).toBe('sobra');
      expect(r.notaObligatoria).toBeTrue();
    });

    it('desmarcar cambia lo declarado; contado no válido no inventa diferencia', () => {
      expect(calcularEntrega(cobros, new Set(), '0').declarado).toBe(0);
      const r = calcularEntrega(cobros, new Set([1]), 'x');
      expect(r.contado).toBeNull();
      expect(r.diferencia).toBe(0);
      expect(calcularEntrega(cobros, new Set([1]), '-5').contado).toBeNull();
    });
  });

  it('días relativos: hoy, ayer, hace n días', () => {
    const hoy = new Date(2026, 9, 19, 9, 0);
    expect(textoRelativo(diasDesde(new Date(2026, 9, 19, 1, 0), hoy))).toBe('hoy');
    expect(textoRelativo(diasDesde(new Date(2026, 9, 18, 23, 0), hoy))).toBe('ayer');
    expect(textoRelativo(diasDesde(new Date(2026, 9, 10, 12, 0), hoy))).toBe('hace 9 días');
    expect(textoRelativo(diasDesde(null, hoy))).toBe('—');
  });

  it('reglas y signo de los movimientos', () => {
    expect(reglaMovimiento('DEVOLUCION')).toEqual({ signo: -1, alumno: 'obligatorio' });
    expect(reglaMovimiento('SOBRANTE').alumno).toBe('oculto');
    expect(reglaMovimiento('ERROR').alumno).toBe('opcional');
    expect(importeConSigno('DEVOLUCION', '15')).toBe(-15);
    expect(importeConSigno('DEVOLUCION', '-15')).toBe(-15);
    expect(importeConSigno('FALTANTE', '2,50')).toBe(-2.5);
    expect(importeConSigno('SOBRANTE', '-3')).toBe(3);
    expect(importeConSigno('ERROR', '-4')).toBe(-4);
    expect(importeConSigno('OTRO', '4')).toBe(4);
    expect(importeConSigno('OTRO', '0')).toBeNull();
    expect(importeConSigno('OTRO', 'x')).toBeNull();
    // Un solo signo delante: lo demás no es un importe.
    expect(importeConSigno('OTRO', '--5')).toBeNull();
    expect(importeConSigno('DEVOLUCION', '--5')).toBeNull();
    expect(importeConSigno('OTRO', '+-5')).toBeNull();
    expect(importeConSigno('OTRO', '5-')).toBeNull();
    expect(importeConSigno('OTRO', '+5')).toBe(5);
    expect(importeConSigno('OTRO', '-1.240,50')).toBe(-1240.5);
  });

  it('importe con signo explícito y clase de color por signo', () => {
    const limpio = (t: string) => t.replace(/ | /g, ' ');
    expect(limpio(importeConSignoTexto(50))).toBe('+50,00 €');
    expect(limpio(importeConSignoTexto(-1))).toBe('−1,00 €');
    expect(limpio(importeConSignoTexto(-1240))).toBe('−1.240,00 €');
    expect(limpio(importeConSignoTexto(0))).toBe('0,00 €');
    expect(claseImporte(50)).toBe('importe-positivo');
    expect(claseImporte(-0.01)).toBe('importe-negativo');
    expect(claseImporte(0)).toBe('');
    expect(claseImporte(0.001)).toBe('');
  });

  it('seleccionar todos: tres estados', () => {
    expect(estadoSeleccion(3, 3)).toBe('todos');
    expect(estadoSeleccion(3, 0)).toBe('ninguno');
    expect(estadoSeleccion(3, 2)).toBe('algunos');
    expect(estadoSeleccion(0, 0)).toBe('ninguno');
  });
});
