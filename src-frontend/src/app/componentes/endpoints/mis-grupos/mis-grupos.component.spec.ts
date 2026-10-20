import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MisGruposComponent } from './mis-grupos.component';
import { GrupoProfesor } from '../../../interfaces/profesor';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ProfesorService } from '../../../servicios/endpoints/profesor.service';

const GRUPOS: GrupoProfesor[] = [
  { id: 4, nombre: 'Infantil lunes y miércoles', sede: { id: 1, nombre: 'Sede Centro' }, rol: 'TITULAR', alumnosActivos: 18, disciplina: null },
  { id: 9, nombre: 'Adultos martes', sede: null, rol: 'AYUDANTE', alumnosActivos: 1, disciplina: null },
];

describe('MisGruposComponent', () => {
  let fixture: ComponentFixture<MisGruposComponent>;
  let profesor: jasmine.SpyObj<ProfesorService>;
  let roles: string[];

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  function crear(respuesta = of(GRUPOS)): void {
    profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', ['misGrupos']);
    profesor.misGrupos.and.returnValue(respuesta);
    TestBed.configureTestingModule({
      imports: [MisGruposComponent],
      providers: [
        provideRouter([]),
        { provide: ProfesorService, useValue: profesor },
        { provide: AuthenticationService, useValue: { getRolesActuales: () => roles, forzarCierreSesion: () => Promise.resolve() } },
      ],
    });
    fixture = TestBed.createComponent(MisGruposComponent);
    fixture.detectChanges();
  }

  beforeEach(() => (roles = ['ROLE_PROFESOR']));

  it('lista sus grupos con sede, rol y número de alumnos, enlazando a cada grupo', () => {
    crear();
    const filas = Array.from(el().querySelectorAll<HTMLAnchorElement>('a.grupo'));
    expect(filas.length).toBe(2);
    expect(filas[0].getAttribute('href')).toBe('/mis-grupos/4');
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Sede Centro · Titular');
    expect(filas[0].textContent).toContain('18 alumnos');
    expect(filas[1].textContent?.replace(/\s+/g, ' ')).toContain('Ayudante');
    expect(filas[1].textContent).toContain('1 alumno');
  });

  it('sin grupos muestra el vacío del boceto', () => {
    crear(of([]));
    const parrafos = Array.from(el().querySelectorAll('.estado-vacio p')).map((p) => p.textContent?.trim());
    expect(parrafos).toEqual(['Aún no tienes grupos asignados.', 'Pídeselo a tu escuela.']);
    expect(el().querySelector('a.grupo')).toBeNull();
  });

  it('un error muestra el mensaje del servidor y deja reintentar', () => {
    crear(throwError(() => new HttpErrorResponse({ status: 500, error: { mensaje: 'Algo ha fallado' } })));
    expect(texto('.estado-error p')).toBe('Algo ha fallado');
    profesor.misGrupos.and.returnValue(of(GRUPOS));
    (el().querySelector('.estado-error button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el().querySelectorAll('a.grupo').length).toBe(2);
  });

  it('pestañas superiores «Mis grupos | Mi caja» con la actual marcada', () => {
    crear();
    const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('nav.nav-profesor a'));
    expect(enlaces.map((a) => a.textContent?.trim())).toEqual(['Mis grupos', 'Mi caja']);
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual(['/mis-grupos', '/mi-caja']);
    expect(enlaces[0].getAttribute('aria-current')).toBe('page');
    expect(enlaces[1].getAttribute('aria-current')).toBeNull();
  });

  it('solo la sesión solo-profesor ve «Cerrar sesión» (no tiene cabecera global)', () => {
    crear();
    expect(el().querySelector('.btn-salir')).not.toBeNull();
    TestBed.resetTestingModule();
    roles = ['ROLE_USER', 'ROLE_PROFESOR'];
    crear();
    expect(el().querySelector('.btn-salir')).toBeNull();
  });
});
