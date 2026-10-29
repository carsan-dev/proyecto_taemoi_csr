import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { MisGruposPanelComponent } from './mis-grupos.component';

describe('MisGruposPanelComponent', () => {
  let fixture: ComponentFixture<MisGruposPanelComponent>;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/profesor/grupos`;
  const elemento = (): HTMLElement => fixture.nativeElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MisGruposPanelComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MisGruposPanelComponent);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); fixture.destroy(); });

  it('muestra el esqueleto durante la carga', () => {
    expect(elemento().querySelector('pn-esqueleto')).not.toBeNull();
    expect(fixture.componentInstance.cargando).toBeTrue();
    http.expectOne(url).flush([]);
  });

  it('muestra el vacío de hoy sin mascota ni navegación propia', () => {
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(elemento().textContent).toContain('Aún no tienes grupos asignados.');
    expect(elemento().textContent).toContain('Pídeselo a tu escuela.');
    expect(elemento().querySelector('pn-mascota, app-navegacion-profesor')).toBeNull();
    expect(elemento().textContent).not.toContain('Cerrar sesión');
  });

  it('presenta el error y vuelve a pedir los grupos al reintentar', () => {
    http.expectOne(url).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(elemento().textContent).toContain('No se han podido cargar tus grupos.');
    elemento().querySelector<HTMLButtonElement>('pn-bloque-error button')!.click();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(elemento().querySelector('pn-bloque-error')).toBeNull();
  });

  it('enlaza al grupo y presenta disciplina, sede, papel y alumnos y horario', () => {
    http.expectOne(url).flush([
      { id: 7, nombre: 'Infantil', sede: { id: 1, nombre: 'Umbrete' }, rol: 'TITULAR', alumnosActivos: 18,
        disciplina: { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
        turnos: [{ id: 1, diaSemana: 'Lunes', horaInicio: '18:00', horaFin: '19:00' }, { id: 2, diaSemana: 'Miércoles', horaInicio: '18:00', horaFin: '19:00' }] },
      { id: 8, nombre: 'Adultos', sede: null, rol: 'AYUDANTE', alumnosActivos: 1, disciplina: null, turnos: [] },
    ]);
    fixture.detectChanges();
    expect(elemento().querySelector('a')?.getAttribute('href')).toBe('/mis-grupos/7');
    expect(elemento().textContent).toContain('Umbrete');
    expect(elemento().textContent).toContain('Titular');
    expect(elemento().textContent).toContain('Ayudante');
    expect(elemento().textContent).toContain('Taekwondo');
    expect(elemento().querySelector('.grupos__disc--1')).not.toBeNull();
    expect(elemento().querySelector('dd')?.textContent).toContain('18');
    const horarios = Array.from(elemento().querySelectorAll('dl > div')).filter((n) => n.querySelector('dt')?.textContent === 'Horario');
    expect(horarios.length).toBe(2);
    expect(horarios[0].querySelector('dd')?.textContent).toContain('L y X · 18:00');
    expect(horarios[1].querySelector('dd')?.textContent).toContain('Sin turnos');
    expect(elemento().querySelectorAll('.grupos__disc').length).toBe(1);
    expect(elemento().querySelector('.mis-grupos__disciplina')).toBeNull();
    expect(elemento().querySelector('.grupos__disc')?.getAttribute('title')).toBe('Taekwondo');
    expect(elemento().querySelector('.grupos__disc .mis-grupos__oculto')?.textContent).toBe('Taekwondo');
  });

  it('un grupo sin el campo `turnos` se lee como «Sin turnos» y no rompe la lista', () => {
    http.expectOne(url).flush([{ id: 9, nombre: 'Sin horario', sede: null, rol: 'TITULAR', alumnosActivos: 2, disciplina: null }]);
    fixture.detectChanges();
    expect(elemento().querySelector('h2')?.textContent).toContain('Sin horario');
    expect(elemento().querySelector('dl > div:nth-child(2) dd')?.textContent).toContain('Sin turnos');
  });
});
