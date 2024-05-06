import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaPrincipalAdminComponent } from './componentes/vistas/vista-principal-admin/vista-principal-admin.component';
import { ListadoAlumnosCompletoDTOComponent } from './componentes/endpoints/listado-alumnos-completo/listado-alumnos-completo.component';
import { CrearAlumnoComponent } from './componentes/endpoints/crear-alumno/crear-alumno.component';
import { EliminarAlumnoComponent } from './componentes/endpoints/eliminar-alumno/eliminar-alumno.component';
import { EventosComponent } from './componentes/vistas/eventos/eventos.component';
import { ContactoComponent } from './componentes/vistas/contacto/contacto.component';

export const routes: Routes = [
  { path: 'inicio', component: EscaparatePrincipalComponent },
  { path: 'login', component: VistaLoginComponent },
  { path: 'adminpage', component: VistaPrincipalAdminComponent },
  { path: 'alumnos', component: ListadoAlumnosCompletoDTOComponent},
  { path: 'alumnos/crear', component: CrearAlumnoComponent},
  { path: 'alumnos/eliminar', component: EliminarAlumnoComponent},
  { path: 'logros', component: EventosComponent},
  { path: 'contacto', component: ContactoComponent},
  { path: '', redirectTo: '/inicio', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
