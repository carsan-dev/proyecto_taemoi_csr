import {
  accionLote,
  avisoSinPagar,
  destinoAlumno,
  exitoLote,
  filaBaja,
  fraseCargos,
  sinPagarDe,
  leerEstado,
  lineaDatos,
  mensajeAnonimizar,
  mensajeLote,
  mensajeLoteMixto,
  motivoFallo,
  progresoLote,
  tienePagos,
  tituloAnonimizar,
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

  it('8b-3l bis: con pagos se elimina anonimizando; sus textos', () => {
    const base = { alumnoId: 1, cargos: 2, pagados: 1, pendientes: 1, importePagado: 30, importePendiente: 20 };
    expect(tienePagos({ ...base, pagos: 1 })).toBeTrue();
    expect(tienePagos({ ...base, pagos: 0 })).toBeFalse();
    expect(tienePagos(base)).toBeFalse();
    expect(tienePagos(undefined)).toBeFalse();
    expect(tituloAnonimizar('Lucía Ortega')).toBe('¿Eliminar anonimizando a Lucía Ortega?');
    expect(mensajeAnonimizar(3)).toBe(
      'Sus datos personales se borran para siempre. Sus 3 pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.'
    );
    expect(mensajeAnonimizar(1)).toContain('Su pago se queda en Tesorería');
    expect(mensajeLoteMixto(2, 2)).toBe(
      'Sus datos personales se borran para siempre. Sus pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.'
    );
    expect(mensajeLoteMixto(1, 3)).toContain('Los que no tienen pagos se borran para siempre.');
    expect(destinoAlumno(true)).toBe('se anonimiza (tiene pagos)');
    expect(destinoAlumno(false)).toBe('se elimina');
    // Decisión 287 (ronda 2): al anonimizar, lo pagado se conserva y lo sin pagar se cancela.
    expect(fraseCargos([base], 1, 'conservan').replace(/[  ]/g, ' ')).toBe(
      'Tiene 2 cargos: 1 pagado (30,00 €) y 1 pendiente (20,00 €). Lo pagado se conserva. ' +
        'Tiene 1 cargo sin pagar (20,00 €): se cancela.'
    );
    expect(
      fraseCargos([{ ...base, pendientes: 0, importePendiente: 0, cargos: 1 }], 1, 'conservan').replace(/[  ]/g, ' ')
    ).toBe('Tiene 1 cargo: 1 pagado (30,00 €). Se conserva.');
    expect(fraseCargos([base, base], 2, 'conservan').replace(/[  ]/g, ' ')).toBe(
      'Entre los 2 tienen 4 cargos: 2 pagados (60,00 €) y 2 pendientes (40,00 €). Lo pagado se conserva; lo sin pagar ' +
        'se cancela.'
    );
    expect(avisoSinPagar(3, 90).replace(/[  ]/g, ' ')).toBe('Tiene 3 cargos sin pagar (90,00 €): se cancelan.');
    expect(avisoSinPagar(1, 30).replace(/[  ]/g, ' ')).toBe('Tiene 1 cargo sin pagar (30,00 €): se cancela.');
    expect(avisoSinPagar(0, 0)).toBe('');
    expect(sinPagarDe({ ...base, pendientes: 3, importePendiente: 90 }).replace(/[  ]/g, ' ')).toBe(
      '3 sin pagar (90,00 €): se cancelan'
    );
    expect(sinPagarDe({ ...base, pendientes: 0 })).toBe('');
    expect(sinPagarDe(undefined)).toBe('');
    expect(fraseCargos([base], 1).endsWith('Se borran con el alumno.')).toBeTrue();
    expect(exitoLote('eliminar', 1, 1)).toBe('El alumno ha sido eliminado anonimizando. Sus pagos siguen en Tesorería.');
    expect(exitoLote('eliminar', 2, 1)).toBe('Los alumnos seleccionados han sido eliminados correctamente.');
  });
});
