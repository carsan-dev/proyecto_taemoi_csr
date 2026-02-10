import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-eventos-vista',
  standalone: true,
  imports: [CommonModule, SkeletonCardComponent],
  templateUrl: './eventos-vista.component.html',
  styleUrl: './eventos-vista.component.scss',
  host: {
    '[class.preview]': 'previewMode'
  }
})
export class EventosVistaComponent implements OnChanges {
  @Input() eventos: any[] = [];
  @Input() isLoading: boolean = false;
  @Input() enableNavigation: boolean = true;
  @Input() showHero: boolean = true;
  @Input() previewMode: boolean = false;
  @Input() showUserBackButton: boolean = false;

  @Output() eventoClick = new EventEmitter<number>();
  @Output() backToUser = new EventEmitter<void>();

  private readonly loadedImages = new Set<number>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['eventos']) {
      const currentIds = new Set(
        this.eventos
          .map((evento) => evento?.id)
          .filter((id) => id !== null && id !== undefined)
      );
      for (const id of this.loadedImages) {
        if (!currentIds.has(id)) {
          this.loadedImages.delete(id);
        }
      }
    }
  }

  onEventoClick(eventoId: number): void {
    if (!this.enableNavigation) {
      return;
    }
    this.eventoClick.emit(eventoId);
  }

  onBackToUser(): void {
    this.backToUser.emit();
  }

  isImageLoaded(eventoId: number): boolean {
    return this.loadedImages.has(eventoId);
  }

  onImageLoad(eventoId: number): void {
    if (eventoId === null || eventoId === undefined) {
      return;
    }
    this.loadedImages.add(eventoId);
  }

  onImageError(eventoId: number): void {
    if (eventoId === null || eventoId === undefined) {
      return;
    }
    this.loadedImages.add(eventoId);
  }
}
