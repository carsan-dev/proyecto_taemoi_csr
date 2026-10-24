import {
  accionLote,
  exitoLote,
  filaBaja,
  fraseCargos,
  leerEstado,
  lineaDatos,
  mensajeLote,
  motivoFallo,
  progresoLote,
  tituloFallos,
  tituloLote,
} from './bajas-alumnos-vista';

describe('bajas-alumnos-vista (8b-3l)', () => {
  const r = (alumnoId: number, pagados: number, pendientes: number, importePagado: number, importePendiente: number) => ({
    alumnoId,
    cargos: pagados + pendientes,
    pagados,
    pendientes,
    importePagado,
    importePendiente,
  });

  const frase = (rs: ReturnType<typeof r>[], n: number): string => fraseCargos(rs, n).replace(/ /g, ' ');

  it('P-3: la línea de pagos y cargos de uno y de varios; sin cargos lo dice', () => {
    expect(frase([r(1, 12, 2, 360, 60)], 1)).toBe(
      'Tiene 14 cargos: 12 pagados (360,00 €) y 2 pendientes (60,00 €). Se borran con el alumno.'
    );
    expect(frase([r(1, 1, 0, 30, 0)], 1)).toBe('Tiene 1 cargo: 1 pagado (30,00 €). Se borra con el alumno.');
    expect(frase([r(1, 10, 1, 300, 30), r(2, 0, 1, 0, 20), r(3, 0, 0, 0, 0)], 3)).toBe(
      'Entre los 3 tienen 12 cargos: 10 pagados (300,00 €) y 2 pendientes (50,00 €). Se borran con ellos.'
    );
    expect(frase([r(1, 0, 0, 0, 0)], 1)).toBe('No tiene pagos ni cargos.');
    expect(frase([], 2)).toBe('Ninguno tiene pagos ni cargos.');
    expect(frase([r(1, 0, 1, 0, 1010)], 1)).toBe('Tiene 1 cargo: 1 pendiente (1.010,00 €). Se borra con el alumno.');
  });

  it('textos del diálogo: título con el nombre o el recuento, frase, acción, progreso, fallos y éxito', () => {
    expect(tituloLote('eliminar', ['Lucía Ortega'])).toBe('¿Eliminar a Lucía Ortega?');
    expect(tituloLote('eliminar', ['A', 'B', 'C'])).toBe('¿Eliminar a 3 alumnos?');
    expect(tituloLote('baja', ['A', 'B'])).toBe('¿Dar de baja a 2 alumnos?');
    expect(mensajeLote('eliminar', 3)).toBe('Se borran para siempre. No se puede deshacer.');
    expect(mensajeLote('baja', 2)).toBe('Los alumnos seleccionados serán dados de baja.');
    expect(accionLote('eliminar', 3)).toBe('Eliminar 3 alumnos');
    expect(accionLote('eliminar', 1)).toBe('Eliminar definitivamente');
    expect(progresoLote('eliminar', 2, 3)).toBe('Eliminando 2 de 3…');
    expect(progresoLote('baja', 1, 2)).toBe('Dando de baja 1 de 2…');
    expect(tituloFallos('eliminar', 1, 3)).toBe('No se pudo eliminar a 1 de 3');
    expect(tituloFallos('eliminar', 1, 1)).toBe('No se pudo eliminar al alumno');
    expect(exitoLote('eliminar', 1)).toBe('El alumno ha sido eliminado correctamente.');
    expect(exitoLote('baja', 3)).toBe('Los alumnos seleccionados han sido dados de baja correctamente.');
  });

  it('el motivo de un fallo: el del servidor si lo da; si no, el texto de hoy', () => {
    expect(motivoFallo('eliminar', { error: { mensaje: 'Solo el propietario puede eliminar alumnos definitivamente.' } })).toBe(
      'Solo el propietario puede eliminar alumnos definitivamente.'
    );
    expect(motivoFallo('eliminar', { error: 'Tiene una devolución pendiente.' })).toBe('Tiene una devolución pendiente.');
    expect(motivoFallo('eliminar', { error: null })).toBe('Ha ocurrido un error al intentar eliminar al alumno.');
    expect(motivoFallo('baja', {})).toBe('Ha ocurrido un error al intentar dar de baja al alumno.');
  });

  it('filas: nombre completo, «Exp. · DNI» y activo por defecto, como hoy', () => {
    const f = filaBaja({ id: 3, nombre: 'Lucía', apellidos: 'Ortega Ramos', numeroExpediente: 1042, nif: '12345678Z' });
    expect(f.nombreCompleto).toBe('Lucía Ortega Ramos');
    expect(f.activo).toBeTrue();
    expect(lineaDatos(f)).toBe('Exp. 1042 · DNI 12345678Z');
    expect(lineaDatos(filaBaja({ id: 4, nombre: 'A', apellidos: 'B', activo: false }))).toBe('');
    expect(filaBaja({ id: 4, activo: false }).activo).toBeFalse();
  });

  it('el estado guardado de hoy (misma clave y forma); sin él o roto, el de por defecto', () => {
    expect(leerEstado(JSON.stringify({ paginaActual: 3, tamanoPagina: 10, nombreFiltro: 'ana', mostrarInactivos: true }))).toEqual({
      paginaActual: 3,
      tamanoPagina: 10,
      nombreFiltro: 'ana',
      mostrarInactivos: true,
    });
    expect(leerEstado(null).paginaActual).toBe(1);
    expect(leerEstado('{roto').mostrarInactivos).toBeFalse();
  });
});
