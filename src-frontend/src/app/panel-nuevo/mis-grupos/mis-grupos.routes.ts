import { Routes } from '@angular/router';

export const MIS_GRUPOS_PANEL_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./mis-grupos.component').then(m => m.MisGruposPanelComponent) },
  {
    path: 'alumnos/:alumnoId',
    loadComponent: () => import('./ficha-profesor-panel.component').then(m => m.FichaProfesorPanelComponent),
  },
  { path: ':grupoId', loadComponent: () => import('./grupo.component').then(m => m.GrupoPanelComponent) },
];
