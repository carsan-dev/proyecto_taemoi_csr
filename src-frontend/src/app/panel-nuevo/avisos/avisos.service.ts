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

import { PilaCapas, cerrarSweetAlertAbierto } from '../capas/capa-modal';
import { AvisosComponent } from './avisos.component';

export type TipoAviso = 'exito' | 'error' | 'info';

/** Acción opcional del aviso: «Deshacer», «Reintentar», «Descargar». */
export interface AccionAviso {
  texto: string;
  alPulsar: () => void;
}

export interface Aviso {
  id: number;
  tipo: TipoAviso;
  texto: string;
  accion?: AccionAviso;
}

/** Éxito e información se cierran solos a los 5 s; el error, no. */
export const DURACION_AVISO = 5000;
/** Como mucho 3 a la vez: el cuarto saca al más antiguo. */
export const MAX_AVISOS = 3;

interface Temporizador {
  id: ReturnType<typeof setTimeout> | null;
  restante: number;
  desde: number;
}

/**
 * Avisos del panel nuevo (8b-D1, decisión 271; boceto `Avisos.dc.html` y `AvisosMovil.dc.html`). Sustituyen a los
 * avisos de SweetAlert2 en las pantallas nuevas. La región se crea al primer aviso y se pinta en el `body` o, si hay una
 * capa modal abierta, dentro de la de arriba (fuera de ella quedaría inerte). Vive en los chunks del panel nuevo.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);
  private readonly documento = inject(DOCUMENT);
  private readonly pila = inject(PilaCapas);
  private readonly zona = inject(NgZone);

  readonly lista = signal<Aviso[]>([]);
  private siguiente = 0;
  private region: ComponentRef<AvisosComponent> | null = null;
  private readonly temporizadores = new Map<number, Temporizador>();

  constructor() {
    // La región sigue a la capa modal de arriba (o vuelve al body).
    effect(() => {
      const capa = this.pila.arriba();
      const host = this.region?.location.nativeElement as HTMLElement | undefined;
      if (host) {
        (capa ?? this.documento.body).appendChild(host);
      }
    });
  }

  exito(texto: string, accion?: AccionAviso): number {
    return this.mostrar('exito', texto, accion);
  }

  /** Sustituye al SweetAlert2 del interceptor de errores, como el aviso de hoy. */
  error(texto: string, accion?: AccionAviso): number {
    cerrarSweetAlertAbierto();
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
    this.lista.update((lista) => lista.filter((a) => a.id !== id));
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

  pulsarAccion(aviso: Aviso): void {
    this.cerrar(aviso.id);
    aviso.accion?.alPulsar();
  }

  private mostrar(tipo: TipoAviso, texto: string, accion?: AccionAviso): number {
    this.asegurarRegion();
    const aviso: Aviso = { id: ++this.siguiente, tipo, texto, accion };
    const actuales = this.lista();
    actuales.slice(0, Math.max(actuales.length - (MAX_AVISOS - 1), 0)).forEach((a) => this.cerrar(a.id));
    this.lista.update((lista) => [...lista, aviso]);
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

  private asegurarRegion(): void {
    if (this.region) {
      return;
    }
    const ref = createComponent(AvisosComponent, { environmentInjector: this.injector });
    this.appRef.attachView(ref.hostView);
    (this.pila.arriba() ?? this.documento.body).appendChild(ref.location.nativeElement);
    this.region = ref;
  }
}
