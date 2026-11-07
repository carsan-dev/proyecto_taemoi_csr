import { RECORDATORIO_PANEL } from '../../componentes/vistas/layout/skeleton/skeleton.component';
import { CLAVE_ULTIMA_SEDE } from '../caja-sede/caja-sede-vista';
import { CLAVE_VISTA } from '../alumnos/alumnos.component';
import { CLAVES_LOCALES_DE_ESCUELA, limpiarAlmacenesDeEscuela } from './limpieza-escuela';

describe('Limpieza del navegador al cambiar de escuela', () => {
  const DE_ESCUELA = [
    'dashboard-user-vistos-eventos',
    'dashboard-user-estado-deportes-41',
    'dashboard-user-vistos-documentos-41',
    'taemoi.cajaSede.ultimaSede',
    'panelNuevo',
  ];
  const PREFERENCIAS = ['cookieConsent', 'taemoi_vista_preferida', 'panelAlumnosVista'];
  const guardadas = new Map<string, string | null>();

  beforeEach(() => {
    for (const clave of [...DE_ESCUELA, ...PREFERENCIAS]) {
      guardadas.set(clave, localStorage.getItem(clave));
      localStorage.setItem(clave, `valor-${clave}`);
    }
    sessionStorage.setItem('alumnoId', '41');
  });

  afterEach(() => {
    for (const [clave, valor] of guardadas) {
      if (valor === null) {
        localStorage.removeItem(clave);
      } else {
        localStorage.setItem(clave, valor);
      }
    }
    guardadas.clear();
    sessionStorage.removeItem('alumnoId');
  });

  it('borra lo de la escuela anterior (familias, última sede, recordatorio del panel) y vacía el sessionStorage', () => {
    limpiarAlmacenesDeEscuela(window);
    for (const clave of DE_ESCUELA) {
      expect(localStorage.getItem(clave)).withContext(clave).toBeNull();
    }
    expect(sessionStorage.getItem('alumnoId')).toBeNull();
  });

  it('conserva las preferencias que no son de ninguna escuela (cookies, vista preferida, filas o tarjetas)', () => {
    limpiarAlmacenesDeEscuela(window);
    for (const clave of PREFERENCIAS) {
      expect(localStorage.getItem(clave)).withContext(clave).toBe(`valor-${clave}`);
    }
  });

  it('usa las mismas claves que las pantallas que las guardan', () => {
    expect(CLAVES_LOCALES_DE_ESCUELA).toContain(CLAVE_ULTIMA_SEDE);
    expect(CLAVES_LOCALES_DE_ESCUELA).toContain(RECORDATORIO_PANEL);
    expect(CLAVES_LOCALES_DE_ESCUELA).not.toContain(CLAVE_VISTA);
  });

  it('no lanza sin almacenes utilizables', () => {
    expect(() => limpiarAlmacenesDeEscuela(null)).not.toThrow();
    const bloqueada = {
      get sessionStorage(): Storage {
        throw new Error('bloqueado');
      },
      get localStorage(): Storage {
        throw new Error('bloqueado');
      },
    } as unknown as Window;
    expect(() => limpiarAlmacenesDeEscuela(bloqueada)).not.toThrow();
  });
});
