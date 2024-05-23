import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, tap, throwError } from 'rxjs';
import { LoginInterface } from '../../interfaces/login-interface';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private urlBase = 'http://localhost:8080/api/auth';
  private usuarioLogueadoSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  usuarioLogueadoCambio: Observable<boolean> = this.usuarioLogueadoSubject.asObservable();
  private rolesSubject: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
  rolesCambio: Observable<string[]> = this.rolesSubject.asObservable();

  private usernameSubject: BehaviorSubject<string | null> = new BehaviorSubject<string | null>(null);
  usernameCambio: Observable<string | null> = this.usernameSubject.asObservable();

  private emailSubject: BehaviorSubject<string | null> = new BehaviorSubject<string | null>(null);
  emailCambio: Observable<string | null> = this.emailSubject.asObservable();

  constructor(private http: HttpClient) {
    if (typeof localStorage !== 'undefined') {
      this.verificarValidezToken();
    }
  }

  private crearHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<any> {
    return this.http.post<any>(`${this.urlBase}/signin`, credenciales, { withCredentials: true }).pipe(
      tap((response) => {
        const email = credenciales.email;
        const username = this.extraerNombreUsuario(email);
        const tokenCreationTime = Date.now().toString();
        localStorage.setItem('token', response.token);
        localStorage.setItem('email', email);
        localStorage.setItem('username', username);
        localStorage.setItem('tokenCreationTime', tokenCreationTime.toString());
        this.actualizarEstadoLogueado(true);
        this.usernameSubject.next(username);
        this.emailSubject.next(email);
        this.obtenerRoles(response.token);
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

  obtenerRoles(token: string): void {
    const headers = this.crearHeaders(token);
    this.http.get<string[]>(`${this.urlBase}/roles`, { headers }).pipe(
      catchError(this.manejarError)
    ).subscribe(roles => {
      this.rolesSubject.next(roles);
    });
  }

  obtenerUsuarioAutenticado(token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http.get<any>(`${this.urlBase}/user`, { headers }).pipe(
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

  obtenerNombreUsuario(): string | null {
    return localStorage.getItem('username');
  }

  private extraerNombreUsuario(email: string): string {
    return email.substring(0, email.indexOf('@'));
  }

  private eliminarToken(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    localStorage.removeItem('username');
    localStorage.removeItem('tokenCreationTime');
  }

  private verificarValidezToken(): void {
    const token = localStorage.getItem('token');
    const tokenCreationTime = localStorage.getItem('tokenCreationTime');

    if (token && tokenCreationTime) {
        const creationDate = new Date(parseInt(tokenCreationTime));
        const expiryDate = new Date(creationDate.getTime() + 10 * 60 * 60 * 1000);

        if (new Date() > expiryDate) {
            this.eliminarToken();
        } else {
            const email = localStorage.getItem('email');
            const username = localStorage.getItem('username');
            if (email && username) {
                this.actualizarEstadoLogueado(true);
                this.usernameSubject.next(username);
                this.emailSubject.next(email);
                this.obtenerRoles(token);
            }
        }
    }
}

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }
}
