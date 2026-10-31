import { Component, EventEmitter, OnDestroy, Output, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { PermisosSesionService, SedeVisible } from '../../servicios/authentication/permisos-sesion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';

@Component({
  selector: 'pn-crear-grupo',
  standalone: true,
  imports: [ReactiveFormsModule, PanelLateralComponent, BotonOcupadoComponent],
  templateUrl: './crear-grupo-panel.component.html',
  styleUrls: ['./crear-grupo-panel.component.scss'],
})
export class CrearGrupoPanelComponent implements OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly avisos = inject(AvisosService);
  private readonly catalogo = inject(CatalogoService);
  private readonly permisos = inject(PermisosSesionService);
  readonly disciplinas = this.catalogo.disciplinasActivas;

  @Output() readonly cerrado = new EventEmitter<void>();
  @Output() readonly creado = new EventEmitter<number>();
  readonly nombre = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] });
  readonly deporte = new FormControl(this.disciplinas().length === 1 ? this.disciplinas()[0].codigo : '', { nonNullable: true, validators: [Validators.required] });
  /** Solo con varias sedes activas (E9-1): con una, el grupo no manda sede y el servidor la fija. */
  pedirSede = false;
  sedes: SedeVisible[] = [];
  readonly sede = new FormControl<number | null>(null);
  enviando = false;
  errorServidor = '';
  errorSedeServidor = '';
  private envio?: Subscription;
  private readonly lectura: Subscription;

  constructor() {
    this.lectura = this.permisos.obtener().subscribe({
      next: (p) => {
        this.sedes = (p?.sedes ?? []).filter((s) => s.activa !== false);
        this.pedirSede = !!p?.variasSedes && this.sedes.length > 1;
        this.sede.setValidators(this.pedirSede ? Validators.required : null);
        this.sede.updateValueAndValidity({ emitEvent: false });
      },
      // Sin permisos no se pide: el servidor decide la sede, como hoy.
      error: () => (this.pedirSede = false),
    });
  }

  get error(): string {
    if (this.nombre.touched && this.nombre.hasError('required')) return 'El nombre del grupo es obligatorio';
    if (this.nombre.hasError('maxlength')) return 'Máximo 50 caracteres';
    return this.errorServidor;
  }

  get errorDeporte(): string {
    return this.deporte.touched && this.deporte.hasError('required') ? 'Elige el deporte del grupo' : '';
  }

  get errorSede(): string {
    if (!this.pedirSede) return '';
    return this.sede.touched && this.sede.hasError('required') ? 'Elige la sede del grupo.' : this.errorSedeServidor;
  }

  crear(): void {
    this.nombre.markAsTouched();
    this.deporte.markAsTouched();
    this.sede.markAsTouched();
    if (this.nombre.invalid || this.deporte.invalid || (this.pedirSede && this.sede.invalid) || this.enviando) return;
    this.enviando = true;
    this.errorServidor = '';
    this.errorSedeServidor = '';
    const cuerpo: { nombre: string; deporte: string; sedeId?: number } = { nombre: this.nombre.value, deporte: this.deporte.value };
    if (this.pedirSede && this.sede.value !== null) cuerpo.sedeId = this.sede.value;
    this.envio = this.endpoints.crearGrupo(cuerpo, contextoErrorLocal()).subscribe({
      next: (grupo: { id: number }) => {
        this.enviando = false;
        this.avisos.exito('Grupo creado');
        this.creado.emit(grupo.id);
      },
      error: (error: { status?: number; error?: string | { mensaje?: string; message?: string } }) => {
        this.enviando = false;
        const respuesta = error?.error;
        const texto = typeof respuesta === 'string' ? respuesta : respuesta?.mensaje ?? respuesta?.message;
        const mensaje = typeof texto === 'string' && texto.trim() ? texto.trim() : 'No se pudo crear el grupo. Inténtalo de nuevo.';
        // Un 400 que habla de la sede se enseña en su campo.
        if (this.pedirSede && error?.status === 400 && /sede/i.test(mensaje)) this.errorSedeServidor = mensaje;
        else this.errorServidor = mensaje;
      },
    });
  }

  ngOnDestroy(): void {
    this.envio?.unsubscribe();
    this.lectura.unsubscribe();
  }
}
