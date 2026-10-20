import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject, finalize, takeUntil } from 'rxjs';

import { GrupoProfesor } from '../../../interfaces/profesor';
import { AuthenticationService, esSoloProfesor } from '../../../servicios/authentication/authentication.service';
import { ProfesorService } from '../../../servicios/endpoints/profesor.service';
import { NavegacionProfesorComponent } from './navegacion-profesor/navegacion-profesor.component';
import { etiquetaRol, mensajeDelServidor, textoAlumnos } from './profesor-textos';

/** «Mis grupos» (§5.1): los grupos que tiene asignados, como titular o ayudante. */
@Component({
  selector: 'app-mis-grupos',
  standalone: true,
  imports: [RouterLink, NavegacionProfesorComponent],
  templateUrl: './mis-grupos.component.html',
  styleUrl: './mis-grupos.component.scss',
})
export class MisGruposComponent implements OnInit, OnDestroy {
  private readonly profesor = inject(ProfesorService);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  readonly etiquetaRol = etiquetaRol;
  readonly textoAlumnos = textoAlumnos;

  /** Quien solo es profesor no tiene cabecera global: cierra sesión desde aquí, como en Mi caja. */
  soloProfesor = false;
  grupos: GrupoProfesor[] = [];
  cargando = false;
  cargado = false;
  error: string | null = null;

  ngOnInit(): void {
    this.soloProfesor = esSoloProfesor(this.auth.getRolesActuales());
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;
    this.profesor
      .misGrupos()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargando = false))
      )
      .subscribe({
        next: (grupos) => {
          this.grupos = grupos;
          this.cargado = true;
        },
        error: (e) => (this.error = mensajeDelServidor(e, 'No se han podido cargar tus grupos.')),
      });
  }

  cerrarSesion(): void {
    this.auth.forzarCierreSesion().then(() => this.router.navigate(['/login']));
  }
}
