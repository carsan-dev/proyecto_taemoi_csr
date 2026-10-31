import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  HostListener,
  InjectionToken,
  OnInit,
  Output,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationStart, Router, RouterLink } from '@angular/router';
import { catchError, filter, map, of, startWith, switchMap, take } from 'rxjs';

import { contraste } from '../../componentes/endpoints/plataforma-logos/regla-contraste';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { Capacidad, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { SolicitudesContadorService } from '../../servicios/endpoints/solicitudes-contador.service';
import { etiquetaRol } from '../formato/roles';
import { MarcaEscuelaPanelService, MarcaPanel } from '../marca-escuela-panel.service';
import { BarraInferiorComponent } from './barra-inferior.component';
import {
  CUENTA_PANEL,
  ContextoMenu,
  ENLACES_LEGALES,
  EntradaMenu,
  IdSeccion,
  SeccionMenu,
  entradaActual,
  menuVisible,
  seccionActiva,
  visible,
} from './menu-panel';
import { RECARGAR_PAGINA } from '../../core/recargar-pagina';
import { enfocarOpcion, teclaEnMenu } from './teclado-menu';

export { RECARGAR_PAGINA };

/** Tema de una escuela sin color (decisión 257): el de `panel-nuevo.scss` mientras no llega la marca. */
const TEXTO_POR_DEFECTO = '#FFFFFF';
const PRIMARIO_POR_DEFECTO = '#1A2230';
const MEZCLA_ACTIVA = 14;

/**
 * Fondo de la entrada actual (y del paso del ratón): el texto de la cabecera al 14 % sobre el color de la escuela
 * (DESIGN.md, Navigation), bajando de uno en uno mientras el texto no llegue a 4,5:1 encima. Con 0 % queda el primario,
 * que ya llega por la regla de contraste.
 */
export function fondoActivoCabecera(texto: string, primario: string): string {
  for (let porcentaje = MEZCLA_ACTIVA; porcentaje > 0; porcentaje--) {
    const mezcla = mezclar(primario, texto, porcentaje / 100);
    if (contraste(texto, mezcla) >= 4.5) {
      return mezcla;
    }
  }
  return primario.toUpperCase();
}

function mezclar(base: string, otro: string, t: number): string {
  const canales = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
  const a = canales(base);
  const b = canales(otro);
  return `#${a
    .map((v, i) => Math.floor(v * (1 - t) + b[i] * t + 0.5).toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()}`;
}

/** «Carla S.» → «CS». */
export function iniciales(nombre: string | null | undefined): string {
  const palabras = (nombre ?? '')
    .split(/\s+/)
    .map((p) => p.replace(/[^\p{L}]/gu, ''))
    .filter(Boolean);
  return palabras
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

type IdMenu = IdSeccion | 'cuenta';

/**
 * Cabecera C del panel nuevo (8b-N, fase3-e8b-plan.md §8; DESIGN.md, Navigation): color y logo de la escuela, seis
 * entradas (cuatro con desplegable) y la cuenta; por debajo de 1024 px, la barra inferior. Se carga en un `@defer`
 * de `skeleton` solo con una sesión del panel (gestión, responsable de sede o profesor) y en una ruta de gestión.
 *
 * - Antes de pintar espera a la marca (`GET /api/escuela/marca`, una vez por sesión) y la escribe en su raíz `.pn`;
 *   mientras tanto, el hueco de 64 px que deja `skeleton`. Si falla, pinta con el tema por defecto.
 * - El contador de solicitudes es el mismo servicio que la cabecera de hoy (que no está montada a la vez): se refresca
 *   al entrar y al navegar, nunca por temporizador.
 * - Las condiciones de cada opción son las de la cabecera de hoy (`menu-panel.ts`).
 */
/** Pasar lista (`/mis-grupos/:id/lista`, `/gruposListar/:id/lista`): su barra fija de guardar sustituye a la navegación inferior. */
export function ocultaBarraInferior(url: string): boolean {
  return /^\/(mis-grupos|gruposListar)\/\d+\/lista(?:[?#]|$)/.test(url);
}

/** Profesor sin gestión ni responsable: los dos accesos directos (la familia + profesor también, decisión 326). */
function soloAccesosDeProfesor(roles: readonly string[]): boolean {
  return (
    roles.includes('ROLE_PROFESOR') &&
    !roles.includes('ROLE_ADMIN') &&
    !roles.includes('ROLE_MANAGER') &&
    !roles.includes('ROLE_RESPONSABLE_SEDE')
  );
}

/** Quién ve la bandeja de solicitudes y su contador. */
function veSolicitudes(roles: readonly string[]): boolean {
  return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER') || roles.includes('ROLE_RESPONSABLE_SEDE');
}

@Component({
  selector: 'app-cabecera-panel',
  standalone: true,
  imports: [RouterLink, BarraInferiorComponent],
  templateUrl: './cabecera-panel.component.html',
  styleUrl: './cabecera-panel.component.scss',
})
export class CabeceraPanelComponent implements OnInit {
  /** «Configurar cookies»: `skeleton` vuelve a abrir el aviso de cookies de hoy (decisiones 250 y 259). */
  @Output() readonly configurarCookies = new EventEmitter<void>();

  @ViewChild('raiz', { static: true }) private readonly raiz!: ElementRef<HTMLElement>;

  private readonly auth = inject(AuthenticationService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly marcaServicio = inject(MarcaEscuelaPanelService);
  private readonly contadorSolicitudes = inject(SolicitudesContadorService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly legales = ENLACES_LEGALES;

  /** La marca llegó (o falló): ya se puede pintar. */
  readonly lista = signal(false);
  readonly marca = signal<MarcaPanel | null>(null);
  readonly logoFallo = signal(false);
  readonly abierto = signal<IdMenu | null>(null);
  readonly url = signal(this.router.url);

  private readonly roles = toSignal(this.auth.getRoles(), { initialValue: [] as string[] });
  readonly soloProfesor = computed(() => soloAccesosDeProfesor(this.roles()));
  private readonly capacidades = toSignal<readonly Capacidad[], readonly Capacidad[]>(this.permisos.capacidades(), {
    initialValue: [],
  });
  private readonly plataforma = toSignal(this.auth.getRoles().pipe(
    filter((roles) => roles.length > 0),
    switchMap((roles) => soloAccesosDeProfesor(roles) ? of(false) : this.auth.esAdminPlataforma()),
    catchError(() => of(false))), {
    initialValue: false,
  });
  /** Se vuelve a pedir cuando «Mi perfil» cambia el nombre (`refrescar()`), sin recargar la página. */
  private readonly nombreSesion = toSignal(
    this.permisos.refrescado$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.permisos.obtener().pipe(
          map((p) => p.nombreVisible ?? null),
          catchError(() => of(null))
        )
      )
    ),
    { initialValue: null }
  );
  private readonly usuario = toSignal(this.auth.usernameCambio, { initialValue: null });

  readonly contexto = computed<ContextoMenu>(() => {
    const roles = this.roles();
    const profesor = roles.includes('ROLE_PROFESOR');
    return {
      propietario: roles.includes('ROLE_ADMIN'),
      soloProfesor: this.soloProfesor(),
      panelSede: this.capacidades().includes('PANEL_SEDE'),
      gestion: roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER'),
      profesor,
      misGrupos: profesor && this.capacidades().includes('MIS_GRUPOS'),
      sedes: this.capacidades().includes('SEDES'),
      miembros: this.capacidades().includes('MIEMBROS'),
      plataforma: this.plataforma(),
    };
  });

  readonly secciones = computed<SeccionMenu[]>(() => menuVisible(this.contexto()));
  readonly cuenta = computed<EntradaMenu[]>(() => CUENTA_PANEL.filter((e) => visible(e, this.contexto())));
  readonly accesoDual = computed(() => this.auth.tieneAccesoDual(this.roles(), this.capacidades()));
  readonly activa = computed(() => seccionActiva(this.url()));
  /** Pasar lista (boceto aprobado): en móvil la barra fija de guardar ocupa el sitio de la navegación inferior. */
  readonly sinBarraInferior = computed(() => ocultaBarraInferior(this.url()));
  readonly contador = computed(() => this.soloProfesor() ? 0 : this.contadorSolicitudes.total());

  readonly nombreEscuela = computed(() => this.marca()?.nombre || 'Panel de gestión');
  readonly logo = computed(() => {
    const marca = this.marca();
    return marca && !this.logoFallo() ? this.marcaServicio.urlLogo(marca) : null;
  });
  readonly nombreVisible = computed(() => this.nombreSesion() || this.usuario() || 'Mi cuenta');
  readonly iniciales = computed(() => iniciales(this.nombreSesion() || this.usuario()));
  readonly rol = computed(() => {
    const roles = this.roles();
    if (this.soloProfesor()) {
      return 'Profesor';
    }
    if (roles.includes('ROLE_ADMIN')) {
      return etiquetaRol('ROLE_ADMIN');
    }
    return etiquetaRol(roles.includes('ROLE_MANAGER') ? 'ROLE_MANAGER' : 'ROLE_RESPONSABLE_SEDE');
  });

  ngOnInit(): void {
    const raiz = this.raiz.nativeElement;
    this.pintarFondoActivo(TEXTO_POR_DEFECTO, PRIMARIO_POR_DEFECTO);
    this.marcaServicio
      .marca()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (marca) => {
          this.marcaServicio.aplicarEn(raiz, marca.tema);
          this.pintarFondoActivo(marca.tema.sobrePrimario, marca.tema.primario);
          this.marca.set(marca);
          this.lista.set(true);
        },
        error: () => this.lista.set(true),
      });

    this.auth.getRoles().pipe(
      filter(veSolicitudes),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.contadorSolicitudes.refrescar());
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((evento) => {
      if (evento instanceof NavigationStart) {
        this.abierto.set(null);
      }
      if (evento instanceof NavigationEnd) {
        this.url.set(evento.urlAfterRedirects || evento.url);
        if (veSolicitudes(this.roles())) {
          this.contadorSolicitudes.refrescar();
        }
      }
    });
  }

  private pintarFondoActivo(texto: string, primario: string): void {
    this.raiz.nativeElement.style.setProperty('--pn-cab-activa', fondoActivoCabecera(texto, primario));
  }

  textoContador(total: number): string {
    return total > 99 ? '99+' : String(total);
  }

  etiquetaEntrada(seccion: SeccionMenu): string | null {
    const total = seccion.contador === 'solicitudes' ? this.contador() : 0;
    return total > 0 ? `${seccion.etiqueta}: ${total} por atender` : null;
  }

  esActual(ruta: string): boolean {
    return entradaActual(ruta, this.url());
  }

  /** Grupos de un desplegable: lo que va antes del primer `grupo`, y cada grupo con su nombre. */
  grupos(entradas: readonly EntradaMenu[]): { nombre: string | null; entradas: EntradaMenu[] }[] {
    const grupos: { nombre: string | null; entradas: EntradaMenu[] }[] = [];
    for (const entrada of entradas) {
      if (entrada.grupo || grupos.length === 0) {
        grupos.push({ nombre: entrada.grupo ?? null, entradas: [] });
      }
      grupos[grupos.length - 1].entradas.push(entrada);
    }
    return grupos;
  }

  alternar(id: IdMenu, evento: MouseEvent): void {
    if (this.abierto() === id) {
      this.cerrar(false);
      return;
    }
    // Un clic del teclado (Intro o Espacio: detail 0) lleva el foco a la primera opción.
    this.abrir(id, evento.detail === 0 ? 'primera' : null);
  }

  private abrir(id: IdMenu, foco: 'primera' | 'ultima' | null): void {
    this.abierto.set(id);
    if (foco) {
      this.cdr.detectChanges();
      enfocarOpcion(this.menu(id), foco);
    }
  }

  cerrar(devolverFoco: boolean): void {
    const id = this.abierto();
    this.abierto.set(null);
    if (devolverFoco && id) {
      this.cdr.detectChanges();
      this.disparador(id)?.focus();
    }
  }

  /** Teclado en las entradas: flechas a los lados entre ellas; abajo o arriba abren el desplegable. */
  teclaEntrada(evento: KeyboardEvent, id: IdMenu | null = null): void {
    const objetivo = evento.currentTarget as HTMLElement;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowLeft': {
        if (id === 'cuenta') {
          return;
        }
        const entradas = Array.from(
          this.raiz.nativeElement.querySelectorAll<HTMLElement>('.pn-cab__nav .pn-cab__entrada')
        );
        const i = entradas.indexOf(objetivo);
        const paso = evento.key === 'ArrowRight' ? 1 : -1;
        entradas[(i + paso + entradas.length) % entradas.length]?.focus();
        evento.preventDefault();
        return;
      }
      case 'ArrowDown':
      case 'ArrowUp':
        if (id) {
          evento.preventDefault();
          this.abrir(id, evento.key === 'ArrowDown' ? 'primera' : 'ultima');
        }
        return;
      case 'Escape':
        if (this.abierto()) {
          evento.preventDefault();
          this.cerrar(true);
        }
        return;
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const id = this.abierto();
    const menu = id ? this.menu(id) : null;
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion) {
      this.cerrar(accion === 'cerrar');
    }
  }

  @HostListener('document:click', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    const id = this.abierto();
    if (!id) {
      return;
    }
    const zona = this.raiz.nativeElement.querySelector(`[data-pn-menu="${id}"]`);
    if (!zona || !zona.contains(evento.target as Node)) {
      this.abierto.set(null);
    }
  }

  private menu(id: IdMenu): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(`#pn-menu-${id}`);
  }

  private disparador(id: IdMenu): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(
      id === 'cuenta' ? '#pn-cuenta-boton' : `#pn-disparador-${id}`
    );
  }

  alConfigurarCookies(): void {
    this.cerrar(false);
    this.configurarCookies.emit();
  }

  cambiarAFamilia(): void {
    this.cerrar(false);
    this.auth.guardarVistaPreferida('user');
    this.router.navigate(['/userpage']);
  }

  /**
   * Igual que «Cerrar sesión» de la cabecera de hoy, sin su SweetAlert2 «Sesión cerrada con éxito» (8b-D2a): se llega a
   * la web pública, fuera del panel nuevo, donde no están sus avisos (y montarlos solo para esto los metería en el bundle
   * inicial). Se ve que la sesión se cerró porque se sale del panel.
   */
  cerrarSesion(): void {
    this.cerrar(false);
    this.auth.logout();
    this.auth.usuarioLogueadoCambio.pipe(filter((logueado) => !logueado), take(1)).subscribe(() => {
      this.router.navigate(['/']);
    });
  }
}
