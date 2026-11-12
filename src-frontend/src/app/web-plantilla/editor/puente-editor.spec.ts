import {
  CANAL_EDITOR_WEB,
  CASOS_VISTA,
  PASOS_PREINSCRIPCION,
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

    it('W12c-bis: el caso del panel, de la lista cerrada o null; nada de datos libres', () => {
      const caso = (valor: unknown) => ({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: valor });
      for (const id of CASOS_VISTA) {
        expect(esMensajeDelPanel(caso(id))).toBeTrue();
      }
      expect(esMensajeDelPanel(caso(null))).toBeTrue();
      expect(esMensajeDelPanel(caso('desconocido'))).toBeFalse();
      expect(esMensajeDelPanel(caso({ tarifas: [] }))).toBeFalse();
      expect(esMensajeDelPanel({ canal: CANAL_EDITOR_WEB, tipo: 'caso' })).toBeFalse();
      expect(CASOS_VISTA).toEqual([
        'lleno',
        'espera',
        'sin-tarifas',
        'sin-proximos',
        'sin-eventos',
        'un-evento',
        'sin-anteriores',
        'una-foto',
        // W12c-ter
        'sin-clases',
        'sin-grupo',
        'fin-espera',
      ]);
    });

    it('W12c-ter: el paso de la preinscripción, un entero de la lista cerrada 1-4; nada de datos libres', () => {
      const paso = (valor: unknown) => ({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: valor });
      for (const n of PASOS_PREINSCRIPCION) {
        expect(esMensajeDelPanel(paso(n))).toBeTrue();
      }
      expect(PASOS_PREINSCRIPCION).toEqual([1, 2, 3, 4]);
      for (const malo of [0, 5, -1, 2.5, Number.NaN, Number.POSITIVE_INFINITY, '2', null, undefined, [3], { paso: 3 }, true]) {
        expect(esMensajeDelPanel(paso(malo))).withContext(String(malo)).toBeFalse();
      }
      expect(esMensajeDelPanel({ canal: CANAL_EDITOR_WEB, tipo: 'paso' })).toBeFalse();
      expect(esMensajeDelPanel({ canal: 'otro', tipo: 'paso', paso: 3 })).toBeFalse();
      // El iframe no lo acepta del lado contrario.
      expect(esMensajeDelEditor(paso(3))).toBeFalse();
    });

    it('W12c-bis: «mostrar» con un número y el destino de Preinscripciones · Plantillas', () => {
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 1200 })).toBeTrue();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: '1200' })).toBeFalse();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: -5 })).toBeFalse();
      expect(esMensajeDelEditor({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'plantillas' })).toBeTrue();
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

describe('puente del editor de la web: fotos sueltas (W12f-1)', () => {
  const c = CANAL_EDITOR_WEB;
  const archivo = new File(['x'], 'a.jpg', { type: 'image/jpeg' });

  it('del panel: estado de las fotos y «describir», con fotos sueltas, de la galería (W12f-2a) y de profesores (W12f-2b)', () => {
    const subidas = { 'portada.foto': { estado: 'subiendo', pct: 40, nombre: 'a.jpg' }, 'texto_foto.2.foto': { estado: 'error', titulo: 'T', msg: 'M' } };
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['presentacion.foto'], anteriores: [], subidas })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['galeria.1'], anteriores: [], subidas: {} })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: [], anteriores: [], subidas: { 'galeria.nueva': subidas['portada.foto'] } })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['galeria.nueva'], anteriores: [], subidas: {} })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['profesor.1.foto'], anteriores: [], subidas: {} })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: [], anteriores: [], subidas: { 'profesor.1.foto': subidas['portada.foto'] } })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['profesor.0.foto'], anteriores: [], subidas: {} })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: ['profesor.1.nombre'], anteriores: [], subidas: {} })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'fotos', cambiadas: [], anteriores: [], subidas: { 'portada.foto': { estado: 'subiendo', pct: 140, nombre: 'a' } } })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'describir', clave: 'portada.foto' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'describir', clave: 'galeria.1' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'describir', clave: 'galeria.nueva' })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'ir-texto', clave: 'texto_foto.3.foto.alt' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'ir-texto', clave: 'galeria.1.alt' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'ir-texto', clave: 'profesor.1.foto.alt' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'ir-texto', clave: 'profesor.1.permiso' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'ir-texto', clave: 'profesor.100.permiso' })).toBeFalse();
  });

  it('quitar un profesor y el foco de vuelta a su «Quitar» (`quitar:<n>`); `foto:<n>` ya no es un foco', () => {
    expect(esMensajeDelEditor({ canal: c, tipo: 'profesor', accion: 'quitar', n: 3 })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'profesor', accion: 'quitar', n: 0 })).toBeFalse();
    expect(esMensajeDelPanel({ canal: c, tipo: 'foco-profesor', foco: 'quitar:3' })).toBeTrue();
    expect(esMensajeDelPanel({ canal: c, tipo: 'foco-profesor', foco: 'foto:3' })).toBeFalse();
  });

  it('de la capa: el fichero, las acciones y la descripción (máximo 150)', () => {
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'portada.foto', archivo })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'portada.foto', archivo: 'a.jpg' })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'galeria.nueva', archivo })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'galeria.2', archivo })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'profesor.1.foto', archivo })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'subir-foto', clave: 'profesor.1.nombre', archivo })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'profesor.1.foto', accion: 'quitar' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'profesor.1.foto', accion: 'antes' })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'presentacion.foto', accion: 'quitar' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'presentacion.foto', accion: 'antes' })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'galeria.3', accion: 'antes' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'galeria.3', accion: 'despues' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'galeria.3', accion: 'quitar' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'galeria.nueva', accion: 'cancelar-subida' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'foto', clave: 'galeria.nueva', accion: 'quitar' })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'descripcion', clave: 'galeria.3', texto: 'x' })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'descripcion', clave: 'galeria.nueva', texto: 'x' })).toBeFalse();
    expect(esMensajeDelEditor({ canal: c, tipo: 'descripcion', clave: 'portada.foto', texto: 'x'.repeat(150) })).toBeTrue();
    expect(esMensajeDelEditor({ canal: c, tipo: 'descripcion', clave: 'portada.foto', texto: 'x'.repeat(151) })).toBeFalse();
  });
});
