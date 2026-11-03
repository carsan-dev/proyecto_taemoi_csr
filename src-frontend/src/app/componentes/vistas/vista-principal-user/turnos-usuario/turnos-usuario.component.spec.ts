import { of } from 'rxjs';

import { TurnosUsuarioComponent } from './turnos-usuario.component';

describe('TurnosUsuarioComponent', () => {
  const turno = (diaSemana: string, id: number): any => ({
    id,
    diaSemana,
    horaInicio: '18:00',
    horaFin: '19:00',
    tipoGrupo: 'Taekwondo',
  });

  const crear = (turnosEscuela: any[]) => {
    const endpoints = { obtenerTurnosDTO: () => of(turnosEscuela) };
    return new TurnosUsuarioComponent(endpoints as any, {} as any, {} as any, {} as any);
  };

  const cargar = (component: TurnosUsuarioComponent, turnosAlumno: any[]) => {
    component.allTurnos = turnosAlumno;
    component.buildTimetable();
  };

  it('should create', () => {
    expect(crear([])).toBeTruthy();
  });

  describe('días de la semana (decisión 380)', () => {
    it('sin turnos de fin de semana en la escuela, cinco días', () => {
      const component = crear([turno('Lunes', 1)]);
      (component as any).cargarTurnosEscuela();
      cargar(component, [turno('Lunes', 1)]);
      expect(component.diasSemana).toEqual(['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes']);
      expect(component.diasCortos.length).toBe(5);
    });

    it('si la escuela tiene sábado, sale Sabado aunque el alumno no tenga turno ese día', () => {
      const component = crear([turno('Lunes', 1), turno('Sábado', 2)]);
      (component as any).cargarTurnosEscuela();
      cargar(component, [turno('Lunes', 1)]);
      expect(component.diasSemana).toEqual(['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado']);
      expect(component.diasCortos[5]).toBe('Sab');
      expect(component.getTurnosForDay('Sabado').length).toBe(0);
    });

    it('solo con domingo, seis días con Domingo detrás del viernes', () => {
      const component = crear([turno('Domingo', 3)]);
      (component as any).cargarTurnosEscuela();
      cargar(component, []);
      expect(component.diasSemana).toEqual(['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Domingo']);
    });

    it('con sábado y domingo, siete días, y el turno del alumno del sábado se pinta', () => {
      const component = crear([turno('Sábado', 2), turno('Domingo', 3)]);
      (component as any).cargarTurnosEscuela();
      cargar(component, [turno('Sábado', 2)]);
      expect(component.diasSemana.slice(5)).toEqual(['Sabado', 'Domingo']);
      expect(component.getTurnosForDay('Sabado').length).toBe(1);
    });

    it('si no llegan los turnos de la escuela, valen los del alumno', () => {
      const component = crear([]);
      cargar(component, [turno('Sábado', 2)]);
      expect(component.diasSemana.length).toBe(6);
    });
  });
});
