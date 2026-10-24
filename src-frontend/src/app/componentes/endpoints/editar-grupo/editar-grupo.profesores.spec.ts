import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Observable, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Toast } from '../../../utils/toast.util';
import { EditarGrupoComponent } from './editar-grupo.component';
import { itSinReloj } from '../../../testing/reloj-prueba';

describe('EditarGrupoComponent · profesores', () => {
  let fixture: ComponentFixture<EditarGrupoComponent>;
  let http: HttpTestingController;
  let toast: jasmine.Spy;
  const url = `${environment.apiUrl}/grupos/5/profesores`;
  const urlMiembros = `${environment.apiUrl}/miembros`;
  const ana = { usuarioId: 1, nombre: 'Ana', apellidos: 'Ejemplo Ruiz', email: 'ana@ejemplo.test', rol: 'TITULAR', invitacionPendiente: false };
  const beto = { usuarioId: 2, nombre: 'Beto', apellidos: 'Muestra Gil', email: 'beto@ejemplo.test', rol: 'AYUDANTE', invitacionPendiente: false };
  const lucia = { usuarioId: 3, nombre: 'Lucía', apellidos: 'Prueba Sanz', email: 'lucia@ejemplo.test', rol: 'AYUDANTE', invitacionPendiente: true };
  const gruposDeLaEscuela = [
    { id: 5, nombre: 'Infantil', numAlumnos: 0, turnos: [] },
    { id: 7, nombre: 'Adultos', numAlumnos: 0, turnos: [] },
    { id: 8, nombre: 'Competición', numAlumnos: 0, turnos: [] },
  ];
  const ficha = { usuarioId: 2, invitacionPendiente: false };

  function montar(gestor = true, grupos: Observable<unknown> = of(gruposDeLaEscuela)): void {
    TestBed.configureTestingModule({
      imports: [EditarGrupoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { params: of({ id: '5' }) } },
        { provide: Location, useValue: { back: () => {} } },
        { provide: AuthenticationService, useValue: { tieneAccesoAdmin: () => gestor } },
        {
          provide: EndpointsService,
          useValue: {
            obtenerGrupoPorId: () => of({ id: 5, nombre: 'Infantil' }),
            obtenerResumenGrupos: () => grupos,
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = spyOn(Toast, 'fire').and.resolveTo({} as never);
    fixture = TestBed.createComponent(EditarGrupoComponent);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function escribir(selector: string, valor: string): Promise<void> {
    await fixture.whenStable(); // ngModel registra su control tras el primer ciclo
    const input = el().querySelector<HTMLInputElement>(selector)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function escribirEmail(valor: string): Promise<void> {
    return escribir('#emailProfesor', valor);
  }

  function enviarAlta(): void {
    el().querySelector<HTMLFormElement>('form.profesor-alta')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function chips(): HTMLButtonElement[] {
    return Array.from(el().querySelectorAll<HTMLButtonElement>('.grupo-chip'));
  }

  function boton(): HTMLButtonElement {
    return el().querySelector<HTMLButtonElement>('.profesor-alta-boton')!;
  }

  function altaMiembros() {
    return http.expectOne((r) => r.method === 'POST' && r.url === urlMiembros);
  }

  afterEach(() => http.verify());

  it('lista los profesores con nombre, email y rol', () => {
    montar();
    http.expectOne(url).flush([ana, beto]);
    fixture.detectChanges();
    const filas = el().querySelectorAll('.profesor-fila');
    expect(filas.length).toBe(2);
    expect(filas[0].textContent).toContain('Ana Ejemplo Ruiz');
    expect(filas[0].textContent).toContain('ana@ejemplo.test');
    expect(filas[1].querySelector<HTMLSelectElement>('select')!.value).toBe('AYUDANTE');
  });

  it('muestra el vacío cuando el grupo no tiene profesores', () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(el().querySelector('.profesores-vacio')!.textContent).toContain('Este grupo aún no tiene profesores.');
  });

  it('un fallo al listar avisa en la sección sin tocar el formulario del grupo', () => {
    montar();
    http.expectOne(url).flush({ mensaje: 'boom' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().querySelector('.profesores-aviso')!.textContent).toContain('boom');
    expect(el().querySelector('#nombre')).not.toBeNull();
  });

  it('no muestra la sección ni pide nada a quien no es de gestión', () => {
    montar(false);
    http.expectNone(url);
    expect(el().querySelector('#seccionProfesores')).toBeNull();
  });

  itSinReloj('añade con éxito: un POST a /api/miembros con este grupo, refresca la lista, vacía el campo y avisa', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    await escribirEmail('  beto@ejemplo.test ');
    expect(boton().textContent!.trim()).toBe('Añadir');
    enviarAlta();
    const alta = altaMiembros();
    expect(alta.request.withCredentials).toBeTrue();
    expect(alta.request.body).toEqual({ email: 'beto@ejemplo.test', rol: 'PROFESOR', grupos: [{ grupoId: 5, rol: 'TITULAR' }] });
    alta.flush(ficha);
    http.expectOne(url).flush([beto]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(el().querySelector<HTMLInputElement>('#emailProfesor')!.value).toBe('');
    expect(el().querySelectorAll('.profesor-fila').length).toBe(1);
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'success', title: 'Profesor añadido' }));
  });

  it('los chips son los demás grupos de la escuela, sin el actual, y se marcan con aria-pressed', () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(el().querySelector('fieldset.grupos-chips legend')!.textContent).toContain('También en estos grupos');
    expect(chips().map((c) => c.textContent!.trim())).toEqual(['Adultos', 'Competición']);
    expect(chips().every((c) => c.type === 'button' && c.getAttribute('aria-pressed') === 'false')).toBeTrue();
    chips()[1].click();
    fixture.detectChanges();
    expect(chips()[1].getAttribute('aria-pressed')).toBe('true');
    expect(boton().textContent!.trim()).toBe('Añadir a 2 grupos');
    chips()[1].click();
    fixture.detectChanges();
    expect(chips()[1].getAttribute('aria-pressed')).toBe('false');
    expect(boton().textContent!.trim()).toBe('Añadir');
  });

  it('sin otros grupos o con la lista fallida, no sale la fila de chips', () => {
    montar(true, throwError(() => new Error('boom')));
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(el().querySelector('fieldset.grupos-chips')).toBeNull();
  });

  itSinReloj('«Añadir a 3 grupos» manda una sola petición con los tres grupos y el mismo rol', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    await escribirEmail('beto@ejemplo.test');
    const rol = el().querySelector<HTMLSelectElement>('#rolProfesor')!;
    rol.value = 'AYUDANTE';
    rol.dispatchEvent(new Event('change'));
    chips().forEach((c) => c.click());
    fixture.detectChanges();
    expect(boton().textContent!.trim()).toBe('Añadir a 3 grupos');
    enviarAlta();
    const alta = altaMiembros();
    expect(alta.request.body).toEqual({
      email: 'beto@ejemplo.test',
      rol: 'PROFESOR',
      grupos: [
        { grupoId: 5, rol: 'AYUDANTE' },
        { grupoId: 7, rol: 'AYUDANTE' },
        { grupoId: 8, rol: 'AYUDANTE' },
      ],
    });
    alta.flush(ficha);
    http.expectOne(url).flush([beto]);
    fixture.detectChanges();
    expect(chips().every((c) => c.getAttribute('aria-pressed') === 'false')).toBeTrue();
    expect(boton().textContent!.trim()).toBe('Añadir');
  });

  itSinReloj('deshabilita el botón mientras va la petición y un doble envío manda una sola', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(boton().disabled).toBeTrue();
    await escribirEmail('beto@ejemplo.test');
    expect(boton().disabled).toBeFalse();
    enviarAlta();
    enviarAlta();
    expect(boton().disabled).toBeTrue();
    altaMiembros().flush(ficha);
    http.expectOne(url).flush([beto]);
  });

  itSinReloj('404 sin cuenta: pide nombre y apellidos y «Enviar invitación» los manda', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    await escribirEmail('nueva@ejemplo.test');
    chips()[0].click();
    fixture.detectChanges();
    enviarAlta();
    altaMiembros().flush({ mensaje: 'No hay ninguna cuenta con ese email.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-alta-aviso')!.textContent).toContain(
      'No hay ninguna cuenta con ese email. Te pedimos su nombre para invitarle.'
    );
    expect(el().querySelector('.profesor-error')).toBeNull();
    expect(boton().textContent!.trim()).toBe('Enviar invitación');
    expect(boton().disabled).toBeTrue();
    await escribir('#nombreProfesor', ' Lucía ');
    await escribir('#apellidosProfesor', 'Prueba Sanz');
    expect(boton().disabled).toBeFalse();
    enviarAlta();
    const invitacion = altaMiembros();
    expect(invitacion.request.body).toEqual({
      email: 'nueva@ejemplo.test',
      rol: 'PROFESOR',
      grupos: [
        { grupoId: 5, rol: 'TITULAR' },
        { grupoId: 7, rol: 'TITULAR' },
      ],
      nombre: 'Lucía',
      apellidos: 'Prueba Sanz',
    });
    invitacion.flush({ usuarioId: 3, invitacionPendiente: true });
    http.expectOne(url).flush([lucia]);
    fixture.detectChanges();
    expect(el().querySelector('.profesor-alta-aviso')).toBeNull();
    expect(el().querySelector('#nombreProfesor')).toBeNull();
    expect(el().querySelector('.profesor-fila .profesor-pendiente')!.textContent).toContain('Invitación pendiente');
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Invitación enviada' }));
  });

  itSinReloj('cambiar el email tras el 404 vuelve al alta normal', async () => {
    montar();
    http.expectOne(url).flush([]);
    await escribirEmail('nueva@ejemplo.test');
    enviarAlta();
    altaMiembros().flush({ mensaje: 'No hay ninguna cuenta con ese email.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await escribirEmail('otra@ejemplo.test');
    expect(el().querySelector('.profesor-alta-aviso')).toBeNull();
    expect(boton().textContent!.trim()).toBe('Añadir');
  });

  itSinReloj('409 de rol desactivado: ofrece «Reactivar y añadir» y manda reactivar: true', async () => {
    montar();
    http.expectOne(url).flush([]);
    await escribirEmail('ana@ejemplo.test');
    enviarAlta();
    altaMiembros().flush(
      { mensaje: 'La cuenta tiene el rol de profesor desactivado en esta escuela.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();
    expect(el().querySelector('.profesor-alta-aviso')!.textContent).toContain('Tiene el rol de profesor desactivado en esta escuela.');
    expect(boton().textContent!.trim()).toBe('Reactivar y añadir');
    enviarAlta();
    const reactivar = altaMiembros();
    expect(reactivar.request.body).toEqual({
      email: 'ana@ejemplo.test',
      rol: 'PROFESOR',
      grupos: [{ grupoId: 5, rol: 'TITULAR' }],
      reactivar: true,
    });
    reactivar.flush(ficha);
    http.expectOne(url).flush([ana]);
  });

  itSinReloj('otros 404 y 409 muestran el mensaje del backend y conservan el email', async () => {
    montar();
    http.expectOne(url).flush([]);
    await escribirEmail('ana@ejemplo.test');
    enviarAlta();
    altaMiembros().flush({ mensaje: 'El grupo no existe.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-error')!.textContent).toContain('El grupo no existe.');
    expect(el().querySelector('.profesor-alta-aviso')).toBeNull();
    expect(el().querySelector<HTMLInputElement>('#emailProfesor')!.value).toBe('ana@ejemplo.test');
    enviarAlta();
    altaMiembros().flush({ mensaje: 'Otro conflicto.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-error')!.textContent).toContain('Otro conflicto.');
  });

  it('la fila pendiente lleva el chip y «Reenviar», que reenvía y avisa', () => {
    montar();
    http.expectOne(url).flush([ana, lucia]);
    fixture.detectChanges();
    const filas = el().querySelectorAll('.profesor-fila');
    expect(filas[0].querySelector('.profesor-pendiente')).toBeNull();
    expect(filas[0].querySelector('.profesor-reenviar')).toBeNull();
    expect(filas[1].querySelector('.profesor-pendiente')!.textContent).toContain('Invitación pendiente');
    filas[1].querySelector<HTMLButtonElement>('.profesor-reenviar')!.click();
    fixture.detectChanges();
    expect(filas[1].querySelector<HTMLButtonElement>('.profesor-reenviar')!.disabled).toBeTrue();
    const reenvio = http.expectOne({ method: 'POST', url: `${urlMiembros}/3/invitacion` });
    expect(reenvio.request.withCredentials).toBeTrue();
    reenvio.flush({ usuarioId: 3, invitacionPendiente: true });
    fixture.detectChanges();
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'success', title: 'Invitación reenviada' }));
    expect(filas[1].querySelector<HTMLButtonElement>('.profesor-reenviar')!.disabled).toBeFalse();
  });

  it('un reenvío fallido muestra el mensaje del backend', () => {
    montar();
    http.expectOne(url).flush([lucia]);
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('.profesor-reenviar')!.click();
    http
      .expectOne({ method: 'POST', url: `${urlMiembros}/3/invitacion` })
      .flush({ mensaje: 'La invitación ya se aceptó.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-error')!.textContent).toContain('La invitación ya se aceptó.');
  });

  it('cambiar el rol envía POST a la ruta del grupo con el mismo email y el rol nuevo', () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    const selector = el().querySelector<HTMLSelectElement>('.profesor-rol')!;
    selector.value = 'AYUDANTE';
    selector.dispatchEvent(new Event('change'));
    const req = http.expectOne((r) => r.method === 'POST' && r.url === url);
    expect(req.request.body).toEqual({ email: 'ana@ejemplo.test', rol: 'AYUDANTE' });
    req.flush({ ...ana, rol: 'AYUDANTE' });
    http.expectOne(url).flush([{ ...ana, rol: 'AYUDANTE' }]);
  });

  it('cambio de rol con 409: tras la recarga el select vuelve al rol real y se muestra el mensaje', () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    const selector = el().querySelector<HTMLSelectElement>('.profesor-rol')!;
    selector.value = 'AYUDANTE';
    selector.dispatchEvent(new Event('change'));
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ mensaje: 'La cuenta tiene el rol de profesor desactivado en esta escuela' }, { status: 409, statusText: 'Conflict' });
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    expect(el().querySelector<HTMLSelectElement>('.profesor-rol')!.value).toBe('TITULAR');
    expect(el().querySelector('.profesor-error')!.textContent).toContain('desactivado');
  });

  it('una respuesta vieja de la lista no pisa la nueva', () => {
    montar();
    const vieja = http.expectOne(url);
    fixture.componentInstance.cargarProfesores();
    expect(vieja.cancelled).toBeTrue();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.profesor-fila').length).toBe(1);
  });

  itSinReloj('quitar pide confirmación y envía DELETE', async () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    const fire = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as never);
    el().querySelector<HTMLButtonElement>('.profesor-quitar')!.click();
    await fixture.whenStable();
    const opciones = fire.calls.mostRecent().args[0] as unknown as { title: string; text: string };
    expect(opciones.title).toBe('¿Quitar a Ana de este grupo?');
    expect(opciones.text).toBe('Seguirá teniendo el rol de profesor en la escuela.');
    http.expectOne({ method: 'DELETE', url: `${url}/1` }).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(url).flush([]);
  });

  itSinReloj('quitar cancelado no envía nada', async () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as never);
    el().querySelector<HTMLButtonElement>('.profesor-quitar')!.click();
    await fixture.whenStable();
    http.expectNone({ method: 'DELETE', url: `${url}/1` });
  });
});
