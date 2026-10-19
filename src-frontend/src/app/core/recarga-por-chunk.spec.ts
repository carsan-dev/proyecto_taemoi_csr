import {
  CLAVE_RECARGA_CHUNK,
  RecargaPorChunkErrorHandler,
  VENTANA_RECARGA_MS,
  esErrorDeCargaDeChunk,
  recargarUnaVezPorChunk,
} from './recarga-por-chunk';

class AlmacenFalso {
  datos = new Map<string, string>();
  getItem(clave: string): string | null {
    return this.datos.has(clave) ? this.datos.get(clave)! : null;
  }
  setItem(clave: string, valor: string): void {
    this.datos.set(clave, valor);
  }
}

describe('recarga por chunk', () => {
  describe('esErrorDeCargaDeChunk', () => {
    it('reconoce los mensajes de Chrome, Firefox y ChunkLoadError', () => {
      expect(esErrorDeCargaDeChunk(new TypeError('Failed to fetch dynamically imported module: https://x/chunk-AB.js'))).toBeTrue();
      expect(esErrorDeCargaDeChunk(new TypeError('error loading dynamically imported module: https://x/chunk.js'))).toBeTrue();
      const chunk = new Error('Loading chunk 12 failed.');
      chunk.name = 'ChunkLoadError';
      expect(esErrorDeCargaDeChunk(chunk)).toBeTrue();
      expect(esErrorDeCargaDeChunk('Importing a module script failed.')).toBeTrue();
    });

    it('mira dentro de rejection y error (promesas y NavigationError)', () => {
      expect(esErrorDeCargaDeChunk({ rejection: new TypeError('Failed to fetch dynamically imported module: a') })).toBeTrue();
      expect(esErrorDeCargaDeChunk({ error: new TypeError('Failed to fetch dynamically imported module: a') })).toBeTrue();
    });

    it('ignora otros errores', () => {
      expect(esErrorDeCargaDeChunk(new Error('Cannot read properties of undefined'))).toBeFalse();
      expect(esErrorDeCargaDeChunk(null)).toBeFalse();
      expect(esErrorDeCargaDeChunk(undefined)).toBeFalse();
    });
  });

  describe('recargarUnaVezPorChunk', () => {
    it('recarga y apunta la hora la primera vez', () => {
      const almacen = new AlmacenFalso();
      const recargar = jasmine.createSpy('recargar');
      expect(recargarUnaVezPorChunk(almacen, 1_000_000, recargar)).toBeTrue();
      expect(recargar).toHaveBeenCalledTimes(1);
      expect(almacen.getItem(CLAVE_RECARGA_CHUNK)).toBe('1000000');
    });

    it('no vuelve a recargar dentro de la ventana (evita bucles)', () => {
      const almacen = new AlmacenFalso();
      almacen.setItem(CLAVE_RECARGA_CHUNK, '1000000');
      const recargar = jasmine.createSpy('recargar');
      expect(recargarUnaVezPorChunk(almacen, 1_000_000 + VENTANA_RECARGA_MS - 1, recargar)).toBeFalse();
      expect(recargar).not.toHaveBeenCalled();
    });

    it('vuelve a recargar pasada la ventana', () => {
      const almacen = new AlmacenFalso();
      almacen.setItem(CLAVE_RECARGA_CHUNK, '1000000');
      const recargar = jasmine.createSpy('recargar');
      expect(recargarUnaVezPorChunk(almacen, 1_000_000 + VENTANA_RECARGA_MS + 1, recargar)).toBeTrue();
      expect(recargar).toHaveBeenCalledTimes(1);
    });

    it('sin sessionStorage utilizable no recarga (no puede evitar el bucle)', () => {
      const roto = {
        getItem: () => { throw new Error('SecurityError'); },
        setItem: () => { throw new Error('SecurityError'); },
      };
      const recargar = jasmine.createSpy('recargar');
      expect(recargarUnaVezPorChunk(roto, 5, recargar)).toBeFalse();
      expect(recargarUnaVezPorChunk(null, 5, recargar)).toBeFalse();
      expect(recargar).not.toHaveBeenCalled();
    });
  });

  describe('RecargaPorChunkErrorHandler', () => {
    it('recarga ante un error de chunk y delega los demás', () => {
      const recargar = jasmine.createSpy('recargar').and.returnValue(true);
      const handler = new RecargaPorChunkErrorHandler(recargar);
      const consola = spyOn(console, 'error');

      handler.handleError({ rejection: new TypeError('Failed to fetch dynamically imported module: x') });
      expect(recargar).toHaveBeenCalledTimes(1);
      expect(consola).not.toHaveBeenCalled();

      handler.handleError(new Error('otro fallo'));
      expect(recargar).toHaveBeenCalledTimes(1);
      expect(consola).toHaveBeenCalled();
    });

    it('si no recarga (ventana), registra el error como siempre', () => {
      const recargar = jasmine.createSpy('recargar').and.returnValue(false);
      const handler = new RecargaPorChunkErrorHandler(recargar);
      const consola = spyOn(console, 'error');
      handler.handleError(new TypeError('Failed to fetch dynamically imported module: x'));
      expect(consola).toHaveBeenCalled();
    });
  });
});
