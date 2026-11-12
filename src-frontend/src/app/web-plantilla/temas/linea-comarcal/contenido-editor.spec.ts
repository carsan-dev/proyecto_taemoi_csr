import { sitioConSedes } from '../../datos/datos-de-prueba';
import { BloqueWeb } from '../../datos/modelos';
import { ordenCompleto } from '../../editor/bloques-editor';
import { vistaPortada } from './contenido';

const AHORA = new Date(2026, 10, 2, 16, 0);
const cab = (clave: string, orden: number, oculto = false): BloqueWeb => ({ clave, texto: null, imagenUrl: null, orden, oculto });

/** W12e: con la capa de edición, la portada pinta también las secciones ocultas y las vacías, sin número. */
describe('vistaPortada en el editor (W12e)', () => {
  const sitio = () => {
    const s = sitioConSedes(2);
    return {
      ...s,
      bloques: [
        ...s.bloques,
        cab('presentacion', 0, true),
        cab('disciplinas', 1),
        cab('galeria', 2),
        cab('profesores', 3),
        { clave: 'profesor.1.nombre', texto: 'Ana Ruiz', imagenUrl: null, orden: 0 },
        cab('eventos', 4),
        cab('tarifas', 5),
      ],
    };
  };
  const ids = (editor: boolean) => vistaPortada(sitio(), AHORA, editor).secciones.map((s) => `${s.id} ${s.numero}`);

  it('en la web, lo oculto y lo vacío no salen (como antes)', () => {
    const web = ids(false);
    expect(web.some((x) => x.startsWith('presentacion'))).toBeFalse();
    expect(web.some((x) => x.startsWith('galeria'))).toBeFalse();
  });

  it('en el editor salen en su sitio, con «—» y sin fondo alterno; lo demás numera como en la web', () => {
    const v = vistaPortada(sitio(), AHORA, true);
    const editor = v.secciones.map((s) => `${s.id} ${s.numero}`);
    expect(editor[0]).toBe('presentacion —');
    expect(editor).toContain('galeria —');
    const web = ids(false);
    // Las que salen en la web llevan el mismo número en el editor.
    for (const linea of web) {
      expect(editor).toContain(linea);
    }
    expect(v.secciones.filter((s) => s.numero === '—').every((s) => !s.anden)).toBeTrue();
    const galeria = v.secciones.find((s) => s.id === 'galeria')!;
    expect(galeria.tipo === 'galeria' && galeria.fotos).toEqual([]);
  });

  it('contrato: el orden que calcula el panel (`ordenCompleto`) es el de las secciones del tema', () => {
    const base = sitioConSedes(2);
    const filas: BloqueWeb[][] = [
      // Sin cabeceras: el orden de antes.
      [],
      // Con cabeceras, desordenadas, con «Fotos de las sedes» y dos «Texto con foto» (uno solo con cabecera).
      [
        cab('galeria', 0),
        cab('texto_foto.2', 1),
        { clave: 'texto_foto.2.titulo', texto: 'Qué traer', imagenUrl: null, orden: 0 },
        cab('sedes', 2, true),
        cab('presentacion', 3, true),
        cab('texto_foto.1', 4),
        cab('profesores', 5),
        { clave: 'profesor.1.nombre', texto: 'Ana Ruiz', imagenUrl: null, orden: 0 },
        cab('disciplinas', 6),
        cab('eventos', 7),
        cab('tarifas', 8),
      ],
    ];
    const idDelTema = (id: string) => (id === 'sedes' ? 'fotos-sedes' : id.replace(/[_.]/g, '-'));
    for (const extra of filas) {
      const sitio = { ...base, bloques: [...base.bloques, ...extra] };
      const tema = vistaPortada(sitio, AHORA, true).secciones.map((x) => x.id).filter((id) => id !== 'contacto');
      const panel = ordenCompleto(sitio.bloques).map(idDelTema).filter((id) => tema.includes(id));
      expect(tema.length).toBeGreaterThan(3);
      expect(panel).toEqual(tema);
    }
  });
});

/** W12f-2b: en el editor, también los profesores sin nombre, con su número; en la web, como antes. */
describe('vistaPortada: profesores en el editor (W12f-2b)', () => {
  const b = (clave: string, texto: string | null, imagenUrl: string | null = null): BloqueWeb => ({ clave, texto, imagenUrl, orden: 0 });
  const conProfesores = (...bloques: BloqueWeb[]) => {
    const s = sitioConSedes(2);
    return { ...s, bloques: [...s.bloques, cab('profesores', 3), ...bloques] };
  };
  const profesores = (bloques: BloqueWeb[], editor: boolean) => {
    const seccion = vistaPortada(conProfesores(...bloques), AHORA, editor).secciones.find((x) => x.id === 'profesores');
    return seccion && seccion.tipo === 'profesores' ? seccion : null;
  };

  it('en la web: solo los que tienen nombre y sin su número (la tarjeta, igual que antes)', () => {
    const web = profesores([b('profesor.1.nombre', 'Ana Ruiz'), b('profesor.2.nombre', ''), b('profesor.2.cargo', 'Maestro')], false)!;
    expect(web.profesores).toEqual([{ n: 1, nombre: 'Ana Ruiz', cargo: null, foto: null, sedes: [], monograma: 'AR' }]);
  });

  it('sedes del profesor: nombres en el orden guardado; una sede que no está (desactivada) se ignora', () => {
    const web = profesores([b('profesor.1.nombre', 'Ana Ruiz'), b('profesor.1.sedes', '2,99,1')], false)!;
    expect(web.profesores[0].sedes).toEqual(['Estepa', 'Herrera']);
    expect(profesores([b('profesor.1.nombre', 'Ana Ruiz'), b('profesor.1.sedes', '99')], false)!.profesores[0].sedes).toEqual([]);
  });

  it('en el editor: también el que no tiene nombre («?»), cada uno con su número', () => {
    const editor = profesores([b('profesor.1.nombre', 'Ana Ruiz'), b('profesor.2.nombre', '')], true)!;
    expect(editor.profesores.map((p) => [p.n, p.nombre, p.monograma, p.marca])).toEqual([
      [1, 'Ana Ruiz', 'AR', 1],
      [2, '', '?', 2],
    ]);
  });

  it('solo uno sin nombre: en el editor sale con «—» (no sale en la web); en la web, no hay sección', () => {
    expect(profesores([b('profesor.4.nombre', '')], false)).toBeNull();
    const editor = profesores([b('profesor.4.nombre', '')], true)!;
    expect(editor.numero).toBe('—');
    expect(editor.profesores.map((p) => p.n)).toEqual([4]);
  });
});
