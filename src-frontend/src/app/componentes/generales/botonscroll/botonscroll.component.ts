import { Component, HostListener} from '@angular/core';

@Component({
  selector: 'app-botonscroll',
  standalone: true,
  imports: [],
  templateUrl: './botonscroll.component.html',
  styleUrl: './botonscroll.component.scss'
})
export class BotonscrollComponent {

  constructor() { }

  scrollArriba() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    if (typeof window !== 'undefined') {
    this.estaScrolleado();
    }
  }

  estaScrolleado() {
    if (typeof window !== 'undefined') {
    return window.scrollY > 100;
    }
    return false;
  }

}
