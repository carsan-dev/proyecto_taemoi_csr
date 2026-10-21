import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { EditarAlumnoComponent } from './editar-alumno.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { MOTIVO_LECTURA } from '../../../utils/subida-archivos.util';

/** Hotfix de subidas desde Android: el documento se copia al elegirlo y el aviso dice el motivo real. */
describe('EditarAlumnoComponent: subida de documentos', () => {
  let endpoints: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): EditarAlumnoComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    const component = TestBed.runInInjectionContext(
      () => new EditarAlumnoComponent(endpoints, {} as any, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    );
    component.alumnoId = 10;
    spyOn(component as any, 'cargarDocumentosAlumno');
    return component;
  }

  function selector(archivo: File): { entrada: HTMLInputElement; evento: Event; vaciado: jasmine.Spy } {
    const entrada = document.createElement('input');
    entrada.type = 'file';
    const lista = new DataTransfer();
    lista.items.add(archivo);
    entrada.files = lista.files;
    const vaciado = spyOnProperty(entrada, 'value', 'set').and.callThrough();
    return { entrada, evento: { target: entrada } as unknown as Event, vaciado };
  }

  const pdf = () => new File([new Uint8Array([0x25, 0x50, 0x44, 0x46])], 'informe.pdf', { type: 'application/pdf' });

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['subirDocumentoAlumno']);
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('si el archivo no se puede leer muestra el motivo, no envía nada y reinicia el selector', async () => {
    const archivo = pdf();
    spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onDocumentoSelected(evento);

    expect(endpoints.subirDocumentoAlumno).not.toHaveBeenCalled();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_LECTURA }));
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('envía la copia en memoria, no el archivo elegido, y reinicia el selector', async () => {
    endpoints.subirDocumentoAlumno.and.returnValue(of({ id: 1 }));
    const archivo = pdf();
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onDocumentoSelected(evento);

    expect(endpoints.subirDocumentoAlumno).toHaveBeenCalledTimes(1);
    const [alumnoId, enviado] = endpoints.subirDocumentoAlumno.calls.mostRecent().args;
    expect(alumnoId).toBe(10);
    expect(enviado).toEqual(jasmine.any(File));
    expect(enviado).not.toBe(archivo);
    expect(enviado.name).toBe('informe.pdf');
    expect(new Uint8Array(await enviado.arrayBuffer())).toEqual(new Uint8Array([0x25, 0x50, 0x44, 0x46]));
    expect(vaciado).toHaveBeenCalledWith('');
    expect((component as any).cargarDocumentosAlumno).toHaveBeenCalledWith(10);
  });

  it('un 400 con «mensaje» lo muestra tal cual', async () => {
    endpoints.subirDocumentoAlumno.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Tipo de documento no permitido.' } }))
    );
    const { evento } = selector(pdf());
    const component = crear();

    await component.onDocumentoSelected(evento);

    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: 'Tipo de documento no permitido.' }));
  });

  it('un error sin «mensaje» usa el texto de siempre', async () => {
    endpoints.subirDocumentoAlumno.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const { evento } = selector(pdf());
    const component = crear();

    await component.onDocumentoSelected(evento);

    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: 'No se pudo subir el documento' }));
  });
  describe('foto del alumno', () => {
    const jpg = () => new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'IMG_3', { type: 'image/jpeg' });

    function crearConAlumno(): EditarAlumnoComponent {
      const component = crear();
      component.alumno = { id: 10, nombre: 'Ana', apellidos: 'Prueba Inventada' };
      spyOn(component, 'cargarAlumno');
      return component;
    }

    beforeEach(() => {
      endpoints.actualizarAlumno = jasmine.createSpy('actualizarAlumno').and.returnValue(of({}));
    });

    it('envía la copia en memoria con la extensión puesta y reinicia el selector', async () => {
      const archivo = jpg();
      const { evento, vaciado } = selector(archivo);
      const component = crearConAlumno();

      await component.onFileSelected(evento);

      expect(endpoints.actualizarAlumno).toHaveBeenCalledTimes(1);
      const formData: FormData = endpoints.actualizarAlumno.calls.mostRecent().args[1];
      const enviado = formData.get('file') as File;
      expect(enviado).not.toBe(archivo);
      expect(enviado.name).toBe('IMG_3.jpg');
      expect(vaciado).toHaveBeenCalledWith('');
    });

    it('foto ilegible: muestra el motivo, no envía y reinicia el selector', async () => {
      const archivo = jpg();
      spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
      const { evento, vaciado } = selector(archivo);
      const component = crearConAlumno();

      await component.onFileSelected(evento);

      expect(endpoints.actualizarAlumno).not.toHaveBeenCalled();
      expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_LECTURA }));
      expect(vaciado).toHaveBeenCalledWith('');
    });

    it('sin alumno cargado no envía nada pero reinicia el selector', async () => {
      const { evento, vaciado } = selector(jpg());
      const component = crear();
      component.alumno = null;

      await component.onFileSelected(evento);

      expect(endpoints.actualizarAlumno).not.toHaveBeenCalled();
      expect(vaciado).toHaveBeenCalledWith('');
    });
  });
});
