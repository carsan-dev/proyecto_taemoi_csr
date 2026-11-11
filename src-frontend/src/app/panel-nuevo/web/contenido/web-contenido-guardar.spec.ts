import {
  clavesCambiadas,
  cuerpoGuardar,
  erroresContenido,
  nombreError,
  ponerTexto,
  textoConflicto,
  tituloResumen,
  tituloSalir,
} from './web-contenido-guardar';
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

/** Lo de un GET de verdad: textos, fotos, cabeceras ordenadas y ocultas, y un profesor con su constancia. */
const GUARDADO: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/portada.webp' }),
  fila('presentacion', null, { orden: 2 }),
  fila('galeria', null, { orden: 3, oculto: true }),
  fila('galeria.1', 'Clase', { imagenId: 8, imagenUrl: 'https://x/g1.webp', orden: 1 }),
  fila('profesor.1.nombre', 'Ana Ruiz', { permiso: { origen: 'PANEL', por: 'María', en: '2026-11-03T10:00:00' } }),
  fila('profesor.1.sedes', '3,4'),
  fila('menu.horarios', 'Horario'),
];

describe('web-contenido-guardar (W12d)', () => {
  it('ponerTexto cambia la fila o añade una sin foto', () => {
    const cambiada = ponerTexto(GUARDADO, 'menu.horarios', 'Horas');
    expect(cambiada.find((f) => f.clave === 'menu.horarios')!.texto).toBe('Horas');
    expect(cambiada.length).toBe(GUARDADO.length);
    const nueva = ponerTexto(GUARDADO, 'cabecera.boton', 'Apúntate');
    expect(nueva.at(-1)).toEqual(fila('cabecera.boton', 'Apúntate'));
    expect(GUARDADO.length).toBe(8);
  });

  describe('cambios', () => {
    it('cada texto cambiado cuenta uno; sin fila vale el texto del tema', () => {
      let b = ponerTexto(GUARDADO, 'menu.horarios', 'Horas');
      b = ponerTexto(b, 'cabecera.boton', 'Apúntate');
      expect(clavesCambiadas(GUARDADO, b, {})).toEqual(['menu.horarios', 'cabecera.boton']);
    });

    it('volver al texto del tema no es un cambio (fijo o leído de la página)', () => {
      const b = ponerTexto(ponerTexto(GUARDADO, 'cabecera.boton', 'Preinscribirme'), 'portada.frase', 'Grupos por edad.');
      expect(clavesCambiadas(GUARDADO, b, { 'portada.frase': 'Grupos por edad.' })).toEqual([]);
    });

    it('las filas que no son textos (fotos, cabeceras, profesores) no cuentan', () => {
      expect(clavesCambiadas(GUARDADO, GUARDADO.slice(1), {})).toEqual(['portada.titular']);
    });
  });

  describe('validación', () => {
    it('sin errores con lo guardado', () => {
      expect(erroresContenido(GUARDADO, {})).toEqual({});
    });

    it('titular, obligatorios vacíos, foto de portada; los opcionales vacíos valen', () => {
      let b = ponerTexto(GUARDADO, 'portada.titular', '  ');
      b = ponerTexto(b, 'menu.horarios', '');
      b = ponerTexto(b, 'presentacion.titulo', '');
      b = ponerTexto(b, 'portada.frase', '');
      b = b.filter((f) => f.clave !== 'portada.foto');
      expect(erroresContenido(b, {})).toEqual({
        'portada.titular': 'Escribe el titular.',
        'portada.foto': 'Añade la foto de portada.',
        'menu.horarios': 'Escribe el texto.',
        'presentacion.titulo': 'Escribe el título.',
      });
    });

    it('el título de un texto con foto solo hace falta si el bloque tiene texto o foto', () => {
      const vacio = ponerTexto(GUARDADO, 'texto_foto.1.titulo', '');
      expect(erroresContenido(vacio, {})).toEqual({});
      const conTexto = ponerTexto(vacio, 'texto_foto.1.texto', 'Ropa cómoda');
      expect(erroresContenido(conTexto, {})).toEqual({ 'texto_foto.1.titulo': 'Escribe el título.' });
    });
  });

  describe('cuerpo del PUT', () => {
    it('todas las filas, con imagen, orden y oculto; sin el permiso de los profesores', () => {
      const { version, bloques } = cuerpoGuardar(4, ponerTexto(GUARDADO, 'menu.horarios', 'Horas'), {});
      expect(version).toBe(4);
      expect(bloques).toEqual([
        { clave: 'portada.titular', texto: 'Taekwondo en seis pueblos', imagenId: null, orden: 0, oculto: false },
        { clave: 'portada.foto', texto: 'Niños en fila', imagenId: 7, orden: 0, oculto: false },
        { clave: 'presentacion', texto: null, imagenId: null, orden: 2, oculto: false },
        { clave: 'galeria', texto: null, imagenId: null, orden: 3, oculto: true },
        { clave: 'galeria.1', texto: 'Clase', imagenId: 8, orden: 1, oculto: false },
        { clave: 'profesor.1.nombre', texto: 'Ana Ruiz', imagenId: null, orden: 0, oculto: false },
        { clave: 'profesor.1.sedes', texto: '3,4', imagenId: null, orden: 0, oculto: false },
        { clave: 'menu.horarios', texto: 'Horas', imagenId: null, orden: 0, oculto: false },
      ]);
      expect(JSON.stringify(bloques)).not.toContain('permiso');
      expect(JSON.stringify(bloques)).not.toContain('imagenUrl');
    });

    it('un texto igual al del tema no se manda (al cambiar de tema, cada uno pone el suyo)', () => {
      const b = ponerTexto(ponerTexto(GUARDADO, 'menu.horarios', 'Horarios'), 'cabecera.boton', ' Preinscribirme ');
      const claves = cuerpoGuardar(1, b, {}).bloques.map((x) => x.clave);
      expect(claves).not.toContain('menu.horarios');
      expect(claves).not.toContain('cabecera.boton');
    });

    it('un texto vacío sí se manda (no se pinta en la web)', () => {
      const b = ponerTexto(GUARDADO, 'tarifas.punto2', '');
      expect(cuerpoGuardar(1, b, {}).bloques).toContain({ clave: 'tarifas.punto2', texto: '', imagenId: null, orden: 0, oculto: false });
    });

    it('sin fila de titular, el que pinta el tema (la API lo pide siempre)', () => {
      const b = GUARDADO.filter((f) => f.clave !== 'portada.titular');
      expect(cuerpoGuardar(1, b, { 'portada.titular': 'Taekwondo en Herrera' }).bloques).toContain({
        clave: 'portada.titular',
        texto: 'Taekwondo en Herrera',
        imagenId: null,
        orden: 0,
        oculto: false,
      });
    });
  });

  it('nombres del resumen (boceto `nombreError`)', () => {
    expect(nombreError('portada.foto', GUARDADO)).toBe('Portada: falta la foto');
    expect(nombreError('portada.titular', GUARDADO)).toBe('Portada: titular');
    expect(nombreError('menu.horarios', GUARDADO)).toBe('Cabecera: enlace del menú «Horarios»');
    expect(nombreError('presentacion.titulo', GUARDADO)).toBe('Presentación: título');
    expect(nombreError('texto_foto.1.titulo', ponerTexto(GUARDADO, 'texto_foto.1.titulo', 'Qué traer'))).toBe(
      'Texto con foto «Qué traer»: título',
    );
    expect(nombreError('profesor.1.permiso', GUARDADO)).toBeNull();
  });

  it('textos del boceto', () => {
    expect(tituloResumen(1)).toBe('Falta 1 dato para guardar');
    expect(tituloResumen(3)).toBe('Faltan 3 datos para guardar');
    expect(tituloSalir(1)).toBe('Tienes 1 cambio sin guardar');
    expect(tituloSalir(2)).toBe('Tienes 2 cambios sin guardar');
    expect(textoConflicto(1)).toContain('Al recargar se pierden el cambio que tienes sin guardar.');
    expect(textoConflicto(4)).toContain('Al recargar se pierden los 4 cambios que tienes sin guardar.');
  });
});
