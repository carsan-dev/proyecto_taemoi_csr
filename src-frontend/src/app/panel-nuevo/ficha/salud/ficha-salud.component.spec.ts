import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { SaludAlumno } from '../../../interfaces/profesor';
import { AvisosService } from '../../avisos/avisos.service';
import { SaludAlumnoService } from '../../salud/salud-alumno.service';
import { FichaAlumnoComponent } from '../ficha-alumno.component';
import { FichaSaludComponent } from './ficha-salud.component';

const URL = environment.apiUrl + '/alumnos/7/salud';
const DTO: SaludAlumno = {
  alumnoId: 7, registrado: true, tieneDiscapacidad: true, discapacidadTipos: ['FISICA'], discapacidadOtra: null,
  lesionEnfermedad: true, descripcion: 'Descripción ficticia', impideActividad: true, impideDetalle: 'Detalle ficticio', alergias: 'Alergia ficticia',
};

describe('FichaSaludComponent', () => {
  let fixture: ComponentFixture<FichaSaludComponent>;
  let c: FichaSaludComponent;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  const el = (): HTMLElement => fixture.nativeElement;
  const botones = () => Array.from(el().querySelectorAll('button')).map(b => b.textContent!.trim());
  function abrir(dto: SaludAlumno = DTO): void {
    c.conmutarSalud(); http.expectOne(URL).flush(dto); fixture.detectChanges();
  }
  beforeEach(() => {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    TestBed.configureTestingModule({ imports: [FichaSaludComponent], providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AvisosService, useValue: avisos }] });
    fixture = TestBed.createComponent(FichaSaludComponent);
    c = fixture.componentInstance;
    fixture.componentRef.setInput('ficha', { alumno: { id: 7, tieneDiscapacidad: true }, bloqueSalud: null } as unknown as FichaAlumnoComponent);
    fixture.componentRef.setInput('alumnoId', 7);
    fixture.detectChanges();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { fixture.destroy(); http.verify(); });

  it('empieza plegado sin petición; cada apertura hace un GET nuevo con contexto local', () => {
    http.expectNone(URL);
    expect(el().querySelector('[aria-controls]')!.getAttribute('aria-expanded')).toBe('false');
    c.conmutarSalud(); fixture.detectChanges();
    expect(el().querySelector('[role="status"]')!.textContent).toBe('Cargando…');
    const req = http.expectOne(URL);
    expect(req.request.withCredentials).toBeTrue(); expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush(DTO); c.conmutarSalud();
    expect(c.salud).toBeNull(); expect(c.formulario).toBeNull();
    c.conmutarSalud(); http.expectOne(URL).flush(DTO);
  });
  it('plegar cancela la petición en vuelo y descarta todos los datos', () => {
    c.conmutarSalud(); const req = http.expectOne(URL); c.conmutarSalud();
    expect(req.cancelled).toBeTrue(); expect(c.salud).toBeNull(); expect(c.cargando).toBeFalse();
  });
  it('carrera 7 a 9: cancela 7 y nunca pinta su respuesta vieja', () => {
    const vieja = new Subject<SaludAlumno>();
    const api = TestBed.inject(SaludAlumnoService);
    const get = spyOn(api, 'obtener').and.returnValue(vieja);
    c.conmutarSalud();
    fixture.componentRef.setInput('alumnoId', 9); fixture.detectChanges();
    expect(vieja.observed).toBeFalse(); expect(c.saludAbierta).toBeFalse();
    get.and.returnValue(of({ ...DTO, alumnoId: 9, alergias: 'Solo nueve' }));
    c.conmutarSalud(); vieja.next(DTO); fixture.detectChanges();
    expect(c.salud?.alumnoId).toBe(9); expect(el().textContent).toContain('Solo nueve');
    expect(el().textContent).not.toContain('Alergia ficticia');
  });
  it('sin datos ofrece añadir; con discapacidad sin registro muestra la lista', () => {
    abrir({ ...DTO, registrado: false, tieneDiscapacidad: false, discapacidadTipos: [] });
    expect(el().textContent).toContain('Sin datos de salud registrados.');
    expect(botones()).toContain('Añadir datos de salud');
    c.conmutarSalud(); abrir({ ...DTO, registrado: false, discapacidadTipos: [] });
    expect(el().querySelector('dl')!.textContent).toContain('Sin indicar');
  });
  it('otra sede solo lee, sin Editar ni Añadir', () => {
    fixture.componentRef.setInput('soloLectura', true); fixture.detectChanges(); abrir();
    expect(botones()).not.toContain('Editar'); c.editar(); expect(c.editando).toBeFalse();
    c.conmutarSalud(); abrir({ ...DTO, registrado: false, tieneDiscapacidad: false });
    expect(botones()).not.toContain('Añadir datos de salud');
  });
  it('anonimizada no muestra bloque ni pide salud', () => {
    fixture.componentRef.setInput('anonimizado', true); fixture.detectChanges(); c.conmutarSalud(); c.pedirSalud();
    expect(el().querySelector('section')).toBeNull(); http.expectNone(URL);
  });
  it('cuenta cinco grupos de cambios y Descartar vuelve a lectura sin GET', () => {
    abrir(); c.editar(); expect(c.cambios).toBe(0);
    const f = c.formulario!;
    f.tieneDiscapacidad = false; f.discapacidadTipos = ['AUDITIVA']; f.descripcion = 'Otra'; f.impideDetalle = 'Otro'; f.alergias = 'Otra';
    expect(c.cambios).toBe(5);
    c.descartarEdicion(); fixture.detectChanges();
    expect(c.cambios).toBe(0); expect(c.editando).toBeFalse(); expect(c.salud).toEqual(DTO); http.expectNone(URL);
  });
  it('no cuenta reordenar tipos como cambio', () => {
    abrir({ ...DTO, discapacidadTipos: ['FISICA', 'AUDITIVA'] }); c.editar();
    c.formulario!.discapacidadTipos = ['AUDITIVA', 'FISICA']; expect(c.cambios).toBe(0);
  });
  it('Otra vacía no envía y muestra el error junto al campo', () => {
    abrir(); c.editar(); c.formulario!.discapacidadTipos = ['OTRA']; c.formulario!.discapacidadOtra = '   ';
    c.guardar(); fixture.detectChanges(); http.expectNone(URL);
    expect(el().textContent).toContain('Indica cuál es la discapacidad.');
  });
  it('PUT exacto, sin metadatos de lectura; éxito actualiza discapacidad sin recargar', () => {
    abrir(); c.editar(); c.formulario!.tieneDiscapacidad = false; c.guardar();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ tieneDiscapacidad: false, discapacidadTipos: [], discapacidadOtra: null,
      lesionEnfermedad: true, descripcion: DTO.descripcion, impideActividad: true, impideDetalle: DTO.impideDetalle, alergias: DTO.alergias });
    expect(c.guardando).toBeTrue();
    c.guardar(); http.expectNone(URL);
    req.flush({ ...DTO, tieneDiscapacidad: false, discapacidadTipos: [] });
    expect(c.ficha.alumno.tieneDiscapacidad).toBeFalse(); expect(c.editando).toBeFalse();
    expect(avisos.exito).toHaveBeenCalledWith('Datos de salud guardados.'); http.expectNone(URL);
  });
  it('con No envía los textos asociados como null, aunque hubiese texto guardado', () => {
    abrir(); c.editar(); c.formulario!.lesionEnfermedad = false; c.formulario!.impideActividad = false; c.guardar();
    const req = http.expectOne(URL);
    expect(req.request.body.descripcion).toBeNull(); expect(req.request.body.impideDetalle).toBeNull();
    req.flush({ ...DTO, lesionEnfermedad: false, descripcion: null, impideActividad: false, impideDetalle: null });
  });
  it('el error del servidor conserva la edición y todo lo escrito', () => {
    abrir(); c.editar(); c.formulario!.alergias = 'No perder'; c.guardar();
    http.expectOne(URL).flush({ mensaje: 'No se pudo guardar.' }, { status: 500, statusText: 'Error' });
    expect(c.editando).toBeTrue(); expect(c.formulario!.alergias).toBe('No perder');
    expect(avisos.error).toHaveBeenCalledWith('No se pudo guardar.'); expect(c.guardando).toBeFalse();
  });
  it('error de lectura permite reintentar sin conservar datos', () => {
    c.conmutarSalud(); http.expectOne(URL).flush({}, { status: 500, statusText: 'Error' }); fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')!.textContent).toContain('No se han podido leer los datos de salud.');
    c.pedirSalud(); http.expectOne(URL).flush(DTO); expect(c.error).toBeNull();
  });
  it('destruir cancela la petición y borra el DTO y el formulario', () => {
    c.conmutarSalud(); const req = http.expectOne(URL); c.ngOnDestroy();
    expect(req.cancelled).toBeTrue(); expect(c.salud).toBeNull(); expect(c.formulario).toBeNull();
  });
});
