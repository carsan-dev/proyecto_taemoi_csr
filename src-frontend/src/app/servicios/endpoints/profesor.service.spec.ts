import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { HojaAsistencia, PaseCreado } from '../../interfaces/profesor';
import { ProfesorService, mensajeDeErrorBlob, nombreDeContentDisposition } from './profesor.service';

describe('ProfesorService', () => {
  let servicio: ProfesorService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/profesor`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ProfesorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide sus grupos con credenciales y errores tratados en la pantalla', () => {
    let grupos: unknown;
    servicio.misGrupos().subscribe((g) => (grupos = g));
    const req = http.expectOne(`${api}/grupos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush(null);
    expect(grupos).toEqual([]);
  });

  it('pide los alumnos de un grupo y la ficha de un alumno', () => {
    servicio.alumnosDelGrupo(7).subscribe();
    http.expectOne(`${api}/grupos/7/alumnos`).flush([]);
    servicio.ficha(3).subscribe();
    http.expectOne(`${api}/alumnos/3`).flush({ id: 3, nombre: 'A', apellidos: 'B', edad: null, grupos: [], contacto: [], deportes: [] });
  });

  it('la salud es una petición propia a /salud', () => {
    servicio.salud(3).subscribe();
    const req = http.expectOne(`${api}/alumnos/3/salud`);
    expect(req.request.method).toBe('GET');
    req.flush({ alumnoId: 3, registrado: false });
  });

  it('apto y fecha de grado son PUT con el cuerpo de la API', () => {
    servicio.actualizarApto(3, 'TAEKWONDO', true).subscribe();
    let req = http.expectOne(`${api}/alumnos/3/deportes/TAEKWONDO/apto`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ aptoParaExamen: true });
    req.flush({});

    servicio.actualizarFechaGrado(3, 'TAEKWONDO', '2026-03-12').subscribe();
    req = http.expectOne(`${api}/alumnos/3/deportes/TAEKWONDO/fecha-grado`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ fechaGrado: '2026-03-12' });
    req.flush({});
  });

  it('los pases van a su ruta con rojoBordado y normalizan el importe', () => {
    let pase: PaseCreado | undefined;
    servicio.pasarGrado(3, 'TAEKWONDO', 'examen', true).subscribe((p) => (pase = p));
    const req = http.expectOne(`${api}/alumnos/3/deportes/TAEKWONDO/pase-examen`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ rojoBordado: true });
    req.flush({ deporte: {}, cargo: { id: 9, concepto: 'Pase', importe: '25.50' } });
    expect(pase?.cargo.importe).toBe(25.5);

    servicio.pasarGrado(3, 'KICKBOXING', 'recompensa', false).subscribe();
    const req2 = http.expectOne(`${api}/alumnos/3/deportes/KICKBOXING/pase-recompensa`);
    expect(req2.request.body).toEqual({ rojoBordado: false });
    req2.flush({ deporte: {}, cargo: { id: 10, concepto: 'Pase', importe: 20 } });
  });

  it('descarga la hoja de asistencia como blob con el nombre del Content-Disposition', () => {
    let hoja: HojaAsistencia | undefined;
    servicio.hojaAsistencia(7, 2026, 10).subscribe((h) => (hoja = h));
    const req = http.expectOne((r) => r.url === `${api}/grupos/7/asistencia`);
    expect(req.request.params.get('year')).toBe('2026');
    expect(req.request.params.get('month')).toBe('10');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }), {
      headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="Asistencia-grupo-7-2026-10.pdf"' }),
    });
    expect(hoja?.nombre).toBe('Asistencia-grupo-7-2026-10.pdf');
    expect(hoja?.blob.size).toBe(4);
  });

  it('sin Content-Disposition propone un nombre con grupo y mes', () => {
    expect(nombreDeContentDisposition(null, 'Asistencia-grupo-7-2026-03.pdf')).toBe('Asistencia-grupo-7-2026-03.pdf');
    expect(nombreDeContentDisposition("attachment; filename*=UTF-8''Hoja%20octubre.pdf", 'x.pdf')).toBe('Hoja octubre.pdf');
  });

  it('lee el {mensaje} de un error que llega como blob', async () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: new Blob([JSON.stringify({ mensaje: 'Mes no válido' })], { type: 'application/json' }),
    });
    expect(await mensajeDeErrorBlob(error)).toBe('Mes no válido');
    expect(await mensajeDeErrorBlob(new HttpErrorResponse({ status: 500, error: new Blob(['nada']) }))).toBeNull();
  });
});
