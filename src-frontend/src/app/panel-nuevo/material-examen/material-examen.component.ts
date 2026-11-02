import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { MaterialExamenApiService } from './material-examen-api.service';
import { coloresBloque, peso, resumenDisciplina, textoGrados, textoVideos } from './material-examen-vista';
import { BloqueResumen, DisciplinaConBloques } from './material-examen.models';

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Escuela → Material de examen (pieza R2; bocetos `Main.dc.html` y `MovilLista.dc.html`): los bloques de cada disciplina
 * con lo que tiene cada uno. Sin pestañas si la escuela tiene una sola disciplina. La disciplina elegida va en la consulta
 * para que volver de un bloque deje la misma pestaña.
 */
@Component({
  selector: 'app-material-examen-panel',
  standalone: true,
  imports: [RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BloqueVacioComponent, EsqueletoComponent],
  templateUrl: './material-examen.component.html',
  styleUrls: ['./material-examen.component.scss'],
})
export class MaterialExamenPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(MaterialExamenApiService);
  private readonly catalogo = inject(CatalogoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  estado: Estado = 'cargando';
  disciplinas: DisciplinaConBloques[] = [];
  elegida = '';
  private lectura?: Subscription;

  readonly peso = peso;
  readonly textoVideos = textoVideos;

  get actual(): DisciplinaConBloques | undefined {
    return this.disciplinas.find((d) => d.codigo === this.elegida) ?? this.disciplinas[0];
  }

  get resumen(): string {
    return this.actual ? resumenDisciplina(this.actual) : '';
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.lectura?.unsubscribe();
  }

  cargar(): void {
    this.lectura?.unsubscribe();
    this.estado = 'cargando';
    this.lectura = this.api.listar().subscribe({
      next: (listado) => {
        this.disciplinas = listado.disciplinas;
        const pedida = this.ruta.snapshot.queryParamMap.get('disciplina');
        this.elegida = this.disciplinas.some((d) => d.codigo === pedida) ? (pedida as string) : (this.disciplinas[0]?.codigo ?? '');
        this.estado = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        this.estado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  elegir(codigo: string): void {
    if (codigo === this.elegida) {
      return;
    }
    this.elegida = codigo;
    void this.router.navigate([], { relativeTo: this.ruta, queryParams: { disciplina: codigo }, replaceUrl: true });
  }

  /** Flechas (con vuelta), Inicio y Fin; el foco sigue a la pestaña elegida. */
  teclaPestanas(evento: KeyboardEvent): void {
    const total = this.disciplinas.length;
    const i = this.disciplinas.findIndex((d) => d.codigo === this.elegida);
    const destino = ({ ArrowRight: (i + 1) % total, ArrowLeft: (i - 1 + total) % total, Home: 0, End: total - 1 } as Record<string, number>)[evento.key];
    if (destino === undefined) {
      return;
    }
    evento.preventDefault();
    const codigo = this.disciplinas[destino].codigo;
    this.elegir(codigo);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`[data-pestana="${codigo}"]`)?.focus();
  }

  subtitulo(disciplina: DisciplinaConBloques, bloque: BloqueResumen): string {
    const esNegros = disciplina.codigo === 'TAEKWONDO' || disciplina.codigo === 'KICKBOXING';
    return textoGrados(bloque.grados, esNegros ? bloque.id : '', (codigo) => this.catalogo.grado(disciplina.codigo, codigo));
  }

  cintos(disciplina: DisciplinaConBloques, bloque: BloqueResumen): [string, string] {
    return coloresBloque(bloque.grados, (codigo) => this.catalogo.grado(disciplina.codigo, codigo), bloque.id, disciplina.codigo);
  }
}
