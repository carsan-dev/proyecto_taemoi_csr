import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
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
export class EventosVistaComponent {
  @Input() eventos: any[] = [];
  @Input() isLoading: boolean = false;
  @Input() enableNavigation: boolean = true;
  @Input() showHero: boolean = true;
  @Input() previewMode: boolean = false;

  @Output() eventoClick = new EventEmitter<number>();

  onEventoClick(eventoId: number): void {
    if (!this.enableNavigation) {
      return;
    }
    this.eventoClick.emit(eventoId);
  }
}
