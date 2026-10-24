import { Component, EventEmitter, OnDestroy, Output, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
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
  readonly disciplinas = this.catalogo.disciplinasActivas;

  @Output() readonly cerrado = new EventEmitter<void>();
  @Output() readonly creado = new EventEmitter<number>();
  readonly nombre = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] });
  readonly deporte = new FormControl(this.disciplinas().length === 1 ? this.disciplinas()[0].codigo : '', { nonNullable: true, validators: [Validators.required] });
  enviando = false;
  errorServidor = '';
  private envio?: Subscription;

  get error(): string {
    if (this.nombre.touched && this.nombre.hasError('required')) return 'El nombre del grupo es obligatorio';
    if (this.nombre.hasError('maxlength')) return 'Máximo 50 caracteres';
    return this.errorServidor;
  }

  get errorDeporte(): string {
    return this.deporte.touched && this.deporte.hasError('required') ? 'Elige el deporte del grupo' : '';
  }

  crear(): void {
    this.nombre.markAsTouched();
    this.deporte.markAsTouched();
    if (this.nombre.invalid || this.deporte.invalid || this.enviando) return;
    this.enviando = true;
    this.errorServidor = '';
    this.envio = this.endpoints.crearGrupo({ nombre: this.nombre.value, deporte: this.deporte.value }, contextoErrorLocal()).subscribe({
      next: (grupo: { id: number }) => {
        this.enviando = false;
        this.avisos.exito('Grupo creado');
        this.creado.emit(grupo.id);
      },
      error: (error: { error?: string | { mensaje?: string; message?: string } }) => {
        this.enviando = false;
        const cuerpo = error?.error;
        const texto = typeof cuerpo === 'string' ? cuerpo : cuerpo?.mensaje ?? cuerpo?.message;
        this.errorServidor = typeof texto === 'string' && texto.trim() ? texto.trim() : 'No se pudo crear el grupo. Inténtalo de nuevo.';
      },
    });
  }

  ngOnDestroy(): void {
    this.envio?.unsubscribe();
  }
}
