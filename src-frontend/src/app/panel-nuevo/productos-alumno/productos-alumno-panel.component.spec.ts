import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { ReciboPagoService } from '../cobro/recibo-pago.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ProductosAlumnoPanelComponent } from './productos-alumno-panel.component';
import { salirSinGuardarGuard } from './salir-sin-guardar.guard';

const API = environment.apiUrl;

@Component({ standalone: true, template: '<p class="ficha">Ficha</p>' })
class FichaFalsa {}

function cargo(id: number): ProductoAlumnoDTO {
  return {
    id,
    productoId: 1,
    alumnoId: 7,
    concepto: `Cargo ${id}`,
    fechaAsignacion: new Date(2026, 9, 1, 18, 30).getTime() as unknown as Date,
    cantidad: 1,
    precio: 30,
    pagado: false,
    fechaPago: null,
    notas: '',
  };
}

const ALUMNO = { id: 7, nombre: 'Lucía', apellidos: 'Ortega', direccion: 'C/ Mayor 1', activo: true, deportes: [] };
const ANONIMIZADO = {
  id: 7,
  nombre: 'Alumno',
  apellidos: 'eliminado',
  direccion: 'Sin datos',
  activo: false,
  deportes: [],
  alumnoEliminado: true,
};

describe('ProductosAlumnoPanelComponent (8b-3n)', () => {
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;
  let harness: RouterTestingHarness;
  let gestion: boolean;

  beforeEach(async () => {
    sessionStorage.clear();
    gestion = true;
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: false, valor: null });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'alumnos/:id/productos', component: ProductosAlumnoPanelComponent, canDeactivate: [salirSinGuardarGuard] },
          { path: 'alumnosEditar/:id', component: FichaFalsa },
        ]),
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: AvisosService, useValue: jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']) },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true, tieneAccesoAdmin: () => gestion, obtenerRoles: () => of([]) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  async function abrir(alumno: unknown, status = 200, cargos = [cargo(1), cargo(2)], catalogo = [{ id: 1, concepto: 'Mensualidad', precio: 30 }]): Promise<ProductosAlumnoPanelComponent> {
    const pantalla = await harness.navigateByUrl('/alumnos/7/productos', ProductosAlumnoPanelComponent);
    http.expectOne(`${API}/productos-alumno/alumno/7`).flush(cargos);
    http.expectOne(`${API}/productos/todos`).flush(catalogo);
    const peticion = http.expectOne(`${API}/alumnos/7`);
    if (status === 200) {
      peticion.flush(alumno as object);
    } else {
      peticion.flush({ mensaje: 'Alumno no encontrado con ID: 7' }, { status, statusText: 'Error' });
    }
    harness.detectChanges();
    return pantalla;
  }

  function el(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  it('pinta los cargos y «Volver a la ficha» lleva a la pestaña Pagos', async () => {
    await abrir(ALUMNO);
    expect(el().querySelectorAll('tbody tr').length).toBe(2);
    expect(el().textContent).toContain('Lucía Ortega');
    const volver = el().querySelector<HTMLAnchorElement>('header a')!;
    expect(volver.getAttribute('href')).toBe('/alumnosEditar/7?pestana=pagos');
  });

  it('decisiones 293 y 299: con `alumnoEliminado` pinta el mismo bloque de error que un alumno que no existe', async () => {
    await abrir(ANONIMIZADO);
    const anonimizado = el().querySelector('main')!.innerHTML;
    expect(el().querySelector('pn-bloque-error')).not.toBeNull();
    expect(el().textContent).toContain('No se han podido cargar los productos del alumno.');
    expect(el().textContent).not.toContain('eliminado');
    expect(el().querySelector('table')).toBeNull();
    await harness.navigateByUrl('/alumnosEditar/7', FichaFalsa);
    await abrir(null, 404);
    expect(el().querySelector('main')!.innerHTML).toBe(anonimizado);
  });

  it('sin cambios se sale sin preguntar', async () => {
    await abrir(ALUMNO);
    await harness.navigateByUrl('/alumnosEditar/7', FichaFalsa);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it('usa el nombre como título y un único selector de producto con búsqueda', async () => {
    await abrir(ALUMNO);
    expect(el().querySelector('h1')!.textContent).toBe('Lucía Ortega');
    expect(el().querySelector('.subtitulo')!.textContent).toBe('Productos y cargos');
    expect(el().querySelector('#asignar-buscar')).toBeNull();
    expect(el().querySelectorAll('[role="combobox"]').length).toBe(1);
  });

  it('vincula la búsqueda y la descripción del error al campo visible del selector reutilizado', async () => {
    const pantalla = await abrir(ALUMNO);
    pantalla.estado.anadirALista();
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    const campo = el().querySelector<HTMLInputElement>('.producto [role="combobox"]')!;
    expect(campo.getAttribute('aria-label')).toBe('Producto');
    expect(campo.getAttribute('aria-describedby')).toBe('asignar-producto-error');
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    campo.focus();
    campo.value = 'Mensualidad';
    campo.dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    expect(pantalla.estado.busqueda()).toBe('Mensualidad');
    expect(campo.value).toBe('Mensualidad');
  });

  it('conserva la consulta al filtrar, muestra la etiqueta al elegir y borra la selección al vaciar el campo', async () => {
    const pantalla = await abrir(ALUMNO, 200, [], [
      { id: 1, concepto: 'Mensualidad', precio: 30 },
      { id: 2, concepto: 'Camiseta', precio: 12 },
    ]);
    const selector = el().querySelector<HTMLSelectElement>('#asignar-producto')!;
    selector.value = '1';
    selector.dispatchEvent(new Event('change', { bubbles: true }));
    harness.detectChanges();
    const campo = el().querySelector<HTMLInputElement>('[role="combobox"]')!;
    campo.focus();
    campo.value = 'Mens';
    campo.dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    expect(pantalla.estado.productoElegido()).toBe(1);
    expect(pantalla.estado.opcionesProducto().length).toBe(1);
    expect(campo.value).toBe('Mens');
    selector.dispatchEvent(new Event('change', { bubbles: true }));
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    expect(campo.value).toContain('Mensualidad');
    expect(campo.value).not.toBe('Mens');
    campo.value = '';
    campo.dispatchEvent(new Event('input', { bubbles: true }));
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    expect(campo.value).toBe('');
    expect(pantalla.estado.busqueda()).toBe('');
    expect(pantalla.estado.productoElegido()).toBeNull();
    expect(pantalla.estado.opcionesProducto().length).toBe(2);
  });

  for (const cierre of ['Escape', 'blur']) {
    it(`restablece el catálogo al cerrar la búsqueda visible con ${cierre} sin elegir`, async () => {
      const pantalla = await abrir(ALUMNO, 200, [], [
        { id: 1, concepto: 'Mensualidad', precio: 30 },
        { id: 2, concepto: 'Camiseta', precio: 12 },
      ]);
      const campo = el().querySelector<HTMLInputElement>('[role="combobox"]')!;
      campo.focus();
      campo.value = 'cam';
      campo.dispatchEvent(new Event('input', { bubbles: true }));
      harness.detectChanges();
      await new Promise<void>((resolver) => setTimeout(resolver));
      expect(pantalla.estado.opcionesProducto().map((producto) => producto.concepto)).toEqual(['Camiseta']);
      expect(campo.value).toBe('cam');
      if (cierre === 'Escape') {
        campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      } else {
        campo.blur();
        await new Promise<void>((resolver) => setTimeout(resolver, 180));
      }
      harness.detectChanges();
      await new Promise<void>((resolver) => setTimeout(resolver));
      expect(pantalla.estado.busqueda()).toBe('');
      expect(pantalla.estado.productoElegido()).toBeNull();
      expect(campo.value).toBe('');
      expect(campo.hasAttribute('aria-activedescendant')).toBeFalse();
      campo.focus();
      campo.click();
      const lista = document.getElementById(campo.getAttribute('aria-controls')!)!;
      const opciones = Array.from(lista.querySelectorAll<HTMLElement>('[role="option"]'))
        .filter((opcion) => opcion.style.display !== 'none');
      expect(opciones.some((opcion) => opcion.textContent!.includes('Mensualidad'))).toBeTrue();
      expect(opciones.some((opcion) => opcion.textContent!.includes('Camiseta'))).toBeTrue();
    });
  }

  it('anuncia la opción activa del selector visible y la elige con el teclado', async () => {
    const pantalla = await abrir(ALUMNO, 200, [], [
      { id: 1, concepto: 'Mensualidad', precio: 30 },
      { id: 2, concepto: 'Camiseta', precio: 12 },
    ]);
    const campo = el().querySelector<HTMLInputElement>('[role="combobox"]')!;
    const tecla = (key: string) => campo.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    const activa = () => document.getElementById(campo.getAttribute('aria-activedescendant')!)!;
    campo.focus();
    tecla('ArrowDown');
    const primera = activa();
    expect(primera.textContent).toContain('Mensualidad');
    expect(primera.classList.contains('is-active')).toBeTrue();
    tecla('ArrowDown');
    expect(activa().textContent).toContain('Camiseta');
    tecla('ArrowUp');
    expect(activa().id).toBe(primera.id);
    tecla('Enter');
    harness.detectChanges();
    await new Promise<void>((resolver) => setTimeout(resolver));
    expect(pantalla.estado.productoElegido()).toBe(1);
    expect(campo.value).toContain('Mensualidad');
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    expect(campo.hasAttribute('aria-activedescendant')).toBeFalse();
    campo.click();
    tecla('Escape');
    expect(campo.getAttribute('aria-expanded')).toBe('false');
    expect(campo.hasAttribute('aria-activedescendant')).toBeFalse();
  });

  it('la etiqueta Producto apunta al campo visible y lo enfoca al pulsarla', async () => {
    await abrir(ALUMNO);
    const etiqueta = el().querySelector<HTMLLabelElement>('.producto label')!;
    const campo = el().querySelector<HTMLInputElement>('[role="combobox"]')!;
    expect(etiqueta.htmlFor).toBe(campo.id);
    etiqueta.click();
    expect(document.activeElement).toBe(campo);
  });

  it('pinta las nuevas asignaciones como primeras filas aunque no haya cargos guardados', async () => {
    const pantalla = await abrir(ALUMNO, 200, []);
    pantalla.estado.elegirProducto(1);
    pantalla.estado.anadirALista();
    harness.detectChanges();
    const nueva = pantalla.estado.nuevas()[0];
    const fila = el().querySelector('tbody tr')!;
    expect(fila.textContent).toContain('Nuevo');
    expect(fila.textContent).toContain(nueva.fechaPrevista);
    expect(fila.textContent).toContain(pantalla.eur(30));
    expect(fila.querySelector('.fecha-prevista')!.textContent).toBe(nueva.fechaPrevista);
    expect(fila.querySelector('.fecha-prevista input')).toBeNull();
    expect(fila.querySelector('[aria-label="Quitar de la lista"]')).not.toBeNull();
    pantalla.estado.descartar();
  });

  it('al marcar para eliminar devuelve el foco al Deshacer de esa fila', async () => {
    const pantalla = await abrir(ALUMNO);
    pantalla.marcarBorrar(1);
    expect(document.activeElement).toBe(el().querySelector('[data-cargo="1"] [data-deshacer]'));
    pantalla.estado.descartar();
  });

  it('mantiene el contador vivo y reserva Cobrando para la petición, no para el diálogo abierto', async () => {
    const pantalla = await abrir(ALUMNO);
    const aviso = el().querySelector('[role="status"].pendientes-estado');
    expect(aviso).not.toBeNull();
    expect(aviso!.textContent!.trim()).toBe('');
    expect(el().querySelector('[role="region"][aria-label="Cambios sin guardar"]')).toBeNull();
    const filas = pantalla.estado.filasPagina();
    const lectura = spyOn(pantalla.estado, 'filasPagina').and.returnValue([
      { ...filas[0], ocupada: true, cobrando: false, puedeCobrar: false },
    ]);
    harness.detectChanges();
    const boton = el().querySelector<HTMLButtonElement>('.pago button')!;
    expect(boton.textContent).toContain('Cobrar');
    expect(boton.textContent).not.toContain('Cobrando');
    expect(boton.disabled).toBeTrue();
    lectura.and.returnValue([{ ...filas[0], ocupada: true, cobrando: true, puedeCobrar: false }]);
    harness.detectChanges();
    expect(boton.textContent).toContain('Cobrando');
    expect(el().querySelector('.pendientes-estado')).toBe(aviso);
    pantalla.estado.cambiarNotas(filas[0].id, 'Nota pendiente');
    harness.detectChanges();
    expect(el().querySelector('[role="region"][aria-label="Cambios sin guardar"]')).not.toBeNull();
    expect(aviso!.textContent).toContain('1 cambio sin guardar');
    pantalla.estado.descartar();
    harness.detectChanges();
    expect(el().querySelector('.pendientes-estado')).toBe(aviso);
    expect(aviso!.textContent!.trim()).toBe('');
    expect(el().querySelector('[role="region"][aria-label="Cambios sin guardar"]')).toBeNull();
  });

  for (const campo of ['precio', 'cantidad']) {
    it('muestra una sola nota de cobro en la barra al editar ' + campo, async () => {
      const pantalla = await abrir(ALUMNO);
      const boton = (id: number) => el().querySelector<HTMLButtonElement>('[data-cargo="' + id + '"] .pago button')!;
      expect(el().querySelector('#cobrar-nota-cambios')).toBeNull();
      expect(boton(1).getAttribute('aria-describedby')).toBeNull();
      expect(boton(1).getAttribute('title')).toBeNull();
      const entrada = el().querySelector<HTMLInputElement>('#' + campo + '-1')!;
      entrada.value = campo === 'precio' ? '25' : '2';
      entrada.dispatchEvent(new Event('input', { bubbles: true }));
      harness.detectChanges();
      const nota = el().querySelector('.pendientes #cobrar-nota-cambios')!;
      expect(nota.textContent).toBe(pantalla.textos.notaCobrarConCambios);
      expect(el().querySelector('.pendientes-aviso')!.nextElementSibling).toBe(nota);
      expect(boton(1).disabled).toBeTrue();
      expect(boton(1).getAttribute('aria-describedby')).toBe(nota.id);
      expect(boton(1).getAttribute('title')).toBe(nota.textContent);
      expect(boton(2).disabled).toBeFalse();
      expect(boton(2).getAttribute('aria-describedby')).toBeNull();
      expect(boton(2).getAttribute('title')).toBeNull();
      pantalla.estado.cambiarCantidad(2, '2');
      harness.detectChanges();
      expect(el().querySelectorAll('#cobrar-nota-cambios').length).toBe(1);
      expect(boton(2).getAttribute('aria-describedby')).toBe(nota.id);
      expect(el().querySelector('tbody [id^="cobrar-nota-"]')).toBeNull();
      expect(el().querySelector('tbody')!.textContent).not.toContain(nota.textContent!);
      if (campo === 'precio') pantalla.estado.cambiarPrecio(1, '30');
      else pantalla.estado.cambiarCantidad(1, '1');
      harness.detectChanges();
      expect(boton(1).disabled).toBeFalse();
      expect(boton(1).getAttribute('aria-describedby')).toBeNull();
      expect(el().querySelector('#cobrar-nota-cambios')).not.toBeNull();
      pantalla.estado.cambiarCantidad(2, '1');
      harness.detectChanges();
      expect(el().querySelector('#cobrar-nota-cambios')).toBeNull();
      expect(boton(2).getAttribute('aria-describedby')).toBeNull();
      expect(el().querySelector('.pendientes')).toBeNull();
    });
  }

  it('mantiene la barra sin nota con altas, borrados o cambios de un cargo pagado', async () => {
    const pantalla = await abrir(ALUMNO, 200, [cargo(1), { ...cargo(2), pagado: true }]);
    pantalla.estado.elegirProducto(1);
    pantalla.estado.anadirALista();
    pantalla.estado.marcarBorrar(1);
    pantalla.estado.cambiarNotas(2, 'Nota pendiente');
    harness.detectChanges();
    expect(el().querySelector('.pendientes')).not.toBeNull();
    expect(el().querySelector('#cobrar-nota-cambios')).toBeNull();
    expect(el().querySelector('[data-cargo="1"] .pago button')!.getAttribute('aria-describedby')).toBeNull();
    pantalla.estado.descartar();
  });

  it('conserva la nota global aunque la fila modificada este en otra pagina', async () => {
    const pantalla = await abrir(ALUMNO, 200, Array.from({ length: 11 }, (_, i) => cargo(i + 1)));
    const id = pantalla.estado.filasPagina()[0].id;
    pantalla.estado.cambiarPrecio(id, '25');
    pantalla.estado.paginaSiguiente();
    harness.detectChanges();
    expect(el().querySelector('[data-cargo="' + id + '"]')).toBeNull();
    expect(el().querySelector('.pendientes #cobrar-nota-cambios')).not.toBeNull();
    expect(el().querySelector('.pago button')!.getAttribute('aria-describedby')).toBeNull();
    pantalla.estado.descartar();
  });

  it('formatea el precio del resumen en euros y conserva las fechas de pago formateadas', async () => {
    const pantalla = await abrir(ALUMNO, 200, [{ ...cargo(1), fechaPago: new Date(2026, 9, 5) }]);
    expect(pantalla.precioResumen('1.010,50')).toBe(pantalla.eur(1010.5));
    expect(pantalla.precioResumen('30')).toBe(pantalla.eur(30));
    expect(pantalla.precioResumen('')).toBe('—');
    expect(pantalla.precioResumen('1.234')).toBe('—');
    expect(el().querySelector('.resumen')!.textContent).toContain(`1 × ${pantalla.eur(30)}`);
    expect(el().querySelector('.fecha-pago')!.textContent).toContain('05/10/2026');
    expect(el().querySelector('.notas-texto')!.textContent).toBe('—');
  });

  it('presenta los errores de cantidad y precio con los textos aprobados y sus descripciones', async () => {
    const pantalla = await abrir(ALUMNO);
    pantalla.estado.cambiarCantidad(1, '0');
    pantalla.estado.cambiarPrecio(1, '1,001');
    harness.detectChanges();
    const cantidad = el().querySelector<HTMLInputElement>('#cantidad-1')!;
    const precio = el().querySelector<HTMLInputElement>('#precio-1')!;
    expect(cantidad.getAttribute('aria-invalid')).toBe('true');
    expect(precio.getAttribute('aria-invalid')).toBe('true');
    expect(cantidad.getAttribute('aria-describedby')).toContain('cantidad-error-1');
    expect(precio.getAttribute('aria-describedby')).toContain('precio-error-1');
    expect(el().querySelector('#cantidad-error-1')!.textContent).toBe('Pon una cantidad entera de 1 o más.');
    expect(el().querySelector('#precio-error-1')!.textContent).toBe('Pon un precio de 0 o más, con dos decimales como mucho.');
    pantalla.estado.descartar();
  });

  it('oculta la paginación con una página y la muestra con más de diez cargos', async () => {
    await abrir(ALUMNO);
    expect(el().querySelector('nav.paginacion')).toBeNull();
    await harness.navigateByUrl('/alumnosEditar/7', FichaFalsa);
    await abrir(ALUMNO, 200, Array.from({ length: 11 }, (_, indice) => cargo(indice + 1)));
    expect(el().querySelector('nav.paginacion')).not.toBeNull();
  });

  it('despliega la fila sin cambiar el cargo y permite cerrar el menú con Escape', async () => {
    const pantalla = await abrir(ALUMNO);
    const expandir = el().querySelector<HTMLButtonElement>('[data-expandir]')!;
    expect(expandir.getAttribute('aria-expanded')).toBe('false');
    expandir.click();
    harness.detectChanges();
    expect(expandir.getAttribute('aria-expanded')).toBe('true');
    expect(pantalla.estado.hayCambios()).toBeFalse();
    const boton = el().querySelector<HTMLButtonElement>('[aria-haspopup="menu"]')!;
    boton.click();
    harness.detectChanges();
    const menu = el().querySelector<HTMLElement>('[role="menu"]')!;
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    harness.detectChanges();
    expect(el().querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(boton);
  });

  it('edita notas con el diálogo compartido y las mantiene pendientes de guardar', async () => {
    const pantalla = await abrir(ALUMNO);
    confirmar.and.resolveTo({ confirmado: true, valores: { 'cargo-notas': 'Nota nueva' } });
    await pantalla.editarNotas(pantalla.estado.filasPagina()[0]);
    harness.detectChanges();
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('Editar notas');
    expect(pantalla.estado.filasPagina()[0].notas).toBe('Nota nueva');
    expect(el().textContent).toContain('1 cambio sin guardar');
    pantalla.estado.descartar();
  });

  it('edita notas solo desde el menú y conserva su disparador para devolver el foco', async () => {
    const pantalla = await abrir(ALUMNO);
    const filas = pantalla.estado.filasPagina();
    const botonMenu = el().querySelector<HTMLButtonElement>('[aria-haspopup="menu"]')!;
    pantalla.alternarMenu(filas[0].id, botonMenu);
    harness.detectChanges();
    expect(el().querySelector('.notas button')).toBeNull();
    const botonNotas = el().querySelector<HTMLButtonElement>('[role="menuitem"]')!;
    expect(botonNotas.textContent).toContain('Editar notas');
    botonNotas.focus();
    let disparadorDialogo: Element | null = null;
    confirmar.and.callFake(async () => {
      // El diálogo compartido conserva el disparador y devuelve el foco al cerrarse.
      disparadorDialogo = document.activeElement;
      (disparadorDialogo as HTMLElement)?.focus();
      return { confirmado: false, valor: null };
    });
    botonNotas.click();
    await Promise.resolve();
    expect<Element | null>(disparadorDialogo).toBe(botonMenu);
    expect(document.activeElement).toBe(botonMenu);
    expect(pantalla.menuAbierto).toBeNull();
  });

  function opcionesDelMenu(pantalla: ProductosAlumnoPanelComponent, id: number): string[] {
    const fila = el().querySelector<HTMLElement>(`[data-cargo="${id}"]`)!;
    pantalla.alternarMenu(id, fila.querySelector<HTMLButtonElement>('[aria-haspopup="menu"]')!);
    harness.detectChanges();
    return Array.from(fila.querySelectorAll('[role="menuitem"]')).map((b) => b.textContent!.trim());
  }

  it('recibo (pieza 279): «Descargar recibo» en el menú de un cargo pagado y descarga el de ese cargo', async () => {
    const pantalla = await abrir(ALUMNO, 200, [{ ...cargo(1), pagado: true }, cargo(2)]);
    const descargar = spyOn(TestBed.inject(ReciboPagoService), 'descargar').and.resolveTo();
    expect(opcionesDelMenu(pantalla, 1)).toContain('Descargar recibo');
    el().querySelector<HTMLElement>('[data-cargo="1"]')!.querySelectorAll<HTMLButtonElement>('[role="menuitem"]').forEach((b) => {
      if (b.textContent!.includes('Descargar recibo')) b.click();
    });
    expect(descargar).toHaveBeenCalledOnceWith(1);
    expect(pantalla.menuAbierto).toBeNull();
    expect(opcionesDelMenu(pantalla, 2)).not.toContain('Descargar recibo');
  });

  it('recibo: sin gestión no hay «Descargar recibo» en un cargo pagado', async () => {
    gestion = false;
    const pantalla = await abrir(ALUMNO, 200, [{ ...cargo(1), pagado: true }]);
    expect(opcionesDelMenu(pantalla, 1)).not.toContain('Descargar recibo');
  });

  it('describe los campos pagados con un único aviso de tabla y conserva la nota móvil', async () => {
    await abrir(ALUMNO, 200, [{ ...cargo(1), pagado: true }, { ...cargo(2), pagado: true }]);
    const escritorio = el().querySelector<HTMLElement>('#precio-nota-pagados')!;
    const movil = el().querySelector<HTMLElement>('.campo #precio-nota-movil-1')!;
    expect(escritorio.textContent).toBe('Revierte el cobro para cambiar precio o cantidad');
    expect(movil.textContent).toBe(escritorio.textContent);
    expect(el().querySelectorAll('#precio-nota-pagados').length).toBe(1);
    expect(el().querySelector('.concepto .nota-precio-escritorio')).toBeNull();
    expect(el().querySelectorAll('#precio-nota-movil-1').length).toBe(1);
    for (const campo of ['cantidad', 'precio']) {
      const entrada = el().querySelector<HTMLInputElement>(`#${campo}-1`)!;
      expect(entrada.disabled).toBeTrue();
      expect(entrada.getAttribute('aria-describedby')).toContain(escritorio.id);
      expect(entrada.getAttribute('aria-describedby')).not.toContain(movil.id);
      expect(entrada.title).toBe(escritorio.textContent);
    }
  });

  it('conserva el precio editable sin símbolo y presenta el sufijo euro dentro del control', async () => {
    await abrir(ALUMNO);
    const entrada = el().querySelector<HTMLInputElement>('#precio-1')!;
    expect(entrada.value).toBe('30,00');
    expect(entrada.parentElement!.querySelector('.precio-sufijo')!.textContent).toBe('€');
    expect(el().querySelector('thead th:last-child')!.textContent).toBe('Acciones');
    expect(el().querySelector('.lista table')).not.toBeNull();
    expect(el().querySelector('#precio-nota-pagados')).toBeNull();
    expect(el().querySelectorAll('[data-cargo="1"] .pago button').length).toBe(1);
    expect(el().querySelector('.cobrar-resumen')).toBeNull();
  });

  it('con cambios pregunta al salir: «Seguir editando» se queda y «Salir sin guardar» sale', async () => {
    const pantalla = await abrir(ALUMNO);
    pantalla.estado.cambiarNotas(1, 'x');
    const router = TestBed.inject(Router);
    expect(await router.navigateByUrl('/alumnosEditar/7')).toBeFalse();
    expect(confirmar).toHaveBeenCalledTimes(1);
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar?');
    expect(router.url).toBe('/alumnos/7/productos');
    confirmar.and.resolveTo({ confirmado: true, valor: null });
    expect(await router.navigateByUrl('/alumnosEditar/7')).toBeTrue();
    expect(router.url).toBe('/alumnosEditar/7');
  });

  it('beforeunload: el aviso del navegador solo con cambios', async () => {
    // No se lanza el evento en window: Karma lo toma por una recarga de la página.
    const pantalla = await abrir(ALUMNO);
    const evento = () => ({ preventDefault: jasmine.createSpy('preventDefault'), returnValue: undefined as unknown });
    const sin = evento();
    pantalla.antesDeDescargar(sin as unknown as BeforeUnloadEvent);
    expect(sin.preventDefault).not.toHaveBeenCalled();
    pantalla.estado.cambiarNotas(1, 'x');
    const con = evento();
    pantalla.antesDeDescargar(con as unknown as BeforeUnloadEvent);
    expect(con.preventDefault).toHaveBeenCalled();
    expect(con.returnValue).toBe('');
    pantalla.estado.descartar();
  });
});
