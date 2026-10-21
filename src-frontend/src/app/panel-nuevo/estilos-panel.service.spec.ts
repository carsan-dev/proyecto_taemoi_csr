import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { EstilosPanelService } from './estilos-panel.service';

describe('EstilosPanelService', () => {
  let service: EstilosPanelService;
  let doc: Document;

  const enlaces = () => Array.from(doc.head.querySelectorAll('link[data-panel-nuevo]')) as HTMLLinkElement[];

  beforeEach(() => {
    TestBed.configureTestingModule({});
    doc = TestBed.inject(DOCUMENT);
    service = TestBed.inject(EstilosPanelService);
    enlaces().forEach((l) => l.remove());
  });

  afterEach(() => enlaces().forEach((l) => l.remove()));

  it('crea un solo link a panel-nuevo.css aunque se llame dos veces', async () => {
    const p1 = service.cargar();
    const p2 = service.cargar();
    expect(enlaces().length).toBe(1);
    const link = enlaces()[0];
    expect(link.rel).toBe('stylesheet');
    expect(link.getAttribute('href')).toContain('panel-nuevo.css');
    link.dispatchEvent(new Event('load'));
    await Promise.all([p1, p2]);
    expect(enlaces().length).toBe(1);
  });

  it('resuelve en load y las llamadas posteriores no añaden otro link', async () => {
    const p = service.cargar();
    enlaces()[0].dispatchEvent(new Event('load'));
    await expectAsync(p).toBeResolved();
    await expectAsync(service.cargar()).toBeResolved();
    expect(enlaces().length).toBe(1);
  });

  it('rechaza en error, retira el link fallido y deja reintentar', async () => {
    const p = service.cargar();
    enlaces()[0].dispatchEvent(new Event('error'));
    await expectAsync(p).toBeRejected();
    expect(enlaces().length).toBe(0);

    const reintento = service.cargar();
    expect(enlaces().length).toBe(1);
    enlaces()[0].dispatchEvent(new Event('load'));
    await expectAsync(reintento).toBeResolved();
  });
});
