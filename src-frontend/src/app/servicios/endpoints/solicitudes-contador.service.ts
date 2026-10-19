import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { EMPTY, Subject, catchError, switchMap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/**
 * Contador de la cabecera de gestión (D6c-4): lo que espera acción de la sesión. Se pide al entrar y al navegar, nunca
 * por temporizador. Va aparte de {@link SolicitudesService} para que la cabecera no arrastre la bandeja al paquete
 * inicial. Un error deja el último valor y no abre el diálogo global.
 */
@Injectable({ providedIn: 'root' })
export class SolicitudesContadorService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/solicitudes/contador`;
  private readonly peticiones = new Subject<void>();
  private readonly totalActual = signal(0);

  readonly total = this.totalActual.asReadonly();

  constructor() {
    this.peticiones
      .pipe(
        switchMap(() =>
          this.http
            .get<{ total?: unknown }>(this.url, {
              withCredentials: true,
              context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
            })
            .pipe(catchError(() => EMPTY))
        )
      )
      .subscribe((respuesta) => {
        const total = Number(respuesta?.total);
        this.totalActual.set(Number.isFinite(total) && total > 0 ? Math.floor(total) : 0);
      });
  }

  refrescar(): void {
    this.peticiones.next();
  }

  reiniciar(): void {
    this.totalActual.set(0);
  }
}
