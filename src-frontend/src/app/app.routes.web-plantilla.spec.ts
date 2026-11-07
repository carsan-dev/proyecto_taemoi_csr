import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router, Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { esWebPlantilla } from './guards/web-plantilla.guard';
import { ponerMetaWebPlantilla, quedaMetaWebPlantilla } from './testing/meta-web-plantilla';
import { PlantillaPendienteComponent } from './web-plantilla/paginas/plantilla-pendiente.component';

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

/** Las páginas de la plantilla (fase4-web-plan.md §3.1), en orden. */
const PAGINAS_PLANTILLA = ['', 'sedes/:slug', 'preinscripcion', 'tarifas', 'eventos', 'eventos/:id', 'legales'];

/** Clave de prueba en `data` con el índice de la entrada original de `app.routes`. */
const INDICE = 'indiceEnAppRoutes';

const grupoPlantilla = (): Route => routes.find((r) => r.canMatch?.includes(esWebPlantilla))!;

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
    const grupos = routes.filter((r) => r.canMatch?.includes(esWebPlantilla));
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

  it('las hijas: las siete páginas, todas con data.webPlantilla, todas a la pantalla técnica provisional y sin comodín', async () => {
    const hijas = (await (grupoPlantilla().loadChildren as () => Promise<Routes>)()) as Routes;
    expect(hijas.map((r) => r.path)).toEqual(PAGINAS_PLANTILLA);
    expect(hijas.some((r) => r.path === '**')).toBeFalse();
    for (const hija of hijas) {
      expect(hija.data?.['webPlantilla']).withContext(hija.path!).toBeTrue();
      expect(hija.canMatch).withContext(hija.path!).toBeUndefined();
      expect(await hija.loadComponent!()).withContext(hija.path!).toBe(PlantillaPendienteComponent);
    }
    expect(hijas[0].pathMatch).toBe('full');
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
        return { path: r.path, pathMatch: r.pathMatch, data, component: PantallaFalsaComponent };
      });
      TestBed.configureTestingModule({ providers: [provideRouter(tabla)] });
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

    it('con la meta: cada página de la plantilla resuelve a la ruta del grupo (pantalla provisional y data.webPlantilla)', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      for (const url of ['/', '/sedes/casariche', '/preinscripcion', '/tarifas', '/eventos', '/eventos/3', '/legales']) {
        await harness.navigateByUrl(url);
        expect(hoja().component).withContext(url).toBe(PlantillaPendienteComponent);
        expect(hoja().data['webPlantilla']).withContext(url).toBeTrue();
        expect(TestBed.inject(Router).url).withContext(url).toBe(url);
      }
      expect(harness.routeNativeElement?.querySelector('main#contenido')).not.toBeNull();
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

    it('con la meta: las públicas solo de Moiskimdo y las desconocidas caen como hoy (§9, n.º 10 sin mapa decidido)', async () => {
      quitarMeta = ponerMetaWebPlantilla();
      const harness = await montar();
      await harness.navigateByUrl('/taekwondo');
      expect(entradaActiva()).toBe(entrada('taekwondo'));
      await harness.navigateByUrl('/sedes/casariche/otra');
      expect(entradaActiva()!.path).toBe('**');
      await harness.navigateByUrl('/no-existe');
      expect(entradaActiva()!.path).toBe('**');
      expect(hoja().data['webPlantilla']).toBeUndefined();
    });
  });
});
