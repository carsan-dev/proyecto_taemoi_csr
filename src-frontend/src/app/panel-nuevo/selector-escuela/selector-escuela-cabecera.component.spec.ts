import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { escuela } from './escuelas-prueba';
import { SelectorEscuelaCabeceraComponent } from './selector-escuela-cabecera.component';
import { EscuelaUsuario, IR_A_URL, RUTA_ENTRADA_SESION } from './selector-escuela.service';

const URL_CAMBIO = `${environment.apiUrl}/auth/organizacion`;

@Component({
  standalone: true,
  imports: [SelectorEscuelaCabeceraComponent],
  template: `
    <div class="pn">
      <pn-selector-escuela [escuelas]="escuelas()">
        <span class="nombre-proyectado">Escuela Ficticia Norte</span>
      </pn-selector-escuela>
    </div>
  `,
})
class AnfitrionComponent {
  readonly escuelas = signal<EscuelaUsuario[]>([]);
}

describe('SelectorEscuelaCabeceraComponent (en el panel)', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;
  let http: HttpTestingController;
  let irA: jasmine.Spy;
  let avisos: jasmine.SpyObj<AvisosService>;

  const NORTE = escuela({ id: 1, nombre: 'Escuela Ficticia Norte', activa: true, roles: ['ROLE_ADMIN'] });
  const SUR = escuela({
    id: 2,
    nombre: 'Club Ficticio Sur',
    roles: ['ROLE_MANAGER'],
    numeroSedes: 6,
    logo: 'SUBIDO',
    logoVersion: 9,
  });
  const ESTE = escuela({ id: 3, nombre: 'Escuela del Este', roles: ['ROLE_USER', 'ROLE_MANAGER'], numeroSedes: 2 });

  async function montar(escuelas: EscuelaUsuario[] = [SUR, NORTE, ESTE]): Promise<void> {
    irA = jasmine.createSpy('irA');
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['error']);
    await TestBed.configureTestingModule({
      imports: [AnfitrionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthenticationService, useValue: { emailCambio: new BehaviorSubject('a@example.test') } },
        { provide: IR_A_URL, useValue: irA },
        { provide: AvisosService, useValue: avisos },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnfitrionComponent);
    fixture.componentInstance.escuelas.set(escuelas);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('#pn-escuela-boton')!;
  const menu = (): HTMLElement | null => el().querySelector<HTMLElement>('#pn-menu-escuela');
  const items = (): HTMLButtonElement[] =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('#pn-menu-escuela [role="menuitemradio"]'));
  const abrir = (): void => {
    boton().click();
    fixture.detectChanges();
  };
  const tecla = (destino: HTMLElement, key: string): void => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  afterEach(() => http.verify());

  it('el nombre de la escuela es un botón que abre el menú «Cambiar de escuela»', async () => {
    await montar();
    expect(boton().textContent).toContain('Escuela Ficticia Norte');
    expect(boton().getAttribute('aria-haspopup')).toBe('menu');
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(menu()).toBeNull();

    abrir();
    expect(boton().getAttribute('aria-expanded')).toBe('true');
    expect(menu()?.getAttribute('role')).toBe('menu');
    expect(el().querySelector('#pn-escuela-titulo')?.textContent?.trim()).toBe('Cambiar de escuela');
  });

  it('lista las escuelas en el orden de la API con su rol, sus sedes y la actual marcada', async () => {
    await montar();
    abrir();
    const lista = items();
    expect(lista.map((b) => b.querySelector('.pn-selector__nombre')?.textContent?.trim())).toEqual([
      'Club Ficticio Sur',
      'Escuela Ficticia Norte',
      'Escuela del Este',
    ]);
    expect(lista.map((b) => b.querySelector('.pn-selector__meta')?.textContent?.trim())).toEqual([
      'Gestión · 6 sedes',
      'Administración',
      'Gestión y familia · 2 sedes',
    ]);
    expect(lista.map((b) => b.getAttribute('aria-checked'))).toEqual(['false', 'true', 'false']);
    // Logo subido de una escuela que no es la activa: su ruta propia; sin logo, las iniciales.
    expect(lista[0].querySelector('img')?.getAttribute('src')).toBe(
      `${environment.apiUrl}/auth/organizaciones/2/logo?v=9`
    );
    expect(lista[1].querySelector('.pn-logo-escuela__iniciales')?.textContent?.trim()).toBe('EF');
    // Al abrir, el foco va a la actual.
    expect(document.activeElement).toBe(lista[1]);
  });

  it('pide al anfitrión ponerse encima mientras el menú está abierto', async () => {
    await montar();
    const host = el().querySelector('pn-selector-escuela')!;
    expect(host.classList).not.toContain('pn-selector--encima');
    abrir();
    expect(host.classList).toContain('pn-selector--encima');
  });

  it('flechas, Inicio y Fin mueven el foco; Esc cierra y devuelve el foco al botón', async () => {
    await montar();
    abrir();
    const lista = items();
    tecla(lista[1], 'ArrowDown');
    expect(document.activeElement).toBe(lista[2]);
    tecla(lista[2], 'ArrowDown');
    expect(document.activeElement).toBe(lista[0]);
    tecla(lista[0], 'End');
    expect(document.activeElement).toBe(lista[2]);
    tecla(lista[2], 'Home');
    expect(document.activeElement).toBe(lista[0]);
    tecla(lista[0], 'Escape');
    expect(menu()).toBeNull();
    expect(document.activeElement).toBe(boton());
  });

  it('un clic fuera cierra el menú', async () => {
    await montar();
    abrir();
    document.body.click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
  });

  it('elegir la escuela actual solo cierra el menú', async () => {
    await montar();
    abrir();
    items()[1].click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
    http.expectNone(URL_CAMBIO);
    expect(irA).not.toHaveBeenCalled();
  });

  it('elegir otra tapa la pantalla con «Entrando en…», cambia la escuela y recarga la app', async () => {
    await montar();
    abrir();
    items()[0].click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
    expect(el().querySelector('pn-entrando-escuela')?.textContent).toContain('Entrando en Club Ficticio Sur…');

    const peticion = http.expectOne(URL_CAMBIO);
    expect(peticion.request.body).toEqual({ organizacionId: 2 });
    peticion.flush({ token: 'nuevo' });
    fixture.detectChanges();
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    // Sigue tapada hasta que la página se recarga.
    expect(el().querySelector('pn-entrando-escuela')).not.toBeNull();
  });

  it('un doble clic no lanza dos cambios', async () => {
    await montar();
    const componente = fixture.debugElement.query(By.directive(SelectorEscuelaCabeceraComponent))
      .componentInstance as SelectorEscuelaCabeceraComponent;
    componente.elegir(SUR);
    componente.elegir(SUR);
    componente.elegir(ESTE);
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledTimes(1);
  });

  it('si el cambio falla, la escuela no cambia: quita la pantalla de espera y avisa con «Reintentar»', async () => {
    await montar();
    abrir();
    items()[0].click();
    fixture.detectChanges();
    http.expectOne(URL_CAMBIO).flush({ mensaje: 'Acceso denegado.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(el().querySelector('pn-entrando-escuela')).toBeNull();
    expect(irA).not.toHaveBeenCalled();
    expect(avisos.error).toHaveBeenCalledTimes(1);
    const [texto, accion] = avisos.error.calls.mostRecent().args;
    expect(texto).toBe('No se ha podido cambiar de escuela. Inténtalo otra vez.');
    expect(accion?.texto).toBe('Reintentar');
    expect(document.activeElement).toBe(boton());

    accion!.alPulsar();
    fixture.detectChanges();
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
  });
});
