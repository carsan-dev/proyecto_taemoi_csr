import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { sitioDePrueba } from '../datos/datos-de-prueba';
import { SitioWeb } from '../datos/modelos';
import { CARGAR_CAPA_EDITOR, EditorWebService } from './editor-web';

describe('EditorWebService (W12c)', () => {
  let cargar: jasmine.Spy;
  let activar: jasmine.Spy;
  let ventana: { parent: unknown };

  function crear(dentroDeIframe = true): EditorWebService {
    activar = jasmine.createSpy('activarCapaEditor').and.returnValue(() => undefined);
    cargar = jasmine.createSpy('cargar').and.resolveTo({ activarCapaEditor: activar });
    ventana = { parent: null };
    ventana.parent = dentroDeIframe ? {} : ventana;
    TestBed.configureTestingModule({
      providers: [
        { provide: CARGAR_CAPA_EDITOR, useValue: cargar },
        { provide: DOCUMENT, useValue: { defaultView: ventana } },
      ],
    });
    return TestBed.inject(EditorWebService);
  }

  const sitio = (vistaPrevia: boolean): SitioWeb => ({ ...sitioDePrueba(), vistaPrevia });

  it('se activa una vez si la URL lo pide, la API confirma la vista previa de gestión y va en un iframe', async () => {
    const editor = crear();
    editor.activarSiToca('1', sitio(true));
    editor.activarSiToca('1', sitio(true));
    await Promise.resolve();
    await Promise.resolve();
    expect(cargar).toHaveBeenCalledTimes(1);
    expect(activar).toHaveBeenCalledTimes(1);
    expect(activar.calls.mostRecent().args[0].ventana).toBe(ventana);
  });

  it('no se activa si la API no confirma la vista previa de gestión (no basta el parámetro)', () => {
    const editor = crear();
    editor.activarSiToca('1', sitio(false));
    editor.activarSiToca('1', null);
    expect(cargar).not.toHaveBeenCalled();
  });

  it('no se activa sin editor=1 ni fuera de un iframe', () => {
    const editor = crear(false);
    editor.activarSiToca(null, sitio(true));
    editor.activarSiToca('si', sitio(true));
    editor.activarSiToca('1', sitio(true));
    expect(cargar).not.toHaveBeenCalled();
  });

  it('pinta con los bloques del borrador solo en la vista previa de gestión', async () => {
    const editor = crear();
    editor.activarSiToca('1', sitio(true));
    await Promise.resolve();
    await Promise.resolve();
    const bloques = [{ clave: 'portada.titular', texto: 'Borrador', imagenUrl: null, orden: 0, oculto: false }];
    activar.calls.mostRecent().args[0].alBorrador(bloques);
    expect(editor.aplicar(sitio(true)).bloques).toEqual(bloques);
    expect(editor.aplicar(sitio(false)).bloques).toEqual(sitio(false).bloques);
  });
});
