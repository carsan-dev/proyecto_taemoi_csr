import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { EditarGrupoComponent } from './editar-grupo.component';

describe('EditarGrupoComponent · profesores', () => {
  let fixture: ComponentFixture<EditarGrupoComponent>;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/grupos/5/profesores`;
  const ana = { usuarioId: 1, nombre: 'Ana', apellidos: 'Ejemplo Ruiz', email: 'ana@ejemplo.test', rol: 'TITULAR' };
  const beto = { usuarioId: 2, nombre: 'Beto', apellidos: 'Muestra Gil', email: 'beto@ejemplo.test', rol: 'AYUDANTE' };

  function montar(gestor = true): void {
    TestBed.configureTestingModule({
      imports: [EditarGrupoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { params: of({ id: '5' }) } },
        { provide: Location, useValue: { back: () => {} } },
        { provide: AuthenticationService, useValue: { tieneAccesoAdmin: () => gestor } },
        { provide: EndpointsService, useValue: { obtenerGrupoPorId: () => of({ id: 5, nombre: 'Infantil' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EditarGrupoComponent);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function escribirEmail(valor: string): Promise<void> {
    await fixture.whenStable(); // ngModel registra su control tras el primer ciclo
    const input = el().querySelector<HTMLInputElement>('#emailProfesor')!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function enviarAlta(): void {
    el().querySelector<HTMLFormElement>('form.profesor-alta')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
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

  it('añade con éxito: envía email y rol, refresca la lista y vacía el campo', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    await escribirEmail('  beto@ejemplo.test ');
    enviarAlta();
    const alta = http.expectOne((r) => r.method === 'POST' && r.url === url);
    expect(alta.request.body).toEqual({ email: 'beto@ejemplo.test', rol: 'TITULAR' });
    alta.flush(beto);
    http.expectOne(url).flush([beto]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(el().querySelector<HTMLInputElement>('#emailProfesor')!.value).toBe('');
    expect(el().querySelectorAll('.profesor-fila').length).toBe(1);
  });

  it('deshabilita Añadir mientras va la petición', async () => {
    montar();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    const boton = el().querySelector<HTMLButtonElement>('.profesor-alta-boton')!;
    expect(boton.disabled).toBeTrue();
    await escribirEmail('beto@ejemplo.test');
    expect(boton.disabled).toBeFalse();
    enviarAlta();
    expect(boton.disabled).toBeTrue();
    http.expectOne((r) => r.method === 'POST').flush(beto);
    http.expectOne(url).flush([beto]);
  });

  it('un 404 muestra el mensaje del backend y conserva el email', async () => {
    montar();
    http.expectOne(url).flush([]);
    await escribirEmail('nadie@ejemplo.test');
    enviarAlta();
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ mensaje: 'No hay ninguna cuenta con ese email. Que se registre primero.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-error')!.textContent).toContain('No hay ninguna cuenta con ese email. Que se registre primero.');
    expect(el().querySelector<HTMLInputElement>('#emailProfesor')!.value).toBe('nadie@ejemplo.test');
  });

  it('un 409 muestra el mensaje del backend', async () => {
    montar();
    http.expectOne(url).flush([]);
    await escribirEmail('ana@ejemplo.test');
    enviarAlta();
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ mensaje: 'La cuenta tiene el rol de profesor desactivado en esta escuela' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el().querySelector('.profesor-error')!.textContent).toContain('rol de profesor desactivado');
  });

  it('cambiar el rol envía POST con el mismo email y el rol nuevo', () => {
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

  it('quitar pide confirmación y envía DELETE', async () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    const fire = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as never);
    el().querySelector<HTMLButtonElement>('.profesor-acciones button')!.click();
    await fixture.whenStable();
    const opciones = fire.calls.mostRecent().args[0] as unknown as { title: string; text: string };
    expect(opciones.title).toBe('¿Quitar a Ana de este grupo?');
    expect(opciones.text).toBe('Seguirá teniendo el rol de profesor en la escuela.');
    http.expectOne({ method: 'DELETE', url: `${url}/1` }).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(url).flush([]);
  });

  it('quitar cancelado no envía nada', async () => {
    montar();
    http.expectOne(url).flush([ana]);
    fixture.detectChanges();
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as never);
    el().querySelector<HTMLButtonElement>('.profesor-acciones button')!.click();
    await fixture.whenStable();
    http.expectNone({ method: 'DELETE', url: `${url}/1` });
  });
});
