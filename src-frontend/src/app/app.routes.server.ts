import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Static public pages - prerender for SEO
  { path: '', renderMode: RenderMode.Prerender },
  { path: 'inicio', renderMode: RenderMode.Prerender },
  { path: 'taekwondo', renderMode: RenderMode.Prerender },
  { path: 'kickboxing', renderMode: RenderMode.Prerender },
  { path: 'pilates', renderMode: RenderMode.Prerender },
  { path: 'defensapersonalfemenina', renderMode: RenderMode.Prerender },
  { path: 'contacto', renderMode: RenderMode.Prerender },
  { path: 'tarifas', renderMode: RenderMode.Prerender },
  { path: 'horarios', renderMode: RenderMode.Prerender },
  { path: 'eventos', renderMode: RenderMode.Prerender },

  // All other routes - client-side rendering only
  { path: '**', renderMode: RenderMode.Client },
];
