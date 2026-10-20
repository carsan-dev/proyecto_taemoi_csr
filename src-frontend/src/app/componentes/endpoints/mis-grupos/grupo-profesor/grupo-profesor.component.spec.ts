import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { GrupoProfesorComponent, esApto } from './grupo-profesor.component';
import { AlumnoDeGrupo, GrupoProfesor } from '../../../../interfaces/profesor';
import { ProfesorService } from '../../../../servicios/endpoints/profesor.service';
import { mesesAsistencia } from '../profesor-textos';

const GRUPO: GrupoProfesor = {
  id: 4, nombre: 'Infantil lunes y miércoles', sede: { id: 1, nombre: 'Sede Centro' }, rol: 'TITULAR', alumnosActivos: 3, disciplina: null,
};

function alumno(id: number, nombre: string, apto: boolean, grado = 'Amarillo'): AlumnoDeGrupo {
  return {
    id, nombre, apellidos: 'Prueba',
    deportes: [{ deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'G', nombre: grado }, fechaGrado: '2026-03-12', apto, delGrupo: true }],
  };
}

const ALUMNOS = [alumno(1, 'Ana', true), alumno(2, 'Luis', false, 'Blanco'), alumno(3, 'Eva', false)];

describe('GrupoProfesorComponent', () => {
  let fixture: ComponentFixture<GrupoProfesorComponent>;
  let profesor: jasmine.SpyObj<ProfesorService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const filtro = (i: number) => el().querySelectorAll<HTMLButtonElement>('.filtro button')[i];

  function crear(alumnos = of(ALUMNOS)): void {
    profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', ['misGrupos', 'alumnosDelGrupo', 'hojaAsistencia', 'salud']);
    profesor.misGrupos.and.returnValue(of([GRUPO]));
    profesor.alumnosDelGrupo.and.returnValue(alumnos);
    TestBed.configureTestingModule({
      imports: [GrupoProfesorComponent],
      providers: [
        provideRouter([]),
        { provide: ProfesorService, useValue: profesor },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ grupoId: '4' }) } } },
      ],
    });
    fixture = TestBed.createComponent(GrupoProfesorComponent);
    fixture.detectChanges();
  }

  it('pinta el grupo y sus alumnos con deporte, grado y chip «apto», sin pedir la salud', () => {
    crear();
    expect(profesor.alumnosDelGrupo).toHaveBeenCalledWith(4);
    expect(texto('h1')).toBe('Infantil lunes y miércoles');
    expect(texto('.subtitulo')).toBe('Sede Centro · Titular');
    const filas = el().querySelectorAll<HTMLAnchorElement>('a.alumno');
    expect(filas.length).toBe(3);
    expect(filas[0].getAttribute('href')).toBe('/mis-grupos/alumnos/1?grupo=4');
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Taekwondo · Amarillo');
    expect(filas[0].querySelector('.chip-apto')).not.toBeNull();
    expect(filas[1].querySelector('.chip-apto')).toBeNull();
    expect(profesor.salud).not.toHaveBeenCalled();
  });

  it('filtro segmentado «Todos N | Aptos M»', () => {
    crear();
    expect(filtro(0).textContent?.replace(/\s+/g, ' ').trim()).toBe('Todos 3');
    expect(filtro(1).textContent?.replace(/\s+/g, ' ').trim()).toBe('Aptos 1');
    expect(filtro(0).getAttribute('aria-pressed')).toBe('true');

    filtro(1).click();
    fixture.detectChanges();
    expect(filtro(1).getAttribute('aria-pressed')).toBe('true');
    expect(Array.from(el().querySelectorAll('.alumno-nombre')).map((n) => n.textContent?.trim())).toEqual(['Ana Prueba']);

    filtro(0).click();
    fixture.detectChanges();
    expect(el().querySelectorAll('a.alumno').length).toBe(3);
  });

  it('esApto: apto en alguno de sus deportes', () => {
    const mixto = alumno(5, 'Mar', false);
    mixto.deportes.push({ ...mixto.deportes[0], deporte: 'KICKBOXING', apto: true });
    expect(esApto(mixto)).toBeTrue();
    expect(esApto(alumno(6, 'Leo', false))).toBeFalse();
  });

  it('sin alumnos activos muestra el vacío y no el filtro', () => {
    crear(of([]));
    expect(texto('.estado')).toBe('Este grupo no tiene alumnos activos.');
    expect(el().querySelector('.filtro')).toBeNull();
  });

  it('un 403 muestra el mensaje del servidor', () => {
    crear(throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'No tienes acceso a este grupo' } })));
    expect(texto('.estado-error p')).toBe('No tienes acceso a este grupo');
  });

  it('la hoja de asistencia sale por defecto del mes actual y se descarga con el nombre del servidor', fakeAsync(() => {
    crear();
    const actual = mesesAsistencia()[0];
    expect((el().querySelector('#mesAsistencia') as HTMLSelectElement | null)).not.toBeNull();
    expect(fixture.componentInstance.mes).toBe(actual.valor);

    const blob = new Blob(['%PDF'], { type: 'application/pdf' });
    profesor.hojaAsistencia.and.returnValue(of({ blob, nombre: 'Asistencia-grupo-4.pdf' }));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
    spyOn(URL, 'revokeObjectURL');
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');

    (el().querySelector('.btn-asistencia') as HTMLButtonElement).click();
    flushMicrotasks();
    expect(profesor.hojaAsistencia).toHaveBeenCalledWith(4, actual.year, actual.month);
    expect(clic).toHaveBeenCalled();
    expect((clic.calls.mostRecent().object as HTMLAnchorElement).download).toBe('Asistencia-grupo-4.pdf');
    tick(1000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prueba');
  }));
});
