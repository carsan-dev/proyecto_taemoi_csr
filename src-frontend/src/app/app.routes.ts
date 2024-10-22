import { RouterModule, Routes } from '@angular/router';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaPrincipalAdminComponent } from './componentes/vistas/vista-principal-admin/vista-principal-admin.component';
import { EditarAlumnoComponent } from './componentes/endpoints/editar-alumno/editar-alumno.component';
import { CrearAlumnoComponent } from './componentes/endpoints/crear-alumno/crear-alumno.component';
import { EliminarAlumnoComponent } from './componentes/endpoints/eliminar-alumno/eliminar-alumno.component';
import { EventosComponent } from './componentes/vistas/eventos/eventos.component';
import { ContactoComponent } from './componentes/vistas/contacto/contacto.component';
import { EltaekwondoComponent } from './componentes/vistas/eltaekwondo/eltaekwondo.component';
import { HorariosComponent } from './componentes/vistas/horarios/horarios.component';
import { ListadoAlumnosComponent } from './componentes/endpoints/listado-alumnos/listado-alumnos.component';
import { ListadoGruposComponent } from './componentes/endpoints/listado-grupos/listado-grupos.component';
import { TurnosGrupoComponent } from './componentes/endpoints/listado-grupos/turnos-grupo/turnos-grupo.component';
import { VistaPrincipalUserComponent } from './componentes/vistas/vista-principal-user/vista-principal-user.component';
import { CrearGrupoComponent } from './componentes/endpoints/crear-grupo/crear-grupo.component';
import { EditarGrupoComponent } from './componentes/endpoints/editar-grupo/editar-grupo.component';
import { GestionarAlumnosComponent } from './componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-alumnos.component';
import { SeleccionarAlumnosComponent } from './componentes/endpoints/listado-grupos/gestionar-alumnos/seleccionar-alumnos/seleccionar-alumnos.component';
import { ListadoTurnosComponent } from './componentes/endpoints/listado-turnos/listado-turnos.component';
import { CrearTurnoComponent } from './componentes/endpoints/crear-turno/crear-turno.component';
import { SeleccionarGrupoComponent } from './componentes/endpoints/listado-turnos/seleccionar-grupo/seleccionar-grupo.component';
import { EditarTurnoComponent } from './componentes/endpoints/editar-turno/editar-turno.component';
import { TurnosUsuarioComponent } from './componentes/vistas/vista-principal-user/turnos-usuario/turnos-usuario.component';
import { roleGuard } from './guards/role.guard';
import { ListadoEventosComponent } from './componentes/endpoints/listado-eventos/listado-eventos.component';
import { CrearEventoComponent } from './componentes/endpoints/crear-evento/crear-evento.component';
import { EditarEventoComponent } from './componentes/endpoints/editar-evento/editar-evento.component';
import { NgModule } from '@angular/core';
import { GestionarTurnosAlumnoComponent } from './componentes/endpoints/listado-grupos/gestionar-alumnos/gestionar-turnos-alumno/gestionar-turnos-alumno.component';
import { KickboxingComponent } from './componentes/vistas/kickboxing/kickboxing.component';
import { PilatesComponent } from './componentes/vistas/pilates/pilates.component';
import { EventoDetalleComponent } from './componentes/vistas/eventos/evento-detalle/evento-detalle.component';
import { ListadoExamenesComponent } from './componentes/endpoints/listado-examenes/listado-examenes.component';

export const routes: Routes = [
  { path: '', redirectTo: '/inicio', pathMatch: 'full' },
  { path: 'inicio', component: EscaparatePrincipalComponent },
  { path: 'eltaekwondo', component: EltaekwondoComponent },
  { path: 'kickboxing', component: KickboxingComponent },
  { path: 'pilates', component: PilatesComponent },
  { path: 'horarios', component: HorariosComponent },
  { path: 'eventos', component: EventosComponent },
  { path: 'eventos/:eventoId', component: EventoDetalleComponent },
  { path: 'contacto', component: ContactoComponent },
  { path: 'login', component: VistaLoginComponent },
  {
    path: 'adminpage',
    component: VistaPrincipalAdminComponent,
    canActivate: [roleGuard],
  },
  { path: 'userpage', component: VistaPrincipalUserComponent },
  { path: 'userpage/:id/turnos', component: TurnosUsuarioComponent },
  {
    path: 'alumnosListar',
    component: ListadoAlumnosComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEditar',
    component: EditarAlumnoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosCrear',
    component: CrearAlumnoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'alumnosEliminar',
    component: EliminarAlumnoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'examenesListar',
    component: ListadoExamenesComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'gruposListar',
    component: ListadoGruposComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'gruposCrear',
    component: CrearGrupoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'gruposEditar/:id',
    component: EditarGrupoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'gestionarAlumnos/:id',
    component: GestionarAlumnosComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'seleccionarAlumnos/:id',
    component: SeleccionarAlumnosComponent,
    canActivate: [roleGuard],
  },
  { path: 'gestionarAlumnos/:id', component: GestionarAlumnosComponent },
  { path: 'gestionarTurnosAlumno/:alumnoId', component: GestionarTurnosAlumnoComponent },
  {
    path: 'turnosGrupo/:id',
    component: TurnosGrupoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'turnosListar',
    component: ListadoTurnosComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'turnosCrear',
    component: CrearTurnoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'seleccionarGrupo/:turnoId',
    component: SeleccionarGrupoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'turnosEditar/:id',
    component: EditarTurnoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'eventosListar',
    component: ListadoEventosComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'eventosCrear',
    component: CrearEventoComponent,
    canActivate: [roleGuard],
  },
  {
    path: 'eventosEditar/:id',
    component: EditarEventoComponent,
    canActivate: [roleGuard],
  },
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
