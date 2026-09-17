import { fakeAsync, TestBed, tick, flushMicrotasks } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { ScrollLockService } from '../../../servicios/generales/scroll-lock.service';

describe('PdfViewerComponent', () => {
  it('navega y limita zoom; libera render, worker y expansión al cerrar', fakeAsync(() => {
    const fixture = TestBed.createComponent(PdfViewerComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    const internals = component as any;
    internals.pdf = {};
    component.totalPaginasPdf = 3;
    spyOn(component, 'programarRender');
    component.irAPaginaAnteriorPdf(); expect(component.paginaActualPdf).toBe(1);
    component.irAPaginaSiguientePdf(); expect(component.paginaActualPdf).toBe(2);
    component.irAPaginaSiguientePdf(); component.irAPaginaSiguientePdf(); expect(component.paginaActualPdf).toBe(3);
    component.irAPaginaAnteriorPdf(); expect(component.paginaActualPdf).toBe(2);
    for (let i = 0; i < 30; i++) { component.aumentarZoomPdf(); }
    expect(component.getZoomPdfPorcentaje()).toBe(220);
    for (let i = 0; i < 30; i++) { component.reducirZoomPdf(); }
    expect(component.getZoomPdfPorcentaje()).toBe(75);
    const cancel = jasmine.createSpy(); const destroy = jasmine.createSpy().and.resolveTo();
    internals.renderTask = { cancel }; internals.loadingTask = { destroy };
    component.toggleVisorExpandido();
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(1);
    fixture.destroy(); flushMicrotasks();
    expect(cancel).toHaveBeenCalled(); expect(destroy).toHaveBeenCalled();
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(0);
  }));
  it('descarta conversión Blob pendiente al sustituir o cerrar', fakeAsync(() => {
    const fixture = TestBed.createComponent(PdfViewerComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    let resolve!: (value: ArrayBuffer) => void;
    component.blob = { arrayBuffer: () => new Promise<ArrayBuffer>(r => resolve = r) } as Blob;
    component.ngOnChanges();
    component.blob = null; component.ngOnChanges();
    resolve(new ArrayBuffer(0)); flushMicrotasks();
    expect((component as any).loadingTask).toBeUndefined();
    expect(component.errorVisorPdf).toBeNull();
    component.blob = { arrayBuffer: () => new Promise<ArrayBuffer>(r => resolve = r) } as Blob;
    component.ngOnChanges(); fixture.destroy();
    resolve(new ArrayBuffer(0)); flushMicrotasks();
    expect((component as any).loadingTask).toBeUndefined();
  }));
  it('un getPage antiguo no renderiza tras sustituir documento', fakeAsync(() => {
    const fixture = TestBed.createComponent(PdfViewerComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    let resolve!: (value: unknown) => void;
    (component as any).pdf = { getPage: () => new Promise(r => resolve = r) };
    component.programarRender(); tick(20); flushMicrotasks();
    component.blob = null; component.ngOnChanges();
    const render = jasmine.createSpy(); resolve({ render }); flushMicrotasks();
    expect(render).not.toHaveBeenCalled(); fixture.destroy();
  }));
  it('menú contextual opcional y error de render visible', fakeAsync(() => {
    const fixture = TestBed.createComponent(PdfViewerComponent); fixture.detectChanges();
    const component = fixture.componentInstance;
    const normal = new Event('contextmenu', { cancelable: true }); component.onBloquearDescargaContenido(normal);
    expect(normal.defaultPrevented).toBeFalse();
    component.bloquearMenuContextual = true;
    const protectedEvent = new Event('contextmenu', { cancelable: true }); component.onBloquearDescargaContenido(protectedEvent);
    expect(protectedEvent.defaultPrevented).toBeTrue();
    (component as any).pdf = { getPage: () => Promise.reject(new Error('render')) };
    component.programarRender(); tick(20); flushMicrotasks();
    expect(component.errorVisorPdf).toContain('renderizar'); expect(component.cargandoPaginaPdf).toBeFalse();
    fixture.destroy();
  }));
});
