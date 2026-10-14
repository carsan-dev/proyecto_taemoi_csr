import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, distinctUntilChanged, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import {
  Catalogo,
  CatalogoCategoria,
  CatalogoDisciplina,
  CatalogoGrado,
  CatalogoPublico,
  CatalogoTarifa,
  EstadoCatalogo,
} from './catalogo.models';
import {
  categoriasDeDisciplina,
  disciplinasActivasOrdenadas,
  gradosDeDisciplina,
  gradosSeleccionables,
  tarifasDeDisciplina,
} from './catalogo-reglas';
import { catalogoEsLegadoTaemoi } from './legado-taemoi';

/**
 * Catálogo de la organización activa (disciplinas, grados, categorías, tarifas).
 *
 * - Estado en un signal; las lecturas síncronas son `computed`/métodos sobre él.
 * - La petición en curso se comparte (`shareReplay`); se vacía al iniciar/cerrar sesión o cambiar de
 *   usuario, para que otra escuela u otro usuario nunca vea un catálogo anterior.
 * - D15: si la carga falla, `estado` pasa a `error` y las listas quedan vacías. Nunca hay respaldo a las
 *   constantes de Taemoi.
 */
@Injectable({
  providedIn: 'root',
})
export class CatalogoService {
  private readonly urlBase = `${environment.apiUrl}/catalogo`;

  private readonly estadoSignal = signal<EstadoCatalogo>({ estado: 'vacio' });
  private peticion$: Observable<Catalogo> | null = null;
  /** Se incrementa en cada invalidación: una respuesta de una generación anterior se descarta. */
  private generacion = 0;

  /** Estado de carga: `vacio` | `cargando` | `listo` | `error`. */
  readonly estado = this.estadoSignal.asReadonly();
  readonly catalogo = computed<Catalogo | null>(() => {
    const e = this.estadoSignal();
    return e.estado === 'listo' ? e.datos : null;
  });
  readonly cargado = computed(() => this.estadoSignal().estado === 'listo');
  readonly cargando = computed(() => this.estadoSignal().estado === 'cargando');
  readonly error = computed<unknown>(() => {
    const e = this.estadoSignal();
    return e.estado === 'error' ? e.error : null;
  });
  readonly hayError = computed(() => this.estadoSignal().estado === 'error');

  readonly esLegadoTaemoi = computed(() => catalogoEsLegadoTaemoi(this.catalogo()));
  readonly disciplinas = computed<CatalogoDisciplina[]>(() => this.catalogo()?.disciplinas ?? []);
  readonly disciplinasActivas = computed<CatalogoDisciplina[]>(() => disciplinasActivasOrdenadas(this.disciplinas()));
  readonly tarifas = computed<CatalogoTarifa[]>(() => this.catalogo()?.tarifas ?? []);

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthenticationService
  ) {
    this.vigilarSesion();
  }

  /**
   * Carga el catálogo (o devuelve el ya cargado). Las llamadas concurrentes comparten una sola petición.
   * Si falla, el estado pasa a `error` y el observable emite el error (el llamador decide si reintenta).
   */
  cargar(): Observable<Catalogo> {
    const actual = this.estadoSignal();
    if (actual.estado === 'listo') {
      return of(actual.datos);
    }
    if (this.peticion$) {
      return this.peticion$;
    }

    const generacion = this.generacion;
    this.estadoSignal.set({ estado: 'cargando' });

    const peticion$ = this.http
      .get<Catalogo>(this.urlBase, {
        withCredentials: true,
        // El fallo se presenta como estado de error, no como diálogo global.
        context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
      })
      .pipe(
        tap((datos) => {
          if (generacion === this.generacion) {
            this.estadoSignal.set({ estado: 'listo', datos });
          }
        }),
        catchError((error) => {
          if (generacion === this.generacion) {
            this.estadoSignal.set({ estado: 'error', error });
          }
          return throwError(() => error);
        }),
        finalize(() => {
          if (generacion === this.generacion) {
            this.peticion$ = null;
          }
        }),
        shareReplay(1)
      );

    this.peticion$ = peticion$;
    return peticion$;
  }

  /** Descarta lo cargado y vuelve a pedirlo. */
  recargar(): Observable<Catalogo> {
    this.invalidar();
    return this.cargar();
  }

  /** Vacía el catálogo y descarta la petición en curso (cambio de usuario, cierre de sesión o de escuela). */
  invalidar(): void {
    this.generacion++;
    this.peticion$ = null;
    this.estadoSignal.set({ estado: 'vacio' });
  }

  /**
   * Catálogo público (preinscripción, sin sesión). Es independiente del catálogo autenticado:
   * no toca el estado ni se guarda; cada llamada es una petición.
   */
  cargarPublico(): Observable<CatalogoPublico> {
    return this.http.get<CatalogoPublico>(`${this.urlBase}/publico`, {
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Lecturas síncronas (vacías mientras no haya catálogo: D15)
  // ---------------------------------------------------------------------------------------------

  disciplina(codigo: string | null | undefined): CatalogoDisciplina | undefined {
    return codigo ? this.disciplinas().find((d) => d.codigo === codigo) : undefined;
  }

  /** Nombre de la disciplina; el propio código si no está en el catálogo (inscripciones antiguas). */
  etiquetaDisciplina(codigo: string | null | undefined): string {
    return this.disciplina(codigo)?.nombre ?? codigo ?? '';
  }

  /** Grados del sistema de la disciplina, por `orden`. */
  grados(codigoDisciplina: string | null | undefined): CatalogoGrado[] {
    return gradosDeDisciplina(this.catalogo()?.sistemasGrados ?? [], this.disciplina(codigoDisciplina));
  }

  grado(codigoDisciplina: string | null | undefined, codigoGrado: string | null | undefined): CatalogoGrado | undefined {
    return codigoGrado ? this.grados(codigoDisciplina).find((g) => g.codigo === codigoGrado) : undefined;
  }

  gradosSeleccionables(codigoDisciplina: string | null | undefined, esMenor?: boolean): CatalogoGrado[] {
    return gradosSeleccionables(this.catalogo()?.sistemasGrados ?? [], this.disciplina(codigoDisciplina), esMenor);
  }

  tarifasDe(codigoDisciplina: string | null | undefined, incluirInactivas = false): CatalogoTarifa[] {
    return tarifasDeDisciplina(this.tarifas(), codigoDisciplina, incluirInactivas);
  }

  tarifa(codigo: string | null | undefined): CatalogoTarifa | undefined {
    return codigo ? this.tarifas().find((t) => t.codigo === codigo) : undefined;
  }

  categoriasDe(codigoDisciplina: string | null | undefined): CatalogoCategoria[] {
    return categoriasDeDisciplina(this.catalogo()?.categorias ?? [], codigoDisciplina);
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * Invalida cuando cambia la identidad de la sesión (inicio, cierre o cambio de usuario) y precarga al
   * iniciar. La identidad es el correo del usuario; mientras la sesión está abierta pero el correo aún
   * no ha llegado, solo se invalida (el correo llega justo después y dispara la precarga).
   */
  private vigilarSesion(): void {
    combineLatest([this.authService.usuarioLogueadoCambio, this.authService.emailCambio])
      .pipe(
        map(([logueado, email]) => (logueado ? (email ?? '') : null)),
        distinctUntilChanged(),
        takeUntilDestroyed()
      )
      .subscribe((identidad) => {
        this.invalidar();
        if (identidad) {
          this.cargar().subscribe({ error: () => undefined });
        }
      });
  }
}
