import { CommonModule } from '@angular/common';
import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

import { RetoDiarioInfo } from '../../../../interfaces/reto-diario-info';

@Component({
  selector: 'app-reto-diario-ayuda-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reto-diario-ayuda-modal.component.html',
  styleUrl: './reto-diario-ayuda-modal.component.scss',
})
export class RetoDiarioAyudaModalComponent {
  @Input({ required: true }) reto!: RetoDiarioInfo;
  @Input() checklistItems: string[] = [];
  @Input() checklistMarcados: boolean[] = [];
  @Input() checklistConfirmada: boolean = false;

  @Output() cerrar = new EventEmitter<void>();
  @Output() checklistItemChange = new EventEmitter<{ index: number; checked: boolean }>();
  @Output() checklistConfirmadaChange = new EventEmitter<boolean>();
  @Output() buscarExplicacion = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscPressed(): void {
    this.cerrar.emit();
  }

  onOverlayClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cerrar.emit();
    }
  }

  get checklistCompleta(): boolean {
    if (!this.checklistItems.length) {
      return false;
    }

    return this.checklistItems.every((_, index) => !!this.checklistMarcados[index]);
  }

  onChecklistItemChange(index: number, event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.checklistItemChange.emit({ index, checked: target.checked });
  }

  onChecklistConfirmadaChange(event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }
    if (target.checked && !this.checklistCompleta) {
      return;
    }

    this.checklistConfirmadaChange.emit(target.checked);
  }
}
