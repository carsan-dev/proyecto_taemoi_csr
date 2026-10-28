import { Component, DestroyRef, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, catchError, filter, from, map, of, skip, switchMap, take } from 'rxjs';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { CommonModule, DOCUMENT, Location } from '@angular/common';
import { borrarCookiesGa, cargarScriptGtag, urlSinToken } from '../../../../utils/analitica.util';
import { resolverPortalContexto } from '../../../../app.routes';
import { PermisosSesionService } from '../../../../servicios/authentication/permisos-sesion.service';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { CabeceraPanelComponent } from '../../../../panel-nuevo/navegacion/cabecera-panel.component';

/** Rutas del panel sin `portalContext: 'admin'` que una sesión de gestión abre desde su menú (Mi caja, Mis grupos, plataforma). */
const RUTAS_PANEL = ['/mi-caja', '/mis-grupos', '/plataformaErrores', '/plataformaLogos'];

/** Ruta de gestión: la cabecera nueva solo va en ellas (8b-N); las públicas y las de familia siguen con las de hoy. */
export function esRutaDelPanel(url: string): boolean {
  const ruta = url.split(/[?#]/)[0] || '/';
  return (
    resolverPortalContexto(ruta) === 'admin' || RUTAS_PANEL.some((r) => ruta === r || ruta.startsWith(`${r}/`))
  );
}

/** Recordatorio global (sin cuenta ni email) de que el interruptor estaba encendido en este navegador. */
export const RECORDATORIO_PANEL = 'panelNuevo';

/** localStorage puede lanzar (modo privado, bloqueado): entonces, sin recordatorio. */
function recordatorio(accion: (s: Storage) => unknown): unknown {
  try {
    return accion(localStorage);
  } catch {
    return null;
  }
}

/** `hoy`: cabecera y pie de hoy; `hueco`: aún no se sabe el interruptor; `nuevo`: la navegación del panel nuevo. */
type ModoCabecera = 'hoy' | 'hueco' | 'nuevo';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    CommonModule,
    CabeceraPanelComponent,
  ],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent implements OnInit {
  showCookieBanner = false;
  private readonly storageKey = 'cookieConsent';
  private readonly measurementId = 'G-R3H7GC1T4X';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private analiticaActiva = false;
  private ultimaPagina: string | null = null;
  private readonly injector = inject(Injector);
  private readonly rutaPanel = signal(esRutaDelPanel(inject(Location).path()));
  /** El interruptor de la sesión (null: aún no se sabe). Se pide la primera vez que se entra en una ruta de gestión. */
  private readonly encendido = signal<boolean | null>(null);
  private readonly recordado = signal(recordatorio((s) => s.getItem(RECORDATORIO_PANEL)) === '1');
  private evaluar$?: Subject<void>;

  /**
   * Fase3-e8b-plan.md §3 «Cabecera»: la de hoy salvo con el interruptor encendido y una ruta de gestión. Mientras no se
   * sabe, la de hoy (apagado = hoy desde el primer píxel), salvo que este navegador recuerde que estaba encendido:
   * entonces el hueco de 64 px, para no pintar la de hoy y saltar a la nueva.
   */
  readonly modo = computed<ModoCabecera>(() => {
    if (!this.rutaPanel()) {
      return 'hoy';
    }
    const encendido = this.encendido() ?? (this.recordado() ? null : false);
    return encendido === null ? 'hueco' : encendido ? 'nuevo' : 'hoy';
  });

  ngOnInit(): void {
    // Un page_view por página, siempre sin el token de los enlaces de contraseña (revisión E8-6 F1). El page_location
    // se fija en cada navegación decida lo que decida el usuario. Sin consentimiento gtag.js no se carga (decisión 233)
    // y el `set` se queda en dataLayer sin mandar nada; si luego acepta, la cola ya va limpia.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((e) => {
        this.alCambiarDeRuta(e.urlAfterRedirects);
        const pagina = urlSinToken(`${globalThis.location?.origin ?? ''}${e.urlAfterRedirects}`);
        this.fijarPagina(pagina);
        if (this.analiticaActiva) {
          this.enviarPageView(pagina);
        }
      });
    if (this.rutaPanel()) {
      this.evaluarInterruptor();
    }
    // La decisión guardada se vuelve a aplicar igual que al pulsar el botón (volver a guardarla no cambia nada).
    const stored = this.getStored();
    if (stored === 'accepted') {
      this.acceptCookies();
      return;
    }
    if (stored === 'rejected') {
      this.rejectCookies();
      return;
    }
    this.showCookieBanner = true;
  }

  /** Una ruta sin prefijo de gestión que se declara del panel con `data.panelNuevo` (el 404 del panel, 8b-M2b). */
  private rutaMarcadaComoPanel(): boolean {
    let ruta = this.router.routerState.snapshot.root;
    while (ruta.firstChild) {
      ruta = ruta.firstChild;
    }
    return ruta.data?.['panelNuevo'] === true;
  }

  private alCambiarDeRuta(url: string): void {
    this.rutaPanel.set(esRutaDelPanel(url) || this.rutaMarcadaComoPanel());
    if (this.rutaPanel()) {
      this.evaluarInterruptor();
    }
  }

  /**
   * Lee el interruptor al entrar en cada ruta del panel (los permisos están en caché: solo hay petición tras
   * `reiniciar()`, p. ej. al cambiar el propio diseño) y al entrar o salir de una cuenta. Los servicios se piden aquí,
   * no al crear el componente: fuera del panel no se pide nada. Encendido solo con `disenoNuevo.activo`, gestión en la
   * escuela (la sesión solo-profesor queda fuera, decisión 260) y la hoja `panel-nuevo.css` cargada. Falla cerrado.
   * Una respuesta válida guarda o borra el recordatorio; un error no lo toca; cerrar sesión lo borra.
   */
  private evaluarInterruptor(): void {
    if (!this.evaluar$) {
      const permisos = this.injector.get(PermisosSesionService);
      const estilos = this.injector.get(EstilosPanelService);
      const evaluar$ = (this.evaluar$ = new Subject<void>());
      evaluar$
        .pipe(
          switchMap(() =>
            permisos.obtener().pipe(
              take(1),
              map(
                (p) =>
                  p?.disenoNuevo?.activo === true &&
                  (p.roles ?? []).some((rol) => rol === 'PROPIETARIO' || rol === 'ADMINISTRADOR')
              ),
              switchMap((activo) => {
                this.recordar(activo);
                return activo ? from(estilos.cargar()).pipe(map(() => true)) : of(false);
              }),
              catchError(() => of(false))
            )
          ),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe((v) => this.encendido.set(v));
      this.injector
        .get(AuthenticationService)
        .usuarioLogueadoCambio.pipe(skip(1), takeUntilDestroyed(this.destroyRef))
        .subscribe((logueado) => {
          if (!logueado) {
            this.encendido.set(null);
            this.recordar(false);
          }
          if (this.rutaPanel()) {
            evaluar$.next();
          }
        });
    }
    this.evaluar$.next();
  }

  private recordar(encendido: boolean): void {
    this.recordado.set(encendido);
    recordatorio((s) => (encendido ? s.setItem(RECORDATORIO_PANEL, '1') : s.removeItem(RECORDATORIO_PANEL)));
  }

  acceptCookies(): void {
    this.setStored('accepted');
    this.desactivarGa(false);
    this.applyConsent(true);
    this.enableAnalytics();
    this.showCookieBanner = false;
  }

  /**
   * Rechazar, también desde «Configurar cookies» tras aceptar (decisión 250). Si gtag.js ya estaba cargado, el
   * `consent update` denegado y `ga-disable-<id>` hacen que deje de mandar hasta recargar; además se borran las
   * cookies _ga* que hubiera escrito.
   */
  rejectCookies(): void {
    this.setStored('rejected');
    this.applyConsent(false);
    this.desactivarGa(true);
    this.analiticaActiva = false;
    borrarCookiesGa(this.document);
    this.showCookieBanner = false;
  }

  private desactivarGa(desactivar: boolean): void {
    (globalThis as any)[`ga-disable-${this.measurementId}`] = desactivar;
  }

  private applyConsent(granted: boolean): void {
    const globalAny = globalThis as any;
    const update = {
      ad_storage: 'denied',
      analytics_storage: granted ? 'granted' : 'denied',
      functionality_storage: 'granted',
      security_storage: 'granted',
    };
    if (typeof globalAny.gtag === 'function') {
      globalAny.gtag('consent', 'update', update);
    } else {
      globalAny.dataLayer = globalAny.dataLayer || [];
      globalAny.dataLayer.push(['consent', 'update', update]);
    }
  }

  /**
   * El page_view automático de `config` usaría la URL de la barra, que en el arranque aún puede llevar `?token=`
   * (restablecer contraseña, invitación). Se desactiva y cada page_view se manda a mano con la URL sin el token.
   * Este `config` es el primero que ve gtag.js (consentimiento básico, decisión 233: el script se carga aquí, tras
   * aceptar) y ya lleva el page_location sin token. Lo que fija la página de los hits siguientes es el `set` de
   * page_location (`fijarPagina`).
   */
  private enableAnalytics(): void {
    const href = urlSinToken(globalThis.location?.href ?? '');
    const config = { anonymize_ip: true, send_page_view: false, page_location: href };
    this.gtag('js', new Date());
    this.gtag('config', this.measurementId, config);
    this.analiticaActiva = true;
    this.fijarPagina(href);
    this.enviarPageView(href);
    this.cargarScript();
  }

  /** gtag.js procesa al cargar la cola de dataLayer (consentimiento, config, set, page_view) en orden. */
  private cargarScript(): void {
    cargarScriptGtag(this.document, this.measurementId);
  }

  /** Fija la página de todos los hits siguientes de gtag.js; `set` no manda nada por sí mismo. */
  private fijarPagina(pagina: string): void {
    this.gtag('set', { page_location: urlSinToken(pagina) });
  }

  /** Manda el page_view de la página ya fijada con `fijarPagina`. */
  private enviarPageView(href: string): void {
    const pagina = urlSinToken(href);
    if (pagina === this.ultimaPagina) {
      return; // la navegación inicial (o quitar el token de la barra) no cuenta otra visita
    }
    this.ultimaPagina = pagina;
    let ruta = pagina;
    try {
      const url = new URL(pagina);
      ruta = url.pathname + url.search;
    } catch {
      // se manda tal cual
    }
    this.gtag('event', 'page_view', { page_location: pagina, page_path: ruta });
  }

  private gtag(...args: unknown[]): void {
    const globalAny = globalThis as any;
    if (typeof globalAny.gtag === 'function') {
      globalAny.gtag(...args);
    } else {
      globalAny.dataLayer = globalAny.dataLayer || [];
      globalAny.dataLayer.push(args);
    }
  }

  private getStored(): string | null {
    try {
      return localStorage.getItem(this.storageKey);
    } catch {
      return null;
    }
  }

  private setStored(value: string): void {
    try {
      localStorage.setItem(this.storageKey, value);
    } catch {
      // ignore
    }
  }
}
