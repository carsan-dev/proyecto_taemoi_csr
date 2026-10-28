import { DOCUMENT } from '@angular/common';
import {
  ApplicationRef,
  ComponentRef,
  EnvironmentInjector,
  Injectable,
  NgZone,
  createComponent,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, PRIMARY_OUTLET, Router } from '@angular/router';
import { filter } from 'rxjs';

import { sesionCerrada } from '../../servicios/authentication/sesion-cerrada';

import { PilaCapas, cerrarSweetAlertAbierto } from '../capas/capa-modal';
import { PoseMascota } from '../mascota/poses';
import { AvisosComponent } from './avisos.component';

export type TipoAviso = 'exito' | 'error' | 'info';

/** Acción opcional del aviso: «Deshacer», «Reintentar», «Descargar». */
export interface AccionAviso {
  texto: string;
  alPulsar: () => void;
  /** `false`: pulsarla no cierra el aviso (sigue su temporizador y la otra acción). Por defecto cierra. */
  cierra?: boolean;
}

export interface Aviso {
  id: number;
  tipo: TipoAviso;
  texto: string;
  accion?: AccionAviso;
  /** Segunda acción, que se pinta antes de `accion` («Recibo» antes de «Deshacer»). */
  accionPrevia?: AccionAviso;
  /** 8b-M: la mascota celebrando en lugar del icono, solo en el cierre de una tarea larga. */
  mascota?: PoseMascota;
}

/** Opciones de un aviso de éxito. */
export interface OpcionesAviso {
  /** Cierre de una tarea larga (mensualidades generadas): la gota de 32 px con «celebrar» en lugar del icono. */
  mascota?: PoseMascota;
  /** Una segunda acción, antes de la principal: «Recibo» antes de «Deshacer». */
  accionPrevia?: AccionAviso;
}

/** Éxito e información se cierran solos a los 5 s; el error, no. */
export const DURACION_AVISO = 5000;
/** Como mucho 3 a la vez: el cuarto saca al más antiguo. */
export const MAX_AVISOS = 3;
/**
 * Lo que tarda el texto de un aviso en llegar a su región viva. Las regiones (`role=status` y `role=alert`) nacen vacías
 * con la de avisos y el texto se añade después: un lector de pantalla no anuncia una región viva que se inserta ya llena.
 */
export const RETRASO_ANUNCIO = 100;

interface Temporizador {
  id: ReturnType<typeof setTimeout> | null;
  restante: number;
  desde: number;
}

/**
 * Avisos del panel nuevo (8b-D1, decisión 271; boceto `Avisos.dc.html` y `AvisosMovil.dc.html`). Sustituyen a los
 * avisos de SweetAlert2 en las pantallas nuevas. La región se crea al primer aviso y se pinta en el `body` o, si hay una
 * capa modal abierta, dentro de la de arriba (fuera de ella quedaría inerte). Las tarjetas se ven al momento; su texto
 * llega a las regiones vivas `RETRASO_ANUNCIO` después. Vive en los chunks del panel nuevo.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);
  private readonly documento = inject(DOCUMENT);
  private readonly pila = inject(PilaCapas);
  private readonly zona = inject(NgZone);
  private readonly router = inject(Router);

  readonly lista = signal<Aviso[]>([]);
  /** Los avisos ya enviados a las regiones vivas (cada uno, `RETRASO_ANUNCIO` después de pintarse). */
  readonly anuncios = signal<Aviso[]>([]);
  private siguiente = 0;
  private region: ComponentRef<AvisosComponent> | null = null;
  private readonly temporizadores = new Map<number, Temporizador>();
  private readonly retrasosAnuncio = new Map<number, ReturnType<typeof setTimeout>>();

  constructor() {
    // La región sigue a la capa modal de arriba (o vuelve al body).
    effect(() => {
      const capa = this.pila.arriba();
      const host = this.region?.location.nativeElement as HTMLElement | undefined;
      if (host) {
        (capa ?? this.documento.body).appendChild(host);
      }
    });
    // Al cerrar o caducar la sesión (logout, limpiarEstadoLocal, forzarCierreSesion) no queda ningún aviso ni temporizador.
    sesionCerrada.pipe(takeUntilDestroyed()).subscribe(() => this.cerrarTodos());
    // Los errores se quedan mientras se sigue en la pantalla y se van al cambiar a otra (otra ruta; no al cambiar solo la
    // query string o el fragmento, que son pestañas internas). Éxito e información siguen su temporizador.
    let pantalla = this.rutaSinParametros(this.router.url);
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe((e) => {
        const nueva = this.rutaSinParametros(e.urlAfterRedirects);
        if (nueva !== pantalla) {
          this.lista()
            .filter((a) => a.tipo === 'error')
            .forEach((a) => this.cerrar(a.id));
        }
        pantalla = nueva;
      });
  }

  exito(texto: string, accion?: AccionAviso, opciones?: OpcionesAviso): number {
    return this.mostrar('exito', texto, accion, opciones?.mascota, opciones?.accionPrevia);
  }

  /** Sustituye al SweetAlert2 del interceptor de errores, como el aviso de hoy. */
  error(texto: string, accion?: AccionAviso): number {
    cerrarSweetAlertAbierto(this.router);
    return this.mostrar('error', texto, accion);
  }

  info(texto: string, accion?: AccionAviso): number {
    return this.mostrar('info', texto, accion);
  }

  cerrar(id: number): void {
    const t = this.temporizadores.get(id);
    if (t?.id) {
      clearTimeout(t.id);
    }
    this.temporizadores.delete(id);
    const retraso = this.retrasosAnuncio.get(id);
    if (retraso) {
      clearTimeout(retraso);
    }
    this.retrasosAnuncio.delete(id);
    this.lista.update((lista) => lista.filter((a) => a.id !== id));
    this.anuncios.update((lista) => lista.filter((a) => a.id !== id));
  }

  cerrarTodos(): void {
    this.lista().forEach((a) => this.cerrar(a.id));
  }

  /** Con el ratón encima o el foco dentro, el tiempo se para. */
  pausar(id: number): void {
    const t = this.temporizadores.get(id);
    if (!t?.id) {
      return;
    }
    clearTimeout(t.id);
    t.id = null;
    t.restante = Math.max(t.restante - (Date.now() - t.desde), 0);
  }

  seguir(id: number): void {
    const t = this.temporizadores.get(id);
    if (!t || t.id) {
      return;
    }
    this.programar(id, t);
  }

  pulsarAccion(aviso: Aviso, accion: AccionAviso | undefined = aviso.accion): void {
    if (accion?.cierra !== false) {
      this.cerrar(aviso.id);
    }
    accion?.alPulsar();
  }

  private mostrar(
    tipo: TipoAviso,
    texto: string,
    accion?: AccionAviso,
    mascota?: PoseMascota,
    accionPrevia?: AccionAviso
  ): number {
    this.asegurarRegion();
    const aviso: Aviso = {
      id: ++this.siguiente,
      tipo,
      texto,
      accion,
      ...(accionPrevia ? { accionPrevia } : {}),
      ...(mascota ? { mascota } : {}),
    };
    const actuales = this.lista();
    actuales.slice(0, Math.max(actuales.length - (MAX_AVISOS - 1), 0)).forEach((a) => this.cerrar(a.id));
    this.lista.update((lista) => [...lista, aviso]);
    this.retrasosAnuncio.set(
      aviso.id,
      setTimeout(
        () =>
          this.zona.run(() => {
            this.retrasosAnuncio.delete(aviso.id);
            this.anuncios.update((lista) => [...lista, aviso]);
          }),
        RETRASO_ANUNCIO
      )
    );
    if (tipo !== 'error') {
      const t: Temporizador = { id: null, restante: DURACION_AVISO, desde: Date.now() };
      this.temporizadores.set(aviso.id, t);
      this.programar(aviso.id, t);
    }
    return aviso.id;
  }

  private programar(id: number, t: Temporizador): void {
    t.desde = Date.now();
    t.id = setTimeout(() => this.zona.run(() => this.cerrar(id)), t.restante);
  }

  /** La ruta de la pantalla, sin query string ni fragmento. */
  private rutaSinParametros(url: string): string {
    const grupo = this.router.parseUrl(url).root.children[PRIMARY_OUTLET];
    return grupo ? grupo.toString() : '';
  }

  private asegurarRegion(): void {
    if (this.region) {
      return;
    }
    const ref = createComponent(AvisosComponent, { environmentInjector: this.injector });
    this.appRef.attachView(ref.hostView);
    (this.pila.arriba() ?? this.documento.body).appendChild(ref.location.nativeElement);
    // Las regiones vivas, en la página y vacías antes del primer aviso.
    ref.changeDetectorRef.detectChanges();
    this.region = ref;
  }
}
