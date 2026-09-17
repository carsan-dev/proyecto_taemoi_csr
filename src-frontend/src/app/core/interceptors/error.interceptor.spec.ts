/// <reference types="node" />
import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import Swal from 'sweetalert2';
import { errorInterceptor } from './error.interceptor';
import { ERROR_HANDLED_LOCALLY } from '../http-context';

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
});
