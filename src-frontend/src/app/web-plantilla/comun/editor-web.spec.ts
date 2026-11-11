import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { sitioDePrueba } from '../datos/datos-de-prueba';
import { EventoDetalleWeb, EventoListaWeb, EventosWeb, SitioWeb } from '../datos/modelos';
import type { EjemploPreinscripcion } from '../editor/casos-vista';
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

  it('guarda la limpieza de la capa y la llama al destruirse', async () => {
    const editor = crear();
    const limpiar = jasmine.createSpy('limpiar');
    activar.and.returnValue(limpiar);
    editor.activarSiToca('1', sitio(true));
    await Promise.resolve();
    await Promise.resolve();
    expect(activar).toHaveBeenCalledTimes(1);
    expect(limpiar).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    expect(limpiar).toHaveBeenCalledTimes(1);
  });

  it('no activa la capa si se destruye antes de que llegue el chunk', async () => {
    const editor = crear();
    editor.activarSiToca('1', sitio(true));
    TestBed.resetTestingModule();
    await Promise.resolve();
    await Promise.resolve();
    expect(activar).not.toHaveBeenCalled();
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

  it('W12c-bis: «Ver el caso» cambia en memoria el sitio, los eventos y el evento; con null, los datos reales', async () => {
    const editor = crear();
    const eventos: EventosWeb = { proximos: [{ id: 1 } as EventoListaWeb], anteriores: [] };
    const evento = { id: 1, fotos: [{ id: 1 }, { id: 2 }] } as EventoDetalleWeb;
    // Sin capa: nada cambia.
    expect(editor.aplicarEventos(eventos)).toBe(eventos);
    expect(editor.aplicarEvento(evento)).toBe(evento);

    editor.activarSiToca('1', sitio(true));
    await Promise.resolve();
    await Promise.resolve();
    const opciones = activar.calls.mostRecent().args[0];
    opciones.alCaso({
      sitio: (s: SitioWeb) => ({ ...s, tarifas: [] }),
      eventos: () => ({ proximos: [], anteriores: [] }),
      evento: (e: EventoDetalleWeb) => ({ ...e, fotos: e.fotos.slice(0, 1) }),
    });
    expect(editor.aplicar(sitio(true)).tarifas).toEqual([]);
    expect(editor.aplicar(sitio(false)).tarifas).toEqual(sitio(false).tarifas);
    expect(editor.aplicarEventos(eventos)).toEqual({ proximos: [], anteriores: [] });
    expect(editor.aplicarEvento(evento).fotos).toEqual([{ id: 1 }]);

    opciones.alCaso(null);
    expect(editor.aplicar(sitio(true)).tarifas).toEqual(sitio(true).tarifas);
    expect(editor.aplicarEventos(eventos)).toBe(eventos);
  });

  it('W12c-ter: sin capa, ni ejemplo de preinscripción ni cambio en la sede; con ella, los del simulador', async () => {
    const editor = crear();
    const vista = { horarios: { proxima: { hora: '17:00' } } };
    const ahora = new Date(2026, 10, 7);
    expect(editor.enEditor()).toBeFalse();
    expect(editor.preinscripcion(sitio(true), ahora)).toBeNull();
    expect(editor.aplicarVistaSede(vista)).toBe(vista);

    editor.activarSiToca('1', sitio(true));
    expect(editor.enEditor()).toBeTrue();
    await Promise.resolve();
    await Promise.resolve();
    const opciones = activar.calls.mostRecent().args[0];
    const ejemplo: EjemploPreinscripcion = { paso: 3, consulta: { sede: 'estepa' }, datos: {}, respuesta: null };
    const preinscripcion = jasmine.createSpy('preinscripcion').and.returnValue(ejemplo);
    opciones.alCaso({
      sitio: (s: SitioWeb) => s,
      eventos: (e: EventosWeb) => e,
      evento: (e: EventoDetalleWeb) => e,
      preinscripcion,
      vistaSede: (v: typeof vista) => ({ ...v, horarios: { ...v.horarios, proxima: null } }),
    });
    expect(editor.preinscripcion(sitio(true), ahora)).toBe(ejemplo);
    expect(preinscripcion).toHaveBeenCalledWith(sitio(true), ahora);
    expect(editor.aplicarVistaSede(vista).horarios.proxima).toBeNull();
    opciones.alCaso(null);
    expect(editor.preinscripcion(sitio(true), ahora)).toBeNull();
    expect(editor.aplicarVistaSede(vista)).toBe(vista);
  });

  it('W12c-ter: fuera del editor (sin editor=1, sin la confirmación de la API o fuera de un iframe) no está en el editor', () => {
    const editor = crear(false);
    editor.activarSiToca('1', sitio(true));
    editor.activarSiToca(null, sitio(true));
    expect(editor.enEditor()).toBeFalse();
  });

  it('W12c-ter: si no llega el chunk de la capa, deja de estar en el editor', async () => {
    const editor = crear();
    cargar.and.rejectWith(new Error('sin red'));
    editor.activarSiToca('1', sitio(true));
    expect(editor.enEditor()).toBeTrue();
    await Promise.resolve();
    await Promise.resolve();
    expect(editor.enEditor()).toBeFalse();
  });
});
