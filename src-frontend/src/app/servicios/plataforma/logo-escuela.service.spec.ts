import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { LogoEscuelaService } from './logo-escuela.service';

describe('LogoEscuelaService', () => {
  let servicio: LogoEscuelaService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/plataforma/escuelas`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(LogoEscuelaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista las escuelas con credenciales', () => {
    servicio.escuelas().subscribe();
    const peticion = http.expectOne(base);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    peticion.flush([]);
  });

  it('sube el archivo en multipart con el campo "archivo"', () => {
    const archivo = new File(['x'], 'logo.png', { type: 'image/png' });
    servicio.subir(7, archivo).subscribe();
    const peticion = http.expectOne(`${base}/7/logo`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body instanceof FormData).toBeTrue();
    expect((peticion.request.body as FormData).get('archivo')).toBe(archivo);
    peticion.flush({ id: 7, nombre: 'Norte', tieneLogo: true, marcaHeredada: false });
  });

  it('quita el logo con DELETE', () => {
    servicio.quitar(7).subscribe();
    const peticion = http.expectOne(`${base}/7/logo`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush({ id: 7, nombre: 'Norte', tieneLogo: false, marcaHeredada: false });
  });

  it('descarga el logo como blob', () => {
    servicio.logo(7).subscribe();
    const peticion = http.expectOne(`${base}/7/logo`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('blob');
    peticion.flush(new Blob(['x'], { type: 'image/png' }));
  });
});
