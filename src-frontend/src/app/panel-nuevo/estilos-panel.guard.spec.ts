import { TestBed } from '@angular/core/testing';

import { EstilosPanelService } from './estilos-panel.service';
import { estilosPanelGuard } from './estilos-panel.guard';

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
});
