import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router, Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { esWebPlantilla } from './guards/web-plantilla.guard';
import { gestionPanelGuard } from './panel-nuevo/no-encontrada/gestion-panel.guard';
import { ponerMetaWebPlantilla, quedaMetaWebPlantilla } from './testing/meta-web-plantilla';
import { EventoLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/evento/evento.component';
import { EventosLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/eventos/eventos.component';
import { LegalesLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/legales/legales.component';
import { PreinscripcionLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/preinscripcion/preinscripcion.component';
import { SedeLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/sede/sede.component';
import { TarifasLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/tarifas/tarifas.component';
import { NoEncontradaLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/no-encontrada/no-encontrada.component';
import { PortadaLineaComarcalComponent } from './web-plantilla/temas/linea-comarcal/portada/portada.component';
import { RUTAS_SOLO_MOISKIMDO } from './web-plantilla/web-plantilla.routes';

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

/** Las páginas de la plantilla (fase4-web-plan.md §3.1), en orden. */
const PAGINAS_PLANTILLA = ['', 'sedes/:slug', 'preinscripcion', 'tarifas', 'eventos', 'eventos/:id', 'legales'];
/** Las páginas del primer tema (W5 y W6). */
const PAGINAS_DEL_TEMA: Record<string, Type<unknown>> = {
  '': PortadaLineaComarcalComponent,
  'sedes/:slug': SedeLineaComarcalComponent,
  preinscripcion: PreinscripcionLineaComarcalComponent,
  tarifas: TarifasLineaComarcalComponent,
  eventos: EventosLineaComarcalComponent,
  'eventos/:id': EventoLineaComarcalComponent,
  legales: LegalesLineaComarcalComponent,
};

/** Clave de prueba en `data` con el índice de la entrada original de `app.routes`. */
const INDICE = 'indiceEnAppRoutes';

const grupoPlantilla = (): Route => routes.find((r) => r.canMatch?.includes(esWebPlantilla) && r.path === '')!;
const comodinPlantilla = (): Route => routes.find((r) => r.canMatch?.includes(esWebPlantilla) && r.path === '**')!;

/** Redirecciones de rutas de Moiskimdo con equivalente claro en la plantilla (§3.2 punto 5). */
const REDIRECCIONES: [string, string][] = [
  ['/inicio', '/'],
  ['/aviso-legal', '/legales#aviso-legal'],
  ['/politica-privacidad', '/legales#privacidad'],
  ['/politica-cookies', '/legales#cookies'],
  ['/contacto', '/#contacto'],
];

/**
 * Armazón de la web por plantilla (fase4-web-plan.md §3.2 y §6, «Armazón»): el grupo con `canMatch` va delante de las
 * rutas públicas; sin la meta no casa (Moiskimdo igual que hoy) y con ella no se queda el panel, el login ni nada ajeno.
 */
describe('app.routes · grupo de la web por plantilla', () => {
  let quitarMeta: (() => void) | null = null;

  afterEach(() => {
    quitarMeta?.();
    quitarMeta = null;
    expect(quedaMetaWebPlantilla()).withContext('la meta no se queda').toBeFalse();
  });

  it('un único grupo, el primero de la tabla, con path vacío, canMatch esWebPlantilla y carga perezosa (sin hijas en línea)', () => {
    const grupos = routes.filter((r) => r.canMatch?.includes(esWebPlantilla) && r.path === '');
    expect(grupos.length).toBe(1);
    const grupo = grupos[0];
    expect(routes.indexOf(grupo)).toBe(0);
    expect(routes.indexOf(grupo)).toBeLessThan(routes.findIndex((r) => r.path === '' && r.component === EscaparatePrincipalComponent));
    expect(grupo.path).toBe('');
    expect(grupo.canMatch).toEqual([esWebPlantilla]);
    expect(grupo.loadChildren).toBeDefined();
    expect(grupo.children).toBeUndefined();
    expect(grupo.component).toBeUndefined();
    expect(grupo.loadComponent).toBeUndefined();
    expect(grupo.redirectTo).toBeUndefined();
  });

  it('las hijas: las siete páginas del tema, sin comodín', async () => {
    const hijas = (await (grupoPlantilla().loadChildren as () => Promise<Routes>)()) as Routes;
    const paginas = hijas.slice(0, PAGINAS_PLANTILLA.length);
    expect(paginas.map((r) => r.path)).toEqual(PAGINAS_PLANTILLA);
    expect(hijas.some((r) => r.path === '**')).toBeFalse();
    for (const hija of paginas) {
      expect(hija.data?.['webPlantilla']).withContext(hija.path!).toBeTrue();
      expect(hija.canMatch).withContext(hija.path!).toBeUndefined();
      expect(await hija.loadComponent!())
        .withContext(hija.path!)
        .toBe(PAGINAS_DEL_TEMA[hija.path!]);
    }
    expect(hijas[0].pathMatch).toBe('full');
  });

  it('las hijas: las rutas solo de Moiskimdo, redirigidas a su equivalente o a la 404 del tema', async () => {
    const hijas = (await (grupoPlantilla().loadChildren as () => Promise<Routes>)()) as Routes;
    const resto = hijas.slice(PAGINAS_PLANTILLA.length);
    expect(resto.filter((r) => r.redirectTo !== undefined).map((r) => r.path)).toEqual([
      'inicio',
      'aviso-legal',
      'politica-privacidad',
      'politica-cookies',
      'contacto',
    ]);
    const del404 = resto.filter((r) => r.redirectTo === undefined);
    expect(del404.map((r) => r.path)).toEqual([...RUTAS_SOLO_MOISKIMDO]);
    for (const hija of del404) {
      expect(hija.data?.['webPlantilla']).withContext(hija.path!).toBeTrue();
      expect(await hija.loadComponent!()).withContext(hija.path!).toBe(NoEncontradaLineaComarcalComponent);
    }
    // Cada ruta del bloque público de Moiskimdo (entre el grupo de la plantilla y el login) tiene su entrada aquí.
    const publicasMoiskimdo = routes
      .slice(1, routes.findIndex((r) => r.path === 'login'))
      .map((r) => r.path!.split('/')[0])
      .filter((p) => p !== '');
    const cubiertas = new Set(hijas.map((r) => r.path!.split('/')[0]));
    expect(publicasMoiskimdo.length).toBeGreaterThan(10);
    expect(publicasMoiskimdo.filter((p) => !cubiertas.has(p))).toEqual([]);
  });

  it('comodín de la plantilla: el penúltimo, con canMatch esWebPlantilla, data.webPlantilla y la 404 del tema, perezoso', async () => {
    const comodines = routes.filter((r) => r.path === '**');
    expect(comodines.length).toBe(3);
    const plantilla = comodinPlantilla();
    expect(routes.indexOf(plantilla)).toBe(routes.length - 2);
    expect(comodines[0].canMatch).toEqual([gestionPanelGuard]);
    expect(plantilla.canMatch).toEqual([esWebPlantilla]);
    expect(plantilla.data?.['webPlantilla']).toBeTrue();
    expect(plantilla.component).toBeUndefined();
    expect(await plantilla.loadComponent!()).toBe(NoEncontradaLineaComarcalComponent);
  });

  describe('al navegar (la tabla real, en su orden; cada pantalla ajena a la plantilla, una falsa sin guards)', () => {
    let cargarHijas: jasmine.Spy;

    async function montar(): Promise<RouterTestingHarness> {
      const grupo = grupoPlantilla();
      cargarHijas = jasmine.createSpy('loadChildren').and.callFake(grupo.loadChildren as () => Promise<Routes>);
      // El router no conserva la identidad de los objetos de la tabla: cada copia lleva el índice de su original.
      const tabla: Routes = routes.map((r, indice) => {
        const data = { ...r.data, [INDICE]: indice };
        if (r === grupo) {
          return { ...r, data, loadChildren: cargarHijas };
        }
        if (r.redirectTo !== undefined) {
          return { ...r };
        }
        // El 404 de la plantilla conserva su canMatch; el del panel, como sin sesión de gestión.
        const canMatch = r.canMatch?.includes(esWebPlantilla)
          ? r.canMatch
          : r.canMatch?.includes(gestionPanelGuard)
            ? [() => false]
            : undefined;
        return { path: r.path, pathMatch: r.pathMatch, data, canMatch, component: PantallaFalsaComponent };
      });
      // La pantalla de la plantilla pide sus datos (W4): HttpClient de pruebas, sin responder.
      TestBed.configureTestingModule({ providers: [provideRouter(tabla), provideHttpClient(), provideHttpClientTesting()] });
      return RouterTestingHarness.create();
    }

    function hoja(): ActivatedRouteSnapshot {
      let ruta = TestBed.inject(Router).routerState.snapshot.root;
      while (ruta.firstChild) {
        ruta = ruta.firstChild;
      }
      return ruta;
    }

    /** La entrada de `app.routes` que ha casado (para las de Moiskimdo, la original y no la copia falsa). */
    function entradaActiva(): Route | undefined {
      const indice = hoja().routeConfig?.data?.[INDICE] as number | undefined;
      return indice === undefined ? hoja().routeConfig ?? undefined : routes[indice];
    }

    const entrada = (path: string) => routes.find((r) => r.path === path && !r.canMatch?.includes(esWebPlantilla))!;

    it('sin la meta: la portada es EscaparatePrincipalComponent y el grupo ni se descarga', async () => {
      const harness = await montar();
      await harness.navigateByUrl('/');
      expect(entradaActiva()).toBe(entrada(''));
      expect(entradaActiva()!.component).toBe(EscaparatePrincipalComponent);
      expect(hoja().data['webPlantilla']).toBeUndefined();
      await harness.navigateByUrl('/tarifas');
      expect(entradaActiva()).toBe(entrada('tarifas'));
      await harness.navigateByUrl('/eventos/7');
      expect(entradaActiva()).toBe(entrada('eventos/:eventoId'));
      await harness.navigateByUrl('/legales');
      expect(entradaActiva()!.path).toBe('**');
      expect(cargarHijas).not.toHaveBeenCalled();
    });

    it('con la meta: cada página de la plantilla resuelve a la ruta del grupo (su página del tema y data.webPlantilla)', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      await harness.navigateByUrl('/');
      expect(hoja().component).toBe(PortadaLineaComarcalComponent);
      expect(hoja().data['webPlantilla']).toBeTrue();
      const componentes: Record<string, Type<unknown>> = {
        '/sedes/casariche': SedeLineaComarcalComponent,
        '/tarifas': TarifasLineaComarcalComponent,
        '/eventos': EventosLineaComarcalComponent,
        '/eventos/3': EventoLineaComarcalComponent,
        '/legales': LegalesLineaComarcalComponent,
        '/preinscripcion': PreinscripcionLineaComarcalComponent,
      };
      for (const url of ['/sedes/casariche', '/tarifas', '/eventos', '/eventos/3', '/legales', '/preinscripcion']) {
        await harness.navigateByUrl(url);
        expect(hoja().component).withContext(url).toBe(componentes[url]);
        expect(hoja().data['webPlantilla']).withContext(url).toBeTrue();
        expect(TestBed.inject(Router).url).withContext(url).toBe(url);
      }
    });

    it('con la meta: el login, el panel, la plataforma y el selector de escuela siguen en su ruta de siempre', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      for (const path of ['login', 'sedes', 'adminpage', 'alumnosListar', 'userpage', 'elegir-escuela', 'plataformaLogos', 'recuperar-contrasena']) {
        await harness.navigateByUrl(`/${path}`);
        expect(entradaActiva()).withContext(path).toBe(entrada(path));
        expect(hoja().data['webPlantilla']).withContext(path).toBeUndefined();
      }
      await harness.navigateByUrl('/alumnosEditar/12');
      expect(entradaActiva()).toBe(entrada('alumnosEditar/:id'));
    });

    it('con la meta: las públicas solo de Moiskimdo van a su equivalente de la plantilla o a la 404 del tema', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      const router = TestBed.inject(Router);
      for (const [desde, hasta] of REDIRECCIONES) {
        await harness.navigateByUrl(desde);
        expect(router.url).withContext(desde).toBe(hasta);
        expect(hoja().data['webPlantilla']).withContext(desde).toBeTrue();
      }
      for (const path of RUTAS_SOLO_MOISKIMDO) {
        await harness.navigateByUrl(`/${path}`);
        expect(hoja().component).withContext(path).toBe(NoEncontradaLineaComarcalComponent);
        expect(hoja().data['webPlantilla']).withContext(path).toBeTrue();
        expect(router.url).withContext(path).toBe(`/${path}`);
      }
    });

    it('con la meta: una URL desconocida cae en el comodín de la plantilla (404 del tema), no en el de Moiskimdo', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      for (const url of ['/no-existe', '/sedes/casariche/otra', '/taekwondo/x']) {
        await harness.navigateByUrl(url);
        expect(entradaActiva()).withContext(url).toBe(comodinPlantilla());
        expect(hoja().data['webPlantilla']).withContext(url).toBeTrue();
        expect(TestBed.inject(Router).url).withContext(url).toBe(url);
      }
    });

    it('sin la meta: las rutas de Moiskimdo y el 404 público no cambian', async () => {
      const harness = await montar();
      for (const path of ['taekwondo', 'horarios', 'aviso-legal', 'politica-privacidad', 'politica-cookies', 'contacto']) {
        await harness.navigateByUrl(`/${path}`);
        expect(entradaActiva()).withContext(path).toBe(entrada(path));
      }
      await harness.navigateByUrl('/no-existe');
      expect(entradaActiva()).toBe(routes[routes.length - 1]);
      expect(hoja().data['webPlantilla']).toBeUndefined();
    });
  });
});
