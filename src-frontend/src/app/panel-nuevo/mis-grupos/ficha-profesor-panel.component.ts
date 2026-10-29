import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, finalize, firstValueFrom } from 'rxjs';

import {
  ContactoAlumno,
  DeporteDelAlumno,
  FichaAlumnoProfesor,
  PaseCreado,
  SaludAlumno,
  TipoPase,
} from '../../interfaces/profesor';
import {
  enlaceTelefono,
  etiquetaContacto,
  fechaCorta,
  hoyIso,
  mensajeDelServidor,
  nombreCompleto,
} from '../../componentes/endpoints/mis-grupos/profesor-textos';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { ProfesorService } from '../../servicios/endpoints/profesor.service';
import { gradoFila, gradoParaFrase } from '../alumnos/alumno-vista';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { CampoDialogo } from '../dialogo/dialogo.component';
import { eur } from '../formato/formato-es';

/** Texto de la confirmación de un pase (D7-6). Igual que en la ficha de hoy. */
export const NOTA_PASE = 'Crea el cargo; el grado sube al cobrarlo en Mi caja.';

/** La gestión (ADMIN o MANAGER) no cobra en Mi caja (decisión 1): el grado sube al cobrarlo en tesorería. */
export const NOTA_PASE_GESTION = 'Crea el cargo; el grado sube al cobrarlo en tesorería.';

const TEXTO_TIPO_PASE: Record<TipoPase, string> = {
  examen: 'Con derecho de examen',
  recompensa: 'Por recompensa',
};

const CAMPO_TIPO_PASE = 'tipo-pase';
const CAMPO_GRADO_PASE = 'grado-pase';
const ROJO_BORDADO = 'rojo-bordado';

/** Tipos de pase que hoy se ofrecen cuando hay pase (admitePases y apto): los dos. */
export function tiposDePase(d: DeporteDelAlumno): TipoPase[] {
  return d.deMisGrupos && d.admitePases && d.apto ? ['examen', 'recompensa'] : [];
}

/** Edición de la fecha de grado de un deporte. */
interface EdicionFecha {
  valor: string;
  guardando: boolean;
  error: string | null;
}

/**
 * Ficha reducida de un alumno para el profesor (boceto R6, sección 6; decisión 326): contacto, un bloque por deporte y
 * la salud a petición, sin NIF, email ni dirección. Misma lógica y mismas llamadas que la ficha de hoy
 * (`FichaProfesorComponent`): solo cambia la presentación. Se usa como pantalla propia (ruta `alumnos/:alumnoId`) o,
 * con `embebida`, dentro del panel lateral del grupo, donde el alumno llega por `alumnoId`. La salud se pide al
 * desplegarla y se descarta al plegarla o al salir: nunca al cargar la ficha (D7-3).
 */
@Component({
  selector: 'pn-ficha-profesor',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet, RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, EsqueletoComponent],
  templateUrl: './ficha-profesor-panel.component.html',
  styleUrl: './ficha-profesor-panel.component.scss',
})
export class FichaProfesorPanelComponent implements OnInit, OnChanges {
  private readonly profesor = inject(ProfesorService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthenticationService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly destruir = inject(DestroyRef);

  /** El alumno, si la ficha va dentro del panel lateral; si no, sale de la ruta. */
  @Input() alumnoId = 0;
  /** Dentro del panel lateral: sin página propia, sin «Volver» ni título (los pone el panel). */
  @Input() embebida = false;
  /** Grupo desde el que se abrió la ficha, para «Volver» (pantalla propia). */
  @Input() grupoOrigen: number | null = null;
  /** Emite al cargar el nombre del alumno, por si quien la aloja quiere titularse con él. */
  @Output() readonly cargada = new EventEmitter<FichaAlumnoProfesor>();
  /** Emite tras guardar con éxito un cambio de «Apto para examen», para que la lista de al lado se ponga al día. */
  @Output() readonly aptoCambiado = new EventEmitter<{ alumnoId: number; deporte: string; apto: boolean }>();

  readonly fechaCorta = fechaCorta;
  readonly enlaceTelefono = enlaceTelefono;
  readonly etiquetaContacto = etiquetaContacto;
  readonly hoy = hoyIso();
  readonly esqueletos = [0, 1, 2];

  ficha: FichaAlumnoProfesor | null = null;
  cargando = false;
  error: string | null = null;

  /** Deportes con una petición de apto en curso. */
  readonly cambiandoApto = new Set<string>();
  readonly ediciones = new Map<string, EdicionFecha>();
  /** Un pase en marcha (el diálogo abierto): impide abrir otro. */
  pasando = false;

  saludAbierta = false;
  cargandoSalud = false;
  salud: SaludAlumno | null = null;
  errorSalud: string | null = null;
  private peticionSalud: Subscription | null = null;
  private peticionFicha: Subscription | null = null;

  constructor() {
    this.destruir.onDestroy(() => {
      this.peticionFicha?.unsubscribe();
      this.descartarSalud();
    });
  }

  /** ADMIN o MANAGER: su Mi caja no cobra, así que los pases se cobran en tesorería. */
  get esGestion(): boolean {
    const roles = this.auth.getRolesActuales();
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
  }

  get notaPase(): string {
    return this.esGestion ? NOTA_PASE_GESTION : NOTA_PASE;
  }

  ngOnInit(): void {
    if (!this.embebida) {
      // Pantalla propia: el alumno y el grupo salen de la ruta.
      this.alumnoId = Number(this.ruta.snapshot.paramMap.get('alumnoId'));
      const grupo = Number(this.ruta.snapshot.queryParamMap.get('grupo'));
      this.grupoOrigen = Number.isInteger(grupo) && grupo > 0 ? grupo : null;
      this.cargar();
    }
  }

  ngOnChanges(cambios: SimpleChanges): void {
    if (this.embebida && cambios['alumnoId'] && this.alumnoId > 0) {
      this.ficha = null;
      this.ediciones.clear();
      this.saludAbierta = false;
      this.descartarSalud();
      this.cargar();
    }
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

  /** Deportes de sus grupos (editables) y, aparte, los de solo lectura. */
  get deportesPropios(): DeporteDelAlumno[] {
    return (this.ficha?.deportes ?? []).filter((d) => d.deMisGrupos);
  }

  get deportesAjenos(): DeporteDelAlumno[] {
    return (this.ficha?.deportes ?? []).filter((d) => !d.deMisGrupos);
  }

  /** Grado con el nombre del catálogo («Blanco-amarillo») y su muestra de color; nulo sin grado. */
  gradoDe(d: DeporteDelAlumno): { texto: string; fondo: string } | null {
    if (!d.grado) {
      return null;
    }
    const grado = gradoFila(d.grado.codigo, d.deporte);
    return { texto: gradoParaFrase(grado), fondo: grado.fondo };
  }

  /** Miga al grupo de origen: su nombre si la ficha ya lo trae; si no, «Mis grupos». */
  get miga(): { texto: string; ruta: (string | number)[] } {
    const grupo = this.grupoOrigen ? (this.ficha?.grupos ?? []).find((g) => g.id === this.grupoOrigen) : undefined;
    return grupo ? { texto: grupo.nombre, ruta: this.volver } : { texto: 'Mis grupos', ruta: ['/mis-grupos'] };
  }

  get edad(): string {
    const edad = this.ficha?.edad;
    return edad === null || edad === undefined ? '' : edad === 1 ? '1 año' : `${edad} años`;
  }

  get iniciales(): string {
    return `${this.ficha?.nombre?.charAt(0) ?? ''}${this.ficha?.apellidos?.charAt(0) ?? ''}`;
  }

  nombreContacto(c: ContactoAlumno): string {
    const nombre = nombreCompleto(c.nombre, c.apellidos);
    return nombre || (c.tipo === 'ALUMNO' ? this.nombre : 'Sin nombre');
  }

  cargar(): void {
    this.peticionFicha?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.peticionFicha = this.profesor
      .ficha(this.alumnoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (ficha) => {
          this.ficha = ficha;
          this.cargada.emit(ficha);
        },
        error: (e) => (this.error = mensajeDelServidor(e, 'No se ha podido cargar la ficha.')),
      });
  }

  // ------------------------------------------------------------------ apto

  conmutarApto(d: DeporteDelAlumno): void {
    if (!d.deMisGrupos || this.cambiandoApto.has(d.deporte)) {
      return;
    }
    this.cambiandoApto.add(d.deporte);
    const alumnoId = this.alumnoId;
    this.profesor
      .actualizarApto(this.alumnoId, d.deporte, !d.apto)
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => this.cambiandoApto.delete(d.deporte))
      )
      .subscribe({
        next: (actualizado) => {
          this.reemplazarDeporte(actualizado);
          this.aptoCambiado.emit({ alumnoId, deporte: actualizado.deporte, apto: actualizado.apto });
        },
        error: (e) => this.avisos.error(mensajeDelServidor(e, 'No se ha podido cambiar el apto.')),
      });
  }

  // ------------------------------------------------------------------ fecha de grado

  /** La edición de la fecha del deporte: el campo siempre está a la vista, con la fecha actual como valor inicial. */
  edicionDe(d: DeporteDelAlumno): EdicionFecha {
    let edicion = this.ediciones.get(d.deporte);
    if (!edicion) {
      edicion = { valor: d.fechaGrado ?? '', guardando: false, error: null };
      this.ediciones.set(d.deporte, edicion);
    }
    return edicion;
  }

  /** ¿Se ha tocado el campo? Entonces aparecen «Guardar» y «Cancelar». */
  fechaCambiada(d: DeporteDelAlumno): boolean {
    const edicion = this.ediciones.get(d.deporte);
    return !!edicion && (edicion.valor ?? '') !== (d.fechaGrado ?? '');
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
        takeUntilDestroyed(this.destruir),
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

  /** Grado al que pasa, para el texto del diálogo (con Rojo / Rojo bordado, lo elige la persona en el diálogo). */
  private destinoPase(d: DeporteDelAlumno): string {
    return d.pideRojoBordado ? 'Rojo o Rojo bordado' : d.siguienteGrado ? gradoParaFrase(gradoFila(d.siguienteGrado.codigo, d.deporte)) : '';
  }

  /**
   * «Proponer pase»: un diálogo del panel nuevo con «<alumno> · <deporte>», el tipo de pase (radios; sin radios si solo
   * hay uno), Rojo / Rojo bordado cuando se pide y la nota de hoy. La petición es la de la ficha de hoy:
   * `pasarGrado(alumno, deporte, tipo, pideRojoBordado && rojoBordado)`. Si falla, el diálogo sigue abierto con el error
   * en línea y se puede reintentar. El importe no se conoce antes de crear el cargo (ninguna llamada lo da), así que no
   * se muestra hasta el aviso posterior.
   */
  async proponerPase(d: DeporteDelAlumno): Promise<void> {
    const tipos = tiposDePase(d);
    if (!tipos.length || this.pasando) {
      return;
    }
    this.pasando = true;
    const alumnoId = this.alumnoId;
    const creado: { pase: PaseCreado | null } = { pase: null };
    const campos: CampoDialogo[] = [];
    if (tipos.length > 1) {
      campos.push({
        id: CAMPO_TIPO_PASE,
        tipo: 'pastillas',
        etiqueta: 'Tipo de pase',
        opciones: tipos.map((t) => ({ valor: t, texto: TEXTO_TIPO_PASE[t] })),
        valor: null,
      });
    }
    if (d.pideRojoBordado) {
      campos.push({
        id: CAMPO_GRADO_PASE,
        tipo: 'pastillas',
        etiqueta: 'Grado al que pasa',
        opciones: [
          { valor: 'rojo', texto: 'Rojo' },
          { valor: ROJO_BORDADO, texto: 'Rojo bordado' },
        ],
        valor: 'rojo',
      });
    }
    try {
      const resultado = await this.confirmacion.confirmar({
        titulo: 'Crear cargo del pase',
        mensaje: [{ fuerte: this.nombre }, ` · ${d.deporteNombre}`],
        bloques: [
          ...(tipos.length === 1 ? [{ tipo: 'parrafo' as const, partes: [`Pase ${TEXTO_TIPO_PASE[tipos[0]].toLowerCase()}.`] }] : []),
          { tipo: 'parrafo', partes: [`${d.grado ? gradoParaFrase(gradoFila(d.grado.codigo, d.deporte)) : ''} pasa a `, { fuerte: this.destinoPase(d) }, '.'] },
          { tipo: 'parrafo', partes: [this.notaPase] },
        ],
        campos: campos.length ? campos : undefined,
        accion: 'Crear cargo',
        accionOcupada: 'Creando cargo…',
        nombre: 'pase-profesor',
        comprobar: (valores) => {
          const tipo = (tipos.length === 1 ? tipos[0] : valores[CAMPO_TIPO_PASE]) as TipoPase | '';
          if (!tipo) {
            return { campo: CAMPO_TIPO_PASE, mensaje: 'Elige el tipo de pase.' };
          }
          return firstValueFrom(
            this.profesor.pasarGrado(alumnoId, d.deporte, tipo, d.pideRojoBordado && valores[CAMPO_GRADO_PASE] === ROJO_BORDADO)
          ).then(
            (pase) => {
              creado.pase = pase;
              return null;
            },
            (e) => ({ mensaje: mensajeDelServidor(e, 'No se ha podido crear el cargo del pase.') })
          );
        },
      });
      const pase = creado.pase;
      if (!resultado.confirmado || !pase) {
        return;
      }
      if (pase.deporte?.deporte) {
        this.reemplazarDeporte(pase.deporte);
      }
      this.avisos.exito(`Cargo creado · ${pase.cargo.concepto} · ${eur(pase.cargo.importe)}`, {
        texto: this.esGestion ? 'Ir a tesorería' : 'Ir a Mi caja',
        alPulsar: () => void this.router.navigate([this.esGestion ? '/tesoreriaCobros' : '/mi-caja']),
      });
    } finally {
      this.pasando = false;
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
