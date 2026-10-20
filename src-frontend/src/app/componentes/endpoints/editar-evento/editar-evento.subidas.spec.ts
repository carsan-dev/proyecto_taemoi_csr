import { HttpErrorResponse } from '@angular/common/http';
import { ElementRef } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { EditarEventoComponent } from './editar-evento.component';
import { MOTIVO_HEIC, MOTIVO_LECTURA } from '../../../utils/subida-archivos.util';

/** Hotfix de subidas desde Android: la imagen y los documentos del evento se copian al elegirlos. */
describe('EditarEventoComponent: subida de imagen y documentos', () => {
  let endpoints: jasmine.SpyObj<any>;
  let router: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): EditarEventoComponent {
    const loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    const component = new EditarEventoComponent(new FormBuilder(), endpoints, {} as any, router, {} as any, loading);
    component.eventoId = 7;
    component.eventoForm.patchValue({ titulo: 'Torneo de prueba', descripcion: 'Descripción inventada' });
    return component;
  }

  function selector(...archivos: File[]): { entrada: HTMLInputElement; evento: Event; vaciado: jasmine.Spy } {
    const entrada = document.createElement('input');
    entrada.type = 'file';
    const lista = new DataTransfer();
    archivos.forEach((archivo) => lista.items.add(archivo));
    entrada.files = lista.files;
    const vaciado = spyOnProperty(entrada, 'value', 'set').and.callThrough();
    return { entrada, evento: { target: entrada } as unknown as Event, vaciado };
  }

  const jpg = (nombre = 'cartel.jpg') =>
    new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2])], nombre, { type: 'image/jpeg' });

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['actualizarEvento', 'subirDocumentoEvento']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('imagen ilegible: muestra el motivo, no la guarda y reinicia el selector', async () => {
    const archivo = jpg();
    spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onFileSelected(evento);

    expect(component.imagen).toBeNull();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_LECTURA }));
    expect(vaciado).toHaveBeenCalledWith('');

    endpoints.actualizarEvento.and.returnValue(of({}));
    component.actualizarEvento();
    const formData: FormData = endpoints.actualizarEvento.calls.mostRecent().args[1];
    expect(formData.get('file')).toBe('null');
  });

  it('una foto HEIC se rechaza con el texto de la decisión 227', async () => {
    const { evento } = selector(new File([new Uint8Array([1, 2, 3])], 'IMG_0001.heic', { type: 'image/heic' }));
    const component = crear();

    await component.onFileSelected(evento);

    expect(component.imagen).toBeNull();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_HEIC }));
  });

  it('imagen válida: al guardar se envía la copia en memoria y el selector queda reiniciado', async () => {
    endpoints.actualizarEvento.and.returnValue(of({}));
    const archivo = jpg('IMG_20261021');
    const { evento, vaciado } = selector(new File([archivo], 'IMG_20261021', { type: 'image/jpeg' }));
    const component = crear();

    await component.onFileSelected(evento);
    component.actualizarEvento();

    const formData: FormData = endpoints.actualizarEvento.calls.mostRecent().args[1];
    const enviado = formData.get('file') as File;
    expect(enviado).toBe(component.imagen!);
    expect(enviado.name).toBe('IMG_20261021.jpg');
    expect(new Uint8Array(await enviado.arrayBuffer())).toEqual(new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2]));
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('un 400 al guardar muestra el «mensaje» del backend', async () => {
    endpoints.actualizarEvento.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'La imagen no se pudo procesar.' } }))
    );
    const component = crear();
    component.inputFile = new ElementRef(document.createElement('input'));

    component.actualizarEvento();

    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: 'La imagen no se pudo procesar.' }));
  });

  it('documentos: añade las copias válidas, avisa de las rechazadas y reinicia el selector', async () => {
    const bueno = new File([new Uint8Array([0x25, 0x50])], 'bases.pdf', { type: 'application/pdf' });
    const malo = new File([new Uint8Array([1])], 'copia.zip', { type: 'application/zip' });
    const { evento, vaciado } = selector(bueno, malo);
    const component = crear();

    await component.onDocumentosChange(evento);

    expect(component.documentosNuevos.length).toBe(1);
    expect(component.documentosNuevos[0]).not.toBe(bueno);
    expect(component.documentosNuevos[0].name).toBe('bases.pdf');
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: jasmine.stringContaining('copia.zip') }));
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('documentos: un 400 con «mensaje» al subir lo muestra', async () => {
    endpoints.subirDocumentoEvento.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Tipo de documento no permitido.' } }))
    );
    const { evento } = selector(new File([new Uint8Array([0x25, 0x50])], 'bases.pdf', { type: 'application/pdf' }));
    const component = crear();
    await component.onDocumentosChange(evento);

    component.subirDocumentosNuevos();

    expect(swal).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'warning', text: jasmine.stringContaining('Tipo de documento no permitido.') })
    );
    expect(swal.calls.mostRecent().args[0].timer).toBeUndefined();
  });
});
