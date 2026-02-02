import { Component, OnInit } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { FabNavegacionComponent } from '../../../generales/fab-navegacion/fab-navegacion.component';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    FabNavegacionComponent,
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

  ngOnInit(): void {
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

  private enableAnalytics(): void {
    const globalAny = globalThis as any;
    const config = { anonymize_ip: true, send_page_view: true };
    if (typeof globalAny.gtag === 'function') {
      globalAny.gtag('config', this.measurementId, config);
    } else {
      globalAny.dataLayer = globalAny.dataLayer || [];
      globalAny.dataLayer.push(['config', this.measurementId, config]);
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
