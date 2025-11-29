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
  @Input() type: 'list' | 'grid' | 'table' = 'list';
  @Input() count: number = 5;
  @Input() columns: number = 3; // For grid layout

  get items(): number[] {
    return Array(this.count).fill(0);
  }
}
