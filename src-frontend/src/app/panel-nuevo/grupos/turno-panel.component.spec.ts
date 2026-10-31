import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import Swal from 'sweetalert2';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { AvisosService } from '../avisos/avisos.service';
import { TIPOS_TURNO, TurnoPanelComponent } from './turno-panel.component';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';

const API = environment.apiUrl;
const GRUPOS = [{ id: 1, nombre: 'Infantiles A', deporte: 'TAEKWONDO', numAlumnos: 99, turnos: [] },
  { id: 2, nombre: 'Infantiles B', numAlumnos: 80, turnos: [] }];
const TURNO = { id: 7, diaSemana: 'Lunes', horaInicio: '17:00:00', horaFin: '18:30:00', grupoId: 1,
  grupoNombre: 'Infantiles A', alumnos: [{ id: 31 }, { id: 32 }] };

describe('TurnoPanelComponent (3m-3)', () => {
  let fixture: ComponentFixture<TurnoPanelComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;

  beforeEach(async () => {
    avisos = jasmine.createSpyObj('AvisosService', ['exito', 'error']);
    spyOn(Swal, 'fire');
    await TestBed.configureTestingModule({ imports: [TurnoPanelComponent], providers: [
      provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([]),
      { provide: AvisosService, useValue: avisos },
    ] }).compileComponents();
    fixture = TestBed.createComponent(TurnoPanelComponent);
    http = TestBed.inject(HttpTestingController);
    document.body.appendChild(fixture.nativeElement);
  });
  afterEach(() => { fixture.destroy(); http.verify(); });

  function iniciar(editar = false, grupoId: number | null = null): TurnoPanelComponent {
    fixture.componentRef.setInput('turnoId', editar ? 7 : null);
    fixture.componentRef.setInput('grupoInicialId', grupoId);
    fixture.detectChanges();
    const grupos = http.expectOne(`${API}/grupos/resumen`);
    expect(grupos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    grupos.flush(GRUPOS);
    if (editar) http.expectOne(`${API}/turnos/7`).flush(TURNO);
    fixture.detectChanges();
    return fixture.componentInstance;
  }
  function horario(panel: TurnoPanelComponent): void {
    panel.form.patchValue({ diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:30' });
  }

  it('muestra metadatos obligatorios y ayuda enlazada al crear', () => {
    iniciar(false, 1);
    const host = fixture.nativeElement as HTMLElement;
    for (const selector of ['#turno-dia-etiqueta', 'label[for="turno-inicio"]', 'label[for="turno-fin"]', 'label[for="turno-tipo"]']) {
      expect(host.querySelector(selector)!.textContent).toContain('obligatorio');
    }
    expect(host.querySelector('label[for="turno-grupo"]')!.textContent).toBe('Grupo opcional');
    expect(host.querySelector('#turno-grupo-ayuda')!.textContent).toBe('Puedes asignar este turno a un grupo o dejarlo sin grupo. «Sin grupo» es la primera opción.');
    expect(host.querySelector('#turno-tipo-ayuda')!.textContent).toBe('Elige el tipo del turno. Solo se pide al crear.');
    expect(host.querySelector('#turno-tipo')!.getAttribute('aria-describedby')).toBe('turno-tipo-ayuda');
    fixture.componentInstance.form.controls.tipoGrupo.markAsTouched(); fixture.detectChanges();
    expect(host.querySelector('#turno-tipo')!.getAttribute('aria-describedby')).toBe('turno-tipo-ayuda turno-tipo-error');
  });

  it('conserva los metadatos de horario y grupo opcional al editar sin pedir tipo', () => {
    iniciar(true);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('#turno-dia-etiqueta')!.textContent).toBe('Día de la semana obligatorio');
    for (const selector of ['label[for="turno-inicio"]', 'label[for="turno-fin"]']) {
      expect(host.querySelector(selector)!.textContent).toContain('obligatorio');
    }
    expect(host.querySelector('label[for="turno-grupo"]')!.textContent).toBe('Grupo opcional');
    expect(host.querySelector('#turno-tipo')).toBeNull();
  });

  it('conserva los siete valores exactos y exige tipo solo al crear con grupo', () => {
    const panel = iniciar();
    expect(TIPOS_TURNO).toEqual(['Taekwondo Infantil', 'Taekwondo Joven', 'Taekwondo Adulto', 'Kickboxing',
      'Pilates', 'Defensa Personal Femenina', 'Competición']);
    horario(panel);
    expect(panel.form.valid).toBeTrue();
    panel.form.controls.grupoId.setValue('1');
    panel.form.controls.tipoGrupo.markAsTouched();
    fixture.detectChanges();
    expect(panel.form.invalid).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('El tipo de grupo es obligatorio');
    expect(fixture.nativeElement.querySelectorAll('#turno-tipo option').length).toBe(8);
    panel.form.controls.tipoGrupo.setValue('TAEKWONDO');
    expect(panel.form.invalid).toBeTrue();
    panel.form.controls.tipoGrupo.setValue('Taekwondo Infantil');
    expect(panel.form.valid).toBeTrue();
    panel.form.controls.grupoId.setValue('');
    fixture.detectChanges();
    expect(panel.form.valid).toBeTrue();
    expect(fixture.nativeElement.querySelector('#turno-tipo')).toBeNull();
  });

  it('valida día, formato y horas cruzadas y calcula la duración española', () => {
    const panel = iniciar();
    expect(panel.form.invalid).toBeTrue();
    horario(panel);
    expect(panel.duracion).toBe('1 h 30 min');
    panel.form.controls.horaFin.setValue('18:00');
    expect(panel.duracion).toBe('1 h');
    panel.form.controls.horaFin.setValue('17:45');
    expect(panel.duracion).toBe('45 min');
    for (const hora of ['17:00', '16:59', '25:00', 'abc']) {
      panel.form.controls.horaFin.setValue(hora);
      expect(panel.form.invalid).toBeTrue();
      expect(panel.duracion).toBe('');
    }
    panel.guardar();
    http.expectNone(`${API}/turnos/crear`);
  });

  it('usa radios nativos de días con nombres completos y primera opción Sin grupo', () => {
    iniciar();
    const radios: HTMLInputElement[] = Array.from(fixture.nativeElement.querySelectorAll('input[type=radio]'));
    expect(radios.map((radio) => radio.getAttribute('aria-label'))).toEqual([
      'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']);
    expect(fixture.nativeElement.querySelector('#turno-grupo option').textContent.trim()).toBe('Sin grupo');
    radios[2].click();
    expect(fixture.componentInstance.form.controls.diaSemana.value).toBe('Miércoles');
  });

  for (const tipo of [undefined, 'TAEKWONDO', 'Taekwondo Infantil']) {
    it(`preselecciona grupo sin inventar tipo (${tipo})`, () => {
      fixture.componentRef.setInput('grupoInicialId', 1);
      fixture.detectChanges();
      http.expectOne(`${API}/grupos/resumen`).flush([{ ...GRUPOS[0], tipo }]);
      expect(fixture.componentInstance.form.controls.grupoId.value).toBe('1');
      expect(fixture.componentInstance.form.controls.tipoGrupo.value).toBe(tipo === 'Taekwondo Infantil' ? tipo : '');
    });
  }

  for (const conGrupo of [false, true]) {
    it(`crea con el cuerpo y endpoint de hoy (grupo ${conGrupo}) y bloquea envíos/cierre duplicados`, () => {
      const panel = iniciar();
      horario(panel);
      if (conGrupo) panel.form.patchValue({ grupoId: '1', tipoGrupo: 'Pilates' });
      const guardado = spyOn(panel.guardado, 'emit');
      const cerrado = spyOn(panel.cerrado, 'emit');
      panel.guardar();
      panel.guardar();
      panel.cerrar();
      fixture.detectChanges();
      expect(panel.enviando).toBeTrue();
      expect(cerrado).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('fieldset').disabled).toBeTrue();
      const req = http.expectOne(`${API}/turnos/${conGrupo ? 'crear-asignando-grupo' : 'crear'}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      expect(req.request.body).toEqual({ diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:30',
        asignarGrupo: conGrupo, grupoId: conGrupo ? '1' : '', tipoGrupo: conGrupo ? 'Pilates' : '' });
      req.flush({ id: 9 });
      expect(guardado).toHaveBeenCalled();
      expect(avisos.exito).toHaveBeenCalledWith('Turno creado');
    });
  }

  it('no envía con carga pendiente o fallida y permite reintentar sin datos obsoletos', () => {
    fixture.detectChanges();
    const panel = fixture.componentInstance;
    horario(panel);
    panel.guardar();
    http.expectNone(`${API}/turnos/crear`);
    http.expectOne(`${API}/grupos/resumen`).flush({}, { status: 500, statusText: 'Error' });
    panel.guardar();
    expect(panel.errorCarga).toBeTruthy();
    http.expectNone(`${API}/turnos/crear`);
    panel.cargar();
    http.expectOne(`${API}/grupos/resumen`).flush(GRUPOS);
    expect(panel.cargando).toBeFalse();
    expect(panel.errorCarga).toBe('');
    expect(Swal.fire).not.toHaveBeenCalled();
  });

  it('muestra un solo error local real y permite repetir el alta sin cerrar', () => {
    const panel = iniciar();
    horario(panel);
    panel.guardar();
    http.expectOne(`${API}/turnos/crear`).flush({ mensaje: 'Horario no disponible' }, { status: 400, statusText: 'Error' });
    fixture.detectChanges();
    expect(panel.enviando).toBeFalse();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Horario no disponible');
    expect(Swal.fire).not.toHaveBeenCalled();
    expect(avisos.error).not.toHaveBeenCalled();
    panel.guardar();
    http.expectOne(`${API}/turnos/crear`).flush({ id: 9 });
  });

  for (const grupo of ['1', '2', '']) {
    it(`edita con PUT de tres campos y solo la operación de grupo necesaria (${grupo})`, () => {
      const panel = iniciar(true);
      expect(panel.form.valid).toBeTrue();
      expect(fixture.nativeElement.querySelector('#turno-tipo')).toBeNull();
      panel.form.controls.grupoId.setValue(grupo);
      fixture.detectChanges();
      expect(panel.avisoAlumnos).toBe(grupo === '1' ? '' : 'Este turno tiene 2 alumnos de Infantiles A. Seguirán apuntados.');
      panel.guardar();
      const req = http.expectOne(`${API}/turnos/7`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:30' });
      req.flush({});
      if (grupo !== '1') {
        const cambio = http.expectOne(`${API}/grupos/${grupo || '1'}/turnos/7`);
        expect(cambio.request.method).toBe(grupo ? 'POST' : 'DELETE');
        expect(cambio.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
        cambio.flush({});
      }
      http.expectNone((r) => r.url.includes('/alumnos'));
      expect(panel.enviando).toBeFalse();
    });
  }

  for (const alumnos of [undefined, null, []]) {
    it(`no inventa el número de alumnos si falta la lista (${JSON.stringify(alumnos)})`, () => {
      fixture.componentRef.setInput('turnoId', 7);
      fixture.detectChanges();
      http.expectOne(`${API}/grupos/resumen`).flush(GRUPOS);
      http.expectOne(`${API}/turnos/7`).flush({ ...TURNO, alumnos });
      fixture.componentInstance.form.controls.grupoId.setValue('2');
      expect(fixture.componentInstance.avisoAlumnos).toBe('');
    });
  }

  for (const grupo of ['2', '']) {
    it(`conserva éxito parcial y avisa al fallar la asignación (${grupo})`, () => {
      const panel = iniciar(true);
      const guardado = spyOn(panel.guardado, 'emit');
      panel.form.controls.grupoId.setValue(grupo);
      panel.guardar();
      http.expectOne(`${API}/turnos/7`).flush({});
      http.expectOne(`${API}/grupos/${grupo || '1'}/turnos/7`).flush({}, { status: 500, statusText: 'Error' });
      expect(avisos.error).toHaveBeenCalledWith(grupo ? 'Turno actualizado pero error al asignar grupo' : 'Turno actualizado pero error al desvincular grupo');
      expect(guardado).toHaveBeenCalled();
      expect(Swal.fire).not.toHaveBeenCalled();
    });
  }

  it('un fallo del PUT no cambia grupo ni cierra el panel', () => {
    const panel = iniciar(true);
    const guardado = spyOn(panel.guardado, 'emit');
    panel.form.controls.grupoId.setValue('2');
    panel.guardar();
    http.expectOne(`${API}/turnos/7`).flush({}, { status: 500, statusText: 'Error' });
    expect(panel.errorGuardar).toBe('No hemos podido actualizar el turno');
    expect(guardado).not.toHaveBeenCalled();
    http.expectNone(`${API}/grupos/2/turnos/7`);
  });

  describe('responsable de sede', () => {
    beforeEach(() => spyOn(TestBed.inject(PermisosSesionService), 'tiene').and.callFake((c: string) => c === 'PANEL_SEDE'));

    it('exige grupo al crear: sin «Sin grupo» y sin POST a /turnos/crear', () => {
      const panel = iniciar();
      expect(panel.exigeGrupo).toBeTrue();
      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('#turno-grupo option')?.textContent).toBe('Selecciona un grupo');
      horario(panel);
      panel.guardar();
      fixture.detectChanges();
      http.expectNone(`${API}/turnos/crear`);
      expect(host.querySelector('#turno-grupo-error')?.textContent).toContain('El grupo es obligatorio');
    });

    it('al editar no cambia nada', () => {
      const panel = iniciar(true);
      expect(panel.exigeGrupo).toBeFalse();
    });
  });
});
