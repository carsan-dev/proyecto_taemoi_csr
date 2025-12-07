import { Component, HostListener} from '@angular/core';

@Component({
  selector: 'app-botonscroll',
  standalone: true,
  imports: [],
  templateUrl: './botonscroll.component.html',
  styleUrl: './botonscroll.component.scss'
})
export class BotonscrollComponent {

  mostrarBoton = false;

  constructor() { }

  scrollArriba() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    if (typeof globalThis !== 'undefined') {
      this.mostrarBoton = window.scrollY > 100;
    }
  }

}
