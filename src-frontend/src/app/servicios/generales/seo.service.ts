import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SEO_DEFAULTS, SeoMeta } from '../../core/constants/seo.constants';

export interface BreadcrumbItem {
  name: string;
  url: string;
}

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly defaultRobots =
    'index,follow,max-snippet:-1,max-image-preview:large,max-video-preview:-1';
  private readonly baseUrl = 'https://moiskimdo.es';

  private readonly routeBreadcrumbs: Record<string, BreadcrumbItem[]> = {
    '/inicio': [{ name: 'Inicio', url: '/inicio' }],
    '/taekwondo': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Taekwondo', url: '/taekwondo' },
    ],
    '/kickboxing': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Kickboxing', url: '/kickboxing' },
    ],
    '/pilates': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Pilates', url: '/pilates' },
    ],
    '/defensapersonalfemenina': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Defensa Personal Femenina', url: '/defensapersonalfemenina' },
    ],
    '/horarios': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Horarios', url: '/horarios' },
    ],
    '/eventos': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Eventos', url: '/eventos' },
    ],
    '/contacto': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Contacto', url: '/contacto' },
    ],
    '/tarifas': [
      { name: 'Inicio', url: '/inicio' },
      { name: 'Tarifas', url: '/tarifas' },
    ],
  };

  constructor(
    private router: Router,
    private title: Title,
    private meta: Meta,
    @Inject(DOCUMENT) private document: Document,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.applyRouteSeo();
      });
  }

  private applyRouteSeo(): void {
    const route = this.getDeepestRoute(this.router.routerState.snapshot.root);
    const seo = (route.data?.['seo'] as SeoMeta | undefined) ?? {};

    const title = seo.title ?? SEO_DEFAULTS.title;
    const description = seo.description ?? SEO_DEFAULTS.description;
    const keywords = seo.keywords ?? SEO_DEFAULTS.keywords;
    const ogImage = seo.ogImage ?? SEO_DEFAULTS.ogImage;
    const canonical = seo.canonical ?? this.buildCanonical(this.router.url);
    const robots = seo.noIndex ? 'noindex, nofollow' : this.defaultRobots;

    this.title.setTitle(title);
    this.updateMetaTag('name', 'description', description);
    this.updateMetaTag('name', 'keywords', keywords);
    this.updateMetaTag('name', 'robots', robots);
    this.updateMetaTag('property', 'og:title', title);
    this.updateMetaTag('property', 'og:description', description);
    this.updateMetaTag('property', 'og:url', canonical);
    this.updateMetaTag('property', 'og:image', ogImage);
    this.updateMetaTag('name', 'twitter:title', title);
    this.updateMetaTag('name', 'twitter:description', description);
    this.updateMetaTag('name', 'twitter:image', ogImage);

    this.setCanonical(canonical);
    this.updateBreadcrumbSchema(this.router.url);
  }

  /**
   * Actualiza el SEO dinámicamente desde un componente.
   * Útil para páginas con contenido dinámico como eventos/:id
   */
  updateDynamicSeo(config: {
    title: string;
    description: string;
    keywords?: string;
    ogImage?: string;
    canonical?: string;
    breadcrumbs?: BreadcrumbItem[];
  }): void {
    const canonical = config.canonical ?? this.buildCanonical(this.router.url);
    const ogImage = config.ogImage ?? SEO_DEFAULTS.ogImage;
    const keywords = config.keywords ?? SEO_DEFAULTS.keywords;

    this.title.setTitle(config.title);
    this.updateMetaTag('name', 'description', config.description);
    this.updateMetaTag('name', 'keywords', keywords);
    this.updateMetaTag('property', 'og:title', config.title);
    this.updateMetaTag('property', 'og:description', config.description);
    this.updateMetaTag('property', 'og:url', canonical);
    this.updateMetaTag('property', 'og:image', ogImage);
    this.updateMetaTag('name', 'twitter:title', config.title);
    this.updateMetaTag('name', 'twitter:description', config.description);
    this.updateMetaTag('name', 'twitter:image', ogImage);

    this.setCanonical(canonical);

    if (config.breadcrumbs) {
      this.setBreadcrumbSchema(config.breadcrumbs);
    }
  }

  private updateMetaTag(attr: 'name' | 'property', key: string, content: string): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}='${key}'`);
  }

  private setCanonical(url: string): void {
    // Works on both browser and server (prerendering)
    let link = this.document.querySelector("link[rel='canonical']") as HTMLLinkElement | null;
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private buildCanonical(url: string): string {
    const cleaned = url.split('?')[0].split('#')[0];
    if (!cleaned || cleaned === '/') {
      return `${this.baseUrl}/`;
    }
    return `${this.baseUrl}${cleaned}`;
  }

  private updateBreadcrumbSchema(url: string): void {
    const cleanUrl = url.split('?')[0].split('#')[0];
    const breadcrumbs = this.routeBreadcrumbs[cleanUrl];

    if (breadcrumbs) {
      this.setBreadcrumbSchema(breadcrumbs);
    }
  }

  private setBreadcrumbSchema(breadcrumbs: BreadcrumbItem[]): void {
    // Works on both browser and server (prerendering)
    const existingScript = this.document.querySelector('script[data-breadcrumb]');
    if (existingScript) {
      existingScript.remove();
    }

    const schema = {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: breadcrumbs.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: item.name,
        item: `${this.baseUrl}${item.url}`,
      })),
    };

    const script = this.document.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute('data-breadcrumb', 'true');
    script.textContent = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }

  private getDeepestRoute(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    let current = route;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current;
  }

  /**
   * Agrega Event Schema JSON-LD al documento para SEO de eventos
   */
  setEventSchema(config: {
    name: string;
    description: string;
    image?: string;
    url: string;
    startDate?: string;
    endDate?: string;
  }): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    // Eliminar schema de evento existente
    const existingScript = this.document.querySelector('script[data-event-schema]');
    if (existingScript) {
      existingScript.remove();
    }

    // Crear Event Schema
    const schema: any = {
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: config.name,
      description: config.description,
      eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
      eventStatus: 'https://schema.org/EventScheduled',
      location: {
        '@type': 'Place',
        name: 'Club Moi\'s Kim Do',
        address: {
          '@type': 'PostalAddress',
          streetAddress: 'Calle Parada de la Ciguena, 36',
          addressLocality: 'Umbrete',
          addressRegion: 'Sevilla',
          postalCode: '41806',
          addressCountry: 'ES'
        }
      },
      organizer: {
        '@type': 'Organization',
        name: 'Club Moi\'s Kim Do',
        url: 'https://moiskimdo.es'
      }
    };

    // Agregar imagen si está disponible
    if (config.image) {
      schema.image = [config.image];
    }

    // Agregar fechas si están disponibles
    if (config.startDate) {
      schema.startDate = config.startDate;
    }
    if (config.endDate) {
      schema.endDate = config.endDate;
    }

    // Agregar URL del evento
    schema.url = `${this.baseUrl}${config.url}`;

    const script = this.document.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute('data-event-schema', 'true');
    script.textContent = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }

  /**
   * Elimina el Event Schema del documento (útil al salir de la página de evento)
   */
  removeEventSchema(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const existingScript = this.document.querySelector('script[data-event-schema]');
    if (existingScript) {
      existingScript.remove();
    }
  }

  /**
   * Agrega Review Schema JSON-LD al documento para SEO de reseñas
   */
  setReviewsSchema(reviews: Array<{
    author: string;
    rating: number;
    text: string;
    date?: string;
  }>): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    // Eliminar schema de reseñas existente
    const existingScript = this.document.querySelector('script[data-reviews-schema]');
    if (existingScript) {
      existingScript.remove();
    }

    // Crear array de Review schemas
    const reviewSchemas = reviews.map(review => ({
      '@type': 'Review',
      author: {
        '@type': 'Person',
        name: review.author
      },
      reviewRating: {
        '@type': 'Rating',
        ratingValue: review.rating,
        bestRating: 5,
        worstRating: 1
      },
      reviewBody: review.text,
      ...(review.date && { datePublished: review.date })
    }));

    // Crear schema que incluye las reseñas en el LocalBusiness
    const schema = {
      '@context': 'https://schema.org',
      '@type': 'LocalBusiness',
      '@id': 'https://moiskimdo.es/#club',
      review: reviewSchemas
    };

    const script = this.document.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute('data-reviews-schema', 'true');
    script.textContent = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }
}
