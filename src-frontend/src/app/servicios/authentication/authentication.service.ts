import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { LoginInterface } from '../../interfaces/login-interface';
import { environment } from '../../../environments/environment';
import { BehaviorSubject, catchError, defer, finalize, map, Observable, of, retry, shareReplay, switchMap, take, tap, throwError, timer } from 'rxjs';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PermisosSesionService } from './permisos-sesion.service';

export type PortalPreferido = 'admin' | 'user';

/** `POST /api/auth/invitacion/datos` (pieza E8-4): lo que enseña «Crea tu contraseña». */
export interface DatosInvitacion {
  email: string;
  nombre: string;
  escuela: string;
}

/**
 * Sesión «solo profesor» (pieza 6b-3b): ROLE_PROFESOR sin gestión ni familia (puede ser también responsable de
 * sede). El servidor solo le deja «Mi caja» y lo imprescindible de la sesión: nada de gestión ni del portal familiar.
 */
export function esSoloProfesor(roles: readonly string[]): boolean {
  return (
    roles.includes('ROLE_PROFESOR') &&
    !roles.includes('ROLE_ADMIN') &&
    !roles.includes('ROLE_MANAGER') &&
    !roles.includes('ROLE_USER')
  );
}

/**
 * Reintentos de `GET /auth/roles` cuando el fallo no es definitivo (red, 408, 429, 5xx): esperas de 500 ms, 1 s
 * y 2 s. Al reabrir la web, el navegador puede cortar la primera petición sin que llegue al servidor (estado 0);
 * sin reintento, `roleGuard` veía `[]` y mandaba a la portada. Sin sesión (401/403) no se reintenta.
 */
const REINTENTOS_ROLES = 3;
const ESPERA_INICIAL_ROLES_MS = 500;

function esFalloTransitorio(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }
  const { status } = error;
  return status === 0 || status === 408 || status === 429 || (status >= 500 && status < 600);
}

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private readonly urlBase = environment.apiUrl + '/auth';
  private readonly vistaPreferidaStorageKey = 'taemoi_vista_preferida';

  private readonly usuarioLogueadoSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  usuarioLogueadoCambio: Observable<boolean> =
    this.usuarioLogueadoSubject.asObservable();

  private readonly rolesSubject: BehaviorSubject<string[]> =
    new BehaviorSubject<string[]>([]);
  rolesCambio: Observable<string[]> = this.rolesSubject.asObservable();

  private readonly usernameSubject: BehaviorSubject<string | null> =
    new BehaviorSubject<string | null>(null);
  usernameCambio: Observable<string | null> =
    this.usernameSubject.asObservable();

  private readonly emailSubject: BehaviorSubject<string | null> =
    new BehaviorSubject<string | null>(null);
  emailCambio: Observable<string | null> = this.emailSubject.asObservable();

  private readonly isAdminSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isAdminCambio: Observable<boolean> = this.isAdminSubject.asObservable();

  private readonly isManagerSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isManagerCambio: Observable<boolean> = this.isManagerSubject.asObservable();

  private readonly isUserSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isUserCambio: Observable<boolean> = this.isUserSubject.asObservable();

  private readonly alumnoIdSubject: BehaviorSubject<number | null> =
    new BehaviorSubject<number | null>(null);
  alumnoIdCambio: Observable<number | null> =
    this.alumnoIdSubject.asObservable();

  private rolesCargados: boolean = false;
  /** Petición de /roles en curso: la comparten quienes la piden a la vez (login, cabecera, guards). */
  private rolesPeticion$: Observable<string[]> | null = null;
  private alumnosAsociadosCache: any[] | null = null;
  private alumnosAsociadosRequest$: Observable<any[]> | null = null;
  private sondeoPlataforma$: Observable<boolean> | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly permisos: PermisosSesionService
  ) {
    this.verificarEstadoAutenticacion();
  }

  /**
   * true si la sesión es administradora de plataforma. Esa autoridad no sale en /roles (una sesión solo de
   * plataforma recibe 403 allí), así que se pregunta a /auth/plataforma, que responde 200 a toda sesión y no deja
   * un 403 en el registro. Vive aquí, y no en PlataformaService, para que la cabecera y el guard no añadan un módulo
   * al bundle inicial.
   */
  esAdminPlataforma(): Observable<boolean> {
    if (!this.sondeoPlataforma$) {
      this.sondeoPlataforma$ = this.http
        .get<{ adminPlataforma?: boolean } | null>(`${this.urlBase}/plataforma`, {
          // withCredentials lo añade credentialsInterceptor.
          headers: { 'X-Skip-Loading': 'true' },
          context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
        })
        .pipe(
          map((respuesta) => respuesta?.adminPlataforma === true),
          catchError((error: HttpErrorResponse) => {
            // Solo 401/403 es definitivo; un fallo de red o 5xx se reintenta en la próxima llamada.
            if (error.status !== 401 && error.status !== 403) {
              this.sondeoPlataforma$ = null;
            }
            return of(false);
          }),
          shareReplay(1)
        );
    }
    return this.sondeoPlataforma$;
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<string[]> {
    return this.abrirSesion(
      () =>
        this.http.post<any>(`${this.urlBase}/signin`, credenciales, {
          withCredentials: true,
        }),
      credenciales.email
    );
  }

  /** Datos que enseña «Crea tu contraseña» antes de escribirla (pieza E8-6). El token va en el cuerpo. */
  datosInvitacion(token: string): Observable<DatosInvitacion> {
    return this.http.post<DatosInvitacion>(
      `${this.urlBase}/invitacion/datos`,
      { token },
      { context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) }
    );
  }

  /**
   * Crea la contraseña de una invitación y abre la sesión (cookie del backend) con el mismo refresco que `login()`:
   * roles, permisos y sondeo de plataforma nuevos. Errores (400 enlace, 403 sin escuela) tratados en la página.
   */
  aceptarInvitacion(token: string, nuevaContrasena: string, email: string): Observable<string[]> {
    return this.abrirSesion(
      () =>
        this.http.post<any>(
          `${this.urlBase}/invitacion/aceptar`,
          { token, nuevaContrasena },
          { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) }
        ),
      email
    );
  }

  private abrirSesion(peticion: () => Observable<unknown>, email: string): Observable<string[]> {
    this.rolesCargados = false;
    this.rolesPeticion$ = null;
    this.permisos.reiniciar();
    // Cambiar de usuario sin cerrar sesión no debe reutilizar el sondeo del anterior.
    this.sondeoPlataforma$ = null;
    return peticion().pipe(
      switchMap(() => {
        const username = this.extraerNombreUsuario(email);
        this.actualizarEstadoLogueado(true);
        this.usernameSubject.next(username); // Actualizar nombre de usuario
        this.emailSubject.next(email); // Actualizar email

        return this.obtenerRoles().pipe(
          tap(() => {
            // Si esto fue exitoso y los roles fueron obtenidos, confirmamos el login completo
            return this.usuarioLogueadoSubject.next(true);
          })
        );
      }),
      catchError(this.manejarError)
    );
  }

  solicitarResetContrasena(email: string): Observable<void> {
    return this.http
      .post<void>(`${this.urlBase}/password/forgot`, { email }, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  resetearContrasena(token: string, nuevaContrasena: string): Observable<void> {
    return this.http
      .post<void>(`${this.urlBase}/password/reset`, { token, nuevaContrasena }, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  solicitarRegistro(payload: { email: string; fechaNacimiento: string; contrasena: string }): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/register/request`, payload, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  confirmarRegistro(token: string): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/register/confirm`, { token }, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  logout(): void {
    this.http
      .post(`${this.urlBase}/logout`, {}, { withCredentials: true })
      .subscribe({
        next: () => {
          this.limpiarEstadoLocal();
        },
        error: (error) => {
          console.error('Error al cerrar sesión', error);
          // Limpiar estado local aunque falle el logout del backend
          this.limpiarEstadoLocal();
        },
      });
  }

  /**
   * Limpia el estado local de autenticación sin llamar al backend.
   * Útil cuando la sesión ha expirado y no podemos llamar al endpoint de logout.
   */
  limpiarEstadoLocal(): void {
    this.actualizarEstadoLogueado(false);
    this.rolesSubject.next([]);
    this.usernameSubject.next(null);
    this.emailSubject.next(null);
    this.alumnoIdSubject.next(null);
    this.isAdminSubject.next(false);
    this.isManagerSubject.next(false);
    this.isUserSubject.next(false);
    this.rolesCargados = false;
    this.rolesPeticion$ = null;
    this.permisos.reiniciar();
    this.alumnosAsociadosCache = null;
    this.alumnosAsociadosRequest$ = null;

    // Limpiar sessionStorage
    if (globalThis.window?.sessionStorage) {
      globalThis.window.sessionStorage.clear();
    }

    // Intentar eliminar la cookie jwt (aunque sea HTTP-only, el navegador puede ignorarlo)
    // La cookie se eliminará correctamente cuando el backend responda al logout
    if (globalThis.document) {
      globalThis.document.cookie = 'jwt=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    }
  }

  /**
   * Fuerza el cierre de sesión cuando la sesión ha expirado.
   * Llama al backend para limpiar la cookie y limpia el estado local.
   * Devuelve una promesa que se resuelve cuando el proceso termina.
   */
  forzarCierreSesion(): Promise<void> {
    // Primero limpiar el estado local
    this.limpiarEstadoLocal();

    // Llamar al backend para limpiar la cookie HTTP-only
    return new Promise((resolve) => {
      this.http
        .post(`${this.urlBase}/logout`, {}, { withCredentials: true })
        .subscribe({
          next: () => {
            resolve();
          },
          error: () => {
            // Aunque falle, el estado local ya está limpio
            resolve();
          },
        });
    });
  }

  obtenerRoles(): Observable<string[]> {
    if (this.rolesPeticion$) {
      return this.rolesPeticion$;
    }
    let intento = 0;
    const peticion$: Observable<string[]> = defer(() => {
      intento++;
      // Los intentos que aún pueden reintentarse no abren el aviso global: si fallan todos, avisa solo el último.
      const context = intento <= REINTENTOS_ROLES ? new HttpContext().set(ERROR_HANDLED_LOCALLY, true) : undefined;
      return this.http.get<string[]>(`${this.urlBase}/roles`, { withCredentials: true, context });
    })
      .pipe(
        retry({
          count: REINTENTOS_ROLES,
          delay: (error, reintento) =>
            esFalloTransitorio(error)
              ? timer(ESPERA_INICIAL_ROLES_MS * 2 ** (reintento - 1))
              : throwError(() => error),
        }),
        tap((roles) => {
          // Una carga que siguió reintentando tras abrir o cerrar sesión ya no es la vigente: no pisa sus roles.
          if (this.rolesPeticion$ !== peticion$) {
            return;
          }
          this.rolesSubject.next(roles);
          this.rolesCargados = true; // Marcar que los roles ya se han cargado
        }),
        catchError((error) => {
          if (this.rolesPeticion$ === peticion$) {
            this.rolesSubject.next([]);
            this.rolesCargados = false;
          }
          return of([]);
        }),
        finalize(() => {
          if (this.rolesPeticion$ === peticion$) {
            this.rolesPeticion$ = null;
          }
        }),
        shareReplay(1)
      );
    this.rolesPeticion$ = peticion$;
    return peticion$;
  }

  getRoles(): Observable<string[]> {
    return this.rolesCambio;
  }

  resolverRolesDisponibles(): Observable<string[]> {
    if (this.rolesEstanCargados()) {
      return this.rolesCambio.pipe(take(1));
    }

    return this.obtenerRoles().pipe(take(1));
  }

  // Nuevo método para obtener los roles actuales
  getRolesActuales(): string[] {
    return this.rolesSubject.value;
  }

  obtenerUsuarioAutenticado(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/user`, { withCredentials: true })
      .pipe(
        tap((usuario) => {
          if (usuario && usuario.alumnoDTO) {
            this.guardarAlumnoId(usuario.alumnoDTO.id); // Guardamos el alumnoId
          }
        }),
        catchError(this.manejarError)
      );
  }

  obtenerRecordatorioRachaEmail(): Observable<{ habilitado: boolean }> {
    return this.http
      .get<{ habilitado: boolean }>(`${this.urlBase}/user/recordatorios-racha-email`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarRecordatorioRachaEmail(habilitado: boolean): Observable<{ habilitado: boolean }> {
    return this.http
      .put<{ habilitado: boolean }>(
        `${this.urlBase}/user/recordatorios-racha-email`,
        { habilitado },
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  tieneRolAdmin(): boolean {
    const roles = this.rolesSubject.value;
    return roles.includes('ROLE_ADMIN');
  }

  tieneRolManager(): boolean {
    const roles = this.rolesSubject.value;
    return roles.includes('ROLE_MANAGER');
  }

  tieneRolUser(): boolean {
    const roles = this.rolesSubject.value;
    return roles.includes('ROLE_USER');
  }

  tieneAccesoAdmin(roles: string[] = this.rolesSubject.value): boolean {
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
  }

  tieneAccesoUser(roles: string[] = this.rolesSubject.value): boolean {
    return roles.includes('ROLE_USER');
  }

  tieneAccesoDual(roles: string[] = this.rolesSubject.value): boolean {
    return this.tieneAccesoAdmin(roles) && this.tieneAccesoUser(roles);
  }

  tieneRolProfesor(roles: string[] = this.rolesSubject.value): boolean {
    return roles.includes('ROLE_PROFESOR');
  }

  /** Ver {@link esSoloProfesor}. */
  esSoloProfesor(roles: string[] = this.rolesSubject.value): boolean {
    return esSoloProfesor(roles);
  }

  guardarVistaPreferida(portal: PortalPreferido): void {
    const storage = this.obtenerLocalStorage();
    storage?.setItem(this.vistaPreferidaStorageKey, portal);
  }

  obtenerVistaPreferida(): PortalPreferido | null {
    const storage = this.obtenerLocalStorage();
    const valor = storage?.getItem(this.vistaPreferidaStorageKey);
    return this.esPortalPreferido(valor) ? valor : null;
  }

  marcarVistaPreferidaSegunRuta(url: string): void {
    const rutaNormalizada = this.normalizarRuta(url);
    if (rutaNormalizada.startsWith('/adminpage')) {
      this.guardarVistaPreferida('admin');
      return;
    }
    if (rutaNormalizada.startsWith('/userpage')) {
      this.guardarVistaPreferida('user');
    }
  }

  resolverRutaInicioPorRoles(roles: string[] = this.rolesSubject.value): string {
    const tieneAccesoAdmin = this.tieneAccesoAdmin(roles);
    const tieneAccesoUser = this.tieneAccesoUser(roles);

    if (tieneAccesoAdmin && tieneAccesoUser) {
      return this.obtenerVistaPreferida() === 'user' ? '/userpage' : '/adminpage';
    }
    if (tieneAccesoAdmin) {
      return '/adminpage';
    }
    if (tieneAccesoUser) {
      return '/userpage';
    }
    if (this.tieneRolProfesor(roles)) {
      return '/mis-grupos';
    }
    return '/';
  }

  actualizarEstadoLogueado(estado: boolean) {
    // Cualquier cambio de sesión (login o logout) invalida el sondeo de plataforma.
    if (estado !== this.usuarioLogueadoSubject.value) {
      this.sondeoPlataforma$ = null;
    }
    this.usuarioLogueadoSubject.next(estado);
  }

  obtenerNombreUsuario(): Observable<string | null> {
    return this.usernameCambio;
  }

  private extraerNombreUsuario(email: string): string {
    return email.substring(0, email.indexOf('@'));
  }

  private obtenerLocalStorage(): Storage | undefined {
    return globalThis.window?.localStorage;
  }

  private esPortalPreferido(valor: string | null | undefined): valor is PortalPreferido {
    return valor === 'admin' || valor === 'user';
  }

  private normalizarRuta(url: string): string {
    return (url || '').split('?')[0].split('#')[0];
  }

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }

  // Método para verificar si los roles ya están cargados
  rolesEstanCargados(): boolean {
    return this.rolesCargados;
  }

  // Método para guardar el alumnoId
  private guardarAlumnoId(alumnoId: number): void {
    this.alumnoIdSubject.next(alumnoId);
    if (globalThis.window?.sessionStorage) {
      globalThis.window.sessionStorage.setItem('alumnoId', alumnoId.toString()); // Guardar en sessionStorage
    }
  }

  // Método para obtener el alumnoId actual
  getAlumnoId(): number | null {
    const alumnoId = this.alumnoIdSubject.value;
    
    if (alumnoId) {
      return alumnoId;
    }
    if (globalThis.window?.sessionStorage) {
      const storedAlumnoId = globalThis.window.sessionStorage.getItem('alumnoId');
      return storedAlumnoId ? Number.parseInt(storedAlumnoId, 10) : null;
    }

    return null;
  }

  // Verificar el estado de autenticación al cargar el servicio
  verificarEstadoAutenticacion() {
    this.http
      .get<boolean>(`${this.urlBase}/auth-status`, { withCredentials: true })
      .pipe(
        tap((isAuthenticated) => {
          if (isAuthenticated) {
            this.actualizarEstadoLogueado(true);
            this.obtenerUsuarioAutenticado().subscribe({
              next: (usuario) => {
                const email = usuario.email;
                const username = this.extraerNombreUsuario(email);
                this.usernameSubject.next(username);
                this.emailSubject.next(email);
              },
              error: (error) => {
                console.error('Error al obtener usuario autenticado', error);
              },
            });

            this.obtenerRoles().subscribe();
          } else {
            this.actualizarEstadoLogueado(false);
            this.usernameSubject.next(null);
            this.emailSubject.next(null);
            this.rolesSubject.next([]);
            this.isAdminSubject.next(false);
            this.isManagerSubject.next(false);
            this.isUserSubject.next(false);
            this.rolesCargados = false;
          }
        }),
        catchError((error) => {
          console.error('Error al verificar estado de autenticación', error);
          this.actualizarEstadoLogueado(false);
          this.rolesCargados = false;
          return of(false);
        })
      )
      .subscribe();
  }

  // Obtener todos los alumnos asociados al email del usuario
  obtenerTodosLosAlumnos(forceRefresh: boolean = false): Observable<any[]> {
    if (forceRefresh) {
      this.alumnosAsociadosCache = null;
      this.alumnosAsociadosRequest$ = null;
    }

    if (this.alumnosAsociadosCache) {
      return of(this.alumnosAsociadosCache);
    }

    if (this.alumnosAsociadosRequest$) {
      return this.alumnosAsociadosRequest$;
    }

    const request$ = this.http
      .get<any[]>(`${this.urlBase}/user/alumnos`, { withCredentials: true })
      .pipe(
        tap((alumnos) => {
          this.alumnosAsociadosCache = Array.isArray(alumnos) ? alumnos : [];
        }),
        finalize(() => {
          this.alumnosAsociadosRequest$ = null;
        }),
        shareReplay(1),
        catchError((error) => {
          this.alumnosAsociadosCache = null;
          return this.manejarError(error);
        })
      );

    this.alumnosAsociadosRequest$ = request$;
    return request$;
  }
}
