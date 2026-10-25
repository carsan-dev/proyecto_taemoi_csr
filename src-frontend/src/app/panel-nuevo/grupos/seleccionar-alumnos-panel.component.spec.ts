import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AvisosService } from '../avisos/avisos.service';
import { SeleccionarAlumnosPanelComponent } from './seleccionar-alumnos-panel.component';

describe('SeleccionarAlumnosPanelComponent (3m-4)', () => {
  let fixture: ComponentFixture<SeleccionarAlumnosPanelComponent>;
  let http: HttpTestingController;
  const alumno = (id: number) => ({ id, nombre: `Alumno ${id}`, apellidos: 'Prueba', activo: true });
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeleccionarAlumnosPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AvisosService, useValue: jasmine.createSpyObj('Avisos', ['exito', 'error']) }],
    }).compileComponents();
    fixture = TestBed.createComponent(SeleccionarAlumnosPanelComponent);
    fixture.componentRef.setInput('grupo', { id: 7, nombre: 'Infantiles', alumnos: [alumno(1)] });
    http = TestBed.inject(HttpTestingController);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function lista(ids: number[], total = ids.length): void {
    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/alumnos`);
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('incluirInactivos')).toBe('false');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ content: ids.map(alumno), totalElements: total, totalPages: Math.ceil(total / 10) });
    fixture.detectChanges();
  }
  it('keeps existing pupils dimmed without a checkbox and disables zero selection', () => {
    lista([1, 2]);
    const fila = fixture.nativeElement.querySelector('.seleccionar__fila--existente') as HTMLElement;
    expect(fila.querySelector(':scope > .seleccionar__estado')?.textContent).toBe('Ya está en el grupo');
    expect(fila.querySelector('.seleccionar__nombre')?.textContent?.trim()).toBe('Alumno 1 Prueba');
    expect(fixture.nativeElement.querySelector('.seleccionar__fila:not(.seleccionar__fila--existente) .seleccionar__estado')).toBeNull();
    expect(fila.querySelector('input')).toBeNull();
    expect(fila.firstElementChild?.classList).toContain('seleccionar__casilla-vacia');
    expect(fila.firstElementChild?.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.querySelector('.seleccionar__anadir').disabled).toBeTrue();
    const checkbox = fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement;
    expect(fixture.nativeElement.querySelector(`label[for="${checkbox.id}"]`)).not.toBeNull();
    fixture.componentInstance.alternar(1);
    expect(fixture.componentInstance.elegidos.size).toBe(0);
  });
  it('renders 32px photos on dimmed rows and falls back to initials without mutating pupils', () => {
    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/alumnos`);
    req.flush({ content: [
      { ...alumno(1), fotoAlumno: { id: 22, url: '/foto?token=x' } },
      alumno(2),
      { ...alumno(3), fotoAlumno: { id: 23, url: '/foto-rota' } },
    ], totalElements: 3, totalPages: 1 });
    fixture.detectChanges();
    const filas = fixture.nativeElement.querySelectorAll('.seleccionar__fila') as NodeListOf<HTMLElement>;
    const foto = filas[0].querySelector('img') as HTMLImageElement;
    expect(filas[0].classList).toContain('seleccionar__fila--existente');
    expect(foto.getAttribute('src')).toBe('/foto?token=x&w=180&v=22');
    expect(foto.getAttribute('width')).toBe('32');
    expect(foto.getAttribute('height')).toBe('32');
    expect(foto.getAttribute('alt')).toBe('');
    expect(foto.getAttribute('loading')).toBe('lazy');
    expect(filas[1].querySelector('.seleccionar__avatar')?.textContent?.trim()).toBe('AP');
    const rota = filas[2].querySelector('img') as HTMLImageElement;
    rota.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(filas[2].querySelector('img')).toBeNull();
    expect(filas[2].querySelector('.seleccionar__avatar')?.textContent?.trim()).toBe('AP');
    expect(fixture.componentInstance.alumnos[2].fotoAlumno?.url).toBe('/foto-rota');
  });
  it('keeps search mounted and focused during loading, selection across pages/searches, and cancels stale requests', fakeAsync(() => {
    lista([2], 20);
    fixture.componentInstance.alternar(2);
    fixture.componentInstance.cambiarPagina(2);
    lista([3], 20);
    fixture.componentInstance.alternar(3);
    const input = fixture.nativeElement.querySelector('input[type=search]') as HTMLInputElement;
    input.focus();
    fixture.componentInstance.buscar('Ana');
    tick(500);
    const vieja = http.expectOne((r) => r.params.get('nombre') === 'Ana');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[type=search]')).toBe(input);
    expect(document.activeElement).toBe(input);
    fixture.componentInstance.buscar('Eva');
    expect(vieja.cancelled).toBeTrue();
    tick(500);
    const nueva = http.expectOne((r) => r.params.get('nombre') === 'Eva');
    expect(nueva.request.params.get('page')).toBe('1');
    nueva.flush({ content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ningún alumno activo coincide con «Eva»');
    expect(fixture.nativeElement.querySelector('.seleccionar__vacio h2')?.textContent).toContain('«Eva»');
    expect(fixture.nativeElement.querySelector('.seleccionar__vacio p')?.textContent).toContain('Los 2 elegidos se conservan. Cambia la búsqueda para elegir más.');
    fixture.componentInstance.elegidos.delete(3); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.seleccionar__vacio p')?.textContent).toContain('El alumno elegido se conserva.');
    fixture.componentInstance.elegidos.add(3);
    expect([...fixture.componentInstance.elegidos]).toEqual([2, 3]);
    fixture.componentInstance.quitarSeleccion();
    expect(fixture.componentInstance.elegidos.size).toBe(0);
  }));
  it('posts the unchanged ID array once, emits success, and allows cancel without posting', () => {
    lista([2]);
    const guardado = spyOn(fixture.componentInstance.anadidos, 'emit');
    const cerrado = spyOn(fixture.componentInstance.cerrado, 'emit');
    fixture.componentInstance.cerrar();
    expect(cerrado).toHaveBeenCalledTimes(1);
    fixture.componentInstance.anadir();
    http.expectNone(`${environment.apiUrl}/grupos/7/alumnos`);
    fixture.componentInstance.alternar(2);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 elegido');
    expect((fixture.nativeElement.querySelector('.seleccionar__anadir') as HTMLButtonElement).textContent).toContain('Añadir 1 alumno');
    fixture.componentInstance.anadir();
    const req = http.expectOne(`${environment.apiUrl}/grupos/7/alumnos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual([2]);
    expect(req.request.withCredentials).toBeTrue();
    req.flush({});
    expect(guardado).toHaveBeenCalledTimes(1);
  });
  it('keeps the no-match message accurate without a retained selection', fakeAsync(() => {
    lista([]);
    fixture.componentInstance.buscar('Zoe'); tick(500);
    lista([]);
    const vacio = fixture.nativeElement.querySelector('.seleccionar__vacio') as HTMLElement;
    expect(vacio.getAttribute('role')).toBe('status');
    expect(vacio.querySelector('h2')?.textContent).toContain('Ningún alumno activo coincide con «Zoe»');
    expect(vacio.querySelector('p')?.textContent?.trim()).toBe('Cambia la búsqueda para elegir más.');
    expect(vacio.querySelector('button')).toBeNull();
  }));
  it('recovers list and submit errors without losing selection', () => {
    http.expectOne((r) => r.url.endsWith('/alumnos')).flush('fallo', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
    fixture.componentInstance.cargarAlumnos();
    lista([2]);
    fixture.componentInstance.alternar(2);
    fixture.componentInstance.anadir();
    http.expectOne(`${environment.apiUrl}/grupos/7/alumnos`).flush('fallo', { status: 500, statusText: 'Error' });
    expect(fixture.componentInstance.enviando).toBeFalse();
    expect(fixture.componentInstance.elegidos.has(2)).toBeTrue();
    expect(fixture.componentInstance.errorEnvio).toBeTruthy();
  });
});
