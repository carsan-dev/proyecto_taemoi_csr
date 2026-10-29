import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentInjector, createEnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import type { OpcionesConfirmacion, ResultadoConfirmacion } from '../dialogo/dialogo.component';
import { eur } from '../formato/formato-es';
import { ProductosAlumnoEstado } from './productos-alumno.estado';
import { TEXTOS_PRODUCTOS_ALUMNO as T, diaLocal } from './productos-alumno-vista';

const API = environment.apiUrl;
const URL_CARGOS = `${API}/productos-alumno/alumno/7`;
const FECHA = new Date(2026, 9, 1, 18, 30, 15, 123).getTime();

function cargo(id: number, extra: Partial<ProductoAlumnoDTO> = {}): ProductoAlumnoDTO {
  return {
    id,
    productoId: 1,
    alumnoId: 7,
    concepto: `Cargo ${id}`,
    fechaAsignacion: FECHA as unknown as Date,
    cantidad: 1,
    precio: 30,
    pagado: false,
    fechaPago: null,
    notas: '',
    ...extra,
  };
}

const ALUMNO = {
  id: 7,
  nombre: 'Lucía',
  apellidos: 'Ortega',
  direccion: 'C/ Mayor 1',
  activo: true,
  nif: '12345678Z',
  deportes: [{ deporte: 'TAEKWONDO', activo: true }],
};
const ANONIMIZADO = {
  id: 7,
  nombre: 'Alumno',
  apellidos: 'eliminado',
  direccion: 'Sin datos',
  activo: false,
  nif: null,
  email: null,
  telefono: null,
  deportes: [{ deporte: 'TAEKWONDO', activo: false }],
  alumnoEliminado: true,
};
const CATALOGO = [
  { id: 1, concepto: 'Mensualidad', precio: 30 },
  { id: 2, concepto: 'Matrícula taekwondo', precio: 25 },
];

type Respuesta = Partial<ResultadoConfirmacion> & { confirmado: boolean };

describe('ProductosAlumnoEstado (8b-3n)', () => {
  let http: HttpTestingController;
  let estado: ProductosAlumnoEstado;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  let opciones: OpcionesConfirmacion[];
  let respuestas: Respuesta[];
  let admin: boolean;
  let gestion: boolean;

  /** El diálogo de prueba: devuelve la siguiente respuesta; si hay `ejecutar` y se confirma, lo lanza como el real. */
  function dialogoFalso(op: OpcionesConfirmacion): Promise<ResultadoConfirmacion> {
    opciones.push(op);
    const r = respuestas.shift() ?? { confirmado: false };
    if (!r.confirmado || !op.ejecutar) {
      return Promise.resolve({ valor: null, ...r } as ResultadoConfirmacion);
    }
    return new Promise((resolver) => {
      op.ejecutar!(r.valor ?? null).subscribe({
        next: (respuesta) => resolver({ confirmado: true, valor: r.valor ?? null, respuesta }),
        error: (error) => resolver({ confirmado: true, valor: r.valor ?? null, error }),
      });
    });
  }

  function crear(): void {
    estado = TestBed.inject(ProductosAlumnoEstado);
  }

  beforeEach(() => {
    sessionStorage.clear();
    admin = true;
    gestion = true;
    opciones = [];
    respuestas = [];
    avisos = jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']);
    confirmar = jasmine.createSpy('confirmar').and.callFake(dialogoFalso);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ProductosAlumnoEstado,
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => admin, tieneAccesoAdmin: () => gestion, obtenerRoles: () => of([]) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function abrir(cargos: ProductoAlumnoDTO[] | null, alumno: unknown = ALUMNO, catalogo: unknown = CATALOGO): void {
    crear();
    estado.iniciar(7);
    responder(cargos, alumno, catalogo);
  }

  function responder(cargos: ProductoAlumnoDTO[] | null, alumno: unknown, catalogo: unknown): void {
    const peticiones = [http.expectOne(URL_CARGOS), http.expectOne(`${API}/productos/todos`), http.expectOne(`${API}/alumnos/7`)];
    for (const p of peticiones) {
      expect(p.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      expect(p.request.withCredentials).toBeTrue();
    }
    flushO(peticiones[0], cargos);
    flushO(peticiones[1], catalogo);
    flushO(peticiones[2], alumno);
  }

  /** `null` = 500; `'404'` = no existe. */
  function flushO(p: TestRequest, cuerpo: unknown): void {
    if (cuerpo === null) {
      p.flush({ mensaje: 'fallo' }, { status: 500, statusText: 'Error' });
    } else if (cuerpo === '404') {
      p.flush({ mensaje: 'Alumno no encontrado con ID: 7' }, { status: 404, statusText: 'Not Found' });
    } else {
      p.flush(cuerpo as object);
    }
  }

  /** Todas las peticiones abiertas (para comprobar que van de una en una). Las saca de la cola de abiertas. */
  function abiertas(): TestRequest[] {
    return http.match(() => true);
  }

  /** `local = false`: la petición de `EndpointsService` que comparte la tesorería (sin `ERROR_HANDLED_LOCALLY`). */
  function una(metodo: string, url: string, local = true): TestRequest {
    const todas = abiertas();
    expect(todas.length).withContext(`una sola petición abierta, esperaba ${metodo} ${url}`).toBe(1);
    expect(todas[0].request.method).toBe(metodo);
    expect(todas[0].request.url).toBe(url);
    expect(todas[0].request.context.get(ERROR_HANDLED_LOCALLY)).toBe(local);
    return todas[0];
  }

  const fila = (id: number) => estado.filas().find((f) => f.id === id)!;

  // ------------------------------------------------------------------ carga

  describe('carga', () => {
    it('lee cargos, catálogo y alumno a la vez y enseña lo más reciente primero', () => {
      crear();
      estado.iniciar(7);
      expect(estado.estado()).toBe('cargando');
      responder([cargo(1), cargo(2), cargo(3)], ALUMNO, CATALOGO);
      expect(estado.estado()).toBe('listo');
      expect(estado.filas().map((f) => f.id)).toEqual([3, 2, 1]);
      expect(estado.nombreAlumno()).toBe('Lucía Ortega');
      expect(estado.deportes()).toEqual(['TAEKWONDO']);
      expect(estado.rutaFicha()).toEqual(['/alumnosEditar', 7]);
      expect(estado.parametrosFicha).toEqual({ pestana: 'pagos' });
    });

    it('vacío: listo y sin cargos', () => {
      abrir([]);
      expect(estado.estado()).toBe('listo');
      expect(estado.totalCargos()).toBe(0);
      expect(estado.desde()).toBe(0);
    });

    it('cada fallo con su texto de hoy, y el del alumno manda', () => {
      crear();
      estado.iniciar(7);
      responder(null, ALUMNO, CATALOGO);
      expect(estado.estado()).toBe('error');
      expect(estado.errorCarga()).toBe(T.errorCargos);

      estado.cargar();
      responder([], ALUMNO, null);
      expect(estado.errorCarga()).toBe(T.errorCatalogo);

      estado.cargar();
      responder(null, null, null);
      expect(estado.errorCarga()).toBe(T.errorAlumno);
    });

    it('«Reintentar» vuelve a pedir las tres lecturas', () => {
      crear();
      estado.iniciar(7);
      responder(null, ALUMNO, CATALOGO);
      estado.cargar();
      expect(estado.estado()).toBe('cargando');
      responder([cargo(1)], ALUMNO, CATALOGO);
      expect(estado.estado()).toBe('listo');
    });

    it('un id que no vale no pide nada y da el error del alumno', () => {
      crear();
      estado.iniciar(0);
      expect(estado.estado()).toBe('error');
      expect(estado.errorCarga()).toBe(T.errorAlumno);
    });
  });

  describe('alumno anonimizado (decisiones 287 y 293)', () => {
    function vista() {
      return {
        estado: estado.estado(),
        error: estado.errorCarga(),
        nombre: estado.nombreAlumno(),
        alumno: estado.alumno(),
        cargos: estado.totalCargos(),
        catalogo: estado.catalogo().length,
        deportes: estado.deportes(),
      };
    }

    it('se ve exactamente igual que un alumno que no existe', () => {
      abrir([cargo(1, { pagado: true }), cargo(2, { canceladoEn: [2026, 10, 24, 10, 0], motivoCancelacion: 'Alumno eliminado' })], ANONIMIZADO);
      const anonimizado = vista();
      abrir([], '404');
      const inexistente = vista();
      expect(anonimizado).toEqual(inexistente);
      expect(anonimizado.estado).toBe('error');
      expect(anonimizado.error).toBe(T.errorAlumno);
      expect(T.errorAlumno).toBe('No se han podido cargar los productos del alumno.');
    });

    it('decisión 299: lo dice la API (`alumnoEliminado`), no el nombre', () => {
      abrir([cargo(1)], { ...ANONIMIZADO, alumnoEliminado: false });
      expect(estado.estado()).toBe('listo');
      expect(estado.nombreAlumno()).toBe('Alumno eliminado');

      abrir([cargo(1)], { ...ALUMNO, alumnoEliminado: true });
      expect(estado.estado()).toBe('error');
      expect(estado.errorCarga()).toBe(T.errorAlumno);
      expect(estado.alumno()).toBeNull();
      expect(estado.filas()).toEqual([]);

      // Sin el indicador (una API anterior), se carga.
      abrir([cargo(1)], ALUMNO);
      expect(estado.estado()).toBe('listo');
    });

    it('igual con todo fallando (el responsable de sede ve 403 en un id que no es suyo)', () => {
      abrir([cargo(1)], ANONIMIZADO);
      const anonimizado = vista();
      abrir(null, null, CATALOGO);
      expect(vista()).toEqual(anonimizado);
    });

    it('no se puede modificar ni reactivar nada', async () => {
      abrir([cargo(1, { canceladoEn: [2026, 10, 24, 10, 0] }), cargo(2)], ANONIMIZADO);
      expect(estado.filas()).toEqual([]);
      expect(estado.cambiarNotas(2, 'x')).toBeFalse();
      expect(estado.marcarBorrar(2)).toBeFalse();
      estado.elegirProducto(1);
      expect(estado.anadirALista()).toBeFalse();
      estado.guardar();
      await estado.reactivar(1);
      await estado.cancelar(2);
      await estado.cobrar(2);
      expect(confirmar).not.toHaveBeenCalled();
      expect(abiertas().length).toBe(0);
    });
  });

  // ------------------------------------------------------------------ campos

  describe('campos y decisión 292', () => {
    it('decisión 297: cantidad nula del servidor cuenta como una unidad', () => {
      abrir([cargo(1, { cantidad: null as unknown as number, precio: 12.5 })]);
      expect(fila(1).total).toBe(12.5);
      estado.cambiarNotas(1, 'Talla M');
      expect(fila(1).total).toBe(12.5);
      estado.cambiarPrecio(1, '15');
      expect(fila(1).total).toBe(15);
    });

    it('decisión 297: precio nulo del servidor cuenta como cero', () => {
      abrir([cargo(1, { precio: null as unknown as number, cantidad: 3 })]);
      expect(fila(1).total).toBe(0);
      estado.cambiarCantidad(1, '2');
      expect(fila(1).total).toBe(0);
      estado.cambiarPrecio(1, '-1');
      expect(fila(1).total).toBeNull();
      expect(estado.puedeGuardar()).toBeFalse();
    });

    it('un cargo con cantidad guardada nula, editado a una cantidad no válida, conserva la edición y su total es null', () => {
      abrir([cargo(1, { cantidad: null as unknown as number, precio: 12.5 })]);
      estado.cambiarCantidad(1, '');
      expect(estado.ediciones().get(1)).toEqual({ cantidad: '' });
      expect(fila(1).modificada).toBeTrue();
      expect(fila(1).errores.cantidad).not.toBeNull();
      expect(fila(1).total).toBeNull();
      expect(estado.hayErrores()).toBeTrue();
      estado.cambiarCantidad(1, '0');
      expect(estado.ediciones().get(1)).toEqual({ cantidad: '0' });
      expect(fila(1).modificada).toBeTrue();
      expect(fila(1).errores.cantidad).toBe('min');
      expect(fila(1).total).toBeNull();
      expect(estado.puedeGuardar()).toBeFalse();
    });

    it('editar marca la fila y volver al valor guardado la desmarca', () => {
      abrir([cargo(1)]);
      expect(estado.cambiarPrecio(1, '35,50')).toBeTrue();
      expect(fila(1).modificada).toBeTrue();
      expect(fila(1).total).toBe(35.5);
      expect(estado.pendientes()).toBe(1);
      estado.cambiarPrecio(1, '30,00');
      expect(fila(1).modificada).toBeFalse();
      expect(estado.pendientes()).toBe(0);
      estado.cambiarCantidad(1, '2');
      estado.cambiarNotas(1, 'Talla M');
      estado.cambiarFecha(1, '2026-10-05');
      expect(estado.ediciones().get(1)).toEqual({ cantidad: '2', notas: 'Talla M', fecha: '2026-10-05' });
      expect(fila(1).total).toBe(60);
      estado.deshacerFila(1);
      expect(estado.hayCambios()).toBeFalse();
    });

    it('cantidad ≥ 1 entera y precio ≥ 0 con dos decimales: con errores no se guarda', () => {
      abrir([cargo(1)]);
      estado.cambiarCantidad(1, '0');
      expect(fila(1).errores.cantidad).toBe('min');
      expect(fila(1).total).toBeNull();
      expect(estado.hayErrores()).toBeTrue();
      expect(estado.puedeGuardar()).toBeFalse();
      estado.guardar();
      expect(abiertas().length).toBe(0);
      estado.cambiarCantidad(1, '1');
      estado.cambiarPrecio(1, '-2');
      expect(fila(1).errores.precio).toBe('min');
      estado.cambiarPrecio(1, '2,345');
      expect(fila(1).errores.precio).toBe('decimales');
      estado.cambiarPrecio(1, '2,34');
      expect(estado.hayErrores()).toBeFalse();
    });

    it('un día que no existe no cambia nada', () => {
      abrir([cargo(1)]);
      expect(estado.cambiarFecha(1, '2026-02-30')).toBeFalse();
      expect(estado.cambiarFecha(1, '')).toBeFalse();
      expect(estado.hayCambios()).toBeFalse();
    });

    it('292: con cobro activo, precio y cantidad deshabilitados con su nota; fecha y notas, sí', () => {
      abrir([cargo(1, { pagado: true, fechaPago: FECHA as unknown as Date, cantidad: 2 })]);
      expect(fila(1).precioCantidadEditables).toBeFalse();
      expect(fila(1).notaPrecioCantidad).toBe('Revierte el cobro para cambiar precio o cantidad');
      expect(estado.cambiarPrecio(1, '10')).toBeFalse();
      expect(estado.cambiarCantidad(1, '5')).toBeFalse();
      expect(estado.hayCambios()).toBeFalse();
      expect(fila(1).editable).toBeTrue();
      expect(estado.cambiarNotas(1, 'Pagado en mano')).toBeTrue();
      estado.guardar();
      const put = una('PUT', `${API}/productos-alumno/1`);
      // Decisión 298: solo lo editado; ni precio, ni cantidad, ni el pago.
      expect(put.request.body).toEqual({ notas: 'Pagado en mano' });
      put.flush({ ...cargo(1, { pagado: true, cantidad: 2 }), notas: 'Pagado en mano' });
      http.expectOne(URL_CARGOS).flush([{ ...cargo(1, { pagado: true, cantidad: 2 }), notas: 'Pagado en mano' }]);
    });

    it('cancelados y marcados para borrar no se editan', () => {
      abrir([cargo(1, { canceladoEn: [2026, 10, 2, 9, 0] }), cargo(2)]);
      expect(fila(1).editable).toBeFalse();
      expect(fila(1).puedeEditarNotas).toBeFalse();
      expect(estado.cambiarNotas(1, 'x')).toBeFalse();
      expect(estado.cambiarFecha(1, '2026-10-05')).toBeFalse();
      estado.marcarBorrar(2);
      expect(fila(2).editable).toBeFalse();
      expect(estado.cambiarPrecio(2, '1')).toBeFalse();
    });
  });

  // ------------------------------------------------------------------ eliminar

  describe('eliminar (solo propietario)', () => {
    it('el gestor no puede marcar para borrar', () => {
      admin = false;
      abrir([cargo(1)]);
      expect(fila(1).puedeEliminar).toBeFalse();
      expect(estado.marcarBorrar(1)).toBeFalse();
      expect(estado.hayCambios()).toBeFalse();
    });

    it('el propietario marca, deshace y recupera los cambios de la fila; pagados y cancelados no', () => {
      abrir([cargo(1), cargo(2, { pagado: true }), cargo(3, { canceladoEn: [2026, 10, 2, 9, 0] })]);
      expect(fila(2).puedeEliminar).toBeFalse();
      expect(fila(3).puedeEliminar).toBeFalse();
      estado.cambiarNotas(1, 'nota');
      expect(estado.marcarBorrar(1)).toBeTrue();
      expect(fila(1).marcadaBorrar).toBeTrue();
      expect(fila(1).modificada).toBeFalse();
      expect(estado.pendientes()).toBe(1);
      // Editada y marcada: solo el «Deshacer» del borrado; el de los cambios no hace nada mientras esté marcada.
      expect(fila(1).puedeDeshacerBorrar).toBeTrue();
      expect(fila(1).puedeDeshacerCambios).toBeFalse();
      estado.deshacerFila(1);
      expect(estado.ediciones().get(1)).toEqual({ notas: 'nota' });
      estado.deshacerBorrar(1);
      expect(fila(1).marcadaBorrar).toBeFalse();
      expect(fila(1).modificada).toBeTrue();
      expect(fila(1).notas).toBe('nota');
      expect(fila(1).puedeDeshacerBorrar).toBeFalse();
      expect(fila(1).puedeDeshacerCambios).toBeTrue();
    });
  });

  describe('«Deshacer» de una fila editada', () => {
    it('deshace solo esa fila: vuelve a lo guardado, conserva los demás cambios y recalcula total y contador', () => {
      abrir([cargo(1), cargo(2, { precio: 12.5, cantidad: 2 }), cargo(3)]);
      expect(fila(2).puedeDeshacerCambios).toBeFalse();
      estado.cambiarPrecio(1, '40');
      estado.cambiarCantidad(2, '3');
      estado.cambiarPrecio(2, '10');
      estado.cambiarNotas(3, 'Talla M');
      estado.elegirProducto(1);
      estado.anadirALista();
      expect(fila(2).total).toBe(30);
      expect(estado.pendientes()).toBe(4);
      expect(fila(2).puedeDeshacerCambios).toBeTrue();
      estado.deshacerFila(2);
      expect(fila(2).modificada).toBeFalse();
      expect(fila(2).puedeDeshacerCambios).toBeFalse();
      expect(fila(2).cantidad).toBe('2');
      expect(fila(2).precio).toBe('12,50');
      expect(fila(2).total).toBe(25);
      expect(estado.ediciones().has(2)).toBeFalse();
      expect(estado.ediciones().get(1)).toEqual({ precio: '40' });
      expect(fila(1).total).toBe(40);
      expect(estado.ediciones().get(3)).toEqual({ notas: 'Talla M' });
      expect(estado.nuevas().length).toBe(1);
      expect(estado.pendientes()).toBe(3);
      expect(estado.hayCambios()).toBeTrue();
    });

    it('una fila con un campo no válido se deshace y deja de bloquear el guardado', () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarCantidad(1, '0');
      estado.cambiarNotas(2, 'nota');
      expect(estado.puedeGuardar()).toBeFalse();
      estado.deshacerFila(1);
      expect(fila(1).total).toBe(30);
      expect(estado.hayErrores()).toBeFalse();
      expect(estado.puedeGuardar()).toBeTrue();
    });

    it('deshacer el último cambio deja la pantalla sin pendientes, como «Descartar»', () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarPrecio(1, '40');
      estado.cambiarFecha(2, '2026-10-05');
      estado.deshacerFila(1);
      expect(estado.pendientes()).toBe(1);
      estado.deshacerFila(2);
      expect(estado.pendientes()).toBe(0);
      expect(estado.hayCambios()).toBeFalse();
      expect(estado.puedeGuardar()).toBeFalse();
      expect(estado.ediciones().size).toBe(0);
    });
  });

  // ------------------------------------------------------------------ asignar

  describe('asignar y la matrícula', () => {
    it('sin producto: «Seleccione un producto»', () => {
      abrir([]);
      expect(estado.anadirALista()).toBeFalse();
      expect(estado.errorAsignacion()).toBe('producto');
    });

    it('la matrícula pide un deporte que el alumno practica', () => {
      abrir([]);
      estado.elegirProducto(2);
      expect(estado.matriculaElegida()).toBeTrue();
      expect(estado.puedeAnadir()).toBeFalse();
      expect(estado.anadirALista()).toBeFalse();
      expect(estado.errorAsignacion()).toBe('deporte');
      estado.elegirDeporte('KICKBOXING');
      expect(estado.anadirALista()).toBeFalse();
      estado.elegirDeporte('TAEKWONDO');
      expect(estado.anadirALista()).toBeTrue();
      expect(estado.nuevas()[0].deporte).toBe('TAEKWONDO');
      expect(estado.productoElegido()).toBeNull();
    });

    it('lo que no es matrícula no lleva deporte y se apila arriba', () => {
      abrir([]);
      estado.elegirProducto(2);
      estado.elegirDeporte('TAEKWONDO');
      estado.elegirProducto(1);
      expect(estado.deporteElegido()).toBeNull();
      estado.anadirALista();
      estado.elegirProducto(1);
      estado.anadirALista();
      expect(estado.nuevas().map((n) => n.deporte)).toEqual([null, null]);
      expect(estado.nuevas()[0].clave).toBeGreaterThan(estado.nuevas()[1].clave);
      expect(estado.pendientes()).toBe(2);
      estado.quitarNueva(estado.nuevas()[0].clave);
      expect(estado.pendientes()).toBe(1);
    });

    it('sin deportes, la matrícula avisa', () => {
      abrir([], { ...ALUMNO, deportes: [] });
      estado.elegirProducto(2);
      expect(estado.sinDeportes()).toBeTrue();
      expect(estado.anadirALista()).toBeFalse();
    });

    it('la búsqueda filtra las opciones', () => {
      abrir([]);
      estado.buscarProducto('matri');
      expect(estado.opcionesProducto().map((p) => p.id)).toEqual([2]);
    });
  });

  // ------------------------------------------------------------------ guardar

  describe('guardar en lote (decisión 296)', () => {
    function preparar(): void {
      // En pantalla: 4, 3, 2, 1.
      abrir([cargo(1), cargo(2), cargo(3), cargo(4)]);
      estado.cambiarPrecio(4, '40');
      estado.cambiarFecha(3, '2026-10-05');
      estado.marcarBorrar(2);
      estado.marcarBorrar(1);
      estado.elegirProducto(1);
      estado.anadirALista();
      estado.elegirProducto(2);
      estado.elegirDeporte('TAEKWONDO');
      estado.anadirALista();
      expect(estado.pendientes()).toBe(6);
    }

    it('en secuencia PUT → DELETE → POST, de uno en uno, sigue tras los fallos y conserva lo fallido', () => {
      preparar();
      estado.guardar();
      expect(estado.guardando()).toBeTrue();
      expect(estado.cambiarNotas(1, 'x')).toBeFalse();

      const put4 = una('PUT', `${API}/productos-alumno/4`);
      expect(put4.request.body).toEqual({ precio: 40 });
      put4.flush({ mensaje: 'Este cargo tiene un cobro activo' }, { status: 409, statusText: 'Conflict' });

      const put3 = una('PUT', `${API}/productos-alumno/3`);
      expect(Object.keys(put3.request.body)).toEqual(['fechaAsignacion']);
      const fecha = put3.request.body.fechaAsignacion as Date;
      expect(diaLocal(fecha)).toBe('2026-10-05');
      expect([fecha.getHours(), fecha.getMinutes(), fecha.getSeconds(), fecha.getMilliseconds()]).toEqual([18, 30, 15, 123]);
      put3.flush({ ...cargo(3), fechaAsignacion: fecha.getTime() });

      una('DELETE', `${API}/productos-alumno/2`).flush(null);
      una('DELETE', `${API}/productos-alumno/1`).flush({ mensaje: 'Tiene historial de cobros' }, { status: 409, statusText: 'Conflict' });

      const post1 = una('POST', `${API}/productos-alumno/alumno/7/producto/1`);
      expect(post1.request.body).toEqual({
        id: 0, productoId: 1, alumnoId: 7, concepto: '', fechaAsignacion: jasmine.any(Date),
        cantidad: 1, precio: 0, pagado: false, fechaPago: null, notas: '',
      });
      expect(post1.request.body.deporte).toBeUndefined();
      post1.flush('', { status: 500, statusText: 'Error' });

      const post2 = una('POST', `${API}/productos-alumno/alumno/7/producto/2/deporte/TAEKWONDO`);
      expect(post2.request.body.precio).toBe(0);
      post2.flush(cargo(9, { concepto: 'Matrícula taekwondo', precio: 25, productoId: 2 }));

      // Lo que salió bien, aplicado; lo que no, con su cambio, su punto y el error del servidor.
      expect(estado.guardando()).toBeFalse();
      expect(avisos.error).toHaveBeenCalledWith('3 cambios no se pudieron guardar');
      expect(avisos.exito).not.toHaveBeenCalled();
      expect(fila(4).modificada).toBeTrue();
      expect(fila(4).precio).toBe('40');
      expect(fila(4).errorServidor).toBe('Este cargo tiene un cobro activo');
      expect(fila(3).modificada).toBeFalse();
      expect(fila(3).fecha).toBe('2026-10-05');
      expect(estado.filas().some((f) => f.id === 2)).toBeFalse();
      expect(fila(1).marcadaBorrar).toBeTrue();
      expect(fila(1).errorServidor).toBe('Tiene historial de cobros');
      expect(estado.nuevas().length).toBe(1);
      expect(estado.nuevas()[0].producto.id).toBe(1);
      expect(estado.nuevas()[0].error).toBe('No se ha podido guardar este cambio.');
      expect(fila(9).concepto).toBe('Matrícula taekwondo');
      expect(estado.pendientes()).toBe(3);

      // La relectura no pierde lo pendiente de lo que falló.
      const relectura = una('GET', URL_CARGOS);
      relectura.flush([cargo(1), { ...cargo(3), fechaAsignacion: fecha.getTime() }, cargo(4), cargo(9, { concepto: 'Matrícula taekwondo', precio: 25 })]);
      expect(estado.filas().map((f) => f.id)).toEqual([9, 4, 3, 1]);
      expect(fila(4).modificada).toBeTrue();
      expect(fila(4).errorServidor).toBe('Este cargo tiene un cobro activo');
      expect(fila(1).marcadaBorrar).toBeTrue();
      expect(estado.nuevas()[0].error).toBe(T.errorGuardarCambio);
      expect(estado.pendientes()).toBe(3);

      // Reintentar = volver a guardar: solo lo que falló.
      estado.guardar();
      expect(fila(4).errorServidor).toBeNull();
      una('PUT', `${API}/productos-alumno/4`).flush({ ...cargo(4), precio: 40 });
      una('DELETE', `${API}/productos-alumno/1`).flush(null);
      una('POST', `${API}/productos-alumno/alumno/7/producto/1`).flush(cargo(10));
      expect(avisos.exito).toHaveBeenCalledWith('Cambios aplicados correctamente');
      una('GET', URL_CARGOS).flush([{ ...cargo(3), fechaAsignacion: fecha.getTime() }, { ...cargo(4), precio: 40 }, cargo(9), cargo(10)]);
      expect(estado.hayCambios()).toBeFalse();
      expect(estado.filas().map((f) => f.id)).toEqual([10, 9, 4, 3]);
    });

    it('decisión 298: el PUT lleva solo los campos editados, nunca el pago ni el resto del cargo', () => {
      abrir([cargo(1, { notas: 'Antes', metodoPago: 'EFECTIVO' }), cargo(2)]);
      estado.cambiarFecha(1, '2026-10-05');
      estado.cambiarCantidad(1, '3');
      estado.cambiarPrecio(1, '12,50');
      estado.cambiarNotas(1, 'Talla M');
      estado.cambiarNotas(2, 'Solo notas');
      estado.guardar();

      // En pantalla, 2 y 1 (lo más reciente primero). Una fila con solo notas: `{notas}`. Sin `pagado`, no puede
      // deshacer un cobro que otra persona hizo mientras tanto.
      const put2 = una('PUT', `${API}/productos-alumno/2`);
      expect(put2.request.body).toEqual({ notas: 'Solo notas' });
      const cuerpo2 = put2.request.body;
      put2.flush({ ...cargo(2, { pagado: true, fechaPago: FECHA as unknown as Date }), notas: 'Solo notas' });
      const put1 = una('PUT', `${API}/productos-alumno/1`);
      expect(Object.keys(put1.request.body).sort()).toEqual(['cantidad', 'fechaAsignacion', 'notas', 'precio']);
      expect(put1.request.body.cantidad).toBe(3);
      expect(put1.request.body.precio).toBe(12.5);
      expect(put1.request.body.notas).toBe('Talla M');
      const fecha = put1.request.body.fechaAsignacion as Date;
      expect(diaLocal(fecha)).toBe('2026-10-05');
      expect([fecha.getHours(), fecha.getMinutes(), fecha.getSeconds(), fecha.getMilliseconds()]).toEqual([18, 30, 15, 123]);
      put1.flush({ ...cargo(1), cantidad: 3, precio: 12.5, notas: 'Talla M', fechaAsignacion: fecha.getTime() });

      for (const clave of ['pagado', 'fechaPago', 'metodoPago', 'motivoCambio', 'id', 'concepto', 'productoId', 'alumnoId']) {
        expect(clave in put1.request.body).withContext(clave).toBeFalse();
        expect(clave in cuerpo2).withContext(clave).toBeFalse();
      }
      una('GET', URL_CARGOS).flush([cargo(1), cargo(2, { pagado: true })]);
    });

    it('un cargo antiguo sin fecha toma el mediodía del día elegido (solo la fecha en el PUT)', () => {
      abrir([cargo(1, { fechaAsignacion: null as unknown as Date })]);
      expect(estado.cambiarFecha(1, '2026-10-05')).toBeTrue();
      estado.guardar();
      const put = una('PUT', `${API}/productos-alumno/1`);
      expect(Object.keys(put.request.body)).toEqual(['fechaAsignacion']);
      const fecha = put.request.body.fechaAsignacion as Date;
      expect(diaLocal(fecha)).toBe('2026-10-05');
      expect([fecha.getHours(), fecha.getMinutes(), fecha.getSeconds(), fecha.getMilliseconds()]).toEqual([12, 0, 0, 0]);
      put.flush({ ...cargo(1), fechaAsignacion: fecha.getTime() });
      una('GET', URL_CARGOS).flush([{ ...cargo(1), fechaAsignacion: fecha.getTime() }]);
    });

    it('error de una fila: el mensaje del servidor; sin mensaje, «No se ha podido guardar este cambio.»', () => {
      abrir([cargo(1), cargo(2), cargo(3)]);
      estado.cambiarNotas(3, 'x');
      estado.cambiarNotas(2, 'y');
      estado.marcarBorrar(1);
      estado.guardar();
      una('PUT', `${API}/productos-alumno/3`).flush('', { status: 500, statusText: 'Error' });
      una('PUT', `${API}/productos-alumno/2`).flush({ mensaje: 'El cargo está cancelado' }, { status: 409, statusText: 'Conflict' });
      una('DELETE', `${API}/productos-alumno/1`).flush(null, { status: 500, statusText: 'Error' });
      expect(fila(3).errorServidor).toBe('No se ha podido guardar este cambio.');
      expect(fila(2).errorServidor).toBe('El cargo está cancelado');
      expect(fila(1).errorServidor).toBe('No se ha podido guardar este cambio.');
      una('GET', URL_CARGOS).flush([cargo(1), cargo(2), cargo(3)]);
    });

    it('sin cambios o con una acción en curso no envía nada', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.guardar();
      expect(abiertas().length).toBe(0);
      estado.cambiarNotas(1, 'x');
      respuestas.push({ confirmado: true, valores: { motivo: 'Duplicado' } });
      await estado.cancelar(2);
      const cancelar = una('POST', `${API}/productos-alumno/2/cancelar`);
      expect(estado.puedeGuardar()).toBeFalse();
      estado.guardar();
      // `abiertas()` saca las peticiones que encuentra: la del cancelar ya salió con `una`; ninguna nueva.
      expect(abiertas().length).toBe(0);
      cancelar.flush(cargo(2, { canceladoEn: [2026, 10, 24, 10, 0] }));
      expect(estado.puedeGuardar()).toBeTrue();
    });

    it('«Descartar» quita todo lo pendiente sin llamar al servidor', () => {
      preparar();
      estado.descartar();
      expect(estado.hayCambios()).toBeFalse();
      expect(estado.nuevas()).toEqual([]);
      expect(fila(1).marcadaBorrar).toBeFalse();
      expect(abiertas().length).toBe(0);
    });
  });

  // ------------------------------------------------------------------ acciones inmediatas

  describe('cancelar y reactivar al momento, conservando lo pendiente', () => {
    it('cancelar: motivo recortado y obligatorio (≤ 500); los demás cambios siguen', async () => {
      abrir([cargo(1), cargo(2), cargo(3)]);
      estado.cambiarPrecio(1, '12');
      estado.marcarBorrar(3);
      estado.elegirProducto(1);
      estado.anadirALista();
      estado.cambiarNotas(2, 'se pierde al cancelar');
      respuestas.push({ confirmado: true, valores: { motivo: '  Alta duplicada  ' } });
      await estado.cancelar(2);
      const op = opciones[0];
      expect(op.titulo).toBe('Cancelar cargo');
      expect(op.campos?.[0].atributos?.['maxlength']).toBe('500');
      expect(op.comprobar!({ motivo: '   ' })).toEqual({ campo: 'motivo', mensaje: 'Indica el motivo de la cancelación' });
      expect(op.comprobar!({ motivo: 'x'.repeat(501) })).toEqual({ campo: 'motivo', mensaje: 'El motivo admite como mucho 500 caracteres' });
      expect(op.comprobar!({ motivo: ' ok ' })).toBeNull();
      expect(fila(2).ocupada).toBeTrue();
      const post = una('POST', `${API}/productos-alumno/2/cancelar`);
      expect(post.request.body).toEqual({ motivo: 'Alta duplicada' });
      post.flush(cargo(2, { canceladoEn: [2026, 10, 24, 10, 0], motivoCancelacion: 'Alta duplicada' }));
      expect(fila(2).estado).toBe('cancelado');
      expect(fila(2).modificada).toBeFalse();
      expect(avisos.exito).toHaveBeenCalledWith('Cargo cancelado');
      expect(fila(1).precio).toBe('12');
      expect(fila(3).marcadaBorrar).toBeTrue();
      expect(estado.nuevas().length).toBe(1);
      expect(estado.pendientes()).toBe(3);
    });

    it('cancelar el diálogo no envía nada; un error se avisa y no toca la fila', async () => {
      abrir([cargo(1)]);
      respuestas.push({ confirmado: false });
      await estado.cancelar(1);
      expect(abiertas().length).toBe(0);
      respuestas.push({ confirmado: true, valores: { motivo: 'x' } });
      await estado.cancelar(1);
      una('POST', `${API}/productos-alumno/1/cancelar`).flush({ mensaje: 'Tiene una convocatoria' }, { status: 409, statusText: 'Conflict' });
      expect(avisos.error).toHaveBeenCalledWith('Tiene una convocatoria');
      expect(fila(1).estado).toBe('pendiente');
      expect(fila(1).ocupada).toBeFalse();
    });

    it('pagados y cancelados no se cancelan', async () => {
      abrir([cargo(1, { pagado: true }), cargo(2, { canceladoEn: [2026, 10, 2, 9, 0] })]);
      expect(fila(1).puedeCancelar).toBeFalse();
      expect(fila(2).puedeCancelar).toBeFalse();
      await estado.cancelar(1);
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('reactivar: confirmación, POST sin cuerpo y los demás cambios siguen', async () => {
      abrir([cargo(1, { canceladoEn: [2026, 10, 2, 9, 0] }), cargo(2)]);
      estado.cambiarNotas(2, 'pendiente');
      expect(fila(1).puedeReactivar).toBeTrue();
      respuestas.push({ confirmado: true });
      const promesa = estado.reactivar(1);
      expect(opciones[0].titulo).toBe('Reactivar cargo');
      expect(opciones[0].mensaje).toBe('«Cargo 1» vuelve a estar pendiente de pago.');
      const post = una('POST', `${API}/productos-alumno/1/reactivar`);
      expect(post.request.body).toBeNull();
      post.flush(cargo(1));
      await promesa;
      expect(fila(1).estado).toBe('pendiente');
      expect(avisos.exito).toHaveBeenCalledWith('Cargo reactivado');
      expect(fila(2).notas).toBe('pendiente');
      expect(estado.pendientes()).toBe(1);
    });

    it('reactivar cancelado en el diálogo no envía nada', async () => {
      abrir([cargo(1, { canceladoEn: [2026, 10, 2, 9, 0] })]);
      respuestas.push({ confirmado: false });
      await estado.reactivar(1);
      expect(abiertas().length).toBe(0);
    });
  });

  describe('cobrar (decisiones 294 y 295)', () => {
    it('294: el importe es el precio del cargo aunque la cantidad sea 3; misma llamada que la tesorería', async () => {
      abrir([cargo(1, { cantidad: 3, precio: 30 })]);
      expect(fila(1).total).toBe(90);
      respuestas.push({ confirmado: true, valor: 'BIZUM' });
      const promesa = estado.cobrar(1);
      expect(opciones[0].destacado).toEqual({ etiqueta: 'Cargo 1', valor: eur(30) });
      expect(fila(1).ocupada).toBeTrue();
      const put = una('PUT', `${API}/productos-alumno/1`, false);
      expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
      put.flush(cargo(1, { cantidad: 3, pagado: true }));
      await promesa;
      // Sigue ocupada hasta la relectura: no se puede volver a cobrar.
      expect(fila(1).ocupada).toBeTrue();
      expect(fila(1).puedeCobrar).toBeFalse();
      una('GET', URL_CARGOS).flush([cargo(1, { cantidad: 3, pagado: true, fechaPago: FECHA as unknown as Date })]);
      expect(fila(1).estado).toBe('pagado');
      expect(fila(1).ocupada).toBeFalse();
      expect(fila(1).muestraCobrar).toBeFalse();
    });

    it('cancelar el diálogo de cobro no envía nada', async () => {
      abrir([cargo(1)]);
      respuestas.push({ confirmado: false });
      await estado.cobrar(1);
      expect(abiertas().length).toBe(0);
      expect(fila(1).ocupada).toBeFalse();
    });

    it('295: bloquea el cobro y proporciona la nota para la barra', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarPrecio(1, '25');
      expect(fila(1).muestraCobrar).toBeTrue();
      expect(fila(1).puedeCobrar).toBeFalse();
      expect(fila(1).notaCobrar).toBe('Guarda los cambios antes de cobrar');
      expect(fila(2).puedeCobrar).toBeTrue();
      expect(fila(2).notaCobrar).toBeNull();
      await estado.cobrar(1);
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('cobrar conserva los cambios de las demás filas', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(2, 'x');
      respuestas.push({ confirmado: true, valor: '' });
      const promesa = estado.cobrar(1);
      una('PUT', `${API}/productos-alumno/1`, false).flush(cargo(1, { pagado: true }));
      await promesa;
      una('GET', URL_CARGOS).flush([cargo(1, { pagado: true }), cargo(2)]);
      expect(fila(2).notas).toBe('x');
      expect(estado.pendientes()).toBe(1);
    });
  });

  describe('descargar recibo (pieza 279)', () => {
    it('gestión: solo en cargos pagados, no en pendientes, cancelados ni devueltos', () => {
      abrir([
        cargo(1, { pagado: true }),
        cargo(2),
        cargo(3, { pagado: true, canceladoEn: [2026, 10, 2, 9, 0] }),
        cargo(4, { pagado: true, devuelto: true }),
      ]);
      expect([1, 2, 3, 4].map((id) => fila(id).puedeDescargarRecibo)).toEqual([true, false, false, false]);
    });

    it('sin gestión (solo profesor o familias) no hay recibo en ningún cargo', () => {
      gestion = false;
      abrir([cargo(1, { pagado: true })]);
      expect(fila(1).puedeDescargarRecibo).toBeFalse();
    });
  });

  describe('pedir que se revierta el cobro (gestor)', () => {
    it('el gestor en un cargo pagado: la solicitud REVERTIR_COBRO con motivo', async () => {
      admin = false;
      abrir([cargo(1, { pagado: true, fechaPago: FECHA as unknown as Date, cantidad: 2 }), cargo(2), cargo(3, { pagado: true, devuelto: true })]);
      expect(fila(1).puedePedirReversion).toBeTrue();
      expect(fila(2).puedePedirReversion).toBeFalse();
      expect(fila(3).puedePedirReversion).toBeFalse();
      respuestas.push({ confirmado: true, valores: { motivo: ' Se cobró dos veces ' } });
      await estado.pedirReversion(1);
      expect(opciones[0].titulo).toBe('Pedir al propietario que revierta este cobro');
      expect(opciones[0].comprobar!({ motivo: ' ' })).not.toBeNull();
      const post = una('POST', `${API}/solicitudes`);
      expect(post.request.body).toEqual({ tipo: 'REVERTIR_COBRO', productoAlumnoId: 1, motivo: 'Se cobró dos veces' });
      post.flush({ id: 1, tipo: 'REVERTIR_COBRO', estado: 'PENDIENTE' });
      expect(avisos.exito).toHaveBeenCalledWith('Solicitud enviada al propietario');
    });

    it('el propietario no la ve', () => {
      abrir([cargo(1, { pagado: true })]);
      expect(fila(1).puedePedirReversion).toBeFalse();
    });
  });

  // ------------------------------------------------------------------ paginación

  describe('paginación local de 10 recordada por alumno', () => {
    const muchos = () => Array.from({ length: 25 }, (_, i) => cargo(i + 1));

    it('vuelve a la página guardada y guarda la nueva', () => {
      sessionStorage.setItem('productosAlumnoEstado_7', JSON.stringify({ paginaActual: 3, tamanoPagina: 10 }));
      abrir(muchos());
      expect(estado.pagina()).toBe(3);
      expect(estado.filasPagina().length).toBe(5);
      expect([estado.desde(), estado.hasta(), estado.totalCargos()]).toEqual([21, 25, 25]);
      estado.paginaAnterior();
      expect(estado.pagina()).toBe(2);
      expect(JSON.parse(sessionStorage.getItem('productosAlumnoEstado_7')!)).toEqual({ paginaActual: 2, tamanoPagina: 10 });
      estado.paginaSiguiente();
      estado.paginaSiguiente();
      expect(estado.pagina()).toBe(3);
    });

    it('una página que ya no existe se ajusta; otro alumno no la comparte', () => {
      sessionStorage.setItem('productosAlumnoEstado_7', JSON.stringify({ paginaActual: 9, tamanoPagina: 10 }));
      sessionStorage.setItem('productosAlumnoEstado_8', JSON.stringify({ paginaActual: 2, tamanoPagina: 10 }));
      abrir(muchos());
      expect(estado.pagina()).toBe(3);
      expect(JSON.parse(sessionStorage.getItem('productosAlumnoEstado_8')!).paginaActual).toBe(2);
    });

    it('si sessionStorage falla, página 1 y sin errores', () => {
      spyOn(Storage.prototype, 'getItem').and.throwError('bloqueado');
      spyOn(Storage.prototype, 'setItem').and.throwError('lleno');
      abrir(muchos());
      expect(estado.pagina()).toBe(1);
      expect(() => estado.irAPagina(2)).not.toThrow();
      expect(estado.pagina()).toBe(2);
    });
  });

  // ------------------------------------------------------------------ salir

  describe('salir con cambios sin guardar', () => {
    it('sin cambios sale sin preguntar', async () => {
      abrir([cargo(1)]);
      expect(await estado.confirmarSalida()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('con cambios pregunta «¿Salir sin guardar?» y respeta la respuesta', async () => {
      abrir([cargo(1)]);
      estado.cambiarNotas(1, 'x');
      respuestas.push({ confirmado: false });
      expect(await estado.confirmarSalida()).toBeFalse();
      expect(opciones[0]).toEqual(jasmine.objectContaining({
        titulo: '¿Salir sin guardar?',
        mensaje: 'Tienes 1 cambio sin guardar. Si sales, se pierden.',
        accion: 'Salir sin guardar',
        cancelar: 'Seguir editando',
        destructivo: true,
      }));
      respuestas.push({ confirmado: true });
      expect(await estado.confirmarSalida()).toBeTrue();
    });

    it('el aviso cuenta los cambios pendientes', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(1, 'x');
      estado.cambiarPrecio(1, '31');
      estado.marcarBorrar(2);
      estado.elegirProducto(1);
      estado.anadirALista();
      respuestas.push({ confirmado: false });
      expect(await estado.confirmarSalida()).toBeFalse();
      expect(opciones[0].mensaje).toBe('Tienes 3 cambios sin guardar. Si sales, se pierden.');
    });
  });

  // ------------------------------------------------------------------ ronda 3

  describe('ronda 3: relecturas, cargos que cambian por fuera y guardados', () => {
    const CANCELADO = { canceladoEn: [2026, 10, 24, 10, 0], motivoCancelacion: 'Baja' } as Partial<ProductoAlumnoDTO>;

    /** Cobra `id` (diálogo confirmado) y responde la relectura con `lista`. */
    async function cobrarYReleer(id: number, lista: ProductoAlumnoDTO[]): Promise<void> {
      respuestas.push({ confirmado: true, valor: '' });
      const promesa = estado.cobrar(id);
      una('PUT', `${API}/productos-alumno/${id}`, false).flush(cargo(id, { pagado: true }));
      await promesa;
      una('GET', URL_CARGOS).flush(lista);
    }

    /** Abre la pantalla en un inyector propio, para poder destruirlo («salir» de la pantalla). */
    function abrirDestruible(cargos: ProductoAlumnoDTO[]): EnvironmentInjector {
      const inyector = createEnvironmentInjector([ProductosAlumnoEstado], TestBed.inject(EnvironmentInjector));
      estado = inyector.get(ProductosAlumnoEstado);
      estado.iniciar(7);
      responder(cargos, ALUMNO, CATALOGO);
      return inyector;
    }

    it('defecto 1: una relectura vieja no pisa lo guardado en un segundo guardado de varias filas (Cobrar ofrece 40 €)', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(1, 'a');
      estado.guardar();
      una('PUT', `${API}/productos-alumno/1`).flush({ ...cargo(1), notas: 'a' });
      // La relectura del primer guardado sigue sin respuesta (el servidor aún tenía 30 €).
      const vieja = http.expectOne(URL_CARGOS);
      estado.cambiarPrecio(2, '40');
      estado.cambiarNotas(1, 'b');
      estado.guardar();
      una('PUT', `${API}/productos-alumno/2`).flush(cargo(2, { precio: 40 }));
      if (!vieja.cancelled) {
        vieja.flush([{ ...cargo(1), notas: 'a' }, cargo(2)]);
      }
      expect(fila(2).cargo.precio).toBe(40);
      una('PUT', `${API}/productos-alumno/1`).flush({ ...cargo(1), notas: 'b' });
      expect(avisos.exito).toHaveBeenCalledWith(T.guardado);
      expect(fila(2).cargo.precio).toBe(40);
      expect(fila(2).precio).toBe('40,00');
      const nueva = una('GET', URL_CARGOS);
      // Antes de que llegue la relectura nueva, «Cobrar» ya ofrece lo que tiene el servidor.
      respuestas.push({ confirmado: false });
      await estado.cobrar(2);
      expect(opciones[0].destacado).toEqual({ etiqueta: 'Cargo 2', valor: eur(40) });
      nueva.flush([{ ...cargo(1), notas: 'b' }, cargo(2, { precio: 40 })]);
      expect(fila(2).cargo.precio).toBe(40);
      expect(estado.hayCambios()).toBeFalse();
    });

    it('defecto 1: una relectura que empezó antes de cancelar no devuelve el cargo a pendiente; se vuelve a leer', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(1, 'a');
      estado.guardar();
      una('PUT', `${API}/productos-alumno/1`).flush({ ...cargo(1), notas: 'a' });
      const vieja = http.expectOne(URL_CARGOS);
      respuestas.push({ confirmado: true, valores: { motivo: 'Duplicado' } });
      await estado.cancelar(2);
      const post = una('POST', `${API}/productos-alumno/2/cancelar`);
      // Llega con la cancelación en vuelo: no se aplica.
      vieja.flush([{ ...cargo(1), notas: 'a' }, cargo(2)]);
      expect(abiertas().length).toBe(0);
      post.flush(cargo(2, CANCELADO));
      expect(fila(2).estado).toBe('cancelado');
      expect(fila(2).muestraCobrar).toBeFalse();
      // Acabada la cancelación, se relee.
      una('GET', URL_CARGOS).flush([{ ...cargo(1), notas: 'a' }, cargo(2, CANCELADO)]);
      expect(fila(2).estado).toBe('cancelado');
      expect(abiertas().length).toBe(0);
    });

    it('defecto 1: una relectura que llega tras acabar una reactivación que empezó después se descarta y se repite', async () => {
      abrir([cargo(1), cargo(2, CANCELADO)]);
      estado.cambiarNotas(1, 'a');
      estado.guardar();
      una('PUT', `${API}/productos-alumno/1`).flush({ ...cargo(1), notas: 'a' });
      const vieja = http.expectOne(URL_CARGOS);
      respuestas.push({ confirmado: true });
      const promesa = estado.reactivar(2);
      una('POST', `${API}/productos-alumno/2/reactivar`).flush(cargo(2));
      await promesa;
      expect(fila(2).estado).toBe('pendiente');
      vieja.flush([{ ...cargo(1), notas: 'a' }, cargo(2, CANCELADO)]);
      expect(fila(2).estado).toBe('pendiente');
      una('GET', URL_CARGOS).flush([{ ...cargo(1), notas: 'a' }, cargo(2)]);
      expect(fila(2).estado).toBe('pendiente');
    });

    it('defecto 1: dos cobros seguidos: la primera fila sigue ocupada hasta que llegue una relectura (no se cobra dos veces)', async () => {
      abrir([cargo(1), cargo(2)]);
      respuestas.push({ confirmado: true, valor: '' });
      const primero = estado.cobrar(1);
      una('PUT', `${API}/productos-alumno/1`, false).flush(cargo(1, { pagado: true }));
      await primero;
      const relectura1 = http.expectOne(URL_CARGOS);
      respuestas.push({ confirmado: true, valor: '' });
      const segundo = estado.cobrar(2);
      una('PUT', `${API}/productos-alumno/2`, false).flush(cargo(2, { pagado: true }));
      await segundo;
      expect(fila(1).ocupada).toBeTrue();
      expect(fila(1).puedeCobrar).toBeFalse();
      expect(relectura1.cancelled).toBeTrue();
      una('GET', URL_CARGOS).flush([cargo(1, { pagado: true }), cargo(2, { pagado: true })]);
      expect(fila(1).ocupada).toBeFalse();
      expect(fila(2).ocupada).toBeFalse();
      expect(fila(1).estado).toBe('pagado');
    });

    it('defecto 2: cobrado por otra persona con precio pendiente: sin PUT vacío, conserva el cambio con su error y cuenta el fallo', async () => {
      abrir([cargo(1), cargo(2), cargo(3)]);
      estado.cambiarPrecio(1, '40');
      estado.cambiarCantidad(2, '2');
      estado.cambiarNotas(2, 'Talla M');
      // Una relectura (tras cobrar la 3) trae la 1 y la 2 cobradas por otra persona.
      await cobrarYReleer(3, [cargo(1, { pagado: true }), cargo(2, { pagado: true }), cargo(3, { pagado: true })]);
      expect(fila(1).modificada).toBeTrue();
      estado.guardar();
      // Ni `{}` para la 1 ni `{notas}` a medias para la 2: ningún PUT; solo la relectura del final.
      const relectura = una('GET', URL_CARGOS);
      expect(avisos.error).toHaveBeenCalledWith('2 cambios no se pudieron guardar');
      expect(avisos.exito).not.toHaveBeenCalledWith(T.guardado);
      expect(fila(1).modificada).toBeTrue();
      expect(fila(1).precio).toBe('40');
      expect(fila(1).errorServidor).toBe('Este cargo ya está cobrado: revierte el cobro para cambiar precio o cantidad.');
      expect(estado.ediciones().get(2)).toEqual({ cantidad: '2', notas: 'Talla M' });
      expect(fila(2).errorServidor).toBe(T.errorCobradoPrecioCantidad);
      relectura.flush([cargo(1, { pagado: true }), cargo(2, { pagado: true }), cargo(3, { pagado: true })]);
      expect(fila(1).errorServidor).toBe(T.errorCobradoPrecioCantidad);
      expect(estado.pendientes()).toBe(2);
      // «Deshacer» de la fila (ya cobrada) lo quita con su error; la 2 sigue pendiente.
      expect(fila(1).precioCantidadEditables).toBeFalse();
      expect(fila(1).puedeDeshacerCambios).toBeTrue();
      estado.deshacerFila(1);
      expect(fila(1).modificada).toBeFalse();
      expect(fila(1).precio).toBe('30,00');
      expect(fila(1).total).toBe(30);
      expect(fila(1).errorServidor).toBeNull();
      expect(estado.pendientes()).toBe(1);
      expect(fila(2).errorServidor).toBe(T.errorCobradoPrecioCantidad);
      estado.deshacerFila(2);
      expect(estado.hayCambios()).toBeFalse();
    });

    it('defecto 2: el cuerpo deja fuera precio y cantidad de un cargo cobrado (por eso guardar no lo envía)', () => {
      abrir([cargo(1, { pagado: true })]);
      expect(estado.cuerpoActualizacion(cargo(1, { pagado: true }), { precio: '40' })).toEqual({});
      expect(estado.cuerpoActualizacion(cargo(1), { precio: '40', notas: 'x' })).toEqual({ precio: 40, notas: 'x' });
    });

    it('defecto 3: cancelado en una relectura: fuera sus cambios salvo las notas, con el aviso en la fila', async () => {
      abrir([cargo(1), cargo(2), cargo(3), cargo(4)]);
      estado.cambiarPrecio(1, '40');
      estado.cambiarNotas(1, 'nota');
      estado.cambiarFecha(2, '2026-10-05');
      estado.cambiarNotas(3, 'solo notas');
      await cobrarYReleer(4, [cargo(1, CANCELADO), cargo(2, CANCELADO), cargo(3, CANCELADO), cargo(4, { pagado: true })]);
      expect(estado.ediciones().get(1)).toEqual({ notas: 'nota' });
      expect(estado.ediciones().has(2)).toBeFalse();
      expect(estado.ediciones().get(3)).toEqual({ notas: 'solo notas' });
      expect(fila(1).errorServidor).toBe('Este cargo se ha cancelado: sus cambios se han descartado.');
      expect(fila(2).errorServidor).toBe(T.errorCanceladoDescartado);
      // A la 3 no se le descartó nada.
      expect(fila(3).errorServidor).toBeNull();
      expect(fila(1).precio).toBe('30,00');
      expect(estado.pendientes()).toBe(2);
      estado.deshacerFila(1);
      expect(estado.pendientes()).toBe(1);
    });

    it('defecto 4: una búsqueda que esconde el producto elegido lo quita; si sigue a la vista, se queda', () => {
      abrir([]);
      estado.elegirProducto(2);
      estado.buscarProducto('matr');
      expect(estado.productoElegido()).toBe(2);
      estado.elegirDeporte('TAEKWONDO');
      estado.buscarProducto('mensu');
      expect(estado.productoElegido()).toBeNull();
      expect(estado.deporteElegido()).toBeNull();
      expect(estado.productoSeleccionado()).toBeNull();
      expect(estado.puedeAnadir()).toBeFalse();
      expect(estado.anadirALista()).toBeFalse();
      expect(estado.nuevas()).toEqual([]);
    });

    it('defecto 5: doble clic en «Guardar» envía cada petición una sola vez', () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(2, 'b');
      estado.marcarBorrar(1);
      estado.guardar();
      estado.guardar();
      una('PUT', `${API}/productos-alumno/2`).flush({ ...cargo(2), notas: 'b' });
      estado.guardar();
      una('DELETE', `${API}/productos-alumno/1`).flush(null);
      estado.guardar();
      una('GET', URL_CARGOS).flush([{ ...cargo(2), notas: 'b' }]);
      expect(abiertas().length).toBe(0);
      expect(avisos.exito).toHaveBeenCalledTimes(1);
    });

    it('defecto 5: mientras se guarda no se puede cobrar', async () => {
      abrir([cargo(1), cargo(2)]);
      estado.cambiarNotas(1, 'a');
      estado.guardar();
      expect(fila(2).puedeCobrar).toBeFalse();
      await estado.cobrar(2);
      expect(confirmar).not.toHaveBeenCalled();
      una('PUT', `${API}/productos-alumno/1`).flush({ ...cargo(1), notas: 'a' });
      una('GET', URL_CARGOS).flush([{ ...cargo(1), notas: 'a' }, cargo(2)]);
      expect(fila(2).puedeCobrar).toBeTrue();
    });

    it('defecto 5: salir a mitad del guardado corta el lote y no aplica nada ni avisa', () => {
      const inyector = abrirDestruible([cargo(1), cargo(2)]);
      estado.cambiarNotas(1, 'a');
      estado.cambiarNotas(2, 'b');
      estado.guardar();
      const put = una('PUT', `${API}/productos-alumno/2`);
      inyector.destroy();
      expect(put.cancelled).toBeTrue();
      expect(abiertas().length).toBe(0);
      expect(avisos.exito).not.toHaveBeenCalled();
      expect(avisos.error).not.toHaveBeenCalled();
    });

    it('defecto 5: salir con una reactivación o un cobro en vuelo: las respuestas tardías no se aplican ni releen', async () => {
      let inyector = abrirDestruible([cargo(1, CANCELADO)]);
      respuestas.push({ confirmado: true });
      const reactivar = estado.reactivar(1);
      const post = una('POST', `${API}/productos-alumno/1/reactivar`);
      inyector.destroy();
      post.flush(cargo(1));
      await reactivar;
      expect(estado.cargos()[0].canceladoEn).toBeTruthy();
      expect(avisos.exito).not.toHaveBeenCalled();
      expect(abiertas().length).toBe(0);

      inyector = abrirDestruible([cargo(2)]);
      respuestas.push({ confirmado: true, valor: '' });
      const cobro = estado.cobrar(2);
      const put = una('PUT', `${API}/productos-alumno/2`, false);
      inyector.destroy();
      put.flush(cargo(2, { pagado: true }));
      await cobro;
      // Sin relectura tras salir.
      expect(abiertas().length).toBe(0);
    });

    it('6: la asignación nueva enseña hoy (día local) como fecha prevista y no la envía', () => {
      abrir([]);
      jasmine.clock().install();
      try {
        jasmine.clock().mockDate(new Date(2026, 9, 25, 23, 50));
        estado.elegirProducto(1);
        expect(estado.anadirALista()).toBeTrue();
      } finally {
        jasmine.clock().uninstall();
      }
      expect(estado.nuevas()[0].fechaPrevista).toBe('25/10/2026');
      estado.guardar();
      const post = una('POST', `${API}/productos-alumno/alumno/7/producto/1`);
      expect('fechaPrevista' in post.request.body).toBeFalse();
      post.flush(cargo(5));
      una('GET', URL_CARGOS).flush([cargo(5)]);
    });

    it('7: `cobrando` solo con el PUT del cobro en vuelo; ni con el diálogo abierto, ni al cancelar, ni al releer', async () => {
      abrir([cargo(1), cargo(2)]);
      expect(fila(1).cobrando).toBeFalse();
      let confirmarCobro!: () => void;
      confirmar.and.callFake(
        (op: OpcionesConfirmacion) =>
          new Promise<ResultadoConfirmacion>((resolver) => {
            confirmarCobro = () =>
              op.ejecutar!('').subscribe({
                next: (respuesta) => resolver({ confirmado: true, valor: '', respuesta }),
                error: (error) => resolver({ confirmado: true, valor: '', error }),
              });
          })
      );
      const promesa = estado.cobrar(1);
      // Diálogo abierto: ocupada, pero no cobrando.
      expect(fila(1).ocupada).toBeTrue();
      expect(fila(1).cobrando).toBeFalse();
      confirmarCobro();
      const put = una('PUT', `${API}/productos-alumno/1`, false);
      expect(fila(1).cobrando).toBeTrue();
      expect(fila(2).cobrando).toBeFalse();
      put.flush(cargo(1, { pagado: true }));
      await promesa;
      // Releyendo: sigue ocupada, ya no cobrando.
      expect(fila(1).ocupada).toBeTrue();
      expect(fila(1).cobrando).toBeFalse();
      una('GET', URL_CARGOS).flush([cargo(1, { pagado: true }), cargo(2)]);
      expect(fila(1).cobrando).toBeFalse();

      // Un cobro que falla: deja de estar cobrando.
      const fallido = estado.cobrar(2);
      confirmarCobro();
      const put2 = una('PUT', `${API}/productos-alumno/2`, false);
      expect(fila(2).cobrando).toBeTrue();
      put2.flush({ mensaje: 'fallo' }, { status: 500, statusText: 'Error' });
      await fallido;
      expect(fila(2).cobrando).toBeFalse();
      expect(fila(2).ocupada).toBeFalse();

      // Cancelar: ocupada, nunca cobrando.
      confirmar.and.callFake(dialogoFalso);
      respuestas.push({ confirmado: true, valores: { motivo: 'x' } });
      await estado.cancelar(2);
      const cancelar = una('POST', `${API}/productos-alumno/2/cancelar`);
      expect(fila(2).ocupada).toBeTrue();
      expect(fila(2).cobrando).toBeFalse();
      cancelar.flush(cargo(2, CANCELADO));
    });
  });
});
