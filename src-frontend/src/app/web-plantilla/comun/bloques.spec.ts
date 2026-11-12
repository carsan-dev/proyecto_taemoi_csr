import { BloqueWeb } from '../datos/modelos';
import { BloquesWeb, ordenarSecciones } from './bloques';

const b = (clave: string, texto: string | null, orden = 0, imagenUrl: string | null = null): BloqueWeb => ({
  clave,
  texto,
  imagenUrl,
  orden,
});

/** Bloques editables (§9 n.º 4 y 11): por defecto el texto del boceto; vacíos, no se pintan. */
describe('bloques · lectura de web_bloque', () => {
  it('clave que falta: el texto por defecto; clave con texto: el suyo; clave vacía: vacío (no se pinta)', () => {
    const bloques = new BloquesWeb([b('portada.titular', '  Taekwondo en Estepa '), b('portada.frase', '   '), b('pie.creditos', null)]);
    expect(bloques.texto('portada.titular', 'Por defecto')).toBe('Taekwondo en Estepa');
    expect(bloques.texto('portada.frase', 'Por defecto')).toBe('');
    expect(bloques.texto('pie.creditos', 'Por defecto')).toBe('');
    expect(bloques.texto('menu.horarios', 'Horarios')).toBe('Horarios');
    expect(bloques.textoPropio('portada.titular')).toBe('Taekwondo en Estepa');
    expect(bloques.textoPropio('portada.frase')).toBeNull();
    expect(bloques.textoPropio('no.esta')).toBeNull();
  });

  it('imágenes: solo con URL, con el texto como alternativo', () => {
    const bloques = new BloquesWeb([b('portada.foto', 'Niños en el tatami', 0, 'https://x/1.jpg'), b('presentacion.foto', 'Sin foto')]);
    expect(bloques.imagen('portada.foto')).toEqual({ url: 'https://x/1.jpg', alt: 'Niños en el tatami' });
    expect(bloques.imagen('portada.foto', 'Otro')).toEqual({ url: 'https://x/1.jpg', alt: 'Otro' });
    expect(bloques.imagen('presentacion.foto')).toBeNull();
    expect(bloques.imagen('no.esta')).toBeNull();
  });

  it('profesores: por número, solo con nombre; cargo y foto opcionales', () => {
    const bloques = new BloquesWeb([
      b('profesor.2.nombre', 'Lucía Fernández'),
      b('profesor.2.foto', null, 0, 'https://x/l.jpg'),
      b('profesor.1.nombre', 'Antonio Ruiz'),
      b('profesor.1.cargo', 'Cinturón negro 5.º dan'),
      b('profesor.3.cargo', 'Sin nombre'),
      b('profesor.10.nombre', 'Manuel Ortega'),
    ]);
    expect(bloques.profesores()).toEqual([
      { n: 1, nombre: 'Antonio Ruiz', cargo: 'Cinturón negro 5.º dan', foto: null, sedes: [] },
      // Sin descripción en su bloque (datos de antes de W12), el nombre.
      { n: 2, nombre: 'Lucía Fernández', cargo: null, foto: { url: 'https://x/l.jpg', alt: 'Lucía Fernández' }, sedes: [] },
      { n: 10, nombre: 'Manuel Ortega', cargo: null, foto: null, sedes: [] },
    ]);
  });

  it('profesores: `sedes` lee ids enteros positivos, sin repetidos y en el orden guardado', () => {
    const sedes = (texto: string | null, fila = true) =>
      new BloquesWeb([b('profesor.1.nombre', 'Ana'), ...(fila ? [b('profesor.1.sedes', texto)] : [])]).profesores()[0].sedes;
    expect(sedes('3,1')).toEqual([3, 1]);
    expect(sedes(null, false)).toEqual([]);
    expect(sedes('')).toEqual([]);
    expect(sedes(' 2 , 5 ,7 ')).toEqual([2, 5, 7]);
    expect(sedes('2,2, 1,2')).toEqual([2, 1]);
    expect(sedes('a,-1,0,01,1.5,4')).toEqual([4]);
    expect(sedes('1,,2,')).toEqual([1, 2]);
  });

  it('galería: solo las que tienen imagen, por orden y luego por número', () => {
    const bloques = new BloquesWeb([
      b('galeria.1', 'Uno', 5, 'https://x/1.jpg'),
      b('galeria.2', 'Dos', 1, 'https://x/2.jpg'),
      b('galeria.3', 'Sin imagen', 0),
      b('galeria.4', null, 1, 'https://x/4.jpg'),
    ]);
    expect(bloques.galeria()).toEqual([
      { n: 2, url: 'https://x/2.jpg', alt: 'Dos' },
      { n: 4, url: 'https://x/4.jpg', alt: '' },
      { n: 1, url: 'https://x/1.jpg', alt: 'Uno' },
    ]);
  });

  it('orden de las secciones: las fijas en su hueco; las movibles, por su orden', () => {
    const secciones = [
      { clave: 'presentacion', movible: true },
      { clave: 'disciplinas', movible: false },
      { clave: 'profesores', movible: true, prefijo: 'profesor' },
      { clave: 'galeria', movible: true },
      { clave: 'eventos', movible: false },
    ];
    const sinBloques = ordenarSecciones(secciones, new BloquesWeb([]));
    expect(sinBloques.map((s) => s.clave)).toEqual(['presentacion', 'disciplinas', 'profesores', 'galeria', 'eventos']);

    const conOrden = ordenarSecciones(
      secciones,
      new BloquesWeb([b('galeria.1', 'x', 0, 'u'), b('presentacion.texto', 't', 2), b('profesor.1.nombre', 'p', 1)]),
    );
    expect(conOrden.map((s) => s.clave)).toEqual(['galeria', 'disciplinas', 'profesores', 'presentacion', 'eventos']);

    // Una movible sin bloques toma su posición en el tema como orden (galería, 3).
    const parcial = ordenarSecciones(secciones, new BloquesWeb([b('presentacion.texto', 't', 9)]));
    expect(parcial.map((s) => s.clave)).toEqual(['profesores', 'disciplinas', 'galeria', 'presentacion', 'eventos']);
  });
});

/** W12b: el modelo del editor (fase4-w12-plan.md; boceto `boceto-panel-contenido-web-comarcal.html`, `FIJOS`). */
describe('bloques · cabeceras de sección (W12b)', () => {
  const cab = (clave: string, orden: number, oculto = false): BloqueWeb => ({ clave, texto: null, imagenUrl: null, orden, oculto });
  const SECCIONES = [
    { clave: 'presentacion', movible: true },
    { clave: 'disciplinas', movible: false },
    { clave: 'profesores', movible: true, prefijo: 'profesor' },
    { clave: 'galeria', movible: true },
    { clave: 'texto_foto.1', movible: true },
    { clave: 'eventos', movible: false },
    { clave: 'tarifas', movible: false },
    { clave: 'contacto', movible: false, sinCabecera: true },
  ];
  const claves = (bloques: BloqueWeb[]) => ordenarSecciones(SECCIONES, new BloquesWeb(bloques)).map((s) => s.clave);

  it('oculto: solo la cabecera lo dice; sin cabecera o sin la marca, no está oculta', () => {
    const bloques = new BloquesWeb([cab('galeria', 0, true), cab('profesores', 1), b('presentacion.texto', 't')]);
    expect(bloques.oculto('galeria')).toBeTrue();
    expect(bloques.oculto('profesores')).toBeFalse();
    expect(bloques.oculto('presentacion')).toBeFalse();
  });

  it('con cabeceras, las movibles pueden saltar sobre las fijas; las fijas conservan su orden relativo', () => {
    expect(
      claves([
        cab('tarifas', 0),
        cab('presentacion', 1),
        cab('eventos', 2),
        cab('galeria', 3),
        cab('disciplinas', 4),
        cab('texto_foto.1', 5),
        cab('profesores', 6),
      ]),
    ).toEqual(['disciplinas', 'presentacion', 'eventos', 'galeria', 'tarifas', 'texto_foto.1', 'profesores', 'contacto']);
  });

  it('con cabeceras, la galería se ordena por su cabecera y no por el orden de sus fotos', () => {
    expect(
      claves([
        cab('presentacion', 0),
        cab('disciplinas', 1),
        cab('profesores', 2),
        cab('galeria', 7),
        cab('texto_foto.1', 3),
        cab('eventos', 4),
        cab('tarifas', 5),
        b('galeria.1', 'Uno', 0, 'u'),
      ]),
    ).toEqual(['presentacion', 'disciplinas', 'profesores', 'texto_foto.1', 'eventos', 'tarifas', 'galeria', 'contacto']);
  });

  it('el orden de las fotos de la galería sigue siendo el suyo, aparte del de la sección', () => {
    const bloques = new BloquesWeb([cab('galeria', 0), b('galeria.1', 'Uno', 2, 'u1'), b('galeria.2', 'Dos', 1, 'u2')]);
    expect(bloques.galeria().map((f) => f.n)).toEqual([2, 1]);
  });

  it('una sección sin cabecera cuando las demás la tienen toma su puesto en el tema', () => {
    expect(claves([cab('profesores', 0), cab('presentacion', 5)])).toEqual([
      'profesores',
      'disciplinas',
      'galeria',
      'texto_foto.1',
      'presentacion',
      'eventos',
      'tarifas',
      'contacto',
    ]);
  });

  it('texto con foto: los números de `texto_foto.<n>`', () => {
    expect(new BloquesWeb([]).numeros('texto_foto')).toEqual([]);
    expect(new BloquesWeb([cab('texto_foto.3', 0), b('texto_foto.1.titulo', 'x'), b('texto_foto.2', null)]).numeros('texto_foto')).toEqual([1, 2, 3]);
  });

  it('las fotos de profesor llevan como alternativo el texto de su bloque', () => {
    const bloques = new BloquesWeb([
      b('profesor.1.nombre', 'Antonio Ruiz'),
      b('profesor.1.foto', 'Retrato de un maestro con cinturón negro', 0, 'https://x/a.jpg'),
      b('profesor.2.nombre', 'Lucía Fernández'),
      b('profesor.2.foto', null, 0, 'https://x/l.jpg'),
    ]);
    expect(bloques.profesores().map((p) => p.foto?.alt)).toEqual(['Retrato de un maestro con cinturón negro', 'Lucía Fernández']);
  });
});
