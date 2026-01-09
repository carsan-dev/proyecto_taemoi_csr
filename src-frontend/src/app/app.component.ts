import { Component, HostListener } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SkeletonComponent } from './componentes/vistas/layout/skeleton/skeleton.component';
import { ScrollService } from './servicios/generales/scroll.service';
import { NgxSpinnerModule } from 'ngx-spinner';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SkeletonComponent, NgxSpinnerModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  title = 'Taemoi_Angular_Front';
  private readonly selectTypeaheadResetMs = 1000;
  private selectTypeaheadBuffer = '';
  private selectTypeaheadLastKeyTime = 0;
  private selectTypeaheadResetTimer: number | null = null;
  private activeSelect: HTMLSelectElement | null = null;

  constructor(private scrollService: ScrollService) {
    // ScrollService is initialized automatically and handles route changes
  }

  @HostListener('document:keydown', ['$event'])
  handleSelectTypeahead(event: KeyboardEvent): void {
    if (
      event.defaultPrevented ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      event.isComposing
    ) {
      return;
    }

    const target = event.target;
    if (!(target instanceof HTMLSelectElement)) {
      return;
    }
    if (target.disabled) {
      return;
    }

    const key = event.key;
    if (key === ' ' || key.length !== 1) {
      return;
    }

    const now = Date.now();
    if (
      target !== this.activeSelect ||
      now - this.selectTypeaheadLastKeyTime > this.selectTypeaheadResetMs
    ) {
      this.selectTypeaheadBuffer = '';
      this.activeSelect = target;
    }
    this.selectTypeaheadLastKeyTime = now;
    this.selectTypeaheadBuffer += key;
    this.scheduleSelectTypeaheadReset();

    const matchIndex = this.findMatchIndex(target, this.selectTypeaheadBuffer);
    if (matchIndex !== -1) {
      target.selectedIndex = matchIndex;
      target.dispatchEvent(new Event('change', { bubbles: true }));
      event.preventDefault();
      return;
    }

    if (this.selectTypeaheadBuffer.length > 1) {
      const fallbackIndex = this.findMatchIndex(target, key);
      if (fallbackIndex !== -1) {
        this.selectTypeaheadBuffer = key;
        target.selectedIndex = fallbackIndex;
        target.dispatchEvent(new Event('change', { bubbles: true }));
        event.preventDefault();
      } else {
        this.selectTypeaheadBuffer = '';
      }
    }
  }

  private findMatchIndex(select: HTMLSelectElement, query: string): number {
    const normalizedQuery = this.normalizeTypeahead(query);
    const options = Array.from(select.options);

    for (let i = 0; i < options.length; i += 1) {
      const option = options[i];
      if (option.disabled) {
        continue;
      }
      if (this.normalizeTypeahead(option.text).startsWith(normalizedQuery)) {
        return i;
      }
    }

    return -1;
  }

  private normalizeTypeahead(value: string): string {
    return value
      .trim()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  private scheduleSelectTypeaheadReset(): void {
    this.clearSelectTypeaheadReset();
    this.selectTypeaheadResetTimer = window.setTimeout(() => {
      this.selectTypeaheadBuffer = '';
      this.selectTypeaheadResetTimer = null;
    }, this.selectTypeaheadResetMs);
  }

  private clearSelectTypeaheadReset(): void {
    if (this.selectTypeaheadResetTimer !== null) {
      window.clearTimeout(this.selectTypeaheadResetTimer);
      this.selectTypeaheadResetTimer = null;
    }
  }
}
