import { HttpClient, HttpEventType, provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CLIENTE_SUBIDAS, MaterialExamenApiService } from './material-examen-api.service';

const BASE = `${environment.apiUrl}/material-examen`;
const video = new File(['contenido'], 'Taeguk Il Jang.mp4', { type: 'video/mp4' });

describe('MaterialExamenApiService', () => {
  describe('peticiones', () => {
    let api: MaterialExamenApiService;
    let http: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: CLIENTE_SUBIDAS, useExisting: HttpClient },
        ],
      });
      api = TestBed.inject(MaterialExamenApiService);
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('lee el listado y el detalle con el error en la pantalla', () => {
      api.listar().subscribe();
      const listado = http.expectOne(BASE);
      expect(listado.request.method).toBe('GET');
      expect(listado.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      listado.flush({ disciplinas: [], totales: { temarios: 0, videos: 0, bytes: 0 } });

      api.detalle('TAEKWONDO', 'b01_inicio_a_amarillo').subscribe();
      http.expectOne(`${BASE}/TAEKWONDO/b01_inicio_a_amarillo`).flush({});
    });

    it('subir un vídeo envía el File crudo, con su tipo y el nombre en la consulta, sin FormData', () => {
      api.subirVideo('TAEKWONDO', 'b01', video).subscribe();
      const r = http.expectOne(`${BASE}/TAEKWONDO/b01/videos?nombre=Taeguk%20Il%20Jang.mp4`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toBe(video);
      expect(r.request.body instanceof FormData).toBeFalse();
      expect(r.request.reportProgress).toBeTrue();
      expect(r.request.headers.get('Content-Type')).toBe('video/mp4');
      expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      r.flush({ fichero: 'x.mp4', bloque: {} });
    });

    it('sin tipo, octet-stream', () => {
      const sinTipo = new File(['x'], 'a.mov');
      api.subirVideo('TAEKWONDO', 'b01', sinTipo).subscribe();
      const r = http.expectOne(`${BASE}/TAEKWONDO/b01/videos?nombre=a.mov`);
      expect(r.request.headers.get('Content-Type')).toBe('application/octet-stream');
      r.flush({ fichero: 'a.mov', bloque: {} });
    });

    it('emite el progreso de la subida', () => {
      const eventos: HttpEventType[] = [];
      api.subirVideo('TAEKWONDO', 'b01', video).subscribe((e) => eventos.push(e.type));
      const r = http.expectOne(() => true);
      r.event({ type: HttpEventType.UploadProgress, loaded: 5, total: 9 });
      r.flush({ fichero: 'x.mp4', bloque: {} });
      expect(eventos).toContain(HttpEventType.UploadProgress);
      expect(eventos).toContain(HttpEventType.Response);
    });

    it('cancelar es cancelar la suscripción: la petición queda abortada', () => {
      const s = api.subirVideo('TAEKWONDO', 'b01', video).subscribe();
      const r = http.expectOne(() => true);
      s.unsubscribe();
      expect(r.cancelled).toBeTrue();
    });

    it('sustituir un vídeo: PUT crudo a /contenido con el nombre codificado', () => {
      api.sustituirVideo('TAEKWONDO', 'b01', '01 taeguk.mp4', video).subscribe();
      const r = http.expectOne(`${BASE}/TAEKWONDO/b01/videos/01%20taeguk.mp4/contenido`);
      expect(r.request.method).toBe('PUT');
      expect(r.request.body).toBe(video);
      r.flush({});
    });

    it('título, orden y eliminar', () => {
      api.cambiarTitulo('T', 'b', 'a.mp4', 'Nuevo').subscribe();
      const titulo = http.expectOne(`${BASE}/T/b/videos/a.mp4`);
      expect(titulo.request.method).toBe('PATCH');
      expect(titulo.request.body).toEqual({ titulo: 'Nuevo' });
      titulo.flush({});

      api.ordenarVideos('T', 'b', ['b.mp4', 'a.mp4']).subscribe();
      const orden = http.expectOne(`${BASE}/T/b/videos/orden`);
      expect(orden.request.method).toBe('PUT');
      expect(orden.request.body).toEqual(['b.mp4', 'a.mp4']);
      orden.flush({});

      api.eliminarVideo('T', 'b', 'a.mp4').subscribe();
      const borrar = http.expectOne(`${BASE}/T/b/videos/a.mp4`);
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush({});
    });

    it('temario y documentación van crudos', () => {
      const pdf = new File(['%PDF'], 'normas.pdf', { type: 'application/pdf' });

      api.subirTemario('T', 'b', pdf).subscribe();
      const temario = http.expectOne(`${BASE}/T/b/temario`);
      expect(temario.request.method).toBe('PUT');
      expect(temario.request.body).toBe(pdf);
      expect(temario.request.headers.get('Content-Type')).toBe('application/pdf');
      temario.flush({});

      api.subirDocumento('T', 'b', pdf).subscribe();
      const doc = http.expectOne(`${BASE}/T/b/documentacion?nombre=normas.pdf`);
      expect(doc.request.method).toBe('POST');
      doc.flush({ fichero: 'normas.pdf', bloque: {} });

      api.sustituirDocumento('T', 'b', 'normas.pdf', pdf).subscribe();
      const sustituir = http.expectOne(`${BASE}/T/b/documentacion/normas.pdf/contenido`);
      expect(sustituir.request.method).toBe('PUT');
      sustituir.flush({});

      api.eliminarDocumento('T', 'b', 'normas.pdf').subscribe();
      const borrar = http.expectOne(`${BASE}/T/b/documentacion/normas.pdf`);
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush({});
    });

    it('ver: los PDF como blob y la URL del vídeo para <video src>', () => {
      api.temario('T', 'b').subscribe();
      const temario = http.expectOne(`${BASE}/T/b/temario`);
      expect(temario.request.responseType).toBe('blob');
      temario.flush(new Blob(['%PDF']));

      api.documento('T', 'b', 'a b.pdf').subscribe();
      http.expectOne(`${BASE}/T/b/documentacion/a%20b.pdf`).flush(new Blob(['%PDF']));

      expect(api.urlVideo('T', 'b', 'a b.mp4')).toBe(`${BASE}/T/b/videos/a%20b.mp4`);
    });
  });

  describe('cliente real de las subidas', () => {
    // La app va con `withFetch()`, y fetch no informa del progreso de subida: las subidas deben salir por XMLHttpRequest.
    it('usa XMLHttpRequest con credenciales aunque la app use fetch', () => {
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(withFetch())] });
      const send = spyOn(XMLHttpRequest.prototype, 'send');
      const open = spyOn(XMLHttpRequest.prototype, 'open').and.callThrough();
      const peticionFetch = spyOn(window, 'fetch').and.callThrough();

      const s = TestBed.inject(MaterialExamenApiService).subirVideo('T', 'b', video).subscribe({ error: () => undefined });

      expect(open.calls.mostRecent().args.slice(0, 2) as string[]).toEqual(['POST', `${BASE}/T/b/videos?nombre=Taeguk%20Il%20Jang.mp4`]);
      expect(send).toHaveBeenCalledOnceWith(video);
      expect((send.calls.mostRecent().object as XMLHttpRequest).withCredentials).toBeTrue();
      expect(peticionFetch).not.toHaveBeenCalled();
      s.unsubscribe();
    });
  });
});
