import { of } from 'rxjs';
import Swal from 'sweetalert2';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

describe('PreinscripcionesAdminComponent', () => {
  let component: PreinscripcionesAdminComponent;
  let api: any;

  beforeEach(() => {
    api = {
      finalizar: jasmine.createSpy('finalizar').and.returnValue(of(void 0)),
      actualizarTurnos: jasmine.createSpy('actualizarTurnos').and.returnValue(of(void 0)),
      reenviarCambioTurnos: jasmine.createSpy('reenviarCambioTurnos').and.returnValue(of(void 0)),
      turnos: jasmine.createSpy('turnos').and.returnValue(of([])),
      listar: jasmine.createSpy('listar').and.returnValue(of({content: [], page: 0, totalElements: 0, totalPages: 0})),
      plantillas: jasmine.createSpy('plantillas').and.returnValue(of({
        activa: {version: 1, contenido: {cabecera: '', contacto: '', titulo: '', consentimiento: '', normas: ['Norma'], importes: ''}, instrucciones: ''},
        historial: [],
      })),
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
        tipoTarifa: 'INFANTIL',
        cuantiaTarifa: 35,
      }],
    };

    component.abrirFinalizacion();

    expect([...component.camposActualizar].sort()).toEqual(['NOMBRE']);
    expect(component.correosDiferentes).toBeTrue();
    expect(component.decisionEmail).toBe('');
    expect(component.finalizacionValida).toBeFalse();
    component.decisionEmail = 'CONSERVAR_FICHA';
    expect(component.finalizacionValida).toBeTrue();
    expect(component.requiereDatosDeporte).toBeFalse();
    expect(component.datosAlta.cuantiaTarifa).toBe(35);
    expect(component.tieneCoincidenciaDniExacta).toBeTrue();
    component.crearNuevaFicha();
    expect(component.accionAlumno).toBe('VINCULAR_EXISTENTE');
  });

  it('envía datos deportivos y campos confirmados al finalizar', () => {
    component.seleccion = solicitudBase();
    component.abrirFinalizacion();
    component.finalizar();

    const payload = api.finalizar.calls.mostRecent().args[1];
    expect(payload.alumnoId).toBeUndefined();
    expect(payload.accionAlumno).toBe('CREAR_NUEVO');
    expect(payload.camposActualizar).not.toContain('EMAIL');
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
        email: 'ana@example.com',
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

  it('muestra todos los turnos públicos y marca los incompatibles con edad', () => {
    const turnos = [
      {id:11,diaSemana:'Lunes',horaInicio:'18:00',grupo:'Infantil',rangoEdadMin:5,rangoEdadMax:12,completo:false},
      {id:21,diaSemana:'Viernes',horaInicio:'20:00',grupo:'Mayores',rangoEdadMin:16,rangoEdadMax:99,completo:true},
    ];
    api.turnos.and.returnValue(of(turnos));
    component.seleccion = {...solicitudBase(), estado:'PENDIENTE', temporada:'2026-2027', turnosSolicitados:[turnos[0]]};

    component.abrirEdicionTurnos();

    expect(component.turnosDisponibles.length).toBe(2);
    expect(component.turnoFueraEdad(turnos[0])).toBeFalse();
    expect(component.turnoFueraEdad(turnos[1])).toBeTrue();
    expect(component.turnoIdsEdicion).toEqual([11]);
  });

  it('guarda un cambio compatible y conserva un único turno por día', async () => {
    const turnos = [
      {id:11,diaSemana:'Lunes',horaInicio:'18:00',grupo:'Infantil',rangoEdadMin:5,rangoEdadMax:12,completo:false},
      {id:12,diaSemana:'lunes',horaInicio:'19:00',grupo:'Infantil',rangoEdadMin:5,rangoEdadMax:12,completo:false},
      {id:13,diaSemana:'Jueves',horaInicio:'18:00',grupo:'Infantil',rangoEdadMin:5,rangoEdadMax:12,completo:false},
    ];
    api.turnos.and.returnValue(of(turnos));
    component.seleccion = {...solicitudBase(), estado:'PENDIENTE', temporada:'2026-2027', turnosSolicitados:[turnos[0]]};
    component.abrirEdicionTurnos();
    component.alternarTurnoAdmin(turnos[1], true);
    component.alternarTurnoAdmin(turnos[2], true);

    await component.guardarTurnos();

    expect(api.actualizarTurnos).toHaveBeenCalledWith('PRE-1',[12,13]);
  });

	 it('muestra como plaza actual un turno completo reservado por la solicitud pendiente', () => {
		 const turno = {id:11,completo:true};
		 component.seleccion = {...solicitudBase(),estado:'PENDIENTE',turnosSolicitados:[turno]};

		 expect(component.turnoEsPlazaActual(turno)).toBeTrue();

		 component.seleccion.estado = 'EN_LISTA_ESPERA';
		 expect(component.turnoEsPlazaActual(turno)).toBeFalse();
	 });

	 it('no pide forzado por una excepción que ya forma parte de la asignación', async () => {
		 const actual = {id:11,diaSemana:'Lunes',rangoEdadMin:16,rangoEdadMax:99,completo:true};
		 const nuevo = {id:13,diaSemana:'Jueves',rangoEdadMin:5,rangoEdadMax:12,completo:false};
		 component.seleccion = {...solicitudBase(),estado:'PENDIENTE',temporada:'2026-2027',turnosSolicitados:[actual]};
		 component.turnosDisponibles = [actual,nuevo];
		 component.turnoIdsEdicion = [11,13];
		 const alerta = spyOn(Swal,'fire').and.resolveTo({isConfirmed:true} as any);

		 await component.guardarTurnos();

		 expect(alerta.calls.allArgs().some((args:any[])=>args[0]?.title==='Forzar asignación de turnos')).toBeFalse();
		 expect(api.actualizarTurnos).toHaveBeenCalledWith('PRE-1',[11,13]);
	 });

	 it('pide confirmación por un turno nuevo sin plaza', async () => {
		 const actual = {id:11,diaSemana:'Lunes',rangoEdadMin:5,rangoEdadMax:12,completo:false};
		 const nuevo = {id:13,diaSemana:'Jueves',rangoEdadMin:5,rangoEdadMax:12,completo:true};
		 component.seleccion = {...solicitudBase(),estado:'PENDIENTE',temporada:'2026-2027',turnosSolicitados:[actual]};
		 component.turnosDisponibles = [actual,nuevo];
		 component.turnoIdsEdicion = [11,13];
		 const alerta = spyOn(Swal,'fire').and.resolveTo({isConfirmed:false} as any);

		 await component.guardarTurnos();

		 expect(alerta).toHaveBeenCalled();
		 expect(api.actualizarTurnos).not.toHaveBeenCalled();
	 });

  it('carga la plantilla independiente de Kickboxing al cambiar el selector', async () => {
    api.plantillas.and.returnValue(of({
      activa: {
        version: 2,
        contenido: {cabecera: 'Escuela', contacto: 'Umbrete', titulo: 'Kickboxing', consentimiento: 'Consentimiento', normas: ['Norma Kickboxing'], importes: 'Pago'},
        instrucciones: 'Instrucciones',
      },
      historial: [{id: 2, version: 2, activa: true}],
    }));

    await component.cambiarDeporte('KICKBOXING');

    expect(api.plantillas).toHaveBeenCalledOnceWith('KICKBOXING');
    expect(component.deportePlantilla).toBe('KICKBOXING');
    expect(component.plantilla.version).toBe(2);
    expect(component.plantilla.normas).toEqual(['Norma Kickboxing']);
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
