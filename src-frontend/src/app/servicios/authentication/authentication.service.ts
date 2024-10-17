import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { LoginInterface } from '../../interfaces/login-interface';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private urlBase = environment.apiUrl + '/auth';

  private usuarioLogueadoSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  usuarioLogueadoCambio: Observable<boolean> =
    this.usuarioLogueadoSubject.asObservable();

  private rolesSubject: BehaviorSubject<string[]> = new BehaviorSubject<
    string[]
  >([]);
  rolesCambio: Observable<string[]> = this.rolesSubject.asObservable();

  private usernameSubject: BehaviorSubject<string | null> = new BehaviorSubject<
    string | null
  >(null);
  usernameCambio: Observable<string | null> =
    this.usernameSubject.asObservable();

  private emailSubject: BehaviorSubject<string | null> = new BehaviorSubject<
    string | null
  >(null);
  emailCambio: Observable<string | null> = this.emailSubject.asObservable();

  private isAdminSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isAdminCambio: Observable<boolean> = this.isAdminSubject.asObservable();

  private isManagerSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isManagerCambio: Observable<boolean> = this.isManagerSubject.asObservable();

  private isUserSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  isUserCambio: Observable<boolean> = this.isUserSubject.asObservable();

  private rolesCargados: boolean = false;

  constructor(private http: HttpClient) {
    this.verificarEstadoAutenticacion();
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<string[]> {
    return this.http
      .post<any>(`${this.urlBase}/signin`, credenciales, {
        withCredentials: true,
      })
      .pipe(
        switchMap(() => {
          const email = credenciales.email;
          const username = this.extraerNombreUsuario(email);
          this.actualizarEstadoLogueado(true);
          this.usernameSubject.next(username);
          this.emailSubject.next(email);
          return this.obtenerRoles();
        }),
        catchError(this.manejarError)
      );
  }

  logout(): void {
    this.http
      .post(`${this.urlBase}/logout`, {}, { withCredentials: true })
      .subscribe({
        next: () => {
          this.actualizarEstadoLogueado(false);
          this.rolesSubject.next([]);
          this.usernameSubject.next(null);
          this.emailSubject.next(null);
          this.isAdminSubject.next(false);
          this.isManagerSubject.next(false);
          this.isUserSubject.next(false);
          this.rolesCargados = false;
        },
        error: (error) => {
          console.error('Error al cerrar sesión', error);
        },
      });
  }

  obtenerRoles(): Observable<string[]> {
    return this.http
      .get<string[]>(`${this.urlBase}/roles`, { withCredentials: true })
      .pipe(
        tap((roles) => {
          this.rolesSubject.next(roles);
          this.isAdminSubject.next(roles.includes('ROLE_ADMIN'));
          this.isManagerSubject.next(roles.includes('ROLE_MANAGER'));
          this.isUserSubject.next(roles.includes('ROLE_USER'));
          this.rolesCargados = true;
        }),
        catchError((error) => {
          console.error('Error al obtener roles', error);
          this.rolesSubject.next([]);
          this.isAdminSubject.next(false);
          this.isManagerSubject.next(false);
          this.isUserSubject.next(false);
          this.rolesCargados = true;
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

  actualizarEstadoLogueado(estado: boolean) {
    this.usuarioLogueadoSubject.next(estado);
  }

  obtenerNombreUsuario(): string | null {
    return this.usernameSubject.value;
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
          }
        }),
        catchError((error) => {
          console.error('Error al verificar estado de autenticación', error);
          this.actualizarEstadoLogueado(false);
          return of(false);
        })
      )
      .subscribe();
  }
}
