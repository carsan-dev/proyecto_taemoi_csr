import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SkeletonComponent } from './componentes/vistas/layout/skeleton/skeleton.component';
import { ScrollService } from './servicios/generales/scroll.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SkeletonComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  title = 'Taemoi_Angular_Front';

  constructor(private scrollService: ScrollService) {
    // ScrollService is initialized automatically and handles route changes
  }
}