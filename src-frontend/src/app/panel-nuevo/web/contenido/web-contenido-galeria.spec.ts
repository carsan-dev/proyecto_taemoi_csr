import { ordenCompleto } from '../../../web-plantilla/editor/bloques-editor';
import { esFotoGaleria, ordenGaleria } from '../../../web-plantilla/editor/fotos-galeria';
import { esClaveSubida, esFotoEditable, fotoDeDescripcion } from '../../../web-plantilla/editor/fotos-sueltas';
import { fotosCambiadas, queFoto } from './web-contenido-fotos';
import {
  anadirFotoGaleria,
  cambiosGaleria,
  moverFotoGaleria,
  numeroGaleriaNuevo,
  quitarFotoGaleria,
  vecinaGaleria,
} from './web-contenido-galeria';
import { cuerpoGuardar, erroresContenido, nombreError } from './web-contenido-guardar';
import type { BloqueContenido } from './web-contenido-vista';

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
const foto = (n: number, orden: number, texto = `Foto ${n}`) =>
  fila(`galeria.${n}`, texto, { imagenId: 100 + n, imagenUrl: `https://x/g${n}.webp`, orden });
const BASE: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo'),
  fila('portada.foto', 'Niños', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('galeria', null, { orden: 4 }),
];
const conFotos = (...fotos: BloqueContenido[]) => [...BASE, ...fotos];
const puestos = (filas: readonly BloqueContenido[]) =>
  ordenGaleria(filas).map((clave) => [clave, filas.find((f) => f.clave === clave)!.orden]);

describe('galería de «Web · Contenido» (W12f-2a, lógica pura)', () => {
  it('claves: `galeria.<n>` (1 a 99) es una foto editable; `galeria.nueva` solo sube', () => {
    expect(['galeria.1', 'galeria.99'].every(esFotoEditable)).toBeTrue();
    expect(['galeria', 'galeria.0', 'galeria.100', 'galeria.rotulo', 'galeria.nueva'].some(esFotoEditable)).toBeFalse();
    expect(esClaveSubida('galeria.nueva')).toBeTrue();
    expect(esFotoGaleria('galeria.nueva')).toBeFalse();
    expect(fotoDeDescripcion('galeria.3.alt')).toBe('galeria.3');
    expect(fotoDeDescripcion('galeria.nueva.alt')).toBeNull();
  });

  it('orden: por `orden` y luego por número, como el tema; sin foto no cuenta', () => {
    const filas = conFotos(foto(5, 1), foto(2, 2), foto(1, 2), fila('galeria.9', 'x', { orden: 0 }));
    expect(ordenGaleria(filas)).toEqual(['galeria.5', 'galeria.1', 'galeria.2']);
    expect(queFoto('galeria.1', filas)).toBe('n.º 2 de la galería');
  });

  describe('número de una foto nueva', () => {
    it('el mayor de lo guardado, el borrador y lo usado en la sesión, más uno; sin rellenar huecos', () => {
      expect(numeroGaleriaNuevo(BASE, BASE)).toBe(1);
      expect(numeroGaleriaNuevo(conFotos(foto(1, 1), foto(4, 2)), conFotos(foto(1, 1), foto(4, 2)))).toBe(5);
      // Una guardada y quitada en el borrador no se reutiliza («Volver a la anterior» no se cruza).
      expect(numeroGaleriaNuevo(conFotos(foto(1, 1)), conFotos(foto(1, 1), foto(7, 2)))).toBe(8);
      // Una nueva quitada sin guardar tampoco.
      expect(numeroGaleriaNuevo(conFotos(foto(1, 1)), conFotos(foto(1, 1)), [2, 3])).toBe(4);
    });

    it('la API admite hasta el 99: después, el menor libre', () => {
      expect(numeroGaleriaNuevo(conFotos(foto(1, 1), foto(99, 2)), BASE)).toBe(2);
      expect(numeroGaleriaNuevo(conFotos(foto(99, 1)), BASE, Array.from({ length: 98 }, (_, i) => i + 1))).toBe(1);
    });
  });

  it('añadir: entra la última, sin descripción, con `orden` = su puesto; con 6 no cabe', () => {
    const r = anadirFotoGaleria(conFotos(foto(3, 1), foto(1, 2)), BASE, [], 70, 'https://x/n.webp')!;
    expect(r.clave).toBe('galeria.4');
    expect(r.filas.find((f) => f.clave === 'galeria.4')).toEqual(
      fila('galeria.4', '', { imagenId: 70, imagenUrl: 'https://x/n.webp', orden: 3 }),
    );
    expect(puestos(r.filas)).toEqual([['galeria.3', 1], ['galeria.1', 2], ['galeria.4', 3]]);
    const llena = conFotos(...[1, 2, 3, 4, 5, 6].map((n) => foto(n, n)));
    expect(anadirFotoGaleria(llena, llena, [], 70, 'https://x/n.webp')).toBeNull();
  });

  it('mover: cambia el puesto con la vecina; en los extremos, null', () => {
    const filas = conFotos(foto(1, 1), foto(2, 2), foto(3, 3));
    expect(moverFotoGaleria(filas, 'galeria.1', -1)).toBeNull();
    expect(moverFotoGaleria(filas, 'galeria.3', 1)).toBeNull();
    expect(moverFotoGaleria(filas, 'galeria.9', 1)).toBeNull();
    const r = moverFotoGaleria(filas, 'galeria.3', -1)!;
    expect([r.puesto, r.total]).toEqual([2, 3]);
    expect(puestos(r.filas)).toEqual([['galeria.1', 1], ['galeria.3', 2], ['galeria.2', 3]]);
  });

  it('quitar: fuera la fila y las demás suben de puesto; el foco va a la siguiente, la anterior o a añadir', () => {
    const filas = conFotos(foto(1, 1), foto(2, 2), foto(3, 3));
    expect(puestos(quitarFotoGaleria(filas, 'galeria.1'))).toEqual([['galeria.2', 1], ['galeria.3', 2]]);
    expect(vecinaGaleria(filas, 'galeria.2')).toBe('galeria.3');
    expect(vecinaGaleria(filas, 'galeria.3')).toBe('galeria.2');
    expect(vecinaGaleria(conFotos(foto(1, 1)), 'galeria.1')).toBeNull();
  });

  it('sin cabeceras guardadas, tocar la galería fija antes el orden de los bloques (el tema no la mueve de sitio)', () => {
    const antiguo = [fila('portada.titular', 'T'), fila('presentacion.texto', 'Hola'), foto(1, 0), foto(2, 0)];
    const r = moverFotoGaleria(antiguo, 'galeria.2', -1)!;
    expect(ordenCompleto(r.filas)).toEqual(ordenCompleto(antiguo));
    expect(r.filas.some((f) => f.clave === 'galeria')).toBeTrue();
  });

  it('cambios: añadida, quitada y orden cuentan uno; en cada foto que sigue, la foto y la descripción', () => {
    const guardado = conFotos(foto(1, 1), foto(2, 2), foto(3, 3));
    expect(cambiosGaleria(guardado, guardado)).toEqual([]);
    expect(cambiosGaleria(guardado, moverFotoGaleria(guardado, 'galeria.2', 1)!.filas)).toEqual(['galeria.orden']);
    // Quitar una no cambia el orden de las demás.
    expect(cambiosGaleria(guardado, quitarFotoGaleria(guardado, 'galeria.1'))).toEqual(['galeria.1']);
    const nueva = anadirFotoGaleria(guardado, guardado, [], 70, 'https://x/n.webp')!.filas;
    expect(cambiosGaleria(guardado, nueva)).toEqual(['galeria.4']);
    const cambiada = nueva.map((f) =>
      f.clave === 'galeria.2' ? { ...f, imagenId: 9, texto: '' } : f.clave === 'galeria.3' ? { ...f, texto: 'Otra' } : f,
    );
    expect(cambiosGaleria(guardado, cambiada)).toEqual(['galeria.4', 'galeria.2', 'galeria.2.alt', 'galeria.3.alt']);
    // En la barra cuentan con las fotos sueltas.
    expect(fotosCambiadas(guardado, cambiada).length).toBe(4);
  });

  it('descripción obligatoria: «Describe la foto.» por cada foto sin ella; el resumen dice su puesto', () => {
    const filas = conFotos(foto(1, 1), foto(2, 2, ' '), foto(3, 3, ''));
    const errores = erroresContenido(filas, {});
    expect(errores).toEqual({ 'galeria.2.alt': 'Describe la foto.', 'galeria.3.alt': 'Describe la foto.' });
    expect(nombreError('galeria.3.alt', filas)).toBe('Foto n.º 3 de la galería: falta describirla');
  });

  it('cuerpo del PUT: la galería completa con clave, imagenId, texto (descripción) y orden (puesto)', () => {
    const filas = moverFotoGaleria(conFotos(foto(1, 1, 'Uno'), foto(2, 2, 'Dos')), 'galeria.2', -1)!.filas;
    const cuerpo = cuerpoGuardar(5, filas, {});
    expect(cuerpo.bloques.filter((b) => esFotoGaleria(b.clave))).toEqual([
      { clave: 'galeria.1', texto: 'Uno', imagenId: 101, orden: 2, oculto: false },
      { clave: 'galeria.2', texto: 'Dos', imagenId: 102, orden: 1, oculto: false },
    ]);
  });
});
