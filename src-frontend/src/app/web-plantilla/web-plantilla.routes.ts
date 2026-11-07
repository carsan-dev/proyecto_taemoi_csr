import { inject } from '@angular/core';
import { RedirectFunction, Router, Routes } from '@angular/router';

import type { PaginaWeb } from './comun/seo-escuela.service';

/**
 * `webPlantilla`: la cáscara (SkeletonComponent) y SeoService lo reconocen (solo el contenido, sin GA4 ni el SEO de
 * Moiskimdo). `pagina`: qué datos y qué SEO pone la página (`SeoEscuelaService`).
 */
const datos = (pagina: PaginaWeb) => ({ webPlantilla: true, pagina }) as const;

const pendiente = () =>
  import('./paginas/plantilla-pendiente.component').then((m) => m.PlantillaPendienteComponent);

/** Las páginas del primer tema (W5): cada una, su chunk perezoso. Solo hay un tema construido (§9 n.º 12). */
const portada = () =>
  import('./temas/linea-comarcal/portada/portada.component').then((m) => m.PortadaLineaComarcalComponent);
const sede = () => import('./temas/linea-comarcal/sede/sede.component').then((m) => m.SedeLineaComarcalComponent);
const tarifas = () =>
  import('./temas/linea-comarcal/tarifas/tarifas.component').then((m) => m.TarifasLineaComarcalComponent);
const eventos = () =>
  import('./temas/linea-comarcal/eventos/eventos.component').then((m) => m.EventosLineaComarcalComponent);
const evento = () =>
  import('./temas/linea-comarcal/evento/evento.component').then((m) => m.EventoLineaComarcalComponent);
const legales = () =>
  import('./temas/linea-comarcal/legales/legales.component').then((m) => m.LegalesLineaComarcalComponent);
export const noEncontradaDelTema = () =>
  import('./temas/linea-comarcal/no-encontrada/no-encontrada.component').then((m) => m.NoEncontradaLineaComarcalComponent);

/** Datos de la 404 del tema: ruta de la plantilla (cáscara sin cabecera de Moiskimdo), sin página de SEO propia. */
export const DATOS_NO_ENCONTRADA = { webPlantilla: true } as const;

/** Redirige a una página de la plantilla con su ancla (`/legales#privacidad`). */
const conAncla =
  (ruta: string, fragmento: string): RedirectFunction =>
  () =>
    inject(Router).createUrlTree([ruta], { fragment: fragmento });

/**
 * Rutas públicas solo de Moiskimdo (las de `app.routes.ts`) que no tienen equivalente en la plantilla: en un host de
 * plantilla dan la 404 del tema, nunca la página de Moiskimdo (§3.2 punto 5 y §9 n.º 10).
 */
export const RUTAS_SOLO_MOISKIMDO = [
  'aljarafe',
  'sevilla',
  'eltaekwondo',
  'taekwondo',
  'kickboxing',
  'pilates',
  'defensapersonalfemenina',
  'defensa-personal-femenina',
  'horarios',
] as const;

/**
 * Rutas de la web por plantilla (fase4-web-plan.md §3.1). Se cargan perezosas desde el grupo con `canMatch` de
 * `app.routes.ts`, que solo casa con la meta de `index-plantilla.html`. Sin comodín: lo que no casa aquí (panel, login,
 * plataforma…) sigue a las rutas de siempre, y lo desconocido cae en el comodín de la plantilla del final de
 * `app.routes.ts` (la 404 del tema). Portada, sede, tarifas, eventos, evento y legales son las del primer tema;
 * preinscripción, la pantalla técnica provisional hasta que se construya. Sin publicar, cada página pinta «Web aún no publicada».
 */
export const WEB_PLANTILLA_ROUTES: Routes = [
  { path: '', pathMatch: 'full', data: datos('portada'), loadComponent: portada },
  { path: 'sedes/:slug', data: datos('sede'), loadComponent: sede },
  { path: 'preinscripcion', data: datos('preinscripcion'), loadComponent: pendiente },
  { path: 'tarifas', data: datos('tarifas'), loadComponent: tarifas },
  { path: 'eventos', data: datos('eventos'), loadComponent: eventos },
  { path: 'eventos/:id', data: datos('evento'), loadComponent: evento },
  { path: 'legales', data: datos('legales'), loadComponent: legales },
  // Rutas de Moiskimdo con equivalente claro en la plantilla.
  { path: 'inicio', pathMatch: 'full', redirectTo: '' },
  { path: 'aviso-legal', pathMatch: 'full', redirectTo: conAncla('/legales', 'aviso-legal') },
  { path: 'politica-privacidad', pathMatch: 'full', redirectTo: conAncla('/legales', 'privacidad') },
  { path: 'politica-cookies', pathMatch: 'full', redirectTo: conAncla('/legales', 'cookies') },
  { path: 'contacto', pathMatch: 'full', redirectTo: conAncla('/', 'contacto') },
  // El resto de las solo de Moiskimdo: la 404 del tema.
  ...RUTAS_SOLO_MOISKIMDO.map((path) => ({ path, data: DATOS_NO_ENCONTRADA, loadComponent: noEncontradaDelTema })),
];
