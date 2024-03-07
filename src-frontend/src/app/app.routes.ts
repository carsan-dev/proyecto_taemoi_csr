import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { EscaparatePrincipalComponent } from './componentes/vistas/escaparate-principal/escaparate-principal.component';
import { VistaPrincipalAdminComponent } from './componentes/vistas/vista-principal-admin/vista-principal-admin.component';
import { SkeletonComponent } from './componentes/vistas/layout/skeleton/skeleton.component';
import { ListadoAlumnosCompletoDTOComponent } from './componentes/endpoints/listado-alumnos-completo/listado-alumnos-completo.component';

export const routes: Routes = [
  { path: '', component: SkeletonComponent, outlet: 'skeleton' },
  { path: '', component: EscaparatePrincipalComponent },
  { path: 'login', component: VistaLoginComponent },
  { path: 'vistaprincipal', component: VistaPrincipalAdminComponent },
  { path: 'listar-alumnos', component: ListadoAlumnosCompletoDTOComponent}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
