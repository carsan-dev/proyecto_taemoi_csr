import { DOCUMENT } from '@angular/common';
import { ApplicationRef, EnvironmentInjector, Injectable, createComponent, inject } from '@angular/core';
import { Router } from '@angular/router';

import { cerrarSweetAlertAbierto, devolverFoco, disparadorActual } from '../capas/capa-modal';
import { DialogoComponent, OpcionesConfirmacion, ResultadoConfirmacion } from './dialogo.component';

/**
 * Confirmaciones del panel nuevo (8b-D1, decisión 269): sustituye a SweetAlert2 en las pantallas nuevas. Pinta un
 * `DialogoComponent` en el `body` (con su raíz `.pn`), lo abre como modal y resuelve con `{confirmado, valor}` al
 * cerrarse: Esc, el clic fuera, la X y «Cancelar» dan `confirmado: false` y no hacen nada. Al cerrar, el foco vuelve a
 * quien lo abrió. Vive en los chunks del panel nuevo.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);
  private readonly documento = inject(DOCUMENT);
  private readonly router = inject(Router);

  confirmar<R = unknown>(opciones: OpcionesConfirmacion<R>): Promise<ResultadoConfirmacion<R>> {
    // Si lo abre la respuesta a un error (el 409 de las herramientas), sustituye al SweetAlert2 del interceptor.
    cerrarSweetAlertAbierto(this.router);
    return new Promise((resolver) => {
      const disparador = disparadorActual(this.documento);
      const ref = createComponent(DialogoComponent, { environmentInjector: this.injector });
      ref.setInput('opciones', opciones);
      this.appRef.attachView(ref.hostView);
      this.documento.body.appendChild(ref.location.nativeElement);
      ref.changeDetectorRef.detectChanges();
      const suscripcion = ref.instance.cerrado.subscribe((resultado) => {
        suscripcion.unsubscribe();
        this.appRef.detachView(ref.hostView);
        ref.destroy();
        devolverFoco(disparador);
        resolver(resultado as ResultadoConfirmacion<R>);
      });
      ref.instance.abrir();
    });
  }
}
