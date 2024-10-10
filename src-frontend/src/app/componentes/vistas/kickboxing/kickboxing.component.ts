import { Component, OnInit, AfterViewInit, ElementRef, ViewChildren, QueryList, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-kickboxing',
  standalone: true,
  imports: [],
  templateUrl: './kickboxing.component.html',
  styleUrls: ['./kickboxing.component.scss']
})
export class KickboxingComponent implements OnInit, AfterViewInit {
  usuarioLogueado: boolean = false;

  @ViewChildren('animatedImage')
  images!: QueryList<ElementRef>;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    @Inject(PLATFORM_ID) private readonly platformId: Object
  ) { }

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }

  ngAfterViewInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
      };

      const observer = new IntersectionObserver(this.handleIntersection.bind(this), options);

      this.images.forEach(image => {
        observer.observe(image.nativeElement);
      });
    }
  }

  handleIntersection(entries: IntersectionObserverEntry[], observer: IntersectionObserver): void {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        (entry.target as HTMLElement).classList.add('fade-in');
        observer.unobserve(entry.target);
      }
    });
  }

  irARuta(ruta: string): void {
    this.router.navigate([ruta]);
  }
}
