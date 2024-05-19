import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
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
      const token = localStorage.getItem('token');
      const email = localStorage.getItem('email');
      const username = localStorage.getItem('username');
      if (token && email && username) {
        this.actualizarEstadoLogueado(true);
        this.usernameSubject.next(username);
        this.emailSubject.next(email);
        this.obtenerRoles(token);
      }
    }
  }

  comprobarLogueado(): boolean {
    return this.usuarioLogueadoSubject.value;
  }

  login(credenciales: LoginInterface): Observable<any> {
    return this.http.post<any>(`${this.urlBase}/signin`, credenciales, { withCredentials: true })
      .pipe(
        tap((response) => {
          const email = credenciales.email;
          const username  = this.extraerNombreUsuario(email);
          localStorage.setItem('token', response.token);
          localStorage.setItem('email', email);
          localStorage.setItem('username', username);
          this.actualizarEstadoLogueado(true);
          this.usernameSubject.next(username);
          this.emailSubject.next(email);
          this.obtenerRoles(response.token);
        })
      );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    localStorage.removeItem('username');
    this.actualizarEstadoLogueado(false);
    this.rolesSubject.next([]);
    this.usernameSubject.next(null);
    this.emailSubject.next(null);
  }

  obtenerRoles(token: string): void {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
    this.http.get<string[]>(`${this.urlBase}/roles`, { headers: headers }).subscribe(roles => {
      this.rolesSubject.next(roles);
    });
  }

  tieneRolAdmin(): boolean {
    const roles = this.rolesSubject.value;
    return roles.includes('ROLE_ADMIN');
  }

  tieneRolManager(): boolean {
    const roles = this.rolesSubject.value;
    return roles.includes('ROLE_MANAGER');
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
}
