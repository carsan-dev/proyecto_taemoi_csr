import { Location } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, forkJoin } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { TurnoResumen } from '../../interfaces/turno-resumen';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';

interface AlumnoTurnos { id: number; nombre: string; apellidos: string; }
const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

@Component({
  selector: 'pn-gestionar-turnos-alumno',
  standalone: true,
  imports: [FormsModule, RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BotonOcupadoComponent, EsqueletoComponent],
  templateUrl: './gestionar-turnos-alumno.component.html',
  styleUrl: './gestionar-turnos-alumno.component.scss',
})
export class GestionarTurnosAlumnoComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly destroyRef = inject(DestroyRef);
  private carga?: Subscription;
  private envio?: Subscription;
  private revision = 0;
  alumnoId = 0;
  alumno: AlumnoTurnos | null = null;
  turnos: TurnoResumen[] = [];
  disponibles: TurnoResumen[] = [];
  seleccion = '';
  cargando = true;
  errorCarga = '';
  asignando = false;
  quitando: number | null = null;
  readonly esqueletos = Array.from({ length: 3 });
  get ocupado(): boolean { return this.asignando || this.quitando !== null; }

  ngOnInit(): void {
    this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params['alumnoId']);
      if (this.revision > 0 && id === this.alumnoId) return;
      this.alumnoId = id;
      this.cargar();
    });
  }
  cargar(): void {
    this.revision++;
    this.carga?.unsubscribe(); this.envio?.unsubscribe();
    this.alumno = null; this.turnos = []; this.disponibles = []; this.seleccion = '';
    this.asignando = false; this.quitando = null; this.cargando = true; this.errorCarga = '';
    if (!Number.isSafeInteger(this.alumnoId) || this.alumnoId <= 0) {
      this.cargando = false; this.errorCarga = 'No hemos podido obtener los datos del alumno'; return;
    }
    const opciones = { withCredentials: true, context: contextoErrorLocal() };
    this.carga = forkJoin({
      alumno: this.http.get<AlumnoTurnos>(`${environment.apiUrl}/alumnos/${this.alumnoId}`, opciones),
      turnos: this.http.get<TurnoResumen[]>(`${environment.apiUrl}/alumnos/${this.alumnoId}/turnos`, opciones),
      disponibles: this.http.get<TurnoResumen[]>(`${environment.apiUrl}/turnos/resumen`, opciones),
    }).subscribe({
      next: ({ alumno, turnos, disponibles }) => {
        this.alumno = alumno; this.turnos = this.ordenar(turnos); this.disponibles = this.ordenar(disponibles);
        this.cargando = false;
      },
      error: (error: unknown) => {
        this.cargando = false;
        this.errorCarga = mensajeDeError(error) ?? 'No hemos podido obtener los datos del alumno';
      },
    });
  }
  private ordenar(turnos: TurnoResumen[]): TurnoResumen[] {
    return [...turnos].sort((a, b) => DIAS.indexOf(a.diaSemana) - DIAS.indexOf(b.diaSemana)
      || a.horaInicio.localeCompare(b.horaInicio) || a.horaFin.localeCompare(b.horaFin) || a.id - b.id);
  }
  horario(turno: TurnoResumen): string { return `${turno.horaInicio.slice(0, 5)} – ${turno.horaFin.slice(0, 5)}`; }
  asignado(id: number): boolean { return this.turnos.some(turno => turno.id === id); }
  volver(): void {
    // TODO(3m-5): la entrada no proporciona el grupo de origen; conservar la vuelta atrás sin inventar su id.
    this.location.back();
  }
  asignar(): void {
    const turno = this.disponibles.find(item => String(item.id) === this.seleccion);
    if (this.ocupado || this.cargando || this.errorCarga || !this.alumno || !turno || this.asignado(turno.id)) return;
    this.asignando = true;
    this.envio = this.http.post<unknown>(`${environment.apiUrl}/alumnos/${this.alumnoId}/turnos/${turno.id}`, {},
      { withCredentials: true, context: contextoErrorLocal() }).subscribe({
      next: () => {
        this.turnos = this.ordenar([...this.turnos, turno]); this.seleccion = ''; this.asignando = false;
        this.avisos.exito('Turno asignado');
      },
      error: (error: unknown) => {
        this.asignando = false;
        this.avisos.error(mensajeDeError(error) ?? 'No hemos podido asignar el turno al alumno');
      },
    });
  }
  async quitar(turno: TurnoResumen): Promise<void> {
    if (this.ocupado || this.cargando || !this.alumno || !this.asignado(turno.id)) return;
    const revision = this.revision;
    this.quitando = turno.id;
    const { confirmado } = await this.confirmacion.confirmar({
      titulo: `¿Quitar el turno del ${turno.diaSemana.toLowerCase()} ${this.horario(turno)} a ${this.alumno.nombre}?`,
      mensaje: 'Puedes volver a asignarlo.', accion: 'Quitar', cancelar: 'Cancelar', destructivo: false,
    });
    if (this.destroyRef.destroyed || revision !== this.revision) return;
    if (!confirmado) { this.quitando = null; return; }
    this.envio = this.http.delete<TurnoResumen[]>(`${environment.apiUrl}/alumnos/${this.alumnoId}/turnos/${turno.id}`,
      { withCredentials: true, context: contextoErrorLocal() }).subscribe({
      next: turnos => {
        this.turnos = this.ordenar(turnos); this.quitando = null;
        this.avisos.exito('Turno quitado');
      },
      error: (error: unknown) => {
        this.quitando = null;
        this.avisos.error(mensajeDeError(error) ?? 'No se pudo quitar el turno');
      },
    });
  }
  ngOnDestroy(): void { this.revision++; this.carga?.unsubscribe(); this.envio?.unsubscribe(); }
}
