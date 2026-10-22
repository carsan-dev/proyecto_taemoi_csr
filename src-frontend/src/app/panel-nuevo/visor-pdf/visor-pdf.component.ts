import { DOCUMENT } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
} from '@angular/core';

import { PdfViewerComponent } from '../../componentes/generales/pdf-viewer/pdf-viewer.component';
import { DocumentoInforme, descargarDocumentoInforme } from '../../servicios/generales/informe-pdf.service';
import { ScrollLockRelease, ScrollLockService } from '../../servicios/generales/scroll-lock.service';
import {
  CONSULTA_HOJA,
  PilaCapas,
  anchoDeHoja,
  contenerFoco,
  devolverFoco,
  disparadorActual,
  esClicEnElVelo,
} from '../capas/capa-modal';
import { EsqueletoComponent } from '../carga/esqueleto.component';

let siguienteId = 0;

/**
 * Visor de PDF del panel nuevo (8b-D2a): sustituye a `app-informe-pdf-modal` en el listado nuevo, sobre las capas de
 * 8b-D1. Un `<dialog>` modal (el resto queda inerte, el foco contenido) de `min(1040px, 100vw - 48px)` por
 * `100dvh - 48px`, con cabecera de 56 px (título del informe, «Descargar PDF» y la X); por debajo de 768 px, a pantalla
 * completa. El cuerpo es el visor de hoy (`app-pdf-viewer` con el Blob) y la descarga, la de hoy (mismo nombre, la URL
 * se crea y se libera al momento). Mientras pdf.js lee el Blob, un esqueleto (a los 300 ms). Esc (primero sale de la
 * expansión del visor), la X y el clic en el velo cierran; el foco vuelve a quien lo abrió.
 */
@Component({
  selector: 'pn-visor-pdf',
  standalone: true,
  imports: [PdfViewerComponent, EsqueletoComponent],
  templateUrl: './visor-pdf.component.html',
  styleUrls: ['./visor-pdf.component.scss'],
})
export class VisorPdfComponent implements AfterViewInit, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly pila = inject(PilaCapas);
  private readonly documento = inject(DOCUMENT);
  private readonly scroll = inject(ScrollLockService);

  @Input() documentos: DocumentoInforme[] = [];
  /** Aviso de un lote parcial (algún PDF no se pudo generar), como hoy. */
  @Input() aviso = '';
  /** Quien pidió el PDF (el botón que lo generó); si no, el elemento enfocado al abrir. */
  @Input() focoRetorno: HTMLElement | null = null;
  @Output() readonly cerrar = new EventEmitter<void>();

  @ViewChild('capa', { static: true }) private capaRef!: ElementRef<HTMLDialogElement>;
  @ViewChild('cerrarBoton', { static: true }) private cerrarRef!: ElementRef<HTMLButtonElement>;
  @ViewChild(PdfViewerComponent) visor?: PdfViewerComponent;

  readonly id = `pn-visor-${++siguienteId}`;
  indice = 0;
  hoja = anchoDeHoja();
  private disparador: HTMLElement | null = null;
  private liberarScroll?: ScrollLockRelease;
  private destruido = false;
  private cierrePedido = false;
  private consulta: MediaQueryList | null = null;
  private readonly alCambiarAncho = (e: MediaQueryListEvent) => {
    this.hoja = e.matches;
    this.cdr.detectChanges();
  };

  get actual(): DocumentoInforme | undefined {
    return this.documentos[this.indice];
  }

  /** pdf.js aún lee el Blob (sin páginas ni error): el esqueleto ocupa el sitio del documento. */
  get cargando(): boolean {
    const v = this.visor;
    return !v || (v.totalPaginasPdf === 0 && !v.errorVisorPdf && (v.cargandoPaginaPdf || !!v.blob));
  }

  ngAfterViewInit(): void {
    this.disparador = this.focoRetorno ?? disparadorActual(this.documento);
    this.liberarScroll = this.scroll.lock();
    const capa = this.capaRef.nativeElement;
    capa.showModal();
    this.pila.abrir(capa);
    // Como hoy, el foco empieza en el cierre.
    this.cerrarRef.nativeElement.focus();
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
    const capa = this.capaRef.nativeElement;
    this.pila.cerrar(capa);
    if (capa.open) {
      capa.close();
    }
    this.liberarScroll?.();
    devolverFoco(this.disparador);
  }

  elegir(valor: string): void {
    this.indice = Number(valor) || 0;
  }

  descargar(): void {
    if (this.actual) {
      descargarDocumentoInforme(this.actual);
    }
  }

  /** Esc: primero sale de la expansión del visor; si no está expandido, cierra. */
  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      if (!this.visor?.cerrarExpansion()) {
        this.pedirCierre();
      }
      return;
    }
    contenerFoco(evento, this.capaRef.nativeElement);
  }

  alCancelarNativo(evento: Event): void {
    evento.preventDefault();
    if (!this.visor?.cerrarExpansion()) {
      this.pedirCierre();
    }
  }

  /** El navegador cerró el `<dialog>` sin quitarlo (un segundo Esc): se pide el cierre una vez. */
  alCerrarNativo(): void {
    if (!this.destruido) {
      this.pedirCierre();
    }
  }

  alClic(evento: MouseEvent): void {
    if (esClicEnElVelo(evento, this.capaRef.nativeElement)) {
      this.pedirCierre();
    }
  }

  pedirCierre(): void {
    if (this.cierrePedido || this.destruido) {
      return;
    }
    this.cierrePedido = true;
    this.cerrar.emit();
  }
}
