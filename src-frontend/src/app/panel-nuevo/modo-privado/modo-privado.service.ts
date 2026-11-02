import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';

export type PreferenciaImportes = 'desconocida' | 'activa' | 'inactiva';

/**
 * Modo privado de importes (decisión 419): la preferencia de la cuenta y si la pantalla actual está destapada. Falla
 * cerrado: mientras no se sabe, tapado, para que un importe no se vea ni un instante.
 */
@Injectable({ providedIn: 'root' })
export class ModoPrivadoService {
  private readonly permisos = inject(PermisosSesionService);

  /** La petición de la que sale la preferencia: si cambia, es otra sesión o un refresco. */
  private leida$: Observable<PermisosSesion> | null = null;
  private lectura?: Subscription;

  readonly preferencia = signal<PreferenciaImportes>('desconocida');
  readonly destapado = signal(false);
  readonly tapado = computed(() => this.preferencia() !== 'inactiva' && !this.destapado());
  /** Hay botón «Ver importes». */
  readonly disponible = computed(() => this.preferencia() === 'activa');

  constructor() {
    // Guardar «Mi perfil» se aplica sin recargar.
    this.permisos.refrescado$.pipe(takeUntilDestroyed()).subscribe(() => this.cargar());
  }

  /** Lee la preferencia; lo llama cada importe y cada botón, y solo hace algo si la petición de permisos es otra. */
  cargar(): void {
    const peticion$ = this.permisos.obtener();
    if (peticion$ === this.leida$) {
      return;
    }
    this.leida$ = peticion$;
    // Antes de suscribirse: lo del usuario anterior no vale para el siguiente.
    this.preferencia.set('desconocida');
    this.destapado.set(false);
    this.lectura?.unsubscribe();
    this.lectura = peticion$.subscribe({
      next: (permisos) => this.preferencia.set(permisos?.importesTapados === true ? 'activa' : 'inactiva'),
      // Sigue tapado, pero con el botón: no debe quedarse tapado sin salida.
      error: () => this.preferencia.set('activa'),
    });
  }

  alternar(): void {
    this.destapado.update((destapado) => !destapado);
  }

  tapar(): void {
    this.destapado.set(false);
  }

  /** Para un `aria-label` con un importe dentro: tapado, no lo dice. */
  textoImporte(importe: string): string {
    return this.tapado() ? 'importe oculto' : importe;
  }
}
