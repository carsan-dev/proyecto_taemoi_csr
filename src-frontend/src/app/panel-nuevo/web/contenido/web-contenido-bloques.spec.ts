import { ordenCompleto } from '../../../web-plantilla/editor/bloques-editor';
import {
  alternarOculto,
  anadirBloque,
  cambiosBloques,
  confirmacionQuitar,
  esDeBloqueFuera,
  esQuitable,
  lineaHoja,
  moverBloque,
  numeroTextoFotoLibre,
  ordenVisible,
  partesSinGuardar,
  quitarBloque,
} from './web-contenido-bloques';
import type { BloqueContenido } from './web-contenido-vista';

const fila = (clave: string, texto: string | null = null, extra: Partial<BloqueContenido> = {}): BloqueContenido => ({
  clave,
  texto,
  imagenId: null,
  imagenUrl: null,
  orden: 0,
  oculto: false,
  permiso: null,
  ...extra,
});
const cab = (clave: string, orden: number, oculto = false) => fila(clave, null, { orden, oculto });

/** Con cabeceras (W12a): presentación, disciplinas, galería, texto con foto 2, profesores, eventos, tarifas. */
const CON_CABECERAS: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo'),
  cab('presentacion', 0),
  cab('disciplinas', 1),
  cab('galeria', 2),
  cab('texto_foto.2', 3),
  fila('texto_foto.2.titulo', 'Qué traer'),
  fila('texto_foto.2.foto', 'Cinturón', { imagenId: 9, imagenUrl: 'https://x/c.webp' }),
  cab('profesores', 4),
  cab('eventos', 5),
  cab('tarifas', 6),
];
const TODOS = new Set(['disciplinas', 'eventos', 'tarifas']);
const orden = (filas: BloqueContenido[]) => ordenCompleto(filas);

describe('bloques de Inicio en el borrador (W12e)', () => {
  it('orden del tema: por cabeceras; sin ellas, el de antes; las fijas que no se pintan no son puesto', () => {
    expect(orden(CON_CABECERAS)).toEqual(['presentacion', 'disciplinas', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas']);
    expect(orden([fila('portada.titular', 'X')])).toEqual(['presentacion', 'disciplinas', 'profesores', 'galeria', 'eventos', 'tarifas']);
    expect(ordenVisible(CON_CABECERAS, new Set(['disciplinas']))).toEqual(['presentacion', 'disciplinas', 'galeria', 'texto_foto.2', 'profesores']);
    expect(ordenVisible(CON_CABECERAS, null)).toEqual(orden(CON_CABECERAS));
  });

  it('mover sobre una fija: la salta y las fijas conservan su orden relativo (boceto `mover`, `FIJOS`)', () => {
    const r = moverBloque(CON_CABECERAS, 'presentacion', 1, TODOS)!;
    expect(orden(r.filas)).toEqual(['disciplinas', 'presentacion', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas']);
    expect(r.puesto).toBe(1);
    expect(r.total).toBe(7);
    // Profesores baja dos veces: tras eventos y tras tarifas.
    const a = moverBloque(CON_CABECERAS, 'profesores', 1, TODOS)!;
    const b = moverBloque(a.filas, 'profesores', 1, TODOS)!;
    expect(orden(b.filas)).toEqual(['presentacion', 'disciplinas', 'galeria', 'texto_foto.2', 'eventos', 'tarifas', 'profesores']);
    expect(b.puesto).toBe(6);
  });

  it('una fija que no se pinta no cuenta: bajar el último visible no hace nada', () => {
    const sinFijas = new Set(['disciplinas']);
    expect(moverBloque(CON_CABECERAS, 'profesores', 1, sinFijas)).toBeNull();
    expect(moverBloque(CON_CABECERAS, 'presentacion', -1, sinFijas)).toBeNull();
    // Subir galería salta disciplinas.
    const r = moverBloque(CON_CABECERAS, 'galeria', -1, sinFijas)!;
    expect(ordenVisible(r.filas, sinFijas)).toEqual(['presentacion', 'galeria', 'disciplinas', 'texto_foto.2', 'profesores']);
  });

  it('las fijas no se mueven ni se ocultan', () => {
    expect(moverBloque(CON_CABECERAS, 'eventos', -1, TODOS)).toBeNull();
    expect(alternarOculto(CON_CABECERAS, 'tarifas')).toEqual(CON_CABECERAS);
  });

  it('sin cabeceras, la primera acción pone las de todos con el orden que ya tenían', () => {
    const antiguo = [fila('portada.titular', 'X'), fila('galeria.1', 'Foto', { imagenUrl: 'https://x/g.webp', orden: 1 })];
    const r = alternarOculto(antiguo, 'galeria');
    expect(orden(r)).toEqual(orden(antiguo));
    expect(r.find((f) => f.clave === 'galeria')).toEqual(jasmine.objectContaining({ oculto: true, texto: null, imagenId: null }));
    expect(r.filter((f) => ['presentacion', 'disciplinas', 'profesores', 'galeria', 'eventos', 'tarifas'].includes(f.clave)).length).toBe(6);
    // La foto de la galería conserva su puesto en la galería.
    expect(r.find((f) => f.clave === 'galeria.1')!.orden).toBe(1);
  });

  it('ocultar y mostrar: el `oculto` de la cabecera', () => {
    const oculto = alternarOculto(CON_CABECERAS, 'texto_foto.2');
    expect(oculto.find((f) => f.clave === 'texto_foto.2')!.oculto).toBeTrue();
    expect(alternarOculto(oculto, 'texto_foto.2').find((f) => f.clave === 'texto_foto.2')!.oculto).toBeFalse();
  });

  it('añadir «Texto con foto»: el menor número libre (ni en el borrador ni en lo guardado) en el puesto pedido', () => {
    expect(numeroTextoFotoLibre(CON_CABECERAS, CON_CABECERAS)).toBe(1);
    const guardado = [...CON_CABECERAS, cab('texto_foto.1', 7)];
    // Quitado sin guardar: no se reutiliza.
    expect(numeroTextoFotoLibre(CON_CABECERAS, guardado)).toBe(3);
    const r = anadirBloque(CON_CABECERAS, guardado, 2, 'texto_foto', TODOS)!;
    expect(r.id).toBe('texto_foto.3');
    expect(orden(r.filas)).toEqual(['presentacion', 'disciplinas', 'texto_foto.3', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas']);
    // Solo su cabecera: el bloque nuevo va vacío (boceto `nuevoBloque`).
    expect(r.filas.filter((f) => f.clave.startsWith('texto_foto.3'))).toEqual([jasmine.objectContaining({ clave: 'texto_foto.3', texto: null, oculto: false })]);
    // Al final: tras el último visible.
    const fin = anadirBloque(CON_CABECERAS, CON_CABECERAS, 5, 'texto_foto', new Set(['disciplinas']))!;
    expect(ordenVisible(fin.filas, new Set(['disciplinas'])).pop()).toBe('texto_foto.1');
  });

  it('sin números libres no se añade; los únicos que ya están, tampoco', () => {
    const llenos = Array.from({ length: 99 }, (_, i) => cab(`texto_foto.${i + 1}`, i));
    expect(numeroTextoFotoLibre(llenos, llenos)).toBeNull();
    expect(anadirBloque(llenos, llenos, 0, 'texto_foto', null)).toBeNull();
    expect(anadirBloque(CON_CABECERAS, CON_CABECERAS, 0, 'galeria', null)).toBeNull();
    const sedes = anadirBloque(CON_CABECERAS, CON_CABECERAS, 1, 'sedes', TODOS)!;
    expect(sedes.id).toBe('sedes');
    expect(orden(sedes.filas)[1]).toBe('sedes');
    expect(anadirBloque(sedes.filas, CON_CABECERAS, 1, 'sedes', TODOS)).toBeNull();
  });

  it('quitar: la cabecera y todas sus filas; se puede quitar cada «Texto con foto» y «Fotos de las sedes»', () => {
    const sin = quitarBloque(CON_CABECERAS, 'texto_foto.2');
    expect(sin.some((f) => f.clave.startsWith('texto_foto.2'))).toBeFalse();
    expect(sin.length).toBe(CON_CABECERAS.length - 3);
    expect(esQuitable('texto_foto.2')).toBeTrue();
    expect(esQuitable('galeria')).toBeFalse();
    // Boceto `quitable`: lo añadido sigue quitable tras guardar (la API acepta un contenido sin la cabecera `sedes`).
    expect(esQuitable('sedes')).toBeTrue();
    // De «Fotos de las sedes» solo sus filas (no los textos de la línea de sedes).
    const conSedes = [...CON_CABECERAS, cab('sedes', 9), fila('sedes.titulo', 'Un tatami'), fila('sedes.proxima_rotulo', 'Próxima')];
    expect(quitarBloque(conSedes, 'sedes').map((f) => f.clave)).toContain('sedes.proxima_rotulo');
    expect(quitarBloque(conSedes, 'sedes').map((f) => f.clave)).not.toContain('sedes.titulo');
  });

  it('cambios (boceto `cambios`): el orden cuenta uno; ocultar, uno; añadir o quitar, dos (el bloque y el orden)', () => {
    expect(cambiosBloques(CON_CABECERAS, CON_CABECERAS).n).toBe(0);
    const movido = moverBloque(CON_CABECERAS, 'galeria', -1, TODOS)!.filas;
    const c = cambiosBloques(CON_CABECERAS, movido);
    expect(c.n).toBe(1);
    // «Sin guardar» en los movibles que cambian de puesto, no en las fijas.
    expect([...c.bloques]).toEqual(['galeria']);
    expect(cambiosBloques(CON_CABECERAS, alternarOculto(movido, 'presentacion')).n).toBe(2);
    const anadido = anadirBloque(CON_CABECERAS, CON_CABECERAS, 0, 'texto_foto', TODOS)!;
    const a = cambiosBloques(CON_CABECERAS, anadido.filas);
    expect(a.n).toBe(2);
    expect(a.fuera.has('texto_foto.1')).toBeTrue();
    const quitado = cambiosBloques(CON_CABECERAS, quitarBloque(CON_CABECERAS, 'texto_foto.2'));
    expect(quitado.n).toBe(2);
    expect(quitado.fuera.has('texto_foto.2')).toBeTrue();
    // Sus textos y fotos no cuentan aparte.
    expect(esDeBloqueFuera('texto_foto.2.titulo', quitado.fuera)).toBeTrue();
    expect(esDeBloqueFuera('texto_foto.2.foto', quitado.fuera)).toBeTrue();
    expect(esDeBloqueFuera('menu.horarios', quitado.fuera)).toBeFalse();
  });

  it('partes con «Sin guardar»: los bloques y la parte de cada texto o foto cambiado', () => {
    const c = cambiosBloques(CON_CABECERAS, alternarOculto(CON_CABECERAS, 'galeria'));
    expect(partesSinGuardar(c, ['menu.horarios', 'texto_foto.2.titulo', 'presentacion.foto', 'contacto.titulo']).sort()).toEqual(
      ['cabecera', 'contacto', 'galeria', 'presentacion', 'texto_foto.2'].sort(),
    );
  });

  it('hoja y confirmación con los textos del boceto', () => {
    expect(lineaHoja(1, 7, false, false)).toBe('Puesto 2 de 7 · Se ve en la web');
    expect(lineaHoja(0, 7, true, true)).toBe('Puesto 1 de 7 · Oculto en la web');
    expect(lineaHoja(6, 7, false, true)).toBe('Puesto 7 de 7 · Vacío: no sale en la web');
    expect(confirmacionQuitar('texto_foto.2', CON_CABECERAS)).toEqual({
      titulo: '¿Quitar «Qué traer» de tu web?',
      mensaje: 'Se quita al guardar. Hasta entonces, «Descartar» lo recupera.',
      accion: 'Quitar bloque',
      cancelar: 'Cancelar',
    });
    expect(confirmacionQuitar('sedes', CON_CABECERAS).titulo).toBe('¿Quitar «Fotos de las sedes» de tu web?');
  });
});
