import { TestBed } from '@angular/core/testing';

import { valorDeHoy } from '../../testing/oraculo-hoy';
import { HOY } from './alta-preinscripcion.hoy';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { Catalogo } from '../../features/catalogo/catalogo.models';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { AltaPreinscripcion } from './alta-preinscripcion';
import {
  alternarTurno,
  esPlazaActual,
  necesitaForzar,
  ordenarTurnos,
  turnoFueraEdad,
  turnosCambiados,
} from './preinscripciones-vista';

/**
 * «Revisar y finalizar alta» y «Cambiar la asignación» de la pantalla nueva frente a `PreinscripcionesAdminComponent`
 * (8b-3h): la misma solicitud, los mismos pasos y la misma elección de ficha (nueva o existente), los mismos campos
 * marcados y el MISMO cuerpo de `POST /preinscripciones/{ref}/finalizar` (y de `PUT …/turnos`). La pantalla de hoy se
 * retiró en 8b-Z: sus valores van grabados en `HOY` (alta-preinscripcion.hoy.ts), en el orden en que se comparan.
 */
describe('AltaPreinscripcion (8b-3h) igual que la pantalla de hoy', () => {
  /** El catálogo de la escuela en un TestBed limpio (la pantalla nueva lo lee de `CatalogoService`). */
  function conCatalogo(catalogo: Catalogo = CATALOGO_TAEMOI): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(catalogo)] });
  }

  const catalogo = (): CatalogoService => TestBed.inject(CatalogoService);

  /** El cuerpo tal como viaja en el POST (JSON: las claves sin valor no se envían), como se grabó el de hoy. */
  const comoJson = (valor: unknown): unknown => JSON.parse(JSON.stringify(valor));

  function estado(x: {
    accionAlumno: string;
    alumnoSeleccionado: any;
    camposActualizar: Set<string>;
    datosAlta: any;
    finalizacionValida: boolean;
    correosDiferentes: any;
    tieneCoincidenciaDniExacta: boolean;
    requiereDatosDeporte: any;
  }) {
    return {
      accion: x.accionAlumno,
      alumno: x.alumnoSeleccionado?.id ?? null,
      campos: [...x.camposActualizar].sort(),
      datos: { ...x.datosAlta },
      valida: x.finalizacionValida,
      correos: !!x.correosDiferentes,
      dniExacto: x.tieneCoincidenciaDniExacta,
      deporte: !!x.requiereDatosDeporte,
    };
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0));
  });
  afterEach(() => jasmine.clock().uninstall());

  function base(extra: Record<string, unknown> = {}): any {
    return {
      referencia: 'PRE-1',
      deporte: 'TAEKWONDO',
      nombre: 'Ana',
      apellidos: 'García López',
      dni: '12345678Z',
      fechaNacimiento: [2014, 4, 12],
      direccion: 'Calle Mayor 1',
      telefono: '+34 612 345 678',
      telefono2: '',
      email: 'ana@example.com',
      tieneDiscapacidad: false,
      tutorNombre: 'María García',
      tutorDni: '87654321X',
      alumnosCoincidentes: [],
      grupo: 'Taekwondo · Lunes y miércoles',
      turnosSolicitados: [{ id: 11 }, { id: 12 }],
      ...extra,
    };
  }

  const EXISTENTE = {
    id: 3,
    nif: '12345678Z',
    nombre: 'Nombre anterior',
    apellidos: 'García López',
    fechaNacimiento: 1397260800000,
    direccion: 'Calle Mayor 1',
    telefono: 612345678,
    telefono2: null,
    email: 'conservar@example.com',
    tieneDiscapacidad: false,
    responsableLegalNombre: 'María García',
    responsableLegalNif: '87654321X',
    requiereDatosDeporte: false,
    tipoTarifa: 'INFANTIL',
    cuantiaTarifa: 35,
    activo: false,
  };
  const OTRA = { ...EXISTENTE, id: 4, nif: '99999999R', nombre: 'Ana', email: 'ana@example.com', requiereDatosDeporte: true };

  /** Abre la nueva con la solicitud y compara el estado inicial con el de hoy. */
  function abrirAmbas(solicitud: any, cat: Catalogo = CATALOGO_TAEMOI) {
    conCatalogo(cat);
    const nueva = new AltaPreinscripcion(structuredClone(solicitud), catalogo());
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    return { nueva };
  }

  it('sin fichas compatibles: ficha nueva, tarifa inicial y grado; mismo cuerpo', () => {
    const { nueva } = abrirAmbas(base());
    expect(nueva.accionAlumno).toBe('CREAR_NUEVO');
    expect(nueva.datosAlta.tipoTarifa).toBe('INFANTIL');
    expect(nueva.datosAlta.grado).toBe('BLANCO');
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
    expect(nueva.cuerpo().alumnoId).toBeUndefined();
  });

  it('una ficha con el mismo DNI: se vincula sola, solo los campos distintos, y no deja crear otra', () => {
    const { nueva } = abrirAmbas(base({ alumnosCoincidentes: [EXISTENTE] }));
    expect(nueva.accionAlumno).toBe('VINCULAR_EXISTENTE');
    expect([...nueva.camposActualizar]).toEqual(['NOMBRE']);
    expect(nueva.tieneCoincidenciaDniExacta).toBeTrue();
    nueva.crearNuevaFicha();
    expect(nueva.accionAlumno).toBe('VINCULAR_EXISTENTE');
    // Los correos no coinciden: hay que elegir el definitivo de acceso.
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.decisionEmail = 'USAR_PREINSCRIPCION';
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
    expect(nueva.cuerpo().alumnoId).toBe(3);
    expect(nueva.cuerpo().decisionEmail).toBe('USAR_PREINSCRIPCION');
  });

  it('varias compatibles: no elige ninguna; al elegir la otra o crear nueva, igual que hoy', () => {
    const sol = base({ dni: '11111111H', alumnosCoincidentes: [EXISTENTE, OTRA] });
    const { nueva } = abrirAmbas(sol);
    expect(nueva.accionAlumno).toBe('');
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.elegirAlumno(nueva.solicitud['alumnosCoincidentes'][1]);
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    nueva.alternarCampo('TELEFONO', false);
    nueva.crearNuevaFicha();
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    expect(nueva.accionAlumno).toBe('CREAR_NUEVO');
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
  });

  it('sin DNI: ninguna coincidencia se vincula sola; NIF y teléfono 2 no se comparan', () => {
    const sol = base({ dni: null, alumnosCoincidentes: [{ ...OTRA, telefono2: 699887766, requiereDatosDeporte: false }] });
    const { nueva } = abrirAmbas(sol);
    expect(nueva.accionAlumno).toBe('');
    nueva.elegirAlumno(nueva.solicitud['alumnosCoincidentes'][0]);
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    expect(nueva.camposActualizar.has('NIF')).toBeFalse();
    expect(nueva.camposActualizar.has('TELEFONO2')).toBeFalse();
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
  });

  it('solicitud antigua sin discapacidad: hay que confirmarla y viaja en discapacidadHistorica', () => {
    const { nueva } = abrirAmbas(base({ tieneDiscapacidad: null, alumnosCoincidentes: [EXISTENTE] }));
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.confirmarDiscapacidadHistorica(true);
    nueva.decisionEmail = 'CONSERVAR_FICHA';
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    expect(nueva.camposActualizar.has('TIENE_DISCAPACIDAD')).toBeTrue();
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
    expect(nueva.cuerpo().discapacidadHistorica).toBeTrue();
  });

  it('tarifas: cambiar de tarifa limpia rol y grupo y pone el importe del catálogo; las banderas exigen rol o grupo', () => {
    const { nueva } = abrirAmbas(base());
    for (const t of ['PADRES_HIJOS', 'HERMANOS', 'ADULTO']) {
      nueva.datosAlta.tipoTarifa = t;
      nueva.cambiarTarifa();
      expect(estado(nueva)).withContext(t).toEqual(valorDeHoy(HOY));
      expect(nueva.requiereRolFamiliar).withContext(t).toBe(valorDeHoy(HOY));
      expect(nueva.requiereGrupoFamiliar).withContext(t).toBe(valorDeHoy(HOY));
    }
    nueva.datosAlta.tipoTarifa = 'PADRES_HIJOS';
    nueva.cambiarTarifa();
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.datosAlta.rolFamiliar = 'MADRE';
    nueva.datosAlta.cuantiaTarifa = 12.5;
    expect(nueva.finalizacionValida).toBeTrue();
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
  });

  it('cuantía negativa o vacía no es válida', () => {
    const { nueva } = abrirAmbas(base());
    nueva.datosAlta.cuantiaTarifa = -1;
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.datosAlta.cuantiaTarifa = null;
    expect(nueva.finalizacionValida).toBeFalse();
  });

  it('otra escuela: sin tarifa preseleccionada ni válida hasta elegir una', () => {
    const { nueva } = abrirAmbas(base({ deporte: 'JUDO' }), CATALOGO_SEGUNDA_ESCUELA);
    expect(nueva.datosAlta.tipoTarifa).toBe('');
    expect(nueva.finalizacionValida).toBeFalse();
    expect(nueva.tarifasDisponibles.map((t) => t.codigo)).toEqual(valorDeHoy(HOY));
    nueva.datosAlta.tipoTarifa = 'JUDO_MES';
    nueva.cambiarTarifa();
    expect(estado(nueva)).toEqual(valorDeHoy(HOY));
    expect(comoJson(nueva.cuerpo())).toEqual(valorDeHoy(HOY));
  });

  it('enseña los valores como hoy (fecha dd/mm/aaaa, Sí/No, «Sin dato»)', () => {
    const { nueva } = abrirAmbas(base({ alumnosCoincidentes: [EXISTENTE] }));
    for (const campo of nueva.camposAplicables) {
      expect(nueva.mostrarValor(nueva.valorPreinscripcion(campo), campo.clave)).toBe(
        valorDeHoy(HOY)
      );
      expect(nueva.mostrarValor(EXISTENTE[campo.alumno as keyof typeof EXISTENTE], campo.clave)).toBe(
        valorDeHoy(HOY)
      );
    }
    expect(nueva.camposAplicables.map((x) => x.clave)).toEqual(valorDeHoy(HOY));
  });

  describe('«Cambiar la asignación» (turnos)', () => {
    const TURNOS = [
      { id: 13, diaSemana: 'Jueves', horaInicio: '18:00', grupo: 'Infantil', rangoEdadMin: 5, rangoEdadMax: 12, completo: false },
      { id: 12, diaSemana: 'lunes', horaInicio: '19:00', grupo: 'Infantil', rangoEdadMin: 5, rangoEdadMax: 12, completo: false },
      { id: 21, diaSemana: 'Sábado', horaInicio: '10:00', grupo: 'Mayores', rangoEdadMin: 16, rangoEdadMax: 99, completo: true },
      { id: 11, diaSemana: 'Lunes', horaInicio: '18:00', grupo: 'Infantil', rangoEdadMin: 5, rangoEdadMax: 12, completo: true },
    ];
    const solicitud = (estado = 'PENDIENTE') => base({ estado, temporada: '2026-2027', turnosSolicitados: [TURNOS[3]] });

    it('el mismo orden (lunes a domingo, luego hora), la misma regla de un turno por día y los mismos avisos de edad y plaza', () => {
      const seleccion = solicitud();
      const ordenados = ordenarTurnos(TURNOS);
      expect(ordenados.map((t) => t.id)).toEqual(valorDeHoy(HOY));
      expect(ordenados.map((t) => t.id)).toEqual([11, 12, 13, 21]);
      for (const t of ordenados) {
        expect(turnoFueraEdad(t, seleccion)).withContext(String(t.id)).toBe(valorDeHoy(HOY));
        expect(esPlazaActual(t, seleccion)).withContext(String(t.id)).toBe(valorDeHoy(HOY));
      }
      let ids = [11];
      for (const [turno, marcado] of [[TURNOS[1], true], [TURNOS[0], true], [TURNOS[2], true], [TURNOS[2], false]] as const) {
        ids = alternarTurno(ids, turno, ordenados, marcado);
        expect(ids).toEqual(valorDeHoy(HOY));
        expect(turnosCambiados(seleccion, ids)).toBe(valorDeHoy(HOY));
      }
      expect(ids).toEqual([12, 13]);
    });

    it('pide forzar con un turno NUEVO sin plaza o fuera de edad, no por el que ya tenía', () => {
      const sol = solicitud();
      expect(necesitaForzar(sol, TURNOS, [11, 13])).toBeFalse();
      expect(necesitaForzar(sol, TURNOS, [11, 21])).toBeTrue();
      expect(necesitaForzar(base({ temporada: '2026-2027', turnosSolicitados: [] }), TURNOS, [13])).toBeFalse();
    });

    it('sin cambios no hay nada que guardar; el orden de los ids no cuenta', () => {
      const sol = base({ turnosSolicitados: [{ id: 2 }, { id: 1 }] });
      expect(turnosCambiados(sol, [1, 2])).toBeFalse();
      expect(turnosCambiados(sol, [1])).toBeTrue();
      expect(turnosCambiados(sol, [1, 3])).toBeTrue();
    });
  });
});
