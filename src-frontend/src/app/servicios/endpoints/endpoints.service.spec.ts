import { of } from 'rxjs';

import { EndpointsService } from './endpoints.service';

describe('EndpointsService', () => {
  function createService(getSpy: jasmine.Spy = jasmine.createSpy('get')): EndpointsService {
    const httpClientSpy = jasmine.createSpyObj('HttpClient', ['get']);
    httpClientSpy.get = getSpy;
    return new EndpointsService(httpClientSpy as any);
  }

  it('reconstruye URLs de material de examen desde rutas controladas del backend', (done) => {
    const getSpy = jasmine.createSpy('get').and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: {
          fileName: 'temario.pdf',
          downloadUrl: 'https://malicioso.example/temario.pdf',
        },
        videos: [
          {
            id: '01_patada.mp4',
            title: 'Patada',
            order: 1,
            streamUrl: 'https://malicioso.example/video.mp4',
          },
        ],
        videosAnteriores: [
          {
            id: 'anterior__b01_inicio_a_amarillo__01_taeguk.mp4',
            title: 'Taeguk anterior',
            order: 1,
            streamUrl: 'https://malicioso.example/video-anterior.mp4',
          },
        ],
        documentos: [
          {
            id: '02_reglamento.pdf',
            fileName: '02_reglamento.pdf',
            title: 'Reglamento',
            order: 2,
            mimeType: 'application/pdf',
            previewable: true,
            openUrl: 'https://malicioso.example/reglamento.pdf',
            downloadUrl: 'https://malicioso.example/reglamento.pdf?download=true',
          },
        ],
      })
    );
    const service = createService(getSpy);

    service.obtenerMaterialExamenAlumno(10, 'TAEKWONDO').subscribe((material) => {
      expect(getSpy).toHaveBeenCalled();
      expect(material.temario?.downloadUrl).toContain('/api/alumnos/10/deportes/TAEKWONDO/material-examen/temario?download=true');
      expect(material.videos[0].streamUrl).toContain('/api/alumnos/10/deportes/TAEKWONDO/material-examen/videos/01_patada.mp4');
      expect(material.videosAnteriores[0].streamUrl).toContain('/api/alumnos/10/deportes/TAEKWONDO/material-examen/videos/anterior__b01_inicio_a_amarillo__01_taeguk.mp4');
      expect(material.documentos[0].openUrl).toContain('/api/alumnos/10/deportes/TAEKWONDO/material-examen/documentacion/02_reglamento.pdf');
      expect(material.documentos[0].downloadUrl).toContain('download=true');
      done();
    });
  });

  it('mantiene el temario sobre la ruta dedicada y no lo mueve a documentacion', (done) => {
    const service = createService(
      jasmine.createSpy('get').and.returnValue(
        of({
          deporte: 'TAEKWONDO',
          gradoActual: 'AMARILLO',
          bloqueId: 'b02_amarillo_a_naranja',
          temario: {
            fileName: 'temario.pdf',
            downloadUrl: '/api/alumnos/1/deportes/TAEKWONDO/material-examen/temario?download=true',
          },
          videos: [],
          documentos: [
            {
              id: 'temario.pdf',
              fileName: 'temario.pdf',
              title: 'Temario',
              order: 0,
              mimeType: 'application/pdf',
              previewable: true,
              openUrl: '/api/alumnos/1/deportes/TAEKWONDO/material-examen/temario',
              downloadUrl: '/api/alumnos/1/deportes/TAEKWONDO/material-examen/temario?download=true',
            },
          ],
        })
      )
    );

    service.obtenerMaterialExamenAlumno(1, 'TAEKWONDO').subscribe((material) => {
      expect(material.documentos[0].openUrl).toContain('/api/alumnos/1/deportes/TAEKWONDO/material-examen/temario');
      expect(material.documentos[0].openUrl).not.toContain('/documentacion/temario.pdf');
      expect(material.documentos[0].downloadUrl).toContain('/api/alumnos/1/deportes/TAEKWONDO/material-examen/temario?download=true');
      done();
    });
  });

  it('pide solo los ids de los alumnos con el mismo filtro de inactivos que el listado', (done) => {
    const getSpy = jasmine.createSpy('get').and.returnValue(of([3, 7, 9]));
    const service = createService(getSpy);

    service.obtenerIdsAlumnos(true).subscribe((ids) => {
      expect(ids).toEqual([3, 7, 9]);
      const [url, opciones] = getSpy.calls.mostRecent().args;
      expect(url).toMatch(/\/alumnos\/ids$/);
      expect(opciones.params.get('incluirInactivos')).toBe('true');
      expect(opciones.withCredentials).toBeTrue();
      done();
    });
  });

  it('cambia «Mi caja» de un producto con PATCH y cuerpo vendibleProfesor', (done) => {
    const patchSpy = jasmine.createSpy('patch').and.returnValue(of({ id: 7, vendibleProfesor: true }));
    const service = new EndpointsService({ patch: patchSpy } as any);

    service.cambiarVendibleProfesor(7, true).subscribe((producto) => {
      const [url, cuerpo, opciones] = patchSpy.calls.mostRecent().args;
      expect(url).toMatch(/\/productos\/7\/vendible-profesor$/);
      expect(cuerpo).toEqual({ vendibleProfesor: true });
      expect(opciones.withCredentials).toBeTrue();
      expect(producto.vendibleProfesor).toBeTrue();
      done();
    });
  });

  it('cambia «Mi caja» en bloque con PATCH y ids', (done) => {
    const patchSpy = jasmine.createSpy('patch').and.returnValue(of({ actualizados: 2 }));
    const service = new EndpointsService({ patch: patchSpy } as any);

    service.cambiarVendibleProfesorEnBloque([1, 2], false).subscribe((respuesta) => {
      const [url, cuerpo, opciones] = patchSpy.calls.mostRecent().args;
      expect(url).toMatch(/\/productos\/vendible-profesor$/);
      expect(cuerpo).toEqual({ ids: [1, 2], vendibleProfesor: false });
      expect(opciones.withCredentials).toBeTrue();
      expect(respuesta.actualizados).toBe(2);
      done();
    });
  });
});
