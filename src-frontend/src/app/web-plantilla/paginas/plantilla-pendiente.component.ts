import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Pantalla técnica provisional del armazón (fase4-web-plan.md §6, «Armazón»): todas las rutas de la plantilla apuntan
 * aquí hasta que llegue el primer tema (W5). Sin estilo ni texto a propósito: el diseño es de los temas.
 */
@Component({
  selector: 'app-plantilla-pendiente',
  standalone: true,
  template: '<main id="contenido"></main>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlantillaPendienteComponent {}
