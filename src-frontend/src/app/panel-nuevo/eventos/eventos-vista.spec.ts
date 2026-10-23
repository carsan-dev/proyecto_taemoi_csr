import {
  IMAGEN_POR_DEFECTO,
  cuerpoCrear,
  cuerpoEditar,
  estadoFecha,
  etiquetaEstado,
  fechaCorta,
  fechaParaCampo,
  fechaTitulo,
  mensajesError,
  moverEn,
  nombreDescarga,
  resumenEventos,
  tamanoArchivo,
  urlImagen,
} from './eventos-vista';

describe('eventos-vista (8b-3f)', () => {
  const hoy = new Date(2026, 9, 22, 10, 0);

  it('estadoFecha: próximo, hoy, realizado y sin fecha, con la fecha en texto o en lista como hoy', () => {
    expect(estadoFecha('2026-10-23', hoy)).toBe('proximo');
    expect(estadoFecha('2026-10-22', hoy)).toBe('hoy');
    expect(estadoFecha([2026, 10, 22], hoy)).toBe('hoy');
    expect(estadoFecha('2026-10-21', hoy)).toBe('realizado');
    expect(estadoFecha(null, hoy)).toBe('sin-fecha');
    expect(estadoFecha('', hoy)).toBe('sin-fecha');
    expect(estadoFecha('2026-02-30', hoy)).toBe('sin-fecha');
    expect(estadoFecha('22/10/2026', hoy)).toBe('sin-fecha');
  });

  it('etiquetaEstado: los textos de hoy', () => {
    expect(etiquetaEstado('proximo')).toBe('Próximo');
    expect(etiquetaEstado('hoy')).toBe('Hoy');
    expect(etiquetaEstado('realizado')).toBe('Realizado');
    expect(etiquetaEstado('sin-fecha')).toBe('Sin fecha');
  });

  it('fechaCorta y fechaTitulo: «14 nov 2026» y «Próximo: Sábado, 14 de noviembre de 2026», como hoy', () => {
    expect(fechaCorta('2026-11-14')).toBe('14 nov 2026');
    expect(fechaCorta(null)).toBeNull();
    expect(fechaTitulo('2026-11-14', hoy)).toBe('Próximo: Sábado, 14 de noviembre de 2026');
    expect(fechaTitulo(null, hoy)).toBeNull();
  });

  it('fechaParaCampo: AAAA-MM-DD desde el texto o la lista de hoy; vacío si no hay', () => {
    expect(fechaParaCampo('2026-11-14')).toBe('2026-11-14');
    expect(fechaParaCampo([2026, 3, 5])).toBe('2026-03-05');
    expect(fechaParaCampo(null)).toBe('');
    expect(fechaParaCampo(undefined)).toBe('');
  });

  it('urlImagen: la de hoy con «w» y «v» (id de la foto); la ampliada sin «w»; sin foto, la imagen por defecto', () => {
    const evento = { fotoEvento: { id: 7, nombre: 'a.webp', url: 'https://x.test/img/a.webp?w=100' } };
    expect(urlImagen(evento, 480)).toBe('https://x.test/img/a.webp?w=480&v=7');
    expect(urlImagen(evento, 920)).toBe('https://x.test/img/a.webp?w=920&v=7');
    expect(urlImagen(evento, null)).toBe('https://x.test/img/a.webp?w=100&v=7');
    expect(urlImagen({ fotoEvento: { nombre: 'b c.webp', url: '/i/b' } }, 480)).toBe('/i/b?w=480&v=b%20c.webp');
    expect(urlImagen({ fotoEvento: null }, 480)).toBe(IMAGEN_POR_DEFECTO);
    expect(urlImagen({ fotoEvento: null }, null)).toBeNull();
  });

  it('moverEn: una lista nueva con el elemento movido; fuera de rango, la misma', () => {
    const lista = [1, 2, 3, 4];
    expect(moverEn(lista, 0, 2)).toEqual([2, 3, 1, 4]);
    expect(moverEn(lista, 3, 2)).toEqual([1, 2, 4, 3]);
    expect(lista).toEqual([1, 2, 3, 4]);
    expect(moverEn(lista, 0, -1)).toBe(lista);
    expect(moverEn(lista, 3, 4)).toBe(lista);
  });

  it('nombreDescarga: el nombre de hoy; sin extensión, la del tipo; sin nombre, «documento»', () => {
    expect(nombreDescarga('Circular.pdf', 'application/pdf')).toBe('Circular.pdf');
    expect(nombreDescarga('Autorización', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')).toBe(
      'Autorización.docx'
    );
    expect(nombreDescarga('Hoja', 'application/vnd.ms-excel; charset=x')).toBe('Hoja.xls');
    expect(nombreDescarga('  ', 'image/png')).toBe('documento.png');
    expect(nombreDescarga(null, 'application/zip')).toBe('documento');
  });

  it('tamanoArchivo: bytes, KB y MB con coma decimal es-ES', () => {
    expect(tamanoArchivo(0)).toBe('0 bytes');
    expect(tamanoArchivo(17)).toBe('17 bytes');
    expect(tamanoArchivo(1536)).toBe('1,5 KB');
    expect(tamanoArchivo(5 * 1024 * 1024)).toBe('5 MB');
  });

  it('cuerpoCrear: el JSON de «nuevo» de hoy (fecha vacía → null, visible tal cual), sin recortar', () => {
    expect(JSON.stringify(cuerpoCrear({ titulo: ' Copa ', descripcion: 'D', fechaEvento: '', visible: false }))).toBe(
      '{"titulo":" Copa ","descripcion":"D","fechaEvento":null,"visible":false}'
    );
    expect(cuerpoCrear({ titulo: 'T', descripcion: 'D', fechaEvento: '2026-11-14', visible: true }).fechaEvento).toBe('2026-11-14');
  });

  it('cuerpoEditar: el JSON de «eventoEditado» de hoy, con «fotoEvento: null» al final', () => {
    expect(JSON.stringify(cuerpoEditar({ titulo: 'T', descripcion: 'D', fechaEvento: '' }))).toBe(
      '{"titulo":"T","descripcion":"D","fechaEvento":null,"fotoEvento":null}'
    );
  });

  it('mensajesError: los textos de hoy por campo', () => {
    expect(mensajesError('titulo', { required: true })).toEqual(['El título es obligatorio']);
    expect(mensajesError('titulo', { maxlength: { requiredLength: 100 } })).toEqual(['Máximo 100 caracteres']);
    expect(mensajesError('descripcion', { required: true })).toEqual(['La descripción es obligatoria']);
    expect(mensajesError('descripcion', { maxlength: {} })).toEqual(['Máximo 500 caracteres']);
    expect(mensajesError('titulo', null)).toEqual([]);
  });

  it('resumenEventos: total y visibles con plurales', () => {
    expect(resumenEventos(5, 4)).toBe('5 eventos, 4 visibles en la web');
    expect(resumenEventos(1, 1)).toBe('1 evento, 1 visible en la web');
    expect(resumenEventos(2, 0)).toBe('2 eventos, ninguno visible en la web');
    expect(resumenEventos(0, 0)).toBe('Ningún evento');
  });
});
