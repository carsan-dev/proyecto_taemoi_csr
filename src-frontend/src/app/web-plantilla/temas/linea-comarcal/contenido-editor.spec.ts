import { sitioConSedes } from '../../datos/datos-de-prueba';
import { BloqueWeb } from '../../datos/modelos';
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
});
