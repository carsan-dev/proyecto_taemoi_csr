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
      { n: 1, nombre: 'Antonio Ruiz', cargo: 'Cinturón negro 5.º dan', foto: null },
      { n: 2, nombre: 'Lucía Fernández', cargo: null, foto: { url: 'https://x/l.jpg', alt: 'Lucía Fernández' } },
      { n: 10, nombre: 'Manuel Ortega', cargo: null, foto: null },
    ]);
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
