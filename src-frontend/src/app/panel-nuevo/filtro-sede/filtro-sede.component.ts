import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, booleanAttribute, inject } from '@angular/core';
import { Observable, Subscription, catchError, map, of } from 'rxjs';

import { PermisosSesion, PermisosSesionService, SedeVisible } from '../../servicios/authentication/permisos-sesion.service';

/**
 * Sedes que ofrece el filtro (decisión 376): las activas, solo para propietario y gestión y solo con dos o más. Con una
 * sede, o para el responsable, que ya va limitado a las suyas, ninguna: el filtro no existe y la pantalla no cambia.
 */
export function sedesParaFiltrar(permisos: PermisosSesion | null | undefined): SedeVisible[] {
  const gestion = (permisos?.roles ?? []).some((rol) => rol === 'PROPIETARIO' || rol === 'ADMINISTRADOR');
  if (!gestion || !permisos?.variasSedes || permisos.capacidades?.includes('PANEL_SEDE')) {
    return [];
  }
  const activas = (permisos.sedes ?? []).filter((sede) => sede.activa !== false);
  return activas.length > 1 ? activas : [];
}

/** Las sedes del filtro para esta sesión; si no se pueden leer los permisos, ninguna (falla cerrado). */
export function sedesFiltrables(permisos: PermisosSesionService): Observable<SedeVisible[]> {
  return permisos.obtener().pipe(
    map(sedesParaFiltrar),
    catchError(() => of([] as SedeVisible[]))
  );
}

/**
 * Una sede guardada (dirección o estado de la pantalla) solo vale si el selector la ofrece: un enlace compartido o de
 * otra escuela no filtra a quien no ve el selector.
 */
export function sedeVigente(permisos: PermisosSesionService, sedeId: number): Observable<number | null> {
  return sedesFiltrables(permisos).pipe(map((sedes) => (sedes.some((sede) => sede.id === sedeId) ? sedeId : null)));
}

/**
 * Filtro por sede de las pantallas del panel (pieza 9-6, decisión 376): «Todas las sedes» más las activas, con la forma
 * de los campos de filtro del panel (DESIGN.md: 42 px, `borde-control`, 8 px de radio). Sin sedes que ofrecer no pinta
 * nada y su elemento no ocupa sitio. Da igual cómo conserve el filtro cada pantalla: aquí solo se elige.
 *
 * Los permisos ya los ha pedido el arranque de la sesión y la petición se comparte: pintarlo no añade ninguna.
 */
@Component({
  selector: 'pn-filtro-sede',
  standalone: true,
  template: `
    @if (sedes.length > 0) {
    <label class="filtro-sede__etiqueta" [class.filtro-sede__etiqueta--oculta]="!etiquetaVisible" [for]="campoId">Sede</label>
    <select class="filtro-sede__control" [class.filtro-sede__control--elegido]="marcarElegida && valor !== null" [id]="campoId"
      (change)="elegir($any($event.target).value)">
      <option value="" [selected]="valor === null">Todas las sedes</option>
      @for (sede of sedes; track sede.id) {
      <option [value]="sede.id" [selected]="sede.id === valor">{{ sede.nombre }}</option>
      }
    </select>
    }
  `,
  styleUrls: ['./filtro-sede.component.scss'],
  host: { '[style.display]': "sedes.length > 0 ? null : 'none'" },
})
export class FiltroSedeComponent implements OnInit, OnDestroy {
  private readonly permisos = inject(PermisosSesionService);
  private readonly cdr = inject(ChangeDetectorRef);
  private lectura?: Subscription;

  @Input({ required: true }) campoId = '';
  /** Sede elegida; `null`, todas. */
  @Input() valor: number | null = null;
  /** Etiqueta encima, en las barras que la llevan en cada campo; en el resto, solo para lectores de pantalla. */
  @Input({ transform: booleanAttribute }) etiquetaVisible = false;
  /** Estado «Elegido» de DESIGN.md, en las barras cuyos filtros lo pintan. */
  @Input({ transform: booleanAttribute }) marcarElegida = false;
  @Output() readonly cambio = new EventEmitter<number | null>();

  sedes: SedeVisible[] = [];

  ngOnInit(): void {
    this.lectura = sedesFiltrables(this.permisos).subscribe((sedes) => {
      this.sedes = sedes;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.lectura?.unsubscribe();
  }

  elegir(valor: string): void {
    this.cambio.emit(valor === '' ? null : Number(valor));
  }
}
