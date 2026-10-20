import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { GrupoProfesoresService } from './grupo-profesores.service';

describe('GrupoProfesoresService', () => {
  let servicio: GrupoProfesoresService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/grupos/5/profesores`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(GrupoProfesoresService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista, asigna y quita con credenciales y errores tratados en la pantalla', () => {
    servicio.listar(5).subscribe();
    const get = http.expectOne(api);
    expect(get.request.method).toBe('GET');
    expect(get.request.withCredentials).toBeTrue();
    expect(get.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    get.flush([]);

    servicio.asignar(5, 'a@b.test', 'AYUDANTE').subscribe();
    const post = http.expectOne(api);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ email: 'a@b.test', rol: 'AYUDANTE' });
    post.flush({});

    servicio.quitar(5, 9).subscribe();
    const del = http.expectOne(`${api}/9`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
