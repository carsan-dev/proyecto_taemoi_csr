import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { CommonModule } from '@angular/common';
import { urlSinToken } from '../../../../utils/analitica.util';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    CommonModule,
  ],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent implements OnInit {
  showCookieBanner = false;
  showAdblockNotice = false;
  private readonly storageKey = 'cookieConsent';
  private readonly measurementId = 'G-R3H7GC1T4X';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private analiticaActiva = false;
  private ultimaPagina: string | null = null;

  ngOnInit(): void {
    // Un page_view por página, siempre sin el token de los enlaces de contraseña (revisión E8-6 F1). El page_location
    // se fija en cada navegación decida lo que decida el usuario: con Consent Mode avanzado, gtag.js manda eventos
    // automáticos (scroll, user_engagement, formularios) también sin consentimiento, y usan ese valor.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((e) => {
        const pagina = urlSinToken(`${globalThis.location?.origin ?? ''}${e.urlAfterRedirects}`);
        this.fijarPagina(pagina);
        if (this.analiticaActiva) {
          this.enviarPageView(pagina);
        }
      });
    const stored = this.getStored();
    if (stored === 'accepted') {
      this.applyConsent(true);
      this.enableAnalytics();
      this.showCookieBanner = false;
      return;
    }
    if (stored === 'rejected') {
      this.applyConsent(false);
      this.showCookieBanner = false;
      return;
    }
    this.showCookieBanner = true;
    this.scheduleGtagCheck();
  }

  acceptCookies(): void {
    this.setStored('accepted');
    this.applyConsent(true);
    this.enableAnalytics();
    this.showCookieBanner = false;
  }

  rejectCookies(): void {
    this.setStored('rejected');
    this.applyConsent(false);
    this.showCookieBanner = false;
  }

  private applyConsent(granted: boolean): void {
    const globalAny = globalThis as any;
    const update = {
      ad_storage: 'denied',
      analytics_storage: granted ? 'granted' : 'denied',
      functionality_storage: 'granted',
      security_storage: 'granted',
    };
    if (typeof globalAny.gtag === 'function') {
      globalAny.gtag('consent', 'update', update);
    } else {
      globalAny.dataLayer = globalAny.dataLayer || [];
      globalAny.dataLayer.push(['consent', 'update', update]);
    }
  }

  /**
   * El page_view automático de `config` usaría la URL de la barra, que en el arranque aún puede llevar `?token=`
   * (restablecer contraseña, invitación). Se desactiva y cada page_view se manda a mano con la URL sin el token.
   * El `config` de index.html ya lleva el page_location sin token. Lo que fija la página de los hits siguientes es el
   * `set` de page_location (`fijarPagina`), no este `config`.
   */
  private enableAnalytics(): void {
    const href = urlSinToken(globalThis.location?.href ?? '');
    const config = { anonymize_ip: true, send_page_view: false, page_location: href };
    this.gtag('config', this.measurementId, config);
    this.analiticaActiva = true;
    this.fijarPagina(href);
    this.enviarPageView(href);
  }

  /** Fija la página de todos los hits siguientes de gtag.js; `set` no manda nada por sí mismo. */
  private fijarPagina(pagina: string): void {
    this.gtag('set', { page_location: urlSinToken(pagina) });
  }

  /** Manda el page_view de la página ya fijada con `fijarPagina`. */
  private enviarPageView(href: string): void {
    const pagina = urlSinToken(href);
    if (pagina === this.ultimaPagina) {
      return; // la navegación inicial (o quitar el token de la barra) no cuenta otra visita
    }
    this.ultimaPagina = pagina;
    let ruta = pagina;
    try {
      const url = new URL(pagina);
      ruta = url.pathname + url.search;
    } catch {
      // se manda tal cual
    }
    this.gtag('event', 'page_view', { page_location: pagina, page_path: ruta });
  }

  private gtag(...args: unknown[]): void {
    const globalAny = globalThis as any;
    if (typeof globalAny.gtag === 'function') {
      globalAny.gtag(...args);
    } else {
      globalAny.dataLayer = globalAny.dataLayer || [];
      globalAny.dataLayer.push(args);
    }
  }

  private scheduleGtagCheck(): void {
    const globalAny = globalThis as any;
    setTimeout(() => {
      const gtmReady =
        globalAny.google_tag_manager &&
        globalAny.google_tag_manager[this.measurementId];
      if (globalAny.__gtagLoadFailed === true || globalAny.__gtagLoaded !== true || !gtmReady) {
        this.showAdblockNotice = true;
      }
    }, 2000);
  }

  private getStored(): string | null {
    try {
      return localStorage.getItem(this.storageKey);
    } catch {
      return null;
    }
  }

  private setStored(value: string): void {
    try {
      localStorage.setItem(this.storageKey, value);
    } catch {
      // ignore
    }
  }
}
