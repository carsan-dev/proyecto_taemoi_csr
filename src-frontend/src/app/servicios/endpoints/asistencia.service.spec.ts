import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AsistenciaService } from './asistencia.service';

describe('AsistenciaService', () => {
  let servicio: AsistenciaService;
  let http: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(AsistenciaService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('clases: prefijo profesor y gestión, errores locales', () => {
    servicio.clases('profesor', 7).subscribe((c) => expect(c.proxima).toBeNull());
    const a = http.expectOne(`${api}/profesor/grupos/7/clases`);
    expect(a.request.method).toBe('GET');
    expect(a.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(a.request.withCredentials).toBeTrue();
    a.flush({ hoy: [], recientes: [], proxima: null });
    servicio.clases('gestion', 7).subscribe();
    http.expectOne(`${api}/grupos/7/clases`).flush({});
  });

  it('lista: GET por turno y fecha; deportes ausentes quedan en []', () => {
    let alumnos: unknown;
    servicio.lista('profesor', 7, 9, '2026-10-26').subscribe((l) => (alumnos = l.alumnos));
    http.expectOne(`${api}/profesor/grupos/7/clases/9/2026-10-26`).flush({ clase: {}, alumnos: [{ id: 1, nombre: 'A', apellidos: 'B', presente: true }] });
    expect((alumnos as { deportes: unknown[] }[])[0].deportes).toEqual([]);
  });

  it('guardar: PUT con {presentes}', () => {
    servicio.guardar('gestion', 7, 9, '2026-10-26', [12, 15]).subscribe();
    const r = http.expectOne(`${api}/grupos/7/clases/9/2026-10-26`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ presentes: [12, 15] });
    r.flush({});
  });

  it('resumen y de alumno: year y month como parámetros', () => {
    servicio.resumen('profesor', 7, 2026, 10).subscribe();
    const a = http.expectOne((r) => r.url === `${api}/profesor/grupos/7/asistencia/resumen`);
    expect(a.request.params.get('year')).toBe('2026');
    expect(a.request.params.get('month')).toBe('10');
    a.flush([]);
    servicio.deAlumno('profesor', 7, 12, 2026, 10).subscribe((x) => expect(x.dias).toEqual([]));
    const b = http.expectOne((r) => r.url === `${api}/profesor/grupos/7/alumnos/12/asistencia`);
    expect(b.request.params.get('month')).toBe('10');
    b.flush({ presentes: 0, clases: 0 });
  });
});
