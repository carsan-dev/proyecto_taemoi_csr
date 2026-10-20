import { Routes } from '@angular/router';
import { roleGuard } from './guards/role.guard';
import { adminOnlyGuard } from './guards/admin-only.guard';
import { plataformaGuard } from './guards/plataforma.guard';
import { profesorGuard } from './guards/profesor.guard';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { SEO_ROUTES } from './core/constants/seo.constants';
import { RouteScrollPolicy } from './servicios/generales/page-scroll.service';
import { catalogoResolver } from './features/catalogo/catalogo.resolver';

/** Las rutas autenticadas esperan al catálogo (tras los guards) para que los componentes lo lean de forma síncrona. */
const CATALOGO_RESOLVE = { catalogo: catalogoResolver };

type RouteRole = 'ROLE_ADMIN' | 'ROLE_MANAGER' | 'ROLE_USER';
export type PortalContext = 'public' | 'admin' | 'user';
export interface AppRouteData {
  portalContext?: PortalContext;
  scrollPolicy?: RouteScrollPolicy;
}

const ROUTE_ROLE_SETS = {
  management: ['ROLE_ADMIN', 'ROLE_MANAGER'] as const satisfies readonly RouteRole[],
  adminOnly: ['ROLE_ADMIN'] as const satisfies readonly RouteRole[],
  userPortal: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER'] as const satisfies readonly RouteRole[],
};

function privateRouteData(
  requiredRoles: readonly RouteRole[],
  portalContext: PortalContext = 'admin',
  scrollPolicy: RouteScrollPolicy = 'standard'
) {
  return {
    seo: SEO_ROUTES.noIndex,
    requiredRoles,
    portalContext,
    scrollPolicy,
  };
}

function normalizarRuta(url: string): string {
  return (url || '').split('?')[0].split('#')[0];
}

function coincideRuta(routePath: string, url: string): boolean {
  if (!routePath) {
    return url === '' || url === '/';
  }

  if (routePath === '**') {
    return true;
  }

  const routeSegments = routePath.split('/').filter(Boolean);
  const urlSegments = url.split('/').filter(Boolean);

  if (routeSegments.length !== urlSegments.length) {
    return false;
  }

  return routeSegments.every((segment, index) => segment.startsWith(':') || segment === urlSegments[index]);
}

export function resolverPortalContexto(url: string): PortalContext {
  const rutaNormalizada = normalizarRuta(url);
  const matchingRoute = routes.find((route) => route.path && coincideRuta(route.path, rutaNormalizada));
  const routeData = matchingRoute?.data as AppRouteData | undefined;
  return routeData?.portalContext ?? 'public';
}

export const routes: Routes = [
  {
    path: '',
    data: { seo: SEO_ROUTES.home },
    component: EscaparatePrincipalComponent // Eager-load main page to prevent white screen
  },
  { path: 'inicio', redirectTo: '', pathMatch: 'full' },
  {
    path: 'aljarafe',
    data: { seo: SEO_ROUTES.aljarafe, landingKey: 'aljarafe' },
    loadComponent: () =>
      import('./componentes/vistas/localidad-landing/localidad-landing.component').then(
        (m) => m.LocalidadLandingComponent
      ),
  },
  {
    path: 'sevilla',
    data: { seo: SEO_ROUTES.sevilla, landingKey: 'sevilla' },
    loadComponent: () =>
      import('./componentes/vistas/localidad-landing/localidad-landing.component').then(
        (m) => m.LocalidadLandingComponent
      ),
  },
  {
    path: 'eltaekwondo',
    redirectTo: '/taekwondo',
    pathMatch: 'full',
  },
  {
    path: 'taekwondo',
    data: { seo: SEO_ROUTES.taekwondo },
    loadComponent: () => import('./componentes/vistas/taekwondo/taekwondo.component').then(m => m.TaekwondoComponent)
  },
  {
    path: 'kickboxing',
    data: { seo: SEO_ROUTES.kickboxing },
    loadComponent: () => import('./componentes/vistas/kickboxing/kickboxing.component').then(m => m.KickboxingComponent)
  },
  {
    path: 'pilates',
    data: { seo: SEO_ROUTES.pilates },
    loadComponent: () => import('./componentes/vistas/pilates/pilates.component').then(m => m.PilatesComponent)
  },
  {
    path: 'defensapersonalfemenina',
    redirectTo: '/defensa-personal-femenina',
    pathMatch: 'full',
  },
  {
    path: 'defensa-personal-femenina',
    data: { seo: SEO_ROUTES.defensaPersonal },
    loadComponent: () =>
      import('./componentes/vistas/defensa-personal-femenina/defensa-personal-femenina.component').then(
        (m) => m.DefensaPersonalFemeninaComponent
      ),
  },
  {
    path: 'horarios',
    data: { seo: SEO_ROUTES.horarios },
    loadComponent: () => import('./componentes/vistas/horarios/horarios.component').then(m => m.HorariosComponent)
  },
  {
    path: 'eventos',
    data: { seo: SEO_ROUTES.eventos },
    loadComponent: () => import('./componentes/vistas/eventos/eventos.component').then(m => m.EventosComponent)
  },
  {
    path: 'eventos/:eventoId',
    loadComponent: () => import('./componentes/vistas/eventos/evento-detalle/evento-detalle.component').then(m => m.EventoDetalleComponent)
  },
  {
    path: 'contacto',
    data: { seo: SEO_ROUTES.contacto },
    loadComponent: () => import('./componentes/vistas/contacto/contacto.component').then(m => m.ContactoComponent)
  },
  {
    path: 'politica-privacidad',
    data: { seo: SEO_ROUTES.privacidad },
    loadComponent: () => import('./componentes/vistas/legal/politica-privacidad/politica-privacidad.component').then(m => m.PoliticaPrivacidadComponent)
  },
  {
    path: 'politica-cookies',
    data: { seo: SEO_ROUTES.cookies },
    loadComponent: () => import('./componentes/vistas/legal/politica-cookies/politica-cookies.component').then(m => m.PoliticaCookiesComponent)
  },
  {
    path: 'aviso-legal',
    data: { seo: SEO_ROUTES.avisoLegal },
    loadComponent: () => import('./componentes/vistas/legal/aviso-legal/aviso-legal.component').then(m => m.AvisoLegalComponent)
  },
  {
    path: 'tarifas',
    data: { seo: SEO_ROUTES.tarifas },
    loadComponent: () => import('./componentes/vistas/tarifas/tarifas.component').then(m => m.TarifasComponent)
  },
  {
    path: 'preinscripcion',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/preinscripcion/preinscripcion.component').then(m => m.PreinscripcionComponent)
  },
  {
    path: 'login',
    data: { seo: SEO_ROUTES.noIndex },
    component: VistaLoginComponent // Eager-load login page for better UX
  },
  {
    path: 'recuperar-contrasena',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/recuperar-contrasena/recuperar-contrasena.component').then(m => m.RecuperarContrasenaComponent)
  },
  {
    path: 'reset-password',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/reset-password/reset-password.component').then(m => m.ResetPasswordComponent)
  },
  {
    path: 'registro-confirmar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/registro-confirmar/registro-confirmar.component').then(m => m.RegistroConfirmarComponent)
  },
  {
    path: 'adminpage',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/vistas/vista-principal-admin/vista-principal-admin.component').then(m => m.VistaPrincipalAdminComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'userpage',
    data: privateRouteData(ROUTE_ROLE_SETS.userPortal, 'user'),
    loadComponent: () => import('./componentes/vistas/vista-principal-user/vista-principal-user.component').then(m => m.VistaPrincipalUserComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'userpage/turnos',
    data: privateRouteData(ROUTE_ROLE_SETS.userPortal, 'user'),
    loadComponent: () => import('./componentes/vistas/vista-principal-user/turnos-usuario/turnos-usuario.component').then(m => m.TurnosUsuarioComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosListar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-alumnos/listado-alumnos.component').then(m => m.ListadoAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'preinscripciones',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/preinscripciones-admin/preinscripciones-admin.component').then(m => m.PreinscripcionesAdminComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosEditar/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosEditar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
    pathMatch: 'full',
  },
  {
    path: 'alumnos/:id/productos',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-alumno/productos-alumno/productos-alumno.component').then(m => m.ProductosAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosCrear',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/crear-alumno/crear-alumno.component').then(m => m.CrearAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosEliminar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/eliminar-alumno/eliminar-alumno.component').then(m => m.EliminarAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposListar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-grupos/listado-grupos.component').then(m => m.ListadoGruposComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposCrear',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/crear-grupo/crear-grupo.component').then(m => m.CrearGrupoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposEditar/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-grupo/editar-grupo.component').then(m => m.EditarGrupoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gestionarAlumnos/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-alumnos.component').then(m => m.GestionarAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'seleccionarAlumnos/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/seleccionar-alumnos/seleccionar-alumnos.component').then(m => m.SeleccionarAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gestionarTurnosAlumno/:alumnoId',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-turnos-alumno/gestionar-turnos-alumno.component').then(m => m.GestionarTurnosAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'turnosCrear',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/crear-turno/crear-turno.component').then(m => m.CrearTurnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'turnosEditar/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-turno/editar-turno.component').then(m => m.EditarTurnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'eventosListar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-eventos/listado-eventos.component').then(m => m.ListadoEventosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'eventosCrear',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/crear-evento/crear-evento.component').then(m => m.CrearEventoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'eventosEditar/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-evento/editar-evento.component').then(m => m.EditarEventoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'productosListar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-productos/listado-productos.component').then(m => m.ListadoProductosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'productosCrear',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/crear-producto/crear-producto.component').then(m => m.CrearProductoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'productosEditar/:id',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/editar-producto/editar-producto.component').then(m => m.EditarProductoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'tesoreriaCobros',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/tesoreria-cobros/tesoreria-cobros.component').then(m => m.TesoreriaCobrosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'cajaSede',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/caja-sede/caja-sede.component').then(m => m.CajaSedeComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Bandeja de solicitudes al propietario (6c-7).
    path: 'solicitudes',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/solicitudes-bandeja/solicitudes-bandeja.component').then(m => m.SolicitudesBandejaComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'auditoriaSistema',
    data: privateRouteData(ROUTE_ROLE_SETS.adminOnly),
    loadComponent: () => import('./componentes/endpoints/auditoria-sistema/auditoria-sistema.component').then(m => m.AuditoriaSistemaComponent),
    canActivate: [roleGuard, adminOnlyGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'convocatoriasListar',
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./componentes/endpoints/listado-convocatorias/listado-convocatorias.component').then(m => m.ListadoConvocatoriasComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'configuracion-sistema',
    data: privateRouteData(ROUTE_ROLE_SETS.adminOnly),
    loadComponent: () => import('./componentes/endpoints/configuracion-sistema/configuracion-sistema.component').then(m => m.ConfiguracionSistemaComponent),
    canActivate: [roleGuard, adminOnlyGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Plataforma (todas las escuelas): sin requiredRoles ni catálogo; la sesión puede no tener membresía.
    // Sin portalContext: solo distingue el portal 'user', y así no suma bytes al bundle inicial (al límite).
    path: 'plataformaErrores',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/plataforma-errores/plataforma-errores.component').then(m => m.PlataformaErroresComponent),
    canActivate: [plataformaGuard],
  },
  {
    // «Mi caja» del profesor (6b-3b): sin catálogo ni requiredRoles; una sesión solo profesor solo puede usar su caja.
    path: 'mi-caja',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/mi-caja/mi-caja.component').then(m => m.MiCajaComponent),
    canActivate: [profesorGuard],
  },
  {
    // Profesor (E7-5): sus grupos, sus alumnos y la ficha reducida. Mismo guard que «Mi caja», sin catálogo.
    path: 'mis-grupos',
    data: { seo: SEO_ROUTES.noIndex },
    loadChildren: () => import('./componentes/endpoints/mis-grupos/mis-grupos.routes').then(m => m.MIS_GRUPOS_ROUTES),
    canActivate: [profesorGuard],
  },
  {
    path: '**',
    loadComponent: () => import('./componentes/vistas/no-encontrado/no-encontrado').then(m => m.NoEncontrado)
  }
];
