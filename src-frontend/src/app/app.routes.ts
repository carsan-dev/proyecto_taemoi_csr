import { Routes } from '@angular/router';
import { roleGuard } from './guards/role.guard';
import { adminOnlyGuard } from './guards/admin-only.guard';
import { plataformaGuard } from './guards/plataforma.guard';
import { profesorGuard } from './guards/profesor.guard';
import { misGruposGuard } from './guards/mis-grupos.guard';
import { estilosPanelGuard } from './panel-nuevo/estilos-panel.guard';
import { estilosGestionGuard } from './panel-nuevo/estilos-gestion.guard';
import { gestionPanelGuard } from './panel-nuevo/no-encontrada/gestion-panel.guard';
import { salirSinGuardarGuard } from './panel-nuevo/productos-alumno/salir-sin-guardar.guard';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { SEO_ROUTES } from './core/constants/seo.constants';
import { RouteScrollPolicy } from './servicios/generales/page-scroll.service';
import { catalogoResolver } from './features/catalogo/catalogo.resolver';

/** Las rutas autenticadas esperan al catálogo (tras los guards) para que los componentes lo lean de forma síncrona. */
const CATALOGO_RESOLVE = { catalogo: catalogoResolver };

type RouteRole = 'ROLE_ADMIN' | 'ROLE_MANAGER' | 'ROLE_USER' | 'ROLE_RESPONSABLE_SEDE';
export type PortalContext = 'public' | 'admin' | 'user';
export interface AppRouteData {
  portalContext?: PortalContext;
  scrollPolicy?: RouteScrollPolicy;
}

const ROUTE_ROLE_SETS = {
  management: ['ROLE_ADMIN', 'ROLE_MANAGER'] as const satisfies readonly RouteRole[],
  /** Las pantallas de la decisión 370: gestión y responsable de sede; `roleGuard` exige a este PANEL_SEDE. */
  panelSede: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_RESPONSABLE_SEDE'] as const satisfies readonly RouteRole[],
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
    // Enlace del correo de invitación (pieza E8-6): pública, como restablecer la contraseña.
    path: 'crear-contrasena',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/crear-contrasena/crear-contrasena.component').then(m => m.CrearContrasenaComponent)
  },
  {
    path: 'registro-confirmar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/registro-confirmar/registro-confirmar.component').then(m => m.RegistroConfirmarComponent)
  },
  {
    path: 'adminpage',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/inicio/inicio.component').then(m => m.InicioComponent),
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
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/alumnos/alumnos.component').then(m => m.AlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'preinscripciones',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/preinscripciones/preinscripciones.component').then(m => m.PreinscripcionesPanelComponent),
    canActivate: [roleGuard],
    canDeactivate: [salirSinGuardarGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosEditar/:id',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/ficha/ficha-alumno.component').then(m => m.FichaAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Ficha sin alumno (decisión 333): la ficha nueva solo existe con id; sin él, al listado.
    path: 'alumnosEditar',
    redirectTo: '/alumnosListar',
    pathMatch: 'full',
  },
  {
    path: 'alumnos/:id/productos',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/productos-alumno/productos-alumno-panel.component').then(m => m.ProductosAlumnoPanelComponent),
    canActivate: [roleGuard],
    canDeactivate: [salirSinGuardarGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // «Mi perfil» (decisión 322): nombre y cumpleaños de la cuenta; propietario, gestor y responsable (`/api/panel/**`).
    path: 'panel/mi-perfil',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/mi-perfil/mi-perfil.component').then(m => m.MiPerfilComponent),
    canActivate: [roleGuard],
    canDeactivate: [salirSinGuardarGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'alumnosCrear',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/alta-alumno/alta-alumno.component').then(m => m.AltaAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // «Bajas y eliminaciones» (8b-3l): «Eliminar definitivamente» lo ve solo el propietario (y el servidor solo se lo
    // admite a él).
    path: 'alumnosEliminar',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/bajas-alumnos/bajas-alumnos.component').then(m => m.BajasAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposListar',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/grupos/grupos.component').then(m => m.GruposComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Pasar lista desde gestión (decisión 324): las mismas rutas de asistencia bajo `/api/grupos/{id}`.
    path: 'gruposListar/:grupoId/lista',
    canMatch: [estilosPanelGuard],
    data: { ...privateRouteData(ROUTE_ROLE_SETS.panelSede), prefijoAsistencia: 'gestion' },
    loadComponent: () => import('./panel-nuevo/asistencia/pasar-lista.component').then(m => m.PasarListaComponent),
    canActivate: [roleGuard],
    canDeactivate: [salirSinGuardarGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposCrear',
    canMatch: [estilosPanelGuard],
    data: { ...privateRouteData(ROUTE_ROLE_SETS.panelSede), crearGrupo: true },
    loadComponent: () => import('./panel-nuevo/grupos/grupos.component').then(m => m.GruposComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gruposEditar/:id',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/grupos/editar-grupo.component').then(m => m.EditarGrupoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gestionarAlumnos/:id',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/grupos/gestionar-alumnos.component').then(m => m.GestionarAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'seleccionarAlumnos/:id',
    canMatch: [estilosPanelGuard],
    data: { ...privateRouteData(ROUTE_ROLE_SETS.panelSede), seleccionarAlumnos: true },
    loadComponent: () => import('./panel-nuevo/grupos/gestionar-alumnos.component').then(m => m.GestionarAlumnosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'gestionarTurnosAlumno/:alumnoId',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/grupos/gestionar-turnos-alumno.component').then(m => m.GestionarTurnosAlumnoComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'turnosCrear',
    canMatch: [estilosPanelGuard],
    data: { ...privateRouteData(ROUTE_ROLE_SETS.panelSede), crearTurno: true },
    loadComponent: () => import('./panel-nuevo/grupos/grupos.component').then(m => m.GruposComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'turnosEditar/:id',
    canMatch: [estilosPanelGuard],
    data: { ...privateRouteData(ROUTE_ROLE_SETS.panelSede), editarTurno: true },
    loadComponent: () => import('./panel-nuevo/grupos/grupos.component').then(m => m.GruposComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'eventosListar',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/eventos/eventos.component').then(m => m.EventosPanelComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Crear evento del panel nuevo (8b-3f), el mismo formulario que editar.
    path: 'eventosCrear',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/eventos/evento-formulario.component').then(m => m.EventoFormularioComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Editar evento del panel nuevo (8b-3f).
    path: 'eventosEditar/:id',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/eventos/evento-formulario.component').then(m => m.EventoFormularioComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'productosListar',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/productos/productos.component').then(m => m.ProductosComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Crear producto del panel nuevo (8b-3g), el mismo formulario que editar.
    path: 'productosCrear',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/productos/producto-formulario.component').then(m => m.ProductoFormularioComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Editar producto del panel nuevo (8b-3g).
    path: 'productosEditar/:id',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/productos/producto-formulario.component').then(m => m.ProductoFormularioComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'tesoreriaCobros',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/tesoreria/tesoreria.component').then(m => m.TesoreriaComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'cajaSede',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/caja-sede/caja-sede.component').then(m => m.CajaSedePanelComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'solicitudes',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/solicitudes/solicitudes.component').then(m => m.SolicitudesPanelComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'auditoriaSistema',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.adminOnly),
    loadComponent: () => import('./panel-nuevo/escuela/registro/registro.component').then(m => m.RegistroAccionesComponent),
    canActivate: [roleGuard, adminOnlyGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'convocatoriasListar',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.panelSede),
    loadComponent: () => import('./panel-nuevo/convocatorias/convocatorias.component').then(m => m.ConvocatoriasComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Sedes de la escuela (E9-3): propietario y gestión; la entrada del menú sale con la capacidad SEDES.
    path: 'sedes',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/sedes/sedes.component').then(m => m.SedesPanelComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Miembros de la escuela (E9-4): propietario y gestión; la entrada del menú sale con la capacidad MIEMBROS.
    path: 'miembros',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.management),
    loadComponent: () => import('./panel-nuevo/miembros/miembros.component').then(m => m.MiembrosPanelComponent),
    canActivate: [roleGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    path: 'configuracion-sistema',
    canMatch: [estilosPanelGuard],
    data: privateRouteData(ROUTE_ROLE_SETS.adminOnly),
    loadComponent: () => import('./panel-nuevo/escuela/configuracion/configuracion.component').then(m => m.ConfiguracionPanelComponent),
    canActivate: [roleGuard, adminOnlyGuard],
    resolve: CATALOGO_RESOLVE,
  },
  {
    // Plataforma (todas las escuelas): sin requiredRoles ni catálogo; la sesión puede no tener membresía.
    // Sin portalContext: solo distingue el portal 'user', y así no suma bytes al bundle inicial (al límite).
    path: 'plataformaErrores',
    canMatch: [estilosGestionGuard],
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/plataforma-errores/plataforma-errores.component').then(m => m.PlataformaErroresComponent),
    canActivate: [plataformaGuard],
  },
  {
    // Logo de cada escuela (E7-7b): misma sesión de plataforma y mismo guard que los errores.
    path: 'plataformaLogos',
    canMatch: [estilosGestionGuard],
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/plataforma-logos/plataforma-logos.component').then(m => m.PlataformaLogosComponent),
    canActivate: [plataformaGuard],
  },
  {
    // Mi caja del panel nuevo (8b-3p): carga diferida y el mismo permiso, sin catálogo.
    path: 'mi-caja',
    canMatch: [estilosPanelGuard],
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./panel-nuevo/mi-caja/mi-caja.component').then(m => m.MiCajaPanelComponent),
    canActivate: [profesorGuard],
  },
  {
    // Mis grupos del panel nuevo: carga diferida y el mismo permiso, sin catálogo.
    path: 'mis-grupos',
    canMatch: [estilosPanelGuard],
    data: { seo: SEO_ROUTES.noIndex },
    loadChildren: () => import('./panel-nuevo/mis-grupos/mis-grupos.routes').then(m => m.MIS_GRUPOS_PANEL_ROUTES),
    canActivate: [misGruposGuard],
  },
  {
    // 404 dentro del panel (8b-M2b): solo con una sesión de gestión o de responsable; el resto cae al 404 público.
    // `panelNuevo` avisa a la cáscara (SkeletonComponent) de que debe pintar la cabecera del panel aunque la URL sea desconocida.
    path: '**',
    canMatch: [gestionPanelGuard],
    canActivate: [estilosPanelGuard],
    data: { seo: SEO_ROUTES.noIndex, panelNuevo: true },
    loadComponent: () => import('./panel-nuevo/no-encontrada/no-encontrada.component').then(m => m.NoEncontradaPanelComponent),
  },
  {
    path: '**',
    loadComponent: () => import('./componentes/vistas/no-encontrado/no-encontrado').then(m => m.NoEncontrado)
  }
];
