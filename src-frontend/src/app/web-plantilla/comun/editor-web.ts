import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, InjectionToken, inject, signal } from '@angular/core';

import type { OpcionesCapaEditor } from '../editor/capa-editor';
import type { SimuladorCaso } from '../editor/casos-vista';
import type { BloqueBorrador } from '../editor/puente-editor';
import { EventoDetalleWeb, EventosWeb, SitioWeb } from '../datos/modelos';

/** Parámetro de la URL que pide la capa de edición: `/?vista-previa=<tema>&editor=1` («Web · Contenido», W12c). */
export const PARAMETRO_EDITOR = 'editor';

export type CargarCapaEditor = () => Promise<{ activarCapaEditor: (opciones: OpcionesCapaEditor) => () => void }>;

/**
 * Carga la capa de edición: un chunk perezoso propio, fuera de los del tema y del bundle inicial. Las pruebas lo
 * sustituyen.
 */
export const CARGAR_CAPA_EDITOR = new InjectionToken<CargarCapaEditor>('CARGAR_CAPA_EDITOR', {
  providedIn: 'root',
  factory: () => () => import('../editor/capa-editor'),
});

/**
 * El modo editor de la plantilla (W12c). Se activa una sola vez por página y solo si se cumplen las tres cosas:
 * la URL lo pide (`editor=1`), la API confirmó la vista previa de la gestión (`SitioWeb.vistaPrevia`, la misma
 * comprobación que la vista previa de «Web · Tema»; no basta el parámetro) y la página está dentro de un iframe.
 * Guarda el borrador que manda el panel por el puente: con él, el tema pinta sus datos públicos y esos bloques.
 * W12c-bis: «Ver el caso» (d528) cambia en memoria los datos de la vista previa con el simulador que da la capa (su
 * código vive en el chunk de la capa); nunca toca la API.
 */
@Injectable({ providedIn: 'root' })
export class EditorWebService {
  private readonly cargar = inject(CARGAR_CAPA_EDITOR);
  private readonly documento = inject(DOCUMENT);
  private readonly bloques = signal<BloqueBorrador[] | null>(null);
  private readonly simulador = signal<SimuladorCaso | null>(null);
  private activado = false;
  private destruido = false;
  /** Quita la capa (oyentes, observadores y puente); null si no está puesta. */
  private desactivar: (() => void) | null = null;

  /** Los bloques del borrador del panel; null mientras no llega (o fuera del editor). */
  readonly borrador = this.bloques.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destruido = true;
      this.quitarCapa();
    });
  }

  activarSiToca(editor: string | null, sitio: SitioWeb | null): void {
    if (this.activado || this.destruido || editor !== '1' || sitio?.vistaPrevia !== true) {
      return;
    }
    const ventana = this.documento.defaultView;
    if (!ventana || ventana.parent === ventana) {
      return;
    }
    this.activado = true;
    this.cargar().then(
      (capa) => {
        if (this.destruido) {
          return;
        }
        this.quitarCapa();
        this.desactivar = capa.activarCapaEditor({
          ventana,
          alBorrador: (bloques) => this.bloques.set(bloques),
          alCaso: (simulador) => this.simulador.set(simulador),
        });
      },
      () => {
        this.activado = false;
      },
    );
  }

  private quitarCapa(): void {
    const desactivar = this.desactivar;
    this.desactivar = null;
    desactivar?.();
  }

  /**
   * El sitio con los bloques del borrador, si los hay, y con el caso que se esté viendo (solo en la vista previa de la
   * gestión).
   */
  aplicar(sitio: SitioWeb): SitioWeb {
    if (!sitio.vistaPrevia) {
      return sitio;
    }
    const bloques = this.bloques();
    const simulador = this.simulador();
    const conBloques = bloques ? { ...sitio, bloques } : sitio;
    return simulador ? simulador.sitio(conBloques) : conBloques;
  }

  /** La lista de eventos con el caso que se esté viendo (solo con la capa de edición activa). */
  aplicarEventos(eventos: EventosWeb): EventosWeb {
    return this.simulador()?.eventos(eventos) ?? eventos;
  }

  /** Un evento con el caso que se esté viendo (solo con la capa de edición activa). */
  aplicarEvento(evento: EventoDetalleWeb): EventoDetalleWeb {
    return this.simulador()?.evento(evento) ?? evento;
  }
}
