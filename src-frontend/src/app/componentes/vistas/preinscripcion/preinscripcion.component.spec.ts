import { of, throwError } from 'rxjs';
import { PreinscripcionComponent } from './preinscripcion.component';

describe('PreinscripcionComponent normas', () => {
  function crear(configuracion: unknown, grupos: any[] = []) {
    const api = {
      temporada: () => of({ temporada: '2026/2027' }),
      grupos: () => of(grupos),
      configuracion: () => configuracion instanceof Error ? throwError(() => configuracion) : of(configuracion)
    };
    const route = { snapshot: { queryParamMap: { get: () => null } } };
    return new PreinscripcionComponent(api as any, route as any);
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
    component.form.controls.fechaNacimiento.setValue(`${new Date().getFullYear()-10}-01-01`);
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();

    expect(component.gruposCompatibles.map(g => g.id)).toEqual([1]);
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
});
