import { ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';

import { FichaCompleta, MiembrosService, RolMembresia } from '../../servicios/endpoints/miembros.service';
import { AvisosService } from '../avisos/avisos.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { ContextoMiembros } from './miembro-ficha.component';
import {
  AVISO_SIN_CUENTA,
  CamposAlta,
  ClaveAlta,
  cuerpoAlta,
  estadoHttp,
  etiquetaRol,
  mensajeError,
  pideReactivar,
  rolesParaAlta,
  validarAlta,
} from './miembros-vista';

/**
 * «Añadir miembro» en el panel lateral (E9, pieza 9-4): email y rol (y sedes si es Responsable de sede). Si la API
 * responde 404 (no hay cuenta con ese email) el panel pide nombre y apellidos y envía una invitación; si responde que el
 * rol está desactivado (409), ofrece «Reactivar». Los grupos del profesor se asignan en la ficha, después. Nada del email
 * llega a la consola ni a la dirección.
 */
@Component({
  selector: 'pn-miembro-alta',
  standalone: true,
  imports: [PanelLateralComponent, BotonOcupadoComponent],
  templateUrl: './miembro-alta.component.html',
  styleUrls: ['./miembro-panel.scss'],
})
export class MiembroAltaComponent implements OnDestroy {
  private readonly api = inject(MiembrosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly avisos = inject(AvisosService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private destruido = false;

  @Input({ required: true }) contexto!: ContextoMiembros;
  @Output() readonly cerrar = new EventEmitter<void>();
  /** Llamada cuando el alta termina con el panel ya cerrado (los `@Output` ya no llegan): la lista se refresca. */
  @Input() alTerminarCerrado: (() => void) | null = null;
  /** La ficha del miembro nuevo y si fue una invitación. */
  @Output() readonly creada = new EventEmitter<{ ficha: FichaCompleta; invitacion: boolean }>();

  readonly avisoSinCuenta = AVISO_SIN_CUENTA;
  readonly etiquetaRol = etiquetaRol;

  campos: CamposAlta = { email: '', rol: '', sedeIds: [], nombre: '', apellidos: '' };
  errores: Partial<Record<ClaveAlta, string>> = {};
  error: string | null = null;
  enviando = false;
  /** No hay cuenta con ese email: se piden nombre y apellidos y se invita. */
  invitar = false;
  /** El rol estaba desactivado: se ofrece reactivarlo. */
  reactivar = false;

  get roles(): RolMembresia[] {
    return rolesParaAlta(this.contexto.propietario);
  }

  get sedesActivas() {
    return this.contexto.sedes.filter((s) => s.activa);
  }

  get textoEnviar(): string {
    if (this.reactivar) {
      return 'Reactivar';
    }
    return this.invitar ? 'Enviar invitación' : 'Añadir miembro';
  }

  get textoEnviando(): string {
    return this.reactivar ? 'Reactivando…' : this.invitar ? 'Enviando…' : 'Añadiendo…';
  }

  ngOnDestroy(): void {
    this.destruido = true;
  }

  private limpiar(clave?: ClaveAlta): void {
    if (clave) {
      delete this.errores[clave];
    }
    this.error = null;
  }

  escribirEmail(evento: Event): void {
    this.campos.email = (evento.target as HTMLInputElement).value;
    // Otro email, otra cuenta: vuelve el camino normal.
    this.invitar = false;
    this.reactivar = false;
    this.limpiar('email');
  }

  elegirRol(evento: Event): void {
    this.campos.rol = (evento.target as HTMLSelectElement).value as RolMembresia | '';
    this.reactivar = false;
    this.limpiar('rol');
    delete this.errores.sedes;
  }

  escribirNombre(clave: 'nombre' | 'apellidos', evento: Event): void {
    this.campos[clave] = (evento.target as HTMLInputElement).value;
    this.limpiar(clave);
  }

  alternarSede(id: number, marcada: boolean): void {
    this.campos.sedeIds = marcada ? [...this.campos.sedeIds.filter((s) => s !== id), id] : this.campos.sedeIds.filter((s) => s !== id);
    this.limpiar('sedes');
  }

  enviar(evento?: Event): void {
    evento?.preventDefault();
    if (this.enviando) {
      return;
    }
    this.errores = validarAlta(this.campos, this.invitar);
    const primero = (['email', 'rol', 'sedes', 'nombre', 'apellidos'] as ClaveAlta[]).find((c) => this.errores[c]);
    if (primero) {
      this.cdr.detectChanges();
      this.host.nativeElement.querySelector<HTMLElement>(`#miembro-alta-${primero === 'sedes' ? 'sedes-grupo' : primero}`)?.focus();
      return;
    }
    this.enviando = true;
    this.error = null;
    const invitacion = this.invitar;
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    this.api.altaDeMiembro(cuerpoAlta(this.campos, this.invitar, this.reactivar)).subscribe({
      next: (ficha) => {
        this.enviando = false;
        if (this.destruido) {
          this.avisos.exito(invitacion ? 'Invitación enviada.' : 'Miembro añadido.');
          this.alTerminarCerrado?.();
          return;
        }
        this.creada.emit({ ficha, invitacion });
      },
      error: (error) => {
        this.enviando = false;
        if (this.destruido) {
          return;
        }
        const estado = estadoHttp(error);
        const mensaje = mensajeError(error, 'No se pudo añadir al miembro.');
        if (estado === 404 && !this.invitar) {
          // Sin cuenta: el 404 de la API es la señal para pedir nombre y apellidos.
          this.invitar = true;
          this.cdr.detectChanges();
          this.host.nativeElement.querySelector<HTMLElement>('#miembro-alta-nombre')?.focus();
          return;
        }
        if (pideReactivar(estado, mensaje)) {
          this.reactivar = true;
        }
        this.error = mensaje;
        this.cdr.detectChanges();
      },
    });
  }
}
