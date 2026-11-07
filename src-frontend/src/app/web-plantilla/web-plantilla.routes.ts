import { Routes } from '@angular/router';

/** La cáscara (SkeletonComponent) y SeoService lo reconocen: solo el contenido, sin GA4 ni el SEO de Moiskimdo. */
const DATOS_PLANTILLA = { webPlantilla: true } as const;

const pendiente = () =>
  import('./paginas/plantilla-pendiente.component').then((m) => m.PlantillaPendienteComponent);

/**
 * Rutas de la web por plantilla (fase4-web-plan.md §3.1). Se cargan perezosas desde el grupo con `canMatch` de
 * `app.routes.ts`, que solo casa con la meta de `index-plantilla.html`. Sin comodín: lo que no casa aquí (panel, login,
 * plataforma…) sigue a las rutas de siempre. Cada página apunta por ahora a la pantalla técnica provisional; los temas
 * llegan en W5.
 */
export const WEB_PLANTILLA_ROUTES: Routes = [
  { path: '', pathMatch: 'full', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'sedes/:slug', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'preinscripcion', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'tarifas', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'eventos', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'eventos/:id', data: DATOS_PLANTILLA, loadComponent: pendiente },
  { path: 'legales', data: DATOS_PLANTILLA, loadComponent: pendiente },
];
