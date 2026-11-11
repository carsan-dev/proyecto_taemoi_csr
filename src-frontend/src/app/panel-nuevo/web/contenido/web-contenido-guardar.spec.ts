import {
  clavesCambiadas,
  cuerpoGuardar,
  entradaResumen,
  erroresContenido,
  nombreError,
  ponerTexto,
  textoConflicto,
  tituloResumen,
  tituloSalir,
} from './web-contenido-guardar';
import { clavesPuntos, defTexto, defectoDeLaPagina } from '../../../web-plantilla/editor/textos-editor';
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

    it('sin fila de titular y sin texto del tema recibido: no se inventa titular (la API responde «Escribe el titular.»)', () => {
      const b = GUARDADO.filter((f) => f.clave !== 'portada.titular');
      expect(erroresContenido(b, {})).toEqual({});
      expect(cuerpoGuardar(1, b, {}).bloques.map((x) => x.clave)).not.toContain('portada.titular');
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

  it('resumen: en la foto de portada, el mensaje de la API si lo hay', () => {
    expect(entradaResumen('portada.foto', 'Añade la foto de portada.', GUARDADO, false)).toEqual({
      clave: 'portada.foto',
      texto: 'Portada: falta la foto',
      enlace: true,
    });
    expect(entradaResumen('portada.foto', 'Describe la foto.', GUARDADO, true)).toEqual({
      clave: 'portada.foto',
      texto: 'Describe la foto.',
      enlace: true,
    });
    expect(entradaResumen('menu.horarios', 'Máximo 20 caracteres.', GUARDADO, true).texto).toBe('Cabecera: enlace del menú «Horarios»');
    expect(entradaResumen('profesor.1.permiso', 'Marca la casilla.', GUARDADO, true)).toEqual({
      clave: 'profesor.1.permiso',
      texto: 'Marca la casilla.',
      enlace: false,
    });
  });

  it('puntos de tarifas: del 1 al 9, como la API (`ClavesWeb`)', () => {
    expect(defTexto('tarifas.punto9')).not.toBeNull();
    expect(defTexto('tarifas.punto10')).toBeNull();
    expect(defTexto('tarifas.punto20')).toBeNull();
    expect(clavesPuntos(() => true).at(-1)).toBe('tarifas.punto9');
  });

  it('textos por defecto leídos de la página: solo los que dependen de los datos, y nunca el «Escribe aquí…»', () => {
    expect(defectoDeLaPagina('portada.titular', 'Taekwondo en Herrera')).toBeTrue();
    expect(defectoDeLaPagina('portada.frase', 'Grupos por edad.')).toBeTrue();
    expect(defectoDeLaPagina('portada.boton_horarios', 'Ver horarios')).toBeTrue();
    expect(defectoDeLaPagina('horarios.apoyo', 'Toca tu pueblo')).toBeTrue();
    expect(defectoDeLaPagina('tarifas.igual', 'Para todos los grupos')).toBeTrue();
    expect(defectoDeLaPagina('presentacion.texto', 'Uno.')).toBeTrue();
    expect(defectoDeLaPagina('portada.frase', 'Escribe aquí una frase: para quién son las clases y qué hacer')).toBeFalse();
    expect(defectoDeLaPagina('portada.titular', '')).toBeFalse();
    expect(defectoDeLaPagina('texto_foto.2.rotulo', '02')).toBeFalse();
    expect(defectoDeLaPagina('texto_foto.2.titulo', 'Escribe aquí el título')).toBeFalse();
    expect(defectoDeLaPagina('menu.horarios', 'Horarios')).toBeFalse();
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
