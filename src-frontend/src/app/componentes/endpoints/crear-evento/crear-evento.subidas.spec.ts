import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { CrearEventoComponent } from './crear-evento.component';
import { MOTIVO_LECTURA } from '../../../utils/subida-archivos.util';

/** Hotfix de subidas desde Android: la imagen y los documentos del evento nuevo se copian al elegirlos. */
describe('CrearEventoComponent: subida de imagen y documentos', () => {
  let endpoints: jasmine.SpyObj<any>;
  let router: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): CrearEventoComponent {
    const loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    const component = new CrearEventoComponent(endpoints, new FormBuilder(), router, loading);
    component.ngOnInit();
    component.eventoForm.patchValue({ titulo: 'Torneo de prueba', descripcion: 'Descripción inventada' });
    return component;
  }

  function selector(...archivos: File[]): { evento: Event; vaciado: jasmine.Spy } {
    const entrada = document.createElement('input');
    entrada.type = 'file';
    const lista = new DataTransfer();
    archivos.forEach((archivo) => lista.items.add(archivo));
    entrada.files = lista.files;
    const vaciado = spyOnProperty(entrada, 'value', 'set').and.callThrough();
    return { evento: { target: entrada } as unknown as Event, vaciado };
  }

  const pdf = (nombre = 'bases.pdf') => new File([new Uint8Array([0x25, 0x50, 0x44, 0x46])], nombre, { type: 'application/pdf' });

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['crearEvento', 'subirDocumentoEvento']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('imagen válida: se envía la copia en memoria al crear y el selector queda reiniciado', async () => {
    endpoints.crearEvento.and.returnValue(of({ id: 3 }));
    const archivo = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'cartel.png', { type: 'image/png' });
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onFileChange(evento);
    component.onSubmit();

    const enviada: File = endpoints.crearEvento.calls.mostRecent().args[1];
    expect(enviada).toBe(component.imagen!);
    expect(enviada).not.toBe(archivo);
    expect(enviada.name).toBe('cartel.png');
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('imagen ilegible: muestra el motivo y no la guarda', async () => {
    const archivo = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'cartel.jpg', { type: 'image/jpeg' });
    spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onFileChange(evento);

    expect(component.imagen).toBeNull();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_LECTURA }));
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('documentos: añade las copias válidas y avisa de cada rechazado con su motivo', async () => {
    const bueno = pdf();
    const ilegible = pdf('acta.pdf');
    spyOn(ilegible, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    const { evento, vaciado } = selector(bueno, ilegible);
    const component = crear();

    await component.onDocumentosChange(evento);

    expect(component.documentos.length).toBe(1);
    expect(component.documentos[0]).not.toBe(bueno);
    const aviso = swal.calls.mostRecent().args[0];
    expect(aviso.icon).toBe('error');
    expect(aviso.text).toContain('acta.pdf');
    expect(aviso.text).toContain(MOTIVO_LECTURA);
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('documentos que fallan al subir: el aviso lleva el «mensaje» del backend y no se cierra solo', async () => {
    endpoints.crearEvento.and.returnValue(of({ id: 3 }));
    endpoints.subirDocumentoEvento.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Tipo de documento no permitido.' } }))
    );
    const { evento } = selector(pdf());
    const component = crear();
    await component.onDocumentosChange(evento);

    component.onSubmit();

    const aviso = swal.calls.mostRecent().args[0];
    expect(aviso.icon).toBe('warning');
    expect(aviso.text).toContain('Tipo de documento no permitido.');
    expect(aviso.timer).toBeUndefined();
  });

  it('documentos que fallan sin «mensaje»: el aviso de siempre, con su temporizador', async () => {
    endpoints.crearEvento.and.returnValue(of({ id: 3 }));
    endpoints.subirDocumentoEvento.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { evento } = selector(pdf());
    const component = crear();
    await component.onDocumentosChange(evento);

    component.onSubmit();

    const aviso = swal.calls.mostRecent().args[0];
    expect(aviso.text).toBe('El evento se ha creado, pero 1 documento(s) no se pudieron subir.');
    expect(aviso.timer).toBe(3000);
  });
});
