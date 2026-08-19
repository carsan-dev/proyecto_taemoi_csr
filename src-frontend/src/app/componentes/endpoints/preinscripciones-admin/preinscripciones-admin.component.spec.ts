import { of } from 'rxjs';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

describe('PreinscripcionesAdminComponent', () => {
  let component: PreinscripcionesAdminComponent;
  let api: any;

  beforeEach(() => {
    api = {
      finalizar: jasmine.createSpy('finalizar').and.returnValue(of(void 0)),
      listar: jasmine.createSpy('listar').and.returnValue(of({content: [], page: 0, totalElements: 0, totalPages: 0})),
    };
    const auth = {tieneRolAdmin: () => true};
    const route = {snapshot: {queryParamMap: {get: () => null}}};
    const router = {navigate: () => Promise.resolve(true)};
    component = new PreinscripcionesAdminComponent(api, auth as any, route as any, router as any);
  });

  it('prepara alta automática cuando no existe ficha', () => {
    component.seleccion = solicitudBase();

    component.abrirFinalizacion();

    expect(component.mostrandoFinalizacion).toBeTrue();
    expect(component.requiereDatosDeporte).toBeTrue();
    expect(component.datosAlta.tipoTarifa).toBe('INFANTIL');
    expect(component.datosAlta.cuantiaTarifa).toBe(28);
    expect(component.datosAlta.grado).toBe('BLANCO');
  });

  it('selecciona por defecto solo datos diferentes de ficha existente', () => {
    component.seleccion = {
      ...solicitudBase(),
      alumnosCoincidentes: [{
        id: 3,
        nif: '12345678Z',
        nombre: 'Nombre anterior',
        apellidos: 'García López',
        fechaNacimiento: '2014-04-12',
        direccion: 'Calle Mayor 1',
        telefono: 612345678,
        telefono2: null,
        email: 'conservar@example.com',
        tieneDiscapacidad: false,
        responsableLegalNombre: 'María García',
        responsableLegalNif: '87654321X',
        requiereDatosDeporte: false,
      }],
    };

    component.abrirFinalizacion();

    expect([...component.camposActualizar].sort()).toEqual(['EMAIL', 'NOMBRE']);
    expect(component.requiereDatosDeporte).toBeFalse();
    expect(component.tieneCoincidenciaDniExacta).toBeTrue();
    component.crearNuevaFicha();
    expect(component.accionAlumno).toBe('VINCULAR_EXISTENTE');
  });

  it('envía datos deportivos y campos confirmados al finalizar', () => {
    component.seleccion = solicitudBase();
    component.abrirFinalizacion();
    component.camposActualizar.add('EMAIL');

    component.finalizar();

    const payload = api.finalizar.calls.mostRecent().args[1];
    expect(payload.alumnoId).toBeUndefined();
    expect(payload.accionAlumno).toBe('CREAR_NUEVO');
    expect(payload.camposActualizar).toContain('EMAIL');
    expect(payload.datosDeporte.tipoTarifa).toBe('INFANTIL');
    expect(payload.datosDeporte.grado).toBe('BLANCO');
  });

  it('exige una elección expresa cuando una solicitud sin DNI tiene candidatos', () => {
    component.seleccion = {
      ...solicitudBase(),
      dni: null,
      alumnosCoincidentes: [{
        id: 3,
        nif: '87654321X',
        nombre: 'Ana',
        apellidos: 'García López',
        fechaNacimiento: '2014-04-12',
        telefono2: 699887766,
        requiereDatosDeporte: false,
      }],
    };

    component.abrirFinalizacion();

    expect(component.accionAlumno).toBe('');
    expect(component.alumnoSeleccionado).toBeUndefined();
    expect(component.finalizacionValida).toBeFalse();
    component.elegirAlumno(component.seleccion.alumnosCoincidentes[0]);
    expect(component.accionAlumno).toBe('VINCULAR_EXISTENTE');
    expect(component.camposActualizar.has('NIF')).toBeFalse();
    expect(component.camposActualizar.has('TELEFONO2')).toBeFalse();
    expect(component.finalizacionValida).toBeTrue();
  });

  it('exige completar la discapacidad de una preinscripción antigua', () => {
    component.seleccion = {...solicitudBase(), tieneDiscapacidad: null};

    component.abrirFinalizacion();

    expect(component.finalizacionValida).toBeFalse();
    component.discapacidadHistorica = false;
    expect(component.finalizacionValida).toBeTrue();
    component.finalizar();
    expect(api.finalizar.calls.mostRecent().args[1].discapacidadHistorica).toBeFalse();
  });

  function solicitudBase() {
    return {
      referencia: 'PRE-1',
      deporte: 'TAEKWONDO',
      nombre: 'Ana',
      apellidos: 'García López',
      dni: '12345678Z',
      fechaNacimiento: '2014-04-12',
      direccion: 'Calle Mayor 1',
      telefono: '+34 612 345 678',
      telefono2: '',
      email: 'ana@example.com',
      tieneDiscapacidad: false,
      tutorNombre: 'María García',
      tutorDni: '87654321X',
      alumnosCoincidentes: [],
      grupo: 'Taekwondo · Lunes y miércoles',
    };
  }
});
