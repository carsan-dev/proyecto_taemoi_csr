import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { EditarGrupoComponent } from './editar-grupo.component';

const profesor = { usuarioId: 9, nombre: 'Marta', apellidos: 'Gil', email: 'marta@ejemplo.com', rol: 'TITULAR' as const, invitacionPendiente: true };

describe('Editar grupo (3m-2)', () => {
  let fixture: ComponentFixture<EditarGrupoComponent>;
  let component: EditarGrupoComponent;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmacion: jasmine.SpyObj<ConfirmacionService>;
  let params: BehaviorSubject<{ id: string }>;
  const api = environment.apiUrl;
  beforeEach(() => {
    params = new BehaviorSubject({ id: '7' });
    avisos = jasmine.createSpyObj('AvisosService', ['exito', 'error']);
    confirmacion = jasmine.createSpyObj('ConfirmacionService', ['confirmar']);
    TestBed.configureTestingModule({ imports: [EditarGrupoComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: ActivatedRoute, useValue: { params } },
      { provide: AvisosService, useValue: avisos }, { provide: ConfirmacionService, useValue: confirmacion },
    ] });
    fixture = TestBed.createComponent(EditarGrupoComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function cargar(): void {
    http.expectOne(`${api}/grupos/7`).flush({ id: 7, nombre: 'Infantiles A', rangoEdadMin: 6, rangoEdadMax: 12, alumnos: [] });
    http.expectOne(`${api}/grupos/7/profesores`).flush([profesor]);
    http.expectOne(`${api}/grupos/resumen`).flush([
      { id: 7, nombre: 'Infantiles A', numAlumnos: 18, turnos: [{ id: 1 }, { id: 2 }] },
      { id: 8, nombre: 'Adultos', numAlumnos: 2, turnos: [] },
    ]);
    fixture.detectChanges();
  }
  it('carga con esqueleto, reutiliza el resumen y enlaza alumnos y listado', () => {
    expect(fixture.nativeElement.querySelector('pn-esqueleto')).not.toBeNull();
    cargar();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Infantiles A');
    expect(fixture.nativeElement.textContent).toContain('18 alumnos · 2 turnos');
    expect(fixture.nativeElement.querySelector('[data-ver-alumnos]').getAttribute('href')).toBe('/gestionarAlumnos/7');
    expect(fixture.nativeElement.querySelector('nav a').getAttribute('href')).toBe('/gruposListar');
  });
  it('permite reintentar la carga fallida sin inventar cifras', () => {
    http.expectOne(`${api}/grupos/7`).flush({}, { status: 500, statusText: 'Error' });
    http.expectOne(`${api}/grupos/7/profesores`).flush([]);
    http.expectOne(`${api}/grupos/resumen`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('pn-bloque-error')).not.toBeNull();
    component.cargarGrupo();
    http.expectOne(`${api}/grupos/7`).flush({ id: 7, nombre: 'Grupo', alumnos: [] });
    fixture.detectChanges();
    expect(component.resumen).toBe('0 alumnos');
  });
  it('guarda exactamente nombre y rango oculto, permanece y muestra éxito', () => {
    cargar();
    component.nombre.setValue('Infantiles B');
    component.guardar();
    const req = http.expectOne(`${api}/grupos/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(req.request.body).toEqual({ nombre: 'Infantiles B', rangoEdadMin: 6, rangoEdadMax: 12 });
    req.flush({ nombre: 'Infantiles B' });
    expect(avisos.exito).toHaveBeenCalledWith('El grupo se ha actualizado correctamente');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Infantiles B');
  });
  it('valida obligatorio y máximo 50 y conserva el contador', () => {
    cargar(); component.nombre.setValue(''); component.nombre.markAsTouched(); component.guardar();
    expect(component.errorNombre).toBe('El nombre del grupo es obligatorio');
    component.nombre.setValue('a'.repeat(51)); component.guardar();
    expect(component.errorNombre).toBe('Máximo 50 caracteres');
    http.expectNone(`${api}/grupos/7`);
    component.nombre.setValue('a'.repeat(50)); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('50 / 50');
  });
  it('muestra el texto real del servidor bajo Datos del grupo', () => {
    cargar(); component.guardar();
    http.expectOne(`${api}/grupos/7`).flush({ mensaje: 'El nombre ya está utilizado' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#guardar-error').textContent).toContain('El nombre ya está utilizado');
  });
  it('quitar pide diálogo normal con el texto exacto y avisa de éxito', async () => {
    cargar(); confirmacion.confirmar.and.resolveTo({ confirmado: true, valor: null });
    await component.quitarProfesor(profesor);
    expect(confirmacion.confirmar).toHaveBeenCalledWith(jasmine.objectContaining({
      titulo: '¿Quitar a Marta Gil de este grupo?', mensaje: 'Seguirá teniendo el rol de profesor en la escuela.', accion: 'Quitar', destructivo: false,
    }));
    http.expectOne(`${api}/grupos/7/profesores/9`).flush(null);
    http.expectOne(`${api}/grupos/7/profesores`).flush([]);
    expect(avisos.exito).toHaveBeenCalledWith('Profesor quitado del grupo');
  });
  it('cancelar quitar no hace la petición', async () => {
    cargar(); confirmacion.confirmar.and.resolveTo({ confirmado: false, valor: null });
    await component.quitarProfesor(profesor);
    http.expectNone(`${api}/grupos/7/profesores/9`);
  });
  it('cambia rol con la llamada de hoy y no bloquea reenviar ni guardar', () => {
    cargar(); component.cambiarRol(profesor, 'AYUDANTE');
    const req = http.expectOne(`${api}/grupos/7/profesores`);
    expect(req.request.body).toEqual({ email: profesor.email, rol: 'AYUDANTE' });
    component.reenviarInvitacion(profesor);
    http.expectOne(`${api}/miembros/9/invitacion`).flush({});
    component.guardar(); http.expectOne(`${api}/grupos/7`).flush({});
    req.flush({ ...profesor, rol: 'AYUDANTE' });
    http.expectOne(`${api}/grupos/7/profesores`).flush([{ ...profesor, rol: 'AYUDANTE' }]);
  });
  it('un error de rol mantiene el rol real y muestra el mensaje', () => {
    cargar(); component.cambiarRol(profesor, 'AYUDANTE');
    http.expectOne(`${api}/grupos/7/profesores`).flush({ mensaje: 'No permitido' }, { status: 403, statusText: 'Forbidden' });
    expect(component.profesores[0].rol).toBe('TITULAR'); expect(component.errorAlta).toBe('No permitido');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#profesores-error[role="alert"]').textContent).toContain('No permitido');
  });
  it('blocks removal of the same professor during a role change', async () => {
    cargar(); component.cambiarRol(profesor, 'AYUDANTE'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.editar-grupo__mas').disabled).toBeTrue();
    await component.quitarProfesor(profesor);
    expect(confirmacion.confirmar).not.toHaveBeenCalled();
    http.expectNone(`${api}/grupos/7/profesores/9`);
    http.expectOne(`${api}/grupos/7/profesores`).flush({ ...profesor, rol: 'AYUDANTE' });
    http.expectOne(`${api}/grupos/7/profesores`).flush([{ ...profesor, rol: 'AYUDANTE' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.editar-grupo__mas').disabled).toBeFalse();
  });
  it('blocks role changes during removal confirmation and the pending DELETE', async () => {
    cargar();
    let resolver!: (resultado: { confirmado: boolean; valor: null }) => void;
    confirmacion.confirmar.and.returnValue(new Promise((resolve) => { resolver = resolve; }));
    const quitar = component.quitarProfesor(profesor);
    fixture.detectChanges();
    const roles = fixture.nativeElement.querySelectorAll('.editar-grupo__profesor .editar-grupo__segmento button') as NodeListOf<HTMLButtonElement>;
    expect(roles.length).toBe(2);
    expect(Array.from(roles).every((boton) => boton.disabled)).toBeTrue();
    component.cambiarRol(profesor, 'AYUDANTE');
    http.expectNone(`${api}/grupos/7/profesores`);
    resolver({ confirmado: true, valor: null }); await quitar;
    component.cambiarRol(profesor, 'AYUDANTE');
    http.expectNone(`${api}/grupos/7/profesores`);
    http.expectOne(`${api}/grupos/7/profesores/9`).flush(null);
    http.expectOne(`${api}/grupos/7/profesores`).flush([]);
  });
  it('releases the role controls when removal is cancelled', async () => {
    cargar(); confirmacion.confirmar.and.resolveTo({ confirmado: false, valor: null });
    await component.quitarProfesor(profesor);
    fixture.detectChanges();
    const roles = fixture.nativeElement.querySelectorAll('.editar-grupo__profesor .editar-grupo__segmento button') as NodeListOf<HTMLButtonElement>;
    expect(roles.length).toBe(2);
    expect(Array.from(roles).every((boton) => !boton.disabled)).toBeTrue();
    component.cambiarRol(profesor, 'AYUDANTE');
    http.expectOne(`${api}/grupos/7/profesores`).flush({ ...profesor, rol: 'AYUDANTE' });
    http.expectOne(`${api}/grupos/7/profesores`).flush([{ ...profesor, rol: 'AYUDANTE' }]);
  });
  it('reenvía invitación sin cuerpo y con aviso', () => {
    cargar(); component.reenviarInvitacion(profesor);
    const req = http.expectOne(`${api}/miembros/9/invitacion`);
    expect(req.request.method).toBe('POST'); expect(req.request.body).toBeNull(); req.flush({});
    expect(avisos.exito).toHaveBeenCalledWith('Invitación reenviada');
  });
  it('añade al grupo y a los marcados con texto variable', () => {
    cargar(); component.nuevoEmail = ' nuevo@ejemplo.com '; expect(component.textoBotonAlta).toBe('Añadir');
    component.alternarGrupo(8); expect(component.textoBotonAlta).toBe('Añadir a 2 grupos');
    component.anadirProfesor(); const req = http.expectOne(`${api}/miembros`);
    expect(req.request.body).toEqual({ email: 'nuevo@ejemplo.com', rol: 'PROFESOR', grupos: [{ grupoId: 7, rol: 'TITULAR' }, { grupoId: 8, rol: 'TITULAR' }] });
    req.flush({}); http.expectOne(`${api}/grupos/7/profesores`).flush([]);
    expect(avisos.exito).toHaveBeenCalledWith('Profesor añadido');
  });
  it('sin cuenta muestra aviso, pide nombre y apellidos e invita', () => {
    cargar(); component.nuevoEmail = 'nuevo@ejemplo.com'; component.anadirProfesor();
    http.expectOne(`${api}/miembros`).flush({ mensaje: 'No hay ninguna cuenta con ese email' }, { status: 404, statusText: 'Not Found' });
    expect(component.textoBotonAlta).toBe('Enviar invitación'); expect(component.puedeEnviarAlta).toBeFalse();
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('No hay ninguna cuenta con ese email. Te pedimos su nombre para invitarle.');
    component.nuevoNombre = ' Ana '; component.nuevosApellidos = ' Gil '; component.anadirProfesor();
    const req = http.expectOne(`${api}/miembros`); expect(req.request.body.nombre).toBe('Ana'); expect(req.request.body.apellidos).toBe('Gil');
    req.flush({}); http.expectOne(`${api}/grupos/7/profesores`).flush([]);
    expect(avisos.exito).toHaveBeenCalledWith('Invitación enviada');
  });
  it('reactiva y añade; cambiar email reinicia el modo', () => {
    cargar(); component.nuevoEmail = 'nuevo@ejemplo.com'; component.anadirProfesor();
    http.expectOne(`${api}/miembros`).flush({ mensaje: 'Tiene el rol de profesor desactivado en esta escuela.' }, { status: 409, statusText: 'Conflict' });
    expect(component.textoBotonAlta).toBe('Reactivar y añadir'); component.anadirProfesor();
    const req = http.expectOne(`${api}/miembros`); expect(req.request.body.reactivar).toBeTrue(); req.flush({});
    http.expectOne(`${api}/grupos/7/profesores`).flush([]);
    component.modoAlta = 'sinCuenta'; component.nuevoNombre = 'Ana'; component.emailCambiado();
    expect(component.modoAlta).toBe('normal'); expect(component.nuevoNombre).toBe('');
  });
  it('cambia de grupo sin mostrar profesores antiguos y cancela las acciones anteriores', () => {
    cargar();
    component.cambiarRol(profesor, 'AYUDANTE');
    const antigua = http.expectOne(api + '/grupos/7/profesores');
    component.nuevoEmail = 'anterior@ejemplo.com'; component.anadirProfesor();
    const altaAnterior = http.expectOne(api + '/miembros');
    params.next({ id: '8' }); fixture.detectChanges();
    expect(antigua.cancelled).toBeTrue(); expect(altaAnterior.cancelled).toBeTrue();
    expect(component.profesores).toEqual([]); expect(component.nuevoEmail).toBe('');
    expect(fixture.nativeElement.querySelector('pn-esqueleto')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('marta@ejemplo.com');
    http.expectOne(api + '/grupos/8').flush({ id: 8, nombre: 'Adultos', alumnos: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.editar-grupo__profesor')).toBeNull();
    http.expectOne(api + '/grupos/8/profesores').flush([{ ...profesor, usuarioId: 10, email: 'actual@ejemplo.com' }]);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('actual@ejemplo.com');
  });
  it('una confirmacion del grupo anterior no quita profesores del nuevo', async () => {
    cargar();
    let resolver!: (resultado: { confirmado: boolean; valor: null }) => void;
    confirmacion.confirmar.and.returnValue(new Promise((resolve) => { resolver = resolve; }));
    const quitar = component.quitarProfesor(profesor);
    params.next({ id: '8' });
    http.expectOne(api + '/grupos/8').flush({ id: 8, nombre: 'Adultos', alumnos: [] });
    http.expectOne(api + '/grupos/8/profesores').flush([]);
    resolver({ confirmado: true, valor: null }); await quitar;
    http.expectNone(api + '/grupos/8/profesores/9');
    http.expectNone(api + '/grupos/7/profesores/9');
  });
  it('bloquea solo los campos del alta durante su peticion y conserva operativos nombre y profesores', () => {
    cargar(); component.nuevoEmail = 'nuevo@ejemplo.com'; component.anadirProfesor(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#profesor-email').readOnly).toBeTrue();
    expect(fixture.nativeElement.querySelector('#grupo-nombre').readOnly).toBeFalse();
    expect(fixture.nativeElement.querySelector('.editar-grupo__rol-alta button').disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('.editar-grupo__pastilla').disabled).toBeTrue();
    const reenviar = fixture.nativeElement.querySelector('.editar-grupo__invitacion button') as HTMLButtonElement;
    expect(reenviar.disabled).toBeFalse();
    component.alternarGrupo(8); expect(component.gruposMarcados.size).toBe(0);
    http.expectOne(api + '/miembros').flush({ mensaje: 'No hay ninguna cuenta con ese email' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#profesor-email').readOnly).toBeFalse();
    component.nuevoNombre = 'Ana'; component.nuevosApellidos = 'Gil'; component.anadirProfesor(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#profesor-nombre').readOnly).toBeTrue();
    expect(fixture.nativeElement.querySelector('#profesor-apellidos').readOnly).toBeTrue();
    http.expectOne(api + '/miembros').flush({}); http.expectOne(api + '/grupos/7/profesores').flush([]);
  });

});
