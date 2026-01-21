import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LoginInterface } from '../../interfaces/login-interface';
import { environment } from '../../../environments/environment';
import { BehaviorSubject } from 'rxjs/internal/BehaviorSubject';
import { Observable } from 'rxjs/internal/Observable';
import { of } from 'rxjs/internal/observable/of';
import { throwError } from 'rxjs/internal/observable/throwError';
import { switchMap } from 'rxjs/internal/operators/switchMap';
import { tap } from 'rxjs/internal/operators/tap';
import { catchError } from 'rxjs/internal/operators/catchError';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private readonly urlBase = environment.apiUrl + '/auth';

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

  constructor(private readonly http: HttpClient) {
    this.verificarEstadoAutenticacion();
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<string[]> {
    this.rolesCargados = false;
    return this.http
      .post<any>(`${this.urlBase}/signin`, credenciales, {
        withCredentials: true,
      })
      .pipe(
        switchMap(() => {
          const email = credenciales.email;
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
    return this.http
      .get<string[]>(`${this.urlBase}/roles`, { withCredentials: true })
      .pipe(
        tap((roles) => {
          this.rolesSubject.next(roles);
          this.rolesCargados = true; // Marcar que los roles ya se han cargado
        }),
        catchError((error) => {
          this.rolesSubject.next([]);
          this.rolesCargados = false;
          return of([]);
        })
      );
  }

  getRoles(): Observable<string[]> {
    return this.rolesCambio;
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

  actualizarEstadoLogueado(estado: boolean) {
    this.usuarioLogueadoSubject.next(estado);
  }

  obtenerNombreUsuario(): Observable<string | null> {
    return this.usernameCambio;
  }

  private extraerNombreUsuario(email: string): string {
    return email.substring(0, email.indexOf('@'));
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
  obtenerTodosLosAlumnos(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/user/alumnos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }
}
