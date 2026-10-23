import {
  ESTADOS,
  conObservacion,
  etiquetaEmail,
  etiquetaEstado,
  filaLista,
  haceCuanto,
  nombreCompleto,
  opcionesTemporada,
  puedeCambiarAsignacion,
  puedeEliminar,
  puedeReenviarCambio,
  temporadaDe,
  textoDni,
  textoEmailInicial,
  textoIntentos,
  textoPastilla,
  textoRango,
  tonoEstado,
} from './preinscripciones-vista';

/** Un Instant de la API: segundos (con decimales) desde 1970, como llega `creadaEn`. */
const segundos = (d: Date): number => d.getTime() / 1000;

describe('preinscripciones-vista (8b-3h, lo puro de Preinscripciones P1)', () => {
  const AHORA = new Date(2026, 9, 23, 12, 0, 0);

  describe('estado y pastilla', () => {
    it('los cuatro estados de la API, en el orden del filtro de hoy, con su etiqueta', () => {
      expect(ESTADOS.map((e) => e.valor)).toEqual(['PENDIENTE', 'EN_LISTA_ESPERA', 'FINALIZADA', 'CANCELADA']);
      expect(ESTADOS.map((e) => e.etiqueta)).toEqual(['Pendiente', 'En lista de espera', 'Finalizada', 'Cancelada']);
      expect(etiquetaEstado('EN_LISTA_ESPERA')).toBe('En lista de espera');
      // Un estado que la pantalla no conoce sale tal cual: nunca se esconde.
      expect(etiquetaEstado('OTRO')).toBe('OTRO');
      expect(etiquetaEstado(undefined)).toBe('');
    });

    it('la pastilla lleva el puesto en la lista de espera (hoy «LISTA DE ESPERA · 3»)', () => {
      expect(textoPastilla({ estado: 'EN_LISTA_ESPERA', posicionListaEspera: 3 })).toBe('En lista de espera · puesto 3');
      expect(textoPastilla({ estado: 'EN_LISTA_ESPERA', posicionListaEspera: null })).toBe('En lista de espera');
      expect(textoPastilla({ estado: 'PENDIENTE', posicionListaEspera: 4 })).toBe('Pendiente');
      expect(textoPastilla({ estado: 'FINALIZADA' })).toBe('Finalizada');
    });

    it('el tono: pendiente en ocre (atención), finalizada en «bien», el resto neutro', () => {
      expect(tonoEstado('PENDIENTE')).toBe('atencion');
      expect(tonoEstado('EN_LISTA_ESPERA')).toBe('espera');
      expect(tonoEstado('FINALIZADA')).toBe('bien');
      expect(tonoEstado('CANCELADA')).toBe('neutro');
      expect(tonoEstado('OTRO')).toBe('neutro');
    });
  });

  describe('«hace cuánto llegó» (añadido)', () => {
    const hace = (ms: number): number => segundos(new Date(AHORA.getTime() - ms));
    const MIN = 60_000;
    const H = 60 * MIN;

    it('minutos y horas por lo transcurrido', () => {
      expect(haceCuanto(hace(20_000), AHORA)).toBe('Ahora mismo');
      expect(haceCuanto(hace(MIN), AHORA)).toBe('Hace 1 min');
      expect(haceCuanto(hace(59 * MIN), AHORA)).toBe('Hace 59 min');
      expect(haceCuanto(hace(H), AHORA)).toBe('Hace 1 h');
      expect(haceCuanto(hace(23 * H), AHORA)).toBe('Hace 23 h');
    });

    it('días, semanas, meses y años por el calendario', () => {
      expect(haceCuanto(segundos(new Date(2026, 9, 22, 9, 0)), AHORA)).toBe('Ayer');
      expect(haceCuanto(segundos(new Date(2026, 9, 20, 18, 0)), AHORA)).toBe('Hace 3 días');
      expect(haceCuanto(segundos(new Date(2026, 9, 16, 12, 0)), AHORA)).toBe('Hace 1 semana');
      expect(haceCuanto(segundos(new Date(2026, 9, 1, 12, 0)), AHORA)).toBe('Hace 3 semanas');
      expect(haceCuanto(segundos(new Date(2026, 8, 20, 12, 0)), AHORA)).toBe('Hace 1 mes');
      expect(haceCuanto(segundos(new Date(2026, 3, 1, 12, 0)), AHORA)).toBe('Hace 6 meses');
      expect(haceCuanto(segundos(new Date(2025, 9, 1, 12, 0)), AHORA)).toBe('Hace 1 año');
      expect(haceCuanto(segundos(new Date(2023, 0, 1, 12, 0)), AHORA)).toBe('Hace 3 años');
    });

    it('sin fecha, inválida o en el futuro (reloj adelantado)', () => {
      expect(haceCuanto(null, AHORA)).toBe('');
      expect(haceCuanto('no es fecha', AHORA)).toBe('');
      expect(haceCuanto(hace(-5 * MIN), AHORA)).toBe('Ahora mismo');
    });

    it('acepta las formas de fecha de la API (segundos, ISO)', () => {
      expect(haceCuanto(new Date(2026, 9, 20, 12).toISOString(), AHORA)).toBe('Hace 3 días');
      expect(haceCuanto(String(hace(2 * H)), AHORA)).toBe('Hace 2 h');
    });
  });

  describe('fila de la lista', () => {
    const item = {
      referencia: 'PRE-7',
      nombre: 'Lucía',
      apellidos: 'Pérez Ruiz',
      dni: '',
      deporte: 'TAEKWONDO',
      estado: 'EN_LISTA_ESPERA',
      posicionListaEspera: 2,
      observaciones: 'Solo puede los martes',
      creadaEn: segundos(new Date(2026, 9, 20, 18, 0)),
    };

    it('nombre, referencia y DNI o «Sin DNI», disciplina, pastilla, observación y llegada', () => {
      const fila = filaLista(item, AHORA, (c) => (c === 'TAEKWONDO' ? 'Taekwondo' : c));
      expect(fila).toEqual({
        referencia: 'PRE-7',
        nombre: 'Lucía Pérez Ruiz',
        dni: 'Sin DNI',
        disciplina: 'Taekwondo',
        estado: 'EN_LISTA_ESPERA',
        pastilla: 'En lista de espera · puesto 2',
        tono: 'espera',
        conObservacion: true,
        llegada: 'Hace 3 días',
        llegadaCompleta: jasmine.stringMatching(/^20\/10\/2026, 18:00$/),
        llegadaIso: new Date(2026, 9, 20, 18, 0).toISOString(),
      });
    });

    it('«Con observación» como hoy: cualquier observación (no vacía)', () => {
      expect(conObservacion({ observaciones: 'x' })).toBeTrue();
      expect(conObservacion({ observaciones: '' })).toBeFalse();
      expect(conObservacion({ observaciones: null })).toBeFalse();
      expect(conObservacion({})).toBeFalse();
    });

    it('nombre y DNI sin datos', () => {
      expect(nombreCompleto({ nombre: 'Ana', apellidos: null })).toBe('Ana');
      expect(nombreCompleto({})).toBe('');
      expect(textoDni({ dni: '12345678Z' })).toBe('12345678Z');
      expect(textoDni({ dni: null })).toBe('Sin DNI');
    });

    it('el orden de la lista es el de la API (la más reciente primero): la vista no reordena', () => {
      const items = [
        { referencia: 'B', creadaEn: 10 },
        { referencia: 'A', creadaEn: 30 },
        { referencia: 'C', creadaEn: 20 },
      ];
      expect(items.map((i) => filaLista(i, AHORA, (c) => c).referencia)).toEqual(['B', 'A', 'C']);
    });
  });

  describe('avisos por correo', () => {
    it('etiquetas de los estados de aviso de la API; uno desconocido, tal cual', () => {
      expect(etiquetaEmail('NO_ENVIADO')).toBe('Sin enviar');
      expect(etiquetaEmail('PENDIENTE')).toBe('En cola');
      expect(etiquetaEmail('ENVIADO')).toBe('Enviado');
      expect(etiquetaEmail('ERROR')).toBe('Con error');
      expect(etiquetaEmail('RARO')).toBe('RARO');
      expect(etiquetaEmail(null)).toBe('Sin dato');
    });

    it('email inicial como hoy («Enviado» o «Pendiente») e intentos con plural', () => {
      expect(textoEmailInicial({ emailEnviado: true })).toBe('Enviado');
      expect(textoEmailInicial({ emailEnviado: false })).toBe('Pendiente');
      expect(textoIntentos(1)).toBe('1 intento');
      expect(textoIntentos(3)).toBe('3 intentos');
      expect(textoIntentos(0)).toBe('0 intentos');
      expect(textoIntentos(undefined)).toBe('0 intentos');
    });
  });

  describe('paginación', () => {
    it('«1–25 de 60» como hoy, con la página de la API (desde 0)', () => {
      expect(textoRango(0, 25, 60)).toBe('1–25 de 60');
      expect(textoRango(2, 25, 60)).toBe('51–60 de 60');
      expect(textoRango(0, 25, 6)).toBe('1–6 de 6');
      expect(textoRango(0, 25, 1044)).toBe('1–25 de 1.044');
      expect(textoRango(0, 25, 0)).toBe('0 de 0');
    });
  });

  describe('temporada (desplegable)', () => {
    it('la temporada cambia el 30 de junio, como TemporadaService.paraFecha', () => {
      expect(temporadaDe(new Date(2026, 5, 29))).toBe('2025-2026');
      expect(temporadaDe(new Date(2026, 5, 30))).toBe('2026-2027');
      expect(temporadaDe(new Date(2026, 9, 23))).toBe('2026-2027');
    });

    it('la actual y las cuatro anteriores, más las vistas y la elegida, de la más reciente a la más antigua', () => {
      expect(opcionesTemporada(AHORA, [], '')).toEqual(['2026-2027', '2025-2026', '2024-2025', '2023-2024', '2022-2023']);
      expect(opcionesTemporada(AHORA, ['2019-2020', '2026-2027', 'basura', null], '2027-2028')).toEqual([
        '2027-2028',
        '2026-2027',
        '2025-2026',
        '2024-2025',
        '2023-2024',
        '2022-2023',
        '2019-2020',
      ]);
    });
  });

  describe('acciones según el estado (como hoy)', () => {
    it('cambiar la asignación: pendiente o en lista de espera', () => {
      expect(puedeCambiarAsignacion({ estado: 'PENDIENTE' })).toBeTrue();
      expect(puedeCambiarAsignacion({ estado: 'EN_LISTA_ESPERA' })).toBeTrue();
      expect(puedeCambiarAsignacion({ estado: 'FINALIZADA' })).toBeFalse();
      expect(puedeCambiarAsignacion({ estado: 'CANCELADA' })).toBeFalse();
      expect(puedeCambiarAsignacion(null)).toBeFalse();
    });

    it('reenviar aviso de cambio: pendiente con turnos modificados', () => {
      expect(puedeReenviarCambio({ estado: 'PENDIENTE', turnosModificadosEn: 1760000000 })).toBeTrue();
      expect(puedeReenviarCambio({ estado: 'PENDIENTE', turnosModificadosEn: null })).toBeFalse();
      expect(puedeReenviarCambio({ estado: 'EN_LISTA_ESPERA', turnosModificadosEn: 1760000000 })).toBeFalse();
    });

    it('eliminar definitivamente: solo cancelada y solo Administración', () => {
      expect(puedeEliminar({ estado: 'CANCELADA' }, true)).toBeTrue();
      expect(puedeEliminar({ estado: 'CANCELADA' }, false)).toBeFalse();
      expect(puedeEliminar({ estado: 'PENDIENTE' }, true)).toBeFalse();
      expect(puedeEliminar(null, true)).toBeFalse();
    });
  });
});
