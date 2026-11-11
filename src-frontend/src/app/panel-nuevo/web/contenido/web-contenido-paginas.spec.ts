import { CASOS_VISTA } from '../../../web-plantilla/editor/puente-editor';
import {
  CASOS_PAGINA,
  PAGINAS_CONTENIDO,
  direccionEditor,
  esPaginaContenido,
  nombrePagina,
  notaPagina,
  opcionesEvento,
  opcionesSede,
  primeraOpcion,
  tituloMenuCasos,
  vozCaso,
  vozPagina,
} from './web-contenido-vista';

describe('web-contenido-vista: páginas y casos (W12c-bis)', () => {
  it('las siete páginas del boceto, con sus nombres literales', () => {
    expect(PAGINAS_CONTENIDO.map((p) => p.opcion)).toEqual([
      'Inicio, con cabecera y pie',
      'Una sede',
      'Tarifas',
      'Eventos',
      'Un evento',
      'Legales',
      'Preinscripción',
    ]);
    expect(PAGINAS_CONTENIDO.map((p) => p.nombre)).toEqual(['Inicio', 'Una sede', 'Tarifas', 'Eventos', 'Un evento', 'Legales', 'Preinscripción']);
    expect(esPaginaContenido('tarifas')).toBeTrue();
    expect(esPaginaContenido('panel')).toBeFalse();
    expect(esPaginaContenido(null)).toBeFalse();
  });

  it('dirección del iframe de cada página, siempre en modo editor; sede y evento codificados y sin ellos, null', () => {
    const t = 'linea-comarcal';
    expect(direccionEditor(t, 'inicio')).toBe('/?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'tarifas')).toBe('/tarifas?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'eventos')).toBe('/eventos?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'legales')).toBe('/legales?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'preinscripcion')).toBe('/preinscripcion?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'sede', 'roda de/x')).toBe('/sedes/roda%20de%2Fx?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'evento', '12')).toBe('/eventos/12?vista-previa=linea-comarcal&editor=1');
    expect(direccionEditor(t, 'sede', null)).toBeNull();
    expect(direccionEditor(t, 'evento', null)).toBeNull();
  });

  it('sedes: la que no tiene dirección de página sale «(sin página)» y no se elige; se ve la primera con página', () => {
    const opciones = opcionesSede([
      { nombre: 'Lora de Estepa', slug: null },
      { nombre: 'Herrera', slug: 'herrera' },
    ]);
    expect(opciones).toEqual([
      { valor: '', texto: 'Lora de Estepa (sin página)', desactivada: true },
      { valor: 'herrera', texto: 'Herrera', desactivada: false },
    ]);
    expect(primeraOpcion(opciones)).toBe('herrera');
    expect(primeraOpcion(opcionesSede([{ nombre: 'Lora', slug: null }]))).toBeNull();
  });

  it('eventos: «Título (día mes)»', () => {
    expect(
      opcionesEvento([
        { id: 4, titulo: 'Examen de cinturones', fecha: '2026-11-14' },
        { id: 9, titulo: 'Exhibición', fecha: '2026-12-02' },
        { id: 10, titulo: 'Sin fecha', fecha: null },
      ]).map((o) => [o.valor, o.texto]),
    ).toEqual([
      ['4', 'Examen de cinturones (14 nov)'],
      ['9', 'Exhibición (2 dic)'],
      ['10', 'Sin fecha'],
    ]);
  });

  it('nota de la página: en sede con las sedes sin página; en evento, la del boceto; en las demás, ninguna', () => {
    expect(notaPagina('sede', ['Lora de Estepa'])).toBe(
      'Sus textos valen para todas las sedes. Una sede sin dirección de página (Lora de Estepa) no tiene página: se la pones en Sedes.',
    );
    expect(notaPagina('evento', [])).toBe(
      'Sus textos valen para todos los eventos. El nombre, la fecha, las fotos y los documentos se cambian en Eventos.',
    );
    expect(notaPagina('inicio', ['Lora'])).toBeNull();
  });

  it('casos por página: los del boceto que el tema puede enseñar, todos de la lista cerrada del puente', () => {
    const ids = (p: keyof typeof CASOS_PAGINA) => CASOS_PAGINA[p].map((c) => c.id);
    expect(ids('inicio')).toEqual(['lleno', 'espera']);
    expect(ids('sede')).toEqual(['lleno', 'espera', 'sin-clases']);
    expect(ids('tarifas')).toEqual(['sin-tarifas']);
    expect(ids('eventos')).toEqual(['sin-proximos', 'sin-eventos', 'un-evento', 'sin-anteriores']);
    expect(ids('evento')).toEqual(['una-foto']);
    expect(ids('legales')).toEqual([]);
    expect(ids('preinscripcion')).toEqual(['sin-grupo', 'espera', 'fin-espera']);
    for (const lista of Object.values(CASOS_PAGINA)) {
      for (const c of lista) {
        expect((CASOS_VISTA as readonly string[]).includes(c.id)).toBeTrue();
      }
    }
    expect(CASOS_PAGINA.eventos[2]).toEqual({ id: 'un-evento', nombre: 'Un solo evento', cuando: 'La cuenta dice «1 evento»' });
  });

  it('textos de voz y del menú', () => {
    expect(nombrePagina('preinscripcion')).toBe('Preinscripción');
    expect(vozPagina('sede')).toBe('Vista previa de Una sede.');
    expect(tituloMenuCasos('eventos')).toBe('Ver un caso de Eventos');
    expect(vozCaso(CASOS_PAGINA.tarifas[0])).toBe('Viendo: Sin tarifas publicadas. No es un cambio y no se guarda.');
  });
});
