import { AfterViewInit, Component, HostListener, NgZone, OnDestroy, OnInit } from '@angular/core';
import { PageScrollService } from '../../../servicios/generales/page-scroll.service';

@Component({
  selector: 'app-botonscroll',
  standalone: true,
  imports: [],
  templateUrl: './botonscroll.component.html',
  styleUrl: './botonscroll.component.scss'
})
export class BotonscrollComponent implements OnInit, AfterViewInit, OnDestroy {
  mostrarBoton = false;
  bottomOffset = 'calc(20px + env(safe-area-inset-bottom, 0px))';
  private readonly scrollThreshold = 100;
  private readonly bottomBasePx = 20;
  private windowResizeHandler?: () => void;
  private visualViewportResizeHandler?: () => void;
  private visualViewportScrollHandler?: () => void;
  private rafBottomOffsetId: number | null = null;

  constructor(
    private readonly zone: NgZone,
    private readonly pageScroll: PageScrollService
  ) {}

  ngOnInit(): void {
    this.updateVisibility();
    this.actualizarBottomOffset();
  }

  ngAfterViewInit(): void {
    if (globalThis.document === undefined) {
      return;
    }

    const win = globalThis.window;
    if (win !== undefined) {
      this.windowResizeHandler = () => {
        this.zone.run(() => this.programarActualizacionBottomOffset());
      };
      win.addEventListener('resize', this.windowResizeHandler, { passive: true });

      const viewport = win.visualViewport;
      if (viewport) {
        this.visualViewportResizeHandler = () => {
          this.zone.run(() => this.programarActualizacionBottomOffset());
        };
        this.visualViewportScrollHandler = () => {
          this.zone.run(() => this.programarActualizacionBottomOffset());
        };
        viewport.addEventListener('resize', this.visualViewportResizeHandler, { passive: true });
        viewport.addEventListener('scroll', this.visualViewportScrollHandler, { passive: true });
      }
    }

    this.programarActualizacionBottomOffset();
  }

  ngOnDestroy(): void {
    const win = globalThis.window;
    if (win !== undefined && this.windowResizeHandler) {
      win.removeEventListener('resize', this.windowResizeHandler);
      this.windowResizeHandler = undefined;
    }

    const viewport = win?.visualViewport;
    if (viewport && this.visualViewportResizeHandler) {
      viewport.removeEventListener('resize', this.visualViewportResizeHandler);
      this.visualViewportResizeHandler = undefined;
    }
    if (viewport && this.visualViewportScrollHandler) {
      viewport.removeEventListener('scroll', this.visualViewportScrollHandler);
      this.visualViewportScrollHandler = undefined;
    }

    if (this.rafBottomOffsetId !== null && win?.cancelAnimationFrame) {
      win.cancelAnimationFrame(this.rafBottomOffsetId);
      this.rafBottomOffsetId = null;
    }
  }

  scrollArriba(): void {
    this.pageScroll.scrollToTop('smooth');
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    this.updateVisibility();
    this.programarActualizacionBottomOffset();
  }

  @HostListener('window:orientationchange')
  onOrientationChange(): void {
    this.programarActualizacionBottomOffset();
  }

  private updateVisibility(): void {
    const shouldShow = this.pageScroll.scrollTop > this.scrollThreshold;
    if (shouldShow === this.mostrarBoton) {
      return;
    }

    this.mostrarBoton = shouldShow;
  }

  private programarActualizacionBottomOffset(): void {
    const win = globalThis.window;
    if (win === undefined || typeof win.requestAnimationFrame !== 'function') {
      this.actualizarBottomOffset();
      return;
    }

    if (this.rafBottomOffsetId !== null) {
      return;
    }

    this.rafBottomOffsetId = win.requestAnimationFrame(() => {
      this.rafBottomOffsetId = null;
      this.actualizarBottomOffset();
    });
  }

  private actualizarBottomOffset(): void {
    const win = globalThis.window;
    const doc = globalThis.document;
    if (win === undefined || doc === undefined) {
      return;
    }

    const esMovil = win.matchMedia?.('(max-width: 768px)').matches ?? win.innerWidth <= 768;
    if (!esMovil) {
      this.bottomOffset = `calc(${this.bottomBasePx}px + env(safe-area-inset-bottom, 0px))`;
      return;
    }

    const viewport = win.visualViewport;
    const layoutHeight =
      Math.max(win.innerHeight || 0, doc.documentElement?.clientHeight || 0, doc.body?.clientHeight || 0) || 0;
    const visibleHeight = viewport?.height ?? layoutHeight;
    const offsetTop = viewport?.offsetTop ?? 0;

    // Extra bottom real ocupado por barras de UI del navegador/sistema.
    const uiInsetBottom = Math.max(0, Math.round(layoutHeight - visibleHeight - offsetTop));
    const extraPorAltura = this.obtenerExtraMovilPorAltura(Math.round(visibleHeight || layoutHeight));
    const extraPx = Math.max(extraPorAltura, uiInsetBottom + 10);
    const extraPxAjustado = Math.min(Math.max(extraPx, 16), 46);

    this.bottomOffset = `calc(${this.bottomBasePx + extraPxAjustado}px + env(safe-area-inset-bottom, 0px))`;
  }

  private obtenerExtraMovilPorAltura(alturaVisiblePx: number): number {
    if (alturaVisiblePx <= 620) {
      return 34;
    }
    if (alturaVisiblePx <= 720) {
      return 28;
    }
    if (alturaVisiblePx <= 820) {
      return 22;
    }
    return 18;
  }
}
