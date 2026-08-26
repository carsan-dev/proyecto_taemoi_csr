import { DOCUMENT } from '@angular/common';
import { Inject, Injectable, OnDestroy } from '@angular/core';

export type ScrollLockRelease = () => void;

interface SavedViewportStyles {
  bodyPosition: string;
  bodyTop: string;
  bodyLeft: string;
  bodyRight: string;
  bodyWidth: string;
  bodyOverflow: string;
  documentOverflow: string;
}

@Injectable({ providedIn: 'root' })
export class ScrollLockService implements OnDestroy {
  private lockCount = 0;
  private scrollPosition: readonly [number, number] = [0, 0];
  private lockedUrl = '';
  private savedStyles?: SavedViewportStyles;
  private observer?: MutationObserver;
  private readonly observedReleases = new Map<Element, ScrollLockRelease>();

  constructor(@Inject(DOCUMENT) private readonly document: Document) {}

  get activeLocks(): number {
    return this.lockCount;
  }

  lock(): ScrollLockRelease {
    let released = false;
    if (this.lockCount++ === 0) {
      this.applyViewportLock();
    }

    return () => {
      if (released) {
        return;
      }
      released = true;
      this.lockCount = Math.max(0, this.lockCount - 1);
      if (this.lockCount === 0) {
        this.releaseViewportLock();
      }
    };
  }

  observeOverlays(): void {
    if (this.observer || !this.document.body || !this.document.defaultView?.MutationObserver) {
      return;
    }

    this.observer = new this.document.defaultView.MutationObserver(() => this.syncObservedOverlays());
    this.observer.observe(this.document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['class', 'open', 'aria-modal', 'hidden', 'style'],
    });
    this.syncObservedOverlays();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.observer = undefined;
    for (const release of this.observedReleases.values()) {
      release();
    }
    this.observedReleases.clear();
    while (this.lockCount > 0) {
      this.lockCount -= 1;
    }
    this.releaseViewportLock();
  }

  private syncObservedOverlays(): void {
    const overlays = new Set<Element>();

    for (const container of Array.from(this.document.querySelectorAll<HTMLElement>('.swal2-container'))) {
      if (!container.querySelector('.swal2-toast') && this.isVisible(container)) {
        overlays.add(container);
      }
    }
    for (const modal of Array.from(this.document.querySelectorAll<HTMLElement>('[data-scroll-lock-overlay="true"]'))) {
      if (this.isVisible(modal)) {
        overlays.add(modal);
      }
    }
    const modalSelectors = '[aria-modal="true"], dialog[open], .modal.show, .image-modal, .modal-overlay';
    for (const modal of Array.from(this.document.querySelectorAll<HTMLElement>(modalSelectors))) {
      if (!modal.closest('.swal2-container') && this.isVisible(modal)) {
        overlays.add(modal);
      }
    }

    for (const overlay of overlays) {
      if (!this.observedReleases.has(overlay)) {
        this.observedReleases.set(overlay, this.lock());
      }
    }
    for (const [overlay, release] of this.observedReleases) {
      if (!overlays.has(overlay) || !overlay.isConnected) {
        release();
        this.observedReleases.delete(overlay);
      }
    }
  }

  private isVisible(element: HTMLElement): boolean {
    return !element.hidden && element.getAttribute('aria-hidden') !== 'true' &&
      this.document.defaultView?.getComputedStyle(element).display !== 'none';
  }

  private applyViewportLock(): void {
    const win = this.document.defaultView;
    const body = this.document.body;
    const root = this.document.documentElement;
    if (!win || !body || !root) {
      return;
    }

    const scroller = this.document.scrollingElement ?? root;
    this.scrollPosition = [win.scrollX ?? scroller.scrollLeft, win.scrollY ?? scroller.scrollTop];
    this.lockedUrl = win.location.href;
    this.savedStyles = {
      bodyPosition: body.style.position,
      bodyTop: body.style.top,
      bodyLeft: body.style.left,
      bodyRight: body.style.right,
      bodyWidth: body.style.width,
      bodyOverflow: body.style.overflow,
      documentOverflow: root.style.overflow,
    };

    body.style.position = 'fixed';
    body.style.top = `${-this.scrollPosition[1]}px`;
    body.style.left = `${-this.scrollPosition[0]}px`;
    body.style.right = '0';
    body.style.width = '100%';
    body.style.overflow = 'hidden';
    root.style.overflow = 'hidden';
  }

  private releaseViewportLock(): void {
    const win = this.document.defaultView;
    const body = this.document.body;
    const root = this.document.documentElement;
    const styles = this.savedStyles;
    if (!win || !body || !root || !styles) {
      return;
    }

    body.style.position = styles.bodyPosition;
    body.style.top = styles.bodyTop;
    body.style.left = styles.bodyLeft;
    body.style.right = styles.bodyRight;
    body.style.width = styles.bodyWidth;
    body.style.overflow = styles.bodyOverflow;
    root.style.overflow = styles.documentOverflow;
    this.savedStyles = undefined;

    // A route transition may finish while an overlay is still fading out.
    // In that case the route coordinator already owns the destination scroll;
    // restoring the outgoing page position here would overwrite it.
    if (win.location.href === this.lockedUrl) {
      win.scrollTo({
        left: this.scrollPosition[0],
        top: this.scrollPosition[1],
        behavior: 'auto',
      });
    }
    this.lockedUrl = '';
  }
}
