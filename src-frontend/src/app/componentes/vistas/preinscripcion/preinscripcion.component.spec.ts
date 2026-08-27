import { of, Subject, throwError } from 'rxjs';
import { PreinscripcionComponent } from './preinscripcion.component';

describe('PreinscripcionComponent normas', () => {
  function crear(
    configuracion: unknown,
    grupos: any[] = [],
    parametros: Record<string,string> = {},
    scrollService: { scrollToTopAfterNextRender: (behavior?: ScrollBehavior) => Promise<boolean> } = {
      scrollToTopAfterNextRender: () => Promise.resolve(true),
    },
  ) {
    const api = {
      temporada: () => of({ temporada: '2026-2027' }),
      grupos: () => of(grupos),
      configuracion: () => configuracion instanceof Error ? throwError(() => configuracion) : of(configuracion),
      crear: () => of({ referencia: 'PRE-123', plazaAsignada: true }),
    };
    const route = { snapshot: { queryParamMap: { get: (clave:string) => parametros[clave] ?? null } } };
    return new PreinscripcionComponent(api as any, route as any, scrollService as any);
  }

  it('muestra normas recibidas como objeto y mantiene bloqueada su aceptación', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'], importes: 'Pago presencial' } });
    component.form.controls.deporte.setValue('PILATES');
    component.cambiarDeporte();
    expect(component.configuracion?.contenido.normas).toEqual(['Norma uno']);
    expect(component.form.controls.aceptacionNormas.disabled).toBeTrue();
  });

  it('habilita aceptación al alcanzar el final de las normas', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'] } });
    component.form.controls.deporte.setValue('PILATES');
    component.cambiarDeporte();
    component.comprobarLecturaNormas({ scrollTop: 700, clientHeight: 300, scrollHeight: 1000 } as HTMLElement);
    expect(component.form.controls.aceptacionNormas.enabled).toBeTrue();
  });

  it('normaliza contenido JSON serializado', () => {
    const component = crear({ deporte: 'TAEKWONDO', version: 1, contenido: '{"normas":["Norma uno"]}' });
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();
    expect(component.normasDisponibles).toBeTrue();
  });

  it('mantiene aceptación y envío bloqueados cuando falla la carga', () => {
    const component = crear(new Error('fallo'));
    component.form.controls.deporte.setValue('KICKBOXING');
    component.cambiarDeporte();
    expect(component.errorNormas).toContain('No se pudieron cargar');
    expect(component.form.controls.aceptacionNormas.disabled).toBeTrue();
    expect(component.normasDisponibles).toBeFalse();
  });

  it('mantiene las normas bloqueadas hasta seleccionar actividad', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'] } });
    expect(component.form.controls.deporte.value).toBe('');
    expect(component.form.controls.aceptacionNormas.disabled).toBeTrue();
    expect(component.normasDisponibles).toBeFalse();
  });

  it('permite DNI vacío al menor y exige responsable legal', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'] } });
    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('2015-01-01');
    component.form.controls.dni.setValue('');

    expect(component.form.controls.dni.valid).toBeTrue();
    expect(component.form.controls.tutorNombre.invalid).toBeTrue();
    expect(component.form.controls.tutorDni.invalid).toBeTrue();

    component.form.controls.tutorNombre.setValue('María García');
    component.form.controls.tutorDni.setValue('12345678Z');
    expect(component.form.controls.tutorNombre.valid).toBeTrue();
    expect(component.form.controls.tutorDni.valid).toBeTrue();
  });

  it('exige DNI y respuesta de discapacidad a una persona adulta', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'] } });
    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('1990-01-01');
    component.form.controls.dni.setValue('');

    expect(component.form.controls.dni.invalid).toBeTrue();
    expect(component.form.controls.tieneDiscapacidad.invalid).toBeTrue();

    component.form.controls.dni.setValue('12345678Z');
    component.form.controls.tieneDiscapacidad.setValue(false);
    expect(component.form.controls.dni.valid).toBeTrue();
    expect(component.form.controls.tieneDiscapacidad.valid).toBeTrue();
  });

  it('muestra solo grupos compatibles con la edad', () => {
    const component = crear({ deporte: 'TAEKWONDO', version: 1, contenido: { normas: ['Norma'] } }, [
      {id: 1, rangoEdadMin: 5, rangoEdadMax: 12, turnos: [{id: 11, diaSemana: 'Lunes'}]},
      {id: 2, rangoEdadMin: 13, rangoEdadMax: 17, turnos: [{id: 12, diaSemana: 'Martes'}]},
    ]);
    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('2016-01-01');
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();

    expect(component.gruposCompatibles.map(g => g.id)).toEqual([1]);
  });

  it('usa año de nacimiento para mantener toda la cohorte en el mismo grupo', () => {
    const component = crear({ deporte: 'TAEKWONDO', version: 1, contenido: { normas: ['Norma'] } }, [
      {id: 1, rangoEdadMin: 7, rangoEdadMax: 7, turnos: [{id: 11, diaSemana: 'Lunes', horaInicio: '17:00'}]},
      {id: 2, rangoEdadMin: 8, rangoEdadMax: 8, turnos: [{id: 12, diaSemana: 'Lunes', horaInicio: '18:00'}]},
    ]);
    component.ngOnInit();
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();

    component.form.controls.fechaNacimiento.setValue('2018-01-02');
    expect(component.gruposCompatibles.map(g => g.id)).toEqual([2]);

    component.form.controls.fechaNacimiento.setValue('2018-12-30');
    expect(component.gruposCompatibles.map(g => g.id)).toEqual([2]);
  });

  it('permite turnos de grupos distintos y reemplaza selección del mismo día', () => {
    const grupos = [
      {id: 1, rangoEdadMin: 0, rangoEdadMax: 99, turnos: [{id: 11, diaSemana: 'Lunes'}]},
      {id: 2, rangoEdadMin: 0, rangoEdadMax: 99, turnos: [{id: 12, diaSemana: 'Jueves'}, {id: 13, diaSemana: 'Lunes'}]},
    ];
    const component = crear({ deporte: 'TAEKWONDO', version: 1, contenido: { normas: ['Norma'] } }, grupos);
    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('1990-01-01');
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();
    component.alternarTurno(grupos[0].turnos[0], true);
    component.alternarTurno(grupos[1].turnos[0], true);
    expect(component.form.controls.turnoIds.value).toEqual([11, 12]);

    component.alternarTurno(grupos[1].turnos[1], true);
    expect(component.form.controls.turnoIds.value).toEqual([12, 13]);
  });

  it('preselecciona solo el turno enlazado tras conocer edad y grupos compatibles', () => {
    const grupos = [{id:1,rangoEdadMin:5,rangoEdadMax:12,turnos:[
      {id:11,diaSemana:'Lunes'},{id:12,diaSemana:'Miércoles'}
    ]}];
    const component = crear({deporte:'TAEKWONDO',version:1,contenido:{normas:['Norma']}},grupos,
      {deporte:'TAEKWONDO',turnoId:'12'});

    component.ngOnInit();
    expect(component.form.controls.turnoIds.value).toEqual([]);
    component.form.controls.fechaNacimiento.setValue('2016-01-01');

    expect(component.form.controls.turnoIds.value).toEqual([12]);
  });

  it('rechaza el turno enlazado incompatible con edad', () => {
    const grupos = [{id:1,rangoEdadMin:5,rangoEdadMax:12,turnos:[{id:11,diaSemana:'Lunes'}]}];
    const component = crear({deporte:'TAEKWONDO',version:1,contenido:{normas:['Norma']}},grupos,
      {deporte:'TAEKWONDO',turnoId:'11'});

    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('1990-01-01');

    expect(component.form.controls.turnoIds.value).toEqual([]);
    expect(component.errorHorarios).toContain('no es compatible');
  });

  it('mantiene enlaces antiguos de grupo sin seleccionar dos turnos del mismo día', () => {
    const grupos = [{id:7,rangoEdadMin:0,rangoEdadMax:99,turnos:[
      {id:11,diaSemana:'Lunes'},{id:12,diaSemana:'lunes'},{id:13,diaSemana:'Jueves'}
    ]}];
    const component = crear({deporte:'TAEKWONDO',version:1,contenido:{normas:['Norma']}},grupos,
      {deporte:'TAEKWONDO',grupoId:'7'});

    component.ngOnInit();
    component.form.controls.fechaNacimiento.setValue('1990-01-01');

    expect(component.form.controls.turnoIds.value).toEqual([11,13]);
  });

  it('limita las observaciones del horario a mil caracteres', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma'] } });

    component.form.controls.observaciones.setValue('x'.repeat(1001));

    expect(component.form.controls.observaciones.hasError('maxlength')).toBeTrue();
  });

  it('sube instantáneamente después de renderizar el resultado', () => {
    const scrollService = jasmine.createSpyObj('PageScrollService', ['scrollToTopAfterNextRender']);
    scrollService.scrollToTopAfterNextRender.and.returnValue(Promise.resolve(true));
    const component = crear(
      { deporte: 'PILATES', version: 1, contenido: { normas: ['Norma'] } },
      [],
      {},
      scrollService,
    );
    component.configuracion = {
      deporte: 'PILATES',
      version: 1,
      contenido: { normas: ['Norma'] },
    } as any;
    component.form.controls.aceptacionNormas.enable();
    component.form.patchValue({
      deporte: 'PILATES',
      turnoIds: [1],
      nombre: 'Ana',
      apellidos: 'Pérez',
      dni: '12345678Z',
      fechaNacimiento: '1990-01-01',
      direccion: 'Calle Principal 1',
      telefono: '612345678',
      email: 'ana@example.com',
      tieneDiscapacidad: false,
      aceptacionNormas: true,
      firmanteNombre: 'Ana Pérez',
    });
    component.firmada = true;
    component.canvas = {
      nativeElement: { toDataURL: () => 'data:image/png;base64,firma' },
    } as any;

    component.enviar();

    expect(component.resultado.referencia).toBe('PRE-123');
    expect(scrollService.scrollToTopAfterNextRender).toHaveBeenCalledOnceWith('auto');
  });

  it('ignora un segundo envio mientras el primero sigue pendiente', () => {
    const respuesta = new Subject<any>();
    const api = {
      temporada: () => of({ temporada: '2026-2027' }),
      crear: jasmine.createSpy('crear').and.returnValue(respuesta),
    };
    const route = { snapshot: { queryParamMap: { get: () => null } } };
    const scrollService = { scrollToTopAfterNextRender: () => Promise.resolve(true) };
    const component = new PreinscripcionComponent(api as any, route as any, scrollService as any);
    component.configuracion = { deporte: 'PILATES', version: 1, contenido: { normas: ['Norma'] } } as any;
    component.form.controls.aceptacionNormas.enable();
    component.form.patchValue({
      deporte: 'PILATES', turnoIds: [1], nombre: 'Ana', apellidos: 'Perez', dni: '12345678Z',
      fechaNacimiento: '1990-01-01', direccion: 'Calle Principal 1', telefono: '612345678',
      email: 'ana@example.com', tieneDiscapacidad: false, aceptacionNormas: true, firmanteNombre: 'Ana Perez',
    });
    component.firmada = true;
    component.canvas = { nativeElement: { toDataURL: () => 'data:image/png;base64,firma' } } as any;

    component.enviar();
    component.enviar();

    expect(api.crear).toHaveBeenCalledTimes(1);
    expect(api.crear.calls.mostRecent().args[1]).toMatch(/^[A-Za-z0-9-]+$/);
  });
});
