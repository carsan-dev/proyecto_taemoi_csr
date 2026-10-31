import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, filter } from 'rxjs/operators';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { FichaCompleta, Miembro, MiembrosService, RolMembresia } from '../../servicios/endpoints/miembros.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { numero } from '../formato/formato-es';
import { ContextoMiembros, MiembroFichaComponent } from './miembro-ficha.component';
import { MiembroAltaComponent } from './miembro-alta.component';
import {
  ESPERA_BUSQUEDA_MS,
  OPCIONES_ROL,
  POR_PAGINA,
  etiquetaRol,
  filaDeFicha,
  lineaSecundaria,
  nombreCompleto,
  rolesActivos,
} from './miembros-vista';

type Estado = 'cargando' | 'listo' | 'error';

/**
 * Miembros del panel nuevo (E9, pieza 9-4). Una fila por cuenta (nombre, email, etiquetas de sus roles activos y, si
 * procede, «Invitación pendiente»); buscador por nombre o email con espera, selector de rol, «Incluir familias» y
 * paginación de servidor. La ficha y el alta se abren en el panel lateral. La búsqueda y los filtros no van a la
 * dirección: el email es un dato personal. Solo propietario y gestión.
 */
@Component({
  selector: 'app-miembros-panel',
  standalone: true,
  imports: [
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    MiembroFichaComponent,
    MiembroAltaComponent,
  ],
  templateUrl: './miembros.component.html',
  styleUrls: ['./miembros.component.scss'],
})
export class MiembrosPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(MiembrosService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly busqueda = new Subject<string>();
  private readonly suscripciones = new Subscription();
  private lectura: Subscription | null = null;

  readonly opcionesRol = OPCIONES_ROL;
  readonly etiquetaRol = etiquetaRol;
  readonly lineaSecundaria = lineaSecundaria;
  readonly nombreCompleto = nombreCompleto;
  readonly rolesActivos = rolesActivos;
  readonly numero = numero;

  // Filtros (al servidor).
  texto = '';
  rol: RolMembresia | '' = '';
  incluirFamilias = false;
  /** Desde 1 en pantalla; la API cuenta desde 0. */
  pagina = 1;

  estado: Estado = 'cargando';
  miembros: Miembro[] = [];
  total = 0;
  totalPaginas = 0;

  contexto: ContextoMiembros = { propietario: false, sedes: [], variasSedes: false };
  abierto: Miembro | null = null;
  altaAbierta = false;

  get hayFiltros(): boolean {
    return this.texto.trim() !== '' || this.rol !== '' || this.incluirFamilias;
  }

  get rango(): string {
    if (this.total === 0) {
      return '';
    }
    const desde = (this.pagina - 1) * POR_PAGINA + 1;
    const hasta = Math.min(this.pagina * POR_PAGINA, this.total);
    return `${numero(desde)}–${numero(hasta)} de ${numero(this.total)}`;
  }

  /** Páginas a la vista: la primera, la última y las vecinas de la actual; `null` es un salto («…»). */
  get paginas(): (number | null)[] {
    const total = this.totalPaginas;
    const visibles = new Set([1, total, this.pagina - 1, this.pagina, this.pagina + 1].filter((p) => p >= 1 && p <= total));
    const orden = [...visibles].sort((a, b) => a - b);
    const salida: (number | null)[] = [];
    orden.forEach((p, i) => {
      if (i > 0 && p - orden[i - 1] > 1) {
        salida.push(null);
      }
      salida.push(p);
    });
    return salida;
  }

  ngOnInit(): void {
    this.suscripciones.add(
      // Solo si el texto cambia frente al filtro vigente: «Quitar filtros» vacía la búsqueda sin repetir la carga.
      this.busqueda.pipe(debounceTime(ESPERA_BUSQUEDA_MS), filter((t) => t !== this.texto)).subscribe((t) => {
        this.texto = t;
        this.pagina = 1;
        this.cargar();
      })
    );
    this.suscripciones.add(
      this.permisos.obtener().subscribe({
        next: (p) => {
          this.contexto = {
            propietario: (p.capacidades ?? []).includes('DAR_GESTION'),
            sedes: p.sedes ?? [],
            variasSedes: p.variasSedes === true,
          };
          this.cdr.detectChanges();
        },
        // Sin permisos legibles, falla cerrado: sin Propietario ni Gestor y sin sedes que elegir.
        error: () => undefined,
      })
    );
    this.cargar();
  }

  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.lectura?.unsubscribe();
    this.busqueda.complete();
  }

  // ------------------------------------------------------------------ lista

  /** `silencioso`: tras un cambio, la lista se refresca sin volver al esqueleto (y un fallo no la tira). */
  cargar(silencioso = false): void {
    this.lectura?.unsubscribe();
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.lectura = this.api
      .listar({ q: this.texto, rol: this.rol, incluirFamilias: this.incluirFamilias, pagina: this.pagina - 1, tamano: POR_PAGINA })
      .subscribe({
        next: (respuesta) => {
          this.miembros = respuesta?.contenido ?? [];
          this.total = Number(respuesta?.total ?? this.miembros.length) || 0;
          this.totalPaginas = Number(respuesta?.totalPaginas ?? 0) || (this.total > 0 ? Math.ceil(this.total / POR_PAGINA) : 0);
          this.estado = 'listo';
          this.cdr.detectChanges();
        },
        error: () => {
          if (!silencioso) {
            this.estado = 'error';
          }
          this.cdr.detectChanges();
        },
      });
  }

  buscar(texto: string): void {
    this.busqueda.next(texto);
  }

  elegirRol(valor: string): void {
    this.rol = valor as RolMembresia | '';
    this.pagina = 1;
    this.cargar();
  }

  alternarFamilias(incluir: boolean): void {
    this.incluirFamilias = incluir;
    this.pagina = 1;
    this.cargar();
  }

  quitarFiltros(): void {
    this.busqueda.next('');
    this.texto = '';
    this.rol = '';
    this.incluirFamilias = false;
    this.pagina = 1;
    this.cargar();
  }

  irAPagina(pagina: number): void {
    if (pagina < 1 || pagina > this.totalPaginas || pagina === this.pagina || this.estado === 'cargando') {
      return;
    }
    this.pagina = pagina;
    this.cargar();
  }

  // ------------------------------------------------------------------ paneles

  abrirFicha(miembro: Miembro): void {
    this.altaAbierta = false;
    this.abierto = miembro;
  }

  cerrarFicha(): void {
    this.abierto = null;
  }

  abrirAlta(): void {
    this.abierto = null;
    this.altaAbierta = true;
  }

  cerrarAlta(): void {
    this.altaAbierta = false;
  }

  /** La ficha cambió algo: la fila se pone al día ya y la lista se recarga sin esqueleto. */
  fichaCambiada(ficha: FichaCompleta): void {
    const fila = filaDeFicha(ficha);
    this.miembros = this.miembros.map((m) => (m.usuarioId === fila.usuarioId ? fila : m));
    this.cargar(true);
  }

  /** Alta hecha: se cierra el panel, se avisa, se recarga la lista y se abre la ficha del miembro nuevo. */
  altaHecha(evento: { ficha: FichaCompleta; invitacion: boolean }): void {
    this.altaAbierta = false;
    this.avisos.exito(evento.invitacion ? 'Invitación enviada.' : 'Miembro añadido.');
    this.abierto = filaDeFicha(evento.ficha);
    this.cargar(true);
    this.cdr.detectChanges();
  }
}
