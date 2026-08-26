import { Injectable, OnDestroy } from '@angular/core';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  Scroll,
} from '@angular/router';
import { Subscription } from 'rxjs';

import {
  PageScrollService,
  PageScrollSnapshot,
  RouteScrollPolicy,
} from './page-scroll.service';

interface PendingNavigation {
  id: number;
  fromUrl: string;
  toUrl: string;
  outgoing: PageScrollSnapshot;
  restoredNavigationId: number | null;
}

@Injectable({ providedIn: 'root' })
export class RouteScrollCoordinator implements OnDestroy {
  private readonly snapshots = new Map<number, PageScrollSnapshot>();
  private subscription?: Subscription;
  private activeNavigationId = 0;
  private activeUrl = '';
  private pending?: PendingNavigation;

  constructor(
    private readonly router: Router,
    private readonly pageScroll: PageScrollService
  ) {}

  start(): void {
    if (this.subscription) {
      return;
    }

    this.activeUrl = this.router.url;
    this.pageScroll.setManualHistoryRestoration();
    this.subscription = this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.onNavigationStart(event);
      } else if (event instanceof NavigationEnd) {
        this.activeNavigationId = event.id;
        this.activeUrl = event.urlAfterRedirects;
      } else if (event instanceof Scroll) {
        this.onScroll(event);
      } else if (event instanceof NavigationCancel || event instanceof NavigationError) {
        this.pending = undefined;
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    this.pageScroll.cancelPendingRestoration();
  }

  private onNavigationStart(event: NavigationStart): void {
    this.pageScroll.cancelPendingRestoration();
    const outgoing = this.pageScroll.captureSnapshot();
    if (this.activeNavigationId > 0) {
      this.snapshots.set(this.activeNavigationId, outgoing);
    }

    this.pending = {
      id: event.id,
      fromUrl: this.activeUrl,
      toUrl: event.url,
      outgoing,
      restoredNavigationId: this.getRestoredNavigationId(event),
    };
  }

  private onScroll(event: Scroll): void {
    // Angular schedules Scroll after NavigationEnd. If another navigation has
    // already started, an older scheduled event must not clear or overwrite it.
    if (this.pending && this.pending.id !== event.routerEvent.id) {
      return;
    }
    const pending = this.pending?.id === event.routerEvent.id ? this.pending : undefined;
    const policy = this.currentPolicy;
    const completedUrl = event.routerEvent.url;
    const fragment = event.anchor ?? this.getFragment(completedUrl);

    if (fragment && policy !== 'top') {
      void this.pageScroll.scrollToAnchorAfterNextRender(fragment, 'smooth');
    } else if (policy === 'top') {
      void this.pageScroll.scrollToTopAfterNextRender('auto');
    } else if (policy === 'preserve') {
      void this.pageScroll.restoreSnapshotAfterNextRender(
        pending?.outgoing ?? this.pageScroll.captureSnapshot()
      );
    } else {
      const restoredId = pending?.restoredNavigationId;
      const storedSnapshot = restoredId ? this.snapshots.get(restoredId) : undefined;

      if (storedSnapshot) {
        void this.pageScroll.restoreSnapshotAfterNextRender(storedSnapshot);
      } else if (event.position) {
        void this.pageScroll.restoreSnapshotAfterNextRender({ position: event.position });
      } else if (pending && this.samePath(pending.fromUrl, completedUrl)) {
        void this.pageScroll.restoreSnapshotAfterNextRender(pending.outgoing);
      } else {
        void this.pageScroll.scrollToTopAfterNextRender('auto');
      }
    }

    this.pending = undefined;
  }

  private get currentPolicy(): RouteScrollPolicy {
    let route = this.router.routerState.snapshot.root;
    let policy: RouteScrollPolicy = 'standard';
    while (route) {
      const candidate = route.data['scrollPolicy'];
      if (candidate === 'top' || candidate === 'preserve' || candidate === 'standard') {
        policy = candidate;
      }
      route = route.firstChild!;
    }
    return policy;
  }

  private getRestoredNavigationId(event: NavigationStart): number | null {
    const navigationId = event.restoredState?.['navigationId'];
    return typeof navigationId === 'number' ? navigationId : null;
  }

  private getFragment(url: string): string | null {
    const hashIndex = url.indexOf('#');
    return hashIndex >= 0 ? url.slice(hashIndex + 1) || null : null;
  }

  private samePath(first: string, second: string): boolean {
    const path = (url: string) => url.split('#', 1)[0].split('?', 1)[0].replace(/\/$/, '') || '/';
    return path(first) === path(second);
  }
}
