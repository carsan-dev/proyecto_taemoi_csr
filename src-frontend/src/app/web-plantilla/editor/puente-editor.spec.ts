import {
  CANAL_EDITOR_WEB,
  enviarPorPuente,
  esMensajeDelEditor,
  esMensajeDelPanel,
  escucharPuente,
} from './puente-editor';

describe('puente del editor de la web (W12c)', () => {
  const borrador = { canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [{ clave: 'portada.titular', texto: 'Hola', imagenUrl: null, orden: 0, oculto: false }] };

  describe('formas', () => {
    it('del panel: solo el borrador con filas válidas y acento hex', () => {
      expect(esMensajeDelPanel(borrador)).toBeTrue();
      expect(esMensajeDelPanel({ ...borrador, acento: { acento: '#1663b4', borde: '#678cb9', suave: '#e4f0ff' } })).toBeTrue();
      expect(esMensajeDelPanel({ ...borrador, acento: { acento: 'red;}', borde: '#678cb9', suave: '#e4f0ff' } })).toBeFalse();
      expect(esMensajeDelPanel({ ...borrador, canal: 'otro' })).toBeFalse();
      expect(esMensajeDelPanel({ ...borrador, tipo: 'guardar' })).toBeFalse();
      expect(esMensajeDelPanel({ ...borrador, bloques: [{ clave: 1, texto: null, imagenUrl: null, orden: 0 }] })).toBeFalse();
      expect(esMensajeDelPanel({ ...borrador, bloques: [{ clave: 'a', texto: null, imagenUrl: null, orden: Number.NaN }] })).toBeFalse();
      expect(esMensajeDelPanel('borrador')).toBeFalse();
      expect(esMensajeDelPanel(null)).toBeFalse();
    });

    it('del editor: listo, alto y un destino de la lista cerrada', () => {
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'listo' })).toBeTrue();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 2400 })).toBeTrue();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: -1 })).toBeFalse();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'sedes' })).toBeTrue();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: '/plataformaErrores' })).toBeFalse();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'navegar', url: 'https://ejemplo.es' })).toBeFalse();
      expect(esMensajeDelEditor({ tipo: 'listo' })).toBeFalse();
    });
  });

  describe('escuchar', () => {
    let marco: HTMLIFrameElement;
    let recibidos: unknown[];
    let parar: () => void;

    beforeEach(() => {
      marco = document.createElement('iframe');
      document.body.appendChild(marco);
      recibidos = [];
      parar = escucharPuente(window, () => marco.contentWindow, esMensajeDelPanel, (m) => recibidos.push(m));
    });

    afterEach(() => {
      parar();
      marco.remove();
    });

    const llegar = (data: unknown, origin: string, source: Window | null) =>
      window.dispatchEvent(new MessageEvent('message', { data, origin, source }));

    it('atiende el mensaje del mismo origen, de la ventana esperada y con forma válida', () => {
      llegar(borrador, location.origin, marco.contentWindow);
      expect(recibidos).toEqual([borrador]);
    });

    it('ignora otro origen, otra ventana, sin ventana o una forma desconocida', () => {
      llegar(borrador, 'https://otra.example', marco.contentWindow);
      llegar(borrador, location.origin, window);
      llegar(borrador, location.origin, null);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'raro' }, location.origin, marco.contentWindow);
      expect(recibidos).toEqual([]);
    });

    it('deja de escuchar', () => {
      parar();
      llegar(borrador, location.origin, marco.contentWindow);
      expect(recibidos).toEqual([]);
    });
  });

  it('envía siempre al propio origen, nunca a «*»', () => {
    const destino = jasmine.createSpyObj<Window>('destino', ['postMessage']);
    const mensaje = { canal: CANAL_EDITOR_WEB, tipo: 'listo' } as const;
    expect(enviarPorPuente(window, destino, mensaje)).toBeTrue();
    const llamada = (destino.postMessage as jasmine.Spy).calls;
    expect(llamada.count()).toBe(1);
    expect(llamada.mostRecent().args).toEqual([mensaje, location.origin]);
    expect(llamada.mostRecent().args[1]).not.toBe('*');
    expect(enviarPorPuente(window, null, mensaje)).toBeFalse();
  });
});
