import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import {
  BehaviorSubject,
  Observable,
  catchError,
  of,
  tap,
  throwError,
} from 'rxjs';
import { LoginInterface } from '../../interfaces/login-interface';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private urlBase = 'http://localhost:8080/api/auth';
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

  constructor(private http: HttpClient) {
    if (this.isLocalStorageAvailable()) {
      this.verificarValidezToken();
    }
  }

  private crearHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  private isLocalStorageAvailable(): boolean {
    try {
      const testKey = '__localStorageTest__';
      localStorage.setItem(testKey, testKey);
      localStorage.removeItem(testKey);
      return true;
    } catch (e) {
      return false;
    }
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/signin`, credenciales, {
        withCredentials: true,
      })
      .pipe(
        tap((response) => {
          const email = credenciales.email;
          const username = this.extraerNombreUsuario(email);
          const tokenCreationTime = Date.now().toString();
          if (this.isLocalStorageAvailable()) {
            localStorage.setItem('token', response.token);
            localStorage.setItem('email', email);
            localStorage.setItem('username', username);
            localStorage.setItem('tokenCreationTime', tokenCreationTime);
          }
          this.actualizarEstadoLogueado(true);
          this.usernameSubject.next(username);
          this.emailSubject.next(email);
          this.obtenerRoles(response.token).subscribe(); // Ensure roles are fetched after login
        }),
        catchError(this.manejarError)
      );
  }

  logout(): void {
    this.eliminarToken();
    this.actualizarEstadoLogueado(false);
    this.rolesSubject.next([]);
    this.usernameSubject.next(null);
    this.emailSubject.next(null);
  }

  obtenerRoles(token: string): Observable<string[]> {
    const headers = this.crearHeaders(token);
    return this.http.get<string[]>(`${this.urlBase}/roles`, { headers }).pipe(
      tap((roles) => {
        this.rolesSubject.next(roles);
        if (this.isLocalStorageAvailable()) {
          localStorage.setItem('roles', JSON.stringify(roles)); // Store roles in localStorage
        }
      }),
      catchError(this.manejarError)
    );
  }

  getRoles(): Observable<string[]> {
    if (this.isLocalStorageAvailable()) {
      const token = localStorage.getItem('token');
      const roles = localStorage.getItem('roles');
      if (roles) {
        this.rolesSubject.next(JSON.parse(roles));
        return of(JSON.parse(roles));
      } else if (token) {
        return this.obtenerRoles(token);
      } else {
        return of([]);
      }
    } else {
      return of([]);
    }
  }

  obtenerUsuarioAutenticado(token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/user`, { headers })
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
    if (this.isLocalStorageAvailable()) {
      return localStorage.getItem('username');
    }
    return null;
  }

  private extraerNombreUsuario(email: string): string {
    return email.substring(0, email.indexOf('@'));
  }

  private eliminarToken(): void {
    if (this.isLocalStorageAvailable()) {
      localStorage.removeItem('token');
      localStorage.removeItem('email');
      localStorage.removeItem('username');
      localStorage.removeItem('tokenCreationTime');
      localStorage.removeItem('roles');
    }
  }

  private verificarValidezToken(): void {
    if (this.isLocalStorageAvailable()) {
      const token = localStorage.getItem('token');
      const tokenCreationTime = localStorage.getItem('tokenCreationTime');

      if (token && tokenCreationTime) {
        const creationDate = new Date(parseInt(tokenCreationTime));
        const expiryDate = new Date(
          creationDate.getTime() + 10 * 60 * 60 * 1000
        );

        if (new Date() > expiryDate) {
          this.eliminarToken();
        } else {
          const email = localStorage.getItem('email');
          const username = localStorage.getItem('username');
          if (email && username) {
            this.actualizarEstadoLogueado(true);
            this.usernameSubject.next(username);
            this.emailSubject.next(email);
            this.obtenerRoles(token).subscribe(); // Ensure roles are fetched on app load
          }
        }
      }
    }
  }

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }
}
