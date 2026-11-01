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
  it('cambiar soloLectura no cierra el bloque ni pierde lo que se edita', () => {
    abrir(); c.editar(); c.formulario!.alergias = 'No perder';
    fixture.componentRef.setInput('soloLectura', true); fixture.detectChanges();
    expect(c.saludAbierta).toBeTrue(); expect(c.editando).toBeTrue(); expect(c.salud).toEqual(DTO);
    expect(c.formulario!.alergias).toBe('No perder');
  });
  it('cambiar alumnoId cierra el bloque y descarta los datos', () => {
    abrir(); c.editar();
    fixture.componentRef.setInput('alumnoId', 9); fixture.detectChanges();
    expect(c.saludAbierta).toBeFalse(); expect(c.editando).toBeFalse(); expect(c.salud).toBeNull(); expect(c.formulario).toBeNull();
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
  it('cuenta cinco grupos de cambios y descartar vuelve a lectura sin GET', () => {
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
  it('editar en la app no reenvía el origen anterior', () => {
    abrir({ ...DTO, origen: 'PREINSCRIPCION' }); c.editar(); c.formulario!.alergias = 'Otra'; c.guardar();
    const req = http.expectOne(URL);
    expect('origen' in req.request.body).toBeFalse();
    req.flush({ ...DTO, alergias: 'Otra', origen: 'APP' });
  });
  it('sin cambios el botón interno dice Cancelar; con cambios no se ve y manda la barra', () => {
    abrir(); c.editar(); fixture.detectChanges();
    expect(botones()).toContain('Cancelar'); expect(botones()).not.toContain('Descartar');
    c.formulario!.alergias = 'Otra'; fixture.detectChanges();
    expect(botones()).not.toContain('Cancelar'); expect(botones()).not.toContain('Descartar');
  });
  it('Editar lleva el lápiz y sigue siendo un botón aparte de Ocultar salud', () => {
    abrir();
    const editar = Array.from(el().querySelectorAll('button')).find(b => b.textContent!.trim() === 'Editar')!;
    expect(editar.querySelector('i.bi.bi-pencil')).not.toBeNull();
    expect(botones()).toContain('Ocultar salud');
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
  it('destruir con el bloque abierto y el formulario poblado cancela lo que va en vuelo y borra el DTO y el formulario', () => {
    const ficha = c.ficha; expect(ficha.bloqueSalud).toBe(c);
    abrir(); c.editar(); c.formulario!.alergias = 'Otra'; c.guardar();
    const req = http.expectOne(URL);
    expect(c.salud).not.toBeNull(); expect(c.formulario).not.toBeNull(); expect(req.cancelled).toBeFalse();
    fixture.destroy();
    expect(req.cancelled).toBeTrue(); expect(c.salud).toBeNull(); expect(c.formulario).toBeNull();
    expect(ficha.bloqueSalud).toBeNull();
  });
  it('pasar a anonimizado con el bloque abierto cancela y borra los datos, y no deja cambios pendientes', () => {
    abrir(); c.editar(); c.formulario!.alergias = 'Otra'; c.guardar();
    const req = http.expectOne(URL);
    fixture.componentRef.setInput('anonimizado', true); fixture.detectChanges();
    expect(req.cancelled).toBeTrue(); expect(c.saludAbierta).toBeFalse(); expect(c.guardando).toBeFalse();
    expect(c.salud).toBeNull(); expect(c.formulario).toBeNull(); expect(c.cambios).toBe(0);
    expect(el().querySelector('section')).toBeNull();
  });

  describe('textos sin respuesta afirmativa (fichas importadas de papel)', () => {
    const CUERPO = { tieneDiscapacidad: true, discapacidadTipos: ['FISICA'], discapacidadOtra: null, alergias: 'Otra' };
    function guardarCuerpo(): unknown {
      c.formulario!.alergias = 'Otra'; c.guardar();
      const req = http.expectOne(URL), cuerpo = req.request.body;
      req.flush(DTO);
      return cuerpo;
    }
    for (const leido of [null, false]) {
      const papel: SaludAlumno = { ...DTO, lesionEnfermedad: leido, descripcion: '  Texto de papel ', impideActividad: leido, impideDetalle: 'Detalle de papel' };
      it(`con ${leido} sin cambiar envía el texto original tal cual y no cuenta cambios`, () => {
        abrir(papel); c.editar(); expect(c.cambios).toBe(0);
        expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: leido, descripcion: '  Texto de papel ', impideActividad: leido, impideDetalle: 'Detalle de papel' });
      });
      it(`de ${leido} a Sí envía el texto del formulario`, () => {
        abrir(papel); c.editar();
        c.formulario!.lesionEnfermedad = true; c.formulario!.impideActividad = true; c.formulario!.impideDetalle = ' Otro detalle ';
        expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: true, descripcion: 'Texto de papel', impideActividad: true, impideDetalle: 'Otro detalle' });
      });
      it(`con ${leido}, marcar Sí, escribir y volver atrás conserva el texto original`, () => {
        abrir(papel); c.editar();
        c.formulario!.lesionEnfermedad = true; c.formulario!.descripcion = 'Escrito';
        c.formulario!.lesionEnfermedad = leido;
        expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: leido, descripcion: '  Texto de papel ', impideActividad: leido, impideDetalle: 'Detalle de papel' });
      });
    }
    it('de sin indicar a No conserva el texto, que nadie ha visto ni pedido borrar', () => {
      abrir({ ...DTO, lesionEnfermedad: null, descripcion: 'Texto de papel', impideActividad: null, impideDetalle: 'Detalle de papel' }); c.editar();
      c.formulario!.lesionEnfermedad = false; c.formulario!.impideActividad = false;
      expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: false, descripcion: 'Texto de papel', impideActividad: false, impideDetalle: 'Detalle de papel' });
    });
    for (const nuevo of [false, null]) {
      it(`de Sí a ${nuevo} envía null`, () => {
        abrir(); c.editar(); c.formulario!.lesionEnfermedad = nuevo; c.formulario!.impideActividad = nuevo;
        expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: nuevo, descripcion: null, impideActividad: nuevo, impideDetalle: null });
      });
    }
    it('con Sí envía el texto editado del formulario', () => {
      abrir(); c.editar(); c.formulario!.descripcion = ' Otra descripción '; c.formulario!.impideDetalle = '   ';
      expect(guardarCuerpo()).toEqual({ ...CUERPO, lesionEnfermedad: true, descripcion: 'Otra descripción', impideActividad: true, impideDetalle: null });
    });
  });
});
