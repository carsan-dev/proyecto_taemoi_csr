import { HttpErrorResponse } from '@angular/common/http';

import { esFotoSuelta, fotoDeDescripcion, queFoto as queFotoBase } from '../../../web-plantilla/editor/fotos-sueltas';
import {
  MAX_BYTES_FOTO,
  comprobarArchivo,
  conFotosCambiadasDesde,
  describirFoto,
  estadoFotos,
  falloDeSubida,
  fotosCambiadas,
  ponerFoto,
  queFoto,
  quitarFoto,
  volverALaAnterior,
} from './web-contenido-fotos';
import { cuerpoGuardar, entradaResumen, erroresContenido, nombreError } from './web-contenido-guardar';
import { BloqueContenido } from './web-contenido-vista';

const fila = (clave: string, texto: string | null, extra: Partial<BloqueContenido> = {}): BloqueContenido => ({
  clave,
  texto,
  imagenId: null,
  imagenUrl: null,
  orden: 0,
  oculto: false,
  permiso: null,
  ...extra,
});
const PORTADA = fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' });
const GUARDADO: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  PORTADA,
  fila('texto_foto.3', null, { orden: 4, oculto: true }),
  fila('texto_foto.3.titulo', 'Nuestra sala'),
  fila('texto_foto.3.foto', 'El tatami', { imagenId: 9, imagenUrl: 'https://x/t.webp', orden: 2 }),
  fila('galeria.1', 'Exhibición', { imagenId: 11, imagenUrl: 'https://x/g.webp', orden: 1 }),
];
const archivo = (name: string, type: string, size = 1000) => ({ name, type, size });

/** W12f-1: fotos sueltas (boceto `fotoCapa`, `subidaHtml`, `abrirMenuFoto`, `abrirDescribir`, `cambios`, `errores`). */
describe('web-contenido-fotos (W12f-1)', () => {
  it('fotos sueltas: portada, presentación y «Texto con foto»; ni galería ni profesores', () => {
    expect(['portada.foto', 'presentacion.foto', 'texto_foto.1.foto', 'texto_foto.12.foto'].every(esFotoSuelta)).toBeTrue();
    expect(['galeria.1', 'profesor.1.foto', 'disciplina.tkd.foto', 'texto_foto.0.foto', 'portada.alt', 'sedes.foto'].some(esFotoSuelta)).toBeFalse();
    expect(fotoDeDescripcion('presentacion.foto.alt')).toBe('presentacion.foto');
    expect(fotoDeDescripcion('galeria.1.alt')).toBeNull();
  });

  it('queFoto: como el boceto', () => {
    expect(queFoto('portada.foto', GUARDADO)).toBe('de la portada');
    expect(queFoto('presentacion.foto', GUARDADO)).toBe('de la presentación');
    expect(queFoto('texto_foto.3.foto', GUARDADO)).toBe('de «Nuestra sala»');
    expect(queFotoBase('texto_foto.5.foto', () => '  ')).toBe('de Texto con foto');
  });

  describe('comprobarArchivo (antes de subir)', () => {
    it('JPG, PNG y WebP de hasta 50 MB valen (por tipo o por extensión)', () => {
      expect(comprobarArchivo(archivo('a.jpg', 'image/jpeg', MAX_BYTES_FOTO))).toBeNull();
      expect(comprobarArchivo(archivo('a.PNG', ''))).toBeNull();
      expect(comprobarArchivo(archivo('a', 'image/webp'))).toBeNull();
    });

    it('«Demasiado grande» con lo que pesa (boceto `?subida=grande`)', () => {
      expect(comprobarArchivo(archivo('exhibicion-navidad.jpg', 'image/jpeg', 64 * 1024 * 1024))).toEqual({
        estado: 'error',
        titulo: 'Demasiado grande',
        msg: '«exhibicion-navidad.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.',
      });
    });

    it('«Formato no admitido»: HEIC con el texto del boceto (`?subida=tipo`) y otros formatos', () => {
      expect(comprobarArchivo(archivo('grupo-estepa.heic', ''))).toEqual({
        estado: 'error',
        titulo: 'Formato no admitido',
        msg: '«grupo-estepa.heic»: convierte la foto a JPG o desactiva HEIF en la cámara.',
      });
      expect(comprobarArchivo(archivo('foto.jpg', 'image/heif'))!.titulo).toBe('Formato no admitido');
      expect(comprobarArchivo(archivo('cartel.gif', 'image/gif'))).toEqual({
        estado: 'error',
        titulo: 'Formato no admitido',
        msg: '«cartel.gif»: la foto tiene que ser JPG, PNG o WebP.',
      });
    });
  });

  describe('falloDeSubida (respuesta de la API)', () => {
    const a = archivo('pabellon.jpg', 'image/jpeg', 52 * 1024 * 1024);
    const http = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

    it('413 y 400 de megapíxeles: «Demasiado grande»', () => {
      expect(falloDeSubida(a, http(413))).toEqual({
        estado: 'error',
        titulo: 'Demasiado grande',
        msg: '«pabellon.jpg» pesa 52 MB. Usa una foto de menos de 50 MB.',
      });
      expect(falloDeSubida(a, http(400, { mensaje: 'La foto es demasiado grande. Usa una de menos de 60 megapíxeles.' }))).toEqual({
        estado: 'error',
        titulo: 'Demasiado grande',
        msg: '«pabellon.jpg»: la foto es demasiado grande. Usa una de menos de 60 megapíxeles.',
      });
    });

    it('400 de formato: «Formato no admitido»', () => {
      expect(falloDeSubida(a, http(400, { mensaje: 'La foto tiene que ser JPG, PNG o WebP.' }))).toEqual({
        estado: 'error',
        titulo: 'Formato no admitido',
        msg: '«pabellon.jpg»: la foto tiene que ser JPG, PNG o WebP.',
      });
      expect(falloDeSubida(a, http(400, { mensaje: 'Convierte la foto a JPG o desactiva HEIF en la cámara.' })).titulo).toBe(
        'Formato no admitido',
      );
    });

    it('otro error: «No se pudo subir la foto» con el mensaje de la API o «Inténtalo de nuevo.»', () => {
      expect(falloDeSubida(a, http(400, { mensaje: 'No se pudo leer la foto.' }))).toEqual({
        estado: 'error',
        titulo: 'No se pudo subir la foto',
        msg: '«pabellon.jpg»: no se pudo leer la foto.',
      });
      expect(falloDeSubida(a, http(500, { mensaje: 'Error al guardar la foto.' })).msg).toBe('«pabellon.jpg»: inténtalo de nuevo.');
      expect(falloDeSubida(a, new Error('red')).titulo).toBe('No se pudo subir la foto');
    });
  });

  describe('borrador', () => {
    it('ponerFoto: cambia la foto de la fila (sin descripción) y conserva orden y oculto; sin fila, una nueva', () => {
      const b = ponerFoto(GUARDADO, 'texto_foto.3.foto', 20, 'https://x/n.webp');
      expect(b.find((f) => f.clave === 'texto_foto.3.foto')).toEqual(
        fila('texto_foto.3.foto', '', { imagenId: 20, imagenUrl: 'https://x/n.webp', orden: 2 }),
      );
      const n = ponerFoto(GUARDADO, 'presentacion.foto', 21, 'https://x/q.webp');
      expect(n.at(-1)).toEqual(fila('presentacion.foto', '', { imagenId: 21, imagenUrl: 'https://x/q.webp' }));
    });

    it('quitar, volver a la anterior y describir (máximo 150)', () => {
      const sin = quitarFoto(GUARDADO, 'texto_foto.3.foto');
      expect(sin.some((f) => f.clave === 'texto_foto.3.foto')).toBeFalse();
      expect(volverALaAnterior(sin, GUARDADO, 'texto_foto.3.foto').find((f) => f.clave === 'texto_foto.3.foto')).toEqual(GUARDADO[4]);
      const otra = ponerFoto(GUARDADO, 'portada.foto', 30, 'https://x/o.webp');
      expect(volverALaAnterior(otra, GUARDADO, 'portada.foto').find((f) => f.clave === 'portada.foto')).toEqual(PORTADA);
      const descrita = describirFoto(otra, 'portada.foto', 'x'.repeat(200));
      expect(descrita.find((f) => f.clave === 'portada.foto')!.texto).toBe('x'.repeat(150));
      expect(describirFoto(sin, 'texto_foto.3.foto', 'algo')).toEqual(sin);
    });

    it('cambios: la foto y su descripción cuentan uno cada uno; la galería no cuenta aquí', () => {
      expect(fotosCambiadas(GUARDADO, GUARDADO)).toEqual([]);
      const subida = ponerFoto(GUARDADO, 'portada.foto', 30, 'https://x/o.webp');
      expect(fotosCambiadas(GUARDADO, subida)).toEqual(['portada.foto', 'portada.foto.alt']);
      expect(fotosCambiadas(GUARDADO, describirFoto(subida, 'portada.foto', 'Niños en fila'))).toEqual(['portada.foto']);
      expect(fotosCambiadas(GUARDADO, describirFoto(GUARDADO, 'portada.foto', 'Otra'))).toEqual(['portada.foto.alt']);
      expect(fotosCambiadas(GUARDADO, quitarFoto(GUARDADO, 'texto_foto.3.foto'))).toEqual(['texto_foto.3.foto', 'texto_foto.3.foto.alt']);
      expect(fotosCambiadas(GUARDADO, GUARDADO.filter((f) => f.clave !== 'galeria.1'))).toEqual([]);
    });

    it('estadoFotos: «Nueva, sin guardar» y «Volver a la anterior» solo con una guardada', () => {
      const b = ponerFoto(ponerFoto(GUARDADO, 'portada.foto', 30, 'u'), 'presentacion.foto', 31, 'v');
      expect(estadoFotos(GUARDADO, b)).toEqual({ cambiadas: ['portada.foto', 'presentacion.foto'], anteriores: ['portada.foto'] });
      expect(estadoFotos(GUARDADO, quitarFoto(GUARDADO, 'portada.foto'))).toEqual({ cambiadas: [], anteriores: [] });
    });

    it('tras guardar: lo cambiado en las fotos durante el PUT se conserva', () => {
      const enviado = GUARDADO;
      const actual = describirFoto(ponerFoto(GUARDADO, 'portada.foto', 30, 'u'), 'portada.foto', 'Nueva');
      const r = conFotosCambiadasDesde(GUARDADO, enviado, quitarFoto(actual, 'texto_foto.3.foto'));
      expect(r.find((f) => f.clave === 'portada.foto')).toEqual(fila('portada.foto', 'Nueva', { imagenId: 30, imagenUrl: 'u' }));
      expect(r.some((f) => f.clave === 'texto_foto.3.foto')).toBeFalse();
      expect(conFotosCambiadasDesde(GUARDADO, enviado, enviado)).toEqual(GUARDADO);
    });
  });

  describe('validación, resumen y PUT', () => {
    it('falta la descripción de una foto puesta: «Foto de la portada: falta describirla»', () => {
      const b = ponerFoto(GUARDADO, 'portada.foto', 30, 'u');
      const errores = erroresContenido(b, {});
      expect(errores).toEqual({ 'portada.foto.alt': 'Describe la foto.' });
      expect(nombreError('portada.foto.alt', b)).toBe('Foto de la portada: falta describirla');
      expect(nombreError('texto_foto.3.foto.alt', b)).toBe('Foto de «Nuestra sala»: falta describirla');
      expect(entradaResumen('portada.foto.alt', 'Describe la foto.', b, false)).toEqual({
        clave: 'portada.foto.alt',
        texto: 'Foto de la portada: falta describirla',
        enlace: true,
      });
    });

    it('sin foto de portada: «Portada: falta la foto», con enlace', () => {
      const b = quitarFoto(GUARDADO, 'portada.foto');
      expect(erroresContenido(b, {})).toEqual({ 'portada.foto': 'Añade la foto de portada.' });
      expect(entradaResumen('portada.foto', 'Añade la foto de portada.', b, false)).toEqual({
        clave: 'portada.foto',
        texto: 'Portada: falta la foto',
        enlace: true,
      });
    });

    it('un «Texto con foto» con foto y sin título: «Escribe el título.»', () => {
      const b = GUARDADO.filter((f) => f.clave !== 'texto_foto.3.titulo');
      expect(erroresContenido(b, {})).toEqual({ 'texto_foto.3.titulo': 'Escribe el título.' });
    });

    it('un error de la API en otra foto suelta lleva a la foto con su mensaje', () => {
      expect(entradaResumen('texto_foto.3.foto', 'Describe la foto.', GUARDADO, true)).toEqual({
        clave: 'texto_foto.3.foto',
        texto: 'Describe la foto.',
        enlace: true,
      });
    });

    it('PUT: imagenId y descripción de la fila, con su orden y oculto; sin imagenUrl ni permiso', () => {
      const b = describirFoto(ponerFoto(GUARDADO, 'texto_foto.3.foto', 30, 'https://x/n.webp'), 'texto_foto.3.foto', 'Sala nueva');
      const cuerpo = cuerpoGuardar(5, quitarFoto(b, 'portada.foto'), {});
      expect(cuerpo.bloques).toContain({ clave: 'texto_foto.3.foto', texto: 'Sala nueva', imagenId: 30, orden: 2, oculto: false });
      expect(cuerpo.bloques).toContain({ clave: 'galeria.1', texto: 'Exhibición', imagenId: 11, orden: 1, oculto: false });
      expect(cuerpo.bloques.some((f) => f.clave === 'portada.foto')).toBeFalse();
      expect(cuerpo.bloques.every((f) => !('imagenUrl' in f) && !('permiso' in f))).toBeTrue();
    });
  });
});
