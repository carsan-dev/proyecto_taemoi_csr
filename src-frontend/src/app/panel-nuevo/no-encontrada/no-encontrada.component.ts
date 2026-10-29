import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SeoService } from '../../servicios/generales/seo.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';

/** Inicio de la sesión de gestión del panel (primera sección de `MENU_PANEL`). */
export const RUTA_INICIO_GESTION = '/adminpage';

/**
 * 404 dentro del panel nuevo (8b-M2b): una URL desconocida con una sesión de gestión se muestra con la cabecera del
 * panel y la mascota, no con la página pública. Mismo `<title>` que el 404 público.
 */
@Component({
  selector: 'pn-no-encontrada',
  standalone: true,
  imports: [AcentoEscuelaDirective, BloqueVacioComponent],
  template: `
    <div class="pn no-encontrada" pnAcento>
      <div class="no-encontrada__pagina">
        <pn-bloque-vacio
          pose="buscar"
          titulo="No encontramos esta página"
          texto="Puede que el enlace esté mal o que la página ya no exista."
          accion="Volver a Inicio"
          (accionar)="volver()"
        />
      </div>
    </div>
  `,
  styleUrl: './no-encontrada.component.scss',
})
export class NoEncontradaPanelComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  ngOnInit(): void {
    this.seo.updateDynamicSeo({
      title: "Página no encontrada (404) | Moi's Kim Do",
      description: 'La página que buscas no existe.',
      noIndex: true,
    });
  }

  volver(): void {
    void this.router.navigateByUrl(RUTA_INICIO_GESTION);
  }
}
