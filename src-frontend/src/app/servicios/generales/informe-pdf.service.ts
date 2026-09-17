import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, finalize, takeUntil } from 'rxjs';
import Swal, { SweetAlertOptions, SweetAlertResult } from 'sweetalert2';
import { LoadingService } from './loading.service';

export type AccionInforme = 'ver' | 'descargar';
export interface DocumentoInforme { blob: Blob; nombreArchivo: string; titulo: string; }
export interface ResultadoInformes { documentos: DocumentoInforme[]; aviso?: string; }

export function descargarDocumentoInforme(documento: DocumentoInforme): void {
  const url = URL.createObjectURL(documento.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = documento.nombreArchivo;
  link.click();
  URL.revokeObjectURL(url);
}

// Provided by each report screen: navigating away cancels its requests and dialogs.
@Injectable()
export class InformePdfService implements OnDestroy {
  documentos: DocumentoInforme[] = [];
  aviso = '';
  generando = false;
  seleccionando = false;
  destruido = false;
  focoRetorno: HTMLElement | null = null;
  private focoSeleccion: HTMLElement | null = null;
  private readonly destroy$ = new Subject<void>();
  private readonly loading = inject(LoadingService);
  get ocupado(): boolean { return this.generando || this.seleccionando; }

  async seleccionar(options: SweetAlertOptions): Promise<SweetAlertResult | null> {
    if (this.ocupado || this.destruido) { return null; }
    this.focoSeleccion = document.activeElement as HTMLElement;
    this.seleccionando = true;
    try {
      const result = await Swal.fire(options);
      if (result.isDismissed) { this.focoSeleccion = null; }
      return this.destruido ? null : result;
    } finally { this.seleccionando = false; }
  }

  generar(solicitud: Observable<ResultadoInformes>, accion: AccionInforme, error: string): void {
    if (this.ocupado || this.destruido) { return; }
    this.focoRetorno = this.focoSeleccion?.isConnected ? this.focoSeleccion : document.activeElement as HTMLElement;
    this.focoSeleccion = null;
    this.generando = true;
    this.loading.show();
    solicitud.pipe(takeUntil(this.destroy$), finalize(() => {
      this.generando = false; this.loading.hide();
    })).subscribe({
      next: ({ documentos, aviso = '' }) => {
        if (this.destruido) { return; }
        if (accion === 'ver' && documentos.length) {
          this.documentos = documentos;
          this.aviso = aviso;
        } else {
          if (accion === 'descargar') { documentos.forEach(descargarDocumentoInforme); }
          if (aviso) { void Swal.fire('Aviso', aviso, 'warning'); }
        }
      },
      error: () => { if (!this.destruido) { void Swal.fire('Error', error, 'error'); } },
    });
  }
  cerrar(): void { this.documentos = []; this.aviso = ''; this.focoRetorno = null; }
  ngOnDestroy(): void {
    this.destruido = true;
    this.destroy$.next(); this.destroy$.complete();
    if (this.seleccionando) { Swal.close(); }
    this.cerrar();
  }
}
