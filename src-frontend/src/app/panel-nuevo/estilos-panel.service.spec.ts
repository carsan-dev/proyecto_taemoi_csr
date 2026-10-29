import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { EstilosPanelService } from './estilos-panel.service';

describe('EstilosPanelService', () => {
  let servicio: EstilosPanelService;
  let documento: Document;

  const enlace = (nombre: string) => documento.head.querySelector<HTMLLinkElement>(`link[data-${nombre}]`)!;
  const cargarHojas = () => {
    enlace('gestion-compartida').dispatchEvent(new Event('load'));
    enlace('panel-nuevo').dispatchEvent(new Event('load'));
  };

  beforeEach(() => {
    documento = document.implementation.createHTMLDocument();
    TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: documento }] });
    servicio = TestBed.inject(EstilosPanelService);
  });

  it('crea una sola hoja de cada tipo y reutiliza las cargas completadas', async () => {
    const primera = servicio.cargar();
    const segunda = servicio.cargar();
    expect(documento.head.querySelectorAll('link').length).toBe(2);
    expect(enlace('gestion-compartida').getAttribute('href')).toBe('gestion-compartida.css');
    expect(enlace('panel-nuevo').getAttribute('href')).toBe('panel-nuevo.css');
    expect(enlace('gestion-compartida').rel).toBe('stylesheet');
    expect(enlace('panel-nuevo').rel).toBe('stylesheet');
    cargarHojas();
    await Promise.all([primera, segunda]);
    await servicio.cargar();
    await servicio.cargarGestion();
    expect(documento.head.querySelectorAll('link').length).toBe(2);
    expect(documento.head.lastChild).toBe(enlace('panel-nuevo'));
  });

  for (const href of ['styles.css', '/base/styles-ABC123.css', 'https://ejemplo.test/styles-ABC.css?v=1#hoja']) {
    it(`inserta gestión justo después de ${href}, antes de Bootstrap y cualquier style`, async () => {
      const inicial = documento.createElement('link');
      inicial.rel = 'stylesheet';
      inicial.href = href;
      const bootstrap = documento.createElement('link');
      bootstrap.rel = 'stylesheet';
      bootstrap.href = 'bootstrap.css';
      const componente = documento.createElement('style');
      documento.head.append(inicial, bootstrap, componente);
      const carga = servicio.cargarGestion();
      expect(inicial.nextSibling).toBe(enlace('gestion-compartida'));
      expect(enlace('gestion-compartida').nextSibling).toBe(bootstrap);
      expect(bootstrap.nextSibling).toBe(componente);
      enlace('gestion-compartida').dispatchEvent(new Event('load'));
      await carga;
    });
  }

  it('sin styles inicial inserta gestión al principio del head, antes de cualquier style', async () => {
    const componente = documento.createElement('style');
    documento.head.prepend(componente);
    const carga = servicio.cargarGestion();
    expect(documento.head.firstChild).toBe(enlace('gestion-compartida'));
    expect(enlace('gestion-compartida').nextSibling).toBe(componente);
    enlace('gestion-compartida').dispatchEvent(new Event('load'));
    await carga;
    expect(enlace('panel-nuevo')).toBeNull();
  });

  for (const primera of ['gestion-compartida', 'panel-nuevo']) {
    it(`espera las dos hojas aunque ${primera} termine antes`, async () => {
      let completada = false;
      const carga = servicio.cargar().then(() => { completada = true; });
      enlace(primera).dispatchEvent(new Event('load'));
      await Promise.resolve();
      await Promise.resolve();
      expect(completada).toBeFalse();
      cargarHojas();
      await carga;
      expect(completada).toBeTrue();
    });
  }

  for (const fallida of ['gestion-compartida', 'panel-nuevo']) {
    it(`retira y reintenta solo ${fallida} si falla`, async () => {
      const conservada = fallida === 'gestion-compartida' ? 'panel-nuevo' : 'gestion-compartida';
      const carga = servicio.cargar();
      const hojaConservada = enlace(conservada);
      hojaConservada.dispatchEvent(new Event('load'));
      enlace(fallida).dispatchEvent(new Event('error'));
      await expectAsync(carga).toBeRejected();
      expect(enlace(fallida)).toBeNull();
      const reintento = servicio.cargar();
      expect(enlace(conservada)).toBe(hojaConservada);
      enlace(fallida).dispatchEvent(new Event('load'));
      await expectAsync(reintento).toBeResolved();
      expect(documento.head.querySelectorAll('link').length).toBe(2);
    });
  }

  it('reutiliza gestión solicitada antes por su guard al cargar después el panel', async () => {
    const gestion = servicio.cargarGestion();
    const hoja = enlace('gestion-compartida');
    const panel = servicio.cargar();
    expect(enlace('gestion-compartida')).toBe(hoja);
    expect(documento.head.querySelectorAll('link').length).toBe(2);
    cargarHojas();
    await Promise.all([gestion, panel]);
  });
});
