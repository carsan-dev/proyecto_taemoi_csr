import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { SedeVisible } from '../../../servicios/authentication/permisos-sesion.service';
import { CambiarSedePanelComponent, efectosDelCambio } from './cambiar-sede-panel.component';
import { CambioSede } from './cambio-sede.service';

const SEDES: SedeVisible[] = [
  { id: 1, nombre: 'Mollina', activa: true },
  { id: 2, nombre: 'Estepa', activa: true },
  { id: 3, nombre: 'Herrera', activa: true },
  { id: 4, nombre: 'Cerrada', activa: false },
];

function lectura(extra: Partial<CambioSede> = {}): CambioSede {
  return {
    sedeActualId: 1, sedeActualNombre: 'Mollina', sedeNuevaId: 2, sedeNuevaNombre: 'Estepa',
    cargosPendientes: 3, importePendiente: 75, cargosCobrados: 9, importeCobrado: 225, gruposEnSedeActual: 1, ...extra,
  };
}

describe('Panel «Cambiar de sede» (E9-1)', () => {
  let fixture: ComponentFixture<CambiarSedePanelComponent>;
  let http: HttpTestingController;
  const api = environment.apiUrl;
  let cambiado: string[];
  let cerrado: number;

  function montar(entrada: { sedeActualId: number | null; sedeActualNombre: string | null } = { sedeActualId: 1, sedeActualNombre: 'Mollina' }): void {
    fixture = TestBed.createComponent(CambiarSedePanelComponent);
    fixture.componentRef.setInput('alumnoId', 7);
    fixture.componentRef.setInput('sedeActualId', entrada.sedeActualId);
    fixture.componentRef.setInput('sedeActualNombre', entrada.sedeActualNombre);
    fixture.componentRef.setInput('sedes', SEDES);
    cambiado = [];
    cerrado = 0;
    fixture.componentInstance.cambiado.subscribe((n) => cambiado.push(n));
    fixture.componentInstance.cerrado.subscribe(() => cerrado++);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  }

  const host = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => (host().querySelector(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const confirmar = () => Array.from(host().querySelectorAll<HTMLButtonElement>('.cambiar-sede__pie button')).at(-1)!;

  function elegir(nombre: string): void {
    const select = host().querySelector<HTMLSelectElement>('#cambiar-sede-nueva')!;
    select.selectedIndex = Array.from(select.options).findIndex((o) => o.textContent === nombre);
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function responderLectura(cuerpo: CambioSede, sedeId = 2): void {
    const req = http.expectOne((r) => r.url === `${api}/alumnos/7/cambio-sede`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('sedeId')).toBe(String(sedeId));
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush(cuerpo);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CambiarSedePanelComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('antes de elegir enseña solo la sede actual, ofrece las activas sin la actual y no deja confirmar', () => {
    montar();
    expect(texto('[data-ruta]')).toBe('Mollina');
    expect(texto('label')).toContain('Sede nueva (obligatorio)');
    const opciones = Array.from(host().querySelectorAll('#cambiar-sede-nueva option')).map((o) => o.textContent);
    expect(opciones).toEqual(['Elige una sede', 'Estepa', 'Herrera']);
    expect(host().querySelector('[data-efecto]')).toBeNull();
    expect(confirmar().disabled).toBeTrue();
    expect(confirmar().textContent).toContain('Cambiar de sede');
  });

  it('al elegir pide la lectura previa, la enseña y el botón nombra la sede nueva', () => {
    montar();
    elegir('Estepa');
    expect(texto('[role="status"]')).toContain('Calculando');
    expect(confirmar().disabled).toBeTrue();
    responderLectura(lectura());
    expect(texto('[data-ruta]')).toBe('Mollina a Estepa');
    const efecto = (clave: string) => `${texto(`[data-efecto="${clave}"] b`)} | ${texto(`[data-efecto="${clave}"] small`)}`;
    expect(efecto('pendientes')).toBe('3 cargos sin cobrar pasan a Estepa | 75,00 €. Se cobrarán en la caja de Estepa.');
    expect(efecto('cobrados')).toBe('9 cargos cobrados se quedan en Mollina | 225,00 €. La caja de Mollina no cambia.');
    expect(efecto('grupos')).toBe('Sigue en 1 grupo de Mollina | Los grupos no cambian. Si deja de ir, cámbialo en Deportes.');
    expect(confirmar().disabled).toBeFalse();
    expect(confirmar().textContent?.trim()).toBe('Cambiar a Estepa');
  });

  it('singular, plural y cero, con importes es-ES', () => {
    montar();
    const uno = efectosDelCambio(lectura({ cargosPendientes: 1, importePendiente: 1234.5, cargosCobrados: 1, importeCobrado: 10, gruposEnSedeActual: 2 }));
    expect(uno.map((e) => e.titulo)).toEqual([
      '1 cargo sin cobrar pasa a Estepa', '1 cargo cobrado se queda en Mollina', 'Sigue en 2 grupos de Mollina']);
    expect(uno[0].detalle.replace(/\s/g, ' ')).toBe('1.234,50 €. Se cobrará en la caja de Estepa.');
    const cero = efectosDelCambio(lectura({ cargosPendientes: 0, importePendiente: 0, cargosCobrados: 0, importeCobrado: 0, gruposEnSedeActual: 0 }));
    expect(cero).toEqual([{ clave: 'pendientes', titulo: 'No hay cargos sin cobrar que mover', detalle: 'Nada pasa a Estepa.' }]);
  });

  it('un alumno sin sede: «Sin sede → Estepa», sin cobrados ni grupos', () => {
    montar({ sedeActualId: null, sedeActualNombre: null });
    expect(texto('[data-ruta]')).toBe('Sin sede');
    expect(Array.from(host().querySelectorAll('#cambiar-sede-nueva option')).map((o) => o.textContent)).toEqual(['Elige una sede', 'Mollina', 'Estepa', 'Herrera']);
    elegir('Estepa');
    responderLectura(lectura({ sedeActualId: null, sedeActualNombre: null, cargosCobrados: 4, importeCobrado: 40, gruposEnSedeActual: 0 }));
    expect(texto('[data-ruta]')).toBe('Sin sede a Estepa');
    expect(host().querySelector('[data-efecto="cobrados"]')).toBeNull();
    expect(host().querySelector('[data-efecto="grupos"]')).toBeNull();
    expect(host().querySelector('[data-efecto="pendientes"]')).not.toBeNull();
  });

  it('si la lectura falla enseña el error con «Reintentar», sin poder confirmar, y reintentar vuelve a pedirla', () => {
    montar();
    elegir('Estepa');
    http.expectOne((r) => r.url === `${api}/alumnos/7/cambio-sede`).flush({ mensaje: 'Alumno anonimizado' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(texto('.cambiar-sede__error')).toContain('Alumno anonimizado');
    expect(confirmar().disabled).toBeTrue();
    host().querySelector<HTMLButtonElement>('.cambiar-sede__reintentar')!.click();
    fixture.detectChanges();
    responderLectura(lectura());
    expect(host().querySelector('.cambiar-sede__error')).toBeNull();
    expect(confirmar().disabled).toBeFalse();
  });

  it('cambiar de sede elegida descarta la lectura anterior', () => {
    montar();
    elegir('Estepa');
    const primera = http.expectOne((r) => r.url === `${api}/alumnos/7/cambio-sede`);
    elegir('Herrera');
    expect(primera.cancelled).toBeTrue();
    responderLectura(lectura({ sedeNuevaId: 3, sedeNuevaNombre: 'Herrera' }), 3);
    expect(confirmar().textContent?.trim()).toBe('Cambiar a Herrera');
  });

  it('confirma con PUT {sedeId}, bloquea el doble envío, y al terminar emite el nombre', () => {
    montar();
    elegir('Estepa');
    responderLectura(lectura());
    confirmar().click();
    confirmar().click();
    fixture.detectChanges();
    expect(confirmar().getAttribute('aria-busy')).toBe('true');
    expect(host().querySelector<HTMLButtonElement>('.cambiar-sede__cancelar')!.disabled).toBeTrue();
    expect(host().querySelector<HTMLButtonElement>('.pn-panel__cerrar')!.disabled).toBeTrue();
    const req: TestRequest = http.expectOne(`${api}/alumnos/7/sede`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ sedeId: 2 });
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush(lectura());
    expect(cambiado).toEqual(['Estepa']);
  });

  it('un error del servidor al confirmar se queda en el panel, sin cerrar, y deja reintentar', () => {
    montar();
    elegir('Estepa');
    responderLectura(lectura());
    confirmar().click();
    http.expectOne(`${api}/alumnos/7/sede`).flush({ mensaje: 'La sede está inactiva' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(texto('#cambiar-sede-error')).toContain('La sede está inactiva');
    expect(cambiado).toEqual([]);
    expect(cerrado).toBe(0);
    expect(host().querySelector('dialog')!.open).toBeTrue();
    expect(confirmar().disabled).toBeFalse();
    confirmar().click();
    http.expectOne(`${api}/alumnos/7/sede`).flush(lectura());
    expect(cambiado).toEqual(['Estepa']);
  });
});
