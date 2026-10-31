import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import {
  AVISO_SEGUNDA_SEDE,
  CAMPOS_SEDE,
  CamposSede,
  ClaveCampo,
  Sede,
  TEXTO_DESACTIVAR,
  TEXTO_UNICA_ACTIVA,
  activarCreaSegunda,
  camposDe,
  cuerpoSede,
  esUnicaActiva,
  estadoHttp,
  lineaSecundaria,
  mensajeError,
  seraSegundaSede,
  validarCampos,
} from './sedes-vista';
import { SedesService } from './sedes.service';

type Estado = 'cargando' | 'listo' | 'error';

/** El panel lateral abierto: `sede` null es «Nueva sede». */
interface PanelSede {
  sede: Sede | null;
  campos: CamposSede;
  errores: Partial<Record<ClaveCampo, string>>;
  /** Error que no es de un campo: se ve en el propio panel (el SweetAlert2 quedaría detrás del `<dialog>`). */
  error: string | null;
  enviando: boolean;
  cambiandoEstado: boolean;
}

/**
 * Sedes del panel nuevo (E9, pieza 9-3). Una fila por sede (nombre, localidad y teléfono, estado y editar); «Nueva sede» y
 * «Editar sede» en el panel lateral, y «Estado» (activar y desactivar) al final del panel al editar. `PUT /api/sedes/{id}`
 * reemplaza todos los datos editables: al editar se envía el `aforoTurno` que devolvió la API, aunque no se muestre.
 * Tras un alta, una edición o un cambio de estado se descartan los permisos en caché (`variasSedes` y `sedes` de
 * `GET /api/auth/permisos`) para que el resto de pantallas se enteren. Solo propietario y gestión.
 */
@Component({
  selector: 'app-sedes-panel',
  standalone: true,
  imports: [
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    PanelLateralComponent,
  ],
  templateUrl: './sedes.component.html',
  styleUrls: ['./sedes.component.scss'],
})
export class SedesPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(SedesService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private lectura: Subscription | null = null;
  private destruido = false;

  readonly campos = CAMPOS_SEDE;
  readonly avisoSegundaSede = AVISO_SEGUNDA_SEDE;
  readonly textoUnicaActiva = TEXTO_UNICA_ACTIVA;
  readonly lineaSecundaria = lineaSecundaria;

  estado: Estado = 'cargando';
  sedes: Sede[] = [];
  panel: PanelSede | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.lectura?.unsubscribe();
  }

  // ------------------------------------------------------------------ lista

  /** `silencioso`: tras un cambio, la lista se refresca sin volver al esqueleto (y un fallo no la tira). */
  cargar(silencioso = false): void {
    this.lectura?.unsubscribe();
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.lectura = this.api.listar().subscribe({
      next: (sedes) => {
        this.sedes = sedes ?? [];
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

  // ------------------------------------------------------------------ panel «Nueva sede» y «Editar sede»

  abrirNueva(): void {
    this.panel = { sede: null, campos: camposDe(null), errores: {}, error: null, enviando: false, cambiandoEstado: false };
  }

  abrirEditar(sede: Sede): void {
    this.panel = { sede, campos: camposDe(sede), errores: {}, error: null, enviando: false, cambiandoEstado: false };
  }

  cerrarPanel(): void {
    if (this.panel?.enviando || this.panel?.cambiandoEstado) {
      return;
    }
    this.panel = null;
  }

  /** Crear con exactamente una sede activa la convierte en la segunda. */
  get segundaSede(): boolean {
    return !!this.panel && !this.panel.sede && seraSegundaSede(this.sedes);
  }

  get textoGuardar(): string {
    if (this.panel?.sede) {
      return 'Guardar cambios';
    }
    return this.segundaSede ? 'Crear segunda sede' : 'Crear sede';
  }

  get textoGuardando(): string {
    return this.panel?.sede ? 'Guardando…' : 'Creando…';
  }

  /** La sede abierta tal como está en la lista (el estado se actualiza sin cerrar el panel). */
  get sedeAbierta(): Sede | null {
    const sede = this.panel?.sede;
    return sede ? (this.sedes.find((s) => s.id === sede.id) ?? sede) : null;
  }

  get unicaActiva(): boolean {
    const sede = this.sedeAbierta;
    return !!sede && esUnicaActiva(this.sedes, sede);
  }

  escribir(clave: ClaveCampo, evento: Event): void {
    if (!this.panel) {
      return;
    }
    this.panel.campos[clave] = (evento.target as HTMLInputElement).value;
    delete this.panel.errores[clave];
    this.panel.error = null;
  }

  guardar(evento?: Event): void {
    evento?.preventDefault();
    const panel = this.panel;
    if (!panel || panel.enviando || panel.cambiandoEstado) {
      return;
    }
    panel.errores = validarCampos(panel.campos);
    const primero = this.campos.find((c) => panel.errores[c.clave]);
    if (primero) {
      this.enfocar(primero.clave);
      return;
    }
    panel.enviando = true;
    panel.error = null;
    const edicion = this.sedeAbierta;
    // El PUT reemplaza todo: se reenvía el aforo que devolvió la API, aunque la pantalla no lo muestre.
    const cuerpo = cuerpoSede(panel.campos, edicion?.aforoTurno ?? null);
    const peticion = edicion ? this.api.editar(edicion.id, cuerpo) : this.api.crear(cuerpo);
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    peticion.subscribe({
      next: () => {
        // La sede cambió el nombre o apareció: los permisos en caché (`sedes`, `variasSedes`) ya no valen.
        this.permisos.reiniciar();
        if (this.destruido) {
          return;
        }
        this.panel = null;
        this.avisos.exito(edicion ? 'Cambios guardados.' : 'Sede creada.');
        this.cargar(true);
      },
      error: (error) => {
        if (this.destruido) {
          return;
        }
        panel.enviando = false;
        if (estadoHttp(error) === 409) {
          panel.errores = { nombre: mensajeError(error, 'Ya hay una sede con ese nombre.') };
          this.cdr.detectChanges();
          this.enfocar('nombre');
          return;
        }
        panel.error = mensajeError(error, 'No se pudo guardar la sede.');
        this.cdr.detectChanges();
      },
    });
  }

  private enfocar(clave: ClaveCampo): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#sedes-campo-${clave}`)?.focus();
  }

  // ------------------------------------------------------------------ activar y desactivar

  async desactivar(): Promise<void> {
    const sede = this.sedeAbierta;
    const panel = this.panel;
    if (!sede || !panel || !sede.activa || esUnicaActiva(this.sedes, sede) || panel.cambiandoEstado) {
      return;
    }
    const resultado = await this.confirmacion.confirmar<Sede>({
      titulo: `¿Desactivar ${sede.nombre}?`,
      mensaje: TEXTO_DESACTIVAR,
      accion: 'Desactivar',
      accionOcupada: 'Desactivando…',
      nombre: 'desactivar-sede',
      ejecutar: () => this.api.cambiarActiva(sede.id, false),
    });
    if (!resultado.confirmado || this.destruido) {
      return;
    }
    if (resultado.error) {
      this.avisos.error(mensajeError(resultado.error, 'No se pudo desactivar la sede.'));
      this.cdr.detectChanges();
      return;
    }
    this.estadoCambiado(resultado.respuesta ?? { ...sede, activa: false }, 'Sede desactivada.');
  }

  async activar(): Promise<void> {
    const sede = this.sedeAbierta;
    const panel = this.panel;
    if (!sede || !panel || sede.activa || panel.cambiandoEstado) {
      return;
    }
    if (activarCreaSegunda(this.sedes, sede)) {
      const resultado = await this.confirmacion.confirmar<Sede>({
        titulo: `¿Activar ${sede.nombre}?`,
        mensaje: AVISO_SEGUNDA_SEDE,
        accion: 'Activar',
        accionOcupada: 'Activando…',
        nombre: 'activar-segunda-sede',
        ejecutar: () => this.api.cambiarActiva(sede.id, true),
      });
      if (!resultado.confirmado || this.destruido) {
        return;
      }
      if (resultado.error) {
        this.avisos.error(mensajeError(resultado.error, 'No se pudo activar la sede.'));
        this.cdr.detectChanges();
        return;
      }
      this.estadoCambiado(resultado.respuesta ?? { ...sede, activa: true }, 'Sede activada.');
      return;
    }
    panel.cambiandoEstado = true;
    this.api.cambiarActiva(sede.id, true).subscribe({
      next: (respuesta) => {
        panel.cambiandoEstado = false;
        if (this.destruido) {
          this.permisos.reiniciar();
          return;
        }
        this.estadoCambiado(respuesta, 'Sede activada.');
      },
      error: (error) => {
        if (this.destruido) {
          return;
        }
        panel.cambiandoEstado = false;
        this.avisos.error(mensajeError(error, 'No se pudo activar la sede.'));
        this.cdr.detectChanges();
      },
    });
  }

  /** Alta o cambio de estado hecho: la lista se pone al día, los permisos en caché se descartan y se avisa. */
  private estadoCambiado(sede: Sede, aviso: string): void {
    this.sedes = this.sedes.map((s) => (s.id === sede.id ? { ...s, ...sede } : s));
    this.permisos.reiniciar();
    this.avisos.exito(aviso);
    this.cdr.detectChanges();
    this.cargar(true);
  }
}
