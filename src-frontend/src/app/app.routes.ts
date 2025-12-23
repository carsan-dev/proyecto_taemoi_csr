import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';
import { roleGuard } from './guards/role.guard';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';

export const routes: Routes = [
  { path: '', redirectTo: '/inicio', pathMatch: 'full' },
  {
    path: 'inicio',
    component: EscaparatePrincipalComponent // Eager-load main page to prevent white screen
  },
  {
    path: 'eltaekwondo',
    loadComponent: () => import('./componentes/vistas/eltaekwondo/eltaekwondo.component').then(m => m.EltaekwondoComponent)
  },
  {
    path: 'kickboxing',
    loadComponent: () => import('./componentes/vistas/kickboxing/kickboxing.component').then(m => m.KickboxingComponent)
  },
  {
    path: 'pilates',
    loadComponent: () => import('./componentes/vistas/pilates/pilates.component').then(m => m.PilatesComponent)
  },
  {
    path: 'defensapersonalfemenina',
    loadComponent: () => import('./componentes/vistas/defensa-personal-femenina/defensa-personal-femenina.component').then(m => m.DefensaPersonalFemeninaComponent)
  },
  {
    path: 'horarios',
    loadComponent: () => import('./componentes/vistas/horarios/horarios.component').then(m => m.HorariosComponent)
  },
  {
    path: 'eventos',
    loadComponent: () => import('./componentes/vistas/eventos/eventos.component').then(m => m.EventosComponent)
  },
  {
    path: 'eventos/:eventoId',
    loadComponent: () => import('./componentes/vistas/eventos/evento-detalle/evento-detalle.component').then(m => m.EventoDetalleComponent)
  },
  {
    path: 'contacto',
    loadComponent: () => import('./componentes/vistas/contacto/contacto.component').then(m => m.ContactoComponent)
  },
  {
    path: 'login',
    component: VistaLoginComponent // Eager-load login page for better UX
  },
  {
    path: 'adminpage',
    loadComponent: () => import('./componentes/vistas/vista-principal-admin/vista-principal-admin.component').then(m => m.VistaPrincipalAdminComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'userpage',
    loadComponent: () => import('./componentes/vistas/vista-principal-user/vista-principal-user.component').then(m => m.VistaPrincipalUserComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'userpage/turnos',
    loadComponent: () => import('./componentes/vistas/vista-principal-user/turnos-usuario/turnos-usuario.component').then(m => m.TurnosUsuarioComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosListar',
    loadComponent: () => import('./componentes/endpoints/listado-alumnos/listado-alumnos.component').then(m => m.ListadoAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEditar',
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEditar/:id',
    loadComponent: () => import('./componentes/endpoints/editar-alumno/editar-alumno.component').then(m => m.EditarAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnos/:id/productos',
    loadComponent: () => import('./componentes/endpoints/editar-alumno/productos-alumno/productos-alumno.component').then(m => m.ProductosAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosCrear',
    loadComponent: () => import('./componentes/endpoints/crear-alumno/crear-alumno.component').then(m => m.CrearAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEliminar',
    loadComponent: () => import('./componentes/endpoints/eliminar-alumno/eliminar-alumno.component').then(m => m.EliminarAlumnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposListar',
    loadComponent: () => import('./componentes/endpoints/listado-grupos/listado-grupos.component').then(m => m.ListadoGruposComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposCrear',
    loadComponent: () => import('./componentes/endpoints/crear-grupo/crear-grupo.component').then(m => m.CrearGrupoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gruposEditar/:id',
    loadComponent: () => import('./componentes/endpoints/editar-grupo/editar-grupo.component').then(m => m.EditarGrupoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gestionarAlumnos/:id',
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-alumnos.component').then(m => m.GestionarAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'seleccionarAlumnos/:id',
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/seleccionar-alumnos/seleccionar-alumnos.component').then(m => m.SeleccionarAlumnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'gestionarTurnosAlumno/:alumnoId',
    loadComponent: () => import('./componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-turnos-alumno/gestionar-turnos-alumno.component').then(m => m.GestionarTurnosAlumnoComponent)
  },
  {
    path: 'turnosListar',
    loadComponent: () => import('./componentes/endpoints/listado-turnos/listado-turnos.component').then(m => m.ListadoTurnosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'turnosCrear',
    loadComponent: () => import('./componentes/endpoints/crear-turno/crear-turno.component').then(m => m.CrearTurnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'turnosEditar/:id',
    loadComponent: () => import('./componentes/endpoints/editar-turno/editar-turno.component').then(m => m.EditarTurnoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosListar',
    loadComponent: () => import('./componentes/endpoints/listado-eventos/listado-eventos.component').then(m => m.ListadoEventosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosCrear',
    loadComponent: () => import('./componentes/endpoints/crear-evento/crear-evento.component').then(m => m.CrearEventoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'eventosEditar/:id',
    loadComponent: () => import('./componentes/endpoints/editar-evento/editar-evento.component').then(m => m.EditarEventoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosListar',
    loadComponent: () => import('./componentes/endpoints/listado-productos/listado-productos.component').then(m => m.ListadoProductosComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosCrear',
    loadComponent: () => import('./componentes/endpoints/crear-producto/crear-producto.component').then(m => m.CrearProductoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'productosEditar/:id',
    loadComponent: () => import('./componentes/endpoints/editar-producto/editar-producto.component').then(m => m.EditarProductoComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'convocatoriasListar',
    loadComponent: () => import('./componentes/endpoints/listado-convocatorias/listado-convocatorias.component').then(m => m.ListadoConvocatoriasComponent),
    canActivate: [roleGuard],
  },
  {
    path: 'configuracion-sistema',
    loadComponent: () => import('./componentes/endpoints/configuracion-sistema/configuracion-sistema.component').then(m => m.ConfiguracionSistemaComponent),
    canActivate: [roleGuard],
  }
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
