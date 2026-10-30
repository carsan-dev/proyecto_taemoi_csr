import { Routes } from '@angular/router';

import { salirSinGuardarGuard } from '../productos-alumno/salir-sin-guardar.guard';

export const MIS_GRUPOS_PANEL_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./mis-grupos.component').then(m => m.MisGruposPanelComponent) },
  {
    path: 'alumnos/:alumnoId',
    loadComponent: () => import('./ficha-profesor-panel.component').then(m => m.FichaProfesorPanelComponent),
  },
  {
    // Pasar lista (decisión 324): `?turno&fecha` opcionales; con marcas sin guardar pregunta antes de salir.
    path: ':grupoId/lista',
    data: { prefijoAsistencia: 'profesor' },
    loadComponent: () => import('../asistencia/pasar-lista.component').then(m => m.PasarListaComponent),
    canDeactivate: [salirSinGuardarGuard],
  },
  { path: ':grupoId', loadComponent: () => import('./grupo.component').then(m => m.GrupoPanelComponent) },
];
