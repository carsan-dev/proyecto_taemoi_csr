import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { of } from 'rxjs';

import { MaterialesExamenUserComponent } from './materiales-examen-user.component';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';

describe('MaterialesExamenUserComponent', () => {
  let component: MaterialesExamenUserComponent;
  let fixture: ComponentFixture<MaterialesExamenUserComponent>;
  let endpointsServiceSpy: jasmine.SpyObj<EndpointsService>;

  const deportesMock = [
    {
      id: 1,
      deporte: 'TAEKWONDO',
      grado: 'AMARILLO',
      activo: true,
    },
  ] as any;

  beforeEach(async () => {
    endpointsServiceSpy = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerMaterialExamenAlumno',
      'descargarArchivoPrivado',
    ]);
    endpointsServiceSpy.descargarArchivoPrivado.and.returnValue(of(new Blob(['pdf'])));

    await TestBed.configureTestingModule({
      imports: [MaterialesExamenUserComponent],
      providers: [{ provide: EndpointsService, useValue: endpointsServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(MaterialesExamenUserComponent);
    component = fixture.componentInstance;
  });

  function triggerInputs(): void {
    component.alumnoId = 10;
    component.deportes = deportesMock;
    component.ngOnChanges({
      alumnoId: new SimpleChange(null, component.alumnoId, true),
      deportes: new SimpleChange([], component.deportes, true),
    });
    fixture.detectChanges();
  }

  it('debe renderizar lista de documentos y priorizar PDF para seleccion inicial', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: null,
        videos: [],
        documentos: [
          {
            id: '01_glosario.docx',
            fileName: '01_glosario.docx',
            title: 'Glosario',
            order: 1,
            mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            previewable: false,
            openUrl: 'https://example.com/glosario',
            downloadUrl: 'https://example.com/glosario?download=true',
          },
          {
            id: '02_reglamento.pdf',
            fileName: '02_reglamento.pdf',
            title: 'Reglamento',
            order: 2,
            mimeType: 'application/pdf',
            previewable: true,
            openUrl: 'https://example.com/reglamento.pdf',
            downloadUrl: 'https://example.com/reglamento.pdf?download=true',
          },
        ],
      } as any)
    );

    triggerInputs();

    const documentos = fixture.nativeElement.querySelectorAll('.doc-item-btn');
    expect(documentos.length).toBe(2);
    expect(component.documentoSeleccionado?.id).toBe('02_reglamento.pdf');
    expect(component.esDocumentoSeleccionadoPrevisualizable()).toBeTrue();
  });

  it('debe ocultar visor integrado cuando el documento no es PDF', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: null,
        videos: [],
        documentos: [
          {
            id: '01_glosario.docx',
            fileName: '01_glosario.docx',
            title: 'Glosario',
            order: 1,
            mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            previewable: false,
            openUrl: 'https://example.com/glosario',
            downloadUrl: 'https://example.com/glosario?download=true',
          },
        ],
      } as any)
    );

    triggerInputs();

    expect(component.esDocumentoSeleccionadoPrevisualizable()).toBeFalse();
    expect(fixture.nativeElement.querySelector('.docs-frame')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'Este formato no admite visor integrado en la web.'
    );
  });

  it('debe construir fallback de documentos cuando solo llega temario', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: {
          fileName: 'temario.pdf',
          downloadUrl: 'https://example.com/temario.pdf',
        },
        videos: [],
      } as any)
    );

    triggerInputs();

    expect(component.material?.documentos.length).toBe(1);
    expect(component.material?.documentos[0].fileName).toBe('Temario para cinturón Amarillo-Naranja.pdf');
    expect(component.material?.documentos[0].title).toBe('Temario para cinturón Amarillo/Naranja');
    expect(component.material?.documentos[0].previewable).toBeTrue();
    expect(component.material?.documentos[0].downloadUrl).toContain('download=true');
  });

  it('debe descargar por blob usando el endpoint privado del documento', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: null,
        videos: [],
        documentos: [
          {
            id: 'temario.pdf',
            fileName: 'temario.pdf',
            title: 'Temario',
            order: 0,
            mimeType: 'text/plain',
            previewable: false,
            openUrl: 'https://example.com/temario.pdf',
            downloadUrl: 'https://example.com/temario.pdf?download=true',
          },
        ],
      } as any)
    );

    spyOn(globalThis.URL, 'createObjectURL').and.returnValue('blob://test-url');
    spyOn(globalThis.URL, 'revokeObjectURL').and.stub();

    triggerInputs();
    component.descargarDocumentoSeleccionado();

    expect(endpointsServiceSpy.descargarArchivoPrivado).toHaveBeenCalledWith(
      'https://example.com/temario.pdf?download=true'
    );
  });

  it('debe abrir documento principal por URL controlada', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: null,
        videos: [],
        documentos: [
          {
            id: 'temario.pdf',
            fileName: 'temario.pdf',
            title: 'Temario',
            order: 0,
            mimeType: 'text/plain',
            previewable: false,
            openUrl: 'https://example.com/temario.pdf',
            downloadUrl: 'https://example.com/temario.pdf?download=true',
          },
        ],
      } as any)
    );

    spyOn(window, 'open').and.returnValue({} as Window);
    triggerInputs();

    component.abrirDocumentoSeleccionado();

    expect(endpointsServiceSpy.descargarArchivoPrivado).not.toHaveBeenCalled();
    expect(window.open).toHaveBeenCalledWith('https://example.com/temario.pdf', '_blank', 'noopener');
  });

  it('debe permitir abrir el visor integrado en Android para PDF previsualizable', () => {
    endpointsServiceSpy.obtenerMaterialExamenAlumno.and.returnValue(
      of({
        deporte: 'TAEKWONDO',
        gradoActual: 'AMARILLO',
        bloqueId: 'b02_amarillo_a_naranja',
        temario: null,
        videos: [],
        documentos: [
          {
            id: '02_reglamento.pdf',
            fileName: '02_reglamento.pdf',
            title: 'Reglamento',
            order: 2,
            mimeType: 'application/pdf',
            previewable: true,
            openUrl: 'https://example.com/reglamento.pdf',
            downloadUrl: 'https://example.com/reglamento.pdf?download=true',
          },
        ],
      } as any)
    );

    spyOnProperty(globalThis.navigator, 'userAgent', 'get').and.returnValue(
      'Mozilla/5.0 (Linux; Android 14)'
    );

    triggerInputs();

    expect(component.esDocumentoSeleccionadoPrevisualizable()).toBeTrue();
    expect(endpointsServiceSpy.descargarArchivoPrivado).toHaveBeenCalledWith(
      'https://example.com/reglamento.pdf'
    );

    component.toggleDocumentoVisor();

    expect(component.mostrarDocumentoVisor).toBeTrue();
  });
});
