import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, ViewChild, inject } from '@angular/core';
import { getDocument, GlobalWorkerOptions, PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import { ScrollLockRelease, ScrollLockService } from '../../../servicios/generales/scroll-lock.service';

const baseHref = globalThis.document?.querySelector('base')?.getAttribute('href') ?? '/';
const baseUrl = new URL(baseHref, globalThis.location?.origin ?? 'http://localhost/');
GlobalWorkerOptions.workerSrc = new URL('assets/pdfjs/pdf.worker.min.mjs?v=20260222-1', baseUrl).toString();

@Component({
  selector: 'app-pdf-viewer', standalone: true, imports: [CommonModule],
  templateUrl: './pdf-viewer.component.html', styleUrl: './pdf-viewer.component.scss',
})
export class PdfViewerComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() blob: Blob | null = null;
  @Input() titulo = 'Documento PDF';
  @Input() bloquearMenuContextual = false;
  @ViewChild('pdfCanvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;
  visorExpandido = false;
  visorExpandidoTopOffsetPx = 0;
  totalPaginasPdf = 0;
  paginaActualPdf = 1;
  zoomPdf = 1;
  cargandoPaginaPdf = false;
  errorVisorPdf: string | null = null;
  private readonly scrollLock = inject(ScrollLockService);
  private release?: ScrollLockRelease;
  private loadingTask?: PDFDocumentLoadingTask;
  private pdf?: PDFDocumentProxy;
  private renderTask?: RenderTask;
  private version = 0;
  private renderVersion = 0;
  private frame?: number;

  ngOnChanges(): void { void this.cargar(); }
  ngAfterViewInit(): void { this.programarRender(); }
  ngOnDestroy(): void { this.limpiar(); this.cerrarExpansion(); }

  private limpiar(): void {
    this.version++;
    this.renderVersion++;
    if (this.frame !== undefined) { cancelAnimationFrame(this.frame); this.frame = undefined; }
    this.renderTask?.cancel();
    this.renderTask = undefined;
    // The loading task owns the document and its worker, including pending loads.
    void this.loadingTask?.destroy().catch(() => {});
    this.loadingTask = undefined;
    this.pdf = undefined;
  }

  private async cargar(): Promise<void> {
    this.limpiar();
    this.cerrarExpansion();
    this.totalPaginasPdf = 0;
    this.paginaActualPdf = 1;
    this.zoomPdf = 1;
    this.errorVisorPdf = null;
    this.cargandoPaginaPdf = !!this.blob;
    const version = this.version;
    const blob = this.blob;
    if (!blob) { return; }
    try {
      const data = await blob.arrayBuffer();
      if (version !== this.version) { return; }
      const task = getDocument({ data, enableXfa: false, isEvalSupported: false, useSystemFonts: true });
      this.loadingTask = task;
      const pdf = await task.promise;
      if (version !== this.version) { return; }
      this.pdf = pdf;
      this.totalPaginasPdf = pdf.numPages;
      this.programarRender();
    } catch {
      if (version !== this.version) { return; }
      this.errorVisorPdf = 'No se pudo cargar el visor de este PDF.';
      this.cargandoPaginaPdf = false;
    }
  }

  irAPaginaAnteriorPdf(): void {
    if (!this.pdf || this.paginaActualPdf <= 1) { return; }
    this.paginaActualPdf--; this.programarRender();
  }
  irAPaginaSiguientePdf(): void {
    if (!this.pdf || this.paginaActualPdf >= this.totalPaginasPdf) { return; }
    this.paginaActualPdf++; this.programarRender();
  }
  aumentarZoomPdf(): void {
    if (!this.pdf) { return; }
    this.zoomPdf = Math.min(2.2, this.zoomPdf + .15); this.programarRender();
  }
  reducirZoomPdf(): void {
    if (!this.pdf) { return; }
    this.zoomPdf = Math.max(.75, this.zoomPdf - .15); this.programarRender();
  }
  getZoomPdfPorcentaje(): number { return Math.round(this.zoomPdf * 100); }
  onBloquearDescargaContenido(event: Event): void {
    if (this.bloquearMenuContextual) { event.preventDefault(); event.stopPropagation(); }
  }
  toggleVisorExpandido(): void {
    if (this.visorExpandido) { this.cerrarExpansion(); }
    else { this.visorExpandido = true; this.release = this.scrollLock.lock(); }
    this.programarRender();
  }
  cerrarExpansion(): boolean {
    if (!this.visorExpandido) { return false; }
    this.visorExpandido = false;
    this.release?.(); this.release = undefined;
    this.programarRender();
    return true;
  }
  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.cerrarExpansion()) { event.preventDefault(); event.stopPropagation(); }
  }
  @HostListener('window:resize')
  programarRender(): void {
    if (this.visorExpandido) {
      const headers = Array.from(document.querySelectorAll<HTMLElement>('header.fixed-header, .admin-top-navbar'));
      this.visorExpandidoTopOffsetPx = Math.ceil(headers.reduce((bottom, header) => {
        const style = getComputedStyle(header);
        return style.display === 'none' || style.visibility === 'hidden' || header.classList.contains('is-hidden')
          ? bottom : Math.max(bottom, header.getBoundingClientRect().bottom);
      }, 0)) + 8;
    }
    this.renderVersion++;
    if (!this.pdf || !this.canvasRef) { return; }
    if (this.frame !== undefined) { cancelAnimationFrame(this.frame); }
    this.frame = requestAnimationFrame(() => { this.frame = undefined; void this.render(); });
  }
  private async render(): Promise<void> {
    const pdf = this.pdf;
    const canvas = this.canvasRef?.nativeElement;
    if (!pdf || !canvas) { return; }
    const version = this.renderVersion;
    const previous = this.renderTask;
    previous?.cancel();
    this.cargandoPaginaPdf = true;
    this.errorVisorPdf = null;
    try {
      // A cancelled render must settle before the same canvas can be reused.
      await previous?.promise.catch(() => {});
      const page = await pdf.getPage(this.paginaActualPdf);
      if (version !== this.renderVersion) { return; }
      const base = page.getViewport({ scale: 1 });
      const width = Math.max(1, (canvas.parentElement?.clientWidth ?? base.width) - 8);
      const viewport = page.getViewport({ scale: Math.max(.1, Math.min(3.2, width / base.width * this.zoomPdf)) });
      const ratio = Math.max(1, globalThis.devicePixelRatio ?? 1);
      const context = canvas.getContext('2d', { alpha: false });
      if (!context) { throw new Error('No se pudo inicializar el canvas del visor'); }
      canvas.width = Math.floor(viewport.width * ratio);
      canvas.height = Math.floor(viewport.height * ratio);
      canvas.style.width = `${Math.floor(viewport.width)}px`;
      canvas.style.height = `${Math.floor(viewport.height)}px`;
      const task = page.render({ canvasContext: context, viewport, transform: [ratio, 0, 0, ratio, 0, 0] });
      this.renderTask = task;
      await task.promise;
      if (version === this.renderVersion) { this.renderTask = undefined; this.cargandoPaginaPdf = false; }
    } catch (error) {
      if (version !== this.renderVersion || (error as Error)?.name === 'RenderingCancelledException') { return; }
      this.errorVisorPdf = 'No se pudo renderizar la página del documento.';
      this.cargandoPaginaPdf = false;
    }
  }
}
