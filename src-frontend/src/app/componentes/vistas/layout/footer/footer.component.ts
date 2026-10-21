import { Component, EventEmitter, Output } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss'
})
export class FooterComponent {
  /** «Configurar cookies»: el skeleton vuelve a abrir el aviso de cookies (decisión 250). */
  @Output() readonly configurarCookies = new EventEmitter<void>();
}
