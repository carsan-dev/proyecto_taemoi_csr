import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { PermisosSesionService } from '../../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { EstilosPanelService } from '../../estilos-panel.service';
import { ConfiguracionPanelComponent } from './configuracion.component';

const API = environment.apiUrl;
const LIMITE = `${API}/turnos/limite`;
const GUARDAR_LIMITE = `${API}/admin/configuracion/limite-turno`;

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
  const valores = (): string =>
    Array.from(el().querySelectorAll('.conf__valores > div')).map((d) => `${texto(d.querySelector('dt'))}: ${texto(d.querySelector('dd'))}`).join(' | ');

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

  function cargar(ancho = 375, limite = 36): void {
    crear(ancho);
    peticiones(LIMITE).forEach((r) => r.flush(limite));
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
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
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
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lecturas y distribuciones

  it('al entrar pide solo el límite (sin la lectura de cuentas) con ERROR_HANDLED_LOCALLY', () => {
    crear(375);
    const [l] = peticiones(LIMITE);
    expect(l.request.urlWithParams).toBe(LIMITE);
    expect(l.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    l.flush(36);
    http.verify();
    expect(texto(el().querySelector('h1'))).toBe('Configuración');
  });

  it('a 375 (P1): solo «Alumnos por turno», sin índice; «Avisos legales y cookies» queda en el menú de la cuenta', () => {
    cargar(375);
    expect(el().querySelector('[role="tablist"]')).toBeNull();
    const secciones = Array.from(el().querySelectorAll('.conf__seccion')).map((s) => texto(s.querySelector('h2')));
    expect(secciones).toEqual(['Alumnos por turno']);
  });

  it('desde 768 (P2): índice con dos secciones, Alumnos por turno y Avisos legales y cookies, y sin rastro de roles', () => {
    cargar(768);
    const tabs = Array.from(el().querySelectorAll('[role="tab"]'));
    expect(tabs.map((t) => [texto(t), t.getAttribute('aria-selected')])).toEqual([
      ['Alumnos por turno', 'true'], ['Avisos legales y cookies', 'false'],
    ]);
    expect(el().querySelector('[role="tablist"]')!.getAttribute('aria-orientation')).toBe('vertical');
    const visibles = () => Array.from(el().querySelectorAll('.conf__seccion')).map((s) => texto(s.querySelector('h2')));
    expect(visibles()).toEqual(['Alumnos por turno']);
    elegirSeccion('legal');
    expect(visibles()).toEqual(['Avisos legales y cookies']);
    const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('.conf__legales a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toEqual(['/politica-privacidad', '/politica-cookies', '/aviso-legal']);
    expect(el().textContent).not.toMatch(/Roles y permisos|Restablecer contraseña/);
    expect(el().querySelector('#conf-tab-roles')).toBeNull();
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
    expect(document.activeElement?.id).toBe('conf-tab-legal');
    expect(el().querySelector('#conf-tab-legal')!.getAttribute('aria-selected')).toBe('true');
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement?.id).toBe('conf-tab-turno');
    expect(router.url).toBe(url);
  });

  it('las mismas acciones del límite a 375 y a 768', () => {
    const acciones = (): string[] => {
      const botones = Array.from(el().querySelectorAll<HTMLButtonElement>('.conf__seccion button')).map((b) => texto(b));
      const campos = Array.from(el().querySelectorAll<HTMLInputElement>('.conf__seccion input')).map((c) => c.id);
      return [...new Set([...botones, ...campos])].sort();
    };
    cargar(375);
    const enMovil = acciones();
    fixture.destroy();
    (window.matchMedia as jasmine.Spy).and.callFake(consultaDeAncho(768));
    fixture = TestBed.createComponent(ConfiguracionPanelComponent);
    fixture.detectChanges();
    peticiones(LIMITE).forEach((r) => r.flush(36));
    fixture.detectChanges();
    expect(el().querySelector('[role="tablist"]')).not.toBeNull();
    expect(acciones()).toEqual(enMovil);
    expect(enMovil).toContain('Guardar cambios');
    expect(enMovil).toContain('Restaurar');
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

  it('límite: la ayuda explica el aforo de sede y se une al campo, con y sin error', () => {
    cargar(375);
    const campo = el().querySelector<HTMLInputElement>('#conf-limite')!;
    expect(texto(el().querySelector('#conf-limite-ayuda'))).toBe('Es el límite común de la escuela. Si una sede tiene su propio aforo, sus turnos usan el de la sede.');
    expect(campo.getAttribute('aria-describedby')).toBe('conf-limite-ayuda');
    escribir(campo, '0');
    expect(campo.getAttribute('aria-describedby')).toBe('conf-limite-ayuda conf-limite-error');
    expect(el().querySelector('#conf-limite-error')).not.toBeNull();
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
