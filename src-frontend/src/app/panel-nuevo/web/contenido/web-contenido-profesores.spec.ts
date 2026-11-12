import { esFotoEditable, fotoDeDescripcion } from '../../../web-plantilla/editor/fotos-sueltas';
import { esFotoProfesor, etiquetaMonograma, numeroPermiso } from '../../../web-plantilla/editor/fotos-profesores';
import { defTexto, esTextoEditable } from '../../../web-plantilla/editor/textos-editor';
import { ordenCompleto } from '../../../web-plantilla/editor/bloques-editor';
import { cambiosBloques } from './web-contenido-bloques';
import { cuerpoGuardar, erroresContenido, nombreError, ponerTexto } from './web-contenido-guardar';
import { queFoto } from './web-contenido-fotos';
import {
  anadirProfesor,
  cambiosProfesores,
  csvDeSedes,
  elegirSedes,
  erroresProfesores,
  estadoProfesores,
  idsDeSedes,
  marcasVigentes,
  numeroProfesorNuevo,
  numerosProfesores,
  permisosParaEnviar,
  pidePermiso,
  quitarProfesor,
  sedesDelProfesor,
  sedesElegibles,
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
      { n: 1, nombre: 'Antonio Ruiz', permiso: { tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' }, sedes: [] },
      { n: 3, nombre: 'Lucía Fernández', permiso: { tipo: 'constancia', texto: 'Permiso dado en el alta el 01/10/2026' }, sedes: [] },
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

  describe('permiso ligado al nombre con que se marcó (decisión del propietario, 12/11)', () => {
    const conNuevo = (nombre: string) => ponerTexto(anadirProfesor(GUARDADO, GUARDADO, [1, 3])!.filas, 'profesor.4.nombre', nombre);

    it('la marca cuenta solo con el mismo nombre (como la API: `strip()`); con otro, no', () => {
      const marcas = new Map([[4, 'Ana Ruiz']]);
      expect([...marcasVigentes(conNuevo('Ana Ruiz'), marcas)]).toEqual([4]);
      expect([...marcasVigentes(conNuevo('  Ana Ruiz\t'), marcas)]).toEqual([4]);
      expect([...marcasVigentes(conNuevo('Ana Ruiz Gil'), marcas)]).toEqual([]);
      expect([...marcasVigentes(conNuevo('ana ruiz'), marcas)]).toEqual([]);
      // Sin la fila del nombre (quitado), tampoco.
      expect([...marcasVigentes(quitarProfesor(conNuevo('Ana Ruiz'), 4), marcas)]).toEqual([]);
    });

    it('invariante: el PUT nunca lleva `permiso: true` con un nombre distinto del que había al marcar', () => {
      const nombres = ['', 'Ana', 'Ana ', 'Pedro', 'Antonio Ruiz', 'Antonio R.', ' Lucía Fernández'];
      for (const marcadoCon of nombres) {
        for (const ahora of nombres) {
          for (const n of [1, 3, 4]) {
            const base = n === 4 ? conNuevo('') : GUARDADO;
            const borrador = ponerTexto(base, `profesor.${n}.nombre`, ahora);
            const marcas = new Map([[n, stripJava(marcadoCon)]]);
            const { bloques } = cuerpoGuardar(5, borrador, {}, permisosParaEnviar(GUARDADO, borrador, marcasVigentes(borrador, marcas), new Set([n])));
            const enviado = bloques.find((b) => b.clave === `profesor.${n}.nombre`)!;
            if (enviado.permiso === true) {
              expect(stripJava(enviado.texto)).withContext(`${n}: «${marcadoCon}» → «${ahora}»`).toBe(stripJava(marcadoCon));
            }
          }
        }
      }
    });
  });

  it('quitar un profesor fija antes las cabeceras: la sección no se mueve y no cuenta un cambio de orden falso', () => {
    const sinCabeceras = [
      fila('presentacion.texto', 'Hola', { orden: 2 }),
      fila('profesor.1.nombre', 'Antonio Ruiz', { orden: 1, permiso: PANEL }),
      fila('profesor.2.nombre', 'Lucía Fernández', { orden: 4, permiso: ALTA }),
      fila('galeria.1', 'Clase', { orden: 3, imagenId: 5, imagenUrl: 'https://x/g.webp' }),
    ];
    const sin = quitarProfesor(sinCabeceras, 1);
    expect(ordenCompleto(sin)).toEqual(ordenCompleto(sinCabeceras));
    expect(cambiosBloques(sinCabeceras, sin).n).toBe(0);
    expect(cambiosProfesores(sinCabeceras, sin, nadie).claves).toEqual(['profesor.1.quitado']);
    // Un número que no está: nada cambia.
    expect(quitarProfesor(sinCabeceras, 9)).toEqual(sinCabeceras);
  });
});

/** W12f-3: sedes de cada profesor (`profesor.<n>.sedes`; API `ContenidoWebService.sedesDelProfesor`). */
describe('sedes de cada profesor (W12f-3, lógica pura)', () => {
  /** Las activas de la vista previa, en el orden de la API (no por id). La 7 es inactiva: no está. */
  const ELEGIBLES = [6, 3, 4, 5];
  const texto = (filas: readonly BloqueContenido[], clave: string) => filas.find((f) => f.clave === clave)?.texto;
  const con = (filas: readonly BloqueContenido[], clave: string, t: string) => filas.map((f) => (f.clave === clave ? { ...f, texto: t } : f));

  it('CSV ↔ ids: «1,2» sin espacios; lo que no es un id se ignora; sin repetidos', () => {
    expect(idsDeSedes('3,4')).toEqual([3, 4]);
    expect(idsDeSedes('4,3')).toEqual([4, 3]);
    expect(idsDeSedes('')).toEqual([]);
    expect(idsDeSedes(null)).toEqual([]);
    expect(idsDeSedes(undefined)).toEqual([]);
    expect(idsDeSedes(' 3, 4 ')).toEqual([3, 4]);
    expect(idsDeSedes('3,,x,4,3,0,-1,1.5,1e3,9007199254740993')).toEqual([3, 4]);
    expect(csvDeSedes([1, 2])).toBe('1,2');
    expect(csvDeSedes([])).toBe('');
  });

  it('elegibles: solo id y nombre, ids válidos y sin repetir, en el orden de la API', () => {
    const sedes = [
      { id: 6, nombre: 'Casariche', slug: 'casariche' },
      { nombre: 'Sin id', slug: null },
      { id: 6, nombre: 'Repetida', slug: null },
      { id: 0, nombre: 'Cero', slug: null },
      { id: '4', nombre: 'Texto', slug: null },
      { id: 1.5, nombre: 'Decimal', slug: null },
      { id: 3, nombre: 'Estepa', slug: 'estepa' },
    ];
    expect(sedesElegibles(sedes)).toEqual([
      { id: 6, nombre: 'Casariche' },
      { id: 3, nombre: 'Estepa' },
    ]);
    expect(sedesElegibles([])).toEqual([]);
  });

  it('a la capa: las de su fila que son elegibles, en el orden de la lista; sin fila, ninguna', () => {
    const filas = con(GUARDADO, 'profesor.1.sedes', '7,5,3');
    expect(sedesDelProfesor(filas, 1, ELEGIBLES)).toEqual([3, 5]);
    expect(sedesDelProfesor(filas, 3, ELEGIBLES)).toEqual([5]);
    expect(sedesDelProfesor(filas, 9, ELEGIBLES)).toEqual([]);
    expect(sedesDelProfesor(filas, 1, [])).toEqual([]);
    expect(estadoProfesores(GUARDADO, GUARDADO, nadie, nadie, ELEGIBLES).map((p) => p.sedes)).toEqual([[3, 4], [5]]);
  });

  it('cambiar: solo elegibles y en el orden de la lista, CSV sin espacios; el resto del borrador, intacto', () => {
    const r = elegirSedes(GUARDADO, GUARDADO, 1, [5, 99, 6], ELEGIBLES)!;
    expect(texto(r, 'profesor.1.sedes')).toBe('6,5');
    expect(r.filter((f) => f.clave !== 'profesor.1.sedes')).toEqual(GUARDADO.filter((f) => f.clave !== 'profesor.1.sedes'));
    expect(cambiosProfesores(GUARDADO, r, nadie).claves).toEqual(['profesor.1.sedes']);
    // Ninguna: la fila queda vacía (no se borra: estaba guardada con sedes).
    const vacia = elegirSedes(GUARDADO, r, 1, [], ELEGIBLES)!;
    expect(texto(vacia, 'profesor.1.sedes')).toBe('');
    expect(cuerpoGuardar(5, vacia, {}).bloques.find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('');
  });

  it('invariante c: el mismo conjunto que lo guardado no cuenta ni reescribe la fila, aunque venga en otro orden', () => {
    const guardado = con(GUARDADO, 'profesor.1.sedes', '4,3');
    // Sin tocar antes: nada que hacer.
    expect(elegirSedes(guardado, guardado, 1, [3, 4], ELEGIBLES)).toBeNull();
    expect(elegirSedes(guardado, guardado, 1, [4, 3], ELEGIBLES)).toBeNull();
    // Tocado y de vuelta: la fila vuelve a ser la guardada byte a byte.
    const tocado = elegirSedes(guardado, guardado, 1, [3], ELEGIBLES)!;
    expect(texto(tocado, 'profesor.1.sedes')).toBe('3');
    const deVuelta = elegirSedes(guardado, tocado, 1, [3, 4], ELEGIBLES)!;
    expect(texto(deVuelta, 'profesor.1.sedes')).toBe('4,3');
    expect(cambiosProfesores(guardado, deVuelta, nadie).claves).toEqual([]);
    // El mismo conjunto que ya tiene el borrador: nada que hacer.
    expect(elegirSedes(guardado, tocado, 1, [3], ELEGIBLES)).toBeNull();
  });

  it('invariante a: las sedes inactivas guardadas (fuera de la lista) se conservan si el conjunto elegible no cambia', () => {
    const guardado = con(GUARDADO, 'profesor.1.sedes', '7,3');
    expect(elegirSedes(guardado, guardado, 1, [3], ELEGIBLES)).toBeNull();
    const tocado = elegirSedes(guardado, guardado, 1, [3, 4], ELEGIBLES)!;
    // Cambiado de verdad: solo elegibles (la 7 sale, como el boceto).
    expect(texto(tocado, 'profesor.1.sedes')).toBe('3,4');
    const deVuelta = elegirSedes(guardado, tocado, 1, [3], ELEGIBLES)!;
    expect(texto(deVuelta, 'profesor.1.sedes')).toBe('7,3');
    expect(cambiosProfesores(guardado, deVuelta, nadie).claves).toEqual([]);
    // Y el PUT de otro cambio la reenvía tal cual.
    const otro = elegirSedes(guardado, guardado, 3, [6], ELEGIBLES)!;
    const { bloques } = cuerpoGuardar(5, otro, {});
    expect(bloques.find((b) => b.clave === 'profesor.1.sedes')).toEqual({ clave: 'profesor.1.sedes', texto: '7,3', imagenId: null, orden: 0, oculto: false });
    expect(bloques.find((b) => b.clave === 'profesor.3.sedes')!.texto).toBe('6');
  });

  it('sin fila: se crea como las demás del profesor (con cabeceras, orden 0, visible); sin sedes no se crea; volver a ninguna la quita', () => {
    const sinFila = GUARDADO.filter((f) => f.clave !== 'profesor.1.sedes');
    expect(elegirSedes(sinFila, sinFila, 1, [], ELEGIBLES)).toBeNull();
    const r = elegirSedes(sinFila, sinFila, 1, [4], ELEGIBLES)!;
    expect(r.find((f) => f.clave === 'profesor.1.sedes')).toEqual(fila('profesor.1.sedes', '4'));
    expect(ordenCompleto(r)).toEqual(ordenCompleto(sinFila));
    expect(cambiosBloques(sinFila, r).n).toBe(0);
    expect(cambiosProfesores(sinFila, r, nadie).claves).toEqual(['profesor.1.sedes']);
    const ninguna = elegirSedes(sinFila, r, 1, [], ELEGIBLES)!;
    expect(ninguna.some((f) => f.clave === 'profesor.1.sedes')).toBeFalse();
    expect(cambiosProfesores(sinFila, ninguna, nadie).claves).toEqual([]);
  });

  it('invariante e: un profesor nuevo lleva sedes; al quitarlo, su fila se va; cuenta solo como añadido', () => {
    const r = anadirProfesor(GUARDADO, GUARDADO, [1, 3])!;
    const conSedes = elegirSedes(GUARDADO, r.filas, 4, [5, 6], ELEGIBLES)!;
    expect(texto(conSedes, 'profesor.4.sedes')).toBe('6,5');
    expect(cambiosProfesores(GUARDADO, conSedes, nadie).claves).toEqual(['profesor.4.anadido']);
    const sin = quitarProfesor(conSedes, 4);
    expect(sin.some((f) => f.clave.startsWith('profesor.4.'))).toBeFalse();
    expect(numeroProfesorNuevo(sin, GUARDADO, [1, 3, 4])).toBe(5);
  });

  it('invariante b: cambiar sedes no pide ni manda el permiso', () => {
    const r = elegirSedes(GUARDADO, GUARDADO, 1, [6], ELEGIBLES)!;
    expect(pidePermiso(GUARDADO, r, 1)).toBeFalse();
    expect(erroresProfesores(GUARDADO, r, nadie)).toEqual({});
    expect(estadoProfesores(GUARDADO, r, nadie, nadie, ELEGIBLES)[0].permiso['tipo']).toBe('constancia');
    expect(JSON.stringify(cuerpoGuardar(5, r, {}, permisosParaEnviar(GUARDADO, r, nadie)))).not.toContain('permiso');
  });

  it('invariante h: peticiones que no valen no cambian nada (profesor que no está, más ids que sedes, sin lista)', () => {
    expect(elegirSedes(GUARDADO, GUARDADO, 9, [3], ELEGIBLES)).toBeNull();
    expect(elegirSedes(GUARDADO, GUARDADO, 1, [6, 3, 4, 5, 99], ELEGIBLES)).toBeNull();
    expect(elegirSedes(GUARDADO, GUARDADO, 1, [3], [])).toBeNull();
    expect(elegirSedes(GUARDADO, GUARDADO, 1, [], [])).toBeNull();
    // Solo ids que no son elegibles: como pedir ninguna.
    expect(texto(elegirSedes(GUARDADO, GUARDADO, 1, [98, 99], ELEGIBLES)!, 'profesor.1.sedes')).toBe('');
  });

  it('fila rechazada por la API (`forzar`): marcar y desmarcar deja solo elegibles, no vuelve a la guardada que falló', () => {
    const guardado = con(GUARDADO, 'profesor.1.sedes', '3,99');
    // Lo que manda la interfaz: el conjunto entero de casillas tras cada cambio (marca 4; desmarca 4).
    const marcada = elegirSedes(guardado, guardado, 1, [3, 4], ELEGIBLES, true)!;
    expect(texto(marcada, 'profesor.1.sedes')).toBe('3,4');
    const desmarcada = elegirSedes(guardado, marcada, 1, [3], ELEGIBLES, true)!;
    expect(texto(desmarcada, 'profesor.1.sedes')).toBe('3');
    expect(cambiosProfesores(guardado, desmarcada, nadie).claves).toEqual(['profesor.1.sedes']);
    expect(cuerpoGuardar(5, desmarcada, {}).bloques.find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('3');
    // Sin `forzar` (fila no rechazada), la misma secuencia vuelve a la guardada byte a byte (invariante c).
    const sinForzar = elegirSedes(guardado, elegirSedes(guardado, guardado, 1, [3, 4], ELEGIBLES)!, 1, [3], ELEGIBLES)!;
    expect(texto(sinForzar, 'profesor.1.sedes')).toBe('3,99');
    expect(cambiosProfesores(guardado, sinForzar, nadie).claves).toEqual([]);
  });
});
