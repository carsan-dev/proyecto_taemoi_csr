import { adminOnlyGuard } from './guards/admin-only.guard';
import { roleGuard } from './guards/role.guard';
import { plataformaGuard } from './guards/plataforma.guard';
import { profesorGuard } from './guards/profesor.guard';
import { misGruposGuard } from './guards/mis-grupos.guard';
import { disenoNuevoGuard } from './panel-nuevo/diseno-nuevo.guard';
import { InicioComponent } from './panel-nuevo/inicio/inicio.component';
import { AlumnosComponent } from './panel-nuevo/alumnos/alumnos.component';
import { FichaAlumnoComponent } from './panel-nuevo/ficha/ficha-alumno.component';
import { GruposComponent } from './panel-nuevo/grupos/grupos.component';
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
import { EstilosPanelService } from './panel-nuevo/estilos-panel.service';
import { AuthenticationService } from './servicios/authentication/authentication.service';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { PermisosSesionService } from './servicios/authentication/permisos-sesion.service';
import { resolverPortalContexto, routes } from './app.routes';
import { catalogoResolver } from './features/catalogo/catalogo.resolver';
import { Routes } from '@angular/router';

describe('app routes', () => {
  it('declara requiredRoles en todas las rutas protegidas por roleGuard', () => {
    const protectedRoutes = routes.filter((route) => route.canActivate?.includes(roleGuard));

    expect(protectedRoutes.length).toBeGreaterThan(0);
    expect(protectedRoutes.every((route) => Array.isArray(route.data?.['requiredRoles']) && route.data?.['requiredRoles'].length > 0)).toBeTrue();
  });

  it('protege auditoría y configuración con guard de admin', () => {
    const auditoriaRoute = routes.find((route) => route.path === 'auditoriaSistema');
    const configuracionRoute = routes.find((route) => route.path === 'configuracion-sistema');

    expect(auditoriaRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
    expect(configuracionRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
    expect(auditoriaRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
    expect(configuracionRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
    // 8b-3k: también cada entrada duplicada (la nueva, tras el interruptor, y la de hoy).
    const todas = routes.filter((route) => route.path === 'auditoriaSistema' || route.path === 'configuracion-sistema');
    expect(todas.length).toBe(4);
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

  it('«Mi caja» es lazy, con su guard de profesor y sin catálogo (una sesión solo profesor recibiría 403)', () => {
    const indice = routes.findIndex((route) => route.path === 'mi-caja');
    const ruta = routes[indice];

    expect(ruta?.canActivate).toEqual([profesorGuard]);
    expect(typeof ruta?.loadComponent).toBe('function');
    expect(ruta?.resolve).toBeUndefined();
    expect(indice).toBeLessThan(routes.findIndex((route) => route.path === '**'));
  });

  it('«Mis grupos» es lazy, con el guard de solo profesor y sin catálogo; sus hijas tampoco lo piden', async () => {
    const indice = routes.findIndex((route) => route.path === 'mis-grupos');
    const ruta = routes[indice];

    expect(ruta?.canActivate).toEqual([misGruposGuard]);
    expect(typeof ruta?.loadChildren).toBe('function');
    expect(ruta?.resolve).toBeUndefined();
    expect(indice).toBeLessThan(routes.findIndex((route) => route.path === '**'));

    const hijas = (await (ruta!.loadChildren as () => Promise<Routes>)()) as Routes;
    expect(hijas.map((h) => h.path)).toEqual(['', 'alumnos/:alumnoId', ':grupoId']);
    expect(hijas.every((h) => !h.resolve && typeof h.loadComponent === 'function')).toBeTrue();
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

  it('adminpage (8b-2): primero el Inicio nuevo tras disenoNuevoGuard y después la pantalla de hoy, los dos con roleGuard', async () => {
    const entradas = routes.filter((route) => route.path === 'adminpage');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data?.['requiredRoles']).toEqual(['ROLE_ADMIN', 'ROLE_MANAGER']);
    expect(await nueva.loadComponent!()).toBe(InicioComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(hoy.canActivate).toEqual([roleGuard]);
    expect(hoy.loadComponent).toBeDefined();
    expect(await hoy.loadComponent!()).not.toBe(InicioComponent);
  });

  it('alumnosListar (8b-3a): primero el listado nuevo tras disenoNuevoGuard, con la misma data, guards y resolve que el de hoy', async () => {
    const entradas = routes.filter((route) => route.path === 'alumnosListar');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual(hoy.canActivate!);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data).toEqual(hoy.data!);
    expect(nueva.resolve).toEqual(hoy.resolve!);
    expect(await nueva.loadComponent!()).toBe(AlumnosComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(await hoy.loadComponent!()).not.toBe(AlumnosComponent);
  });

  it('alumnosListar (8b-3a): con el interruptor apagado no se descarga el chunk nuevo', async () => {
    @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
    class PantallaDeHoy {}
    const nueva = routes.find((route) => route.path === 'alumnosListar' && route.canMatch)!;
    const cargar = jasmine.createSpy('loadComponent nuevo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: 'alumnosListar', component: PantallaDeHoy }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
            capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('alumnosEditar/:id (8b-3b): primero la ficha nueva tras disenoNuevoGuard, con la misma data, guards y resolve que la de hoy', async () => {
    const entradas = routes.filter((route) => route.path === 'alumnosEditar/:id');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual(hoy.canActivate!);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data).toEqual(hoy.data!);
    expect(nueva.resolve).toEqual(hoy.resolve!);
    expect(await nueva.loadComponent!()).toBe(FichaAlumnoComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(await hoy.loadComponent!()).not.toBe(FichaAlumnoComponent);
    // La ruta sin id sigue siendo solo la de hoy (redirige al primer alumno con /alumnosEditar/:id).
    expect(routes.filter((route) => route.path === 'alumnosEditar').every((route) => !route.canMatch)).toBeTrue();
  });

  it('alumnosEditar/:id (8b-3b): con el interruptor apagado no se descarga el chunk nuevo', async () => {
    @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
    class FichaDeHoy {}
    const nueva = routes.find((route) => route.path === 'alumnosEditar/:id' && route.canMatch)!;
    const cargar = jasmine.createSpy('loadComponent nuevo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: 'alumnosEditar/:id', component: FichaDeHoy }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
            capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosEditar/7');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('gruposListar (8b-3c): primero Grupos nuevo tras disenoNuevoGuard, con la misma data, guards y resolve que el de hoy', async () => {
    const entradas = routes.filter((route) => route.path === 'gruposListar');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual(hoy.canActivate!);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data).toEqual(hoy.data!);
    expect(nueva.resolve).toEqual(hoy.resolve!);
    expect(await nueva.loadComponent!()).toBe(GruposComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(await hoy.loadComponent!()).not.toBe(GruposComponent);
  });

  it('gruposListar (8b-3c): con el interruptor apagado no se descarga el chunk nuevo', async () => {
    @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
    class GruposDeHoy {}
    const nueva = routes.find((route) => route.path === 'gruposListar' && route.canMatch)!;
    const cargar = jasmine.createSpy('loadComponent nuevo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: 'gruposListar', component: GruposDeHoy }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
            capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/gruposListar');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });
  it('tesoreriaCobros (8b-3d): primero Tesorería nueva tras disenoNuevoGuard, con la misma data, guards y resolve que la de hoy', async () => {
    const entradas = routes.filter((route) => route.path === 'tesoreriaCobros');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual(hoy.canActivate!);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data).toEqual(hoy.data!);
    expect(nueva.resolve).toEqual(hoy.resolve!);
    expect(await nueva.loadComponent!()).toBe(TesoreriaComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(await hoy.loadComponent!()).not.toBe(TesoreriaComponent);
  });

  it('tesoreriaCobros (8b-3d): con el interruptor apagado no se descarga el chunk nuevo', async () => {
    @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
    class TesoreriaDeHoy {}
    const nueva = routes.find((route) => route.path === 'tesoreriaCobros' && route.canMatch)!;
    const cargar = jasmine.createSpy('loadComponent nuevo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: 'tesoreriaCobros', component: TesoreriaDeHoy }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
            capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tesoreriaCobros');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('convocatoriasListar (8b-3e): primero Convocatorias nueva tras disenoNuevoGuard, con la misma data, guards y resolve que la de hoy', async () => {
    const entradas = routes.filter((route) => route.path === 'convocatoriasListar');
    expect(entradas.length).toBe(2);
    const [nueva, hoy] = entradas;
    expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
    expect(nueva.canActivate).toEqual(hoy.canActivate!);
    expect(nueva.canActivate).toEqual([roleGuard]);
    expect(nueva.data).toEqual(hoy.data!);
    expect(nueva.resolve).toEqual(hoy.resolve!);
    expect(await nueva.loadComponent!()).toBe(ConvocatoriasComponent);
    expect(hoy.canMatch).toBeUndefined();
    expect(await hoy.loadComponent!()).not.toBe(ConvocatoriasComponent);
  });

  it('convocatoriasListar (8b-3e): con el interruptor apagado no se descarga el chunk nuevo', async () => {
    @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
    class ConvocatoriasDeHoy {}
    const nueva = routes.find((route) => route.path === 'convocatoriasListar' && route.canMatch)!;
    const cargar = jasmine.createSpy('loadComponent nuevo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: 'convocatoriasListar', component: ConvocatoriasDeHoy }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
            capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/convocatoriasListar');
    expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });
  for (const [ruta, url, componente] of [
    ['eventosListar', '/eventosListar', EventosPanelComponent],
    ['eventosCrear', '/eventosCrear', EventoFormularioComponent],
    ['eventosEditar/:id', '/eventosEditar/7', EventoFormularioComponent],
    ['productosListar', '/productosListar', ProductosComponent],
    ['productosCrear', '/productosCrear', ProductoFormularioComponent],
    ['productosEditar/:id', '/productosEditar/7', ProductoFormularioComponent],
    ['preinscripciones', '/preinscripciones', PreinscripcionesPanelComponent],
    ['solicitudes', '/solicitudes', SolicitudesPanelComponent],
    ['cajaSede', '/cajaSede', CajaSedePanelComponent],
  ] as const) {
    it(`${ruta} (8b-3f a 8b-3j): primero la entrada nueva tras disenoNuevoGuard, con la misma data, guards y resolve que la de hoy`, async () => {
      const entradas = routes.filter((route) => route.path === ruta);
      expect(entradas.length).toBe(2);
      const [nueva, hoy] = entradas;
      expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
      expect(nueva.canActivate).toEqual(hoy.canActivate!);
      expect(nueva.canActivate).toEqual([roleGuard]);
      expect(nueva.data).toEqual(hoy.data!);
      expect(nueva.resolve).toEqual(hoy.resolve!);
      expect(await nueva.loadComponent!()).toBe(componente);
      expect(hoy.canMatch).toBeUndefined();
      expect(await hoy.loadComponent!()).not.toBe(componente);
    });

    it(`${ruta} (8b-3f a 8b-3j): con el interruptor apagado no se descarga el chunk nuevo`, async () => {
      @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
      class EventosDeHoy {}
      const nueva = routes.find((route) => route.path === ruta && route.canMatch)!;
      const cargar = jasmine.createSpy('loadComponent nuevo');
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: ruta, component: EventosDeHoy }]),
          {
            provide: PermisosSesionService,
            useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
              capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
          },
        ],
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
      expect(cargar).not.toHaveBeenCalled();
    });
  }
  // 8b-3k: las dos rutas del propietario duplicadas conservan roleGuard y adminOnlyGuard (riesgo: perder un guard).
  for (const [ruta, url, componente] of [
    ['configuracion-sistema', '/configuracion-sistema', ConfiguracionPanelComponent],
    ['auditoriaSistema', '/auditoriaSistema', RegistroAccionesComponent],
  ] as const) {
    it(`${ruta} (8b-3k): primero la entrada nueva tras disenoNuevoGuard, con roleGuard, adminOnlyGuard, data y resolve de hoy`, async () => {
      const entradas = routes.filter((route) => route.path === ruta);
      expect(entradas.length).toBe(2);
      const [nueva, hoy] = entradas;
      expect(nueva.canMatch).toEqual([disenoNuevoGuard]);
      expect(nueva.canActivate).toEqual(hoy.canActivate!);
      expect(nueva.canActivate).toEqual([roleGuard, adminOnlyGuard]);
      expect(nueva.data).toEqual(hoy.data!);
      expect(nueva.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
      expect(nueva.resolve).toEqual(hoy.resolve!);
      expect(await nueva.loadComponent!()).toBe(componente);
      expect(hoy.canMatch).toBeUndefined();
      expect(await hoy.loadComponent!()).not.toBe(componente);
    });

    it(`${ruta} (8b-3k): con el interruptor apagado no se descarga el chunk nuevo`, async () => {
      @Component({ standalone: true, template: '<p class="hoy">hoy</p>' })
      class PantallaDeHoy {}
      const nueva = routes.find((route) => route.path === ruta && route.canMatch)!;
      const cargar = jasmine.createSpy('loadComponent nuevo');
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ ...nueva, canActivate: [], resolve: {}, loadComponent: cargar }, { path: ruta, component: PantallaDeHoy }]),
          {
            provide: PermisosSesionService,
            useValue: { obtener: () => of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false,
              capacidades: [], disenoNuevo: { activo: false, permitido: false } }) },
          },
        ],
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      expect((harness.routeNativeElement as HTMLElement).querySelector('.hoy')).not.toBeNull();
      expect(cargar).not.toHaveBeenCalled();
    });

    for (const [rolesSesion, entra] of [[['ROLE_MANAGER'], false], [['ROLE_USER'], false], [['ROLE_ADMIN'], true]] as const) {
      it(`${ruta} (8b-3k): con el interruptor encendido, ${rolesSesion.join(', ')} ${entra ? 'entra' : 'no entra (roleGuard y adminOnlyGuard)'}`, async () => {
        @Component({ standalone: true, template: '<p class="nueva">nueva</p>' })
        class PantallaNueva {}
        @Component({ standalone: true, template: '<p class="portada">portada</p>' })
        class Portada {}
        const nueva = routes.find((route) => route.path === ruta && route.canMatch)!;
        const cargar = jasmine.createSpy('loadComponent nuevo').and.returnValue(Promise.resolve(PantallaNueva));
        TestBed.configureTestingModule({
          providers: [
            // Los guards de verdad (canMatch y canActivate de la entrada nueva); solo se quita el catálogo.
            provideRouter([{ ...nueva, resolve: {}, loadComponent: cargar }, { path: '', component: Portada }]),
            {
              provide: PermisosSesionService,
              useValue: { obtener: () => of({ roles: [...rolesSesion], esAdminPlataforma: false, sedes: [], variasSedes: false,
                tieneGrupos: false, capacidades: [], disenoNuevo: { activo: true, permitido: true } }) },
            },
            { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
            { provide: AuthenticationService, useValue: { resolverRolesDisponibles: () => of([...rolesSesion]) } },
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
