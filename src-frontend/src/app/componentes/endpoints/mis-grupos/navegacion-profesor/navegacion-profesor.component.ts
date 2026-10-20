import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

export type SeccionProfesor = 'grupos' | 'caja';

/**
 * Pestañas superiores del profesor, «Mis grupos | Mi caja» (D7-7). Son navegación entre pantallas, así que van como
 * enlaces con `aria-current`, no como `role="tab"` (las pestañas de dentro de Mi caja sí lo son).
 */
@Component({
  selector: 'app-navegacion-profesor',
  standalone: true,
  imports: [RouterLink],
  template: `
    <nav class="nav-profesor" aria-label="Profesor">
      <a [routerLink]="['/mis-grupos']" [class.actual]="actual === 'grupos'"
        [attr.aria-current]="actual === 'grupos' ? 'page' : null">
        <i class="bi bi-people" aria-hidden="true"></i>
        <span>Mis grupos</span>
      </a>
      <a [routerLink]="['/mi-caja']" [class.actual]="actual === 'caja'"
        [attr.aria-current]="actual === 'caja' ? 'page' : null">
        <i class="bi bi-wallet2" aria-hidden="true"></i>
        <span>Mi caja</span>
      </a>
    </nav>
  `,
  styles: `
    :host {
      display: block;
    }

    .nav-profesor {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      border-bottom: 1px solid #e2e8f0;
    }

    a {
      position: relative;
      min-height: 3rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.45rem;
      color: #4a5568;
      font-weight: 700;
      font-size: 0.98rem;
      text-decoration: none;
      border-radius: 8px 8px 0 0;
      transition: color 150ms ease-out;
    }

    a::after {
      content: '';
      position: absolute;
      left: 22%;
      right: 22%;
      bottom: -1px;
      height: 3px;
      border-radius: 3px 3px 0 0;
      background: transparent;
      transition: background-color 150ms ease-out;
    }

    a:hover {
      color: #1a202c;
    }

    a.actual {
      color: #1f4d86;
    }

    a.actual::after {
      background: #2b6cb0;
    }

    a:focus-visible {
      outline: 2px solid #2b6cb0;
      outline-offset: -2px;
    }

    @media (prefers-reduced-motion: reduce) {
      a,
      a::after {
        transition: none;
      }
    }
  `,
})
export class NavegacionProfesorComponent {
  @Input({ required: true }) actual!: SeccionProfesor;
}
