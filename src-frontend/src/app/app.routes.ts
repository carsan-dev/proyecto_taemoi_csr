import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';
import { roleGuard } from './guards/role.guard';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { SEO_ROUTES } from './core/constants/seo.constants';

export const routes: Routes = [
  {
    path: '',
    data: { seo: SEO_ROUTES.home },
    component: EscaparatePrincipalComponent // Eager-load main page to prevent white screen
  },
  { path: 'inicio', redirectTo: '', pathMatch: 'full' },
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
    data: { seo: SEO_ROUTES.defensaPersonal },
    loadComponent: () => import('./componentes/vistas/defensa-personal-femenina/defensa-personal-femenina.component').then(m => m.DefensaPersonalFemeninaComponent)
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
    path: 'tarifas',
    data: { seo: SEO_ROUTES.tarifas },
    loadComponent: () => import('./componentes/vistas/tarifas/tarifas.component').then(m => m.TarifasComponent)
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
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/vista-principal-admin/vista-principal-admin.component').then(m => m.VistaPrincipalAdminComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'userpage',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/vista-principal-user/vista-principal-user.component').then(m => m.VistaPrincipalUserComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'userpage/turnos',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/vistas/vista-principal-user/turnos-usuario/turnos-usuario.component').then(m => m.TurnosUsuarioComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosListar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-alumnos/listado-alumnos.component').then(m => m.ListadoAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEditar/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEditar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
    pathMatch: 'full',
  },
  {
    path: 'alumnos/:id/productos',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-alumno/productos-alumno/productos-alumno.component').then(m => m.ProductosAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosCrear',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/crear-alumno/crear-alumno.component').then(m => m.CrearAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEliminar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/eliminar-alumno/eliminar-alumno.component').then(m => m.EliminarAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposListar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-grupos/listado-grupos.component').then(m => m.ListadoGruposComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposCrear',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/crear-grupo/crear-grupo.component').then(m => m.CrearGrupoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposEditar/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-grupo/editar-grupo.component').then(m => m.EditarGrupoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gestionarAlumnos/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-alumnos.component').then(m => m.GestionarAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'seleccionarAlumnos/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/seleccionar-alumnos/seleccionar-alumnos.component').then(m => m.SeleccionarAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gestionarTurnosAlumno/:alumnoId',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-turnos-alumno/gestionar-turnos-alumno.component').then(m => m.GestionarTurnosAlumnoComponent)
  },
  {
    path: 'turnosCrear',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/crear-turno/crear-turno.component').then(m => m.CrearTurnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'turnosEditar/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-turno/editar-turno.component').then(m => m.EditarTurnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosListar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-eventos/listado-eventos.component').then(m => m.ListadoEventosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosCrear',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/crear-evento/crear-evento.component').then(m => m.CrearEventoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosEditar/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-evento/editar-evento.component').then(m => m.EditarEventoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosListar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-productos/listado-productos.component').then(m => m.ListadoProductosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosCrear',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/crear-producto/crear-producto.component').then(m => m.CrearProductoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosEditar/:id',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/editar-producto/editar-producto.component').then(m => m.EditarProductoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'convocatoriasListar',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/listado-convocatorias/listado-convocatorias.component').then(m => m.ListadoConvocatoriasComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'configuracion-sistema',
    data: { seo: SEO_ROUTES.noIndex },
    loadComponent: () => import('./componentes/endpoints/configuracion-sistema/configuracion-sistema.component').then(m => m.ConfiguracionSistemaComponent),
    canActivate: [roleGuard],
  },
  {
    path: '**',
    loadComponent: () => import('./componentes/vistas/no-encontrado/no-encontrado').then(m => m.NoEncontrado)
  }
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
