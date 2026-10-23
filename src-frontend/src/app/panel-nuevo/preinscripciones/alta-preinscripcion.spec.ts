import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PreinscripcionesAdminComponent } from '../../componentes/endpoints/preinscripciones-admin/preinscripciones-admin.component';
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
 * de verdad (8b-3h): la misma solicitud, los mismos pasos y la misma elección de ficha (nueva o existente), los mismos
 * campos marcados y el MISMO cuerpo de `POST /preinscripciones/{ref}/finalizar` (y de `PUT …/turnos`).
 */
describe('AltaPreinscripcion (8b-3h) igual que la pantalla de hoy', () => {
  let api: any;

  function hoy(catalogo: Catalogo = CATALOGO_TAEMOI): PreinscripcionesAdminComponent {
    api = {
      finalizar: jasmine.createSpy('finalizar').and.returnValue(of(void 0)),
      actualizarTurnos: jasmine.createSpy('actualizarTurnos').and.returnValue(of(void 0)),
      listar: () => of({ content: [], page: 0, totalElements: 0, totalPages: 0 }),
    };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(catalogo)] });
    const auth = { tieneRolAdmin: () => true };
    const route = { snapshot: { queryParamMap: { get: () => null } } };
    const router = { navigate: () => Promise.resolve(true) };
    return TestBed.runInInjectionContext(() => new PreinscripcionesAdminComponent(api, auth as any, route as any, router as any));
  }

  const catalogo = (): CatalogoService => TestBed.inject(CatalogoService);

  /** El cuerpo que envía hoy, tras `abrirFinalizacion`, los pasos y `finalizar`. */
  function cuerpoDeHoy(c: PreinscripcionesAdminComponent): unknown {
    const referencia = c.seleccion.referencia;
    c.finalizar();
    expect(api.finalizar).toHaveBeenCalledTimes(1);
    expect(api.finalizar.calls.mostRecent().args[0]).toBe(referencia);
    return api.finalizar.calls.mostRecent().args[1];
  }

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

  /** Abre las dos con la misma solicitud y compara el estado inicial. */
  function abrirAmbas(solicitud: any, cat: Catalogo = CATALOGO_TAEMOI) {
    const c = hoy(cat);
    c.seleccion = structuredClone(solicitud);
    c.abrirFinalizacion();
    const nueva = new AltaPreinscripcion(structuredClone(solicitud), catalogo());
    expect(estado(nueva)).toEqual(estado(c));
    return { c, nueva };
  }

  it('sin fichas compatibles: ficha nueva, tarifa inicial y grado; mismo cuerpo', () => {
    const { c, nueva } = abrirAmbas(base());
    expect(nueva.accionAlumno).toBe('CREAR_NUEVO');
    expect(nueva.datosAlta.tipoTarifa).toBe('INFANTIL');
    expect(nueva.datosAlta.grado).toBe('BLANCO');
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
    expect(nueva.cuerpo().alumnoId).toBeUndefined();
  });

  it('una ficha con el mismo DNI: se vincula sola, solo los campos distintos, y no deja crear otra', () => {
    const { c, nueva } = abrirAmbas(base({ alumnosCoincidentes: [EXISTENTE] }));
    expect(nueva.accionAlumno).toBe('VINCULAR_EXISTENTE');
    expect([...nueva.camposActualizar]).toEqual(['NOMBRE']);
    expect(nueva.tieneCoincidenciaDniExacta).toBeTrue();
    nueva.crearNuevaFicha();
    c.crearNuevaFicha();
    expect(nueva.accionAlumno).toBe('VINCULAR_EXISTENTE');
    // Los correos no coinciden: hay que elegir el definitivo de acceso.
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.decisionEmail = 'USAR_PREINSCRIPCION';
    c.decisionEmail = 'USAR_PREINSCRIPCION';
    expect(estado(nueva)).toEqual(estado(c));
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
    expect(nueva.cuerpo().alumnoId).toBe(3);
    expect(nueva.cuerpo().decisionEmail).toBe('USAR_PREINSCRIPCION');
  });

  it('varias compatibles: no elige ninguna; al elegir la otra o crear nueva, igual que hoy', () => {
    const sol = base({ dni: '11111111H', alumnosCoincidentes: [EXISTENTE, OTRA] });
    const { c, nueva } = abrirAmbas(sol);
    expect(nueva.accionAlumno).toBe('');
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.elegirAlumno(nueva.solicitud['alumnosCoincidentes'][1]);
    c.elegirAlumno(c.seleccion.alumnosCoincidentes[1]);
    expect(estado(nueva)).toEqual(estado(c));
    nueva.alternarCampo('TELEFONO', false);
    c.alternarCampo('TELEFONO', false);
    nueva.crearNuevaFicha();
    c.crearNuevaFicha();
    expect(estado(nueva)).toEqual(estado(c));
    expect(nueva.accionAlumno).toBe('CREAR_NUEVO');
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
  });

  it('sin DNI: ninguna coincidencia se vincula sola; NIF y teléfono 2 no se comparan', () => {
    const sol = base({ dni: null, alumnosCoincidentes: [{ ...OTRA, telefono2: 699887766, requiereDatosDeporte: false }] });
    const { c, nueva } = abrirAmbas(sol);
    expect(nueva.accionAlumno).toBe('');
    nueva.elegirAlumno(nueva.solicitud['alumnosCoincidentes'][0]);
    c.elegirAlumno(c.seleccion.alumnosCoincidentes[0]);
    expect(estado(nueva)).toEqual(estado(c));
    expect(nueva.camposActualizar.has('NIF')).toBeFalse();
    expect(nueva.camposActualizar.has('TELEFONO2')).toBeFalse();
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
  });

  it('solicitud antigua sin discapacidad: hay que confirmarla y viaja en discapacidadHistorica', () => {
    const { c, nueva } = abrirAmbas(base({ tieneDiscapacidad: null, alumnosCoincidentes: [EXISTENTE] }));
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.confirmarDiscapacidadHistorica(true);
    c.confirmarDiscapacidadHistorica(true);
    nueva.decisionEmail = 'CONSERVAR_FICHA';
    c.decisionEmail = 'CONSERVAR_FICHA';
    expect(estado(nueva)).toEqual(estado(c));
    expect(nueva.camposActualizar.has('TIENE_DISCAPACIDAD')).toBeTrue();
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
    expect(nueva.cuerpo().discapacidadHistorica).toBeTrue();
  });

  it('tarifas: cambiar de tarifa limpia rol y grupo y pone el importe del catálogo; las banderas exigen rol o grupo', () => {
    const { c, nueva } = abrirAmbas(base());
    for (const t of ['PADRES_HIJOS', 'HERMANOS', 'ADULTO']) {
      nueva.datosAlta.tipoTarifa = t;
      c.datosAlta.tipoTarifa = t;
      nueva.cambiarTarifa();
      c.cambiarTarifa();
      expect(estado(nueva)).withContext(t).toEqual(estado(c));
      expect(nueva.requiereRolFamiliar).withContext(t).toBe(c.requiereRolFamiliar);
      expect(nueva.requiereGrupoFamiliar).withContext(t).toBe(c.requiereGrupoFamiliar);
    }
    nueva.datosAlta.tipoTarifa = 'PADRES_HIJOS';
    c.datosAlta.tipoTarifa = 'PADRES_HIJOS';
    nueva.cambiarTarifa();
    c.cambiarTarifa();
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.datosAlta.rolFamiliar = 'MADRE';
    c.datosAlta.rolFamiliar = 'MADRE';
    nueva.datosAlta.cuantiaTarifa = 12.5;
    c.datosAlta.cuantiaTarifa = 12.5;
    expect(nueva.finalizacionValida).toBeTrue();
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
  });

  it('cuantía negativa o vacía no es válida', () => {
    const { nueva } = abrirAmbas(base());
    nueva.datosAlta.cuantiaTarifa = -1;
    expect(nueva.finalizacionValida).toBeFalse();
    nueva.datosAlta.cuantiaTarifa = null;
    expect(nueva.finalizacionValida).toBeFalse();
  });

  it('otra escuela: sin tarifa preseleccionada ni válida hasta elegir una', () => {
    const { c, nueva } = abrirAmbas(base({ deporte: 'JUDO' }), CATALOGO_SEGUNDA_ESCUELA);
    expect(nueva.datosAlta.tipoTarifa).toBe('');
    expect(nueva.finalizacionValida).toBeFalse();
    expect(nueva.tarifasDisponibles.map((t) => t.codigo)).toEqual(c.tarifasDisponibles.map((t) => t.codigo));
    nueva.datosAlta.tipoTarifa = 'JUDO_MES';
    c.datosAlta.tipoTarifa = 'JUDO_MES';
    nueva.cambiarTarifa();
    c.cambiarTarifa();
    expect(estado(nueva)).toEqual(estado(c));
    expect(nueva.cuerpo()).toEqual(cuerpoDeHoy(c) as any);
  });

  it('enseña los valores como hoy (fecha dd/mm/aaaa, Sí/No, «Sin dato»)', () => {
    const { c, nueva } = abrirAmbas(base({ alumnosCoincidentes: [EXISTENTE] }));
    for (const campo of nueva.camposAplicables) {
      expect(nueva.mostrarValor(nueva.valorPreinscripcion(campo), campo.clave)).toBe(
        c.mostrarValor(c.valorPreinscripcion(campo), campo.clave)
      );
      expect(nueva.mostrarValor(EXISTENTE[campo.alumno as keyof typeof EXISTENTE], campo.clave)).toBe(
        c.mostrarValor(EXISTENTE[campo.alumno as keyof typeof EXISTENTE], campo.clave)
      );
    }
    expect(nueva.camposAplicables.map((x) => x.clave)).toEqual(c.camposAplicables.map((x) => x.clave));
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
      const c = hoy();
      api.turnos = jasmine.createSpy('turnos').and.returnValue(of(TURNOS));
      c.seleccion = solicitud();
      c.abrirEdicionTurnos();
      const ordenados = ordenarTurnos(TURNOS);
      expect(ordenados.map((t) => t.id)).toEqual(c.turnosDisponibles.map((t) => t.id));
      expect(ordenados.map((t) => t.id)).toEqual([11, 12, 13, 21]);
      for (const t of ordenados) {
        expect(turnoFueraEdad(t, c.seleccion)).withContext(String(t.id)).toBe(c.turnoFueraEdad(t));
        expect(esPlazaActual(t, c.seleccion)).withContext(String(t.id)).toBe(c.turnoEsPlazaActual(t));
      }
      let ids = [11];
      for (const [turno, marcado] of [[TURNOS[1], true], [TURNOS[0], true], [TURNOS[2], true], [TURNOS[2], false]] as const) {
        ids = alternarTurno(ids, turno, ordenados, marcado);
        c.alternarTurnoAdmin(turno, marcado);
        expect(ids).toEqual(c.turnoIdsEdicion);
        expect(turnosCambiados(c.seleccion, ids)).toBe(c.turnosEdicionCambiados);
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
