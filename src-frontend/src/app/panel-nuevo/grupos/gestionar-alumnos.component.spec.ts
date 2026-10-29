import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { MASCOTA_CARGADORES } from '../mascota/mascota.component';
import { environment } from '../../../environments/environment';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { GestionarAlumnosComponent } from './gestionar-alumnos.component';
import type { AlumnoGrupo } from './seleccionar-alumnos-panel.component';

describe('GestionarAlumnosComponent (3m-4)', () => {
  let fixture: ComponentFixture<GestionarAlumnosComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.SpyObj<ConfirmacionService>;
  const data = new BehaviorSubject<Record<string, boolean>>({});
  const params = new BehaviorSubject({ id: '7' });
  const alumno = (id: number, activo = true) => ({ id, nombre: `Alumno ${id}`, apellidos: 'Prueba', activo });
  beforeEach(async () => {
    data.next({}); params.next({ id: '7' });
    confirmar = jasmine.createSpyObj('Confirmacion', ['confirmar']);
    confirmar.confirmar.and.resolveTo({ confirmado: false, valor: null });
    await TestBed.configureTestingModule({
      imports: [GestionarAlumnosComponent],
      providers: [
        { provide: MASCOTA_CARGADORES, useValue: {
          esperar: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
          buscar: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
        } },
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { params, data } },
        { provide: ConfirmacionService, useValue: confirmar },
        { provide: AvisosService, useValue: jasmine.createSpyObj('Avisos', ['exito', 'error']) }],
    }).compileComponents();
    fixture = TestBed.createComponent(GestionarAlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function grupo(alumnos: AlumnoGrupo[] = [alumno(1)], resolverTurnos = true): void {
    const req = http.expectOne(`${environment.apiUrl}/grupos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7, nombre: 'Infantiles', alumnos });
    if (resolverTurnos) vaciarTurnos();
    fixture.detectChanges();
  }
  function vaciarTurnos(): void {
    http.match((r) => /\/grupos\/7\/alumnos\/\d+\/turnos$/.test(r.url)).forEach(req => req.flush([]));
  }
  it('shows esperar above the empty group heading', () => {
    grupo([]);
    const mascot = fixture.nativeElement.querySelector('.alumnos-grupo__vacio pn-mascota') as HTMLElement;
    expect(mascot.getAttribute('pose')).toBe('esperar');
    expect(mascot.getAttribute('aria-hidden')).toBe('true');
    expect(mascot.nextElementSibling?.tagName).toBe('H2');
  });

  it('shows buscar when no pupil matches the search', fakeAsync(() => {
    grupo();
    fixture.componentInstance.buscar('Nobody');
    tick(300);
    fixture.detectChanges();
    const mascot = fixture.nativeElement.querySelector('.alumnos-grupo__vacio pn-mascota') as HTMLElement;
    expect(mascot.getAttribute('pose')).toBe('buscar');
    expect(mascot.nextElementSibling?.tagName).toBe('H2');
  }));

  it('shows only the pupil schedules in this group, grouped by start time and ordered by weekday', () => {
    grupo([alumno(1), alumno(2)], false);
    http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/1/turnos`).flush([
      { grupoId: 7, diaSemana: 'Miércoles', horaInicio: '17:30:00' },
      { grupoId: 7, diaSemana: 'Lunes', horaInicio: '17:30:00' },
      { grupoId: 7, diaSemana: 'Viernes', horaInicio: '18:00' },
      { grupoId: 8, diaSemana: 'Martes', horaInicio: '20:00' },
    ]);
    http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/2/turnos`).flush('sin turnos', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.alumnos-grupo__turnos')?.textContent).toBe('L y X 17:30 · V 18:00');
    expect(fixture.nativeElement.querySelectorAll('.alumnos-grupo__turnos').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.alumnos-grupo__fila').length).toBe(2);
    expect(fixture.componentInstance.errorCarga).toBe('');
  });
  it('loads only visible schedules, caches revisited pages and resets cancelled requests on refresh', () => {
    const alumnos = Array.from({ length: 11 }, (_, i) => alumno(i + 1));
    grupo(alumnos, false);
    const pendientes = http.match(r => /\/grupos\/7\/alumnos\/\d+\/turnos$/.test(r.url));
    expect(pendientes.length).toBe(10);
    fixture.componentInstance.cargarGrupo();
    expect(pendientes.every(req => req.cancelled)).toBeTrue();
    grupo(alumnos);
    fixture.componentInstance.cambiarPagina(2);
    http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/11/turnos`).flush([]);
    fixture.componentInstance.cambiarPagina(1);
    fixture.componentInstance.cambiarPagina(2);
    http.expectNone(r => r.url.endsWith('/turnos'));
  });
  it('cancels schedule reads on group navigation and reloads the same pupil in the new group', () => {
    grupo([alumno(1)], false);
    const anterior = http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/1/turnos`);
    params.next({ id: '8' });
    expect(anterior.cancelled).toBeTrue();
    http.expectOne(`${environment.apiUrl}/grupos/8`).flush({ id: 8, nombre: 'Adultos', alumnos: [alumno(1)] });
    const nuevo = http.expectOne(`${environment.apiUrl}/grupos/8/alumnos/1/turnos`);
    expect(nuevo.request.withCredentials).toBeTrue();
    nuevo.flush([{ grupoId: 8, diaSemana: 'Martes', horaInicio: '19:00:00' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.alumnos-grupo__turnos')?.textContent).toBe('M 19:00');
  });
  for (const seleccionarAlumnos of [false, true]) {
    it(`rejects zero on the first ${seleccionarAlumnos ? 'selection' : 'management'} route emission without leaving a skeleton`, () => {
      fixture.destroy();
      http.match(() => true);
      params.next({ id: '0' });
      data.next({ seleccionarAlumnos });
      fixture = TestBed.createComponent(GestionarAlumnosComponent);
      fixture.detectChanges();
      expect(fixture.componentInstance.cargando).toBeFalse();
      expect(fixture.componentInstance.errorCarga).toBe('No se pudo cargar el grupo');
      expect(fixture.nativeElement.querySelector('pn-esqueleto')).toBeNull();
      expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('pn-seleccionar-alumnos')).toBeNull();
      http.expectNone(() => true);
    });
  }
  it('shows empty with one primary action, then local search/no-match and active pupils only', fakeAsync(() => {
    grupo([]);
    expect(fixture.nativeElement.textContent).toContain('Aún no hay alumnos en este grupo');
    expect(fixture.nativeElement.querySelectorAll('.alumnos-grupo__primario').length).toBe(1);
    fixture.componentInstance.cargarGrupo();
    grupo([alumno(1), alumno(2, false)]);
    expect(fixture.nativeElement.textContent).toContain('Alumnos de Infantiles');
    expect(fixture.componentInstance.alumnos.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Mostrando 1 de 1');
    fixture.componentInstance.buscar('Eva'); tick(300); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ningún alumno coincide con «Eva»');
    expect(fixture.nativeElement.textContent).toContain('Mostrando 0 de 1');
    fixture.componentInstance.buscar(''); tick(300); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.alumnos-grupo__nombre').getAttribute('href')).toBe('/alumnosEditar/1');
  }));
  it('passes the origin group to the pupil schedules screen', () => {
    grupo([alumno(1)]);
    const enlace: HTMLAnchorElement = fixture.nativeElement.querySelector('a.alumnos-grupo__texto[href^="/gestionarTurnosAlumno"]');
    expect(enlace.getAttribute('href')).toBe(`/gestionarTurnosAlumno/1?grupoId=${fixture.componentInstance.grupoId}`);
  });
  it('renders versioned photos and falls back to initials without mutating pupils', () => {
    grupo([{ ...alumno(1), fotoAlumno: { id: 17, url: '/foto' } }, alumno(2)]);
    const avatares = fixture.nativeElement.querySelectorAll('.alumnos-grupo__avatar') as NodeListOf<HTMLElement>;
    const foto = avatares[0].querySelector('img') as HTMLImageElement;
    expect(foto.getAttribute('src')).toBe('/foto?w=180&v=17');
    expect(foto.getAttribute('alt')).toBe('');
    expect(avatares[1].textContent?.trim()).toBe('AP');
    foto.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(avatares[0].querySelector('img')).toBeNull();
    expect(avatares[0].textContent?.trim()).toBe('AP');
    expect(fixture.componentInstance.alumnos[0].fotoAlumno?.url).toBe('/foto');
  });
  it('paginates ten locally and preserves search/page after refreshing on close', fakeAsync(() => {
    grupo(Array.from({ length: 18 }, (_, i) => alumno(i + 1)));
    fixture.componentInstance.buscar('Alumno'); tick(300);
    fixture.componentInstance.cambiarPagina(2); fixture.detectChanges();
    vaciarTurnos();
    expect(fixture.nativeElement.textContent).toContain('11-18 de 18');
    fixture.componentInstance.cerrarPanel();
    grupo(Array.from({ length: 17 }, (_, i) => alumno(i + 1)));
    expect(fixture.componentInstance.pagina).toBe(2);
    expect(fixture.componentInstance.filtro).toBe('Alumno');
  }));
  it('cancels removal, then confirms a reversible removal and sends the unchanged empty DELETE', async () => {
    grupo();
    await fixture.componentInstance.quitar(fixture.componentInstance.alumnos[0]);
    http.expectNone(`${environment.apiUrl}/grupos/7/alumnos/1`);
    confirmar.confirmar.and.resolveTo({ confirmado: true, valor: null });
    await fixture.componentInstance.quitar(fixture.componentInstance.alumnos[0]);
    const opciones = confirmar.confirmar.calls.mostRecent().args[0];
    expect(opciones.destructivo).toBeFalse();
    expect(opciones.mensaje).toContain('Sigue siendo alumno de la escuela');
    const req = http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/1`);
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toBeNull();
    req.flush({});
    grupo([]);
  });
  it('refreshes after successful addition and restores opener focus', () => {
    grupo();
    const boton = fixture.nativeElement.querySelector('.alumnos-grupo__primario') as HTMLButtonElement;
    fixture.componentInstance.abrirPanel(boton); fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/alumnos')).flush({ content: [], totalPages: 0, totalElements: 0 });
    fixture.componentInstance.alumnosAnadidos();
    expect(document.activeElement).toBe(boton);
    grupo();
    expect(fixture.componentInstance.panelAbierto).toBeFalse();
  });
  it('cleans deep-link routing and does not reopen after a refresh', async () => {
    const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    data.next({ seleccionarAlumnos: true }); grupo();
    expect(fixture.componentInstance.panelAbierto).toBeTrue();
    http.expectOne((r) => r.url.endsWith('/alumnos')).flush({ content: [], totalPages: 0, totalElements: 0 });
    fixture.componentInstance.cerrarPanel();
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith(['/gestionarAlumnos', 7], { replaceUrl: true, state: { enfocarAnadirAlumnos: true } });
    expect(fixture.componentInstance.panelAbierto).toBeFalse();
    fixture.componentInstance.cargarGrupo(); grupo();
    expect(fixture.componentInstance.panelAbierto).toBeFalse();
  });
  it('returns focus to the replacement add action when the empty group becomes populated', () => {
    grupo([]);
    const boton = fixture.nativeElement.querySelector('.alumnos-grupo__primario') as HTMLButtonElement;
    fixture.componentInstance.abrirPanel(boton); fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/alumnos')).flush({ content: [alumno(1)], totalPages: 1, totalElements: 1 });
    fixture.componentInstance.alumnosAnadidos(); grupo();
    const nuevo = fixture.nativeElement.querySelector('.alumnos-grupo__primario') as HTMLButtonElement;
    expect(nuevo).not.toBe(boton);
    expect(document.activeElement).toBe(nuevo);
  });
  it('provides recoverable group and removal errors', async () => {
    http.expectOne(`${environment.apiUrl}/grupos/7`).flush('fallo', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
    fixture.componentInstance.cargarGrupo(); grupo();
    confirmar.confirmar.and.resolveTo({ confirmado: true, valor: null });
    await fixture.componentInstance.quitar(fixture.componentInstance.alumnos[0]);
    http.expectOne(`${environment.apiUrl}/grupos/7/alumnos/1`).flush('fallo', { status: 500, statusText: 'Error' });
    expect(fixture.componentInstance.quitandos.size).toBe(0);
    expect(fixture.componentInstance.alumnos.length).toBe(1);
  });
});
