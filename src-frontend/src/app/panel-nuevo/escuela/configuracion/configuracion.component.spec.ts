import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { PermisosSesionService } from '../../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../../avisos/avisos.service';
import { CLASE_ERROR_HTTP } from '../../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { estilosPanelGuard } from '../../estilos-panel.guard';
import { EstilosPanelService } from '../../estilos-panel.service';
import { CuentaConfiguracion } from './configuracion-vista';
import { ConfiguracionPanelComponent } from './configuracion.component';

@Component({ standalone: true, template: '' })
class PantallaNueva {}

const API = environment.apiUrl;
const LIMITE = `${API}/turnos/limite`;
const USUARIOS = `${API}/admin/users`;
const GUARDAR_LIMITE = `${API}/admin/configuracion/limite-turno`;
const roles = (id: number) => `${API}/admin/users/${id}/roles`;
const contrasena = (id: number) => `${API}/admin/users/${id}/password`;

function cuenta(cambios: Partial<CuentaConfiguracion> = {}): CuentaConfiguracion {
  return { id: 2, nombre: 'Ana', apellidos: 'Prueba', email: 'ana@ejemplo.test', rol: '[ROLE_USER]', authProvider: 'LOCAL', ...cambios };
}

const CUENTAS: CuentaConfiguracion[] = [
  cuenta(),
  cuenta({ id: 3, nombre: 'Leo', apellidos: 'Ficticio', email: 'leo@ejemplo.test', rol: '[ROLE_ADMIN, ROLE_USER]' }),
  cuenta({ id: 4, nombre: 'Iris', apellidos: 'Inventada', email: 'iris@ejemplo.test', rol: '[ROLE_PROFESOR, ROLE_RESPONSABLE_SEDE]' }),
  cuenta({ id: 5, nombre: 'Gael', apellidos: 'Google', email: 'gael@ejemplo.test', rol: '[ROLE_MANAGER]', authProvider: 'GOOGLE' }),
];

/** `matchMedia` de un navegador de `px` de ancho: las consultas de ancho de las capas y del componente. */
function consultaDeAncho(px: number): (q: string) => MediaQueryList {
  return (q: string) => {
    const max = /max-width:\s*([\d.]+)px/.exec(q);
    const min = /min-width:\s*([\d.]+)px/.exec(q);
    const matches = (!!max || !!min) && (!max || px <= Number(max[1])) && (!min || px >= Number(min[1]));
    return {
      matches, media: q, onchange: null, addEventListener: () => undefined, removeEventListener: () => undefined,
      addListener: () => undefined, removeListener: () => undefined, dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  };
}

function simularAncho(px: number): void {
  spyOn(window, 'matchMedia').and.callFake(consultaDeAncho(px));
}

describe('ConfiguracionPanelComponent (8b-3k, Configuración del panel nuevo: P1 en móvil, P2 desde 768 px)', () => {
  let fixture: ComponentFixture<ConfiguracionPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/[\s  ]+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const peticiones = (url: string, metodo = 'GET'): TestRequest[] => http.match((r) => r.method === metodo && r.url === url);
  const fila = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.conf__cuenta[data-id="${id}"]`)!;
  const valores = (): string =>
    Array.from(el().querySelectorAll('.conf__valores > div')).map((d) => `${texto(d.querySelector('dt'))}: ${texto(d.querySelector('dd'))}`).join(' | ');
  const casilla = (id: number, rol: string): HTMLInputElement =>
    fila(id).querySelector<HTMLInputElement>(`input[type="checkbox"][value="${rol}"]`)!;

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : a.classList.contains('pn-aviso--info') ? 'info' : 'exito',
      texto: texto(a.querySelector('.pn-aviso__texto')),
    }));
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function crear(ancho: number): void {
    simularAncho(ancho);
    fixture = TestBed.createComponent(ConfiguracionPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function cargar(ancho = 375, cuentas: CuentaConfiguracion[] = CUENTAS, limite = 36): void {
    crear(ancho);
    peticiones(LIMITE).forEach((r) => r.flush(limite));
    peticiones(USUARIOS).forEach((r) => r.flush(cuentas));
    fixture.detectChanges();
  }

  function elegirSeccion(clave: string): void {
    el().querySelector<HTMLButtonElement>(`#conf-tab-${clave}`)!.click();
    fixture.detectChanges();
  }

  function escribir(control: HTMLInputElement, valor: string): void {
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function marcar(c: HTMLInputElement): void {
    c.click();
    fixture.detectChanges();
  }

  async function confirmar(): Promise<void> {
    (dialogo()!.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
    await esperar();
  }

  beforeEach(async () => {
    Swal.close();
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
    sessionStorage.removeItem('panelConfiguracionUsuariosEstado');
    quitarTokens = ponerTokensDePrueba();
    await TestBed.configureTestingModule({
      imports: [ConfiguracionPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of({}) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    quitarTokens();
    jasmine.clock().uninstall();
    sessionStorage.removeItem('panelConfiguracionUsuariosEstado');
  });

  // ------------------------------------------------------------------ lecturas y distribuciones

  it('al entrar pide el límite y las cuentas con las peticiones de hoy y ERROR_HANDLED_LOCALLY', () => {
    crear(375);
    const [l] = peticiones(LIMITE);
    expect(l.request.urlWithParams).toBe(LIMITE);
    expect(l.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    const [u] = peticiones(USUARIOS);
    expect(u.request.withCredentials).toBeTrue();
    expect(u.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    l.flush(36);
    u.flush(CUENTAS);
    http.verify();
    expect(texto(el().querySelector('h1'))).toBe('Configuración');
  });

  it('a 375 (P1): las dos secciones seguidas, sin índice; «Avisos legales y cookies» queda en el menú de la cuenta', () => {
    cargar(375);
    expect(el().querySelector('[role="tablist"]')).toBeNull();
    const secciones = Array.from(el().querySelectorAll('.conf__seccion')).map((s) => texto(s.querySelector('h2')));
    expect(secciones).toEqual(['Alumnos por turno', 'Roles y permisos']);
  });

  it('desde 768 (P2): índice con Alumnos por turno, Roles y permisos y Avisos legales y cookies, y a la derecha la sección elegida', () => {
    cargar(768);
    const tabs = Array.from(el().querySelectorAll('[role="tab"]'));
    expect(tabs.map((t) => [texto(t), t.getAttribute('aria-selected')])).toEqual([
      ['Alumnos por turno', 'true'], ['Roles y permisos', 'false'], ['Avisos legales y cookies', 'false'],
    ]);
    expect(el().querySelector('[role="tablist"]')!.getAttribute('aria-orientation')).toBe('vertical');
    const visibles = () => Array.from(el().querySelectorAll('.conf__seccion')).map((s) => texto(s.querySelector('h2')));
    expect(visibles()).toEqual(['Alumnos por turno']);
    elegirSeccion('roles');
    expect(visibles()).toEqual(['Roles y permisos']);
    elegirSeccion('legal');
    expect(visibles()).toEqual(['Avisos legales y cookies']);
    const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('.conf__legales a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toEqual(['/politica-privacidad', '/politica-cookies', '/aviso-legal']);
  });

  it('el índice se mueve con las flechas y no cambia la URL', () => {
    cargar(1280);
    document.body.appendChild(el());
    const router = TestBed.inject(Router);
    const url = router.url;
    const lista = el().querySelector<HTMLElement>('[role="tablist"]')!;
    el().querySelector<HTMLButtonElement>('#conf-tab-turno')!.focus();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement?.id).toBe('conf-tab-roles');
    expect(el().querySelector('#conf-tab-roles')!.getAttribute('aria-selected')).toBe('true');
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement?.id).toBe('conf-tab-legal');
    expect(router.url).toBe(url);
  });

  it('las mismas acciones a 375 y a 768', () => {
    const acciones = (): string[] => {
      const botones = Array.from(el().querySelectorAll<HTMLButtonElement>('.conf__seccion button')).map((b) => texto(b));
      const casillas = Array.from(el().querySelectorAll<HTMLInputElement>('.conf__seccion input[type="checkbox"]')).map(
        (c) => `${c.value}:${texto(c.closest('label'))}`
      );
      const campos = Array.from(el().querySelectorAll<HTMLInputElement>('.conf__seccion input:not([type="checkbox"])')).map((c) => c.id);
      return [...new Set([...botones, ...casillas, ...campos])].sort();
    };
    cargar(375);
    const enMovil = acciones();
    fixture.destroy();
    (window.matchMedia as jasmine.Spy).and.callFake(consultaDeAncho(768));
    fixture = TestBed.createComponent(ConfiguracionPanelComponent);
    fixture.detectChanges();
    peticiones(LIMITE).forEach((r) => r.flush(36));
    peticiones(USUARIOS).forEach((r) => r.flush(CUENTAS));
    fixture.detectChanges();
    expect(el().querySelector('[role="tablist"]')).not.toBeNull();
    const turno = acciones();
    elegirSeccion('roles');
    const enAncho = [...new Set([...turno, ...acciones()])].sort();
    expect(enAncho).toEqual(enMovil);
    expect(enMovil).toContain('Guardar cambios');
    expect(enMovil).toContain('Restaurar');
    expect(enMovil).toContain('Guardar');
    expect(enMovil).toContain('Restablecer contraseña');
    expect(enMovil).toContain('ROLE_USER:Familia');
    expect(enMovil).toContain('ROLE_MANAGER:Gestión');
    expect(enMovil).toContain('ROLE_ADMIN:Administración');
  });

  // ------------------------------------------------------------------ alumnos por turno

  it('límite: valor actual, nuevo valor y «Modificado»; Guardar y Restaurar solo con cambios', () => {
    cargar(375);
    const campo = el().querySelector<HTMLInputElement>('#conf-limite')!;
    expect(campo.value).toBe('36');
    expect(campo.getAttribute('min')).toBe('1');
    expect(valores()).toBe('Valor actual: 36 alumnos | Nuevo valor: 36 alumnos');
    expect(boton(el(), 'Guardar cambios').disabled).toBeTrue();
    expect(boton(el(), 'Restaurar').disabled).toBeTrue();
    escribir(campo, '40');
    expect(valores()).toBe('Valor actual: 36 alumnos | Nuevo valor: 40 alumnos Modificado');
    expect(boton(el(), 'Guardar cambios').disabled).toBeFalse();
    boton(el(), 'Restaurar').click();
    fixture.detectChanges();
    expect(campo.value).toBe('36');
    expect(boton(el(), 'Guardar cambios').disabled).toBeTrue();
    expect(texto(el().querySelector('.conf__nota'))).toContain('página pública de horarios');
  });

  it('límite menor que 1: el error bajo el campo y Guardar desactivado', () => {
    cargar(375);
    const campo = el().querySelector<HTMLInputElement>('#conf-limite')!;
    escribir(campo, '0');
    const error = el().querySelector('#conf-limite-error');
    expect(texto(error)).toBe('El límite debe ser al menos 1.');
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    expect(campo.getAttribute('aria-describedby')).toContain('conf-limite-error');
    expect(boton(el(), 'Guardar cambios').disabled).toBeTrue();
    expect(dialogo()).toBeNull();
  });

  it('guardar el límite: confirmación y la misma llamada de hoy (PUT con el número en el cuerpo)', async () => {
    cargar(375);
    escribir(el().querySelector<HTMLInputElement>('#conf-limite')!, '40');
    boton(el(), 'Guardar cambios').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('El límite de alumnos por turno pasa de 36 a 40.');
    await confirmar();
    const [put] = peticiones(GUARDAR_LIMITE, 'PUT');
    expect(put.request.body).toBe(40);
    expect(typeof put.request.body).toBe('number');
    expect(put.request.withCredentials).toBeTrue();
    put.flush(40);
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Límite por turno guardado: 40 alumnos.' }]);
    expect(valores()).toBe('Valor actual: 40 alumnos | Nuevo valor: 40 alumnos');
    http.verify();
  });

  it('cancelar la confirmación del límite no envía nada; un error lo avisa y deja el valor nuevo', async () => {
    cargar(375);
    escribir(el().querySelector<HTMLInputElement>('#conf-limite')!, '40');
    boton(el(), 'Guardar cambios').click();
    await esperar();
    (dialogo()!.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
    await esperar();
    expect(peticiones(GUARDAR_LIMITE, 'PUT').length).toBe(0);
    boton(el(), 'Guardar cambios').click();
    await esperar();
    await confirmar();
    peticiones(GUARDAR_LIMITE, 'PUT')[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo guardar la configuración.' }]);
    expect(valores()).toContain('Modificado');
  });

  it('si no se puede leer el límite: bloque de error con «Reintentar» que vuelve a pedirlo', () => {
    crear(375);
    peticiones(LIMITE)[0].flush(null, { status: 500, statusText: 'Error' });
    peticiones(USUARIOS)[0].flush(CUENTAS);
    fixture.detectChanges();
    const seccion = el().querySelector('.conf__seccion[data-seccion="turno"]')!;
    expect(texto(seccion.querySelector('.pn-bloque-error'))).toContain('No se ha podido leer el límite por turno.');
    expect(seccion.querySelector('#conf-limite')).toBeNull();
    boton(seccion, 'Reintentar').click();
    fixture.detectChanges();
    peticiones(LIMITE)[0].flush(30);
    fixture.detectChanges();
    expect(el().querySelector<HTMLInputElement>('#conf-limite')!.value).toBe('30');
  });

  // ------------------------------------------------------------------ roles y permisos

  it('cada cuenta: iniciales, nombre, email, pastillas con los nombres nuevos y casillas Familia, Gestión y Administración', () => {
    cargar(375);
    const ana = fila(2);
    expect(texto(ana.querySelector('.conf__avatar'))).toBe('AP');
    expect(texto(ana.querySelector('.conf__nombre'))).toBe('Ana Prueba');
    expect(texto(ana.querySelector('.conf__email'))).toBe('ana@ejemplo.test');
    expect(Array.from(fila(3).querySelectorAll('.conf__rol')).map((p) => texto(p))).toEqual(['Administración', 'Familia']);
    const casillas = Array.from(ana.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')).map((c) => [
      c.value, texto(c.closest('label')), c.checked,
    ]);
    expect(casillas).toEqual([
      ['ROLE_USER', 'Familia', true], ['ROLE_MANAGER', 'Gestión', false], ['ROLE_ADMIN', 'Administración', false],
    ]);
    expect(texto(el())).not.toContain('Manager');
    expect(texto(el())).not.toContain('Usuario');
  });

  it('Profesorado y Responsable de sede se ven como pastillas, pero no se ofrecen (la ruta de roles no los acepta)', () => {
    cargar(375);
    const iris = fila(4);
    expect(Array.from(iris.querySelectorAll('.conf__rol')).map((p) => texto(p))).toEqual(['Responsable de sede', 'Profesorado']);
    expect(Array.from(iris.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')).map((c) => c.value)).toEqual([
      'ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN',
    ]);
    expect(el().querySelector('input[value="ROLE_PROFESOR"], input[value="ROLE_RESPONSABLE_SEDE"]')).toBeNull();
  });

  it('cambiar roles: Guardar con cambios, confirmación y el mismo cuerpo con los ROLE_* (en el orden de hoy); después relee', async () => {
    cargar(375);
    const guardar = () => boton(fila(2), 'Guardar');
    expect(guardar().disabled).toBeTrue();
    marcar(casilla(2, 'ROLE_ADMIN'));
    marcar(casilla(2, 'ROLE_MANAGER'));
    expect(guardar().disabled).toBeFalse();
    guardar().click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Se cambiarán los roles de Ana Prueba a Familia, Gestión y Administración.'
    );
    await confirmar();
    const [put] = peticiones(roles(2), 'PUT');
    expect(put.request.body).toEqual(['ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN']);
    expect(put.request.withCredentials).toBeTrue();
    put.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Roles de Ana Prueba actualizados.' }]);
    const [relectura] = peticiones(USUARIOS);
    relectura.flush([cuenta({ rol: '[ROLE_USER, ROLE_MANAGER, ROLE_ADMIN]' }), ...CUENTAS.slice(1)]);
    fixture.detectChanges();
    expect(Array.from(fila(2).querySelectorAll('.conf__rol')).map((p) => texto(p))).toEqual(['Administración', 'Gestión', 'Familia']);
    expect(guardar().disabled).toBeTrue();
    http.verify();
  });

  it('el 409 de último propietario (decisión 221) se avisa con su «mensaje» y las casillas vuelven a lo guardado', async () => {
    cargar(375);
    marcar(casilla(3, 'ROLE_ADMIN'));
    boton(fila(3), 'Guardar').click();
    await esperar();
    await confirmar();
    const [put] = peticiones(roles(3), 'PUT');
    expect(put.request.body).toEqual(['ROLE_USER']);
    put.flush({ mensaje: 'La escuela no puede quedarse sin propietario.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'La escuela no puede quedarse sin propietario.' }]);
    expect(casilla(3, 'ROLE_ADMIN').checked).toBeTrue();
    expect(boton(fila(3), 'Guardar').disabled).toBeTrue();
  });

  it('una cuenta no se queda sin roles: la última casilla vuelve a marcarse con un aviso', () => {
    cargar(375);
    marcar(casilla(2, 'ROLE_USER'));
    expect(casilla(2, 'ROLE_USER').checked).toBeTrue();
    expect(avisos()).toEqual([{ tipo: 'info', texto: 'Una cuenta debe tener al menos un rol.' }]);
    expect(boton(fila(2), 'Guardar').disabled).toBeTrue();
  });

  it('buscador por nombre o email, cinco cuentas por página y aviso sin usuarios', () => {
    const muchas = Array.from({ length: 7 }, (_, i) =>
      cuenta({ id: 10 + i, nombre: `Persona${i}`, apellidos: 'Prueba', email: `p${i}@ejemplo.test` })
    );
    cargar(375, muchas);
    expect(el().querySelectorAll('.conf__cuenta').length).toBe(5);
    const paginas = el().querySelector('.conf__paginacion')!;
    boton(paginas, 'Página 2').click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.conf__cuenta').length).toBe(2);
    escribir(el().querySelector<HTMLInputElement>('#conf-buscar')!, 'p3@');
    expect(Array.from(el().querySelectorAll('.conf__cuenta')).map((f) => f.getAttribute('data-id'))).toEqual(['13']);
    escribir(el().querySelector<HTMLInputElement>('#conf-buscar')!, 'nadie');
    expect(texto(el().querySelector('.conf__seccion[data-seccion="roles"] .pn-bloque-vacio'))).toContain('Ninguna cuenta coincide');
    fixture.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
  });

  it('sin cuentas: el aviso de hoy', () => {
    cargar(375, []);
    expect(texto(el().querySelector('.conf__seccion[data-seccion="roles"] .pn-bloque-vacio'))).toContain(
      'No hay usuarios registrados en el sistema.'
    );
  });

  // ------------------------------------------------------------------ restablecer contraseña

  it('restablecer contraseña: panel con las reglas de hoy y la misma llamada (PUT con nuevaContrasena)', async () => {
    cargar(375);
    boton(fila(2), 'Restablecer contraseña').click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('dialog.pn-panel')!;
    expect(texto(panel.querySelector('.pn-panel__titulo'))).toBe('Restablecer contraseña');
    expect(texto(panel.querySelector('.pn-panel__descripcion'))).toBe('Cuenta de Ana Prueba');
    const reglas = () => Array.from(panel.querySelectorAll('.conf__regla')).map((r) => [texto(r.querySelector('span')), r.getAttribute('data-cumple')]);
    expect(reglas().map((r) => r[0])).toEqual([
      'Mínimo 8 caracteres', 'Al menos 1 mayúscula', 'Al menos 1 minúscula', 'Al menos 1 número', 'Coinciden',
    ]);
    escribir(panel.querySelector<HTMLInputElement>('#conf-nueva')!, 'Abcdefg1');
    escribir(panel.querySelector<HTMLInputElement>('#conf-confirmar')!, 'Abcdefg2');
    expect(reglas().map((r) => r[1])).toEqual(['true', 'true', 'true', 'true', 'false']);
    boton(panel, 'Guardar contraseña').click();
    fixture.detectChanges();
    expect(texto(panel.querySelector('#conf-contrasena-error'))).toBe('Las contraseñas no coinciden');
    expect(peticiones(contrasena(2), 'PUT').length).toBe(0);
    escribir(panel.querySelector<HTMLInputElement>('#conf-confirmar')!, 'Abcdefg1');
    const mostrar = panel.querySelector<HTMLInputElement>('#conf-mostrar')!;
    marcar(mostrar);
    expect(panel.querySelector<HTMLInputElement>('#conf-nueva')!.type).toBe('text');
    boton(panel, 'Guardar contraseña').click();
    fixture.detectChanges();
    const [put] = peticiones(contrasena(2), 'PUT');
    expect(put.request.body).toEqual({ nuevaContrasena: 'Abcdefg1' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({});
    await esperar();
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Contraseña actualizada correctamente.' }]);
  });

  it('restablecer contraseña: el error del servidor queda en el panel; una cuenta de Google solo avisa', async () => {
    cargar(375);
    boton(fila(2), 'Restablecer contraseña').click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('dialog.pn-panel')!;
    escribir(panel.querySelector<HTMLInputElement>('#conf-nueva')!, 'Abcdefg1');
    escribir(panel.querySelector<HTMLInputElement>('#conf-confirmar')!, 'Abcdefg1');
    boton(panel, 'Guardar contraseña').click();
    fixture.detectChanges();
    peticiones(contrasena(2), 'PUT')[0].flush(null, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(texto(el().querySelector('#conf-contrasena-error'))).toBe('No tienes permisos para restablecer contraseñas.');
    boton(el().querySelector('dialog.pn-panel')!, 'Cancelar').click();
    fixture.detectChanges();
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    const google = boton(fila(5), 'Restablecer contraseña');
    expect(google.getAttribute('aria-disabled')).toBe('true');
    google.click();
    fixture.detectChanges();
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'info', texto: 'Esta cuenta usa Google. El restablecimiento manual no aplica.' }]);
  });

  it('a 768 el índice solo apunta con aria-controls al panel que existe (el de la pestaña elegida)', () => {
    cargar(768);
    const tabs = Array.from(el().querySelectorAll<HTMLElement>('[role="tab"]'));
    for (const t of tabs) {
      const destino = t.getAttribute('aria-controls');
      if (t.getAttribute('aria-selected') === 'true') {
        expect(el().querySelector(`#${destino}`)).not.toBeNull();
      } else {
        expect(destino).toBeNull();
      }
    }
  });

  it('restablecer contraseña: un error del servidor cierra el SweetAlert2 del interceptor (swal-error-http) y se ve en el panel', async () => {
    const router = TestBed.inject(Router);
    router.resetConfig([{ path: 'configuracion', canMatch: [estilosPanelGuard], component: PantallaNueva }]);
    await router.navigateByUrl('/configuracion');
    cargar(375);
    boton(fila(2), 'Restablecer contraseña').click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('dialog.pn-panel')!;
    escribir(panel.querySelector<HTMLInputElement>('#conf-nueva')!, 'Abcdefg1');
    escribir(panel.querySelector<HTMLInputElement>('#conf-confirmar')!, 'Abcdefg1');
    boton(panel, 'Guardar contraseña').click();
    fixture.detectChanges();
    void Swal.fire({ title: 'Error', showClass: { popup: '' }, hideClass: { popup: '' }, customClass: { popup: CLASE_ERROR_HTTP } });
    expect(Swal.isVisible()).toBeTrue();
    peticiones(contrasena(2), 'PUT')[0].flush({ mensaje: 'Contraseña rechazada.' }, { status: 400, statusText: 'Bad Request' });
    await esperar();
    expect(Swal.isVisible()).toBeFalse();
    expect(texto(el().querySelector('#conf-contrasena-error'))).toBe('Contraseña rechazada.');
  });

  it('restablecer contraseña: si no cumple las reglas (mayúsculas, minúsculas, números, mínimo 8) no se envía y se ve el mensaje', () => {
    cargar(375);
    boton(fila(2), 'Restablecer contraseña').click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('dialog.pn-panel')!;
    for (const mala of ['abcdefgh1', 'ABCDEFGH1', 'Abcdefgh', 'Abc1']) {
      escribir(panel.querySelector<HTMLInputElement>('#conf-nueva')!, mala);
      escribir(panel.querySelector<HTMLInputElement>('#conf-confirmar')!, mala);
      boton(panel, 'Guardar contraseña').click();
      fixture.detectChanges();
      expect(texto(panel.querySelector('#conf-contrasena-error'))).toBe('La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).');
      expect(peticiones(contrasena(2), 'PUT').length).toBe(0);
    }
    expect(el().querySelector('dialog.pn-panel')).not.toBeNull();
  });

  it('a 375 sin desplazamiento horizontal', () => {
    cargar(375);
    const host = el();
    document.body.appendChild(host);
    host.style.display = 'block';
    host.style.width = '375px';
    fixture.detectChanges();
    const pagina = host.querySelector<HTMLElement>('.conf__pagina')!;
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
  });
});
