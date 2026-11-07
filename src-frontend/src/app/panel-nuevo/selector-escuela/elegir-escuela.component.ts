import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, filter, map, of, take } from 'rxjs';

import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { EntrandoEscuelaComponent } from './entrando-escuela.component';
import { LogoEscuelaComponent } from './logo-escuela.component';
import {
  EscuelaUsuario,
  SelectorEscuelaService,
  debeElegirTrasEntrar,
  metaEscuela,
  ordenTrasEntrar,
} from './selector-escuela.service';

/** «Ana López» → «AL» (como el avatar de la cuenta en la cabecera del panel). */
function inicialesPersona(nombre: string): string {
  return nombre
    .split(/\s+/)
    .map((p) => p.replace(/[^\p{L}]/gu, ''))
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

/**
 * «Tras entrar» (boceto del selector de escuela, lugar=tras): después del login con contraseña, a pantalla completa y
 * solo con dos escuelas o más ({@link debeElegirTrasEntrar}); si no toca o no se pueden leer las escuelas, sigue al
 * destino de siempre sin pintar nada.
 *
 * - Primero la de «La última vez» (la activa: el login ya entró en ella) y luego las demás en el orden de la API.
 * - «La última vez» entra sin pedir nada a la API. Otra: «Entrando en …» y `SelectorEscuelaService.cambiar`, que
 *   recarga la app en la escuela nueva. Si falla, se queda aquí con el error y «Reintentar».
 * - Tapa la cabecera de la web (va fija sobre todo, como el paso del boceto) con los tokens por defecto del panel.
 */
@Component({
  selector: 'app-elegir-escuela',
  standalone: true,
  imports: [LogoEscuelaComponent, EntrandoEscuelaComponent],
  templateUrl: './elegir-escuela.component.html',
  styleUrl: './elegir-escuela.component.scss',
})
export class ElegirEscuelaComponent implements OnInit {
  private readonly selector = inject(SelectorEscuelaService);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  /** null mientras llegan (o si no toca elegir: entonces se sale). */
  readonly escuelas = signal<EscuelaUsuario[] | null>(null);
  readonly entrando = signal<EscuelaUsuario | null>(null);
  /** La escuela cuyo cambio falló: su «Reintentar». */
  readonly fallida = signal<EscuelaUsuario | null>(null);
  readonly meta = metaEscuela;

  private readonly nombreSesion = toSignal(
    inject(PermisosSesionService)
      .obtener()
      .pipe(
        map((p) => p?.nombreVisible ?? null),
        catchError(() => of(null))
      ),
    { initialValue: null }
  );
  private readonly usuario = toSignal(this.auth.usernameCambio, { initialValue: null });
  readonly nombre = computed(() => this.nombreSesion() || this.usuario() || '');
  readonly iniciales = computed(() => inicialesPersona(this.nombre()));

  ngOnInit(): void {
    this.selector
      .organizaciones()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (escuelas) => {
          if (debeElegirTrasEntrar(escuelas)) {
            this.escuelas.set(ordenTrasEntrar(escuelas));
          } else {
            this.seguir();
          }
        },
        error: () => this.seguir(),
      });
  }

  elegir(escuela: EscuelaUsuario): void {
    if (this.entrando() || this.selector.ocupado) {
      return;
    }
    this.fallida.set(null);
    if (escuela.activa) {
      // Ya es la escuela de la sesión: se entra sin pedir nada.
      this.seguir();
      return;
    }
    this.entrando.set(escuela);
    this.selector.cambiar(escuela.id).subscribe({
      error: () => {
        this.entrando.set(null);
        this.fallida.set(escuela);
        this.cdr.detectChanges();
        this.host.nativeElement.querySelector<HTMLButtonElement>('.pn-paso__reintentar')?.focus();
      },
    });
  }

  reintentar(): void {
    const escuela = this.fallida();
    if (escuela) {
      this.elegir(escuela);
    }
  }

  /** Flechas arriba y abajo (con vuelta), Inicio y Fin entre las escuelas. */
  tecla(evento: KeyboardEvent): void {
    const opciones = Array.from(this.host.nativeElement.querySelectorAll<HTMLButtonElement>('.pn-paso__opcion'));
    const i = opciones.indexOf(evento.target as HTMLButtonElement);
    const destino: Record<string, number> = {
      ArrowDown: i + 1,
      ArrowUp: i - 1,
      Home: 0,
      End: opciones.length - 1,
    };
    if (i < 0 || !(evento.key in destino)) {
      return;
    }
    evento.preventDefault();
    opciones[(destino[evento.key] + opciones.length) % opciones.length].focus();
  }

  cerrarSesion(): void {
    this.auth.logout();
    this.auth.usuarioLogueadoCambio
      .pipe(
        filter((logueado) => !logueado),
        take(1),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => this.router.navigate(['/']));
  }

  /** Al destino de siempre de la sesión (la escuela en la que ya entró el login). */
  private seguir(): void {
    this.auth
      .resolverRutaInicio()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((ruta) => this.router.navigate([ruta], { replaceUrl: true }));
  }
}
