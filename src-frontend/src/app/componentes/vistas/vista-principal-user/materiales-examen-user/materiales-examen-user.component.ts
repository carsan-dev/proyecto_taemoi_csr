import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import {
  getDocument,
  GlobalWorkerOptions,
  PDFDocumentLoadingTask,
  PDFDocumentProxy,
  RenderTask,
} from 'pdfjs-dist';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';
import {
  MaterialExamenDTO,
  MaterialExamenDocumentoDTO,
  MaterialExamenVideoDTO,
} from '../../../../interfaces/material-examen';
import { getDeporteLabel } from '../../../../enums/deporte';

const baseHref = globalThis.document?.querySelector('base')?.getAttribute('href') ?? '/';
const baseUrl = globalThis.location
  ? new URL(baseHref, globalThis.location.origin)
  : new URL('http://localhost/');
GlobalWorkerOptions.workerSrc = new URL('assets/pdfjs/pdf.worker.min.mjs', baseUrl).toString();

@Component({
  selector: 'app-materiales-examen-user',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './materiales-examen-user.component.html',
  styleUrl: './materiales-examen-user.component.scss',
})
export class MaterialesExamenUserComponent implements OnChanges, OnDestroy {
  @Input() alumnoId: number | null = null;
  @Input() deportes: AlumnoDeporteDTO[] = [];
  @ViewChild('docsGridRef')
  set docsGridRefSetter(ref: ElementRef<HTMLElement> | undefined) {
    this.docsGridRef = ref;
    this.reiniciarObservadorAlineacionDocs();
    this.programarRecalculoAlineacionDocs();
  }
  @ViewChild('docsActionsRef')
  set docsActionsRefSetter(ref: ElementRef<HTMLElement> | undefined) {
    this.docsActionsRef = ref;
    this.reiniciarObservadorAlineacionDocs();
    this.programarRecalculoAlineacionDocs();
  }
  @ViewChild('pdfCanvasRef')
  set pdfCanvasRefSetter(ref: ElementRef<HTMLCanvasElement> | undefined) {
    this.pdfCanvasRef = ref;
    if (ref && this.mostrarDocumentoVisor && this.esVisorCanvasComplementarioActivo()) {
      this.programarRenderPaginaPdf();
    }
  }

  deportesConMaterial: AlumnoDeporteDTO[] = [];
  deporteSeleccionado: string | null = null;
  material: MaterialExamenDTO | null = null;
  videoSeleccionado: MaterialExamenVideoDTO | null = null;
  videoSeleccionadoUrl: SafeUrl | null = null;

  documentoSeleccionado: MaterialExamenDocumentoDTO | null = null;
  mostrarDocumentoVisor: boolean = false;
  visorExpandido: boolean = false;
  visorExpandidoTopOffsetPx: number = 0;

  cargando: boolean = false;
  cargandoVideoSeleccionado: boolean = false;
  cargandoDocumentoSeleccionado: boolean = false;
  errorCarga: string | null = null;
  descripcionBloqueActual: string | null = null;
  docsActionsOffsetPx: number = 0;
  totalPaginasPdf: number = 0;
  paginaActualPdf: number = 1;
  zoomPdf: number = 1;
  cargandoPaginaPdf: boolean = false;
  errorVisorPdf: string | null = null;

  private materialSubscription: Subscription | null = null;
  private documentoPreviewSubscription: Subscription | null = null;
  private lastFetchKey: string | null = null;
  private bodyOverflowOriginal: string | null = null;
  private docsGridRef: ElementRef<HTMLElement> | undefined;
  private docsActionsRef: ElementRef<HTMLElement> | undefined;
  private docsResizeObserver: ResizeObserver | null = null;
  private rafAlineacionDocsId: number | null = null;
  private rafScrollDocsId: number | null = null;
  private rafPdfRenderId: number | null = null;
  private pdfCanvasRef: ElementRef<HTMLCanvasElement> | undefined;
  private pdfLoadingTask: PDFDocumentLoadingTask | null = null;
  private pdfDocument: PDFDocumentProxy | null = null;
  private pdfRenderTask: RenderTask | null = null;
  private readonly descripcionPreparacionPorGrado: Record<string, string> = {
    BLANCO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N BLANCO/AMARILLO',
    BLANCO_AMARILLO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AMARILLO',
    AMARILLO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AMARILLO/NARANJA',
    AMARILLO_NARANJA: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NARANJA',
    NARANJA: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NARANJA/VERDE',
    NARANJA_VERDE: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N VERDE',
    VERDE: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N VERDE/AZUL',
    VERDE_AZUL: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AZUL',
    AZUL: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AZUL/ROJO',
    AZUL_ROJO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO',
    ROJO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 1\u00BA PUM / NEGRO 1\u00BA DAN',
    ROJO_NEGRO_1_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 2\u00BA PUM',
    ROJO_NEGRO_2_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 3\u00BA PUM',
    ROJO_NEGRO_3_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 3\u00BA PUM',
    NEGRO_1_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 2\u00BA DAN',
    NEGRO_2_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 3\u00BA DAN',
    NEGRO_3_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 4\u00BA DAN',
    NEGRO_4_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 5\u00BA DAN',
    NEGRO_5_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 6\u00BA DAN',
  };

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly sanitizer: DomSanitizer
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['alumnoId'] && !changes['deportes']) {
      return;
    }

    this.deportesConMaterial = this.obtenerDeportesConMaterial();
    if (!this.alumnoId || this.alumnoId <= 0 || this.deportesConMaterial.length === 0) {
      this.resetearVista();
      return;
    }

    const deporteActualDisponible = this.deportesConMaterial.some(
      (item) => item.deporte === this.deporteSeleccionado
    );
    if (!deporteActualDisponible) {
      this.deporteSeleccionado = this.deportesConMaterial[0].deporte;
    }

    this.cargarMaterialSeleccionado();
  }

  ngOnDestroy(): void {
    this.materialSubscription?.unsubscribe();
    this.documentoPreviewSubscription?.unsubscribe();
    this.cerrarVisorExpandido();
    this.limpiarEstadoPdfViewer();
    this.docsResizeObserver?.disconnect();
    if (this.rafAlineacionDocsId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafAlineacionDocsId);
      this.rafAlineacionDocsId = null;
    }
    if (this.rafScrollDocsId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafScrollDocsId);
      this.rafScrollDocsId = null;
    }
    if (this.rafPdfRenderId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafPdfRenderId);
      this.rafPdfRenderId = null;
    }
  }

  onSeleccionarDeporte(deporte: string): void {
    if (!deporte || deporte === this.deporteSeleccionado) {
      return;
    }

    this.deporteSeleccionado = deporte;
    this.cargarMaterialSeleccionado(true);
  }

  onSeleccionarVideo(video: MaterialExamenVideoDTO): void {
    this.videoSeleccionado = video;
    this.cargarVideoSeleccionado(video);
  }

  onBloquearDescargaContenido(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    if (this.visorExpandido) {
      this.actualizarOffsetSuperiorVisorExpandido();
    }
    if (!this.mostrarDocumentoVisor || !this.esVisorCanvasComplementarioActivo()) {
      return;
    }
    this.programarRenderPaginaPdf();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.visorExpandido) {
      this.cerrarVisorExpandido();
    }
  }

  irAPaginaAnteriorPdf(): void {
    if (!this.pdfDocument || this.paginaActualPdf <= 1) {
      return;
    }
    this.paginaActualPdf -= 1;
    this.programarRenderPaginaPdf();
  }

  irAPaginaSiguientePdf(): void {
    if (!this.pdfDocument || this.paginaActualPdf >= this.totalPaginasPdf) {
      return;
    }
    this.paginaActualPdf += 1;
    this.programarRenderPaginaPdf();
  }

  aumentarZoomPdf(): void {
    if (!this.pdfDocument) {
      return;
    }
    this.zoomPdf = Math.min(2.2, this.zoomPdf + 0.15);
    this.programarRenderPaginaPdf();
  }

  reducirZoomPdf(): void {
    if (!this.pdfDocument) {
      return;
    }
    this.zoomPdf = Math.max(0.75, this.zoomPdf - 0.15);
    this.programarRenderPaginaPdf();
  }

  getZoomPdfPorcentaje(): number {
    return Math.round(this.zoomPdf * 100);
  }

  esVisorCanvasComplementarioActivo(): boolean {
    const documento = this.documentoSeleccionado;
    return !!documento && !!documento.previewable && this.esDocumentoPdf(documento);
  }

  onSeleccionarDocumento(documento: MaterialExamenDocumentoDTO): void {
    if (this.documentoSeleccionado?.id !== documento.id) {
      this.cerrarVisorExpandido();
    }
    const visorEstabaAbierto = this.mostrarDocumentoVisor;
    this.documentoSeleccionado = documento;
    this.mostrarDocumentoVisor =
      visorEstabaAbierto && this.puedeMostrarDocumentoEnVisorIntegrado(documento);
    this.cargarPreviewDocumentoSeleccionado(documento);
    this.programarRecalculoAlineacionDocs();
  }

  toggleDocumentoVisor(): void {
    const documento = this.documentoSeleccionado;
    if (!documento) {
      return;
    }
    if (!this.esDocumentoSeleccionadoPrevisualizable()) {
      return;
    }
    this.mostrarDocumentoVisor = !this.mostrarDocumentoVisor;
    if (!this.mostrarDocumentoVisor) {
      this.cerrarVisorExpandido();
      this.programarScrollListaAlDocumentoSeleccionado();
      return;
    }
    if (this.esVisorCanvasComplementarioActivo()) {
      this.programarRenderPaginaPdf();
    }
    this.programarRecalculoAlineacionDocs();
  }

  toggleVisorExpandido(): void {
    if (!this.mostrarDocumentoVisor || !this.esVisorCanvasComplementarioActivo()) {
      return;
    }
    this.visorExpandido = !this.visorExpandido;
    this.actualizarBloqueoScrollDocumento(this.visorExpandido);
    if (this.visorExpandido) {
      this.actualizarOffsetSuperiorVisorExpandido();
    } else {
      this.visorExpandidoTopOffsetPx = 0;
    }
    this.programarRenderPaginaPdf();
  }

  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  getDocumentoOpenUrl(): string | null {
    return this.documentoSeleccionado?.openUrl ?? null;
  }

  getDocumentoDownloadUrl(): string | null {
    const documento = this.documentoSeleccionado;
    if (!documento || !this.puedeDescargarDocumento(documento)) {
      return null;
    }
    return documento.downloadUrl ?? documento.openUrl ?? null;
  }

  esDocumentoActivoEnMovil(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    if (!documento || !this.documentoSeleccionado) {
      return false;
    }
    if (!globalThis.window?.matchMedia) {
      return false;
    }

    return (
      globalThis.window.matchMedia('(max-width: 768px)').matches &&
      documento.id === this.documentoSeleccionado.id
    );
  }

  onAbrirDocumentoDesdeLista(documento: MaterialExamenDocumentoDTO): void {
    if (!documento) {
      return;
    }
    if (this.documentoSeleccionado?.id !== documento.id) {
      this.onSeleccionarDocumento(documento);
    }

    if (!this.puedeAbrirDocumentoExterno(documento)) {
      if (this.esDocumentoSeleccionadoPrevisualizable()) {
        this.mostrarDocumentoVisor = true;
        this.programarRenderPaginaPdf();
        this.programarRecalculoAlineacionDocs();
        return;
      }
      this.mostrarAvisoDocumentoComplementarioProtegido();
      return;
    }

    this.abrirDocumentoSeleccionado();
  }

  onDescargarDocumentoDesdeLista(documento: MaterialExamenDocumentoDTO): void {
    if (!documento) {
      return;
    }
    if (!this.puedeDescargarDocumento(documento)) {
      this.mostrarAvisoDocumentoComplementarioProtegido();
      return;
    }
    if (this.documentoSeleccionado?.id !== documento.id) {
      this.onSeleccionarDocumento(documento);
    }
    this.descargarDocumentoSeleccionado();
  }

  onToggleVisorDesdeLista(documento: MaterialExamenDocumentoDTO): void {
    if (!documento?.previewable) {
      return;
    }
    if (this.documentoSeleccionado?.id !== documento.id) {
      this.onSeleccionarDocumento(documento);
      this.mostrarDocumentoVisor = true;
      if (this.esVisorCanvasComplementarioActivo()) {
        this.programarRenderPaginaPdf();
      }
      this.programarRecalculoAlineacionDocs();
      return;
    }
    this.toggleDocumentoVisor();
  }

  esDocumentoSeleccionadoPrevisualizable(): boolean {
    const documento = this.documentoSeleccionado;
    return !!documento && this.puedeMostrarDocumentoEnVisorIntegrado(documento);
  }

  esDocumentoPrincipal(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    if (!documento) {
      return false;
    }
    const nombre = (documento.fileName || '').toLowerCase();
    return documento.order === 0 || nombre === 'temario.pdf' || nombre.startsWith('temario.');
  }

  puedeDescargarDocumento(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    return this.esDocumentoPrincipal(documento);
  }

  puedeAbrirDocumentoExterno(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    return this.esDocumentoPrincipal(documento);
  }

  private puedeMostrarDocumentoEnVisorIntegrado(
    documento: MaterialExamenDocumentoDTO | null | undefined
  ): boolean {
    return !!documento?.previewable && !!documento.openUrl && this.esDocumentoPdf(documento);
  }

  getDocumentoBadge(documento: MaterialExamenDocumentoDTO | null | undefined): string {
    if (this.esDocumentoPrincipal(documento)) {
      return 'TEM';
    }
    if (!documento) {
      return 'DOC';
    }
    return documento.order > 0 && documento.order < 10000 ? String(documento.order) : 'DOC';
  }

  getDocumentosTemarioPrincipal(): MaterialExamenDocumentoDTO[] {
    const documentos = this.material?.documentos ?? [];
    return documentos.filter((documento) => this.esDocumentoPrincipal(documento));
  }

  getDocumentosComplementarios(): MaterialExamenDocumentoDTO[] {
    const documentos = this.material?.documentos ?? [];
    return documentos.filter((documento) => !this.esDocumentoPrincipal(documento));
  }

  abrirDocumentoSeleccionado(): void {
    const documento = this.documentoSeleccionado;
    if (!documento) {
      return;
    }
    if (!this.puedeAbrirDocumentoExterno(documento)) {
      if (this.esDocumentoSeleccionadoPrevisualizable()) {
        this.mostrarDocumentoVisor = true;
        this.programarRenderPaginaPdf();
        this.programarRecalculoAlineacionDocs();
      } else {
        this.mostrarAvisoDocumentoComplementarioProtegido();
      }
      return;
    }

    const openUrl = this.getDocumentoOpenUrl();
    if (!openUrl) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo abrir el documento seleccionado',
        icon: 'error',
      });
      return;
    }

    const popup = globalThis.window?.open(openUrl, '_blank', 'noopener');
    if (!popup) {
      globalThis.window?.location.assign(openUrl);
    }
  }

  descargarDocumentoSeleccionado(abrirEnNuevaPestana: boolean = false): void {
    const documento = this.documentoSeleccionado;
    if (!documento) {
      return;
    }
    if (!this.puedeDescargarDocumento(documento)) {
      this.mostrarAvisoDocumentoComplementarioProtegido();
      return;
    }

    const url = abrirEnNuevaPestana ? this.getDocumentoOpenUrl() : this.getDocumentoDownloadUrl();
    if (!url) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo procesar el documento seleccionado',
        icon: 'error',
      });
      return;
    }

    this.endpointsService.descargarArchivoPrivado(url).subscribe({
      next: (blob) => {
        const blobUrl = globalThis.URL.createObjectURL(blob);

        if (abrirEnNuevaPestana || this.esDispositivoIOS()) {
          const popup = globalThis.window?.open(blobUrl, '_blank', 'noopener');
          if (!popup && this.esDispositivoIOS()) {
            globalThis.window?.location.assign(blobUrl);
          }
          setTimeout(() => globalThis.URL.revokeObjectURL(blobUrl), 60_000);
          return;
        }

        const link = globalThis.document?.createElement('a');
        if (!link) {
          globalThis.URL.revokeObjectURL(blobUrl);
          return;
        }

        link.href = blobUrl;
        link.download = this.obtenerNombreDescarga(documento.fileName, documento.mimeType);
        link.click();
        globalThis.URL.revokeObjectURL(blobUrl);
      },
      error: () => {
        if (this.esDispositivoIOS()) {
          if (abrirEnNuevaPestana) {
            globalThis.window?.open(url, '_blank', 'noopener');
          } else {
            globalThis.window?.location.assign(url);
          }
          return;
        }
        Swal.fire({
          title: 'Error',
          text: 'No se pudo descargar el documento',
          icon: 'error',
        });
      },
    });
  }

  private mostrarAvisoDocumentoComplementarioProtegido(): void {
    Swal.fire({
      title: 'No permitido',
      text: 'El material complementario no se puede descargar ni abrir externamente.',
      icon: 'info',
      timer: 2200,
      showConfirmButton: false,
    });
  }

  private cargarMaterialSeleccionado(force: boolean = false): void {
    if (!this.alumnoId || !this.deporteSeleccionado) {
      this.resetearVista();
      return;
    }

    const fetchKey = `${this.alumnoId}-${this.deporteSeleccionado}`;
    if (!force && fetchKey === this.lastFetchKey) {
      return;
    }
    this.lastFetchKey = fetchKey;

    this.materialSubscription?.unsubscribe();
    this.cargando = true;
    this.cargandoVideoSeleccionado = false;
    this.cargandoDocumentoSeleccionado = false;
    this.errorCarga = null;
    this.material = null;
    this.documentoPreviewSubscription?.unsubscribe();
    this.cerrarVisorExpandido();
    this.limpiarEstadoPdfViewer();
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.documentoSeleccionado = null;
    this.mostrarDocumentoVisor = false;
    this.docsActionsOffsetPx = 0;

    this.materialSubscription = this.endpointsService
      .obtenerMaterialExamenAlumno(this.alumnoId, this.deporteSeleccionado)
      .subscribe({
        next: (material) => {
          this.material = this.normalizarMaterial(material);
          this.descripcionBloqueActual = this.obtenerDescripcionBloque(this.material);

          const primerVideo = this.material.videos[0] ?? null;
          if (primerVideo) {
            this.onSeleccionarVideo(primerVideo);
          }

          this.seleccionarDocumentoInicial();
          this.programarRecalculoAlineacionDocs();
          this.cargando = false;
        },
        error: () => {
          this.errorCarga = 'No se pudo cargar el material de examen.';
          this.cargando = false;
        },
      });
  }

  private seleccionarDocumentoInicial(): void {
    const documentos = this.material?.documentos ?? [];
    if (documentos.length === 0) {
      return;
    }

    const primerPdf = documentos.find((documento) => documento.previewable && !!documento.openUrl);
    this.onSeleccionarDocumento(primerPdf ?? documentos[0]);
  }

  private normalizarMaterial(material: MaterialExamenDTO | null | undefined): MaterialExamenDTO {
    const documentosCompat = this.obtenerDocumentosCompatibles(material);

    return {
      deporte: material?.deporte ?? this.deporteSeleccionado ?? '',
      gradoActual: material?.gradoActual ?? null,
      bloqueId: material?.bloqueId ?? null,
      temario: material?.temario ?? null,
      documentos: documentosCompat,
      videos: Array.isArray(material?.videos) ? material!.videos : [],
    };
  }

  private obtenerDocumentosCompatibles(
    material: MaterialExamenDTO | null | undefined
  ): MaterialExamenDocumentoDTO[] {
    const documentos = Array.isArray(material?.documentos)
      ? material!.documentos.filter((documento) => !!documento?.openUrl || !!documento?.downloadUrl)
      : [];

    if (documentos.length > 0) {
      return documentos.map((documento) => this.normalizarNombreTemarioDocumento(documento, material?.gradoActual));
    }

    if (!material?.temario?.downloadUrl) {
      return [];
    }

    const fileName = this.construirNombreTemarioParaGrado(
      material?.gradoActual,
      material.temario.fileName,
      'application/pdf'
    );
    const openUrl = material.temario.downloadUrl;
    const downloadUrl = this.agregarDownloadParam(openUrl);

    return [
      {
        id: fileName,
        fileName,
        title: this.construirTituloTemarioParaGrado(material?.gradoActual),
        order: 0,
        mimeType: 'application/pdf',
        previewable: true,
        openUrl,
        downloadUrl,
      },
    ];
  }

  private agregarDownloadParam(url: string): string {
    if (!url) {
      return url;
    }

    if (/[?&]download=/.test(url)) {
      return url.replace(/([?&]download=)[^&]*/i, '$1true');
    }

    return url.includes('?') ? `${url}&download=true` : `${url}?download=true`;
  }

  private esDispositivoIOS(): boolean {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef) {
      return false;
    }

    const userAgent = navigatorRef.userAgent ?? '';
    const esIOSClasico = /iPad|iPhone|iPod/.test(userAgent);
    const esIPadOS = navigatorRef.platform === 'MacIntel' && navigatorRef.maxTouchPoints > 1;
    return esIOSClasico || esIPadOS;
  }

  private obtenerNombreDescarga(
    nombre: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const base = (nombre ?? '').trim() || 'documento';
    if (base.includes('.')) {
      return base;
    }
    const extension = this.obtenerExtensionDesdeMime(mimeType);
    return extension ? `${base}.${extension}` : base;
  }

  private obtenerExtensionDesdeMime(mimeType: string | null | undefined): string | null {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    switch (mime) {
      case 'application/pdf':
        return 'pdf';
      case 'text/csv':
        return 'csv';
      case 'text/plain':
        return 'txt';
      case 'image/jpeg':
        return 'jpg';
      case 'image/png':
        return 'png';
      case 'image/webp':
        return 'webp';
      case 'image/gif':
        return 'gif';
      case 'application/msword':
        return 'doc';
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return 'docx';
      case 'application/vnd.ms-excel':
        return 'xls';
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return 'xlsx';
      default:
        return null;
    }
  }

  private normalizarNombreTemarioDocumento(
    documento: MaterialExamenDocumentoDTO,
    gradoActual: string | null | undefined
  ): MaterialExamenDocumentoDTO {
    if (!this.esDocumentoPrincipal(documento)) {
      return documento;
    }

    const fileName = this.construirNombreTemarioParaGrado(
      gradoActual,
      documento.fileName,
      documento.mimeType
    );

    return {
      ...documento,
      fileName,
      title: this.construirTituloTemarioParaGrado(gradoActual),
    };
  }

  private construirNombreTemarioParaGrado(
    gradoActual: string | null | undefined,
    fileNameOriginal: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const extension =
      this.extraerExtensionDesdeNombre(fileNameOriginal) ||
      this.obtenerExtensionDesdeMime(mimeType) ||
      'pdf';
    const titulo = this.construirTituloTemarioParaGrado(gradoActual);
    const baseSanitizada = titulo
      .replace(/\s*\/\s*/g, '-')
      .replace(/[\\:*?"<>|]/g, '-')
      .replace(/\s+/g, ' ')
      .trim();
    return `${baseSanitizada}.${extension}`;
  }

  private construirTituloTemarioParaGrado(gradoActual: string | null | undefined): string {
    const etiquetaCinturon = this.obtenerEtiquetaCinturonObjetivo(gradoActual);
    if (!etiquetaCinturon) {
      return 'Temario';
    }
    return `Temario para cintur\u00F3n ${etiquetaCinturon}`;
  }

  private obtenerEtiquetaCinturonObjetivo(gradoActual: string | null | undefined): string | null {
    const gradoNormalizado = (gradoActual || '').toUpperCase().trim();
    if (!gradoNormalizado) {
      return null;
    }

    const descripcion = this.descripcionPreparacionPorGrado[gradoNormalizado];
    if (!descripcion) {
      return null;
    }

    const prefijo = 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ';
    if (!descripcion.startsWith(prefijo)) {
      return null;
    }

    return this.formatearEtiquetaCinturon(descripcion.substring(prefijo.length));
  }

  private formatearEtiquetaCinturon(etiquetaRaw: string): string {
    const normalizada = (etiquetaRaw || '')
      .replace(/\s*\/\s*/g, '/')
      .replace(/\s+/g, ' ')
      .trim()
      .toLowerCase();

    let resultado = '';
    let capitalizar = true;
    for (const caracter of normalizada) {
      const esLetra = caracter.toLowerCase() !== caracter.toUpperCase();
      if (esLetra) {
        resultado += capitalizar ? caracter.toUpperCase() : caracter;
        capitalizar = false;
        continue;
      }

      resultado += caracter;
      capitalizar = caracter === ' ' || caracter === '/' || caracter === '-';
    }

    return resultado;
  }

  private extraerExtensionDesdeNombre(fileName: string | null | undefined): string | null {
    const nombre = (fileName || '').trim();
    if (!nombre) {
      return null;
    }
    const indice = nombre.lastIndexOf('.');
    if (indice < 0 || indice >= nombre.length - 1) {
      return null;
    }
    return nombre.substring(indice + 1).toLowerCase();
  }

  private generarTituloDesdeArchivo(fileName: string): string {
    const nombreSinExtension = fileName.replace(/\.[^.]+$/, '');
    return nombreSinExtension
      .replace(/^\d{1,3}[_\-.\s]+/, '')
      .replace(/[_-]+/g, ' ')
      .trim();
  }

  private obtenerDescripcionBloque(material: MaterialExamenDTO): string | null {
    const gradoNormalizado = (material.gradoActual || '').toUpperCase().trim();
    if (!gradoNormalizado) {
      return null;
    }

    return this.descripcionPreparacionPorGrado[gradoNormalizado] ?? null;
  }

  private obtenerDeportesConMaterial(): AlumnoDeporteDTO[] {
    if (!Array.isArray(this.deportes)) {
      return [];
    }

    return this.deportes.filter((item) =>
      !!item?.deporte &&
      !!item?.grado &&
      item.activo !== false &&
      (item.deporte === 'TAEKWONDO' || item.deporte === 'KICKBOXING')
    );
  }

  private resetearVista(): void {
    this.materialSubscription?.unsubscribe();
    this.documentoPreviewSubscription?.unsubscribe();
    this.cerrarVisorExpandido();
    this.limpiarEstadoPdfViewer();
    this.deporteSeleccionado = null;
    this.material = null;
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.documentoSeleccionado = null;
    this.errorCarga = null;
    this.cargando = false;
    this.cargandoVideoSeleccionado = false;
    this.cargandoDocumentoSeleccionado = false;
    this.mostrarDocumentoVisor = false;
    this.descripcionBloqueActual = null;
    this.docsActionsOffsetPx = 0;
    this.lastFetchKey = null;
  }

  private cerrarVisorExpandido(): void {
    if (!this.visorExpandido) {
      return;
    }
    this.visorExpandido = false;
    this.visorExpandidoTopOffsetPx = 0;
    this.actualizarBloqueoScrollDocumento(false);
  }

  private actualizarBloqueoScrollDocumento(activar: boolean): void {
    const body = globalThis.document?.body;
    if (!body) {
      return;
    }

    if (activar) {
      if (this.bodyOverflowOriginal === null) {
        this.bodyOverflowOriginal = body.style.overflow;
      }
      body.style.overflow = 'hidden';
      return;
    }

    if (this.bodyOverflowOriginal !== null) {
      body.style.overflow = this.bodyOverflowOriginal;
      this.bodyOverflowOriginal = null;
    }
  }

  private actualizarOffsetSuperiorVisorExpandido(): void {
    const documentRef = globalThis.document;
    if (!documentRef) {
      this.visorExpandidoTopOffsetPx = 0;
      return;
    }

    const candidatos = Array.from(
      documentRef.querySelectorAll<HTMLElement>('header.fixed-header, .admin-top-navbar')
    );
    let bottomMaximo = 0;

    for (const candidato of candidatos) {
      const estilos = globalThis.getComputedStyle?.(candidato);
      if (!estilos) {
        continue;
      }
      if (estilos.display === 'none' || estilos.visibility === 'hidden') {
        continue;
      }
      if (candidato.classList.contains('is-hidden')) {
        continue;
      }

      const rect = candidato.getBoundingClientRect();
      if (rect.height <= 0 || rect.bottom <= 0) {
        continue;
      }
      bottomMaximo = Math.max(bottomMaximo, rect.bottom);
    }

    this.visorExpandidoTopOffsetPx = Math.ceil(bottomMaximo) + 8;
  }

  private esDocumentoPdf(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    if (!documento) {
      return false;
    }

    const mime = (documento.mimeType ?? '').toLowerCase();
    const fileName = (documento.fileName ?? '').toLowerCase();
    return mime.includes('pdf') || fileName.endsWith('.pdf');
  }

  private reiniciarObservadorAlineacionDocs(): void {
    this.docsResizeObserver?.disconnect();
    this.docsResizeObserver = null;

    if (typeof globalThis.ResizeObserver !== 'function') {
      return;
    }

    const gridRef = this.docsGridRef?.nativeElement;
    if (!gridRef) {
      return;
    }

    this.docsResizeObserver = new globalThis.ResizeObserver(() => {
      this.programarRecalculoAlineacionDocs();
    });
    this.docsResizeObserver.observe(gridRef);
  }

  private programarRecalculoAlineacionDocs(): void {
    if (typeof globalThis.requestAnimationFrame !== 'function') {
      this.recalcularAlineacionDocs();
      return;
    }

    if (this.rafAlineacionDocsId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafAlineacionDocsId);
    }

    this.rafAlineacionDocsId = globalThis.requestAnimationFrame(() => {
      this.rafAlineacionDocsId = null;
      this.recalcularAlineacionDocs();
    });
  }

  private programarScrollListaAlDocumentoSeleccionado(): void {
    if (typeof globalThis.requestAnimationFrame !== 'function') {
      this.scrollListaAlDocumentoSeleccionado();
      this.recalcularAlineacionDocs();
      return;
    }

    if (this.rafScrollDocsId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafScrollDocsId);
      this.rafScrollDocsId = null;
    }

    this.rafScrollDocsId = globalThis.requestAnimationFrame(() => {
      this.rafScrollDocsId = globalThis.requestAnimationFrame(() => {
        this.rafScrollDocsId = null;
        this.scrollListaAlDocumentoSeleccionado();
        this.recalcularAlineacionDocs();
      });
    });
  }

  private scrollListaAlDocumentoSeleccionado(): void {
    if (this.mostrarDocumentoVisor) {
      return;
    }

    const gridElement = this.docsGridRef?.nativeElement;
    if (!gridElement) {
      return;
    }

    const listaDocs = gridElement.querySelector<HTMLElement>('.docs-list');
    const botonActivo = gridElement.querySelector<HTMLElement>('.doc-item-btn.is-active');
    if (!listaDocs || !botonActivo) {
      return;
    }

    const listaRect = listaDocs.getBoundingClientRect();
    const botonRect = botonActivo.getBoundingClientRect();
    const visibleCompleto = botonRect.top >= listaRect.top && botonRect.bottom <= listaRect.bottom;
    if (visibleCompleto) {
      return;
    }

    const topEnLista = botonRect.top - listaRect.top + listaDocs.scrollTop;
    const destino = topEnLista - (listaDocs.clientHeight - botonRect.height) / 2;
    const maxScroll = Math.max(0, listaDocs.scrollHeight - listaDocs.clientHeight);
    listaDocs.scrollTop = Math.max(0, Math.min(destino, maxScroll));
  }

  private recalcularAlineacionDocs(): void {
    const gridElement = this.docsGridRef?.nativeElement;
    const actionsElement = this.docsActionsRef?.nativeElement;
    if (!gridElement || !actionsElement || !this.documentoSeleccionado) {
      this.docsActionsOffsetPx = 0;
      return;
    }

    if (globalThis.window?.matchMedia('(max-width: 768px)').matches) {
      this.docsActionsOffsetPx = 0;
      return;
    }

    const botonActivo = gridElement.querySelector<HTMLElement>('.doc-item-btn.is-active');
    if (!botonActivo) {
      this.docsActionsOffsetPx = 0;
      return;
    }
    const listaDocs = gridElement.querySelector<HTMLElement>('.docs-list');
    if (!listaDocs) {
      this.docsActionsOffsetPx = 0;
      return;
    }

    const gridRect = gridElement.getBoundingClientRect();
    const listaRect = listaDocs.getBoundingClientRect();
    const botonRect = botonActivo.getBoundingClientRect();
    const actionsRect = actionsElement.getBoundingClientRect();
    const centroBoton = botonRect.top - gridRect.top + botonRect.height / 2;
    const offsetCrudo = centroBoton - actionsRect.height / 2;
    const offsetMinimo = Math.max(0, listaRect.top - gridRect.top);
    const offsetMaximo = Math.max(
      offsetMinimo,
      listaRect.bottom - gridRect.top - actionsRect.height
    );
    const offset = Math.max(offsetMinimo, Math.min(offsetCrudo, offsetMaximo));
    this.docsActionsOffsetPx = Math.round(offset);
  }

  private cargarVideoSeleccionado(video: MaterialExamenVideoDTO): void {
    this.videoSeleccionadoUrl = null;

    if (!video?.streamUrl) {
      this.cargandoVideoSeleccionado = false;
      return;
    }

    // Use direct stream URL so the browser can start playback with HTTP range requests.
    this.cargandoVideoSeleccionado = false;
    this.videoSeleccionadoUrl = this.sanitizer.bypassSecurityTrustUrl(video.streamUrl);
  }

  private cargarPreviewDocumentoSeleccionado(documento: MaterialExamenDocumentoDTO): void {
    this.documentoPreviewSubscription?.unsubscribe();
    this.cerrarVisorExpandido();
    this.limpiarEstadoPdfViewer();

    if (!this.puedeMostrarDocumentoEnVisorIntegrado(documento)) {
      if (documento.previewable && !this.esDocumentoPdf(documento)) {
        this.errorVisorPdf = 'Solo se pueden previsualizar documentos PDF.';
      }
      this.cargandoDocumentoSeleccionado = false;
      return;
    }

    const documentoId = documento.id;
    this.cargandoDocumentoSeleccionado = true;
    this.documentoPreviewSubscription = this.endpointsService
      .descargarArchivoPrivado(documento.openUrl)
      .subscribe({
        next: async (blob) => {
          if (this.documentoSeleccionado?.id !== documentoId) {
            this.cargandoDocumentoSeleccionado = false;
            return;
          }

          try {
            const data = await blob.arrayBuffer();
            await this.inicializarVisorPdfCanvas(data, documentoId);
          } catch {
            if (this.documentoSeleccionado?.id !== documentoId) {
              return;
            }
            this.errorVisorPdf = 'No se pudo preparar el visor de este documento.';
            this.cargandoDocumentoSeleccionado = false;
          }
        },
        error: () => {
          this.cargandoDocumentoSeleccionado = false;
          if (this.documentoSeleccionado?.id !== documentoId) {
            return;
          }
          this.errorVisorPdf = 'No se pudo cargar el documento seleccionado.';
        },
      });
  }

  private async inicializarVisorPdfCanvas(data: ArrayBuffer, documentoId: string): Promise<void> {
    this.errorVisorPdf = null;
    this.cargandoPaginaPdf = true;
    const loadingTask = getDocument({
      data,
      enableXfa: false,
      isEvalSupported: false,
      useSystemFonts: true,
    });
    this.pdfLoadingTask = loadingTask;

    try {
      const pdf = await loadingTask.promise;
      if (this.documentoSeleccionado?.id !== documentoId || this.pdfLoadingTask !== loadingTask) {
        await pdf.destroy();
        return;
      }

      this.pdfDocument = pdf;
      this.totalPaginasPdf = pdf.numPages;
      this.paginaActualPdf = 1;
      this.zoomPdf = 1;
      this.cargandoDocumentoSeleccionado = false;
      this.programarRenderPaginaPdf();
    } catch {
      if (this.pdfLoadingTask !== loadingTask || this.documentoSeleccionado?.id !== documentoId) {
        return;
      }
      this.errorVisorPdf = 'No se pudo cargar el visor de este PDF.';
      this.cargandoDocumentoSeleccionado = false;
      this.cargandoPaginaPdf = false;
    }
  }

  private programarRenderPaginaPdf(): void {
    if (!this.mostrarDocumentoVisor || !this.esVisorCanvasComplementarioActivo()) {
      return;
    }
    if (!this.pdfCanvasRef?.nativeElement || !this.pdfDocument) {
      return;
    }

    if (typeof globalThis.requestAnimationFrame !== 'function') {
      this.renderPaginaPdfActual();
      return;
    }

    if (this.rafPdfRenderId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafPdfRenderId);
    }

    this.rafPdfRenderId = globalThis.requestAnimationFrame(() => {
      this.rafPdfRenderId = null;
      this.renderPaginaPdfActual();
    });
  }

  private async renderPaginaPdfActual(): Promise<void> {
    const pdf = this.pdfDocument;
    const canvas = this.pdfCanvasRef?.nativeElement;
    if (!pdf || !canvas) {
      return;
    }

    if (this.pdfRenderTask) {
      try {
        this.pdfRenderTask.cancel();
      } catch {
        // no-op
      }
      this.pdfRenderTask = null;
    }

    this.cargandoPaginaPdf = true;
    this.errorVisorPdf = null;

    try {
      const page = await pdf.getPage(this.paginaActualPdf);
      const viewportBase = page.getViewport({ scale: 1 });
      const parentWidth = Math.max(260, (canvas.parentElement?.clientWidth ?? viewportBase.width) - 8);
      const fitScale = parentWidth / viewportBase.width;
      const finalScale = Math.max(0.6, Math.min(3.2, fitScale * this.zoomPdf));
      const viewport = page.getViewport({ scale: finalScale });
      const pixelRatio = Math.max(1, globalThis.window?.devicePixelRatio ?? 1);
      const context = canvas.getContext('2d', { alpha: false });
      if (!context) {
        throw new Error('No se pudo inicializar el canvas del visor');
      }

      canvas.width = Math.floor(viewport.width * pixelRatio);
      canvas.height = Math.floor(viewport.height * pixelRatio);
      canvas.style.width = `${Math.floor(viewport.width)}px`;
      canvas.style.height = `${Math.floor(viewport.height)}px`;
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.clearRect(0, 0, viewport.width, viewport.height);

      const renderTask = page.render({
        canvasContext: context,
        viewport,
      });
      this.pdfRenderTask = renderTask;
      await renderTask.promise;
      if (this.pdfRenderTask === renderTask) {
        this.pdfRenderTask = null;
      }
      this.cargandoPaginaPdf = false;
    } catch (error) {
      const esCancelado =
        typeof error === 'object' &&
        error !== null &&
        'name' in error &&
        (error as { name?: string }).name === 'RenderingCancelledException';
      if (esCancelado) {
        return;
      }
      this.errorVisorPdf = 'No se pudo renderizar la pagina del documento.';
      this.cargandoPaginaPdf = false;
    }
  }

  private limpiarEstadoPdfViewer(): void {
    if (this.rafPdfRenderId !== null) {
      globalThis.cancelAnimationFrame?.(this.rafPdfRenderId);
      this.rafPdfRenderId = null;
    }
    if (this.pdfRenderTask) {
      try {
        this.pdfRenderTask.cancel();
      } catch {
        // no-op
      }
      this.pdfRenderTask = null;
    }
    if (this.pdfLoadingTask) {
      try {
        this.pdfLoadingTask.destroy();
      } catch {
        // no-op
      }
      this.pdfLoadingTask = null;
    }
    if (this.pdfDocument) {
      try {
        this.pdfDocument.destroy();
      } catch {
        // no-op
      }
      this.pdfDocument = null;
    }
    this.totalPaginasPdf = 0;
    this.paginaActualPdf = 1;
    this.zoomPdf = 1;
    this.cargandoPaginaPdf = false;
    this.errorVisorPdf = null;
  }
}

