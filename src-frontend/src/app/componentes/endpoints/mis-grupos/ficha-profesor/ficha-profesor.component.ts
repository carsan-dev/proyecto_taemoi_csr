import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { Subject, Subscription, finalize, takeUntil } from 'rxjs';

import {
  CargoDelPase,
  ContactoAlumno,
  DeporteDelAlumno,
  FichaAlumnoProfesor,
  SaludAlumno,
  TipoPase,
} from '../../../../interfaces/profesor';
import { ProfesorService } from '../../../../servicios/endpoints/profesor.service';
import { showErrorToast } from '../../../../utils/toast.util';
import { NavegacionProfesorComponent } from '../navegacion-profesor/navegacion-profesor.component';
import {
  enlaceTelefono,
  etiquetaContacto,
  fechaCorta,
  formatearEuros,
  hoyIso,
  mensajeDelServidor,
  nombreCompleto,
} from '../profesor-textos';

/** Texto de la confirmación de un pase (D7-6). */
export const NOTA_PASE = 'Crea el cargo; el grado sube al cobrarlo en Mi caja.';

const TITULO_PASE: Record<TipoPase, string> = {
  examen: 'Pase con derecho de examen',
  recompensa: 'Pase por recompensa',
};

/** Edición de la fecha de grado de un deporte. */
interface EdicionFecha {
  valor: string;
  guardando: boolean;
  error: string | null;
}

/** Hoja de confirmación de un pase. */
interface HojaPase {
  deporte: DeporteDelAlumno;
  tipo: TipoPase;
  titulo: string;
  /** Solo cuenta si el deporte `pideRojoBordado`. */
  rojoBordado: boolean;
  enviando: boolean;
  error: string | null;
}

/** Aviso tras crear el cargo de un pase, con el enlace a Mi caja. */
interface AvisoPase {
  cargo: CargoDelPase;
}

/**
 * Ficha reducida de un alumno (§5.3). No se editan datos del alumno (D7-1): solo la fecha de grado, el apto y los
 * pases de los deportes de sus grupos (decisión 207). La salud se pide al desplegarla y se descarta al plegarla o al
 * salir: nunca al cargar la ficha, nunca en estado compartido (D7-3).
 */
@Component({
  selector: 'app-ficha-profesor',
  standalone: true,
  imports: [FormsModule, RouterLink, A11yModule, NavegacionProfesorComponent],
  templateUrl: './ficha-profesor.component.html',
  styleUrl: './ficha-profesor.component.scss',
})
export class FichaProfesorComponent implements OnInit, OnDestroy {
  private readonly profesor = inject(ProfesorService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  readonly notaPase = NOTA_PASE;
  readonly fechaCorta = fechaCorta;
  readonly euros = formatearEuros;
  readonly enlaceTelefono = enlaceTelefono;
  readonly etiquetaContacto = etiquetaContacto;
  readonly hoy = hoyIso();

  alumnoId = 0;
  /** Grupo desde el que se abrió la ficha, para «Volver». */
  grupoOrigen: number | null = null;

  ficha: FichaAlumnoProfesor | null = null;
  cargando = false;
  error: string | null = null;

  /** Deportes con una petición de apto en curso. */
  readonly cambiandoApto = new Set<string>();
  readonly ediciones = new Map<string, EdicionFecha>();

  hojaPase: HojaPase | null = null;
  aviso: AvisoPase | null = null;
  private focoAntesDeLaHoja: HTMLElement | null = null;

  saludAbierta = false;
  cargandoSalud = false;
  salud: SaludAlumno | null = null;
  errorSalud: string | null = null;
  private peticionSalud: Subscription | null = null;

  ngOnInit(): void {
    this.alumnoId = Number(this.ruta.snapshot.paramMap.get('alumnoId'));
    const grupo = Number(this.ruta.snapshot.queryParamMap.get('grupo'));
    this.grupoOrigen = Number.isInteger(grupo) && grupo > 0 ? grupo : null;
    this.cargar();
  }

  ngOnDestroy(): void {
    this.descartarSalud();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get volver(): (string | number)[] {
    return this.grupoOrigen ? ['/mis-grupos', this.grupoOrigen] : ['/mis-grupos'];
  }

  get nombre(): string {
    return this.ficha ? nombreCompleto(this.ficha.nombre, this.ficha.apellidos) : '';
  }

  get gruposCompartidos(): string {
    return (this.ficha?.grupos ?? []).map((g) => g.nombre).join(', ');
  }

  nombreContacto(c: ContactoAlumno): string {
    const nombre = nombreCompleto(c.nombre, c.apellidos);
    return nombre || (c.tipo === 'ALUMNO' ? this.nombre : 'Sin nombre');
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;
    this.profesor
      .ficha(this.alumnoId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargando = false))
      )
      .subscribe({
        next: (ficha) => (this.ficha = ficha),
        error: (e) => (this.error = mensajeDelServidor(e, 'No se ha podido cargar la ficha.')),
      });
  }

  // ------------------------------------------------------------------ apto

  conmutarApto(d: DeporteDelAlumno): void {
    if (!d.deMisGrupos || this.cambiandoApto.has(d.deporte)) {
      return;
    }
    this.cambiandoApto.add(d.deporte);
    this.profesor
      .actualizarApto(this.alumnoId, d.deporte, !d.apto)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.cambiandoApto.delete(d.deporte))
      )
      .subscribe({
        next: (actualizado) => this.reemplazarDeporte(actualizado),
        error: (e) => showErrorToast(mensajeDelServidor(e, 'No se ha podido cambiar el apto.')),
      });
  }

  // ------------------------------------------------------------------ fecha de grado

  editarFecha(d: DeporteDelAlumno): void {
    this.ediciones.set(d.deporte, { valor: d.fechaGrado ?? this.hoy, guardando: false, error: null });
    setTimeout(() => document.getElementById(`fecha-${d.deporte}`)?.focus());
  }

  cancelarFecha(d: DeporteDelAlumno): void {
    this.ediciones.delete(d.deporte);
  }

  guardarFecha(d: DeporteDelAlumno): void {
    const edicion = this.ediciones.get(d.deporte);
    if (!edicion || edicion.guardando) {
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(edicion.valor)) {
      edicion.error = 'Elige una fecha.';
      return;
    }
    edicion.guardando = true;
    edicion.error = null;
    this.profesor
      .actualizarFechaGrado(this.alumnoId, d.deporte, edicion.valor)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (edicion.guardando = false))
      )
      .subscribe({
        next: (actualizado) => {
          this.reemplazarDeporte(actualizado);
          this.ediciones.delete(d.deporte);
        },
        error: (e) => (edicion.error = mensajeDelServidor(e, 'No se ha podido guardar la fecha.')),
      });
  }

  // ------------------------------------------------------------------ pases

  abrirPase(d: DeporteDelAlumno, tipo: TipoPase, evento?: Event): void {
    if (!d.deMisGrupos || !d.admitePases || !d.apto) {
      return;
    }
    const destino = evento?.currentTarget;
    this.focoAntesDeLaHoja = destino instanceof HTMLElement ? destino : null;
    this.hojaPase = { deporte: d, tipo, titulo: TITULO_PASE[tipo], rojoBordado: false, enviando: false, error: null };
  }

  destinoPase(hoja: HojaPase): string {
    if (hoja.deporte.pideRojoBordado) {
      return hoja.rojoBordado ? 'Rojo bordado' : 'Rojo';
    }
    return hoja.deporte.siguienteGrado?.nombre ?? '';
  }

  confirmarPase(): void {
    const hoja = this.hojaPase;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.enviando = true;
    hoja.error = null;
    this.profesor
      .pasarGrado(this.alumnoId, hoja.deporte.deporte, hoja.tipo, hoja.deporte.pideRojoBordado && hoja.rojoBordado)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (hoja.enviando = false))
      )
      .subscribe({
        next: (pase) => {
          hoja.enviando = false;
          if (pase.deporte?.deporte) {
            this.reemplazarDeporte(pase.deporte);
          }
          this.aviso = { cargo: pase.cargo };
          this.cerrarPase();
        },
        error: (e) => (hoja.error = mensajeDelServidor(e, 'No se ha podido crear el cargo del pase.')),
      });
  }

  cerrarPase(): void {
    if (this.hojaPase?.enviando) {
      return;
    }
    this.hojaPase = null;
    const foco = this.focoAntesDeLaHoja;
    this.focoAntesDeLaHoja = null;
    setTimeout(() => foco?.isConnected && foco.focus());
  }

  cerrarAviso(): void {
    this.aviso = null;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.hojaPase) {
      this.cerrarPase();
    }
  }

  // ------------------------------------------------------------------ salud (D7-3)

  conmutarSalud(): void {
    if (this.saludAbierta) {
      this.saludAbierta = false;
      this.descartarSalud();
      return;
    }
    this.saludAbierta = true;
    this.pedirSalud();
  }

  pedirSalud(): void {
    this.peticionSalud?.unsubscribe();
    this.cargandoSalud = true;
    this.errorSalud = null;
    this.salud = null;
    this.peticionSalud = this.profesor
      .salud(this.alumnoId)
      .pipe(finalize(() => (this.cargandoSalud = false)))
      .subscribe({
        next: (salud) => (this.salud = salud),
        error: (e) => (this.errorSalud = mensajeDelServidor(e, 'No se han podido leer los datos de salud.')),
      });
  }

  private descartarSalud(): void {
    this.peticionSalud?.unsubscribe();
    this.peticionSalud = null;
    this.salud = null;
    this.errorSalud = null;
    this.cargandoSalud = false;
  }

  // ------------------------------------------------------------------ utilidades

  private reemplazarDeporte(actualizado: DeporteDelAlumno): void {
    if (!this.ficha) {
      return;
    }
    this.ficha = {
      ...this.ficha,
      deportes: this.ficha.deportes.map((d) => (d.deporte === actualizado.deporte ? actualizado : d)),
    };
  }
}
