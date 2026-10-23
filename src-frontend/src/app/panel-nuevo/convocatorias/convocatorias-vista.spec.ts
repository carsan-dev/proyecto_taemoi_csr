import {
  AlumnoConvocatoria,
  cifrasDe,
  cuantiaDeCampo,
  diaParaCampo,
  fechaDeCampo,
  iniciales,
  nombreInforme,
  paginar,
  paginasVisibles,
  pestanasDisciplina,
  textoCifra,
  totalPaginas,
} from './convocatorias-vista';

function alumno(id: number, pagado: boolean | null): AlumnoConvocatoria {
  return {
    id,
    alumnoId: 100 + id,
    nombre: 'Ana',
    apellidos: 'Ficticia',
    cuantiaExamen: 25,
    gradoActual: 'BLANCO',
    gradoSiguiente: 'AMARILLO',
    pagado: pagado as boolean,
  };
}

describe('convocatorias-vista (8b-3e)', () => {
  it('cifrasDe: inscritos, pagados (pagado true) y pendientes (pagado false), como el filtro de hoy', () => {
    expect(cifrasDe([alumno(1, true), alumno(2, false), alumno(3, false), alumno(4, null)])).toEqual({
      inscritos: 4,
      pagados: 1,
      pendientes: 2,
    });
    expect(cifrasDe([])).toEqual({ inscritos: 0, pagados: 0, pendientes: 0 });
  });

  it('textoCifra: plurales calculados con cifras es-ES', () => {
    expect(textoCifra(1, 'inscritos')).toBe('1 inscrito');
    expect(textoCifra(0, 'inscritos')).toBe('0 inscritos');
    expect(textoCifra(1, 'pagados')).toBe('1 pagado');
    expect(textoCifra(12, 'pagados')).toBe('12 pagados');
    expect(textoCifra(1, 'pendientes')).toBe('1 pendiente');
    expect(textoCifra(1500, 'pendientes')).toBe('1.500 pendientes');
  });

  it('nombreInforme: el nombre y el título del PDF de hoy, con la fecha es-ES y guiones bajos', () => {
    const conv = { id: 7, deporte: 'TAEKWONDO', fechaConvocatoria: new Date(2026, 10, 14) };
    expect(nombreInforme(conv)).toEqual({
      nombreArchivo: 'informe_convocatoria_TAEKWONDO_14_11_2026.pdf',
      titulo: 'Convocatoria TAEKWONDO — 14_11_2026',
    });
  });

  it('paginar y totalPaginas: 5 por página en el navegador, como hoy', () => {
    const lista = Array.from({ length: 12 }, (_, i) => i + 1);
    expect(totalPaginas(12, 5)).toBe(3);
    expect(totalPaginas(0, 5)).toBe(0);
    expect(paginar(lista, 1, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(paginar(lista, 3, 5)).toEqual([11, 12]);
  });

  it('paginasVisibles: la primera, la última y las vecinas de la actual, con saltos', () => {
    expect(paginasVisibles(1, 1)).toEqual([1]);
    expect(paginasVisibles(1, 3)).toEqual([1, 2, 3]);
    expect(paginasVisibles(5, 10)).toEqual([1, null, 4, 5, 6, null, 10]);
  });

  it('iniciales: la primera letra del nombre y de los apellidos, en mayúscula', () => {
    expect(iniciales('ana', 'ficticia ruiz')).toBe('AF');
    expect(iniciales('Ana', '')).toBe('A');
    expect(iniciales(null, null)).toBe('');
  });

  it('diaParaCampo: el día local en AAAA-MM-DD (no el de UTC)', () => {
    expect(diaParaCampo(new Date(2026, 0, 5, 0, 30))).toBe('2026-01-05');
    expect(diaParaCampo(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31');
  });

  it('fechaDeCampo: la fecha que se envía hoy al crear (medianoche local del día elegido)', () => {
    const f = fechaDeCampo('2026-11-14');
    expect(f.getFullYear()).toBe(2026);
    expect(f.getMonth()).toBe(10);
    expect(f.getDate()).toBe(14);
    expect(f.getHours()).toBe(0);
  });

  it('cuantiaDeCampo: número del campo, vacío como null (lo que mandaba el campo numérico de hoy)', () => {
    expect(cuantiaDeCampo('25')).toBe(25);
    expect(cuantiaDeCampo('25.5')).toBe(25.5);
    expect(cuantiaDeCampo('25,5')).toBe(25.5);
    expect(cuantiaDeCampo('')).toBeNull();
    expect(cuantiaDeCampo('  ')).toBeNull();
    expect(cuantiaDeCampo('abc')).toBeNull();
  });

  it('pestanasDisciplina: las disciplinas con grados en su orden, con su número y su color', () => {
    const disciplinas = [
      { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
      { codigo: 'KICKBOXING', nombre: 'Kickboxing' },
    ];
    expect(pestanasDisciplina(disciplinas, { TAEKWONDO: 4 }, ['TAEKWONDO', 'KICKBOXING', 'PILATES'])).toEqual([
      { codigo: 'TAEKWONDO', nombre: 'Taekwondo', total: 4, color: '1' },
      { codigo: 'KICKBOXING', nombre: 'Kickboxing', total: 0, color: '2' },
    ]);
  });
});
