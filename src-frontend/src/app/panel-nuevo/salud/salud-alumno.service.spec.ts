import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { GuardarSalud, SaludAlumnoService } from './salud-alumno.service';

describe('SaludAlumnoService', () => {
  it('GET independientes y PUT exacto, con credenciales y errores locales', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(SaludAlumnoService), http = TestBed.inject(HttpTestingController);
    const url = environment.apiUrl + '/alumnos/7/salud';
    for (let n = 0; n < 2; n++) {
      api.obtener(7).subscribe();
      const req = http.expectOne(url);
      expect(req.request.method).toBe('GET'); expect(req.request.withCredentials).toBeTrue();
      expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue(); req.flush({});
    }
    const cuerpo: GuardarSalud = { tieneDiscapacidad: true, discapacidadTipos: ['VISUAL'], discapacidadOtra: null,
      lesionEnfermedad: null, descripcion: null, impideActividad: null, impideDetalle: null, alergias: null };
    api.guardar(7, cuerpo).subscribe();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('PUT'); expect(req.request.body).toEqual(cuerpo);
    expect(req.request.withCredentials).toBeTrue(); expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({}); http.verify();
  });
});
