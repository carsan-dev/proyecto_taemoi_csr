import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { ImagenInterface } from '../../../../interfaces/imagen-interface';

@Component({
  selector: 'app-slider-tocable',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './slider-tocable.component.html',
  styleUrl: './slider-tocable.component.scss'
})
export class SliderTocableComponent {
  @Input() imagenesSlider: ImagenInterface[] = [];

  indexSeleccionado: number = 0;

  mostrarAnterior(i: number) {
    if (this.indexSeleccionado > 0) {
      this.indexSeleccionado = i - 1;
    }
  }

  mostrarSiguiente(i: number) {
    if (this.indexSeleccionado < this.imagenesSlider?.length - 1) {
      this.indexSeleccionado = i + 1;
    }
  }
}
