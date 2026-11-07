import { Routes } from '@angular/router';

import type { PaginaWeb } from './comun/seo-escuela.service';

/**
 * `webPlantilla`: la cáscara (SkeletonComponent) y SeoService lo reconocen (solo el contenido, sin GA4 ni el SEO de
 * Moiskimdo). `pagina`: qué datos y qué SEO pone la página (`SeoEscuelaService`).
 */
const datos = (pagina: PaginaWeb) => ({ webPlantilla: true, pagina }) as const;

const pendiente = () =>
  import('./paginas/plantilla-pendiente.component').then((m) => m.PlantillaPendienteComponent);

/**
 * Rutas de la web por plantilla (fase4-web-plan.md §3.1). Se cargan perezosas desde el grupo con `canMatch` de
 * `app.routes.ts`, que solo casa con la meta de `index-plantilla.html`. Sin comodín: lo que no casa aquí (panel, login,
 * plataforma…) sigue a las rutas de siempre. Cada página apunta por ahora a la pantalla técnica provisional; los temas
 * llegan en W5.
 */
export const WEB_PLANTILLA_ROUTES: Routes = [
  { path: '', pathMatch: 'full', data: datos('portada'), loadComponent: pendiente },
  { path: 'sedes/:slug', data: datos('sede'), loadComponent: pendiente },
  { path: 'preinscripcion', data: datos('preinscripcion'), loadComponent: pendiente },
  { path: 'tarifas', data: datos('tarifas'), loadComponent: pendiente },
  { path: 'eventos', data: datos('eventos'), loadComponent: pendiente },
  { path: 'eventos/:id', data: datos('evento'), loadComponent: pendiente },
  { path: 'legales', data: datos('legales'), loadComponent: pendiente },
];
