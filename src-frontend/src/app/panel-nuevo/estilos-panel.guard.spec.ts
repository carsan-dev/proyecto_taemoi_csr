import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';

import { EstilosPanelService } from './estilos-panel.service';
import { estilosPanelGuard } from './estilos-panel.guard';
import { TIEMPO_MAXIMO_ESTILOS_MS } from './esperar-estilos';

describe('estilosPanelGuard (8b-Z): la hoja del panel antes de la pantalla', () => {
  let cargar: jasmine.Spy<() => Promise<void>>;

  beforeEach(() => {
    cargar = jasmine.createSpy('cargar').and.resolveTo();
    TestBed.configureTestingModule({ providers: [{ provide: EstilosPanelService, useValue: { cargar } }] });
  });

  const decidir = () => TestBed.runInInjectionContext(() => estilosPanelGuard());

  it('true solo después de cargar la hoja del panel nuevo', async () => {
    let resolverHoja!: () => void;
    cargar.and.returnValue(new Promise<void>((r) => (resolverHoja = r)));

    let decidido: boolean | undefined;
    const promesa = decidir().then((v) => (decidido = v));
    await Promise.resolve();
    expect(cargar).toHaveBeenCalledTimes(1);
    expect(decidido).toBeUndefined();

    resolverHoja();
    await promesa;
    expect(decidido).toBeTrue();
  });

  it('error de la hoja: la pantalla se abre igual (ya no hay otra a la que pasar)', async () => {
    cargar.and.rejectWith(new Error('No se pudo cargar panel-nuevo.css'));
    expect(await decidir()).toBeTrue();
  });

  it('abre al agotar el tiempo máximo sin load ni error', fakeAsync(() => {
    cargar.and.returnValue(new Promise<void>(() => {}));
    let resultado: boolean | undefined;
    decidir().then((valor) => { resultado = valor; });
    tick(TIEMPO_MAXIMO_ESTILOS_MS - 1);
    expect(resultado).toBeUndefined();
    tick(1);
    expect(resultado).toBeTrue();
  }));

  it('con el servicio real espera las dos hojas antes de abrir y cancela el temporizador', fakeAsync(() => {
    const documento = document.implementation.createHTMLDocument();
    TestBed.overrideProvider(DOCUMENT, { useValue: documento });
    TestBed.overrideProvider(EstilosPanelService, { useFactory: () => new EstilosPanelService() });
    let resultado: boolean | undefined;
    decidir().then((valor) => { resultado = valor; });
    documento.head.querySelector('link[data-panel-nuevo]')!.dispatchEvent(new Event('load'));
    flushMicrotasks();
    expect(resultado).toBeUndefined();
    documento.head.querySelector('link[data-gestion-compartida]')!.dispatchEvent(new Event('load'));
    flushMicrotasks();
    expect(resultado).toBeTrue();
  }));
});
