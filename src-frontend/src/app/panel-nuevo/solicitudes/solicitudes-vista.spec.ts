import { EstadoSolicitud, Solicitud, TipoSolicitud } from '../../interfaces/solicitud';
import {
  DEVOLUCION_OTRA_SEDE,
  SEGMENTOS,
  accionesDe,
  descripcion,
  devolucionDelProfesor,
  efectoRechazo,
  etiquetaEstado,
  etiquetaRespuesta,
  etiquetaSegmento,
  etiquetaTipo,
  fechaHora,
  numeroSegmento,
  pasosEncargo,
  pastillaCobro,
  pastillas,
  sobreQue,
  subtitulo,
  textoAccion,
  textoResolucion,
  tonoEstado,
  vacio,
  verboAceptar,
} from './solicitudes-vista';

/** Una solicitud inventada, con la forma que da `normalizarSolicitud`. */
export function solicitudVista(cambios: Partial<Solicitud> = {}): Solicitud {
  return {
    id: 7,
    tipo: 'REVERTIR_COBRO',
    estado: 'PENDIENTE',
    sedeId: 1,
    sedeNombre: 'Sede Centro',
    solicitadoPorUsuarioId: 5,
    solicitadoPorNombre: 'Gestor Prueba',
    solicitadoEn: new Date(2026, 9, 19, 9, 30).toISOString(),
    motivo: 'Se marcó por error',
    resueltoPorUsuarioId: null,
    resueltoPorNombre: null,
    resueltoEn: null,
    respuesta: null,
    cobroId: 45,
    entregaCajaId: null,
    movimientoCajaId: null,
    productoAlumnoId: 30,
    productoId: null,
    alumnoId: 9,
    alumnoNombre: 'Alumno',
    alumnoApellidos: 'De Prueba',
    concepto: 'CUOTA OCTUBRE',
    importe: 10.5,
    motivoDeshacer: null,
    encargadoEn: null,
    recibidoEn: null,
    entregadoEn: null,
    devolucionPendiente: false,
    propia: false,
    ...cambios,
  };
}

const encargo = (cambios: Partial<Solicitud> = {}) =>
  solicitudVista({ id: 20, tipo: 'ENCARGO', estado: 'PEDIDO', cobroId: null, concepto: 'DOBOK T-160', importe: 25, ...cambios });

/** Pieza 8b-3i: reglas puras de la bandeja nueva (segmentos, pastillas, pasos, verbos y acciones por rol). */
describe('solicitudes-vista (8b-3i)', () => {
  it('tres segmentos en el orden de hoy; Pendientes y Encargos con su número al cargar, Resueltas sin número', () => {
    expect(SEGMENTOS).toEqual(['PENDIENTES', 'ENCARGOS', 'RESUELTAS']);
    expect(SEGMENTOS.map(etiquetaSegmento)).toEqual(['Pendientes', 'Encargos en curso', 'Resueltas']);
    expect(numeroSegmento('PENDIENTES', null)).toBeNull();
    expect(numeroSegmento('PENDIENTES', [])).toBe(0);
    expect(numeroSegmento('ENCARGOS', [encargo(), encargo({ id: 21 })])).toBe(2);
    expect(numeroSegmento('RESUELTAS', [solicitudVista()])).toBeNull();
  });

  it('etiquetas de tipo y estado, como hoy', () => {
    const tipos: Record<TipoSolicitud, string> = {
      REVERTIR_COBRO: 'Revertir cobro',
      DESHACER_COBRO_ENTREGADO: 'Deshacer cobro entregado',
      ANULAR_ENTREGA: 'Anular entrega',
      ANULAR_MOVIMIENTO: 'Anular movimiento',
      ENCARGO: 'Encargo',
    };
    for (const [tipo, texto] of Object.entries(tipos)) {
      expect(etiquetaTipo(tipo as TipoSolicitud)).toBe(texto);
    }
    const estados: Record<EstadoSolicitud, string> = {
      PENDIENTE: 'Pendiente',
      ACEPTADA: 'Aceptada',
      DENEGADA: 'Denegada',
      RETIRADA: 'Retirada',
      NO_APLICABLE: 'No aplicable',
      PEDIDO: 'Pedido',
      ENCARGADO: 'Encargado',
      RECIBIDO: 'Recibido',
      ENTREGADO: 'Entregado',
      RECHAZADO: 'Rechazado',
    };
    for (const [estado, texto] of Object.entries(estados)) {
      expect(etiquetaEstado(estado as EstadoSolicitud)).toBe(texto);
    }
    expect(etiquetaTipo('OTRO' as TipoSolicitud)).toBe('OTRO');
  });

  it('tono de cada estado con las mismas familias que hoy (hecho, no, aviso, neutro y en curso)', () => {
    expect(tonoEstado('ACEPTADA')).toBe('bien');
    expect(tonoEstado('ENTREGADO')).toBe('bien');
    expect(tonoEstado('DENEGADA')).toBe('peligro');
    expect(tonoEstado('RECHAZADO')).toBe('peligro');
    expect(tonoEstado('NO_APLICABLE')).toBe('atencion');
    expect(tonoEstado('RETIRADA')).toBe('neutro');
    for (const e of ['PENDIENTE', 'PEDIDO', 'ENCARGADO', 'RECIBIDO'] as EstadoSolicitud[]) {
      expect(tonoEstado(e)).toBe('curso');
    }
  });

  it('pastillas: tipo; estado fuera de Pendientes (como hoy); «Devolución pendiente» en ocre si la hay', () => {
    expect(pastillas(solicitudVista(), true)).toEqual([{ texto: 'Revertir cobro', tono: 'tipo' }]);
    expect(pastillas(encargo({ estado: 'ENCARGADO' }), false)).toEqual([
      { texto: 'Encargo', tono: 'tipo' },
      { texto: 'Encargado', tono: 'curso' },
    ]);
    expect(pastillas(encargo({ estado: 'RECHAZADO', devolucionPendiente: true }), false)).toEqual([
      { texto: 'Encargo', tono: 'tipo' },
      { texto: 'Rechazado', tono: 'peligro' },
      { texto: 'Devolución pendiente', tono: 'atencion' },
    ]);
    expect(pastillas(solicitudVista({ devolucionPendiente: true }), true)).toEqual([
      { texto: 'Revertir cobro', tono: 'tipo' },
      { texto: 'Devolución pendiente', tono: 'atencion' },
    ]);
  });

  it('estado del cobro de un encargo, con el texto de hoy; nada fuera de los encargos', () => {
    expect(pastillaCobro(encargo({ estadoCobro: 'COBRADO' }))).toEqual({ texto: 'Cobrado', tono: 'bien' });
    expect(pastillaCobro(encargo({ estadoCobro: 'PENDIENTE' }))).toEqual({ texto: 'Sin cobrar', tono: 'atencion' });
    expect(pastillaCobro(encargo({ estadoCobro: 'DEVUELTO' }))).toEqual({ texto: 'Devuelto', tono: 'neutro' });
    expect(pastillaCobro(encargo({ estadoCobro: 'CANCELADO' }))).toEqual({ texto: 'Cargo cancelado', tono: 'neutro' });
    expect(pastillaCobro(encargo({ estadoCobro: null }))).toBeNull();
    expect(pastillaCobro(solicitudVista({ estadoCobro: 'COBRADO' }))).toBeNull();
  });

  it('pasos del encargo: Pedido, Encargado, Recibido y Entregado con el actual; ninguno si cayó', () => {
    expect(pasosEncargo(encargo({ estado: 'ENCARGADO' }))).toEqual([
      { etiqueta: 'Pedido', estado: 'hecho' },
      { etiqueta: 'Encargado', estado: 'actual' },
      { etiqueta: 'Recibido', estado: 'pendiente' },
      { etiqueta: 'Entregado', estado: 'pendiente' },
    ]);
    expect(pasosEncargo(encargo({ estado: 'ENTREGADO' }))!.map((p) => p.estado)).toEqual(['hecho', 'hecho', 'hecho', 'actual']);
    expect(pasosEncargo(encargo({ estado: 'PEDIDO' }))![0].estado).toBe('actual');
    expect(pasosEncargo(encargo({ estado: 'RECHAZADO' }))).toBeNull();
    expect(pasosEncargo(encargo({ estado: 'RETIRADA' }))).toBeNull();
    expect(pasosEncargo(solicitudVista())).toBeNull();
  });

  it('verbo de «Aceptar» por tipo, como hoy; los encargos no tienen', () => {
    expect(verboAceptar('REVERTIR_COBRO')).toBe('Aceptar y revertir');
    expect(verboAceptar('DESHACER_COBRO_ENTREGADO')).toBe('Aceptar y deshacer');
    expect(verboAceptar('ANULAR_ENTREGA')).toBe('Aceptar y anular');
    expect(verboAceptar('ANULAR_MOVIMIENTO')).toBe('Aceptar y anular');
    expect(verboAceptar('ENCARGO')).toBe('');
  });

  it('textos de los botones, los de hoy', () => {
    const s = solicitudVista({ tipo: 'ANULAR_ENTREGA' });
    expect(textoAccion('aceptar', s)).toBe('Aceptar y anular');
    expect(textoAccion('denegar', s)).toBe('Denegar…');
    expect(textoAccion('retirar', s)).toBe('Retirar');
    expect(textoAccion('encargado', s)).toBe('Marcar encargado');
    expect(textoAccion('recibido', s)).toBe('Marcar recibido');
    expect(textoAccion('rechazar', s)).toBe('Rechazar…');
    expect(textoAccion('devolucion', s)).toBe('Registrar devolución');
  });

  describe('acciones por rol (criterios de hoy)', () => {
    it('pendiente de los tipos 1 a 3: el propietario acepta y deniega; la gestión, nada', () => {
      expect(accionesDe(solicitudVista(), true)).toEqual(['aceptar', 'denegar']);
      expect(accionesDe(solicitudVista(), false)).toEqual([]);
      expect(accionesDe(solicitudVista({ tipo: 'ANULAR_MOVIMIENTO' }), true)).toEqual(['aceptar', 'denegar']);
    });

    it('retirar solo las propias y pendientes, con cualquier rol', () => {
      expect(accionesDe(solicitudVista({ propia: true }), false)).toEqual(['retirar']);
      expect(accionesDe(solicitudVista({ propia: true }), true)).toEqual(['aceptar', 'denegar', 'retirar']);
      expect(accionesDe(solicitudVista({ propia: true, estado: 'ACEPTADA' }), false)).toEqual([]);
      expect(accionesDe(solicitudVista({ propia: true, estado: 'DENEGADA' }), true)).toEqual([]);
      // Un encargo propio no se retira desde la bandeja (se retira en Mi caja), como hoy.
      expect(accionesDe(encargo({ propia: true }), false)).toEqual(['encargado', 'rechazar']);
    });

    it('ya resueltas: nada que hacer', () => {
      for (const estado of ['ACEPTADA', 'DENEGADA', 'RETIRADA', 'NO_APLICABLE'] as EstadoSolicitud[]) {
        expect(accionesDe(solicitudVista({ estado }), true)).toEqual([]);
      }
    });

    it('encargos: los mismos botones con propietario y gestión, por estado', () => {
      for (const propietario of [true, false]) {
        expect(accionesDe(encargo({ estado: 'PEDIDO' }), propietario)).toEqual(['encargado', 'rechazar']);
        expect(accionesDe(encargo({ estado: 'ENCARGADO' }), propietario)).toEqual(['recibido', 'rechazar']);
        expect(accionesDe(encargo({ estado: 'RECIBIDO' }), propietario)).toEqual(['rechazar']);
        expect(accionesDe(encargo({ estado: 'ENTREGADO' }), propietario)).toEqual([]);
        expect(
          accionesDe(encargo({ estado: 'RECHAZADO', devolucionPendiente: true, devolucionEnSede: true }), propietario)
        ).toEqual(['devolucion']);
        expect(
          accionesDe(encargo({ estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false }), propietario)
        ).toEqual([]);
        // Sin saber dónde está el dinero (null), hoy no se ofrece: solo con `true`.
        expect(
          accionesDe(encargo({ estado: 'RECHAZADO', devolucionPendiente: true, devolucionEnSede: null }), propietario)
        ).toEqual([]);
      }
    });

    it('«la registra el profesor» solo con el dinero en el profesor', () => {
      expect(devolucionDelProfesor(encargo({ estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false }))).toBeTrue();
      expect(devolucionDelProfesor(encargo({ estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: true }))).toBeFalse();
      expect(devolucionDelProfesor(encargo({ devolucionPendiente: false, devolucionEnSede: false }))).toBeFalse();
      expect(devolucionDelProfesor(solicitudVista({ devolucionPendiente: true, devolucionEnSede: false }))).toBeFalse();
    });
  });

  it('sobre qué: el alumno o, si no hay, el recurso; descripción con concepto e importe es-ES', () => {
    expect(sobreQue(solicitudVista())).toBe('Alumno De Prueba');
    expect(sobreQue(solicitudVista({ alumnoNombre: null, alumnoApellidos: null, tipo: 'ANULAR_ENTREGA' }))).toBe('Entrega de caja');
    expect(sobreQue(solicitudVista({ alumnoNombre: ' ', alumnoApellidos: null, tipo: 'ANULAR_MOVIMIENTO' }))).toBe(
      'Movimiento de caja'
    );
    expect(sobreQue(solicitudVista({ alumnoNombre: null, alumnoApellidos: null }))).toBe('Sin alumno');
    expect(descripcion(solicitudVista())).toBe('Alumno De Prueba, CUOTA OCTUBRE, 10,50 €');
    expect(descripcion(solicitudVista({ concepto: null, importe: null }))).toBe('Alumno De Prueba');
    expect(descripcion(solicitudVista({ importe: 1044 }))).toContain('1.044,00 €');
  });

  it('fecha y hora como hoy (dd/MM/yyyy HH:mm); vacía sin fecha', () => {
    expect(fechaHora(new Date(2026, 9, 19, 9, 5).toISOString())).toBe('19/10/2026 09:05');
    expect(fechaHora(null)).toBe('');
    expect(fechaHora('no')).toBe('');
  });

  it('resolución en Resueltas: «Resuelta» o «Retirada», por quién y cuándo', () => {
    const cuando = new Date(2026, 9, 19, 12, 0).toISOString();
    expect(textoResolucion(solicitudVista({ estado: 'DENEGADA', resueltoPorNombre: 'Propietario', resueltoEn: cuando }))).toBe(
      'Resuelta por Propietario el 19/10/2026 12:00'
    );
    expect(textoResolucion(solicitudVista({ estado: 'RETIRADA', resueltoPorNombre: null, resueltoEn: cuando }))).toBe(
      'Retirada el 19/10/2026 12:00'
    );
    expect(textoResolucion(solicitudVista({ estado: 'ACEPTADA', resueltoPorNombre: 'Ana', resueltoEn: null }))).toBe('Resuelta por Ana');
    expect(textoResolucion(solicitudVista({ estado: 'ACEPTADA' }))).toBeNull();
  });

  it('etiqueta de la respuesta según el estado, como hoy', () => {
    expect(etiquetaRespuesta(solicitudVista({ estado: 'NO_APLICABLE' }))).toBe('Por qué no se aplicó:');
    expect(etiquetaRespuesta(encargo({ estado: 'RECHAZADO' }))).toBe('Motivo del rechazo:');
    expect(etiquetaRespuesta(solicitudVista({ estado: 'DENEGADA' }))).toBe('Respuesta:');
  });

  it('efecto de rechazar según el cobro del cargo, como hoy', () => {
    expect(efectoRechazo(encargo({ estadoCobro: 'COBRADO' }))).toBe('El cargo ya está cobrado: quedará una devolución pendiente.');
    expect(efectoRechazo(encargo({ estadoCobro: 'PENDIENTE' }))).toBe('El cargo del alumno se cancelará.');
    expect(efectoRechazo(encargo({ estadoCobro: null }))).toBe('El cargo del alumno se cancelará.');
  });

  it('subtítulo y vacíos por rol y segmento, con el texto de hoy', () => {
    expect(subtitulo(true)).toBe('Lo que la gestión te pide revertir o anular, y los encargos de material.');
    expect(subtitulo(false)).toBe('Lo que has pedido al propietario y los encargos de material.');
    expect(vacio('PENDIENTES', true)).toEqual({
      titulo: 'No hay solicitudes pendientes.',
      texto: 'Cuando la gestión pida revertir un cobro o anular algo de la caja, aparecerá aquí.',
    });
    expect(vacio('PENDIENTES', false)).toEqual({
      titulo: 'No hay solicitudes pendientes.',
      texto: 'Lo que pidas al propietario desde la tesorería, la ficha o la caja de sede aparecerá aquí.',
    });
    expect(vacio('ENCARGOS', true)).toEqual({ titulo: 'No hay encargos en curso.', texto: '' });
    expect(vacio('RESUELTAS', false)).toEqual({ titulo: 'Todavía no hay solicitudes resueltas.', texto: '' });
  });

  it('el aviso del 403 al registrar la devolución es el de hoy', () => {
    expect(DEVOLUCION_OTRA_SEDE).toBe(
      'El cobro de este encargo es de otra sede: la devolución la registra la gestión o el propietario.'
    );
  });
});
