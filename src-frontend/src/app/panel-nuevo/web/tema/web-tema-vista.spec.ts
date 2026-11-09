import {
  EstadoWebEscuela,
  FICHAS_TEMA,
  PAGINAS_PREVIA,
  TEMAS_CONSTRUIDOS,
  direccionVistaPrevia,
  escala,
  hayCambiosSinPublicar,
  modoBarra,
  temaInicial,
  temaPublicado,
  temasVisibles,
  textoPublicada,
  textoVistaLista,
  textosPublicar,
  tituloVistaPrevia,
} from './web-tema-vista';

function estado(parcial: Partial<EstadoWebEscuela> = {}): EstadoWebEscuela {
  return { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: [], ...parcial };
}

describe('web-tema-vista (W7)', () => {
  it('los cuatro temas llevan el nombre y la descripción del boceto', () => {
    expect(Object.values(FICHAS_TEMA).map((f) => [f.nombre, f.descripcion])).toEqual([
      ['La línea comarcal', 'Las sedes como paradas de una línea, con sus horarios.'],
      ['El chándal del club', 'Franja del color de la escuela y un parche por sede.'],
      ['Una sede por pantalla', 'La foto de cada sede a pantalla completa.'],
      ['El estándar del sector', 'Foto de acción, lema y las secciones de siempre.'],
    ]);
  });

  it('temas visibles: solo los construidos y, si es otro, el publicado (§9 n.º 12)', () => {
    expect(TEMAS_CONSTRUIDOS).toEqual(['linea-comarcal']);
    expect(temasVisibles(null).map((t) => t.id)).toEqual(['linea-comarcal']);
    expect(temasVisibles('linea-comarcal').map((t) => t.id)).toEqual(['linea-comarcal']);
    expect(temasVisibles('estandar').map((t) => t.id)).toEqual(['linea-comarcal', 'estandar']);
  });

  it('tema publicado: solo con la web publicada y un tema conocido; el inicial, el publicado o el primero construido', () => {
    expect(temaPublicado(estado())).toBe('linea-comarcal');
    expect(temaPublicado(estado({ publicada: false }))).toBeNull();
    expect(temaPublicado(estado({ tema: null, publicada: false }))).toBeNull();
    expect(temaPublicado(estado({ tema: 'otro' }))).toBeNull();
    expect(temaInicial('chandal')).toBe('chandal');
    expect(temaInicial(null)).toBe('linea-comarcal');
  });

  it('estados: publicado (sin barra), cambios y primera vez; solo «cambios» avisa al salir', () => {
    expect(modoBarra('linea-comarcal', 'linea-comarcal')).toBe('oculta');
    expect(modoBarra('chandal', 'linea-comarcal')).toBe('cambios');
    expect(modoBarra(null, 'linea-comarcal')).toBe('primera');
    expect(hayCambiosSinPublicar('linea-comarcal', 'linea-comarcal')).toBeFalse();
    expect(hayCambiosSinPublicar('chandal', 'linea-comarcal')).toBeTrue();
    expect(hayCambiosSinPublicar(null, 'linea-comarcal')).toBeFalse();
  });

  it('diálogo con la web publicada', () => {
    expect(textosPublicar('linea-comarcal', 'chandal')).toEqual({
      titulo: '¿Publicar tu web con “La línea comarcal”?',
      mensaje: ['Las familias verán tu web con el tema ', { fuerte: '“La línea comarcal”' }, ' desde ahora, en lugar de “El chándal del club”.'],
      bloques: [{ tipo: 'parrafo', partes: ['Tus sedes, horarios, logo y color no cambian.'] }],
    });
  });

  it('diálogo la primera vez', () => {
    expect(textosPublicar('linea-comarcal', null)).toEqual({
      titulo: '¿Publicar tu web?',
      mensaje: ['Las familias verán tu web con el tema ', { fuerte: '“La línea comarcal”' }, ' desde ahora.'],
      bloques: [{ tipo: 'parrafo', partes: ['Hasta ahora tu web no estaba publicada.'] }],
    });
    expect(textoPublicada('linea-comarcal')).toBe('Tu web ya se ve con “La línea comarcal”.');
  });

  it('páginas del selector, en el orden del boceto', () => {
    expect(PAGINAS_PREVIA.map((p) => p.etiqueta)).toEqual(['Portada', 'Una sede', 'Preinscripción', 'Tarifas', 'Eventos', 'Legales']);
  });

  it('dirección del iframe: la ruta de la plantilla con ?vista-previa=; «Una sede» con el slug o nada', () => {
    expect(direccionVistaPrevia('portada', 'linea-comarcal', null)).toBe('/?vista-previa=linea-comarcal');
    expect(direccionVistaPrevia('sede', 'linea-comarcal', 'casariche')).toBe('/sedes/casariche?vista-previa=linea-comarcal');
    expect(direccionVistaPrevia('sede', 'linea-comarcal', null)).toBeNull();
    expect(direccionVistaPrevia('preinscripcion', 'linea-comarcal', null)).toBe('/preinscripcion?vista-previa=linea-comarcal');
    expect(direccionVistaPrevia('tarifas', 'chandal', null)).toBe('/tarifas?vista-previa=chandal');
    expect(direccionVistaPrevia('eventos', 'linea-comarcal', null)).toBe('/eventos?vista-previa=linea-comarcal');
    expect(direccionVistaPrevia('legales', 'linea-comarcal', null)).toBe('/legales?vista-previa=linea-comarcal');
  });

  it('título del iframe y aviso de lista', () => {
    expect(tituloVistaPrevia('linea-comarcal', 'sede', 'movil')).toBe('Vista previa de tu web: La línea comarcal, una sede, móvil');
    expect(tituloVistaPrevia('linea-comarcal', 'preinscripcion', 'escritorio')).toBe(
      'Vista previa de tu web: La línea comarcal, preinscripción, escritorio'
    );
    expect(textoVistaLista('linea-comarcal', 'portada')).toBe('Vista previa: La línea comarcal, portada.');
  });

  it('escala: al ancho disponible, nunca mayor que 1', () => {
    expect(escala(641, 1282)).toBe(0.5);
    expect(escala(2000, 1282)).toBe(1);
    expect(escala(0, 1282)).toBe(1);
  });
});
