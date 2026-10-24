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

  beforeEach(async () => {
    sessionStorage.clear();
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
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true, obtenerRoles: () => of([]) },
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

  async function abrir(alumno: unknown, status = 200, cargos = [cargo(1), cargo(2)]): Promise<ProductosAlumnoPanelComponent> {
    const pantalla = await harness.navigateByUrl('/alumnos/7/productos', ProductosAlumnoPanelComponent);
    http.expectOne(`${API}/productos-alumno/alumno/7`).flush(cargos);
    http.expectOne(`${API}/productos/todos`).flush([{ id: 1, concepto: 'Mensualidad', precio: 30 }]);
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

  it('describe cantidad y precio pagados con notas únicas en concepto y junto a los campos', async () => {
    await abrir(ALUMNO, 200, [{ ...cargo(1), pagado: true }]);
    const escritorio = el().querySelector<HTMLElement>('.concepto #precio-nota-escritorio-1')!;
    const movil = el().querySelector<HTMLElement>('.campo #precio-nota-movil-1')!;
    expect(escritorio.textContent).toBe('Revierte el cobro para cambiar precio o cantidad');
    expect(movil.textContent).toBe(escritorio.textContent);
    expect(el().querySelectorAll('#precio-nota-escritorio-1').length).toBe(1);
    expect(el().querySelectorAll('#precio-nota-movil-1').length).toBe(1);
    for (const campo of ['cantidad', 'precio']) {
      const entrada = el().querySelector<HTMLInputElement>(`#${campo}-1`)!;
      expect(entrada.disabled).toBeTrue();
      expect(entrada.getAttribute('aria-describedby')).toContain(escritorio.id);
      expect(entrada.getAttribute('aria-describedby')).toContain(movil.id);
    }
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
