import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { CommonModule, DOCUMENT } from '@angular/common';
import { borrarCookiesGa, cargarScriptGtag, urlSinToken } from '../../../../utils/analitica.util';

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
  private readonly storageKey = 'cookieConsent';
  private readonly measurementId = 'G-R3H7GC1T4X';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private analiticaActiva = false;
  private ultimaPagina: string | null = null;

  ngOnInit(): void {
    // Un page_view por página, siempre sin el token de los enlaces de contraseña (revisión E8-6 F1). El page_location
    // se fija en cada navegación decida lo que decida el usuario. Sin consentimiento gtag.js no se carga (decisión 233)
    // y el `set` se queda en dataLayer sin mandar nada; si luego acepta, la cola ya va limpia.
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
    // La decisión guardada se vuelve a aplicar igual que al pulsar el botón (volver a guardarla no cambia nada).
    const stored = this.getStored();
    if (stored === 'accepted') {
      this.acceptCookies();
      return;
    }
    if (stored === 'rejected') {
      this.rejectCookies();
      return;
    }
    this.showCookieBanner = true;
  }

  acceptCookies(): void {
    this.setStored('accepted');
    this.desactivarGa(false);
    this.applyConsent(true);
    this.enableAnalytics();
    this.showCookieBanner = false;
  }

  /**
   * Rechazar, también desde «Configurar cookies» tras aceptar (decisión 250). Si gtag.js ya estaba cargado, el
   * `consent update` denegado y `ga-disable-<id>` hacen que deje de mandar hasta recargar; además se borran las
   * cookies _ga* que hubiera escrito.
   */
  rejectCookies(): void {
    this.setStored('rejected');
    this.applyConsent(false);
    this.desactivarGa(true);
    this.analiticaActiva = false;
    borrarCookiesGa(this.document);
    this.showCookieBanner = false;
  }

  private desactivarGa(desactivar: boolean): void {
    (globalThis as any)[`ga-disable-${this.measurementId}`] = desactivar;
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
   * Este `config` es el primero que ve gtag.js (consentimiento básico, decisión 233: el script se carga aquí, tras
   * aceptar) y ya lleva el page_location sin token. Lo que fija la página de los hits siguientes es el `set` de
   * page_location (`fijarPagina`).
   */
  private enableAnalytics(): void {
    const href = urlSinToken(globalThis.location?.href ?? '');
    const config = { anonymize_ip: true, send_page_view: false, page_location: href };
    this.gtag('js', new Date());
    this.gtag('config', this.measurementId, config);
    this.analiticaActiva = true;
    this.fijarPagina(href);
    this.enviarPageView(href);
    this.cargarScript();
  }

  /** gtag.js procesa al cargar la cola de dataLayer (consentimiento, config, set, page_view) en orden. */
  private cargarScript(): void {
    cargarScriptGtag(this.document, this.measurementId);
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
