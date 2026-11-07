import { DOCUMENT } from '@angular/common';
import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, InjectionToken, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable, filter, map, switchMap, take } from 'rxjs';

/**
 * Entrada sin pantalla que resuelve el destino de la sesión con la regla del login con contraseña (decisión 384,
 * `entradaGoogleGuard`). Tras cambiar de escuela, la recarga completa va aquí: los roles de la escuela nueva deciden si
 * el destino es el panel, «Mis grupos», «Mi caja» o el área de familias.
 */
export const RUTA_ENTRADA_SESION = '/login/google';

/** Nombre del `BroadcastChannel` y clave de `localStorage` (evento `storage`) del aviso entre pestañas. */
export const CANAL_CAMBIO_ESCUELA = 'taemoi.cambioEscuela';

/** Tipo de los mensajes del canal. */
export const MENSAJE_CAMBIO_ESCUELA = 'cambio-escuela';

/**
 * Sin cierre (`confirmado` o `cancelado`) de un cambio anunciado en otra pestaña en este tiempo, esta muestra la capa:
 * recargar siempre es seguro, y seguir no lo es si el cambio llegó a hacerse.
 */
export const ESPERA_CIERRE_OTRA_PESTANA_MS = 30_000;

/** La petición del propio cambio de escuela: es la única que sale mientras la pestaña está bloqueada. */
export const PASA_BLOQUEO_CAMBIO_ESCUELA = new HttpContextToken<boolean>(() => false);

/** La escuela a la que se cambia (su nombre sale en la capa de las demás pestañas). */
export interface EscuelaAnunciada {
  id: number;
  nombre: string;
}

export type EstadoCambioEscuela = 'cambiando' | 'confirmado' | 'cancelado';

/** Mensaje entre pestañas: el mismo por `BroadcastChannel` y por el evento `storage`. */
export interface MensajeCambioEscuela {
  tipo: typeof MENSAJE_CAMBIO_ESCUELA;
  idCambio: string;
  estado: EstadoCambioEscuela;
  escuelaNueva: EscuelaAnunciada | null;
}

/** Carga completa de una URL de la app (tras cambiar de escuela); en los tests se sustituye. */
export const IR_A_URL = new InjectionToken<(url: string) => void>('IR_A_URL', {
  providedIn: 'root',
  factory: () => {
    const documento = inject(DOCUMENT);
    return (url: string) => documento.defaultView?.location.assign(url);
  },
});

const ESTADOS: readonly EstadoCambioEscuela[] = ['cambiando', 'confirmado', 'cancelado'];

/** Un cambio anunciado por otra pestaña que aún no se ha cerrado. */
interface CambioAjeno {
  temporizador: ReturnType<typeof setTimeout>;
}

/**
 * Coordinación entre pestañas del cambio de escuela (fase 4, selector de escuela). La cookie de sesión es común a todas
 * las pestañas del navegador: tras un cambio, las demás seguirían con la memoria de la escuela anterior y la cookie de
 * la nueva. Ninguna puede escribir con esa mezcla.
 *
 * Pestaña que cambia (`SelectorEscuelaService`), ya fuera de su pantalla y ANTES del `POST`:
 * - {@link anunciarCambio}: bloquea sus peticiones (salvo la del cambio, con {@link PASA_BLOQUEO_CAMBIO_ESCUELA}) y
 *   publica `cambiando` con la escuela nueva;
 * - si el `POST` va bien, {@link confirmarCambio} (bloqueo definitivo y `confirmado`) y recarga;
 * - si la API lo rechaza, {@link cancelarCambio} (`cancelado` y desbloqueo);
 * - si no se sabe (5xx, red caída, tiempo agotado), {@link bloquear} y recarga: las demás muestran la capa (sin nombre)
 *   ya; si el aviso se perdiera, a los {@link ESPERA_CIERRE_OTRA_PESTANA_MS}.
 *
 * Demás pestañas: con `cambiando` bloquean sus peticiones al momento (quedan en espera, sin capa); con `confirmado`, o
 * sin cierre en {@link ESPERA_CIERRE_OTRA_PESTANA_MS}, se tapan con la capa del boceto aprobado
 * (`boceto-aviso-cambio-escuela-pestana.html`), cuyo único botón vacía su `sessionStorage` y recarga la app entera en
 * {@link RUTA_ENTRADA_SESION}; con `cancelado`, las peticiones en espera salen como si nada.
 *
 * Cada mensaje se publica por `BroadcastChannel` Y por el evento `storage` (una pestaña puede tener solo uno de los
 * dos); el receptor descarta los repetidos por `idCambio` y estado. Va en el bundle inicial (la arranca
 * `AppComponent`): debe escuchar también en el área de familias y en la web. La capa y sus estilos se cargan aparte
 * (`capa-cambio-escuela.ts`) solo cuando hay que mostrarla.
 */
@Injectable({ providedIn: 'root' })
export class CambioEscuelaPestanasService implements OnDestroy {
  private readonly documento = inject(DOCUMENT);
  private readonly irA = inject(IR_A_URL);
  private canal: BroadcastChannel | null = null;
  private iniciado = false;
  private destruido = false;
  /** Bloqueo hasta recargar: la escuela de la sesión ya no es (o puede no ser) la de la memoria de la pestaña. */
  private definitivo = false;
  /** El cambio en vuelo de esta pestaña (anunciado y sin cerrar). */
  private propio: { idCambio: string; escuela: EscuelaAnunciada } | null = null;
  private readonly ajenos = new Map<string, CambioAjeno>();
  /** `idCambio:estado` ya atendidos (llegan por los dos canales) e `idCambio` ya cerrados o propios. */
  private readonly vistos = new Set<string>();
  private readonly cerrados = new Set<string>();
  private readonly creados = new Set<string>();
  private readonly bloqueo$ = new BehaviorSubject(false);
  private capa: HTMLDialogElement | null = null;
  /** La capa a la vista solo porque un cambio ajeno no se cerró a tiempo (un `cancelado` tardío la quita). */
  private capaPorEspera = false;
  private pidiendoCapa = false;
  /** Nombre de la escuela nueva para la capa (null: la variante sin nombre). */
  private nombreCapa: string | null = null;

  private readonly alRecibirAlmacen = (evento: StorageEvent): void => {
    if (evento.key !== CANAL_CAMBIO_ESCUELA || !evento.newValue) {
      return;
    }
    try {
      this.recibir(JSON.parse(evento.newValue));
    } catch {
      // Un valor que no es nuestro: se ignora.
    }
  };

  /** true mientras ninguna petición puede salir (salvo la del propio cambio). */
  get bloqueada(): boolean {
    return this.definitivo || this.propio !== null || this.ajenos.size > 0;
  }

  /** Emite (y completa) cuando la pestaña se desbloquea; tras un bloqueo definitivo, nunca. */
  desbloqueo(): Observable<void> {
    return this.bloqueo$.pipe(
      filter((bloqueada) => !bloqueada),
      take(1),
      map(() => undefined)
    );
  }

  /** Empieza a escuchar a las demás pestañas (una vez). */
  iniciar(): void {
    const ventana = this.documento.defaultView;
    if (this.iniciado || !ventana) {
      return;
    }
    this.iniciado = true;
    this.canal = this.abrirCanal();
    if (this.canal) {
      this.canal.onmessage = (evento: MessageEvent) => this.recibir(evento.data);
    }
    // Siempre también el almacén: la pestaña que avisa puede no tener BroadcastChannel.
    ventana.addEventListener('storage', this.alRecibirAlmacen);
  }

  /**
   * Esta pestaña va a pedir el cambio: bloquea sus peticiones (salvo la del cambio) y avisa a las demás de que dejen de
   * pedir nada. Devuelve el `idCambio` para cerrarlo con {@link confirmarCambio} o {@link cancelarCambio}.
   */
  anunciarCambio(escuela: EscuelaAnunciada): string {
    const idCambio = this.nuevoId();
    this.creados.add(idCambio);
    this.propio = { idCambio, escuela: { id: escuela.id, nombre: escuela.nombre } };
    this.actualizarBloqueo();
    this.publicar({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio, estado: 'cambiando', escuelaNueva: this.propio.escuela });
    return idCambio;
  }

  /** El cambio se hizo: bloqueo hasta recargar y las demás pestañas se tapan con la capa (con el nombre). */
  confirmarCambio(idCambio: string): void {
    const escuela = this.propio?.idCambio === idCambio ? this.propio.escuela : null;
    this.definitivo = true;
    this.propio = null;
    this.actualizarBloqueo();
    this.publicar({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio, estado: 'confirmado', escuelaNueva: escuela });
  }

  /** La API rechazó el cambio: la sesión sigue en la escuela de antes; esta pestaña y las demás se desbloquean. */
  cancelarCambio(idCambio: string): void {
    if (this.propio?.idCambio === idCambio) {
      this.propio = null;
    }
    this.publicar({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio, estado: 'cancelado', escuelaNueva: null });
    this.actualizarBloqueo();
  }

  /**
   * Resultado incierto (no se sabe si la API lo hizo): bloqueo hasta recargar y las demás pestañas se tapan YA con la
   * capa sin nombre (sin esperar a {@link ESPERA_CIERRE_OTRA_PESTANA_MS}). Va como `confirmado` sin escuela: el receptor
   * lo trata igual, solo que sin nombre.
   */
  bloquear(idCambio: string): void {
    this.definitivo = true;
    this.propio = null;
    this.actualizarBloqueo();
    this.publicar({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio, estado: 'confirmado', escuelaNueva: null });
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.canal?.close();
    this.canal = null;
    this.documento.defaultView?.removeEventListener('storage', this.alRecibirAlmacen);
    this.ajenos.forEach((cambio) => clearTimeout(cambio.temporizador));
    this.ajenos.clear();
    this.quitarCapa();
  }

  private abrirCanal(): BroadcastChannel | null {
    const ventana = this.documento.defaultView;
    if (!ventana || typeof ventana.BroadcastChannel !== 'function') {
      return null;
    }
    try {
      return new ventana.BroadcastChannel(CANAL_CAMBIO_ESCUELA);
    } catch {
      return null;
    }
  }

  /** Por los dos canales: el receptor descarta los repetidos. */
  private publicar(mensaje: MensajeCambioEscuela): void {
    const ventana = this.documento.defaultView;
    if (!ventana) {
      return;
    }
    const canal = this.canal ?? this.abrirCanal();
    if (canal) {
      try {
        canal.postMessage(mensaje);
      } catch {
        // Sigue con el almacén.
      }
      if (canal !== this.canal) {
        canal.close();
      }
    }
    try {
      // El evento `storage` solo llega a las demás pestañas; la clave se quita al momento (no guarda nada).
      ventana.localStorage.setItem(CANAL_CAMBIO_ESCUELA, JSON.stringify(mensaje));
      ventana.localStorage.removeItem(CANAL_CAMBIO_ESCUELA);
    } catch {
      // Sin almacén utilizable queda el BroadcastChannel.
    }
  }

  private recibir(datos: unknown): void {
    const mensaje = leerMensaje(datos);
    if (!mensaje || this.creados.has(mensaje.idCambio)) {
      return;
    }
    const clave = `${mensaje.idCambio}:${mensaje.estado}`;
    if (this.vistos.has(clave)) {
      return;
    }
    this.vistos.add(clave);
    switch (mensaje.estado) {
      case 'cambiando':
        this.alAnunciarseOtro(mensaje);
        break;
      case 'confirmado':
        this.cerrarAjeno(mensaje.idCambio);
        this.definitivo = true;
        this.actualizarBloqueo();
        this.capaPorEspera = false;
        this.mostrarCapa(mensaje.escuelaNueva?.nombre ?? null);
        break;
      case 'cancelado':
        this.cerrarAjeno(mensaje.idCambio);
        if (this.capaPorEspera && !this.definitivo && this.ajenos.size === 0) {
          // La capa salió solo por la espera y el cambio no se hizo: la pestaña sigue siendo válida.
          this.capaPorEspera = false;
          this.quitarCapa();
        }
        this.actualizarBloqueo();
        break;
    }
  }

  /** Otra pestaña va a cambiar: nada sale de aquí hasta que lo cierre (o pase la espera y salga la capa). */
  private alAnunciarseOtro(mensaje: MensajeCambioEscuela): void {
    if (this.cerrados.has(mensaje.idCambio) || this.ajenos.has(mensaje.idCambio)) {
      return;
    }
    const temporizador = setTimeout(() => {
      if (this.ajenos.has(mensaje.idCambio) && !this.capa) {
        this.capaPorEspera = true;
        // Sin saber si el cambio se hizo: la variante sin nombre.
        this.mostrarCapa(null);
      }
    }, ESPERA_CIERRE_OTRA_PESTANA_MS);
    this.ajenos.set(mensaje.idCambio, { temporizador });
    this.actualizarBloqueo();
  }

  private cerrarAjeno(idCambio: string): void {
    this.cerrados.add(idCambio);
    const cambio = this.ajenos.get(idCambio);
    if (cambio) {
      clearTimeout(cambio.temporizador);
      this.ajenos.delete(idCambio);
    }
  }

  private actualizarBloqueo(): void {
    if (this.bloqueo$.value !== this.bloqueada) {
      this.bloqueo$.next(this.bloqueada);
    }
  }

  /** Pinta la capa (o le pone el nombre si ya estaba). Se carga aparte: el bundle inicial no lleva sus estilos. */
  private mostrarCapa(nombre: string | null): void {
    if (this.capa) {
      if (nombre) {
        this.capa.dispatchEvent(new CustomEvent(EVENTO_NOMBRE_CAPA, { detail: nombre }));
      }
      return;
    }
    this.nombreCapa = nombre ?? this.nombreCapa;
    if (this.pidiendoCapa) {
      return;
    }
    this.pidiendoCapa = true;
    import('./capa-cambio-escuela')
      .then((m) => m.pintarCapaCambioEscuela)
      .catch(() => pintarCapaMinima)
      .then((pintar) => {
        this.pidiendoCapa = false;
        if (this.destruido || this.capa || !this.debeVerseLaCapa()) {
          return;
        }
        this.capa = pintar(this.documento, { nombre: this.nombreCapa, alRecargar: () => this.recargar() });
      });
  }

  /** Un `cancelado` puede llegar mientras se carga la capa de la espera. */
  private debeVerseLaCapa(): boolean {
    return this.definitivo || this.capaPorEspera;
  }

  private quitarCapa(): void {
    const capa = this.capa;
    this.capa = null;
    this.nombreCapa = null;
    if (capa) {
      if (capa.open) {
        capa.close();
      }
      capa.remove();
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

  private nuevoId(): string {
    const cripto = this.documento.defaultView?.crypto;
    if (cripto && typeof cripto.randomUUID === 'function') {
      return cripto.randomUUID();
    }
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  }
}

/** Evento con el que la capa ya pintada recibe el nombre de la escuela nueva (llega el `confirmado` tras la espera). */
export const EVENTO_NOMBRE_CAPA = 'taemoi-cambio-escuela-nombre';

function leerMensaje(datos: unknown): MensajeCambioEscuela | null {
  if (!datos || typeof datos !== 'object') {
    return null;
  }
  const m = datos as Partial<MensajeCambioEscuela>;
  if (m.tipo !== MENSAJE_CAMBIO_ESCUELA || typeof m.idCambio !== 'string' || !m.idCambio) {
    return null;
  }
  if (!ESTADOS.includes(m.estado as EstadoCambioEscuela)) {
    return null;
  }
  const escuela = m.escuelaNueva;
  const escuelaNueva =
    escuela && typeof escuela === 'object' && typeof escuela.nombre === 'string' && escuela.nombre.trim()
      ? { id: Number(escuela.id), nombre: escuela.nombre.trim() }
      : null;
  return { tipo: MENSAJE_CAMBIO_ESCUELA, idCambio: m.idCambio, estado: m.estado as EstadoCambioEscuela, escuelaNueva };
}

/** Opciones de la capa de las demás pestañas. */
export interface OpcionesCapaCambioEscuela {
  nombre: string | null;
  alRecargar: () => void;
}

/** Si no se puede cargar la capa con sus estilos (p. ej. un despliegue entretanto), una sin estilos con los mismos textos. */
function pintarCapaMinima(documento: Document, opciones: OpcionesCapaCambioEscuela): HTMLDialogElement {
  const capa = documento.createElement('dialog');
  capa.className = 'taemoi-cambio-escuela';
  capa.setAttribute('role', 'alertdialog');
  capa.setAttribute('aria-modal', 'true');
  capa.setAttribute('aria-labelledby', 'taemoi-cambio-escuela-titulo');
  const titulo = documento.createElement('h2');
  titulo.id = 'taemoi-cambio-escuela-titulo';
  titulo.textContent = 'Has cambiado de escuela';
  const boton = documento.createElement('button');
  boton.type = 'button';
  boton.textContent = 'Recargar';
  boton.style.minHeight = '44px';
  boton.addEventListener('click', () => opciones.alRecargar());
  capa.append(titulo, boton);
  capa.addEventListener('cancel', (evento) => evento.preventDefault());
  documento.body.appendChild(capa);
  try {
    capa.showModal();
  } catch {
    capa.setAttribute('open', '');
  }
  boton.focus();
  return capa;
}

/**
 * Mientras la pestaña está bloqueada por un cambio de escuela (aquí o en otra), las peticiones esperan: si el cambio se
 * cancela salen tal cual; si se hace, nunca (la pestaña se va a recargar) y así no salta ningún aviso de error por
 * encima de la capa. La del propio cambio pasa con {@link PASA_BLOQUEO_CAMBIO_ESCUELA}.
 */
export const bloqueoCambioEscuelaInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  if (peticion.context.get(PASA_BLOQUEO_CAMBIO_ESCUELA)) {
    return siguiente(peticion);
  }
  const pestanas = inject(CambioEscuelaPestanasService);
  return pestanas.bloqueada ? pestanas.desbloqueo().pipe(switchMap(() => siguiente(peticion))) : siguiente(peticion);
};
