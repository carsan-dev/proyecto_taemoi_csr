import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

type ConsentState = 'accepted' | 'rejected';

@Component({
  selector: 'app-cookie-consent',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './cookie-consent.component.html',
  styleUrl: './cookie-consent.component.scss',
})
export class CookieConsentComponent implements OnInit {
  visible = false;
  private readonly storageKey = 'cookieConsent';

  ngOnInit(): void {
    const windowRef = this.getWindowRef();
    if (!windowRef) {
      return;
    }

    const stored = windowRef.localStorage.getItem(this.storageKey) as ConsentState | null;
    if (stored === 'accepted') {
      this.applyConsent(true);
      this.visible = false;
      return;
    }

    if (stored === 'rejected') {
      this.applyConsent(false);
      this.visible = false;
      return;
    }

    this.visible = true;
  }

  acceptAnalytics(): void {
    const windowRef = this.getWindowRef();
    if (!windowRef) {
      return;
    }
    windowRef.localStorage.setItem(this.storageKey, 'accepted');
    this.applyConsent(true);
    this.visible = false;
  }

  rejectAnalytics(): void {
    const windowRef = this.getWindowRef();
    if (!windowRef) {
      return;
    }
    windowRef.localStorage.setItem(this.storageKey, 'rejected');
    this.applyConsent(false);
    this.visible = false;
  }

  private applyConsent(analyticsGranted: boolean): void {
    const windowRef = this.getWindowRef() as any;
    if (!windowRef) {
      return;
    }

    const consentUpdate = {
      ad_storage: 'denied',
      analytics_storage: analyticsGranted ? 'granted' : 'denied',
      functionality_storage: 'granted',
      security_storage: 'granted',
    };

    if (typeof windowRef.gtag === 'function') {
      windowRef.gtag('consent', 'update', consentUpdate);
      return;
    }

    windowRef.dataLayer = windowRef.dataLayer || [];
    windowRef.dataLayer.push(['consent', 'update', consentUpdate]);
  }

  private getWindowRef(): Window | undefined {
    return (globalThis as { window?: Window }).window;
  }
}
