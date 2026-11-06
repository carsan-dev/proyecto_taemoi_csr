import {
  Festivo,
  cuerpoFestivo,
  detalleConLista,
  diasDeRango,
  fechaDeFila,
  fechaEnFrase,
  formularioDe,
  nombreDeFila,
  sedesEnFrase,
  textoFestivoDeClase,
  textoImpacto,
  textoTipo,
  validarFestivo,
} from './festivos-vista';

const festivo = (cambios: Partial<Festivo> = {}): Festivo => ({
  id: 1, tipo: 'DIA', fechaDesde: '2026-11-05', fechaHasta: '2026-11-05', mes: null, dia: null, proximaFecha: '2026-11-05',
  nombre: 'Feria de Estepa', sedes: [{ id: 2, nombre: 'Estepa' }], todasLasSedes: false, editable: true, ...cambios,
});

describe('festivos-vista', () => {
  it('fecha de la fila por tipo', () => {
    expect(fechaDeFila(festivo())).toBe('Jueves 5 de noviembre');
    expect(fechaDeFila(festivo({ tipo: 'RANGO', fechaDesde: '2026-12-23', fechaHasta: '2027-01-07' }))).toBe('Del 23 dic al 7 ene');
    expect(fechaDeFila(festivo({ tipo: 'RANGO', fechaDesde: '2027-03-22', fechaHasta: '2027-03-26' }))).toBe('Del 22 al 26 mar');
    expect(fechaDeFila(festivo({ tipo: 'ANUAL', fechaDesde: null, fechaHasta: null, mes: 12, dia: 8 }))).toBe('8 de diciembre');
  });

  it('fecha y sedes en frase', () => {
    expect(fechaEnFrase(festivo())).toBe('El jueves 5 de noviembre');
    expect(fechaEnFrase(festivo({ tipo: 'RANGO', fechaDesde: '2026-12-23', fechaHasta: '2027-01-07' }))).toBe('Del 23 de diciembre al 7 de enero');
    expect(fechaEnFrase(festivo({ tipo: 'ANUAL', mes: 2, dia: 28 }))).toBe('Cada 28 de febrero');
    expect(sedesEnFrase(festivo({ todasLasSedes: true }))).toBe('todas las sedes');
    expect(sedesEnFrase(festivo({ sedes: [{ id: 1, nombre: 'Estepa' }, { id: 2, nombre: 'Herrera' }, { id: 3, nombre: 'Mollina' }] }))).toBe('Estepa, Herrera y Mollina');
  });

  it('tipo y nombre', () => {
    expect(['DIA', 'RANGO', 'ANUAL'].map((t) => textoTipo(t as never))).toEqual(['Un día', 'Varios días', 'Cada año']);
    expect(nombreDeFila({ nombre: '  ' })).toBe('Festivo sin nombre');
  });

  it('días de un rango: los dos extremos cuentan', () => {
    expect(diasDeRango('2026-10-30', '2026-11-02')).toBe(4);
    expect(diasDeRango('2026-10-30', '2026-10-30')).toBe(1);
    expect(diasDeRango('2026-11-02', '2026-10-30')).toBe(0);
    expect(diasDeRango('', '2026-10-30')).toBe(0);
  });

  it('validar por tipo', () => {
    const vacio = formularioDe(null, []);
    expect(validarFestivo(vacio)).toEqual({ fecha: 'Elige el día.', sedes: 'Elige al menos una sede.' });
    expect(validarFestivo({ ...vacio, tipo: 'RANGO', desde: '2026-11-02', hasta: '2026-10-30', sedeIds: [1] })).toEqual({
      hasta: 'El último día no puede ser anterior al primero.',
    });
    expect(validarFestivo({ ...vacio, tipo: 'ANUAL', sedeIds: [1] })).toEqual({ mes: 'Elige el mes.', dia: 'Elige el día.' });
    expect(validarFestivo({ ...vacio, tipo: 'ANUAL', mes: '4', dia: '31', sedeIds: [1] })).toEqual({ dia: 'Abril no tiene día 31.' });
    expect(validarFestivo({ ...vacio, tipo: 'ANUAL', mes: '2', dia: '29', sedeIds: [1] })).toEqual({});
  });

  it('cuerpo por tipo; el nombre vacío no viaja', () => {
    const f = formularioDe(null, [2]);
    expect(cuerpoFestivo({ ...f, fecha: '2026-11-05' })).toEqual({ tipo: 'DIA', sedeIds: [2], fechaDesde: '2026-11-05', fechaHasta: '2026-11-05' });
    expect(cuerpoFestivo({ ...f, tipo: 'RANGO', desde: '2026-10-30', hasta: '2026-11-02', nombre: ' Puente ' })).toEqual({
      tipo: 'RANGO', sedeIds: [2], nombre: 'Puente', fechaDesde: '2026-10-30', fechaHasta: '2026-11-02',
    });
    expect(cuerpoFestivo({ ...f, tipo: 'ANUAL', mes: '12', dia: '8' })).toEqual({ tipo: 'ANUAL', sedeIds: [2], mes: 12, dia: 8 });
  });

  it('formulario desde un festivo', () => {
    expect(formularioDe(festivo({ tipo: 'ANUAL', fechaDesde: null, fechaHasta: null, mes: 12, dia: 8 }), [])).toEqual(jasmine.objectContaining({
      tipo: 'ANUAL', dia: '8', mes: '12', sedeIds: [2], nombre: 'Feria de Estepa',
    }));
  });

  it('nota de impacto', () => {
    expect(textoImpacto({ clases: 0, conLista: 0, conListaDetalle: [] })).toEqual({ fuerte: 'No afecta a ninguna clase.', resto: '' });
    expect(textoImpacto({ clases: 18, conLista: 0, conListaDetalle: [] })).toEqual({ fuerte: 'Afecta a 18 clases', resto: '; ninguna tiene lista todavía.' });
    const detalle = [
      { sedeId: 2, sede: 'Estepa', fecha: '2026-10-30', horaInicio: '17:00', grupo: 'Iniciación' },
      { sedeId: 2, sede: 'Estepa', fecha: '2026-10-30', horaInicio: '19:00', grupo: 'Avanzados' },
    ];
    expect(textoImpacto({ clases: 9, conLista: 2, conListaDetalle: detalle })).toEqual({
      fuerte: 'Afecta a 9 clases; 2 ya tienen lista y se quedan como están',
      resto: ' (Estepa, viernes 30: Iniciación y Avanzados).',
    });
    expect(detalleConLista([1, 2, 3, 4].map((d) => ({ sedeId: 1, sede: 'A', fecha: `2026-11-0${d}`, horaInicio: '', grupo: 'G' }))))
      .toBe('A, domingo 1: G; A, lunes 2: G; A, martes 3: G y 1 más');
  });

  it('texto del festivo en pasar lista, según el tipo, las fechas y las sedes', () => {
    const f = (cambios: object) => ({ id: 1, nombre: null, tipo: 'DIA' as const, deUnDiaYUnaSede: true, fechaDesde: '2026-11-05', fechaHasta: '2026-11-05', sedes: 1, ...cambios });
    expect(textoFestivoDeClase(f({}), '2026-11-05', 'Estepa')).toBe('No hay clase en Estepa el jueves 5 de noviembre. No cuenta como falta para nadie.');
    expect(textoFestivoDeClase(f({ sedes: 3, deUnDiaYUnaSede: false }), '2026-11-05', 'Estepa'))
      .toBe('No hay clase en varias sedes el jueves 5 de noviembre. No cuenta como falta para nadie.');
    expect(textoFestivoDeClase(f({}), '2026-11-05', null)).toBe('No hay clase el jueves 5 de noviembre. No cuenta como falta para nadie.');
    const rango = f({ tipo: 'RANGO', fechaDesde: '2026-12-23', fechaHasta: '2027-01-07', sedes: 1, deUnDiaYUnaSede: false });
    expect(textoFestivoDeClase(rango, '2026-12-28', 'Herrera'))
      .toBe('Del 23 de diciembre al 7 de enero, en Herrera. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
    expect(textoFestivoDeClase({ ...rango, sedes: 6 }, '2026-12-28', 'Herrera'))
      .toBe('Del 23 de diciembre al 7 de enero, en varias sedes. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
    expect(textoFestivoDeClase({ ...rango, fechaDesde: '2026-03-22', fechaHasta: '2026-03-26' }, '2026-03-24', null))
      .toBe('Del 22 al 26 de marzo. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
    const anual = f({ tipo: 'ANUAL', fechaDesde: '2026-12-08', fechaHasta: '2026-12-08', sedes: 6, deUnDiaYUnaSede: false });
    expect(textoFestivoDeClase(anual, '2026-12-08', 'Estepa'))
      .toBe('Cada año, 8 de diciembre, en varias sedes. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
    // Sin fechas del API: como un día.
    expect(textoFestivoDeClase(f({ tipo: 'RANGO', fechaDesde: null, fechaHasta: null }), '2026-11-05', 'Estepa'))
      .toBe('No hay clase en Estepa el jueves 5 de noviembre. No cuenta como falta para nadie.');
  });
});
