import { Routes } from '@angular/router';

/**
 * Pantallas del profesor (E7-5), hijas de `/mis-grupos` (perezosa, con `profesorGuard` en la ruta padre). Sin catálogo:
 * las respuestas de `/api/profesor/**` ya traen los nombres de grado.
 */
export const MIS_GRUPOS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./mis-grupos.component').then((m) => m.MisGruposComponent),
  },
  {
    path: 'alumnos/:alumnoId',
    loadComponent: () => import('./ficha-profesor/ficha-profesor.component').then((m) => m.FichaProfesorComponent),
  },
  {
    path: ':grupoId',
    loadComponent: () => import('./grupo-profesor/grupo-profesor.component').then((m) => m.GrupoProfesorComponent),
  },
];
