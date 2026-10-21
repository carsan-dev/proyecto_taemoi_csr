import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import Swal from 'sweetalert2';

import { CrearAlumnoComponent } from './crear-alumno.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { MOTIVO_HEIC, MOTIVO_LECTURA } from '../../../utils/subida-archivos.util';

/** Hotfix de subidas desde Android: la foto del alumno nuevo se copia al elegirla. */
describe('CrearAlumnoComponent: foto del alumno', () => {
  let swal: jasmine.Spy;

  function crear(): CrearAlumnoComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    return TestBed.runInInjectionContext(
      () => new CrearAlumnoComponent(new FormBuilder(), {} as any, {} as any, {} as any)
    );
  }

  function selector(archivo: File): { evento: Event; vaciado: jasmine.Spy } {
    const entrada = document.createElement('input');
    entrada.type = 'file';
    const lista = new DataTransfer();
    lista.items.add(archivo);
    entrada.files = lista.files;
    const vaciado = spyOnProperty(entrada, 'value', 'set').and.callThrough();
    return { evento: { target: entrada } as unknown as Event, vaciado };
  }

  beforeEach(() => {
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('foto válida: guarda la copia en memoria y reinicia el selector', async () => {
    const archivo = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'IMG_1', { type: 'image/jpeg' });
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onFileChange(evento);

    expect(component.imagen).not.toBeNull();
    expect(component.imagen).not.toBe(archivo);
    expect(component.imagen!.name).toBe('IMG_1.jpg');
    expect(vaciado).toHaveBeenCalledWith('');
    expect(swal).not.toHaveBeenCalled();
  });

  it('foto ilegible: muestra el motivo, no la guarda y reinicia el selector', async () => {
    const archivo = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'foto.jpg', { type: 'image/jpeg' });
    spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    const { evento, vaciado } = selector(archivo);
    const component = crear();

    await component.onFileChange(evento);

    expect(component.imagen).toBeNull();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_LECTURA }));
    expect(vaciado).toHaveBeenCalledWith('');
  });

  it('foto HEIC: se rechaza con el texto de la decisión 227', async () => {
    const { evento } = selector(new File([new Uint8Array([1, 2])], 'IMG_2.heic', { type: 'image/heic' }));
    const component = crear();

    await component.onFileChange(evento);

    expect(component.imagen).toBeNull();
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: MOTIVO_HEIC }));
  });
});
