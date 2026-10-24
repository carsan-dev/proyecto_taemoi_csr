import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { eur } from '../formato/formato-es';
import {
  cuerpoAsignacion,
  diaLocal,
  esMatricula,
  estadoPago,
  etiquetaProducto,
  fechaConDia,
  filtrarCatalogo,
  guardarPagina,
  leerCantidad,
  leerPaginaGuardada,
  leerPrecio,
  paginaValida,
  textoCancelacion,
  textoFallosGuardado,
  tieneCobroActivo,
} from './productos-alumno-vista';


function cargo(extra: Partial<ProductoAlumnoDTO> = {}): ProductoAlumnoDTO {
  return {
    id: 1,
    productoId: 1,
    alumnoId: 7,
    concepto: 'Mensualidad',
    fechaAsignacion: new Date(2026, 9, 1, 18, 30, 15, 123),
    cantidad: 1,
    precio: 30,
    pagado: false,
    fechaPago: null,
    notas: '',
    ...extra,
  };
}

function almacenFalso(): Storage {
  const datos = new Map<string, string>();
  return {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (k: string) => datos.get(k) ?? null,
    key: (i: number) => [...datos.keys()][i] ?? null,
    removeItem: (k: string) => void datos.delete(k),
    setItem: (k: string, v: string) => void datos.set(k, v),
  };
}

describe('productos-alumno-vista (8b-3n)', () => {
  describe('fecha de asignación', () => {
    it('cambia el día local y conserva hora y milisegundos', () => {
      const original = new Date(2026, 9, 1, 18, 30, 15, 123);
      const nueva = fechaConDia(original.getTime(), '2026-10-05')!;
      expect(diaLocal(nueva)).toBe('2026-10-05');
      expect([nueva.getHours(), nueva.getMinutes(), nueva.getSeconds(), nueva.getMilliseconds()]).toEqual([18, 30, 15, 123]);
      expect(original.getDate()).toBe(1);
    });

    it('un cargo antiguo sin fecha toma el mediodía', () => {
      const nueva = fechaConDia(null, '2026-03-02')!;
      expect(diaLocal(nueva)).toBe('2026-03-02');
      expect([nueva.getHours(), nueva.getMinutes(), nueva.getSeconds(), nueva.getMilliseconds()]).toEqual([12, 0, 0, 0]);
    });

    it('rechaza días que no existen o mal escritos', () => {
      expect(fechaConDia(Date.now(), '2026-02-30')).toBeNull();
      expect(fechaConDia(Date.now(), '')).toBeNull();
      expect(fechaConDia(Date.now(), '05/10/2026')).toBeNull();
    });

    it('diaLocal lee milisegundos, Date e ISO; vacío sin fecha', () => {
      const f = new Date(2026, 0, 9, 23, 59);
      expect(diaLocal(f)).toBe('2026-01-09');
      expect(diaLocal(f.getTime())).toBe('2026-01-09');
      expect(diaLocal(null)).toBe('');
      expect(diaLocal('no')).toBe('');
    });
  });

  describe('cantidad y precio', () => {
    it('cantidad: entero mayor o igual a 1', () => {
      expect(leerCantidad('3')).toEqual({ valor: 3, error: null });
      expect(leerCantidad(' 2 ')).toEqual({ valor: 2, error: null });
      expect(leerCantidad('0').error).toBe('min');
      expect(leerCantidad('-1').error).toBe('min');
      expect(leerCantidad('1,5').error).toBe('entero');
      expect(leerCantidad('').error).toBe('vacio');
      expect(leerCantidad('abc').error).toBe('formato');
      expect(leerCantidad('99999999999').error).toBe('formato');
    });

    it('precio: es-ES, mayor o igual a 0 y paso 0,01', () => {
      expect(leerPrecio('25')).toEqual({ valor: 25, error: null });
      expect(leerPrecio('25,50')).toEqual({ valor: 25.5, error: null });
      expect(leerPrecio('1.010,50')).toEqual({ valor: 1010.5, error: null });
      expect(leerPrecio('0')).toEqual({ valor: 0, error: null });
      expect(leerPrecio('0,1')).toEqual({ valor: 0.1, error: null });
      expect(leerPrecio('-1').error).toBe('min');
      expect(leerPrecio('12,345').error).toBe('decimales');
      expect(leerPrecio('1.234').error).toBe('ambiguo');
      expect(leerPrecio('').error).toBe('vacio');
      expect(leerPrecio('x').error).toBe('formato');
    });
  });

  describe('estado de pago', () => {
    it('Cancelado manda; Devuelto en pagados con devolución; Pagado; Pendiente', () => {
      expect(estadoPago(cargo({ canceladoEn: [2026, 10, 2, 9, 0], pagado: false }))).toBe('cancelado');
      expect(estadoPago(cargo({ pagado: true, devuelto: true }))).toBe('devuelto');
      expect(estadoPago(cargo({ pagado: true }))).toBe('pagado');
      expect(estadoPago(cargo())).toBe('pendiente');
    });

    it('decisión 292: un cargo pagado cuenta como con cobro activo', () => {
      expect(tieneCobroActivo(cargo({ pagado: true }))).toBeTrue();
      expect(tieneCobroActivo(cargo({ pagado: false }))).toBeFalse();
    });

    it('texto de la cancelación de hoy', () => {
      expect(
        textoCancelacion(cargo({ canceladoEn: [2026, 10, 2, 9, 0], canceladoPorNombre: 'Ana Ruiz', motivoCancelacion: ' Duplicado ' }))
      ).toBe('Cancelado por Ana Ruiz el 02/10/2026 · «Duplicado»');
      expect(textoCancelacion(cargo({ canceladoEn: [2026, 10, 2, 9, 0] }))).toBe('Cancelado el 02/10/2026');
      expect(textoCancelacion(cargo())).toBe('');
    });
  });

  describe('asignar', () => {
    it('matrícula: la palabra MATRICULA sin tildes', () => {
      expect(esMatricula('Matrícula taekwondo')).toBeTrue();
      expect(esMatricula('MATRICULA KICKBOXING')).toBeTrue();
      expect(esMatricula('Mensualidad')).toBeFalse();
      expect(esMatricula('MATRICULAS')).toBeFalse();
      expect(esMatricula(null)).toBeFalse();
    });

    it('etiqueta «concepto · precio» y búsqueda sin tildes', () => {
      expect(etiquetaProducto({ id: 1, concepto: 'Matrícula taekwondo', precio: 25 })).toBe(`Matrícula taekwondo · ${eur(25)}`);
      expect(etiquetaProducto({ id: 1, concepto: 'Sin precio', precio: null })).toBe('Sin precio');
      const catalogo = [
        { id: 1, concepto: 'Matrícula taekwondo', precio: 25 },
        { id: 2, concepto: 'Mensualidad', precio: 30 },
      ];
      expect(filtrarCatalogo(catalogo, 'matricula').map((p) => p.id)).toEqual([1]);
      expect(filtrarCatalogo(catalogo, '  ').length).toBe(2);
    });

    it('cuerpo B de hoy (decisión 285: el servidor pone precio y concepto)', () => {
      const ahora = new Date(2026, 9, 24);
      expect(cuerpoAsignacion(7, 3, ahora)).toEqual({
        id: 0,
        productoId: 3,
        alumnoId: 7,
        concepto: '',
        fechaAsignacion: ahora,
        cantidad: 1,
        precio: 0,
        pagado: false,
        fechaPago: null,
        notas: '',
      });
    });
  });

  describe('paginación recordada', () => {
    it('guarda y lee con la clave y el formato de hoy', () => {
      const almacen = almacenFalso();
      guardarPagina(7, 3, almacen);
      expect(almacen.getItem('productosAlumnoEstado_7')).toBe(JSON.stringify({ paginaActual: 3, tamanoPagina: 10 }));
      expect(leerPaginaGuardada(7, almacen)).toBe(3);
      expect(leerPaginaGuardada(8, almacen)).toBe(1);
    });

    it('un valor roto o un almacén que falla dan la página 1 sin errores', () => {
      const almacen = almacenFalso();
      almacen.setItem('productosAlumnoEstado_7', '{roto');
      expect(leerPaginaGuardada(7, almacen)).toBe(1);
      almacen.setItem('productosAlumnoEstado_7', JSON.stringify({ paginaActual: -2 }));
      expect(leerPaginaGuardada(7, almacen)).toBe(1);
      const roto = { getItem: () => { throw new Error('bloqueado'); }, setItem: () => { throw new Error('lleno'); } } as unknown as Storage;
      expect(leerPaginaGuardada(7, roto)).toBe(1);
      expect(() => guardarPagina(7, 2, roto)).not.toThrow();
    });

    it('la página se queda dentro de las que hay', () => {
      expect(paginaValida(9, 25)).toBe(3);
      expect(paginaValida(0, 25)).toBe(1);
      expect(paginaValida(2, 0)).toBe(1);
    });
  });

  it('aviso de fallos del guardado (decisión 296)', () => {
    expect(textoFallosGuardado(1)).toBe('1 cambio no se pudo guardar');
    expect(textoFallosGuardado(3)).toBe('3 cambios no se pudieron guardar');
  });
});
