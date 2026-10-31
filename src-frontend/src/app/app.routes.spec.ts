import { adminOnlyGuard } from './guards/admin-only.guard';
import { roleGuard } from './guards/role.guard';
import { plataformaGuard } from './guards/plataforma.guard';
import { profesorGuard } from './guards/profesor.guard';
import { misGruposGuard } from './guards/mis-grupos.guard';
import { estilosPanelGuard } from './panel-nuevo/estilos-panel.guard';
import { estilosGestionGuard } from './panel-nuevo/estilos-gestion.guard';
import { gestionPanelGuard } from './panel-nuevo/no-encontrada/gestion-panel.guard';
import { NoEncontradaPanelComponent } from './panel-nuevo/no-encontrada/no-encontrada.component';
import { salirSinGuardarGuard } from './panel-nuevo/productos-alumno/salir-sin-guardar.guard';
import { InicioComponent } from './panel-nuevo/inicio/inicio.component';
import { AlumnosComponent } from './panel-nuevo/alumnos/alumnos.component';
import { FichaAlumnoComponent } from './panel-nuevo/ficha/ficha-alumno.component';
import { MiPerfilComponent } from './panel-nuevo/mi-perfil/mi-perfil.component';
import { ProductosAlumnoPanelComponent } from './panel-nuevo/productos-alumno/productos-alumno-panel.component';
import { PasarListaComponent } from './panel-nuevo/asistencia/pasar-lista.component';
import { GruposComponent } from './panel-nuevo/grupos/grupos.component';
import { EditarGrupoComponent } from './panel-nuevo/grupos/editar-grupo.component';
import { GestionarAlumnosComponent } from './panel-nuevo/grupos/gestionar-alumnos.component';
import { GestionarTurnosAlumnoComponent } from './panel-nuevo/grupos/gestionar-turnos-alumno.component';
import { TesoreriaComponent } from './panel-nuevo/tesoreria/tesoreria.component';
import { ConvocatoriasComponent } from './panel-nuevo/convocatorias/convocatorias.component';
import { EventosPanelComponent } from './panel-nuevo/eventos/eventos.component';
import { EventoFormularioComponent } from './panel-nuevo/eventos/evento-formulario.component';
import { ProductosComponent } from './panel-nuevo/productos/productos.component';
import { ProductoFormularioComponent } from './panel-nuevo/productos/producto-formulario.component';
import { PreinscripcionesPanelComponent } from './panel-nuevo/preinscripciones/preinscripciones.component';
import { SolicitudesPanelComponent } from './panel-nuevo/solicitudes/solicitudes.component';
import { CajaSedePanelComponent } from './panel-nuevo/caja-sede/caja-sede.component';
import { ConfiguracionPanelComponent } from './panel-nuevo/escuela/configuracion/configuracion.component';
import { RegistroAccionesComponent } from './panel-nuevo/escuela/registro/registro.component';
import { AltaAlumnoComponent } from './panel-nuevo/alta-alumno/alta-alumno.component';
import { BajasAlumnosComponent } from './panel-nuevo/bajas-alumnos/bajas-alumnos.component';
import { MiCajaPanelComponent } from './panel-nuevo/mi-caja/mi-caja.component';
import { EstilosPanelService } from './panel-nuevo/estilos-panel.service';
import { AuthenticationService } from './servicios/authentication/authentication.service';
import { Component, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { PermisosSesionService } from './servicios/authentication/permisos-sesion.service';
import { resolverPortalContexto, routes } from './app.routes';
import { catalogoResolver } from './features/catalogo/catalogo.resolver';
import { Routes } from '@angular/router';

/** Pantalla de gestión rediseñada: su ruta, el componente nuevo, los guards de `canActivate` y la data propia. */
type Rediseno = [ruta: string, componente: Type<unknown>, canActivate: unknown[], extra?: Record<string, unknown>];

const GESTION = [roleGuard];
const PROPIETARIO = [roleGuard, adminOnlyGuard];

/** Las 27 rutas de gestión que tenían dos entradas (la nueva tras el interruptor y la de hoy) hasta 8b-Z. */
const REDISENADAS: Rediseno[] = [
  ['adminpage', InicioComponent, GESTION],
  ['alumnosListar', AlumnosComponent, GESTION],
  ['preinscripciones', PreinscripcionesPanelComponent, GESTION],
  ['alumnosEditar/:id', FichaAlumnoComponent, GESTION],
  ['alumnos/:id/productos', ProductosAlumnoPanelComponent, GESTION],
  ['alumnosCrear', AltaAlumnoComponent, GESTION],
  ['alumnosEliminar', BajasAlumnosComponent, GESTION],
  ['gruposListar', GruposComponent, GESTION],
  ['gruposListar/:grupoId/lista', PasarListaComponent, GESTION, { prefijoAsistencia: 'gestion' }],
  ['gruposCrear', GruposComponent, GESTION, { crearGrupo: true }],
  ['gruposEditar/:id', EditarGrupoComponent, GESTION],
  ['gestionarAlumnos/:id', GestionarAlumnosComponent, GESTION],
  ['seleccionarAlumnos/:id', GestionarAlumnosComponent, GESTION, { seleccionarAlumnos: true }],
  ['gestionarTurnosAlumno/:alumnoId', GestionarTurnosAlumnoComponent, GESTION],
  ['turnosCrear', GruposComponent, GESTION, { crearTurno: true }],
  ['turnosEditar/:id', GruposComponent, GESTION, { editarTurno: true }],
  ['eventosListar', EventosPanelComponent, GESTION],
  ['eventosCrear', EventoFormularioComponent, GESTION],
  ['eventosEditar/:id', EventoFormularioComponent, GESTION],
  ['productosListar', ProductosComponent, GESTION],
  ['productosCrear', ProductoFormularioComponent, GESTION],
  ['productosEditar/:id', ProductoFormularioComponent, GESTION],
  ['tesoreriaCobros', TesoreriaComponent, GESTION],
  ['cajaSede', CajaSedePanelComponent, GESTION],
  ['solicitudes', SolicitudesPanelComponent, GESTION],
  ['auditoriaSistema', RegistroAccionesComponent, PROPIETARIO],
  ['convocatoriasListar', ConvocatoriasComponent, GESTION],
  ['configuracion-sistema', ConfiguracionPanelComponent, PROPIETARIO],
  ['panel/mi-perfil', MiPerfilComponent, GESTION],
];

/** Permisos de una sesión (sin el interruptor del diseño, que desde 8b-Z no cuenta). */
function permisos(roles: string[]) {
  return { roles, esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false, capacidades: [] };
}

/** Lo que el responsable de sede no ve (decisión 370): el resto de pantallas de gestión le dejan pasar. */
const SOLO_GESTION = /^(alumnosEliminar|eventos(Listar|Crear|Editar)|productos(Listar|Crear|Editar)|sedes|miembros)/;

describe('app routes', () => {
  for (const camino of ['plataformaErrores', 'plataformaLogos']) {
    it(`${camino}: carga solo gestión sin convertirla en pantalla nueva`, () => {
      const ruta = routes.find((entrada) => entrada.path === camino)!;
      expect(ruta.canMatch).toEqual([estilosGestionGuard]);
      expect(ruta.canActivate).toEqual([plataformaGuard]);
      expect(ruta.data?.['panelNuevo']).toBeUndefined();
      expect(ruta.canActivate).not.toContain(estilosPanelGuard);
    });
  }

  describe('8b-Z: una sola entrada por pantalla rediseñada, la nueva, sin interruptor', () => {
    for (const [ruta, componente, canActivate, extra] of REDISENADAS) {
      it(`${ruta}: una entrada con la pantalla nueva, la hoja del panel y sus guards`, async () => {
        const entradas = routes.filter((route) => route.path === ruta);
        expect(entradas.length).toBe(1);
        const [entrada] = entradas;
        expect(entrada.canMatch).toEqual([estilosPanelGuard]);
        expect(entrada.canActivate).toEqual(canActivate as any);
        expect(entrada.resolve).toEqual({ catalogo: catalogoResolver });
        expect(entrada.data?.['portalContext']).toBe('admin');
        const gestion = ['ROLE_ADMIN', 'ROLE_MANAGER'];
        expect(entrada.data?.['requiredRoles']).toEqual(
          canActivate === PROPIETARIO
            ? ['ROLE_ADMIN']
            : SOLO_GESTION.test(ruta)
              ? gestion
              : [...gestion, 'ROLE_RESPONSABLE_SEDE']
        );
        for (const [clave, valor] of Object.entries(extra ?? {})) {
          expect(entrada.data?.[clave]).withContext(clave).toBe(valor);
        }
        expect(entrada.component).toBeUndefined();
        expect(await entrada.loadComponent!()).toBe(componente);
      });
    }

    it('ninguna ruta repite camino salvo los dos comodines (404 del panel y el público)', () => {
      const caminos = routes.map((route) => route.path).filter((path) => path !== '**');
      expect(caminos.length).toBe(new Set(caminos).size);
      expect(routes.filter((route) => route.path === '**').length).toBe(2);
    });

    it('la salida sin guardar sigue en Preinscripciones, Productos y cargos y Mi perfil', () => {
      expect(routes.find((route) => route.path === 'panel/mi-perfil')!.canDeactivate).toEqual([salirSinGuardarGuard]);
      expect(routes.find((route) => route.path === 'preinscripciones')!.canDeactivate).toEqual([salirSinGuardarGuard]);
      expect(routes.find((route) => route.path === 'alumnos/:id/productos')!.canDeactivate).toEqual([salirSinGuardarGuard]);
    });

    it('alumnosEditar sin id (decisión 333): redirige al listado, solo con la ruta exacta', () => {
      const entradas = routes.filter((route) => route.path === 'alumnosEditar');
      expect(entradas.length).toBe(1);
      expect(entradas[0]).toEqual({ path: 'alumnosEditar', redirectTo: '/alumnosListar', pathMatch: 'full' });
    });

    it('alumnosEditar sin id: al navegar acaba en /alumnosListar; con id sigue en la ficha', async () => {
      @Component({ standalone: true, template: '<p class="listado">listado</p>' })
      class Listado {}
      @Component({ standalone: true, template: '<p class="ficha">ficha</p>' })
      class Ficha {}
      const redireccion = routes.find((route) => route.path === 'alumnosEditar')!;
      TestBed.configureTestingModule({
        providers: [
          provideRouter([
            redireccion,
            { path: 'alumnosEditar/:id', component: Ficha },
            { path: 'alumnosListar', component: Listado },
          ]),
        ],
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/alumnosEditar');
      expect(TestBed.inject(Router).url).toBe('/alumnosListar');
      expect((harness.routeNativeElement as HTMLElement).querySelector('.listado')).not.toBeNull();
      await harness.navigateByUrl('/alumnosEditar/7');
      expect(TestBed.inject(Router).url).toBe('/alumnosEditar/7');
      expect((harness.routeNativeElement as HTMLElement).querySelector('.ficha')).not.toBeNull();
    });

    it('la pantalla del panel espera a la hoja panel-nuevo.css antes de abrirse', async () => {
      @Component({ standalone: true, template: '<p class="nueva">nueva</p>' })
      class PantallaNueva {}
      let resolverHoja!: () => void;
      const cargarHoja = jasmine.createSpy('cargar').and.returnValue(new Promise<void>((r) => (resolverHoja = r)));
      const entrada = routes.find((route) => route.path === 'alumnosListar')!;
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ ...entrada, canActivate: [], resolve: {}, loadComponent: () => Promise.resolve(PantallaNueva) }]),
          { provide: EstilosPanelService, useValue: { cargar: cargarHoja } },
        ],
      });
      const harness = await RouterTestingHarness.create();
      const navegacion = harness.navigateByUrl('/alumnosListar');
      await new Promise((r) => setTimeout(r));
      expect(cargarHoja).toHaveBeenCalledTimes(1);
      expect(TestBed.inject(Router).url).toBe('/');
      resolverHoja();
      await navegacion;
      expect(TestBed.inject(Router).url).toBe('/alumnosListar');
      expect((harness.routeNativeElement as HTMLElement).querySelector('.nueva')).not.toBeNull();
    });
  });

  describe('8b-Z (decisión 332): la sesión solo-profesor va a las pantallas nuevas', () => {
    it('Mi caja: una entrada, la nueva, con su guard de profesor, sin catálogo', async () => {
      const entradas = routes.filter((route) => route.path === 'mi-caja');
      expect(entradas.length).toBe(1);
      const [ruta] = entradas;
      expect(ruta.canMatch).toEqual([estilosPanelGuard]);
      expect(ruta.canActivate).toEqual([profesorGuard]);
      expect(ruta.resolve).toBeUndefined();
      expect(ruta.component).toBeUndefined();
      expect(await ruta.loadComponent!()).toBe(MiCajaPanelComponent);
      expect(routes.indexOf(ruta)).toBeLessThan(routes.findIndex((route) => route.path === '**'));
    });

    it('Mis grupos: una entrada, la nueva, con su guard, hijas diferidas del panel sin catálogo', async () => {
      const entradas = routes.filter((route) => route.path === 'mis-grupos');
      expect(entradas.length).toBe(1);
      const [ruta] = entradas;
      expect(ruta.canMatch).toEqual([estilosPanelGuard]);
      expect(ruta.canActivate).toEqual([misGruposGuard]);
      expect(ruta.resolve).toBeUndefined();
      expect(routes.indexOf(ruta)).toBeLessThan(routes.findIndex((route) => route.path === '**'));
      const hijas = await (ruta.loadChildren as () => Promise<Routes>)();
      expect(hijas.map((hija) => hija.path)).toEqual(['', 'alumnos/:alumnoId', ':grupoId/lista', ':grupoId']);
      expect(hijas.every((hija) => !hija.resolve && typeof hija.loadComponent === 'function')).toBeTrue();
      expect(await hijas[0].loadComponent!()).toBe((await import('./panel-nuevo/mis-grupos/mis-grupos.component')).MisGruposPanelComponent);
      expect(await hijas[1].loadComponent!()).toBe((await import('./panel-nuevo/mis-grupos/ficha-profesor-panel.component')).FichaProfesorPanelComponent);
      expect(await hijas[2].loadComponent!()).toBe((await import('./panel-nuevo/asistencia/pasar-lista.component')).PasarListaComponent);
      expect(hijas[2].data).toEqual({ prefijoAsistencia: 'profesor' });
      expect(hijas[2].canDeactivate).toEqual([salirSinGuardarGuard]);
      expect(await hijas[3].loadComponent!()).toBe((await import('./panel-nuevo/mis-grupos/grupo.component')).GrupoPanelComponent);
    });

    for (const [camino, url] of [['mi-caja', '/mi-caja'], ['mis-grupos', '/mis-grupos']] as const) {
      it(`${url} con una sesión solo-profesor (sin interruptor en sus permisos): abre la pantalla nueva`, async () => {
        @Component({ standalone: true, template: '<p class="nueva">nueva</p>' })
        class PantallaNueva {}
        const ruta = routes.find((route) => route.path === camino)!;
        const carga =
          camino === 'mi-caja'
            ? { loadComponent: jasmine.createSpy('nueva').and.resolveTo(PantallaNueva) }
            : { loadChildren: jasmine.createSpy('nuevas').and.resolveTo([{ path: '', component: PantallaNueva }]) };
        TestBed.configureTestingModule({
          providers: [
            provideRouter([{ ...ruta, canActivate: [], ...carga }]),
            { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of(permisos(['PROFESOR'])) } },
            { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
          ],
        });
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl(url);
        expect(TestBed.inject(Router).url).toBe(url);
        expect((harness.routeNativeElement as HTMLElement).querySelector('.nueva')).not.toBeNull();
      });
    }
  });

  it('declara requiredRoles en todas las rutas protegidas por roleGuard', () => {
    const protectedRoutes = routes.filter((route) => route.canActivate?.includes(roleGuard));

    expect(protectedRoutes.length).toBeGreaterThan(0);
    expect(protectedRoutes.every((route) => Array.isArray(route.data?.['requiredRoles']) && route.data?.['requiredRoles'].length > 0)).toBeTrue();
  });

  it('protege auditoría y configuración con guard de admin', () => {
    const todas = routes.filter((route) => route.path === 'auditoriaSistema' || route.path === 'configuracion-sistema');
    expect(todas.length).toBe(2);
    for (const route of todas) {
      expect(route.canActivate).toEqual([roleGuard, adminOnlyGuard]);
      expect(route.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
    }
  });

  it('protege errores de plataforma con su propio guard, sin roles de escuela ni catálogo', () => {
    const plataformaIndex = routes.findIndex((route) => route.path === 'plataformaErrores');
    const plataformaRoute = routes[plataformaIndex];

    expect(plataformaRoute?.canActivate).toEqual([plataformaGuard]);
    expect(plataformaRoute?.data?.['requiredRoles']).toBeUndefined();
    expect(plataformaRoute?.resolve?.['catalogo']).toBeUndefined();
    expect(plataformaIndex).toBeLessThan(routes.findIndex((route) => route.path === '**'));
    expect(resolverPortalContexto('/plataformaErrores')).not.toBe('user');
  });

  it('protege el logo de la escuela con el guard de plataforma, sin roles de escuela ni catálogo', () => {
    const indice = routes.findIndex((route) => route.path === 'plataformaLogos');
    const ruta = routes[indice];

    expect(ruta?.canActivate).toEqual([plataformaGuard]);
    expect(ruta?.loadComponent).toBeDefined();
    expect(ruta?.data?.['requiredRoles']).toBeUndefined();
    expect(ruta?.resolve?.['catalogo']).toBeUndefined();
    expect(indice).toBeLessThan(routes.findIndex((route) => route.path === '**'));
    expect(resolverPortalContexto('/plataformaLogos')).not.toBe('user');
  });

  it('mantiene el portal user accesible para cuentas duales y usuarios finales', () => {
    const userRoute = routes.find((route) => route.path === 'userpage');

    expect(userRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER']);
  });

  it('resuelve el contexto visual del portal a partir de metadata de ruta', () => {
    expect(resolverPortalContexto('/userpage/turnos')).toBe('user');
    expect(resolverPortalContexto('/alumnosEditar/10')).toBe('admin');
    expect(resolverPortalContexto('/contacto')).toBe('public');
  });

  it('toda ruta protegida por roleGuard resuelve el catálogo antes de activarse', () => {
    const protectedRoutes = routes.filter((route) => route.canActivate?.includes(roleGuard));

    expect(protectedRoutes.every((route) => route.resolve?.['catalogo'] === catalogoResolver)).toBeTrue();
  });

  it('las rutas públicas no piden el catálogo autenticado', () => {
    const publicRoutes = routes.filter((route) => !route.canActivate?.includes(roleGuard));

    expect(publicRoutes.some((route) => route.resolve?.['catalogo'])).toBeFalse();
  });

  it('404 del panel (8b-M2b): ** con gestionPanelGuard (y la hoja al activarse) va antes del comodín público', async () => {
    const comodines = routes.filter((route) => route.path === '**');
    expect(comodines.length).toBe(2);
    expect(routes[routes.length - 1]).toBe(comodines[1]);
    const [panel, publico] = comodines;
    expect(panel.canMatch).toEqual([gestionPanelGuard]);
    expect(panel.canActivate).toEqual([estilosPanelGuard]);
    expect(panel.data?.['panelNuevo']).toBeTrue();
    expect(await panel.loadComponent!()).toBe(NoEncontradaPanelComponent);
    expect(publico.canMatch).toBeUndefined();
    expect(publico.canActivate).toBeUndefined();
    expect(await publico.loadComponent!()).not.toBe(NoEncontradaPanelComponent);
  });

  it('404: una sesión de familia cae al público sin cargar la hoja del panel', async () => {
    @Component({ standalone: true, template: '<p class="publico">404</p>' })
    class Publico {}
    const [panel] = routes.filter((route) => route.path === '**');
    const cargarHoja = jasmine.createSpy('cargar').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...panel, loadComponent: () => Promise.resolve(NoEncontradaPanelComponent) }, { path: '**', component: Publico }]),
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of(permisos(['FAMILIA'])) } },
        { provide: EstilosPanelService, useValue: { cargar: cargarHoja } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/no-existe');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.publico')).not.toBeNull();
    expect(cargarHoja).not.toHaveBeenCalled();
  });

  // 8b-3k: las dos rutas del propietario conservan roleGuard y adminOnlyGuard (riesgo: perder un guard).
  for (const [ruta, url] of [
    ['configuracion-sistema', '/configuracion-sistema'],
    ['auditoriaSistema', '/auditoriaSistema'],
  ] as const) {
    for (const [rolesSesion, entra] of [[['ROLE_MANAGER'], false], [['ROLE_USER'], false], [['ROLE_ADMIN'], true]] as const) {
      it(`${ruta} (8b-3k): ${rolesSesion.join(', ')} ${entra ? 'entra' : 'no entra (roleGuard y adminOnlyGuard)'}`, async () => {
        @Component({ standalone: true, template: '<p class="nueva">nueva</p>' })
        class PantallaNueva {}
        @Component({ standalone: true, template: '<p class="portada">portada</p>' })
        class Portada {}
        const entrada = routes.find((route) => route.path === ruta)!;
        const cargar = jasmine.createSpy('loadComponent nuevo').and.returnValue(Promise.resolve(PantallaNueva));
        TestBed.configureTestingModule({
          providers: [
            // Los guards de verdad (canMatch y canActivate de la entrada); solo se quita el catálogo.
            provideRouter([{ ...entrada, resolve: {}, loadComponent: cargar }, { path: '', component: Portada }]),
            { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of(permisos([...rolesSesion])) } },
            { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
            {
              provide: AuthenticationService,
              useValue: { resolverRolesDisponibles: () => of([...rolesSesion]), capacidadesDeResponsable: () => of([]) },
            },
          ],
        });
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl(url);
        await harness.fixture.whenStable();
        const pagina = harness.routeNativeElement as HTMLElement;
        if (entra) {
          expect(cargar).toHaveBeenCalled();
          expect(pagina.querySelector('.nueva')).not.toBeNull();
          expect(TestBed.inject(Router).url).toBe(url);
        } else {
          expect(cargar).not.toHaveBeenCalled();
          expect(pagina.querySelector('.nueva')).toBeNull();
          expect(TestBed.inject(Router).url).toBe('/');
        }
      });
    }
  }
});
