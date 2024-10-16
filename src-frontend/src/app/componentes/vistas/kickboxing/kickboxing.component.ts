import {
  Component,
  AfterViewInit,
  ElementRef,
  ViewChildren,
  QueryList,
  Inject,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-kickboxing',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './kickboxing.component.html',
  styleUrls: ['./kickboxing.component.scss'],
})
export class KickboxingComponent implements AfterViewInit {
  usuarioLogueado: boolean = false;

  @ViewChildren('animatedImage')
  images!: QueryList<ElementRef>;

  constructor(
    @Inject(PLATFORM_ID) private readonly platformId: Object
  ) {}

  ngAfterViewInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1,
      };

      const observer = new IntersectionObserver(
        this.handleIntersection.bind(this),
        options
      );

      this.images.forEach((image) => {
        observer.observe(image.nativeElement);
      });
    }
  }

  handleIntersection(
    entries: IntersectionObserverEntry[],
    observer: IntersectionObserver
  ): void {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        (entry.target as HTMLElement).classList.add('fade-in');
        observer.unobserve(entry.target);
      }
    });
  }
}
