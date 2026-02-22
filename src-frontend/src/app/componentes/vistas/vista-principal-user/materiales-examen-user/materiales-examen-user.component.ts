import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
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

  deportesConMaterial: AlumnoDeporteDTO[] = [];
  deporteSeleccionado: string | null = null;
  material: MaterialExamenDTO | null = null;
  videoSeleccionado: MaterialExamenVideoDTO | null = null;
  videoSeleccionadoUrl: SafeUrl | null = null;

  documentoSeleccionado: MaterialExamenDocumentoDTO | null = null;
  documentoSeleccionadoUrl: SafeResourceUrl | null = null;
  mostrarDocumentoVisor: boolean = false;

  cargando: boolean = false;
  cargandoVideoSeleccionado: boolean = false;
  cargandoDocumentoSeleccionado: boolean = false;
  errorCarga: string | null = null;
  descripcionBloqueActual: string | null = null;

  private materialSubscription: Subscription | null = null;
  private documentoPreviewSubscription: Subscription | null = null;
  private lastFetchKey: string | null = null;
  private documentoBlobUrl: string | null = null;
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
    this.revocarBlobDocumento();
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

  onSeleccionarDocumento(documento: MaterialExamenDocumentoDTO): void {
    this.documentoSeleccionado = documento;
    this.mostrarDocumentoVisor = false;
    this.cargarPreviewDocumentoSeleccionado(documento);
  }

  toggleDocumentoVisor(): void {
    if (!this.esDocumentoSeleccionadoPrevisualizable()) {
      return;
    }
    this.mostrarDocumentoVisor = !this.mostrarDocumentoVisor;
  }

  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  getDocumentoOpenUrl(): string | null {
    return this.documentoSeleccionado?.openUrl ?? null;
  }

  getDocumentoDownloadUrl(): string | null {
    return this.documentoSeleccionado?.downloadUrl ?? this.documentoSeleccionado?.openUrl ?? null;
  }

  esDocumentoSeleccionadoPrevisualizable(): boolean {
    return !!this.documentoSeleccionado?.previewable && !!this.documentoSeleccionadoUrl;
  }

  esDocumentoPrincipal(documento: MaterialExamenDocumentoDTO | null | undefined): boolean {
    if (!documento) {
      return false;
    }
    const nombre = (documento.fileName || '').toLowerCase();
    return documento.order === 0 || nombre === 'temario.pdf' || nombre.startsWith('temario.');
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

  abrirDocumentoSeleccionado(): void {
    const documento = this.documentoSeleccionado;
    if (!documento?.previewable) {
      this.descargarDocumentoSeleccionado();
      return;
    }

    this.descargarDocumentoSeleccionado(true);
  }

  descargarDocumentoSeleccionado(abrirEnNuevaPestana: boolean = false): void {
    const documento = this.documentoSeleccionado;
    const url = abrirEnNuevaPestana ? this.getDocumentoOpenUrl() : this.getDocumentoDownloadUrl();

    if (!documento || !url) {
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
    this.revocarBlobDocumento();
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.documentoSeleccionado = null;
    this.documentoSeleccionadoUrl = null;
    this.mostrarDocumentoVisor = false;

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
    this.revocarBlobDocumento();
    this.deporteSeleccionado = null;
    this.material = null;
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.documentoSeleccionado = null;
    this.documentoSeleccionadoUrl = null;
    this.errorCarga = null;
    this.cargando = false;
    this.cargandoVideoSeleccionado = false;
    this.cargandoDocumentoSeleccionado = false;
    this.mostrarDocumentoVisor = false;
    this.descripcionBloqueActual = null;
    this.lastFetchKey = null;
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
    this.revocarBlobDocumento();
    this.documentoSeleccionadoUrl = null;

    if (!documento?.previewable || !documento.openUrl) {
      this.cargandoDocumentoSeleccionado = false;
      return;
    }

    const documentoId = documento.id;
    this.cargandoDocumentoSeleccionado = true;
    this.documentoPreviewSubscription = this.endpointsService
      .descargarArchivoPrivado(documento.openUrl)
      .subscribe({
        next: (blob) => {
          this.cargandoDocumentoSeleccionado = false;
          if (this.documentoSeleccionado?.id !== documentoId) {
            return;
          }

          const blobUrl = globalThis.URL.createObjectURL(blob);
          this.documentoBlobUrl = blobUrl;
          this.documentoSeleccionadoUrl = this.sanitizer.bypassSecurityTrustResourceUrl(blobUrl);
        },
        error: () => {
          this.cargandoDocumentoSeleccionado = false;
          if (this.documentoSeleccionado?.id !== documentoId) {
            return;
          }
          this.documentoSeleccionadoUrl = null;
        },
      });
  }

  private revocarBlobDocumento(): void {
    if (!this.documentoBlobUrl) {
      return;
    }
    globalThis.URL.revokeObjectURL(this.documentoBlobUrl);
    this.documentoBlobUrl = null;
  }
}
