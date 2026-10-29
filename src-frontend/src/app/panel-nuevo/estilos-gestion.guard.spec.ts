import { DOCUMENT } from '@angular/common';
import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';

import { enPantallaNueva } from './capas/capa-modal';
import { estilosGestionGuard } from './estilos-gestion.guard';
import { TIEMPO_MAXIMO_ESTILOS_MS } from './esperar-estilos';

describe('estilosGestionGuard', () => {
  let documento: Document;
  const decidir = () => TestBed.runInInjectionContext(() => estilosGestionGuard());
  const enlace = () => documento.head.querySelector('link[data-gestion-compartida]')!;

  beforeEach(() => {
    documento = document.implementation.createHTMLDocument();
    TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: documento }] });
  });

  it('espera solo gestión sin cargar panel-nuevo ni marcar la pantalla como nueva', fakeAsync(() => {
    let resultado: boolean | undefined;
    decidir().then((valor) => { resultado = valor; });
    flushMicrotasks();
    expect(resultado).toBeUndefined();
    expect(documento.head.querySelector('link[data-panel-nuevo]')).toBeNull();
    enlace().dispatchEvent(new Event('load'));
    flushMicrotasks();
    expect(resultado).toBeTrue();
    const router = {
      routerState: { snapshot: { root: { routeConfig: { canMatch: [estilosGestionGuard] }, firstChild: null } } },
    } as unknown as Router;
    expect(enPantallaNueva(router)).toBeFalse();
  }));

  it('abre también si la hoja falla', async () => {
    const resultado = decidir();
    enlace().dispatchEvent(new Event('error'));
    expect(await resultado).toBeTrue();
  });

  it('abre con el mismo tiempo máximo si la hoja no emite eventos', fakeAsync(() => {
    let resultado: boolean | undefined;
    decidir().then((valor) => { resultado = valor; });
    tick(TIEMPO_MAXIMO_ESTILOS_MS - 1);
    expect(resultado).toBeUndefined();
    tick(1);
    expect(resultado).toBeTrue();
    expect(documento.head.querySelector('link[data-panel-nuevo]')).toBeNull();
    expect(enlace()).not.toBeNull();
  }));
});
