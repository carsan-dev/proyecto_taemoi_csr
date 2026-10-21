import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription, forkJoin } from 'rxjs';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { DisenoPanelService, DisenoUsuario, MiembroDeGestion } from '../diseno-panel.service';
import { EstilosPanelService } from '../estilos-panel.service';
import { plural } from '../formato/formato-es';
import { etiquetaRol } from '../formato/roles';

/** Una cuenta de gestión con su nombre (de la API de miembros) y su interruptor. */
export interface FilaDiseno extends DisenoUsuario {
  nombre: string;
  rol: string;
}

type Estado = 'cargando' | 'listo' | 'error';

const ERROR_GUARDAR = 'No se pudo guardar el cambio. Revisa la conexión y vuelve a intentarlo.';
const ERROR_CARGAR = 'No se pudo cargar el equipo. Revisa la conexión y vuelve a intentarlo.';

/**
 * Pantalla del propietario (8b-I, decisión 256): quién del equipo de gestión usa el diseño nuevo del panel. Se abre desde
 * «Diseño nuevo» del menú de cuenta y va en su propio chunk (`panel/diseno-nuevo`). Espera a la hoja del panel nuevo
 * (tokens bajo `.pn`). Un clic, una petición: el botón de la fila queda bloqueado hasta la respuesta. «Para todo el
 * equipo» está construido pero oculto hasta R5 (decisión 258).
 */
@Component({
  selector: 'app-diseno-nuevo',
  standalone: true,
  templateUrl: './diseno-nuevo.component.html',
  styleUrls: ['./diseno-nuevo.component.scss'],
})
export class DisenoNuevoComponent implements OnInit, OnDestroy {
  private readonly api = inject(DisenoPanelService);
  private readonly estilos = inject(EstilosPanelService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly suscripciones = new Subscription();

  /** Decisión 258: el control existe, pero no se enseña hasta R5. */
  readonly mostrarParaTodo = false;

  hojaLista = false;
  estado: Estado = 'cargando';
  filas: FilaDiseno[] = [];
  porDefecto = false;
  /** Cuenta cuyo cambio está en vuelo (o `'por-defecto'`); bloquea su botón. */
  guardando: number | 'por-defecto' | null = null;
  error: string | null = null;

  ngOnInit(): void {
    // Sin la hoja la pantalla funciona igual (sin tokens): se enseña al terminar la carga, salga bien o mal.
    this.estilos
      .cargar()
      .catch(() => undefined)
      .then(() => {
        this.hojaLista = true;
        this.cargar();
      });
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
  }

  get conPermiso(): number {
    return this.filas.filter((f) => f.permitido).length;
  }

  get resumen(): string {
    return `${this.conPermiso} de ${plural(this.filas.length, 'persona', 'personas')} con permiso`;
  }

  cargar(): void {
    this.estado = 'cargando';
    this.error = null;
    this.suscripciones.add(
      forkJoin([this.api.listar(), this.api.gestionDeLaEscuela()]).subscribe({
        next: ([estado, miembros]) => {
          const porId = new Map<number, MiembroDeGestion>(miembros.map((m) => [m.usuarioId, m]));
          this.porDefecto = estado.porDefecto;
          this.filas = estado.usuarios.map((u) => this.fila(u, porId.get(u.usuarioId)));
          this.estado = 'listo';
        },
        error: (error: unknown) => {
          this.estado = 'error';
          this.error = mensajeDe(error, ERROR_CARGAR);
        },
      })
    );
  }

  cambiarPermiso(fila: FilaDiseno): void {
    if (this.guardando !== null) {
      return;
    }
    this.guardando = fila.usuarioId;
    this.error = null;
    this.suscripciones.add(
      this.api.cambiarPermiso(fila.usuarioId, !fila.permitido).subscribe({
        next: (respuesta) => {
          this.filas = this.filas.map((f) =>
            f.usuarioId === respuesta.usuarioId ? { ...f, permitido: respuesta.permitido, activo: respuesta.activo } : f
          );
          this.guardando = null;
          // Si era la propia cuenta, su interruptor cambió: los permisos se vuelven a pedir en la próxima navegación.
          this.permisos.reiniciar();
        },
        error: (error: unknown) => {
          this.guardando = null;
          this.error = mensajeDe(error, ERROR_GUARDAR);
        },
      })
    );
  }

  /** «Para todo el equipo» (oculto hasta R5). */
  cambiarPorDefecto(): void {
    if (this.guardando !== null) {
      return;
    }
    this.guardando = 'por-defecto';
    this.error = null;
    this.suscripciones.add(
      this.api.cambiarPorDefecto(!this.porDefecto).subscribe({
        next: () => {
          this.guardando = null;
          this.permisos.reiniciar();
          this.cargar();
        },
        error: (error: unknown) => {
          this.guardando = null;
          this.error = mensajeDe(error, ERROR_GUARDAR);
        },
      })
    );
  }

  textoEstado(fila: FilaDiseno): string {
    if (fila.activo) {
      return 'Usa el diseño nuevo';
    }
    return fila.permitido ? 'Con permiso, usa el de siempre' : 'Sin permiso';
  }

  private fila(usuario: DisenoUsuario, miembro: MiembroDeGestion | undefined): FilaDiseno {
    const nombre = [miembro?.nombre, miembro?.apellidos].filter((p) => !!p && p.trim()).join(' ').trim();
    const activos = (miembro?.roles ?? []).filter((r) => r.activa).map((r) => r.rol);
    const rol = activos.includes('PROPIETARIO') ? 'PROPIETARIO' : activos.includes('ADMINISTRADOR') ? 'ADMINISTRADOR' : '';
    return { ...usuario, nombre: nombre || `Cuenta n.º ${usuario.usuarioId}`, rol: etiquetaRol(rol) };
  }
}

/** El `mensaje` de la API (`{"mensaje"}`) o, sin él, uno que dice qué pasó y qué hacer. */
function mensajeDe(error: unknown, porDefecto: string): string {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    const mensaje = (error.error as { mensaje?: unknown }).mensaje;
    if (typeof mensaje === 'string' && mensaje.trim()) {
      return mensaje;
    }
  }
  return porDefecto;
}
