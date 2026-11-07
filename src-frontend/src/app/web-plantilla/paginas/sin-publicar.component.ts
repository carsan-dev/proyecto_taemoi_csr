import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import {
  EscuelaPublica,
  MarcaEscuelaLoginService,
  inicialesEscuela,
} from '../../componentes/vistas/vista-login/marca-escuela-login.service';
import { BLANCO, COLOR_POR_DEFECTO, contraste, esColorHex, hexARgb, mezclar, oscurecer, rgbAHex } from '../comun/colores';
import { RELOJ_WEB } from '../comun/reloj';

/** Tinta del boceto para el texto sobre el color de la escuela cuando el blanco no llega a 4,5:1. */
const TINTA = '#1A2230';

type Marca =
  | { estado: 'cargando' }
  | { estado: 'error' }
  | { estado: 'lista'; nombre: string; iniciales: string; logoUrl: string | null; fondo: string; sobre: string; hover: string };

/**
 * «Web aún no publicada» (boceto `fase4-web/boceto-login-404-sin-publicar.html`, sección 3; §9 n.º 10): el host de una
 * escuela sin web publicada enseña esta página en cualquier ruta de la plantilla, sin tema: nombre (o logo), un aviso
 * y «Entrar» al login, en el color de la escuela. La marca sale de `GET /api/publico/escuela` (la misma que el login),
 * porque `GET /api/publico/web` responde 404. Se carga perezosa (`@defer`) solo cuando hace falta.
 *
 * Mientras llega la marca no pinta el nombre; si falla, el aviso y «Entrar» siguen (en el color por defecto).
 */
@Component({
  selector: 'app-web-sin-publicar',
  standalone: true,
  imports: [RouterLink],
  template: `
    @let m = marca();
    <div class="sp" [attr.data-logo]="m.estado === 'lista' && m.logoUrl ? 'subido' : 'ninguno'" [style]="colores()">
      <main class="sp-centro">
        @if (m.estado === 'lista') {
          <p class="sp-marca">
            @if (m.logoUrl) {
              <span class="sp-mono"><img [src]="m.logoUrl" width="56" height="56" [alt]="'Logo de ' + m.nombre"></span>
            } @else {
              <span class="sp-mono" aria-hidden="true">{{ m.iniciales }}</span>
            }
            <span class="sp-nombre">{{ m.nombre }}</span>
          </p>
        }
        <h1>Esta web todavía no está publicada</h1>
        <p class="sp-texto">La escuela la está preparando. Si ya eres alumno, familia o del equipo, entra con tu cuenta.</p>
        <a class="sp-entrar" routerLink="/login">Entrar <i class="bi bi-arrow-right" aria-hidden="true"></i></a>
      </main>
      @if (m.estado === 'lista') {
        <footer class="sp-pie"><p>© {{ anio }} {{ m.nombre }}</p></footer>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .sp {
      --sp-fondo: #0B5CAD; --sp-sobre: #FFFFFF; --sp-hover: #094C90;
      --fondo: #F5F5F2; --tinta: #1A2230; --tinta-2: #5D6573; --linea: #E4E4DF;
      display: flex; flex-direction: column; min-height: 100vh; min-height: 100svh;
      background: var(--fondo); color: var(--tinta); font: 400 1rem/1.55 Montserrat, system-ui, sans-serif;
      border-top: 6px solid var(--sp-fondo);
    }
    .sp * { font-family: inherit; }
    .sp ::selection { background: var(--tinta); color: #FFFFFF; }
    .sp p, .sp h1 { margin: 0; }
    .sp-centro {
      flex: 1; display: flex; flex-direction: column; justify-content: center;
      width: 100%; max-width: 36rem; margin: 0 auto; padding: clamp(40px, 10vh, 96px) 24px;
    }
    .sp .sp-marca { display: flex; align-items: center; gap: 14px; margin: 0 0 clamp(32px, 6vh, 56px); }
    .sp-mono {
      flex: none; display: grid; place-items: center; width: 56px; height: 56px; border-radius: 50%;
      background: var(--sp-fondo); color: var(--sp-sobre); font: 800 1.0625rem/1 Montserrat, system-ui, sans-serif; letter-spacing: .04em;
    }
    .sp-mono img { display: block; width: 56px; height: 56px; object-fit: contain; }
    .sp[data-logo="subido"] .sp-mono { background: none; }
    .sp-nombre { font: 700 1.1875rem/1.2 Montserrat, system-ui, sans-serif; }
    .sp h1 {
      font: 700 clamp(1.875rem, 1.2rem + 2.6vw, 2.625rem)/1.12 Montserrat, system-ui, sans-serif; letter-spacing: -.015em;
      text-wrap: balance; color: var(--tinta);
    }
    .sp .sp-texto { margin: 16px 0 0; max-width: 40ch; color: var(--tinta-2); font-size: 1.0625rem; }
    .sp-entrar {
      align-self: flex-start; display: inline-flex; align-items: center; gap: 10px; min-height: 48px; margin-top: 28px; padding: 0 26px;
      background: var(--sp-fondo); color: var(--sp-sobre); border-radius: 8px; text-decoration: none;
      font: 600 1rem/1 Montserrat, system-ui, sans-serif; transition: background-color 150ms ease-out;
    }
    .sp-entrar:hover { background: var(--sp-hover); color: var(--sp-sobre); }
    .sp-entrar:focus-visible { outline: 3px solid var(--tinta); outline-offset: 3px; }
    .sp-pie { padding: 18px 24px 24px; border-top: 1px solid var(--linea); text-align: center; color: var(--tinta-2); font-size: .8125rem; }
    @media (prefers-reduced-motion: reduce) { .sp-entrar { transition: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SinPublicarComponent {
  private readonly servicio = inject(MarcaEscuelaLoginService);

  protected readonly anio = inject(RELOJ_WEB)().getFullYear();
  protected readonly marca = toSignal(
    this.servicio.escuela().pipe(
      map((escuela): Marca => this.marcaDe(escuela)),
      catchError(() => of<Marca>({ estado: 'error' })),
    ),
    { initialValue: { estado: 'cargando' } as Marca },
  );
  protected readonly colores = computed(() => {
    const m = this.marca();
    const { fondo, sobre, hover } = m.estado === 'lista' ? m : colorDe(COLOR_POR_DEFECTO, null);
    return { '--sp-fondo': fondo, '--sp-sobre': sobre, '--sp-hover': hover };
  });

  private marcaDe(escuela: EscuelaPublica): Marca {
    const nombre = escuela?.nombre?.trim() ?? '';
    if (!nombre) {
      return { estado: 'error' };
    }
    const marca = this.servicio.marcaDe(escuela);
    const logoUrl = marca.tipo === 'subido' ? marca.url : null;
    return {
      estado: 'lista',
      nombre,
      iniciales: inicialesEscuela(nombre),
      logoUrl,
      ...colorDe(escuela.tema?.primario ?? COLOR_POR_DEFECTO, escuela.tema?.sobrePrimario ?? null),
    };
  }
}

/**
 * Fondo, texto y hover del botón (boceto: `superficie` y `hover`). Con el par de la API (`primario` y `sobrePrimario`,
 * ya con 4,5:1), tal cual; sin él, blanco o tinta según el contraste, y si ninguno llega, el fondo se oscurece.
 */
function colorDe(base: string, sobreApi: string | null): { fondo: string; sobre: string; hover: string } {
  const color = rgbAHex(hexARgb(esColorHex(base) ? base : COLOR_POR_DEFECTO));
  let fondo = color;
  let sobre: string;
  if (sobreApi && esColorHex(sobreApi)) {
    sobre = rgbAHex(hexARgb(sobreApi));
  } else if (contraste(BLANCO, color) >= 4.5) {
    sobre = BLANCO;
  } else if (contraste(TINTA, color) >= 4.5) {
    sobre = TINTA;
  } else {
    fondo = oscurecer(color, 4.5, [BLANCO]);
    sobre = BLANCO;
  }
  const hover = sobre === BLANCO ? mezclar(fondo, '#000000', 0.16) : mezclar(fondo, '#FFFFFF', 0.22);
  return { fondo, sobre, hover };
}
