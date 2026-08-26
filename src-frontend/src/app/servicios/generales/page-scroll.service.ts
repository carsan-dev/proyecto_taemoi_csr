import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';

export type RouteScrollPolicy = 'standard' | 'top' | 'preserve';

export interface PageScrollAnchor {
  key: string;
  viewportOffset: number;
}

export interface PageScrollSnapshot {
  position: readonly [number, number];
  anchor?: PageScrollAnchor;
}

/**
 * The single API for page-level scrolling. Scrollable tables, modal bodies and
 * other deliberately nested regions remain outside this service.
 */
@Injectable({ providedIn: 'root' })
export class PageScrollService {
  private renderOperation = 0;

  constructor(@Inject(DOCUMENT) private readonly document: Document) {}

  get position(): readonly [number, number] {
    const win = this.document.defaultView;
    const scroller = this.document.scrollingElement ?? this.document.documentElement;
    return [win?.scrollX ?? scroller.scrollLeft ?? 0, win?.scrollY ?? scroller.scrollTop ?? 0];
  }

  get scrollTop(): number {
    return this.position[1];
  }

  captureSnapshot(preferredAnchor?: string | HTMLElement | null): PageScrollSnapshot {
    const position = this.position;
    const anchorElement = this.resolvePreferredAnchor(preferredAnchor) ?? this.findStableAnchor();

    if (!anchorElement) {
      return { position };
    }

    const key = anchorElement.dataset['scrollAnchor'] || anchorElement.id;
    if (!key) {
      return { position };
    }

    return {
      position,
      anchor: {
        key,
        viewportOffset: anchorElement.getBoundingClientRect().top,
      },
    };
  }

  restoreSnapshot(snapshot: PageScrollSnapshot, behavior: ScrollBehavior = 'auto'): void {
    const anchor = snapshot.anchor ? this.findAnchor(snapshot.anchor.key) : null;
    if (anchor && snapshot.anchor) {
      const top = this.scrollTop + anchor.getBoundingClientRect().top - snapshot.anchor.viewportOffset;
      this.scrollToPosition([snapshot.position[0], Math.max(0, top)], behavior);
      return;
    }

    this.scrollToPosition(snapshot.position, behavior);
  }

  restoreSnapshotAfterNextRender(
    snapshot: PageScrollSnapshot,
    behavior: ScrollBehavior = 'auto'
  ): Promise<boolean> {
    return this.afterNextRender(() => this.restoreSnapshot(snapshot, behavior));
  }

  scrollToTop(behavior: ScrollBehavior = 'smooth'): void {
    this.scrollToPosition([0, 0], behavior);
  }

  scrollToTopAfterNextRender(behavior: ScrollBehavior = 'auto'): Promise<boolean> {
    return this.afterNextRender(() => this.scrollToTop(behavior));
  }

  scrollToAnchor(anchor: string, behavior: ScrollBehavior = 'smooth'): boolean {
    const element = this.findAnchor(anchor);
    if (!element) {
      return false;
    }

    const top = this.scrollTop + element.getBoundingClientRect().top - this.fixedHeaderOffset;
    this.scrollToPosition([0, Math.max(0, top)], behavior);
    return true;
  }

  scrollToAnchorAfterNextRender(
    anchor: string,
    behavior: ScrollBehavior = 'smooth'
  ): Promise<boolean> {
    return this.afterNextRender(() => this.scrollToAnchor(anchor, behavior));
  }

  scrollToPosition(
    position: readonly [number, number],
    behavior: ScrollBehavior = 'auto'
  ): void {
    const win = this.document.defaultView;
    const scroller = this.document.scrollingElement ?? this.document.documentElement;
    const resolvedBehavior = this.respectReducedMotion(behavior);
    const [left, top] = position;

    if (resolvedBehavior === 'auto') {
      if (win) {
        win.scrollTo({ left, top, behavior: 'auto' });
      } else {
        scroller.scrollLeft = left;
        scroller.scrollTop = top;
      }
      return;
    }

    win?.scrollTo({ left, top, behavior: resolvedBehavior });
  }

  cancelPendingRestoration(): void {
    this.renderOperation += 1;
  }

  setManualHistoryRestoration(): void {
    const history = this.document.defaultView?.history;
    if (history && 'scrollRestoration' in history) {
      history.scrollRestoration = 'manual';
    }
  }

  get fixedHeaderOffset(): number {
    const win = this.document.defaultView;
    if (!win) {
      return 0;
    }

    const selectors = [
      '.header-anonimo.fixed-header',
      '.header-user.fixed-header',
      '.admin-top-navbar',
      'header.fixed-header',
      '.navbar.fixed-top',
      '.navbar.sticky-top',
    ];
    let bottom = 0;

    for (const element of Array.from(this.document.querySelectorAll<HTMLElement>(selectors.join(',')))) {
      const style = win.getComputedStyle(element);
      if (style.display === 'none' || style.visibility === 'hidden' || element.classList.contains('is-hidden')) {
        continue;
      }
      const rect = element.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0 && rect.bottom > 0) {
        bottom = Math.max(bottom, rect.bottom);
      }
    }

    return Math.max(0, Math.ceil(bottom));
  }

  private afterNextRender(action: () => void | boolean): Promise<boolean> {
    const operation = ++this.renderOperation;
    const win = this.document.defaultView;

    return new Promise<boolean>((resolve) => {
      const run = () => {
        if (operation !== this.renderOperation) {
          resolve(false);
          return;
        }
        action();
        resolve(true);
      };

      if (win?.requestAnimationFrame) {
        win.requestAnimationFrame(run);
      } else {
        run();
      }
    });
  }

  private respectReducedMotion(behavior: ScrollBehavior): ScrollBehavior {
    if (behavior !== 'smooth') {
      return behavior;
    }
    const win = this.document.defaultView;
    return win?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
  }

  private resolvePreferredAnchor(preferredAnchor?: string | HTMLElement | null): HTMLElement | null {
    if (preferredAnchor instanceof HTMLElement) {
      return preferredAnchor.isConnected ? preferredAnchor : null;
    }
    return preferredAnchor ? this.findAnchor(preferredAnchor) : null;
  }

  private findStableAnchor(): HTMLElement | null {
    const viewportTop = this.fixedHeaderOffset;
    const candidates = Array.from(
      this.document.querySelectorAll<HTMLElement>('[data-scroll-anchor], main [id], #content [id]')
    ).filter((element) => {
      if (!element.id && !element.dataset['scrollAnchor']) {
        return false;
      }
      if (element.closest('[role="dialog"], .modal, .swal2-container')) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      return rect.height > 0 && rect.bottom >= viewportTop;
    });

    if (!candidates.length) {
      return null;
    }

    const containing = candidates
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.top <= viewportTop && rect.bottom > viewportTop;
      })
      .sort((a, b) => b.getBoundingClientRect().top - a.getBoundingClientRect().top);

    return containing[0] ?? candidates.sort(
      (a, b) => Math.abs(a.getBoundingClientRect().top - viewportTop) - Math.abs(b.getBoundingClientRect().top - viewportTop)
    )[0] ?? null;
  }

  private findAnchor(rawAnchor: string): HTMLElement | null {
    const anchor = this.decodeAnchor(rawAnchor);
    const byId = this.document.getElementById(anchor);
    if (byId) {
      return byId;
    }

    return Array.from(this.document.querySelectorAll<HTMLElement>('[data-scroll-anchor]'))
      .find((element) => element.dataset['scrollAnchor'] === anchor) ?? null;
  }

  private decodeAnchor(anchor: string): string {
    try {
      return decodeURIComponent(anchor.replace(/^#/, ''));
    } catch {
      return anchor.replace(/^#/, '');
    }
  }
}
