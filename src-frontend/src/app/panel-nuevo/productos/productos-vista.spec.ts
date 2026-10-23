import { FormControl } from '@angular/forms';

import {
  OPCIONES_ORDEN,
  ORDEN_POR_DEFECTO,
  TIPOS_PRODUCTO,
  cuerpoProducto,
  etiquetaOrden,
  filtrosActivos,
  importeParaCampo,
  leerImporte,
  mensajesError,
  miCajaParaApi,
  ordenar,
  textoTotal,
  validarPrecio,
} from './productos-vista';

describe('productos-vista (8b-3g)', () => {
  // ------------------------------------------------------------------ leer el importe escrito

  describe('leerImporte: el precio escrito en es-ES, sin cambiar el valor que hoy se envía', () => {
    /** [escrito, número enviado]: el número es el mismo `Number` que hoy da el campo `type="number"` con punto. */
    const validos: [string, number][] = [
      ['25', 25],
      ['0', 0],
      ['25,5', 25.5],
      ['25,50', 25.5],
      ['1010,50', 1010.5],
      ['1.010,50', 1010.5],
      ['1.234.567,89', 1234567.89],
      ['1.234.567', 1234567],
      ['12,345', 12.345],
      [',5', 0.5],
      ['0,00', 0],
      // Con punto decimal, como el campo de hoy: «1.5» sigue siendo 1,5 y no 15.
      ['1010.50', 1010.5],
      ['1010.5', 1010.5],
      ['1.5', 1.5],
      ['1.50', 1.5],
      ['0.5', 0.5],
      ['.5', 0.5],
      ['0.500', 0.5],
      ['1234.567', 1234.567],
      // Espacios por fuera y el símbolo del euro (también con el espacio duro de Intl, al copiar un importe).
      ['  25  ', 25],
      ['25 €', 25],
      ['25€', 25],
      ['25,00 €', 25],
      ['1.010,50 €', 1010.5],
      // El signo menos se lee: la validación de hoy lo rechaza después (mayor o igual a 0).
      ['-5', -5],
      ['-1.010,50', -1010.5],
    ];
    for (const [escrito, valor] of validos) {
      it(`«${escrito}» → ${valor}`, () => {
        expect(leerImporte(escrito)).toEqual({ estado: 'valido', valor });
      });
    }

    it('el número es exactamente el que daba el campo de hoy con punto decimal (mismo double)', () => {
      const pares: [string, string][] = [
        ['1.010,50', '1010.50'],
        ['25,5', '25.5'],
        ['0,1', '0.1'],
        ['19,99', '19.99'],
        ['1.234.567,89', '1234567.89'],
      ];
      for (const [es, hoy] of pares) {
        const r = leerImporte(es);
        expect(r.estado === 'valido' && Object.is(r.valor, Number(hoy))).withContext(es).toBeTrue();
      }
    });

    it('«-0» y «0,00» se leen como 0 (sin el cero negativo)', () => {
      const r = leerImporte('-0');
      expect(r.estado === 'valido' && Object.is(r.valor, 0)).toBeTrue();
    });

    it('vacío: cadena vacía, solo espacios, solo «€», null y undefined', () => {
      for (const escrito of ['', '   ', '€', ' € ', null, undefined]) {
        expect(leerImporte(escrito)).withContext(String(escrito)).toEqual({ estado: 'vacio' });
      }
    });

    it('ambiguo: un solo punto seguido de tres cifras («1.234» puede ser mil doscientos o uno con dos)', () => {
      for (const escrito of ['1.234', '1.500', '10.000', '100.250', '-1.234', '1.234 €']) {
        expect(leerImporte(escrito)).withContext(escrito).toEqual({ estado: 'ambiguo' });
      }
    });

    it('formato: lo que no es un número es-ES ni el de hoy, sin adivinar', () => {
      const malos = [
        'abc',
        '12a',
        '1,010.50',
        '1.2.3',
        '1,5,3',
        '12,',
        '12.',
        '1.23,5',
        '12.34.567',
        '1 000',
        '1 5',
        '+5',
        '--5',
        '-',
        '1e3',
        'Infinity',
        'NaN',
        '€25',
        '25 € 30',
        '0x1A',
      ];
      for (const escrito of malos) {
        expect(leerImporte(escrito)).withContext(escrito).toEqual({ estado: 'formato' });
      }
    });
  });

  describe('importeParaCampo: el precio guardado, en es-ES y sin perder decimales', () => {
    const casos: [number | null | undefined, string][] = [
      [1010.5, '1.010,50'],
      [25, '25,00'],
      [12.5, '12,50'],
      [0, '0,00'],
      [0.1, '0,10'],
      [12.345, '12,345'],
      [1234567.89, '1.234.567,89'],
      [-5, '-5,00'],
      [null, ''],
      [undefined, ''],
      [Number.NaN, ''],
    ];
    for (const [valor, campo] of casos) {
      it(`${valor} → «${campo}»`, () => {
        expect(importeParaCampo(valor)).toBe(campo);
      });
    }

    it('ida y vuelta: leer lo que se escribe en el campo da el mismo número', () => {
      for (const valor of [0, 0.5, 1, 9.99, 12.345, 30, 45.5, 999, 1000, 1010.5, 12345.67, 1234567.89]) {
        const r = leerImporte(importeParaCampo(valor));
        expect(r.estado === 'valido' && Object.is(r.valor, valor)).withContext(String(valor)).toBeTrue();
      }
    });
  });

  describe('validarPrecio: la validación de hoy (obligatorio y mayor o igual a 0) más el formato', () => {
    const errores = (valor: unknown) => validarPrecio(new FormControl(valor));

    it('válido: sin errores', () => {
      expect(errores('1.010,50')).toBeNull();
      expect(errores('0')).toBeNull();
      expect(errores('1.5')).toBeNull();
    });

    it('vacío: obligatorio, como hoy', () => {
      expect(errores('')).toEqual({ required: true });
      expect(errores(null)).toEqual({ required: true });
      expect(errores('  ')).toEqual({ required: true });
    });

    it('negativo: mayor o igual a 0, como hoy', () => {
      expect(errores('-5')).toEqual({ min: { min: 0, actual: -5 } });
      expect(errores('-0,01')).toEqual({ min: { min: 0, actual: -0.01 } });
    });

    it('texto: formato; un punto con tres cifras: ambiguo', () => {
      expect(errores('abc')).toEqual({ formato: true });
      expect(errores('1.234')).toEqual({ ambiguo: true });
    });
  });

  it('mensajesError: los textos de hoy («El concepto es obligatorio») y los nuevos del precio', () => {
    expect(mensajesError('concepto', { required: true }, '')).toEqual(['El concepto es obligatorio']);
    expect(mensajesError('precio', { required: true }, '')).toEqual(['El precio es obligatorio']);
    expect(mensajesError('precio', { min: { min: 0, actual: -5 } }, '-5')).toEqual(['El precio debe ser mayor o igual a 0']);
    expect(mensajesError('precio', { formato: true }, 'abc')).toEqual(['Escribe el precio con cifras, por ejemplo 25 o 1.010,50']);
    expect(mensajesError('precio', { ambiguo: true }, '1.234')).toEqual([
      '«1.234» puede ser 1234 € o 1,234 €: escríbelo sin punto (1234) o con coma (1,234)',
    ]);
    expect(mensajesError('precio', null, '')).toEqual([]);
  });

  // ------------------------------------------------------------------ cuerpo enviado

  describe('cuerpoProducto: el MISMO cuerpo que hoy (concepto, precio y vendibleProfesor, en ese orden)', () => {
    it('las tres claves de hoy, ni una más ni una menos, y el precio como número', () => {
      const cuerpo = cuerpoProducto({ concepto: 'Camiseta del club', precio: '1.010,50', vendibleProfesor: true });
      expect(Object.keys(cuerpo)).toEqual(['concepto', 'precio', 'vendibleProfesor']);
      expect(cuerpo).toEqual({ concepto: 'Camiseta del club', precio: 1010.5, vendibleProfesor: true });
      expect(JSON.stringify(cuerpo)).toBe('{"concepto":"Camiseta del club","precio":1010.5,"vendibleProfesor":true}');
    });

    it('el concepto va tal cual se escribió, como hoy (sin recortar)', () => {
      expect(cuerpoProducto({ concepto: ' Cuota ', precio: '25', vendibleProfesor: false }).concepto).toBe(' Cuota ');
    });

    it('vendibleProfesor siempre booleano', () => {
      expect(cuerpoProducto({ concepto: 'A', precio: '1', vendibleProfesor: null as unknown as boolean }).vendibleProfesor).toBeFalse();
    });

    it('un precio que no se puede leer no produce cuerpo', () => {
      expect(() => cuerpoProducto({ concepto: 'A', precio: '1.234', vendibleProfesor: false })).toThrowError(/precio/);
      expect(() => cuerpoProducto({ concepto: 'A', precio: '', vendibleProfesor: false })).toThrowError(/precio/);
    });
  });

  // ------------------------------------------------------------------ listado

  it('TIPOS_PRODUCTO: los siete tipos de la API, en el orden de los grupos, con la etiqueta del filtro de hoy', () => {
    expect(TIPOS_PRODUCTO.map((t) => t.valor)).toEqual([
      'MENSUALIDAD',
      'LICENCIA',
      'RESERVA_PLAZA',
      'COMPETIDOR',
      'EXAMEN',
      'RECOMPENSA',
      'OTRO',
    ]);
    expect(TIPOS_PRODUCTO.map((t) => t.etiqueta)).toEqual([
      'Mensualidad',
      'Licencia',
      'Reserva de plaza',
      'Competidor',
      'Examen',
      'Recompensa',
      'Otro',
    ]);
    expect(TIPOS_PRODUCTO.map((t) => t.grupo)).toEqual([
      'Mensualidades',
      'Licencias',
      'Reservas de plaza',
      'Competidor',
      'Derechos de examen',
      'Recompensas',
      'Otros productos',
    ]);
  });

  describe('ordenar', () => {
    const lista = [
      { id: 3, concepto: 'Camiseta', precio: 12.5 },
      { id: 1, concepto: 'derecho de examen 10', precio: 30 },
      { id: 2, concepto: 'Derecho de examen 2', precio: 25 },
      { id: 4, concepto: 'Álbum', precio: null },
    ];
    const ids = (orden: Parameters<typeof ordenar>[1]) => ordenar(lista, orden).map((p) => p.id);

    it('por defecto, concepto de la A a la Z (como hoy), sin mayúsculas ni acentos y con los números en su orden', () => {
      expect(ORDEN_POR_DEFECTO).toEqual({ campo: 'concepto', sentido: 'asc' });
      expect(ids(ORDEN_POR_DEFECTO)).toEqual([4, 3, 2, 1]);
      expect(ids({ campo: 'concepto', sentido: 'desc' })).toEqual([1, 2, 3, 4]);
    });

    it('por nº y por precio (sin precio, al final en los dos sentidos); no cambia la lista de entrada', () => {
      expect(ids({ campo: 'id', sentido: 'asc' })).toEqual([1, 2, 3, 4]);
      expect(ids({ campo: 'id', sentido: 'desc' })).toEqual([4, 3, 2, 1]);
      expect(ids({ campo: 'precio', sentido: 'asc' })).toEqual([3, 2, 1, 4]);
      expect(ids({ campo: 'precio', sentido: 'desc' })).toEqual([1, 2, 3, 4]);
      expect(lista.map((p) => p.id)).toEqual([3, 1, 2, 4]);
    });

    it('las seis opciones del menú y su etiqueta', () => {
      expect(OPCIONES_ORDEN.map((o) => o.etiqueta)).toEqual([
        'Concepto, de la A a la Z',
        'Concepto, de la Z a la A',
        'Precio, de menor a mayor',
        'Precio, de mayor a menor',
        'Nº, de menor a mayor',
        'Nº, de mayor a menor',
      ]);
      expect(etiquetaOrden({ campo: 'precio', sentido: 'desc' })).toBe('Precio, de mayor a menor');
    });
  });

  it('filtrosActivos: los chips de hoy (concepto recortado, tipo y Mi caja)', () => {
    expect(filtrosActivos({ concepto: '', tipo: '', miCaja: '' })).toEqual([]);
    expect(filtrosActivos({ concepto: '  camiseta ', tipo: 'EXAMEN', miCaja: 'true' })).toEqual([
      { clave: 'concepto', texto: '«camiseta»' },
      { clave: 'tipo', texto: 'Tipo: Examen' },
      { clave: 'miCaja', texto: 'Mi caja: Sí' },
    ]);
    expect(filtrosActivos({ concepto: '   ', tipo: '', miCaja: 'false' })).toEqual([{ clave: 'miCaja', texto: 'Mi caja: No' }]);
  });

  it('miCajaParaApi: como hoy, «» no filtra y «true»/«false» van como booleano', () => {
    expect(miCajaParaApi('')).toBeNull();
    expect(miCajaParaApi('true')).toBeTrue();
    expect(miCajaParaApi('false')).toBeFalse();
  });

  it('textoTotal: plural calculado y miles es-ES', () => {
    expect(textoTotal(0)).toBe('0 productos');
    expect(textoTotal(1)).toBe('1 producto');
    expect(textoTotal(1044)).toBe('1.044 productos');
  });
});
