import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';

export type PreferenciaImportes = 'desconocida' | 'activa' | 'inactiva';

/**
 * Modo privado de importes (decisión 419): la preferencia de la cuenta y si la pantalla actual está destapada. Falla
 * cerrado: mientras no se sabe, tapado, para que un importe no se vea ni un instante.
 */
@Injectable({ providedIn: 'root' })
export class ModoPrivadoService {
  private readonly permisos = inject(PermisosSesionService);

  /** La generación de sesión de la que sale la preferencia; -1 mientras no se ha leído ninguna. */
  private generacionLeida = -1;
  private lectura?: Subscription;

  readonly preferencia = signal<PreferenciaImportes>('desconocida');
  readonly destapado = signal(false);
  readonly tapado = computed(() => this.preferencia() !== 'inactiva' && !this.destapado());
  /** Hay botón «Ver importes». */
  readonly disponible = computed(() => this.preferencia() === 'activa');

  constructor() {
    // Guardar «Mi perfil» se aplica sin recargar y sin volver a tapar lo que la persona destapó.
    this.permisos.refrescado$.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.permisos.generacion !== this.generacionLeida) {
        this.cargar();
      } else {
        this.leer();
      }
    });
  }

  /**
   * Lo llama cada importe y cada botón. Solo hace algo cuando la sesión es otra (login, cierre de sesión, cambio de
   * escuela): vuelve a «desconocida», tapa y lee. Tras un error no relee por sí solo, o cada importe nuevo volvería a
   * tapar lo destapado.
   */
  cargar(): void {
    const generacion = this.permisos.generacion;
    if (generacion === this.generacionLeida) {
      return;
    }
    this.generacionLeida = generacion;
    // Antes de suscribirse: lo del usuario anterior no vale para el siguiente.
    this.preferencia.set('desconocida');
    this.destapado.set(false);
    this.leer();
  }

  private leer(): void {
    this.lectura?.unsubscribe();
    this.lectura = this.permisos.obtener().subscribe({
      // Solo `importesTapados === false` abre; un campo ausente es un servidor sin el modo (opcional) y también abre.
      next: (permisos) => {
        const tapar = permisos === null || permisos === undefined ? true : permisos.importesTapados !== false && permisos.importesTapados !== undefined;
        this.preferencia.set(tapar ? 'activa' : 'inactiva');
      },
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
