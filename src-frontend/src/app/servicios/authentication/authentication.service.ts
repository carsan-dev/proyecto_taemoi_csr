import { EventEmitter, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginInterface } from '../../interfaces/login-interface';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private urlBase = 'http://localhost:8080/api/auth';
  private usuarioLogueado: boolean = false;
  usuarioLogueadoCambio: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private http: HttpClient) {}

  comprobarLogueado(): boolean {
    return this.usuarioLogueado;
  }

  login(credenciales: LoginInterface): Observable<any> {
    return this.http.post<any>(`${this.urlBase}/signin`, credenciales, {
      withCredentials: true,
    });
  }

  actualizarEstadoLogueado(estado: boolean) {
    this.usuarioLogueado = estado;
    this.usuarioLogueadoCambio.emit(estado);
  }
}
