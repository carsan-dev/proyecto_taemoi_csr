import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './skeleton-card.component.html',
  styleUrls: ['./skeleton-card.component.scss'],
})
export class SkeletonCardComponent {
  @Input() type: 'list' | 'grid' | 'table' | 'distribution' | 'events' | 'masonry' = 'list';
  @Input() count: number = 5;
  @Input() columns: number | null = 3; // For grid layout

  get items(): number[] {
    return new Array(this.count).fill(0);
  }

  // Random widths for distribution bars to look more natural
  getBarWidth(index: number): number {
    const widths = [85, 60, 45, 30];
    return widths[index % widths.length];
  }
}
