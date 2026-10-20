import { HttpErrorResponse } from '@angular/common/http';

import {
  MOTIVO_HEIC,
  MOTIVO_LECTURA,
  SUBIDA_DOCUMENTO,
  SUBIDA_IMAGEN,
  SUBIDA_LOGO,
  mensajeErrorSubida,
  prepararFicheroParaSubida,
  reiniciarSelectorArchivo,
} from './subida-archivos.util';

describe('subida-archivos.util', () => {
  const bytes = (...valores: number[]) => new Uint8Array(valores);
  const ascii = (texto: string) => Array.from(texto).map((c) => c.charCodeAt(0));

  function ftyp(marca: string) {
    return bytes(0, 0, 0, 24, ...ascii('ftyp'), ...ascii(marca), 0, 0, 0, 0);
  }

  describe('prepararFicheroParaSubida', () => {
    it('si la lectura falla devuelve el motivo de lectura', async () => {
      const archivo = new File([bytes(1, 2, 3)], 'informe.pdf', { type: 'application/pdf' });
      spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));

      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);

      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_LECTURA });
      expect(MOTIVO_LECTURA).toBe(
        'No se pudo leer el archivo. Si está en Drive o en la galería, guárdalo primero en el teléfono y vuelve a elegirlo.'
      );
    });

    it('un archivo que se lee vacío también da el motivo de lectura', async () => {
      const archivo = new File([], 'foto.jpg', { type: 'image/jpeg' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_LECTURA });
    });

    it('sin extensión y con image/jpeg le pone .jpg', async () => {
      const archivo = new File([bytes(0xff, 0xd8, 0xff, 0xe0)], 'IMG_20261021_101010', { type: 'image/jpeg' });

      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);

      expect(resultado.ok).toBeTrue();
      if (resultado.ok) {
        expect(resultado.file.name).toBe('IMG_20261021_101010.jpg');
        expect(resultado.file.type).toBe('image/jpeg');
      }
    });

    it('sin extensión y con application/pdf le pone .pdf', async () => {
      const archivo = new File([bytes(0x25, 0x50, 0x44, 0x46)], 'documento', { type: 'application/pdf' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado.ok && resultado.file.name).toBe('documento.pdf');
    });

    it('rechaza .heic por la extensión', async () => {
      const archivo = new File([bytes(1, 2, 3)], 'IMG_0001.HEIC', { type: '' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_HEIC });
      expect(MOTIVO_HEIC).toBe('Convierte la foto a JPG o desactiva HEIF en la cámara.');
    });

    it('rechaza image/heif por el tipo', async () => {
      const archivo = new File([bytes(1, 2, 3)], 'foto', { type: 'image/heif' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_HEIC });
    });

    it('rechaza la cabecera ftypheic aunque el nombre diga .jpg', async () => {
      const archivo = new File([ftyp('heic')], 'foto.jpg', { type: 'image/jpeg' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_HEIC });
    });

    it('rechaza también las marcas mif1 y hevc', async () => {
      for (const marca of ['mif1', 'hevc']) {
        const archivo = new File([ftyp(marca)], 'foto.jpg', { type: 'image/jpeg' });
        const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
        expect(resultado).toEqual({ ok: false, motivo: MOTIVO_HEIC });
      }
    });

    it('no confunde un MP4 (ftypisom) con HEIC: lo rechaza por formato', async () => {
      const archivo = new File([ftyp('isom')], 'video.mp4', { type: 'video/mp4' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado.ok).toBeFalse();
      if (!resultado.ok) {
        expect(resultado.motivo).not.toBe(MOTIVO_HEIC);
        expect(resultado.motivo).toContain('no se admite');
      }
    });

    it('rechaza un formato que no admite ese tipo de subida', async () => {
      const zip = new File([bytes(0x50, 0x4b, 3, 4)], 'copia.zip', { type: 'application/zip' });
      const pdfComoImagen = new File([bytes(0x25, 0x50)], 'cartel.pdf', { type: 'application/pdf' });
      const gifComoLogo = new File([bytes(0x47, 0x49)], 'logo.gif', { type: 'image/gif' });

      const doc = await prepararFicheroParaSubida(zip, SUBIDA_DOCUMENTO);
      const imagen = await prepararFicheroParaSubida(pdfComoImagen, SUBIDA_IMAGEN);
      const logo = await prepararFicheroParaSubida(gifComoLogo, SUBIDA_LOGO);

      expect(doc).toEqual({ ok: false, motivo: SUBIDA_DOCUMENTO.motivoFormato });
      expect(imagen).toEqual({ ok: false, motivo: SUBIDA_IMAGEN.motivoFormato });
      expect(logo).toEqual({ ok: false, motivo: SUBIDA_LOGO.motivoFormato });
    });

    it('rechaza una extensión permitida con un tipo que no le corresponde', async () => {
      const archivo = new File([bytes(1, 2)], 'informe.pdf', { type: 'text/html' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado).toEqual({ ok: false, motivo: SUBIDA_DOCUMENTO.motivoFormato });
    });

    it('rechaza el archivo que supera el tamaño máximo sin leerlo', async () => {
      const archivo = new File([bytes(1, 2, 3)], 'grande.pdf', { type: 'application/pdf' });
      Object.defineProperty(archivo, 'size', { value: SUBIDA_DOCUMENTO.maxBytes + 1 });
      const lectura = spyOn(archivo, 'arrayBuffer').and.callThrough();

      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);

      expect(resultado).toEqual({ ok: false, motivo: SUBIDA_DOCUMENTO.motivoTamano });
      expect(SUBIDA_DOCUMENTO.motivoTamano).toContain('50 MB');
      expect(lectura).not.toHaveBeenCalled();
    });

    it('un archivo válido devuelve una copia en memoria con el mismo contenido', async () => {
      const contenido = bytes(0x89, 0x50, 0x4e, 0x47, 9, 8, 7);
      const archivo = new File([contenido], 'logo.png', { type: 'image/png', lastModified: 1234 });

      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_LOGO);

      expect(resultado.ok).toBeTrue();
      if (resultado.ok) {
        expect(resultado.file).not.toBe(archivo);
        expect(resultado.file.name).toBe('logo.png');
        expect(resultado.file.type).toBe('image/png');
        expect(resultado.file.lastModified).toBe(1234);
        expect(new Uint8Array(await resultado.file.arrayBuffer())).toEqual(contenido);
      }
    });

    it('acepta application/octet-stream si la extensión está permitida', async () => {
      const archivo = new File([bytes(0x25, 0x50)], 'bases.pdf', { type: 'application/octet-stream' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado.ok && resultado.file.name).toBe('bases.pdf');
    });

    it('ignora los parámetros del MIME («;charset»)', async () => {
      const archivo = new File([bytes(0x61, 0x62)], 'lista.csv', { type: 'text/csv;charset=utf-8' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado.ok).toBeTrue();
    });

    it('un nombre vacío pasa a «archivo» con la extensión del MIME', async () => {
      const archivo = new File([bytes(0x89, 0x50, 0x4e, 0x47)], '', { type: 'image/png' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado.ok && resultado.file.name).toBe('archivo.png');
    });

    it('sin extensión ni MIME deduce .jpg por la firma JPEG y pone su tipo', async () => {
      const archivo = new File([bytes(0xff, 0xd8, 0xff, 0xe1, 0, 0)], '1000012345', { type: '' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado.ok).toBeTrue();
      if (resultado.ok) {
        expect(resultado.file.name).toBe('1000012345.jpg');
        expect(resultado.file.type).toBe('image/jpeg');
      }
    });

    it('sin extensión y con octet-stream deduce .pdf, .png, .gif y .webp por la firma', async () => {
      const casos: [ReturnType<typeof bytes>, string, string][] = [
        [bytes(...ascii('%PDF-1.7')), 'pdf', 'application/pdf'],
        [bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a), 'png', 'image/png'],
        [bytes(...ascii('GIF89a')), 'gif', 'image/gif'],
        [bytes(...ascii('RIFF'), 1, 2, 3, 4, ...ascii('WEBPVP8 ')), 'webp', 'image/webp'],
      ];
      for (const [contenido, ext, tipo] of casos) {
        const archivo = new File([contenido], 'descarga', { type: 'application/octet-stream' });
        const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
        expect(resultado.ok && resultado.file.name).toBe(`descarga.${ext}`);
        expect(resultado.ok && resultado.file.type).toBe(tipo);
      }
    });

    it('sin extensión ni MIME y con bytes desconocidos se rechaza por formato', async () => {
      const archivo = new File([bytes(0x50, 0x4b, 3, 4, 0, 0)], 'desconocido', { type: '' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado).toEqual({ ok: false, motivo: SUBIDA_DOCUMENTO.motivoFormato });
    });

    it('la firma deducida también debe estar permitida para ese tipo de subida', async () => {
      const archivo = new File([bytes(...ascii('%PDF-1.7'))], 'cartel', { type: '' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado).toEqual({ ok: false, motivo: SUBIDA_IMAGEN.motivoFormato });
    });

    it('acepta la extensión en mayúsculas sin tocar el nombre', async () => {
      const archivo = new File([bytes(0xff, 0xd8, 0xff)], 'FOTO.JPG', { type: 'image/jpeg' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      expect(resultado.ok && resultado.file.name).toBe('FOTO.JPG');
    });

    it('«foto.» queda «foto.jpg» y «.jpg» queda «archivo.jpg»', async () => {
      const conPunto = new File([bytes(0xff, 0xd8, 0xff)], 'foto.', { type: 'image/jpeg' });
      const sinBase = new File([bytes(0xff, 0xd8, 0xff)], '.jpg', { type: 'image/jpeg' });
      const a = await prepararFicheroParaSubida(conPunto, SUBIDA_IMAGEN);
      const b = await prepararFicheroParaSubida(sinBase, SUBIDA_IMAGEN);
      expect(a.ok && a.file.name).toBe('foto.jpg');
      expect(b.ok && b.file.name).toBe('archivo.jpg');
    });

    it('«x.jpg» con image/png se acepta como imagen y logo (el backend mira el contenido) pero no como documento', async () => {
      const archivo = () => new File([bytes(0x89, 0x50, 0x4e, 0x47)], 'x.jpg', { type: 'image/png' });
      expect((await prepararFicheroParaSubida(archivo(), SUBIDA_IMAGEN)).ok).toBeTrue();
      expect((await prepararFicheroParaSubida(archivo(), SUBIDA_LOGO)).ok).toBeTrue();
      expect(await prepararFicheroParaSubida(archivo(), SUBIDA_DOCUMENTO)).toEqual({
        ok: false,
        motivo: SUBIDA_DOCUMENTO.motivoFormato,
      });
    });

    it('acepta image/jpg y image/x-png como imagen', async () => {
      const jpg = new File([bytes(0xff, 0xd8, 0xff)], 'a.jpg', { type: 'image/jpg' });
      const png = new File([bytes(0x89, 0x50, 0x4e, 0x47)], 'b.png', { type: 'image/x-png' });
      expect((await prepararFicheroParaSubida(jpg, SUBIDA_IMAGEN)).ok).toBeTrue();
      expect((await prepararFicheroParaSubida(png, SUBIDA_IMAGEN)).ok).toBeTrue();
    });

    it('si crear la copia lanza, devuelve el motivo de lectura', async () => {
      const archivo = new File([bytes(0x25, 0x50)], 'bases.pdf', { type: 'application/pdf' });
      spyOn(window as any, 'File').and.throwError('sin memoria');
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado).toEqual({ ok: false, motivo: MOTIVO_LECTURA });
    });

    it('acepta un .docx que llega como application/zip, igual que el backend', async () => {
      const archivo = new File([bytes(0x50, 0x4b, 3, 4)], 'acta.docx', { type: 'application/zip' });
      const resultado = await prepararFicheroParaSubida(archivo, SUBIDA_DOCUMENTO);
      expect(resultado.ok).toBeTrue();
    });
  });

  describe('reiniciarSelectorArchivo', () => {
    it('vacía el valor del input para poder elegir el mismo archivo otra vez', () => {
      const entrada = document.createElement('input');
      entrada.type = 'file';
      const valor = spyOnProperty(entrada, 'value', 'set').and.callThrough();
      reiniciarSelectorArchivo(entrada);
      expect(valor).toHaveBeenCalledWith('');
    });

    it('tolera un input nulo', () => {
      expect(() => reiniciarSelectorArchivo(null)).not.toThrow();
    });
  });

  describe('mensajeErrorSubida', () => {
    it('usa el «mensaje» del backend', () => {
      const error = new HttpErrorResponse({ status: 400, error: { mensaje: 'Tipo de documento no permitido.' } });
      expect(mensajeErrorSubida(error, 'Genérico')).toBe('Tipo de documento no permitido.');
    });

    it('sin mensaje usa el texto por defecto', () => {
      expect(mensajeErrorSubida(new HttpErrorResponse({ status: 500 }), 'Genérico')).toBe('Genérico');
      expect(mensajeErrorSubida(new HttpErrorResponse({ status: 400, error: { mensaje: '  ' } }), 'Genérico')).toBe('Genérico');
    });
  });
});
