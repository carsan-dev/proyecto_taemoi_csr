import { esFotoEditable, fotoDeDescripcion } from '../../../web-plantilla/editor/fotos-sueltas';
import { esFotoProfesor, etiquetaMonograma, numeroPermiso } from '../../../web-plantilla/editor/fotos-profesores';
import { defTexto, esTextoEditable } from '../../../web-plantilla/editor/textos-editor';
import { cuerpoGuardar, erroresContenido, nombreError, ponerTexto } from './web-contenido-guardar';
import { queFoto } from './web-contenido-fotos';
import {
  anadirProfesor,
  cambiosProfesores,
  erroresProfesores,
  estadoProfesores,
  numeroProfesorNuevo,
  numerosProfesores,
  permisosParaEnviar,
  pidePermiso,
  quitarProfesor,
  stripJava,
  textoConstancia,
  vecinoProfesor,
} from './web-contenido-profesores';
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
const PANEL = { origen: 'PANEL' as const, por: 'María Ruiz', en: '2026-11-03T18:42:10' };
const ALTA = { origen: 'ALTA' as const, por: null, en: '2026-10-01T09:00:00' };
const GUARDADO: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('profesor.1.nombre', 'Antonio Ruiz', { permiso: PANEL }),
  fila('profesor.1.cargo', 'Cinturón negro 5.º dan'),
  fila('profesor.1.sedes', '3,4'),
  fila('profesor.3.nombre', 'Lucía Fernández', { permiso: ALTA }),
  fila('profesor.3.foto', 'Lucía en el tatami', { imagenId: 9, imagenUrl: 'https://x/l.webp' }),
  fila('profesor.3.sedes', '5'),
];
const nadie = new Set<number>();

/** W12f-2b: profesores de «Web · Contenido» (boceto `profeHtml`, `anadirProfe`, `quitarProfe`; API `ContenidoWebService`). */
describe('profesores de «Web · Contenido» (W12f-2b, lógica pura)', () => {
  it('claves: foto editable, nombre y cargo editables (60 y 40, nombre obligatorio), permiso solo para errores', () => {
    expect(['profesor.1.foto', 'profesor.99.foto'].every(esFotoEditable)).toBeTrue();
    expect(['profesor.0.foto', 'profesor.100.foto', 'profesor.1.nombre'].some(esFotoProfesor)).toBeFalse();
    expect(fotoDeDescripcion('profesor.4.foto.alt')).toBe('profesor.4.foto');
    expect(defTexto('profesor.2.nombre')).toEqual(jasmine.objectContaining({ max: 60, obligatorio: true, vacio: 'Nombre', falta: 'Escribe su nombre.' }));
    expect(defTexto('profesor.2.cargo')).toEqual(jasmine.objectContaining({ max: 40, obligatorio: false, vacio: 'Cargo o grado (opcional)' }));
    expect(['profesor.2.sedes', 'profesor.2.foto', 'profesor.2.permiso', 'profesor.100.nombre'].some(esTextoEditable)).toBeFalse();
    expect(numeroPermiso('profesor.12.permiso')).toBe(12);
    expect(numeroPermiso('profesor.12.nombre')).toBeNull();
  });

  describe('número de un profesor nuevo (d531: nunca se reutiliza)', () => {
    it('uno más que el mayor guardado, del borrador o usado; sin rellenar huecos', () => {
      expect(numeroProfesorNuevo(GUARDADO, GUARDADO)).toBe(4);
      // El 2 está libre pero no se rellena.
      expect(numerosProfesores(GUARDADO)).toEqual([1, 3]);
      expect(numeroProfesorNuevo(GUARDADO, GUARDADO, [7])).toBe(8);
    });

    it('quitar y volver a añadir: número nuevo y casilla sin marcar', () => {
      const usados = new Set(numerosProfesores(GUARDADO));
      const a = anadirProfesor(GUARDADO, GUARDADO, usados)!;
      expect(a.n).toBe(4);
      usados.add(a.n);
      const sin = quitarProfesor(a.filas, 4);
      const b = anadirProfesor(sin, GUARDADO, usados)!;
      expect(b.n).toBe(5);
      expect(estadoProfesores(GUARDADO, b.filas, nadie).find((p) => p.n === 5)!.permiso).toEqual({ tipo: 'casilla', marcada: false });
    });

    it('quitar y guardar (simulado): el número del quitado no vuelve aunque ya no esté guardado', () => {
      const usados = new Set(numerosProfesores(GUARDADO));
      const guardadoDespues = quitarProfesor(GUARDADO, 3);
      expect(numeroProfesorNuevo(guardadoDespues, guardadoDespues)).toBe(2);
      expect(numeroProfesorNuevo(guardadoDespues, guardadoDespues, usados)).toBe(4);
    });

    it('rango agotado: el menor que no esté guardado, en el borrador ni usado; si no hay, no se añade', () => {
      const usados = new Set(Array.from({ length: 99 }, (_, i) => i + 1).filter((n) => n !== 2 && n !== 50));
      expect(numeroProfesorNuevo(GUARDADO, GUARDADO, usados)).toBe(2);
      const todos = new Set(Array.from({ length: 99 }, (_, i) => i + 1));
      expect(numeroProfesorNuevo([], [], todos)).toBeNull();
      expect(anadirProfesor(GUARDADO, GUARDADO, todos)).toBeNull();
      // Libre en el rango, pero guardado o en el borrador: tampoco.
      const salvo2y99 = [...todos].filter((n) => n !== 2 && n !== 99);
      expect(numeroProfesorNuevo([fila('profesor.99.nombre', 'Z'), fila('profesor.2.nombre', '')], GUARDADO, salvo2y99)).toBeNull();
    });

    it('al añadir: la tarjeta va con su nombre vacío y los bloques con su cabecera (la sección no se mueve)', () => {
      const r = anadirProfesor(GUARDADO, GUARDADO, [1, 3])!;
      expect(r.filas.find((f) => f.clave === 'profesor.4.nombre')).toEqual(fila('profesor.4.nombre', ''));
      expect(r.filas.some((f) => f.clave === 'profesores')).toBeTrue();
    });
  });

  it('quitar: fuera todas sus filas (también sus sedes); las de los demás, intactas; foco al siguiente o al de antes', () => {
    const sin = quitarProfesor(GUARDADO, 1);
    expect(sin.some((f) => f.clave.startsWith('profesor.1.'))).toBeFalse();
    expect(sin.find((f) => f.clave === 'profesor.3.sedes')).toEqual(fila('profesor.3.sedes', '5'));
    expect(vecinoProfesor(GUARDADO, 1)).toBe(3);
    expect(vecinoProfesor(GUARDADO, 3)).toBe(1);
    expect(vecinoProfesor(quitarProfesor(GUARDADO, 3), 1)).toBeNull();
  });

  describe('permiso y cuerpo del PUT', () => {
    const permisoDe = (bloques: { clave: string; permiso?: unknown }[], clave: string) =>
      bloques.find((b) => b.clave === clave)!.permiso;

    it('guardado sin cambios: conserva la constancia; el PUT no manda permiso (jamás el objeto del GET)', () => {
      expect(pidePermiso(GUARDADO, GUARDADO, 1)).toBeFalse();
      const marcados = new Set([1, 3]);
      const permisos = permisosParaEnviar(GUARDADO, GUARDADO, marcados);
      expect([...permisos]).toEqual([]);
      const { bloques } = cuerpoGuardar(5, GUARDADO, {}, permisos);
      expect(bloques.some((b) => 'permiso' in b)).toBeFalse();
      expect(JSON.stringify(bloques)).not.toContain('María');
      expect(JSON.stringify(bloques)).not.toContain('PANEL');
      expect(JSON.stringify(bloques)).not.toContain('ALTA');
    });

    it('sedes guardadas: viajan intactas en el reemplazo completo', () => {
      const { bloques } = cuerpoGuardar(5, ponerTexto(GUARDADO, 'profesor.1.cargo', 'Maestro'), {});
      expect(bloques.find((b) => b.clave === 'profesor.1.sedes')).toEqual({ clave: 'profesor.1.sedes', texto: '3,4', imagenId: null, orden: 0, oculto: false });
      expect(bloques.find((b) => b.clave === 'profesor.3.sedes')!.texto).toBe('5');
    });

    it('profesor nuevo: `true` solo con la casilla marcada; sin marcar, nada (y no se puede guardar)', () => {
      const conNuevo = ponerTexto(anadirProfesor(GUARDADO, GUARDADO, [1, 3])!.filas, 'profesor.4.nombre', 'Rosa Gil');
      expect(pidePermiso(GUARDADO, conNuevo, 4)).toBeTrue();
      expect(erroresProfesores(GUARDADO, conNuevo, nadie)).toEqual({
        'profesor.4.permiso': 'Marca la casilla si tienes su permiso. Sin él no puede salir en la web.',
      });
      expect(permisoDe(cuerpoGuardar(5, conNuevo, {}, permisosParaEnviar(GUARDADO, conNuevo, nadie)).bloques, 'profesor.4.nombre')).toBeUndefined();
      const marcados = new Set([4]);
      expect(erroresProfesores(GUARDADO, conNuevo, marcados)).toEqual({});
      const { bloques } = cuerpoGuardar(5, conNuevo, {}, permisosParaEnviar(GUARDADO, conNuevo, marcados));
      expect(permisoDe(bloques, 'profesor.4.nombre')).toBeTrue();
      expect(bloques.filter((b) => 'permiso' in b).map((b) => b.clave)).toEqual(['profesor.4.nombre']);
      // Siempre un booleano: nunca un objeto.
      expect(bloques.every((b) => b.permiso === undefined || b.permiso === true)).toBeTrue();
    });

    it('nombre cambiado en uno guardado: vuelve la casilla; con ella, `true`; volver al nombre guardado la quita', () => {
      const otro = ponerTexto(GUARDADO, 'profesor.1.nombre', 'Antonio Ruiz Carmona');
      expect(pidePermiso(GUARDADO, otro, 1)).toBeTrue();
      expect(estadoProfesores(GUARDADO, otro, nadie)[0].permiso).toEqual({ tipo: 'casilla', marcada: false });
      expect(Object.keys(erroresProfesores(GUARDADO, otro, nadie))).toEqual(['profesor.1.permiso']);
      const marcados = new Set([1]);
      expect(permisoDe(cuerpoGuardar(5, otro, {}, permisosParaEnviar(GUARDADO, otro, marcados)).bloques, 'profesor.1.nombre')).toBeTrue();

      const deVuelta = ponerTexto(otro, 'profesor.1.nombre', 'Antonio Ruiz');
      expect(estadoProfesores(GUARDADO, deVuelta, marcados)[0].permiso).toEqual({ tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' });
      expect([...permisosParaEnviar(GUARDADO, deVuelta, marcados)]).toEqual([]);
      expect(erroresProfesores(GUARDADO, deVuelta, nadie)).toEqual({});
    });

    it('comparación como la API (`strip()` y nada más): espacios de los extremos no cuentan; mayúsculas y dentro sí', () => {
      expect(pidePermiso(GUARDADO, ponerTexto(GUARDADO, 'profesor.1.nombre', '  Antonio Ruiz \n'), 1)).toBeFalse();
      expect(pidePermiso(GUARDADO, ponerTexto(GUARDADO, 'profesor.1.nombre', 'antonio ruiz'), 1)).toBeTrue();
      expect(pidePermiso(GUARDADO, ponerTexto(GUARDADO, 'profesor.1.nombre', 'Antonio  Ruiz'), 1)).toBeTrue();
      // El espacio de no separación no es blanco para `String.strip()`.
      expect(pidePermiso(GUARDADO, ponerTexto(GUARDADO, 'profesor.1.nombre', 'Antonio Ruiz '), 1)).toBeTrue();
      expect(stripJava(' Ana\t')).toBe('Ana');
    });

    it('guardado sin constancia (datos de antes): pide la casilla', () => {
      const sin = [fila('profesor.2.nombre', 'Pedro')];
      expect(pidePermiso(sin, sin, 2)).toBeTrue();
      expect(estadoProfesores(sin, sin, nadie)[0].permiso).toEqual({ tipo: 'casilla', marcada: false });
    });

    it('el permiso que pidió la API (400) vuelve a pedir la casilla aunque haya constancia', () => {
      const pedidos = new Set([3]);
      expect(pidePermiso(GUARDADO, GUARDADO, 3, pedidos)).toBeTrue();
      expect([...permisosParaEnviar(GUARDADO, GUARDADO, new Set([3]), pedidos)]).toEqual([3]);
    });
  });

  it('constancia: PANEL con quién y fecha, ALTA con su fecha (d526); nunca la hora', () => {
    expect(textoConstancia(PANEL)).toBe('Permiso marcado por María Ruiz el 03/11/2026');
    expect(textoConstancia(ALTA)).toBe('Permiso dado en el alta el 01/10/2026');
    expect(textoConstancia({ origen: 'PANEL', por: null, en: '2026-11-05T08:00:00' })).toBe('Permiso marcado el 05/11/2026');
    const estados = estadoProfesores(GUARDADO, GUARDADO, nadie);
    expect(estados).toEqual([
      { n: 1, nombre: 'Antonio Ruiz', permiso: { tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' } },
      { n: 3, nombre: 'Lucía Fernández', permiso: { tipo: 'constancia', texto: 'Permiso dado en el alta el 01/10/2026' } },
    ]);
  });

  it('errores: nombre vacío «Escribe su nombre.», foto sin descripción «Describe su foto.»; nombres del resumen', () => {
    const r = anadirProfesor(GUARDADO, GUARDADO, [1, 3])!;
    const filas = [...r.filas.map((f) => (f.clave === 'profesor.3.foto' ? { ...f, texto: ' ' } : f))];
    const errores = erroresContenido(filas, {});
    expect(errores['profesor.4.nombre']).toBe('Escribe su nombre.');
    expect(errores['profesor.3.foto.alt']).toBe('Describe su foto.');
    expect(nombreError('profesor.4.nombre', filas)).toBe('Profesor nuevo: nombre');
    expect(nombreError('profesor.4.permiso', filas)).toBe('Profesor nuevo: permiso para publicar');
    expect(nombreError('profesor.3.foto.alt', filas)).toBe('Foto de Lucía Fernández: falta describirla');
    expect(queFoto('profesor.4.foto', filas)).toBe('de el profesor nuevo');
    // Sin fila de nombre (solo cargo): también falta.
    expect(erroresProfesores([], [fila('profesor.8.cargo', 'Maestro')], new Set([8]))).toEqual({ 'profesor.8.nombre': 'Escribe su nombre.' });
    expect(etiquetaMonograma('')).toBe('Sin foto: iniciales de Profesor nuevo');
  });

  it('cambios: añadido y quitado cuentan uno; foto, descripción, sedes y casilla de uno guardado, uno cada uno', () => {
    const r = anadirProfesor(GUARDADO, GUARDADO, [1, 3])!;
    let filas = quitarProfesor(r.filas, 1);
    filas = filas.map((f) => (f.clave === 'profesor.3.foto' ? { ...f, imagenId: 10, texto: 'Otra' } : f));
    const c = cambiosProfesores(GUARDADO, filas, new Set([3, 4]));
    expect(c.claves).toEqual(['profesor.4.anadido', 'profesor.1.quitado', 'profesor.3.foto', 'profesor.3.foto.alt']);
    expect([...c.fuera]).toEqual([4, 1]);
    // La casilla de uno guardado cuenta solo si la necesita (nombre cambiado).
    const otro = ponerTexto(GUARDADO, 'profesor.3.nombre', 'Lucía F.');
    expect(cambiosProfesores(GUARDADO, otro, new Set([3])).claves).toEqual(['profesor.3.permiso']);
    expect(cambiosProfesores(GUARDADO, GUARDADO, new Set([3])).claves).toEqual([]);
  });
});
