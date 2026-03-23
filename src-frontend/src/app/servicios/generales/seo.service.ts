import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SEO_DEFAULTS, SEO_ROUTES, SeoMeta } from '../../core/constants/seo.constants';

export interface BreadcrumbItem {
  name: string;
  url: string;
}

type JsonLdSchema = Record<string, unknown>;

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly defaultRobots =
    'index,follow,max-snippet:-1,max-image-preview:large,max-video-preview:-1';
  private readonly baseUrl = 'https://moiskimdo.es';

  private readonly routeBreadcrumbs: Record<string, BreadcrumbItem[]> = {
    '/': [{ name: 'Inicio', url: '/' }],
    '/inicio': [{ name: 'Inicio', url: '/' }],
    '/taekwondo': [
      { name: 'Inicio', url: '/' },
      { name: 'Taekwondo', url: '/taekwondo' },
    ],
    '/aljarafe': [
      { name: 'Inicio', url: '/' },
      { name: 'Aljarafe', url: '/aljarafe' },
    ],
    '/sevilla': [
      { name: 'Inicio', url: '/' },
      { name: 'Sevilla', url: '/sevilla' },
    ],
    '/kickboxing': [
      { name: 'Inicio', url: '/' },
      { name: 'Kickboxing', url: '/kickboxing' },
    ],
    '/pilates': [
      { name: 'Inicio', url: '/' },
      { name: 'Pilates', url: '/pilates' },
    ],
    '/defensa-personal-femenina': [
      { name: 'Inicio', url: '/' },
      { name: 'Defensa Personal Femenina', url: '/defensa-personal-femenina' },
    ],
    '/horarios': [
      { name: 'Inicio', url: '/' },
      { name: 'Horarios', url: '/horarios' },
    ],
    '/eventos': [
      { name: 'Inicio', url: '/' },
      { name: 'Eventos', url: '/eventos' },
    ],
    '/contacto': [
      { name: 'Inicio', url: '/' },
      { name: 'Contacto', url: '/contacto' },
    ],
    '/politica-privacidad': [
      { name: 'Inicio', url: '/' },
      { name: 'Politica de privacidad', url: '/politica-privacidad' },
    ],
    '/politica-cookies': [
      { name: 'Inicio', url: '/' },
      { name: 'Politica de cookies', url: '/politica-cookies' },
    ],
    '/aviso-legal': [
      { name: 'Inicio', url: '/' },
      { name: 'Aviso legal', url: '/aviso-legal' },
    ],
    '/tarifas': [
      { name: 'Inicio', url: '/' },
      { name: 'Tarifas', url: '/tarifas' },
    ],
  };

  constructor(
    private readonly router: Router,
    private readonly title: Title,
    private readonly meta: Meta,
    @Inject(DOCUMENT) private readonly document: Document,
    @Inject(PLATFORM_ID) private readonly platformId: Object,
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
    const cleanPath = this.getCleanPath(this.router.url);

    const title = seo.title ?? SEO_DEFAULTS.title;
    const description = seo.description ?? SEO_DEFAULTS.description;
    const ogImage = seo.ogImage ?? SEO_DEFAULTS.ogImage;
    const canonical = seo.canonical ?? this.buildCanonical(this.router.url);
    const robots = seo.noIndex ? 'noindex, nofollow' : this.defaultRobots;

    this.title.setTitle(title);
    this.updateMetaTag('name', 'description', description);
    this.removeMetaTag('name', 'keywords');
    this.updateMetaTag('name', 'robots', robots);
    this.updateMetaTag('name', 'googlebot', robots);
    this.updateMetaTag('property', 'og:title', title);
    this.updateMetaTag('property', 'og:description', description);
    this.updateMetaTag('property', 'og:url', canonical);
    this.updateMetaTag('property', 'og:image', ogImage);
    this.updateMetaTag('property', 'og:image:alt', `Imagen de ${title}`);
    this.updateMetaTag('property', 'og:type', 'website');
    this.updateMetaTag('name', 'twitter:title', title);
    this.updateMetaTag('name', 'twitter:description', description);
    this.updateMetaTag('name', 'twitter:image', ogImage);
    this.updateMetaTag('name', 'twitter:card', 'summary_large_image');

    this.setCanonical(canonical);
    this.setHreflang(canonical);
    this.updateBreadcrumbSchema(cleanPath);
    this.updateRouteSchemas(cleanPath);
  }

  updateDynamicSeo(config: {
    title: string;
    description: string;
    ogImage?: string;
    canonical?: string;
    breadcrumbs?: BreadcrumbItem[];
    noIndex?: boolean;
    ogType?: 'article' | 'website';
  }): void {
    const canonical = config.canonical ?? this.buildCanonical(this.router.url);
    const ogImage = config.ogImage ?? SEO_DEFAULTS.ogImage;
    const robots = config.noIndex ? 'noindex, nofollow' : this.defaultRobots;

    this.title.setTitle(config.title);
    this.updateMetaTag('name', 'description', config.description);
    this.removeMetaTag('name', 'keywords');
    this.updateMetaTag('name', 'robots', robots);
    this.updateMetaTag('name', 'googlebot', robots);
    this.updateMetaTag('property', 'og:title', config.title);
    this.updateMetaTag('property', 'og:description', config.description);
    this.updateMetaTag('property', 'og:url', canonical);
    this.updateMetaTag('property', 'og:image', ogImage);
    this.updateMetaTag('property', 'og:image:alt', `Imagen de ${config.title}`);
    this.updateMetaTag('property', 'og:type', config.ogType ?? 'website');
    this.updateMetaTag('name', 'twitter:title', config.title);
    this.updateMetaTag('name', 'twitter:description', config.description);
    this.updateMetaTag('name', 'twitter:image', ogImage);
    this.updateMetaTag('name', 'twitter:card', 'summary_large_image');

    this.setCanonical(canonical);
    this.setHreflang(canonical);

    if (config.breadcrumbs) {
      this.setBreadcrumbSchema(config.breadcrumbs);
    } else {
      this.updateBreadcrumbSchema(this.getCleanPath(this.router.url));
    }

    this.updateRouteSchemas(this.getCleanPath(this.router.url));
  }

  private updateMetaTag(
    attr: 'name' | 'property',
    key: string,
    content: string,
  ): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}='${key}'`);
  }

  private removeMetaTag(attr: 'name' | 'property', key: string): void {
    this.meta.removeTag(`${attr}='${key}'`);
  }

  private setCanonical(url: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    let link = this.document.querySelector(
      "link[rel='canonical']",
    ) as HTMLLinkElement | null;
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private setHreflang(url: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.setOrCreateLink('alternate', 'es-ES', url);
    this.setOrCreateLink('alternate', 'x-default', url);
  }

  private setOrCreateLink(rel: string, hreflang: string, href: string): void {
    let link = this.document.querySelector(
      `link[rel='${rel}'][hreflang='${hreflang}']`
    ) as HTMLLinkElement | null;

    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', rel);
      link.setAttribute('hreflang', hreflang);
      this.document.head.appendChild(link);
    }

    link.setAttribute('href', href);
  }

  private buildCanonical(url: string): string {
    const cleaned = this.getCleanPath(url);
    if (!cleaned || cleaned === '/') {
      return `${this.baseUrl}/`;
    }
    return `${this.baseUrl}${cleaned}`;
  }

  private getCleanPath(url: string): string {
    return url.split('?')[0].split('#')[0];
  }

  private updateBreadcrumbSchema(path: string): void {
    const breadcrumbs = this.routeBreadcrumbs[path];
    if (breadcrumbs) {
      this.setBreadcrumbSchema(breadcrumbs);
      return;
    }

    this.removeSchemaBySelector('script[data-breadcrumb]');
  }

  private setBreadcrumbSchema(breadcrumbs: BreadcrumbItem[]): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.removeSchemaBySelector('script[data-breadcrumb]');

    const schema: JsonLdSchema = {
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

  private updateRouteSchemas(path: string): void {
    this.setRouteSchemas(this.getRouteSchemas(path));
  }

  private getSeoForPath(path: string): SeoMeta | null {
    switch (path) {
      case '/':
      case '/inicio':
        return SEO_ROUTES.home;
      case '/aljarafe':
        return SEO_ROUTES.aljarafe;
      case '/sevilla':
        return SEO_ROUTES.sevilla;
      case '/taekwondo':
        return SEO_ROUTES.taekwondo;
      case '/kickboxing':
        return SEO_ROUTES.kickboxing;
      case '/pilates':
        return SEO_ROUTES.pilates;
      case '/defensa-personal-femenina':
        return SEO_ROUTES.defensaPersonal;
      case '/horarios':
        return SEO_ROUTES.horarios;
      case '/eventos':
        return SEO_ROUTES.eventos;
      case '/contacto':
        return SEO_ROUTES.contacto;
      case '/tarifas':
        return SEO_ROUTES.tarifas;
      case '/politica-privacidad':
        return SEO_ROUTES.privacidad;
      case '/politica-cookies':
        return SEO_ROUTES.cookies;
      case '/aviso-legal':
        return SEO_ROUTES.avisoLegal;
      default:
        return null;
    }
  }

  private buildPageSchema(
    path: string,
    config?: {
      type?: string;
      idSuffix?: string;
      mainEntityId?: string;
      about?: JsonLdSchema[];
    },
  ): JsonLdSchema {
    const seo = this.getSeoForPath(path);
    const canonical = seo?.canonical ?? this.buildCanonical(path);
    const schema: JsonLdSchema = {
      '@context': 'https://schema.org',
      '@type': config?.type ?? 'WebPage',
      '@id': `${canonical}#${config?.idSuffix ?? 'webpage'}`,
      name: seo?.title ?? SEO_DEFAULTS.title,
      description: seo?.description ?? SEO_DEFAULTS.description,
      url: canonical,
    };

    if (config?.mainEntityId) {
      schema['mainEntity'] = { '@id': config.mainEntityId };
    } else {
      schema['isPartOf'] = { '@id': `${this.baseUrl}/#club` };
    }

    if (config?.about?.length) {
      schema['about'] = config.about;
    }

    return schema;
  }

  private buildLocationLandingSchema(path: string, areaName: string): JsonLdSchema {
    return this.buildPageSchema(path, {
      about: [
        { '@type': 'Place', name: 'Umbrete' },
        { '@type': 'AdministrativeArea', name: areaName },
        { '@type': 'City', name: 'Sevilla' },
      ],
    });
  }

  private getRouteSchemas(path: string): JsonLdSchema[] {
    switch (path) {
      case '/':
      case '/inicio':
        return [
          this.buildPageSchema('/', {
            mainEntityId: `${this.baseUrl}/#club`,
          }),
          this.buildFaqSchema(
            [
              {
                question: 'Donde esta la escuela de taekwondo Moiskimdo en Umbrete?',
                answer:
                  'Estamos en Calle Parada de la Ciguena 36, 41806 Umbrete, Sevilla, en el Aljarafe sevillano. Facil acceso desde Sevilla y municipios cercanos.',
              },
              {
                question: 'Que clases de artes marciales ofreceis en el Aljarafe y Sevilla?',
                answer:
                  'Ofrecemos clases de taekwondo, kickboxing light, pilates balance y defensa personal femenina para ninos, jovenes y adultos de todos los niveles.',
              },
              {
                question: 'Hay clases de taekwondo para ninos en Umbrete?',
                answer:
                  'Si, tenemos grupos especificos para ninos desde 4 anos, jovenes y adultos. Cada grupo esta adaptado por edad y nivel de experiencia.',
              },
              {
                question: 'Cual es el horario de las clases de artes marciales?',
                answer:
                  'Lunes 17:00-21:05, martes 17:00-21:00, miercoles 17:00-21:05, jueves 17:00-21:30, viernes 18:30-19:30. Consulta los horarios especificos de cada deporte en nuestra web.',
              },
              {
                question: 'Cuanto cuestan las clases de taekwondo o kickboxing?',
                answer:
                  'Tenemos diferentes tarifas segun la edad y modalidad. Ofrecemos descuentos familiares y la primera clase es gratuita. Contactanos para informacion detallada.',
              },
              {
                question: 'Necesito experiencia previa para apuntarme?',
                answer:
                  'No, aceptamos alumnos de todos los niveles. Tenemos grupos de iniciacion donde empezamos desde cero con atencion personalizada.',
              },
            ],
            '/',
          ),
        ];
      case '/aljarafe':
        return [this.buildLocationLandingSchema('/aljarafe', 'Aljarafe')];
      case '/sevilla':
        return [this.buildLocationLandingSchema('/sevilla', 'Sevilla')];
      case '/taekwondo':
        return [
          this.buildPageSchema('/taekwondo'),
          this.buildSportsServiceSchema(
            '/taekwondo',
            'Taekwondo',
            SEO_ROUTES.taekwondo.title ?? SEO_DEFAULTS.title,
            SEO_ROUTES.taekwondo.description ?? SEO_DEFAULTS.description,
          ),
          this.buildFaqSchema([
            {
              question: 'Hay clases de taekwondo para ninos en Umbrete?',
              answer:
                'Si. Tenemos grupos para ninos, jovenes y adultos, con acceso desde Umbrete, Aljarafe y Sevilla.',
            },
            {
              question: 'Se puede empezar taekwondo sin experiencia previa?',
              answer:
                'Si. Las clases de iniciacion estan preparadas para personas que empiezan desde cero.',
            },
            {
              question: 'Donde esta la escuela para taekwondo en el Aljarafe?',
              answer:
                'La escuela esta en Umbrete, Sevilla, en Calle Parada de la Ciguena 36.',
            },
          ], '/taekwondo'),
        ];
      case '/kickboxing':
        return [
          this.buildPageSchema('/kickboxing'),
          this.buildSportsServiceSchema(
            '/kickboxing',
            'Kickboxing',
            SEO_ROUTES.kickboxing.title ?? SEO_DEFAULTS.title,
            SEO_ROUTES.kickboxing.description ?? SEO_DEFAULTS.description,
          ),
          this.buildFaqSchema([
            {
              question:
                'Las clases de kickboxing en Umbrete son para principiantes?',
              answer:
                'Si. Trabajamos con niveles de iniciacion e intermedio para entrenar con seguridad.',
            },
            {
              question:
                'Puedo entrenar kickboxing si vivo en Sevilla o el Aljarafe?',
              answer:
                'Si. Muchas alumnas y alumnos llegan desde municipios del Aljarafe y desde Sevilla.',
            },
            {
              question: 'Que necesito para empezar kickboxing light?',
              answer:
                'Solo ropa comoda. Te orientamos con el material basico en la primera clase.',
            },
          ], '/kickboxing'),
        ];
      case '/pilates':
        return [
          this.buildPageSchema('/pilates'),
          this.buildSportsServiceSchema(
            '/pilates',
            'Pilates',
            SEO_ROUTES.pilates.title ?? SEO_DEFAULTS.title,
            SEO_ROUTES.pilates.description ?? SEO_DEFAULTS.description,
          ),
          this.buildFaqSchema([
            {
              question: 'Hay pilates para principiantes en Umbrete?',
              answer:
                'Si. Las sesiones se adaptan al nivel y objetivo de cada persona.',
            },
            {
              question: 'El pilates esta pensado solo para gente joven?',
              answer:
                'No. Trabajamos con diferentes edades y niveles de condicion fisica.',
            },
            {
              question:
                'Puedo venir desde Sevilla o el Aljarafe a clases de pilates?',
              answer:
                'Si. La escuela en Umbrete da servicio a alumnado de Aljarafe y Sevilla.',
            },
          ], '/pilates'),
        ];
      case '/defensa-personal-femenina':
        return [
          this.buildPageSchema('/defensa-personal-femenina'),
          this.buildSportsServiceSchema(
            '/defensa-personal-femenina',
            'Defensa Personal Femenina',
            SEO_ROUTES.defensaPersonal.title ?? SEO_DEFAULTS.title,
            SEO_ROUTES.defensaPersonal.description ?? SEO_DEFAULTS.description,
          ),
          this.buildFaqSchema([
            {
              question:
                'Las clases de defensa personal femenina son solo para mujeres en forma?',
              answer:
                'No. Las clases se adaptan a cualquier nivel de condicion fisica y experiencia.',
            },
            {
              question: 'Se practica defensa personal femenina en grupo?',
              answer:
                'Si. Se trabaja en grupo y por parejas para entrenar tecnicas utiles con seguridad y progresion.',
            },
            {
              question: 'Atendeis alumnas de Sevilla y del Aljarafe?',
              answer:
                'Si. La escuela de Umbrete recibe alumnas de Sevilla y municipios cercanos.',
            },
          ], '/defensa-personal-femenina'),
        ];
      case '/horarios':
        return [
          this.buildPageSchema('/horarios', {
            about: [
              { '@type': 'Thing', name: 'Taekwondo' },
              { '@type': 'Thing', name: 'Kickboxing' },
              { '@type': 'Thing', name: 'Pilates' },
              { '@type': 'Thing', name: 'Defensa Personal Femenina' },
            ],
          }),
        ];
      case '/contacto':
        return [
          this.buildPageSchema('/contacto', {
            type: 'ContactPage',
            idSuffix: 'contact',
            mainEntityId: `${this.baseUrl}/#club`,
          }),
        ];
      case '/eventos':
        return [
          this.buildPageSchema('/eventos', {
            type: 'CollectionPage',
            idSuffix: 'collection',
            about: [
              { '@type': 'Thing', name: 'Taekwondo' },
              { '@type': 'Thing', name: 'Kickboxing' },
              { '@type': 'Place', name: 'Umbrete' },
              { '@type': 'Place', name: 'Sevilla' },
            ],
          }),
        ];
      default:
        return [];
    }
  }

  private buildSportsServiceSchema(
    path: string,
    serviceType: string,
    name: string,
    description: string,
  ): JsonLdSchema {
    return {
      '@context': 'https://schema.org',
      '@type': 'Service',
      '@id': `${this.baseUrl}${path}#service`,
      name,
      description,
      serviceType,
      provider: { '@id': `${this.baseUrl}/#club` },
      areaServed: this.getAreaServed(),
      url: `${this.baseUrl}${path}`,
      offers: {
        '@type': 'Offer',
        url: `${this.baseUrl}/contacto`,
        availability: 'https://schema.org/InStock',
      },
    };
  }

  private buildFaqSchema(
    questions: Array<{
      question: string;
      answer: string;
    }>,
    path: string,
  ): JsonLdSchema {
    const faqId =
      path === '/' ? `${this.baseUrl}/#faq` : `${this.baseUrl}${path}#faq`;

    return {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      '@id': faqId,
      mainEntity: questions.map((entry) => ({
        '@type': 'Question',
        name: entry.question,
        acceptedAnswer: {
          '@type': 'Answer',
          text: entry.answer,
        },
      })),
    };
  }

  private getAreaServed(): JsonLdSchema[] {
    return [
      { '@type': 'City', name: 'Umbrete' },
      { '@type': 'AdministrativeArea', name: 'Aljarafe' },
      { '@type': 'City', name: 'Sevilla' },
      { '@type': 'AdministrativeArea', name: 'Andalucia' },
    ];
  }

  private setRouteSchemas(schemas: JsonLdSchema[]): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.removeSchemasBySelector('script[data-route-schema]');

    schemas.forEach((schema, index) => {
      const script = this.document.createElement('script');
      script.type = 'application/ld+json';
      script.setAttribute('data-route-schema', `${index}`);
      script.textContent = JSON.stringify(schema);
      this.document.head.appendChild(script);
    });
  }

  private removeSchemaBySelector(selector: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const script = this.document.querySelector(selector);
    if (script) {
      script.remove();
    }
  }

  private removeSchemasBySelector(selector: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.document
      .querySelectorAll(selector)
      .forEach((element) => element.remove());
  }

  private getDeepestRoute(
    route: ActivatedRouteSnapshot,
  ): ActivatedRouteSnapshot {
    let current = route;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current;
  }

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

    this.removeSchemaBySelector('script[data-event-schema]');

    const schema: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: config.name,
      description: config.description,
      eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
      eventStatus: 'https://schema.org/EventScheduled',
      location: {
        '@type': 'Place',
        name: "Club Moi's Kim Do",
        address: {
          '@type': 'PostalAddress',
          streetAddress: 'Calle Parada de la Ciguena, 36',
          addressLocality: 'Umbrete',
          addressRegion: 'Sevilla',
          postalCode: '41806',
          addressCountry: 'ES',
        },
      },
      organizer: {
        '@type': 'Organization',
        name: "Club Moi's Kim Do",
        url: 'https://moiskimdo.es',
      },
    };

    if (config.image) {
      schema['image'] = [config.image];
    }
    if (config.startDate) {
      schema['startDate'] = config.startDate;
    }
    if (config.endDate) {
      schema['endDate'] = config.endDate;
    }

    schema['url'] = `${this.baseUrl}${config.url}`;

    const script = this.document.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute('data-event-schema', 'true');
    script.textContent = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }

  removeEventSchema(): void {
    this.removeSchemaBySelector('script[data-event-schema]');
  }

  setReviewsSchema(
    reviews: Array<{
      author: string;
      rating: number;
      text: string;
      date?: string;
    }>,
  ): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.removeSchemaBySelector('script[data-reviews-schema]');

    const reviewSchemas = reviews.map((review) => ({
      '@type': 'Review',
      author: {
        '@type': 'Person',
        name: review.author,
      },
      reviewRating: {
        '@type': 'Rating',
        ratingValue: review.rating,
        bestRating: 5,
        worstRating: 1,
      },
      reviewBody: review.text,
      ...(review.date && { datePublished: review.date }),
    }));

    const schema: JsonLdSchema = {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@id': 'https://moiskimdo.es/#club',
          review: reviewSchemas,
        },
      ],
    };

    const script = this.document.createElement('script');
    script.type = 'application/ld+json';
    script.setAttribute('data-reviews-schema', 'true');
    script.textContent = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }
}
