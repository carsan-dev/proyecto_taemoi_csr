import { CommonModule } from '@angular/common';
import { Component, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { PdfViewerComponent } from '../../../generales/pdf-viewer/pdf-viewer.component';
import { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';
import {
  MaterialExamenDTO,
  MaterialExamenDocumentoDTO,
  MaterialExamenVideoDTO,
} from '../../../../interfaces/material-examen';
import { getDeporteLabel } from '../../../../enums/deporte';

@Component({
  selector: 'app-materiales-examen-user',
  standalone: true,
  imports: [CommonModule, PdfViewerComponent],
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
  deportesConMaterial: AlumnoDeporteDTO[] = [];
  deporteSeleccionado: string | null = null;
  material: MaterialExamenDTO | null = null;
  videoSeleccionado: MaterialExamenVideoDTO | null = null;
  videoSeleccionadoUrl: string | null = null;
  videosAnterioresExpandido: boolean = false;

  documentoSeleccionado: MaterialExamenDocumentoDTO | null = null;
  mostrarDocumentoVisor: boolean = false;
  documentoBlob: Blob | null = null;

  cargando: boolean = false;
  cargandoVideoSeleccionado: boolean = false;
  cargandoDocumentoSeleccionado: boolean = false;
  errorCarga: string | null = null;
  descripcionBloqueActual: string | null = null;
  docsActionsOffsetPx: number = 0;
  errorVisorPdf: string | null = null;

  private materialSubscription: Subscription | null = null;
  private documentoPreviewSubscription: Subscription | null = null;
  private lastFetchKey: string | null = null;
  private docsGridRef: ElementRef<HTMLElement> | undefined;
  private docsActionsRef: ElementRef<HTMLElement> | undefined;
  private docsResizeObserver: ResizeObserver | null = null;
  private rafAlineacionDocsId: number | null = null;
  private rafScrollDocsId: number | null = null;
  private readonly mobileViewportMediaQuery =
    '(max-width: 768px), (max-height: 540px) and (pointer: coarse)';
  private readonly etiquetasSiguienteGrado: Record<string, string> = {
    BLANCO_AMARILLO: 'BLANCO-AMARILLO',
    AMARILLO: 'AMARILLO',
    AMARILLO_NARANJA: 'AMARILLO-NARANJA',
    NARANJA: 'NARANJA',
    NARANJA_VERDE: 'NARANJA-VERDE',
    VERDE: 'VERDE',
    VERDE_AZUL: 'VERDE-AZUL',
    AZUL: 'AZUL',
    AZUL_ROJO: 'AZUL-ROJO',
    ROJO: 'ROJO',
    ROJO_NEGRO_1_PUM: 'NEGRO 1\u00BA PUM',
    ROJO_NEGRO_2_PUM: 'NEGRO 2\u00BA PUM',
    ROJO_NEGRO_3_PUM: 'NEGRO 3\u00BA PUM',
    NEGRO_1_DAN: 'NEGRO 1\u00BA DAN',
    NEGRO_2_DAN: 'NEGRO 2\u00BA DAN',
    NEGRO_3_DAN: 'NEGRO 3\u00BA DAN',
    NEGRO_4_DAN: 'NEGRO 4\u00BA DAN',
    NEGRO_5_DAN: 'NEGRO 5\u00BA DAN',
  };

  constructor(
    private readonly endpointsService: EndpointsService
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

  toggleVideosAnteriores(): void {
    this.videosAnterioresExpandido = !this.videosAnterioresExpandido;
  }

  onBloquearDescargaContenido(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
  }

  esVisorCanvasComplementarioActivo(): boolean {
    const documento = this.documentoSeleccionado;
    return !!documento && !!documento.previewable && this.esDocumentoPdf(documento);
  }

  onSeleccionarDocumento(documento: MaterialExamenDocumentoDTO): void {
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
        this.programarScrollListaAlDocumentoSeleccionado();
      return;
    }

    this.programarRecalculoAlineacionDocs();
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

    return this.esVistaMovilActiva() && documento.id === this.documentoSeleccionado.id;
  }

  esVideoActivoEnMovil(video: MaterialExamenVideoDTO | null | undefined): boolean {
    if (!video || !this.videoSeleccionado) {
      return false;
    }

    return this.esVistaMovilActiva() && video.id === this.videoSeleccionado.id;
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
    this.limpiarEstadoPdfViewer();
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.videosAnterioresExpandido = false;
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
      siguienteGrado: material?.siguienteGrado ?? null,
      bloqueId: material?.bloqueId ?? null,
      temario: material?.temario ?? null,
      documentos: documentosCompat,
      videos: Array.isArray(material?.videos) ? material!.videos : [],
      videosAnteriores: Array.isArray(material?.videosAnteriores) ? material!.videosAnteriores : [],
      gruposVideosAnteriores: Array.isArray(material?.gruposVideosAnteriores)
        ? material!.gruposVideosAnteriores.filter((grupo) => Array.isArray(grupo?.videos) && grupo.videos.length > 0)
        : [],
    };
  }

  private obtenerDocumentosCompatibles(
    material: MaterialExamenDTO | null | undefined
  ): MaterialExamenDocumentoDTO[] {
    const documentos = Array.isArray(material?.documentos)
      ? material!.documentos.filter((documento) => !!documento?.openUrl || !!documento?.downloadUrl)
      : [];

    if (documentos.length > 0) {
      return documentos.map((documento) => this.normalizarNombreTemarioDocumento(documento, material?.siguienteGrado));
    }

    if (!material?.temario?.downloadUrl) {
      return [];
    }

    const fileName = this.construirNombreTemarioParaGrado(
      material?.siguienteGrado,
      material.temario.fileName,
      'application/pdf'
    );
    const openUrl = material.temario.downloadUrl;
    const downloadUrl = this.agregarDownloadParam(openUrl);

    return [
      {
        id: fileName,
        fileName,
        title: this.construirTituloTemarioParaGrado(material?.siguienteGrado),
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
    siguienteGrado: string | null | undefined
  ): MaterialExamenDocumentoDTO {
    if (!this.esDocumentoPrincipal(documento)) {
      return documento;
    }

    const fileName = this.construirNombreTemarioParaGrado(
      siguienteGrado,
      documento.fileName,
      documento.mimeType
    );

    return {
      ...documento,
      fileName,
      title: this.construirTituloTemarioParaGrado(siguienteGrado),
    };
  }

  private construirNombreTemarioParaGrado(
    siguienteGrado: string | null | undefined,
    fileNameOriginal: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const extension =
      this.extraerExtensionDesdeNombre(fileNameOriginal) ||
      this.obtenerExtensionDesdeMime(mimeType) ||
      'pdf';
    const titulo = this.construirTituloTemarioParaGrado(siguienteGrado);
    const baseSanitizada = titulo
      .replace(/\s*\/\s*/g, '-')
      .replace(/[\\:*?"<>|]/g, '-')
      .replace(/\s+/g, ' ')
      .trim();
    return `${baseSanitizada}.${extension}`;
  }

  private construirTituloTemarioParaGrado(siguienteGrado: string | null | undefined): string {
    const etiquetaCinturon = this.obtenerEtiquetaCinturonObjetivo(siguienteGrado);
    if (!etiquetaCinturon) {
      return 'Temario';
    }
    return `Temario para cintur\u00F3n ${this.formatearEtiquetaCinturon(etiquetaCinturon)}`;
  }

  private obtenerEtiquetaCinturonObjetivo(siguienteGrado: string | null | undefined): string | null {
    if (!siguienteGrado) {
      return null;
    }
    return this.etiquetasSiguienteGrado[siguienteGrado] ?? null;
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
    const etiqueta = this.obtenerEtiquetaCinturonObjetivo(material.siguienteGrado);
    if (!etiqueta) {
      return null;
    }
    return `PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ${etiqueta}`;
  }

  private obtenerDeportesConMaterial(): AlumnoDeporteDTO[] {
    if (!Array.isArray(this.deportes)) {
      return [];
    }

    const deportesConMaterial = this.deportes.filter((item) =>
      !!item?.deporte &&
      !!item?.grado &&
      item.activo !== false &&
      (item.deporte === 'TAEKWONDO' || item.deporte === 'KICKBOXING')
    );

    return deportesConMaterial
      .map((item, index) => ({ item, index }))
      .sort((a, b) => {
        if (!!a.item.principal === !!b.item.principal) {
          return a.index - b.index;
        }
        return a.item.principal ? -1 : 1;
      })
      .map(({ item }) => item);
  }

  private resetearVista(): void {
    this.materialSubscription?.unsubscribe();
    this.documentoPreviewSubscription?.unsubscribe();
    this.limpiarEstadoPdfViewer();
    this.deporteSeleccionado = null;
    this.material = null;
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.videosAnterioresExpandido = false;
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

    if (this.esVistaMovilActiva()) {
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

  private esVistaMovilActiva(): boolean {
    const windowRef = globalThis.window;
    if (!windowRef || typeof windowRef.matchMedia !== 'function') {
      return false;
    }

    return windowRef.matchMedia(this.mobileViewportMediaQuery).matches;
  }

  private cargarVideoSeleccionado(video: MaterialExamenVideoDTO): void {
    this.videoSeleccionadoUrl = null;

    if (!video?.id || !this.alumnoId || !this.deporteSeleccionado) {
      this.cargandoVideoSeleccionado = false;
      return;
    }

    this.cargandoVideoSeleccionado = false;
    this.videoSeleccionadoUrl =
      video.streamUrl ||
      this.endpointsService.obtenerUrlVideoMaterialExamenAlumno(
        this.alumnoId,
        this.deporteSeleccionado,
        video.id
      );
  }

  private cargarPreviewDocumentoSeleccionado(documento: MaterialExamenDocumentoDTO): void {
    this.documentoPreviewSubscription?.unsubscribe();
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
        next: (blob) => {
          if (this.documentoSeleccionado?.id !== documentoId) { return; }
          this.documentoBlob = blob;
          this.cargandoDocumentoSeleccionado = false;
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

  private limpiarEstadoPdfViewer(): void {
    this.documentoBlob = null;
    this.errorVisorPdf = null;
  }
}
