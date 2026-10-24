import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
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
  function grupo(alumnos: AlumnoGrupo[] = [alumno(1)]): void {
    const req = http.expectOne(`${environment.apiUrl}/grupos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7, nombre: 'Infantiles', alumnos });
    fixture.detectChanges();
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
