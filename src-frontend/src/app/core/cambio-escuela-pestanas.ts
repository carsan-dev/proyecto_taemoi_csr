import { DOCUMENT } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, InjectionToken, OnDestroy, inject } from '@angular/core';
import { NEVER } from 'rxjs';

/**
 * Entrada sin pantalla que resuelve el destino de la sesión con la regla del login con contraseña (decisión 384,
 * `entradaGoogleGuard`). Tras cambiar de escuela, la recarga completa va aquí: los roles de la escuela nueva deciden si
 * el destino es el panel, «Mis grupos», «Mi caja» o el área de familias.
 */
export const RUTA_ENTRADA_SESION = '/login/google';

/** Nombre del `BroadcastChannel` y clave de `localStorage` (alternativa con el evento `storage`) del aviso entre pestañas. */
export const CANAL_CAMBIO_ESCUELA = 'taemoi.cambioEscuela';

/** Mensaje que manda la pestaña que cambió de escuela al resto de pestañas del mismo origen. */
export const MENSAJE_ESCUELA_CAMBIADA = 'escuela-cambiada';

/** Texto de la capa que bloquea las demás pestañas. */
export const TEXTO_CAMBIO_OTRA_PESTANA = 'Has cambiado de escuela en otra pestaña';

/** Carga completa de una URL de la app (tras cambiar de escuela); en los tests se sustituye. */
export const IR_A_URL = new InjectionToken<(url: string) => void>('IR_A_URL', {
  providedIn: 'root',
  factory: () => {
    const documento = inject(DOCUMENT);
    return (url: string) => documento.defaultView?.location.assign(url);
  },
});

/**
 * Coordinación entre pestañas tras cambiar de escuela (fase 4, selector de escuela). La cookie de sesión es común a
 * todas las pestañas del navegador: tras un cambio, las demás siguen con la memoria de la escuela anterior y la cookie
 * de la nueva.
 *
 * - La pestaña que cambia llama a {@link bloquear} (ninguna petición más hasta recargar) y a {@link avisarOtras}.
 * - Las demás reciben el aviso por `BroadcastChannel` o, sin él, por el evento `storage`: se bloquean igual y muestran
 *   una capa modal («Has cambiado de escuela en otra pestaña») cuyo único botón, «Recargar», vacía su `sessionStorage`
 *   y recarga la app entera en {@link RUTA_ENTRADA_SESION}. La capa es un `<dialog>` con `showModal()`: el resto de la
 *   página queda inerte y Esc no la cierra.
 *
 * Va en el bundle inicial (la arranca `AppComponent`): debe escuchar también en las pestañas del área de familias y
 * de la web, que no cargan el panel. La capa se pinta sin Angular para no depender de ningún chunk perezoso.
 */
@Injectable({ providedIn: 'root' })
export class CambioEscuelaPestanasService implements OnDestroy {
  private readonly documento = inject(DOCUMENT);
  private readonly irA = inject(IR_A_URL);
  private canal: BroadcastChannel | null = null;
  private iniciado = false;
  private estaBloqueada = false;
  private capa: HTMLDialogElement | null = null;

  private readonly alRecibirAlmacen = (evento: StorageEvent): void => {
    if (evento.key === CANAL_CAMBIO_ESCUELA && evento.newValue) {
      this.recibir();
    }
  };

  /** true desde que la escuela cambió (aquí o en otra pestaña): ninguna petición sale hasta recargar. */
  get bloqueada(): boolean {
    return this.estaBloqueada;
  }

  /** Empieza a escuchar a las demás pestañas (una vez). */
  iniciar(): void {
    const ventana = this.documento.defaultView;
    if (this.iniciado || !ventana) {
      return;
    }
    this.iniciado = true;
    if (typeof ventana.BroadcastChannel === 'function') {
      try {
        this.canal = new ventana.BroadcastChannel(CANAL_CAMBIO_ESCUELA);
        this.canal.onmessage = (evento: MessageEvent) => {
          if (evento.data?.tipo === MENSAJE_ESCUELA_CAMBIADA) {
            this.recibir();
          }
        };
      } catch {
        this.canal = null;
      }
    }
    // También sin BroadcastChannel en esta pestaña: la que avisa puede no tenerlo y usar el almacén.
    ventana.addEventListener('storage', this.alRecibirAlmacen);
  }

  /** Esta pestaña ya no debe pedir nada más a la API (la escuela de la cookie ya no es la de su memoria). */
  bloquear(): void {
    this.estaBloqueada = true;
  }

  /** Avisa al resto de pestañas del mismo origen de que la escuela de la sesión ha cambiado. */
  avisarOtras(): void {
    const ventana = this.documento.defaultView;
    if (!ventana) {
      return;
    }
    if (typeof ventana.BroadcastChannel === 'function') {
      try {
        const canal = this.canal ?? new ventana.BroadcastChannel(CANAL_CAMBIO_ESCUELA);
        canal.postMessage({ tipo: MENSAJE_ESCUELA_CAMBIADA });
        if (canal !== this.canal) {
          canal.close();
        }
        return;
      } catch {
        // Sigue con la alternativa del almacén.
      }
    }
    try {
      // El evento `storage` solo llega a las demás pestañas; la clave se quita al momento (no guarda nada).
      ventana.localStorage.setItem(CANAL_CAMBIO_ESCUELA, String(Date.now()));
      ventana.localStorage.removeItem(CANAL_CAMBIO_ESCUELA);
    } catch {
      // Sin almacén utilizable no hay forma de avisar.
    }
  }

  ngOnDestroy(): void {
    this.canal?.close();
    this.canal = null;
    this.documento.defaultView?.removeEventListener('storage', this.alRecibirAlmacen);
    this.capa?.remove();
    this.capa = null;
  }

  /** La escuela cambió en otra pestaña: bloquear y tapar esta hasta que recargue. */
  private recibir(): void {
    this.bloquear();
    this.mostrarCapa();
  }

  private mostrarCapa(): void {
    const documento = this.documento;
    if (this.capa?.isConnected) {
      this.abrirModal(this.capa);
      return;
    }
    const capa = documento.createElement('dialog');
    capa.className = 'taemoi-cambio-escuela';
    capa.setAttribute('role', 'alertdialog');
    capa.setAttribute('aria-modal', 'true');
    capa.setAttribute('aria-labelledby', 'taemoi-cambio-escuela-texto');
    Object.assign(capa.style, {
      maxWidth: 'min(420px, calc(100vw - 32px))',
      padding: '24px',
      border: '0',
      borderRadius: '12px',
      textAlign: 'center',
      font: 'inherit',
    });

    const texto = documento.createElement('p');
    texto.id = 'taemoi-cambio-escuela-texto';
    texto.textContent = TEXTO_CAMBIO_OTRA_PESTANA;
    Object.assign(texto.style, { margin: '0 0 16px', fontWeight: '600' });

    const boton = documento.createElement('button');
    boton.type = 'button';
    boton.textContent = 'Recargar';
    Object.assign(boton.style, { minHeight: '40px', padding: '0 16px', cursor: 'pointer' });
    boton.addEventListener('click', () => this.recargar());

    capa.append(texto, boton);
    // Esc no la cierra: no se puede seguir usando la pantalla de la escuela anterior. Si el navegador la cierra igual
    // (Chrome deja cerrar con un segundo Esc), vuelve a abrirse.
    capa.addEventListener('cancel', (evento) => evento.preventDefault());
    capa.addEventListener('close', () => {
      if (capa.isConnected) {
        this.abrirModal(capa);
      }
    });
    documento.body.appendChild(capa);
    this.capa = capa;
    this.abrirModal(capa);
    boton.focus();
  }

  private abrirModal(capa: HTMLDialogElement): void {
    if (capa.open) {
      return;
    }
    try {
      capa.showModal();
    } catch {
      // Sin `showModal` (navegador antiguo): al menos a la vista.
      capa.setAttribute('open', '');
    }
  }

  private recargar(): void {
    try {
      this.documento.defaultView?.sessionStorage.clear();
    } catch {
      // Sin sessionStorage utilizable no hay nada que vaciar.
    }
    this.irA(RUTA_ENTRADA_SESION);
  }
}

/**
 * Tras cambiar de escuela (en esta pestaña o en otra), ninguna petición sale hasta recargar: la cookie ya es de la
 * escuela nueva y la memoria de la pestaña, de la anterior. La petición queda sin respuesta (la página se va a recargar):
 * así no salta ningún aviso de error por encima de la capa.
 */
export const bloqueoCambioEscuelaInterceptor: HttpInterceptorFn = (peticion, siguiente) =>
  inject(CambioEscuelaPestanasService).bloqueada ? NEVER : siguiente(peticion);
