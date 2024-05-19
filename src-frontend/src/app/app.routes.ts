import { NgModule } from '@angular/core';
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

export const routes: Routes = [
  { path: 'inicio', component: EscaparatePrincipalComponent },
  { path: 'eltaekwondo', component: EltaekwondoComponent },
  { path: 'horarios', component: HorariosComponent },
  { path: 'logros', component: EventosComponent },
  { path: 'contacto', component: ContactoComponent },
  { path: 'login', component: VistaLoginComponent },
  { path: 'adminpage', component: VistaPrincipalAdminComponent },
  { path: 'alumnosListar', component: ListadoAlumnosComponent },
  { path: 'alumnosEditar', component: EditarAlumnoComponent },
  { path: 'alumnosCrear', component: CrearAlumnoComponent },
  { path: 'alumnosEliminar', component: EliminarAlumnoComponent },
  { path: 'gruposListar', component: ListadoGruposComponent },
  { path: 'turnosGrupo/:id', component: TurnosGrupoComponent },
  { path: '', redirectTo: '/inicio', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
