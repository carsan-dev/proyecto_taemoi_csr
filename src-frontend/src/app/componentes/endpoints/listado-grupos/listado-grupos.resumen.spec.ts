import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ListadoGruposComponent } from './listado-grupos.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { GrupoResumen } from '../../../interfaces/grupo-resumen';
import { TurnoResumen } from '../../../interfaces/turno-resumen';

/**
 * R2-P4: la pantalla «Grupos y Turnos» se pinta con las listas ligeras (`/grupos/resumen` y `/turnos/resumen`):
 * dos peticiones en total, sin los alumnos ni una petición de turnos por grupo, con lo mismo en pantalla.
 */
describe('ListadoGruposComponent (resumen)', () => {
  let fixture: ComponentFixture<ListadoGruposComponent>;
  let component: ListadoGruposComponent;
  let endpoints: jasmine.SpyObj<EndpointsService>;

  // Datos nuevos en cada prueba: el componente mueve turnos entre listas.
  const datosGrupos = (): GrupoResumen[] => [
    {
      id: 1, nombre: 'Taekwondo Infantil', deporte: 'TAEKWONDO', rangoEdadMin: 6, rangoEdadMax: 12, numAlumnos: 3,
      turnos: [
        { id: 10, diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:00' },
        { id: 11, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00' },
      ],
    },
    { id: 2, nombre: 'Kickboxing', deporte: 'KICKBOXING', rangoEdadMin: null, rangoEdadMax: null, numAlumnos: 0, turnos: [] },
  ];
  const datosTurnos = (): TurnoResumen[] => [
    { id: 10, diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:00', grupoId: 1, grupoNombre: 'Taekwondo Infantil', tipoGrupo: 'Infantil' },
    { id: 11, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00', grupoId: 1, grupoNombre: 'Taekwondo Infantil', tipoGrupo: 'Infantil' },
    { id: 12, diaSemana: 'Sábado', horaInicio: '10:00', horaFin: '11:00', grupoId: null, grupoNombre: 'Sin grupo', tipoGrupo: null },
  ];

  async function crear(): Promise<void> {
    endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerResumenGrupos',
      'obtenerResumenTurnos',
      'obtenerConteoAlumnosPorGrupo',
      'agregarTurnoAGrupo',
      'obtenerTurnosDelGrupoPorId',
      'obtenerTodosLosGrupos',
      'obtenerTodosLosTurnos',
    ]);
    (endpoints as any).conteoAlumnosPorGrupo$ = new BehaviorSubject<any>({ Taekwondo: { active: 5, total: 6 } });
    (endpoints as any).conteoAlumnosPorGrupo = { Taekwondo: { active: 5, total: 6 } };
    endpoints.obtenerResumenGrupos.and.returnValue(of(datosGrupos()));
    endpoints.obtenerResumenTurnos.and.returnValue(of(datosTurnos()));
    endpoints.agregarTurnoAGrupo.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [ListadoGruposComponent],
      providers: [provideRouter([]), { provide: EndpointsService, useValue: endpoints }],
    }).compileComponents();

    fixture = TestBed.createComponent(ListadoGruposComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => fixture?.destroy());

  it('carga los grupos y los turnos con dos peticiones ligeras, sin pedir los completos ni los turnos grupo a grupo', async () => {
    await crear();

    expect(endpoints.obtenerResumenGrupos).toHaveBeenCalledTimes(1);
    expect(endpoints.obtenerResumenTurnos).toHaveBeenCalledTimes(1);
    expect(endpoints.obtenerTurnosDelGrupoPorId).not.toHaveBeenCalled();
    expect(endpoints.obtenerTodosLosGrupos).not.toHaveBeenCalled();
    expect(endpoints.obtenerTodosLosTurnos).not.toHaveBeenCalled();
    expect(component.cargando).toBeFalse();
  });

  it('pinta cada grupo con su rango de edad y sus turnos', async () => {
    await crear();

    const tarjetas: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.admin-item-card'));
    expect(tarjetas.length).toBe(2);
    const primera = tarjetas[0].textContent!.replace(/\s+/g, ' ');
    expect(primera).toContain('Taekwondo Infantil');
    expect(primera).toContain('Rango edad: 6 - 12 años');
    expect(primera).toContain('Turnos (2)');
    expect(primera).toContain('Lunes 17:00 - 18:00');
    expect(primera).toContain('Miércoles 17:00 - 18:00');
    const segunda = tarjetas[1].textContent!.replace(/\s+/g, ' ');
    expect(segunda).toContain('Rango edad: Sin rango');
    expect(segunda).toContain('Sin turnos asignados');
  });

  it('con los datos reales ("Sin grupo" en el turno sin grupo) no ofrece turnos sin grupo, como hasta ahora', async () => {
    await crear();

    expect(component.turnosSinGrupo).toEqual([]);
  });

  it('al asignar un turno sin grupo lo mueve a la lista del grupo', async () => {
    await crear();
    component.turnosSinGrupo = datosTurnos().filter(t => t.id === 12);

    component.asignarTurnoAGrupo(2, '12');

    expect(endpoints.agregarTurnoAGrupo).toHaveBeenCalledOnceWith(2, 12);
    expect(component.turnosSinGrupo).toEqual([]);
    expect(component.getTurnosDelGrupo(2).map(t => t.id)).toEqual([12]);
  });

  it('si los grupos no llegan deja de cargar y no pide los turnos', async () => {
    await crear();
    endpoints.obtenerResumenGrupos.and.returnValue(throwError(() => new Error('sin conexión')));
    endpoints.obtenerResumenTurnos.calls.reset();

    component.obtenerGrupos();

    expect(component.cargando).toBeFalse();
    expect(endpoints.obtenerResumenTurnos).not.toHaveBeenCalled();
  });
});
