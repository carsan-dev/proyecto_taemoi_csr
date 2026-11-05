import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/** Una disciplina de la plantilla de plataforma, con lo que se copiaría a la escuela nueva. */
export interface DisciplinaPlantilla {
  codigo: string;
  nombre: string;
  conGrados: boolean;
  tarifas: number;
  productos: number;
}

export interface AltaEscuela {
  nombre: string;
  emailContacto: string;
  sede: { nombre: string };
  propietario: { nombre: string; apellidos: string; email: string };
  disciplinas: string[];
}

export interface EscuelaCreada {
  id: number;
  nombre: string;
  invitacionEnviadaA: string;
}

/** Alta de escuelas (solo la sesión de plataforma). Los errores los muestra la pantalla. */
@Injectable({ providedIn: 'root' })
export class AltaEscuelaService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/plataforma`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  disciplinas(): Observable<DisciplinaPlantilla[]> {
    return this.http.get<DisciplinaPlantilla[]>(`${this.api}/plantilla/disciplinas`, this.opciones());
  }

  crear(alta: AltaEscuela): Observable<EscuelaCreada> {
    return this.http.post<EscuelaCreada>(`${this.api}/escuelas`, alta, this.opciones());
  }
}
