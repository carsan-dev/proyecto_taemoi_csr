import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Subscription, forkJoin, of } from 'rxjs';
import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';

export const TIPOS_TURNO = ['Taekwondo Infantil', 'Taekwondo Joven', 'Taekwondo Adulto', 'Kickboxing',
  'Pilates', 'Defensa Personal Femenina', 'Competición'] as const;
const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const;
const HORA = /^([01]\d|2[0-3]):[0-5]\d$/;

interface GrupoDeTurno extends GrupoResumen { tipo?: string; }
interface DetalleTurno {
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
  grupoId?: number | null;
  grupoNombre?: string | null;
  alumnos?: unknown[] | null;
}

function horasValidas(form: AbstractControl): ValidationErrors | null {
  const inicio: string = form.get('horaInicio')?.value;
  const fin: string = form.get('horaFin')?.value;
  return inicio && fin && inicio >= fin ? { horasInvalidas: true } : null;
}

@Component({
  selector: 'pn-turno-panel',
  standalone: true,
  imports: [ReactiveFormsModule, PanelLateralComponent, BotonOcupadoComponent, BloqueErrorComponent, EsqueletoComponent],
  templateUrl: './turno-panel.component.html',
  styleUrls: ['./turno-panel.component.scss'],
})
export class TurnoPanelComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly avisos = inject(AvisosService);
  @Input() turnoId: number | null = null;
  @Input() grupoInicialId: number | null = null;
  @Output() readonly cerrado = new EventEmitter<void>();
  @Output() readonly guardado = new EventEmitter<void>();
  readonly dias = DIAS.map((nombre, i) => ({ nombre, letra: ['L', 'M', 'X', 'J', 'V', 'S', 'D'][i] }));
  readonly tipos = TIPOS_TURNO;
  readonly form = new FormGroup({
    diaSemana: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^(Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo)$/)] }),
    horaInicio: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(HORA)] }),
    horaFin: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(HORA)] }),
    grupoId: new FormControl('', { nonNullable: true }),
    tipoGrupo: new FormControl('', { nonNullable: true }),
  }, { validators: horasValidas });
  grupos: GrupoDeTurno[] = [];
  turno: DetalleTurno | null = null;
  cargando = true;
  errorCarga = '';
  errorGuardar = '';
  enviando = false;
  private carga?: Subscription;
  private envio?: Subscription;
  private readonly cambios = this.form.controls.grupoId.valueChanges.subscribe(() => this.validarTipo());

  get editando(): boolean { return this.turnoId !== null; }
  get grupoActualAusente(): boolean { return !!this.turno?.grupoId && !this.grupos.some((g) => g.id === this.turno?.grupoId); }
  get duracion(): string {
    if (this.form.controls.horaInicio.invalid || this.form.controls.horaFin.invalid || this.form.hasError('horasInvalidas')) return '';
    const minutos = (hora: string) => Number(hora.slice(0, 2)) * 60 + Number(hora.slice(3, 5));
    const total = minutos(this.form.controls.horaFin.value) - minutos(this.form.controls.horaInicio.value);
    return [Math.floor(total / 60) ? `${Math.floor(total / 60)} h` : '', total % 60 ? `${total % 60} min` : ''].filter(Boolean).join(' ');
  }
  get avisoAlumnos(): string {
    const n = Array.isArray(this.turno?.alumnos) ? this.turno.alumnos.length : 0;
    const nombre = this.turno?.grupoNombre || this.grupos.find((g) => g.id === this.turno?.grupoId)?.nombre;
    return n > 0 && nombre && this.grupoElegido !== (this.turno?.grupoId ?? null)
      ? `Este turno tiene ${n} alumnos de ${nombre}. Seguirán apuntados.` : '';
  }
  private get grupoElegido(): number | null { return this.form.controls.grupoId.value ? Number(this.form.controls.grupoId.value) : null; }

  ngOnInit(): void { this.cargar(); }
  cargar(): void {
    if (this.enviando) return;
    this.carga?.unsubscribe();
    this.cargando = true;
    this.errorCarga = '';
    this.errorGuardar = '';
    this.turno = null;
    this.carga = forkJoin({
      grupos: this.endpoints.obtenerResumenGrupos(contextoErrorLocal()),
      turno: this.editando ? this.endpoints.obtenerTurnoPorId(this.turnoId!, contextoErrorLocal()) : of(null),
    }).subscribe({
      next: ({ grupos, turno }: { grupos: GrupoDeTurno[]; turno: DetalleTurno | null }) => {
        this.grupos = grupos ?? [];
        this.turno = turno;
        if (this.editando && !turno) {
          this.errorCarga = 'No hemos podido cargar los detalles del turno';
        } else if (turno) {
          this.form.patchValue({ diaSemana: turno.diaSemana, horaInicio: turno.horaInicio?.slice(0, 5) ?? '',
            horaFin: turno.horaFin?.slice(0, 5) ?? '', grupoId: turno.grupoId ? String(turno.grupoId) : '' });
        } else {
          const grupo = this.grupos.find((g) => g.id === this.grupoInicialId);
          if (!this.grupos.some((g) => String(g.id) === this.form.controls.grupoId.value)) {
            this.form.patchValue({ grupoId: '', tipoGrupo: '' });
          }
          if (grupo) this.form.patchValue({ grupoId: String(grupo.id), tipoGrupo: this.esTipo(grupo.tipo) ? grupo.tipo : '' });
        }
        this.validarTipo();
        this.cargando = false;
      },
      error: (error: unknown) => {
        this.cargando = false;
        this.errorCarga = mensajeDeError(error) ?? 'No se pudieron cargar los datos del turno. Inténtalo de nuevo.';
      },
    });
  }

  private esTipo(valor: string | undefined): valor is typeof TIPOS_TURNO[number] {
    return TIPOS_TURNO.some((tipo) => tipo === valor);
  }
  private validarTipo(): void {
    const control = this.form.controls.tipoGrupo;
    control.setValidators(!this.editando && this.form.controls.grupoId.value
      ? [Validators.required, (campo) => this.esTipo(campo.value) ? null : { tipoInvalido: true }] : []);
    control.updateValueAndValidity({ emitEvent: false });
  }
  errorCampo(campo: 'diaSemana' | 'horaInicio' | 'horaFin' | 'tipoGrupo'): string {
    const control = this.form.controls[campo];
    if (!control.touched || !control.invalid) return '';
    if (campo === 'diaSemana') return 'El día de la semana es obligatorio';
    if (campo === 'tipoGrupo') return 'El tipo de grupo es obligatorio';
    return control.hasError('required') ? `La hora de ${campo === 'horaInicio' ? 'inicio' : 'fin'} es obligatoria` : 'Formato HH:mm requerido';
  }
  cerrar(): void { if (!this.enviando) this.cerrado.emit(); }
  guardar(): void {
    this.form.markAllAsTouched();
    if (this.enviando || this.cargando || this.errorCarga || this.form.invalid || (this.editando && !this.turno)) return;
    this.enviando = true;
    this.errorGuardar = '';
    const { diaSemana, horaInicio, horaFin, grupoId, tipoGrupo } = this.form.getRawValue();
    const horario = { diaSemana, horaInicio, horaFin };
    const cuerpo = { ...horario, asignarGrupo: !!grupoId, grupoId, tipoGrupo: grupoId ? tipoGrupo : '' };
    const peticion = this.editando ? this.endpoints.actualizarTurno(this.turnoId!, horario, contextoErrorLocal())
      : grupoId ? this.endpoints.crearTurnoConGrupo(cuerpo, contextoErrorLocal()) : this.endpoints.crearTurnoSinGrupo(cuerpo, contextoErrorLocal());
    this.envio = peticion.subscribe({
      next: () => {
        if (this.editando && this.grupoElegido !== (this.turno?.grupoId ?? null)) this.cambiarGrupo();
        else this.terminar(this.editando ? 'Turno actualizado correctamente' : 'Turno creado');
      },
      error: (error: unknown) => {
        this.enviando = false;
        this.errorGuardar = mensajeDeError(error) ?? (this.editando ? 'No hemos podido actualizar el turno' : 'No se pudo crear el turno. Inténtalo de nuevo.');
      },
    });
  }
  private cambiarGrupo(): void {
    const nuevo = this.grupoElegido;
    // El POST del grupo nuevo sustituye al anterior; no se tocan los alumnos apuntados.
    const peticion = nuevo ? this.endpoints.agregarTurnoAGrupo(nuevo, this.turnoId!, contextoErrorLocal())
      : this.endpoints.eliminarTurnoDeGrupo(this.turno!.grupoId!, this.turnoId!, contextoErrorLocal());
    this.envio = peticion.subscribe({
      next: () => this.terminar(nuevo ? 'Turno actualizado y asignado al grupo' : 'Turno actualizado y desvinculado del grupo'),
      error: () => {
        this.enviando = false;
        this.avisos.error(nuevo ? 'Turno actualizado pero error al asignar grupo' : 'Turno actualizado pero error al desvincular grupo');
        this.guardado.emit();
      },
    });
  }
  private terminar(mensaje: string): void {
    this.enviando = false;
    this.avisos.exito(mensaje);
    this.guardado.emit();
  }
  ngOnDestroy(): void { this.carga?.unsubscribe(); this.envio?.unsubscribe(); this.cambios.unsubscribe(); }
}
