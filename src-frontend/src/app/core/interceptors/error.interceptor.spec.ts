/// <reference types="node" />
import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import Swal from 'sweetalert2';
import { errorInterceptor } from './error.interceptor';
import { ERROR_HANDLED_LOCALLY } from '../http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';

describe('Errores de informes parciales', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()] });
    spyOn(Swal, 'fire');
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('devuelve el fallo al informe sin abrir un segundo modal global', () => {
    const error = jasmine.createSpy('error');
    TestBed.inject(HttpClient).get('/informe', { context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) }).subscribe({ error });
    TestBed.inject(HttpTestingController).expectOne('/informe').flush({}, { status: 500, statusText: 'Error' });
    expect(error).toHaveBeenCalled(); expect(Swal.fire).not.toHaveBeenCalled();
  });
  it('mantiene aviso global en peticiones sin tratamiento local', () => {
    TestBed.inject(HttpClient).get('/otra').subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController).expectOne('/otra').flush({}, { status: 500, statusText: 'Error' });
    expect(Swal.fire).toHaveBeenCalled();
  });
  it('con ERROR_HANDLED_LOCALLY, un 401 de sesión caducada sigue cerrando la sesión y llevando al login (8b-D2a)', async () => {
    const forzarCierreSesion = jasmine.createSpy('forzarCierreSesion').and.resolveTo();
    TestBed.overrideProvider(AuthenticationService, {
      useValue: { comprobarLogueado: () => true, rolesEstanCargados: () => true, forzarCierreSesion },
    });
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.resolveTo(true);
    (Swal.fire as jasmine.Spy).and.resolveTo({ isConfirmed: true });
    const error = jasmine.createSpy('error');
    TestBed.inject(HttpClient)
      .get('/api/alumnos', { context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) })
      .subscribe({ error });
    TestBed.inject(HttpTestingController).expectOne('/api/alumnos').flush({}, { status: 401, statusText: 'Unauthorized' });
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    expect(error).toHaveBeenCalled();
    expect(forzarCierreSesion).toHaveBeenCalled();
    expect(Swal.fire).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Sesión Expirada' }));
    expect(navegar).toHaveBeenCalledWith(['/login']);
  });
  it('429: aviso fijo «Demasiadas peticiones» sin mostrar el HTML de nginx', () => {
    TestBed.inject(HttpClient).get('/api/otra').subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController).expectOne('/api/otra')
      .flush('<html><body>429 Too Many Requests nginx</body></html>', { status: 429, statusText: 'Too Many Requests' });
    expect(Swal.fire).toHaveBeenCalledWith(jasmine.objectContaining({
      title: 'Demasiadas peticiones',
      text: 'Has hecho muchas peticiones seguidas. Espera unos segundos y vuelve a intentarlo.',
    }));
  });
  it('429 en /api/auth/ no abre aviso y devuelve el error (el servicio lo reintenta)', () => {
    const error = jasmine.createSpy('error');
    TestBed.inject(HttpClient).get('/api/auth/roles').subscribe({ error });
    TestBed.inject(HttpTestingController).expectOne('/api/auth/roles').flush('<html></html>', { status: 429, statusText: 'Too Many Requests' });
    expect(error).toHaveBeenCalled(); expect(Swal.fire).not.toHaveBeenCalled();
  });
  it('su SweetAlert2 lleva la clase «swal-error-http» (sin estilos): el panel nuevo cierra solo ese (8b-D1)', () => {
    TestBed.inject(HttpClient).get('/otra').subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController).expectOne('/otra').flush({}, { status: 500, statusText: 'Error' });
    expect(Swal.fire).toHaveBeenCalledWith({
      title: 'Error del Servidor',
      text: jasmine.any(String),
      icon: 'error',
      confirmButtonText: 'Aceptar',
      customClass: { popup: 'swal-error-http' },
    } as never);
  });
});
