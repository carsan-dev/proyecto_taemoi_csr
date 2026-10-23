import { Component } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Location } from '@angular/common';
import { Router, RouterOutlet, Routes, provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { EstilosPanelService } from './panel-nuevo/estilos-panel.service';
import { CatalogoService } from './features/catalogo/catalogo.service';
import { SkeletonComponent } from './componentes/vistas/layout/skeleton/skeleton.component';

/**
 * Arranque en frío en una pantalla del panel con la sesión viva (pestaña restaurada): si la PRIMERA
 * `GET /api/auth/roles` falla sin llegar al servidor (estado 0), la web se queda en la pantalla pedida
 * en vez de acabar en la portada. Rutas reales con las pantallas cambiadas por stubs.
 */
@Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
class PantallaHoy {}
@Component({ standalone: true, template: '<p class="nueva">nueva</p>' })
class PantallaNueva {}
@Component({
  standalone: true,
  imports: [SkeletonComponent, RouterOutlet],
  template: '<app-skeleton><router-outlet></router-outlet></app-skeleton>',
})
class Anfitrion {}

const RUTAS: Routes = routes.map((r) =>
  r.loadComponent ? { ...r, loadComponent: () => Promise.resolve(r.canMatch ? PantallaNueva : PantallaHoy) } : r
);

function respuesta(url: string, disenoNuevo: boolean): unknown {
  if (url.endsWith('/auth/auth-status')) return true;
  if (url.endsWith('/auth/permisos')) {
    return {
      roles: ['PROPIETARIO'], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
      capacidades: [], disenoNuevo: { activo: disenoNuevo, permitido: true }, nombreVisible: 'X',
    };
  }
  if (url.endsWith('/auth/roles')) return ['ROLE_ADMIN'];
  if (url.endsWith('/auth/user')) return { email: 'a@b.c' };
  if (url.endsWith('/auth/plataforma')) return { adminPlataforma: false };
  if (url.includes('/solicitudes/contador')) return { total: 0 };
  if (url.includes('/escuela/marca')) return { nombre: 'E', tema: { primario: '#112233', sobrePrimario: '#FFFFFF' } };
  return {};
}

describe('Arranque en una pantalla del panel con un fallo de red en el primer /roles', () => {
  afterEach(() => {
    localStorage.removeItem('panelNuevo');
    localStorage.removeItem('cookieConsent');
    sessionStorage.clear();
  });

  /** Se llama dentro de `fakeAsync`. */
  function arrancar(disenoNuevo: boolean): string {
    if (disenoNuevo) {
      localStorage.setItem('panelNuevo', '1');
    } else {
      localStorage.removeItem('panelNuevo');
    }
    sessionStorage.clear();
    // Pestaña restaurada: la portada ya hizo su reenvío una vez (decisión 266) y no lo repite.
    sessionStorage.setItem('escaparate-auth-autoredirect-done', 'true');
    localStorage.setItem('cookieConsent', 'rejected');
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [
        provideRouter(RUTAS),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
        { provide: CatalogoService, useValue: { cargar: () => of(void 0), cargado: () => true, notificarFallo: () => {} } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    TestBed.inject(Location).go('/convocatoriasListar');
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    router.initialNavigation();

    const prioridad = ['permisos', 'auth-status', '/roles'];
    let rolesFallados = 0;
    let rolesServidos = 0;
    let quietas = 0;
    // Reloj simulado: cada vuelta avanza 10 ms y el reintento de /roles (500 ms) llega hacia la vuelta 50. Con tiempo
    // real, un parón del navegador en CI disparaba el timeout de Jasmine y dejaba el bucle vivo tras el test.
    // Se para cuando /roles ya respondió bien y lleva 15 vueltas sin nada abierto.
    for (let i = 0; i < 400 && quietas < 15; i++) {
      const abiertas = ((http as any).open as TestRequest[]).filter((t) => !t.cancelled);
      if (abiertas.length) {
        quietas = 0;
        abiertas.sort((a, b) => {
          const pa = prioridad.findIndex((p) => a.request.url.includes(p));
          const pb = prioridad.findIndex((p) => b.request.url.includes(p));
          return (pa < 0 ? 99 : pa) - (pb < 0 ? 99 : pb);
        });
        const r = abiertas[0];
        if (r.request.url.endsWith('/auth/roles') && rolesFallados === 0) {
          rolesFallados++;
          r.error(new ProgressEvent('error'));
        } else {
          if (r.request.url.endsWith('/auth/roles')) rolesServidos++;
          r.flush(respuesta(r.request.url, disenoNuevo) as object);
        }
      } else if (rolesServidos > 0) {
        quietas++;
      }
      fixture.detectChanges();
      tick(10);
      fixture.detectChanges();
    }
    expect(quietas).withContext('quedan peticiones abriéndose sin parar').toBe(15);
    expect(rolesFallados).toBe(1);
    expect(rolesServidos).toBeGreaterThan(0);
    return router.url;
  }

  it('con el diseño nuevo se queda en la pantalla pedida', fakeAsync(() => {
    expect(arrancar(true)).toBe('/convocatoriasListar');
  }));

  it('con el diseño de hoy se queda en la pantalla pedida', fakeAsync(() => {
    expect(arrancar(false)).toBe('/convocatoriasListar');
  }));
});
