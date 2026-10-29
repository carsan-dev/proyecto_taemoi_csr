import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';

import { TurnoDeGrupoResumen } from '../../interfaces/grupo-resumen';
import { GrupoProfesor } from '../../interfaces/profesor';
import { ProfesorService } from '../../servicios/endpoints/profesor.service';
import { etiquetaRol, mensajeDelServidor } from '../../componentes/endpoints/mis-grupos/profesor-textos';
import { resumenHorario } from '../grupos/grupos-vista';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { numero } from '../formato/formato-es';

@Component({
  selector: 'pn-mis-grupos',
  standalone: true,
  imports: [RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BloqueVacioComponent, EsqueletoComponent],
  templateUrl: './mis-grupos.component.html',
  styleUrl: './mis-grupos.component.scss',
})
export class MisGruposPanelComponent implements OnInit {
  private readonly profesor = inject(ProfesorService);
  private readonly destruir = inject(DestroyRef);
  private carga?: Subscription;
  readonly etiquetaRol = etiquetaRol;
  readonly numero = numero;
  readonly esqueletos = [0, 1, 2];
  grupos: GrupoProfesor[] = [];
  cargando = true;
  error: string | null = null;

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.carga = this.profesor.misGrupos().pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.cargando = false)),
    ).subscribe({
      next: grupos => (this.grupos = grupos),
      error: e => (this.error = mensajeDelServidor(e, 'No se han podido cargar tus grupos.')),
    });
  }

  /** Una API sin `turnos` no rompe la lista: se lee como «Sin turnos». */
  horario(turnos: TurnoDeGrupoResumen[] | undefined): string {
    return resumenHorario(turnos ?? []);
  }

  colorDisciplina(codigo: string): string {
    // Orden fijo de los colores de disciplina de DESIGN.md; sin pedir el catálogo de gestión.
    const indice = ['TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA'].indexOf(codigo);
    return indice < 0 ? 'otra' : String(indice + 1);
  }
}
