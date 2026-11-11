import { DESTINOS_PANEL } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import {
  BloqueContenido,
  RUTA_DESTINO,
  acentoDe,
  bloquesParaVistaPrevia,
  direccionEditor,
  escalaVista,
  nombrePublicada,
  temaDeLaVista,
  textoMarco,
} from './web-contenido-vista';

const estado = (parcial: Partial<EstadoWebEscuela> = {}): EstadoWebEscuela => ({
  tema: 'linea-comarcal',
  publicada: true,
  publicadaEn: '2026-11-01T10:00:00Z',
  temasDisponibles: ['linea-comarcal'],
  ...parcial,
});

describe('web-contenido-vista (W12c)', () => {
  it('estado: publicada con el tema entre comillas; sin publicar, null', () => {
    expect(nombrePublicada(estado())).toBe('«La línea comarcal»');
    expect(nombrePublicada(estado({ publicada: false, tema: null, publicadaEn: null }))).toBeNull();
  });

  it('tema de la vista previa: el publicado o, sin publicar, el primero construido', () => {
    expect(temaDeLaVista(estado())).toBe('linea-comarcal');
    expect(temaDeLaVista(estado({ publicada: false, tema: null }))).toBe('linea-comarcal');
  });

  it('iframe en modo editor', () => {
    expect(direccionEditor('linea-comarcal')).toBe('/?vista-previa=linea-comarcal&editor=1');
  });

  it('escala: nunca mayor que 1 y el rótulo del marco con el porcentaje solo si no cabe', () => {
    expect(escalaVista(1500, 1200)).toBe(1);
    expect(escalaVista(744, 1200)).toBeCloseTo(0.62, 5);
    expect(escalaVista(0, 1200)).toBe(1);
    expect(textoMarco('escritorio', 0.62)).toBe('Escritorio, 1200 px · al 62 %');
    expect(textoMarco('escritorio', 1)).toBe('Escritorio, 1200 px');
    expect(textoMarco('movil', 0.996)).toBe('Móvil, 390 px');
    expect(textoMarco('movil', 0.82)).toBe('Móvil, 390 px · al 82 %');
  });

  it('el borrador que viaja al iframe no lleva ids de imagen ni la constancia del permiso', () => {
    const fila: BloqueContenido = {
      clave: 'profesor.1.nombre',
      texto: 'Ana',
      imagenId: 7,
      imagenUrl: null,
      orden: 0,
      oculto: false,
      permiso: { origen: 'PANEL', por: 'María Ruiz', en: '2026-11-10T10:00:00' },
    };
    expect(bloquesParaVistaPrevia([fila])).toEqual([{ clave: 'profesor.1.nombre', texto: 'Ana', imagenUrl: null, orden: 0, oculto: false }]);
  });

  it('cada destino de candado tiene su pantalla del panel', () => {
    expect(RUTA_DESTINO).toEqual({
      sedes: '/sedes',
      grupos: '/gruposListar',
      eventos: '/eventosListar',
      productos: '/productosListar',
      'datos-escuela': '/datos-escuela',
    });
    expect(Object.keys(RUTA_DESTINO).sort()).toEqual([...DESTINOS_PANEL].sort());
  });

  it('acento del panel: solo hex de 6 cifras', () => {
    const estilo = (v: Record<string, string>) => ({ getPropertyValue: (n: string) => v[n] ?? '' });
    expect(acentoDe(estilo({ '--pn-acento': ' #1663b4', '--pn-acento-borde': '#678cb9', '--pn-acento-suave': '#e4f0ff' }))).toEqual({
      acento: '#1663b4',
      borde: '#678cb9',
      suave: '#e4f0ff',
    });
    expect(acentoDe(estilo({ '--pn-acento': 'rgb(1 2 3)', '--pn-acento-borde': '#678cb9', '--pn-acento-suave': '#e4f0ff' }))).toBeNull();
  });
});
