import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import { PdfViewerComponent } from '../pdf-viewer/pdf-viewer.component';
import { DocumentoInforme, descargarDocumentoInforme } from '../../../servicios/generales/informe-pdf.service';
import { ScrollLockRelease, ScrollLockService } from '../../../servicios/generales/scroll-lock.service';

@Component({
  selector: 'app-informe-pdf-modal', standalone: true, imports: [PdfViewerComponent],
  template: `
    <dialog #dialog aria-labelledby="informe-pdf-titulo" (keydown.tab)="contenerFoco($event)" (cancel)="cancelar($event)" (close)="cerrar.emit()">
      <header><h2 id="informe-pdf-titulo">{{ actual?.titulo }}</h2>
        <button type="button" autofocus (click)="dialog.close()">Cerrar</button></header>
      @if (aviso) { <p role="alert">{{ aviso }}</p> }
      @if (documentos.length > 1) {
        <label for="documento-informe">Documento</label>
        <select id="documento-informe" [value]="indice" (change)="indice = +$any($event.target).value">
          @for (documento of documentos; track $index) { <option [value]="$index">{{ documento.titulo }}</option> }
        </select>
      }
      @if (actual; as documento) {
        <button type="button" class="descargar" (click)="descargar()">Descargar PDF</button>
        <app-pdf-viewer [blob]="documento.blob" [titulo]="documento.titulo" />
      }
    </dialog>`,
  styles: `
    dialog { width: min(1100px, 96vw); max-width: 96vw; max-height: 94dvh; padding: 1rem; border: 0; border-radius: 12px; color: #1f2937; }
    dialog::backdrop { background: #090f189e; }
    header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    h2 { font-size: 1.2rem; overflow-wrap: anywhere; }
    button, select { min-height: 44px; padding: .5rem .8rem; border: 1px solid #214d85; border-radius: 8px; background: #f0f6ff; color: #214d85; }
    select { max-width: 100%; } label { margin-right: .5rem; } .descargar { margin-top: .75rem; }
    button:focus-visible, select:focus-visible { outline: 3px solid #214d85; outline-offset: 2px; }
    [role=alert] { color: #9f1239; }
  `,
})
export class InformePdfModalComponent implements AfterViewInit, OnDestroy {
  @Input() documentos: DocumentoInforme[] = [];
  @Input() aviso = '';
  @Input() focoRetorno: HTMLElement | null = null;
  @Output() cerrar = new EventEmitter<void>();
  @ViewChild('dialog') dialog!: ElementRef<HTMLDialogElement>;
  @ViewChild(PdfViewerComponent) visor?: PdfViewerComponent;
  indice = 0;
  private readonly scroll = inject(ScrollLockService);
  private release?: ScrollLockRelease;
  private foco?: HTMLElement;
  get actual(): DocumentoInforme | undefined { return this.documentos[this.indice]; }
  ngAfterViewInit(): void {
    this.foco = this.focoRetorno ?? document.activeElement as HTMLElement;
    this.release = this.scroll.lock();
    this.dialog.nativeElement.showModal();
  }
  cancelar(event: Event): void {
    if (this.visor?.cerrarExpansion()) { event.preventDefault(); }
  }
  contenerFoco(event: Event): void {
    const keyboard = event as KeyboardEvent;
    const elementos = Array.from(this.dialog.nativeElement.querySelectorAll<HTMLElement>('button:not(:disabled), select:not(:disabled)'));
    const primero = elementos[0];
    const ultimo = elementos[elementos.length - 1];
    if (keyboard.shiftKey && document.activeElement === primero) {
      keyboard.preventDefault(); ultimo?.focus();
    } else if (!keyboard.shiftKey && document.activeElement === ultimo) {
      keyboard.preventDefault(); primero?.focus();
    }
  }
  descargar(): void { if (this.actual) { descargarDocumentoInforme(this.actual); } }
  ngOnDestroy(): void {
    this.dialog.nativeElement.close();
    this.release?.();
    if (this.foco?.isConnected) { this.foco.focus({ preventScroll: true }); }
  }
}
