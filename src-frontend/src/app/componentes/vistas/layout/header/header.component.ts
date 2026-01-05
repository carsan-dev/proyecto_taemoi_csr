import { Component, ElementRef, HostListener, OnDestroy, OnInit, AfterViewInit, NgZone, ViewChild } from '@angular/core';
import { NavigationStart, Router, RouterModule } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

type ResizeCorner = 'br' | 'bl' | 'tr' | 'tl';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit, AfterViewInit, OnDestroy {
  usuarioLogueado: boolean = false;
  isAdmin: boolean = false;
  isUser: boolean = false;
  isHidden: boolean = false;
  adminMenuVisible: boolean = false;
  isAuthChecked: boolean = false; // Prevents header flash during auth check
  private lastScrollTop: number = 0;
  private readonly scrollThreshold: number = 100;
  username: string | null = null;
  private documentScrollHandler?: (event: Event) => void;
  spotifyWidgetVisible: boolean = false;
  spotifyUrlInput: string = '';
  spotifyEmbedUrl: SafeResourceUrl | null = null;
  spotifyOpenUrl: string | null = null;
  spotifyError: string = '';
  spotifyWidgetWidth: number = 360;
  spotifyWidgetHeight: number = 360;
  private readonly spotifyUrlStorageKey = 'adminSpotifyUrl';
  private readonly spotifyVisibleStorageKey = 'adminSpotifyVisible';
  private readonly spotifySizeStorageKey = 'adminSpotifySize';
  private resizeCorner: ResizeCorner | null = null;
  private resizeStartX: number = 0;
  private resizeStartY: number = 0;
  private resizeStartWidth: number = 0;
  private resizeStartHeight: number = 0;
  private resizeMoveHandler?: (event: PointerEvent) => void;
  private resizeUpHandler?: (event: PointerEvent) => void;
  @ViewChild('spotifyWidget') private readonly spotifyWidgetRef?: ElementRef<HTMLElement>;

  // Expandable menu sections
  expandedSections: { [key: string]: boolean } = {
    alumnos: false,
    grupos: false,
    turnos: false,
    eventos: false,
    productos: false,
    convocatorias: false
  };

  // Scroll optimization
  private readonly subscriptions = new Subscription();

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly zone: NgZone,
    private readonly sanitizer: DomSanitizer,
    private readonly endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    // Subscribe to authentication state changes
    this.subscriptions.add(
      this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        this.usuarioLogueado = estado;
        if (estado) {
          this.comprobarRoles();
        }
      })
    );

    // Subscribe to username changes
    this.subscriptions.add(
      this.authService.usernameCambio.subscribe((username: string | null) => {
        this.username = username;
      })
    );

    // Check initial login state
    if (this.authService.comprobarLogueado()) {
      this.usuarioLogueado = true;
      this.comprobarRoles();
    } else {
      // Not logged in, auth check complete
      this.isAuthChecked = true;
    }

    // Close admin menu on navigation
    this.subscriptions.add(
      this.router.events.subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.adminMenuVisible = false;
        }
      })
    );

    this.cargarSpotifyConfig();
  }

  ngAfterViewInit(): void {
    const documentRef = this.getDocumentRef();
    if (documentRef !== undefined) {
      // Listen to document scroll events (same as botonscroll)
      this.documentScrollHandler = (event: Event) => {
        this.zone.run(() => this.handleScrollEvent(event));
      };
      documentRef.addEventListener('scroll', this.documentScrollHandler, true);
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.detenerRedimension();

    // Clean up document scroll listener
    const documentRef = this.getDocumentRef();
    if (this.documentScrollHandler && documentRef !== undefined) {
      documentRef.removeEventListener('scroll', this.documentScrollHandler, true);
      this.documentScrollHandler = undefined;
    }
  }

  comprobarRoles() {
    this.authService.getRoles().subscribe((roles: string[]) => {
      this.isAdmin = roles.includes('ROLE_ADMIN');
      this.isUser = roles.includes('ROLE_USER');
      this.isAuthChecked = true;
      if (this.isAdmin) {
        this.cargarSpotifyUrlServidor();
      }
    });
  }

  toggleAdminMenu(): void {
    this.adminMenuVisible = !this.adminMenuVisible;
  }

  closeAdminMenu(): void {
    this.adminMenuVisible = false;
    Object.keys(this.expandedSections).forEach(key => {
      this.expandedSections[key] = false;
    });
  }

  toggleSection(section: string): void {
    this.expandedSections[section] = !this.expandedSections[section];
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    this.handleScrollEvent();
  }

  private getWindowRef(): Window | undefined {
    return (globalThis as { window?: Window }).window;
  }

  private getDocumentRef(): Document | undefined {
    return (globalThis as { document?: Document }).document;
  }

  private handleScrollEvent(event?: Event): void {
    const windowRef = this.getWindowRef();
    const documentRef = this.getDocumentRef();
    if (windowRef !== undefined && documentRef !== undefined) {
      const currentScrollTop = this.getEffectiveScrollTop(event, windowRef, documentRef);

      // Siempre mostrar header en la parte superior
      if (currentScrollTop <= this.scrollThreshold) {
        this.isHidden = false;
        this.lastScrollTop = currentScrollTop;
      }
      // Scroll hacia abajo: ocultar
      else if (currentScrollTop > this.lastScrollTop && currentScrollTop > this.scrollThreshold) {
        this.isHidden = true;
      }
      // Scroll hacia arriba: mostrar
      else if (currentScrollTop < this.lastScrollTop) {
        this.isHidden = false;
      }

      this.lastScrollTop = Math.max(currentScrollTop, 0);

      // Collapse navbar when scrolling
      this.collapseNavbar();
    }
  }

  private getEffectiveScrollTop(event: Event | undefined, windowRef: Window, documentRef: Document): number {
    const target = event?.target;
    const targetScrollTop = target instanceof HTMLElement ? target.scrollTop : 0;
    const scrollingElement = documentRef.scrollingElement as HTMLElement | null;
    const scrollingElementTop = scrollingElement?.scrollTop ?? 0;
    const windowScrollTop = windowRef.scrollY || documentRef.documentElement.scrollTop || documentRef.body?.scrollTop || 0;

    return Math.max(targetScrollTop, scrollingElementTop, windowScrollTop);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (this.shouldIgnoreAdminClick(target)) {
      return;
    }

    this.handleNavbarClick(target);
  }

  private shouldIgnoreAdminClick(target: HTMLElement): boolean {
    if (!this.adminMenuVisible) {
      return false;
    }

    const clickedToggleButton = target.closest('.admin-toggle-button');
    if (clickedToggleButton) {
      return true;
    }

    const clickedInsideMenu = target.closest('.admin-menu');
    if (!clickedInsideMenu) {
      this.adminMenuVisible = false;
    }

    return false;
  }

  private handleNavbarClick(target: HTMLElement): void {
    const documentRef = this.getDocumentRef();
    if (documentRef !== undefined) {
      const navbarCollapse = documentRef.querySelector('.navbar-collapse.show');
      if (!navbarCollapse) {
        return;
      }

      if (this.shouldSkipNavbarClose(target)) {
        return;
      }

      const clickedInsideNavbar = target.closest('.navbar');
      const clickedNavLink = target.closest('.nav-link:not(.dropdown-toggle)'); // Exclude dropdown toggles
      const clickedButton = target.closest('.btn-login')
        || target.closest('.btn-my-classes')
        || target.closest('.btn-my-classes-mobile')
        || target.closest('.social-link');

      if (clickedNavLink || clickedButton || !clickedInsideNavbar) {
        this.collapseNavbar();
      }
    }
  }

  private shouldSkipNavbarClose(target: HTMLElement): boolean {
    const clickedToggler = target.closest('.navbar-toggler');
    if (clickedToggler) {
      return true;
    }

    const clickedDropdownToggle = target.closest('.dropdown-toggle');
    if (clickedDropdownToggle) {
      return true;
    }

    const clickedInsideDropdown = target.closest('.dropdown-menu');
    if (clickedInsideDropdown) {
      const clickedDropdownItem = target.closest('.dropdown-item');
      if (clickedDropdownItem) {
        this.collapseNavbar();
      }
      return true;
    }

    return false;
  }

  private collapseNavbar(): void {
    const navbarCollapse = document.querySelector('.navbar-collapse.show') as HTMLElement;
    if (navbarCollapse) {
      // Use Bootstrap's Collapse API for smooth animation
      const bsCollapse = (globalThis as any).bootstrap?.Collapse?.getInstance(navbarCollapse);
      if (bsCollapse) {
        bsCollapse.hide();
      } else {
        // Fallback: manually remove class with transition
        navbarCollapse.classList.remove('show');
      }
    }
  }

  cerrarSesion(): void {
    if (!this.usuarioLogueado) {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión.',
        icon: 'warning',
        timer: 2000,
      });
      return;
    }

    this.authService.logout();

    // Create temporary subscription for logout confirmation
    const subscription = this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      if (!estado) {
        subscription.unsubscribe();

        Swal.fire({
          title: 'Sesión cerrada con éxito',
          text: '¡Hasta la próxima!',
          icon: 'success',
          timer: 2000,
        });

        this.adminMenuVisible = false;
        this.router.navigate(['/inicio']);
      }
    });
  }

  toggleSpotifyWidget(): void {
    this.spotifyWidgetVisible = !this.spotifyWidgetVisible;
    const windowRef = this.getWindowRef();
    if (windowRef !== undefined) {
      windowRef.localStorage.setItem(this.spotifyVisibleStorageKey, String(this.spotifyWidgetVisible));
    }
  }

  guardarSpotifyUrl(): void {
    const url = this.spotifyUrlInput.trim();
    const windowRef = this.getWindowRef();
    if (!url) {
      this.spotifyError = '';
      this.spotifyEmbedUrl = null;
      this.spotifyOpenUrl = null;
      if (windowRef !== undefined) {
        windowRef.localStorage.removeItem(this.spotifyUrlStorageKey);
      }
      this.guardarSpotifyUrlServidor(null);
      return;
    }

    const embedUrl = this.construirSpotifyEmbedUrl(url);
    const openUrl = this.construirSpotifyOpenUrl(url);
    if (!embedUrl || !openUrl) {
      this.spotifyError = 'Pega un enlace válido de Spotify (track, playlist, álbum, artista, show o episodio).';
      return;
    }
    this.spotifyError = '';
    this.spotifyEmbedUrl = this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
    this.spotifyOpenUrl = openUrl;
    if (windowRef !== undefined) {
      windowRef.localStorage.setItem(this.spotifyUrlStorageKey, url);
    }
    this.guardarSpotifyUrlServidor(url);
  }

  abrirSpotify(): void {
    const windowRef = this.getWindowRef();
    if (this.spotifyOpenUrl && windowRef !== undefined) {
      windowRef.open(this.spotifyOpenUrl, '_blank', 'noopener');
    }
  }

  private cargarSpotifyConfig(): void {
    const windowRef = this.getWindowRef();
    if (windowRef === undefined) {
      return;
    }

    this.cargarSpotifyUrlLocal(windowRef);
    this.cargarSpotifyVisibilidadLocal(windowRef);
    this.cargarSpotifySizeLocal(windowRef);
  }

  private cargarSpotifyUrlLocal(windowRef: Window): void {
    const savedUrl = windowRef.localStorage.getItem(this.spotifyUrlStorageKey);
    if (savedUrl) {
      this.aplicarSpotifyUrl(savedUrl);
    }
  }

  private cargarSpotifyVisibilidadLocal(windowRef: Window): void {
    const savedVisible = windowRef.localStorage.getItem(this.spotifyVisibleStorageKey);
    if (savedVisible !== null) {
      this.spotifyWidgetVisible = savedVisible === 'true';
    }
  }

  private cargarSpotifySizeLocal(windowRef: Window): void {
    const savedSize = windowRef.localStorage.getItem(this.spotifySizeStorageKey);
    if (!savedSize) {
      return;
    }
    try {
      const parsed = JSON.parse(savedSize);
      this.aplicarSpotifySize(parsed);
    } catch {
      // Ignore invalid stored size
    }
  }

  private aplicarSpotifySize(parsed: { width?: unknown; height?: unknown }): void {
    const width = Number(parsed?.width);
    const height = Number(parsed?.height);
    if (Number.isFinite(width)) {
      this.spotifyWidgetWidth = width;
    }
    if (Number.isFinite(height)) {
      this.spotifyWidgetHeight = height;
    }
  }

  private cargarSpotifyUrlServidor(): void {
    this.endpointsService.obtenerSpotifyUrl().subscribe({
      next: (url) => {
        if (url) {
          this.aplicarSpotifyUrl(url);
          const windowRef = this.getWindowRef();
          if (windowRef !== undefined) {
            windowRef.localStorage.setItem(this.spotifyUrlStorageKey, url);
          }
          return;
        }
        this.spotifyEmbedUrl = null;
        this.spotifyOpenUrl = null;
        this.spotifyUrlInput = '';
        const windowRef = this.getWindowRef();
        if (windowRef !== undefined) {
          windowRef.localStorage.removeItem(this.spotifyUrlStorageKey);
        }
      },
      error: () => {
        // Mantener el valor local si falla el backend
      },
    });
  }

  private guardarSpotifyUrlServidor(spotifyUrl: string | null): void {
    if (!this.isAdmin) {
      return;
    }
    this.endpointsService.actualizarSpotifyUrl(spotifyUrl).subscribe({
      error: () => {
        this.spotifyError = 'No se pudo guardar el enlace en el servidor.';
      },
    });
  }

  private aplicarSpotifyUrl(rawUrl: string): void {
    const url = rawUrl.trim();
    if (!url) {
      this.spotifyUrlInput = '';
      this.spotifyEmbedUrl = null;
      this.spotifyOpenUrl = null;
      return;
    }
    const embedUrl = this.construirSpotifyEmbedUrl(url);
    const openUrl = this.construirSpotifyOpenUrl(url);
    if (!embedUrl || !openUrl) {
      this.spotifyError = 'Pega un enlace válido de Spotify (track, playlist, álbum, artista, show o episodio).';
      return;
    }
    this.spotifyError = '';
    this.spotifyUrlInput = url;
    this.spotifyEmbedUrl = this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
    this.spotifyOpenUrl = openUrl;
  }

  private construirSpotifyEmbedUrl(rawUrl: string): string | null {
    if (!rawUrl) {
      return null;
    }

    if (rawUrl.includes('open.spotify.com/embed/')) {
      return this.normalizarSpotifyEmbedUrl(rawUrl);
    }

    if (rawUrl.startsWith('spotify:')) {
      const parts = rawUrl.split(':');
      if (parts.length >= 3) {
        const type = parts[1];
        const id = parts[2];
        return this.normalizarSpotifyEmbedUrl(`https://open.spotify.com/embed/${type}/${id}`);
      }
    }

    try {
      const parsed = new URL(rawUrl);
      if (!parsed.hostname.includes('spotify.com')) {
        return null;
      }

      const segments = parsed.pathname.split('/').filter(Boolean);
      if (segments.length < 2) {
        return null;
      }

      let type = segments[0];
      let id = segments[1];
      if (type.startsWith('intl-') && segments.length >= 3) {
        type = segments[1];
        id = segments[2];
      }

      const tiposValidos = new Set(['track', 'album', 'playlist', 'artist', 'show', 'episode']);
      if (!tiposValidos.has(type)) {
        return null;
      }

      return this.normalizarSpotifyEmbedUrl(`https://open.spotify.com/embed/${type}/${id}`);
    } catch {
      return null;
    }
  }

  private construirSpotifyOpenUrl(rawUrl: string): string | null {
    if (!rawUrl) {
      return null;
    }

    if (rawUrl.includes('open.spotify.com/embed/')) {
      return rawUrl.replace('/embed/', '/');
    }

    if (rawUrl.startsWith('spotify:')) {
      const parts = rawUrl.split(':');
      if (parts.length >= 3) {
        const type = parts[1];
        const id = parts[2];
        return `https://open.spotify.com/${type}/${id}`;
      }
    }

    try {
      const parsed = new URL(rawUrl);
      if (!parsed.hostname.includes('spotify.com')) {
        return null;
      }

      const segments = parsed.pathname.split('/').filter(Boolean);
      if (segments.length < 2) {
        return null;
      }

      let type = segments[0];
      let id = segments[1];
      if (type.startsWith('intl-') && segments.length >= 3) {
        type = segments[1];
        id = segments[2];
      }

      const tiposValidos = new Set(['track', 'album', 'playlist', 'artist', 'show', 'episode']);
      if (!tiposValidos.has(type)) {
        return null;
      }

      return `https://open.spotify.com/${type}/${id}`;
    } catch {
      return null;
    }
  }

  private normalizarSpotifyEmbedUrl(url: string): string {
    try {
      const parsed = new URL(url);
      if (!parsed.searchParams.has('theme')) {
        parsed.searchParams.set('theme', '0');
      }
      return parsed.toString();
    } catch {
      return url;
    }
  }

  iniciarRedimension(event: PointerEvent, corner: ResizeCorner): void {
    const windowRef = this.getWindowRef();
    const widgetElement = this.spotifyWidgetRef?.nativeElement;
    if (windowRef !== undefined && widgetElement) {
      event.preventDefault();
      event.stopPropagation();

      const rect = widgetElement.getBoundingClientRect();
      this.resizeCorner = corner;
      this.resizeStartX = event.clientX;
      this.resizeStartY = event.clientY;
      this.resizeStartWidth = rect.width;
      this.resizeStartHeight = rect.height;

      this.resizeMoveHandler = (moveEvent: PointerEvent) => this.handleResizeMove(moveEvent);
      this.resizeUpHandler = () => this.detenerRedimension();
      windowRef.addEventListener('pointermove', this.resizeMoveHandler);
      windowRef.addEventListener('pointerup', this.resizeUpHandler);
      windowRef.addEventListener('pointercancel', this.resizeUpHandler);
    }
  }

  private handleResizeMove(event: PointerEvent): void {
    if (!this.resizeCorner) {
      return;
    }
    const deltaX = event.clientX - this.resizeStartX;
    const deltaY = event.clientY - this.resizeStartY;
    let width = this.resizeStartWidth;
    let height = this.resizeStartHeight;

    switch (this.resizeCorner) {
      case 'br':
        width += deltaX;
        height += deltaY;
        break;
      case 'tr':
        width += deltaX;
        height -= deltaY;
        break;
      case 'bl':
        width -= deltaX;
        height += deltaY;
        break;
      case 'tl':
        width -= deltaX;
        height -= deltaY;
        break;
    }

    const minWidth = 260;
    const minHeight = 240;
    const windowRef = this.getWindowRef();
    const maxWidth = (windowRef?.innerWidth ?? width + 24) - 24;
    const maxHeight = (windowRef?.innerHeight ?? height + 24) - 24;

    this.spotifyWidgetWidth = Math.round(Math.min(Math.max(width, minWidth), maxWidth));
    this.spotifyWidgetHeight = Math.round(Math.min(Math.max(height, minHeight), maxHeight));
  }

  private detenerRedimension(): void {
    const windowRef = this.getWindowRef();
    if (windowRef !== undefined) {
      if (this.resizeMoveHandler) {
        windowRef.removeEventListener('pointermove', this.resizeMoveHandler);
      }
      if (this.resizeUpHandler) {
        windowRef.removeEventListener('pointerup', this.resizeUpHandler);
        windowRef.removeEventListener('pointercancel', this.resizeUpHandler);
      }
    }
    this.resizeMoveHandler = undefined;
    this.resizeUpHandler = undefined;
    this.resizeCorner = null;

    if (windowRef !== undefined) {
      const payload = JSON.stringify({
        width: this.spotifyWidgetWidth,
        height: this.spotifyWidgetHeight,
      });
      windowRef.localStorage.setItem(this.spotifySizeStorageKey, payload);
    }
  }
}
