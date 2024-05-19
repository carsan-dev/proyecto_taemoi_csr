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

  constructor(private http: HttpClient) {
    if (typeof localStorage !== 'undefined') {
      const token = localStorage.getItem('token');
      if (token) {
        this.actualizarEstadoLogueado(true);
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
          const nombreUsuario = this.extraerNombreUsuario(credenciales.email);
          localStorage.setItem('token', response.token);
          localStorage.setItem('username', nombreUsuario);
          this.actualizarEstadoLogueado(true);
          this.obtenerRoles(response.token);
        })
      );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.actualizarEstadoLogueado(false);
    this.rolesSubject.next([]);
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
