import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VistaLoginComponent } from './componentes/vistas/vista-login/vista-login.component';
import { EjemploComponent } from './componentes/ejemplo/ejemplo.component';
import { EscaparatePrincipalComponent } from './componentes/vistas/layout/escaparate-principal/escaparate-principal.component';

export const routes: Routes = [
  { path: '', component: EscaparatePrincipalComponent },
  { path: 'login', component: VistaLoginComponent },
  { path: 'ejemplo', component: EjemploComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
