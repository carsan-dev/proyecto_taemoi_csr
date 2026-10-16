import { FechaApiPipe, diaApi, fechaApi } from './fecha-api';

describe('fechaApi', () => {
  it('interpreta un número pequeño como SEGUNDOS (Instant de la API), no como milisegundos de 1970', () => {
    const fecha = fechaApi(1760000000)!;
    expect(fecha.toISOString()).toBe('2025-10-09T08:53:20.000Z');
    expect(fecha.getUTCFullYear()).not.toBe(1970);
  });

  it('conserva la fracción de segundo (Instant con nanosegundos)', () => {
    expect(fechaApi(1760000000.123456789)!.toISOString()).toBe('2025-10-09T08:53:20.123Z');
    expect(fechaApi('1760000000.5')!.toISOString()).toBe('2025-10-09T08:53:20.500Z');
  });

  it('interpreta un número grande como milisegundos (java.util.Date de la API)', () => {
    expect(fechaApi(1397260800000)!.toISOString()).toBe('2014-04-12T00:00:00.000Z');
  });

  it('acepta cadenas ISO', () => {
    expect(fechaApi('2026-10-16T19:00:00Z')!.toISOString()).toBe('2026-10-16T19:00:00.000Z');
  });

  it('acepta LocalDate como [año, mes, día] en hora local', () => {
    const fecha = fechaApi([2014, 4, 12])!;
    expect([fecha.getFullYear(), fecha.getMonth() + 1, fecha.getDate()]).toEqual([2014, 4, 12]);
  });

  it('devuelve null para vacíos o valores no reconocibles', () => {
    for (const valor of [null, undefined, '', 'no-es-fecha', {}, ['x'], Number.NaN]) {
      expect(fechaApi(valor)).withContext(String(valor)).toBeNull();
    }
  });

  describe('diaApi (fecha sin hora ni zona, yyyy-MM-dd)', () => {
    it('toma [a,m,d] y cadenas yyyy-MM-dd tal cual', () => {
      expect(diaApi([2014, 4, 12])).toBe('2014-04-12');
      expect(diaApi('2014-04-12')).toBe('2014-04-12');
    });
    it('redondea los ms a la medianoche más próxima (servidor en UTC o en Europe/Madrid)', () => {
      expect(diaApi(1397260800000)).toBe('2014-04-12'); // 00:00Z
      expect(diaApi(1397253600000)).toBe('2014-04-12'); // 22:00Z del día anterior = 00:00 CEST
      expect(diaApi(1389740400000)).toBe('2014-01-15'); // 23:00Z del día anterior = 00:00 CET
    });
    it('un número (o cadena numérica) es SIEMPRE milisegundos, también entre 1966 y 1973', () => {
      expect(diaApi(14256000000)).toBe('1970-06-15');
      expect(diaApi(14256000000 - 3600000)).toBe('1970-06-15'); // medianoche CET
      expect(diaApi(94608000000)).toBe('1972-12-31');
      expect(diaApi('94608000000')).toBe('1972-12-31');
      expect(diaApi(-99964800000)).toBe('1966-11-01');
    });
    it('null si no es reconocible', () => {
      expect(diaApi(null)).toBeNull();
      expect(diaApi('no-es-fecha')).toBeNull();
    });
  });

  it('el pipe delega en fechaApi', () => {
    expect(new FechaApiPipe().transform(1760000000)!.toISOString()).toBe('2025-10-09T08:53:20.000Z');
  });
});
