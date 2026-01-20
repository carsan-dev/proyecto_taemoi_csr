import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SEO_DEFAULTS, SeoMeta } from '../../core/constants/seo.constants';

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly defaultRobots =
    'index,follow,max-snippet:-1,max-image-preview:large,max-video-preview:-1';
  private readonly baseUrl = 'https://moiskimdo.es';

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
  }

  private updateMetaTag(attr: 'name' | 'property', key: string, content: string): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}='${key}'`);
  }

  private setCanonical(url: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

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

  private getDeepestRoute(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    let current = route;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current;
  }
}
