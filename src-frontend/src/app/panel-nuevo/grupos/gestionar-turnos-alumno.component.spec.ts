import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { GestionarTurnosAlumnoComponent } from './gestionar-turnos-alumno.component';
import type { TurnoResumen } from '../../interfaces/turno-resumen';

describe('GestionarTurnosAlumnoComponent (3m-5)', () => {
  let fixture: ComponentFixture<GestionarTurnosAlumnoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.SpyObj<ConfirmacionService>;
  let avisos: jasmine.SpyObj<AvisosService>;
  const params = new BehaviorSubject({ alumnoId: '7' });
  const base = environment.apiUrl;
  const turno = (id: number, diaSemana = 'Lunes', horaInicio = '17:30:00'): TurnoResumen =>
    ({ id, diaSemana, horaInicio, horaFin: '18:30:00', tipoGrupo: 'Taekwondo infantil', grupoId: 4, grupoNombre: 'Infantiles' });
  beforeEach(async () => {
    params.next({ alumnoId: '7' });
    confirmar = jasmine.createSpyObj('Confirmacion', ['confirmar']);
    confirmar.confirmar.and.resolveTo({ confirmado: false, valor: null });
    avisos = jasmine.createSpyObj('Avisos', ['exito', 'error']);
    await TestBed.configureTestingModule({ imports: [GestionarTurnosAlumnoComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: ActivatedRoute, useValue: { params } },
      { provide: ConfirmacionService, useValue: confirmar }, { provide: AvisosService, useValue: avisos },
    ] }).compileComponents();
    fixture = TestBed.createComponent(GestionarTurnosAlumnoComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function cargar(asignados: TurnoResumen[] = [], disponibles: TurnoResumen[] = [turno(1)], id = 7): void {
    const alumno = http.expectOne(`${base}/alumnos/${id}`);
    expect(alumno.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(alumno.request.withCredentials).toBeTrue();
    alumno.flush({ id, nombre: 'Lucía', apellidos: 'Ortega' });
    http.expectOne(`${base}/alumnos/${id}/turnos`).flush(asignados);
    http.expectOne(`${base}/turnos/resumen`).flush(disponibles);
    fixture.detectChanges();
  }
  it('loads locally and orders rows and options by day then time', () => {
    expect(fixture.nativeElement.querySelector('pn-esqueleto')).not.toBeNull();
    const lista = [turno(3, 'Martes'), turno(2, 'Lunes', '18:00:00'), turno(1)];
    cargar(lista, lista);
    expect(fixture.componentInstance.turnos.map(t => t.id)).toEqual([1, 2, 3]);
    expect(fixture.componentInstance.disponibles.map(t => t.id)).toEqual([1, 2, 3]);
    expect(fixture.nativeElement.textContent).toContain('Turnos de Lucía Ortega');
    expect(fixture.nativeElement.textContent).toContain('17:30 – 18:30');
    expect(fixture.nativeElement.textContent).toContain('Taekwondo infantil · Infantiles');
    expect(fixture.nativeElement.querySelector('header a').getAttribute('href')).toBe('/alumnosEditar/7');
    fixture.nativeElement.querySelector('[role=combobox]').click(); fixture.detectChanges();
    const opciones = fixture.nativeElement.querySelectorAll('[role=option]');
    expect(opciones[0].getAttribute('aria-disabled')).toBe('true');
    expect(opciones[0].textContent).toContain('ya asignado');
  });
  it('shows empty state and disables assignment without selection', () => {
    cargar();
    expect(fixture.nativeElement.textContent).toContain('Lucía no tiene turnos asignados.');
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
  });
  it('navigates enabled options and selects without submitting', () => {
    cargar([turno(1)], [turno(1), turno(2), turno(3, 'Martes')]);
    const campo: HTMLButtonElement = fixture.nativeElement.querySelector('[role=combobox]');
    const tecla = (key: string): void => { campo.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })); fixture.detectChanges(); };
    campo.focus(); tecla('ArrowDown');
    expect(campo.getAttribute('aria-expanded')).toBe('true');
    expect(campo.getAttribute('aria-activedescendant')).toBe('turno-alumno-opcion-2');
    tecla('End'); expect(campo.getAttribute('aria-activedescendant')).toBe('turno-alumno-opcion-3');
    tecla('Home'); tecla('ArrowDown'); tecla('ArrowUp'); tecla('Enter');
    expect(fixture.componentInstance.seleccion).toBe('2');
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    expect(campo.textContent).toContain('Lunes · 17:30 – 18:30 · Taekwondo infantil');
    http.expectNone(`${base}/alumnos/7/turnos/2`);
    tecla(' '); tecla('Escape');
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(campo);
  });
  it('reveals the active option when opening a selection or navigating to the end', () => {
    const revelar = spyOn(HTMLElement.prototype, 'scrollIntoView');
    cargar([], Array.from({ length: 30 }, (_, indice) => turno(indice + 1)));
    fixture.componentInstance.seleccion = '15'; fixture.detectChanges();
    const campo: HTMLButtonElement = fixture.nativeElement.querySelector('[role=combobox]');
    campo.focus(); campo.click(); fixture.detectChanges();
    expect((revelar.calls.mostRecent().object as HTMLElement).id).toBe('turno-alumno-opcion-15');
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true })); fixture.detectChanges();
    expect((revelar.calls.mostRecent().object as HTMLElement).id).toBe('turno-alumno-opcion-30');
    expect(revelar).toHaveBeenCalledWith({ block: 'nearest', behavior: 'instant' });
    expect(document.activeElement).toBe(campo);
  });
  it('rejects assigned options and closes outside or on Tab', () => {
    cargar([turno(1)], [turno(1), turno(2)]);
    const campo: HTMLButtonElement = fixture.nativeElement.querySelector('[role=combobox]');
    campo.click(); fixture.detectChanges();
    fixture.nativeElement.querySelector('[role=option]').click(); fixture.detectChanges();
    expect(fixture.componentInstance.seleccion).toBe('');
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
    document.body.click(); fixture.detectChanges();
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    campo.click(); fixture.detectChanges();
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true })); fixture.detectChanges();
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    fixture.componentInstance.seleccion = '1'; fixture.componentInstance.asignar();
    http.expectNone(`${base}/alumnos/7/turnos/1`);
  });
  it('handles all-assigned options and resets the list on student changes', () => {
    cargar([turno(1)], [turno(1)]);
    const campo: HTMLButtonElement = fixture.nativeElement.querySelector('[role=combobox]');
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true })); fixture.detectChanges();
    expect(campo.hasAttribute('aria-activedescendant')).toBeFalse();
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })); fixture.detectChanges();
    expect(fixture.componentInstance.seleccion).toBe('');
    params.next({ alumnoId: '8' }); cargar([], [turno(2)], 8);
    expect(fixture.nativeElement.querySelector('[role=combobox]').getAttribute('aria-expanded')).toBe('false');
  });
  it('selects by pointer, closes on focus loss and blocks changes during assignment', () => {
    cargar([], [turno(1), turno(2, 'Martes')]);
    const campo: HTMLButtonElement = fixture.nativeElement.querySelector('[role=combobox]');
    campo.click(); fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('[role=option]')[1].click(); fixture.detectChanges();
    expect(fixture.componentInstance.seleccion).toBe('2');
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    campo.click(); fixture.detectChanges();
    campo.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: fixture.nativeElement.querySelector('button[type=submit]') }));
    fixture.detectChanges(); expect(campo.getAttribute('aria-expanded')).toBe('false');
    fixture.componentInstance.asignar(); fixture.detectChanges();
    expect(campo.disabled).toBeTrue();
    fixture.componentInstance.elegirTurno(turno(1)); fixture.componentInstance.alternarLista();
    expect(fixture.componentInstance.seleccion).toBe('2');
    expect(fixture.componentInstance.listaAbierta).toBeFalse();
    http.expectOne(`${base}/alumnos/7/turnos/2`).flush({});
  });
  it('assigns with exact body and prevents duplicate and concurrent submissions', () => {
    cargar(); fixture.componentInstance.seleccion = '1';
    fixture.componentInstance.asignar(); fixture.componentInstance.asignar();
    const req = http.expectOne(`${base}/alumnos/7/turnos/1`);
    expect(req.request.method).toBe('POST'); expect(req.request.body).toEqual({});
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({}); fixture.detectChanges();
    expect(avisos.exito).toHaveBeenCalledWith('Turno asignado');
    expect(fixture.componentInstance.seleccion).toBe('');
    fixture.componentInstance.seleccion = '1'; fixture.componentInstance.asignar();
    http.expectNone(`${base}/alumnos/7/turnos/1`);
    expect(fixture.componentInstance.turnos.length).toBe(1);
  });
  it('cancels removal then confirms the reversible DELETE', async () => {
    cargar([turno(1)]); await fixture.componentInstance.quitar(turno(1));
    http.expectNone(`${base}/alumnos/7/turnos/1`);
    confirmar.confirmar.and.resolveTo({ confirmado: true, valor: null });
    await fixture.componentInstance.quitar(turno(1));
    const opciones = confirmar.confirmar.calls.mostRecent().args[0];
    expect(opciones.titulo).toBe('¿Quitar el turno del lunes 17:30 – 18:30 a Lucía?');
    expect(opciones.mensaje).toBe('Puedes volver a asignarlo.'); expect(opciones.destructivo).toBeFalse();
    const req = http.expectOne(`${base}/alumnos/7/turnos/1`);
    expect(req.request.method).toBe('DELETE'); expect(req.request.body).toBeNull();
    req.flush([]); fixture.detectChanges();
    expect(avisos.exito).toHaveBeenCalledWith('Turno quitado');
    expect(fixture.componentInstance.turnos).toEqual([]);
  });
  for (const servidor of [true, false]) {
    it(`shows ${servidor ? 'server' : 'generic'} load error and retries`, () => {
      const peticiones = http.match(() => true);
      peticiones[0].flush(servidor ? { message: 'Alumno no disponible' } : {}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(fixture.componentInstance.errorCarga).toBe(servidor ? 'Alumno no disponible' : 'No hemos podido obtener los datos del alumno');
      expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
      fixture.componentInstance.cargar(); cargar();
    });
    it(`shows ${servidor ? 'server' : 'generic'} assign error without losing selection`, () => {
      cargar(); fixture.componentInstance.seleccion = '1'; fixture.componentInstance.asignar();
      http.expectOne(`${base}/alumnos/7/turnos/1`).flush(servidor ? { message: 'Sin plazas' } : {}, { status: 409, statusText: 'Error' });
      expect(avisos.error).toHaveBeenCalledWith(servidor ? 'Sin plazas' : 'No hemos podido asignar el turno al alumno');
      expect(fixture.componentInstance.seleccion).toBe('1'); expect(fixture.componentInstance.ocupado).toBeFalse();
    });
    it(`shows ${servidor ? 'server' : 'generic'} remove error without losing the row`, async () => {
      cargar([turno(1)]); confirmar.confirmar.and.resolveTo({ confirmado: true, valor: null });
      await fixture.componentInstance.quitar(turno(1));
      http.expectOne(`${base}/alumnos/7/turnos/1`).flush(servidor ? { message: 'No permitido' } : {}, { status: 500, statusText: 'Error' });
      expect(avisos.error).toHaveBeenCalledWith(servidor ? 'No permitido' : 'No se pudo quitar el turno');
      expect(fixture.componentInstance.turnos.length).toBe(1); expect(fixture.componentInstance.ocupado).toBeFalse();
    });
  }
  it('validates zero on the first route emission without requesting data or leaving a skeleton', () => {
    fixture.destroy();
    http.match(() => true);
    params.next({ alumnoId: '0' });
    fixture = TestBed.createComponent(GestionarTurnosAlumnoComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.cargando).toBeFalse();
    expect(fixture.componentInstance.errorCarga).toBe('No hemos podido obtener los datos del alumno');
    expect(fixture.nativeElement.querySelector('pn-esqueleto')).toBeNull();
    expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
    http.expectNone(() => true);
  });
  it('cancels loading and clears selection on student changes', () => {
    const anteriores = http.match(() => true); params.next({ alumnoId: '8' });
    expect(anteriores.every(req => req.cancelled)).toBeTrue();
    expect(fixture.componentInstance.alumno).toBeNull(); cargar([], [], 8);
    fixture.componentInstance.seleccion = '1'; params.next({ alumnoId: '9' });
    expect(fixture.componentInstance.seleccion).toBe(''); expect(fixture.componentInstance.turnos).toEqual([]);
    cargar([], [], 9);
  });
  it('rejects stale confirmation even after returning to the same student', async () => {
    cargar([turno(1)]);
    let resolver!: (valor: { confirmado: boolean; valor: null }) => void;
    confirmar.confirmar.and.returnValue(new Promise(resolve => { resolver = resolve; }));
    const pendiente = fixture.componentInstance.quitar(turno(1));
    params.next({ alumnoId: '8' }); cargar([], [], 8);
    params.next({ alumnoId: '7' }); cargar([turno(1)]);
    resolver({ confirmado: true, valor: null }); await pendiente;
    http.expectNone(`${base}/alumnos/7/turnos/1`);
  });
  it('cancels pending mutation before loading another student', () => {
    cargar(); fixture.componentInstance.seleccion = '1'; fixture.componentInstance.asignar();
    const req = http.expectOne(`${base}/alumnos/7/turnos/1`);
    params.next({ alumnoId: '8' }); expect(req.cancelled).toBeTrue(); cargar([], [], 8);
    expect(avisos.exito).not.toHaveBeenCalled(); expect(fixture.componentInstance.ocupado).toBeFalse();
  });
});
